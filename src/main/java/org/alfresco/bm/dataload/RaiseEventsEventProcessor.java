package org.alfresco.bm.dataload;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;

public class RaiseEventsEventProcessor extends AbstractEventProcessor
{
    private final List<String> outputEventNames;
    
    /**
     * Constructor with <b>essential</b> values
     * 
     * @param outputEventName               the name of the event to emit
     * @param timeBetweenEvents             the time between events
     * @param outputEventCount              the number of events to emit
     */
    public RaiseEventsEventProcessor(List<String> outputEventNames)
    {
        super();
        this.outputEventNames = outputEventNames;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        List<Event> nextEvents = new ArrayList<Event>(outputEventNames.size());

        for(String eventName : outputEventNames)
        {
            Event nextEvent = new Event(eventName, System.currentTimeMillis(), null);
            nextEvents.add(nextEvent);
        }

        // Done
        return new EventResult(
                "Created " + outputEventNames.size() + " events",
                nextEvents,
                true);
    }
}
