package org.alfresco.bm.dataload.rm;

import java.net.URLEncoder;
import java.util.Collections;

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
 * @version 1.4
 *
 */
public class AssignRole extends AuthenticatedHttpEventProcessor
{
    public static final String EVENT_NAME_ASSIGN_RM_ROLES = "rmRoleAssigned";
    private static final String CREATE_RM_SITE_URL = "/alfresco/service/api/rm/roles/%s/authorities/%s";
    private SiteDataService siteDataService;

    /**
     * @param services              data collections
     */
    public AssignRole(
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
        UserRoleData data = (UserRoleData) event.getDataObject();
        String username = URLEncoder.encode(data.getUsername(),"UTF-8");
        String role = data.getRole().toString();
        
        if(logger.isTraceEnabled())
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
            if (httpStatus.getStatusCode() == HttpStatus.SC_CONFLICT )
            {
                // site already exist
                return new EventResult(
                        String.format("Ignoring assign rm role %s, already present in alfresco: ", role),
                        Collections.<Event> emptyList());
            }
            else
            {
                throw new RuntimeException(String.format(
                        "Assign an rm role :%S to user %s failed, REST-call resulted in status:%d with error %s ",
                        role,
                        username,
                        httpStatus.getStatusCode(),
                        httpStatus.getReasonPhrase()));
            }
        }
        
        siteDataService.markSiteMemberCreated(data.getSiteId(), data.getUsername(), data.getRole(), true);
        return new EventResult(String.format("RM role %s assigned to user %s", role, data.getUsername()), true);
    }
}
