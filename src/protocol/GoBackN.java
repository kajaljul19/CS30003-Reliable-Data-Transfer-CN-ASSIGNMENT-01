package protocol;

import common.Packet;
import common.PacketConstants;
import common.PacketIntegrity;
import common.PacketType;
import transport.ReceivedDatagram;
import transport.TransportProtocol;
import transport.UdpTransport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Go-Back-N ARQ over the team's transport.UdpTransport, implementing transport.TransportProtocol.
 *
 * Flow (same as Stop-and-Wait):  START -> DATA(seq 0..n-1) -> END, START and END are reliably ACKed.
 *
 * Sender: up to cfg.windowSize unacknowledged DATA packets in flight, ONE timer for the oldest
 *         unacknowledged packet (base). On timeout it goes back and resends every packet from base up to
 *         nextSeq-1. ACKs are cumulative.
 * Receiver: accepts only the next expected in-order packet; anything else is discarded and the last
 *         in-order ACK is repeated.
 *
 * ACK rules:
 *   ACK + FLAG_NONE  : cumulative - "ACK N" means DATA 0..N have all been received in order
 *   ACK + FLAG_START : START acknowledged
 *   ACK + FLAG_END   : END acknowledged
 * START/END do not consume sequence numbers; their seq field is 0.
 *
 * The RTO comes from the team's shared AdaptiveRto (Karn's algorithm is applied by recordRttSample).
 */
public class GoBackN implements TransportProtocol {
    private static final byte FLAG_NONE = 0;

    private final ProtocolConfig cfg;
    private final AdaptiveRto rto = new AdaptiveRto();
    private final TransferStats stats = new TransferStats();

    // per-transfer state
    private UdpTransport net;
    private InetAddress remoteAddress;
    private int remotePort;

    public GoBackN(ProtocolConfig cfg) {
        if (cfg.windowSize < 1) throw new IllegalArgumentException("windowSize must be >= 1");
        this.cfg = cfg;
    }

    /** Metrics of the last transfer (goodput, retransmissions, time) for the experiments. */
    public TransferStats getStats() { return stats; }

    // =====================================================================
    //                              helpers
    // =====================================================================
    private static Packet make(int seq, int ack, byte type, byte flags, byte[] data) {
        return new Packet(seq, ack, type, flags, data);
    }

    /** Serialize + CRC-32 with the team's shared PacketIntegrity (same wire format as Selective Repeat). */
    private static byte[] seal(Packet p) {
        return PacketIntegrity.encode(p);
    }

    /** Parse + verify. Returns null if malformed or the CRC-32 does not match (corrupted). */
    private static Packet parse(ReceivedDatagram d) {
        if (d == null) return null;
        Packet p = PacketIntegrity.decode(d.getData());
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
        stats.packetsSent++;
    }

    private static String describe(Packet p) {
        String[] names = {"?", "DATA", "ACK", "START", "END"};
        return names[p.getType()] + "[seq=" + p.getSequenceNumber() + ", ack=" + p.getAckNumber()
                + ", flags=" + p.getFlags() + ", len=" + p.getDataLength() + "]";
    }

    private void log(String s) { if (cfg.verbose) System.out.println(s); }

