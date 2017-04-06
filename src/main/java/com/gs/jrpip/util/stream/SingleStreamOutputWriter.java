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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SingleStreamOutputWriter implements PacketWriter
{
    private final Map<Integer, DataOutputStream> streamOutputMap = new ConcurrentHashMap<Integer, DataOutputStream>();
    private final ExceptionHandler exceptionHandler;

    public SingleStreamOutputWriter(ExceptionHandler exceptionHandler)
    {
        this.exceptionHandler = exceptionHandler;
    }

    public void register(int streamId, DataOutputStream outputStream) throws IOException
    {
        this.streamOutputMap.put(Integer.valueOf(streamId), outputStream);
        outputStream.writeInt(SerialMultiplexedWriter.VERSION);
    }

    public void close(int streamId)
    {
        DataOutputStream outputStream = this.streamOutputMap.remove(streamId);
        if (outputStream != null)
        {
            try
            {
                outputStream.close();
            }
            catch (IOException e)
            {
                this.exceptionHandler.handle(e);
            }
        }
    }

    @Override
    public void writePacket(Packet packet)
    {
        try
        {
            DataOutputStream outputStream = this.streamOutputMap.get(packet.getStreamId());
            packet.write(outputStream);
            if (packet.isEmpty())
            {
                this.streamOutputMap.remove(packet.getStreamId());
                outputStream.close();
            }
        }
        catch (IOException e)
        {
            this.exceptionHandler.handle(e);
        }
    }

    @Override
    public void close()
    {
        for (DataOutputStream dataOutputStream : this.streamOutputMap.values())
        {
            try
            {
                dataOutputStream.close();
            }
            catch (IOException e)
            {
                this.exceptionHandler.handle(e);
            }
        }
    }
}
