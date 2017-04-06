/*
 Copyright 2017 Goldman Sachs.
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

/*
    Copyright Adrien Grand (based on Yann Collet's BSD licensed LZ4 implementation)
    changes copyright Goldman Sachs, licensed under Apache 2.0 license
*/
package com.gs.jrpip.util.lz4;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static com.gs.jrpip.util.lz4.LZ4Utils.*;

/**
 * Streaming LZ4.
 * <p/>
 * This class compresses data into fixed-size blocks of compressed data.
 *
 * @see net.jpountz.lz4.LZ4BlockInputStream
 */
public final class LZ4BlockOutputStream extends FilterOutputStream
{
    private static final int MAX_INSTANCES = 10;

    private static final ArrayList<LZ4BlockOutputStream> INSTANCES = new ArrayList<LZ4BlockOutputStream>();

    public static LZ4BlockOutputStream getInstance(OutputStream out)
    {
        synchronized (INSTANCES)
        {
            if (!INSTANCES.isEmpty())
            {
                LZ4BlockOutputStream result = INSTANCES.remove(INSTANCES.size() - 1);
                result.reset(out);
                return result;
            }
        }
        return new LZ4BlockOutputStream(out, true);
    }


    static final byte[] MAGIC = new byte[]{'L', 'Z', '4', 'B', 'l', 'o', 'c', 'k'};
    static final int MAGIC_LENGTH = MAGIC.length;

    static final int HEADER_LENGTH =
            MAGIC_LENGTH // magic bytes
                    + 1          // token
                    + 4          // compressed length
                    + 4          // decompressed length
                    + 4;         // checksum

    static final int COMPRESSION_LEVEL_BASE = 10;
    static final int MIN_BLOCK_SIZE = 64;
    static final int MAX_BLOCK_SIZE = 1 << (COMPRESSION_LEVEL_BASE + 0x0F);

    static final int COMPRESSION_METHOD_RAW = 0x10;
    static final int COMPRESSION_METHOD_LZ4 = 0x20;

    static final int DEFAULT_SEED = 0x9747b28c;

    private static int compressionLevel(int blockSize)
    {
        if (blockSize < MIN_BLOCK_SIZE)
        {
            throw new IllegalArgumentException("blockSize must be >= " + MIN_BLOCK_SIZE + ", got " + blockSize);
        }
        else if (blockSize > MAX_BLOCK_SIZE)
        {
            throw new IllegalArgumentException("blockSize must be <= " + MAX_BLOCK_SIZE + ", got " + blockSize);
        }
        int compressionLevel = 32 - Integer.numberOfLeadingZeros(blockSize - 1); // ceil of log2
        assert (1 << compressionLevel) >= blockSize;
        assert blockSize * 2 > (1 << compressionLevel);
        compressionLevel = Math.max(0, compressionLevel - COMPRESSION_LEVEL_BASE);
        assert compressionLevel >= 0 && compressionLevel <= 0x0F;
        return compressionLevel;
    }

    private final int blockSize;
    private final int compressionLevel;
    private final Checksum checksum;
    private final byte[] buffer;
    private final byte[] compressedBuffer;
    private final boolean syncFlush;
    private boolean finished;
    private int o;
    private final short[] hashTable = new short[HASH_TABLE_SIZE_64K];


    /**
     * Create a new {@link java.io.OutputStream} with configurable block size. Large
     * blocks require more memory at compression and decompression time but
     * should improve the compression ratio.
     *
     * @param out        the {@link java.io.OutputStream} to feed
     * @param blockSize  the maximum number of bytes to try to compress at once,
     *                   must be >= 64 and <= 32 M
     * @param syncFlush  true if pending data should also be flushed on {@link #flush()}
     */
    public LZ4BlockOutputStream(OutputStream out, boolean syncFlush)
    {
        super(out);
        this.blockSize = 64*1024;
        this.checksum = new Adler32();
        this.compressionLevel = compressionLevel(blockSize);
        this.buffer = new byte[blockSize];
        final int compressedBlockSize = HEADER_LENGTH + maxCompressedLength(blockSize);
        this.compressedBuffer = new byte[compressedBlockSize];
        this.syncFlush = syncFlush;
        o = 0;
        finished = false;
        System.arraycopy(MAGIC, 0, compressedBuffer, 0, MAGIC_LENGTH);
    }

    private static final int maxCompressedLength(int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        return length + length / 255 + 16;
    }

    public void reset(OutputStream out)
    {
        this.out = out;
        Arrays.fill(buffer, (byte) 0);
        Arrays.fill(compressedBuffer, (byte) 0);
        o = 0;
        finished = false;
        System.arraycopy(MAGIC, 0, compressedBuffer, 0, MAGIC_LENGTH);
    }

