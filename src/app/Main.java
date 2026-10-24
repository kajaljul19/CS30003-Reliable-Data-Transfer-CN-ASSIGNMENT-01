package app;

import common.FileHasher;
import protocol.GoBackN;
import protocol.ProtocolConfig;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Usage:
 *   java [-Dwindow=N] [-Dverbose=true] -cp out app.Main recv <port> <destinationFile>
 *   java [-Dwindow=N] [-Dverbose=true] -cp out app.Main send <host> <port> <sourceFile>   (host/port = channel emulator or receiver)
 *   java -cp out app.Main verify <sourceFile> <receivedFile>
 */
public class Main {
    public static void main(String[] a) throws Exception {
        ProtocolConfig cfg = new ProtocolConfig();
        cfg.verbose = Boolean.getBoolean("verbose");
        cfg.windowSize = Integer.getInteger("window", cfg.windowSize);

        if (a.length >= 3 && a[0].equals("recv")) {
            GoBackN proto = new GoBackN(cfg);
            Path dest = Paths.get(a[2]);
            proto.receiveFile(dest, Integer.parseInt(a[1]));
            System.out.println("Received -> " + dest + "\nSHA-256: " + FileHasher.sha256(dest));
            System.out.println(proto.getStats());
        } else if (a.length >= 4 && a[0].equals("send")) {
            GoBackN proto = new GoBackN(cfg);
            Path src = Paths.get(a[3]);
            proto.sendFile(src, InetAddress.getByName(a[1]), Integer.parseInt(a[2]));
            System.out.println("Sent " + src + " (window=" + cfg.windowSize + ")\nSHA-256: " + FileHasher.sha256(src));
            System.out.println(proto.getStats());
        } else if (a.length >= 3 && a[0].equals("verify")) {
            boolean ok = FileHasher.matches(Paths.get(a[1]), Paths.get(a[2]));
            System.out.println(ok ? "INTEGRITY OK (hashes match)" : "INTEGRITY FAILED (hashes differ)");
        } else {
            System.out.println("Usage:\n  recv <port> <destFile>\n  send <host> <port> <file>\n  verify <src> <received>");
        }
    }
}
