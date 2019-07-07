package com.gs.jrpip.client;

import com.gs.jrpip.server.StreamBasedInvocator;

public class ResponseMessage
{
    public static final int SERVER_OK = 200;

    private final int transportStatusCode;
    private final byte responseStatusCode;
    private final Object result;
    private final String transportError;

    public static ResponseMessage forTransportErrorCode(int transportStatusCode, String transportError)
    {
        return new ResponseMessage(transportStatusCode, StreamBasedInvocator.FAULT_STATUS, null, transportError);
    }

    public static ResponseMessage forSuccess(byte responseStatusCode, Object result)
    {
        return new ResponseMessage(SERVER_OK, responseStatusCode, result, "");
    }

    private ResponseMessage(int transportStatusCode, byte responseStatusCode, Object result, String transportError)
    {
        this.transportStatusCode = transportStatusCode;
        this.responseStatusCode = responseStatusCode;
        this.result = result;
        this.transportError = transportError;
    }

    public byte getResponseStatusCode()
    {
        return responseStatusCode;
    }

    public String getTransportError()
    {
        return transportError;
    }

    public int getTransportStatusCode()
    {
        return transportStatusCode;
    }

    public Object getResult()
    {
        return result;
    }
}
