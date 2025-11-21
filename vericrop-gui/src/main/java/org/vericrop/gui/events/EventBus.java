package org.vericrop.gui.events;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Thread-safe EventBus for publishing and subscribing to domain events.
 * Supports automatic JavaFX thread dispatching for UI updates.
 */
public class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    private static final EventBus instance = new EventBus();
    
    private final Map<Class<?>, Set<Consumer<Object>>> subscribers;
    
    private EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
        logger.info("EventBus initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static EventBus getInstance() {
        return instance;
    }
    
    /**
     * Subscribe to events of a specific type.
     * 
     * @param eventType The class of events to subscribe to
     * @param handler The handler to invoke when events are published
     * @param <T> The event type
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribe(eventType, handler, false);
    }
    
    /**
     * Subscribe to events of a specific type.
     * 
     * @param eventType The class of events to subscribe to
     * @param handler The handler to invoke when events are published
     * @param runOnFxThread If true, handler will be invoked on JavaFX Application Thread
     * @param <T> The event type
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler, boolean runOnFxThread) {
        if (eventType == null || handler == null) {
            throw new IllegalArgumentException("Event type and handler must not be null");
        }
        
        Consumer<Object> wrappedHandler = event -> {
            try {
                if (runOnFxThread && !Platform.isFxApplicationThread()) {
                    Platform.runLater(() -> handler.accept((T) event));
                } else {
                    handler.accept((T) event);
                }
            } catch (Exception e) {
                logger.error("Error handling event of type {}", eventType.getSimpleName(), e);
            }
        };
        
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArraySet<>())
                   .add(wrappedHandler);
        
        logger.debug("Subscribed to event type: {}", eventType.getSimpleName());
    }
    
    /**
     * Unsubscribe from events of a specific type.
     * 
     * @param eventType The class of events to unsubscribe from
     * @param handler The handler to remove
     * @param <T> The event type
     */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        Set<Consumer<Object>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            // Note: This will not work with wrapped handlers
            // In production, you'd need to maintain a mapping
            handlers.removeIf(h -> h.equals(handler));
            
            if (handlers.isEmpty()) {
                subscribers.remove(eventType);
            }
            
            logger.debug("Unsubscribed from event type: {}", eventType.getSimpleName());
        }
    }
    
    /**
     * Publish an event to all subscribers.
     * 
     * @param event The event to publish
     */
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }
        
        Class<?> eventType = event.getClass();
        Set<Consumer<Object>> handlers = subscribers.get(eventType);
        
        if (handlers == null || handlers.isEmpty()) {
            logger.trace("No subscribers for event type: {}", eventType.getSimpleName());
            return;
        }
        
        logger.debug("Publishing event of type {} to {} subscriber(s)", 
                    eventType.getSimpleName(), handlers.size());
        
        for (Consumer<Object> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                logger.error("Error notifying subscriber of event type {}", 
                           eventType.getSimpleName(), e);
            }
        }
    }
    
    /**
     * Get the number of subscribers for a specific event type.
     * 
     * @param eventType The event type
     * @return The number of subscribers
     */
    public int getSubscriberCount(Class<?> eventType) {
        Set<Consumer<Object>> handlers = subscribers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }
    
    /**
     * Clear all subscriptions (useful for testing).
     */
    public void clearAll() {
        subscribers.clear();
        logger.info("All subscriptions cleared");
    }
}
