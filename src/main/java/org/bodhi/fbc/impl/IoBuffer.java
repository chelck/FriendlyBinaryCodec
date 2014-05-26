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
package org.bodhi.fbc.impl;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;


/**
 * A byte buffer used by MINA applications.
 * <p>
 * This is a replacement for {@link ByteBuffer}. Please refer to
 * {@link ByteBuffer} documentation for preliminary usage. MINA does not use NIO
 * {@link ByteBuffer} directly for two reasons:
 * <ul>
 * <li>It doesn't provide useful getters and putters such as <code>fill</code>,
 * <code>get/putString</code>, and <code>get/putAsciiInt()</code> enough.</li>
 * <li>It is difficult to write variable-length data due to its fixed capacity</li>
 * </ul>
 * </p>
 * 
 * <h2>Allocation</h2>
 * <p>
 * You can allocate a new heap buffer.
 * 
 * <pre>
 * IoBuffer buf = IoBuffer.allocate(1024);
 * </pre>
 * 
 *
 * </p>
 * 
 * <h2>Wrapping existing NIO buffers and arrays</h2>
 * <p>
 * This class provides a few <tt>wrap(...)</tt> methods that wraps any NIO
 * buffers and byte arrays.
 * 
 * <h2>AutoExpand</h2>
 * <p>
 * Writing variable-length data using NIO <tt>ByteBuffers</tt> is not really
 * easy, and it is because its size is fixed. {@link IoBuffer} introduces
 * <tt>autoExpand</tt> property. If <tt>autoExpand</tt> property is true, you
 * never get {@link BufferOverflowException} or
 * {@link IndexOutOfBoundsException} (except when index is negative). It
 * automatically expands its capacity and limit value. For example:
 * 
 * <pre>
 * String greeting = messageBundle.getMessage(&quot;hello&quot;);
 * IoBuffer buf = IoBuffer.allocate(16);
 * // Turn on autoExpand (it is off by default)
 * buf.setAutoExpand(true);
 * buf.putString(greeting, utf8encoder);
 * </pre>
 * 
 * The underlying {@link ByteBuffer} is reallocated by {@link IoBuffer} behind
 * the scene if the encoded data is larger than 16 bytes in the example above.
 * Its capacity will double, and its limit will increase to the last position
 * the string is written.
 * </p>
 * 
 *
 */

public abstract class IoBuffer implements Comparable<IoBuffer> {
    /** The allocator used to create new buffers */
    private static final IoBufferAllocator allocator = new SimpleBufferAllocator();

    /**
     * Returns the allocator used by existing and new buffers
     */
    public static IoBufferAllocator getAllocator() {
        return allocator;
    }

    /**
     * Returns the buffer which is capable of the specified size.
     * 
     * @param capacity
     *            the capacity of the buffer
     */
    public static IoBuffer allocate(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity: " + capacity);
        }

