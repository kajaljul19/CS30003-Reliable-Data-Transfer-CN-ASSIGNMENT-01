
package protocol;

/** Configurable parameters shared by the transport protocols. */
public class ProtocolConfig {
    public int windowSize = 8;
    public int maxRetries = 15;
    public int receiverIdleTimeoutMs = 30_000;
    public int lingerMs = 2_000;
    public boolean verbose = false;
    public double minRtoMs = 100.0;
public double maxRtoMs = 30000.0;
public double initialRtoMs = 500.0;
}
