package transport;

import common.Packet;
import common.PacketConstants;
import common.PacketIntegrity;
import protocol.TransferStats;
import common.PacketType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Selective Repeat file receiver over UDP. */
public class SelectiveRepeatReceiver {
    private static final int MAX_BUFFERED_PACKETS = 4096;
    private static final int END_LINGER_TIMEOUT_MS = 5000;
    private final TransferStats stats = new TransferStats();
    public TransferStats getStats() { return stats; }
    private static Packet decode(byte[] bytes) { return PacketIntegrity.decode(bytes); }

    public void receiveFile(Path destinationFile, int localPort) throws IOException {
        if (localPort < 1 || localPort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        stats.reset(); stats.startNanos = System.nanoTime();
        Path parent = destinationFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (UdpTransport socket = new UdpTransport(localPort)) {
            InetAddress peerAddress = null;
            int peerPort = -1;
            long expectedSize = -1;
            int expectedPackets = -1;
            int base = 0;
            Map<Integer, byte[]> buffer = new TreeMap<>();
            boolean started = false;

            while (true) {
                ReceivedDatagram datagram = socket.receive();
                Packet packet;
                packet = decode(datagram.getData());
                if (packet == null) { stats.corruptedDropped++; continue; }

                if (!started) {
                    if (packet.getType() != PacketType.START || packet.getData().length < 10) continue;
                    ByteBuffer meta = ByteBuffer.wrap(packet.getData());
                    int nameLength = Short.toUnsignedInt(meta.getShort());
                    if (nameLength > meta.remaining() - 8) continue;
                    byte[] nameBytes = new byte[nameLength];
                    meta.get(nameBytes);
                    expectedSize = meta.getLong();
                    stats.fileBytes = expectedSize;
                    if (expectedSize < 0) continue;
                    // The sender-provided filename is metadata only; use the caller's destination path.
                    expectedPackets = (int) ((expectedSize + PacketConstants.MAX_PAYLOAD_SIZE - 1) / PacketConstants.MAX_PAYLOAD_SIZE);
                    peerAddress = datagram.getSenderAddress();
                    peerPort = datagram.getSenderPort();
                    Files.write(destinationFile, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    started = true;
                    sendAck(socket, peerAddress, peerPort, packet.getSequenceNumber(), PacketConstants.FLAG_START);
                    continue;
                }

                if (!datagram.getSenderAddress().equals(peerAddress) || datagram.getSenderPort() != peerPort) continue;

                if (packet.getType() == PacketType.START) {
                    sendAck(socket, peerAddress, peerPort, packet.getSequenceNumber(), PacketConstants.FLAG_START);
                    continue;
                }

                if (packet.getType() == PacketType.DATA) {
                    int seq = packet.getSequenceNumber();
                    if (seq < 0 || seq >= expectedPackets) continue;
                    // ACK every valid DATA packet, including duplicates and out-of-order arrivals.
                    sendAck(socket, peerAddress, peerPort, seq, (byte) 0);
                    if (seq >= base && seq < base + MAX_BUFFERED_PACKETS) {
                        buffer.putIfAbsent(seq, packet.getData());
                        try (OutputStream out = Files.newOutputStream(destinationFile,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                            while (buffer.containsKey(base)) {
                                byte[] bytes = buffer.remove(base);
                                out.write(bytes);
                                base++;
                            }
                        }
                    }
                    continue;
                }

                if (packet.getType() == PacketType.END) {
    if (base == expectedPackets
            && Files.exists(destinationFile)
            && Files.size(destinationFile) == expectedSize) {

        stats.endNanos = System.nanoTime();
        sendAck(
                socket,
                peerAddress,
                peerPort,
                packet.getSequenceNumber(),
                PacketConstants.FLAG_END
        );

        long lingerDeadline = System.currentTimeMillis() + END_LINGER_TIMEOUT_MS;

        while (System.currentTimeMillis() < lingerDeadline) {
            try {
                socket.setTimeout((int) Math.max(
                        1,
                        lingerDeadline - System.currentTimeMillis()
                ));

                ReceivedDatagram duplicate = socket.receive();

                if (!duplicate.getSenderAddress().equals(peerAddress)
        || duplicate.getSenderPort() != peerPort) {
                    continue;
                }

                Packet repeatedEnd = decode(duplicate.getData());

                if (repeatedEnd != null
        && repeatedEnd.getType() == PacketType.END
        && repeatedEnd.getFlags() == PacketConstants.FLAG_END
        && repeatedEnd.getSequenceNumber() == packet.getSequenceNumber()) {
                    sendAck(
                            socket,
                            peerAddress,
                            peerPort,
                            repeatedEnd.getSequenceNumber(),
                            PacketConstants.FLAG_END
                    );
                }
            } catch (java.net.SocketTimeoutException timeout) {
                break;
            }
        }

        return;
    }

    // Do not acknowledge END until every expected byte is safely written.
}
            }
        } 
    }

    private void sendAck(UdpTransport socket, InetAddress address, int port, int ackNumber, byte flags) throws IOException {
        Packet ack = new Packet(0, ackNumber, PacketType.ACK, flags, new byte[0]);
        socket.send(PacketIntegrity.encode(ack), address, port);
        stats.packetsSent++;
    }
}
