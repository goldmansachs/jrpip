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

import java.security.Principal;

import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.client.JrpipRuntimeException;
import org.junit.Assert;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.UserRealm;
import org.mortbay.http.handler.SecurityHandler;

public class AuthenticatedServiceTest extends JrpipTestCase
{
    private static final String TEST_USER = "testUser";
    private static final String TEST_PASSWORD = "testPassword";

    @Override
    protected void setUp() throws Exception
    {
        // nb: don't perform default server startup (override prevents this)
    }

    private SecurityConstraint createSecurityConstraint()
    {
        SecurityConstraint securityConstraint = new SecurityConstraint();
        securityConstraint.setAuthenticate(true);
        securityConstraint.addRole("*");
        return securityConstraint;
    }

    public void testMissingAuthentication() throws Exception
    {
        this.setupServerWithHandler(new SecurityHandler(), this.createSecurityConstraint(), new TestRealm());

        try
        {
            Echo echo = this.buildEchoProxy();

            echo.echo("error");
            Assert.fail("should've failed with a 401 error");
        }
        catch (JrpipRuntimeException e)
        {
            // ok
        }
    }

    public void testWithAuthentication() throws Exception
    {
        this.setupServerWithHandler(new SecurityHandler(), this.createSecurityConstraint(), new TestRealm());

        FastServletProxyFactory fspf = new FastServletProxyFactory(TEST_USER, TEST_PASSWORD);
        Echo echo = fspf.create(Echo.class, this.getJrpipUrl());
        Assert.assertSame(EchoImpl.class, echo.getClass());
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
