package com.gs.jrpip.util;

import java.io.IOException;

public class BlockCorruptException extends IOException
{
    public BlockCorruptException()
    {
    }

    public BlockCorruptException(String message)
    {
        super(message);
    }

    public BlockCorruptException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public BlockCorruptException(Throwable cause)
    {
        super(cause);
    }
}
