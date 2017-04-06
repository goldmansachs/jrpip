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

package com.gs.jrpip.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.gs.jrpip.FixedDeflaterOutputStream;
import com.gs.jrpip.FixedInflaterInputStream;

public final class ByteUtils
{
    private ByteUtils()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    public static byte[] convertObjectToBytes(Object data) throws IOException
    {
        byte[] pileOfBytes = null;
        FixedDeflaterOutputStream zip = null;
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(200);
            zip = new FixedDeflaterOutputStream(bos);
            ObjectOutputStream oos = new ObjectOutputStream(zip);
            oos.writeObject(data);
            oos.flush();
            bos.flush();
            zip.finish();
            zip.close();
            zip = null;
            pileOfBytes = bos.toByteArray();
            bos.close();
        }
        finally
        {
            if (zip != null)
            {
                zip.finish();
            }
        }
        return pileOfBytes;
    }

    public static Object convertBytesToObject(byte[] input) throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        FixedInflaterInputStream zis = null;
        Object result = null;
        try
        {
            zis = new FixedInflaterInputStream(bis);
            ObjectInputStream ois = new ObjectInputStream(zis);
            result = ois.readObject();
            ois.close();
            zis.finish();
            zis.close();
            zis = null;
            bis.close();
        }
        finally
        {
            if (zis != null)
            {
                zis.finish();
            }
        }
        return result;
    }
}
