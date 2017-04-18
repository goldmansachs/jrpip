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

package com.gs.jrpip;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;

public class JrpipEvent
{
    public static final int METHOD_STARTED_EVENT = 1;
    public static final int METHOD_FINISHED_EVENT = 2;
    public static final int METHOD_FAILED_EVENT = 3;

    private final Method method;
    private final String remoteAddress;
    private final RequestId requestId;
    private final int state;

    private SoftReference softRefToArguments;
    private SoftReference softRefToResult;
    private SoftReference softRefToException;

    private JrpipEvent(
            RequestId requestId,
            Method method,
            String remoteAddress,
            int state)
    {
        this.requestId = requestId;
        this.method = method;
        this.remoteAddress = remoteAddress;
        this.state = state;
    }

    public JrpipEvent(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Object[] arguments)
    {
        this(requestId, method, remoteAddress, METHOD_STARTED_EVENT);
        this.softRefToArguments = new SoftReference(arguments);
    }

    public JrpipEvent(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Object result)
    {
        this(requestId, method, remoteAddress, METHOD_FINISHED_EVENT);
        this.softRefToResult = new SoftReference(result);
    }

    public JrpipEvent(
            RequestId requestId,
            Method method,
            String remoteAddress,
            Throwable exception)
    {
        this(requestId, method, remoteAddress, METHOD_FAILED_EVENT);
        this.softRefToException = new SoftReference(exception);
    }

    public Method getMethod()
    {
        return this.method;
    }

    public Object[] getArguments()
    {
        return (Object[]) this.softRefToArguments.get();
    }

    public int getState()
    {
        return this.state;
    }

    public String getRemoteAddress()
    {
        return this.remoteAddress;
    }

    public RequestId getRequestId()
    {
        return this.requestId;
    }

    public Object getResult()
    {
        return this.softRefToResult.get();
    }

    public Throwable getException()
    {
        return (Throwable) this.softRefToException.get();
    }

    @Override
    public String toString()
    {
        return "Method: " + this.method.getName()
                + " Invoking Host: " + this.remoteAddress
                + " Request Id: " + this.requestId.hashCode()
                + " State: " + this.state;
    }
}
