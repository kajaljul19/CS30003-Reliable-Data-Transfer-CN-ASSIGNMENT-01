package app;

import common.FileHasher;
import network.UdpNetwork;
import protocol.ProtocolConfig;
import protocol.StopAndWait;
import protocol.TransportProtocol;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Usage:
 *   java app.Main recv <port> <outputDir>
 *   java app.Main send <host> <port> <file>     (host/port = channel emulator or receiver)
 *   java app.Main verify <sourceFile> <receivedFile>
 */
public class Main {
    public static void main(String[] a) throws Exception {
        ProtocolConfig cfg = new ProtocolConfig();
        cfg.verbose = Boolean.getBoolean("verbose");

        if (a.length >= 3 && a[0].equals("recv")) {
            try (UdpNetwork net = new UdpNetwork(Integer.parseInt(a[1]))) {
                TransportProtocol proto = new StopAndWait(net, cfg);   // swap protocol here only
                Path out = proto.receive(Paths.get(a[2]));
                System.out.println("Received -> " + out + "\nSHA-256: " + FileHasher.sha256(out));
                System.out.println(proto.getStats());
            }
        } else if (a.length >= 4 && a[0].equals("send")) {
            try (UdpNetwork net = new UdpNetwork(a[1], Integer.parseInt(a[2]))) {
                TransportProtocol proto = new StopAndWait(net, cfg);
                Path f = Paths.get(a[3]);
                proto.send(f);
                System.out.println("Sent " + f + "\nSHA-256: " + FileHasher.sha256(f));
                System.out.println(proto.getStats());
            }
        } else if (a.length >= 3 && a[0].equals("verify")) {
            boolean ok = FileHasher.matches(Paths.get(a[1]), Paths.get(a[2]));
            System.out.println(ok ? "INTEGRITY OK (hashes match)" : "INTEGRITY FAILED (hashes differ)");
        } else {
            System.out.println("Usage:\n  recv <port> <outDir>\n  send <host> <port> <file>\n  verify <src> <received>");
        }
    }
}
