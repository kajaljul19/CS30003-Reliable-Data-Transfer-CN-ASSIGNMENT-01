
package protocol;

/** Configurable parameters shared by the transport protocols. */
public class ProtocolConfig {
    public int windowSize = 8;
    public int maxRetries = 15;
    public int receiverIdleTimeoutMs = 30_000;
    public int lingerMs = 2_000;
    public boolean verbose = false;
}
