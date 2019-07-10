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
import com.gs.jrpip.client.MtProxyFactory;
import com.gs.jrpip.client.ThankYouWriter;
import com.gs.jrpip.server.SocketServerConfig;
import com.gs.jrpip.server.SocketServer;
import org.jmock.MockObjectTestCase;
import org.junit.Assert;

import java.io.IOException;
import java.net.MalformedURLException;

public abstract class SocketTestCase
        extends MockObjectTestCase
{
    protected SocketServer server;

    private int port;
    private String jrpipUrl;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        this.setupServer();
    }

    public int getPort()
    {
        return this.port;
    }

    public String getJrpipUrl()
    {
        return this.jrpipUrl;
    }

    protected void setupServer() throws Exception
    {
        SocketServerConfig config = createConfig();
        startServer(config);
    }

    protected void startServer(SocketServerConfig config) throws IOException
    {
        this.server = new SocketServer(config);
        this.server.start();
        this.port = this.server.getPort();
        this.jrpipUrl = "jpfs://localhost:" + this.port;
    }

    protected SocketServerConfig createConfig()
    {
        SocketServerConfig config = new SocketServerConfig(0);
        config.setServerSocketTimeout(50);
        config.addServiceConfig(Echo.class, EchoImpl.class);
        this.addMoreConfig(config);
        return config;
    }

    protected void addMoreConfig(SocketServerConfig config)
    {

    }

    @Override
    protected void tearDown() throws Exception
    {
        ThankYouWriter.getINSTANCE().stopThankYouThread();
        if (this.server != null)
        {
            this.server.stop();
        }
        SocketMessageTransport.clearServerStatus();
        super.tearDown();
    }

    protected Echo buildEchoProxy() throws MalformedURLException
    {
        return this.buildEchoProxy(0);
    }

    protected Echo buildEchoProxy(int timeout) throws MalformedURLException
    {
        SocketMessageTransport transport = new SocketMessageTransport();
        return buildEchoFromTransport(timeout, transport);
    }

    protected Echo buildEchoFromTransport(int timeout, SocketMessageTransport transport) throws MalformedURLException
    {
        MtProxyFactory factory = new MtProxyFactory(transport);
        factory.setUseLocalService(false);

        Echo echo;
        if (timeout == 0)
        {
            echo = factory.create(Echo.class, this.jrpipUrl);
        }
        else
        {
            echo = factory.create(Echo.class, this.jrpipUrl, timeout);
        }

        Assert.assertNotSame(echo.getClass(), EchoImpl.class);

        return echo;
    }
}
