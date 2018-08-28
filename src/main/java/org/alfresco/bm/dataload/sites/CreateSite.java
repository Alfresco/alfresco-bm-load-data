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
import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.dataprep.SiteService;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.rest.model.RestSiteContainerModelsCollection;
import org.alfresco.rest.model.RestSiteModel;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.UserModel;
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
        siteModel.setVisibility(SiteService.Visibility.valueOf(site.getVisibility()));

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
            RestSiteContainerModelsCollection siteContainers = getRestWrapper().authenticateUser(userModel).withCoreAPI().usingSite(createdSite)
                .getSiteContainers();
            //this should always succeed...
            String statusCodeForGetDocLib = getRestWrapper().getStatusCode();
            if (isOKStatus(statusCodeForGetDocLib) && hasValidDocLibNodeRef(siteContainers))
            {
                String docLibFolderNodeRef = siteContainers.getEntries().get(0).onModel().getId();
                logger
                    .debug(statusCodeForGetDocLib + " Successfully got " + PATH_SNIPPET_DOCLIB + " Folder id: " + docLibFolderNodeRef + " for site: " + siteId);
                FolderData docLib = new FolderData(docLibFolderNodeRef,                                   // already unique
                    "", "/" + PATH_SNIPPET_SITES + "/" + siteId + "/" + PATH_SNIPPET_DOCLIB, 0L, 0L);
                fileFolderService.createNewFolder(docLib);
            }
            else
            {
                // but just in case it did not succeed (ACS may have run out of memory or something...)
                logger.warn("Failed to get a valid nodeRef id for the " + PATH_SNIPPET_DOCLIB + " node of the newly created site: " + siteId + " . Status code "
                    + statusCodeForGetDocLib);
                // not sure how this will impact the rest of the logic in this BM driver as we needed that folder id to create file and folders in it later
            }
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

    private boolean isOKStatus(String statusCodeForGetDocLib)
    {
        return HttpStatus.OK.toString().equalsIgnoreCase(statusCodeForGetDocLib);
    }

    private boolean hasValidDocLibNodeRef(RestSiteContainerModelsCollection siteContainers)
    {
        return siteContainers != null && siteContainers.getEntries() != null && siteContainers.getEntries().size() > 0
            && siteContainers.getEntries().get(0).onModel().getId() != null && !siteContainers.getEntries().get(0).onModel().getId().isEmpty();
    }

}
