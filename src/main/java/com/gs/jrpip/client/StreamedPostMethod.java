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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpConstants;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

public class StreamedPostMethod extends EntityEnclosingMethod
{
    private final OutputStreamWriter writer;

    public StreamedPostMethod(String uri, OutputStreamWriter writer)
    {
        super(uri);
        this.writer = writer;
        this.setContentChunked(true);
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
            BufferedChunkedOutputStream bufferedChunkedOutputStream = new BufferedChunkedOutputStream(conn, state, this);
            this.writer.write(bufferedChunkedOutputStream);
            bufferedChunkedOutputStream.finish();
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

    public void reallyWriteHeaders(HttpState state, HttpConnection conn) throws IOException
    {
        this.writeRequestLine(state, conn);
        this.writeRequestHeaders(state, conn);
        conn.writeLine(); // close head
        // make sure the status line and headers have been sent
        conn.flushRequestOutputStream();
    }

    public static class BufferedChunkedOutputStream
            extends OutputStream
            implements RequestEntity
    {
        private static final byte[] CRLF = {(byte) 13, (byte) 10};

        /**
         * End chunk
         */
        private static final byte[] ENDCHUNK = CRLF;

        /**
         * 0
         */
        private static final byte[] ZERO = {(byte) '0'};

        //private static final boolean CAUSE_RANDOM_ERROR = true;
        private static final double ERROR_RATE = 0.98;

        private final OutputStream stream;

        private final StreamedPostMethod streamedPostMethod;
        private final HttpConnection httpConnection;
        private final HttpState state;

        private final byte[] cache;

        private int cachePosition;

        private boolean wroteHeaders;
        private boolean wroteLastChunk;

        public BufferedChunkedOutputStream(
                HttpConnection conn,
                HttpState state,
                StreamedPostMethod streamedPostMethod) throws IOException
        {
            this.streamedPostMethod = streamedPostMethod;
            this.state = state;
            this.httpConnection = conn;
            this.cache = new byte[2048];
            this.stream = this.httpConnection.getRequestOutputStream();
        }

        protected void flushCache() throws IOException
        {
            if (this.cachePosition > 0)
            {
                this.writeHeaders();
                byte[] chunkHeader = HttpConstants.getBytes(
                        Integer.toHexString(this.cachePosition) + "\r\n");
                this.stream.write(chunkHeader, 0, chunkHeader.length);
                this.stream.write(this.cache, 0, this.cachePosition);
                this.stream.write(ENDCHUNK, 0, ENDCHUNK.length);
                this.cachePosition = 0;
            }
        }

        protected void writeHeaders() throws IOException
        {
            if (!this.wroteHeaders)
            {
                this.streamedPostMethod.reallyWriteHeaders(this.state, this.httpConnection);
                this.wroteHeaders = true;
            }
        }

        protected void flushCacheWithAppend(byte[] bufferToAppend, int off, int len) throws IOException
        {
            this.writeHeaders();
            byte[] chunkHeader = HttpConstants.getBytes(
                    Integer.toHexString(this.cachePosition + len) + "\r\n");
            this.stream.write(chunkHeader, 0, chunkHeader.length);
            this.stream.write(this.cache, 0, this.cachePosition);
            this.stream.write(bufferToAppend, off, len);
            this.stream.write(ENDCHUNK, 0, ENDCHUNK.length);
            this.cachePosition = 0;
        }

        @Override
        public void write(int b) throws IOException
        {
            this.cache[this.cachePosition] = (byte) b;
            this.cachePosition++;
            if (this.cachePosition == this.cache.length)
            {
                this.flushCache();
            }
        }

        @Override
        public void write(byte[] b) throws IOException
        {
            this.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] src, int off, int len) throws IOException
        {
            if (len >= this.cache.length - this.cachePosition)
            {
                this.flushCacheWithAppend(src, off, len);
            }
            else
            {
                System.arraycopy(src, off, this.cache, this.cachePosition, len);
                this.cachePosition += len;
            }
        }

        protected void writeClosingChunk() throws IOException
        {
            // Write the final chunk.
            //if (CAUSE_RANDOM_ERROR) if (Math.random() > ERROR_RATE) throw new IOException("Random error, for testing only!");

            this.stream.write(ZERO, 0, ZERO.length);
            this.stream.write(CRLF, 0, CRLF.length);
            this.stream.write(ENDCHUNK, 0, ENDCHUNK.length);
        }

        public void finish() throws IOException
        {
            if (!this.wroteLastChunk)
            {
                if (this.wroteHeaders)
                {
                    this.flushCache();
                    this.writeClosingChunk();
                }
                else
                {
                    this.streamedPostMethod.setContentChunked(false);
                    this.streamedPostMethod.setRequestEntity(this);
                    this.streamedPostMethod.reallyWriteHeaders(this.state, this.httpConnection);
                    this.streamedPostMethod.writeRequestBody(this.state, this.httpConnection);
                    this.wroteHeaders = true;
                }
                this.wroteLastChunk = true;
            }
        }

        @Override
        public void flush() throws IOException
        {
            this.stream.flush();
        }

        @Override
        public void close() throws IOException
        {
            this.finish();
            super.close();
        }

        @Override
        public boolean isRepeatable()
        {
            return false;
        }

        @Override
        public void writeRequest(OutputStream out) throws IOException
        {
            this.stream.write(this.cache, 0, this.cachePosition);
            this.stream.write(CRLF, 0, CRLF.length);
        }

        @Override
        public long getContentLength()
        {
            return this.cachePosition;
        }

        @Override
        public String getContentType()
        {
            return null;
        }
    }
}
