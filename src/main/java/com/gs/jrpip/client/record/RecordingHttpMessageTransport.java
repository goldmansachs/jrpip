package com.gs.jrpip.client.record;

import com.gs.jrpip.client.HttpMessageTransport;
import com.gs.jrpip.client.record.MethodCallStreamResolver;
import com.gs.jrpip.util.stream.CopyOnReadInputStream;
import com.gs.jrpip.util.stream.DedicatedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class RecordingHttpMessageTransport extends HttpMessageTransport
{
    private static final DedicatedOutputStream SINGLE_OUTPUT_STREAM = new DedicatedOutputStream();
    private final MethodCallStreamResolver streamResolver;

    public RecordingHttpMessageTransport(MethodCallStreamResolver streamResolver)
    {
        this.streamResolver = streamResolver;
    }

    public RecordingHttpMessageTransport(String user, String password, MethodCallStreamResolver streamResolver)
    {
        super(user, password);
        this.streamResolver = streamResolver;
    }

    public RecordingHttpMessageTransport(String[] tokenArr, String path, String domain, MethodCallStreamResolver streamResolver)
    {
        super(tokenArr, path, domain);
        this.streamResolver = streamResolver;
    }

    @Override
    protected Object getResult(Method method, Object[] args, InputStream is) throws IOException, ClassNotFoundException
    {
        OutputStream resultStream = this.streamResolver.resolveOutputStream(method, args);
        if (resultStream == null)
        {
            return super.getResult(method, args, is);
        }

        OutputStream outputStream = SINGLE_OUTPUT_STREAM.dedicatedFor(resultStream);
        CopyOnReadInputStream copyOnReadInputStream = this.splitResponseStream(is, outputStream);
        try
        {
            return super.getResult(method, args, copyOnReadInputStream);
        }
        finally
        {
            outputStream.close();
        }
    }

    private CopyOnReadInputStream splitResponseStream(InputStream is, OutputStream outputStream)
    {
        CopyOnReadInputStream copyOnReadInputStream = new CopyOnReadInputStream(is);
        copyOnReadInputStream.startCopyingInto(outputStream);
        return copyOnReadInputStream;
    }

}
