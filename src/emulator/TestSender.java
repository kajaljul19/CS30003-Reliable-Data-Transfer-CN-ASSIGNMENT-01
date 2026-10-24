
package emulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TestSender {
    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);

            byte[] message = "Hello through the emulator!".getBytes(
                StandardCharsets.UTF_8
            );

            DatagramPacket packet = new DatagramPacket(
                message, message.length,
                InetAddress.getByName("127.0.0.1"), 4001
            );
            socket.send(packet);

            System.out.println("Message sent from local port "
                + socket.getLocalPort());

            byte[] buffer = new byte[2048];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            System.out.println("Reply: " + new String(
                response.getData(), response.getOffset(),
                response.getLength(), StandardCharsets.UTF_8
            ));
        }
    }
}
