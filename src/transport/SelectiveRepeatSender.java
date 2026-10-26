package transport;

import common.Packet;
import common.PacketConstants;
import common.PacketIntegrity;
import common.PacketType;
import protocol.AdaptiveRto;
import protocol.SelectiveRepeat;
import protocol.TransferStats;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Selective Repeat file sender over UDP. */
public class SelectiveRepeatSender implements AutoCloseable {
    private final UdpTransport transport;
    private final int windowSize;
    private final AdaptiveRto rto = new AdaptiveRto();
    private final TransferStats stats = new TransferStats();
    public TransferStats getStats() { return stats; }
    private static final int MAX_RETRIES = 20;

    public SelectiveRepeatSender(int localPort) throws IOException {
        this(localPort, 8);
    }

    public SelectiveRepeatSender(int localPort, int windowSize) throws IOException {
        if (windowSize <= 0) throw new IllegalArgumentException("Window size must be positive");
        this.transport = new UdpTransport(localPort);
        this.windowSize = windowSize;
    }

    public void sendFile(Path sourceFile, InetAddress receiverAddress, int receiverPort) throws IOException {
        if (!Files.isRegularFile(sourceFile)) throw new IOException("Source file does not exist: " + sourceFile);
        stats.reset(); stats.startNanos = System.nanoTime(); stats.fileBytes = Files.size(sourceFile);
        if (receiverPort < 1 || receiverPort > 65535) throw new IllegalArgumentException("Invalid receiver port");

        long fileSize = Files.size(sourceFile);
        byte[] name = sourceFile.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        if (name.length > 65535) throw new IOException("Filename is too long");
        ByteBuffer metadata = ByteBuffer.allocate(2 + name.length + 8);
        metadata.putShort((short) name.length).put(name).putLong(fileSize);
        Packet start = new Packet(0, 0, PacketType.START, PacketConstants.FLAG_START, metadata.array());
        exchangeControl(start, PacketConstants.FLAG_START, receiverAddress, receiverPort);

        byte[] file = Files.readAllBytes(sourceFile);
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < file.length; offset += PacketConstants.MAX_PAYLOAD_SIZE) {
            chunks.add(Arrays.copyOfRange(file, offset, Math.min(file.length, offset + PacketConstants.MAX_PAYLOAD_SIZE)));
        }

        SelectiveRepeat window = new SelectiveRepeat(windowSize);
        Packet[] packets = new Packet[chunks.size()];
        long[] firstSent = new long[chunks.size()];
        boolean[] retransmitted = new boolean[chunks.size()];
        int retries = 0;

        while (window.getBase() < chunks.size()) {
            while (window.canSend() && window.getNextSequenceNumber() < chunks.size()) {
                int seq = window.getNextSequenceNumber();
                packets[seq] = new Packet(seq, 0, PacketType.DATA, (byte) 0, chunks.get(seq));
                long now = System.currentTimeMillis();
                window.recordSend(seq, now);
                window.advanceNextSequenceNumber();
                firstSent[seq] = now;
                transport.send(PacketIntegrity.encode(packets[seq]), receiverAddress, receiverPort); stats.packetsSent++;
            }

            int[] expired = window.getTimedOutPackets(System.currentTimeMillis(), rto.getRtoMillis());
            for (int seq : expired) {
                if (++retries > MAX_RETRIES * Math.max(1, chunks.size())) throw new IOException("Too many retransmissions; receiver may be unavailable");
                window.recordRetransmission(seq);
                window.recordRetransmissionSend(seq, System.currentTimeMillis());
                retransmitted[seq] = true;
                transport.send(PacketIntegrity.encode(packets[seq]), receiverAddress, receiverPort); stats.packetsSent++; stats.retransmissions++;
            }

            try {
                transport.setTimeout((int) Math.max(20, Math.min(1000, rto.getRtoMillis())));
                ReceivedDatagram datagram = transport.receive();
                if (!sameEndpoint(datagram, receiverAddress, receiverPort)) continue;
                Packet ack;
                ack = PacketIntegrity.decode(datagram.getData());
                if (ack == null) { stats.corruptedDropped++; continue; }
                if (ack.getType() != PacketType.ACK || ack.getFlags() != 0) continue;
                int seq = ack.getAckNumber();
                if (seq >= window.getBase() && seq < window.getNextSequenceNumber() && !window.isAcknowledged(seq)) {
                    long sentAt = window.getSentTime(seq);
                    if (sentAt >= 0 && !retransmitted[seq]) rto.recordRttSample(System.currentTimeMillis() - sentAt, false);
                    window.recordAcknowledgement(seq);
                }
            } catch (java.net.SocketTimeoutException timeout) {
                // The next loop pass checks each outstanding packet's timer.
            }
        }

        Packet end = new Packet(0, 0, PacketType.END, PacketConstants.FLAG_END, new byte[0]);
        exchangeControl(end, PacketConstants.FLAG_END, receiverAddress, receiverPort);
        stats.endNanos = System.nanoTime();
    }

    private void exchangeControl(Packet packet, byte expectedFlag, InetAddress address, int port) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            long sentAt = System.currentTimeMillis();
            transport.send(PacketIntegrity.encode(packet), address, port); stats.packetsSent++; if (attempt > 0) stats.retransmissions++;
            try {
                transport.setTimeout((int) rto.getRtoMillis());
                ReceivedDatagram datagram = transport.receive();
                if (!sameEndpoint(datagram, address, port)) continue;
                Packet ack;
                ack = PacketIntegrity.decode(datagram.getData());
                if (ack == null) { stats.corruptedDropped++; continue; }
                if (ack.getType() == PacketType.ACK && ack.getFlags() == expectedFlag && ack.getAckNumber() == packet.getSequenceNumber()) {
                    rto.recordRttSample(System.currentTimeMillis() - sentAt, attempt > 0);
                    return;
                }
            } catch (java.net.SocketTimeoutException timeout) {
                // Retransmit control packet.
            }
        }
        throw new IOException("Timed out waiting for receiver acknowledgement");
    }

    private static boolean sameEndpoint(ReceivedDatagram d, InetAddress address, int port) {
        return d.getSenderPort() == port && d.getSenderAddress().equals(address);
    }

    @Override public void close() { transport.close(); }
}
