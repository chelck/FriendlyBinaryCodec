package org.bodhi.fbc;

import java.nio.charset.Charset;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.bodhi.fbc.impl.Utils.*;

public class BinaryWriterTest {

    @Test
    public void test_growth() throws Exception {
        BinaryWriter bw = new BinaryWriter(4, Charset.forName("ISO-8859-1"));

        System.out.println("============ test growth ====== ");
        for (int ii=0; ii<20; ii++) {
            bw.putInt1(ii);
        }

        byte[] raw = bw.getBytes();
        assertEquals(20, raw.length);
        for (int ii=0; ii<20; ii++) {
            assertEquals(ii, raw[ii]);
        }
    }

}