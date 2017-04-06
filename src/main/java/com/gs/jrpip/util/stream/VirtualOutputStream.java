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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gs.jrpip.util.JrpipThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualOutputStream
        implements OutputStreamBuilder, ExceptionHandler
{
    public static final OutputStream NULL_OUTPUT_STREAM = new OutputStream()
    {
        @Override
        public void write(int b)
        {
        }

        @Override
        public void write(byte[] b, int off, int len)
        {
        }
    };

    public static final DataOutputStream NULL_DATA_OUTPUT_STREAM = new DataOutputStream(NULL_OUTPUT_STREAM);

    public static final OutputStreamBuilder NULL_OUTPUT_STREAM_BUILDER = new OutputStreamBuilder()
    {
        public DataOutputStream newOutputStream()
        {
            return NULL_DATA_OUTPUT_STREAM;
        }

        public void close()
        {
        }

        public void shutdownNow()
        {
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualOutputStream.class);
    private static final int WAIT_BEFORE_RETRY_MINUTES = 10;
    private static final int MAX_RETRIES = 20;

    private volatile OutputStreamBuilder outputStreamBuilder;
    private MultiOutputStreamBuilder multiOutputStreamBuilder;

    private int retryNumber;

    private final Thread shutdownHook = new Thread(new Runnable()
    {
        public void run()
        {
            VirtualOutputStream.this.doClose();
        }
    });

    private ScheduledExecutorService retryExecutorService;

    public VirtualOutputStream(String baseFileName, String fileDir)
    {
        this(baseFileName, fileDir, MultiOutputStream.DEFAULT_FLUSH_BUFFER_SIZE_BYTES, MultiOutputStream.DEFAULT_MAX_FILE_SIZE_MEGABYTES);
    }

    public VirtualOutputStream(String baseFileName, String fileDir, int flushSizeBytes, int fileMaxSizeMegs)
    {
        this.initialize(MultiOutputStream.builder(baseFileName, fileDir, flushSizeBytes, fileMaxSizeMegs));
    }

    public VirtualOutputStream(MultiOutputStreamBuilder multiOutputStreamBuilder)
    {
        this.initialize(multiOutputStreamBuilder);
    }

    private void initialize(MultiOutputStreamBuilder multiOutputStreamBuilder)
    {
        this.multiOutputStreamBuilder = multiOutputStreamBuilder;
        this.outputStreamBuilder = multiOutputStreamBuilder.buildForExceptionHandler(this);
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    @Override
    public DataOutputStream newOutputStream()
    {
        return this.outputStreamBuilder.newOutputStream();
    }

    private ScheduledExecutorService getOrCreateRetryExecutorService()
    {
        if (this.retryExecutorService == null)
        {
            this.retryExecutorService = Executors.newScheduledThreadPool(1, new JrpipThreadFactory("Stream Builder Retry " + System.currentTimeMillis()));
        }
        return this.retryExecutorService;
    }

    @Override
    public void handle(Throwable e)
    {
        LOGGER.error("The following exception has been thrown", e);
        OutputStreamBuilder streamBuilder = this.outputStreamBuilder;
        if (!streamBuilder.equals(NULL_OUTPUT_STREAM_BUILDER))
        {
            this.outputStreamBuilder = NULL_OUTPUT_STREAM_BUILDER;
            LOGGER.warn("Shutting down output stream builder.");
            this.manageRetry();
            streamBuilder.shutdownNow();
            this.shutdownExecutor();
        }
    }

    private void manageRetry()
    {
        if (this.retryNumber + 1 <= MAX_RETRIES)
        {
            this.retryNumber++;

            LOGGER.warn("This is retry number {}. Max retry number is " + MAX_RETRIES + ". Will enable output stream builder in " + WAIT_BEFORE_RETRY_MINUTES + " minutes.", this.retryNumber);

            this.getOrCreateRetryExecutorService().schedule(new Runnable()
            {
                public void run()
                {
                    VirtualOutputStream.this.outputStreamBuilder = VirtualOutputStream.this.multiOutputStreamBuilder.buildForExceptionHandler(VirtualOutputStream.this);
                }
            }, WAIT_BEFORE_RETRY_MINUTES * 60L, TimeUnit.SECONDS);
        }
    }

    @Override
    public void shutdownNow()
    {
        try
        {
            this.outputStreamBuilder.shutdownNow();
            this.shutdownExecutor();
        }
        finally
        {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    @Override
    public void close()
    {
        try
        {
            this.doClose();
        }
        finally
        {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    private void doClose()
    {
        this.outputStreamBuilder.close();
        this.shutdownExecutor();
    }

    private void shutdownExecutor()
    {
        if (this.retryExecutorService != null)
        {
            this.retryExecutorService.shutdownNow();
        }
    }
}
