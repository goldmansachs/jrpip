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

import javax.servlet.http.Cookie;

import com.gs.jrpip.RequestId;

public class JrpipRequestContext
{
    private final String remoteAddress;
    private final RequestId requestId;
    private final String remoteUser;
    private final Cookie[] cookies;

    public JrpipRequestContext(RequestId requestId, String remoteUser, String remoteAddress, Cookie[] cookies)
    {
        this.requestId = requestId;
        this.remoteUser = remoteUser;
        this.remoteAddress = remoteAddress;
        this.cookies = cookies;
    }

    public RequestId getRequestId()
    {
        return this.requestId;
    }

    public String getRemoteUser()
    {
        return this.remoteUser;
    }

    public String getRemoteAddress()
    {
        return this.remoteAddress;
    }

    public Cookie[] getCookies()
    {
        return this.cookies;
    }
}
