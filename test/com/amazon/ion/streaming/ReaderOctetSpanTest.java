// Copyright (c) 2011 Amazon.com, Inc.  All rights reserved.

package com.amazon.ion.streaming;

import com.amazon.ion.BinaryTest;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonType;
import com.amazon.ion.OctetSpan;
import com.amazon.ion.SpanReader;
import com.amazon.ion.impl.IonReaderOctetPosition;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class ReaderOctetSpanTest
    extends IonTestCase
{
    private IonReader in;
    private SpanReader p;

    private IonReader read(byte[] binary)
    {
        in = system().newReader(binary);
        p = in.asFacet(SpanReader.class);
        return in;
    }

    private IonReader readAsStream(String text)
    {
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(encode(text));
        in = system().newReader(bytesIn);
        p = in.asFacet(SpanReader.class);
        return in;
    }

    private InputStream repeatStream(final String text, final long times)
    {
        final byte[] binary = encode(text);
        return new InputStream()
        {
            private long remainder = times;
            private ByteBuffer buf = ByteBuffer.wrap(binary);

            private boolean isDone() {
                return remainder == 0 && !buf.hasRemaining();
            }

            private void checkBuf() {
                if (!isDone() && !buf.hasRemaining()) {
                    remainder--;
                    buf.clear();
                }
            }

            @Override
            public int read() throws IOException
            {
                if (isDone())
                {
                    return -1;
                }

                int octet = buf.get() & 0xFF;
                checkBuf();
                return octet;
            }
            @Override
            public int read(byte[] b, int off, int len) throws IOException
            {
                if (isDone())
                {
                    return -1;
                }

                int rem = len - off;
                int consumed = 0;
                while (rem > 0 && !isDone())
                {
                    int amount = Math.min(rem, buf.remaining());
                    buf.get(b, off, amount);
                    off += amount;
                    rem -= amount;
                    consumed += amount;
                    checkBuf();
                }
                return consumed;
            }
        };
    }


    private void checkCurrentSpan(long start, long finish)
    {
        OctetSpan span = p.currentSpan().asFacet(OctetSpan.class);
        assertNotNull(span);
        assertEquals(start,  span.getStartOffset());
        assertEquals(finish, span.getFinishOffset());

        // Transitional APIs
        long len = finish - start;

        IonReaderOctetPosition pos = p.currentSpan().asFacet(IonReaderOctetPosition.class);
        assertNotNull(pos);
        assertEquals(start,  pos.getOffset());
        assertEquals(start,  pos.getStartOffset());
        assertEquals(len,    pos.getLength());
        assertEquals(finish, pos.getFinishOffset());
    }


    @Test
    public void testGetPosFromStream()
    {
        readAsStream("'''hello''' 1 2 3 4 5 6 7 8 9 10 '''Kumo the fluffy dog! He is so fluffy and yet so happy!'''");
        assertSame(IonType.STRING, in.next());
        checkCurrentSpan(4, 10);

        for (int i = 1; i <= 10; i++) {
            assertSame(IonType.INT, in.next());
            assertEquals(i, in.intValue());
        }

        checkCurrentSpan(28, 30);

        // Capture for ION-217
        assertSame(IonType.STRING, in.next());
        checkCurrentSpan(30, 86);
    }

    // Capture for ION-219
    @Test
    public void testGetPosFromStreamMed() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final int count = 8000;
        for (int i = 0; i < count; i++) {
            buf.write(BinaryTest.hexToBytes("E0 01 00 EA"));
            buf.write(BinaryTest.hexToBytes("22 03 E8"));
        }

        read(buf.toByteArray());
        int offset = 4;
        for (int i = 0; i < count; i++) {
            assertSame(IonType.INT, in.next());
            checkCurrentSpan(offset, offset+3);
            offset += 7;
        }
        assertNull(in.next());
    }

    // FIXME ION-216
    @Ignore
    @Test
    public void testGetPosFromStreamBig() {
        final String text = "'''Kumo the fluffy dog! He is so fluffy and yet so happy!'''";
        final long repeat = 40000000L;
        in = system().newReader(
            repeatStream(text, repeat) // make sure we go past Integer.MAX_VALUE
        );
        p = in.asFacet(SpanReader.class);

        long iterLimit = repeat - 10;
        for (long i = 0; i < iterLimit; i++)
        {
            assertSame(IonType.STRING, in.next());
        }
        checkCurrentSpan(iterLimit * 60, iterLimit * 60 + 56);
    }
}