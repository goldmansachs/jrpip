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

import java.util.List;

import com.gs.jrpip.JrpipTestCase;
import org.junit.Assert;
import org.junit.Test;

public class StreamFlusherTest
{
    private static final class MockFlushableCloseable
            implements FlushableCloseable
    {
        private boolean closed;
        private boolean flushed;

        private MockFlushableCloseable(boolean closed, boolean flushed)
        {
            this.closed = closed;
            this.flushed = flushed;
        }

        @Override
        public boolean isClosed()
        {
            return this.closed;
        }

        @Override
        public void close()
        {
            this.closed = true;
        }

        @Override
        public void flush()
        {
            this.flushed = true;
        }
    }

    private StreamFlusher newFlusherWithStreams(List<FlushableCloseable> streams)
    {
        final StreamFlusher streamFlusher = new StreamFlusher();
        for(FlushableCloseable fc: streams)
        {
            streamFlusher.addStream(fc);
        }
        return streamFlusher;
    }

    @Test
    public void testAllOpenedStreams()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(false, false);
        MockFlushableCloseable closeableTwo = new MockFlushableCloseable(false, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableOne, closeableTwo);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertTrue(closeableOne.flushed);
        Assert.assertTrue(closeableTwo.flushed);
        closeableOne.flushed = false;
        closeableTwo.flushed = false;
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertTrue(closeableOne.flushed);
        Assert.assertTrue(closeableTwo.flushed);
    }

    @Test
    public void testAllClosedStreams()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableTwo = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableThree = new MockFlushableCloseable(true, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableOne, closeableTwo, closeableThree);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertFalse(closeableOne.flushed);
        Assert.assertFalse(closeableTwo.flushed);
        Assert.assertFalse(closeableThree.flushed);
    }

    @Test
    public void testLastStreamOpen()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableTwo = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableThree = new MockFlushableCloseable(false, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableOne, closeableTwo, closeableThree);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertTrue(closeableThree.flushed);
        Assert.assertFalse(closeableTwo.flushed);
        Assert.assertFalse(closeableOne.flushed);
    }

    @Test
    public void testFirstStreamOpen()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableTwo = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableThree = new MockFlushableCloseable(false, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableThree, closeableOne, closeableTwo);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertTrue(closeableThree.flushed);
        Assert.assertFalse(closeableTwo.flushed);
        Assert.assertFalse(closeableOne.flushed);
    }

    @Test
    public void testOneStreamClosed()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(true, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableOne);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertFalse(closeableOne.flushed);
    }

    @Test
    public void testSomeStreamsClosed()
    {
        MockFlushableCloseable closeableOne = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableTwo = new MockFlushableCloseable(false, false);
        MockFlushableCloseable closeableThree = new MockFlushableCloseable(true, false);
        MockFlushableCloseable closeableFour = new MockFlushableCloseable(false, false);
        List<FlushableCloseable> streams = JrpipTestCase.<FlushableCloseable>newListWith(closeableThree, closeableOne, closeableTwo, closeableFour);
        StreamFlusher flusher = this.newFlusherWithStreams(streams);
        flusher.flushActiveStreamsAndPurgeClosedStreams();
        Assert.assertTrue(closeableTwo.flushed);
        Assert.assertFalse(closeableOne.flushed);
        Assert.assertFalse(closeableThree.flushed);
        Assert.assertTrue(closeableFour.flushed);
    }
}
