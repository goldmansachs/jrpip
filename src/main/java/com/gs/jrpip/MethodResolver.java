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

import com.gs.jrpip.client.JrpipRuntimeException;

public class MethodResolver
{
    private final Map<Method, String> methodToNameMap = new HashMap<>();
    private final Map<String, Method> nameToMethodMap = new HashMap<>();
    private final Map<Method, Integer> methodToTimeoutMap = new HashMap<>();
    private final Map<Method, Boolean> methodToCompressionMap = new HashMap<>();

    private final Class serviceClass;

    public MethodResolver(Class serviceClass)
    {
        this.serviceClass = serviceClass;
        Method[] methodList = serviceClass.getMethods();

        Integer classTimeout = null;
        if (this.serviceClass.isAnnotationPresent(Timeout.class))
        {
            classTimeout = (int) ((Timeout)this.serviceClass.getAnnotation(Timeout.class)).timeoutMillis();
        }
        Boolean classCompression = null;
        if (this.serviceClass.isAnnotationPresent(Compression.class))
        {
            classCompression = ((Compression)this.serviceClass.getAnnotation(Compression.class)).compress();
        }
        for (Method method : methodList)
        {
            String mangledName = this.mangleName(method);
            this.methodToNameMap.put(method, mangledName);
            this.nameToMethodMap.put(mangledName, method);

            configureTimeout(classTimeout, method);
            configureCompression(classCompression, method);
        }
    }

    private void configureTimeout(Integer classTimeout, Method method)
    {
        Integer timeout = this.buildTimeoutFromProperty(method);
        if (timeout != null)
        {
            this.methodToTimeoutMap.put(method, timeout);
        }
        else
        {
            if (method.isAnnotationPresent(Timeout.class))
            {
                Timeout annotation = method.getAnnotation(Timeout.class);
                this.methodToTimeoutMap.put(method, (int) annotation.timeoutMillis());
            }
            else if (classTimeout != null)
            {
                this.methodToTimeoutMap.put(method, classTimeout);
            }
        }
    }

    private void configureCompression(Boolean classCompression, Method method)
    {
        boolean compress = true;
        if (method.isAnnotationPresent(Compression.class))
        {
            Compression annotation = method.getAnnotation(Compression.class);
            compress = annotation.compress();
        }
        else if (classCompression != null)
        {
            compress = classCompression;
        }
        this.methodToCompressionMap.put(method, compress);
    }

    public String getMangledMethodName(Method method)
    {
        return this.methodToNameMap.get(method);
    }

    public Method getMethodFromMangledName(String mangledName)
    {
        return this.nameToMethodMap.get(mangledName);
    }

    public Integer getMethodTimeout(Method method)
    {
        return this.methodToTimeoutMap.get(method);
    }

    public boolean getMethodCompression(Method method)
    {
        return this.methodToCompressionMap.get(method);
    }

    protected String mangleName(Method method)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(method.getName());

        Class[] params = method.getParameterTypes();
        for (Class pc: params)
        {
            sb.append('_');
            this.mangleClass(sb, pc);
        }

        return sb.toString();
    }

    protected Integer buildTimeoutFromProperty(Method method)
    {
        String methodName = this.mangleName(method);

        String propertyName = "jrpip.timeout." + this.getServiceClass().getName() + "." + methodName;
        String methodTimeoutSetting = System.getProperty(propertyName);

        Integer timeout = null;
        if (methodTimeoutSetting != null)
        {
            try
            {
                timeout = Integer.valueOf(methodTimeoutSetting);
            }
            catch (Exception ex)
            {
                throw new JrpipRuntimeException(
                        "Failed to parse method timeout setting, method: "
                                + propertyName + " timeout: " + methodTimeoutSetting);
            }
        }

        return timeout;
    }

    /**
     * Mangles a classname.
     */
    protected void mangleClass(StringBuilder sb, Class cl)
    {
        String name = cl.getName();

        if (cl.isArray())
        {
            sb.append("array_");
            this.mangleClass(sb, cl.getComponentType());
        }
        else
        {
            sb.append(name);
        }
    }

    public Class getServiceClass()
    {
        return this.serviceClass;
    }
}
