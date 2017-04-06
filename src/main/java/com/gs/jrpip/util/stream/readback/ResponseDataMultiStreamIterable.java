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

import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import com.gs.jrpip.MethodResolverFactory;
import com.gs.jrpip.util.stream.ByteArrayPool;

public class ResponseDataMultiStreamIterable
        implements Iterable<ResponseData>
{
    private ResponseData responseData;
    private RequestData requestData;
    private final InputStreamDemuxer demuxer;
    private boolean endOfFile;

    public ResponseDataMultiStreamIterable(InputStream inputStream, final Set<Integer> targetRequestStreamIds)
    {
        ByteSequenceListener requestSequenceListener = new ByteSequenceListener()
        {
            public void sequenceCompleted(int streamId, byte[] sequenceOfBytes)
            {
                if (targetRequestStreamIds.contains(streamId))
                {
                    RequestInflatorSequenceHelper resquestInflatorSequenceHelper = new RequestInflatorSequenceHelper(new MethodResolverFactory());
                    ResponseDataMultiStreamIterable.this.setRequestData(resquestInflatorSequenceHelper.inflate(streamId, sequenceOfBytes));
                }
            }
        };
        ByteSequenceListener responseSequenceListener = new ByteSequenceListener()
        {
            public void sequenceCompleted(int streamId, byte[] sequenceOfBytes)
            {
                ResponseInflatorSequenceHelper responseInflatorSequenceHelper = new ResponseInflatorSequenceHelper();
                ResponseData candidateResponse = responseInflatorSequenceHelper.inflate(streamId, sequenceOfBytes);
                if (ResponseDataMultiStreamIterable.this.getRequestData() != null
                        && ResponseDataMultiStreamIterable.this.getRequestData().getRequestId().equals(candidateResponse.getRequestId()))
                {
                    candidateResponse.setRequestData(ResponseDataMultiStreamIterable.this.getRequestData());
                    ResponseDataMultiStreamIterable.this.setResponseData(candidateResponse);
                }
            }
        };
        this.demuxer = new InputStreamDemuxer(
                inputStream,
                new RequestResponseStreamProcessorFactory(
                        new ByteArrayPool(100, 2000),
                        requestSequenceListener,
                        responseSequenceListener));
    }

    @Override
    public Iterator<ResponseData> iterator()
    {
        return new Iterator<ResponseData>()
        {
            public boolean hasNext()
            {
                while (!ResponseDataMultiStreamIterable.this.isEndOfFile() && ResponseDataMultiStreamIterable.this.getResponseData() == null) // request data is set by ByteSequenceListener
                {
                    ResponseDataMultiStreamIterable.this.setEndOfFile(!ResponseDataMultiStreamIterable.this.getDemuxer().readBytes());
                }
                return ResponseDataMultiStreamIterable.this.getResponseData() != null;
            }

            public ResponseData next()
            {
                if (this.hasNext())
                {
                    ResponseData dataToReturn = ResponseDataMultiStreamIterable.this.getResponseData();
                    ResponseDataMultiStreamIterable.this.setResponseData(null);
                    return dataToReturn;
                }
                throw new NoSuchElementException();
            }

            public void remove()
            {
                throw new RuntimeException("Not supported");
            }
        };
    }

    private void setRequestData(RequestData aRequestData)
    {
        this.requestData = aRequestData;
    }

    private RequestData getRequestData()
    {
        return this.requestData;
    }

    private void setResponseData(ResponseData responseData)
    {
        this.responseData = responseData;
    }

    public ResponseData getResponseData()
    {
        return this.responseData;
    }

    private boolean isEndOfFile()
    {
        return this.endOfFile;
    }

    private void setEndOfFile(boolean endOfFile)
    {
        this.endOfFile = endOfFile;
    }

    public InputStreamDemuxer getDemuxer()
    {
        return this.demuxer;
    }
}
