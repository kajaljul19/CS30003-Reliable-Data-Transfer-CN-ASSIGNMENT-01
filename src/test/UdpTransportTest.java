package test;
import transport.ReceivedDatagram;
import transport.UdpTransport;
import java.net.InetAddress;

public class UdpTransportTest {

    public static void main(String[] args) throws Exception {
        int receiverPort = 5001;
        int senderPort = 5002;

        try (
            UdpTransport receiver = new UdpTransport(receiverPort);
            UdpTransport sender = new UdpTransport(senderPort)
        ) {
            Thread receiverThread = new Thread(() -> {
                try {
                    ReceivedDatagram message = receiver.receive();

                    System.out.println(
                        "Receiver got: " +
                        new String(message.getData())
                    );

                    System.out.println(
                        "Sender address: " +
                        message.getSenderAddress()
                    );

                    System.out.println(
                        "Sender port: " +
                        message.getSenderPort()
                    );

                    receiver.sendTo(
                        "ACK".getBytes(),
                        message
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            receiverThread.start();

            sender.send(
                "Hello UDP!".getBytes(),
                InetAddress.getLoopbackAddress(),
                receiverPort
            );

            receiver.setTimeout(3000);
            ReceivedDatagram ack = sender.receive();

            System.out.println(
                "Sender got: " +
                new String(ack.getData())
            );

            receiverThread.join();
        }
    }
}
