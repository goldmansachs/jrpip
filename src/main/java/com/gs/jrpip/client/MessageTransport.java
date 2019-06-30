package com.gs.jrpip.client;

import com.gs.jrpip.RequestId;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.List;

public interface MessageTransport
{
    public static final int PING_TIMEOUT = 5000;
    public static final long PING_INTERVAL = 5000L;

    public <T> InvocationHandler createInvocationHandler(Class<T> api, String url, int timeoutMillis,
            boolean disconnectedMode) throws MalformedURLException;

    public boolean fastFailPing(String url, int timeoutMillis) throws IOException;

    public void waitForServer(long deadline, MessageTransportData data);

    public ResponseMessage sendParameters(MessageTransportData data, RequestId requestId, int timeout, String serviceClass,
            String mangledMethodName, Object[] args, Method method, boolean compress) throws ClassNotFoundException, IOException;

    public ResponseMessage requestResend(MessageTransportData data, RequestId requestId, int timeout, Object[] args, Method method, boolean compress)
            throws ClassNotFoundException, IOException;

    public boolean sendThanks(Object key, List<ThankYouWriter.ThankYouRequest> requestList) throws IOException;

    public void initAndRegisterLocalServices(String url, boolean disconnectedMode, int timeout) throws MalformedURLException;
}
