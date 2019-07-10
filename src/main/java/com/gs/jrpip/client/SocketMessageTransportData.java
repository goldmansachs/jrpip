package com.gs.jrpip.client;

import com.gs.jrpip.util.AuthGenerator;

import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.Objects;

public class SocketMessageTransportData implements MessageTransportData
{
    private final String url;
    private final String host;
    private final int port;
    private final long proxyId;
    private final int hashcode;
    private final String username;
    private final byte[] token;
    private final boolean encrypt;

    public SocketMessageTransportData(String url, long proxyId, String username, byte[] token, boolean encrypt) throws MalformedURLException
    {
        this.url = url;
        this.username = username;
        this.token = token;
        this.encrypt = encrypt;
        if (!url.startsWith("jpfs://"))
        {
            throw new MalformedURLException("jpfs url must start with 'jpfs://' "+url);
        }
        String hostport = url.substring("jpfs://".length());
        int colonIndex = hostport.indexOf(':');
        if (colonIndex <= 0)
        {
            throw new MalformedURLException("jpfs url must contain a host:port "+url);
        }

        this.host = hostport.substring(0, colonIndex);
        try
        {
            this.port = Integer.parseInt(hostport.substring(colonIndex+1).trim());
        }
        catch (NumberFormatException e)
        {
            throw new MalformedURLException("jpfs url does not have a valid port "+url);
        }
        this.proxyId = proxyId;
        int hashcode = this.host.hashCode() * 31 + this.port;
        if (username != null)
        {
            hashcode = hashcode * 31 + username.hashCode();
        }
        this.hashcode = hashcode;
    }

    public boolean requiresEncryption()
    {
        return encrypt;
    }

    public String getUsername()
    {
        return username;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public String getUrl()
    {
        return url;
    }

    @Override
    public boolean isSameEndPoint(MessageTransportData o)
    {
        SocketMessageTransportData other = (SocketMessageTransportData) o;
        return this.port == other.port && other.host.equals(this.host) && Objects.equals(this.username, other.username);
    }

    @Override
    public int endPointHashCode()
    {
        return this.hashcode;
    }

    @Override
    public long getProxyId()
    {
        return this.proxyId;
    }

    @Override
    public int hashCode()
    {
        return this.hashcode;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        return obj instanceof SocketMessageTransportData && this.isSameEndPoint((MessageTransportData) obj);
    }

    @Override
    public Object createThankYouKey()
    {
        return this;
    }

    public boolean requiresAuth()
    {
        return this.username != null;
    }

    @Override
    public String toString()
    {
        String result = this.url;
        if (this.username != null)
        {
            result += " with credentials";
        }
        else
        {
            result += " no credentials configured";
        }
        return result;
    }

    public AuthGenerator createAuthGenerator()
    {
        if (this.token != null)
        {
            try
            {
                return new AuthGenerator(token);
            }
            catch (GeneralSecurityException e)
            {
                throw new RuntimeException("Never happens", e);
            }
        }
        return null;
    }
}
