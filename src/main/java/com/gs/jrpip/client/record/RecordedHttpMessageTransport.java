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

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.RequestId;
import com.gs.jrpip.client.HttpMessageTransport;
import com.gs.jrpip.client.MessageTransportData;
import com.gs.jrpip.client.ResponseMessage;
import com.gs.jrpip.server.StreamBasedInvocator;
import com.gs.jrpip.util.stream.ClampedInputStream;
import com.gs.jrpip.util.stream.SerialMultiplexedWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;

public class RecordedHttpMessageTransport extends HttpMessageTransport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordedHttpMessageTransport.class.getName());
    private final MethodCallStreamResolver streamResolver;

    public RecordedHttpMessageTransport(MethodCallStreamResolver streamResolver)
    {
        this.streamResolver = streamResolver;
    }

    @Override
    public ResponseMessage sendParameters(MessageTransportData data, RequestId requestId, int timeout,
            String serviceClass, String mangledMethodName, Object[] args, Method method, boolean compress) throws ClassNotFoundException, IOException
    {
        InputStream resultStream = this.streamResolver.resolveInputStream(method, args);
        if (resultStream == null)
        {
            throw new NotSerializableException("No recorded result available!");
        }
        Object o = readResultFromFile(resultStream);
        if (o instanceof Throwable)
        {
            return ResponseMessage.forSuccess(StreamBasedInvocator.FAULT_STATUS, o);
        }
        return ResponseMessage.forSuccess(StreamBasedInvocator.OK_STATUS, o);
    }

    private Object readResultFromFile(InputStream recordedResult) throws IOException, ClassNotFoundException
    {
        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(recordedResult));
        try
        {
            this.validate(inputStream);

            return this.readResult(inputStream);
        }
        finally
        {
            try
            {
                inputStream.close();
            }
            catch (IOException e)
            {
                LOGGER.debug("Could not close stream. See previous exception for cause", e);
            }
        }
    }

    private void validate(DataInput inputStream) throws IOException
    {
        if (inputStream.readInt() != SerialMultiplexedWriter.VERSION)
        {
            throw new RuntimeException("Cannot handle stream with version != " + SerialMultiplexedWriter.VERSION);
        }

        if (inputStream.readInt() < 0)
        {
            throw new RuntimeException("Invalid stream id!");
        }
    }

    private Object readResult(DataInputStream inputStream) throws IOException, ClassNotFoundException
    {
        int size = inputStream.readInt();

        FixedInflaterInputStream zipped = new FixedInflaterInputStream(new ClampedInputStream(inputStream, size));
        ObjectInput in = new ObjectInputStream(zipped);

        try
        {
            return in.readObject();
        }
        finally
        {
            in.close();
            zipped.finish();
        }
    }
}
