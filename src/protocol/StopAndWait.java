package protocol;

import common.Checksum;
import common.Packet;
import common.PacketConstants;
import common.PacketType;
import network.UdpNetwork;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Stop-and-Wait ARQ (window size = 1, Decision 14).
 *
 * Flow (Decision 5):  START -> DATA(seq 0..n-1) -> END, each reliably ACKed.
 * ACK rules (Decisions 4, 11):
 *   ACK + FLAG_NONE  : ACK N means DATA N was received
 *   ACK + FLAG_START : START acknowledged
 *   ACK + FLAG_END   : END acknowledged
 * START/END do not consume sequence numbers (Decision 3); their seq field is 0.
 */
public class StopAndWait implements TransportProtocol {
    public static final int WINDOW_SIZE = 1;

    private final UdpNetwork net;
    private final ProtocolConfig cfg;
    private final RttEstimator rtt;
    private final TransferStats stats = new TransferStats();

    public StopAndWait(UdpNetwork net, ProtocolConfig cfg) {
        this.net = net;
        this.cfg = cfg;
        this.rtt = new RttEstimator(cfg);
    }

    @Override
    public TransferStats getStats() { return stats; }

    // =====================================================================
    //                    packet helpers (use the team's Packet)
    // =====================================================================
    private static Packet make(int seq, int ack, byte type, byte flags, byte[] data) {
        return new Packet(seq, ack, type, flags, data);
    }

    /** Fill in the CRC-32: computed over the whole packet with the checksum field = 0. */
    private static byte[] seal(Packet p) {
        p.setChecksum(0);
        p.setChecksum(Checksum.calculate(p.toBytes()));
        return p.toBytes();
    }

    /** Parse + verify. Returns null if malformed or the CRC-32 does not match (corrupted). */
    private static Packet parse(byte[] raw) {
        if (raw == null) return null;
        try {
            Packet p = Packet.fromBytes(raw);          // throws IllegalArgumentException if malformed
            int received = p.getChecksum();
            p.setChecksum(0);
            int computed = Checksum.calculate(p.toBytes());
            p.setChecksum(received);
            if (computed != received) return null;
            byte t = p.getType();
            if (t < PacketType.DATA || t > PacketType.END) return null;
            return p;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String describe(Packet p) {
        String[] names = {"?", "DATA", "ACK", "START", "END"};
        return names[p.getType()] + "[seq=" + p.getSequenceNumber() + ", ack=" + p.getAckNumber()
                + ", flags=" + p.getFlags() + ", len=" + p.getDataLength() + "]";
    }

    // =====================================================================
    //                               SENDER
    // =====================================================================
    @Override
    public void send(Path file) throws IOException {
        stats.reset();
        long size = Files.size(file);
        stats.fileBytes = size;
        stats.startNanos = System.nanoTime();

        // 1. START (reliable) - metadata payload from Decision 6A
        byte[] meta = buildStartPayload(file.getFileName().toString(), size);
        sendReliably(make(0, 0, PacketType.START, PacketConstants.FLAG_NONE, meta),
                PacketConstants.FLAG_START, 0);

        // 2. DATA, one packet at a time (Decision 9)
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buf = new byte[PacketConstants.MAX_PAYLOAD_SIZE];
            int seq = 0, n;
            while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
                sendReliably(make(seq, 0, PacketType.DATA, PacketConstants.FLAG_NONE, Arrays.copyOf(buf, n)),
                        PacketConstants.FLAG_NONE, seq);
                seq++;                                   // only DATA consumes sequence numbers
            }
        }

        // 3. END (reliable, empty payload)
        sendReliably(make(0, 0, PacketType.END, PacketConstants.FLAG_NONE, new byte[0]),
                PacketConstants.FLAG_END, 0);
        stats.endNanos = System.nanoTime();
    }

