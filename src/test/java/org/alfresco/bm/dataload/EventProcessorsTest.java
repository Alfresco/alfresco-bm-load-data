/*
 * #%L
 * Alfresco Benchmark Load Data
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
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
 * #L%
 */
package org.alfresco.bm.dataload;

import com.mongodb.DB;
import com.mongodb.DBObject;
import org.alfresco.bm.BMDataLoadTest;
import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.common.session.MongoSessionService;
import org.alfresco.bm.common.util.junit.tools.MongoDBForTestsFactory;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.dataload.files.ScheduleSiteLoaders;
import org.alfresco.bm.dataload.sites.CreateSite;
import org.alfresco.bm.dataload.sites.PrepareSiteMembers;
import org.alfresco.bm.dataload.sites.PrepareSites;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataServiceImpl;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.site.SiteRole;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Derek Hulley
 * @see PrepareSites
 * @since 2.0
 */
@RunWith(JUnit4.class)
public class EventProcessorsTest
{
    private MongoDBForTestsFactory mongoFactory;
    private MongoSessionService sessionService;
    private SiteDataServiceImpl siteDataService;
    private UserDataServiceImpl userDataService;
    private FileFolderService fileFolderService;
    private DB db;

    @Before
    public void setUp() throws Exception
    {
        mongoFactory = new MongoDBForTestsFactory();
        db = mongoFactory.getObject();
        sessionService = new MongoSessionService(db, "sessions");
        sessionService.start();
        userDataService = new UserDataServiceImpl(db, "users");
        userDataService.afterPropertiesSet();
        siteDataService = new SiteDataServiceImpl(db, "sites", "siteMembers");
        siteDataService.afterPropertiesSet();
        fileFolderService = new FileFolderService(db, "filefolder");
        fileFolderService.afterPropertiesSet();

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
        assertEquals(0, fileFolderService.countEmptyFolders(""));
    }

    @Test
    public void prepareSites() throws Exception
    {
        StopWatch stopWatch = new StopWatch();
        PrepareSites processor = new PrepareSites(userDataService, siteDataService);
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals("Prepared 100 sites to reach a count of 100 valid sites.", result.getData());

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
        SiteMemberData member = siteDataService.randomSiteMember(null, DataCreationState.NotScheduled, null, SiteRole.SiteManager.toString());
        assertNotNull(member);
    }

    @Test
    public void scheduleSiteLoaders() throws Exception
    {
        prepareSiteMembersWithSites();
        List<SiteData> someSites = siteDataService.getSites(null, null, 0, 5);
        for (SiteData site : someSites)
        {
            String siteId = site.getSiteId();
            String docLibPath = "/" + CreateSite.PATH_SNIPPET_SITES + "/" + siteId + "/" + CreateSite.PATH_SNIPPET_DOCLIB;
            fileFolderService.createNewFolder(UUID.randomUUID().toString(), "", docLibPath);
        }

        StopWatch stopWatch = new StopWatch();
        ScheduleSiteLoaders processor = new ScheduleSiteLoaders(sessionService, fileFolderService, 5, 3, 100, 4, 100);
        EventResult result = processor.processEvent(null, stopWatch);
        assertEquals(5, result.getNextEvents().size());
        // A file loader for each folder
        assertEquals(5, fileFolderService.countEmptyFolders(""));
        assertEquals(Integer.valueOf(100), ((DBObject) result.getNextEvents().get(0).getData()).get(ScheduleSiteLoaders.FIELD_FILES_TO_CREATE));
        assertEquals(Integer.valueOf(0), ((DBObject) result.getNextEvents().get(0).getData()).get(ScheduleSiteLoaders.FIELD_FOLDERS_TO_CREATE));

        stopWatch = new StopWatch();
        processor = new ScheduleSiteLoaders(sessionService, fileFolderService, 5, 3, 0, 4, 100);
        result = processor.processEvent(null, stopWatch);
        // All the sessions doing the loading are still active, so just reschedule
        assertEquals(1, result.getNextEvents().size());

        stopWatch = new StopWatch();
        processor = new ScheduleSiteLoaders(sessionService, fileFolderService, 5, 3, 0, 5, 100);
        result = processor.processEvent(null, stopWatch);
        // One more loader is allowed, so we have a loader and a rescheduling
        assertEquals(2, result.getNextEvents().size());
        assertEquals(Integer.valueOf(0), ((DBObject) result.getNextEvents().get(0).getData()).get(ScheduleSiteLoaders.FIELD_FILES_TO_CREATE));
        assertEquals(Integer.valueOf(5), ((DBObject) result.getNextEvents().get(0).getData()).get(ScheduleSiteLoaders.FIELD_FOLDERS_TO_CREATE));
    }
}
