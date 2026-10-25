package network;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

/**
 * Decision 8 - the only class that touches the UDP socket.
 * Creates the socket, sends/receives raw bytes and manages the remote address/port.
 * ARQ protocols just call send()/receive() and never deal with UDP mechanics.
 */
public class UdpNetwork implements AutoCloseable {
    private final DatagramSocket socket;
    private InetSocketAddress remote;
    private final boolean learnRemote;
    private final byte[] buffer = new byte[65535];

    /** Receiver side: bind to a port; remote address is learned from incoming packets. */
    public UdpNetwork(int localPort) throws SocketException {
        this.socket = new DatagramSocket(localPort);
        this.learnRemote = true;
    }

    /** Sender side: ephemeral local port, fixed remote (the receiver or the channel emulator). */
    public UdpNetwork(String remoteHost, int remotePort) throws SocketException {
        this.socket = new DatagramSocket();
        this.remote = new InetSocketAddress(remoteHost, remotePort);
        this.learnRemote = false;
    }

    public void send(byte[] data) throws IOException {
        if (remote == null) throw new IOException("Remote address not known yet");
        socket.send(new DatagramPacket(data, data.length, remote));
    }

    /**
     * Blocks up to timeoutMs (0 = forever). Returns the received bytes, or null on timeout.
     */
    public byte[] receive(int timeoutMs) throws IOException {
        socket.setSoTimeout(Math.max(0, timeoutMs));
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(dp);
        } catch (SocketTimeoutException e) {
            return null;
        }
        if (learnRemote) remote = new InetSocketAddress(dp.getAddress(), dp.getPort());
        return Arrays.copyOf(dp.getData(), dp.getLength());
    }

    public int getLocalPort() { return socket.getLocalPort(); }

    @Override
    public void close() { socket.close(); }
}