    /**
     * Send one packet and wait for its matching ACK, retransmitting on timeout.
     * Adaptive RTO; Karn's algorithm: no RTT sample if the packet was retransmitted.
     */
    private void sendReliably(Packet pkt, byte ackFlags, int ackNum) throws IOException {
        byte[] bytes = seal(pkt);
        int attempts = 0;

        while (true) {
            if (attempts > cfg.maxRetries)
                throw new IOException("Gave up after " + cfg.maxRetries + " retransmissions of " + describe(pkt));

            net.send(bytes);
            stats.packetsSent++;
            if (attempts > 0) stats.retransmissions++;
            boolean retransmitted = attempts > 0;
            attempts++;
            log("TX " + describe(pkt) + (retransmitted ? " (retx)" : "") + "  RTO=" + (long) rtt.getRtoMs() + "ms");

            long sentAt = System.nanoTime();
            long deadline = sentAt + (long) (rtt.getRtoMs() * 1_000_000);

            while (true) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMs <= 0) break;
                byte[] raw = net.receive((int) Math.max(1, remainingMs));
                if (raw == null) break;                                   // timeout

                Packet ack = parse(raw);
                if (ack == null) { stats.corruptedDropped++; continue; }  // corrupted -> ignore

                if (isMatchingAck(ack, ackFlags, ackNum)) {
                    if (!retransmitted)                                   // Karn's algorithm
                        rtt.addSample((System.nanoTime() - sentAt) / 1e6);
                    log("RX " + describe(ack));
                    return;
                }
                // stale / duplicate ACK -> ignore and keep waiting
            }
            rtt.backoff();                                                // timeout -> retransmit
        }
    }

    private static boolean isMatchingAck(Packet p, byte flags, int ackNum) {
        if (p.getType() != PacketType.ACK || p.getFlags() != flags) return false;
        return flags != PacketConstants.FLAG_NONE || p.getAckNumber() == ackNum;
    }

    /** Decision 6A: 2-byte filename length + filename bytes + 8-byte file size. */
    static byte[] buildStartPayload(String filename, long fileSize) {
        byte[] name = filename.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(2 + name.length + 8);
        b.putShort((short) name.length);
        b.put(name);
        b.putLong(fileSize);
        return b.array();
    }

    // =====================================================================
    //                              RECEIVER
    // =====================================================================
    @Override
    public Path receive(Path outputDir) throws IOException {
        stats.reset();
        Files.createDirectories(outputDir);

        // 1. Wait for a valid START
        Packet start;
        while (true) {
            Packet p = parse(net.receive(0));                    // block until something arrives
            if (p == null) { stats.corruptedDropped++; continue; }
            if (p.getType() == PacketType.START) { start = p; break; }
        }
        stats.startNanos = System.nanoTime();

        ByteBuffer meta = ByteBuffer.wrap(start.getData());
        int nameLen = meta.getShort() & 0xFFFF;
        byte[] nameBytes = new byte[nameLen];
        meta.get(nameBytes);
        long fileSize = meta.getLong();
        stats.fileBytes = fileSize;

        // keep only the base name so a sender can't write outside outputDir
        String filename = Paths.get(new String(nameBytes, StandardCharsets.UTF_8)).getFileName().toString();
        Path outFile = outputDir.resolve(filename);

        sendAck(0, PacketConstants.FLAG_START);
        log("Receiving '" + filename + "' (" + fileSize + " bytes)");

        // 2. DATA until END
        int expected = 0;
        long written = 0;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outFile))) {
            boolean done = false;
            while (!done) {
                byte[] raw = net.receive(cfg.receiverIdleTimeoutMs);
                if (raw == null) throw new IOException("Sender silent for " + cfg.receiverIdleTimeoutMs + "ms");

                Packet p = parse(raw);
                if (p == null) { stats.corruptedDropped++; continue; }  // corrupted -> no ACK, sender retransmits
                log("RX " + describe(p));

                switch (p.getType()) {
                    case PacketType.START:                       // our START-ACK was lost
                        sendAck(0, PacketConstants.FLAG_START);
                        break;

                    case PacketType.DATA:
                        if (p.getSequenceNumber() == expected) { // new in-order packet
                            out.write(p.getData());
                            written += p.getData().length;
                            sendAck(p.getSequenceNumber(), PacketConstants.FLAG_NONE);
                            expected++;
                        } else if (p.getSequenceNumber() < expected) {  // duplicate -> our ACK was lost
                            sendAck(p.getSequenceNumber(), PacketConstants.FLAG_NONE);
                        }                                        // seq > expected impossible in S&W: ignore
                        break;

                    case PacketType.END:
                        sendAck(0, PacketConstants.FLAG_END);
                        done = true;
                        break;

                    default:                                     // stray ACK -> ignore
                }
            }
        }
        stats.endNanos = System.nanoTime();

        // If our END-ACK is lost the sender retransmits END; stay alive briefly to re-ACK it.
        while (true) {
            byte[] raw = net.receive(cfg.lingerMs);
            if (raw == null) break;
            Packet p = parse(raw);
            if (p != null && p.getType() == PacketType.END) sendAck(0, PacketConstants.FLAG_END);
        }

        if (written != fileSize)
            throw new IOException("Size mismatch: expected " + fileSize + " bytes, wrote " + written);
        return outFile;
    }

    private void sendAck(int ackNumber, byte flags) throws IOException {
        net.send(seal(make(0, ackNumber, PacketType.ACK, flags, new byte[0])));
        stats.packetsSent++;
    }

    private void log(String s) { if (cfg.verbose) System.out.println(s); }
}
