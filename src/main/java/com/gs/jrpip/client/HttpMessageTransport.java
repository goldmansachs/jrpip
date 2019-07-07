package com.gs.jrpip.client;

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.JrpipServiceRegistry;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.server.StreamBasedInvocator;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpMessageTransport implements MessageTransport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMessageTransport.class);
    public static final String MAX_CONNECTIONS_PER_HOST = "fastServletProxyFactory.maxConnectionsPerHost";
    public static final String MAX_TOTAL_CONNECTION = "fastServletProxyFactory.maxTotalConnections";
    private static final MultiThreadedHttpConnectionManager HTTP_CONNECTION_MANAGER = new MultiThreadedHttpConnectionManager();
    private static final Map<String, ServerId> CHUNK_SUPPORTED = new HashMap<>();

    private Credentials credentials;
    private Cookie[] authenticationCookies;
    private HttpInvocationHandlerFunction invocationHandlerFunction = HttpInvocationHandlerFunction.DEFAULT;

    static
    {
        HttpConnectionManagerParams params = HTTP_CONNECTION_MANAGER.getParams();
        int maxConnectionsPerHost =
                Integer.parseInt(System.getProperty(MAX_CONNECTIONS_PER_HOST, "10"));
        int maxTotalConnection =
                Integer.parseInt(System.getProperty(MAX_TOTAL_CONNECTION,
                        String.valueOf(10 * maxConnectionsPerHost)));
        params.setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, maxConnectionsPerHost);
        params.setMaxTotalConnections(maxTotalConnection);
        params.setStaleCheckingEnabled(true);
        params.setSoTimeout(0);
        params.setConnectionTimeout(20000);
        new IdleConnectionCloser(HTTP_CONNECTION_MANAGER).start();
    }

    public HttpMessageTransport()
    {
    }

    public HttpMessageTransport(String user, String password)
    {
        this.credentials = new UsernamePasswordCredentials(user, password);
    }

    // Initialise FastServletProxyFactory with array of tokens, path and domain
    public HttpMessageTransport(String[] tokenArr, String path, String domain)
    {
        Cookie[] cookies = new Cookie[tokenArr.length];
        for (int i = 0; i < tokenArr.length; i++)
        {
            cookies[i] = this.createCookieFromToken(tokenArr[i], path, domain);
        }
        this.authenticationCookies = cookies;
    }

    @Override
    public <T> InvocationHandler createInvocationHandler(Class<T> api, String url, int timeoutMillis, boolean disconnectedMode) throws MalformedURLException
    {
        AuthenticatedUrl authenticatedUrl = new AuthenticatedUrl(url, this.credentials, this.authenticationCookies);
        boolean supportsChunking = disconnectedMode || serverSupportsChunking(authenticatedUrl);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("chunking support at {} :{} received client id: {}", url, supportsChunking, getProxyId(authenticatedUrl));
        }
        return this.invocationHandlerFunction.getInvocationHandler(this, authenticatedUrl, api, timeoutMillis);

    }

    @Override
    public boolean fastFailPing(String url, int timeoutMillis) throws IOException
    {
        return fastFailPing(new AuthenticatedUrl(url, this.credentials), timeoutMillis) == 200;
    }

    @Override
    public void waitForServer(long deadline, MessageTransportData d)
    {
        HttpMessageTransportData data = (HttpMessageTransportData) d;
        boolean noValidResponse = true;
        while (System.currentTimeMillis() < deadline && noValidResponse)
        {
            try
            {
                int code = fastFailPing(data.getUrl(), MessageTransport.PING_TIMEOUT);
                if (code == 401 || code == 403)
                {
                    throw new JrpipRuntimeException("Authorization required for " + data.getUrl() + " (HTTP/" + code + "). Please provide valid credentials to servlet factory!");
                }

                if (code == 404)
                {
                    throw new JrpipRuntimeException("Could not find " + data.getUrl() + " (HTTP/404). Looks like the servlet is not properly configured!");
                }

                if (code == 200)
                {
                    noValidResponse = false;
                }
                else
                {
                    LOGGER.warn("Ping request to {} resulted in HTTP/{}", data.getUrl(), code);
                }
            }
            catch (IOException e)
            {
                LOGGER.warn("could not ping server at {}", data.getUrl(), e);
            }
            try
            {
                if (noValidResponse)
                {
                    Thread.sleep(PING_INTERVAL);
                }
            }
            catch (InterruptedException e)
            {
                // ok, just ignore it
            }
        }
        if (noValidResponse)
        {
            throw new JrpipRuntimeException("Could not reach server at " + data.getUrl());
        }
    }

    protected HttpClient createClient(MessageTransportData d)
    {
        HttpMessageTransportData data = (HttpMessageTransportData) d;
        HttpClient httpClient = getHttpClient(data.getUrl());
        httpClient.getState().addCookies(data.getCookies());
        return httpClient;
    }

    protected HttpMethod getPostMethod(HttpMessageTransportData data, OutputStreamWriter writer, int timeout)
    {
        HttpMethod httpMethod;
        if (data.isChunkSupported())
        {
            httpMethod = new StreamedPostMethod(data.getUrl().getPath(), writer);
        }
        else
        {
            httpMethod = new BufferedPostMethod(data.getUrl().getPath(), writer);
        }
        if (timeout > 0)
        {
            httpMethod.getParams().setSoTimeout(timeout);
        }
        return httpMethod;
    }

    @Override
    public ResponseMessage sendParameters(MessageTransportData data, RequestId requestId, int timeout,
            String serviceClass, String mangledMethodName, Object[] args, Method method, boolean compress)
            throws ClassNotFoundException, IOException
    {
        OutputStreamWriter writer = new ParameterWriter(serviceClass, mangledMethodName, args, requestId);
        HttpMethod postMethod = this.getPostMethod((HttpMessageTransportData) data, writer, timeout);
        return executePostMethod((HttpMessageTransportData) data, args, method, postMethod);
    }

    @Override
    public ResponseMessage requestResend(MessageTransportData data, RequestId requestId, int timeout,
            Object[] args, Method method, boolean compress) throws ClassNotFoundException, IOException
    {
        OutputStreamWriter writer = new ResultResendWriter(requestId);
        HttpMethod postMethod = this.getPostMethod((HttpMessageTransportData) data, writer, timeout);
        return executePostMethod((HttpMessageTransportData) data, args, method, postMethod);
    }

    @Override
    public boolean sendThanks(Object k, List<ThankYouWriter.ThankYouRequest> requestList) throws IOException
    {
        HttpMethod streamedPostMethod = null;
        HttpMessageTransportData.CoalesceThankYouNotesKey key = (HttpMessageTransportData.CoalesceThankYouNotesKey) k;
        try
        {
            AuthenticatedUrl url = key.getAuthenticatedUrl();
            HttpClient httpClient = getHttpClient(url);
            httpClient.getState().addCookies(key.getCookies());
            OutputStreamWriter writer = new ThankYouStreamWriter(requestList);
            streamedPostMethod = serverSupportsChunking(url) ? new StreamedPostMethod(url.getPath() + "?thanks", writer) : new BufferedPostMethod(url.getPath() + "?thanks", writer);
            httpClient.executeMethod(streamedPostMethod);

            int code = streamedPostMethod.getStatusCode();

            streamedPostMethod.getResponseBodyAsStream().close();
            streamedPostMethod.releaseConnection();
            streamedPostMethod = null;
            return code == 200;
        }
        finally
        {
            if (streamedPostMethod != null)
            {
                streamedPostMethod.releaseConnection();
            }
        }
    }

    @Override
    public void initAndRegisterLocalServices(String url, boolean disconnectedMode, int timeout) throws MalformedURLException
    {
        AuthenticatedUrl authenticatedUrl = new AuthenticatedUrl(url, this.credentials, this.authenticationCookies);
        if (!disconnectedMode)
        {
            serverSupportsChunking(authenticatedUrl);
        }
    }

    private ResponseMessage executePostMethod(HttpMessageTransportData data, Object[] args,
            Method method, HttpMethod postMethod) throws IOException, ClassNotFoundException
    {
        HttpClient httpClient = createClient(data);
        InputStream is = null;
        Object returned = null;
        try
        {
            httpClient.executeMethod(postMethod);

            data.setCookies(httpClient.getState().getCookies());

            int code = postMethod.getStatusCode();

            if (code != 200)
            {
                return ResponseMessage.forTransportErrorCode(code, postMethod.getResponseBodyAsString());
            }

            is = postMethod.getResponseBodyAsStream();

            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");

            byte status = (byte) is.read();
            if (status != StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS)
            {
                returned = this.getResult(method, args, is);
            }
            is.close();
            is = null;
            postMethod.releaseConnection();
            postMethod = null;
            return ResponseMessage.forSuccess(status, returned);
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    LOGGER.debug("Could not close stream. See previous exception for cause", e);
                }
            }
            if (postMethod != null)
            {
                postMethod.releaseConnection();
            }
        }
    }

    protected Object getResult(Method method, Object[] args, InputStream is) throws IOException, ClassNotFoundException
    {
        FixedInflaterInputStream zipped = new FixedInflaterInputStream(is);
        ObjectInputStream in = null;
        try
        {
            in = new ObjectInputStream(zipped);
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            return in.readObject();
        }
        finally
        {
            zipped.finish(); // deallocate memory

            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    LOGGER.debug("Could not close stream. See previous exception for cause", e);
                }
            }
        }
    }

    protected class ParameterWriter extends JrpipRequestWriter
    {
        private final String serviceClassName;
        private final String mangledMethodName;
        private final Object[] args;
        private final RequestId requestId;

        public ParameterWriter(String serviceClassName, String mangledMethodName, Object[] args, RequestId requestId)
        {
            this.serviceClassName = serviceClassName;
            this.mangledMethodName = mangledMethodName;
            this.args = args;
            this.requestId = requestId;
        }

        @Override
        public byte getRequestType()
        {
            return StreamBasedInvocator.INVOKE_REQUEST;
        }

        @Override
        public void writeParameters(ObjectOutputStream objectOutputStream) throws IOException
        {
            objectOutputStream.writeObject(this.requestId);
            objectOutputStream.writeObject(serviceClassName);
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            objectOutputStream.writeObject(this.mangledMethodName);
            if (this.args != null)
            {
                for (int i = 0; i < this.args.length; i++)
                {
                    //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
                    objectOutputStream.writeObject(this.args[i]);
                }
            }
        }
    }

    protected static class ResultResendWriter extends JrpipRequestWriter
    {
        private final RequestId requestId;

        protected ResultResendWriter(RequestId requestId)
        {
            this.requestId = requestId;
        }

        @Override
        public byte getRequestType()
        {
            return StreamBasedInvocator.RESEND_REQUEST;
        }

        @Override
        public void writeParameters(ObjectOutputStream objectOutputStream) throws IOException
        {
            objectOutputStream.writeObject(this.requestId);
        }
    }

    /**
     * @return the http response code returned from the server. Response code 200 means success.
     */
    public static int fastFailPing(AuthenticatedUrl url, int timeout) throws IOException
    {
        PingRequest pingRequest = null;
        try
        {
            HttpClient httpClient = getHttpClient(url);
            pingRequest = new PingRequest(url.getPath());
            HttpMethodParams params = pingRequest.getParams();
            params.setSoTimeout(timeout);
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
        ServerId result = CHUNK_SUPPORTED.get(key);
        if (result == null)
        {
            return generateRandomProxyId();
        }
        return result.getProxyId();
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
        ServerId result = CHUNK_SUPPORTED.get(key);
        return result != null && result.isChunkSupported();
    }

    public void setHttpInvocationHandlerFunction(HttpInvocationHandlerFunction invocationHandlerFunction)
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
            catch (IOException | RuntimeException e)
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


    public static void setMaxConnectionsPerHost(int maxConnections)
    {
        int currentMaxTotalConnection = HttpMessageTransport.HTTP_CONNECTION_MANAGER.getParams().getMaxTotalConnections();

        if (maxConnections > currentMaxTotalConnection)
        {
            HttpMessageTransport.setMaxTotalConnections(maxConnections * 10);
        }
        HttpMessageTransport.HTTP_CONNECTION_MANAGER.getParams().setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, maxConnections);
    }

    public static void setMaxTotalConnections(int maxTotalConnections)
    {
        HttpMessageTransport.HTTP_CONNECTION_MANAGER.getParams().setMaxTotalConnections(maxTotalConnections);
    }

    public static int getMaxTotalConnection()
    {
        return HttpMessageTransport.HTTP_CONNECTION_MANAGER.getParams().getMaxTotalConnections();
    }

    public static int getMaxConnectionsPerHost()
    {
        return HttpMessageTransport.HTTP_CONNECTION_MANAGER.getParams()
                .getMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION);
    }

    public static void clearServerChunkSupportAndIds()
    {
        CHUNK_SUPPORTED.clear();
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

    protected static class ThankYouStreamWriter extends JrpipRequestWriter
    {
        private final List<ThankYouWriter.ThankYouRequest> requestList;

        protected ThankYouStreamWriter(List<ThankYouWriter.ThankYouRequest> requestList)
        {
            this.requestList = requestList;
        }

        @Override
        public byte getRequestType()
        {
            return StreamBasedInvocator.THANK_YOU_REQUEST;
        }

        @Override
        public void writeParameters(ObjectOutputStream objectOutputStream) throws IOException
        {
            objectOutputStream.writeInt(this.requestList.size());
            for (ThankYouWriter.ThankYouRequest request : this.requestList)
            {
                //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
                objectOutputStream.writeObject(request.getRequestId());
            }
        }
    }

}
