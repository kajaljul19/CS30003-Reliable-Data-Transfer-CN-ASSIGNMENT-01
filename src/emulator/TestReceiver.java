
package emulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TestReceiver {
    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(4003)) {
            System.out.println("Test receiver listening on port 4003");

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String message = new String(
                packet.getData(), packet.getOffset(),
                packet.getLength(), StandardCharsets.UTF_8
            );
            System.out.println("Received: " + message);

            byte[] reply = "ACK: received".getBytes(StandardCharsets.UTF_8);
            DatagramPacket response = new DatagramPacket(
                reply, reply.length,
                packet.getAddress(), packet.getPort()
            );
            socket.send(response);
            System.out.println("ACK sent");
        }
    }
}
