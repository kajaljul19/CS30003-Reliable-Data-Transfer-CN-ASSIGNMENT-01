package protocol;

/** Decision 16 - protocol parameters are configurable, not hard-coded. */
public class ProtocolConfig {
    public double initialRtoMs = 1000;   // used until the first RTT sample
    public double minRtoMs     = 50;
    public double maxRtoMs     = 8000;
    public int    maxRetries   = 15;     // retransmissions per packet before giving up
    public int    receiverIdleTimeoutMs = 30_000; // receiver gives up if sender is silent
    public int    lingerMs     = 2000;   // receiver keeps re-ACKing END after finishing (lost END-ACK)
    public boolean verbose     = false;
    public int windowSize = 8;
}
