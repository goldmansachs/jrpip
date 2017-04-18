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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Assert;
import org.junit.Test;

public class PacketConsumerTest
{
    @Test
    public void testSuccessfulExecution() throws IOException, ClassNotFoundException
    {
        final List<ByteArrayOutputStream> outputStreams = new ArrayList<ByteArrayOutputStream>();

        PacketConsumer packetConsumer =
                new PacketConsumer(new LinkedBlockingQueue<Packet>(),
                        (PacketWriter) new CompositeOutputWriter(0, new OutputStreamBuilder()
                        {
                            public DataOutputStream newOutputStream()
                            {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                outputStreams.add(outputStream);
                                return new DataOutputStream(outputStream);
                            }

                            public void close()
                            {
                            }

                            public void shutdownNow()
                            {
                            }
                        }, new ByteArrayPool(10, 10), null));

        packetConsumer.addToQueue(new Packet(1, new byte[]{(byte) 1, (byte) 3, (byte) 4}, 3));
        packetConsumer.addToQueue(new Packet(1, new byte[]{(byte) 5, (byte) 6, (byte) 7}, 3));
        packetConsumer.addToQueue(new Packet(2, new byte[]{(byte) 5, (byte) 6, (byte) 7}, 3));
        packetConsumer.endStream(1);
        for (int i = 0; i < 4; i++)
        {
            packetConsumer.processPacket();
        }
        Assert.assertEquals(2, outputStreams.size());
        DataInputStream objectInputStream = new DataInputStream(new ByteArrayInputStream(outputStreams.get(0).toByteArray()));
        Assert.assertEquals(SerialMultiplexedWriter.VERSION, objectInputStream.readInt());
        Assert.assertEquals(1L, objectInputStream.readInt());
        Assert.assertEquals(3L, objectInputStream.readInt());
        byte[] array = new byte[3];
        objectInputStream.read(array);
        Assert.assertArrayEquals(new byte[]{(byte) 1, (byte) 3, (byte) 4}, array);
        Assert.assertEquals(1L, objectInputStream.readInt());
        Assert.assertEquals(3L, objectInputStream.readInt());
        objectInputStream.read(array);
        Assert.assertArrayEquals(new byte[]{(byte) 5, (byte) 6, (byte) 7}, array);
        Assert.assertEquals(1L, objectInputStream.readInt());
        Assert.assertEquals(0L, objectInputStream.readInt());
    }

    @Test
    public void testQueueOutOfSpace()
    {
        final List<ByteArrayOutputStream> outputStreams = new ArrayList<ByteArrayOutputStream>();

        PacketConsumer packetConsumer =
                new PacketConsumer(new LinkedBlockingQueue<Packet>(1),
                        (PacketWriter) new CompositeOutputWriter(1, new OutputStreamBuilder()
                        {
                            public DataOutputStream newOutputStream()
                            {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                outputStreams.add(outputStream);
                                return new DataOutputStream(outputStream);
                            }

                            public void close()
                            {
                            }

                            public void shutdownNow()
                            {
                            }
                        }, new ByteArrayPool(10, 10), null));

        packetConsumer.addToQueue(new Packet(1, new byte[]{(byte) 1, (byte) 3, (byte) 4}, 3));
        packetConsumer.addToQueue(new Packet(1, new byte[]{(byte) 5, (byte) 6, (byte) 7}, 3));
        packetConsumer.addToQueue(new Packet(2, new byte[]{(byte) 5, (byte) 6, (byte) 7}, 3));
        packetConsumer.endStream(1);
        packetConsumer.run();
        Assert.assertTrue(outputStreams.isEmpty());
    }
}
