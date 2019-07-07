package com.gs.jrpip.client;

import org.apache.commons.httpclient.Cookie;

import java.util.Arrays;
import java.util.Comparator;

public class HttpMessageTransportData implements MessageTransportData
{
    private static final Cookie[] NO_SESSION_COOKIE = new Cookie[0];

    private final AuthenticatedUrl url;
    private final boolean chunkSupported;

    private final long proxyId;
    private Cookie[] cookies;

    public HttpMessageTransportData(AuthenticatedUrl url, boolean chunkSupported, long proxyId)
    {
        this.url = url;
        this.chunkSupported = chunkSupported;
        this.proxyId = proxyId;
        this.cookies = NO_SESSION_COOKIE;
    }

    public HttpMessageTransportData(AuthenticatedUrl url, boolean chunkSupported, long proxyId, Cookie[] cookies)
    {
        this.url = url;
        this.chunkSupported = chunkSupported;
        this.proxyId = proxyId;
        this.cookies = cookies;
    }

    public boolean isChunkSupported()
    {
        return chunkSupported;
    }

    public AuthenticatedUrl getUrl()
    {
        return url;
    }

    public Cookie[] getCookies()
    {
        return cookies;
    }

    @Override
    public long getProxyId()
    {
        return proxyId;
    }

    @Override
    public boolean isSameEndPoint(MessageTransportData other)
    {
        if (other instanceof HttpMessageTransportData)
        {
            return this.url.equals(((HttpMessageTransportData) other).url);
        }
        return false;
    }

    @Override
    public int endPointHashCode()
    {
        return this.url.hashCode();
    }

    @Override
    public String toString()
    {
        return this.url.toString();
    }

    public void setCookies(Cookie[] cookies)
    {
        this.cookies = cookies;
    }

    @Override
    public Object createThankYouKey()
    {
        return new CoalesceThankYouNotesKey(this.url, this.cookies);
    }

    public static final class CoalesceThankYouNotesKey
    {
        private static final Cookie[] NO_COOKIES = new Cookie[0];
        private static final Comparator<? super Cookie> COOKIE_NAME_COMPARATOR =
                (Comparator<Cookie>) (o1, o2) -> o1.getName().compareTo(o2.getName());
        private final AuthenticatedUrl authenticatedUrl;
        private final Cookie[] cookies;

        private CoalesceThankYouNotesKey(AuthenticatedUrl authenticatedUrl, Cookie[] cookies)
        {
            this.authenticatedUrl = authenticatedUrl;
            this.cookies = cookies == null ? NO_COOKIES : this.sort(cookies);
        }

        private Cookie[] sort(Cookie[] cookies)
        {
            Cookie[] result = new Cookie[cookies.length];
            System.arraycopy(cookies, 0, result, 0, cookies.length);
            Arrays.sort(result, COOKIE_NAME_COMPARATOR);
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || this.getClass() != o.getClass())
            {
                return false;
            }

            CoalesceThankYouNotesKey that = (CoalesceThankYouNotesKey) o;

            if (!this.authenticatedUrl.equals(that.authenticatedUrl))
            {
                return false;
            }
            return Arrays.equals(this.cookies, that.cookies);
        }

        @Override
        public int hashCode()
        {
            int result = this.authenticatedUrl.hashCode();
            for(Cookie c: cookies)
            {
                result = 31 * result + c.hashCode();
            }
            return result;
        }

        public Cookie[] getCookies()
        {
            return this.cookies;
        }

        public AuthenticatedUrl getAuthenticatedUrl()
        {
            return this.authenticatedUrl;
        }

        @Override
        public String toString()
        {
            return '{'
                    + "authenticatedUrl: " + this.authenticatedUrl
                    + ", cookies: " + this.cookiesAsString()
                    + '}';
        }

        private String cookiesAsString()
        {
            String result = "[";
            for(Cookie c: this.cookies)
            {
                result = c.getName()+":"+c.getValue();
            }
            result += "]";
            return result;
        }
    }

}
