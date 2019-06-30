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

import com.gs.jrpip.JrpipServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;

public class MtProxyFactory implements ServletProxyFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MtProxyFactory.class);

    private boolean useLocalService = true;

    private MessageTransport transport;

    /**
     * Creates the new proxy factory.
     */
    public MtProxyFactory(MessageTransport transport)
    {
        this.transport = transport;
    }

    public void setUseLocalService(boolean useLocalService)
    {
        this.useLocalService = useLocalService;
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
            this.transport.initAndRegisterLocalServices(url, disconnectedMode, timeoutMillis);
            if (this.useLocalService)
            {
                result = JrpipServiceRegistry.getInstance().getLocalService(url, api);
            }
            if (result == null)
            {
                result = (T) Proxy.newProxyInstance(api.getClassLoader(),
                        new Class[]{api},
                        this.transport.createInvocationHandler(api, url, timeoutMillis, disconnectedMode));
            }
        }
        return result;
    }

    @Override
    public boolean isServiceAvailable(String url)
    {
        boolean result = false;
        try
        {
            result = this.transport.fastFailPing(url, MessageTransport.PING_TIMEOUT);
        }
        catch (IOException e)
        {
            LOGGER.debug("ping failed with ", e);
        }
        return result;
    }
}

