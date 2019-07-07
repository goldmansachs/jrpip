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

package com.gs.jrpip;

import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.util.stream.SerialMultiplexedWriter;
import com.gs.jrpip.util.stream.VirtualOutputStreamFactory;
import com.gs.jrpip.util.stream.readback.RequestData;
import com.gs.jrpip.util.stream.readback.RequestDataMultiStreamIterable;
import org.junit.Assert;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JrpipSocketServiceBinaryLoggingTest
        extends SocketTestCase
{
    public static final int N_THREADS = 50;
    private static final String LONG_ECHO = "Hello everyone, I am the longest echo String you can find anywhere. I am here to test binary jrpip logging and to make"
            + " sure it works perfectly fine and is thread safe since concurrency is a key.";

    private MockVirtualOutputStreamCreator virtualOutputStreamCreator;

    @Override
    protected void setUp() throws Exception
    {
        this.deleteDirectory();
        this.virtualOutputStreamCreator = new MockVirtualOutputStreamCreator();
        VirtualOutputStreamFactory.setOutputStreamCreator(this.virtualOutputStreamCreator);
        System.setProperty("jrpip.enableBinaryLogs", "true");
        System.setProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST, "50");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        VirtualOutputStreamFactory.setOutputStreamCreator(VirtualOutputStreamFactory.DEFAULT_CREATOR);
        this.deleteDirectory();
        System.clearProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST);
        System.clearProperty("jrpip.enableBinaryLogs");
        super.tearDown();
    }

    protected void deleteDirectory()
    {
        File binaryLogsDir = new File("jrpipBinaryLogs");
        File[] files = binaryLogsDir.listFiles();
        if (files != null)
        {
            for (int i = 0; i < files.length; i++)
            {
                files[i].delete();
            }
        }
        binaryLogsDir.delete();
    }

    public void testParseData() throws IOException
    {
        long currentTimeBeforeInvocation = System.currentTimeMillis();
        Echo echo = this.buildEchoProxy();
        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertEquals("alex", echo.echo("alex"));
        Assert.assertNotNull(this.virtualOutputStreamCreator.getVirtualOutputStream());
        this.virtualOutputStreamCreator.getVirtualOutputStream().close();
        FileInputStream fileInputStream = null;
        try
        {
            fileInputStream = new FileInputStream(this.findBinaryLogFile());
            RequestDataMultiStreamIterable multiStreamIterable = new RequestDataMultiStreamIterable(fileInputStream);
            final Iterator<RequestData> iterator = multiStreamIterable.iterator();
            for (int i = 0; i < 10; i++)
            {
                Assert.assertTrue(iterator.hasNext());
            }
            this.verify(currentTimeBeforeInvocation, iterator.next(), 1, "hello");
            this.verify(currentTimeBeforeInvocation, iterator.next(), 3, "alex");

            for (int i = 0; i < 10; i++)
            {
                Assert.assertFalse(iterator.hasNext());
            }
            try
            {
                iterator.next();
                fail("should've thrown NoSuchElementException");
            }
            catch (NoSuchElementException e)
            {
                //expected
            }
        }
        finally
        {
            if (fileInputStream != null)
            {
                fileInputStream.close();
            }
        }
    }

    private void verify(
            long currentTimeBeforeInvocation,
            RequestData firstRequest,
            int expectedStreamId,
            String expectedArgument)
    {
        Assert.assertEquals(expectedStreamId, firstRequest.getStreamId());
        assertEquals(1, firstRequest.getArguments().length);
        Assert.assertEquals(expectedArgument, firstRequest.getArguments()[0]);
        Assert.assertEquals("echo", firstRequest.getMethodName());
        Assert.assertTrue(firstRequest.getStartTime() >= currentTimeBeforeInvocation);
        Assert.assertTrue(firstRequest.getEndTime() >= currentTimeBeforeInvocation);
        Assert.assertTrue(firstRequest.getEndTime() >= firstRequest.getStartTime());
    }

    public void testConcurrency() throws IOException, InterruptedException
    {
        final Echo echo = this.buildEchoProxy();
        ExecutorService executorService = Executors.newFixedThreadPool(N_THREADS);
        List<Callable<Object>> callables = new ArrayList<Callable<Object>>(N_THREADS);
        for (int i = 0; i < N_THREADS; i++)
        {
            callables.add(Executors.callable(new Runnable()
            {
                public void run()
                {
                    for (int j = 0; j < 100; j++)
                    {
                        echo.echo(LONG_ECHO);
                    }
                }
            }));
        }
        executorService.invokeAll(callables);
        Assert.assertNotNull(this.virtualOutputStreamCreator.getVirtualOutputStream());
        this.virtualOutputStreamCreator.getVirtualOutputStream().close();
        File binaryLog = this.findBinaryLogFile();
        DataInputStream dataInputStream = null;
        try
        {
            dataInputStream = new DataInputStream(new FileInputStream(binaryLog));
            Assert.assertEquals(SerialMultiplexedWriter.VERSION, dataInputStream.readInt());
            this.validateBinaryLogReadBack(binaryLog);
        }
        finally
        {
            executorService.shutdown();
            if (dataInputStream != null)
            {
                dataInputStream.close();
            }
        }
    }

    private void validateBinaryLogReadBack(File binaryLog) throws IOException
    {
        FileInputStream inputStream = null;
        try
        {
            inputStream = new FileInputStream(binaryLog);
            int count = 0;
            for(RequestData rd: new RequestDataMultiStreamIterable(inputStream))
            {
                Assert.assertEquals(LONG_ECHO, rd.getArguments()[0]);
                count++;
            }
            Assert.assertEquals(N_THREADS * 100, count);
        }
        finally
        {
            if (inputStream != null)
            {
                inputStream.close();
            }
        }
    }

    private File findBinaryLogFile()
    {
        File binaryLogsDir = new File("jrpipBinaryLogs");
        File[] files = binaryLogsDir.listFiles();
        Assert.assertEquals(1, files.length);
        return files[0];
    }
}
