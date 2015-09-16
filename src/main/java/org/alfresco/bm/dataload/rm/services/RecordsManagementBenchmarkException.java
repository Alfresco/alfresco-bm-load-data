package org.alfresco.bm.dataload.rm.services;

import org.alfresco.bm.utils.ParameterCheck;

/**
 * Records Management exception class
 * 
 * @author Frank Becker
 * @since 2.6
 */
public class RecordsManagementBenchmarkException extends Exception
{
    /** Serialization ID */
    private static final long serialVersionUID = -134991898752916891L;

    /**
     * Default constructor for de-/serialization only.
     */
    public RecordsManagementBenchmarkException()
    {
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message
     *            (String) the detail message.
     */
    public RecordsManagementBenchmarkException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause
     *            (Throwable, optional) the cause.
     */
    public RecordsManagementBenchmarkException(Throwable cause)
    {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message
     *            (String) the detail message.
     * @param cause
     *            (Throwable, optional) the cause.
     */
    public RecordsManagementBenchmarkException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified detail message, cause, suppression enabled or disabled, and
     * writable stack trace enabled or disabled.
     *
     * @param message
     *            (String) the detail message.
     * @param cause
     *            (Throwable, optional) the cause.
     * @param enableSuppression
     *            (boolean) indicates whether or not suppression is enabled or disabled
     * @param writableStackTrace
     *            (boolean) indicates whether or not the stack trace should be writable
     */
    public RecordsManagementBenchmarkException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace)
    {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Validates object identifier and throws a records management exception with an object not found message.
     * 
     * @param objectIdentifier
     *            (String, will be validated) object name or identifier
     * 
     * @throws RecordsManagementBenchmarkException
     */
    public static void throwNewObjectNotFoundException(String objectIdentifier)
            throws RecordsManagementBenchmarkException
    {
        ParameterCheck.mandatoryString("objectIdentifier", objectIdentifier);
        throw new RecordsManagementBenchmarkException("Unable to find records management object with identifier '" + objectIdentifier
                + "'");
    }
}
