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
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class SingleOutputWriterTest
{
    @Test
    public void testWrite() throws IOException, ClassNotFoundException
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SerialMultiplexedWriter fileWriter = new SerialMultiplexedWriter(outputStream, 1, null);
        fileWriter.writePacket(new Packet(1, new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4}, 4));
        fileWriter.writePacket(new Packet(2, new byte[]{(byte) 5, (byte) 6, (byte) 7, (byte) 8}, 4));
        fileWriter.writePacket(new Packet(1));
        fileWriter.writePacket(new Packet(2));
        fileWriter.close();
        DataInputStream objectInputStream = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        Assert.assertEquals(1L, objectInputStream.readInt());
        Assert.assertEquals(4L, objectInputStream.readInt());
        byte[] bytes = new byte[4];
        objectInputStream.read(bytes);
        Assert.assertArrayEquals(bytes, new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4});
        Assert.assertEquals(2L, objectInputStream.readInt());
        Assert.assertEquals(4L, objectInputStream.readInt());
        objectInputStream.read(bytes);
        Assert.assertArrayEquals(bytes, new byte[]{(byte) 5, (byte) 6, (byte) 7, (byte) 8});
        this.assertEndOfPacketOne(objectInputStream);
        this.assertEndOfPacketTwo(objectInputStream);
        this.assertEndOfFile(objectInputStream);
    }

    private void assertEndOfFile(DataInputStream objectInputStream) throws IOException
    {
        Assert.assertEquals(-1L, objectInputStream.readInt());
        Assert.assertEquals(0L, objectInputStream.readInt());
    }

    private void assertEndOfPacketTwo(DataInputStream objectInputStream) throws IOException
    {
        Assert.assertEquals(2L, objectInputStream.readInt());
        Assert.assertEquals(0L, objectInputStream.readInt());
    }

    private void assertEndOfPacketOne(DataInputStream objectInputStream) throws IOException
    {
        Assert.assertEquals(1L, objectInputStream.readInt());
        Assert.assertEquals(0L, objectInputStream.readInt());
    }

    @Test
    public void testPurgeCandidate()
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SerialMultiplexedWriter fileWriter = new SerialMultiplexedWriter(outputStream, 0, null);
        Assert.assertTrue(fileWriter.isDone());
        fileWriter.writePacket(new Packet(1, new byte[]{(byte) 1}, 1));
        Assert.assertFalse(fileWriter.isDone());
        fileWriter.writePacket(new Packet(2, new byte[]{(byte) 2}, 1));
        Assert.assertFalse(fileWriter.isDone());
        fileWriter.writePacket(new Packet(2));
        Assert.assertFalse(fileWriter.isDone());
        fileWriter.writePacket(new Packet(1));
        Assert.assertTrue(fileWriter.isDone());
    }

    @Test
    public void testHasReachedMaxSizeLimit()
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Assert.assertTrue(new SerialMultiplexedWriter(outputStream, 0, null).hasReachedMaxSizeLimit());
        Assert.assertFalse(new SerialMultiplexedWriter(outputStream, 1, null).hasReachedMaxSizeLimit());
    }
}
