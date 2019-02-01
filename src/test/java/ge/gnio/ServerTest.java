package ge.gnio;

import ge.gnio.annotation.KeyPacketListener;
import ge.gnio.listener.PacketListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;



@RunWith(MockitoJUnitRunner.class)
public class ServerTest {


    Server<Client> server;

    @Before
    public void beforeInit() throws IOException {
        server = new Server<>(Client.class,30000, Executors.newFixedThreadPool(2),100,null,this);
    }

    @Test
    public void addPacketListener() {
        server.addPacketListener(new MyPacketListener());
        server.addPacketListener(new MyPacketListener());
    }


    @KeyPacketListener(10)
    private static class MyPacketListener implements PacketListener<Client> {

        @Override
        public void readPacket(Client client, Packet packet) {
            System.out.println(packet.readString());
        }
    }
}