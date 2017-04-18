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

import com.gs.jrpip.MethodResolverFactory;
import com.gs.jrpip.util.stream.ByteArrayPool;

public class RequestDataMultiStreamIterable
        implements Iterable<RequestData>
{
    private RequestData requestData;
    private final InputStreamDemuxer demuxer;
    private boolean endOfFile;

    public RequestDataMultiStreamIterable(InputStream inputStream)
    {
        this.demuxer = new InputStreamDemuxer(inputStream, new RequestStreamProcessorFactory(new ByteArrayPool(100, 2000), new ByteSequenceListener()
        {
            private final RequestInflatorSequenceHelper requestInflatorSequenceHelper = new RequestInflatorSequenceHelper(new MethodResolverFactory());

            public void sequenceCompleted(int streamId, byte[] sequenceOfBytes)
            {
                RequestDataMultiStreamIterable.this.requestData = this.requestInflatorSequenceHelper.inflate(streamId, sequenceOfBytes);
            }
        }));
    }

    @Override
    public Iterator<RequestData> iterator()
    {
        return new Iterator<RequestData>()
        {
            public boolean hasNext()
            {
                while (!RequestDataMultiStreamIterable.this.endOfFile && RequestDataMultiStreamIterable.this.requestData == null) // request data is set by ByteSequenceListener
                {
                    RequestDataMultiStreamIterable.this.endOfFile = !RequestDataMultiStreamIterable.this.demuxer.readBytes();
                }
                return RequestDataMultiStreamIterable.this.requestData != null;
            }

            public RequestData next()
            {
                if (this.hasNext())
                {
                    RequestData dataToReturn = RequestDataMultiStreamIterable.this.requestData;
                    RequestDataMultiStreamIterable.this.requestData = null;
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
}
