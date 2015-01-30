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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alfresco.bm.cm.FileFolderService;
import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.session.SessionService;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Schedule the {@link #EVENT_NAME_LOAD_SITE_FOLDER folder} loaders and {@link #EVENT_NAME_SCHEDULE_LOADERS reschedule self}
 * until all folders have correct number of files and subfolders.
 * 
 * @author Derek Hulley
 * @since 2.0
 */
public class ScheduleSiteLoaders extends AbstractEventProcessor
{
    public static final String FIELD_CONTEXT = "context";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_FOLDERS_TO_CREATE = "foldersToCreate";
    public static final String FIELD_FILES_TO_CREATE = "filesToCreate";
    
    public static final String EVENT_NAME_LOAD_SITE_FOLDERS = "loadSiteFolders";
    public static final String EVENT_NAME_LOAD_SITE_FILES = "loadSiteFiles";
    public static final String EVENT_NAME_SCHEDULE_LOADERS = "scheduleLoaders";
    public static final String EVENT_NAME_LOADING_COMPLETE = "loadingComplete";
    
    private final SessionService sessionService;
    private final FileFolderService fileFolderService;
    private final int subfolders;
    private final int maxLevel;
    private final int filesPerFolder;
    private final int maxActiveLoaders;
    private long loadCheckDelay;
    private String eventNameLoadSiteFolders;
    private String eventNameLoadSiteFiles;
    private String eventNameScheduleLoaders;
    private String eventNameLoadingComplete;

    /**
     * @param activeSiteLoaders                 the number of concurrently-active processes site loaders
     */
    public ScheduleSiteLoaders(
            SessionService sessionService, FileFolderService fileFolderService,
            int subfolders, int maxDepth, int filesPerFolder,
            int maxActiveLoaders, long loadCheckDelay)
    {
        super();
        
        this.sessionService = sessionService;
        this.fileFolderService = fileFolderService;

        this.subfolders = subfolders;
        this.maxLevel = maxDepth + 3;      // Add levels for "/Sites<L1>/siteId<L2>/documentLibrary<L3>"
        this.filesPerFolder = filesPerFolder;
        
        this.maxActiveLoaders = maxActiveLoaders;
        this.loadCheckDelay = loadCheckDelay;
        
        this.eventNameLoadSiteFolders = EVENT_NAME_LOAD_SITE_FOLDERS;
        this.eventNameLoadSiteFiles = EVENT_NAME_LOAD_SITE_FILES;
        this.eventNameScheduleLoaders = EVENT_NAME_SCHEDULE_LOADERS;
        this.eventNameLoadingComplete = EVENT_NAME_LOADING_COMPLETE;
    }

    /**
     * Override the {@link #EVENT_NAME_LOAD_SITE_FOLDERS default} output event name
     */
    public void setEventNameLoadSiteFolders(String eventNameLoadSiteFolders)
    {
        this.eventNameLoadSiteFolders = eventNameLoadSiteFolders;
    }

    /**
     * Override the {@link #EVENT_NAME_LOAD_SITE_FILES default} output event name
     */
    public void setEventNameLoadSiteFiles(String eventNameLoadSiteFiles)
    {
        this.eventNameLoadSiteFiles = eventNameLoadSiteFiles;
    }
    
    /**
     * Override the {@link #EVENT_NAME_SCHEDULE_LOADERS default} output event name
     */
    public void setEventNameScheduleLoaders(String eventNameScheduleLoaders)
    {
        this.eventNameScheduleLoaders = eventNameScheduleLoaders;
    }

    /**
     * Override the {@link #EVENT_NAME_LOADING_COMPLETE default} output event name
     */
    public void setEventNameLoadingComplete(String eventNameLoadingComplete)
    {
        this.eventNameLoadingComplete = eventNameLoadingComplete;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        // Are there still sessions active?
        long sessionCount = sessionService.getActiveSessionsCount();
        int toCreate = maxActiveLoaders - (int) sessionCount;
        
        List<Event> nextEvents = new ArrayList<Event>(maxActiveLoaders);

        int skip = 0;
        int limit = 100;
        // Find folders at the deepest level and schedule file-only loads
        while (nextEvents.size() < toCreate)
        {
            // Get folders needing loading
            List<FolderData> emptyFolders = fileFolderService.getFoldersByCounts(
                    "",
                    null, Long.valueOf(maxLevel),
                    null, null,                                 // Ignore folder limits
                    0L, Long.valueOf(filesPerFolder-1),         // Get folders that still need files
                    skip, limit);
            skip += limit;
            if (emptyFolders.size() == 0)
            {
                // The folders were populated in the mean time
                break;
            }
            // Schedule a load for each folder 
            for (FolderData emptyFolder : emptyFolders)
            {
                int filesToCreate = filesPerFolder - (int) emptyFolder.getFileCount();
                try
                {
                    // Create a lock folder that has too many files and folders so that it won't be picked up
                    // by this process in subsequent trawls
                    String lockPath = emptyFolder.getPath() + "/locked";
                    FolderData lockFolder = new FolderData(
                            UUID.randomUUID().toString(),
                            "", lockPath,
                            Long.MAX_VALUE, Long.MAX_VALUE);
                    fileFolderService.createNewFolder(lockFolder);
                    // We locked this, so the load can be scheduled.
                    // The loader will remove the lock when it completes
                    DBObject loadData = BasicDBObjectBuilder.start()
                            .add(FIELD_CONTEXT, emptyFolder.getContext())
                            .add(FIELD_PATH, emptyFolder.getPath())
                            .add(FIELD_FOLDERS_TO_CREATE, Integer.valueOf(0))
                            .add(FIELD_FILES_TO_CREATE, Integer.valueOf(filesToCreate))
                            .get();
                    Event loadEvent = new Event(eventNameLoadSiteFiles, loadData);
                    nextEvents.add(loadEvent);
                }
                catch (Exception e)
                {
                    // The lock was already applied; find another
                    continue;
                }
                // Check if we have enough
                if (nextEvents.size() >= toCreate)
                {
                    break;
                }
            }
        }
        
        skip = 0;
        limit = 100;
        // Target folders that need subfolders
        while (nextEvents.size() < toCreate)
        {
            // Get folders needing loading
            List<FolderData> emptyFolders = fileFolderService.getFoldersByCounts(
                    "",
                    null, Long.valueOf(maxLevel - 1),
                    0L, Long.valueOf(subfolders-1),             // Get folders that still need folders
                    null, null,                                 // Ignore file limits
                    skip, limit);
            skip += limit;
            if (emptyFolders.size() == 0)
            {
                // The folders were populated in the mean time
                break;
            }
            // Schedule a load for each folder 
            for (FolderData emptyFolder : emptyFolders)
            {
                int foldersToCreate = subfolders - (int) emptyFolder.getFolderCount();
                try
                {
                    // Create a lock folder that has too many files and folders so that it won't be picked up
                    // by this process in subsequent trawls
                    String lockPath = emptyFolder.getPath() + "/locked";
                    FolderData lockFolder = new FolderData(
                            UUID.randomUUID().toString(),
                            "", lockPath,
                            Long.MAX_VALUE, Long.MAX_VALUE);
                    fileFolderService.createNewFolder(lockFolder);
                    // We locked this, so the load can be scheduled.
                    // The loader will remove the lock when it completes
                    DBObject loadData = BasicDBObjectBuilder.start()
                            .add(FIELD_CONTEXT, emptyFolder.getContext())
                            .add(FIELD_PATH, emptyFolder.getPath())
                            .add(FIELD_FOLDERS_TO_CREATE, Integer.valueOf(foldersToCreate))
                            .add(FIELD_FILES_TO_CREATE, Integer.valueOf(0))
                            .get();
                    Event loadEvent = new Event(eventNameLoadSiteFolders, loadData);
                    nextEvents.add(loadEvent);
                }
                catch (Exception e)
                {
                    // The lock was already applied; find another
                    continue;
                }
                // Check if we have enough
                if (nextEvents.size() >= toCreate)
                {
                    break;
                }
            }
        }
        
        // If there are no events, then we have finished
        String msg = null;
        if (nextEvents.size() == 0)
        {
            // There are no folders to load
            Event nextEvent = new Event(eventNameLoadingComplete, null);
            nextEvents.add(nextEvent);
            msg = "Loading completed.  Raising 'done' event.";
        }
        else
        {
            // Reschedule self
            Event nextEvent = new Event(eventNameScheduleLoaders, System.currentTimeMillis() + loadCheckDelay, null);
            nextEvents.add(nextEvent);
            msg = "Raised further " + (nextEvents.size() - 1) + " events and rescheduled self.";
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }

        EventResult result = new EventResult(msg, nextEvents);
        return result;
    }
}
