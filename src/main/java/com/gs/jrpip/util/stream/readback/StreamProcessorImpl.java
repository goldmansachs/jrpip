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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.gs.jrpip.util.stream.ByteArrayPool;

public class StreamProcessorImpl implements StreamProcessor
{
    private final ArrayList<byte[]> bytes;
    private int lastBufferLength;
    private final int streamId;
    private final ByteArrayPool byteArrayPool;
    private final ByteSequenceListener byteSequenceListener;

    public StreamProcessorImpl(int streamId, ByteArrayPool byteArrayPool, ByteSequenceListener byteSequenceListener)
    {
        this.streamId = streamId;
        this.byteArrayPool = byteArrayPool;
        this.byteSequenceListener = byteSequenceListener;
        this.bytes = new ArrayList<byte[]>();
        this.bytes.add(byteArrayPool.borrowByteArray());
    }

    @Override
    public void readBytes(DataInputStream dataInputStream, int size)
    {
        if (size == 0)
        {
            this.byteSequenceListener.sequenceCompleted(this.streamId, this.combineAndReset());
        }
        else
        {
            int bytesRead = 0;
            while ((bytesRead += this.readBytes(dataInputStream, size, bytesRead)) < size)
            {
                this.grow();
            }
        }
    }

    private byte[] combineAndReset()
    {
        int totalSize = 0;
        for (int i = 0; i < this.bytes.size() - 1; i++)
        {
            totalSize += this.bytes.get(i).length;
        }
        totalSize += this.lastBufferLength;
        byte[] results = new byte[totalSize];
        int copiedSoFar = 0;
        for (int i = 0; i < this.bytes.size() - 1; i++)
        {
            byte[] source = this.bytes.get(i);
            System.arraycopy(source, 0, results, copiedSoFar, source.length);
            copiedSoFar += source.length;
        }
        System.arraycopy(this.bytes.get(this.bytes.size() - 1), 0, results, copiedSoFar, this.lastBufferLength);
        this.returnByteArrays();
        return results;
    }

    private void returnByteArrays()
    {
        for (int i = 0; i < this.bytes.size(); i++)
        {
            this.byteArrayPool.returnByteArray(this.bytes.get(i));
        }
        this.bytes.clear();
    }

    private void grow()
    {
        byte[] newArray = this.byteArrayPool.borrowByteArray();
        this.bytes.add(newArray);
        this.lastBufferLength = 0;
    }

    private int readBytes(DataInputStream inputStream, int totalSize, int numberOfBytesRead)
    {
        try
        {
            byte[] readInto = this.bytes.get(this.bytes.size() - 1);
            int bytesRemaining = readInto.length - this.lastBufferLength;
            int remainingBytesToRead = totalSize - numberOfBytesRead;
            int maxNumberOfBytesToRead = Math.min(remainingBytesToRead, bytesRemaining);
            this.read(inputStream, readInto, maxNumberOfBytesToRead);
            return maxNumberOfBytesToRead;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void read(DataInputStream inputStream, byte[] readInto, int maxNumberOfBytesToRead) throws IOException
    {
        int remainingBytesToRead = maxNumberOfBytesToRead;
        while (remainingBytesToRead > 0)
        {
            int bytesRead = inputStream.read(readInto, this.lastBufferLength, remainingBytesToRead);
            this.lastBufferLength += bytesRead;
            remainingBytesToRead -= bytesRead;
        }
    }
}

