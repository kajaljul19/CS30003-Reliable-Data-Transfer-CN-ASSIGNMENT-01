
package protocol;

public class AdaptiveRto {

    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

    private static final double MIN_RTO_MS = 100.0;
    private static final double MAX_RTO_MS = 60000.0;

    private double smoothedRtt;
    private double rttVariation;
    private double rto;

    private boolean initialized;

    public AdaptiveRto() {
        this.rto = 1000.0;
        this.smoothedRtt = 0.0;
        this.rttVariation = 0.0;
        this.initialized = false;
    }

    public void recordRttSample(double sampleMs,
                                boolean wasRetransmitted) {

        // Karn's algorithm: ignore ambiguous RTT samples.
        if (wasRetransmitted || sampleMs <= 0) {
            return;
        }

        if (!initialized) {
            smoothedRtt = sampleMs;
            rttVariation = sampleMs / 2.0;
            initialized = true;
        } else {
            rttVariation =
                (1.0 - BETA) * rttVariation
                + BETA * Math.abs(smoothedRtt - sampleMs);

            smoothedRtt =
                (1.0 - ALPHA) * smoothedRtt
                + ALPHA * sampleMs;
        }

        rto = smoothedRtt + 4.0 * rttVariation;

        rto = Math.max(MIN_RTO_MS,
                       Math.min(MAX_RTO_MS, rto));
    }

    public long getRtoMillis() {
        return Math.round(rto);
    }

    public double getSmoothedRtt() {
        return smoothedRtt;
    }

    public double getRttVariation() {
        return rttVariation;
    }
}
