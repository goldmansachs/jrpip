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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoImpl implements Echo
{
    public static Method getEchoMethod()
    {
        return ReflectionHelper.getMethod(EchoImpl.class, "echo", new Class[]{String.class});
    }

    public static Method getUnexpectedExceptionMethod()
    {
        return ReflectionHelper.getMethod(EchoImpl.class, "throwUnexpectedException", new Class[]{});
    }

    @Override
    public String echo(String input)
    {
        return input;
    }

    @Override
    public String uncompressedEcho(String input)
    {
        return input;
    }

    @Override
    public Object echoObject(Object inputObject)
    {
        return inputObject;
    }

    @Override
    public String echoAndSleep(String input, long sleepInMillis) throws Exception
    {
        Thread.sleep(sleepInMillis);
        return input;
    }

    @Override
    public String echoAndSleepNoSetting(String input, long sleepInMillis) throws Exception
    {
        Thread.sleep(sleepInMillis);
        return input;
    }

    @Override
    public void throwExpectedException() throws FakeException
    {
        throw new FakeException();
    }

    @Override
    public void throwUnexpectedException()
    {
        throw new RuntimeException("exception for testing");
    }

    @Override
    public ObjectWithSerializationError echoWithException(String input)
    {
        return new ObjectWithSerializationError(input);
    }

    @Override
    public int testUnserializableObject(Object o)
    {
        return o.hashCode();
    }

    /**
     * A utility/helper class for working with Classes and Reflection.
     */
    private static final class ReflectionHelper
    {
        private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionHelper.class);

        /**
         * Mapping of primitive wrapper classes to primitive types
         */
        private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS = new HashMap<Class<?>, Class<?>>();

        static
        {
            PRIMITIVES_TO_WRAPPERS.put(short.class, Short.class);
            PRIMITIVES_TO_WRAPPERS.put(boolean.class, Boolean.class);
            PRIMITIVES_TO_WRAPPERS.put(byte.class, Byte.class);
            PRIMITIVES_TO_WRAPPERS.put(char.class, Character.class);
            PRIMITIVES_TO_WRAPPERS.put(int.class, Integer.class);
            PRIMITIVES_TO_WRAPPERS.put(float.class, Float.class);
            PRIMITIVES_TO_WRAPPERS.put(long.class, Long.class);
            PRIMITIVES_TO_WRAPPERS.put(double.class, Double.class);
        }

        private ReflectionHelper()
        {
            throw new AssertionError("Suppress default constructor for noninstantiability");
        }

        // These are special methods that will not produce error messages if the getter method is not found

        public static Method getMethod(Class methodClass, String methodName, Class[] parameterTypes)
        {
            return getMethod(methodClass, methodName, parameterTypes, false);
        }

        private static Method getMethod(Class<?> methodClass, String methodName, Class[] parameterTypes, boolean isSilent)
        {
            Method newMethod;
            try
            {
                newMethod = methodClass.getMethod(methodName, parameterTypes);
            }
            catch (NoSuchMethodException nsme)
            {
                newMethod = searchForMethod(methodClass, methodName, parameterTypes);
                if (newMethod == null)
                {
                    printNoSuchMethodException(methodClass, methodName, parameterTypes, nsme, isSilent);
                }
            }
            return newMethod;
        }

        private static Method searchForMethod(Class<?> methodClass, String methodName, Class[] parameterTypes)
        {
            Method method = null;
            Method[] allMethods = methodClass.getMethods();
            for (int i = 0; i < allMethods.length && method == null; i++)
            {
                if (methodMatch(allMethods[i], methodName, parameterTypes))
                {
                    method = allMethods[i];
                }
            }
            return method;
        }

        public static boolean methodMatch(Method candidate, String methodName, Class[] parameterTypes)
        {
            return candidate.getName().equals(methodName)
                    && parameterTypesMatch(candidate.getParameterTypes(), parameterTypes);
        }

        public static boolean parameterTypesMatch(Class[] candidateParamTypes, Class[] desiredParameterTypes)
        {
            boolean match = candidateParamTypes.length == desiredParameterTypes.length;
            for (int i = 0; i < candidateParamTypes.length && match; i++)
            {
                Class<?> candidateType = candidateParamTypes[i].isPrimitive() && !desiredParameterTypes[i].isPrimitive()
                        ? PRIMITIVES_TO_WRAPPERS.get(candidateParamTypes[i])
                        : candidateParamTypes[i];
                match = candidateType.isAssignableFrom(desiredParameterTypes[i]);
            }
            return match;
        }

        private static void printNoSuchMethodException(
                Class<?> methodClass,
                String methodName,
                Class[] parameterTypes,
                NoSuchMethodException nsme,
                boolean isSilent)
        {
            StringBuilder errorMsg =
                    new StringBuilder("Could not find: " + methodClass.getName() + ">>" + methodName + '(');

            for (Class<?> parameterType : parameterTypes)
            {
                errorMsg.append(parameterType.getName());
                errorMsg.append(", ");
            }
            errorMsg.append(')');

            if (isSilent)
            {
                LOGGER.warn(errorMsg.toString());
            }
            else
            {
                LOGGER.error(errorMsg.toString(), nsme);
            }
        }
    }
}
