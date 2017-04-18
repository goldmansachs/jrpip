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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class CompositeOutputWriterTest
{
    private static class MockOutputStream
            extends OutputStream
    {
        private final List<Integer> bytes = new ArrayList<Integer>();
        private boolean flushed;
        private boolean closed;

        @Override
        public void write(int b) throws IOException
        {
            this.bytes.add(b);
        }

        @Override
        public void flush() throws IOException
        {
            this.flushed = true;
        }

        @Override
        public void close() throws IOException
        {
            this.closed = true;
        }
    }

    private static class MockOutputStreamBuilder
            implements OutputStreamBuilder
    {
        private final List<MockOutputStream> outputStreams = new ArrayList();

        @Override
        public DataOutputStream newOutputStream()
        {
            MockOutputStream newStream = new MockOutputStream();
            this.outputStreams.add(newStream);
            return new DataOutputStream(newStream);
        }

        @Override
        public void close()
        {
        }

        @Override
        public void shutdownNow()
        {
        }
    }

    @Test
    public void testDelisting()
    {
        MockOutputStreamBuilder outputStreamBuilder = new MockOutputStreamBuilder();
        CompositeOutputWriter outputWriter = new CompositeOutputWriter(0, outputStreamBuilder, new ByteArrayPool(10, 10), null);
        outputWriter.writePacket(new Packet(1, new byte[]{(byte) 1, (byte) 3, (byte) 4}, 3));
        Assert.assertEquals(1, outputStreamBuilder.outputStreams.size());
        MockOutputStream first = outputStreamBuilder.outputStreams.get(0);
        Assert.assertFalse(first.flushed);
        Assert.assertFalse(first.closed);
        outputWriter.writePacket(new Packet(1));
        Assert.assertEquals(1, outputStreamBuilder.outputStreams.size());
        MockOutputStream first2 = outputStreamBuilder.outputStreams.get(0);
        Assert.assertTrue(first2.flushed);
        Assert.assertTrue(first2.closed);
        outputWriter.writePacket(new Packet(2, new byte[]{(byte) 4}, 1));
        Assert.assertEquals(2, outputStreamBuilder.outputStreams.size());
    }

    @Test
    public void testLargeBuffer() throws IOException, ClassNotFoundException
    {
        MockOutputStreamBuilder outputStreamBuilder = new MockOutputStreamBuilder();
        CompositeOutputWriter outputWriter = new CompositeOutputWriter(1, outputStreamBuilder, new ByteArrayPool(10, 10), null);
        for (int i = 0; i < 100; i++)
        {
            outputWriter.writePacket(new Packet(i, new byte[]{(byte) 1, (byte) 3, (byte) 4}, 3));
        }
        Assert.assertEquals(1, outputStreamBuilder.outputStreams.size());
        MockOutputStream first = outputStreamBuilder.outputStreams.get(0);
        Assert.assertFalse(first.flushed);
        Assert.assertFalse(first.closed);
    }
}
