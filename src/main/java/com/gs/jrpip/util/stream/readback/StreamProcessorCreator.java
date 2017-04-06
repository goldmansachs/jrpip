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
import java.util.HashSet;
import java.util.Set;

import com.gs.jrpip.util.stream.ByteArrayPool;

public class StreamProcessorCreator
{
    private static final NullStreamProcessor NULL_STREAM_PROCESSOR = new NullStreamProcessor();
    private final ByteArrayPool byteArrayPool;
    private final ByteSequenceListener byteSequenceListener;
    private final Set<Byte> supportedHeaders = new HashSet<Byte>();

    public StreamProcessorCreator(
            ByteArrayPool byteArrayPool,
            ByteSequenceListener byteSequenceListener,
            byte... supportedHeaders)
    {
        this.byteArrayPool = byteArrayPool;
        this.byteSequenceListener = byteSequenceListener;
        for (int i = 0; i < supportedHeaders.length; i++)
        {
            this.supportedHeaders.add(supportedHeaders[i]);
        }
    }

    public StreamProcessor createProcessor(
            byte requestType,
            DataInputStream inputStream,
            int streamId,
            int size)
    {
        StreamProcessor streamProcessor = NULL_STREAM_PROCESSOR;
        if (this.isHeaderSupported(requestType))
        {
            streamProcessor = new StreamProcessorImpl(streamId, this.byteArrayPool, this.byteSequenceListener);
        }
        int sizeWithoutHeaderByte = size - 1;
        if (sizeWithoutHeaderByte > 0)
        {
            streamProcessor.readBytes(inputStream, sizeWithoutHeaderByte);
        }
        return streamProcessor;
    }

    public boolean isHeaderSupported(byte header)
    {
        return this.supportedHeaders.contains(header);
    }
}
