/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This file is part of Alfresco
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
 */
package org.alfresco.bm.dataload.rm;

import java.util.Collections;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
/**
 * Event processes which determines if record management is needed 
 * and starts if the loading of data or ends the process. 
 * @author Michael Suzuki
 * @version 1.4
 */
public class StartRecordManagement extends AbstractEventProcessor
{
    public static final String EVENT_NAME_RM_STARTED = "prepareRMAdmin";
    private String eventNameStartRM;
    private boolean rmEnabled;

    /**
     * @param userDataService              User data collections
     * @param username                     String username identifier
     * @param password                     String password user password
     */
    public StartRecordManagement(boolean enabled)
    {
        super();
        this.rmEnabled = enabled;
        this.eventNameStartRM = EVENT_NAME_RM_STARTED;
    }

    /**
     * Override the {@link #EVENT_NAME_RM_SITE_PREPARED default} event name when sites have been created.
     */
    public void setEventNameSitesPrepared(String eventNameStartRM)
    {
        this.eventNameStartRM = eventNameStartRM;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        if(rmEnabled)
        {
            Event nextEvent = new Event(eventNameStartRM, System.currentTimeMillis());
            return new EventResult("Starting record management data load ", nextEvent);
        }
        //Do nothing as rm module is not needed.
        return new EventResult("Record management data load is disabled", Collections.<Event>emptyList());
    }
}