    // =====================================================================
    //                               SENDER
    // =====================================================================
    @Override
    public void sendFile(Path sourceFile, InetAddress receiverAddress, int receiverPort) throws IOException {
        stats.reset();
        long size = Files.size(sourceFile);
        stats.fileBytes = size;
        int total = (int) ((size + PacketConstants.MAX_PAYLOAD_SIZE - 1) / PacketConstants.MAX_PAYLOAD_SIZE);
        final int window = cfg.windowSize;

        try (UdpTransport transport = new UdpTransport(0)) {      // 0 = any free local port
            this.net = transport;
            this.remoteAddress = receiverAddress;
            this.remotePort = receiverPort;
            stats.startNanos = System.nanoTime();

            // 1. START (reliable) - metadata: 2-byte filename length + filename + 8-byte file size
            byte[] meta = buildStartPayload(sourceFile.getFileName().toString(), size);
            sendControl(make(0, 0, PacketType.START, FLAG_NONE, meta), PacketConstants.FLAG_START);

            // 2. DATA with a sliding window of size N
            try (InputStream in = new BufferedInputStream(Files.newInputStream(sourceFile))) {
                byte[][] payload = new byte[window][];     // payload of each in-flight packet, slot = seq % window
                long[] sentAt = new long[window];          // time of the last transmission (nanos)
                boolean[] retransmitted = new boolean[window];

                int base = 0;          // oldest unacknowledged sequence number
                int next = 0;          // next sequence number to send for the first time
                long timerStart = 0;   // the single timer: runs for the packet 'base'
                int timeoutsWithoutProgress = 0;

                while (base < total) {
                    // fill the window
                    while (next < total && next < base + window) {
                        int slot = next % window;
                        payload[slot] = in.readNBytes(PacketConstants.MAX_PAYLOAD_SIZE);
                        retransmitted[slot] = false;
                        sendData(next, payload[slot]);
                        sentAt[slot] = System.nanoTime();
                        if (base == next) timerStart = sentAt[slot];   // window was empty: start the timer
                        next++;
                    }

                    long remainingMs = (timerStart + rto.getRtoMillis() * 1_000_000L - System.nanoTime()) / 1_000_000L;
                    ReceivedDatagram d = remainingMs > 0 ? receiveTimed((int) remainingMs) : null;

                    if (d != null) {
                        Packet ack = parse(d);
                        if (ack == null) { stats.corruptedDropped++; continue; }       // corrupted -> ignore
                        if (ack.getType() == PacketType.ACK && ack.getFlags() == FLAG_NONE) {
                            int a = ack.getAckNumber();                                 // cumulative ACK
                            if (a >= base && a < next) {
                                int slot = a % window;
                                // Karn's algorithm is applied inside AdaptiveRto (ignored if retransmitted)
                                rto.recordRttSample((System.nanoTime() - sentAt[slot]) / 1e6, retransmitted[slot]);
                                log("RX " + describe(ack));
                                base = a + 1;                                           // window slides forward
                                timeoutsWithoutProgress = 0;
                                timerStart = System.nanoTime();                         // restart timer for new base
                            }
                            // else: old / duplicate ACK -> ignore
                        }
                        continue;
                    }

                    // timeout: go back N - resend every unacknowledged packet from base to next-1
                    if (++timeoutsWithoutProgress > cfg.maxRetries)
                        throw new IOException("Gave up after " + cfg.maxRetries + " timeouts waiting for ACK of seq " + base);
                    log("TIMEOUT base=" + base + " -> resending " + base + ".." + (next - 1)
                            + "  RTO=" + rto.getRtoMillis() + "ms");
                    for (int s = base; s < next; s++) {
                        int slot = s % window;
                        sendData(s, payload[slot]);
                        retransmitted[slot] = true;
                        sentAt[slot] = System.nanoTime();
                        stats.retransmissions++;
                    }
                    timerStart = System.nanoTime();
                }
            }

            // 3. END (reliable, empty payload)
            sendControl(make(0, 0, PacketType.END, FLAG_NONE, new byte[0]), PacketConstants.FLAG_END);
            stats.endNanos = System.nanoTime();
        } finally {
            this.net = null;
        }
    }

    private void sendData(int seq, byte[] data) throws IOException {
        Packet p = make(seq, 0, PacketType.DATA, FLAG_NONE, data);
        transmit(seal(p));
        log("TX " + describe(p));
    }

    /** Send START or END and wait for the matching ACK flag, retransmitting on timeout. */
    private void sendControl(Packet pkt, byte ackFlag) throws IOException {
        byte[] bytes = seal(pkt);
        for (int attempt = 0; attempt <= cfg.maxRetries; attempt++) {
            transmit(bytes);
            if (attempt > 0) stats.retransmissions++;
            log("TX " + describe(pkt) + (attempt > 0 ? " (retx)" : ""));

            long sentAt = System.nanoTime();
            long deadline = sentAt + rto.getRtoMillis() * 1_000_000L;
            while (true) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0) break;
                ReceivedDatagram d = receiveTimed((int) remainingMs);
                if (d == null) break;                                            // timeout
                Packet ack = parse(d);
                if (ack == null) { stats.corruptedDropped++; continue; }
                if (ack.getType() == PacketType.ACK && ack.getFlags() == ackFlag) {
                    rto.recordRttSample((System.nanoTime() - sentAt) / 1e6, attempt > 0);
                    log("RX " + describe(ack));
                    return;
                }
                // stale data ACK -> ignore and keep waiting
            }
        }
        throw new IOException("Gave up after " + cfg.maxRetries + " retransmissions of " + describe(pkt));
    }

    /** 2-byte filename length + filename bytes + 8-byte file size. */
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
            int expectedPackets = (int) ((fileSize + PacketConstants.MAX_PAYLOAD_SIZE - 1) / PacketConstants.MAX_PAYLOAD_SIZE);

            sendAck(0, PacketConstants.FLAG_START);
            log("Receiving '" + new String(nameBytes, StandardCharsets.UTF_8) + "' (" + fileSize
                    + " bytes) -> " + destinationFile);

            // 2. DATA until END
            int expected = 0;          // next in-order sequence number
            long written = 0;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destinationFile))) {
                boolean done = false;
                while (!done) {
                    ReceivedDatagram d = receiveTimed(cfg.receiverIdleTimeoutMs);
                    if (d == null) throw new IOException("Sender silent for " + cfg.receiverIdleTimeoutMs + "ms");

                    Packet p = parse(d);
                    if (p == null) { stats.corruptedDropped++; continue; }   // corrupted -> drop, sender times out
                    rememberSender(d);
                    log("RX " + describe(p));

                    switch (p.getType()) {
                        case PacketType.START:                        // our START-ACK was lost
                            sendAck(0, PacketConstants.FLAG_START);
                            break;

                        case PacketType.DATA:
                            if (p.getSequenceNumber() == expected && expected < expectedPackets) {
                                out.write(p.getData());               // in order: deliver
                                written += p.getData().length;
                                expected++;
                                sendAck(expected - 1, FLAG_NONE);     // cumulative ACK
                            } else if (expected > 0) {
                                // out of order or duplicate: discard, repeat the last in-order ACK
                                sendAck(expected - 1, FLAG_NONE);
                            }
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
    }
}
