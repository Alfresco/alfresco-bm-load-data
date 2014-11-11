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
package org.alfresco.bm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Properties;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.dataload.CreateSiteMembers;
import org.alfresco.bm.dataload.CreateSites;
import org.alfresco.bm.event.ResultService;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteDataServiceImpl;
import org.alfresco.bm.test.TestRunServicesCache;
import org.alfresco.bm.tools.BMTestRunner;
import org.alfresco.bm.tools.BMTestRunnerListenerAdaptor;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.alfresco.mongo.MongoDBFactory;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.context.ApplicationContext;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

/**
 * Initiates the application context associated with this test.
 * 
 * @author Derek Hulley
 * @author Michael Suzuki
 * @since 2.0
 */
@RunWith(JUnit4.class)
public class BMDataLoadTest extends BMTestRunnerListenerAdaptor
{
    private MongoDBForTestsFactory mongoDBForTestsFactory;
    private DB db;
    private String uriWithoutDB;
    private String mongoHost;
    private String usersCollection;
    private String sitesCollection;
    private String siteMembersCollection;
    private UserDataServiceImpl userDataService;
    private SiteDataService siteDataService;
    
    @Before
    public void setUpDB() throws Exception
    {
        mongoDBForTestsFactory = new MongoDBForTestsFactory();
        uriWithoutDB = mongoDBForTestsFactory.getMongoURIWithoutDB();
        mongoHost = new MongoClientURI(uriWithoutDB).getHosts().get(0);
        
        // Connect to the test DB
        db = new MongoDBFactory(new MongoClient(mongoHost), "bm20-data").getObject();
        
        usersCollection = "mirror.test.users";
        sitesCollection = "mirror.test.sites";
        siteMembersCollection = "mirror.test.siteMembers";
        
        userDataService = new UserDataServiceImpl(db, usersCollection);
        userDataService.afterPropertiesSet();
        siteDataService = new SiteDataServiceImpl(db, sitesCollection, siteMembersCollection);
        // Create some users
        createSomeUsers(userDataService, 10, 10);
    }
    
    /**
     * Helper method to create some users in the created state
     */
    public static void createSomeUsers(UserDataService userDataService, int domains, int usersPerDomain)
    {
        for (int i = 0; i < domains; i++)
        {
            String domain = String.format("D%02d", i);
            for (int j = 0; j < usersPerDomain; j++)
            {
                String username = String.format("U%03d@%s", j, domain);
                UserData user = new UserData();
                user.setCreationState(DataCreationState.Created);
                user.setDomain(domain);
                user.setEmail(username + "@" + domain);
                user.setFirstName("U");
                user.setLastName(String.format("%3d", j));
                user.setPassword(username);
                user.setUsername(username);
                userDataService.createNewUser(user);
            }
        }
    }
    
    @After
    public void tearDownDB() throws Exception
    {
        mongoDBForTestsFactory.destroy();
    }
    
    @Test
    public void testBasic() throws Exception
    {
        Properties props = new Properties();
        props.setProperty("mirror.users", usersCollection);
        props.setProperty("mirror.sites", sitesCollection);
        props.setProperty("mirror.siteMembers", siteMembersCollection);
        props.setProperty("rm.enabled", "true");
        props.setProperty("load.sitesCount", "10");
        props.setProperty("load.usersPerSite", "10");
        
        BMTestRunner testRunner = new BMTestRunner(3000000L);
        testRunner.addListener(this);
        testRunner.run(mongoHost, null, props);
    }

    @Override
    public synchronized void testRunFinished(ApplicationContext testCtx, String test, String run)
    {
        // A slight pause is required here so that MongoDB will return the most up to date counts
        try { this.wait(500L); } catch (Exception e) {}
        
        TestRunServicesCache services = testCtx.getBean(TestRunServicesCache.class);
        ResultService resultService = services.getResultService(test, run);
        
        List<String> eventNames = resultService.getEventNames();
        assertEquals(13, eventNames.size());
        
        assertEquals("Expected 100 users + RM. ", 101L, userDataService.countUsers(null, DataCreationState.Created));
        assertEquals("Expected 10 sites + RM. ", 11L, siteDataService.countSites(null, null));
        assertEquals("Expected 10 user, user per site, including a manager and 49 RM users. ", 60L, siteDataService.countSiteMembers(null, null));
        assertEquals("All site members should be failures except the RM admin. ", 59L, siteDataService.countSiteMembers(null, DataCreationState.Failed));
        assertEquals("Only the RM admin was considered to be created. ", 1L, siteDataService.countSiteMembers(null, DataCreationState.Created));
        assertNotNull(siteDataService.getSite("rm"));
        assertEquals(DataCreationState.Created, siteDataService.getSite("rm").getCreationState());
        
        assertEquals(10L, resultService.countResultsByEventName(CreateSites.DEFAULT_EVENT_NAME_CREATE_SITE));
        //No site member should be created as there are no successfully created sites.
        assertEquals(0L, resultService.countResultsByEventName(CreateSiteMembers.DEFAULT_EVENT_NAME_CREATE_SITE_MEMBER));
        assertEquals(1L, resultService.countResultsByEventName("prepareRM"));
        assertEquals(1L, resultService.countResultsByEventName("prepareRMRoles"));
        assertEquals(49L, resultService.countResultsByEventName("assignRMRole"));
    }
}
