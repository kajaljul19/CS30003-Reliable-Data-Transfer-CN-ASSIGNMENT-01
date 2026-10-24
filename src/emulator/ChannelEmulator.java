
package emulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class ChannelEmulator {

    private static final int MAX_PACKET_SIZE = 65535;

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println(
                "Usage: java emulator.ChannelEmulator "
                + "<senderListenPort> <receiverListenPort> "
                + "<receiverHost> <receiverPort> "
                + "<senderHost> <senderPort>"
            );
            return;
        }

        int senderListenPort = Integer.parseInt(args[0]);
        int receiverListenPort = Integer.parseInt(args[1]);
        InetAddress receiverHost = InetAddress.getByName(args[2]);
        int receiverPort = Integer.parseInt(args[3]);

        // Retained for command-line compatibility.
        // Replies are sent to the actual sender endpoint learned at runtime.
        InetAddress configuredSenderHost = InetAddress.getByName(args[4]);
        int configuredSenderPort = Integer.parseInt(args[5]);

        validatePort(senderListenPort);
        validatePort(receiverListenPort);
        validatePort(receiverPort);
        validatePort(configuredSenderPort);

        AtomicReference<InetSocketAddress> senderEndpoint =
            new AtomicReference<>();

        try (
            DatagramSocket senderSocket =
                new DatagramSocket(senderListenPort);
            DatagramSocket receiverSocket =
                new DatagramSocket(receiverListenPort)
        ) {
            System.out.println("Channel emulator started.");
            System.out.println("Sender-facing port: " + senderListenPort);
            System.out.println("Receiver-facing port: " + receiverListenPort);
            System.out.println("Receiver: " + receiverHost + ":" + receiverPort);
            System.out.println("Configured sender fallback: "
                + configuredSenderHost + ":" + configuredSenderPort);

            Thread dataThread = new Thread(() -> {
                byte[] buffer = new byte[MAX_PACKET_SIZE];

                while (!senderSocket.isClosed()) {
                    try {
                        DatagramPacket incoming =
                            new DatagramPacket(buffer, buffer.length);

                        senderSocket.receive(incoming);

                        InetSocketAddress actualSender =
                            new InetSocketAddress(
                                incoming.getAddress(),
                                incoming.getPort()
                            );

                        senderEndpoint.set(actualSender);

                        byte[] bytes = Arrays.copyOfRange(
                            incoming.getData(),
                            incoming.getOffset(),
                            incoming.getOffset() + incoming.getLength()
                        );

                        DatagramPacket outgoing = new DatagramPacket(
                            bytes,
                            bytes.length,
                            receiverHost,
                            receiverPort
                        );

                        receiverSocket.send(outgoing);

                        System.out.println("Forwarded data: "
                            + bytes.length + " bytes from " + actualSender);
                    } catch (SocketException e) {
                        if (!senderSocket.isClosed()) {
                            System.err.println("Sender socket error: "
                                + e.getMessage());
                        }
                    } catch (IOException e) {
                        System.err.println("Data forwarding failed: "
                            + e.getMessage());
                    }
                }
            }, "emulator-data-forwarder");

            Thread replyThread = new Thread(() -> {
                byte[] buffer = new byte[MAX_PACKET_SIZE];

                while (!receiverSocket.isClosed()) {
                    try {
                        DatagramPacket incoming =
                            new DatagramPacket(buffer, buffer.length);

                        receiverSocket.receive(incoming);

                        byte[] bytes = Arrays.copyOfRange(
                            incoming.getData(),
                            incoming.getOffset(),
                            incoming.getOffset() + incoming.getLength()
                        );

                        InetSocketAddress endpoint = senderEndpoint.get();

                        if (endpoint == null) {
                            System.out.println(
                                "Reply ignored: no sender endpoint learned yet."
                            );
                            continue;
                        }

                        DatagramPacket outgoing = new DatagramPacket(
                            bytes,
                            bytes.length,
                            endpoint.getAddress(),
                            endpoint.getPort()
                        );

                        senderSocket.send(outgoing);

                        System.out.println("Forwarded reply: "
                            + bytes.length + " bytes to " + endpoint);
                    } catch (SocketException e) {
                        if (!receiverSocket.isClosed()) {
                            System.err.println("Receiver socket error: "
                                + e.getMessage());
                        }
                    } catch (IOException e) {
                        System.err.println("Reply forwarding failed: "
                            + e.getMessage());
                    }
                }
            }, "emulator-reply-forwarder");

            dataThread.start();
            replyThread.start();

            dataThread.join();
            replyThread.join();
        }
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                "Port must be between 1 and 65535: " + port
            );
        }
    }
}
