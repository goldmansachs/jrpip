package com.gs.jrpip;

public class ConstEcho extends EchoImpl
{
    private final String constReturn;

    public ConstEcho(String constReturn)
    {
        this.constReturn = constReturn;
    }

    @Override
    public String echo(String input)
    {
        return constReturn;
    }
}
