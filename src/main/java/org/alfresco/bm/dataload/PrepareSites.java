package org.alfresco.bm.dataload;

import java.util.Collections;
import java.util.Iterator;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMember;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.site.SiteVisibility;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares sites for creation by populating the sites collection.
 * <p/>
 * The number of sites is driven by: {@link #setSitesPerDomain(int)}
 * 
 * @author steveglover
 *
 */
public class PrepareSites extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITES_PREPARED = "sitesPrepared";
    public static final int DEFAULT_SITES_PER_DOMAIN = 100;

    private UserDataService userDataService;
    private SiteDataService siteDataService;
    private String eventNameSitesPrepared;
    private int sitesPerDomain;

    /**
     * @param services              data collections
     */
    public PrepareSites(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.eventNameSitesPrepared = EVENT_NAME_SITES_PREPARED;
        this.sitesPerDomain = DEFAULT_SITES_PER_DOMAIN;
    }

    public void setSitesPerDomain(int sitesPerDomain)
    {
        this.sitesPerDomain = sitesPerDomain;
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
        int domainsCount = 0;

        Iterator domains = userDataService.getDomainsIterator();
        
        int domainCount = 0;
        while (domains.hasNext())
        {
            final String domain = (String) domains.next();

            for (int siteCount = 0; siteCount < sitesPerDomain; siteCount++)
            {
                String siteId = String.format("Site.%05d.%05d", domainCount, siteCount);
                SiteData site = siteDataService.findSiteBySiteId(siteId);
                if(site != null)
                {
                    // Site already exists
                    continue;
                }

                // Choose a user that will be the manager
                UserData userData = userDataService.getRandomUserFromDomain(domain);
                String username = userData.getUsername();
                //
                // Create data
                final SiteData newSite = new SiteData();
                newSite.setDescription("");
                newSite.setSiteId(siteId);
                newSite.setSitePreset("preset");
                newSite.setTitle(siteId);
                newSite.setVisibility(SiteVisibility.getRandomVisibility());
                newSite.setType("{http://www.alfresco.org/model/site/1.0}site");
                newSite.setDomain(domain);
                newSite.setCreatedBy(username);
                newSite.setCreated(Boolean.FALSE);

                // Persist
                siteDataService.addSite(newSite);
                sitesCount++;
                
                // Record the user as the site manager
                SiteMember siteMember = new SiteMember(username, siteId, domain, SiteRole.SiteManager.toString());
                siteDataService.addSiteMember(siteMember);
            }
        }

        // We need an event to mark completion
        String msg = "Prepared " + sitesCount + " sites in " + domainsCount + " domains.";
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
