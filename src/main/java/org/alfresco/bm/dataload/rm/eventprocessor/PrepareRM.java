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
package org.alfresco.bm.dataload.rm.eventprocessor;

import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.AbstractEventProcessor;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

import java.util.ArrayList;
import java.util.List;

/**
 * Event processor that initializes the data such that RM functionality can be used.
 *
 * @author Michael Suzuki
 * @author Derek Hulley
 * @version 2.0
 */
public class PrepareRM extends AbstractEventProcessor
{
    public static final String RM_SITE_DESC = "Records Management Site";
    public static final String RM_SITE_PRESET = "rm-site-dashboard";
    public static final String RM_SITE_ID = "rm";
    public static final String RM_SITE_TITLE = "Records Management";
    public static final String RM_SITE_DOMAIN = "default";
    public static final String RM_SITE_GUID = RM_SITE_ID;
    public static final String RM_SITE_TYPE = "{http://www.alfresco.org/model/recordsmanagement/1.0}rmsite";
    public static final String RM_SITE_VISIBILITY = "PUBLIC";

    public static final String DEFAULT_EVENT_NAME_RM_PREPARED = "rmPrepared";

    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private final boolean enabled;
    private final String username;
    private final String password;
    private String eventNameRMPrepared;

    /**
     * @param userDataService User data collections
     * @param siteDataService collection of site data
     * @param enabled         <tt>true</tt> if RM is enabled
     * @param username        admin username (can be an RM administrator)
     * @param password        admin password
     */
    public PrepareRM(UserDataService userDataService, SiteDataService siteDataService, boolean enabled, String username, String password)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.eventNameRMPrepared = DEFAULT_EVENT_NAME_RM_PREPARED;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RM_PREPARED default} event name when RM has been started
     */
    public void setEventNameRMPrepared(String eventNameRMPrepared)
    {
        this.eventNameRMPrepared = eventNameRMPrepared;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        StringBuilder msg = new StringBuilder("Preparing Records Management: \n");
        List<Event> events = new ArrayList<Event>(10);

        // Do we actually need to do anything
        if (!enabled)
        {
            return new EventResult("Record management data load is disabled.", new Event(eventNameRMPrepared, null));
        }

        UserData rmAdmin = userDataService.findUserByUsername(username);
        if (rmAdmin == null)
        {
            rmAdmin = new UserData();
            rmAdmin.setCreationState(DataCreationState.Created);
            rmAdmin.setDomain(RM_SITE_DOMAIN);
            rmAdmin.setUsername(username);
            rmAdmin.setPassword(password);
            userDataService.createNewUser(rmAdmin);
        }
        else
        {
            // Check for creation
            if (rmAdmin.getCreationState() != DataCreationState.Created)
            {
                userDataService.setUserCreationState(username, DataCreationState.Created);
                msg.append("   Updating user " + username + " state to created.\n");
            }
        }

        // The RM site must exist
        SiteData rmSite = siteDataService.getSite(RM_SITE_ID);
        if (rmSite == null)
        {
            // Create data
            rmSite = new SiteData();
            rmSite.setSiteId(RM_SITE_ID);
            rmSite.setTitle(RM_SITE_TITLE);
            rmSite.setGuid(RM_SITE_GUID);
            rmSite.setDomain(RM_SITE_DOMAIN);
            rmSite.setDescription(RM_SITE_DESC);
            rmSite.setSitePreset(RM_SITE_PRESET);
            rmSite.setVisibility("PUBLIC");
            rmSite.setType(RM_SITE_TYPE);
            rmSite.setCreationState(DataCreationState.Created);
            siteDataService.addSite(rmSite);
            msg.append("   Added RM site '" + RM_SITE_ID + "' as created.\n");
            // Record the administrator
            SiteMemberData rmAdminMember = new SiteMemberData();
            rmAdminMember.setCreationState(DataCreationState.Created);
            rmAdminMember.setRole(RMRole.Administrator.toString());
            rmAdminMember.setSiteId(RM_SITE_ID);
            rmAdminMember.setUsername(username);
            siteDataService.addSiteMember(rmAdminMember);
            msg.append("   Added user '" + username + "' RM administrator.\n");
        }

        // Last event marks us as done
        events.add(new Event(eventNameRMPrepared, msg.toString()));
        // Done
        return new EventResult(msg.toString(), events);
    }
}
