package org.alfresco.bm.dataload;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteVisibility;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares sites for creation by populating the sites collection.
 * 
 * The number of sites is driven by:
 * 
 * the number of networks (implicit in the Users collection)
 * maxSitesPerNetwork: the maximum number of sites per network.
 * 
 * startSite: start number site names from here.
 * 
 * @author steveglover
 *
 */
public class PrepareSites extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITES_PREPARED = "sitesPrepared";
    public static final int DEFAULT_MAX_SITES_PER_NETWORK = Integer.MAX_VALUE; // all

    private int maxSitesPerNetwork;
    private String eventNameSitesPrepared;
    private UserDataService userDataService;
    private SiteDataService siteDataService;

    /**
     * @param services              data collections
     */
    public PrepareSites(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.maxSitesPerNetwork = DEFAULT_MAX_SITES_PER_NETWORK;
        this.eventNameSitesPrepared = EVENT_NAME_SITES_PREPARED;
    }
    
    public void init()
    {
    }

    public void setMaxSitesPerNetwork(int maxSitesPerNetwork)
    {
        this.maxSitesPerNetwork = maxSitesPerNetwork;
    }

    /**
     * Override the {@link #EVENT_NAME_SITES_PREPARED default} event name when sites have been created.
     */
    public void setEventNameSitesPrepared(String eventNameSitesPrepared)
    {
        this.eventNameSitesPrepared = eventNameSitesPrepared;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        int sitesCount = 0;
        int networksCount = 0;

        Iterator networksIt = userDataService.getDomainsIterator();
        while(networksIt.hasNext())
        {
            final String networkId = (String)networksIt.next();

            long numSitesInNetwork = siteDataService.countSites(networkId);
            if(numSitesInNetwork < maxSitesPerNetwork)
            {
                List<UserData> usersInNetwork = userDataService.getUsersInDomain(networkId, 0, 200);
                if(usersInNetwork.size() > 0)
                {
                    // there are users in the network, which means that the network exists. Proceed.
                    int userIndex = 0;
    
                    for (int j = 0; j < maxSitesPerNetwork; j++)
                    {
                        String siteId = String.format("Site.%05d.%05d", System.currentTimeMillis(), j);
                        SiteData site = siteDataService.findSiteBySiteId(siteId);
                        if(site != null)
                        {
                            // Site already exists
                            continue;
                        }
        
                        if(userIndex >= usersInNetwork.size())
                        {
                            userIndex = 0;
                        }
    
                        UserData creator = usersInNetwork.get(userIndex);
                        //
                        // Create data
                        final SiteData newSite = new SiteData();
                        newSite.setDescription("");
                        newSite.setSiteId(siteId);
                        newSite.setSitePreset("preset");
                        newSite.setTitle(siteId);
                        newSite.setVisibility(SiteVisibility.getRandomVisibility());
                        newSite.setType("{http://www.alfresco.org/model/site/1.0}site");
                        newSite.setNetworkId(networkId);
                        newSite.setCreatedBy(creator.getEmail());
                        newSite.setCreated(Boolean.FALSE);
        
                        // Persist
                        siteDataService.addSite(newSite);
                        sitesCount++;
                        userIndex++;
                    }
                }
    
                networksCount++;
            }
        }

        // We need an event to mark completion
        String msg = "Prepared " + sitesCount + " sites in " + networksCount + " networks.";
        Event outputEvent = new Event(eventNameSitesPrepared, 0L, msg);

        // Create result
        EventResult result = new EventResult(msg, Collections.singletonList(outputEvent));

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }
}
