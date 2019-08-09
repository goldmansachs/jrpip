package com.gs.jrpip.server;

import com.gs.jrpip.JrpipEventListener;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.util.AuthGenerator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketServerConfig
{
    private final int port;
    private int serverSocketTimeout = 1000;
    private int idleSocketCloseTime = 10000;
    private long logStatsInterval = 60*60*1000; // an hour
    private MethodInterceptor methodInterceptor;
    private List<SingleServiceConfig> configs = new ArrayList<>(2);
    private List<JrpipEventListener> listeners = new ArrayList<>(2);
    private Map<String, byte[]> userTokens = new HashMap<>(2);

    /**
     * Create a new config for a socket server
     * @param port The port the server should listen on. If set to 0 (zero), the server will pick
     *             a port, which can be found after the server has started via server.getPort()
     */
    public SocketServerConfig(int port)
    {
        this.port = port;
    }

    public int getIdleSocketCloseTime()
    {
        return idleSocketCloseTime;
    }

    /**
     * Idle clients are dropped after this time (milliseconds). Default: 10000 (10 seconds)
     * @param idleSocketCloseTime value in milliseconds
     */
    public void setIdleSocketCloseTime(int idleSocketCloseTime)
    {
        this.idleSocketCloseTime = idleSocketCloseTime;
    }

    public int getServerSocketTimeout()
    {
        return serverSocketTimeout;
    }

    /**
     * The server will periodically check its shutdown flag via this timeout (milliseconds). Default: 1000
     * A lower value can be useful in tests
     * @param serverSocketTimeout value in milliseconds
     */
    public void setServerSocketTimeout(int serverSocketTimeout)
    {
        if (serverSocketTimeout <= 0)
        {
            throw new JrpipRuntimeException("Socket timeout must be > 0");
        }
        this.serverSocketTimeout = serverSocketTimeout;
    }

    public MethodInterceptor getMethodInterceptor()
    {
        return methodInterceptor;
    }

    /**
     * Specify a MethodInterceptor that will get called during server side method invocation
     * @param methodInterceptor The instance of the interceptor
     */
    public void setMethodInterceptor(MethodInterceptor methodInterceptor)
    {
        this.methodInterceptor = methodInterceptor;
    }

    /**
     * Add the service interface and implementation classes
     *
     * @param serviceInterface The interface class
     * @param serviceClass The service class
     * @param vmBound This service is bound to this VM and if the VM goes down, existing clients will not reconnect
     *                to a new VM.
     */
    public void addServiceConfig(Class serviceInterface, Class serviceClass, boolean vmBound)
    {
        this.configs.add(new SingleServiceConfig(serviceInterface, serviceClass, vmBound));
    }

    /**
     * Adds an instance of service. This allows for stateful services.
     *
     * @param serviceInterface The interface the remote will be calling with
     * @param service The instance of the service that should be invoked
     * @param vmBound This service is bound to this VM and if the VM goes down, existing clients will not reconnect
     *                to a new VM.
     */
    public void addServiceInstance(Class serviceInterface, Object service, boolean vmBound)
    {
        this.configs.add(new SingleServiceConfig(serviceInterface, service, vmBound));
    }

    /**
     * Add the service interface and implementation classes. Can be called multiple times.
     *
     * @param serviceInterface The interface class
     * @param serviceClass The service class
     */
    public void addServiceConfig(Class serviceInterface, Class serviceClass)
    {
        this.addServiceConfig(serviceInterface, serviceClass, false);
    }

    /**
     * Adds an instance of service. This allows for stateful services.
     *
     * @param serviceInterface The interface class
     * @param serviceInstance The service object instance
     */
    public void addServiceInstance(Class serviceInterface, Object serviceInstance)
    {
        this.addServiceInstance(serviceInterface, serviceInstance, false);
    }

    /**
     * Add authentication requirement. Can be called multiple times with different credentials.
     * All credentials have the same level of access.
     *
     * The token must be in base32 format (RFC 4648): A-Z and 2-7 (the alphabet plus numbers 2 to 7)
     *
     * @param username The username
     * @param base32Token The token that's used for hashing a challenge during authentication
     * @throws ParseException is thrown if the token passed in is not in base32 format.
     */
    public void addCredentials(String username, String base32Token)
            throws ParseException
    {
        this.userTokens.put(username, AuthGenerator.decode(base32Token));
    }

    public byte[] getTokenForUser(String username)
    {
        return this.userTokens.get(username);
    }

    /**
     * Add a method invocation listener
     * @param listener The listener
     */
    public void addListener(JrpipEventListener listener)
    {
        this.listeners.add(listener);
    }

    public long getLogStatsInterval()
    {
        return logStatsInterval;
    }

    /**
     * The interval at which log the server stats. Should be longer than serverSocketTimeout.
     * @param logStatsInterval in milliseconds. 0 will disable the logging.
     */
    public void setLogStatsInterval(long logStatsInterval)
    {
        this.logStatsInterval = logStatsInterval;
    }

    public int getPort()
    {
        return port;
    }

    public List<SingleServiceConfig> getConfigs()
    {
        return configs;
    }

    public List<JrpipEventListener> getListeners()
    {
        return listeners;
    }

    public boolean requiresAuth()
    {
        return !this.userTokens.isEmpty();
    }
}
