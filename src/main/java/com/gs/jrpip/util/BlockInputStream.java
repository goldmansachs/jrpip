package com.gs.jrpip.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class BlockInputStream extends InputStream
{
    private static ThreadLocal<Blockbuf> buffers = new ThreadLocal<>();
    public static final int MAX_LENGTH = 9000; // about 4*tcp MTU
    public static final byte[] MAGIC = new byte[4];

    static
    {
        MAGIC[0] = (byte) 0x81;
        MAGIC[1] = (byte) 0xb8;
        MAGIC[2] = (byte) 0xbd;
        MAGIC[3] = (byte) 0x3f;
    }

    private InputStream in;
    private Blockbuf buffer;

    public BlockInputStream(InputStream in)
    {
        this.in = in;
    }

    public byte readByte() throws IOException
    {
        int read = this.buffer.read(this.in);
        if (read == -1)
        {
            throw new EOFException();
        }
        return (byte) (read & 0xFF);
    }

    @Override
    public int read() throws IOException
    {
        return this.buffer.read(this.in);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        return this.buffer.read(this.in, b, off, len);
    }

    @Override
    public long skip(long n) throws IOException
    {
        return this.buffer.skip(this.in, n);
    }

    @Override
    public int available() throws IOException
    {
        return this.buffer.leftToCopy();
    }

    @Override
    public void close() throws IOException
    {
        //nothing
    }

    @Override
    public synchronized void mark(int readlimit)
    {
        //nothing
    }

    @Override
    public synchronized void reset() throws IOException
    {
        throw new IOException("Mark not supported");
    }

    @Override
    public boolean markSupported()
    {
        return false;
    }

    public void beginConversation() throws IOException
    {
        this.buffer = buffers.get();
        if (buffer == null)
        {
            buffer = new Blockbuf();
            buffers.set(buffer);
        }
        this.buffer.reset();
    }

    public void endConversation() throws IOException
    {
        this.buffer.readToEnd(this.in);
    }

    private static class Blockbuf
    {
        private byte[] buf = new byte[MAX_LENGTH];
        private int totalLength;
        private int lengthRead;
        private boolean last;
        private boolean headerRead;
        private int readPos;

        public void reset()
        {
            this.totalLength = 0;
            this.lengthRead = 0;
            this.last = false;
            this.headerRead = false;
            this.readPos = 0;
        }

        public int read(InputStream in) throws IOException
        {
            readHeaderIfNot(in);
            if (last && this.readPos == this.totalLength)
            {
                return -1;
            }
            fillAvailable(in, 1);
            byte result = buf[this.readPos];
            this.readPos++;
            return ((int)result) & 0xFF;
        }

        public int read(InputStream in, byte[] buf, int off, int len) throws IOException
        {
            int curOff = off;
            int lenLeft = len;
            int actualCopied = 0;
            readHeaderIfNot(in);
            while(lenLeft > 0 && !(last && this.readPos == this.totalLength))
            {
                if (this.readPos == this.totalLength)
                {
                    this.reset();
                    this.readHeaderIfNot(in);
                    continue;
                }
                fillAvailable(in, lenLeft);
                int toCopy = leftToCopy();
                if (lenLeft <= leftToCopy())
                {
                    toCopy = lenLeft;
                }
                System.arraycopy(this.buf, this.readPos, buf, curOff, toCopy);
                this.readPos += toCopy;
                curOff += toCopy;
                lenLeft -= toCopy;
                actualCopied += toCopy;
            }
            return actualCopied;
        }

        private void fillAvailable(InputStream in, long lenLeft) throws IOException
        {
            if (lenLeft < leftToCopy())
            {
                return;
            }
            int localSpace = leftToFill();
            if (localSpace == 0)
            {
                return;
            }
            int avail = in.available();
            if (avail == 0)
            {
                avail = 128;
            }
            int toFill = Math.min(localSpace, avail);
            fullyRead(in, this.buf, this.lengthRead, toFill);
            this.lengthRead += toFill;
        }

        private int leftToFill()
        {
            return this.totalLength - this.lengthRead;
        }

        private int leftToCopy()
        {
            return this.lengthRead - this.readPos;
        }

        public void readToEnd(InputStream in) throws IOException
        {
            while(true)
            {
                readHeaderIfNot(in);
                if (this.lengthRead != this.totalLength)
                {
                    fullyReadBlock(in);
                }
                if (this.last)
                {
                    return;
                }
                this.reset();
            }
        }

        private void fullyReadBlock(InputStream in) throws IOException
        {
            int toRead = leftToFill();
            int read = fullyRead(in, this.buf, this.lengthRead, toRead);
            if (read != toRead)
            {
                throw new BlockCorruptException("Could not read to the end of the block");
            }
        }

        private void readHeaderIfNot(InputStream in) throws IOException
        {
            if (headerRead)
            {
                return;
            }
            readMagic(in);
            readTotalLength(in);
            this.headerRead = true;
            if (in.available() > 0)
            {
                int localSpace = leftToFill();
                int avail = in.available();
                int toFill = Math.min(localSpace, avail);
                fullyRead(in, this.buf, this.lengthRead, toFill);
                this.lengthRead += toFill;
            }
        }

        private void readTotalLength(InputStream in) throws IOException
        {
            int one = in.read();
            if (one < 0)
            {
                throw new BlockCorruptException("early EOF");
            }
            int two = in.read();
            if (two < 0)
            {
                throw new BlockCorruptException("early EOF");
            }
            if ((one & (1 << 7)) != 0)
            {
                this.last = true;
                one &= ~(1 << 7);
            }
            this.totalLength = (one << 8) | two;
        }

        private void readMagic(InputStream in) throws IOException
        {
            for(int i=0;i<4;i++)
            {
                int r = in.read();
                if (r == -1)
                {
                    throw new EOFException();
                }
                byte read = (byte) r;
                if (read != MAGIC[i])
                {
                    throw new BlockCorruptException("Bad magic value "+read);
                }
            }
        }

        public long skip(InputStream in, long n) throws IOException
        {
            long lenLeft = n;
            int actualSkipped = 0;
            readHeaderIfNot(in);
            while(lenLeft > 0 && !(last && this.readPos == this.totalLength))
            {
                if (this.readPos == this.totalLength)
                {
                    this.reset();
                    this.readHeaderIfNot(in);
                    continue;
                }
                fillAvailable(in, lenLeft);
                long toSkip = leftToCopy();
                if (lenLeft <= leftToCopy())
                {
                    toSkip = lenLeft;
                }
                this.readPos += (int) toSkip;
                lenLeft -= toSkip;
                actualSkipped += toSkip;
            }
            return actualSkipped;

        }
    }

    public static int fullyRead(InputStream in, byte[] b, int off, int len) throws IOException
    {
        int read = 0;
        while (read < len) {
            int count = in.read(b, off + read, len - read);
            if (count < 0)
                break;
            read += count;
        }
        return read;
    }

}
