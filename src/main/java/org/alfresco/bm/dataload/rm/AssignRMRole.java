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
package org.alfresco.bm.dataload.rm;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.http.AuthenticatedHttpEventProcessor;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
import org.alfresco.http.SimpleHttpRequestCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;

import com.mongodb.DBObject;

/**
 * Assign a record management based role to a given user.
 * The possible roles that will be assigned to existing user collection:
 * <ul>
 *     <li>Records Management Administrator</li>
 *     <li>Records Management Power User</li>
 *     <li>Records Management Records Manager</li>
 *     <li>Records Management Security Officer</li>
 *     <li>Records Management User</li>
 * </ul>
 * @author Michael Suzuki
 * @author Derek Hulley
 * @version 1.4
 */
public class AssignRMRole extends AuthenticatedHttpEventProcessor
{
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_USERNAME = "username";
    
    public static final String EVENT_NAME_ASSIGN_RM_ROLES = "rmRoleAssigned";
    private static final String CREATE_RM_SITE_URL = "/alfresco/service/api/rm/roles/%s/authorities/%s";
    private SiteDataService siteDataService;

    /**
     * @param services              data collections
     */
    public AssignRMRole(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            SiteDataService siteDataService)
    {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        this.siteDataService = siteDataService;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        DBObject dataObj = (DBObject) event.getDataObject();
        if (dataObj == null || !dataObj.containsField(FIELD_ROLE) || !dataObj.containsField(FIELD_USERNAME))
        {
            throw new IllegalStateException("Insufficient data for event: " + dataObj);
        }
        String role = (String) dataObj.get(FIELD_ROLE);
        String username = (String) dataObj.get(FIELD_USERNAME);
        
        if (logger.isTraceEnabled())
        {
            logger.trace(String.format("Assign role %s to user %s", role, username));
        }

        HttpPost assignRoleRequest = new HttpPost(getFullUrlForPath(String.format(CREATE_RM_SITE_URL, role, username)));
        HttpResponse httpResponse = executeHttpMethodAsAdmin(
                assignRoleRequest,
                SimpleHttpRequestCallback.getInstance());
        
        StatusLine httpStatus = httpResponse.getStatusLine();
        // Expecting "OK" status
        if (httpStatus.getStatusCode() != HttpStatus.SC_OK)
        {
            siteDataService.setSiteMemberCreationState(PrepareRM.RM_SITE_ID, username, DataCreationState.Failed);
            if (httpStatus.getStatusCode() == HttpStatus.SC_CONFLICT )
            {
                // site already exist
                return new EventResult(
                        String.format("Ignoring assign rm role %s, already present in alfresco: ", role),
                        true);
            }
            else
            {
                String msg = String.format(
                        "Assign an rm role :%S to user %s failed, REST-call resulted in status:%d with error %s ",
                        role,
                        username,
                        httpStatus.getStatusCode(),
                        httpStatus.getReasonPhrase());
                return new EventResult(msg, false);
            }
        }
        else
        {
            siteDataService.setSiteMemberCreationState(PrepareRM.RM_SITE_ID, username, DataCreationState.Created);
            return new EventResult(
                    String.format("RM role %s assigned to user %s", role, username),
                    true);
        }
    }
}