        return allocator.allocate(capacity);
    }

    /**
     * Wraps the specified NIO {@link ByteBuffer} into MINA buffer.
     */
    public static IoBuffer wrap(ByteBuffer nioBuffer) {
        return allocator.wrap(nioBuffer);
    }

    /**
     * Wraps the specified byte array into MINA heap buffer.
     */
    public static IoBuffer wrap(byte[] byteArray) {
        return wrap(ByteBuffer.wrap(byteArray));
    }

    /**
     * Normalizes the specified capacity of the buffer to power of 2, which is
     * often helpful for optimal memory usage and performance. If it is greater
     * than or equal to {@link Integer#MAX_VALUE}, it returns
     * {@link Integer#MAX_VALUE}. If it is zero, it returns zero.
     */
    protected static int normalizeCapacity(int requestedCapacity) {
        if (requestedCapacity < 0) {
            return Integer.MAX_VALUE;
        }

        int newCapacity = Integer.highestOneBit(requestedCapacity);
        newCapacity <<= (newCapacity < requestedCapacity ? 1 : 0);
        return newCapacity < 0 ? Integer.MAX_VALUE : newCapacity;
    }

    /**
     * Creates a new instance. This is an empty constructor.
     */
    protected IoBuffer() {
        // Do nothing
    }

    /**
     * Returns the underlying NIO buffer instance.
     */
    public abstract ByteBuffer buf();

    /**
     * @see java.nio.Buffer#position()
     */
    public abstract int position();

    /**
     * @see java.nio.Buffer#position(int)
     */
    public abstract IoBuffer position(int newPosition);

    /**
     * @see java.nio.Buffer#limit()
     */
    public abstract int limit();

    /**
     * @see java.nio.Buffer#limit(int)
     */
    public abstract IoBuffer limit(int newLimit);

    /**
     * @see java.nio.Buffer#reset()
     */
    public abstract IoBuffer reset();

    /**
     * @see java.nio.Buffer#clear()
     */
    public abstract IoBuffer clear();

    /**
     * @see java.nio.Buffer#flip()
     */
    public abstract IoBuffer flip();

    /**
     * @see java.nio.Buffer#rewind()
     */
    public abstract IoBuffer rewind();

    /**
     * @see java.nio.Buffer#remaining()
     */
    public abstract int remaining();

    /**
     * @see java.nio.Buffer#hasRemaining()
     */
    public abstract boolean hasRemaining();

    /**
     * @see ByteBuffer#array()
     */
    public abstract byte[] array();

    /**
     * @see ByteBuffer#get()
     */
    public abstract byte get();

    /**
     * Reads one unsigned byte as a short integer.
     */
    public abstract short getUnsigned();

    /**
     * @see ByteBuffer#put(byte)
     */
    public abstract IoBuffer put(byte b);

    /**
     * @see ByteBuffer#get(int)
     */
    public abstract byte get(int index);

    /**
     * Reads one byte as an unsigned short integer.
     */
    public abstract short getUnsigned(int index);

    /**
     * @see ByteBuffer#put(int, byte)
     */
    public abstract IoBuffer put(int index, byte b);

    /**
     * @see ByteBuffer#get(byte[], int, int)
     */
    public abstract IoBuffer get(byte[] dst, int offset, int length);

    /**
     * @see ByteBuffer#get(byte[])
     */
    public abstract IoBuffer get(byte[] dst);

     /**
     * @see ByteBuffer#put(byte[], int, int)
     */
    public abstract IoBuffer put(byte[] src, int offset, int length);

    /**
     * @see ByteBuffer#put(byte[])
     */
    public abstract IoBuffer put(byte[] src);

    /**
     * @see ByteBuffer#order()
     */
    public abstract ByteOrder order();

    /**
     * @see ByteBuffer#order(ByteOrder)
     */
    public abstract IoBuffer order(ByteOrder bo);

    /**
     * @see ByteBuffer#getChar()
     */
    public abstract char getChar();

    /**
     * @see ByteBuffer#putChar(char)
     */
    public abstract IoBuffer putChar(char value);

    /**
     * @see ByteBuffer#getChar(int)
     */
    public abstract char getChar(int index);

    /**
     * @see ByteBuffer#putChar(int, char)
     */
    public abstract IoBuffer putChar(int index, char value);

    /**
     * @see ByteBuffer#asCharBuffer()
     */
    public abstract CharBuffer asCharBuffer();

    /**
     * @see ByteBuffer#getShort()
     */
    public abstract short getShort();

    /**
     * Reads two bytes unsigned integer.
     */
    public abstract int getUnsignedShort();

    /**
     * @see ByteBuffer#putShort(short)
     */
    public abstract IoBuffer putShort(short value);

    /**
     * @see ByteBuffer#getShort()
     */
    public abstract short getShort(int index);

    /**
     * Reads two bytes unsigned integer.
     */
    public abstract int getUnsignedShort(int index);

    /**
     * @see ByteBuffer#putShort(int, short)
     */
    public abstract IoBuffer putShort(int index, short value);

    /**
     * @see ByteBuffer#getInt()
     */
    public abstract int getInt();

    /**
     * Reads four bytes unsigned integer.
     */
    public abstract long getUnsignedInt();


    /**
     * @see ByteBuffer#putInt(int)
     */
    public abstract IoBuffer putInt(int value);

    /**
     * @see ByteBuffer#getInt(int)
     */
    public abstract int getInt(int index);

    /**
     * Reads four bytes unsigned integer.
     */
    public abstract long getUnsignedInt(int index);

    /**
     * @see ByteBuffer#putInt(int, int)
     */
    public abstract IoBuffer putInt(int index, int value);

    /**
     * @see ByteBuffer#getLong()
     */
    public abstract long getLong();

    /**
     * @see ByteBuffer#putLong(int, long)
     */
    public abstract IoBuffer putLong(long value);

    /**
     * @see ByteBuffer#getLong(int)
     */
    public abstract long getLong(int index);

    /**
     * @see ByteBuffer#putLong(int, long)
     */
    public abstract IoBuffer putLong(int index, long value);

    // ////////////////////////
    // Skip or fill methods //
    // ////////////////////////

    /**
     * Forwards the position of this buffer as the specified <code>size</code>
     * bytes.
     */
    public abstract IoBuffer skip(int size);


}