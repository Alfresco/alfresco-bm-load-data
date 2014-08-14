package org.alfresco.bm.dataload.sitecontent;

import java.util.Iterator;

public interface SiteContentDataService
{
    SiteContentData createFolder(String siteId, String networkId, String siteCreator, String parentFolderPath, String name);
    SiteContentData createDocument(String siteId, String networkId, String siteCreator, String parentFolderPath, String name);
    Iterator<SiteContentData> siteContentIterator(String networkId, String siteId, boolean created);
    long countSiteContent(String networkId, String siteId, boolean created);
}
