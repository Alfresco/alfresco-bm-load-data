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

import java.io.Serializable;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.utils.ParameterCheck;

/**
 * Record category data to serialize/deserialize to/from MongoDB.
 * 
 * @author Frank Becker
 * 
 * @since 2.6
 */
public class RecordCategoryData implements Serializable
{
    /** serial version UID */
    private static final long serialVersionUID = -8692439057344074644L;

    /** creation state */
    private DataCreationState creationState;

    /** Stores the name of the file plan */
    private String name;

    /** Stores the file plan the record category is stored in */
    private FilePlanData filePlan;

    /**
     * Constructor
     */
    public RecordCategoryData()
    {
        this.creationState = DataCreationState.Unknown;

        // TODO name??

        throw new UnsupportedOperationException();
    }

    /**
     * Returns the name of the record category
     * 
     * @return (String)the name of the record category
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Sets the name of the record category.
     * 
     * @param name
     *            (String) name of the record category (required).
     * 
     * @return this.
     */
    public RecordCategoryData setName(String name)
    {
        ParameterCheck.mandatoryString("name", name);
        this.name = name;
        return this;
    }

    /**
     * Gets the creation state.
     * 
     * @return (DataCreationState) state of the record category in MongoDB.
     */
    public DataCreationState getCreationState()
    {
        return creationState;
    }

    /**
     * Sets the creation state of the record category in MongoDB.
     * 
     * @param creationState
     *            (DataCreationState)state to set.
     * 
     * @return this.
     */
    public RecordCategoryData setCreationState(DataCreationState creationState)
    {
        this.creationState = creationState;
        return this;
    }

    /**
     * Returns the file plan the record category is stored in.
     * 
     * @return
     */
    public FilePlanData getFilePlan()
    {
        return filePlan;
    }

    /**
     * Sets the file plan for the record category.
     * 
     * @param filePla
     *            (FilePlanData, required) file plan to associate with record category.
     * 
     * @return this.
     */
    public RecordCategoryData setFilePlan(FilePlanData filePla)
    {
        ParameterCheck.mandatory("filePlan", filePla);
        this.filePlan = filePla;
        return this;
    }

    @Override
    public String toString()
    {
        return "RecordCategoryData '" + this.name + "' [creationState='" + this.creationState + "'] ";
    }
}
