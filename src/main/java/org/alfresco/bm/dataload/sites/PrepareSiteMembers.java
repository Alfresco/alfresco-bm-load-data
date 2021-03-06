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

import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.AbstractEventProcessor;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

import java.util.Collections;
import java.util.List;

/**
 * Prepares site members for creation by populating the site members collection.
 * <p/>
 * The number of site members to create is driven by the number of sites and the {@link #setUsersPerSite(int) users per site}.
 * maxSites: if -1, uses all created sites in the sites collection maxMembersPerSite: must be greater than 0
 *
 * @author steveglover
 * @author Derek Hulley
 */
public class PrepareSiteMembers extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_MEMBERS_PREPARED = "siteMembersPrepared";
    public static final int DEFAULT_USERS_PER_SITE = 10;

    private UserDataService userDataService;
    private SiteDataService siteDataService;
    private String eventNameSiteMembersPrepared;

    private int usersPerSite;

    /**
     * @param services data collections
     */
    public PrepareSiteMembers(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.eventNameSiteMembersPrepared = EVENT_NAME_SITE_MEMBERS_PREPARED;
        this.usersPerSite = DEFAULT_USERS_PER_SITE;
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_MEMBERS_PREPARED default} event name when site members have been created.
     */
    public void setEventNameSiteMembersPrepared(String eventNameSiteMembersPrepared)
    {
        this.eventNameSiteMembersPrepared = eventNameSiteMembersPrepared;
    }

    /**
     * Override the {@link #DEFAULT_USERS_PER_SITE default} sites per user
     */
    public void setUsersPerSite(int usersPerSite)
    {
        if (usersPerSite < 0)
        {
            throw new IllegalArgumentException("'usersPerSite' must be zero or greater");
        }
        this.usersPerSite = usersPerSite;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        final int sitePageSize = 500;
        int membersCount = 0;

        long numSites = siteDataService.countSites(null, DataCreationState.Created);
        int siteLoops = ((int) (numSites / sitePageSize) + 1);           // The number of times to query for sites

        int userSkip = 0;
        int currentUser = 0;
        final int userPageSize = 100;
        List<UserData> users = userDataService.getUsersByCreationState(DataCreationState.Created, userSkip, userPageSize);
        if (users.size() == 0L)
        {
            EventResult result = new EventResult("There are no users available.", false);
            return result;
        }
        long userCount = userDataService.countUsers(null, DataCreationState.Created);

        for (int siteLoop = 0; siteLoop < siteLoops; siteLoop++)
        {
            // Get the next page of sites
            List<SiteData> sites = siteDataService.getSites(null, DataCreationState.Created, siteLoop * sitePageSize, sitePageSize);
            for (SiteData site : sites)
            {
                String siteId = site.getSiteId();
                // How many users do we have for the site?
                int currentSiteUsersCount = siteDataService.getSiteMembers(siteId, DataCreationState.Created, null, 0, usersPerSite).size();
                int siteUsersToCreate = usersPerSite - currentSiteUsersCount;
                // Keep going while we attempt to find a user to use
                for (int i = 0; i < siteUsersToCreate; i++)
                {
                    // Get some users
                    if (currentUser >= users.size())
                    {
                        // We need to requery
                        // Check if we need to go back to the beginning of the available users
                        userSkip += users.size();
                        if (userSkip >= userCount)
                        {
                            userSkip = 0;
                        }
                        users = userDataService.getUsersByCreationState(DataCreationState.Created, userSkip, userPageSize);
                        if (users.size() == 0L)
                        {
                            EventResult result = new EventResult("There are no users available (any more)", false);
                            return result;
                        }
                        // Go back to first user in the list
                        currentUser = 0;
                    }
                    UserData user = users.get(currentUser);
                    currentUser++;
                    // Check if the user is already a member
                    String username = user.getUsername();
                    SiteMemberData siteMember = siteDataService.getSiteMember(siteId, username);
                    if (siteMember != null)
                    {
                        // The user is already a set to be a site member (we could hit site manager)
                        i--;            // Don't count this one
                        continue;
                    }
                    // Create the membership
                    siteMember = new SiteMemberData();
                    siteMember.setCreationState(DataCreationState.NotScheduled);
                    siteMember.setRole(SiteRole.getRandomRole().toString());
                    siteMember.setSiteId(siteId);
                    siteMember.setUsername(username);
                    siteDataService.addSiteMember(siteMember);
                    membersCount++;
                }
            }
        }

        // We need an event to mark completion
        String msg = "Prepared " + membersCount + " site members";
        Event outputEvent = new Event(eventNameSiteMembersPrepared, null);

        // Create result
        EventResult result = new EventResult(msg, Collections.singletonList(outputEvent));

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }
}
