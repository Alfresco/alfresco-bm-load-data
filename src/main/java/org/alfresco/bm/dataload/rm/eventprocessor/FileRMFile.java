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
package org.alfresco.bm.dataload.rm.eventprocessor;

import com.google.common.io.Files;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.dataload.CreateSite;
import org.alfresco.bm.driver.event.AbstractEventProcessor;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.management.CMIS;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;

import java.util.HashMap;
import java.util.Map;

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
    private final String cmisBindingType;
    private final OperationContext cmisCtx;
    private final String username;
    private final String password;

    private SiteDataService siteDataService;
    private String eventNameRmFileComplete;

    public FileRMFile(SiteDataService siteDataService, String cmisBindingUrl, String cmisBindingType, OperationContext cmisCtx, String username,
        String password)
    {
        super();
        this.siteDataService = siteDataService;
        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisBindingType = cmisBindingType;
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
            return new EventResult("There is no RM site. There is no 'Filing' process to perform.", new Event(eventNameRmFileComplete, null));
        }
        String rmSiteId = rmSite.getSiteId();

        DBObject eventDBData = (DBObject) event.getData();
        String existingFile = (String) eventDBData.get(RM_FILE_PATH_ID);
        String msg = null;

        if (existingFile != null)
        {
            try
            {
                Session cmisSession = CMIS.startSession(username, password, cmisBindingType, cmisBindingUrl, cmisCtx);

                Document docOnRm = fileToRM(existingFile, cmisSession, rmSiteId);
                msg = "Document: " + docOnRm.getId() + " was filed to RM";
            }
            catch (Exception e)
            {
                DBObject data = BasicDBObjectBuilder.start().append("Cannot 'file' docs to RM site", e.getMessage()).get();
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
        String unifiedRecords =
            "/" + CreateSite.PATH_SNIPPET_SITES + "/" + rmSiteId + "/" + CreateSite.PATH_SNIPPET_DOCLIB + "/" + FileRMFile.RM_UNIFIED_RECORDS;
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
