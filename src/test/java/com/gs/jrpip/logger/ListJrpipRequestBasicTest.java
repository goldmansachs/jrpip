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

package com.gs.jrpip.logger;

import java.util.Map;

import com.gs.jrpip.JrpipTestCase;
import com.gs.jrpip.util.Predicate;
import com.gs.jrpip.util.stream.readback.RequestData;
import org.junit.Assert;
import org.junit.Test;

public class ListJrpipRequestBasicTest
{
    private final String[] minimumArgumentSet = {"-f", "inputFile.log"};
    private final String[] fullArgumentSet = {"-f", "inputFile.log", "-id", "11", "23", "-method", "myMethod", "-between", "2011-05-30 13:00:00,", "2011-05-30", " 13:05:00", "-argument", "GSCO"};

    @Test
    public void testConstructor()
    {
        ListJrpipRequest listRequest = new ListJrpipRequest(this.fullArgumentSet);
        Assert.assertNotNull(listRequest);
    }

    @Test(expected = RuntimeException.class)
    public void testConstructorParamCheck()
    {
        new ListJrpipRequest(new String[]{""});
    }

    @Test
    public void testCreateMatchOperations()
    {
        ListJrpipRequest listRequest = new ListJrpipRequest(this.minimumArgumentSet);
        Map<String, String> optionValues = JrpipTestCase.newMapWithKeyValues("-id", "11,23", "-method", "myMethod", "-between", "2011-05-30 13:00:00,2011-05-30 13:05:00", "-argument", "GSCO");
        Predicate<RequestData> operation = listRequest.createMatchOperations(optionValues);
        Assert.assertNotNull(operation);
    }

    @Test(expected = RuntimeException.class)
    public void testForUnsupportedOperation()
    {
        ListJrpipRequest listRequest = new ListJrpipRequest(this.minimumArgumentSet);
        listRequest.createMatchOperations(JrpipTestCase.newMapWithKeyValues("-f", "unsupported"));
    }
}
