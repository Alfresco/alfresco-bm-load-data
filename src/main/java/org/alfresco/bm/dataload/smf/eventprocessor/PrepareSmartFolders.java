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
package org.alfresco.bm.dataload.smf.eventprocessor;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.common.util.ArgumentCheck;
import org.alfresco.bm.driver.event.AbstractEventProcessor;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.publicapi.factory.SiteException;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.management.CMIS;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.Session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Prepares creation of smart folders in sites:
 * <p>
 * - Executed only if property 'DATALOAD.smf.enabled.default' is true
 * - Checks how many sites were created
 * - Property 'DATALOAD.smf.siteRatio.default' stores how many sites must
 * receive a smart folder
 * - executed after "siteMembersPrepare"
 *
 * @author Frank Becker
 * @since 2.7
 */
public class PrepareSmartFolders extends AbstractEventProcessor
{
    /**
     * site count field name
     */
    public final static String SITE_COUNT = "SITE_COUNT";
    /**
     * node field name
     */
    public final static String NODE_REF = "NODE_REF";
    /**
     * stores the site data service
     */
    private final SiteDataService siteDataService;
    /**
     * path to the smart folder template
     */
    private final String smfTemplatePath;
    /**
     * CMIS binding URL
     */
    private final String cmisBindingUrl;
    /**
     * CMIS binding type
     */
    private final String cmisBindingType;
    /**
     * CMIS context
     */
    private final OperationContext cmisCtx;
    /**
     * CMIS user name
     */
    private final String username;
    /**
     * CMIS password
     */
    private final String password;
    /**
     * event name of event that will create the smart folders
     */
    private final String createSmartFolderEvent;
    /**
     * event name of event to execute when smart folders created
     */
    private final String createdSmartFolderEvent;
    /**
     * event name of this event
     */
    private final String eventNameSelf;
    /**
     * Smart folders enabled?
     */
    private boolean smfEnabled;
    /**
     * smart folder ratio
     */
    private double smfRatio;

    /**
     * Constructor
     *
     * @param siteDataService         Site data service
     * @param smfEnabled              Smart folders enabled?
     * @param cmisBindingUrl          CMIS binding URL
     * @param cmisBindingType         CMIS binding type
     * @param cmisCtx                 CMIS context
     * @param username                CMIS user name
     * @param password                CMIS password
     * @param smfTemplatePath         path to the smart folder template
     * @param smfRatio                smart folder ratio
     * @param createSmartFolderEvent  event name of event that will create the smart folders
     * @param createdSmartFolderEvent event name of event to execute when smart folders created
     * @param eventNameSelf           Name of this event processor
     */
    public PrepareSmartFolders(SiteDataService siteDataService, boolean smfEnabled, String cmisBindingUrl, String cmisBindingType, OperationContext cmisCtx,
        String username, String password, String smfTemplatePath, double smfRatio, String createSmartFolderEvent, String createdSmartFolderEvent,
        String eventNameSelf)
    {
        this.siteDataService = siteDataService;
        this.smfEnabled = smfEnabled;
        this.cmisBindingUrl = cmisBindingUrl;
        this.cmisBindingType = cmisBindingType;
        this.cmisCtx = cmisCtx;
        this.username = username;
        this.password = password;
        this.smfTemplatePath = smfTemplatePath;
        this.smfRatio = smfRatio;
        this.createSmartFolderEvent = createSmartFolderEvent;
        this.createdSmartFolderEvent = createdSmartFolderEvent;
        this.eventNameSelf = eventNameSelf;

        // evaluate required values
        ArgumentCheck.checkMandatoryObject(siteDataService, "siteDataService");
        ArgumentCheck.checkMandatoryString(cmisBindingUrl, "cmisBindingUrl");
        ArgumentCheck.checkMandatoryString(cmisBindingType, "cmisBindingType");
        ArgumentCheck.checkMandatoryString(username, "username");
        ArgumentCheck.checkMandatoryString(smfTemplatePath, "smfTemplatePath");
        ArgumentCheck.checkMandatoryString(createSmartFolderEvent, "createSmartFolderEvent");
        ArgumentCheck.checkMandatoryString(createdSmartFolderEvent, "createdSmartFolderEvent");
        ArgumentCheck.checkMandatoryString(eventNameSelf, "eventNameSelf");
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        // current event processor prepared number of smart folder roots
        int preparedCount = 0;

        // max number of folders to create in on event
        int max = 100;

        // list of events to create smart folders
        List<Event> events = new ArrayList<Event>(max);

        // return message (default)
        String msg = "Skipped smart folder creation.";

        if (this.smfEnabled)
        {
            // total numbers of smart folders created so far
            long smartFoldersCreatedCount = 0;

            // get max number of sites available - throws exception if less than
            // one site created.
            long maxSites = this.siteDataService.countSites(null, null, 1);
            long smartFoldersToCreate = (long) ((double) maxSites * this.smfRatio);

            // get the workspace address of the smart folder template
            Session cmisSession = CMIS.startSession(username, password, cmisBindingType, cmisBindingUrl, cmisCtx);
            Document cmisObject = (Document) cmisSession.getObjectByPath(this.smfTemplatePath);
            Property<?> propId = cmisObject.getProperty("alfcmis:nodeRef");
            String nodeRef = "N" + propId.getValueAsString();

            // get or create event data
            DBObject eventData = (event.getData() instanceof BasicDBObject) ? (BasicDBObject) event.getData() : new BasicDBObject();
            // query total number of smart folders created from event data
            if (eventData.containsField(SITE_COUNT))
            {
                smartFoldersCreatedCount = (long) eventData.get(SITE_COUNT);
                smartFoldersToCreate -= smartFoldersCreatedCount;
            }

            // get sites to create a smart folder root in
            if (smartFoldersCreatedCount > Integer.MAX_VALUE)
            {
                throw new SiteException("Integer overflow in site count.");
            }
            List<SiteData> sites = this.siteDataService.getSites(null, null, (int) smartFoldersCreatedCount, max);
            Iterator<SiteData> it = sites.iterator();

            // create a max number of smart folder events, then reschedule self
            while (smartFoldersToCreate > 0 && preparedCount < max)
            {
                // get site data
                SiteData sd = it.next();
                preparedCount++;
                smartFoldersCreatedCount++;
                eventData.put(SITE_COUNT, smartFoldersCreatedCount);
                smartFoldersToCreate--;

                // event data and add event
                DBObject newEventData = new BasicDBObject().append(SiteData.FIELD_SITE_ID, sd.getSiteId()).append(SITE_COUNT, preparedCount)
                    .append(NODE_REF, nodeRef);
                Event siteEvent = new Event(this.createSmartFolderEvent, newEventData);
                events.add(siteEvent);
            }

            // reschedule self
            if (smartFoldersToCreate > 0)
            {
                Event outputEvent = new Event(this.eventNameSelf, eventData);
                msg = "Prepared " + preparedCount + " sites. Rescheduled self to reach a total of " + (smartFoldersCreatedCount + smartFoldersToCreate)
                    + " smart root folders";
                events.add(outputEvent);
            }
            else if (preparedCount > 0)
            {
                msg = "Prepared " + preparedCount + " sites to receive a smart root folder.";
            }
        }

        if (0 == preparedCount)
        {
            // create and return the appropriate done event
            Event outputEvent = new Event(this.createdSmartFolderEvent, null);
            events.add(outputEvent);
        }

        // create result and finish
        EventResult result = new EventResult(msg, events);
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }
}
