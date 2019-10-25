package com.gs.jrpip.server;

import com.gs.jrpip.MethodResolver;
import com.gs.jrpip.util.stream.OutputStreamBuilder;

class ServiceDefinition
{
    private final Object service;
    private Class serviceInterface;
    private final MethodResolver methodResolver;
    private final boolean isVmBound;
    private final OutputStreamBuilder outputStreamBuilder;

    protected ServiceDefinition(
            Object service,
            MethodResolver methodResolver,
            OutputStreamBuilder outputStreamBuilder,
            boolean isVmBound)
    {
        this.service = service;
        this.methodResolver = methodResolver;
        this.outputStreamBuilder = outputStreamBuilder;
        this.isVmBound = isVmBound;
    }

    public Class getServiceInterface()
    {
        return serviceInterface;
    }

    public void setServiceInterface(Class serviceInterface)
    {
        this.serviceInterface = serviceInterface;
    }

    public OutputStreamBuilder getOutputStreamBuilder()
    {
        return this.outputStreamBuilder;
    }

    public Object getService()
    {
        return this.service;
    }

    public MethodResolver getMethodResolver()
    {
        return this.methodResolver;
    }

    public boolean isVmBound()
    {
        return this.isVmBound;
    }
}
