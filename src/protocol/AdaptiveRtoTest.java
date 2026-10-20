
package protocol;

public class AdaptiveRtoTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("PASSED: " + message);
    }

    public static void main(String[] args) {
        AdaptiveRto rto = new AdaptiveRto();

        // Initial RTO
        check(rto.getRtoMillis() == 1000,
                "Initial RTO is 1000 ms");

        // First RTT sample: 200 ms
        rto.recordRttSample(200, false);

        check(rto.getSmoothedRtt() == 200,
                "First RTT sample initializes SRTT");
        check(rto.getRttVariation() == 100,
                "First RTT sample initializes RTTVAR");
        check(rto.getRtoMillis() == 600,
                "RTO is SRTT + 4 * RTTVAR");

        // Retransmitted packet: sample must be ignored
        rto.recordRttSample(400, true);

        check(rto.getSmoothedRtt() == 200,
                "Retransmitted packet RTT sample is ignored");
        check(rto.getRtoMillis() == 600,
                "RTO remains unchanged after ignored sample");

        System.out.println("Adaptive RTO tests completed.");
    }
}
