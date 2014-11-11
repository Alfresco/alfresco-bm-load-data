/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.bm.dataload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.alfresco.bm.BMDataLoadTest;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.dataload.rm.PrepareRM;
import org.alfresco.bm.dataload.rm.PrepareRMRoles;
import org.alfresco.bm.dataload.rm.RMRole;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataServiceImpl;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.mongodb.DB;

/**
 * @see PrepareSites
 * 
 * @author Derek Hulley
 * @since 2.0
 */
@RunWith(JUnit4.class)
public class EventProcessorsTest
{
    private MongoDBForTestsFactory mongoFactory;
    private SiteDataServiceImpl siteDataService;
    private UserDataServiceImpl userDataService;
    private DB db;
    
    @Before
    public void setUp() throws Exception
    {
        mongoFactory = new MongoDBForTestsFactory();
        db = mongoFactory.getObject();
        userDataService = new UserDataServiceImpl(db, "users");
        userDataService.afterPropertiesSet();
        siteDataService = new SiteDataServiceImpl(db, "sites", "siteMembers");
        siteDataService.afterPropertiesSet();
        
        // Create a bunch of users
        BMDataLoadTest.createSomeUsers(userDataService, 10, 20);
    }
    
    @After
    public void tearDown() throws Exception
    {
        mongoFactory.destroy();
    }
    
    @Test
    public void startState()
    {
        assertEquals(200, userDataService.countUsers(null, DataCreationState.Created));
        assertEquals(0, siteDataService.countSites(null, null));
        assertEquals(0, siteDataService.countSiteMembers(null, null));
    }
    
    @Test
    public void prepareSites() throws Exception
    {
        StopWatch stopWatch = new StopWatch();
        PrepareSites processor = new PrepareSites(userDataService, siteDataService);
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals("Prepared 100 sites.", result.getData());
        
        assertEquals(PrepareSites.DEFAULT_SITES_COUNT, siteDataService.countSites(null, DataCreationState.NotScheduled));
        assertEquals(PrepareSites.DEFAULT_SITES_COUNT, siteDataService.countSiteMembers(null, DataCreationState.NotScheduled));
        SiteData site = siteDataService.randomSite(null, DataCreationState.NotScheduled);
        assertNotNull(site);
        String siteId = site.getSiteId();
        List<SiteMemberData> siteManagers = siteDataService.getSiteMembers(siteId, DataCreationState.NotScheduled, SiteRole.SiteManager.toString(), 0, 10);
        assertEquals(1, siteManagers.size());
    }
    
    @Test
    public void prepareSiteMembersNoSites() throws Exception
    {
        StopWatch stopWatch = new StopWatch();
        PrepareSiteMembers processor = new PrepareSiteMembers(userDataService, siteDataService);
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals(1, result.getNextEvents().size());
        assertEquals("Prepared 0 site members", result.getData());
    }
    
    @Test
    public void prepareSiteMembersWithSites() throws Exception
    {
        prepareSites();
        
        StopWatch stopWatch = new StopWatch();
        PrepareSiteMembers processor = new PrepareSiteMembers(userDataService, siteDataService);
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals(1, result.getNextEvents().size());
        
        long siteMemberCount = siteDataService.countSiteMembers(null, DataCreationState.NotScheduled);
        assertEquals("Unscheduled site member count incorrect. ", 100L, siteMemberCount);
        SiteMemberData member = siteDataService.randomSiteMember(
                null, DataCreationState.NotScheduled, null,
                SiteRole.SiteManager.toString());
        assertNotNull(member);
    }
    
    @Test
    public void prepareRM() throws Exception
    {
        StopWatch stopWatch = new StopWatch();
        PrepareRM processor = new PrepareRM(userDataService, siteDataService, true, "bob", "secret");
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals(1, result.getNextEvents().size());
        
        // Check RM admin user
        assertNotNull(userDataService.findUserByUsername("bob"));
        assertEquals(DataCreationState.Created, userDataService.findUserByUsername("bob").getCreationState());
        // Check RM site
        assertNotNull(siteDataService.getSite(PrepareRM.RM_SITE_ID));
        assertEquals(DataCreationState.Created, siteDataService.getSite(PrepareRM.RM_SITE_ID).getCreationState());
        // Check RM admin member
        assertNotNull(siteDataService.getSiteMember(PrepareRM.RM_SITE_ID, "bob"));
        assertEquals(DataCreationState.Created, siteDataService.getSiteMember(PrepareRM.RM_SITE_ID, "bob").getCreationState());
        assertEquals(RMRole.Administrator.toString(), siteDataService.getSiteMember(PrepareRM.RM_SITE_ID, "bob").getRole());
    }
    
    @Test
    public void prepareRMRoles() throws Exception
    {
        // Should be no scheduled creations
        assertEquals(0L, siteDataService.countSiteMembers(PrepareRM.RM_SITE_ID, null));

        prepareRM();
        StopWatch stopWatch = new StopWatch();
        PrepareRMRoles processor = new PrepareRMRoles(userDataService, siteDataService);
        EventResult result = processor.processEvent(null, stopWatch);

        assertEquals("The RM role already existed, so there must be 50 events", PrepareRMRoles.DEFAULT_USER_COUNT, result.getNextEvents().size());
        Event eventOne = result.getNextEvents().get(0);
        Event eventTwo = result.getNextEvents().get(1);
        assertEquals(PrepareRMRoles.DEFAULT_ASSIGNMENT_DELAY, eventTwo.getScheduledTime() - eventOne.getScheduledTime());

        // All users should be scheduled for assignment
        assertEquals("The RM admin would not need scheduling. ",
                PrepareRMRoles.DEFAULT_USER_COUNT - 1, siteDataService.countSiteMembers(PrepareRM.RM_SITE_ID, DataCreationState.Scheduled));
    }
}
