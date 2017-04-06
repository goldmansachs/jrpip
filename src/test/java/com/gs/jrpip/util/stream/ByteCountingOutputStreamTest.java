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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class ByteCountingOutputStreamTest
{
    @Test
    public void testHasReachedSize() throws IOException
    {
        ByteCountingOutputStream byteCountingOutputStream = new ByteCountingOutputStream(new ByteArrayOutputStream(1000));
        byteCountingOutputStream.write(1);
        byteCountingOutputStream.write(2);
        Assert.assertTrue(byteCountingOutputStream.hasReachedSize(1L));
        Assert.assertTrue(byteCountingOutputStream.hasReachedSize(2L));
        Assert.assertFalse(byteCountingOutputStream.hasReachedSize(3L));
    }
}
