package org.bodhi.fbc.impl;

import org.junit.Test;

import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void test_toBytes() {
        byte[] expected = new byte[] {(byte) 0xff, (byte) 0x10};
        byte[] actual = Utils.toBytes(255, 16);

        assertArrayEquals(expected, actual);
    }


    @Test
    public void test_toString_with_one_buffer() {
        Buffer buffer = new Buffer(Utils.toBytes(1, 2, 3));
        buffer.skip(3);

        Trace trace = new Trace();
        trace.trace(1, "F2", "Comment");

        String actual = Utils.toString(trace, buffer);

        String expected =
                "   0 0x0000                      0x01 \n" +
                "   1 0x0001                   F2 0x02 Comment\n" +
                "   2 0x0002                      0x03 \n";

        assertEquals(expected, actual);
    }

    @Test
    public void test_toString_with_two_buffers() {
        Buffer buffer1 = new Buffer(Utils.toBytes(1, 2, 3));
        buffer1.skip(3);

        Buffer buffer2 = new Buffer(Utils.toBytes(1, 2, 4, 5));
        buffer2.skip(4);

        Trace trace = new Trace();
        trace.trace(1, "F2", "Comment");

        String actual = Utils.toString(trace, buffer1, buffer2);

        String expected =
                "      Off                Field Actual Expected\n" +
                "   0 0x0000                      0x01 0x01   \n" +
                "   1 0x0001                   F2 0x02 0x02   Comment\n" +
                "   2 0x0002                      0x03 0x04 * \n" +
                "   3 0x0003                      ---- 0x05 * \n";

        assertEquals(expected, actual);
    }
}