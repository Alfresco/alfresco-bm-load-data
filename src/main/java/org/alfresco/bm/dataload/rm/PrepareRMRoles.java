package org.alfresco.bm.dataload.rm;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMember;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;

/**
 * Prepares record management site users with roles relating to record management.
 * The possible roles that can be assigned to an existing user collection:
 * <ul>
 *     <li>Records Management Administrator</li>
 *     <li>Records Management Power User</li>
 *     <li>Records Management Records Manager</li>
 *     <li>Records Management Security Officer</li>
 *     <li>Records Management User</li>
 * </ul>
 * @author Michael Suzuki
 * @version 1.4
 *
 */
public class PrepareRMRoles extends AbstractEventProcessor
{
    public static final String EVENT_NAME_PREPARE_RM_ROLES = "rmRolesPrepared";
            
    private String eventNamePrepareRMRoles;
    private UserDataService userDataService;
    private SiteDataService siteDataService;


    /**
     * @param services              data collections
     */
    public PrepareRMRoles(UserDataService userDataService, SiteDataService siteDataService)
    {
        super();
        this.userDataService = userDataService;
        this.siteDataService = siteDataService; 
        this.eventNamePrepareRMRoles = EVENT_NAME_PREPARE_RM_ROLES;
    }

    /**
     * Override the {@link #EVENT_NAME_PREPARE_RM_ROLES default} event name when site members have been created.
     */
    public void setEventNameSiteMembersPrepared(String eventNameSiteMembersPrepared)
    {
        this.eventNamePrepareRMRoles = eventNameSiteMembersPrepared;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        
        List<Event> nextEvents = new ArrayList<Event>();
        //Find rm site
        SiteData rmSite = siteDataService.findSiteBySiteId("rm");
        if(rmSite == null)
        {
            // There is nothing more to do
            Event doneEvent = new Event("No record manamgent site found in site collection", System.currentTimeMillis(), null);
            nextEvents.add(doneEvent);
        }
        //Find all users that are not in rm
        int totalUsers = (int) userDataService.countUsers(true);
        List<UserData> users = userDataService.getCreatedUsers(0, totalUsers);
        if(users.size() == 0)
        {
            // There is nothing more to do
            Event doneEvent = new Event(eventNamePrepareRMRoles, System.currentTimeMillis(), null);
            nextEvents.add(doneEvent);
        }
        else
        {
            for(UserData user : users)
            {
                RMRole role = RMRole.getRandomRole();
                SiteMember siteMember = siteDataService.getSiteMember(rmSite.getSiteId(), user.getUsername(), role);
                if(siteMember == null)
                {
                    siteMember = new SiteMember(user.getUsername(), rmSite.getSiteId(), rmSite.getNetworkId(), role.toString());
                    //persist user and with an rm role
                    siteDataService.addSiteMember(siteMember);
                }
                //site member is false which means it needs to be created
                if(!siteMember.isCreated())
                {
                    UserRoleData data = new UserRoleData(user.getUsername(), rmSite.getSiteId(), role);
                    Event nextEvent = new Event(eventNamePrepareRMRoles, System.currentTimeMillis(), data);
                    nextEvents.add(nextEvent);
                }
            }
        }
        
        String msg = "Prepared " + users.size() + "users as rm site members";
        // Return messages + next events
        EventResult result = new EventResult(msg, nextEvents, true);

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }
        return result;
    }
}
