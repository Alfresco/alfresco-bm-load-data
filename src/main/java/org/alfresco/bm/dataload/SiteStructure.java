package org.alfresco.bm.dataload;

public class SiteStructure
{
    private String siteId;
    private String prefix;
    private int folderDepth;
    private int maxFoldersPerFolder;
    private int maxDocumentsPerFolder;

    public SiteStructure(String prefix, int folderDepth, int maxFoldersPerFolder, int maxDocumentsPerFolder)
    {
        super();
        this.prefix = prefix;
        this.folderDepth = folderDepth;
        this.maxFoldersPerFolder = maxFoldersPerFolder;
        this.maxDocumentsPerFolder = maxDocumentsPerFolder;
    }
    
    public String getSiteId()
    {
        return siteId;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public int getFolderDepth()
    {
        return folderDepth;
    }
    
    public int getMaxFoldersPerFolder()
    {
        return maxFoldersPerFolder;
    }
    
    public int getMaxDocumentsPerFolder()
    {
        return maxDocumentsPerFolder;
    }
}
