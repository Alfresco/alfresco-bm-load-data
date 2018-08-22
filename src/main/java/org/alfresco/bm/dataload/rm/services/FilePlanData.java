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

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.utils.ParameterCheck;

import java.io.Serializable;

/**
 * File plan data to serialize / deserialize to/from MongoDB.
 *
 * @author Frank Becker
 * @since 2.6
 */
public class FilePlanData implements Serializable
{
    /**
     * serial version UID
     */
    private static final long serialVersionUID = -4335517045934714692L;

    /**
     * default type of file plan
     */
    private static final String DEFAULT_FILEPLAN_TYPE = "rma:filePlan";

    /**
     * creation state
     */
    private DataCreationState creationState;

    /**
     * Stores the name of the file plan
     */
    private String name;

    /**
     * file plan type (must be rma:filePlan or a sub-type)
     */
    private String type;

    /**
     * Constructor
     */
    public FilePlanData()
    {
        this.creationState = DataCreationState.Unknown;
        this.type = DEFAULT_FILEPLAN_TYPE;

        // TODO name?? 

        throw new UnsupportedOperationException();
    }

    /**
     * Returns the name of the file plan
     *
     * @return (String)the name of the file plan
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Sets the name of the file plan.
     *
     * @param name (String) name of the file plan (required).
     * @return this.
     */
    public FilePlanData setName(String name)
    {
        ParameterCheck.mandatoryString("name", name);
        this.name = name;
        return this;
    }

    /**
     * Gets the creation state.
     *
     * @return (DataCreationState) state of file plan in MongoDB.
     */
    public DataCreationState getCreationState()
    {
        return creationState;
    }

    /**
     * Sets the creation state of the file plan in MongoDB.
     *
     * @param creationState (DataCreationState)state to set.
     * @return this.
     */
    public FilePlanData setCreationState(DataCreationState creationState)
    {
        this.creationState = creationState;
        return this;
    }

    /**
     * Returns the type of the plan - always "rma:filePlan" or a sub-type.
     *
     * @return (String) the type of the plan - always "rma:filePlan" or a sub-type.
     */
    public String getPlanType()
    {
        return this.type;
    }

    /**
     * Sets the file plan type - always "rma:filePlan" or a sub-type.
     *
     * @param value (String) value to set.
     * @return this
     */
    public FilePlanData setPlanType(String value)
    {
        ParameterCheck.mandatoryString("value", value);
        this.type = value;
        return this;
    }

    @Override
    public String toString()
    {
        return "FilePlanData '" + this.name + "' [creationState='" + this.creationState + "', type='" + this.type + "'] ";
    }
}
