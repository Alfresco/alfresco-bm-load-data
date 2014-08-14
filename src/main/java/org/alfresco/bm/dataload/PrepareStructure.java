package org.alfresco.bm.dataload;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.alfresco.bm.dataload.sitecontent.SiteContentDataService;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.apache.chemistry.opencmis.commons.PropertyIds;

public class PrepareStructure extends AbstractEventProcessor
{
    private SiteContentDataService siteContentDataService;
    private SiteDataService siteDataService;
    private Random random = new Random(System.currentTimeMillis());

    
    public PrepareStructure(SiteContentDataService siteContentDataService, SiteDataService siteDataService)
    {
        super();
        this.siteContentDataService = siteContentDataService;
        this.siteDataService = siteDataService;
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        SiteStructure siteStructure = (SiteStructure)event.getDataObject();

        String siteId = siteStructure.getSiteId();
        int folderDepth = siteStructure.getFolderDepth();
        int maxDocumentsPerFolder = siteStructure.getMaxDocumentsPerFolder();
        int maxFoldersPerFolder = siteStructure.getMaxFoldersPerFolder();
        
        SiteData siteData = siteDataService.findSiteBySiteId(siteId);
        String networkId = siteData.getNetworkId();

        List<Event> nextEvents = new ArrayList<Event>();

        String rootPath = "/Sites/" + siteData.getSiteId() + "/documentLibrary";
        StructureCreator structureCreator = new StructureCreator(rootPath, networkId, siteData, folderDepth, maxDocumentsPerFolder, maxFoldersPerFolder);
        structureCreator.createStructure();

        EventResult result = new EventResult(null, nextEvents, true);
        return result;
    }
    
    private class StructureCreator
    {
        private String networkId;
        private SiteData siteData;
        private String siteCreator;
        private int folderDepth;
        private int maxDocumentsPerFolder;
        private int maxFoldersPerFolder;
        private String parentPath;

        public StructureCreator(String parentPath, String networkId, SiteData siteData, int folderDepth, int maxDocumentsPerFolder,
                int maxFoldersPerFolder)
        {
            super();
            this.parentPath = parentPath;
            this.networkId = networkId;
            this.siteData = siteData;
            this.siteCreator = siteData.getCreatedBy();
            this.folderDepth = folderDepth;
            this.maxDocumentsPerFolder = maxDocumentsPerFolder;
            this.maxFoldersPerFolder = maxFoldersPerFolder;
        }

        void createStructure()
        {
            if(folderDepth == 0)
            {
                return;
            }

            int size = random.nextInt(maxFoldersPerFolder);
            for(int j = 0; j < size; j++)
            {
                StringBuilder nameBuilder = new StringBuilder("folder");
                nameBuilder.append(j);
                String name = nameBuilder.toString();

                Map<String, Serializable> properties = new HashMap<String, Serializable>();
                properties.put(PropertyIds.NAME, name);

                siteContentDataService.createFolder(siteData.getSiteId(), networkId, siteCreator, parentPath.toString(), name);

                StringBuilder sb = new StringBuilder(parentPath);
                sb.append("/");
                sb.append(name);
                StructureCreator sc = new StructureCreator(sb.toString(), networkId, siteData, folderDepth - 1, maxDocumentsPerFolder, maxFoldersPerFolder);
                sc.createStructure();
            }

            size = random.nextInt(maxDocumentsPerFolder);
            for(int j = 0; j < size; j++)
            {
                StringBuilder nameBuilder = new StringBuilder("document");
                nameBuilder.append(j);
                String name = nameBuilder.toString();

                Map<String, Serializable> properties = new HashMap<String, Serializable>();
                properties.put(PropertyIds.NAME, name);

                siteContentDataService.createDocument(siteData.getSiteId(), networkId, siteCreator, parentPath.toString(), name);
            }
        }
    }
}
