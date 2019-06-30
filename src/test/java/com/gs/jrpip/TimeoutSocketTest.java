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

package com.gs.jrpip;

import com.gs.jrpip.client.JrpipRuntimeException;
import com.gs.jrpip.client.JrpipTimeoutException;
import org.junit.Assert;

public class TimeoutSocketTest extends SocketTestCase
{
    @Override
    public void setUp() throws Exception
    {
        System.setProperty("jrpip.timeout.com.gs.jrpip.Echo.echoAndSleep_java.lang.String_long", "1000");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        System.getProperties().remove("jrpip.timeout.com.gs.jrpip.Echo.echoAndSleep_java.lang.String_long");
    }

    public void testTimeoutWithProperty() throws Exception
    {
        Echo echo = this.buildEchoProxy();

        // test timeout of 1 second on connection vs. 4 sec delay
        try
        {
            echo.echoAndSleep("hello", 2000L);
            Assert.fail("JRPIP method timeout did not work.");
        }
        catch (JrpipTimeoutException ex)
        {
        }

        echo.echoAndSleepNoSetting("hello", 2000L);
    }

    public void testTimeoutWithConstructor() throws Exception
    {
        Echo echoWithTimeout = this.buildEchoProxy(1000);

        try
        {
            echoWithTimeout.echoAndSleepNoSetting("hello", 2000L);
            Assert.fail("JRPIP method timeout did not work.");
        }
        catch (JrpipTimeoutException ex)
        {
        }

        Echo echoWithoutTimeout = this.buildEchoProxy();

        echoWithoutTimeout.echoAndSleepNoSetting("hello", 2000L);
    }

    public void testTimeoutPrecedence() throws Exception
    {
        Echo echoWithTimeout = this.buildEchoProxy(200);

        // proxy has setting of 200, while property is at 1000
        // property should have precedence making the timeout 1000
        // so, method call of 2000 should timeout, but if 800 should not if precedence is correct

        try
        {
            echoWithTimeout.echoAndSleep("hello", 2000L);
            Assert.fail("JRPIP method timeout did not work.");
        }
        catch (JrpipTimeoutException ex)
        {
        }

        Echo echoWithoutTimeout = this.buildEchoProxy();

        echoWithoutTimeout.echoAndSleep("hello", 800L);
    }

    public void testTimeoutAnnotation() throws Exception
    {
        Echo echo = this.buildEchoProxy();

        try
        {
            echo.echoWithTimeout("hello", 300);
            Assert.fail("JRPIP annotation timeout did not work");
        }
        catch (JrpipTimeoutException te)
        {
            //expected
        }
        echo.echoWithTimeout("goodbye", 100);
    }
}
