package protocol;

/** Decision 16 - protocol parameters are configurable, not hard-coded. */
public class ProtocolConfig {
    public int    windowSize   = 8;      // Go-Back-N window N (same default as Selective Repeat); Stop-and-Wait ignores it
    public int    maxRetries   = 15;     // retransmissions / consecutive timeouts before giving up
    public int    receiverIdleTimeoutMs = 30_000; // receiver gives up if sender is silent
    public int    lingerMs     = 2000;   // receiver keeps re-ACKing END after finishing (lost END-ACK)
    public boolean verbose     = false;
}
