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

public class ResponseData
{
    private final int streamId;
    private final RequestId requestId;
    private final byte status;
    private final Object returnedData;
    private RequestData requestData;

    public ResponseData(int streamId, RequestId requestId, byte status, Object returnedData)
    {
        this(streamId, requestId, status, returnedData, null);
    }

    public ResponseData(int streamId, RequestId requestId, byte status, Object returnedData, RequestData requestData)
    {
        this.streamId = streamId;
        this.requestId = requestId;
        this.status = status;
        this.returnedData = returnedData;
        this.requestData = requestData;
    }

    public int getStreamId()
    {
        return this.streamId;
    }

    public RequestId getRequestId()
    {
        return this.requestId;
    }

    public byte getStatus()
    {
        return this.status;
    }

    public Object getReturnedData()
    {
        return this.returnedData;
    }

    public RequestData getRequestData()
    {
        return this.requestData;
    }

    public void setRequestData(RequestData requestData)
    {
        this.requestData = requestData;
    }
}
