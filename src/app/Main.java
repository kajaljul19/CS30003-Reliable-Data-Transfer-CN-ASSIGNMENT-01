
package app;

import common.FileHasher;
import protocol.ProtocolConfig;
import protocol.StopAndWait;
import protocol.GoBackN;
import transport.TransportProtocol;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static TransportProtocol createProtocol(
            ProtocolConfig cfg, String name) {
        if ("gbn".equalsIgnoreCase(name)) {
            return new GoBackN(cfg);
        }
        if ("saw".equalsIgnoreCase(name)) {
            return new StopAndWait(cfg);
        }
        throw new IllegalArgumentException(
                "Protocol must be 'saw' or 'gbn'");
    }

    public static void main(String[] a) throws Exception {
        ProtocolConfig cfg = new ProtocolConfig();
        cfg.verbose = Boolean.getBoolean("verbose");
        cfg.windowSize = Integer.getInteger("window", cfg.windowSize);

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
