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
import com.gs.jrpip.client.JrpipVmBoundException;
import com.gs.jrpip.client.ThankYouWriter;
import com.gs.jrpip.server.JrpipServlet;
import junit.framework.TestCase;
import org.junit.Assert;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SocketListener;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VmBoundServiceTest extends TestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(VmBoundServiceTest.class);
    private static final int PORT = 10123;
    private HttpServer server;
    private JrpipServlet servlet;

    public void testVmBoundEcho() throws Exception
    {
        this.setupServer();

        FastServletProxyFactory fspf = new FastServletProxyFactory();
        fspf.setUseLocalService(false);
        Echo echo = fspf.create(Echo.class, "http://localhost:" + PORT + "/JrpipServlet");
        Assert.assertNotSame(echo.getClass(), EchoImpl.class);
        Assert.assertEquals("hello", echo.echo("hello"));
        Assert.assertEquals("goodbye", echo.echo("goodbye"));
        Thread.sleep(600L);
        Assert.assertTrue(this.servlet.getThankYous() > 0);

        this.tearDownServer();
        this.setupServer();

        try
        {
            echo.echo("hello");
            Assert.fail("must not get here");
        }
        catch (JrpipVmBoundException e)
        {
            LOGGER.info("expected exception thrown for testing", e);
        }
    }

    private void tearDownServer() throws InterruptedException
    {
        ThankYouWriter.getINSTANCE().stopThankYouThread();
        this.server.stop();
        this.server = null;
    }

    @Override
    public void tearDown() throws InterruptedException
    {
        FastServletProxyFactory.clearServerChunkSupportAndIds();
        if (this.server != null)
        {
            ThankYouWriter.getINSTANCE().stopThankYouThread();
            this.server.stop();
            this.server = null;
        }
    }

    private void setupServer() throws Exception
    {
        this.server = new HttpServer();
        SocketListener listener = new SocketListener();
        listener.setPort(PORT);
        this.server.addListener(listener);
        HttpContext context = new HttpContext();
        context.setContextPath("/");

        ServletHandler servletHandler = new ServletHandler();
        context.addHandler(servletHandler);

        // Map a servlet onto the container
        ServletHolder holder =
                servletHandler.addServlet("JrpipServlet", "/JrpipServlet", "com.gs.jrpip.server.JrpipServlet");
        holder.put("serviceInterface.Echo", "com.gs.jrpip.Echo");
        holder.put("vmBoundServiceClass.Echo", "com.gs.jrpip.EchoImpl");
        holder.setInitOrder(10);

        this.server.addContext(context);

        this.server.start();
        this.servlet = (JrpipServlet) holder.getServlet();
    }
}
