package com.gs.jrpip.server;

public class SingleServiceConfig
{
    private final Class serviceInterface;
    private final Class serviceClass;
    private final boolean isVmBound;

    public SingleServiceConfig(Class serviceInterface, Class serviceClass, boolean isVmBound)
    {
        this.serviceInterface = serviceInterface;
        this.serviceClass = serviceClass;
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
}
