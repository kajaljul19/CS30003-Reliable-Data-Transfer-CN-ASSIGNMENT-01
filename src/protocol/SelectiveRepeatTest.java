
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

        // Test: Reject a sequence number that was never sent.
SelectiveRepeat sr6 = new SelectiveRepeat(2);

try {
    sr6.getSentTime(0);
    throw new AssertionError(
        "Unsent sequence number was accepted"
    );
} catch (IllegalArgumentException e) {
    check(true, "Unsent sequence number is rejected");
}

// Test: Reject a negative sequence number.
try {
    sr6.getRetransmissionCount(-1);
    throw new AssertionError(
        "Negative sequence number was accepted"
    );
} catch (IllegalArgumentException e) {
    check(true, "Negative sequence number is rejected");
}
     // Test: A delayed ACK for an old packet must be ignored.
SelectiveRepeat sr7 = new SelectiveRepeat(2);

sr7.recordSend(0, 1000);
sr7.advanceNextSequenceNumber();

sr7.recordSend(1, 1100);
sr7.advanceNextSequenceNumber();

sr7.recordAcknowledgement(0);
sr7.recordAcknowledgement(1);

// Slot 0 can now be reused by packet 2.
sr7.recordSend(2, 1200);
sr7.advanceNextSequenceNumber();

// Simulate a delayed duplicate ACK for packet 0.
sr7.recordAcknowledgement(0);

check(!sr7.isAcknowledged(2),
        "Delayed ACK for packet 0 does not acknowledge packet 2");
check(sr7.getSentTime(2) == 1200,
        "Delayed ACK does not change packet 2 timestamp");

        // Test: Reject a packet sent out of sequence.
SelectiveRepeat sr8 = new SelectiveRepeat(2);

try {
    sr8.recordSend(1, 1000);
    throw new AssertionError(
        "Out-of-sequence packet was accepted"
    );
} catch (IllegalArgumentException e) {
    check(true, "Out-of-sequence packet is rejected");
}

// Test: Reject a new packet when the window is full.
SelectiveRepeat sr9 = new SelectiveRepeat(2);

sr9.recordSend(0, 1000);
sr9.advanceNextSequenceNumber();

sr9.recordSend(1, 1100);
sr9.advanceNextSequenceNumber();

try {
    sr9.recordSend(2, 1200);
    throw new AssertionError(
        "Packet was accepted when the window was full"
    );
} catch (IllegalStateException e) {
    check(true, "New packet is rejected when window is full");
}
  
        // Test: Packets are detected only after their timeout expires.
        SelectiveRepeat sr10 = new SelectiveRepeat(4);

        sr10.recordSend(0, 1000);
        sr10.advanceNextSequenceNumber();

        check(sr10.getTimedOutPackets(1099, 100) .length == 0,
                "Packet is not timed out before RTO expires");

        int[] timedOut = sr10.getTimedOutPackets(1100, 100);

        check(timedOut.length == 1 && timedOut[0] == 0,
                "Packet is timed out when RTO expires");

        // Test: Acknowledged packets must not time out.
        sr10.recordAcknowledgement(0);

        check(sr10.getTimedOutPackets(2000, 100).length == 0,
                "Acknowledged packet is not reported as timed out");
             // Test: Outstanding packets time out independently.
        SelectiveRepeat sr11 = new SelectiveRepeat(4);

        sr11.recordSend(0, 1000);
        sr11.advanceNextSequenceNumber();

        sr11.recordSend(1, 1050);
        sr11.advanceNextSequenceNumber();

        // At time 1100, only packet 0 has reached its 100 ms timeout.
        int[] timedOutFirst = sr11.getTimedOutPackets(1100, 100);

        check(timedOutFirst.length == 1 && timedOutFirst[0] == 0,
                "Only the first expired packet is timed out");

        // At time 1150, both packets have reached their timeout.
        int[] timedOutBoth = sr11.getTimedOutPackets(1150, 100);

        check(timedOutBoth.length == 2
                        && timedOutBoth[0] == 0
                        && timedOutBoth[1] == 1,
                "Each outstanding packet times out independently");

                 // Test: A retransmitted packet can time out again.
        SelectiveRepeat sr12 = new SelectiveRepeat(2);

        sr12.recordSend(0, 1000);
        sr12.advanceNextSequenceNumber();

        // The original transmission times out at 1100.
        check(sr12.getTimedOutPackets(1100, 100)[0] == 0,
                "Original transmission times out");

        // Record the retransmission and its new send time.
        sr12.recordRetransmission(0);
        sr12.recordRetransmissionSend(0, 1200);

        check(sr12.getRetransmissionCount(0) == 1,
                "Retransmission count remains correct");

        check(sr12.getSentTime(0) == 1200,
                "Retransmission timestamp is recorded");

        check(sr12.getTimedOutPackets(1299, 100).length == 0,
                "Retransmitted packet does not time out too early");

        int[] timedOutAgain = sr12.getTimedOutPackets(1300, 100);

        check(timedOutAgain.length == 1 && timedOutAgain[0] == 0,
                "Retransmitted packet can time out again");
        // Test: Selective Repeat uses the adaptive RTO value.
        SelectiveRepeat sr13 = new SelectiveRepeat(2);
        AdaptiveRto adaptiveRto = new AdaptiveRto();

        sr13.recordSend(0, 1000);
        sr13.advanceNextSequenceNumber();

        check(adaptiveRto.getRtoMillis() == 1000,
                "Adaptive RTO starts at 1000 ms");

        check(sr13.getTimedOutPackets(
                        1500, adaptiveRto.getRtoMillis()).length == 0,
                "Packet does not time out before adaptive RTO");

        adaptiveRto.recordRttSample(50, false);

        check(adaptiveRto.getRtoMillis() == 150,
                "Adaptive RTO updates from RTT sample");

        check(sr13.getTimedOutPackets(
                        1149, adaptiveRto.getRtoMillis()).length == 0,
                "Packet does not time out before updated RTO");

        int[] adaptiveTimeout = sr13.getTimedOutPackets(
                1150, adaptiveRto.getRtoMillis());

        check(adaptiveTimeout.length == 1 && adaptiveTimeout[0] == 0,
                "Selective Repeat uses the updated adaptive RTO");

      System.out.println("Initial tests completed.");
    }
}
