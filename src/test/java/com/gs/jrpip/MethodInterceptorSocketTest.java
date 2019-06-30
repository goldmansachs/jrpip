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

import com.gs.jrpip.client.MtProxyFactory;
import com.gs.jrpip.client.SocketMessageTransport;
import com.gs.jrpip.server.JrpipRequestContext;
import com.gs.jrpip.server.MethodInterceptor;
import com.gs.jrpip.server.SocketServerConfig;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class MethodInterceptorSocketTest extends SocketTestCase
{
    private static final String TEST_USER = "testUser";
    private static final String TEST_PASSWORD = "testPassword";

    private TestMethodInterceptor methodInterceptor;

    @Override
    protected void setUp() throws Exception
    {
        // nb: don't perform default server startup (override prevents this)
    }

    @Override
    protected void tearDown() throws Exception
    {
        TestMethodInterceptor.REQUEST_IDS.clear();
        super.tearDown();
    }

    public void testRemoteUserIsSet() throws Exception
    {
        SocketServerConfig config = this.createConfig();
        config.addCredentials(TEST_USER, TEST_PASSWORD);
        this.startServer(config);
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport(TEST_USER, TEST_PASSWORD));
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        Assert.assertEquals("hello!", echo.echo("hello!"));
        Assert.assertEquals(TEST_USER, this.methodInterceptor.remoteUser);
    }

    public void testExceptionOnPreMethodEvaluationBubblesUp() throws Exception
    {
        SocketServerConfig config = this.createConfig();
        this.startServer(config);
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport());
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());

        try
        {
            echo.echo("throw");
            Assert.fail();
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("Throwing...", e.getMessage());
        }
    }

    public void testPostMethodEvaluationReplacesResultWithItsOwnException() throws Exception
    {
        SocketServerConfig config = this.createConfig();
        this.startServer(config);
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport());
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        try
        {
            echo.echo("throwAfter");
            Assert.fail();
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("replace result with exception", e.getMessage());
        }
    }

    public void testPostMethodEvaluationReplacesExceptionWithItsOwnException() throws Exception
    {
        SocketServerConfig config = this.createConfig();
        this.startServer(config);
        MtProxyFactory fspf = new MtProxyFactory(new SocketMessageTransport());
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        try
        {
            echo.throwUnexpectedException();
            Assert.fail();
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("replace exception with exception", e.getMessage());
        }
    }

    @Override
    protected void addMoreConfig(SocketServerConfig config)
    {
        this.methodInterceptor = new TestMethodInterceptor();
        config.setMethodInterceptor(methodInterceptor);
    }

    public static class TestMethodInterceptor implements MethodInterceptor
    {
        private static final Set<RequestId> REQUEST_IDS = new HashSet<RequestId>();
        private String remoteUser;

        @Override
        public void beforeMethodEvaluation(JrpipRequestContext context, Method method, Object[] arguments)
        {
            REQUEST_IDS.add(context.getRequestId());
            remoteUser = context.getRemoteUser();
            if ("echo".equals(method.getName()) && "throw".equals(arguments[0]))
            {
                throw new RuntimeException("Throwing...");
            }
        }

        @Override
        public void afterMethodEvaluationFinishes(JrpipRequestContext context, Method method, Object[] arguments, Object returnValue)
        {
            this.checkRequestWasHandledByPre(context);

            if ("throwAfter".equals(returnValue))
            {
                throw new RuntimeException("replace result with exception");
            }
        }

        @Override
        public void afterMethodEvaluationFails(JrpipRequestContext context, Method method, Object[] arguments, Throwable e)
        {
            this.checkRequestWasHandledByPre(context);

            if ("throwUnexpectedException".equals(method.getName()))
            {
                throw new RuntimeException("replace exception with exception", e);
            }
        }

        private void checkRequestWasHandledByPre(JrpipRequestContext context)
        {
            if (!REQUEST_IDS.remove(context.getRequestId()))
            {
                throw new IllegalArgumentException("Post method called without pre...");
            }
        }
    }
}
