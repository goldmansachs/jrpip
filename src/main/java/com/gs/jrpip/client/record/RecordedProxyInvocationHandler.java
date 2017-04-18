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

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import com.gs.jrpip.FixedInflaterInputStream;
import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.util.stream.ClampedInputStream;
import com.gs.jrpip.util.stream.SerialMultiplexedWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordedProxyInvocationHandler implements InvocationHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordedProxyInvocationHandler.class.getName());
    private final MethodCallStreamResolver streamResolver;

    public RecordedProxyInvocationHandler(MethodCallStreamResolver streamResolver)
    {
        this.streamResolver = streamResolver;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        InputStream resutStream = this.streamResolver.resolveInputStream(method, args);
        if (resutStream == null)
        {
            throw new JrpipRuntimeException("No recorded result available!");
        }
        return this.readResultFromFile(resutStream);
    }

    private Object readResultFromFile(InputStream recordedResult) throws Throwable
    {
        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(recordedResult));
        try
        {
            this.validate(inputStream);

            Object o = this.readResult(inputStream);
            if (o instanceof Throwable)
            {
                throw (Throwable) o;
            }
            return o;
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
