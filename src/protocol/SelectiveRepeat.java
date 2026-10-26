
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
    if (sequenceNumber != nextSequenceNumber) {
        throw new IllegalArgumentException(
            "Packets must be sent in sequence order"
        );
    }

    if (!canSend()) {
        throw new IllegalStateException(
            "Sending window is full"
        );
    }

    int index = sequenceNumber % windowSize;

    sentTime[index] = timestamp;
    acknowledged[index] = false;
    retransmissionCount[index] = 0;
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
    if (sequenceNumber < 0 ||
        sequenceNumber >= nextSequenceNumber) {
        throw new IllegalArgumentException(
            "Invalid sequence number"
        );
    }

    return sentTime[sequenceNumber % windowSize];
}

    public void recordRetransmission(int sequenceNumber) {
    if (sequenceNumber < base ||
        sequenceNumber >= nextSequenceNumber) {
        throw new IllegalArgumentException(
            "Sequence number is not outstanding"
        );
    }

    int index = sequenceNumber % windowSize;
    retransmissionCount[index]++;
    sentTime[index] = -1L;
}

    public void recordRetransmissionSend(int sequenceNumber, long timestamp) {
        if (sequenceNumber < base || sequenceNumber >= nextSequenceNumber) {
            throw new IllegalArgumentException(
                    "Sequence number is not outstanding");
        }

        int index = sequenceNumber % windowSize;

        if (acknowledged[index]) {
            throw new IllegalStateException(
                    "Cannot retransmit an acknowledged packet");
        }

        sentTime[index] = timestamp;
    }


public int getRetransmissionCount(int sequenceNumber) {
    if (sequenceNumber < 0 ||
        sequenceNumber >= nextSequenceNumber) {
        throw new IllegalArgumentException(
            "Invalid sequence number"
        );
    }

    return retransmissionCount[sequenceNumber % windowSize];
}

    public void advanceNextSequenceNumber() {
        if (canSend()) {
            nextSequenceNumber++;
        }
    }
    public int[] getTimedOutPackets(long currentTime, long rtoMillis) {
    java.util.ArrayList<Integer> timedOut =
        new java.util.ArrayList<>();

    for (int sequenceNumber = base;
         sequenceNumber < nextSequenceNumber;
         sequenceNumber++) {

        int index = sequenceNumber % windowSize;

        if (!acknowledged[index] &&
            sentTime[index] >= 0 &&
            currentTime - sentTime[index] >= rtoMillis) {

            timedOut.add(sequenceNumber);
        }
    }

    int[] result = new int[timedOut.size()];

    for (int i = 0; i < timedOut.size(); i++) {
        result[i] = timedOut.get(i);
    }

    return result;
}
}
