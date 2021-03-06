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
package org.alfresco.bm.dataload.files;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.alfresco.bm.AbstractRestApiEventProcessor;
import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.common.session.SessionService;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.rest.core.RestWrapper;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.utility.model.UserModel;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Collections;

/**
 * Clean up a loaded folder.
 * <p>
 * Folders that have files but no subfolders can be deleted.  Specify a percentage of folders that
 * get deleted in order to simulate a proportioanl delete load.
 * <p>
 * The test-side lock is released for the folder in any event.
 *
 * @author Derek Hulley
 * @since 2.4.1
 */
public class CleanSiteFolder extends AbstractRestApiEventProcessor
{
    public static final String EVENT_NAME_SITE_FOLDER_CLEANED = "siteFolderCleaned";

    private final SessionService sessionService;
    private final FileFolderService fileFolderService;
    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private final int deleteFolderPercentage;
    private String eventNameSiteFolderCleaned;

    /**
     * @param sessionService         service to close this loader's session
     * @param fileFolderService      service to access folders
     * @param userDataService        service to access usernames and passwords
     * @param siteDataService        service to access site details
     * @param deleteFolderPercentage the percentage of filled folders to delete
     */
    public CleanSiteFolder(SessionService sessionService, FileFolderService fileFolderService, UserDataService userDataService, SiteDataService siteDataService,
        int deleteFolderPercentage)
    {
        super();

        this.sessionService = sessionService;
        this.fileFolderService = fileFolderService;
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;

        this.deleteFolderPercentage = deleteFolderPercentage;

        this.eventNameSiteFolderCleaned = EVENT_NAME_SITE_FOLDER_CLEANED;
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_FOLDER_CLEANED default} event name
     */
    public void setEventNameSiteFolderCleaned(String eventNameSiteFolderCleaned)
    {
        this.eventNameSiteFolderCleaned = eventNameSiteFolderCleaned;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        super.suspendTimer();

        DBObject dataObj = (DBObject) event.getData();
        if (dataObj == null)
        {
            throw new IllegalStateException("This processor requires data with field " + ScheduleSiteLoaders.FIELD_PATH);
        }
        String context = (String) dataObj.get(ScheduleSiteLoaders.FIELD_CONTEXT);
        String path = (String) dataObj.get(ScheduleSiteLoaders.FIELD_PATH);
        if (context == null || path == null)
        {
            return new EventResult("Request data not complete for folder loading: " + dataObj, false);
        }
        // Get the folder
        FolderData folder = fileFolderService.getFolder(context, path);
        if (folder == null)
        {
            throw new IllegalStateException("No such folder recorded: " + dataObj);
        }
        // Get the session
        String sessionId = event.getSessionId();
        if (sessionId == null)
        {
            return new EventResult("Load scheduling should create a session for each loader.", false);
        }

        // Determine if we need to delete the folder
        boolean deleteFolder = (Math.random() * 100.0 < (double) deleteFolderPercentage)
            && folder.getLevel() >= 4;                                         // Don't delete the documentLibrary of sites or above

        try
        {
            return deleteFolder(folder, deleteFolder);
        }
        finally
        {
            if (deleteFolder)
            {
                // Clean up the folder if we deleted it
                fileFolderService.deleteFolder(context, path, true);
            }
            else
            {
                // Clean up the lock
                String lockedPath = folder.getPath() + "/locked";
                fileFolderService.deleteFolder(context, lockedPath, false);
            }
            // End the session
            sessionService.endSession(sessionId);
        }
    }

    private EventResult deleteFolder(FolderData folder, boolean deleteFolder) throws IOException
    {
        UserData user = SiteFolderLoader.getUser(siteDataService, userDataService, folder, logger);
        String username = user.getUsername();
        String password = user.getPassword();

        if (deleteFolder)
        {
            //we need a user
            UserModel userModel = new UserModel();
            userModel.setUsername(username);
            userModel.setPassword(password);

            try
            {
                RestWrapper restWrapper = getRestWrapper();
                resumeTimer();
                restWrapper.authenticateUser(userModel).withCoreAPI().usingNode().deleteNode(folder.getId());
                suspendTimer();

                if (!HttpStatus.NO_CONTENT.toString().equalsIgnoreCase(restWrapper.getStatusCode()))
                {
                    // for some reason we couldn't delete it
                    DBObject data = BasicDBObjectBuilder.start().append("statusCode", restWrapper.getStatusCode())
                        .append("error", getRestCallErrorMessage(restWrapper)).append("username", username).append("folderID", folder.getId()).get();
                    return new EventResult(data, false);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to delete folder: " + folder.getId() + " . Exception: " + e.getMessage(), e);
            }
        }

        // Build next event
        DBObject eventData = BasicDBObjectBuilder.start().add(ScheduleSiteLoaders.FIELD_CONTEXT, folder.getContext())
            .add(ScheduleSiteLoaders.FIELD_PATH, folder.getPath()).get();
        Event nextEvent = new Event(eventNameSiteFolderCleaned, eventData);

        DBObject resultData = BasicDBObjectBuilder.start().add("msg", "Cleaned up folder.").add("path", folder.getPath()).add("deleted", deleteFolder)
            .add("username", username).get();
        return new EventResult(resultData, Collections.singletonList(nextEvent));

    }

    private String getRestCallErrorMessage(RestWrapper restWrapper)
    {
        RestErrorModel restErrorModel = restWrapper.assertLastError();
        return restErrorModel != null ? restErrorModel.toString() : "<nothing>";
    }
}
