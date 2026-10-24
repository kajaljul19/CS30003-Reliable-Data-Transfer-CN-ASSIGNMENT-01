package protocol;

/** Metrics needed for the experiments (goodput, retransmission count, transfer time). */
public class TransferStats {
    public long packetsSent;        // every transmission, including retransmissions
    public long retransmissions;
    public long corruptedDropped;   // packets discarded because CRC-32 failed
    public long fileBytes;
    public long startNanos;
    public long endNanos;

    public void reset() {
        packetsSent = retransmissions = corruptedDropped = fileBytes = 0;
        startNanos = endNanos = 0;
    }

    public double seconds() { return (endNanos - startNanos) / 1e9; }

    /** Goodput = useful file bytes delivered per second (bytes/s). */
    public double goodputBytesPerSec() {
        double s = seconds();
        return s > 0 ? fileBytes / s : 0;
    }

    @Override
    public String toString() {
        return String.format("time=%.3fs  goodput=%.1f KB/s  packetsSent=%d  retransmissions=%d  corruptDropped=%d",
                seconds(), goodputBytesPerSec() / 1024.0, packetsSent, retransmissions, corruptedDropped);
    }
}