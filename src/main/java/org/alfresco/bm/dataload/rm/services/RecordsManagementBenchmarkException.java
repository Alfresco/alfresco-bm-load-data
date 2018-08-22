/*
 * #%L
 * Alfresco Benchmark Load Data
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
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
