package management;

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
    public static Session startSession(String username, String password, String cmisBindingUrl, OperationContext cmisCtx)
    {
     // Build session parameters
        Map<String, String> parameters = new HashMap<String, String>();
        // Browser binding
        parameters.put(SessionParameter.BINDING_TYPE, BindingType.BROWSER.value());
        parameters.put(SessionParameter.BROWSER_URL, cmisBindingUrl);
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
