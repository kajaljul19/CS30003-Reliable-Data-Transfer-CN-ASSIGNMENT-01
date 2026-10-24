package common;

/** Serializes packets with CRC-32 and rejects packets with invalid CRCs. */
public final class PacketIntegrity {
    private PacketIntegrity() {}
    public static byte[] encode(Packet packet) {
        packet.setChecksum(0);
        packet.setChecksum(Checksum.calculatePacketChecksum(packet.toBytes()));
        return packet.toBytes();
    }
    public static Packet decode(byte[] bytes) {
        try {
            Packet packet = Packet.fromBytes(bytes);
            int received = packet.getChecksum();
            int computed = Checksum.calculatePacketChecksum(bytes);
            return received == computed ? packet : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
