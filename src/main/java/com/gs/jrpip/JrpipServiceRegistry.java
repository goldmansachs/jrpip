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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JrpipServiceRegistry
{
    public static final String ENCODE_STRING = "ISO8859_1";
    private static final JrpipServiceRegistry INSTANCE = new JrpipServiceRegistry();

    private final Map<ServiceIdentifier, Object> webAppInterfaceToImplMap = new ConcurrentHashMap<ServiceIdentifier, Object>();
    private final Map<ServiceIdentifier, Object> urlInterfaceToImplMap = new ConcurrentHashMap<ServiceIdentifier, Object>();

    /**
     * Creates the new proxy factory.
     */
    private JrpipServiceRegistry()
    {
    }

    public static JrpipServiceRegistry getInstance()
    {
        return INSTANCE;
    }

    public void addServiceForWebApp(String webapp, Class interfaceClass, Object implementation)
    {
        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(webapp, interfaceClass);
        this.webAppInterfaceToImplMap.put(serviceIdentifier, implementation);
    }

    public void addServiceForUrl(String url, Class interfaceClass, Object implementation)
    {
        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(url, interfaceClass);
        this.urlInterfaceToImplMap.put(serviceIdentifier, implementation);
    }

    public <T> T getLocalService(String url, Class<T> interfaceClass)
    {
        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(url, interfaceClass);
        return (T) this.urlInterfaceToImplMap.get(serviceIdentifier);
    }

    public Object getSerivceForWebApp(String webapp, Class interfaceClass)
    {
        ServiceIdentifier serviceIdentifier = new ServiceIdentifier(webapp, interfaceClass);
        return this.webAppInterfaceToImplMap.get(serviceIdentifier);
    }

    public void registerWebAppAtUrl(String webapp, String url)
    {
        Iterator<ServiceIdentifier> webappInterfaces = this.webAppInterfaceToImplMap.keySet().iterator();

        while (webappInterfaces.hasNext())
        {
            ServiceIdentifier si = webappInterfaces.next();
            if (si.getServiceLocation().equals(webapp))
            {
                ServiceIdentifier urlInterface = new ServiceIdentifier(url, si.getInterfaceClass());
                this.urlInterfaceToImplMap.put(urlInterface, this.webAppInterfaceToImplMap.get(si));
            }
        }
    }

    public void clearServiceMaps()
    {
        this.urlInterfaceToImplMap.clear();
        this.webAppInterfaceToImplMap.clear();
    }

    public static class ServiceIdentifier
    {
        private final String serviceLocation; // either webapp or url
        private final Class interfaceClass;

        public ServiceIdentifier(String serviceLocation, Class interfaceDefinition)
        {
            this.serviceLocation = serviceLocation;
            this.interfaceClass = interfaceDefinition;
        }

        public String getServiceLocation()
        {
            return this.serviceLocation;
        }

        public Class getInterfaceClass()
        {
            return this.interfaceClass;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (!(o instanceof ServiceIdentifier))
            {
                return false;
            }

            ServiceIdentifier serviceIdentifier = (ServiceIdentifier) o;

            if (!this.interfaceClass.equals(serviceIdentifier.interfaceClass))
            {
                return false;
            }
            return this.serviceLocation.equals(serviceIdentifier.serviceLocation);
        }

        @Override
        public int hashCode()
        {
            int result = this.serviceLocation.hashCode();
            result = 29 * result + this.interfaceClass.hashCode();
            return result;
        }
    }
}

