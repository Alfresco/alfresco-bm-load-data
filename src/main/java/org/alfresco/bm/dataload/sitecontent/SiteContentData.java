package org.alfresco.bm.dataload.sitecontent;

import java.io.Serializable;

/**
 * Data representing site content.
 * 
 * @author steveglover
 *
 */
public class SiteContentData implements Serializable
{
    private static final long serialVersionUID = -6574863327844189070L;

    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_CREATED = "created";
    public static final String FIELD_CREATOR = "creator";
    public static final String FIELD_RANDOMIZER = "randomizer";
    public static final String FIELD_NETWORKID = "networkId";

    private Boolean created = false;

    private String creator;
    private String networkId;

    private String parentPath;
    private String siteId;
    private String type;
    private String name;

    private int randomizer;

    public SiteContentData()
    {
        randomizer = (int)(Math.random() * 1E6);
    }

    public SiteContentData(String creator, String networkId,
            String parentPath, String siteId, String type, String name)
    {
        super();
        this.creator = creator;
        this.networkId = networkId;
        this.parentPath = parentPath;
        this.siteId = siteId;
        this.type = type;
        this.name = name;
    }

    public int getRandomizer()
    {
        return randomizer;
    }

    public String getNetworkId()
    {
        return networkId;
    }

    public Boolean isCreated()
    {
        return created;
    }

    public void setCreated(Boolean created)
    {
        this.created = created;
    }

    public String getSiteId()
    {
        return siteId;
    }

    public String getType()
    {
        return type;
    }

    public void setNetworkId(String networkId)
    {
        this.networkId = networkId;
    }

    public void setSiteId(String siteId)
    {
        this.siteId = siteId;
    }

    public void setType(String type)
    {
        this.type = type;
    }
    
    public String getCreator()
    {
        return creator;
    }

    public String getParentPath()
    {
        return parentPath;
    }

    public String getName()
    {
        return name;
    }

    public Boolean getCreated()
    {
        return created;
    }

    public void setCreator(String creator)
    {
        this.creator = creator;
    }

    public void setParentPath(String parentPath)
    {
        this.parentPath = parentPath;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return "SiteContentData [created=" + created + ", creator=" + creator
                + ", networkId=" + networkId + ", parentPath=" + parentPath
                + ", siteId=" + siteId + ", type=" + type + ", name=" + name
                + ", randomizer=" + randomizer + "]";
    }
}
