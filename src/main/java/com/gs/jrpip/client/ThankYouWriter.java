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
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gs.jrpip.RequestId;
import com.gs.jrpip.server.StreamBasedInvocator;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThankYouWriter implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ThankYouWriter.class);
    private static final ThankYouWriter INSTANCE;
    private static final int SLEEP_TIME = 500; // so multiple requests get coalesced
    private boolean done = true;

    private final Map<CoalesceThankYouNotesKey, List> requestMap = new HashMap<CoalesceThankYouNotesKey, List>();

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

    public synchronized void addRequest(AuthenticatedUrl url, Cookie[] cookies, RequestId requestId)
    {
        this.startThankYouThread();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("added request for {}", url);
        }
        requestId.setFinishedTime(System.currentTimeMillis());

        CoalesceThankYouNotesKey key = new CoalesceThankYouNotesKey(url, cookies);
        List list = this.requestMap.get(key);
        if (list == null)
        {
            list = new ArrayList(6);
            this.requestMap.put(key, list);
        }
        list.add(requestId);
        this.notifyAll();
    }

    public synchronized List removeRequestList(CoalesceThankYouNotesKey url)
    {
        return this.requestMap.remove(url);
    }

    @Override
    public void run()
    {
        ArrayList<CoalesceThankYouNotesKey> urlsToSend = new ArrayList<CoalesceThankYouNotesKey>(6);
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
                    CoalesceThankYouNotesKey key = urlsToSend.get(i);
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

    void sendThankYouRequest(CoalesceThankYouNotesKey key)
    {
        boolean success = false;
        List requestList = this.removeRequestList(key);
        if (done || requestList == null)
        {
            return;
        }
        HttpMethod streamedPostMethod = null;
        try
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Sending thank you for {}", requestList.size());
            }
            AuthenticatedUrl url = key.getAuthenticatedUrl();
            HttpClient httpClient = FastServletProxyFactory.getHttpClient(url);
            httpClient.getState().addCookies(key.getCookies());
            OutputStreamWriter writer = new ThankYouStreamWriter(requestList);
            streamedPostMethod = FastServletProxyFactory.serverSupportsChunking(url) ? new StreamedPostMethod(url.getPath() + "?thanks", writer) : new BufferedPostMethod(url.getPath() + "?thanks", writer);
            httpClient.executeMethod(streamedPostMethod);

            int code = streamedPostMethod.getStatusCode();

            streamedPostMethod.getResponseBodyAsStream().close();
            streamedPostMethod.releaseConnection();
            streamedPostMethod = null;
            success = code == 200;
        }
        catch (Exception e)
        {
            LOGGER.warn("Exception in JRPIP thank you note for URL: {} Retrying.", key.toString(), e);
        }
        finally
        {
            if (streamedPostMethod != null)
            {
                streamedPostMethod.releaseConnection();
            }
        }
        if (!success)
        {
            this.readList(key, requestList);
        }
    }

    void readList(CoalesceThankYouNotesKey urlPair, List requestList)
    {
        // technically requestList cannot be null, but in rear conditions, where JVM runs
        // out of memory, it can be null. Here is an example:
        if (!this.done && requestList != null)
        {
            for (int i = 0; i < requestList.size(); )
            {
                RequestId requestId = (RequestId) requestList.get(i);
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
                List list = this.requestMap.get(urlPair);
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

    synchronized void getUrlsToSend(List<CoalesceThankYouNotesKey> listToSend)
    {
        listToSend.addAll(this.requestMap.keySet());
    }

    protected static class ThankYouStreamWriter extends JrpipRequestWriter
    {
        private final List requestList;

        protected ThankYouStreamWriter(List requestList)
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
            for (Object aRequestList : this.requestList)
            {
                //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");
                objectOutputStream.writeObject(aRequestList);
            }
        }
    }

    private static final class CoalesceThankYouNotesKey
    {
        private static final Cookie[] NO_COOKIES = new Cookie[0];
        private static final Comparator<? super Cookie> COOKIE_NAME_COMPARATOR = new Comparator<Cookie>()
        {
            @Override
            public int compare(Cookie o1, Cookie o2)
            {
                return o1.getName().compareTo(o2.getName());
            }
        };
        private final AuthenticatedUrl authenticatedUrl;
        private final Cookie[] cookies;

        private CoalesceThankYouNotesKey(AuthenticatedUrl authenticatedUrl, Cookie[] cookies)
        {
            this.authenticatedUrl = authenticatedUrl;
            this.cookies = cookies == null ? NO_COOKIES : this.sort(cookies);
        }

        private Cookie[] sort(Cookie[] cookies)
        {
            Cookie[] result = new Cookie[cookies.length];
            System.arraycopy(cookies, 0, result, 0, cookies.length);
            Arrays.sort(result, COOKIE_NAME_COMPARATOR);
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || this.getClass() != o.getClass())
            {
                return false;
            }

            CoalesceThankYouNotesKey that = (CoalesceThankYouNotesKey) o;

            if (!this.authenticatedUrl.equals(that.authenticatedUrl))
            {
                return false;
            }
            return Arrays.equals(this.cookies, that.cookies);
        }

        @Override
        public int hashCode()
        {
            int result = this.authenticatedUrl.hashCode();
            for(Cookie c: cookies)
            {
                result = 31 * result + c.hashCode();
            }
            return result;
        }

        public Cookie[] getCookies()
        {
            return this.cookies;
        }

        public AuthenticatedUrl getAuthenticatedUrl()
        {
            return this.authenticatedUrl;
        }

        @Override
        public String toString()
        {
            return '{'
                    + "authenticatedUrl: " + this.authenticatedUrl
                    + ", cookies: " + this.cookiesAsString()
                    + '}';
        }

        private String cookiesAsString()
        {
            String result = "[";
            for(Cookie c: this.cookies)
            {
                result = c.getName()+":"+c.getValue();
            }
            result += "]";
            return result;
        }
    }
}
