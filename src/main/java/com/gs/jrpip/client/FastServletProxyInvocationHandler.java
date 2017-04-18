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
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.MethodResolver;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.server.Context;
import com.gs.jrpip.server.StreamBasedInvocator;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastServletProxyInvocationHandler
        implements InvocationHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FastServletProxyInvocationHandler.class);

    private static final int SEND_PARAMETERS_STATE = 0;
    private static final int RECEIVE_RESULT_STATE = 1;

    private static final int RETRY_COUNT = 4;

    private static final long PING_INTERVAL = 5000L;

    private static final long MAX_WAIT_FOR_NETWORK_FAILURE = 120000L;
    //private static final boolean CAUSE_RANDOM_ERROR = true;

    // private static final double ERROR_RATE = 0.98;
    private static final Cookie[] NO_SESSION_COOKIE = new Cookie[0];

    private final AuthenticatedUrl url;
    private final MethodResolver methodResolver;
    private final boolean chunkSupported;

    private final long proxyId;
    private final int timeout;
    private Cookie[] cookies;

    protected FastServletProxyInvocationHandler(
            AuthenticatedUrl url,
            Class api,
            int timeout)
    {
        this(url, api, timeout, false);
    }

    protected FastServletProxyInvocationHandler(
            AuthenticatedUrl url,
            Class api,
            int timeout,
            boolean stickySessionEnabled)
    {
        this.url = url;
        this.methodResolver = new MethodResolver(api);
        this.timeout = timeout;
        this.chunkSupported = FastServletProxyFactory.serverSupportsChunking(url);
        this.proxyId = FastServletProxyFactory.getProxyId(url);
        this.cookies = stickySessionEnabled ? FastServletProxyFactory.requestNewSession(url) : NO_SESSION_COOKIE;
    }

    public Cookie[] getCookies()
    {
        return this.cookies;
    }

    public static Logger getLogger()
    {
        return LOGGER;
    }

    /**
     * Returns the proxy's URL.
     */
    protected AuthenticatedUrl getURL()
    {
        return this.url;
    }

    /**
     * Handles the object invocation.
     *
     * @param proxy  the proxy object to invoke
     * @param method the method to call
     * @param args   the arguments to the proxy object
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String simpleMethodName = method.getName();
        Class[] params = method.getParameterTypes();

        // equals and hashCode are special cased
        if ("equals".equals(simpleMethodName)
                && params.length == 1 && params[0].equals(Object.class))
        {
            Object value = args[0];
            if (value == null || !Proxy.isProxyClass(value.getClass()))
            {
                return Boolean.FALSE;
            }

            FastServletProxyInvocationHandler handler = (FastServletProxyInvocationHandler) Proxy.getInvocationHandler(value);

            return this.url.equals(handler.getURL()) ? Boolean.TRUE : Boolean.FALSE;
        }
        if ("hashCode".equals(simpleMethodName) && params.length == 0)
        {
            return Integer.valueOf(this.url.hashCode());
        }
        if ("toString".equals(simpleMethodName) && params.length == 0)
        {
            return "[FastServletProxyInvocationHandler " + this.url + ']';
        }

        return this.invokeRemoteMethod(proxy, method, args);
    }

    /**
     * pings the server until it responds or we give up
     */
    protected void determineServerStatus(boolean parametersSent)
    {
        long timeToLive = System.currentTimeMillis() + Context.MAX_LIFE_TIME_FROM_FINISHED;
        if (!parametersSent)
        {
            timeToLive = System.currentTimeMillis() + MAX_WAIT_FOR_NETWORK_FAILURE;
        }
        boolean noValidResponse = true;
        while (System.currentTimeMillis() < timeToLive && noValidResponse)
        {
            try
            {
                int code = FastServletProxyFactory.fastFailPing(this.url);
                if (code == 401 || code == 403)
                {
                    throw new JrpipRuntimeException("Authorization required for " + this.url + " (HTTP/" + code + "). Please provide valid credentials to servlet factory!");
                }

                if (code == 404)
                {
                    throw new JrpipRuntimeException("Could not find " + this.url + " (HTTP/404). Looks like the servlet is not properly configured!");
                }

                if (code == 200)
                {
                    noValidResponse = false;
                }
                else
                {
                    LOGGER.warn("Ping request to {} resulted in HTTP/{}", this.url, code);
                }
            }
            catch (IOException e)
            {
                LOGGER.warn("could not ping server at {}", this.url, e);
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
            throw new JrpipRuntimeException("Could not reach server at " + this.url);
        }
    }

    protected Object invokeRemoteMethod(Object proxy, Method method, Object[] args) throws Throwable
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("starting remote method {}.{}", method.getDeclaringClass(), method.getName());
        }
        RequestId requestId = new RequestId(this.proxyId);
        int retries = RETRY_COUNT;
        int state = SEND_PARAMETERS_STATE;
        Exception lastException = null;
        boolean checkServerStatus = false;
        while (retries > 0)
        {
            InputStream is = null;
            byte status = StreamBasedInvocator.FAULT_STATUS;
            Object returned = null;
            HttpClient httpClient = FastServletProxyFactory.getHttpClient(this.url);
            httpClient.getState().addCookies(this.cookies);
            boolean gotResult = false;
            HttpMethod postMethod = null;
            try
            {
                OutputStreamWriter writer = null;
                switch (state)
                {
                    case SEND_PARAMETERS_STATE:
                        writer = new ParameterWriter(proxy, method, args, requestId);
                        break;
                    case RECEIVE_RESULT_STATE:
                        writer = new ResultResendWriter(requestId);
                        break;
                }
                postMethod = this.getPostMethod(writer);

                this.setMethodTimeout(postMethod, method);

                httpClient.executeMethod(postMethod);

                this.cookies = httpClient.getState().getCookies();

                state = RECEIVE_RESULT_STATE;

                int code = postMethod.getStatusCode();

                if (code != 200)
                {
                    checkServerStatus = true;
                    this.analyzeServerErrorAndThrow(code, postMethod);
                }

                is = postMethod.getResponseBodyAsStream();

                //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");

                status = (byte) is.read();
                if (status != StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS)
                {
                    returned = this.getResult(method, args, is);
                }
                gotResult = true;
                is.close();
                is = null;
                postMethod.releaseConnection();
                postMethod = null;
            }
            catch (SocketTimeoutException e)
            {
                LOGGER.debug("Socket timeout reached for JRPIP invocation", e);
                throw new JrpipTimeoutException("Remote method " + method.getName() + " timed out." + this.url, e);
            }
            catch (NotSerializableException e)
            {
                throw new JrpipRuntimeException("Method arguments are not serializable!", e);
            }
            catch (ClassNotFoundException e)
            {
                throw new JrpipRuntimeException("Method call successfully completed but result class not found", e);
            }
            catch (Exception e)
            {
                retries--;
                lastException = e;
                LOGGER.debug("Exception in JRPIP invocation. Retries left {}", retries, e);
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
            if (gotResult)
            {
                switch (status)
                {
                    case StreamBasedInvocator.OK_STATUS:
                        ThankYouWriter.getINSTANCE().addRequest(this.url, this.cookies, requestId);
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("finished remote method normally {}.{}", method.getDeclaringClass(), method.getName());
                        }
                        return returned;
                    case StreamBasedInvocator.FAULT_STATUS:
                        ThankYouWriter.getINSTANCE().addRequest(this.url, this.cookies, requestId);
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("finished remote method {}.{} with exception {}", method.getDeclaringClass(), method.getName(), returned.getClass().getName(), new JrpipRuntimeException("for tracing local invocation context"));
                        }
                        Class[] exceptions = method.getExceptionTypes();
                        for (int i = 0; i < exceptions.length; i++)
                        {
                            if (exceptions[i].isAssignableFrom(returned.getClass()))
                            {
                                throw (Throwable) returned;
                            }
                        }
                        if (RuntimeException.class.isAssignableFrom(returned.getClass()))
                        {
                            throw (RuntimeException) returned;
                        }
                        if (Error.class.isAssignableFrom(returned.getClass()))
                        {
                            throw (Error) returned;
                        }
                        if (Throwable.class.isAssignableFrom(returned.getClass()) && !Exception.class.isAssignableFrom(returned.getClass()))
                        {
                            throw (Throwable) returned;
                        }
                        throw new JrpipRuntimeException("Could not throw returned exception, as it was not declared in the method signature for method " + method.getName(), (Throwable) returned);
                    case StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS:
                        state = SEND_PARAMETERS_STATE;
                        break;
                }
            }
            else
            {
                checkServerStatus = true;
            }
            if (checkServerStatus)
            {
                this.determineServerStatus(state == RECEIVE_RESULT_STATE);
                checkServerStatus = false;
            }
        }
        throw new JrpipRuntimeException("Could not invoke remote method " + method.getName() + " while accessing " + this.url, lastException);
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

    private void analyzeServerErrorAndThrow(int code, HttpMethod streamedPostMethod) throws IOException
    {
        throw new JrpipRuntimeException("Server error (" + code + ").\n" + streamedPostMethod.getResponseBodyAsString());
    }

    protected HttpMethod getPostMethod(OutputStreamWriter writer)
    {
        if (this.chunkSupported)
        {
            return new StreamedPostMethod(this.url.getPath(), writer);
        }
        return new BufferedPostMethod(this.url.getPath(), writer);
    }

    protected void setMethodTimeout(HttpMethod httpMethod, Method methodToCall)
    {
        int timeout = this.timeout;
        Integer methodTimeout = this.methodResolver.getMethodTimeout(methodToCall);
        if (methodTimeout != null)
        {
            timeout = methodTimeout.intValue();
        }

        if (timeout > 0)
        {
            httpMethod.getParams().setSoTimeout(timeout);
        }
    }

    protected class ParameterWriter extends JrpipRequestWriter
    {
        private final Object proxy;
        private final Method method;
        private final Object[] args;
        private final RequestId requestId;

        protected ParameterWriter(Object proxy, Method method, Object[] args, RequestId requestId)
        {
            this.proxy = proxy;
            this.method = method;
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
            objectOutputStream.writeObject(FastServletProxyInvocationHandler.this.methodResolver.getServiceClass().getName());
            String remoteMethodName = FastServletProxyInvocationHandler.this.methodResolver.getMangledMethodName(this.method);

            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            objectOutputStream.writeObject(remoteMethodName);
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
}
