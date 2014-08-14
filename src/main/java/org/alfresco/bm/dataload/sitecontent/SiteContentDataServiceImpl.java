package org.alfresco.bm.dataload.sitecontent;

import java.util.Iterator;

import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteMember;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

public class SiteContentDataServiceImpl implements SiteContentDataService, InitializingBean
{
    private MongoTemplate mongo;

    private String siteContentCollectionName;

    public SiteContentDataServiceImpl(MongoTemplate mongo)
    {
        this.mongo = mongo;
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
        mongo.getDb().getCollection(siteContentCollectionName).setWriteConcern(WriteConcern.SAFE);

        DBObject idxSiteId = BasicDBObjectBuilder
                .start(SiteData.FIELD_SITE_ID, 1)
                .get();
        mongo.getDb().getCollection(siteContentCollectionName).ensureIndex(idxSiteId, "idx_SiteId", true);
        
        DBObject idxNetworkSiteId = BasicDBObjectBuilder
                .start(SiteData.FIELD_CREATED, 1)
                .append(SiteMember.FIELD_NETWORK_ID, 1)
                .append(SiteMember.FIELD_SITE_ID, 1)
                .get();
        mongo.getDb().getCollection(siteContentCollectionName).ensureIndex(idxNetworkSiteId, "idx_NetworkSiteId", true);

        DBObject idxCreated = BasicDBObjectBuilder
                .start(SiteData.FIELD_CREATED, 1)
                .append(SiteMember.FIELD_RANDOMIZER, 1)
                .get();
        mongo.getDb().getCollection(siteContentCollectionName).ensureIndex(idxCreated, "idx_Created", false);
    }

    @Override
    public SiteContentData createFolder(String siteId, String networkId, String siteCreator,
            String parentFolderPath, String name)
    {
        String type = "cmis:folder";
        SiteContentData siteContentData = new SiteContentData(siteCreator, networkId, parentFolderPath, siteId, type, name);
        mongo.insert(siteContentData, siteContentCollectionName);
        
        return siteContentData;
    }

    @Override
    public SiteContentData createDocument(String siteId, String networkId, String siteCreator,
            String parentFolderPath, String name)
    {
        String type = "cmis:document";
        SiteContentData siteContentData = new SiteContentData(siteCreator, networkId, parentFolderPath, siteId, type, name);
        mongo.insert(siteContentData, siteContentCollectionName);
        
        return siteContentData;
    }
    
    @Override
    public Iterator<SiteContentData> siteContentIterator(String networkId, String siteId, boolean created)
    {
        Criteria siteContentCriteria = Criteria.where(SiteData.FIELD_CREATED).is(Boolean.valueOf(created));
        if(networkId != null)
        {
            siteContentCriteria = siteContentCriteria.and(SiteData.FIELD_NETWORKID).is(networkId);
        }
        if(siteId != null)
        {
            siteContentCriteria = siteContentCriteria.and(SiteData.FIELD_SITE_ID).is(siteId);
        }
        Query siteContentQuery = new Query(siteContentCriteria);
        Iterator<SiteContentData> siteContentIt = mongo.find(siteContentQuery, SiteContentData.class, siteContentCollectionName).iterator();
        return siteContentIt;
    }
    
    @Override
    public long countSiteContent(String networkId, String siteId, boolean created)
    {
        Criteria siteContentCriteria = Criteria.where(SiteData.FIELD_CREATED).is(Boolean.valueOf(created));
        if(networkId != null)
        {
            siteContentCriteria = siteContentCriteria.and(SiteData.FIELD_NETWORKID).is(networkId);
        }
        if(siteId != null)
        {
            siteContentCriteria = siteContentCriteria.and(SiteData.FIELD_SITE_ID).is(siteId);
        }
        Query siteContentQuery = new Query(siteContentCriteria);
        long count = mongo.count(siteContentQuery, siteContentCollectionName);
        // Done
        return count;
    }
}
