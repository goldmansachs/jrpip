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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.gs.jrpip.JrpipServiceRegistry;
import com.gs.jrpip.server.StreamBasedInvocator;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpConstants;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastServletProxyFactory implements ServletProxyFactory
{
    public static final String MAX_CONNECTIONS_PER_HOST = "fastServletProxyFactory.maxConnectionsPerHost";
    public static final String MAX_TOTAL_CONNECTION = "fastServletProxyFactory.maxTotalConnections";
    private static final int PING_TIMEOUT = 5000;
    private static final MultiThreadedHttpConnectionManager HTTP_CONNECTION_MANAGER = new MultiThreadedHttpConnectionManager();
    private static final Map CHUNK_SUPPORTED = new HashMap();
    private static final Logger LOGGER = LoggerFactory.getLogger(FastServletProxyFactory.class);

    private Credentials credentials;
    private Cookie[] authenticationCookies;
    private boolean useLocalService = true;
    private InvocationHandlerFunction invocationHandlerFunction = InvocationHandlerFunction.DEFAULT;

    static
    {
        HttpConnectionManagerParams params = FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams();
        int maxConnectionsPerHost =
                Integer.parseInt(System.getProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST, "10"));
        int maxTotalConnection =
                Integer.parseInt(System.getProperty(FastServletProxyFactory.MAX_TOTAL_CONNECTION,
                        String.valueOf(10 * maxConnectionsPerHost)));
        params.setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, maxConnectionsPerHost);
        params.setMaxTotalConnections(maxTotalConnection);
        params.setStaleCheckingEnabled(true);
        params.setSoTimeout(0);
        params.setConnectionTimeout(20000);
        new IdleConnectionCloser(FastServletProxyFactory.HTTP_CONNECTION_MANAGER).start();
    }

    /**
     * Creates the new proxy factory.
     */
    public FastServletProxyFactory()
    {
    }

    public FastServletProxyFactory(String user, String password)
    {
        this.credentials = new UsernamePasswordCredentials(user, password);
    }

    // Initialise FastServletProxyFactory with array of tokens, path and domain
    public FastServletProxyFactory(String[] tokenArr, String path, String domain)
    {
        Cookie[] cookies = new Cookie[tokenArr.length];
        for (int i = 0; i < tokenArr.length; i++)
        {
            cookies[i] = this.createCookieFromToken(tokenArr[i], path, domain);
        }
        this.authenticationCookies = cookies;
    }

    public static void setMaxConnectionsPerHost(int maxConnections)
    {
        int currentMaxTotalConnection = FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams().getMaxTotalConnections();

        if (maxConnections > currentMaxTotalConnection)
        {
            FastServletProxyFactory.setMaxTotalConnections(maxConnections * 10);
        }
        FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams().setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, maxConnections);
    }

    public static void setMaxTotalConnections(int maxTotalConnections)
    {
        FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams().setMaxTotalConnections(maxTotalConnections);
    }

    public static int getMaxTotalConnection()
    {
        return FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams().getMaxTotalConnections();
    }

    public static int getMaxConnectionsPerHost()
    {
        return FastServletProxyFactory.HTTP_CONNECTION_MANAGER.getParams()
                .getMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION);
    }

    // Create a Cookie from the Authenticated token and using specified path and domain
    private Cookie createCookieFromToken(String token, String path, String domain)
    {
        Cookie cookie = new Cookie();
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setName(token.substring(0, token.indexOf('=')));
        cookie.setValue(token.substring(token.indexOf('=') + 1));
        return cookie;
    }

    public void setUseLocalService(boolean useLocalService)
    {
        this.useLocalService = useLocalService;
    }

    public static void clearServerChunkSupportAndIds()
    {
        CHUNK_SUPPORTED.clear();
    }

    /**
     * Creates a new proxy with the specified URL.  The returned object
     * is a proxy with the interface specified by api.
     * <p/>
     * <pre>
     * String url = "http://localhost:7001/objectmanager/JrpipServlet");
     * RemoteObjectManager rom = (RemoteObjectManager) factory.create(RemoteObjectManager.class, url);
     * </pre>
     *
     * @param api the interface the proxy class needs to implement
     * @param url the URL where the client object is located.
     * @return a proxy to the object with the specified interface.
     */
    @Override
    public <T> T create(Class<T> api, String url) throws MalformedURLException
    {
        return this.create(api, url, 0);
    }

    /**
     * Creates a new proxy with the specified URL.  The returned object
     * is a proxy with the interface specified by api.
     * <p/>
     * <pre>
     * String url = "http://localhost:7001/objectmanager/JrpipServlet");
     * RemoteObjectManager rom = (RemoteObjectManager) factory.create(RemoteObjectManager.class, url);
     * </pre>
     *
     * @param api           the interface the proxy class needs to implement
     * @param url           the URL where the client object is located.
     * @param timeoutMillis maximum timeoutMillis for remote method call to run, zero for no timeoutMillis
     * @return a proxy to the object with the specified interface.
     */
    @Override
    public <T> T create(Class<T> api, String url, int timeoutMillis) throws MalformedURLException
    {
        return this.create(api, url, timeoutMillis, false);
    }

    /**
     * Creates a new proxy with the specified URL.  The returned object
     * is a proxy with the interface specified by api.
     * <p/>
     * <pre>
     * String url = "http://localhost:7001/objectmanager/JrpipServlet");
     * RemoteObjectManager rom = (RemoteObjectManager) factory.create(RemoteObjectManager.class, url);
     * </pre>
     *
     * @param api           the interface the proxy class needs to implement
     * @param url           the URL where the client object is located.
     * @param timeoutMillis maximum timeoutMillis for remote method call to run, zero for no timeoutMillis
     * @return a proxy to the object with the specified interface.
     */
    public <T> T create(Class<T> api, String url, int timeoutMillis, boolean disconnectedMode) throws MalformedURLException
    {
        T result = null;
        if (this.useLocalService)
        {
            result = JrpipServiceRegistry.getInstance().getLocalService(url, api);
        }
        if (result == null)
        {
            AuthenticatedUrl authenticatedUrl = new AuthenticatedUrl(url, this.credentials, this.authenticationCookies);
            boolean supportsChunking = disconnectedMode || serverSupportsChunking(authenticatedUrl);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("chunking support at {} :{} received client id: {}", url, supportsChunking, getProxyId(authenticatedUrl));
            }
            if (this.useLocalService)
            {
                result = JrpipServiceRegistry.getInstance().getLocalService(url, api);
            }
            if (result == null)
            {
                InvocationHandler handler = this.invocationHandlerFunction.getInvocationHandler(authenticatedUrl, api, timeoutMillis);
                result = (T) Proxy.newProxyInstance(api.getClassLoader(),
                        new Class[]{api},
                        handler);
            }
        }
        return result;
    }

    /**
     * @return the http response code returned from the server. Response code 200 means success.
     */
    public static int fastFailPing(AuthenticatedUrl url) throws IOException
    {
        PingRequest pingRequest = null;
        try
        {
            HttpClient httpClient = getHttpClient(url);
            pingRequest = new PingRequest(url.getPath());
            HttpMethodParams params = pingRequest.getParams();
            params.setSoTimeout(PING_TIMEOUT);
            httpClient.executeMethod(pingRequest);
            return pingRequest.getStatusCode();
        }
        finally
        {
            if (pingRequest != null)
            {
                pingRequest.releaseConnection();
            }
        }
    }

    @Override
    public boolean isServiceAvailable(String url)
    {
        boolean result = false;
        try
        {
            result = FastServletProxyFactory.fastFailPing(new AuthenticatedUrl(url, this.credentials)) == 200;
        }
        catch (IOException e)
        {
            LOGGER.debug("ping failed with ", e);
        }
        return result;
    }

    public static HttpClient getHttpClient(AuthenticatedUrl url)
    {
        HttpClient httpClient = new HttpClient(HTTP_CONNECTION_MANAGER);
        httpClient.getHostConfiguration().setHost(url.getHost(), url.getPort());
        httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, NoRetryRetryHandler.getInstance());
        url.setCredentialsOnClient(httpClient);
        return httpClient;
    }

    public static long getProxyId(AuthenticatedUrl url)
    {
        serverSupportsChunking(url); // make sure we've talked to server at least once
        String key = url.getHost() + ":" + url.getPort();
        ServerId result = (ServerId) CHUNK_SUPPORTED.get(key);
        if (result == null)
        {
            return generateRandomProxyId();
        }
        return result.getProxyId();
    }

    public static boolean serverSupportsChunking(AuthenticatedUrl url)
    {
        String key = url.getHost() + ":" + url.getPort();
        if (CHUNK_SUPPORTED.get(key) == null)
        {
            ChunkedInitMethod chunkedInitMethod = null;
            try
            {
                HttpClient httpClient = getHttpClient(url);
                chunkedInitMethod = new ChunkedInitMethod(url.getPath(), url.getNonAuthenticatedUrl());
                chunkedInitMethod.getParams().setSoTimeout(20000);
                httpClient.executeMethod(chunkedInitMethod);

                int code = chunkedInitMethod.getStatusCode();
                switch (code)
                {
                    case 200:
                        CHUNK_SUPPORTED.put(key, new ServerId(true, Long.parseLong(chunkedInitMethod.getResponseBodyAsString())));
                        break;
                    case 400:
                    case 500:
                        CHUNK_SUPPORTED.put(key, new ServerId(false, generateRandomProxyId()));
                        break;
                    case 404:
                        LOGGER.error("Could not find {} (HTTP/404). Looks like the servlet is not properly configured!", url);
                        break;
                    case 401:
                    case 403:
                        throw new JrpipRuntimeException("Authorization required for " + url + " (HTTP/" + code + "). Please provide valid credentials to servlet factory!");
                    default:
                        LOGGER.error("unhandled response code {} while determining chunk support", code);
                        break;
                }
            }
            catch (IOException e)
            {
                if (!isServerDownOrBusy(url, e))
                {
                    LOGGER.error("Could not determine chunk support for {} ", url, e);
                    CHUNK_SUPPORTED.put(key, new ServerId(false, generateRandomProxyId())); // we really shouldn't do this, but oh well, weblogic 5 is a piece of crap
                }
            }
            finally
            {
                if (chunkedInitMethod != null)
                {
                    chunkedInitMethod.releaseConnection();
                }
            }
        }
        ServerId result = (ServerId) CHUNK_SUPPORTED.get(key);
        return result != null && result.isChunkSupported();
    }

    private static boolean isServerDownOrBusy(AuthenticatedUrl url, Throwable e)
    {
        if (e instanceof ConnectException || e instanceof SocketTimeoutException)
        {
            LOGGER.error("Looks like the service at {} is down or not responding. Could not determine chunk support.", url, e);
            return true;
        }
        if (e.getCause() == null)
        {
            return false;
        }
        return isServerDownOrBusy(url, e.getCause());
    }

    private static long generateRandomProxyId()
    {
        return (long) (Math.random() * 2000000000000L);
    }

    public void setInvocationHandlerFunction(InvocationHandlerFunction invocationHandlerFunction)
    {
        this.invocationHandlerFunction = invocationHandlerFunction;
    }

    public static Cookie[] requestNewSession(AuthenticatedUrl url)
    {
        CreateSessionRequest createSessionRequest = null;
        try
        {
            HttpClient httpClient = getHttpClient(url);
            createSessionRequest = new CreateSessionRequest(url.getPath());
            HttpMethodParams params = createSessionRequest.getParams();
            params.setSoTimeout(PING_TIMEOUT);
            httpClient.executeMethod(createSessionRequest);
            return httpClient.getState().getCookies();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to create session", e);
        }
        finally
        {
            if (createSessionRequest != null)
            {
                createSessionRequest.releaseConnection();
            }
        }
    }

    public static class ServerId
    {
        private final boolean chunkSupported;
        private final long proxyId;

        public ServerId(boolean chunkSupported, long proxyId)
        {
            this.chunkSupported = chunkSupported;
            this.proxyId = proxyId;
        }

        public boolean isChunkSupported()
        {
            return this.chunkSupported;
        }

        public long getProxyId()
        {
            return this.proxyId;
        }
    }

    public static class ChunkedInitMethod extends EntityEnclosingMethod
    {
        private static final byte[] CRLF = {(byte) 13, (byte) 10};

        private static final byte[] ZERO = {(byte) '0'};

        private final URL url;

        public ChunkedInitMethod(String uri, URL url)
        {
            super(uri);
            this.setContentChunked(true);
            this.url = url;
        }

        @Override
        public String getName()
        {
            return "POST";
        }

        @Override
        protected boolean hasRequestContent()
        {
            return true;
        }

        @Override
        protected void writeRequest(HttpState state, HttpConnection conn) throws IOException
        {
            try
            {
                this.writeRequestLine(state, conn);
                this.writeRequestHeaders(state, conn);
                conn.writeLine(); // close head
                // make sure the status line and headers have been sent
                conn.flushRequestOutputStream();
                OutputStream stream = conn.getRequestOutputStream();
                // ping chunk:

                byte[] servletUrl = this.url.toString().getBytes(JrpipServiceRegistry.ENCODE_STRING);
                byte[] chunkHeader = HttpConstants.getBytes(
                        Integer.toHexString(1 + servletUrl.length) + "\r\n");
                stream.write(chunkHeader, 0, chunkHeader.length);
                stream.write(StreamBasedInvocator.INIT_REQUEST);
                stream.write(servletUrl);
                stream.write(CRLF, 0, CRLF.length);
                // end chunk:
                stream.write(ZERO, 0, ZERO.length);
                stream.write(CRLF, 0, CRLF.length);
                stream.write(CRLF, 0, CRLF.length);

                conn.flushRequestOutputStream();
            }
            catch (IOException e)
            {
                this.cleanupConnection(conn);
                throw e;
            }
            catch (RuntimeException e)
            {
                this.cleanupConnection(conn);
                throw e;
            }
        }

        protected void cleanupConnection(HttpConnection conn)
        {
            conn.close();
            conn.releaseConnection();
        }
    }

    private static final class IdleConnectionCloser extends Thread
    {
        private static final long IDLE_CONNECTION_CLOSE_TIME = 15000L;
        private static final long IDLE_CONNECTION_SLEEP_TIME = 1000L;
        private final HttpConnectionManager httpConnectionManager;

        private IdleConnectionCloser(HttpConnectionManager httpConnectionManager)
        {
            super("FastServletProxyFactory.IdleConnectionCloser");
            this.httpConnectionManager = httpConnectionManager;
            this.setName("IdleConnectionCloser");
            this.setDaemon(true);
            this.setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run()
        {
            while (true)
            {
                try
                {
                    sleep(IDLE_CONNECTION_SLEEP_TIME);
                    this.httpConnectionManager.closeIdleConnections(IDLE_CONNECTION_CLOSE_TIME);
                }
                catch (Throwable t)
                {
                    LOGGER.error("error in idle connection closer", t);
                }
            }
        }
    }

    private static class NoRetryRetryHandler implements HttpMethodRetryHandler
    {
        private static final NoRetryRetryHandler INSTANCE = new NoRetryRetryHandler();

        public static NoRetryRetryHandler getInstance()
        {
            return INSTANCE;
        }

        @Override
        public boolean retryMethod(HttpMethod httpMethod, IOException e, int i)
        {
            return false;
        }
    }

    protected static class PingRequest extends EntityEnclosingMethod
    {
        protected PingRequest(String uri)
        {
            super(uri);
        }

        @Override
        protected boolean writeRequestBody(HttpState httpState, HttpConnection httpConnection) throws IOException
        {
            OutputStream outstream = httpConnection.getRequestOutputStream();
            outstream.write(StreamBasedInvocator.PING_REQUEST);
            outstream.flush();
            return true;
        }

        @Override
        protected boolean hasRequestContent()
        {
            return true;
        }

        @Override
        protected long getRequestContentLength()
        {
            return 1L;
        }

        @Override
        public String getName()
        {
            return "POST";
        }
    }

    protected static class CreateSessionRequest extends EntityEnclosingMethod
    {
        protected CreateSessionRequest(String uri)
        {
            super(uri);
        }

        @Override
        protected boolean writeRequestBody(HttpState httpState, HttpConnection httpConnection) throws IOException
        {
            OutputStream outstream = httpConnection.getRequestOutputStream();
            outstream.write(StreamBasedInvocator.CREATE_SESSION_REQUEST);
            outstream.flush();
            return true;
        }

        @Override
        protected boolean hasRequestContent()
        {
            return true;
        }

        @Override
        protected long getRequestContentLength()
        {
            return 1L;
        }

        @Override
        public String getName()
        {
            return "POST";
        }
    }
}

