package com.gs.jrpip.util;

import java.io.IOException;
import java.io.OutputStream;

public class BlockOutputStream extends OutputStream
{
    private static ThreadLocal<BlockWriteBuffer> buffers = new ThreadLocal<>();

    private OutputStream out;
    private BlockWriteBuffer buffer;

    public BlockOutputStream(OutputStream out)
    {
        this.out = out;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        this.buffer.write(this.out, b, off, len);
    }

    @Override
    public void write(int b) throws IOException
    {
        this.buffer.write(this.out, (byte)(b & 0xFF));
    }

    public void beginConversation()
    {
        this.buffer = buffers.get();
        if (buffer == null)
        {
            buffer = new BlockWriteBuffer();
            buffers.set(buffer);
        }
        this.buffer.reset();
    }

    public void endConversation() throws IOException
    {
        this.buffer.writeFinalBlock(this.out);
    }

    public void writeLong(long v) throws IOException
    {
        this.buffer.write(out, (byte)(v >>> 56));
        this.buffer.write(out, (byte)(v >>> 48));
        this.buffer.write(out, (byte)(v >>> 40));
        this.buffer.write(out, (byte)(v >>> 32));
        this.buffer.write(out, (byte)(v >>> 24));
        this.buffer.write(out, (byte)(v >>> 16));
        this.buffer.write(out, (byte)(v >>>  8));
        this.buffer.write(out, (byte) v);
    }

    public void writeInt(int v) throws IOException
    {
        this.buffer.write(out, (byte)(v >>> 24));
        this.buffer.write(out, (byte)(v >>> 16));
        this.buffer.write(out, (byte)(v >>>  8));
        this.buffer.write(out, (byte) v);
    }

    private static class BlockWriteBuffer
    {
        private byte[] buf = new byte[BlockInputStream.MAX_LENGTH];
        private int written;

        public void reset()
        {
            this.written = 0;
        }

        public void write(OutputStream out, byte b) throws IOException
        {
            if (buf.length == written)
            {
                writeToOutput(out, false);
            }
            buf[written] = b;
            written++;
        }

        public void write(OutputStream out, byte[] b, int off, int len) throws IOException
        {
            int leftToWrite = len;
            int copied = 0;
            while(leftToWrite > 0)
            {
                int spaceLeft = buf.length - written;
                if (leftToWrite <= spaceLeft)
                {
                    System.arraycopy(b, off + copied, buf, written, leftToWrite);
                    written += leftToWrite;
                    copied += leftToWrite;
                    leftToWrite = 0;
                }
                else
                {
                    System.arraycopy(b, off + copied, buf, written, spaceLeft);
                    written += spaceLeft;
                    copied += spaceLeft;
                    leftToWrite -= spaceLeft;
                    writeToOutput(out, false);
                }
            }

        }

        private void writeToOutput(OutputStream out, boolean last) throws IOException
        {
            out.write(BlockInputStream.MAGIC);
            int high = written >> 8;
            if (last)
            {
                high |= (1 << 7);
            }
            out.write(high);
            out.write(written & 0xFF);
            out.write(buf, 0, written);
            written = 0;
        }

        public void writeFinalBlock(OutputStream out) throws IOException
        {
            writeToOutput(out, true);
        }
    }
}
