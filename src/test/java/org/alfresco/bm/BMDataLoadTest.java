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

import java.util.Properties;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteDataServiceImpl;
import org.alfresco.bm.tools.BMTestRunner;
import org.alfresco.bm.tools.BMTestRunnerListenerAdaptor;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.context.ApplicationContext;

import com.mongodb.DB;
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
    private String mongoTestHost;
    private String usersCollection;
    private String sitesCollection;
    private String siteMembersCollection;
    private UserDataService userDataService;
    private SiteDataService siteDataService;
    
    @Before
    public void setUpDB() throws Exception
    {
        mongoDBForTestsFactory = new MongoDBForTestsFactory();
        db = mongoDBForTestsFactory.getObject();
        uriWithoutDB = mongoDBForTestsFactory.getMongoURIWithoutDB();
        mongoTestHost = new MongoClientURI(uriWithoutDB).getHosts().get(0);
        
        usersCollection = "mirror.test.users";
        sitesCollection = "mirror.test.sites";
        siteMembersCollection = "mirror.test.siteMembers";
        
        userDataService = new UserDataServiceImpl(db, usersCollection);
        siteDataService = new SiteDataServiceImpl(db, sitesCollection, siteMembersCollection);
        // Create some users
        for (int i = 0; i < 10; i++)
        {
            String domain = String.format("D%2d", i);
            for (int j = 0; j < 100; j++)
            {
                String username = String.format("U%3d", j);
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
        
        BMTestRunner testRunner = new BMTestRunner(60000L);
        testRunner.addListener(this);
        testRunner.run(null, mongoTestHost, props);
    }

    @Override
    public void testRunStarted(ApplicationContext testCtx, String test, String run)
    {
        assertEquals(1000L, userDataService.countUsers(null, DataCreationState.Created));
    }

    @Override
    public void testRunFinished(ApplicationContext testCtx, String test, String run)
    {
        // Expect 10 sites per domain
        assertEquals(100L, siteDataService.countSites(null, null));
        assertEquals(10L, siteDataService.countSites("D01", DataCreationState.Failed));
    }
}
