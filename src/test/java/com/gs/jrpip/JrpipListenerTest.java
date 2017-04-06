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

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Arrays;

import org.jmock.Mock;
import org.jmock.core.Constraint;
import org.junit.Assert;
import org.mortbay.jetty.servlet.ServletHolder;

public class JrpipListenerTest extends JrpipTestCase
{
    private static final String LISTENER_NAME = "listener.FakeListener";

    @Override
    protected void addCustomConfiguration(ServletHolder holder)
    {
        holder.put(LISTENER_NAME, "com.gs.jrpip.FakeListener");
    }

    private Mock buildMockForSuccessfulCall()
    {
        Mock testListenerMock = new Mock(JrpipEventListener.class);

        testListenerMock.expects(this.once()).method("methodInvocationEvent").with(
                EventConstraint.forStartedEvent(EchoImpl.getEchoMethod(), new Object[]{"hello"}))
                .id("started event");

        testListenerMock.expects(this.once()).method("methodInvocationEvent").with(
                EventConstraint.forFinishedEvent(EchoImpl.getEchoMethod(), "hello"))
                .after("started event");

        return testListenerMock;
    }

    private Mock buildMockForExceptionalCall()
    {
        Mock testListenerMock = new Mock(JrpipEventListener.class);

        testListenerMock.expects(this.once()).method("methodInvocationEvent").with(
                EventConstraint.forStartedEvent(EchoImpl.getUnexpectedExceptionMethod(), new Object[]{}))
                .id("started event");

        testListenerMock.expects(this.once()).method("methodInvocationEvent").with(
                EventConstraint.forFailedEvent(EchoImpl.getUnexpectedExceptionMethod(), RuntimeException.class))
                .after("started event");

        return testListenerMock;
    }

    public void testEchoEvents() throws MalformedURLException, InterruptedException
    {
        Mock testListenerMock = this.buildMockForSuccessfulCall();

        FakeListener fakeListener = (FakeListener) this.servlet.getListeners().getListener(LISTENER_NAME);
        fakeListener.setDelegate((JrpipEventListener) testListenerMock.proxy());

        Echo echo = this.buildEchoProxy();
        Assert.assertEquals("hello", echo.echo("hello"));

        testListenerMock.verify();
    }

    public void testExceptionalEvents() throws MalformedURLException,
            InterruptedException
    {
        Mock testListenerMock = this.buildMockForExceptionalCall();

        FakeListener fakeListener = (FakeListener) this.servlet.getListeners().getListener(LISTENER_NAME);
        fakeListener.setDelegate((JrpipEventListener) testListenerMock.proxy());

        Echo echo = this.buildEchoProxy();
        try
        {
            echo.throwUnexpectedException();
        }
        catch (RuntimeException e)
        {
            //caught test exception
        }

        testListenerMock.verify();
    }

    private static final class EventConstraint implements Constraint
    {
        private final Method method;
        private final int state;
        private final Object[] arguments;
        private final Object result;
        private final Class exceptionClass;

        private EventConstraint(
                Method method,
                int state,
                Object[] arguments,
                Object result,
                Class exceptionClass)
        {
            this.method = method;
            this.state = state;
            this.arguments = arguments;
            this.result = result;
            this.exceptionClass = exceptionClass;
        }

        public static EventConstraint forStartedEvent(Method method, Object[] arguments)
        {
            return new EventConstraint(method, JrpipEvent.METHOD_STARTED_EVENT, arguments, null, null);
        }

        public static EventConstraint forFinishedEvent(Method method, Object result)
        {
            return new EventConstraint(method, JrpipEvent.METHOD_FINISHED_EVENT, null, result, null);
        }

        public static EventConstraint forFailedEvent(Method method, Class exceptionClass)
        {
            return new EventConstraint(method, JrpipEvent.METHOD_FAILED_EVENT, null, null, exceptionClass);
        }

        @Override
        public boolean eval(Object argument)
        {
            JrpipEvent event = (JrpipEvent) argument;

            boolean passed = this.method.equals(event.getMethod());
            passed = passed && event.getState() == this.state;

            if (this.state == JrpipEvent.METHOD_STARTED_EVENT)
            {
                passed = passed && Arrays.equals(this.arguments, event.getArguments());
            }
            else if (this.state == JrpipEvent.METHOD_FINISHED_EVENT)
            {
                passed = passed && this.result.equals(event.getResult());
            }
            else if (this.state == JrpipEvent.METHOD_FAILED_EVENT)
            {
                passed = passed && "java.lang.RuntimeException".equals(this.exceptionClass.getName());
            }
            else
            {
                throw new RuntimeException("Invalid state: " + this.state);
            }

            return passed;
        }

        @Override
        public StringBuffer describeTo(StringBuffer stringBuffer)
        {
            return stringBuffer.append("Method event constraint ").append(this.state);
        }
    }
}
