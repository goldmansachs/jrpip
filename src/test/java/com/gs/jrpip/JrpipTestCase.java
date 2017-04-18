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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.client.ThankYouWriter;
import com.gs.jrpip.server.JrpipServlet;
import org.jmock.MockObjectTestCase;
import org.junit.Assert;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpHandler;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.SocketListener;
import org.mortbay.http.UserRealm;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

public abstract class JrpipTestCase
        extends MockObjectTestCase
{
    protected HttpServer server;
    protected JrpipServlet servlet;

    private int port;
    private String jrpipUrl;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        this.setupServerWithHandler(null);
    }

    protected void setupServerWithHandler(HttpHandler handler) throws Exception
    {
        this.setupServerWithHandler(handler, null, null);
    }

    public int getPort()
    {
        return this.port;
    }

    public String getJrpipUrl()
    {
        return this.jrpipUrl;
    }

    protected void setupServerWithHandler(
            HttpHandler handler,
            SecurityConstraint constraint,
            UserRealm realm) throws Exception
    {
        this.port = (int) (Math.random() * 10000.0 + 10000.0);
        this.jrpipUrl = "http://localhost:" + this.port + "/JrpipServlet";
        this.server = new HttpServer();
        SocketListener listener = new SocketListener();
        listener.setPort(this.port);
        this.server.addListener(listener);
        HttpContext context = new HttpContext();
        context.setContextPath("/");

        if (realm != null)
        {
            context.setRealm(realm);
        }

        if (constraint != null)
        {
            context.addSecurityConstraint("/", constraint);
        }

        if (handler != null)
        {
            context.addHandler(handler);
        }

        ServletHandler servletHandler = new ServletHandler();
        context.addHandler(servletHandler);

        ServletHolder holder = servletHandler.addServlet("JrpipServlet", "/JrpipServlet", "com.gs.jrpip.server.JrpipServlet");
        holder.put("serviceInterface.Echo", "com.gs.jrpip.Echo");
        holder.put("serviceClass.Echo", "com.gs.jrpip.EchoImpl");

        this.addCustomConfiguration(holder);

        holder.setInitOrder(10);

        this.server.addContext(context);
        this.server.start();
        this.servlet = (JrpipServlet) holder.getServlet();
    }

    protected void addCustomConfiguration(ServletHolder holder)
    {
    }

    @Override
    protected void tearDown() throws Exception
    {
        FastServletProxyFactory.clearServerChunkSupportAndIds();
        ThankYouWriter.getINSTANCE().stopThankYouThread();
        if (this.server != null)
        {
            this.server.stop();
        }
        super.tearDown();
    }

    protected Echo buildEchoProxy() throws MalformedURLException
    {
        return this.buildEchoProxy(0);
    }

    protected Echo buildEchoProxy(int timeout) throws MalformedURLException
    {
        FastServletProxyFactory fspf = new FastServletProxyFactory();
        fspf.setUseLocalService(false);

        Echo echo;
        if (timeout == 0)
        {
            echo = fspf.create(Echo.class, this.jrpipUrl);
        }
        else
        {
            echo = fspf.create(Echo.class, this.jrpipUrl, timeout);
        }

        Assert.assertNotSame(echo.getClass(), EchoImpl.class);

        return echo;
    }

    public static <E> List<E> newListWith(E... stuff)
    {
        ArrayList<E> es = new ArrayList<E>(stuff.length);
        for(E e: stuff) es.add(e);
        return es;
    }

    public static <E> Set<E> newSetWith(E... stuff)
    {
        HashSet<E> es = new HashSet<E>(stuff.length);
        for(E e: stuff) es.add(e);
        return es;
    }

    public static <E> Map<E, E> newMapWithKeyValues(E... stuff)
    {
        HashMap<E, E> es = new HashMap<E, E>(stuff.length);
        for(int i=0;i<stuff.length;i+=2)
        {
            es.put(stuff[i], stuff[i+1]);
        }
        return es;
    }

}
