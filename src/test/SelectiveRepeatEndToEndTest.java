package test;

import common.FileHasher;
import transport.SelectiveRepeatReceiver;
import transport.SelectiveRepeatSender;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/** Localhost integration test for a complete Selective Repeat file transfer. */
public final class SelectiveRepeatEndToEndTest {
    public static void main(String[] args) throws Exception {
        Path source = Files.createTempFile("sr-source-", ".bin");
        Path destination = Files.createTempFile("sr-destination-", ".bin");
        Files.deleteIfExists(destination);
        byte[] content = new byte[32 * 1024 + 317];
        new Random(2026).nextBytes(content);
        Files.write(source, content);

        int port;
        try (DatagramSocket socket = new DatagramSocket(0)) {
            port = socket.getLocalPort();
        }

        final Throwable[] receiverFailure = new Throwable[1];
        Thread receiver = new Thread(() -> {
            try {
                new SelectiveRepeatReceiver().receiveFile(destination, port);
            } catch (Throwable error) {
                receiverFailure[0] = error;
            }
        }, "selective-repeat-test-receiver");
        receiver.start();
        Thread.sleep(100);

        try (SelectiveRepeatSender sender = new SelectiveRepeatSender(0, 4)) {
            sender.sendFile(source, InetAddress.getLoopbackAddress(), port);
        }
        receiver.join(10_000);

        try {
            if (receiver.isAlive()) throw new AssertionError("Receiver did not finish within 10 seconds");
            if (receiverFailure[0] != null) throw new AssertionError("Receiver failed", receiverFailure[0]);
            byte[] original = Files.readAllBytes(source);
            byte[] received = Files.readAllBytes(destination);
            if (!Arrays.equals(original, received)) throw new AssertionError("Received bytes differ from source");
            if (!FileHasher.matches(source, destination)) throw new AssertionError("SHA-256 hashes differ");
            System.out.println("SHA-256: " + FileHasher.sha256(source));
            if (!Arrays.equals(sha256(original), sha256(received))) throw new AssertionError("SHA-256 mismatch");
            System.out.println("PASS: Selective Repeat end-to-end transfer; byte comparison and SHA-256 match.");
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(destination);
        }
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
}
