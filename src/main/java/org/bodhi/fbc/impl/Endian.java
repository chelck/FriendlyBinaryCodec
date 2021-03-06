package org.bodhi.fbc.impl;

public abstract class Endian {

    // -- get/put char --

    abstract char getUtfChar(byte[] bb, int offset);
    abstract void putUtfChar(byte[] bb, int offset, char x);

    int getInt1(byte[] bb, int offset) {
        return bb[offset];
    }

    void putInt1(byte[] bb, int offset, int value) {
        bb[offset] = (byte) (value & 0xFF);
    }

    // -- get/put integers

    abstract int getInt2(byte[] bb, int offset);
    abstract void putInt2(byte[] bb, int offset, int x);

    abstract int getInt4(byte[] bb, int offset);
    abstract void putInt4(byte[] bb, int offset, int x);

    abstract long getInt8(byte[] bb, int offset);
    abstract void putInt8(byte[] bb, int offset, long x);

    abstract int getUInt2(byte[] bb, int offset);
    abstract void putUInt2(byte[] bb, int offset, int x);

    // -- Makers

    static char makeChar(byte b1, byte b0) {
        return (char)((b1 << 8) | (b0 & 0xff));
    }

    static int makeShort(byte b1, byte b0) {
        return (int)((b1 << 8) | (b0 & 0xff));
    }

    static int makeInt(byte b1, byte b0) {
        return (int)(((b1 & 0xff) << 8) | (b0 & 0xff));
    }

    static int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return (((b3       ) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) <<  8) |
                ((b0 & 0xff)      ));
    }

    static long makeLong(byte b7, byte b6, byte b5, byte b4,
                                 byte b3, byte b2, byte b1, byte b0)
    {
        return ((((long)b7       ) << 56) |
                (((long)b6 & 0xff) << 48) |
                (((long)b5 & 0xff) << 40) |
                (((long)b4 & 0xff) << 32) |
                (((long)b3 & 0xff) << 24) |
                (((long)b2 & 0xff) << 16) |
                (((long)b1 & 0xff) <<  8) |
                (((long)b0 & 0xff)      ));
    }

    // Getters or breakers

    static byte char1(char x) { return (byte)(x >> 8); }
    static byte char0(char x) { return (byte)(x     ); }

    static byte short1(int x) { return (byte)(x >> 8); }
    static byte short0(int x) { return (byte)(x     ); }

    static byte int3(int x) { return (byte)(x >> 24); }
    static byte int2(int x) { return (byte)(x >> 16); }
    static byte int1(int x) { return (byte)(x >>  8); }
    static byte int0(int x) { return (byte)(x      ); }

    static byte long7(long x) { return (byte)(x >> 56); }
    static byte long6(long x) { return (byte)(x >> 48); }
    static byte long5(long x) { return (byte)(x >> 40); }
    static byte long4(long x) { return (byte)(x >> 32); }
    static byte long3(long x) { return (byte)(x >> 24); }
    static byte long2(long x) { return (byte)(x >> 16); }
    static byte long1(long x) { return (byte)(x >>  8); }
    static byte long0(long x) { return (byte)(x      ); }


}
