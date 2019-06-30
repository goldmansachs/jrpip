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

package com.gs.jrpip.server;

import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.MethodResolver;
import com.gs.jrpip.JrpipEventListener;
import com.gs.jrpip.JrpipServiceRegistry;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.JrpipVmBoundException;
import com.gs.jrpip.util.stream.CopyOnReadInputStream;
import com.gs.jrpip.util.stream.OutputStreamBuilder;
import com.gs.jrpip.util.stream.VirtualOutputStream;
import com.gs.jrpip.util.stream.VirtualOutputStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JrpipServlet implements Servlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JrpipServlet.class.getName());

    private static final AtomicInteger CLIENT_ID = new AtomicInteger((int) (Math.random() * 20000.0) * 100000);
    private static long vmId;

    protected ServletConfig config;

    private final HashMap serviceMap = new HashMap();
    private final ListenerRegistry listeners = new ListenerRegistry();

    private int pings;
    private int methodInvocations;
    private int thankYous;
    private int resendRequests;
    private String webapp;
    private long startTime;
    private final HashSet<String> registeredUrls = new HashSet<String>();
    private boolean binaryLoggingEnabled;
    private MethodInterceptor methodInterceptor;

    /**
     * Returns the named initialization parameter.
     *
     * @param name name of parameter
     * @return the initialization parameter
     */
    public String getInitParameter(String name)
    {
        return this.config.getInitParameter(name);
    }

    /**
     * Returns the servlet context.
     */
    @Override
    public ServletConfig getServletConfig()
    {
        return this.config;
    }

    /**
     * Cleanup the service instance.
     */
    @Override
    public void destroy()
    {
        vmId = 0L;
    }

    @Override
    public String getServletInfo()
    {
        return "Proxy Method Invokator Servlet";
    }

    /**
     * Initialize the servlet, including the service object.
     */
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        this.startTime = System.currentTimeMillis();
        this.config = config;
        this.webapp = config.getServletContext().getServletContextName();
        if (this.webapp == null)
        {
            this.webapp = "DEFAULTJRPIP";
        }
        boolean useServiceMap = true;
        String useServiceMapString = this.getInitParameter("useServiceMap");
        if (useServiceMapString != null)
        {
            useServiceMap = useServiceMapString.toUpperCase().startsWith("T");
        }

        Enumeration parameterNameEnum = this.config.getInitParameterNames();
        boolean foundConfig = false;
        while (parameterNameEnum.hasMoreElements())
        {
            String paramName = (String) parameterNameEnum.nextElement();
            if (paramName.startsWith("listener"))
            {
                String listenerClassName = this.getInitParameter(paramName);
                Class listenerClass = this.loadClass(listenerClassName);

                if (!JrpipEventListener.class.isAssignableFrom(listenerClass))
                {
                    throw new ServletException("Jrpip listener "
                            + listenerClassName
                            + " must implement JrpipEventListener interface.");
                }

                try
                {
                    this.listeners.register(paramName, (JrpipEventListener) listenerClass.newInstance());
                }
                catch (Exception ex)
                {
                    throw new ServletException(ex);
                }
            }
            else if (paramName.startsWith("serviceInterface"))
            {
                String interfaceName = this.getInitParameter(paramName);
                String definitionName = paramName.substring("serviceInterface".length(), paramName.length());
                String classParamName = "serviceClass" + definitionName;
                boolean vmBound = false;
                String className = this.getInitParameter(classParamName);
                if (className == null)
                {
                    className = this.getInitParameter("vmBoundServiceClass" + definitionName);
                    vmBound = true;
                    if (className == null)
                    {
                        throw new ServletException("You must configure an implementation class for "
                                + interfaceName
                                + " using the parameter name "
                                + classParamName);
                    }
                }
                Class interfaceClass = this.loadClass(interfaceName); // just checking to see it exists
                Class serviceClass = this.loadClass(className);
                boolean serviceImplementsInterface = interfaceClass.isAssignableFrom(serviceClass);
                if (!serviceImplementsInterface)
                {
                    LOGGER.warn("The class {} does not implement {}. This may be a serious error in your configuration. This class will not be available locally.", serviceClass.getName(), interfaceName);
                }
                Object service = null;
                if (useServiceMap)
                {
                    service = JrpipServiceRegistry.getInstance().getSerivceForWebApp(this.webapp, interfaceClass);
                }
                if (service == null)
                {
                    try
                    {
                        service = serviceClass.newInstance();
                        if (serviceImplementsInterface)
                        {
                            JrpipServiceRegistry.getInstance().addServiceForWebApp(this.webapp, interfaceClass, service);
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Caught exception while instantiating service class: {}", serviceClass, e);
                        throw new ServletException(e);
                    }
                }
                MethodResolver methodResolver = new MethodResolver(serviceClass);
                this.serviceMap.put(interfaceName, new ServiceDefinition(service, methodResolver, this.initializeOutputStreamBuilder(interfaceClass), vmBound));
                foundConfig = true;
            }
        }

        String interceptorClassName = this.getInitParameter("methodInterceptor");

        if (interceptorClassName != null)
        {
            Class clazz = this.loadClass(interceptorClassName);

            try
            {
                this.methodInterceptor = (MethodInterceptor) clazz.newInstance();
            }
            catch (Exception e)
            {
                LOGGER.error("Caught exception while instantiating method interceptor class: {}", interceptorClassName, e);
                throw new ServletException(e);
            }
        }

        if (vmId == 0L)
        {
            vmId = System.currentTimeMillis() >> 8 << 32;
        }
        if (!foundConfig)
        {
            throw new ServletException(
                    "JrpipServlet must be configured using serviceInterface.x and serviceClass.x parameter names (x can be anything)");
        }
    }

    private OutputStreamBuilder initializeOutputStreamBuilder(Class interfaceClass)
    {
        String name = interfaceClass.getSimpleName();
        if (Boolean.parseBoolean(System.getProperty("jrpip.enableBinaryLogs")) || Boolean.parseBoolean(System.getProperty(name + ".enableBinaryLogs")))
        {
            this.binaryLoggingEnabled = true;
            return VirtualOutputStreamFactory.create(name, System.getProperty("jrpip.binaryLogsDirectory", "jrpipBinaryLogs"));
        }
        return VirtualOutputStream.NULL_OUTPUT_STREAM_BUILDER;
    }

    protected Class loadClass(String className) throws ServletException
    {
        Class serviceClass;
        try
        {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            if (loader == null)
            {
                serviceClass = Class.forName(className);
            }
            else
            {
                serviceClass = Class.forName(className, false, loader);
            }
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
        return serviceClass;
    }

    public ListenerRegistry getListeners()
    {
        return this.listeners;
    }

    /**
     * Execute a request.  The path-info of the request selects the bean.
     * Once the bean's selected, it will be applied.
     */
    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if ("POST".equals(req.getMethod()))
        {
            this.processPost(request, response);
        }
        else if ("GET".equals(req.getMethod()))
        {
            this.printStatistics(res);
        }
        else
        {
            String errorMessage = "JRPIP Servlet Requires POST";
            res.sendError(405, errorMessage);
        }
    }

    public int getThankYous()
    {
        return this.thankYous;
    }

    protected void processPost(ServletRequest request, ServletResponse response) throws IOException, ServletException
    {
        InputStream is = request.getInputStream();
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        byte requestType = (byte) is.read();
        if (requestType == StreamBasedInvocator.PING_REQUEST)
        {
            this.pings++;
            response.setContentLength(0);
            return;
        }
        if (requestType == StreamBasedInvocator.INIT_REQUEST)
        {
            this.serviceInitRequest(response, is);
            return;
        }
        if (requestType == StreamBasedInvocator.CREATE_SESSION_REQUEST)
        {
            HttpSession session = httpServletRequest.getSession(true);
            // expire after 4 hours
            session.setMaxInactiveInterval(4 * 60 * 60);
            response.setContentLength(0);
            return;
        }
        String lenString = httpServletRequest.getHeader("Content-length");
        FixedInflaterInputStream zipped;
        if (lenString == null)
        {
            zipped = new FixedInflaterInputStream(is);
        }
        else
        {
            int len = Integer.parseInt(lenString);
            zipped = new FixedInflaterInputStream(new com.gs.jrpip.util.stream.ClampedInputStream(is, len - 1));
        }
        try
        {
            ObjectInput in;
            switch (requestType)
            {
                case StreamBasedInvocator.INVOKE_REQUEST:
                    if (this.binaryLoggingEnabled)
                    {
                        CopyOnReadInputStream copyOnReadInputStream = new CopyOnReadInputStream(zipped);
                        in = new ObjectInputStream(copyOnReadInputStream);
                        this.serviceInvokeRequest(request, response, in, copyOnReadInputStream);
                    }
                    else
                    {
                        in = new ObjectInputStream(zipped);
                        this.serviceInvokeRequest(request, response, in, null);
                    }
                    break;
                case StreamBasedInvocator.RESEND_REQUEST:
                    in = new ObjectInputStream(zipped);
                    this.serviceResendRequest(response, in);
                    break;
                case StreamBasedInvocator.THANK_YOU_REQUEST:
                    in = new ObjectInputStream(zipped);
                    this.serviceThankYou(in);
                    break;
            }
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            LOGGER.error("unexpected exception", e);
            throw new ServletException(e);
        }
        finally
        {
            zipped.finish(); // frees up memory allocated in native zlib library.
        }
    }

    private void serviceInitRequest(ServletResponse response, InputStream is) throws IOException
    {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        while ((n = is.read(buffer)) >= 0)
        {
            outBuffer.write(buffer, 0, n);
        }
        String url = outBuffer.toString(JrpipServiceRegistry.ENCODE_STRING);
        synchronized (this.registeredUrls)
        {
            if (!this.registeredUrls.contains(url))
            {
                JrpipServiceRegistry.getInstance().registerWebAppAtUrl(this.webapp, url);
                this.registeredUrls.add(url);
            }
        }
        int id = CLIENT_ID.incrementAndGet();
        long vmAndClientId = vmId | (long) id;
        String clientCount = String.valueOf(vmAndClientId);
        response.setContentLength(clientCount.length());
        response.getWriter().write(clientCount);
    }

    private void serviceThankYou(ObjectInput in) throws IOException, ClassNotFoundException
    {
        this.thankYous++;
        //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
        int thankYouNotes = in.readInt();
        for (int i = 0; i < thankYouNotes; i++)
        {
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
            ContextCache.getInstance().removeContext((RequestId) in.readObject());
        }
    }

    private void serviceResendRequest(
            ServletResponse response,
            ObjectInput in) throws IOException, ClassNotFoundException
    {
        this.resendRequests++;
        //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
        RequestId resendRequestId = (RequestId) in.readObject();
        Context resendContext = ContextCache.getInstance().getContext(resendRequestId);
        if (resendContext == null || resendContext.isCreatedState() || resendContext.isReadingParameters())
        {
            response.getOutputStream().write(StreamBasedInvocator.REQUEST_NEVER_ARRVIED_STATUS);
        }
        else
        {
            resendContext.waitForInvocationToFinish();
            resendContext.writeAndLogResponse(response.getOutputStream(), resendRequestId);
        }
    }

    private void serviceInvokeRequest(
            ServletRequest request,
            ServletResponse response,
            ObjectInput in,
            CopyOnReadInputStream copyOnReadInputStream) throws Exception
    {
        this.methodInvocations++;
        //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
        RequestId requestId = (RequestId) in.readObject();
        Context invokeContext = ContextCache.getInstance().getOrCreateContext(requestId);
        String serviceInterface = (String) in.readObject();
        ServiceDefinition serviceDefinition = (ServiceDefinition) this.serviceMap.get(serviceInterface);
        if (serviceDefinition == null)
        {
            invokeContext.setReturnValue(new JrpipRuntimeException("JrpipServlet is not servicing "
                    + serviceInterface), true);
        }
        else
        {
            OutputStreamBuilder outputStreamBuilder = serviceDefinition.getOutputStreamBuilder();
            DataOutputStream copyTo = outputStreamBuilder.newOutputStream();
            copyTo.writeByte(OutputStreamBuilder.REQUEST_HEADER);
            try
            {
                if (copyOnReadInputStream != null)
                {
                    copyOnReadInputStream.startCopyingInto(copyTo);
                }
                invokeContext.setOutputStreamBuilder(outputStreamBuilder);
                boolean serviceRequest = true;
                if (serviceDefinition.isVmBound())
                {
                    serviceRequest = this.checkVmBoundCall(requestId, invokeContext, serviceInterface, serviceRequest);
                }
                if (serviceRequest)
                {
                    JrpipRequestContext requestContext = getJrpipRequestContext(request, requestId);

                    new StreamBasedInvocator().invoke(in,
                            invokeContext,
                            serviceDefinition.getService(),
                            serviceDefinition.getMethodResolver(),
                            request.getRemoteAddr(),
                            requestId,
                            this.listeners,
                            copyTo,
                            this.methodInterceptor,
                            requestContext);
                }
            }
            finally
            {
                copyTo.close();
            }
        }
        invokeContext.writeAndLogResponse(response.getOutputStream(), requestId);
    }

    private JrpipRequestContext getJrpipRequestContext(ServletRequest request, RequestId requestId)
    {
        JrpipRequestContext requestContext = null;
        if (request instanceof HttpServletRequest && this.methodInterceptor != null)
        {
            requestContext = new JrpipRequestContext(
                    requestId,
                    ((HttpServletRequest) request).getRemoteUser(),
                    request.getRemoteAddr(),
                    ((HttpServletRequest) request).getCookies());
        }
        return requestContext;
    }

    private boolean checkVmBoundCall(
            RequestId requestId,
            Context invokeContext,
            String serviceInterface,
            boolean serviceRequest)
    {
        long requestVmId = requestId.getProxyId() & ~0xffffffffL;
        if (requestVmId != vmId)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("request vm id: {} server vm id: {}", requestVmId, vmId);
            }
            invokeContext.setReturnValue(new JrpipVmBoundException("The service instance for "
                    + serviceInterface
                    + " has been recycled. You must restart your client."), true);
            return false;
        }
        return serviceRequest;
    }

    protected void printStatistics(HttpServletResponse res) throws IOException
    {
        res.setContentType("text/html");
        res.getWriter().print("<html><body>");
        res.getWriter().print("<h1>JRPIP Servlet</h1><br>Configured for <br>");
        for (Object o : this.serviceMap.keySet())
        {
            res.getWriter().print(o + "<br>");
        }
        res.getWriter().print("<br>Total Method Invocations: " + this.methodInvocations + "<br>");
        res.getWriter().print("<br>Total Resend Requests: " + this.resendRequests + "<br>");
        res.getWriter().print("<br>Total Coalesced Thank You Requests: " + this.thankYous + "<br>");
        res.getWriter().print("<br>Total Pings: " + this.pings + "<br>");
        long seconds = (System.currentTimeMillis() - this.startTime) / 1000L;
        res.getWriter().print("<br>Uptime: " + seconds + " sec (about " + seconds / 3600L + " hours " + seconds / 60L % 60L + " minutes)<br>");
        res.getWriter().print("</body></html>");
    }

    /**
     * Kept for api compatibility sake.
     *
     * @deprecated Use ClampedInputStream top level class instead
     */
    @Deprecated
    protected static class ClampedInputStream extends com.gs.jrpip.util.stream.ClampedInputStream
    {
        protected ClampedInputStream(InputStream in, int maxLength)
        {
            super(in, maxLength);
        }
    }
}
