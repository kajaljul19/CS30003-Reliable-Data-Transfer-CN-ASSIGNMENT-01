package transport;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

public interface TransportProtocol {

    void sendFile(
            Path sourceFile,
            InetAddress receiverAddress,
            int receiverPort
    ) throws IOException;

    void receiveFile(
            Path destinationFile,
            int localPort
    ) throws IOException;
}