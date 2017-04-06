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

import junit.framework.TestCase;
import org.junit.Assert;

public class ByteUtilsTest
        extends TestCase
{
    private static final double TEST_DOUBLE = 42.1415;

    public void testSimpleConversion()
    {
        try
        {
            byte[] bytes = ByteUtils.convertObjectToBytes(new Double(TEST_DOUBLE));
            Assert.assertTrue(bytes.length > 0);

            Double d = (Double) ByteUtils.convertBytesToObject(bytes);
            Assert.assertEquals(TEST_DOUBLE, d, 0.0);
        }
        catch (Exception e)
        {
            fail("Unexpected error "+e.getClass().getName());
        }
    }
}
