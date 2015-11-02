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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.dataload.rm.eventprocessor.FileRMFile;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.alfresco.bm.session.SessionService;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.management.CMIS;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.util.FileUtils;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Schedule the {@link #EVENT_NAME_LOAD_FOLDERS folder} and {@link #EVENT_NAME_LOAD_FILES file} loaders and
 * {@link #EVENT_NAME_SCHEDULE_LOADERS reschedule self} until there are enough folders and files.
 * <p/>
 * Each site has one folder, the <i>Document Library</i>, that acts as the root of each tree. Each <i>Document
 * Library</i> is at <a href="http://en.wikipedia.org/wiki/Tree_%28data_structure%29">level one</a> (depth zero) in the
 * calculations.
 * <p/>
 * Every folder receives the same number of files.<br/>
 * To calculate the number of folders and files:
 * 
 * <pre>
 *     sites:           the total number of sites
 *     subfolders:      the number of subfolders in each folder (except the last level)
 *     filesPerFolder:  the number of files added to each folder
 *     maxDepth:        the <a href="http://en.wikipedia.org/wiki/Tree_%28data_structure%29">depth</a> of the last level of folders
 *                      of the site document libraries.
 *     
 *     folders = (sites)*(subfolders^0 + subfolders^1 + subfolders^2 + ... + subfolders^(maxDepth-1))
 *             = (sites)*(    1        +  subfolders  + subfolders^2 + ... + subfolders^(maxDepth-1))
 *     files   = (folders)*(filesPerFolder)
 * </pre>
 * 
 * Using the test defaults:
 * <ul>
 * <li>sites: 100</li>
 * <li>subfolders: 5</li>
 * <li>filesPerFolder: 100</li>
 * <li>maxDepth: 3</li>
 * </ul>
 * 
 * <pre>
 *     folders = (100)*( 1 + 5 + 25 ) =   3,100
 *     files   = 3100*100             = 310,000
 * </pre>
 * 
 * TODO: Put on Wiki
 * 
 * @author Derek Hulley
 * @since 2.0
 */
public class SiteFolderLoader extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_FOLDER_LOADED = "siteFolderLoaded";
    public static final double DEFAULT_FILE_RATIO = 0.1;
    public static final long DEFAULT_FILING_DELAY = 100L;

    private final SessionService sessionService;
    private final FileFolderService fileFolderService;
    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private final TestFileService testFileService;
    private final String cmisBindingUrl;
    private final OperationContext cmisCtx;
    private String eventNameSiteFolderLoaded;
    private boolean rmEnabled;
    private double rmFileRatio;
    private long rmFileDelay;

    /**
     * Constructor
     * 
     * @param sessionService
     *            service to close this loader's session
     * @param fileFolderService
     *            service to access folders
     * @param userDataService
     *            service to access user names and passwords
     * @param siteDataService
     *            service to access site details
     * @param testFileService
     *            service to access sample documents
     * @param cmisBindingUrl
     *            the CMIS <b>browser</b> binding URL
     * @param cmisCtx
     *            the operation context for all calls made by the session.
     */
    public SiteFolderLoader(SessionService sessionService, FileFolderService fileFolderService,
            UserDataService userDataService, SiteDataService siteDataService, TestFileService testFileService,
            String cmisBindingUrl, OperationContext cmisCtx)
    {
        super();

        this.sessionService = sessionService;
        this.fileFolderService = fileFolderService;
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.testFileService = testFileService;

        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisCtx = cmisCtx;

        this.eventNameSiteFolderLoaded = EVENT_NAME_SITE_FOLDER_LOADED;
        this.setRmFileRatio(DEFAULT_FILE_RATIO);
        this.setRmFileDelay(DEFAULT_FILING_DELAY);
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_FOLDER_LOADED default} event name
     * @since 2.6
     */
    public void setEventNameSiteFolderLoaded(String eventNameSiteFolderLoaded)
    {
        this.eventNameSiteFolderLoaded = eventNameSiteFolderLoaded;
    }

    /**
     * @return whether if Records Management is enabled or not
     * @since 2.6
     */
    public boolean getRmEnabled()
    {
        return rmEnabled;
    }

    /**
     * Enables or disables RM
     * 
     * @param rmEnabled
     * @since 2.6
     */
    public void setRmEnabled(boolean rmEnabled)
    {
        this.rmEnabled = rmEnabled;
    }

    /**
     * @return RM file ration
     * @since 2.6
     */
    public double getRmFileRatio()
    {
        return rmFileRatio;
    }

    /**
     * Override the {@link #DEFAULT_FILE_RATIO } default value
     * @since 2.6
     */
    public void setRmFileRatio(double rmFileRatio)
    {
        this.rmFileRatio = rmFileRatio;
    }

    /**
     * @return RM file delay for follow-up events
     * @since 2.6
     */
    public long getRmFileDelay()
    {
        return rmFileDelay;
    }

    /**
     * Override the {@link #DEFAULT_FILING_DELAY } default value
     * @since 2.6
     */
    public void setRmFileDelay(long rmFileDelay)
    {
        this.rmFileDelay = rmFileDelay;
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
        Integer foldersToCreate = (Integer) dataObj.get(ScheduleSiteLoaders.FIELD_FOLDERS_TO_CREATE);
        Integer filesToCreate = (Integer) dataObj.get(ScheduleSiteLoaders.FIELD_FILES_TO_CREATE);
        if (context == null || path == null || foldersToCreate == null || filesToCreate == null)
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
            return loadFolder(folder, foldersToCreate, filesToCreate); 
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

    private EventResult loadFolder(FolderData folder, int foldersToCreate, int filesToCreate) throws IOException
    {
        UserData user = SiteFolderLoader.getUser(siteDataService, userDataService, folder, logger);
        String username = user.getUsername();
        String password = user.getPassword();

        try
        {
            List<Event> scheduleEvents = new ArrayList<Event>();
            // Establish the CMIS Session
            super.resumeTimer();
            Session session = CMIS.startSession(username, password, this.cmisBindingUrl, this.cmisCtx);
            super.suspendTimer();

            // Get the folder
            String path = folder.getPath();
            Folder cmisFolder = FileUtils.getFolder(path, session);

            // Create folders
            super.resumeTimer();
            createFolders(session, user, folder, cmisFolder, foldersToCreate);
            super.suspendTimer();

            // Create files
            super.resumeTimer();
            List<Event> eventsForRM = createFiles(session, user, folder, cmisFolder, filesToCreate);
            super.suspendTimer();

            // Build next event
            DBObject eventData = BasicDBObjectBuilder.start()
                    .add(ScheduleSiteLoaders.FIELD_CONTEXT, folder.getContext())
                    .add(ScheduleSiteLoaders.FIELD_PATH, folder.getPath()).get();
            Event nextEvent = new Event(eventNameSiteFolderLoaded, eventData);
            // add follow-up event
			scheduleEvents.add(nextEvent);
			// add additional events
            scheduleEvents.addAll(eventsForRM);

            DBObject resultData = BasicDBObjectBuilder
					.start()
                    .add("msg", "Created " + foldersToCreate + " folders and " + filesToCreate + " files.")
                    .add("path", folder.getPath())
                    .add("folderCount", foldersToCreate)
                    .add("fileCount", filesToCreate)
                    .add("docsToFileOnRMCount", eventsForRM.size())
                    .add("username", username).get();
					
            return new EventResult(resultData, scheduleEvents);
        }
        catch (CmisRuntimeException e)
        {
            String error = e.getMessage();
            String stack = ExceptionUtils.getStackTrace(e);
            // Grab the CMIS information
            DBObject data = BasicDBObjectBuilder.start()
					.append("error", error)
					.append("binding", cmisBindingUrl)
                    .append("username", username)
					.append("folder", folder)
					.append("stack", stack)
					.push("cmisFault")
                    	.append("code", "" + e.getCode()) // BigInteger is not Serializable
                    	.append("errorContent", e.getErrorContent())
					.pop()
					.get();
            // Build failure result
            return new EventResult(data, false);
        }
    }

    /**
     */
    private void createFolders(Session session, UserData user, FolderData folder, Folder cmisFolder, int foldersToCreate)
    {
        String folderPath = folder.getPath();
        for (int i = 0; i < foldersToCreate; i++)
        {
            // The folder name
            String newFolderName = UUID.randomUUID().toString();

            Map<String, String> newFolderProps = new HashMap<String, String>();
            newFolderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
            newFolderProps.put(PropertyIds.NAME, newFolderName);

            Folder newCmisFolder = cmisFolder.createFolder(newFolderProps);
            // Record the folder
            String newFolderId = newCmisFolder.getId();
            fileFolderService.createNewFolder(newFolderId, "", folderPath + "/" + newFolderName);
        }
        // Increment counts
        fileFolderService.incrementFolderCount("", folderPath, foldersToCreate);
    }

    /**
     * @throws IOException
     *             if the binary could not be uploaded
     */
    private List<Event> createFiles(Session session, UserData user, FolderData folder, Folder cmisFolder,
            int filesToCreate) throws IOException
    {
        String folderPath = folder.getPath();
        List<Event> nextEvents = new ArrayList<Event>();

        // TODO: randomize the file to RM instead
        int rmFileNeeded = (int) (DEFAULT_FILE_RATIO * filesToCreate); // the number of files sent to RM, based on
                                                                       // DEFAULT_FILE_RATIO
        int rmFileEventsScheduled = 0;
        for (int i = 0; i < filesToCreate; i++)
        {
            File file = testFileService.getFile();
            if (file == null)
            {
                throw new RuntimeException("No test files exist for upload: " + testFileService);
            }
            String filename = UUID.randomUUID().toString() + "-" + file.getName();

            Map<String, String> newFileProps = new HashMap<String, String>();
            newFileProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
            newFileProps.put(PropertyIds.NAME, filename);

            // Open up a stream to the file
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            Document newFile = null;
            try
            {
                long fileLen = file.length();
                ContentStream cs = new ContentStreamImpl(filename, BigInteger.valueOf(fileLen),
                        "application/octet-stream", is);

                newFile = cmisFolder.createDocument(newFileProps, cs, VersioningState.MAJOR);

                if (getRmEnabled() && cmisFolder.getPath() != null)
                {
                    if (rmFileEventsScheduled < rmFileNeeded)
                    {
                        long nextEventTime = System.currentTimeMillis();
                        DBObject rmData = new BasicDBObject().append(FileRMFile.RM_FILE_PATH_ID, cmisFolder.getPath()
                                + "/" + filename);
                        Event nextEvent = new Event(FileRMFile.DEFAULT_EVENT_NAME_RM_FILE, nextEventTime
                                + getRmFileDelay(), rmData);
                        nextEvents.add(nextEvent);
                        rmFileEventsScheduled++;
                    }
                }

                // Increment counts
                fileFolderService.incrementFileCount("", folderPath, 1);

                // Done
                if (logger.isDebugEnabled())
                {
                    logger.debug("Create CMIS document: " + newFile);
                }
            }
            finally
            {
                if (is != null)
                {
                    try
                    {
                        is.close();
                    }
                    catch (IOException e)
                    {
                    }
                }
            }
        }
        return nextEvents;
    }

    /**
     * Attempt to find a user to use.<br/>
     * The site ID will be used to find a valid site manager or collaborator.
     */
    /* protected */static UserData getUser(SiteDataService siteDataService, UserDataService userDataService,
            FolderData folder, Log logger)
    {
        String folderPath = folder.getPath();
        int idxSites = folderPath.indexOf("/" + CreateSite.PATH_SNIPPET_SITES + "/");
        if (idxSites < 0)
        {
            throw new IllegalStateException("This test expects to operate on folders within an existing site: "
                    + folder);
        }
        int idxDocLib = folderPath.indexOf("/" + CreateSite.PATH_SNIPPET_DOCLIB);
        if (idxDocLib < 0 || idxDocLib < idxSites)
        {
            throw new IllegalStateException(
                    "This test expects to operate on folders within an existing site document library: " + folder);
        }
        String siteId = folderPath.substring(idxSites + 7, idxDocLib);
        // Check
        SiteData siteData = siteDataService.getSite(siteId);
        if (siteData == null)
        {
            throw new IllegalStateException("Unable to find site '" + siteId + "' taken from folder path: " + folder);
        }
        SiteMemberData siteMember = siteDataService.randomSiteMember(siteId, DataCreationState.Created, null,
                SiteRole.SiteManager.toString(), SiteRole.SiteCollaborator.toString());
        if (siteMember == null)
        {
            throw new IllegalStateException("Unable to find a collaborator or manager for site: " + siteId);
        }
        String username = siteMember.getUsername();
        // Retrieve the user data
        UserData user = userDataService.findUserByUsername(username);
        if (user == null)
        {
            throw new IllegalStateException("Unable to find a user '" + username + "' linked to site: " + siteId);
        }
        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug("Found site member '" + username + "' for folder '" + folderPath + "'.");
        }
        return user;
    }
}
