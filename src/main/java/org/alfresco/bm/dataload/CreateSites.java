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

import java.util.ArrayList;
import java.util.List;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Generate create site member events for site members in the site members collection that are pending creation.
 */
public class CreateSites extends AbstractEventProcessor
{
    public static final String DEFAULT_EVENT_NAME_SITES_CREATED = "sitesCreated";
    public static final String DEFAULT_EVENT_NAME_CREATE_SITE = "createSite";
    public static final String DEFAULT_EVENT_NAME_CREATE_SITES = "createSites";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final long DEFAULT_SITE_CREATION_DELAY = 100L;
    
    private final SiteDataService siteDataService;

    private String eventNameSitesCreated = DEFAULT_EVENT_NAME_SITES_CREATED;
    private String eventNameCreateSite = DEFAULT_EVENT_NAME_CREATE_SITE;
    private String eventNameCreateSites = DEFAULT_EVENT_NAME_CREATE_SITES;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long siteCreationDelay = DEFAULT_SITE_CREATION_DELAY;

    public CreateSites(SiteDataService siteDataService)
    {
        super();
        this.siteDataService = siteDataService;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITES_CREATED default} event name for completion
     */
    public void setEventNameSitesCreated(String eventNameSitesCreated)
    {
        this.eventNameSitesCreated = eventNameSitesCreated;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_CREATE_SITE default} event name for creating a site
     */
    public void setEventNameCreateSite(String eventNameCreateSite)
    {
        this.eventNameCreateSite = eventNameCreateSite;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_CREATE_SITES default} event name for rescheduling this event
     */
    public void setEventNameCreateSiteMembers(String eventNameCreateSites)
    {
        this.eventNameCreateSites = eventNameCreateSites;
    }

    /**
     * Override the {@link #DEFAULT_BATCH_SIZE default} batch size for this event before it reschedules itself
     */
    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }

    /**
     * Override the {@link #DEFAULT_Site_CREATION_DELAY default} time between creation requests
     */
    public void setSiteCreationDelay(long siteCreationDelay)
    {
        this.siteCreationDelay = siteCreationDelay;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        List<Event> nextEvents = new ArrayList<Event>();
        
        // Schedule events for each site member to be created
        int numSites = 0;

        List<SiteData> pendingSites = siteDataService.getSites(null, DataCreationState.NotScheduled, 0, batchSize);
        if (pendingSites.size() == 0)
        {
            // There is nothing more to do, adding a pause before next event can happen to allow the last of site creation.
            long time = System.currentTimeMillis() + 5000L;
            Event doneEvent = new Event(eventNameSitesCreated, time, null);
            nextEvents.add(doneEvent);
        }
        else
        {
            long nextEventTime = System.currentTimeMillis();
            for (SiteData site : pendingSites)
            {
                nextEventTime += siteCreationDelay;
                // Do we need to schedule it?
                String siteId = site.getSiteId();
                
                String siteManager = null;
                List<SiteMemberData> siteManagers = siteDataService.getSiteMembers(
                        siteId, DataCreationState.NotScheduled,
                        SiteRole.SiteManager.toString(),
                        0, 1);
                if (siteManagers.size() > 0)
                {
                    siteManager = siteManagers.get(0).getUsername();
                }

                // Ignore sites that have not been initialized or created
                if (siteManager == null)
                {
                    // This site will not be creatable
                    siteDataService.setSiteCreationState(siteId, null, DataCreationState.Failed);
                    continue;
                }
                DBObject dataObj = new BasicDBObject()
                    .append(CreateSite.FIELD_SITE_ID, siteId)
                    .append(CreateSite.FIELD_SITE_MANAGER, siteManager);
                Event nextEvent = new Event(eventNameCreateSite, nextEventTime, dataObj);
                nextEvents.add(nextEvent);
                numSites++;
                
                // The site creation is now scheduled
                siteDataService.setSiteCreationState(siteId, null, DataCreationState.Scheduled);
                siteDataService.setSiteMemberCreationState(siteId, siteManager, DataCreationState.Scheduled);
            }

            // Reschedule for the next batch (might be zero next time)
            Event self = new Event(eventNameCreateSites, nextEventTime + siteCreationDelay, null);
            nextEvents.add(self);
        }

        // Return messages + next events
        return new EventResult("Scheduled " + numSites + " site(s) for creation", nextEvents);
    }
}
