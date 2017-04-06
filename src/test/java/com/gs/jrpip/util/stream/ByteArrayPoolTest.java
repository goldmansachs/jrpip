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

import org.junit.Assert;
import org.junit.Test;

public class ByteArrayPoolTest
{
    @Test
    public void testPool()
    {
        ByteArrayPool byteArrayPool = new ByteArrayPool(1, 10);
        byte[] array = byteArrayPool.borrowByteArray(10);
        byteArrayPool.returnByteArray(array);
        Assert.assertSame(array, byteArrayPool.borrowByteArray(10));
        Assert.assertNotSame(array, byteArrayPool.borrowByteArray(10));
    }

    @Test
    public void testTakingArrayOfDifferentLength()
    {
        ByteArrayPool byteArrayPool = new ByteArrayPool(1, 10);
        byte[] array = byteArrayPool.borrowByteArray(11);
        byteArrayPool.returnByteArray(array);
        Assert.assertNotSame(array, byteArrayPool.borrowByteArray(11));
    }
}
