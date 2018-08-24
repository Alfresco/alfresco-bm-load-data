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
package org.alfresco.bm.dataload;

import com.mongodb.DBObject;
import org.alfresco.bm.AbstractRestApiEventProcessor;
import org.alfresco.bm.RestApiUtils;
import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.rest.model.RestSiteModel;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.UserModel;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;

/**
 * Create a functional site in Alfresco.
 *
 * @author Derek Hulley
 * @since 2.0
 */
public class CreateSite extends AbstractRestApiEventProcessor
{
    public static final String PATH_SNIPPET_SITES = "Sites";
    public static final String PATH_SNIPPET_DOCLIB = "documentLibrary";
    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_SITE_MANAGER = "siteManager";
    public static final String DEFAULT_EVENT_NAME_SITE_CREATED = "siteCreated";

    private final SiteDataService siteDataService;
    private final FileFolderService fileFolderService;
    private String eventNameSiteCreated = DEFAULT_EVENT_NAME_SITE_CREATED;

    public CreateSite(SiteDataService siteDataService, FileFolderService fileFolderService)
    {
        super();
        this.fileFolderService = fileFolderService;
        this.siteDataService = siteDataService;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_SITE_CREATED default} event name when the site is created
     */
    public void setEventNameSiteCreated(String eventNameSiteCreated)
    {
        this.eventNameSiteCreated = eventNameSiteCreated;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        suspendTimer();
        DBObject dataObj = (DBObject) event.getData();
        String siteId = (String) dataObj.get(FIELD_SITE_ID);
        String siteManager = (String) dataObj.get(FIELD_SITE_MANAGER);

        if (siteId == null || siteManager == null)
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
        siteDataService.setSiteMemberCreationState(siteId, siteManager, DataCreationState.Failed);

        SiteModel siteModel = new SiteModel();
        siteModel.setId(siteId);
        siteModel.setTitle(siteId);
        siteModel.setVisibility(RestApiUtils.getVisibility(site));

        UserModel userModel = new UserModel();
        userModel.setUsername(siteManager);
        userModel.setPassword(siteManager);

        resumeTimer();
        RestSiteModel createdSite = getRestWrapper().authenticateUser(userModel).withCoreAPI().usingSite(siteModel).createSite();
        suspendTimer();

        if (createdSite == null)
        {
            throw new RuntimeException("Could not create site:" + siteId + " .");
        }
        final String statusCode = getRestWrapper().getStatusCode();
        if (HttpStatus.CREATED.toString().equals(statusCode))
        {
            // Create site has succeeded.  Mark the site.
            String guid = createdSite.getGuid();
            siteDataService.setSiteCreationState(siteId, guid, DataCreationState.Created);
            siteDataService.setSiteMemberCreationState(siteId, siteManager, DataCreationState.Created);

            // Create a folder reference for the document library
            FolderData docLib = new FolderData(guid,                                   // already unique
                "", "/" + PATH_SNIPPET_SITES + "/" + siteId + "/" + PATH_SNIPPET_DOCLIB, 0L, 0L);
            fileFolderService.createNewFolder(docLib);

            String msg = "Created site: " + createdSite.getId() + " guid: " + guid + " SiteManager: " + siteManager;
            event = new Event(eventNameSiteCreated, null);

            if (logger.isDebugEnabled())
            {
                logger.debug(msg);
            }

            EventResult result = new EventResult(msg, event);
            return result;
        }
        else if (HttpStatus.CONFLICT.toString().equals(statusCode))
        {
            return new EventResult("Site exists: " + siteId, false);
        }
        else
        {
            // failed to create the site. failed status already set above
            final RestErrorModel restErrorModel = getRestWrapper().assertLastError();
            final String detailedError = (restErrorModel != null) ? restErrorModel.toString() : "<nothing>";
            throw new RuntimeException(
                "Could not create site:" + siteId + " . " + "Return code was: " + statusCode + " . " + "Last error message:" + System.lineSeparator()
                    + detailedError);
        }
    }

}
