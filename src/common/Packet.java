package common;
import java.nio.ByteBuffer;
public class Packet {
 private int sequenceNumber;
 private int ackNumber;
 private byte type;
 private byte flags;
 private short dataLength;
 private int checksum;
 private byte[] data;

 public Packet(int sequenceNumber, int ackNumber, byte type, byte flags, byte[] data) {
    this.sequenceNumber = sequenceNumber;
    this.ackNumber = ackNumber;
    this.type = type;
    this.flags = flags;
    this.data = data;
    this.dataLength = (short) data.length;
    this.checksum = 0;
}
public int getSequenceNumber() {
    return sequenceNumber;
}

public int getAckNumber() {
    return ackNumber;
}

public byte getType() {
    return type;
}

public byte getFlags() {
    return flags;
}

public short getDataLength() {
    return dataLength;
}

public int getChecksum() {
    return checksum;
}

public byte[] getData() {
    return data;
}

public void setChecksum(int checksum) {
    this.checksum = checksum;
}

public void setData(byte[] data) {
    this.data = data;
    this.dataLength = (short) data.length;
}

public void setFlags(byte flags) {
    this.flags = flags;
}

public void setType(byte type) {
    this.type = type;
}

public void setAckNumber(int ackNumber) {
    this.ackNumber = ackNumber;
}

public void setSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
}
public byte[] toBytes() {
 ByteBuffer buffer = ByteBuffer.allocate(PacketConstants.HEADER_SIZE + data.length);
    buffer.putInt(sequenceNumber);
    buffer.putInt(ackNumber);
    buffer.put(type);
    buffer.put(flags);
    buffer.putShort(dataLength);
    buffer.putInt(checksum);
    buffer.put(data);
    return buffer.array();
}
public static Packet fromBytes(byte[] bytes) {
     if (bytes.length < PacketConstants.HEADER_SIZE) {
        throw new IllegalArgumentException("Packet is too short");
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    int sequenceNumber = buffer.getInt();
    int ackNumber = buffer.getInt();
    byte type = buffer.get();
    byte flags = buffer.get();
    short dataLength = buffer.getShort();
    if (dataLength < 0 || dataLength > PacketConstants.MAX_PAYLOAD_SIZE) {
    throw new IllegalArgumentException("Invalid payload length");
}

if (PacketConstants.HEADER_SIZE + dataLength != bytes.length) {
    throw new IllegalArgumentException("Packet size does not match payload length");
}
    int checksum = buffer.getInt();

    byte[] data = new byte[dataLength];
    buffer.get(data);

    Packet packet = new Packet(sequenceNumber, ackNumber, type, flags, data);
    packet.setChecksum(checksum);

    return packet;
}
}