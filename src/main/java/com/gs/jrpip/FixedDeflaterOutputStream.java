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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.gs.jrpip.util.lz4.LZ4BlockOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixedDeflaterOutputStream extends OutputStream
{
    /**
     * The value here should be 0 for no compression, 9 for maximum compression, or Deflater.DEFAULT_COMPRESSION
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedDeflaterOutputStream.class);

    private LZ4BlockOutputStream lz4Out;

    public FixedDeflaterOutputStream(OutputStream out)
    {
        this.lz4Out = LZ4BlockOutputStream.getInstance(out);
    }

    @Override
    public void write(int b) throws IOException
    {
        this.lz4Out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        this.lz4Out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException
    {
        this.lz4Out.flush();
    }

    /**
     * finishes writing to the stream and frees up memory allocated by native library. No more calls can be made to this stream.
     */
    public void finish() throws IOException
    {
        this.lz4Out.finish();
        this.lz4Out = null;
    }

    public static Logger getLogger()
    {
        return LOGGER;
    }
}
