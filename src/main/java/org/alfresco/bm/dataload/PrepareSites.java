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

import java.util.Collections;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.site.SiteVisibility;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares sites for creation by populating the sites collection.
 * <p/>
 * The number of sites is driven by: {@link #setSitesPerDomain(int)}
 * 
 * @author Derek Hulley
 * @since 2.0
 */
public class PrepareSites extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITES_PREPARED = "sitesPrepared";
    public static final int DEFAULT_SITES_COUNT = 100;

    private UserDataService userDataService;
    private SiteDataService siteDataService;
    private String eventNameSitesPrepared;
    private int sitesCount;
    private String siteFormat;

    /**
     * @param services              data collections
     */
    public PrepareSites(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.eventNameSitesPrepared = EVENT_NAME_SITES_PREPARED;
        this.sitesCount = DEFAULT_SITES_COUNT;
        this.setSiteFormat("Site.%s.%05d");
    }

    /**
     * The number of sites that need to be created
     */
    public void setSitesCount(int sitesCount)
    {
        this.sitesCount = sitesCount;
    }

    /**
     * Override the {@link #EVENT_NAME_SITES_PREPARED default} event name when sites have been created.
     */
    public void setEventNameSitesPrepared(String eventNameSitesPrepared)
    {
        this.eventNameSitesPrepared = eventNameSitesPrepared;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        int preparedCount = 0;
        int siteNumber = -1;
        int validSites = 0;
        while (validSites < sitesCount)
        {
            // Start with site number 0
            siteNumber++;
            
            // First choose a random user to be the creator / manager for the site
            UserData user = userDataService.getRandomUser();
            if (user == null)
            {
                return new EventResult("No random user found to be site manager.", false);
            }
            String username = user.getUsername();
            String domain = user.getDomain();
            
            String siteId = String.format(getSiteFormat(), domain, siteNumber);
            SiteData site = siteDataService.getSite(siteId);
            if (site != null)
            {
                // Site already exists.  Check that it is valid.
                DataCreationState siteState = site.getCreationState();
                if (siteState != DataCreationState.Failed)
                {
                    // We found a site that will be scheduled, created, etc.
                    // It is a valid site and we count it
                    validSites++;
                }
                // Move onto a new site number (i.e. a new site name) and try again
                continue;
            }

            // Create data
            final SiteData newSite = new SiteData();
            newSite.setDescription("");
            newSite.setSiteId(siteId);
            newSite.setSitePreset("preset");
            newSite.setTitle(siteId);
            newSite.setVisibility(SiteVisibility.getRandomVisibility());
            newSite.setType("{http://www.alfresco.org/model/site/1.0}site");
            newSite.setDomain(domain);
            newSite.setCreationState(DataCreationState.NotScheduled);

            // Persist
            siteDataService.addSite(newSite);
            preparedCount++;
            validSites++;
            
            // Record the user as the site manager
            final SiteMemberData siteMember = new SiteMemberData();
            siteMember.setUsername(username);
            siteMember.setSiteId(siteId);
            siteMember.setRole(SiteRole.SiteManager.toString());
            siteMember.setCreationState(DataCreationState.NotScheduled);
            siteDataService.addSiteMember(siteMember);
        }

        // We need an event to mark completion
        Event outputEvent = new Event(eventNameSitesPrepared, null);

        // Create result
        String msg = "Prepared " + preparedCount + " sites to reach a count of " + validSites + " valid sites.";
        EventResult result = new EventResult(msg, Collections.singletonList(outputEvent));

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }

    public String getSiteFormat()
    {
        return siteFormat;
    }

    public void setSiteFormat(String siteFormat)
    {
        this.siteFormat = siteFormat;
    }
}
