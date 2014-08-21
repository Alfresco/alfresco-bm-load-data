package org.alfresco.bm.dataload.rm;

import java.util.Random;

/**
 * Site membership role based on record management site.
 * 
 * @author Michael Suzuki
 */
public enum RMRole
{
    User,
    PowerUser,
    RecordsManager,
    SecurityOfficer,
    Administrator;
    
    private static Random random = new Random();
    
    public static RMRole getRandomRole()
    {
        return values()[random.nextInt(values().length)];
    }
}
