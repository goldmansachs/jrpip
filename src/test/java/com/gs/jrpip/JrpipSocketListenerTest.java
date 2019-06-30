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

import com.gs.jrpip.server.SocketServerConfig;
import org.jmock.Mock;
import org.jmock.core.Constraint;
import org.junit.Assert;
import org.mortbay.jetty.servlet.ServletHolder;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JrpipSocketListenerTest extends SocketTestCase
{
    private RecordingListener listener;

    @Override
    protected void addMoreConfig(SocketServerConfig config)
    {
        this.listener = new RecordingListener();
        config.addListener(listener);
    }


    public void testEchoEvents() throws MalformedURLException, InterruptedException
    {
        Echo echo = this.buildEchoProxy();
        Assert.assertEquals("hello", echo.echo("hello"));

        assertEvent(this.listener.events.get(0), JrpipEvent.METHOD_STARTED_EVENT, EchoImpl.getEchoMethod());
        assertEvent(this.listener.events.get(1), JrpipEvent.METHOD_FINISHED_EVENT, EchoImpl.getEchoMethod());
    }

    private void assertEvent(JrpipEvent event, int state, Method echoMethod)
    {
        Assert.assertEquals(state, event.getState());
        Assert.assertEquals(echoMethod, event.getMethod());
    }

    public void testExceptionalEvents() throws MalformedURLException
    {
        Echo echo = this.buildEchoProxy();
        try
        {
            echo.throwUnexpectedException();
        }
        catch (RuntimeException e)
        {
            //caught test exception
        }

        assertEvent(this.listener.events.get(0), JrpipEvent.METHOD_STARTED_EVENT, EchoImpl.getUnexpectedExceptionMethod());
        assertEvent(this.listener.events.get(1), JrpipEvent.METHOD_FAILED_EVENT, EchoImpl.getUnexpectedExceptionMethod());
    }

    private static class RecordingListener implements JrpipEventListener
    {
        private List<JrpipEvent> events = new ArrayList<>();

        @Override
        public void methodInvocationEvent(JrpipEvent stateChangeEvent)
        {
            this.events.add(stateChangeEvent);
        }
    }
}
