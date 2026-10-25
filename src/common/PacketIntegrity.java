package common;

public final class PacketIntegrity {
    private PacketIntegrity() {}

    public static byte[] encode(Packet packet) {
        packet.setChecksum(0);
        packet.setChecksum(Checksum.calculate(packet.toBytes()));
        return packet.toBytes();
    }

    public static Packet decode(byte[] bytes) {
        try {
            Packet packet = Packet.fromBytes(bytes);
            int received = packet.getChecksum();
            packet.setChecksum(0);
            int computed = Checksum.calculate(packet.toBytes());
            packet.setChecksum(received);
            return received == computed ? packet : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
