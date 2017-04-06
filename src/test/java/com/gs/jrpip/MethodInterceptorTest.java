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

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.server.MethodInterceptor;
import com.gs.jrpip.server.JrpipRequestContext;
import org.junit.Assert;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.UserRealm;
import org.mortbay.http.handler.SecurityHandler;
import org.mortbay.jetty.servlet.ServletHolder;

public class MethodInterceptorTest extends JrpipTestCase
{
    private static final String TEST_USER = "testUser";
    private static final String TEST_PASSWORD = "testPassword";

    @Override
    protected void setUp() throws Exception
    {
        // nb: don't perform default server startup (override prevents this)
    }

    @Override
    protected void tearDown() throws Exception
    {
        TestMethodInterceptor.remoteUser = null;
        TestMethodInterceptor.REQUEST_IDS.clear();
        super.tearDown();
    }

    public void testRemoteUserIsSet() throws Exception
    {
        this.setupServerWithHandler(new SecurityHandler(), this.createSecurityConstraint(), new TestRealm());
        FastServletProxyFactory fspf = new FastServletProxyFactory(TEST_USER, TEST_PASSWORD);
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        Assert.assertEquals("hello!", echo.echo("hello!"));
        Assert.assertEquals(TEST_USER, TestMethodInterceptor.remoteUser);
    }

    public void testExceptionOnPreMethodEvaluationBubblesUp() throws Exception
    {
        this.setupServerWithHandler(null);
        FastServletProxyFactory fspf = new FastServletProxyFactory();
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
        this.setupServerWithHandler(null);
        FastServletProxyFactory fspf = new FastServletProxyFactory();
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
        this.setupServerWithHandler(null);
        FastServletProxyFactory fspf = new FastServletProxyFactory();
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
    protected void addCustomConfiguration(ServletHolder holder)
    {
        holder.put("methodInterceptor", TestMethodInterceptor.class.getName());
    }

    private SecurityConstraint createSecurityConstraint()
    {
        SecurityConstraint securityConstraint = new SecurityConstraint();
        securityConstraint.setAuthenticate(true);
        securityConstraint.addRole("*");
        return securityConstraint;
    }

    public static class TestMethodInterceptor implements MethodInterceptor
    {
        private static final Set<RequestId> REQUEST_IDS = new HashSet<RequestId>();
        private static String remoteUser;

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

    protected static class TestRealm implements UserRealm
    {
        @Override
        public String getName()
        {
            return "TestRealm";
        }

        @Override
        public Principal getPrincipal(String username)
        {
            if (username.equals(TEST_USER))
            {
                return new Principal()
                {
                    public String getName()
                    {
                        return TEST_USER;
                    }
                };
            }
            return null;
        }

        @Override
        public Principal authenticate(String username, Object credentials, HttpRequest request)
        {
            if (username.equals(TEST_USER) && credentials instanceof String)
            {
                String pw = (String) credentials;
                if (pw.equals(TEST_PASSWORD))
                {
                    return new Principal()
                    {
                        public String getName()
                        {
                            return TEST_USER;
                        }
                    };
                }
            }
            return null;
        }

        @Override
        public boolean reauthenticate(Principal user)
        {
            return true;
        }

        @Override
        public boolean isUserInRole(Principal user, String role)
        {
            return true;
        }

        @Override
        public void disassociate(Principal user)
        {
        }

        @Override
        public Principal pushRole(Principal user, String role)
        {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Principal popRole(Principal user)
        {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void logout(Principal user)
        {
            throw new RuntimeException("not implemented");
        }
    }
}
