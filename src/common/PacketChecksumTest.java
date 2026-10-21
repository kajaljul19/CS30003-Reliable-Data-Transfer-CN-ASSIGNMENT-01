
package common;

public class PacketChecksumTest {
    public static void main(String[] args) {
        byte[] packet = new byte[20];

        for (int i = 0; i < packet.length; i++) {
            packet[i] = (byte) i;
        }

        int first = Checksum.calculatePacketChecksum(packet);

        // Changing checksum-field bytes must not change the result.
        packet[12] = 99;
        packet[13] = 88;
        packet[14] = 77;
        packet[15] = 66;

        int second = Checksum.calculatePacketChecksum(packet);

        if (first != second) {
            throw new AssertionError("Checksum field was not excluded");
        }

        // Changing payload must change the result for this test packet.
        packet[19] ^= 1;
        int third = Checksum.calculatePacketChecksum(packet);

        if (second == third) {
            throw new AssertionError("Payload change was not detected");
        }
          // Test actual packet serialization and checksum validation.
Packet original = new Packet(
    7, 3, PacketType.DATA, (byte) 0,
    new byte[] {10, 20, 30, 40}
);

byte[] bytes = original.toBytes();
Packet received = Packet.fromBytes(bytes);

if (received.getSequenceNumber() != 7) {
    throw new AssertionError("Sequence number mismatch");
}

if (!java.util.Arrays.equals(
        received.getData(), original.getData())) {
    throw new AssertionError("Payload mismatch");
}

// Corrupt one payload byte.
bytes[bytes.length - 1] ^= 1;

try {
    Packet.fromBytes(bytes);
    throw new AssertionError("Corrupted packet was accepted");
} catch (IllegalArgumentException e) {
    if (!"Packet checksum mismatch".equals(e.getMessage())) {
        throw e;
    }
}

System.out.println("Packet serialization and validation tests passed!");

        System.out.println("Packet checksum tests passed!");
    }
}
