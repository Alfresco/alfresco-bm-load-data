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
package org.alfresco.bm.dataload.sites;

import com.mongodb.DBObject;
import org.alfresco.bm.AbstractRestApiEventProcessor;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.driver.event.selector.EventDataObject;
import org.alfresco.bm.driver.event.selector.EventDataObject.STATUS;
import org.alfresco.bm.driver.event.selector.EventProcessorResponse;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.rest.model.RestSiteMemberModel;
import org.alfresco.utility.constants.UserRole;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.UserModel;
import org.springframework.http.HttpStatus;


/**
 * Create a site member.
 *
 * @author steveglover
 * @author Derek Hulley
 */
public class CreateSiteMember extends AbstractRestApiEventProcessor
{
    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_USERNAME = "username";

    public static final String DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED = "siteMemberCreated";

    private String eventNameSiteMemberCreated = DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED;

    private final UserDataService userDataService;
    private final SiteDataService siteDataService;

    /**
     * @param userDataService access to user data
     * @param siteDataService access to site data
     */
    public CreateSiteMember(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITE_MEMBER_CREATED default} event name emitted when a site member is created
     */
    public void setEventNameSiteMemberCreated(String eventNameSiteMemberCreated)
    {
        this.eventNameSiteMemberCreated = eventNameSiteMemberCreated;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        suspendTimer();
        DBObject dataObj = (DBObject) event.getData();
        String siteId = (String) dataObj.get(FIELD_SITE_ID);
        String username = (String) dataObj.get(FIELD_USERNAME);

        EventProcessorResponse response = null;

        Event nextEvent = null;
        String msg = null;

        // Check the input
        if (siteId == null || username == null)
        {
            dataObj.put("msg", "Invalid site member request.");
            return new EventResult(dataObj, false);
        }

        // Get the membership data
        SiteMemberData siteMember = siteDataService.getSiteMember(siteId, username);
        if (siteMember == null)
        {
            dataObj.put("msg", "Site member is missing: " + username);
            return new EventResult(dataObj, false);
        }
        if (siteMember.getCreationState() != DataCreationState.Scheduled)
        {
            dataObj.put("msg", "Site membership has already been processed: " + siteMember);
            return new EventResult(dataObj, false);
        }

        // Start by marking it as a failure in order to handle all failure paths
        siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Failed);

        String roleStr = siteMember.getRole();

        // Get a site manager
        SiteMemberData siteManager = siteDataService.randomSiteMember(siteId, DataCreationState.Created, null, SiteRole.SiteManager.toString());
        if (siteManager == null)
        {
            dataObj.put("msg", "Site does not have a manager: " + siteId);
            return new EventResult(dataObj, false);
        }
        String runAs = siteManager.getUsername();
        UserData runAsData = userDataService.findUserByUsername(runAs);
        if (runAsData == null)
        {
            dataObj.put("msg", "Site manager does not have a user entry: " + runAs);
            return new EventResult(dataObj, false);
        }

        UserModel runAsUser = new UserModel();
        runAsUser.setUsername(runAsData.getUsername());
        runAsUser.setPassword(runAsData.getPassword());
        //runAsUser.setDomain(runAsDomain);

        SiteModel site = new SiteModel();
        site.setId(siteId);

        UserRole role = UserRole.valueOf(roleStr);

        UserModel newMember = new UserModel();
        newMember.setUsername(username);
        newMember.setUserRole(role);

        resumeTimer();
        RestSiteMemberModel restSiteMemberModel = getRestWrapper().authenticateUser(runAsUser).withCoreAPI().usingSite(site).addPerson(newMember);
        suspendTimer();
        String statusCode = getRestWrapper().getStatusCode();
        if (HttpStatus.CREATED.toString().equals(statusCode))
        {
            siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Created);
            siteMember = siteDataService.getSiteMember(siteId, username);
            EventDataObject responseData = new EventDataObject(STATUS.SUCCESS, siteMember);
            response = new EventProcessorResponse(
                "Added member: " + restSiteMemberModel.getPerson().getId() + " to site: " + restSiteMemberModel.getId() + " as: " + restSiteMemberModel
                    .getRole(), true, responseData);

            msg = "Created site member: \n" + "   Response: " + response;
            nextEvent = new Event(eventNameSiteMemberCreated, null);
            if (logger.isDebugEnabled())
            {
                logger.debug(msg);
            }

            EventResult result = new EventResult(msg, nextEvent);
            return result;
        }
        else if (HttpStatus.CONFLICT.toString().equals(statusCode))
        {
            // Already a member
            siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Created);

            siteMember = siteDataService.getSiteMember(siteId, username);
            EventDataObject responseData = new EventDataObject(STATUS.SUCCESS, siteMember);
            response = new EventProcessorResponse("Added site member", true, responseData);

            msg = "Site member already exists on server: \n" + "   Response: " + response;
            nextEvent = new Event(eventNameSiteMemberCreated, null);
            EventResult result = new EventResult(msg, nextEvent);
            return result;
        }
        else
        {
            // Failure
            final RestErrorModel restErrorModel = getRestWrapper().assertLastError();
            final String detailedError = (restErrorModel != null) ? restErrorModel.toString() : "<nothing>";
            throw new RuntimeException("Create site member as user: " + runAs + " failed (" + statusCode + ") for: " + username + " . Error: " + detailedError);
        }
    }
}
