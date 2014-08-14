package org.alfresco.bm.dataload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.dataload.sitecontent.SiteContentData;
import org.alfresco.bm.dataload.sitecontent.SiteContentDataService;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.publicapi.data.CreateDocumentRequestFactory;
import org.alfresco.bm.publicapi.data.CreateFolderRequestFactory;
import org.alfresco.bm.publicapi.data.Path;
import org.alfresco.bm.publicapi.requests.CreateDocumentByParentPathRequest;
import org.alfresco.bm.publicapi.requests.CreateFolderWithParentPathRequest;

public class CreateStructure extends AbstractEventProcessor
{
    private static final int BATCH_SIZE = 100;
    private static final String EVENT_NAME_CREATE_FOLDER = "";
    private static final String EVENT_NAME_CREATE_DOCUMENT = "";
    private static final String EVENT_NAME_CREATE_STRUCTURE = "";
    
    private int batchSize = BATCH_SIZE;

    private CreateFolderRequestFactory createFolderRequestFactory;
    private CreateDocumentRequestFactory createDocumentRequestFactory;
    private SiteContentDataService siteContentDataService;

    public CreateStructure(CreateFolderRequestFactory createFolderRequestFactory,
            CreateDocumentRequestFactory createDocumentRequestFactory,
            SiteContentDataService siteContentDataService)
    {
        super();
        this.createFolderRequestFactory = createFolderRequestFactory;
        this.createDocumentRequestFactory = createDocumentRequestFactory;
        this.siteContentDataService = siteContentDataService;
    }

    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        List<Event> nextEvents = new ArrayList<Event>(BATCH_SIZE);

        long remaining = siteContentDataService.countSiteContent(null, null, false);

        if(remaining > 0)
        {
            Iterator<SiteContentData> siteContentIt = siteContentDataService.siteContentIterator(null, null, false);
            int i = batchSize;
            while(siteContentIt.hasNext() && i-- > 0)
            {
                SiteContentData siteContentData = siteContentIt.next();
                String type = siteContentData.getType();
                String parentPath = siteContentData.getParentPath();
                String networkId = siteContentData.getNetworkId();
                String creator = siteContentData.getCreator();
                
                Path path = new Path(parentPath);
    
                if(type.equals("cmis:folder"))
                {
                    CreateFolderWithParentPathRequest request = createFolderRequestFactory.create(networkId, creator, path);
                    Event newEvent = new Event(EVENT_NAME_CREATE_FOLDER, request);
                    nextEvents.add(newEvent);
                }
                else if(type.equals("cmis:document"))
                {
                    CreateDocumentByParentPathRequest request = createDocumentRequestFactory.create(networkId, creator, path);
                    Event newEvent = new Event(EVENT_NAME_CREATE_DOCUMENT, request);
                    nextEvents.add(newEvent);
                }
            }
        }
        
        if(remaining > batchSize)
        {
            Event newEvent = new Event(EVENT_NAME_CREATE_STRUCTURE, null);
            nextEvents.add(newEvent);
        }

        EventResult result = new EventResult(null, nextEvents, true);
        return result;
    }
}
