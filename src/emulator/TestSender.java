
package emulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class TestSender {
    private static final String HOST = "127.0.0.1";
    private static final int EMULATOR_PORT = 4001;
    private static final int TIMEOUT_MS = 5000;
    private static final int MESSAGE_COUNT = 5;

    public static void main(String[] args) throws Exception {
        InetAddress address = InetAddress.getByName(HOST);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);

            System.out.println("Sender started on local port "
                    + socket.getLocalPort());

            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                String message = "Test message " + i
                        + " through the emulator!";

                byte[] data = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, address, EMULATOR_PORT);

                socket.send(packet);
                System.out.println("Sent: " + message);

                byte[] buffer = new byte[2048];
                DatagramPacket response =
                        new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(response);

                    String reply = new String(
                            response.getData(),
                            response.getOffset(),
                            response.getLength(),
                            StandardCharsets.UTF_8);

                    System.out.println("Reply: " + reply);
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout: no ACK received for message "
                            + i + " within 5 seconds.");
                }
            }

            System.out.println("Sender test completed.");
        }
    }
}
