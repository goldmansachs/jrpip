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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

public class BufferedPostMethod
        extends EntityEnclosingMethod
        implements RequestEntity
{
    private final OutputStreamWriter writer;
    private byte[] result;

    public BufferedPostMethod(String uri, OutputStreamWriter writer)
    {
        super(uri);
        this.writer = writer;
    }

    @Override
    public String getName()
    {
        return "POST";
    }

    @Override
    protected boolean hasRequestContent()
    {
        return true;
    }

    @Override
    protected void writeRequest(HttpState state, HttpConnection conn) throws IOException
    {
        try
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(2048);
            this.writer.write(outputStream);
            outputStream.close();
            this.result = outputStream.toByteArray();
            this.setRequestEntity(this);

            this.writeRequestLine(state, conn);
            this.writeRequestHeaders(state, conn);
            conn.writeLine(); // close head
            // make sure the status line and headers have been sent
            conn.flushRequestOutputStream();
            this.writeRequestBody(state, conn);
            conn.flushRequestOutputStream();
        }
        catch (IOException e)
        {
            this.cleanupConnection(conn);
            throw e;
        }
    }

    protected void cleanupConnection(HttpConnection conn)
    {
        conn.close();
        conn.releaseConnection();
    }

    @Override
    public boolean isRepeatable()
    {
        return false;
    }

    @Override
    public void writeRequest(OutputStream out) throws IOException
    {
        out.write(this.result);
    }

    @Override
    public long getContentLength()
    {
        return this.result.length;
    }

    @Override
    public String getContentType()
    {
        return null;
    }
}
