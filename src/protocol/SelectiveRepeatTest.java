
package protocol;

public class SelectiveRepeatTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("PASSED: " + message);
    }

    public static void main(String[] args) {
        SelectiveRepeat sr = new SelectiveRepeat(4);

        // Test 1: Initial state
        check(sr.getWindowSize() == 4, "Window size is 4");
        check(sr.getBase() == 0, "Initial base is 0");
        check(sr.getNextSequenceNumber() == 0,
                "Initial next sequence number is 0");

        // Test 2: Sending window capacity
        check(sr.canSend(), "Can send when window is empty");

        sr.recordSend(0, 1000);
        sr.advanceNextSequenceNumber();

        check(sr.getNextSequenceNumber() == 1,
                "Next sequence number advances after sending");
        check(sr.getSentTime(0) == 1000,
                "Send timestamp is recorded");

        // Test 3: Individual acknowledgement
        sr.recordAcknowledgement(0);

        check(sr.isAcknowledged(0), "ACK marks packet 0 acknowledged");
        check(sr.getBase() == 1, "Base advances after ACK of packet 0");
        
        // Test 4: Out-of-order acknowledgements
SelectiveRepeat sr2 = new SelectiveRepeat(4);

sr2.recordSend(0, 1000);
sr2.advanceNextSequenceNumber();

sr2.recordSend(1, 1100);
sr2.advanceNextSequenceNumber();

sr2.recordAcknowledgement(1);

check(sr2.isAcknowledged(1),
        "Out-of-order ACK for packet 1 is recorded");
check(sr2.getBase() == 0,
        "Base does not advance while packet 0 is unacknowledged");

sr2.recordAcknowledgement(0);

check(sr2.getBase() == 2,
        "Base advances past both packets after ACK 0");

System.out.println("Out-of-order ACK tests completed.");

// Test 5: Retransmission tracking
SelectiveRepeat sr3 = new SelectiveRepeat(4);

sr3.recordSend(0, 2000);
sr3.advanceNextSequenceNumber();

sr3.recordRetransmission(0);

check(sr3.getRetransmissionCount(0) == 1,
        "Retransmission count increments");
check(sr3.getSentTime(0) == -1,
        "Timestamp resets after retransmission");

System.out.println("Retransmission tests completed.");

// Test: Window slots can be reused safely
SelectiveRepeat sr4 = new SelectiveRepeat(2);

sr4.recordSend(0, 1000);
sr4.advanceNextSequenceNumber();

sr4.recordSend(1, 1100);
sr4.advanceNextSequenceNumber();

sr4.recordAcknowledgement(0);
check(sr4.getBase() == 1, "Base advances to 1");

sr4.recordAcknowledgement(1);
check(sr4.getBase() == 2, "Base advances to 2");

check(sr4.canSend(), "Window allows new packet after advancing");

// Test: Old ACK must not acknowledge a new packet
SelectiveRepeat sr5 = new SelectiveRepeat(2);

sr5.recordSend(0, 1000);
sr5.advanceNextSequenceNumber();
sr5.recordSend(1, 1100);
sr5.advanceNextSequenceNumber();

sr5.recordAcknowledgement(0);
sr5.recordAcknowledgement(1);

sr5.recordSend(2, 1200);
sr5.advanceNextSequenceNumber();

check(!sr5.isAcknowledged(2),
        "New packet starts unacknowledged after slot reuse");
check(sr5.getSentTime(2) == 1200,
        "New packet has its own send timestamp");
        
        System.out.println("Initial tests completed.");
    }
}
