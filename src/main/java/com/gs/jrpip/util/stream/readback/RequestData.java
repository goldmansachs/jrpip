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

package com.gs.jrpip.util.stream.readback;

import com.gs.jrpip.RequestId;

public class RequestData
{
    private final int streamId;
    private final RequestId requestId;
    private final String methodName;
    private final Object[] arguments;
    private final long startTime;
    private final long endTime;

    public RequestData(
            int streamId,
            RequestId requestId,
            String methodName,
            Object[] arguments,
            long startTime,
            long endTime)
    {
        this.streamId = streamId;
        this.requestId = requestId;
        this.methodName = methodName;
        this.arguments = arguments;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getStreamId()
    {
        return this.streamId;
    }

    public String getMethodName()
    {
        return this.methodName;
    }

    public Object[] getArguments()
    {
        return this.arguments;
    }

    public RequestId getRequestId()
    {
        return this.requestId;
    }

    public long getStartTime()
    {
        return this.startTime;
    }

    public long getEndTime()
    {
        return this.endTime;
    }
}
