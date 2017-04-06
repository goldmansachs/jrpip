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
import java.io.InputStream;
import java.util.HashMap;

import com.gs.jrpip.util.stream.SerialMultiplexedWriter;

public class InputStreamDemuxer
{
    private final DataInputStream inputStream;
    private final StreamProcessorFactory processorFactory;
    private final HashMap<Integer, StreamProcessor> streamProcessors = new HashMap<Integer, StreamProcessor>();

    public InputStreamDemuxer(InputStream inputStream, StreamProcessorFactory processorFactory)
    {
        try
        {
            this.inputStream = new DataInputStream(inputStream);
            this.processorFactory = processorFactory;
            int version = this.inputStream.readInt();
            if (version != SerialMultiplexedWriter.VERSION)
            {
                throw new RuntimeException("Cannot handle stream with version != " + SerialMultiplexedWriter.VERSION);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean readBytes()
    {
        try
        {
            int streamId = this.inputStream.readInt();
            if (streamId < 0)
            {
                return false;
            }
            int size = this.inputStream.readInt();
            StreamProcessor streamProcessor = this.streamProcessors.get(streamId);
            if (streamProcessor == null)
            {
                this.startNewStream(streamId, size);
            }
            else
            {
                streamProcessor.readBytes(this.inputStream, size);
                this.checkForEndOfStream(streamId, size);
            }
            return true;
        }
        catch (IOException ignore)
        {
            // this is to handle log files with non-proper file endings.
            return false;
        }
    }

    private void checkForEndOfStream(int streamId, int size)
    {
        if (size == 0)
        {
            this.streamProcessors.remove(streamId);
        }
    }

    private void startNewStream(int streamId, int size) throws IOException
    {
        StreamProcessor streamProcessor = this.processorFactory.createProcessorAndReadFirstPacket(this.inputStream, streamId, size);
        this.streamProcessors.put(streamId, streamProcessor);
    }
}
