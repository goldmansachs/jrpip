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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;

import com.gs.jrpip.MethodResolverFactory;
import com.gs.jrpip.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestInflatorSequenceHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestInflatorSequenceHelper.class);

    private final MethodResolverFactory methodResolverFactory;

    public RequestInflatorSequenceHelper(MethodResolverFactory methodResolverFactory)
    {
        this.methodResolverFactory = methodResolverFactory;
    }

    public RequestData inflate(int streamId, byte[] sequenceOfBytes)
    {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(sequenceOfBytes));
        try
        {
            ObjectInputStream result = new ObjectInputStream(stream);
            RequestId requestId = (RequestId) result.readObject();
            String className = (String) result.readObject();
            Method method = this.methodResolverFactory.resolveMethodForClassName(className, (String) result.readObject());
            return new RequestData(streamId, requestId, method.getName(), this.inflateParameters(result, method), stream.readLong(), stream.readLong());
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to process RequestData for stream {} because of exception", streamId, e);
        }
        return null;
    }

    private Object[] inflateParameters(
            ObjectInputStream result,
            Method method) throws IOException, ClassNotFoundException
    {
        int parametersSize = method.getParameterTypes().length;
        Object[] parameters = new Object[parametersSize];
        for (int i = 0; i < parametersSize; i++)
        {
            parameters[i] = result.readObject();
        }
        return parameters;
    }
}
