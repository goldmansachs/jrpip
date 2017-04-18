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

package com.gs.jrpip.util.stream.readback;

import java.io.DataInputStream;
import java.io.IOException;

import com.gs.jrpip.util.stream.ByteArrayPool;
import com.gs.jrpip.util.stream.OutputStreamBuilder;

public class RequestStreamProcessorFactory implements StreamProcessorFactory
{
    private final StreamProcessorCreator streamProcessorCreator;

    public RequestStreamProcessorFactory(ByteArrayPool byteArrayPool, ByteSequenceListener byteSequenceListener)
    {
        this.streamProcessorCreator = new StreamProcessorCreator(byteArrayPool, byteSequenceListener, OutputStreamBuilder.REQUEST_HEADER);
    }

    @Override
    public StreamProcessor createProcessorAndReadFirstPacket(
            DataInputStream inputStream,
            int streamId,
            int size) throws IOException
    {
        byte requestType = inputStream.readByte();
        return this.streamProcessorCreator.createProcessor(requestType, inputStream, streamId, size);
    }
}
