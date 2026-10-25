package app;

import common.FileHasher;
import network.UdpNetwork;
import protocol.ProtocolConfig;
import protocol.StopAndWait;
import protocol.TransportProtocol;
import protocol.GoBackN;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] a) throws Exception {
        ProtocolConfig cfg = new ProtocolConfig();
        cfg.verbose = Boolean.getBoolean("verbose");

        String selected = System.getProperty("protocol", "saw");

        if (selected.equalsIgnoreCase("gbn")) {
            if (a.length >= 3 && a[0].equals("recv")) {
                Path destination = Paths.get(a[2]);
                GoBackN proto = new GoBackN(cfg);
                proto.receiveFile(destination, Integer.parseInt(a[1]));
                System.out.println("Received -> " + destination);
                System.out.println("SHA-256: " + FileHasher.sha256(destination));
                System.out.println(proto.getStats());
            } else if (a.length >= 4 && a[0].equals("send")) {
                Path file = Paths.get(a[3]);
                GoBackN proto = new GoBackN(cfg);
                proto.sendFile(file, InetAddress.getByName(a[1]), Integer.parseInt(a[2]));
                System.out.println("Sent " + file);
                System.out.println("SHA-256: " + FileHasher.sha256(file));
                System.out.println(proto.getStats());
            } else {
                usage();
            }
            return;
        }

        if (a.length >= 3 && a[0].equals("recv")) {
            try (UdpNetwork net = new UdpNetwork(Integer.parseInt(a[1]))) {
                TransportProtocol proto = new StopAndWait(net, cfg);
                Path out = proto.receive(Paths.get(a[2]));
                System.out.println("Received -> " + out + "\nSHA-256: " + FileHasher.sha256(out));
                System.out.println(proto.getStats());
            }
        } else if (a.length >= 4 && a[0].equals("send")) {
            try (UdpNetwork net = new UdpNetwork(a[1], Integer.parseInt(a[2]))) {
                TransportProtocol proto = new StopAndWait(net, cfg);
                Path file = Paths.get(a[3]);
                proto.send(file);
                System.out.println("Sent " + file + "\nSHA-256: " + FileHasher.sha256(file));
                System.out.println(proto.getStats());
            }
        } else if (a.length >= 3 && a[0].equals("verify")) {
            boolean ok = FileHasher.matches(Paths.get(a[1]), Paths.get(a[2]));
            System.out.println(ok ? "INTEGRITY OK (hashes match)" : "INTEGRITY FAILED (hashes differ)");
        } else {
            usage();
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  java app.Main recv <port> <outputDir>");
        System.out.println("  java app.Main send <host> <port> <file>");
        System.out.println("  java -Dprotocol=gbn app.Main recv <port> <outputFile>");
        System.out.println("  java -Dprotocol=gbn app.Main send <host> <port> <file>");
        System.out.println("  java app.Main verify <sourceFile> <receivedFile>");
    }
}
