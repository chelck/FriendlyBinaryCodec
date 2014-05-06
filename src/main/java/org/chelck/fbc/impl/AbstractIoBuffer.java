/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.chelck.fbc.impl;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * A base implementation of {@link IoBuffer}.  This implementation
 * assumes that {@link IoBuffer#buf()} always returns a correct NIO
 * {@link ByteBuffer} instance.  Most implementations could
 * extend this class and implement their own buffer management mechanism.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @see IoBufferAllocator
 */
public abstract class AbstractIoBuffer extends IoBuffer {

    /** A flag set to true if the buffer can extend automatically */
    private boolean autoExpand;

    /** A flag set to true if the buffer can shrink automatically */
    private boolean autoShrink;

    /** Tells if a buffer can be expanded */
    private boolean recapacityAllowed = true;

    /** The minimum number of bytes the IoBuffer can hold */
    private int minimumCapacity;

    /** A mask for a byte */
    private static final long BYTE_MASK = 0xFFL;

    /** A mask for a short */
    private static final long SHORT_MASK = 0xFFFFL;

    /** A mask for an int */
    private static final long INT_MASK = 0xFFFFFFFFL;

    /**
     * We don't have any access to Buffer.markValue(), so we need to track it down,
     * which will cause small extra overhead.
     */
    private int mark = -1;

    /**
     * Creates a new parent buffer.
     * 
     * @param allocator The allocator to use to create new buffers
     * @param initialCapacity The initial buffer capacity when created
     */
    protected AbstractIoBuffer(IoBufferAllocator allocator, int initialCapacity) {
        setAllocator(allocator);
        this.recapacityAllowed = true;
        this.minimumCapacity = initialCapacity;
    }

    /**
     * Sets the underlying NIO buffer instance.
     * 
     * @param newBuf The buffer to store within this IoBuffer
     */
    protected abstract void buf(ByteBuffer newBuf);

    /**
     * {@inheritDoc}
     */
    @Override
    public final int minimumCapacity() {
        return minimumCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer minimumCapacity(int minimumCapacity) {
        if (minimumCapacity < 0) {
            throw new IllegalArgumentException("minimumCapacity: "
                    + minimumCapacity);
        }
        this.minimumCapacity = minimumCapacity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int capacity() {
        return buf().capacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer capacity(int newCapacity) {
        if (!recapacityAllowed) {
            throw new IllegalStateException(
                    "Derived buffers and their parent can't be expanded.");
        }

        // Allocate a new buffer and transfer all settings to it.
        if (newCapacity > capacity()) {
            // Expand:
            //// Save the state.
            int pos = position();
            int limit = limit();
            ByteOrder bo = order();

            //// Reallocate.
            ByteBuffer oldBuf = buf();
            ByteBuffer newBuf = getAllocator().allocateNioBuffer(newCapacity);
            oldBuf.clear();
            newBuf.put(oldBuf);
            buf(newBuf);

            //// Restore the state.
            buf().limit(limit);
            if (mark >= 0) {
                buf().position(mark);
                buf().mark();
            }
            buf().position(pos);
            buf().order(bo);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAutoExpand() {
        return autoExpand && recapacityAllowed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAutoShrink() {
        return autoShrink && recapacityAllowed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer setAutoExpand(boolean autoExpand) {
        if (!recapacityAllowed) {
            throw new IllegalStateException(
                    "Derived buffers and their parent can't be expanded.");
        }
        this.autoExpand = autoExpand;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer setAutoShrink(boolean autoShrink) {
        if (!recapacityAllowed) {
            throw new IllegalStateException(
                    "Derived buffers and their parent can't be shrinked.");
        }
        this.autoShrink = autoShrink;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer expand(int expectedRemaining) {
        return expand(position(), expectedRemaining, false);
    }

    private IoBuffer expand(int expectedRemaining, boolean autoExpand) {
        return expand(position(), expectedRemaining, autoExpand);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer expand(int pos, int expectedRemaining) {
        return expand(pos, expectedRemaining, false);
    }

    private IoBuffer expand(int pos, int expectedRemaining, boolean autoExpand) {
        if (!recapacityAllowed) {
            throw new IllegalStateException(
                    "Derived buffers and their parent can't be expanded.");
        }

        int end = pos + expectedRemaining;
        int newCapacity;
        if (autoExpand) {
            newCapacity = IoBuffer.normalizeCapacity(end);
        } else {
            newCapacity = end;
        }
        if (newCapacity > capacity()) {
            // The buffer needs expansion.
            capacity(newCapacity);
        }

        if (end > limit()) {
            // We call limit() directly to prevent StackOverflowError
            buf().limit(end);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer shrink() {

        if (!recapacityAllowed) {
            throw new IllegalStateException(
                    "Derived buffers and their parent can't be expanded.");
        }

        int position = position();
        int capacity = capacity();
        int limit = limit();
        if (capacity == limit) {
            return this;
        }

        int newCapacity = capacity;
        int minCapacity = Math.max(minimumCapacity, limit);
        for (;;) {
            if (newCapacity >>> 1 < minCapacity) {
                break;
            }
            newCapacity >>>= 1;
        }

        newCapacity = Math.max(minCapacity, newCapacity);

        if (newCapacity == capacity) {
            return this;
        }

        // Shrink and compact:
        //// Save the state.
        ByteOrder bo = order();

        //// Reallocate.
        ByteBuffer oldBuf = buf();
        ByteBuffer newBuf = getAllocator()
                .allocateNioBuffer(newCapacity);
        oldBuf.position(0);
        oldBuf.limit(limit);
        newBuf.put(oldBuf);
        buf(newBuf);

        //// Restore the state.
        buf().position(position);
        buf().limit(limit);
        buf().order(bo);
        mark = -1;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int position() {
        return buf().position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer position(int newPosition) {
        autoExpand(newPosition, 0);
        buf().position(newPosition);
        if (mark > newPosition) {
            mark = -1;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int limit() {
        return buf().limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer limit(int newLimit) {
        autoExpand(newLimit, 0);
        buf().limit(newLimit);
        if (mark > newLimit) {
            mark = -1;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer mark() {
        buf().mark();
        mark = position();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int markValue() {
        return mark;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer reset() {
        buf().reset();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer clear() {
        buf().clear();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer sweep() {
        clear();
        return fillAndReset(remaining());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer sweep(byte value) {
        clear();
        return fillAndReset(value, remaining());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer flip() {
        buf().flip();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer rewind() {
        buf().rewind();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int remaining() {
        return limit() - position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean hasRemaining() {
        return limit() > position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte get() {
        return buf().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getUnsigned() {
        return (short) (get() & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(byte b) {
        autoExpand(1);
        buf().put(b);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte get(int index) {
        return buf().get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getUnsigned(int index) {
        return (short) (get(index) & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(int index, byte b) {
        autoExpand(index, 1);
        buf().put(index, b);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer get(byte[] dst, int offset, int length) {
        buf().get(dst, offset, length);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(ByteBuffer src) {
        autoExpand(src.remaining());
        buf().put(src);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(byte[] src, int offset, int length) {
        autoExpand(length);
        buf().put(src, offset, length);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer compact() {
        int remaining = remaining();
        int capacity = capacity();

        if (capacity == 0) {
            return this;
        }

        if (isAutoShrink() && remaining <= capacity >>> 2
                && capacity > minimumCapacity) {
            int newCapacity = capacity;
            int minCapacity = Math.max(minimumCapacity, remaining << 1);
            for (;;) {
                if (newCapacity >>> 1 < minCapacity) {
                    break;
                }
                newCapacity >>>= 1;
            }

            newCapacity = Math.max(minCapacity, newCapacity);

            if (newCapacity == capacity) {
                return this;
            }

            // Shrink and compact:
            //// Save the state.
            ByteOrder bo = order();

            //// Sanity check.
            if (remaining > newCapacity) {
                throw new IllegalStateException(
                        "The amount of the remaining bytes is greater than "
                                + "the new capacity.");
            }

            //// Reallocate.
            ByteBuffer oldBuf = buf();
            ByteBuffer newBuf = getAllocator().allocateNioBuffer(newCapacity);
            newBuf.put(oldBuf);
            buf(newBuf);

            //// Restore the state.
            buf().order(bo);
        } else {
            buf().compact();
        }
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteOrder order() {
        return buf().order();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer order(ByteOrder bo) {
        buf().order(bo);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final char getChar() {
        return buf().getChar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putChar(char value) {
        autoExpand(2);
        buf().putChar(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final char getChar(int index) {
        return buf().getChar(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putChar(int index, char value) {
        autoExpand(index, 2);
        buf().putChar(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final CharBuffer asCharBuffer() {
        return buf().asCharBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getShort() {
        return buf().getShort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putShort(short value) {
        autoExpand(2);
        buf().putShort(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getShort(int index) {
        return buf().getShort(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putShort(int index, short value) {
        autoExpand(index, 2);
        buf().putShort(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getInt() {
        return buf().getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putInt(int value) {
        autoExpand(4);
        buf().putInt(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getInt(int index) {
        return buf().getInt(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putInt(int index, int value) {
        autoExpand(index, 4);
        buf().putInt(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getLong() {
        return buf().getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putLong(long value) {
        autoExpand(8);
        buf().putLong(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getLong(int index) {
        return buf().getLong(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putLong(int index, long value) {
        autoExpand(index, 8);
        buf().putLong(index, value);
        return this;
    }


    /**
     * Implement this method to return the unexpandable read only version of
     * this buffer.
     */
    //protected abstract IoBuffer asReadOnlyBuffer0();




    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--) {
            h = 31 * h + get(i);
        }
        return h;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IoBuffer)) {
            return false;
        }

        IoBuffer that = (IoBuffer) o;
        if (this.remaining() != that.remaining()) {
            return false;
        }

        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            byte v1 = this.get(i);
            byte v2 = that.get(j);
            if (v1 != v2) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(IoBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            byte v1 = this.get(i);
            byte v2 = that.get(j);
            if (v1 == v2) {
                continue;
            }
            if (v1 < v2) {
                return -1;
            }

            return +1;
        }
        return this.remaining() - that.remaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("HeapBuffer");
        buf.append("[pos=");
        buf.append(position());
        buf.append(" lim=");
        buf.append(limit());
        buf.append(" cap=");
        buf.append(capacity());
        buf.append(": ");
        buf.append(getHexDump(16));
        buf.append(']');
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer put(IoBuffer src) {
        return put(src.buf());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedShort() {
        return getShort() & 0xffff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xffff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnsignedInt() {
        return getInt() & 0xffffffffL;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xffffffffL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHexDump() {
        return this.getHexDump(Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHexDump(int lengthLimit) {
        return IoBufferHexDumper.getHexdump(this, lengthLimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(CharsetDecoder decoder)
            throws CharacterCodingException {
        if (!hasRemaining()) {
            return "";
        }

        boolean utf16 = decoder.charset().name().startsWith("UTF-16");

        int oldPos = position();
        int oldLimit = limit();
        int end = -1;
        int newPos;

        if (!utf16) {
            end = indexOf((byte) 0x00);
            if (end < 0) {
                newPos = end = oldLimit;
            } else {
                newPos = end + 1;
            }
        } else {
            int i = oldPos;
            for (;;) {
                boolean wasZero = get(i) == 0;
                i++;

                if (i >= oldLimit) {
                    break;
                }

                if (get(i) != 0) {
                    i++;
                    if (i >= oldLimit) {
                        break;
                    }

                    continue;
                }

                if (wasZero) {
                    end = i - 1;
                    break;
                }
            }

            if (end < 0) {
                newPos = end = oldPos + (oldLimit - oldPos & 0xFFFFFFFE);
            } else {
                if (end + 2 <= oldLimit) {
                    newPos = end + 2;
                } else {
                    newPos = end;
                }
            }
        }

        if (oldPos == end) {
            position(newPos);
            return "";
        }

        limit(end);
        decoder.reset();

        int expectedLength = (int) (remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (;;) {
            CoderResult cr;
            if (hasRemaining()) {
                cr = decoder.decode(buf(), out, true);
            } else {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow()) {
                break;
            }

            if (cr.isOverflow()) {
                CharBuffer o = CharBuffer.allocate(out.capacity()
                        + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            if (cr.isError()) {
                // Revert the buffer back to the previous state.
                limit(oldLimit);
                position(oldPos);
                cr.throwException();
            }
        }

        limit(oldLimit);
        position(newPos);
        return out.flip().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(int fieldSize, CharsetDecoder decoder)
            throws CharacterCodingException {
        checkFieldSize(fieldSize);

        if (fieldSize == 0) {
            return "";
        }

        if (!hasRemaining()) {
            return "";
        }

        boolean utf16 = decoder.charset().name().startsWith("UTF-16");

        if (utf16 && (fieldSize & 1) != 0) {
            throw new IllegalArgumentException("fieldSize is not even.");
        }

        int oldPos = position();
        int oldLimit = limit();
        int end = oldPos + fieldSize;

        if (oldLimit < end) {
            throw new BufferUnderflowException();
        }

        int i;

        if (!utf16) {
            for (i = oldPos; i < end; i++) {
                if (get(i) == 0) {
                    break;
                }
            }

            if (i == end) {
                limit(end);
            } else {
                limit(i);
            }
        } else {
            for (i = oldPos; i < end; i += 2) {
                if (get(i) == 0 && get(i + 1) == 0) {
                    break;
                }
            }

            if (i == end) {
                limit(end);
            } else {
                limit(i);
            }
        }

        if (!hasRemaining()) {
            limit(oldLimit);
            position(end);
            return "";
        }
        decoder.reset();

        int expectedLength = (int) (remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (;;) {
            CoderResult cr;
            if (hasRemaining()) {
                cr = decoder.decode(buf(), out, true);
            } else {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow()) {
                break;
            }

            if (cr.isOverflow()) {
                CharBuffer o = CharBuffer.allocate(out.capacity()
                        + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            if (cr.isError()) {
                // Revert the buffer back to the previous state.
                limit(oldLimit);
                position(oldPos);
                cr.throwException();
            }
        }

        limit(oldLimit);
        position(end);
        return out.flip().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putString(CharSequence val, CharsetEncoder encoder)
            throws CharacterCodingException {
        if (val.length() == 0) {
            return this;
        }

        CharBuffer in = CharBuffer.wrap(val);
        encoder.reset();

        int expandedState = 0;

        for (;;) {
            CoderResult cr;
            if (in.hasRemaining()) {
                cr = encoder.encode(in, buf(), true);
            } else {
                cr = encoder.flush(buf());
            }

            if (cr.isUnderflow()) {
                break;
            }
            if (cr.isOverflow()) {
                if (isAutoExpand()) {
                    switch (expandedState) {
                    case 0:
                        autoExpand((int) Math.ceil(in.remaining()
                                * encoder.averageBytesPerChar()));
                        expandedState++;
                        break;
                    case 1:
                        autoExpand((int) Math.ceil(in.remaining()
                                * encoder.maxBytesPerChar()));
                        expandedState++;
                        break;
                    default:
                        throw new RuntimeException("Expanded by "
                                + (int) Math.ceil(in.remaining()
                                        * encoder.maxBytesPerChar())
                                + " but that wasn't enough for '" + val + "'");
                    }
                    continue;
                }
            } else {
                expandedState = 0;
            }
            cr.throwException();
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putString(CharSequence val, int fieldSize,
            CharsetEncoder encoder) throws CharacterCodingException {
        checkFieldSize(fieldSize);

        if (fieldSize == 0) {
            return this;
        }

        autoExpand(fieldSize);

        boolean utf16 = encoder.charset().name().startsWith("UTF-16");

        if (utf16 && (fieldSize & 1) != 0) {
            throw new IllegalArgumentException("fieldSize is not even.");
        }

        int oldLimit = limit();
        int end = position() + fieldSize;

        if (oldLimit < end) {
            throw new BufferOverflowException();
        }

        if (val.length() == 0) {
            if (!utf16) {
                put((byte) 0x00);
            } else {
                put((byte) 0x00);
                put((byte) 0x00);
            }
            position(end);
            return this;
        }

        CharBuffer in = CharBuffer.wrap(val);
        limit(end);
        encoder.reset();

        for (;;) {
            CoderResult cr;
            if (in.hasRemaining()) {
                cr = encoder.encode(in, buf(), true);
            } else {
                cr = encoder.flush(buf());
            }

            if (cr.isUnderflow() || cr.isOverflow()) {
                break;
            }
            cr.throwException();
        }

        limit(oldLimit);

        if (position() < end) {
            if (!utf16) {
                put((byte) 0x00);
            } else {
                put((byte) 0x00);
                put((byte) 0x00);
            }
        }

        position(end);
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int indexOf(byte b) {
        if (hasArray()) {
            int arrayOffset = arrayOffset();
            int beginPos = arrayOffset + position();
            int limit = arrayOffset + limit();
            byte[] array = array();

            for (int i = beginPos; i < limit; i++) {
                if (array[i] == b) {
                    return i - arrayOffset;
                }
            }
        } else {
            int beginPos = position();
            int limit = limit();

            for (int i = beginPos; i < limit; i++) {
                if (get(i) == b) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer skip(int size) {
        autoExpand(size);
        return position(position() + size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fill(byte value, int size) {
        autoExpand(size);
        int q = size >>> 3;
        int r = size & 7;

        if (q > 0) {
            int intValue = value | value << 8 | value << 16 | value << 24;
            long longValue = intValue;
            longValue <<= 32;
            longValue |= intValue;

            for (int i = q; i > 0; i--) {
                putLong(longValue);
            }
        }

        q = r >>> 2;
        r = r & 3;

        if (q > 0) {
            int intValue = value | value << 8 | value << 16 | value << 24;
            putInt(intValue);
        }

        q = r >> 1;
        r = r & 1;

        if (q > 0) {
            short shortValue = (short) (value | value << 8);
            putShort(shortValue);
        }

        if (r > 0) {
            put(value);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fillAndReset(byte value, int size) {
        autoExpand(size);
        int pos = position();
        try {
            fill(value, size);
        } finally {
            position(pos);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fill(int size) {
        autoExpand(size);
        int q = size >>> 3;
        int r = size & 7;

        for (int i = q; i > 0; i--) {
            putLong(0L);
        }

        q = r >>> 2;
        r = r & 3;

        if (q > 0) {
            putInt(0);
        }

        q = r >> 1;
        r = r & 1;

        if (q > 0) {
            putShort((short) 0);
        }

        if (r > 0) {
            put((byte) 0);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fillAndReset(int size) {
        autoExpand(size);
        int pos = position();
        try {
            fill(size);
        } finally {
            position(pos);
        }

        return this;
    }


    /**
     * This method forwards the call to {@link #expand(int)} only when
     * <tt>autoExpand</tt> property is <tt>true</tt>.
     */
    private IoBuffer autoExpand(int expectedRemaining) {
        if (isAutoExpand()) {
            expand(expectedRemaining, true);
        }
        return this;
    }

    /**
     * This method forwards the call to {@link #expand(int)} only when
     * <tt>autoExpand</tt> property is <tt>true</tt>.
     */
    private IoBuffer autoExpand(int pos, int expectedRemaining) {
        if (isAutoExpand()) {
            expand(pos, expectedRemaining, true);
        }
        return this;
    }

    private static void checkFieldSize(int fieldSize) {
        if (fieldSize < 0) {
            throw new IllegalArgumentException("fieldSize cannot be negative: "
                    + fieldSize);
        }
    }
}