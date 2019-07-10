package com.gs.jrpip.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.io.OutputStream;

public class CipherOutputStream128 extends OutputStream
{
    private final Cipher cipher;
    private OutputStream out;

    private byte[] cipherBuf = new byte[16];

    private byte[] outBuf = new byte[16];

    private int outBufPos;

    public CipherOutputStream128(OutputStream out, Cipher cipher) throws IOException
    {
        this.cipher = cipher;
        if (this.cipher.getOutputSize(16) != 16)
        {
            throw new IOException("Bad cipher output size! "+ this.cipher.getOutputSize(16));
        }
        this.out = out;
    }

    public void reset(OutputStream newOut)
    {
        this.out = newOut;
    }

    @Override
    public void write(int b) throws IOException
    {
        outBuf[outBufPos++] = (byte) b;
        flush16();
    }

    private void flush16() throws IOException
    {
        if (outBufPos == 16)
        {
            encode16(this.outBuf, 0);
            outBufPos = 0;
        }
    }

    private void encode16(byte[] buf, int off) throws IOException
    {
        try
        {
            int encrypted = cipher.update(buf, off, 16, cipherBuf);
            if (encrypted != 16)
            {
                throw new IOException("too few bytes");
            }
            this.out.write(cipherBuf);
        }
        catch (ShortBufferException e)
        {
            throw new IOException(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        if (outBufPos + len <= 16)
        {
            writeSmallBuffer(b, off, len);
            return;
        }
        int toCopy = 16 - outBufPos;
        System.arraycopy(b, off, outBuf, outBufPos, toCopy);
        len -= toCopy;
        off += toCopy;
        outBufPos = 16;
        flush16();
        while(len >= 16)
        {
            encode16(b, off);
            off += 16;
            len -= 16;
        }
        writeSmallBuffer(b, off, len);
    }

    private void writeSmallBuffer(byte[] b, int off, int len) throws IOException
    {
        System.arraycopy(b, off, outBuf, outBufPos, len);
        outBufPos += len;
        flush16();
    }

    public void finish() throws IOException
    {
        try
        {
            if (outBufPos > 0)
            {
                cipher.doFinal(this.outBuf, 0, 16, this.cipherBuf); // writes some junk at the end.
                this.out.write(cipherBuf);
                outBufPos = 0;
            }
            else
            {
                int toWrite = cipher.doFinal(this.cipherBuf, 0); //expected to return null
                if (toWrite != 0)
                {
                    throw new IOException("Where did these bytes come from?");
                }
            }
        }
        catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException e)
        {
            throw new IOException(e);
        }
    }
}
