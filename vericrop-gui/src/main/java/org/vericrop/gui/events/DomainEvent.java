package org.vericrop.gui.events;

import java.time.Instant;

/**
 * Base class for all domain events in the application.
 */
public abstract class DomainEvent {
    private final String eventId;
    private final Instant timestamp;
    
    protected DomainEvent() {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
               "eventId='" + eventId + '\'' +
               ", timestamp=" + timestamp +
               '}';
    }
}
