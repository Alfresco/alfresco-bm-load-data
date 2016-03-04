package org.alfresco.bm.dataload.smf.eventprocessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.bm.dataload.CreateSite;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.util.ArgumentCheck;
import org.alfresco.management.CMIS;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.util.FileUtils;
import org.apache.chemistry.opencmis.commons.PropertyIds;

import com.mongodb.DBObject;

/**
 * Event processor that creates a prepared smart folder root in a site.
 * 
 * @author Frank Becker
 * @since 2.1.2
 */
public class CreateSmartFolder extends AbstractEventProcessor
{
    /** stores the site data service */
    private final SiteDataService siteDataService;
    /** CMIS binding URL */
    private final String cmisBindingUrl;
    /** CMIS binding type */
    private final String cmisBindingType;
    /** CMIS context */
    private final OperationContext cmisCtx;
    /** CMIS user name */
    private final String username;
    /** CMIS password */
    private final String password;
    /** name of the smart folder root in the site */
    private final String rootFolderName;
    /** name of the aspect to add to the new CMIS smart root folder */
    private final String aspectName;
    /** name of the property to set with the smart folder template */
    private final String propertyName;
    /** event name of event to execute after creating the smart folder */
    private final String eventNameCreatedSmartFolder;

    /**
     * Constructor
     * 
     * @param siteDataService
     *        site data service
     * @param cmisBindingUrl
     *        CMIS binding URL
     * @param cmisBindingType
     *        CMIS binding type
     * @param cmisCtx
     *        CMIS context
     * @param username
     *        CMIS user name
     * @param password
     *        CMIS password
     * @param rootFolderName
     *        name of the smart root folder to create
     * @param aspectName
     *        name of the aspect to add to the smart folder root
     * @param propertyName
     *        name of the property in aspect to set
     * @param eventNameCreatedSmartFolder
     *        name of the next event
     */
    public CreateSmartFolder(SiteDataService siteDataService,
            String cmisBindingUrl, String cmisBindingType,
            OperationContext cmisCtx, String username, String password,
            String rootFolderName, String aspectName, String propertyName,
            String eventNameCreatedSmartFolder)
    {
        this.siteDataService = siteDataService;
        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisBindingType = cmisBindingType;
        this.cmisCtx = cmisCtx;
        this.username = username;
        this.password = password;
        this.rootFolderName = rootFolderName;
        this.aspectName = aspectName;
        this.propertyName = propertyName;
        this.eventNameCreatedSmartFolder = eventNameCreatedSmartFolder;

        // evaluate required values
        ArgumentCheck.checkMandatoryObject(siteDataService, "siteDataService");
        ArgumentCheck.checkMandatoryString(cmisBindingUrl, "cmisBindingUrl");
        ArgumentCheck.checkMandatoryString(cmisBindingType, "cmisBindingType");
        ArgumentCheck.checkMandatoryString(username, "username");
        ArgumentCheck.checkMandatoryString(rootFolderName, "rootFolderName");
        ArgumentCheck.checkMandatoryString(aspectName, "aspectName");
        ArgumentCheck.checkMandatoryString(propertyName, "propertyName");
        ArgumentCheck.checkMandatoryString(eventNameCreatedSmartFolder,
                "eventNameCreatedSmartFolder");
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        // get site ID and JSON template nodeRef values from event data
        DBObject eventData = (DBObject) event.getData();
        String siteId = (String) eventData.get(SiteData.FIELD_SITE_ID);
        String nodeRefTemplate = (String) eventData
                .get(PrepareSmartFolders.NODE_REF);

        // get site from site service
        SiteData site = this.siteDataService.getSite(siteId);

        // create CMIS session
        Session cmisSession = CMIS.startSession(username, password,
                cmisBindingType, cmisBindingUrl, cmisCtx);

        // get the path to the site's document library
        String siteDocLibPath = "/" + CreateSite.PATH_SNIPPET_SITES + "/"
                + siteId + "/" + CreateSite.PATH_SNIPPET_DOCLIB;
        Folder cmisFolder = FileUtils.getFolder(siteDocLibPath, cmisSession);

        // check if folder already exists
        String newFolderPath = siteDocLibPath + "/" + this.rootFolderName;
        CmisObject folder = null;
        try
        {
            folder = FileUtils.getObject(newFolderPath, cmisSession);
            if (logger.isDebugEnabled())
            {
                logger.debug("Smart folder root alredy exsits in site '"
                        + siteId + "'.");
            }
        }
        catch (Exception e)
        {
            // ignore
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Site '" + siteId
                                + "' doesn't contain a smart folder root .... will be created",
                        e);
            }
        }

        // or create the root folder
        Map<String, String> newFolderProps = new HashMap<String, String>();
        newFolderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        newFolderProps.put(PropertyIds.NAME, this.rootFolderName);
        Folder newFolder = (null == folder)
                ? cmisFolder.createFolder(newFolderProps) : (Folder) folder;

        // add aspect
        List<Object> aspects = newFolder
                .getProperty(PropertyIds.SECONDARY_OBJECT_TYPE_IDS).getValues();
        if (!aspects.contains(this.aspectName))
        {
            aspects.add(this.aspectName);
            HashMap<String, Object> props = new HashMap<String, Object>();
            props.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, aspects);
            newFolder.updateProperties(props);
        }

        // set the JSON template as property value
        HashMap<String, Object> props = new HashMap<String, Object>();
        props.put(this.propertyName, nodeRefTemplate);
        newFolder.updateProperties(props);

        // return value
        String msg = "Successfully created smart folder root '"
                + this.rootFolderName
                + "' in site ' "
                + site.getTitle()
                + "''.";
        Event retEvt = new Event(this.eventNameCreatedSmartFolder, eventData);
        EventResult result = new EventResult(msg, retEvt);
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }

}
