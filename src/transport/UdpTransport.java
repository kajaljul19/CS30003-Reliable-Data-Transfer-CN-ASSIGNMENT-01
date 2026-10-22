
package transport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpTransport implements AutoCloseable {

    private static final int MAX_PACKET_SIZE = 16 + 1024;

    private final DatagramSocket socket;

    public UdpTransport(int localPort) throws IOException {
        socket = new DatagramSocket(localPort);
    }

    public void send(byte[] data, InetAddress address, int port)
            throws IOException {

        DatagramPacket packet = new DatagramPacket(
                data, data.length, address, port);

        socket.send(packet);
    }
    public void sendTo(
        byte[] data,
        ReceivedDatagram destination
) throws IOException {
    send(
            data,
            destination.getSenderAddress(),
            destination.getSenderPort()
    );
}

    public ReceivedDatagram receive() throws IOException {
    byte[] buffer = new byte[MAX_PACKET_SIZE];

    DatagramPacket packet = new DatagramPacket(
            buffer, buffer.length);

    socket.receive(packet);

    byte[] received = new byte[packet.getLength()];

    System.arraycopy(
            packet.getData(),
            packet.getOffset(),
            received,
            0,
            packet.getLength());

    return new ReceivedDatagram(
            received,
            packet.getAddress(),
            packet.getPort());
}

    public void setTimeout(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
    }

    @Override
    public void close() {
        socket.close();
    }
}
