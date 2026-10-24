
package emulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TestReceiver {
    private static final int RECEIVER_PORT = 4003;
    private static final int EXPECTED_MESSAGES = 5;

    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(RECEIVER_PORT)) {
            System.out.println("Test receiver listening on port "
                    + RECEIVER_PORT);

            byte[] buffer = new byte[2048];

            for (int i = 1; i <= EXPECTED_MESSAGES; i++) {
                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8);

                System.out.println("Received (" + i + "): " + message);

                byte[] reply = "ACK: received".getBytes(
                        StandardCharsets.UTF_8);

                DatagramPacket response = new DatagramPacket(
                        reply,
                        reply.length,
                        packet.getAddress(),
                        packet.getPort());

                socket.send(response);
                System.out.println("ACK sent for message " + i);
            }

            System.out.println("Receiver test completed.");
        }
    }
}
