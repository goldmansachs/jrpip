package com.gs.jrpip.server;

import com.gs.jrpip.client.JrpipRuntimeException;

public class SingleServiceConfig
{
    private final Class serviceInterface;
    private final Class serviceClass;
    private Object serviceInstance;
    private final boolean isVmBound;

    public SingleServiceConfig(Class serviceInterface, Class serviceClass, boolean isVmBound)
    {
        this.serviceInterface = serviceInterface;
        this.serviceClass = serviceClass;
        this.isVmBound = isVmBound;
    }

    public SingleServiceConfig(Class serviceInterface, Object serviceInstance, boolean isVmBound)
    {
        this.serviceInterface = serviceInterface;
        this.serviceClass = serviceInstance.getClass();
        this.serviceInstance = serviceInstance;
        this.isVmBound = isVmBound;
    }

    public Class getServiceInterface()
    {
        return serviceInterface;
    }

    public Class getServiceClass()
    {
        return serviceClass;
    }

    public boolean isVmBound()
    {
        return isVmBound;
    }

    public synchronized Object getOrConstructService()
    {
        if (this.serviceInstance == null)
        {
            try
            {
                this.serviceInstance = serviceClass.newInstance();
            }
            catch (Exception e)
            {
                throw new JrpipRuntimeException("Could not instantiate service class", e);
            }
        }
        return this.serviceInstance;
    }
}
