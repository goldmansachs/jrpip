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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DedicatedOutputStream implements MultiOutputStreamBuilder
{
    private final VirtualOutputStream virtualOutputStream = new VirtualOutputStream(this);
    private volatile SingleStreamOutputWriter outputWriter;
    private volatile MultiOutputStream multiOutputStream;

    @Override
    public MultiOutputStream buildForExceptionHandler(ExceptionHandler exceptionHandler)
    {
        this.outputWriter = new SingleStreamOutputWriter(this.virtualOutputStream);
        this.multiOutputStream = new MultiOutputStream(this.outputWriter,
                MultiOutputStream.DEFAULT_FLUSH_BUFFER_SIZE_BYTES);
        return this.multiOutputStream;
    }

    public OutputStream dedicatedFor(OutputStream resultStream) throws IOException
    {
        MultiOutputStream.BufferingOutputStream bufferingOutputStream = this.multiOutputStream.newBufferingOutputStream();
        this.outputWriter.register(bufferingOutputStream.getStreamId(), new DataOutputStream(resultStream));
        return bufferingOutputStream;
    }
}
