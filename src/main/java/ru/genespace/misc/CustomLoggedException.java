package ru.genespace.misc;

import ru.biosoft.exception.ExceptionDescriptor;
import ru.biosoft.exception.LoggedException;

public class CustomLoggedException extends LoggedException
{
    private static final String KEY_MESSAGE = "message";
    public static final ExceptionDescriptor ED_CUSTOM = new ExceptionDescriptor( "Custom", LoggingLevel.Summary,
            "$message$");

    public CustomLoggedException(Throwable t)
    {
        this(t, t.getMessage());
    }

    public CustomLoggedException(String message)
    {
        this( null, message );
    }

    public CustomLoggedException(Throwable t, String message)
    {
        super(t, ED_CUSTOM);
        properties.put( KEY_MESSAGE, message );
    }
}