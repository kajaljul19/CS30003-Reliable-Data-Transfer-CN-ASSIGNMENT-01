package protocol;

/**
 * Decision 13 - Jacobson/Karels adaptive RTO.
 * Karn's algorithm is applied by the caller (never feed samples from retransmitted packets)
 * plus exponential back-off on timeout, which Karn's algorithm pairs with.
 */
public class RttEstimator {
    private final double minRto, maxRto;
    private boolean first = true;
    private double srtt, rttvar, rto;

    public RttEstimator(ProtocolConfig cfg) {
        this.minRto = cfg.minRtoMs;
        this.maxRto = cfg.maxRtoMs;
        this.rto = cfg.initialRtoMs;
    }

    public void addSample(double rMs) {
        if (first) {
            srtt = rMs;
            rttvar = rMs / 2;
            first = false;
        } else {
            rttvar = 0.75 * rttvar + 0.25 * Math.abs(srtt - rMs);
            srtt   = 0.875 * srtt + 0.125 * rMs;
        }
        rto = clamp(srtt + 4 * rttvar);
    }

    /** Called on timeout: double the RTO (Karn back-off). Reset by the next valid sample. */
    public void backoff() { rto = clamp(rto * 2); }

    public double getRtoMs() { return rto; }
    public double getSrttMs() { return srtt; }

    private double clamp(double v) { return Math.max(minRto, Math.min(maxRto, v)); }
}
