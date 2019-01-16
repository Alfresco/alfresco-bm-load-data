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
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.dataload.sites.CreateSite;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.driver.file.TestFileService;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.rest.core.RestWrapper;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.rest.model.RestNodeBodyModel;
import org.alfresco.rest.model.RestNodeModel;
import org.alfresco.rest.model.RestRenditionInfoModel;
import org.alfresco.rest.model.RestRenditionInfoModelCollection;
import org.alfresco.utility.model.ContentModel;
import org.alfresco.utility.model.FileModel;
import org.alfresco.utility.model.UserModel;
import org.apache.commons.logging.Log;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Schedule the #EVENT_NAME_LOAD_FOLDERS folder and #EVENT_NAME_LOAD_FILES file loaders and
 * #EVENT_NAME_SCHEDULE_LOADERS reschedule self until there are enough folders and files.
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
 * <p>
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
 * <p>
 *
 * @author Derek Hulley
 * @since 2.0
 */
public class SiteFolderLoader extends AbstractRestApiEventProcessor
{
    public static final String EVENT_NAME_SITE_FOLDER_LOADED = "siteFolderLoaded";

    private final FileFolderService fileFolderService;
    private final UserDataService userDataService;
    private final SiteDataService siteDataService;
    private final TestFileService testFileService;

    private String eventNameSiteFolderLoaded;

    private boolean requestRenditions;
    private String renditionList;

    /**
     * Constructor
     *
     * @param fileFolderService service to access folders
     * @param userDataService   service to access usernames and passwords
     * @param siteDataService   service to access site details
     * @param testFileService   service to access sample documents
     */
    public SiteFolderLoader(FileFolderService fileFolderService, UserDataService userDataService, SiteDataService siteDataService,
        TestFileService testFileService)
    {
        super();

        this.fileFolderService = fileFolderService;
        this.userDataService = userDataService;
        this.siteDataService = siteDataService;
        this.testFileService = testFileService;

        this.eventNameSiteFolderLoaded = EVENT_NAME_SITE_FOLDER_LOADED;
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
            return new EventResult("Request data not complete for folder loading: " + dataObj, false);
        }
        // Get the folder
        FolderData folder = fileFolderService.getFolder(context, path);
        if (folder == null)
        {
            throw new IllegalStateException("No such folder recorded: " + dataObj);
        }
        // Get the session
        // TODO remove all the related session code - I don't think they are needed for the new TAS rest calls
        String sessionId = event.getSessionId();
        if (sessionId == null)
        {
            return new EventResult("Load scheduling should create a session for each loader.", false);
        }

