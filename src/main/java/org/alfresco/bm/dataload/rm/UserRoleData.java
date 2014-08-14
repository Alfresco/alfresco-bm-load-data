/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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

import java.io.Serializable;

import org.alfresco.bm.site.Role;


/**
 * Simple DTO representing a role a user has on a site.
 * 
 * @author Michael Suzuki
 * @since 1.4
 */
public class UserRoleData implements Serializable
{
    private static final long serialVersionUID = -2241893659787720414L;
    private String username;
    private String siteId;
    private Role role;

    public UserRoleData(final String username, final String siteId, final Role role)
    {
        this.username = username;
        this.siteId = siteId;
        this.role = role;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public Role getRole()
    {
        return role;
    }
    public void setRole(Role role)
    {
        this.role = role;
    }
    
    public String getSiteId()
    {
        return siteId;
    }

    public void setSiteId(String siteId)
    {
        this.siteId = siteId;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("username=");
        builder.append(username);
        builder.append(", siteId=");
        builder.append(siteId);
        builder.append(", role=");
        builder.append(role.toString());
        builder.append("]");
        return builder.toString();
    }
    
}
