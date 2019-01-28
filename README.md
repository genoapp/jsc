# Server-Client
java Server and Client


**Demo Server:**

```java
public class DemoServer {
    public static void main(String[] args) throws IOException{

        Server<Person> server = new Server<>(Person.class,null,null);
        server.addPacketListener(10, new PacketListener<Person>() {
            @Override
            public void readPacket(Person client, Packet packet) {
                client.print(packet.readString());
                client.sendPacket(packet.clear().writeString("Received message Time: "+Long.toString(Calendar.getInstance().getTimeInMillis())).flip());
            }
        });
        server.open(new InetSocketAddress(9090));

    }

    public  static  class Person extends AbstractClient<Person>{


        public Person(Server<Person> server, SelectionKey selectionKey) {
            super(server, selectionKey);
        }


        public void print(String str){
            System.out.println(str);
        }
    }

}
```

**Demo Client:**
```java
public class DemoClient {
    public static void main(String[] args) throws IOException, InterruptedException {


        Server<Client> server = new Server<>(Client.class, null, null);

        Client client = server.connect(new InetSocketAddress(9090));

        while (true){


           String str = client.sendPacket(new Packet(10).writeString("Hello java server").flip(), new CallablePacketListener<Client, String>() {
               @Override
               public String readPacket(Client client, Packet packet) {
                   return packet.readString();
               }
           },4000);

            System.out.println(str);

            Thread.sleep(1000);
        }

    }
}

```
