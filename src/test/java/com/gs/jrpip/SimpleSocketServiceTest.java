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

import com.gs.jrpip.client.SocketMessageTransport;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.MtProxyFactory;
import com.gs.jrpip.client.ThankYouWriter;
import com.gs.jrpip.server.SocketServer;
import com.gs.jrpip.server.SocketServerConfig;
import org.junit.Assert;

import java.io.IOException;
import java.io.NotSerializableException;
import java.net.MalformedURLException;

public class SimpleSocketServiceTest
        extends SocketTestCase
{
    public void testEcho() throws MalformedURLException, InterruptedException
    {
        Echo echo = this.buildEchoProxy();

        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertEquals("hello", echo.uncompressedEcho("hello"));
        Assert.assertEquals("hello", echo.uncompressedEcho("hello"));
        for (int i = 0; i < 100; i++)
        {
            Thread.sleep(100L);
            if (this.server.getThankYous() > 0)
            {
                break;
            }
        }
        Assert.assertTrue(this.server.getThankYous() > 0);
    }

    public void testLocalInstance() throws MalformedURLException
    {
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport());
        fspf.setUseLocalService(true);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        Assert.assertSame(EchoImpl.class, echo.getClass());
    }

    public void testException() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();

        try
        {
            echo.throwExpectedException();
            Assert.fail("should not get here");
        }
        catch (FakeException e)
        {
            // expected
            System.out.println("here");
        }
        Assert.assertEquals("hello", echo.echo("hello"));
    }

    public void testUnexpectedException() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();
        try
        {
            echo.throwUnexpectedException();
            Assert.fail("should not get here");
        }
        catch (RuntimeException e)
        {
            // expected
        }
        Assert.assertEquals("hello", echo.echo("hello"));
    }

    public void testResendRequest() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();

        StringBuilder largeBuffer = new StringBuilder(5000);
        for (int i = 0; i < 5000; i++)
        {
            largeBuffer.append(i);
        }
        Assert.assertTrue(largeBuffer.length() > 5000);
        String largeString = largeBuffer.toString();
        Assert.assertEquals(largeString, echo.echoWithException(largeString).getContents());
    }

    public void testLargePayload() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();

        StringBuilder largeBuffer = new StringBuilder(50000);
        for (int i = 0; i < 50000; i++)
        {
            largeBuffer.append(i);
        }
        Assert.assertTrue(largeBuffer.length() > 50000);
        String largeString = largeBuffer.toString();
        Assert.assertEquals(largeString, echo.echo(largeString));
        Assert.assertEquals(largeString, echo.uncompressedEcho(largeString));
        Assert.assertEquals(largeString, echo.echo(largeString));
        Assert.assertEquals(largeString, echo.uncompressedEcho(largeString));
    }

    public void testUnserializableObject() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();

        for (int i = 0; i < 50; i++)
        {
            try
            {
                echo.testUnserializableObject(new Object());
                Assert.fail("should not get here");
            }
            catch (JrpipRuntimeException e)
            {
                assertTrue(e.getCause() instanceof NotSerializableException);
            }
        }
        Assert.assertEquals("hello", echo.echo("hello"));
    }

    public void testPing() throws IOException
    {
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport());
        Assert.assertTrue(fspf.isServiceAvailable(this.getJrpipUrl()));
    }

    public void testServerRecycle() throws Exception
    {
        this.server.stop();
        this.server.destroy();

        SocketMessageTransport.clearServerStatus();

        SocketServerConfig config = new SocketServerConfig(this.server.getPort());
        config.setServerSocketTimeout(50);
        config.setIdleSocketCloseTime(1000);
        config.addServiceConfig(Echo.class, EchoImpl.class);
        this.addMoreConfig(config);
        this.server = new SocketServer(config);
        this.server.start();

        Echo echo = buildEchoProxy();
        echo.echo("hello"); //now has a 1000 internal socket timer.
        ThankYouWriter.getINSTANCE().stopThankYouThread();

        this.server.stop();
        Thread.sleep(1100); // all sockets are gone now.

        config.setIdleSocketCloseTime(100);
        this.server = new SocketServer(config);
        this.server.start(); //short socket close time now.

        echo.echo("hello"); // still 1000 internal socket timer.
        ThankYouWriter.getINSTANCE().stopThankYouThread();

        Thread.sleep(110);

        long now = System.currentTimeMillis();
        echo.echo("again");
        long end = System.currentTimeMillis();
        Assert.assertTrue(end - now < 100);

    }
}
