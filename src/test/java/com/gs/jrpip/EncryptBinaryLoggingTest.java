package com.gs.jrpip;

import com.gs.jrpip.client.SocketMessageTransport;
import com.gs.jrpip.server.SocketServerConfig;

import java.net.MalformedURLException;
import java.text.ParseException;

public class EncryptBinaryLoggingTest extends JrpipSocketServiceBinaryLoggingTest
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
}
