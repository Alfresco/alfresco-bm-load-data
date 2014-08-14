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
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares site members for creation by populating the site members collection.
 * 
 * The number of site members to create is driven by:
 * 
 * maxSites: if -1, uses all created sites in the sites collection
 * maxMembersPerSite: must be greater than 0
 * 
 * @author steveglover
 *
 * TODO: only site members from same network as site at present.
 */
public class PrepareSiteMembers extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_MEMBERS_PREPARED = "siteMembersPrepared";
            
    private String eventNameSiteMembersPrepared;
    private UserDataService userDataService;
    private SiteDataService siteDataService;

    private int maxSiteMembers;

    /**
     * @param services              data collections
     */
    public PrepareSiteMembers(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.eventNameSiteMembersPrepared = EVENT_NAME_SITE_MEMBERS_PREPARED;
    }

    public void setMaxSiteMembers(int maxSiteMembers)
    {
        if(maxSiteMembers < 1)
        {
            throw new IllegalArgumentException("maxSiteMembers must be greater than 0");
        }
        this.maxSiteMembers = maxSiteMembers;
    }

    /**
     * Override the {@link #EVENT_NAME_SITE_MEMBERS_PREPARED default} event name when site members have been created.
     */
    public void setEventNameSiteMembersPrepared(String eventNameSiteMembersPrepared)
    {
        this.eventNameSiteMembersPrepared = eventNameSiteMembersPrepared;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        int membersCount = 0;

        long numSites = siteDataService.countSites(true);
        long maxMembersPerSite = (int)Math.floor(maxSiteMembers / numSites);

        long totalSiteMembersCount = siteDataService.countSiteMembers();
        long totalDiff = maxSiteMembers - totalSiteMembersCount;
        
        Iterator<String> networksIt = userDataService.getDomainsIterator();
        while(networksIt.hasNext())
        {
            String networkId = networksIt.next();

            // users from network
            Iterator<UserData> users = userDataService.getUsersByDomainIterator(networkId);

            // iterate through sites that exist in the given network on the Alfresco server
            Iterator<SiteData> sitesIt = siteDataService.sitesIterator(networkId, true);
            while(sitesIt.hasNext() && totalDiff > 0)
            {
                final SiteData site = (SiteData)sitesIt.next();
                long siteMembersCount = siteDataService.countSiteMembers(site.getSiteId());
                long diff = maxMembersPerSite - siteMembersCount;
                if(diff > 0)
                {
                    String siteNetworkId = site.getNetworkId();
                    if(!networkId.equals(siteNetworkId))
                    {
                        logger.warn("Unexpected site network mismatch: expected " + networkId + ", got " + siteNetworkId);
                        continue;
                    }

                    while(users.hasNext() && diff > 0 && totalDiff > 0)
                    {
                        UserData user = users.next();
                        String userId = user.getEmail();
                        SiteRole role = SiteRole.SiteContributor; // TODO more random, spread of roles?
                        if(!siteDataService.isSiteMember(site.getSiteId(), userId))
                        {
                            SiteMember siteMember = new SiteMember(userId, site.getSiteId(), site.getNetworkId(), role.toString());
                            siteDataService.addSiteMember(siteMember);
                            membersCount++;
    
                            diff--;
                            totalDiff--;
                        }
                    }
                }
            }
        }

        // We need an event to mark completion
        String msg = "Prepared " + membersCount + " site members";
        Event outputEvent = new Event(eventNameSiteMembersPrepared, 0L, msg);

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
