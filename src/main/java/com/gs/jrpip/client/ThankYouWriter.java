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

import com.gs.jrpip.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class ThankYouWriter implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ThankYouWriter.class);
    private static final ThankYouWriter INSTANCE;
    private static final int SLEEP_TIME = 500; // so multiple requests get coalesced
    private boolean done = true;

    private final Map<Object, List<ThankYouRequest>> requestMap = new HashMap<>();

    // singelton
    private ThankYouWriter()
    {
    }

    public static ThankYouWriter getINSTANCE()
    {
        return INSTANCE;
    }

    static
    {
        INSTANCE = new ThankYouWriter();
    }

    private synchronized void startThankYouThread()
    {
        if (this.done)
        {
            this.done = false;
            Thread thankYouThread = new Thread(INSTANCE);
            thankYouThread.setName("JRPIP Thank You Thread");
            thankYouThread.setDaemon(true);
            thankYouThread.start();
        }
    }

    public synchronized void stopThankYouThread()
    {
        this.done = true;
        this.requestMap.clear();
    }

    public static Logger getLogger()
    {
        return LOGGER;
    }

    public synchronized int getPendingRequests()
    {
        return this.requestMap.size();
    }

    public synchronized void addRequest(MessageTransport transport, MessageTransportData data, RequestId requestId)
    {
        this.startThankYouThread();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("added request for {}", data.toString());
        }
        requestId.setFinishedTime(System.currentTimeMillis());

        Object key = data.createThankYouKey();
        List<ThankYouRequest> list = this.requestMap.get(key);
        if (list == null)
        {
            list = new ArrayList<>(6);
            this.requestMap.put(key, list);
        }
        list.add(new ThankYouRequest(transport, data, requestId));
        this.notifyAll();
    }

    private synchronized List<ThankYouRequest> removeRequestList(Object url)
    {
        return this.requestMap.remove(url);
    }

    @Override
    public void run()
    {
        ArrayList<Object> urlsToSend = new ArrayList<>(6);
        while (!this.done)
        {
            try
            {
                synchronized (this)
                {
                    if (this.requestMap.isEmpty())
                    {
                        this.wait();
                    }
                }
                Thread.sleep(SLEEP_TIME);
                this.getUrlsToSend(urlsToSend);
                for (int i = 0; i < urlsToSend.size(); i++)
                {
                    Object key = urlsToSend.get(i);
                    if (!this.done)
                    {
                        this.sendThankYouRequest(key);
                    }
                }
                urlsToSend.clear();
            }
            catch (InterruptedException e)
            {
                // ok, nothing to do
            }
            catch (Throwable t)
            {
                // this is impossible, but let's not take any chances.
                LOGGER.error("Unexpected exception", t);
            }
        }
    }

    void sendThankYouRequest(Object key)
    {
        boolean success = false;
        List<ThankYouRequest> requestList = this.removeRequestList(key);
        if (done || requestList == null || requestList.isEmpty())
        {
            return;
        }
        try
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Sending thank you for {}", requestList.size());
            }
            ThankYouRequest thankYouRequest = requestList.get(0);
            success = thankYouRequest.transport.sendThanks(key, requestList);
        }
        catch (Exception e)
        {
            LOGGER.warn("Exception in JRPIP thank you note for URL: {} Retrying.", key.toString(), e);
        }
        if (!success)
        {
            this.readdList(key, requestList);
        }
    }

    void readdList(Object urlPair, List<ThankYouRequest> requestList)
    {
        // technically requestList cannot be null, but in rear conditions, where JVM runs
        // out of memory, it can be null. Here is an example:
        if (!this.done && requestList != null)
        {
            for (int i = 0; i < requestList.size(); )
            {
                RequestId requestId = requestList.get(i).requestId;
                if (requestId.isExpired())
                {
                    requestList.remove(i);
                }
                else
                {
                    i++;
                }
            }
            if (requestList.isEmpty())
            {
                return;
            }
            synchronized (this)
            {
                List<ThankYouRequest> list = this.requestMap.get(urlPair);
                if (list == null)
                {
                    this.requestMap.put(urlPair, requestList);
                    this.notifyAll();
                }
                else
                {
                    list.addAll(requestList);
                }
            }
        }
    }

    synchronized void getUrlsToSend(List<Object> listToSend)
    {
        listToSend.addAll(this.requestMap.keySet());
    }

    public static class ThankYouRequest
    {
        private final MessageTransport transport;
        private final MessageTransportData data;
        private final RequestId requestId;

        public ThankYouRequest(MessageTransport transport, MessageTransportData data, RequestId requestId)
        {
            this.transport = transport;
            this.data = data;
            this.requestId = requestId;
        }

        public RequestId getRequestId()
        {
            return requestId;
        }
    }

}
