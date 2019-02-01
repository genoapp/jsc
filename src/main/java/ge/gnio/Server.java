/*
 * Copyright 2018 Geno Papashvili
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ge.gnio;

import com.sun.istack.internal.NotNull;
import ge.gnio.annotation.KeyPacketListener;
import ge.gnio.listener.PacketListener;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.stream.Stream;



public class Server<T extends AbstractClient<T>> {


    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(Server.class.getSimpleName());

    private final int maxBufferSize;

    private final ExecutorService executorService;

    private final Class<T> clazz;

    private final Object attach;

    private final CountDownLatch latch;

    private final Selector selector;

    private final List<RunLaterHelper> runLaterList;

    private Thread selectorThread;

    private boolean eof = false;

    private IFilter iFilter;

    private Map<Integer, PacketListener<T>> packetsListeners;

    private long sleepTime;


    public Server(Class<T> clazz, int maxBufferSize, ExecutorService executorService,int sleepTime, IFilter iFilter,Object attach) throws IOException {

        Objects.requireNonNull(clazz);


        if (maxBufferSize <= 0) {
            throw new NumberFormatException("Buffer size can't negative or zero");
        }


        this.clazz = clazz;

        this.maxBufferSize = maxBufferSize + Integer.BYTES * 2;

        this.sleepTime = sleepTime;

        this.iFilter = iFilter;

        this.attach = attach;

        this.executorService = executorService;

        this.packetsListeners = new HashMap<>();

        this.runLaterList = new ArrayList<>();


        selector = Selector.open();

        latch = new CountDownLatch(1);

        selectorThread = new Thread(this::run);
        selectorThread.setName(getClass().getSimpleName() + "-" + selectorThread.getId());
        selectorThread.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    @SuppressWarnings("StatementWithEmptyBody")
    public T connect(InetSocketAddress inetSocketAddress) throws IOException {
        try {

            SocketChannel channel = SocketChannel.open();

            channel.configureBlocking(false);
            channel.connect(inetSocketAddress);


            while (!channel.finishConnect());

            SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
            T client = clazz.getDeclaredConstructor().newInstance();

            client.initial(this,key);

            key.attach(client);

            key.interestOps(SelectionKey.OP_READ);

            client.initial(attach);

            return client;

        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IOException(e);
        }

    }

    public void open(InetSocketAddress inetSocketAddress) throws IOException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

        serverSocketChannel.bind(inetSocketAddress);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private void run() {

        latch.countDown();

        T client;
        Iterator<SelectionKey> selectedKeys;

        int count;

        eof = true;
        while (eof) {
            try {
                count = selector.selectNow();
            } catch (IOException e) {
                count = 0;
            }

            if (count > 0) {
                selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    if (key.isValid()) {
                        client = clazz.cast(key.attachment());

                        switch (key.readyOps()) {
                            case SelectionKey.OP_ACCEPT:
                                accept(key);
                                break;
                            case SelectionKey.OP_CONNECT:
                                connect(key);
                                break;
                            case SelectionKey.OP_READ:
                                reader(client);
                                break;
                            case SelectionKey.OP_WRITE:
                                writer(client);
                                break;
                        }
                    }
                    selectedKeys.remove();

                }
            }

            if (executorService != null) {
                synchronized (runLaterList) {
                    Iterator<RunLaterHelper> iterator = runLaterList.iterator();
                    while (iterator.hasNext()) {
                        RunLaterHelper rlh = iterator.next();
                        if (rlh.time < Calendar.getInstance().getTimeInMillis()) {
                            iterator.remove();
                            executorService.execute(rlh.runnable);
                        }
                    }
                }
            }

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ignore) {

            }
        }

        for(SelectionKey  k : selector.keys()){
            if (k.channel() != null) {
                try {
                    k.channel().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            k.cancel();
        }

        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void writer(T client) {
        if (!client.getSendPacketQueue().isEmpty()) {
            Packet packet = client.getSendPacketQueue().poll();

            ByteBuffer buffer = ByteBuffer.allocate(Objects.requireNonNull(packet).getBuffer().remaining() + Integer.BYTES * 2).order(ByteOrder.LITTLE_ENDIAN);

            buffer.putInt(packet.getBuffer().remaining() + Integer.BYTES);
            buffer.putInt(packet.getKey());
            buffer.put(packet.getBuffer());
            buffer.flip();

            if (buffer.limit() > maxBufferSize) {
                throw new BufferOverflowException();
            }

            int result = -1;
            try {
                result = client.getChannel().write(buffer);
            } catch (IOException ignore) {
                //ignore
            }
            if (result == -1) {
                client.close();
            }
        }

        try {
            client.getSelectionKey().interestOps(SelectionKey.OP_READ);
        } catch (CancelledKeyException e) {
            //if AbstractClient close
        }

    }


    private void reader(T client) {

        ByteBuffer buffer = client.getReadBuffer();

        if (buffer == null) {
            buffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        }

        int result = -1;
        try {
            result = client.getChannel().read(buffer);
        } catch (IOException e) {
            //ignore
        }

        if (result != -1) {
            if (buffer.position() == Integer.BYTES && buffer.capacity() == Integer.BYTES) {
                buffer.flip();
                int size = buffer.getInt();
                if (size > maxBufferSize || size <= 0) {
                    size = maxBufferSize;
                }
                buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
            } else if (buffer.position() == buffer.capacity() && buffer.capacity() > Integer.BYTES) {

                ByteBuffer finalBuffer = buffer;
                finalBuffer.flip();

                Runnable runnable = () -> {
                    int key = finalBuffer.getInt();
                    byte[] bytes = new byte[finalBuffer.remaining()];
                    finalBuffer.get(bytes);

                    Packet packet = new Packet(key, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));

                    try {
                        packetsListeners.get(key).readPacket(client, packet);
                    } catch (NullPointerException e) {
                        if (client.getSecondaryPacketListener() != null && client.getSecondaryPacketListener().KEY == key) {
                            client.getSecondaryPacketListener().readPacket(client, packet);
                        }
                    }
                };

                client.setLastReadTime(Calendar.getInstance().getTimeInMillis());

                if (executorService != null) {
                    executorService.execute(runnable);
                } else {
                    runnable.run();
                }
                buffer = null;
            }
        } else {
            client.close();
        }

        client.setReadBuffer(buffer);
    }

    @SuppressWarnings("unused")
    private void connect(SelectionKey key) {
        //not code
    }


    private void accept(SelectionKey key) {
        SocketChannel socketChannel;
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            while ((socketChannel = serverSocketChannel.accept()) != null) {

                if (iFilter != null && !iFilter.doAccept(socketChannel.socket().getInetAddress())) {
                    socketChannel.close();
                    return;
                }

                socketChannel.configureBlocking(false);
                SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);

                try {

                    T client = clazz.getDeclaredConstructor().newInstance();
                    client.initial(this, selectionKey);

                    selectionKey.attach(client);

                    client.initial(attach); //user initial

                } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void addPacketListener(@NotNull  PacketListener<T> listener){

        Objects.requireNonNull(listener);

        int key = Objects.requireNonNull(listener.getClass().getAnnotation(KeyPacketListener.class),"Undefined PacketListener  key").value();

        if (packetsListeners.containsKey(key)) {
            throw new IllegalArgumentException(String.format("key %d already exists...", key));
        }

        packetsListeners.put(key,listener);

    }


    public void addPacketListener(int key, PacketListener<T> listener) {
        if (packetsListeners.containsKey(key)) {
            throw new IllegalArgumentException(String.format("key %d already exists...", key));
        }
        packetsListeners.put(key, listener);
    }

    public void removePacketListener(int key) {
        packetsListeners.remove(key);
    }


    public PacketListener<T> getPacketListener(int key) {
        return packetsListeners.get(key);
    }


    @SuppressWarnings("unchecked")
    public Stream<T> getClients() {
        synchronized (selector) {
            Stream<T> client = selector.keys().stream()
                    .filter(k -> k.attachment() != null && k.attachment() instanceof AbstractClient)
                    .map(k -> clazz.cast(k.attachment()));

            return Stream.of(client.toArray(size -> (T[]) Array.newInstance(clazz, size)));
        }
    }


    public void close() {
        if (!eof) {
            throw new IllegalStateException("Server not open");
        }
        eof = false;
    }


    public boolean isClosed() {
        return !eof;
    }

    Thread getSelectorThread() {
        return selectorThread;
    }


    public void runLater(Runnable runnable) {
        runLater(runnable, 0);
    }


    public void runLater(Runnable runnable, int delay) {
        Objects.requireNonNull(runnable);
        if (executorService == null) {
            throw new UnsupportedOperationException("ThreadPoolExecutor is null");
        }
        if (delay < 0) {
            throw new NumberFormatException("Can't a negative delay");
        }
        synchronized (runLaterList) {
            try {
                runLaterList.add(new RunLaterHelper(runnable, Calendar.getInstance().getTimeInMillis() + delay));
            } finally {
                Collections.sort(runLaterList);
            }
        }
    }

    private static class RunLaterHelper implements Comparable<RunLaterHelper> {
        private final long time;
        private final Runnable runnable;

        RunLaterHelper(Runnable runnable, long time) {
            this.time = time;
            this.runnable = runnable;
        }

        @Override
        public int compareTo(RunLaterHelper o) {
            return Long.compare(this.time, o.time);
        }
    }

    public void setSleepTime(long sleepTime) {
        if(sleepTime < 0){
            throw new IllegalArgumentException("can't negative time");
        }
        this.sleepTime = sleepTime;
    }


}
