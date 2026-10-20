
package protocol;

import java.util.Arrays;

public class SelectiveRepeat {

    private final int windowSize;
    private final boolean[] acknowledged;
    private final long[] sentTime;
    private final int[] retransmissionCount;

    private int base;
    private int nextSequenceNumber;

    public SelectiveRepeat(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException(
                "Window size must be positive"
            );
        }

        this.windowSize = windowSize;
        this.acknowledged = new boolean[windowSize];
        this.sentTime = new long[windowSize];
        this.retransmissionCount = new int[windowSize];

        Arrays.fill(sentTime, -1L);

        this.base = 0;
        this.nextSequenceNumber = 0;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getBase() {
        return base;
    }

    public int getNextSequenceNumber() {
        return nextSequenceNumber;
    }

    public boolean canSend() {
        return nextSequenceNumber < base + windowSize;
    }

    public void recordSend(int sequenceNumber, long timestamp) {
        int index = sequenceNumber % windowSize;

        sentTime[index] = timestamp;
        acknowledged[index] = false;
    }

    public void recordAcknowledgement(int sequenceNumber) {
        if (sequenceNumber < base ||
            sequenceNumber >= nextSequenceNumber) {
            return;
        }

        int index = sequenceNumber % windowSize;
        acknowledged[index] = true;

        while (base < nextSequenceNumber &&
               acknowledged[base % windowSize]) {
            acknowledged[base % windowSize] = false;
            sentTime[base % windowSize] = -1L;
            retransmissionCount[base % windowSize] = 0;
            base++;
        }
    }

    public boolean isAcknowledged(int sequenceNumber) {
    if (sequenceNumber < base) {
        return true;
    }

    if (sequenceNumber >= nextSequenceNumber) {
        return false;
    }

    return acknowledged[sequenceNumber % windowSize];
}

    public long getSentTime(int sequenceNumber) {
        return sentTime[sequenceNumber % windowSize];
    }

    public void recordRetransmission(int sequenceNumber) {
        retransmissionCount[sequenceNumber % windowSize]++;
        sentTime[sequenceNumber % windowSize] = -1L;
    }

    public int getRetransmissionCount(int sequenceNumber) {
        return retransmissionCount[sequenceNumber % windowSize];
    }

    public void advanceNextSequenceNumber() {
        if (canSend()) {
            nextSequenceNumber++;
        }
    }
}
