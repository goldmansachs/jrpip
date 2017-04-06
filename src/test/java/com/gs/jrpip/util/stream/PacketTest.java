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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class PacketTest
{
    @Test
    public void testGetStreamId()
    {
        Assert.assertEquals(3L, new Packet(3).getStreamId());
    }

    @Test
    public void testIsEmpty()
    {
        Assert.assertTrue(new Packet(1).isEmpty());
        Assert.assertTrue(new Packet(1, new byte[10], 0).isEmpty());
    }

    @Test
    public void testNotEmpty()
    {
        Assert.assertFalse(new Packet(1, new byte[10], 10).isEmpty());
    }

    @Test
    public void testWritePacket() throws IOException, ClassNotFoundException
    {
        Packet packet = new Packet(10, new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6}, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(out);
        packet.write(outputStream);
        outputStream.close();
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        Assert.assertEquals(10L, inputStream.readInt());
        Assert.assertEquals(3L, inputStream.readInt());
        byte[] bytes = new byte[3];
        inputStream.read(bytes);
        Assert.assertEquals(1L, bytes[0]);
        Assert.assertEquals(2L, bytes[1]);
        Assert.assertEquals(3L, bytes[2]);
    }

    @Test
    public void testWriteEmptyPacket() throws IOException
    {
        Packet packet = new Packet(10);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(out);
        packet.write(outputStream);
        outputStream.close();
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        Assert.assertEquals(10L, inputStream.readInt());
        Assert.assertEquals(0L, inputStream.readInt());
    }

    @Test
    public void testReturnByteArrayToThePool()
    {
        ByteArrayPool byteArrayPool = new ByteArrayPool(10, 3);
        byte[] data = new byte[3];
        Packet packet = new Packet(10, data, 3);
        packet.returnByteArrayToThePool(byteArrayPool);
        Assert.assertSame(data, byteArrayPool.borrowByteArray(3));
    }
}
