package com.gs.jrpip.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.io.InputStream;

public class CipherInputStream128 extends InputStream
{
    private final Cipher cipher;
    private InputStream in;

    private byte[] cipherBuf = new byte[16];

    private byte[] inBuf = new byte[16];

    private int inBufPos = 16;
    private boolean end;


    public CipherInputStream128(InputStream in, Cipher cipher)
    {
        this.cipher = cipher;
        this.in = in;
    }

    public void reset(InputStream in) throws IOException
    {
        this.in = in;
        this.inBufPos = 16;
        this.end = false;
        try
        {
            this.cipher.doFinal(cipherBuf, 0, 0, inBuf);
        }
        catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException e)
        {
            throw new IOException(e);
        }
    }

    @Override
    public int read() throws IOException
    {
        if (end)
        {
            return -1;
        }
        fillInBuf();
        if (end)
        {
            return -1;
        }
        return inBuf[inBufPos++];
    }

    private void fillInBuf() throws IOException
    {
        if (inBufPos < 16)
        {
            return;
        }
        if (end)
        {
            return;
        }

        int read = BlockInputStream.fullyRead(this.in, cipherBuf, 0, 16);
        if (read == -1)
        {
            this.end = true;
        }
        else if (read != 16)
        {
            throw new IOException("Couldn't read 16 bytes of data "+read);
        }
        else
        {
            try
            {
                cipher.update(cipherBuf, 0, 16, inBuf);
            }
            catch (ShortBufferException e)
            {
                throw new IOException(e);
            }
        }
        inBufPos = 0;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        int read = 0;
        if (inBufPos < 16)
        {
            int toCopy = Math.min(len, 16 - inBufPos);
            System.arraycopy(inBuf, inBufPos, b, off, toCopy);
            len -= toCopy;
            inBufPos += toCopy;
            off += toCopy;
            read += toCopy;
        }
        while(len >= 16)
        {
            int incoming = BlockInputStream.fullyRead(this.in, cipherBuf, 0, 16);
            if (incoming == -1)
            {
                break;
            }
            else if (incoming != 16)
            {
                throw new IOException("Couldn't read 16 bytes of data "+incoming);
            }
            else
            {
                try
                {
                    cipher.update(cipherBuf, 0, 16, b, off);
                    len -= 16;
                    off += 16;
                    read += 16;
                }
                catch (ShortBufferException e)
                {
                    throw new IOException(e);
                }
            }
        }
        if(len > 0)
        {
            fillInBuf();
            if (!end)
            {
                int toCopy = Math.min(len, 16 - inBufPos);
                System.arraycopy(inBuf, inBufPos, b, off, toCopy);
                inBufPos += toCopy;
                read += toCopy;
            }
        }
        if (end && read == 0)
        {
            read = -1;
        }
        return read;
    }
}
