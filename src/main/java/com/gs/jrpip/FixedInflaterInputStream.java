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

package com.gs.jrpip;

import java.io.IOException;
import java.io.InputStream;

import com.gs.jrpip.util.lz4.LZ4BlockInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixedInflaterInputStream extends InputStream
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedInflaterInputStream.class);

    private LZ4BlockInputStream lz4In;

    public FixedInflaterInputStream(InputStream in)
    {
        lz4In = LZ4BlockInputStream.getInstance(in);
    }

    @Override
    public int read() throws IOException
    {
        return lz4In.read();
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return lz4In.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        return lz4In.read(b, off, len);
    }

    public void finish()
    {
        this.lz4In.finish();
        this.lz4In = null;
    }
}
