package com.gs.jrpip.client;

public interface MessageTransportData
{
    public boolean isSameEndPoint(MessageTransportData other);

    public int endPointHashCode();

    public long getProxyId();

    public Object createThankYouKey();
}
