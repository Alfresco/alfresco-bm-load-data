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
package org.alfresco.bm.dataload;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.event.selector.EventDataObject;
import org.alfresco.bm.event.selector.EventDataObject.STATUS;
import org.alfresco.bm.event.selector.EventProcessorResponse;
import org.alfresco.bm.publicapi.factory.PublicApiFactory;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.springframework.http.HttpStatus;
import org.springframework.social.alfresco.api.Alfresco;
import org.springframework.social.alfresco.api.entities.Role;
import org.springframework.social.alfresco.connect.exception.AlfrescoException;

import com.mongodb.DBObject;

/**
 * Create a site member.
 * 
 * @author steveglover
 * @author Derek Hulley
 */
public class CreateSiteMember extends AbstractEventProcessor
{
    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_USERNAME = "username";
    
    public static final String DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED = "siteMemberCreated";
    
    private String eventNameSiteMemberCreated = DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED;

    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private PublicApiFactory publicApiFactory;

    /**
     * @param userDataService               access to user data
     * @param siteDataService               access to site data
     * @param publicApiFactory              a factory for creating connections to the public api
     */
    public CreateSiteMember(UserDataService userDataService, SiteDataService siteDataService, PublicApiFactory publicApiFactory)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.publicApiFactory = publicApiFactory; 
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED default} event name emitted when a site member is created
     */
    public void setEventNameSiteMemberCreated(String eventNameSiteMemberCreated)
    {
        this.eventNameSiteMemberCreated = eventNameSiteMemberCreated;
    }

    private Alfresco getPublicApi(String userId) throws Exception
    {
        Alfresco alfresco = publicApiFactory.getPublicApi(userId);
        return alfresco;
    }
    
    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        DBObject dataObj = (DBObject) event.getDataObject();
        String siteId = (String) dataObj.get(FIELD_SITE_ID);
        String username = (String) dataObj.get(FIELD_USERNAME);

        // Start by marking it as a failure in order to handle all failure paths
        siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Failed);

        EventProcessorResponse response = null;

        Event nextEvent = null;
        String msg = null;

        // Check the input
        if (siteId == null || username == null)
        {
             return new EventResult("Invalid site member request: " + dataObj, false);
        }
        
        // Get the membership data
        SiteMemberData siteMember = siteDataService.getSiteMember(siteId, username);
        if (siteMember == null)
        {
            return new EventResult("Site membership is missing: " + dataObj, false);
        }
        if (siteMember.getCreationState() != DataCreationState.Scheduled)
        {
            return new EventResult("Site membership has already been processed: " + siteMember, false);
        }
        String roleStr = siteMember.getRole();
        Role role = Role.valueOf(roleStr);
        
        // Get a site manager
        SiteMemberData siteManager = siteDataService.randomSiteMember(
                siteId, DataCreationState.Created, null,
                SiteRole.SiteManager.toString());
        if (siteManager == null)
        {
            return new EventResult("Site does not have a manager: " + dataObj, false);
        }
        String runAs = siteManager.getUsername();
        UserData runAsData = userDataService.findUserByUsername(runAs);
        if (runAsData == null)
        {
            return new EventResult("Site manager does not have a user entry: " + runAs, false);
        }
        String runAsDomain = runAsData.getDomain();

        try
        {
            getPublicApi(runAs).addMember(runAsDomain, siteId, username, role);
            siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Created);
            
            siteMember = siteDataService.getSiteMember(siteId, username);
            EventDataObject responseData = new EventDataObject(STATUS.SUCCESS, siteMember);
            response = new EventProcessorResponse("Added site member", true, responseData);
            
            msg = "Created site member: \n" + "   Response: " + response;
            nextEvent = new Event(eventNameSiteMemberCreated, null);
        }
        catch (AlfrescoException e)
        {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT))
            {
                // Already a member
                siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Created);
                
                siteMember = siteDataService.getSiteMember(siteId, username);
                EventDataObject responseData = new EventDataObject(STATUS.SUCCESS, siteMember);
                response = new EventProcessorResponse("Added site member", true, responseData);
                
                msg = "Site member already exists on server: \n" + "   Response: " + response;
                nextEvent = new Event(eventNameSiteMemberCreated, null);
            }
            else
            {
                // Failure
                return new EventResult("Create site member failed: " + e.getMessage(), false);
            }
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }

        EventResult result = new EventResult(msg, nextEvent);
        return result;
    }
}
