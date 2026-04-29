package com.soymods.oreveil.obfuscation.chunk;

public final class ChunkBufferReader {
    private final byte[] data;
    private int position;

    public ChunkBufferReader(byte[] data) {
        this.data = data;
    }

    public int readUnsignedByte() {
        ensureAvailable(1);
        return data[position++] & 0xFF;
    }

    public int readVarInt() {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            ensureAvailable(1);
            read = data[position++];
            int value = read & 0x7F;
            result |= value << (7 * numRead);

            numRead++;
            if (numRead > 5) {
                throw new IllegalStateException("VarInt too large in chunk buffer.");
            }
        } while ((read & 0x80) != 0);

        return result;
    }

    public long[] readLongArray() {
        int length = readVarInt();
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = readLong();
        }
        return values;
    }

    public long readLong() {
        ensureAvailable(Long.BYTES);
        long value = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            value = (value << 8) | (data[position++] & 0xFFL);
        }
        return value;
    }

    public void skipBytes(int length) {
        ensureAvailable(length);
        position += length;
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return data.length - position;
    }

    private void ensureAvailable(int length) {
        if (position + length > data.length) {
            throw new IllegalStateException("Unexpected end of chunk buffer.");
        }
    }
}
