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

import java.io.IOException;
import java.io.OutputStream;

public class ByteCountingOutputStream extends OutputStream
{
    private final OutputStream dest;
    private long bytesWritten;

    public ByteCountingOutputStream(OutputStream dest)
    {
        this.dest = dest;
    }

    public boolean hasReachedSize(long size)
    {
        return this.bytesWritten >= size;
    }

    @Override
    public void write(int b) throws IOException
    {
        this.dest.write(b);
        this.bytesWritten++;
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        this.dest.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        this.dest.write(b, off, len);
        this.bytesWritten += len;
    }

    @Override
    public void flush() throws IOException
    {
        this.dest.flush();
    }

    @Override
    public void close() throws IOException
    {
        this.dest.close();
    }
}
