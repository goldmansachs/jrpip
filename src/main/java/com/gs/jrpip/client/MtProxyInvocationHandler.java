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

import com.gs.jrpip.MethodResolver;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.server.Context;
import com.gs.jrpip.server.StreamBasedInvocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class MtProxyInvocationHandler
        implements InvocationHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MtProxyInvocationHandler.class);

    private static final int SEND_PARAMETERS_STATE = 0;
    private static final int RECEIVE_RESULT_STATE = 1;

    private static final int RETRY_COUNT = 4;

    private static final long MAX_WAIT_FOR_NETWORK_FAILURE = 120000L;
    //private static final boolean CAUSE_RANDOM_ERROR = true;

    // private static final double ERROR_RATE = 0.98;
    private final MethodResolver methodResolver;
    private final MessageTransportData mtData;
    private final MessageTransport transport;
    private final int timeout;

    protected MtProxyInvocationHandler(
            MessageTransportData mtData,
            MessageTransport transport,
            Class api,
            int timeout)
    {
        this.mtData = mtData;
        this.transport = transport;
        this.methodResolver = new MethodResolver(api);
        this.timeout = timeout;

    }

    protected MessageTransportData getMessageTransportData()
    {
        return mtData;
    }

    public static Logger getLogger()
    {
        return LOGGER;
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

            MtProxyInvocationHandler handler = (MtProxyInvocationHandler) Proxy.getInvocationHandler(value);

            return this.mtData.isSameEndPoint(handler.mtData) ? Boolean.TRUE : Boolean.FALSE;
        }
        if ("hashCode".equals(simpleMethodName) && params.length == 0)
        {
            return this.mtData.endPointHashCode();
        }
        if ("toString".equals(simpleMethodName) && params.length == 0)
        {
            return "[MtProxyInvocationHandler " + this.mtData.toString() + ']';
        }

        return this.invokeRemoteMethod(method, args);
    }

    /**
     * pings the server until it responds or we give up
     */
    protected void waitForServer(boolean parametersSent)
    {
        long timeToLive = System.currentTimeMillis() + Context.MAX_LIFE_TIME_FROM_FINISHED;
        if (!parametersSent)
        {
            timeToLive = System.currentTimeMillis() + MAX_WAIT_FOR_NETWORK_FAILURE;
        }
        this.transport.waitForServer(timeToLive, this.mtData);
    }

    protected Object invokeRemoteMethod(Method method, Object[] args) throws Throwable
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("starting remote method {}.{}", method.getDeclaringClass(), method.getName());
        }
        RequestId requestId = new RequestId(this.mtData.getProxyId());
        int retries = RETRY_COUNT;
        int state = SEND_PARAMETERS_STATE;
        Exception lastException = null;
        boolean checkServerStatus = false;
        int timeout = this.timeout;
        Integer methodTimeout = this.methodResolver.getMethodTimeout(method);
        if (methodTimeout != null)
        {
            timeout = methodTimeout;
        }
        long methodStart = timeout == 0 ? 0 : System.currentTimeMillis();
        long deadline = timeout == 0 ? Long.MAX_VALUE : methodStart + timeout;
        while (retries > 0)
        {
            long retryStart = System.currentTimeMillis();
            if (retryStart >= deadline)
            {
                throw new JrpipTimeoutException("Remote method " + method.getName() + " timed out." + this.mtData.toString());
            }
            int timeLeftForProcessing = timeout == 0 ? 0 : timeout - (int) (retryStart - methodStart);
            byte status = StreamBasedInvocator.FAULT_STATUS;
            Object returned = null;
            boolean gotResult = false;
            boolean wait = true;
            try
            {
                ResponseMessage responseMessage = null;
                switch (state)
                {
                    case SEND_PARAMETERS_STATE:
                        responseMessage = transport.sendParameters(mtData, requestId, timeLeftForProcessing,
                                this.methodResolver.getServiceClass().getName(), this.methodResolver.getMangledMethodName(method),
                                args, method, this.methodResolver.getMethodCompression(method));
                        break;
                    case RECEIVE_RESULT_STATE:
                        responseMessage = transport.requestResend(mtData, requestId, timeLeftForProcessing, args, method,
                                this.methodResolver.getMethodCompression(method));
                        break;
                }

                state = RECEIVE_RESULT_STATE;

                int code = responseMessage.getTransportStatusCode();

                if (code != ResponseMessage.SERVER_OK)
                {
                    checkServerStatus = true;
                    this.throwServerError(code, responseMessage.getTransportError(), method.getName(), this.mtData.toString());
                }

                status = responseMessage.getResponseStatusCode();
                returned = responseMessage.getResult();
                gotResult = true;
            }
            catch (SocketTimeoutException e)
            {
                LOGGER.debug("Socket timeout reached for JRPIP invocation", e);
                throw new JrpipTimeoutException("Remote method " + method.getName() + " timed out." + this.mtData.toString(), e);
            }
            catch (SocketException e)
            {
                if (e.getMessage().contains("reset") || e.getMessage().contains("peer") || e.getMessage().contains("abort"))
                {
                    wait = false;
                }
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
            if (gotResult)
            {
                switch (status)
                {
                    case StreamBasedInvocator.OK_STATUS:
                        ThankYouWriter.getINSTANCE().addRequest(this.transport, this.mtData, requestId);
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("finished remote method normally {}.{}", method.getDeclaringClass(), method.getName());
                        }
                        return returned;
                    case StreamBasedInvocator.FAULT_STATUS:
                        ThankYouWriter.getINSTANCE().addRequest(this.transport, this.mtData, requestId);
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("finished remote method {}.{} with exception {}", method.getDeclaringClass(), method.getName(), returned.getClass().getName(), new JrpipRuntimeException("for tracing local invocation context"));
                        }
                        Class[] exceptions = method.getExceptionTypes();
                        for (Class exception : exceptions)
                        {
                            if (exception.isAssignableFrom(returned.getClass()))
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
            if (checkServerStatus && wait)
            {
                this.waitForServer(state == RECEIVE_RESULT_STATE);
                checkServerStatus = false;
            }
        }
        if (lastException instanceof JrpipRuntimeException)
        {
            throw lastException;
        }
        throw new JrpipRuntimeException("Could not invoke remote method " + method.getName() + " while accessing " + this.mtData.toString(), lastException);
    }

    private void throwServerError(int code, String serverError, String methodName, String dest)
    {
        if (code == 401 || code == 403)
        {
            throw new JrpipRuntimeException("Authorization failed! "+serverError+" invoking "+methodName+
                    " while accessing "+dest);
        }
        throw new JrpipRuntimeException("Server error (" + code + ").\n" + serverError+" invoking "+methodName+
                " while accessing "+dest);
    }

}
