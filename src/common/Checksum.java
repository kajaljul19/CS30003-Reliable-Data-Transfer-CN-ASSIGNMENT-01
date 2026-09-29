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
}