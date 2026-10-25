package common;

public class PacketConstants {

    public static final int HEADER_SIZE = 16;
    public static final int MAX_PAYLOAD_SIZE = 1024;

    // Decision 12 - flag values
    public static final byte FLAG_NONE  = 0;
    public static final byte FLAG_START = 1;
    public static final byte FLAG_END   = 2;
}