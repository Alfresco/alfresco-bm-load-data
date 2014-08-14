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

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
/**
 * Event processes which creates and stores the record management 
 * admin user details in benchmark user collection.
 * @author Michael Suzuki
 * @version 1.4
 */
public class PrepareRMAdminUser extends AbstractEventProcessor
{
    private static final String PREPARED_RECORDS_MANAGEMENT_ADMIN_USER_MSG = "Prepared records management admin user";
    public static final String EVENT_NAME_RM_ADMIN_PREPARED = "rmAdminPrepared";
    private String eventNameRMAdminUserPrepared;
    private UserDataService userDataService;
    private final String username;
    private final String password;

    /**
     * @param userDataService              User data collections
     * @param username                     String username identifier
     * @param password                     String password user password
     */
    public PrepareRMAdminUser(UserDataService userDataService, final String username, final String password)
    {
        super();
        this.userDataService = userDataService; 
        this.username = username;
        this.password = password;
        this.eventNameRMAdminUserPrepared = EVENT_NAME_RM_ADMIN_PREPARED;
    }

    /**
     * Override the {@link #EVENT_NAME_RM_SITE_PREPARED default} event name when sites have been created.
     */
    public void setEventNameSitesPrepared(String eventNameSitesPrepared)
    {
        this.eventNameRMAdminUserPrepared = eventNameSitesPrepared;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        //Check if user exists
        UserData user = userDataService.findUserByUsername(username);
        if(user == null)
        {
            //Create user
            user = new UserData();
            user.setUsername(username);
            user.setPassword(password);
            //Persist
            userDataService.createNewUser(user);
        }
        Event nextEvent = new Event(eventNameRMAdminUserPrepared, System.currentTimeMillis());
        EventResult result = new EventResult(PREPARED_RECORDS_MANAGEMENT_ADMIN_USER_MSG, nextEvent);
        return result;
    }
}
