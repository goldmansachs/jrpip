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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.MalformedURLException;

import com.gs.jrpip.client.AuthenticatedUrl;
import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.client.InvocationHandlerFunction;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.record.MethodCallStreamResolver;
import com.gs.jrpip.client.record.RecordedProxyInvocationHandler;
import com.gs.jrpip.client.record.RecordingProxyInvocationHandler;
import org.junit.Assert;

public class RecordAndReplayJrpipServiceTest
        extends JrpipTestCase
{
    public static final String NOT_INTERESTING = "not interesting";
    private ByteArrayOutputStream byteArrayOutputStream;

    private final MethodCallStreamResolver streamResolver = new MethodCallStreamResolver()
    {
        public InputStream resolveInputStream(Method method, Object[] args)
        {
            if ("echo".equals(method.getName()) && NOT_INTERESTING.equals(args[0]))
            {
                return null;
            }

            return new ByteArrayInputStream(RecordAndReplayJrpipServiceTest.this.byteArrayOutputStream.toByteArray());
        }

        public OutputStream resolveOutputStream(Method method, Object[] args)
        {
            if ("echo".equals(method.getName()) && NOT_INTERESTING.equals(args[0]))
            {
                return null;
            }

            return RecordAndReplayJrpipServiceTest.this.byteArrayOutputStream;
        }
    };

    private final InvocationHandlerFunction recordedInvocationHandlerFunction = new InvocationHandlerFunction()
    {
        public InvocationHandler getInvocationHandler(AuthenticatedUrl url, Class api, int timeout)
        {
            return new RecordedProxyInvocationHandler(RecordAndReplayJrpipServiceTest.this.streamResolver);
        }
    };

    private final InvocationHandlerFunction recordingInvocationHandlerFunction = new InvocationHandlerFunction()
    {
        public InvocationHandler getInvocationHandler(
                AuthenticatedUrl url, Class api, int timeout)
        {
            return new RecordingProxyInvocationHandler(url, api, timeout, RecordAndReplayJrpipServiceTest.this.streamResolver);
        }
    };

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    public void testEcho() throws Exception
    {
        Echo echo1 = this.getRecordingEcho();

        Assert.assertEquals("hello", echo1.echo("hello"));

        this.tearDown(); // shutdown server

        Echo echo2 = this.getRecordedEcho();

        Assert.assertEquals("hello", echo2.echo("hello"));
    }

    public void testException() throws Exception
    {
        Echo echo1 = this.getRecordingEcho();
        try
        {
            echo1.throwExpectedException();
            Assert.fail("should not get here");
        }
        catch (FakeException e)
        {
            // expected
        }

        this.tearDown(); // shutdown server

        Echo echo2 = this.getRecordedEcho();
        try
        {
            echo2.throwExpectedException();
            Assert.fail("should not get here");
        }
        catch (FakeException e)
        {
            // expected
        }
    }

    public void testUnexpectedException() throws Exception
    {
        Echo echo1 = this.getRecordingEcho();
        try
        {
            echo1.throwUnexpectedException();
            Assert.fail("should not get here");
        }
        catch (RuntimeException e)
        {
            // expected
        }

        this.tearDown(); // shutdown server

        Echo echo2 = this.getRecordedEcho();
        try
        {
            echo2.throwUnexpectedException();
            Assert.fail("should not get here");
        }
        catch (RuntimeException e)
        {
            // expected
        }
    }

    public void testCallNotRecorded() throws Exception
    {
        Echo echo1 = this.getRecordingEcho();

        Assert.assertEquals(NOT_INTERESTING, echo1.echo(NOT_INTERESTING));
        Assert.assertEquals(0, this.byteArrayOutputStream.size());

        this.tearDown(); // shutdown server

        Echo echo2 = this.getRecordedEcho();

        try
        {
            echo2.echo(NOT_INTERESTING);
            Assert.fail();
        }
        catch (JrpipRuntimeException expected)
        {
        }
    }

    private Echo getRecordingEcho() throws MalformedURLException
    {
        FastServletProxyFactory fspf = new FastServletProxyFactory();
        fspf.setUseLocalService(false);
        fspf.setInvocationHandlerFunction(this.recordingInvocationHandlerFunction);
        return fspf.create(Echo.class, this.getJrpipUrl());
    }

    private Echo getRecordedEcho() throws MalformedURLException
    {
        FastServletProxyFactory fspf = new FastServletProxyFactory();
        fspf.setUseLocalService(false);
        fspf.setInvocationHandlerFunction(this.recordedInvocationHandlerFunction);
        return fspf.create(Echo.class, this.getJrpipUrl(), 0, true);
    }
}
