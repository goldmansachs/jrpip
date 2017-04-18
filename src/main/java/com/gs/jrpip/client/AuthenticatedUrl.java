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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.auth.AuthScope;

public class AuthenticatedUrl
{
    private final URL url;
    private final Credentials credentials;
    private final Cookie[] cookies;

    public AuthenticatedUrl(URL url, Credentials credentials, Cookie[] cookies)
    {
        this.url = url;
        this.credentials = credentials;
        this.cookies = cookies;
    }

    public AuthenticatedUrl(String url, Credentials credentials) throws MalformedURLException
    {
        this(new URL(url), credentials, null);
    }

    public AuthenticatedUrl(String url, Credentials credentials, Cookie[] cookies) throws MalformedURLException
    {
        this(new URL(url), credentials, cookies);
    }

    public int getPort()
    {
        return this.url.getPort();
    }

    public String getHost()
    {
        return this.url.getHost();
    }

    public String getPath()
    {
        return this.url.getPath();
    }

    // Added the check for Cookies as well.
    // If cookies are present, set these on HttpClient State
    public void setCredentialsOnClient(HttpClient client)
    {
        if (this.credentials != null)
        {
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(AuthScope.ANY, this.credentials);
        }

        if (this.cookies != null && this.cookies.length > 0)
        {
            client.getState().addCookies(this.cookies);
        }
    }

    public URL getNonAuthenticatedUrl()
    {
        return this.url;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof AuthenticatedUrl))
        {
            return false;
        }

        AuthenticatedUrl authenticatedUrl = (AuthenticatedUrl) o;

        if (this.credentials == null ? authenticatedUrl.credentials != null : !this.credentials.equals(authenticatedUrl.credentials))
        {
            return false;
        }
        if (this.url == null ? authenticatedUrl.url != null : !this.url.equals(authenticatedUrl.url))
        {
            return false;
        }
        return Arrays.equals(this.cookies, authenticatedUrl.cookies);
    }

    @Override
    public int hashCode()
    {
        int result = this.url == null ? 0 : this.url.hashCode();
        result = 29 * result + (this.credentials == null ? 0 : this.credentials.hashCode());
        result = 29 * result + Arrays.hashCode(this.cookies);
        return result;
    }

    @Override
    public String toString()
    {
        String result = this.url.toString();
        result += this.credentials == null ? " (no credentials)" : " (with credentials)";
        result += this.cookies == null ? " (no cookies)" : " (with cookies)";
        return result;
    }
}
