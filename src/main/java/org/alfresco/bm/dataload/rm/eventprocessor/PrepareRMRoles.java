/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
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
package org.alfresco.bm.dataload.rm.eventprocessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Prepares record management site users with roles relating to record management.
 * The possible roles that can be assigned to an existing user collection:
 * <ul>
 *     <li>Records Management Administrator</li>
 *     <li>Records Management Power User</li>
 *     <li>Records Management Records Manager</li>
 *     <li>Records Management Security Officer</li>
 *     <li>Records Management User</li>
 * </ul>
 * @author Michael Suzuki
 * @author Derek Hulley
 * @version 1.4
 */
public class PrepareRMRoles extends AbstractEventProcessor
{
    public static final String DEFAULT_EVENT_NAME_RM_ASSIGN_ROLE = "rmAssignRole";
    public static final String DEFAULT_EVENT_NAME_RM_ROLES_PREPARED = "rmRolesPrepared";
    public static final int DEFAULT_USER_COUNT = 50;
    public static final long DEFAULT_ASSIGNMENT_DELAY = 100L;
            
    private UserDataService userDataService;
    private SiteDataService siteDataService;
    private String eventNameRMAssignRole;
    private String eventNameRMRolesPrepared;
    private int userCount;
    private long assignmentDelay;

    /**
     * @param services              data collections
     */
    public PrepareRMRoles(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.eventNameRMAssignRole = DEFAULT_EVENT_NAME_RM_ASSIGN_ROLE;
        this.eventNameRMRolesPrepared = DEFAULT_EVENT_NAME_RM_ROLES_PREPARED;
        this.userCount = DEFAULT_USER_COUNT;
        this.assignmentDelay = DEFAULT_ASSIGNMENT_DELAY;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RM_ASSIGN_ROLE default} event name to assign RM roles
     */
    public void setEventNameRMAssignRole(String eventNameRMAssignRole)
    {
        this.eventNameRMAssignRole = eventNameRMAssignRole;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RM_ROLES_PREPARED default} event name when rm roles have been prepared
     */
    public void setEventNameRMRolesPrepared(String eventNameRMRolesPrepared)
    {
        this.eventNameRMRolesPrepared = eventNameRMRolesPrepared;
    }

    /**
     * Override the {@link #DEFAULT_USER_COUNT default} number of RM users
     */
    public void setUserCount(int userCount)
    {
        this.userCount = userCount;
    }

    /**
     * Override the {@link #DEFAULT_ASSIGNMENT_DELAY default} time between RM role assignment events
     */
    public void setAssignmentDelay(long assignmentDelay)
    {
        this.assignmentDelay = assignmentDelay;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        List<Event> nextEvents = new ArrayList<Event>();
        
        // Find RM site
        SiteData rmSite = siteDataService.getSite(PrepareRM.RM_SITE_ID);
        if (rmSite == null)
        {
            // There is nothing more to do
            return new EventResult("There is no RM site, so no roles need preparing.", new Event(eventNameRMRolesPrepared, null));
        }
        String rmSiteId = rmSite.getSiteId();
        
        // Check how many users are already members
        int rmSiteMemberCount = (int) siteDataService.countSiteMembers(PrepareRM.RM_SITE_ID, DataCreationState.Created);
        int toSchedule = userCount - rmSiteMemberCount;
        int scheduled = 0;
        
        List<UserData> users = userDataService.getUsersByCreationState(DataCreationState.Created, 0, userCount);
        Iterator<UserData> usersIterator = users.iterator();
        long nextEventTime = System.currentTimeMillis();
        while (scheduled < toSchedule && usersIterator.hasNext())
        {
            UserData user = usersIterator.next();
            
            nextEventTime += assignmentDelay;
            
            String username = user.getUsername();
            SiteMemberData siteMember = siteDataService.getSiteMember(rmSiteId, username);
            if (siteMember != null)
            {
                // The site membership already exists
                continue;
            }
            // Persist user with a RM role - we go directly to the scheduled state
            siteMember = new SiteMemberData();
            siteMember.setCreationState(DataCreationState.Scheduled);
            siteMember.setUsername(username);
            siteMember.setSiteId(rmSiteId);
            siteMember.setRole(RMRole.getRandomRole().toString());
            siteDataService.addSiteMember(siteMember);
            
            DBObject data = new BasicDBObject()
                .append(AssignRMRole.FIELD_ROLE, siteMember.getRole())
                .append(AssignRMRole.FIELD_USERNAME, username);
            Event schedule = new Event(eventNameRMAssignRole, nextEventTime, data);
            nextEvents.add(schedule);
            
            scheduled++;
        }
        
        String msg = "Scheduled " + nextEvents.size() + " RM site member assignments";
        nextEvents.add(new Event(eventNameRMRolesPrepared, nextEventTime + assignmentDelay));
        // Return messages + next events
        EventResult result = new EventResult(msg, nextEvents, true);

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }
}
