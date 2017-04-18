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

import java.io.IOException;
import java.io.NotSerializableException;
import java.net.ServerSocket;
import java.net.Socket;

import com.gs.jrpip.client.AuthenticatedUrl;
import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.client.JrpipRuntimeException;
import org.junit.Assert;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpHandler;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class ErrorConditionsTest extends JrpipTestCase
{
    @Override
    protected void setUp() throws Exception
    {
        // nb: don't perform default server startup (override prevents this)
    }

    public void testPingFailure()
    {
        Assert.assertFalse(new FastServletProxyFactory().isServiceAvailable(this.getJrpipUrl()));
    }

    public void testPingFailureOnce() throws Exception
    {
        this.setupServerWithHandler(new HttpErrorCausingHandler(1));

        FastServletProxyFactory factory = new FastServletProxyFactory();
        Assert.assertFalse(factory.isServiceAvailable(this.getJrpipUrl()));
        Assert.assertTrue(factory.isServiceAvailable(this.getJrpipUrl()));
    }

    public void testNoChunkingSupport() throws Exception
    {
        this.setupServerWithHandler(new HttpErrorCausingHandler(1));

        Echo echo = this.buildEchoProxy();

        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertFalse(FastServletProxyFactory.serverSupportsChunking(new AuthenticatedUrl(this.getJrpipUrl(), null)));
        StringBuilder largeBuffer = new StringBuilder(5000);
        for (int i = 0; i < 5000; i++)
        {
            largeBuffer.append(i);
        }
        Assert.assertTrue(largeBuffer.length() > 5000);
        String largeString = largeBuffer.toString();
        Assert.assertEquals(largeString, echo.echo(largeString));
    }

    public void testRetry() throws Exception
    {
        this.setupServerWithHandler(new HttpErrorCausingHandler(2));

        Echo echo = this.buildEchoProxy();
        Assert.assertEquals("hello", echo.echo("hello"));
    }

    public void testFastFailWhenArgumentsCannotBeSerialized() throws Exception
    {
        this.setupServerWithHandler(null);
        Echo echo = this.buildEchoProxy();
        try
        {
            echo.echoObject(new Object());
            Assert.fail();
        }
        catch (JrpipRuntimeException e)
        {
            assertTrue(e.getCause() instanceof NotSerializableException);
        }
    }

    // the following test takes too long (2 minutes)
    public void xtestNoServer() throws Exception
    {
        ServerSocket ss = new ServerSocket(this.getPort());
        new DummyServer(ss);
        try
        {
            Echo echo = this.buildEchoProxy();
            echo.echo("nothing");
            Assert.fail("should not get here");
        }
        catch (JrpipRuntimeException e)
        {
            // ok
        }
    }

    public static class HttpErrorCausingHandler implements HttpHandler
    {
        private boolean started;
        private HttpContext context;
        private int causeErrorOnEventNumber = -1;
        private int currentEventNumber;

        public HttpErrorCausingHandler(int causeErrorOnEventNumber)
        {
            this.causeErrorOnEventNumber = causeErrorOnEventNumber;
        }

        @Override
        public String getName()
        {
            return this.getClass().getName();
        }

        @Override
        public HttpContext getHttpContext()
        {
            return this.context;
        }

        @Override
        public void initialize(HttpContext context)
        {
            this.context = context;
        }

        @Override
        public void handle(
                String pathInContext,
                String pathParams,
                HttpRequest request,
                HttpResponse response) throws IOException
        {
            this.currentEventNumber++;
            if (this.currentEventNumber == this.causeErrorOnEventNumber)
            {
                throw new IOException("exception for testing");
            }
        }

        @Override
        public void start() throws Exception
        {
            this.started = true;
        }

        @Override
        public void stop() throws InterruptedException
        {
            this.started = false;
        }

        @Override
        public boolean isStarted()
        {
            return this.started;
        }
    }

    public static class DummyServer extends Thread
    {
        private final ServerSocket serverSocket;

        public DummyServer(ServerSocket serverSocket)
        {
            this.serverSocket = serverSocket;
            this.start();
        }

        @Override
        public void run()
        {
            try
            {
                Socket s = this.serverSocket.accept();
                s.close();
                this.serverSocket.close();
            }
            catch (IOException e)
            {
                //nothing to do
            }
        }
    }
}
