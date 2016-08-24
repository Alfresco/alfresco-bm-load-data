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

import java.io.IOException;
import java.util.Collections;

import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.session.SessionService;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.management.CMIS;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.util.FileUtils;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

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
public class CleanSiteFolder extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_FOLDER_CLEANED = "siteFolderCleaned";
    
    private final SessionService sessionService;
    private final FileFolderService fileFolderService;
    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private final String cmisBindingType;
    private final String cmisBindingUrl;
    private final OperationContext cmisCtx;
    private final int deleteFolderPercentage;
    private String eventNameSiteFolderCleaned;

    /**
     * 
     * @param sessionService                    service to close this loader's session
     * @param fileFolderService                 service to access folders
     * @param userDataService                   service to access usernames and passwords
     * @param siteDataService                   service to access site details
     * @param cmisBindingType                   the CMIS <b>browser</b> binding type
     * @param cmisBindingUrl                    the CMIS <b>browser</b> binding URL
     * @param cmisCtx                           the operation context for all calls made by the session.
     * @param deleteFolderPercentage            the percentage of filled folders to delete
     */
    public CleanSiteFolder(
            SessionService sessionService,
            FileFolderService fileFolderService,
            UserDataService userDataService,
            SiteDataService siteDataService,
            String cmisBindingType,
            String cmisBindingUrl,
            OperationContext cmisCtx,
            int deleteFolderPercentage)
    {
        super();
        
        this.sessionService = sessionService;
        this.fileFolderService = fileFolderService;
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        
        this.cmisBindingType = cmisBindingType;
        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisCtx = cmisCtx;
        
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
        boolean deleteFolder =
                    (Math.random() * 100.0 < (double) deleteFolderPercentage) &&
                    folder.getLevel() >= 4;                                         // Don't delete the documentLibrary of sites or above
        
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
        
        try
        {
            if (deleteFolder)
            {
                // Establish the CMIS Session
                super.resumeTimer();
                Session session = CMIS.startSession(username, password, cmisBindingType, cmisBindingUrl, cmisCtx);
                super.suspendTimer();
                
                // Get the folder
                String path = folder.getPath();
                Folder cmisFolder = FileUtils.getFolder(path, session);
                
                // Delete folder
                super.resumeTimer();
                cmisFolder.deleteTree(true, UnfileObject.DELETE, false);
                super.suspendTimer();
            }

            // Build next event
            DBObject eventData = BasicDBObjectBuilder.start()
                    .add(ScheduleSiteLoaders.FIELD_CONTEXT, folder.getContext())
                    .add(ScheduleSiteLoaders.FIELD_PATH, folder.getPath())
                    .get();
            Event nextEvent = new Event(eventNameSiteFolderCleaned, eventData);
            
            DBObject resultData = BasicDBObjectBuilder.start()
                    .add("msg", "Cleaned up folder.")
                    .add("path", folder.getPath())
                    .add("deleted", deleteFolder)
                    .add("username", username)
                    .get();
            return new EventResult(resultData, Collections.singletonList(nextEvent));
        }
        catch (CmisRuntimeException e)
        {
            String error = e.getMessage();
            String stack = ExceptionUtils.getStackTrace(e);
            // Grab the CMIS information
            DBObject data = BasicDBObjectBuilder
                    .start()
                    .append("error", error)
                    .append("binding", cmisBindingUrl)
                    .append("username", username)
                    .append("folder", folder)
                    .append("stack", stack)
                    .push("cmisFault")
                        .append("code", "" + e.getCode())               // BigInteger is not Serializable
                        .append("errorContent", e.getErrorContent())
                    .pop()
                    .get();
            // Build failure result
            return new EventResult(data, false);
        }
    }
}
