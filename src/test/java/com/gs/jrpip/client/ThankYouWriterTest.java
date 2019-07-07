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

import com.gs.jrpip.JrpipTestCase;
import com.gs.jrpip.RequestId;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.UsernamePasswordCredentials;

public class ThankYouWriterTest extends JrpipTestCase
{
    public void testAddRequest() throws Exception
    {
        AuthenticatedUrl url = new AuthenticatedUrl(this.getJrpipUrl(), new UsernamePasswordCredentials("username"));
        HttpMessageTransport transport = new HttpMessageTransport();
        Cookie cookie1 = new Cookie("domain", "cookie1", "val1", "/", 1000, false);
        Cookie cookie2 = new Cookie("domain", "cookie2", "val2", "/", 1000, false);
        Cookie cookie3 = new Cookie("domain", "cookie3", "val3", "/", 1000, false);
        ThankYouWriter thankYouWriter = ThankYouWriter.getINSTANCE();
        thankYouWriter.stopThankYouThread();
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, new Cookie[]{cookie1, cookie2, cookie3}),
                new RequestId(1));
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, new Cookie[]{cookie1, cookie2, cookie3}),
                new RequestId(2));  // same combination
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, new Cookie[]{cookie3, cookie2, cookie1}),
                new RequestId(3)); // cookie order changed
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, new Cookie[]{cookie3}),
                new RequestId(4)); // mismatch cookies
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, new Cookie[]{}),
                new RequestId(5)); // no cookies
        thankYouWriter.addRequest(transport, new HttpMessageTransportData(url, true, 0, null)
                , new RequestId(6)); // null cookies

        assertEquals(3, thankYouWriter.getPendingRequests());
    }
}
