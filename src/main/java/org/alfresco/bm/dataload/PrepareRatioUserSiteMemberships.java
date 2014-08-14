package org.alfresco.bm.dataload;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteDataServiceImpl.UserSitesCount;
import org.alfresco.bm.site.SiteMember;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserData.UserCreationState;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares the site members collection such that, for a given set containing userRatio (in percent) of created users, each user in that set is a member of at least minSitesPerUser sites.
 * 
 * @author steveglover
 *
 */
public class PrepareRatioUserSiteMemberships extends AbstractEventProcessor
{
    public static final String EVENT_NAME_SITE_MEMBERS_PREPARED = "siteMembersPrepared";
            
    private String eventNameSiteMembersPrepared;
    private SiteDataService siteDataService;
    private UserDataService userDataService;
    private int minSitesPerUser;
    private float userRatio;

    /**
     * @param services              data collections
     */
    public PrepareRatioUserSiteMemberships(SiteDataService siteDataService, UserDataService userDataService, float userRatio, int minSitesPerUser)
    {
        super();
        this.siteDataService = siteDataService;
        this.userDataService = userDataService;
        if(userRatio < 0.0 || userRatio > 100.0)
        {
            throw new IllegalArgumentException("userRatio must be between 0.0 and 100.0 inclusive");
        }
        this.userRatio = userRatio;
        this.minSitesPerUser = minSitesPerUser;
        this.eventNameSiteMembersPrepared = EVENT_NAME_SITE_MEMBERS_PREPARED;
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

        List<UserSitesCount> results = siteDataService.userSitesCounts();
        long userCount = userDataService.countUsers(null, UserCreationState.Created);
        long numUsers = (long)(userRatio/100.0 * userCount);

        long counter = 0;
        for(UserSitesCount userSitesCount : results)
        {
            String username = userSitesCount.getUsername();
            UserData user = userDataService.findUserByEmail(username);

            long count = (long)userSitesCount.getCount();

            if(count < minSitesPerUser)
            {
                Iterator<SiteData> sitesIt = siteDataService.sitesIterator(user.getDomain(), true);

                logger.debug("Creating memberships for user " + username);

                while(count < minSitesPerUser && sitesIt.hasNext())
                {
                    SiteData site = sitesIt.next();
                    if(!siteDataService.isSiteMember(site.getSiteId(), username))
                    {
                        String role = SiteRole.SiteContributor.toString(); // TODO more random, spread of roles?
                        SiteMember siteMember = new SiteMember(username, site.getSiteId(), site.getNetworkId(), role);
                        siteDataService.addSiteMember(siteMember);

                        logger.debug("Adding site member " + siteMember);

                        membersCount++;
                        count++;
                    }
                }
            }

            if(counter++ >= numUsers)
            {
                break;
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
