package org.alfresco.bm.dataload.sitecontent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteMember;
import org.springframework.beans.factory.InitializingBean;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

public class SiteContentDataServiceImpl implements SiteContentDataService, InitializingBean
{

    private DBCollection siteContentCollection;

    public SiteContentDataServiceImpl(DB db, String siteContentCollectionName)
    {
        siteContentCollection = db.getCollection(siteContentCollectionName);
    }
    
    @Override
    public void afterPropertiesSet() throws Exception
    {
        checkIndexes();
    }
    
    /**
     * Ensure that the MongoDB collection has the required indexes associated with
     * this user bean.
     */
    public void checkIndexes()
    {
        siteContentCollection.setWriteConcern(WriteConcern.SAFE);

        DBObject idxSiteId = BasicDBObjectBuilder
                .start(SiteData.FIELD_SITE_ID, 1)
                .get();
        DBObject optSiteId = BasicDBObjectBuilder
                .start("name", "idx_SiteId")
                .add("unique", Boolean.TRUE)
                .get();
        siteContentCollection.createIndex(idxSiteId, optSiteId);    
        
        DBObject idxNetworkSiteId = BasicDBObjectBuilder
                .start(SiteData.FIELD_CREATED, 1)
                .append(SiteMember.FIELD_NETWORK_ID, 1)
                .append(SiteMember.FIELD_SITE_ID, 1)
                .get();
        DBObject optNetworkSiteId = BasicDBObjectBuilder
                .start("name", "idx_NetworkSiteId")
                .add("unique", Boolean.TRUE)
                .get();
        siteContentCollection.createIndex(idxNetworkSiteId, optNetworkSiteId);

        DBObject idxCreated = BasicDBObjectBuilder
                .start(SiteData.FIELD_CREATED, 1)
                .append(SiteMember.FIELD_RANDOMIZER, 1)
                .get();
        DBObject optId = BasicDBObjectBuilder
                .start("name", "idx_Created")
                .add("unique", Boolean.FALSE)
                .get();
        siteContentCollection.createIndex(idxCreated, optId);
    }
    
    private DBObject convertSiteContentData(SiteContentData siteContent)
    {
        DBObject data = new BasicDBObject("creator",siteContent.getCreator())
        .append("networkId",siteContent.getNetworkId())
        .append("parentPath", siteContent.getParentPath())
        .append("siteId", siteContent.getSiteId())
        .append("type", siteContent.getType())
        .append("name", siteContent.getName())
        .append("randomizer", siteContent.getRandomizer());
        return data;
    }
    
    private SiteContentData convertSiteContentDataDBObject(DBObject value)
    {
        String creator = (String) value.get("creator");
        String networkId = (String) value.get("networkId");
        String parentPath = (String) value.get("parentPath");
        String siteId = (String) value.get("siteId"); 
        String type = (String) value.get("type");
        String name = (String) value.get("name");
        int randomizer = Integer.parseInt(value.get("randomizer").toString());
        return new SiteContentData(randomizer, creator, networkId, parentPath, siteId, type, name);
    }
    
    @Override
    public SiteContentData createFolder(String siteId, String networkId, String siteCreator,
            String parentFolderPath, String name)
    {
        String type = "cmis:folder";
        SiteContentData siteContentData = new SiteContentData(siteCreator, networkId, parentFolderPath, siteId, type, name);
        siteContentCollection.insert(convertSiteContentData(siteContentData));
        
        return siteContentData;
    }

    @Override
    public SiteContentData createDocument(String siteId, String networkId, String siteCreator,
            String parentFolderPath, String name)
    {
        String type = "cmis:document";
        SiteContentData siteContentData = new SiteContentData(siteCreator, networkId, parentFolderPath, siteId, type, name);
        siteContentCollection.insert(convertSiteContentData(siteContentData));
        
        return siteContentData;
    }
    
    @Override
    public Iterator<SiteContentData> siteContentIterator(String networkId, String siteId, boolean created)
    {
        DBObject query = new BasicDBObject(SiteData.FIELD_CREATED,Boolean.valueOf(created));
        if(networkId != null)
        {
            query.put(SiteData.FIELD_NETWORKID, networkId);
        }
        if(siteId != null)
        {
            query.put(SiteData.FIELD_SITE_ID, siteId);
        }
        DBCursor cursor = siteContentCollection.find(query);
        List<SiteContentData> siteContent = new ArrayList<SiteContentData>();
        try 
        {
            while (cursor.hasNext()) 
            {
                siteContent.add(convertSiteContentDataDBObject(cursor.next()));
            }
        } 
        finally 
        {
            cursor.close();
        }
        return siteContent.iterator();
    }
    

    @Override
    public long countSiteContent(String networkId, String siteId, boolean created)
    {
        DBObject query = new BasicDBObject(SiteData.FIELD_CREATED, Boolean.valueOf(created));
        if(networkId != null)
        {
            query.put(SiteData.FIELD_NETWORKID, networkId);
        }
        if(siteId != null)
        {
            query.put(SiteData.FIELD_SITE_ID, siteId);
        }
        long count = siteContentCollection.count(query);
        // Done
        return count;
    }
}
