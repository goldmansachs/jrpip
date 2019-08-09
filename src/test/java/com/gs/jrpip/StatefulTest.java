package com.gs.jrpip;

import com.gs.jrpip.server.SocketServerConfig;
import org.junit.Assert;

public class StatefulTest extends SocketTestCase
{
    @Override
    protected SocketServerConfig createConfig()
    {
        SocketServerConfig config = new SocketServerConfig(0);
        config.setServerSocketTimeout(50);
        config.addServiceInstance(Echo.class, new ConstEcho("foobar"));
        this.addMoreConfig(config);
        return config;
    }

    public void testStateful() throws Exception
    {
        Assert.assertEquals("foobar", this.buildEchoProxy().echo("gnat"));
    }
}
