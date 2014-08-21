package org.alfresco.bm.dataload;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
 * <p/>
 * The number of site members to create is driven by: <br/>
 * maxSites: if -1, uses all created sites in the sites collection maxMembersPerSite: must be greater than 0
 * <p/>
 * TODO: only site members from same domain as site at present.
 * 
 * @author steveglover
 * @author Derek Hulley
 */
public class PrepareSiteMembers extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_MEMBERS_PREPARED = "siteMembersPrepared";
    public static final int DEFAULT_SITE_MEMBERS = 20;
            
    private String eventNameSiteMembersPrepared;
    private UserDataService userDataService;
    private SiteDataService siteDataService;

    private int siteMembers;

    /**
     * @param services              data collections
     */
    public PrepareSiteMembers(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.eventNameSiteMembersPrepared = EVENT_NAME_SITE_MEMBERS_PREPARED;
        this.siteMembers = DEFAULT_SITE_MEMBERS;
    }

    public void setSiteMembers(int siteMembers)
    {
        if (siteMembers < 1)
        {
            throw new IllegalArgumentException("maxSiteMembers must be greater than 0");
        }
        this.siteMembers = siteMembers;
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
        
        Iterator<String> domains = userDataService.getDomainsIterator();
        while (domains.hasNext())
        {
            String domain = domains.next();

            // iterate through sites that exist in the given domain on the Alfresco server
            int skipSites = 0;
            List<SiteData> sites = siteDataService.getSites(Boolean.TRUE, domain, skipSites, 200);
            for (SiteData site : sites)
            {
                skipSites++;
                
                String siteId = site.getSiteId();
                long siteMembersCount = siteDataService.countSiteMembers(siteId);
                long toCreate = maxMembersPerSite - siteMembersCount;
                if (toCreate <= 0)
                {
                    // This site has enough members
                    continue;
                }
                
                // Loop through available users in the domain
                int skipUsers = 0;
                List<UserData> users = userDataService.getUsersInDomain(domain, skipUsers, 200);
                for (UserData user : users)
                {
                    skipUsers++;
                    String username = user.getUsername();
                    String role = SiteRole.SiteContributor.toString();              // TODO more random, spread of roles?
                    // If the user is already a member, then move on
                    if (siteDataService.isSiteMember(siteId, username))
                    {
                        continue;
                    }
                    SiteMember siteMember = new SiteMember(username, site.getSiteId(), site.getDomain(), role);
                    siteDataService.addSiteMember(siteMember);
                    toCreate--;
                }
                users = userDataService.getUsersInDomain(domain, skipUsers, 200);
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
