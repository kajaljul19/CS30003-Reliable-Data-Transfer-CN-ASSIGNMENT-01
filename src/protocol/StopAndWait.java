package protocol;

import common.Packet;
import common.PacketConstants;
import common.PacketIntegrity;
import common.PacketType;
import transport.ReceivedDatagram;
import transport.TransportProtocol;
import transport.UdpTransport;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Stop-and-Wait ARQ (window size = 1, Decision 14), implementing the team's
 * transport.TransportProtocol and using the team's transport.UdpTransport (Decision 8).
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
    /** Decision 12: ACK/DATA flag value meaning "no flag". Kept here so the shared PacketConstants stays untouched. */
    private static final byte FLAG_NONE = 0;

    private final ProtocolConfig cfg;
    private final AdaptiveRto rto;      // team's shared adaptive RTO (Decision 13)
    private final TransferStats stats = new TransferStats();

    // per-transfer state
    private UdpTransport net;
    private InetAddress remoteAddress;
    private int remotePort;

    public StopAndWait(ProtocolConfig cfg) {
    this.cfg = cfg;
    this.rto = new AdaptiveRto(cfg);
}

    /** Metrics of the last transfer (goodput, retransmissions, time) for the experiments. */
    public TransferStats getStats() { return stats; }

    // =====================================================================
    //               helpers: UdpTransport + team's Packet / Checksum
    // =====================================================================
    private static Packet make(int seq, int ack, byte type, byte flags, byte[] data) {
        return new Packet(seq, ack, type, flags, data);
    }

    /** Serialize + CRC-32 using the team's shared common.PacketIntegrity (same wire format as Selective Repeat). */
    private static byte[] seal(Packet p) {
        return PacketIntegrity.encode(p);
    }

    /** Parse + verify. Returns null if malformed or the CRC-32 does not match (corrupted). */
    private static Packet parse(ReceivedDatagram d) {
        if (d == null) return null;
        Packet p = PacketIntegrity.decode(d.getData());   // null if malformed or checksum mismatch
        if (p == null) return null;
        byte t = p.getType();
        if (t < PacketType.DATA || t > PacketType.END) return null;
        return p;
    }

    /** Wait up to timeoutMs for a datagram (0 = forever). Returns null on timeout. */
    private ReceivedDatagram receiveTimed(int timeoutMs) throws IOException {
        net.setTimeout(timeoutMs);
        try {
            return net.receive();
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    private void transmit(byte[] bytes) throws IOException {
        net.send(bytes, remoteAddress, remotePort);
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
    public void sendFile(Path sourceFile, InetAddress receiverAddress, int receiverPort) throws IOException {
        stats.reset();
        long size = Files.size(sourceFile);
        stats.fileBytes = size;

        try (UdpTransport transport = new UdpTransport(0)) {      // 0 = any free local port
            this.net = transport;
            this.remoteAddress = receiverAddress;
            this.remotePort = receiverPort;
            stats.startNanos = System.nanoTime();

            // 1. START (reliable) - metadata payload from Decision 6A
            byte[] meta = buildStartPayload(sourceFile.getFileName().toString(), size);
            sendReliably(make(0, 0, PacketType.START, FLAG_NONE, meta),
                    PacketConstants.FLAG_START, 0);

            // 2. DATA, one packet at a time (Decision 9)
            try (InputStream in = new BufferedInputStream(Files.newInputStream(sourceFile))) {
                byte[] buf = new byte[PacketConstants.MAX_PAYLOAD_SIZE];
                int seq = 0, n;
                while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
                    sendReliably(make(seq, 0, PacketType.DATA, FLAG_NONE, Arrays.copyOf(buf, n)),
                            FLAG_NONE, seq);
                    seq++;                                        // only DATA consumes sequence numbers
                }
            }

            // 3. END (reliable, empty payload)
            sendReliably(make(0, 0, PacketType.END, FLAG_NONE, new byte[0]),
                    PacketConstants.FLAG_END, 0);
            stats.endNanos = System.nanoTime();
        } finally {
            this.net = null;
        }
    }

    /**
     * Send one packet and wait for its matching ACK, retransmitting on timeout.
     * Adaptive RTO (common AdaptiveRto); Karn's algorithm: no RTT sample if the packet was retransmitted.
     */
    private void sendReliably(Packet pkt, byte ackFlags, int ackNum) throws IOException {
        byte[] bytes = seal(pkt);
        int attempts = 0;

        while (true) {
            if (attempts > cfg.maxRetries)
                throw new IOException("Gave up after " + cfg.maxRetries + " retransmissions of " + describe(pkt));

            transmit(bytes);
            stats.packetsSent++;
            if (attempts > 0) stats.retransmissions++;
            boolean retransmitted = attempts > 0;
            attempts++;
            log("TX " + describe(pkt) + (retransmitted ? " (retx)" : "") + "  RTO=" + rto.getRtoMillis() + "ms");

            long sentAt = System.nanoTime();
            long deadline = sentAt + rto.getRtoMillis() * 1_000_000L;

            while (true) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMs <= 0) break;
                ReceivedDatagram d = receiveTimed((int) Math.max(1, remainingMs));
                if (d == null) break;                                    // timeout

                Packet ack = parse(d);
                if (ack == null) { stats.corruptedDropped++; continue; } // corrupted -> ignore

                if (isMatchingAck(ack, ackFlags, ackNum)) {
                    // Karn's algorithm: AdaptiveRto ignores the sample if the packet was retransmitted
                    rto.recordRttSample((System.nanoTime() - sentAt) / 1e6, retransmitted);
                    log("RX " + describe(ack));
                    return;
                }
                // stale / duplicate ACK -> ignore and keep waiting
            }
        }
    }

    private static boolean isMatchingAck(Packet p, byte flags, int ackNum) {
        if (p.getType() != PacketType.ACK || p.getFlags() != flags) return false;
        return flags != FLAG_NONE || p.getAckNumber() == ackNum;
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
    public void receiveFile(Path destinationFile, int localPort) throws IOException {
        stats.reset();
        Path parent = destinationFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (UdpTransport transport = new UdpTransport(localPort)) {
            this.net = transport;
            this.remoteAddress = null;

            // 1. Wait for a valid START
            Packet start = null;
            while (start == null) {
                ReceivedDatagram d = receiveTimed(0);                 // block until something arrives
                Packet p = parse(d);
                if (p == null) { stats.corruptedDropped++; continue; }
                if (p.getType() == PacketType.START) {
                    start = p;
                    rememberSender(d);
                }
            }
            stats.startNanos = System.nanoTime();

            ByteBuffer meta = ByteBuffer.wrap(start.getData());
            int nameLen = meta.getShort() & 0xFFFF;
            byte[] nameBytes = new byte[nameLen];
            meta.get(nameBytes);
            long fileSize = meta.getLong();
            stats.fileBytes = fileSize;

            sendAck(0, PacketConstants.FLAG_START);
            log("Receiving '" + new String(nameBytes, StandardCharsets.UTF_8) + "' (" + fileSize
                    + " bytes) -> " + destinationFile);

            // 2. DATA until END
            int expected = 0;
            long written = 0;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destinationFile))) {
                boolean done = false;
                while (!done) {
                    ReceivedDatagram d = receiveTimed(cfg.receiverIdleTimeoutMs);
                    if (d == null) throw new IOException("Sender silent for " + cfg.receiverIdleTimeoutMs + "ms");

                    Packet p = parse(d);
                    if (p == null) { stats.corruptedDropped++; continue; }  // corrupted -> no ACK, sender retransmits
                    rememberSender(d);
                    log("RX " + describe(p));

                    switch (p.getType()) {
                        case PacketType.START:                        // our START-ACK was lost
                            sendAck(0, PacketConstants.FLAG_START);
                            break;

                        case PacketType.DATA:
                            if (p.getSequenceNumber() == expected) {  // new in-order packet
                                out.write(p.getData());
                                written += p.getData().length;
                                sendAck(p.getSequenceNumber(), FLAG_NONE);
                                expected++;
                            } else if (p.getSequenceNumber() < expected) {   // duplicate -> our ACK was lost
                                sendAck(p.getSequenceNumber(), FLAG_NONE);
                            }                                         // seq > expected impossible in S&W: ignore
                            break;

                        case PacketType.END:
                            sendAck(0, PacketConstants.FLAG_END);
                            done = true;
                            break;

                        default:                                      // stray ACK -> ignore
                    }
                }
            }
            stats.endNanos = System.nanoTime();

            // If our END-ACK is lost the sender retransmits END; stay alive briefly to re-ACK it.
            while (true) {
                ReceivedDatagram d = receiveTimed(cfg.lingerMs);
                if (d == null) break;
                Packet p = parse(d);
                if (p != null && p.getType() == PacketType.END) {
                    rememberSender(d);
                    sendAck(0, PacketConstants.FLAG_END);
                }
            }

            if (written != fileSize)
                throw new IOException("Size mismatch: expected " + fileSize + " bytes, wrote " + written);
        } finally {
            this.net = null;
        }
    }

    private void rememberSender(ReceivedDatagram d) {
        remoteAddress = d.getSenderAddress();
        remotePort = d.getSenderPort();
    }

    private void sendAck(int ackNumber, byte flags) throws IOException {
        transmit(seal(make(0, ackNumber, PacketType.ACK, flags, new byte[0])));
        stats.packetsSent++;
    }

    private void log(String s) { if (cfg.verbose) System.out.println(s); }
}
