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
import java.io.InputStream;

public class ClampedInputStream extends InputStream
{
    private final InputStream in;
    private final int maxLength;
    private int readSoFar;

    public ClampedInputStream(InputStream in, int maxLength)
    {
        this.in = in;
        this.maxLength = maxLength;
    }

    @Override
    public int read() throws IOException
    {
        if (this.readSoFar >= this.maxLength)
        {
            return -1;
        }
        int result = this.in.read();
        if (result >= 0)
        {
            this.readSoFar++;
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (len == 0)
        {
            return 0;
        }
        if (this.readSoFar >= this.maxLength)
        {
            return -1;
        }
        int toRead = len;
        if (toRead + this.readSoFar > this.maxLength)
        {
            toRead = this.maxLength - this.readSoFar;
        }
        int read = this.in.read(b, off, toRead);
        if (read > 0)
        {
            this.readSoFar += read;
        }
        return read;
    }
}
