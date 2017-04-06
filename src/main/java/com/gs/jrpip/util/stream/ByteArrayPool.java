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

package com.gs.jrpip.util.stream;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ByteArrayPool
{
    private final ConcurrentLinkedQueue<byte[]> pooledByteArrays = new ConcurrentLinkedQueue<byte[]>();
    private final int maxSize;
    private final int arraySize;

    public ByteArrayPool(int maxSize, int arraySize)
    {
        this.maxSize = maxSize;
        this.arraySize = arraySize;
    }

    public byte[] borrowByteArray()
    {
        return this.borrowByteArray(this.arraySize);
    }

    public byte[] borrowByteArray(int requestedArraySize)
    {
        if (requestedArraySize != this.arraySize || this.pooledByteArrays.isEmpty())
        {
            return new byte[requestedArraySize];
        }
        byte[] array = this.pooledByteArrays.poll();
        return array == null ? new byte[requestedArraySize] : array;
    }

    public void returnByteArray(byte[] bytes)
    {
        if (bytes.length == this.arraySize && this.pooledByteArrays.size() < this.maxSize)
        {
            this.pooledByteArrays.add(bytes);
        }
    }
}
