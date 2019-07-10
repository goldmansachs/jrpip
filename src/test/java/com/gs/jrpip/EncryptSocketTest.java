package com.gs.jrpip;

import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.SocketMessageTransport;
import com.gs.jrpip.server.SocketServerConfig;
import org.junit.Assert;

import java.net.MalformedURLException;
import java.text.ParseException;

public class EncryptSocketTest extends SimpleSocketServiceTest
{
    public static final String TOKEN = "nmmnswer263476i623rqwertq";

    @Override
    protected void addMoreConfig(SocketServerConfig config)
    {
        try
        {
            config.addCredentials("fred", TOKEN);
        }
        catch (ParseException e)
        {
            throw new RuntimeException("bad token", e);
        }
    }

    @Override
    protected Echo buildEchoProxy(int timeout) throws MalformedURLException
    {
        SocketMessageTransport transport = new SocketMessageTransport("fred", TOKEN, true);
        return buildEchoFromTransport(timeout, transport);
    }

    public void testUnauthorized() throws Exception
    {
        SocketMessageTransport transport = new SocketMessageTransport("fred", "asdf", true);
        Echo echo = buildEchoFromTransport(0, transport);
        try
        {
            echo.echo("hello");
            Assert.fail("must not get here!");
        }
        catch(JrpipRuntimeException e)
        {
            Assert.assertTrue(e.getMessage().toLowerCase().contains("auth"));
        }
    }

    public void testUnauthorizedNoUser() throws Exception
    {
        SocketMessageTransport transport = new SocketMessageTransport();
        Echo echo = buildEchoFromTransport(0, transport);
        try
        {
            echo.echo("hello");
            Assert.fail("must not get here!");
        }
        catch(JrpipRuntimeException e)
        {
            Assert.assertTrue(e.getMessage().toLowerCase().contains("auth"));
        }
    }
}
