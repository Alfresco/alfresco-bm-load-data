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
package org.alfresco.management;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * CMIS session encapsulation
 * 
 * @author Frank Becker
 * @since 2.6
 */
public class CMIS
{
    /** Logger for the class */
    private static Log logger = LogFactory.getLog(CMIS.class);

    /**
     * Starts a CMIS session
     * 
     * @param username            (String) user name to use for CMIS session
     * @param password            (String) password for user
     * @param cmisBindingUrl      (String) the CMIS <b>browser</b> binding URL
     * @param cmisCtx             (String) the operation context for all calls made by the session
     * 
     * @return (Session)CMIS session
     */
    public static Session startSession(String username, String password, String cmisBindingType, String cmisBindingUrl, OperationContext cmisCtx)
    {
     // Build session parameters
        Map<String, String> parameters = new HashMap<String, String>();
        // Browser binding
        if (cmisBindingType != null && cmisBindingType.equals(BindingType.ATOMPUB.value()))
        {
            parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
            parameters.put(SessionParameter.ATOMPUB_URL, cmisBindingUrl);
        }
        else if (cmisBindingType != null && cmisBindingType.equals(BindingType.BROWSER.value()))
        {
            parameters.put(SessionParameter.BINDING_TYPE, BindingType.BROWSER.value());
            parameters.put(SessionParameter.BROWSER_URL, cmisBindingUrl);
        }
        else
        {
            logger.error("Unsupported CMIS binding type: " + cmisBindingType);
            return null;
        }

        // User
        parameters.put(SessionParameter.USER, username);
        parameters.put(SessionParameter.PASSWORD, password);

        // First check if we need to choose a repository
        SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
        List<Repository> repositories = sessionFactory.getRepositories(parameters);
        if (repositories.size() == 0)
        {
            return null;
        }
        String repositoryIdFirst = repositories.get(0).getId();
        parameters.put(SessionParameter.REPOSITORY_ID, repositoryIdFirst);

        // Create the session
        Session session = SessionFactoryImpl.newInstance().createSession(parameters);
        session.setDefaultContext(cmisCtx);

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug("Created CMIS session with user '" + username + "' to URL: " + cmisBindingUrl);
        }
        return session;
    }
}
