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
public class CreateSiteMembers extends AbstractEventProcessor
{
    public static final String DEFAULT_EVENT_NAME_SITE_MEMBERS_CREATED = "siteMembersCreated";
    public static final String DEFAULT_EVENT_NAME_CREATE_SITE_MEMBER = "createSiteMember";
    public static final String DEFAULT_EVENT_NAME_CREATE_SITE_MEMBERS = "createSiteMembers";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final long DEFAULT_MEMBER_CREATION_DELAY = 100L;
    
    private final SiteDataService siteDataService;

    private String eventNameSiteMembersCreated = DEFAULT_EVENT_NAME_SITE_MEMBERS_CREATED;
    private String eventNameCreateSiteMember = DEFAULT_EVENT_NAME_CREATE_SITE_MEMBER;
    private String eventNameCreateSiteMembers = DEFAULT_EVENT_NAME_CREATE_SITE_MEMBERS;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long memberCreationDelay = DEFAULT_MEMBER_CREATION_DELAY;

    public CreateSiteMembers(SiteDataService siteDataService)
    {
        super();
        this.siteDataService = siteDataService;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITE_MEMBERS_CREATED default} event name for completion
     */
    public void setEventNameSiteMembersCreated(String eventNameSiteMembersCreated)
    {
        this.eventNameSiteMembersCreated = eventNameSiteMembersCreated;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_CREATE_SITE_MEMBER default} event name for creating a site member
     */
    public void setEventNameCreateSiteMember(String eventNameCreateSiteMember)
    {
        this.eventNameCreateSiteMember = eventNameCreateSiteMember;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_CREATE_SITE_MEMBERS default} event name for rescheduling this event
     */
    public void setEventNameCreateSiteMembers(String eventNameCreateSiteMembers)
    {
        this.eventNameCreateSiteMembers = eventNameCreateSiteMembers;
    }

    /**
     * Override the {@link #DEFAULT_BATCH_SIZE default} batch size for this event before it reschedules itself
     */
    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }

    /**
     * Override the {@link #DEFAULT_MEMBER_CREATION_DELAY default} time between membership creatio requests
     */
    public void setMemberCreationDelay(long memberCreationDelay)
    {
        this.memberCreationDelay = memberCreationDelay;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        List<Event> nextEvents = new ArrayList<Event>();
        
        // Schedule events for each site member to be created
        int numSitesMembers = 0;

        List<SiteMemberData> pendingSiteMembers = siteDataService.getSiteMembers(null, DataCreationState.NotScheduled, null, 0, batchSize);
        if (pendingSiteMembers.size() == 0)
        {
            // There is nothing more to do
            Event doneEvent = new Event(eventNameSiteMembersCreated, System.currentTimeMillis(), null);
            nextEvents.add(doneEvent);
        }
        else
        {
            long nextEventTime = System.currentTimeMillis();
            for (SiteMemberData siteMember : pendingSiteMembers)
            {
                nextEventTime += memberCreationDelay;
                // Do we need to schedule it?
                String siteId = siteMember.getSiteId();
                SiteData site = siteDataService.getSite(siteId);
                boolean siteCreated = site.getCreationState() == DataCreationState.Created;
                
                String siteManager = null;
                List<SiteMemberData> siteManagers = siteDataService.getSiteMembers(
                        siteId, DataCreationState.Created,
                        SiteRole.SiteManager.toString(),
                        0, 1);
                if (siteManagers.size() > 0)
                {
                    siteManager = siteManagers.get(0).getUsername();
                }

                // Ignore sites that have not been initialized or created
                if (site == null || siteManager == null || !siteCreated)
                {
                    // Ignore it
                    continue;
                }
                
                String username = siteMember.getUsername();

                DBObject dataObj = new BasicDBObject()
                    .append(CreateSiteMember.FIELD_SITE_ID, siteId)
                    .append(CreateSiteMember.FIELD_USERNAME, username);
                Event nextEvent = new Event(eventNameCreateSiteMember, nextEventTime, dataObj);
                nextEvents.add(nextEvent);
                numSitesMembers++;
                
                // The member creation is now scheduled
                siteDataService.setSiteMemberCreationState(siteId, username, DataCreationState.Scheduled);
            }

            // Reschedule for the next batch (might be zero next time)
            Event self = new Event(eventNameCreateSiteMembers, nextEventTime + memberCreationDelay, null);
            nextEvents.add(self);
        }

        // Return messages + next events
        return new EventResult("Scheduled " + numSitesMembers + " site member(s) for creation", nextEvents);
    }
}
