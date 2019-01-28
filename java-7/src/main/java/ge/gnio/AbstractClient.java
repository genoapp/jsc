
/*
 * Copyright 2018  Geno Papashvili
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

import ge.gnio.listener.CallablePacketListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;


public abstract class AbstractClient<T extends AbstractClient<T>> {


    private Queue<Packet> sendPacketQueue = new LinkedBlockingQueue<>();

    private ReentrantLock reentLocker = new ReentrantLock();


    private Server<T> _server;

    private SelectionKey selectionKey;
    private SocketChannel channel;

    private boolean connected = false;



    private SecondaryPacketListener<T> secondaryPacketListener = null;
    private ByteBuffer readBuffer;
    private long lastReadTime = 0;



    final void initial(Server<T> server, SelectionKey selectionKey){
        this._server = server;
        this.selectionKey = selectionKey;
        this.channel = (SocketChannel) selectionKey.channel();
        this.connected = true;
    }

    public abstract void initial(Object attached);


    final SelectionKey getSelectionKey() {
        return selectionKey;
    }


    public final SocketChannel getChannel() {
        return channel;
    }


    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            // e.printStackTrace();
        }
        selectionKey.cancel();
        connected = false;
    }

    public final Server<T> getServer() {
        return _server;
    }

    public final boolean isConnected() {
        return connected;
    }

    public final synchronized void sendPacket(Packet packet) {
        Objects.requireNonNull(packet);
        sendPacketQueue.add(packet.clone());
        selectionKey.interestOps(SelectionKey.OP_WRITE);
    }

    public final  <R>  R sendPacket(Packet packet, final CallablePacketListener<T,R> callablePacketListener, long timeout) {

        if(Objects.equals(Thread.currentThread(),getServer().getSelectorThread())){
            throw new IllegalThreadStateException("try another thread");
        }

        if(_server.getPacketListener(packet.getKey()) != null){
            throw new IllegalArgumentException(String.format("key %d already exists...",packet.getKey()));
        }

        try {
            reentLocker.lock();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<R> value = new AtomicReference<>();

            secondaryPacketListener = new SecondaryPacketListener<T>(packet.getKey()) {
                @Override
                public void readPacket(T client, Packet packet) {
                    value.set(callablePacketListener.readPacket(client, packet));
                    latch.countDown();
                }
            };

            sendPacket(packet);
            try {
                latch.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {

            }
            secondaryPacketListener = null;
            return value.get();
        } finally {
            reentLocker.unlock();
        }
    }

    SecondaryPacketListener<T> getSecondaryPacketListener() {//package private
        return secondaryPacketListener;
    }

    final void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    static abstract class SecondaryPacketListener<C extends AbstractClient>{


        public final int KEY;

        public abstract void readPacket(C client, Packet packet);

        SecondaryPacketListener(int key) {//package private
            this.KEY = key;
        }

    }



    Queue<Packet> getSendPacketQueue() {
        return sendPacketQueue;
    }


    ByteBuffer getReadBuffer(){
        return this.readBuffer;
    }

    void setReadBuffer(ByteBuffer readBuffer) {
        this.readBuffer = readBuffer;
    }
}
