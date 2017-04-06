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

package com.gs.jrpip.util.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyOnReadInputStream
        extends InputStream
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyOnReadInputStream.class);

    private final InputStream delegate;
    private ByteArrayOutputStream bufferedWrites = new ByteArrayOutputStream();
    private OutputStream copyTo = this.bufferedWrites;

    public CopyOnReadInputStream(InputStream delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException
    {
        int read = this.delegate.read();
        this.copyTo.write(read);
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        int readByteCount = this.delegate.read(b, off, len);
        if (readByteCount > 0)
        {
            this.copyTo.write(b, off, readByteCount);
        }
        return readByteCount;
    }

    public void startCopyingInto(OutputStream copyTo)
    {
        try
        {
            if (this.bufferedWrites != null)
            {
                copyTo.write(this.bufferedWrites.toByteArray());
                this.bufferedWrites = null;
            }
            this.copyTo = copyTo;
        }
        catch (IOException e)
        {
            LOGGER.error("Will not be copying data since exception has been reported", e);
            this.bufferedWrites = null;
            this.copyTo = VirtualOutputStream.NULL_OUTPUT_STREAM;
        }
    }

    @Override
    public void close() throws IOException
    {
        this.delegate.close();
    }
}
