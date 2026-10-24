
package emulator;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ChannelEmulator {
    private static final int MAX_PACKET_SIZE = 65535;

    private static double loss = 0.0;
    private static double duplication = 0.0;
    private static double corruption = 0.0;
    private static double reordering = 0.0;
    private static long delayMs = 0;
    private static long jitterMs = 0;
    private static long reorderDelayMs = 100;
    private static long seed = 42;

    private static ScheduledExecutorService scheduler;
    private static Random dataRandom;
    private static Random replyRandom;

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            usage();
            return;
        }

        int senderListenPort = Integer.parseInt(args[0]);
        int receiverListenPort = Integer.parseInt(args[1]);
        InetAddress receiverHost = InetAddress.getByName(args[2]);
        int receiverPort = Integer.parseInt(args[3]);
        InetAddress configuredSenderHost = InetAddress.getByName(args[4]);
        int configuredSenderPort = Integer.parseInt(args[5]);

        validatePort(senderListenPort);
        validatePort(receiverListenPort);
        validatePort(receiverPort);
        validatePort(configuredSenderPort);

        parseOptions(args, 6);
        validateOptions();

        dataRandom = new Random(seed);
        replyRandom = new Random(seed ^ 0x5DEECE66DL);
        scheduler = Executors.newScheduledThreadPool(2);

        AtomicReference<InetSocketAddress> senderEndpoint =
                new AtomicReference<>();

        try (
            DatagramSocket senderSocket =
                    new DatagramSocket(senderListenPort);
            DatagramSocket receiverSocket =
                    new DatagramSocket(receiverListenPort)
        ) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                scheduler.shutdownNow();
            }));

            System.out.println("Channel emulator started.");
            System.out.println("Sender-facing port: " + senderListenPort);
            System.out.println("Receiver-facing port: " + receiverListenPort);
            System.out.println("Receiver: " + receiverHost + ":" + receiverPort);
            System.out.println("Configured sender fallback: "
                    + configuredSenderHost + ":" + configuredSenderPort);
            System.out.printf(
                    "Impairments: loss=%.2f duplication=%.2f "
                    + "corruption=%.2f reordering=%.2f "
                    + "delay=%dms jitter=%dms reorderDelay=%dms seed=%d%n",
                    loss, duplication, corruption, reordering,
                    delayMs, jitterMs, reorderDelayMs, seed
            );
            System.out.println("Single active sender is supported.");

            Thread dataThread = new Thread(() -> {
                receiveLoop(senderSocket, (incoming) -> {
                    senderEndpoint.set(new InetSocketAddress(
                            incoming.getAddress(), incoming.getPort()));

                    processPacket(
                            incoming, receiverSocket, receiverHost,
                            receiverPort, dataRandom, "DATA"
                    );
                });
            }, "emulator-data-forwarder");

            Thread replyThread = new Thread(() -> {
                receiveLoop(receiverSocket, (incoming) -> {
                    InetSocketAddress endpoint = senderEndpoint.get();

                    if (endpoint == null) {
                        System.out.println(
                                "Reply ignored: no sender endpoint learned yet.");
                        return;
                    }

                    processPacket(
                            incoming, senderSocket, endpoint.getAddress(),
                            endpoint.getPort(), replyRandom, "REPLY"
                    );
                });
            }, "emulator-reply-forwarder");

            dataThread.start();
            replyThread.start();

            dataThread.join();
            replyThread.join();
        } finally {
            scheduler.shutdownNow();
        }
    }

    private interface PacketHandler {
        void handle(DatagramPacket packet);
    }

    private static void receiveLoop(
            DatagramSocket socket, PacketHandler handler) {
        byte[] buffer = new byte[MAX_PACKET_SIZE];

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] copy = Arrays.copyOfRange(
                        packet.getData(), packet.getOffset(),
                        packet.getOffset() + packet.getLength());

                DatagramPacket independent = new DatagramPacket(
                        copy, copy.length,
                        packet.getAddress(), packet.getPort());

                handler.handle(independent);
            } catch (SocketException e) {
                if (!socket.isClosed()) {
                    System.err.println("Socket error: " + e.getMessage());
                }
            } catch (IOException e) {
                System.err.println("Receive error: " + e.getMessage());
            }
        }
    }

    private static void processPacket(
            DatagramPacket incoming,
            DatagramSocket outgoingSocket,
            InetAddress destination,
            int destinationPort,
            Random random,
            String direction) {

        byte[] bytes = Arrays.copyOfRange(
                incoming.getData(), incoming.getOffset(),
                incoming.getOffset() + incoming.getLength());

        synchronized (random) {
            if (random.nextDouble() < loss) {
                System.out.printf("%s dropped (%d bytes)%n",
                        direction, bytes.length);
                return;
            }

            if (bytes.length > 0 && random.nextDouble() < corruption) {
                int index = random.nextInt(bytes.length);
                int bit = random.nextInt(8);
                bytes[index] ^= (byte) (1 << bit);
                System.out.printf(
                        "%s corrupted at byte %d, bit %d%n",
                        direction, index, bit);
            }

            long jitter = jitterMs == 0
                    ? 0
                    : (long) (random.nextDouble() * (jitterMs + 1));

            long packetDelay = delayMs + jitter;

            // Selected packets receive extra delay, allowing later packets
            // to overtake them. This is probabilistic reordering.
            if (random.nextDouble() < reordering) {
                packetDelay += reorderDelayMs;
                System.out.printf("%s selected for reordering%n", direction);
            }

            boolean duplicate = random.nextDouble() < duplication;

            scheduleSend(bytes, outgoingSocket, destination,
                    destinationPort, packetDelay, direction);

            if (duplicate) {
                long duplicateDelay = packetDelay + 1;
                scheduleSend(bytes, outgoingSocket, destination,
                        destinationPort, duplicateDelay, direction);
                System.out.printf("%s duplicated%n", direction);
            }
        }
    }

    private static void scheduleSend(
            byte[] bytes,
            DatagramSocket socket,
            InetAddress destination,
            int destinationPort,
            long delay,
            String direction) {

        byte[] packetBytes = bytes.clone();

        scheduler.schedule(() -> {
            if (socket.isClosed()) {
                return;
            }

            try {
                DatagramPacket outgoing = new DatagramPacket(
                        packetBytes, packetBytes.length,
                        destination, destinationPort);
                socket.send(outgoing);

                System.out.printf("%s forwarded: %d bytes after %dms%n",
                        direction, packetBytes.length, delay);
            } catch (IOException e) {
                System.err.println(direction + " forwarding failed: "
                        + e.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static void parseOptions(String[] args, int start) {
        for (int i = start; i < args.length; i++) {
            String[] option = args[i].split("=", 2);
            if (option.length != 2) {
                throw new IllegalArgumentException(
                        "Expected --name=value, got: " + args[i]);
            }

            String name = option[0];
            String value = option[1];

            switch (name) {
                case "--loss" ->
                    loss = Double.parseDouble(value);
                case "--duplication" ->
                    duplication = Double.parseDouble(value);
                case "--corruption" ->
                    corruption = Double.parseDouble(value);
                case "--reordering" ->
                    reordering = Double.parseDouble(value);
                case "--delay-ms" ->
                    delayMs = Long.parseLong(value);
                case "--jitter-ms" ->
                    jitterMs = Long.parseLong(value);
                case "--reorder-delay-ms" ->
                    reorderDelayMs = Long.parseLong(value);
                case "--seed" ->
                    seed = Long.parseLong(value);
                default ->
                    throw new IllegalArgumentException(
                            "Unknown option: " + name);
            }
        }
    }

    private static void validateOptions() {
        checkProbability("loss", loss);
        checkProbability("duplication", duplication);
        checkProbability("corruption", corruption);
        checkProbability("reordering", reordering);

        if (delayMs < 0 || jitterMs < 0 || reorderDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Delay and jitter values cannot be negative.");
        }
    }

    private static void checkProbability(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0");
        }
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Port must be between 1 and 65535: " + port);
        }
    }

    private static void usage() {
        System.out.println(
            "Usage: java -cp out emulator.ChannelEmulator "
            + "<senderListenPort> <receiverListenPort> "
            + "<receiverHost> <receiverPort> "
            + "<senderHost> <senderPort> [options]\n"
            + "Options:\n"
            + "  --loss=0.0..1.0\n"
            + "  --duplication=0.0..1.0\n"
            + "  --corruption=0.0..1.0\n"
            + "  --reordering=0.0..1.0\n"
            + "  --delay-ms=N\n"
            + "  --jitter-ms=N\n"
            + "  --reorder-delay-ms=N\n"
            + "  --seed=N"
        );
    }
}
