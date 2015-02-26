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
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.http.AuthenticatedHttpEventProcessor;
import org.alfresco.bm.session.SessionService;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Create files remotely using 'spoofing' via URL
 * <pre>
 *    Alfresco 5.1: http://<host><port>/alfresco/s/api/model/filefolder/load
 * </pre>
 * 
 * @author Derek Hulley
 * @since 2.3
 */
public class SpoofFileLoader extends AuthenticatedHttpEventProcessor
{
    public static final String URL_LOAD_FILES = "/alfresco/service/api/model/filefolder/load";
    public static final String EVENT_NAME_SITE_FILES_SPOOFED = "siteFilesSpoofed";
    
    private final SessionService sessionService;
    private final FileFolderService fileFolderService;
    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private String eventNameSiteFilesSpoofed;
    
    // Spoofing properties
    private final long minFileSize;
    private final long maxFileSize;
    private final boolean forceBinaryStorage;

    /**
     * @param sessionService                    service to close this loader's session
     * @param fileFolderService                 service to access folders
     * @param userDataService                   service to access usernames and passwords
     * @param siteDataService                   service to access site details
     * @param minFileSize                       minimum spoofed file size
     * @param maxFileSize                       maximum spoofed file size
     * @param forceBinaryStorage                <tt>true</tt> to force disk space to be filled
     */
    public SpoofFileLoader(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            SessionService sessionService,
            FileFolderService fileFolderService,
            UserDataService userDataService,
            SiteDataService siteDataService,
            long minFileSize, long maxFileSize, boolean forceBinaryStorage)
    {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        
        this.sessionService = sessionService;
        this.fileFolderService = fileFolderService;
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        
        this.minFileSize = minFileSize;
        this.maxFileSize = maxFileSize;
        this.forceBinaryStorage = forceBinaryStorage;
        
        this.eventNameSiteFilesSpoofed = EVENT_NAME_SITE_FILES_SPOOFED;
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_FILES_SPOOFED default} event name
     */
    public void setEventNameSiteFilesSpoofed(String eventNameSiteFilesSpoofed)
    {
        this.eventNameSiteFilesSpoofed = eventNameSiteFilesSpoofed;
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
        Integer filesToCreate = (Integer) dataObj.get(ScheduleSiteLoaders.FIELD_FILES_TO_CREATE);
        if (context == null || path == null || filesToCreate == null)
        {
            return new EventResult("Requests data not complete for folder loading: " + dataObj, false);
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
        
        try
        {
            return loadFiles(folder, filesToCreate);
        }
        finally
        {
            // End the session
            sessionService.endSession(sessionId);
            // Clean up the lock
            String lockedPath = folder.getPath() + "/locked";
            fileFolderService.deleteFolder(context, lockedPath, false);
        }
    }
    
    private EventResult loadFiles(FolderData folder, int filesToCreate) throws IOException
    {
        UserData user = SiteFolderLoader.getUser(siteDataService, userDataService, folder, logger);
        
        String username = user.getUsername();
        String password = user.getPassword();
        
        String folderPath = folder.getPath();
        DBObject reqObj = BasicDBObjectBuilder.start()
                .append("folderPath", folderPath)
                .append("fileCount", filesToCreate)
                .append("minFileSize", minFileSize)
                .append("maxFileSize", maxFileSize)
                .append("forceBinaryStorage", forceBinaryStorage)
                .get();
        
        HttpEntity jsonEntity = new StringEntity(reqObj.toString(), ContentType.TEXT_PLAIN);

        String url = getFullUrlForPath(URL_LOAD_FILES);
        CloseableHttpResponse httpResponse = null;
        try
        {
            HttpClient httpClient = getHttpProvider().getHttpClient(username, password);
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(jsonEntity);
            // Execute
            super.resumeTimer();
            httpResponse = (CloseableHttpResponse) httpClient.execute(httpPost);
            super.suspendTimer();
            // TODO: Get the actual count of files created
            // Check status
            StatusLine httpStatus = httpResponse.getStatusLine();
            if (httpStatus.getStatusCode() != HttpStatus.SC_OK)
            {
                String msg = String.format(
                        "Remote load failed: (user %s).  REST-call resulted in status:%d with error %s ",
                        username,
                        httpStatus.getStatusCode(),
                        httpStatus.getReasonPhrase());
                return new EventResult(msg, false);
            }
            else
            {
                // TODO: Increment by the returned amount
                fileFolderService.incrementFileCount("", folderPath, filesToCreate);
                // Build next event
                DBObject eventData = BasicDBObjectBuilder.start()
                        .add(ScheduleSiteLoaders.FIELD_CONTEXT, folder.getContext())
                        .add(ScheduleSiteLoaders.FIELD_PATH, folder.getPath())
                        .get();
                Event nextEvent = new Event(eventNameSiteFilesSpoofed, eventData);
                
                DBObject resultData = BasicDBObjectBuilder.start()
                        .add("msg", "Created " + filesToCreate + " spoofed files.")
                        .add("path", folder.getPath())
                        .add("fileCount", filesToCreate)
                        .add("username", username)
                        .get();
                return new EventResult(resultData, Collections.singletonList(nextEvent));
            }
        }
        finally
        {
            if (httpResponse != null)
            {
                try { httpResponse.close(); } catch (Exception e) {}        // Will be closed anyway
            }
        }
    }
}