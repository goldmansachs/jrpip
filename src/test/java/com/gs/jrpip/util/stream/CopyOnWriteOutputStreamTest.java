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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class CopyOnWriteOutputStreamTest
{
    @Test
    public void testCopyOnWrite() throws IOException
    {
        ByteArrayOutputStream writeTo = new ByteArrayOutputStream();
        ByteArrayOutputStream copyTo = new ByteArrayOutputStream();
        DataOutputStream copyOnWriteOutputStream = new DataOutputStream(new CopyOnWriteOutputStream(writeTo, copyTo));
        copyOnWriteOutputStream.writeInt(5);
        copyOnWriteOutputStream.writeInt(10);
        copyOnWriteOutputStream.close();
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(writeTo.toByteArray()));
        Assert.assertEquals(5L, dataInputStream.readInt());
        Assert.assertEquals(10L, dataInputStream.readInt());
        DataInputStream dataInputStream2 = new DataInputStream(new ByteArrayInputStream(copyTo.toByteArray()));
        Assert.assertEquals(5L, dataInputStream2.readInt());
        Assert.assertEquals(10L, dataInputStream2.readInt());
    }
}
