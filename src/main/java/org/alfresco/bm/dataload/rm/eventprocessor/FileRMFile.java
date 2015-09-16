package org.alfresco.bm.dataload.rm.eventprocessor;

import java.util.HashMap;
import java.util.Map;

import management.CMIS;

import org.alfresco.bm.dataload.CreateSite;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;

import com.google.common.io.Files;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Prepare RM files.
 * 
 * @author Paul Brodner
 */
public class FileRMFile extends AbstractEventProcessor
{
    public static final String DEFAULT_EVENT_NAME_RM_FILE = "rmFile";
    public static final String DEFAULT_EVENT_NAME_RM_FILE_COMPLETE = "rmFileComplete";
    public static final String RM_FILE_PATH_ID = "docForRmFiling";
    public static final String RM_UNIFIED_RECORDS = "Unfiled Records";
    
    private final String cmisBindingUrl;
    private final OperationContext cmisCtx;
    private final String username;
    private final String password;

    private SiteDataService siteDataService;
    private String eventNameRmFileComplete;

    /**
     * @param fileFolderService
     *            service to access folders
     * @param userDataService
     *            User data collections
     * @param siteDataService
     *            collection of site data
     * @param rmEnabled
     *            <tt>true</tt> if RM is enabled
     */
    public FileRMFile(SiteDataService siteDataService, String cmisBindingUrl, OperationContext cmisCtx,
            String username, String password)
    {
        super();
        this.siteDataService = siteDataService;
        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisCtx = cmisCtx;
        this.username = username;
        this.password = password;
        this.eventNameRmFileComplete = DEFAULT_EVENT_NAME_RM_FILE_COMPLETE;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RM_FILE_COMPLETE default} event name when RM Filing has been completed
     */
    public void setEventNameRmFileComplete(String eventNameRmFileComplete)
    {
        this.eventNameRmFileComplete = eventNameRmFileComplete;
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        // Find RM site
        SiteData rmSite = siteDataService.getSite(PrepareRM.RM_SITE_ID);
        if (rmSite == null)
        {
            // There is nothing more to do
            return new EventResult("There is no RM site. There is no 'Filing' process to perform.", new Event(
                    eventNameRmFileComplete, null));
        }
        String rmSiteId = rmSite.getSiteId();

        DBObject eventDBData = (DBObject) event.getData();
        String existingFile = (String) eventDBData.get(RM_FILE_PATH_ID);
        String msg = null;

        if (existingFile != null)
        {
            try
            {
                Session cmisSession = CMIS.startSession(username, password, cmisBindingUrl, cmisCtx);

                Document docOnRm = fileToRM(existingFile, cmisSession, rmSiteId);
                msg = "Document: " + docOnRm.getId() + " was filed to RM";
            }
            catch (Exception e)
            {
                DBObject data = BasicDBObjectBuilder.start().append("Cannot 'file' docs to RM site", e.getMessage())
                        .get();
                return new EventResult(data, false);
            }
        }
        return new EventResult(msg, new Event(eventNameRmFileComplete, null));
    }

    /**
     * File can be filed to Record Management site in multiple ways: (@link
     * $https://wiki.alfresco.com/wiki/Filing_and_Declaring_Records) The current solution will file an existing document
     * from Share, declaring it as a Record (i.e. moving into RM_UNIFIED_RECORDS)
     * 
     * @param fromFile
     * @param cmisSession
     * @param rmSiteId
     * @return
     */
    private Document fileToRM(String fromFile, Session cmisSession, String rmSiteId) throws Exception
    {
        Document fileFromShare = (Document) cmisSession.getObjectByPath(fromFile);

        // Initialize the default Unified Records folder from Record Management
        String unifiedRecords = "/" + CreateSite.PATH_SNIPPET_SITES + "/" + rmSiteId + "/"
                + CreateSite.PATH_SNIPPET_DOCLIB + "/" + FileRMFile.RM_UNIFIED_RECORDS;
        Folder folder = (Folder) cmisSession.getObjectByPath(unifiedRecords);

        // the document filed will be a cmis:document
        String rmFileName = Files.getNameWithoutExtension(fromFile); // the name is already random generated and saved
                                                                     // in documentLibray. Just using it further.

        // RM related properties
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        // properties.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, "P:rma:declaredRecord"); // set the document as
        // completed
        properties.put(PropertyIds.NAME, rmFileName);

        // use the content of the original file
        ContentStream contentStream = fileFromShare.getContentStream();

        return folder.createDocument(properties, contentStream, VersioningState.MAJOR);
    }
}
