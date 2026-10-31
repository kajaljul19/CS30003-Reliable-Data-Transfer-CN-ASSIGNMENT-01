
package protocol;

public class AdaptiveRto {

    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

    private final double minRtoMs;
    private final double maxRtoMs;

    private double smoothedRtt;
    private double rttVariation;
    private double rto;

    private boolean initialized;

    // Default settings, retained for existing code.
    public AdaptiveRto() {
        this(1000.0, 100.0, 30000.0);
    }

    // Configurable settings from ProtocolConfig.
    public AdaptiveRto(ProtocolConfig cfg) {
        this(cfg.initialRtoMs, cfg.minRtoMs, cfg.maxRtoMs);
    }

    public AdaptiveRto(double initialRtoMs,
                       double minRtoMs,
                       double maxRtoMs) {

        if (minRtoMs <= 0 || maxRtoMs < minRtoMs) {
            throw new IllegalArgumentException("Invalid RTO limits");
        }

        this.minRtoMs = minRtoMs;
        this.maxRtoMs = maxRtoMs;
        this.rto = Math.max(minRtoMs,
                Math.min(maxRtoMs, initialRtoMs));

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

        rto = Math.max(minRtoMs,
                       Math.min(maxRtoMs, rto));
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