    private void ensureNotFinished()
    {
        if (finished)
        {
            throw new IllegalStateException("This stream is already closed");
        }
    }

    @Override
    public void write(int b) throws IOException
    {
        ensureNotFinished();
        if (o == blockSize)
        {
            flushBufferedData();
        }
        buffer[o++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        ensureNotFinished();

        while (o + len > blockSize)
        {
            final int l = blockSize - o;
            System.arraycopy(b, off, buffer, o, blockSize - o);
            o = blockSize;
            flushBufferedData();
            off += l;
            len -= l;
        }
        System.arraycopy(b, off, buffer, o, len);
        o += len;
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        ensureNotFinished();
        write(b, 0, b.length);
    }

    @Override
    public void close() throws IOException
    {
        if (finished)
        {
            return;
        }
        ensureNotFinished();
        finish();
        out.close();
    }

    private void flushBufferedData() throws IOException
    {
        if (o == 0)
        {
            return;
        }
        checksum.reset();
        checksum.update(buffer, 0, o);
        final int check = (int) checksum.getValue();
        int compressedLength = compress64k(buffer, 0, o, compressedBuffer, HEADER_LENGTH);
        final int compressMethod;
        if (compressedLength >= o)
        {
            compressMethod = COMPRESSION_METHOD_RAW;
            compressedLength = o;
            System.arraycopy(buffer, 0, compressedBuffer, HEADER_LENGTH, o);
        }
        else
        {
            compressMethod = COMPRESSION_METHOD_LZ4;
        }

        compressedBuffer[MAGIC_LENGTH] = (byte) (compressMethod | compressionLevel);
        writeIntLE(compressedLength, compressedBuffer, MAGIC_LENGTH + 1);
        writeIntLE(o, compressedBuffer, MAGIC_LENGTH + 5);
        writeIntLE(check, compressedBuffer, MAGIC_LENGTH + 9);
        assert MAGIC_LENGTH + 13 == HEADER_LENGTH;
        out.write(compressedBuffer, 0, HEADER_LENGTH + compressedLength);
        o = 0;
    }

    /**
     * Flush this compressed {@link java.io.OutputStream}.
     * <p/>
     * If the stream has been created with <code>syncFlush=true</code>, pending
     * data will be compressed and appended to the underlying {@link java.io.OutputStream}
     * before calling {@link java.io.OutputStream#flush()} on the underlying stream.
     * Otherwise, this method just flushes the underlying stream, so pending
     * data might not be available for reading until {@link #finish()} or
     * {@link #close()} is called.
     */
    @Override
    public void flush() throws IOException
    {
        if (syncFlush)
        {
            flushBufferedData();
        }
        out.flush();
    }

    /**
     * Same as {@link #close()} except that it doesn't close the underlying stream.
     * This can be useful if you want to keep on using the underlying stream.
     */
    public void finish() throws IOException
    {
        ensureNotFinished();
        flushBufferedData();
        compressedBuffer[MAGIC_LENGTH] = (byte) (COMPRESSION_METHOD_RAW | compressionLevel);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 1);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 5);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 9);
        assert MAGIC_LENGTH + 13 == HEADER_LENGTH;
        out.write(compressedBuffer, 0, HEADER_LENGTH);
        finished = true;
        out.flush();
        synchronized (INSTANCES)
        {
            if (INSTANCES.size() < MAX_INSTANCES)
            {
                INSTANCES.add(this);
            }
        }
    }

    private static void writeIntLE(int i, byte[] buf, int off)
    {
        buf[off++] = (byte) i;
        buf[off++] = (byte) (i >>> 8);
        buf[off++] = (byte) (i >>> 16);
        buf[off++] = (byte) (i >>> 24);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "(out=" + out + ", blockSize=" + blockSize
                + ", checksum=" + checksum + ")";
    }

    public static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();

    static final int HASH_LOG = MEMORY_USAGE - 2;
    static final int HASH_LOG_64K = HASH_LOG + 1;
    static final int HASH_TABLE_SIZE_64K = 1 << HASH_LOG_64K;
    static final int NOT_COMPRESSIBLE_DETECTION_LEVEL = 6;
    static final int SKIP_STRENGTH = Math.max(NOT_COMPRESSIBLE_DETECTION_LEVEL, 2);

    private int compress64k(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff)
    {
        final int srcEnd = srcOff + srcLen;
        final int srcLimit = srcEnd - LAST_LITERALS;
        final int mflimit = srcEnd - MF_LIMIT;

        int sOff = srcOff, dOff = destOff;

        int anchor = sOff;

        if (srcLen >= MIN_LENGTH)
        {
            Arrays.fill(hashTable, (short) 0);
            ++sOff;

            main:
            while (true)
            {

                // find a match
                int forwardOff = sOff;

                int ref;
                int findMatchAttempts = (1 << SKIP_STRENGTH) + 3;
                do
                {
                    sOff = forwardOff;
                    forwardOff += findMatchAttempts++ >>> SKIP_STRENGTH;

                    if (forwardOff > mflimit)
                    {
                        break main;
                    }

                    final int h = hash64k(readInt(src, sOff));
                    ref = srcOff + readShort(hashTable, h);
                    writeShort(hashTable, h, sOff - srcOff);
                }
                while (!readIntEquals(src, ref, sOff));

                // catch up
                final int excess = commonBytesBackward(src, ref, sOff, srcOff, anchor);
                sOff -= excess;
                ref -= excess;

                // sequence == refsequence
                final int runLen = sOff - anchor;

                // encode literal length
                int tokenOff = dOff++;

                if (runLen >= RUN_MASK)
                {
                    writeByte(dest, tokenOff, RUN_MASK << ML_BITS);
                    dOff = writeLen(runLen - RUN_MASK, dest, dOff);
                }
                else
                {
                    writeByte(dest, tokenOff, runLen << ML_BITS);
                }

                // copy literals
                wildArraycopy(src, anchor, dest, dOff, runLen);
                dOff += runLen;

                while (true)
                {
                    // encode offset
                    writeShortLittleEndian(dest, dOff, (short) (sOff - ref));
                    dOff += 2;

                    // count nb matches
                    sOff += MIN_MATCH;
                    ref += MIN_MATCH;
                    final int matchLen = commonBytes(src, ref, sOff, srcLimit);
                    sOff += matchLen;

                    // encode match len
                    if (matchLen >= ML_MASK)
                    {
                        writeByte(dest, tokenOff, dest[tokenOff] | ML_MASK);
                        dOff = writeLen(matchLen - ML_MASK, dest, dOff);
                    }
                    else
                    {
                        writeByte(dest, tokenOff, dest[tokenOff] | matchLen);
                    }

                    // test end of chunk
                    if (sOff > mflimit)
                    {
                        anchor = sOff;
                        break main;
                    }

                    // fill table
                    writeShort(hashTable, hash64k(readInt(src, sOff - 2)), sOff - 2 - srcOff);

                    // test next position
                    final int h = hash64k(readInt(src, sOff));
                    ref = srcOff + readShort(hashTable, h);
                    writeShort(hashTable, h, sOff - srcOff);

                    if (!readIntEquals(src, sOff, ref))
                    {
                        break;
                    }

                    tokenOff = dOff++;
                    dest[tokenOff] = 0;
                }

                // prepare next loop
                anchor = sOff++;
            }
        }

        dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff);
        return dOff - destOff;
    }

    private static int writeLen(int len, byte[] dest, int dOff)
    {
        while (len >= 0xFF)
        {
            dest[dOff++] = (byte) 0xFF;
            len -= 0xFF;
        }
        dest[dOff++] = (byte) len;
        return dOff;
    }

    private static void writeByte(byte[] dest, int tokenOff, int i)
    {
        dest[tokenOff] = (byte) i;
    }

    private static void writeShort(short[] buf, int off, int v)
    {
        buf[off] = (short) v;
    }

    private static int readShort(short[] buf, int off)
    {
        return buf[off] & 0xFFFF;
    }

    private static int readInt(byte[] buf, int i)
    {
        if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN)
        {
            return readIntBE(buf, i);
        }
        else
        {
            return readIntLE(buf, i);
        }
    }

    private static void writeShortLittleEndian(byte[] buf, int off, int v)
    {
        buf[off++] = (byte) v;
        buf[off++] = (byte) (v >>> 8);
    }

    private static int hash64k(int i)
    {
        return (i * -1640531535) >>> ((MIN_MATCH * 8) - HASH_LOG_64K);
    }

    private static boolean readIntEquals(byte[] buf, int i, int j)
    {
        return buf[i] == buf[j] && buf[i + 1] == buf[j + 1] && buf[i + 2] == buf[j + 2] && buf[i + 3] == buf[j + 3];
    }

    private static int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff)
    {
        final int runLen = srcLen;

        if (runLen >= RUN_MASK)
        {
            dest[dOff++] = (byte) (RUN_MASK << ML_BITS);
            dOff = writeLen(runLen - RUN_MASK, dest, dOff);
        }
        else
        {
            dest[dOff++] = (byte) (runLen << ML_BITS);
        }
        // copy literals
        System.arraycopy(src, sOff, dest, dOff, runLen);
        dOff += runLen;

        return dOff;
    }
}
