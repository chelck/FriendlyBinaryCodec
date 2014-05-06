
package org.chelck.fbc;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.chelck.fbc.impl.IoBuffer;
import java.nio.charset.Charset;

public class BinaryWriter {
    private IoBuffer m_buffer;
    private Map<String, Integer> m_positions;
    private Map<Integer, String> m_trace;
    private Charset m_charset;

    public BinaryWriter(Charset charset) {
        m_charset = charset;

        m_buffer = IoBuffer.allocate(128);

        m_buffer.order(ByteOrder.BIG_ENDIAN);
        m_buffer.setAutoExpand(true);
        m_buffer.setAutoShrink(true);

        m_positions = new HashMap<String, Integer>();
        m_trace = new HashMap<Integer, String>();
    }

    public void name(String name) {
        m_positions.put(name, m_buffer.position());
    }

    public int position(String name) {
        assert m_positions.containsKey(name) : name;
        return m_positions.get(name);
    }

    public void putBoolean(boolean b) {
        putInt1(b ? 1 : 0);
    }

    public void putBoolean(boolean b, String name) {
        putInt1(b ? 1 : 0, name);
    }

    public void putInt1(int n) {
        m_buffer.put((byte) n);
    }

    public void putInt1(int n, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        putInt1(n);
    }

    public void putInt2(int n) {
        m_buffer.putShort((short)n);
    }

    public void putInt2(int n, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        putInt2(n);
    }

    public void putInt4(int n) {
        m_buffer.putInt(n);
    }

    public void putInt4(int n, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        putInt4(n);
    }

    public void putInt8(long n) {
        m_buffer.putLong(n);
    }

    public void putInt8(long n, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        putInt8(n);
    }

    public void putBytes(byte[] bytes, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        m_buffer.put(bytes);
    }

    public void putString(String s, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        m_buffer.put(s.getBytes(m_charset));
    }

    public void putString(String s, int length, String name) {
        m_trace.put(m_buffer.position(), name);
        name(name);
        m_buffer.put(padRight(s, length).getBytes(m_charset), 0, length);
    }

    public void replaceInt4(String name, int n) {
        //System.out.println("Replace " + name + " with " + n);
        m_buffer.putInt(position(name), n);
    }

    public int diff(String name1, String name2) {
        return position(name1) - position(name2);
    }

    public byte[] getBytes() {
        System.err.println("getBytes1(): " + m_buffer.limit());
        m_buffer.flip();
        System.err.println("getBytes2(): " + m_buffer.limit());

        byte[] backing = m_buffer.array();
        return Arrays.copyOf(backing, m_buffer.limit());
    }

    public String toString() {
        StringBuilder b = new StringBuilder();

        for (int ii=0; ii<m_buffer.limit(); ii++) {
            String name = m_trace.containsKey(ii) ? m_trace.get(ii) : "";
            String value = hex(m_buffer, ii);
            b.append(String.format("0x%04x %20s %s\n", ii, name, value));
        }
        return b.toString();
    }

/*
    public void assertEquals(IoBuffer actual) {
        m_buffer.flip();

        if (!actual.equals(m_buffer)) {
            System.out.format("%9s %20s %s %s\n", "Off", "Field", "Actual", "Expected");

            int limit = m_buffer.limit() > actual.limit() ? m_buffer.limit() : actual.limit();
            for (int ii=0; ii<limit; ii++) {
                String name = m_trace.containsKey(ii) ? m_trace.get(ii) : "";
                String actualByte = x(actual, ii);
                String expectedByte = x(m_buffer, ii);
                char mark = actualByte.equals(expectedByte) ? ' ' : '*';
                System.out.format("%4d 0x%04x %20s %4s %4s %c\n", ii, ii, name, actualByte, expectedByte, mark);
            }

        }

        Assert.assertEquals(actual.getHexDump(), m_buffer.getHexDump());
    }
*/
    private String hex(IoBuffer buffer, int index) {
        return (index < buffer.limit()) ? String.format("0x%02x", buffer.getUnsigned(index)) : "----";
    }

    private static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);  
    }

}