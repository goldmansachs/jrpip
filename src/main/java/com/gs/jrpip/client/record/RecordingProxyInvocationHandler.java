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

package com.gs.jrpip.client.record;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import com.gs.jrpip.client.AuthenticatedUrl;
import com.gs.jrpip.client.FastServletProxyInvocationHandler;
import com.gs.jrpip.util.stream.CopyOnReadInputStream;
import com.gs.jrpip.util.stream.DedicatedOutputStream;

public class RecordingProxyInvocationHandler extends FastServletProxyInvocationHandler
{
    private static final DedicatedOutputStream SINGLE_OUTPUT_STREAM = new DedicatedOutputStream();
    private final MethodCallStreamResolver streamResolver;

    public RecordingProxyInvocationHandler(
            AuthenticatedUrl url,
            Class<?> api,
            int timeout,
            MethodCallStreamResolver streamResolver)
    {
        super(url, api, timeout);
        this.streamResolver = streamResolver;
    }

    public RecordingProxyInvocationHandler(
            AuthenticatedUrl url,
            Class<?> api,
            int timeout,
            MethodCallStreamResolver streamResolver, boolean stickySessionEnabled)
    {
        super(url, api, timeout, stickySessionEnabled);
        this.streamResolver = streamResolver;
    }

    @Override
    protected Object getResult(Method method, Object[] args, InputStream is) throws IOException, ClassNotFoundException
    {
        OutputStream resultStream = this.streamResolver.resolveOutputStream(method, args);
        if (resultStream == null)
        {
            return super.getResult(method, args, is);
        }

        OutputStream outputStream = SINGLE_OUTPUT_STREAM.dedicatedFor(resultStream);
        CopyOnReadInputStream copyOnReadInputStream = this.splitResponseStream(is, outputStream);
        try
        {
            return super.getResult(method, args, copyOnReadInputStream);
        }
        finally
        {
            outputStream.close();
        }
    }

    private CopyOnReadInputStream splitResponseStream(InputStream is, OutputStream outputStream)
    {
        CopyOnReadInputStream copyOnReadInputStream = new CopyOnReadInputStream(is);
        copyOnReadInputStream.startCopyingInto(outputStream);
        return copyOnReadInputStream;
    }
}
