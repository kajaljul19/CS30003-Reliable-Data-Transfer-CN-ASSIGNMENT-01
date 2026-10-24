package common;

public class Checksum {

    public static int calculate(byte[] data) {

        int crc = 0xFFFFFFFF;

        for (byte b : data) {

            crc = crc ^ (b & 0xFF);

            for (int i = 0; i < 8; i++) {

                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                } else {
                    crc = crc >>> 1;
                }
            }
        }

        return ~crc;
    }
    public static int calculatePacketChecksum(byte[] packetBytes) {
    if (packetBytes == null || packetBytes.length < 16) {
        throw new IllegalArgumentException("Invalid packet bytes");
    }

    int crc = 0xFFFFFFFF;

    for (int i = 0; i < packetBytes.length; i++) {
        // Skip the 4-byte checksum field at offsets 12–15
        if (i >= 12 && i < 16) {
            continue;
        }

        crc ^= packetBytes[i] & 0xFF;

        for (int bit = 0; bit < 8; bit++) {
            if ((crc & 1) != 0) {
                crc = (crc >>> 1) ^ 0xEDB88320;
            } else {
                crc >>>= 1;
            }
        }
    }

    return ~crc;
}

}