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
import org.alfresco.bm.publicapi.factory.PublicApiFactory;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.springframework.social.alfresco.api.Alfresco;
import org.springframework.social.alfresco.api.entities.LegacySite;
import org.springframework.social.alfresco.api.entities.Site.Visibility;

import com.mongodb.DBObject;

/**
 * Create a functional site in Alfresco.
 * 
 * @author Derek Hulley
 * @since 2.0
 */
public class CreateSite extends AbstractEventProcessor
{
    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_SITE_MANAGER = "siteManager";
    
    public static final String DEFAULT_EVENT_NAME_SITE_CREATED = "siteCreated";

    private SiteDataService siteDataService;
    private PublicApiFactory publicApiFactory;
    private String eventNameSiteCreated = DEFAULT_EVENT_NAME_SITE_CREATED;

    public CreateSite(SiteDataService siteDataService, PublicApiFactory publicApiFactory)
    {
        super();
        this.siteDataService = siteDataService;
        this.publicApiFactory = publicApiFactory; 
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITE_CREATED default} event name when the site is created
     */
    public void setEventNameSiteCreated(String eventNameSiteCreated)
    {
        this.eventNameSiteCreated = eventNameSiteCreated;
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
        String manager = (String) dataObj.get(FIELD_SITE_MANAGER);
        
        if (siteId == null || manager == null)
        {
            return new EventResult("Requests data not complete for site creation: " + dataObj, false);
        }
        
        SiteData site = siteDataService.getSite(siteId);
        if (site == null)
        {
            return new EventResult("Site has been removed: " + siteId, false);
        }
        if (site.getCreationState() != DataCreationState.Scheduled)
        {
            return new EventResult("Site state has changed: " + site, false);
        }

        // Start by marking them as failures in order to handle all eventualities
        siteDataService.setSiteCreationState(siteId, null, DataCreationState.Failed);
        SiteMemberData siteManager = new SiteMemberData();
        siteManager.setRole(SiteRole.SiteManager.toString());
        siteManager.setSiteId(site.getSiteId());
        siteManager.setUsername(manager);
        siteDataService.addSiteMember(siteManager);

        String msg = null;
        try
        {
            Alfresco publicApi = getPublicApi(manager);
            LegacySite ret = publicApi.createSite(
                    site.getDomain(), siteId, site.getSitePreset(), site.getTitle(),
                    site.getDescription(), Visibility.valueOf(site.getVisibility().toString()));
            
            // TODO use ret

            // Create site has succeeded.  Mark the site.
            String guid = ret.getNode();
            siteDataService.setSiteCreationState(siteId, guid, DataCreationState.Created);
            siteDataService.setSiteMemberCreationState(siteId, manager, DataCreationState.Created);

            msg = "Created site: " + siteId;
            event = new Event(eventNameSiteCreated, null);
        }
        catch (Exception e)
        {
            // The creation failed
            String errMsg = e.getMessage();
            if (errMsg.indexOf("error.duplicateShortName") != -1)
            {
                return new EventResult("Site exists: " + siteId, false);
            }
            else
            {
                throw e;
            }
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }

        EventResult result = new EventResult(msg, event);
        return result;
    }
}
