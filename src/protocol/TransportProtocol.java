package protocol;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Decision 7 - common interface implemented by StopAndWait, GoBackN, SelectiveRepeat.
 * The application only ever talks to this interface.
 */
public interface TransportProtocol {
    /** Reliably send the given file (START -> DATA... -> END). */
    void send(Path file) throws IOException;

    /** Receive one file into outputDir and return the path of the reconstructed file. */
    Path receive(Path outputDir) throws IOException;

    /** Statistics of the last transfer (for experiments: goodput, retransmissions, time). */
    TransferStats getStats();
}
