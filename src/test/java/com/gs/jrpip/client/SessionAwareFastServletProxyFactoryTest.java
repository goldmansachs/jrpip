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

package com.gs.jrpip.client;

import java.lang.reflect.Proxy;
import java.net.MalformedURLException;

import com.gs.jrpip.Echo;
import com.gs.jrpip.JrpipTestCase;
import junit.framework.TestCase;
import org.apache.commons.httpclient.Cookie;
import org.junit.Assert;

public class SessionAwareFastServletProxyFactoryTest extends JrpipTestCase
{
    public void testCreateSession() throws MalformedURLException
    {
        SessionAwareFastServletProxyFactory factory = new SessionAwareFastServletProxyFactory();
        factory.setUseLocalService(false);
        Echo echo = factory.create(Echo.class, this.getJrpipUrl());
        FastServletProxyInvocationHandler invocationHandler = (FastServletProxyInvocationHandler) Proxy.getInvocationHandler(echo);
        Cookie[] cookies = invocationHandler.getCookies();
        Assert.assertEquals(1, cookies.length);
        TestCase.assertEquals("JSESSIONID", cookies[0].getName());
    }
}
