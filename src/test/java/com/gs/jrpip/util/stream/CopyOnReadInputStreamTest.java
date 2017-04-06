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

public class CopyOnReadInputStreamTest
{
    @Test
    public void testStream() throws IOException, ClassNotFoundException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(out);
        dataOutputStream.writeInt(10);
        dataOutputStream.writeInt(100);
        dataOutputStream.close();
        CopyOnReadInputStream copyOnReadInputStream = new CopyOnReadInputStream(new ByteArrayInputStream(out.toByteArray()));
        DataInputStream dataInputStream = new DataInputStream(copyOnReadInputStream);
        Assert.assertEquals(10L, dataInputStream.readInt());
        ByteArrayOutputStream copyTo = new ByteArrayOutputStream();
        copyOnReadInputStream.startCopyingInto(copyTo);
        Assert.assertEquals(100L, dataInputStream.readInt());
        copyOnReadInputStream.close();
        DataInputStream dataInputStream2 = new DataInputStream(new ByteArrayInputStream(copyTo.toByteArray()));
        Assert.assertEquals(10L, dataInputStream2.readInt());
        Assert.assertEquals(100L, dataInputStream2.readInt());
    }
}
