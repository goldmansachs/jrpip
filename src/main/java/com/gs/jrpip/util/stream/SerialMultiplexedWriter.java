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
import java.util.HashSet;

public class SerialMultiplexedWriter
{
    public static final int VERSION = 1;
    private final long maxSizeBytes;
    private final DataOutputStream outputStream;
    private final ByteCountingOutputStream byteCountingOutputStream;
    private final HashSet<Integer> activeStreams = new HashSet<Integer>();
    private final ExceptionHandler exceptionHandler;

    public SerialMultiplexedWriter(OutputStream outputStream, int maxSizeMegabytes, ExceptionHandler exceptionHandler)
    {
        this.maxSizeBytes = maxSizeMegabytes * 1024 * 1024;
        this.byteCountingOutputStream = new ByteCountingOutputStream(outputStream);
        this.outputStream = new DataOutputStream(this.byteCountingOutputStream);
        this.exceptionHandler = exceptionHandler;
    }

    public void writeVersion()
    {
        try
        {
            this.outputStream.writeInt(VERSION);
        }
        catch (IOException e)
        {
            this.exceptionHandler.handle(e);
        }
    }

    public void writePacket(Packet packet)
    {
        try
        {
            packet.write(this.outputStream);
            this.updateActiveStreams(packet);
        }
        catch (IOException e)
        {
            this.exceptionHandler.handle(e);
        }
    }

    private void updateActiveStreams(Packet packet)
    {
        int id = packet.getStreamId();
        if (packet.isEmpty())
        {
            this.activeStreams.remove(id);
        }
        else
        {
            this.activeStreams.add(id);
        }
    }

    private boolean noActiveStreams()
    {
        return this.activeStreams.isEmpty();
    }

    public boolean isDone()
    {
        return this.hasReachedMaxSizeLimit() && this.noActiveStreams();
    }

    public boolean hasReachedMaxSizeLimit()
    {
        return this.byteCountingOutputStream.hasReachedSize(this.maxSizeBytes);
    }

    public void close()
    {
        try
        {
            Packet.END_OF_FILE.write(this.outputStream);
            this.outputStream.close();
        }
        catch (IOException e)
        {
            this.exceptionHandler.handle(e);
        }
    }

    public boolean containsStream(int streamId)
    {
        return this.activeStreams.contains(streamId);
    }
}
