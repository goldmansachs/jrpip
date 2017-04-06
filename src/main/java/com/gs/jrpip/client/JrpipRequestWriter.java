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

package com.gs.jrpip.client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import com.gs.jrpip.FixedDeflaterOutputStream;

public abstract class JrpipRequestWriter implements OutputStreamWriter
{
    //private static final boolean CAUSE_RANDOM_ERROR = true;
    private static final double ERROR_RATE = 0.98;

    public abstract byte getRequestType();

    public abstract void writeParameters(ObjectOutputStream objectOutputStream) throws IOException;

    @Override
    public void write(OutputStream outputStream) throws IOException
    {
        //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
        outputStream.write(this.getRequestType());
        FixedDeflaterOutputStream zipped = new FixedDeflaterOutputStream(outputStream);
        try
        {
            ObjectOutputStream out = new ObjectOutputStream(zipped);
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            this.writeParameters(out);
            out.flush();
        }
        finally
        {
            zipped.finish();
        }
    }
}