        return loadFolder(folder, foldersToCreate, filesToCreate);
    }

    private EventResult loadFolder(FolderData folder, int foldersToCreate, int filesToCreate)
    {
        UserData user = SiteFolderLoader.getUser(siteDataService, userDataService, folder, logger);

        // Create folders
        createFolders(user, folder, foldersToCreate);

        // Create files
        createFiles(user, folder, filesToCreate);

        // Build next event
        DBObject eventData = BasicDBObjectBuilder.start().add(ScheduleSiteLoaders.FIELD_CONTEXT, folder.getContext())
            .add(ScheduleSiteLoaders.FIELD_PATH, folder.getPath()).get();
        Event nextEvent = new Event(eventNameSiteFolderLoaded, eventData);
        // add follow-up event
        List<Event> scheduleEvents = new ArrayList<Event>();
        scheduleEvents.add(nextEvent);

        DBObject resultData = BasicDBObjectBuilder.start().add("msg", "Created " + foldersToCreate + " folders and " + filesToCreate + " files.")
            .add("path", folder.getPath()).add("folderCount", foldersToCreate).add("fileCount", filesToCreate).add("username", user.getUsername()).get();

        return new EventResult(resultData, scheduleEvents);

    }

    private void createFolders(UserData user, FolderData folder, int foldersToCreate)
    {
        String folderPath = folder.getPath();

        // we need a user model
        UserModel userModel = new UserModel();
        userModel.setUsername(user.getUsername());
        userModel.setPassword(user.getPassword());

        // we also need a reference to a parent folder
        ContentModel parentFolder = new ContentModel();
        parentFolder.setNodeRef(folder.getId());

        for (int i = 0; i < foldersToCreate; i++)
        {
            String newFolderName = UUID.randomUUID().toString();

            try
            {
                createFolder(folder, folderPath, userModel, parentFolder, newFolderName);
            }
            catch (Exception e)
            {
                // let the system handle/log the failure
                throw new RuntimeException("Failed to create folder: " + folder.getId() + " path: " + folderPath + ". Exception: " + e.getMessage(), e);
            }
        }
    }

    private void createFolder(FolderData folder, String folderPath, UserModel userModel, ContentModel parentFolder, String newFolderName) throws Exception
    {
        RestNodeBodyModel model = new RestNodeBodyModel();
        model.setName(newFolderName);
        model.setNodeType("cm:folder");

        RestWrapper restWrapper = getRestWrapper();

        resumeTimer();
        RestNodeModel newFolderModel = restWrapper.authenticateUser(userModel).withCoreAPI().usingNode(parentFolder).createNode(model);
        suspendTimer();

        final String statusCode = restWrapper.getStatusCode();
        if (isStatusCreated(statusCode))
        {
            // Record the folder and increment the folder count
            fileFolderService.createNewFolder(newFolderModel.getId(), "", folderPath + "/" + newFolderName);
            fileFolderService.incrementFolderCount("", folderPath, 1);
            logFolderSuccess(newFolderModel);
        }
        else if (isStatusConflict(statusCode))
        {
            // node already exists, carry on
            logFolderDuplicate(folderPath, newFolderName);
        }
        else
        {
            // this is a failure, throw and exception and let the system handle it
            String message =
                "Could not create folder: " + newFolderName + " in path: " + folderPath + " , folder id: " + folder.getId() + " . Code: " + statusCode
                    + " . Message: " + getRestCallErrorMessage(restWrapper);
            throw new RuntimeException(message);
        }
    }

    private void createFiles(UserData user, FolderData folder, int filesToCreate)
    {
        String folderPath = folder.getPath();

        // we need a user model
        UserModel userModel = new UserModel();
        userModel.setUsername(user.getUsername());
        userModel.setPassword(user.getPassword());

        // we also need a reference to a parent folder
        ContentModel parentFolder = new ContentModel();
        parentFolder.setNodeRef(folder.getId());

        for (int i = 0; i < filesToCreate; i++)
        {
            // get a random file to upload
            File fileToUpload = testFileService.getFile();
            if (fileToUpload == null)
            {
                throw new RuntimeException("No test files exist for upload: " + testFileService);
            }
            String newFileName = UUID.randomUUID().toString() + "-" + fileToUpload.getName();
            try
            {
                createFile(newFileName, fileToUpload, parentFolder, folderPath, userModel);
            }
            catch (Exception e)
            {
                // let the system handle/log the failure
                throw new RuntimeException("Failed to create file: " + newFileName + " in path: " + folderPath + ". Exception: " + e.getMessage(), e);
            }
        }
    }


    private void createFile(String newFileName, File fileToUpload, ContentModel parentFolder, String parentFolderPath, UserModel userModel)
        throws Exception
    {
        RestWrapper restWrapper = getRestWrapper();

        // There is no method in the restassured api for addMultiPart File and also specify a different file name.
        // The name is taken out of the File.getName(). It is always assumed you want to add a file and keep the name of that file.
        // In order to send the contents of the file, but specify a different name, we could use the method with the Stream overloaded parameter:
        // addMultiPart("filedata", newFileName, new FileInputStream(fileToUpload));
        // or the one with the byte array parameter, but that will be converted to stream as well
        // The next line (createNode()) will throw an exception saying that the request is not repeatable(it is not,
        // because the stream once closed can not be reused)
        // Another option is to copy the file with the new name on the disk, for each request, and delete it at the end of this method,
        // but that would mean a lot of IO on the OS running this driver - not really what we want
        //
        // The solution chosen is to hack it: use the FakeNameFile class that will return our custom name for the getName() method
        // it seems to work fine.

        resumeTimer();
        restWrapper.authenticateUser(userModel).configureRequestSpec().addMultiPart("filedata", new FakeNameFile(newFileName, fileToUpload));
        RestNodeModel newFileNode = restWrapper.authenticateUser(userModel).withCoreAPI().usingResource(parentFolder).createNode();
        suspendTimer();

        final String statusCode = restWrapper.getStatusCode();
        if (isStatusCreated(statusCode))
        {
            fileFolderService.incrementFileCount("", parentFolderPath, 1);
            logFileCreated(newFileNode);
            if (isRequestRenditions())
            {
                triggerRenditions(userModel, restWrapper, newFileNode);
            }
        }
        else if (isStatusConflict(statusCode))
        {
            // node already exists, carry on
            logFileConflict(newFileName, parentFolderPath);
        }
        else
        {
            final String message =
                "Could not create file: " + newFileName + " in path: " + parentFolderPath + " , parent folder id: " + parentFolder.getNodeRef()
                    + ". Message: " + getRestCallErrorMessage(restWrapper);
            throw new RuntimeException(message);
        }
    }

    private void triggerRenditions(UserModel userModel, RestWrapper restWrapper, RestNodeModel newFileNode) throws Exception
    {
        final FileModel file = new FileModel();
        file.setNodeRef(newFileNode.getId());

        // Get supported renditions
        logger.debug("Finding out all possible renditions for node: " + newFileNode.getId());
        resumeTimer();
        RestRenditionInfoModelCollection renditionsInfo = restWrapper.withCoreAPI().usingNode(file).getNodeRenditionsInfo();
        suspendTimer();
        for (RestRenditionInfoModel m : renditionsInfo.getEntries())
        {
            RestRenditionInfoModel renditionInfo = m.onModel();
            String renditionId = renditionInfo.getId();
            String targetMimeType = renditionInfo.getContent().getMimeType();
            logger.debug("Supported rendition: " + renditionId + " target mime type: " + targetMimeType);

            if (isRenditionTypeRequested(renditionId))
            {
                logger.debug("Requesting rendition: " + renditionId);
                resumeTimer();
                restWrapper.authenticateUser(userModel).withCoreAPI().usingNode(file).createNodeRendition(renditionId);
                suspendTimer();

                final String statusCodeRendition = restWrapper.getStatusCode();
                logger.debug("Status code rendition: " + statusCodeRendition);
            }
            // It is not advised to call waitForRenditionToBeCreated(restWrapper, file, renditionId);
            // because renditions may take some time to be created.
        }
    }

    /**
     * Careful with this method. Make sure you use it only if you understand the implications.
     */
    private void waitForRenditionToBeCreated(RestWrapper restWrapper, FileModel file, String renditionId) throws Exception
    {
        //if you want to change the default timeout modify this: Utility.retryCountSeconds = 30;
        logger.debug("waiting for rendition to be created... ");
        resumeTimer();
        final RestRenditionInfoModel nodeRenditionUntilIsCreated = restWrapper.withCoreAPI().usingNode(file)
            .getNodeRenditionUntilIsCreated(renditionId);
        suspendTimer();
        logger.debug("Rendition creation status: " + nodeRenditionUntilIsCreated.getStatus());
    }

    private boolean isRenditionTypeRequested(String renditionId)
    {
        if (renditionId==null || renditionId.isEmpty())
        {
            return false; // this is invalid
        }
        String renditionList = getRenditionList();
        if (renditionList==null || renditionList.isEmpty())
        {
            //if the user had not specified any type of rendition, we will request rendition for all supported types
            return true;
        }
        String[] types = renditionList.split(",");
        for(String type: types)
        {
            if (renditionId.equals(type))
            {
                return true;
            }
        }
        return false;
    }

    // TODO the following few methods should go in the super class

    private boolean isStatusConflict(String statusCode)
    {
        return HttpStatus.CONFLICT.toString().equalsIgnoreCase(statusCode);
    }

    private boolean isStatusCreated(String statusCode)
    {
        return HttpStatus.CREATED.toString().equalsIgnoreCase(statusCode);
    }

    private String getRestCallErrorMessage(RestWrapper restWrapper)
    {
        RestErrorModel restErrorModel = restWrapper.assertLastError();
        return restErrorModel != null ? restErrorModel.toString() : "<nothing>";
    }
    // TODO until here

    private void logFolderDuplicate(String folderPath, String newFolderName)
    {
        if (logger.isWarnEnabled())
        {
            logger.warn("Folder: " + newFolderName + " from path: " + folderPath + " seems to exist already. Carry on!");
        }
    }

    private void logFolderSuccess(RestNodeModel newFolderModel)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Created new folder: " + newFolderModel.getName() + " with ID: " + newFolderModel.getId());
        }
    }

    private void logFileConflict(String newFileName, String parentFolderPath)
    {
        if (logger.isWarnEnabled())
        {
            logger.warn("File: " + newFileName + " from path: " + parentFolderPath + " seems to exist already. Carry on!");
        }
    }

    private void logFileCreated(RestNodeModel fileNode)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Created new file: " + fileNode.getName() + " with ID: " + fileNode.getId());
        }
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_FOLDER_LOADED default} event name
     *
     * @since 2.6
     */
    public void setEventNameSiteFolderLoaded(String eventNameSiteFolderLoaded)
    {
        this.eventNameSiteFolderLoaded = eventNameSiteFolderLoaded;
    }

    public boolean isRequestRenditions()
    {
        return requestRenditions;
    }

    public void setRequestRenditions(boolean requestRenditions)
    {
        this.requestRenditions = requestRenditions;
    }

    public String getRenditionList()
    {
        return renditionList;
    }

    public void setRenditionList(String renditionList)
    {
        this.renditionList = renditionList;
    }

    /**
     * Attempt to find a user to use.<br/>
     * The site ID will be used to find a valid site manager or collaborator.
     * <p>
     * TODO this should be rewriten as a service level method  similar to siteDataService.randomSiteMember
     */
    static UserData getUser(SiteDataService siteDataService, UserDataService userDataService, FolderData folder, Log logger)
    {
        String folderPath = folder.getPath();
        int idxSites = folderPath.indexOf("/" + CreateSite.PATH_SNIPPET_SITES + "/");
        if (idxSites < 0)
        {
            throw new IllegalStateException("This test expects to operate on folders within an existing site: " + folder);
        }
        int idxDocLib = folderPath.indexOf("/" + CreateSite.PATH_SNIPPET_DOCLIB);
        if (idxDocLib < 0 || idxDocLib < idxSites)
        {
            throw new IllegalStateException("This test expects to operate on folders within an existing site document library: " + folder);
        }
        String siteId = folderPath.substring(idxSites + 7, idxDocLib);
        // Check
        SiteData siteData = siteDataService.getSite(siteId);
        if (siteData == null)
        {
            throw new IllegalStateException("Unable to find site '" + siteId + "' taken from folder path: " + folder);
        }
        SiteMemberData siteMember = siteDataService
            .randomSiteMember(siteId, DataCreationState.Created, null, SiteRole.SiteManager.toString(), SiteRole.SiteCollaborator.toString());
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

/**
 * Hack class to return whatever name we want for a file
 */
class FakeNameFile extends File
{
    private String fakeName;

    FakeNameFile(String newFakeName, File theFileToFake)
    {
        super(theFileToFake.toURI());
        this.fakeName = newFakeName;
    }

    @Override
    public String getName()
    {
        return fakeName;
    }
}