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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gs.jrpip.JrpipEvent;
import com.gs.jrpip.JrpipEventListener;
import com.gs.jrpip.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListenerRegistry
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ListenerRegistry.class);

    private final Map listenerMap = new HashMap();
    private final List listeners = new ArrayList();

    /**
     * This method should only be used in unit tests, or as part of application startup/shutdown.
     * It is not multi-thread safe.
     */
    public void register(String name, JrpipEventListener eventListener)
    {
        Object oldListener = this.listenerMap.put(name, eventListener);
        if (oldListener != null)
        {
            this.listeners.remove(oldListener);
        }
        this.listeners.add(eventListener);
    }

    public JrpipEventListener getListener(String name)
    {
        return (JrpipEventListener) this.listenerMap.get(name);
    }

    /**
     * This method should only be used in unit tests, or as part of application startup/shutdown.
     * It is not multi-thread safe.
     *
     * @return the removed listener
     */
    public JrpipEventListener deregister(String name)
    {
        JrpipEventListener removed = (JrpipEventListener) this.listenerMap.remove(name);
        this.listeners.remove(removed);
        return removed;
    }

    public void methodStarted(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Object[] arguments)
    {
        if (!this.listeners.isEmpty())
        {
            this.fireEvent(new JrpipEvent(requestId, method, remoteAddress, arguments));
        }
    }

    public void methodFinished(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Object result)
    {
        if (!this.listeners.isEmpty())
        {
            this.fireEvent(new JrpipEvent(requestId, method, remoteAddress, result));
        }
    }

    public void methodFailed(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Throwable exception)
    {
        if (!this.listeners.isEmpty())
        {
            this.fireEvent(new JrpipEvent(requestId, method, remoteAddress, exception));
        }
    }

    private void fireEvent(JrpipEvent event)
    {
        for (int i = 0; i < this.listeners.size(); i++)
        {
            try
            {
                JrpipEventListener listener = (JrpipEventListener) this.listeners.get(i);
                listener.methodInvocationEvent(event);
            }
            catch (Throwable t)
            {
                LOGGER.error("listener failed", t);
            }
        }
    }
}
