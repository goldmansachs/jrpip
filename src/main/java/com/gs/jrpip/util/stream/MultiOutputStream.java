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
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.gs.jrpip.util.JrpipThreadFactory;

public class MultiOutputStream
        implements OutputStreamBuilder
{
    public static final int DEFAULT_MAX_FILE_SIZE_MEGABYTES = 200;
    public static final int DEFAULT_FLUSH_BUFFER_SIZE_BYTES = 2000;
    private static final int DEFAULT_MAX_BYTE_POOL_SIZE = 100;
    private static final int DEFAULT_FLUSH_BUFFER_INTERVAL_SECONDS = 60;

    private final int flushBufferSizeBytes;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final PacketConsumer packetConsumer;
    private final StreamFlusher streamFlusher;
    private final ByteArrayPool byteArrayPool;
    private final ExecutorService packetConsumerExecutor;
    private final ExecutorService intervalFlushExecutor;

    public MultiOutputStream(
            String baseFileName,
            String fileDir,
            int flushBufferSize,
            int maxFileSizeMegs,
            ExceptionHandler exceptionHandler)
    {
        this(new UniqueFileOutputStreamBuilder(baseFileName, fileDir, exceptionHandler), flushBufferSize, maxFileSizeMegs, exceptionHandler);
    }

    public MultiOutputStream(
            OutputStreamBuilder outputStreamBuilder,
            int flushBufferSize,
            int maxFileSizeMegs,
            ExceptionHandler exceptionHandler)
    {
        this(new ByteArrayPool(DEFAULT_MAX_BYTE_POOL_SIZE, flushBufferSize),
                maxFileSizeMegs,
                DEFAULT_FLUSH_BUFFER_INTERVAL_SECONDS,
                flushBufferSize,
                exceptionHandler,
                outputStreamBuilder);
    }

    MultiOutputStream(PacketWriter packetWriter, int flushBufferSize)
    {
        this(new ByteArrayPool(MultiOutputStream.DEFAULT_MAX_BYTE_POOL_SIZE, flushBufferSize),
                packetWriter,
                DEFAULT_FLUSH_BUFFER_INTERVAL_SECONDS,
                flushBufferSize,
                new LinkedBlockingQueue<Packet>(),
                Executors.newSingleThreadExecutor(new JrpipThreadFactory("Packet Consumer Executor")),
                Executors.newSingleThreadScheduledExecutor(new JrpipThreadFactory("Interval Flush Executor")));
    }

    private MultiOutputStream(
            ByteArrayPool byteArrayPool,
            int maxFileSizeMegabytes,
            int flushBufferIntervalSeconds,
            int flushBufferSizeBytes,
            ExceptionHandler exceptionHandler,
            OutputStreamBuilder outputStreamBuilder)
    {
        this(byteArrayPool,
                new CompositeOutputWriter(maxFileSizeMegabytes,
                        outputStreamBuilder,
                        byteArrayPool, exceptionHandler),
                flushBufferIntervalSeconds,
                flushBufferSizeBytes,
                new LinkedBlockingQueue<Packet>(),
                Executors.newSingleThreadExecutor(new JrpipThreadFactory("Packet Consumer Executor")),
                Executors.newSingleThreadScheduledExecutor(new JrpipThreadFactory("Interval Flush Executor")));
    }

    private MultiOutputStream(
            ByteArrayPool byteArrayPool,
            PacketWriter outputWriter,
            int flushBufferIntervalSeconds,
            int flushBufferSizeBytes,
            BlockingQueue<Packet> blockingQueue,
            ExecutorService packetConsumerExecutor,
            ScheduledExecutorService intervalFlushExecutor)
    {
        this.streamFlusher = new StreamFlusher();
        this.byteArrayPool = byteArrayPool;
        this.packetConsumer = new PacketConsumer(blockingQueue, outputWriter);
        this.packetConsumer.startPolling(packetConsumerExecutor);
        this.packetConsumerExecutor = packetConsumerExecutor;
        this.flushBufferSizeBytes = flushBufferSizeBytes;
        intervalFlushExecutor.scheduleAtFixedRate(this.streamFlusher, flushBufferIntervalSeconds, flushBufferIntervalSeconds, TimeUnit.SECONDS);
        this.intervalFlushExecutor = intervalFlushExecutor;
    }

    public static MultiOutputStreamBuilder builder(
            String baseFileName,
            String fileDir,
            int flushBufferSize,
            int maxFileSizeMegs)
    {
        return new MultiOutputStreamBuilderImpl(baseFileName, fileDir, flushBufferSize, maxFileSizeMegs);
    }

    private static final class MultiOutputStreamBuilderImpl
            implements MultiOutputStreamBuilder
    {
        private final String baseFileName;
        private final String fileDir;
        private final int flushBufferSize;
        private final int maxFileSizeMegs;

        private MultiOutputStreamBuilderImpl(
                String baseFileName,
                String fileDir,
                int flushBufferSize,
                int maxFileSizeMegs)
        {
            this.baseFileName = baseFileName;
            this.fileDir = fileDir;
            this.flushBufferSize = flushBufferSize;
            this.maxFileSizeMegs = maxFileSizeMegs;
        }

        @Override
        public MultiOutputStream buildForExceptionHandler(ExceptionHandler exceptionHandler)
        {
            return new MultiOutputStream(this.baseFileName, this.fileDir, this.flushBufferSize, this.maxFileSizeMegs, exceptionHandler);
        }
    }

    @Override
    public void close()
    {
        this.intervalFlushExecutor.shutdown();
        this.packetConsumer.signalShutdown();
        this.packetConsumerExecutor.shutdown();
        this.packetConsumer.waitForShutdown();
    }

    @Override
    public void shutdownNow()
    {
        this.intervalFlushExecutor.shutdown();
        this.packetConsumer.signalShutdownNow();
        this.packetConsumerExecutor.shutdown();
    }

    @Override
    public DataOutputStream newOutputStream()
    {
        return new DataOutputStream(this.newBufferingOutputStream());
    }

    public BufferingOutputStream newBufferingOutputStream()
    {
        return new BufferingOutputStream();
    }

    public final class BufferingOutputStream
            extends OutputStream
            implements FlushableCloseable
    {
        private byte[] bytes;
        private int count;
        private final int streamId;
        private boolean closed;

        private BufferingOutputStream()
        {
            this.streamId = MultiOutputStream.this.counter.incrementAndGet();
            MultiOutputStream.this.streamFlusher.addStream(this);
            this.bytes = MultiOutputStream.this.byteArrayPool.borrowByteArray(MultiOutputStream.this.flushBufferSizeBytes);
        }

        @Override
        public synchronized void write(int b)
        {
            if (this.closed)
            {
                throw new IllegalStateException("The output stream is closed");
            }
            this.addToBufferAndFlushIfNeeded(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len)
        {
            if (len > this.bytes.length)
            {
                this.flush();
                byte[] byteArray = MultiOutputStream.this.byteArrayPool.borrowByteArray(len);
                System.arraycopy(b, off, byteArray, 0, len);
                MultiOutputStream.this.packetConsumer.addToQueue(new Packet(this.streamId, byteArray, len));
                return;
            }
            if (len > this.bytes.length - this.count)
            {
                this.flush();
            }
            System.arraycopy(b, off, this.bytes, this.count, len);
            this.count += len;
            this.flushIfNeeded();
        }

        private synchronized void addToBufferAndFlushIfNeeded(int b)
        {
            this.bytes[this.count++] = (byte) b;
            this.flushIfNeeded();
        }

        private synchronized void flushIfNeeded()
        {
            if (this.count >= MultiOutputStream.this.flushBufferSizeBytes)
            {
                this.flush();
            }
        }

        @Override
        public synchronized void close()
        {
            try
            {
                if (!this.isClosed())
                {
                    this.flush();
                    MultiOutputStream.this.packetConsumer.endStream(this.streamId);
                }
            }
            finally
            {
                this.closed = true;
            }
        }

        @Override
        public synchronized boolean isClosed()
        {
            return this.closed;
        }

        @Override
        public synchronized void flush()
        {
            if (this.count > 0)
            {
                MultiOutputStream.this.packetConsumer.addToQueue(new Packet(this.streamId, this.bytes, this.count));
                this.bytes = MultiOutputStream.this.byteArrayPool.borrowByteArray(MultiOutputStream.this.flushBufferSizeBytes);
                this.count = 0;
            }
        }

        public int getStreamId()
        {
            return this.streamId;
        }
    }
}
