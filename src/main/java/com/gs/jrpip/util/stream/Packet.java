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

public class Packet
{
    public static final Packet END_OF_FILE = new Packet(-1);

    private final int streamId;
    private final byte[] data;
    private final int count;

    public Packet(int streamId)
    {
        this(streamId, null, 0);
    }

    public Packet(int streamId, byte[] data, int count)
    {
        this.streamId = streamId;
        this.data = data;
        this.count = count;
    }

    public boolean isEmpty()
    {
        return this.count == 0;
    }

    public int getStreamId()
    {
        return this.streamId;
    }

    public void write(DataOutputStream outputStream) throws IOException
    {
        outputStream.writeInt(this.getStreamId());
        outputStream.writeInt(this.count);
        if (!this.isEmpty())
        {
            outputStream.write(this.data, 0, this.count);
        }
    }

    public void returnByteArrayToThePool(ByteArrayPool byteArrayPool)
    {
        if (this.data != null)
        {
            byteArrayPool.returnByteArray(this.data);
        }
    }
}
