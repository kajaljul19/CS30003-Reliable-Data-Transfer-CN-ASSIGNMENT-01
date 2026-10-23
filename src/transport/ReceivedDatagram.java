package transport;

import java.net.InetAddress;

public class ReceivedDatagram {

    private final byte[] data;
    private final InetAddress senderAddress;
    private final int senderPort;

    public ReceivedDatagram(
            byte[] data,
            InetAddress senderAddress,
            int senderPort) {
        this.data = data;
        this.senderAddress = senderAddress;
        this.senderPort = senderPort;
    }

    public byte[] getData() {
        return data;
    }

    public InetAddress getSenderAddress() {
        return senderAddress;
    }

    public int getSenderPort() {
        return senderPort;
    }
}