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

import java.util.ArrayList;
import java.util.Collections;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.http.AuthenticatedHttpEventProcessor;
import org.alfresco.bm.publicapi.requests.CreateSiteRequest;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
/**
 * Event processes which prepares a record management site and stores it 
 * in the mongo collection.
 * 
 * @author Michael Suzuki
 * @version 1.4
 */
public class PrepareRecordManagementSite extends AuthenticatedHttpEventProcessor
{
    private static final String RECORDS_MANAGEMENT_SITE_DESC = "Records Management Site";
    private static final String RM_SITE_PRESET = "rm-site-dashboard";
    private static final String RECORDS_MANAGEMENT_SITE_ID = "rm";
    private static final String RECORDS_MANAGEMENT_SITE_TITLE = "Records Management";
    private static final String RM_SITE_TYPE = "{http://www.alfresco.org/model/recordsmanagement/1.0}rmsite";
    public static final String EVENT_NAME_RM_SITE_PREPARED = "rmSitePrepared";
    public static final String EVENT_NAME_RM_SITE_CREATED = "rmSiteCreated";
    private SiteDataService siteDataService;
    private String eventNameRMSitePrepared;
    private String eventNameRMSiteCreated;

    /**
     * @param siteDataService              site data collections
     */
    public PrepareRecordManagementSite(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            SiteDataService siteDataService)
    {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        this.siteDataService = siteDataService; 
        this.eventNameRMSitePrepared = EVENT_NAME_RM_SITE_PREPARED;
        this.eventNameRMSiteCreated = EVENT_NAME_RM_SITE_CREATED;
    }
    
    /**
     * Override the {@link #EVENT_NAME_RM_SITE_PREPARED default} event name when sites have been created.
     */
    public void setEventNameSitesPrepared(String eventNameSitesPrepared)
    {
        this.eventNameRMSitePrepared = eventNameSitesPrepared;
    }

    public void setEventNameRMSiteCreated(String eventNameRMSiteCreated)
    {
        this.eventNameRMSiteCreated = eventNameRMSiteCreated;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        ArrayList<Event> next = new ArrayList<Event>();
        SiteData recordManagementSite = siteDataService.findSiteBySiteId(RECORDS_MANAGEMENT_SITE_ID);
        if(recordManagementSite == null)
        {
            // Create data
            recordManagementSite = new SiteData();
            recordManagementSite.setSiteId(RECORDS_MANAGEMENT_SITE_ID);
            recordManagementSite.setTitle(RECORDS_MANAGEMENT_SITE_TITLE);
            recordManagementSite.setNetworkId("default");
            recordManagementSite.setDescription(RECORDS_MANAGEMENT_SITE_DESC);
            recordManagementSite.setSitePreset(RM_SITE_PRESET);
            recordManagementSite.setVisibility("PUBLIC");
            recordManagementSite.setType(RM_SITE_TYPE);
            recordManagementSite.setCreated(Boolean.FALSE);
            siteDataService.addSite(recordManagementSite);
        }
        if(!recordManagementSite.isCreated())
        {
            String username = getAuthDetailProvider().getAdminUsername();
            CreateSiteRequest request = new CreateSiteRequest("default", username, recordManagementSite);
            next.add(new Event(eventNameRMSitePrepared, request));
            next.add(new Event(eventNameRMSiteCreated,Collections.EMPTY_LIST));
        }
        else
        {
            
            next.add(new Event(eventNameRMSiteCreated ,Collections.EMPTY_LIST));
        }
        EventResult result = new EventResult("Prepared record managment site", next);
        return result;
    }
    
}
