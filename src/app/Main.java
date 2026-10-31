
package app;

import common.FileHasher;
import protocol.ProtocolConfig;
import protocol.StopAndWait;
import protocol.GoBackN;
import transport.TransportProtocol;
import transport.SelectiveRepeatSender;
import transport.SelectiveRepeatReceiver;
import protocol.TransferStats;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static void printStats(TransferStats stats) {
    System.out.println("Transfer time: "
            + String.format("%.3f", stats.seconds()) + " seconds");

    System.out.println("Goodput: "
            + String.format("%.2f",
                    stats.goodputBytesPerSec() / 1024.0)
            + " KB/s");

    System.out.println("Packets sent: " + stats.packetsSent);
    System.out.println("Retransmissions: " + stats.retransmissions);
    System.out.println("Corrupted packets dropped: "
            + stats.corruptedDropped);
}
    private static TransportProtocol createProtocol(
            ProtocolConfig cfg, String name) {
        if ("gbn".equalsIgnoreCase(name)) {
            return new GoBackN(cfg);
        }
        if ("saw".equalsIgnoreCase(name)) {
            return new StopAndWait(cfg);
        }
        if ("sr".equalsIgnoreCase(name)) {
    return new TransportProtocol() {
        @Override
        public void sendFile(
                Path sourceFile,
                InetAddress receiverAddress,
                int receiverPort
        ) throws IOException {
            try (SelectiveRepeatSender sender =
                         new SelectiveRepeatSender(0, cfg.windowSize,new protocol.AdaptiveRto(cfg))) {
                sender.sendFile(sourceFile, receiverAddress, receiverPort);
                printStats(sender.getStats());
            }
        }

        @Override
        public void receiveFile(
                Path destinationFile,
                int localPort
        ) throws IOException {
            new SelectiveRepeatReceiver().receiveFile(destinationFile, localPort);
        }
    };
}
        throw new IllegalArgumentException(
                "Protocol must be 'saw' or 'gbn'or 'sr'");
    }

    public static void main(String[] a) throws Exception {
        ProtocolConfig cfg = new ProtocolConfig();
cfg.verbose = Boolean.getBoolean("verbose");
cfg.windowSize = Integer.getInteger("window", cfg.windowSize);
cfg.initialRtoMs = Double.parseDouble(
        System.getProperty("initialRto", "500"));

        if (a.length >= 4 && a[0].equals("recv")) {
            TransportProtocol proto = createProtocol(cfg, a[3]);
            Path dest = Paths.get(a[2]);
            proto.receiveFile(dest, Integer.parseInt(a[1]));
            System.out.println("Received -> " + dest
                    + "\nSHA-256: " + FileHasher.sha256(dest));
            System.out.println(proto);

        } else if (a.length >= 5 && a[0].equals("send")) {
            TransportProtocol proto = createProtocol(cfg, a[4]);
            Path src = Paths.get(a[3]);
            proto.sendFile(src, InetAddress.getByName(a[1]),
                    Integer.parseInt(a[2]));
                    if (proto instanceof StopAndWait) {
    printStats(((StopAndWait) proto).getStats());
} else if (proto instanceof GoBackN) {
    printStats(((GoBackN) proto).getStats());
}
            System.out.println("Sent " + src
                    + "\nSHA-256: " + FileHasher.sha256(src));
            System.out.println(proto);

        } else if (a.length >= 3 && a[0].equals("verify")) {
            boolean ok = FileHasher.matches(
                    Paths.get(a[1]), Paths.get(a[2]));
            System.out.println(ok
                    ? "INTEGRITY OK (hashes match)"
                    : "INTEGRITY FAILED (hashes differ)");

        } else {
            System.out.println("Usage:\n"
                    + "  recv <port> <destFile> <saw|gbn>\n"
                    + "  send <host> <port> <sourceFile> <saw|gbn>\n"
                    + "  verify <sourceFile> <receivedFile>");
        }
    }
}
