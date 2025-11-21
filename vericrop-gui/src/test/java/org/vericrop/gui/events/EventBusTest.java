package org.vericrop.gui.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventBus.
 */
public class EventBusTest {
    
    private EventBus eventBus;
    
    @BeforeEach
    public void setUp() {
        eventBus = EventBus.getInstance();
        eventBus.clearAll();
    }
    
    @AfterEach
    public void tearDown() {
        eventBus.clearAll();
    }
    
    @Test
    public void testSingleton() {
        EventBus instance1 = EventBus.getInstance();
        EventBus instance2 = EventBus.getInstance();
        assertSame(instance1, instance2);
    }
    
    @Test
    public void testSubscribeAndPublish() {
        List<AlertRaised> receivedEvents = new ArrayList<>();
        
        eventBus.subscribe(AlertRaised.class, receivedEvents::add);
        
        AlertRaised event = new AlertRaised("Test", "Test message", 
                                           AlertRaised.Severity.INFO, "Test");
        eventBus.publish(event);
        
        assertEquals(1, receivedEvents.size());
        assertEquals("Test", receivedEvents.get(0).getTitle());
    }
    
    @Test
    public void testMultipleSubscribers() {
        List<AlertRaised> subscriber1Events = new ArrayList<>();
        List<AlertRaised> subscriber2Events = new ArrayList<>();
        
        eventBus.subscribe(AlertRaised.class, subscriber1Events::add);
        eventBus.subscribe(AlertRaised.class, subscriber2Events::add);
        
        AlertRaised event = new AlertRaised("Test", "Test message", 
                                           AlertRaised.Severity.INFO, "Test");
        eventBus.publish(event);
        
        assertEquals(1, subscriber1Events.size());
        assertEquals(1, subscriber2Events.size());
        assertEquals(2, eventBus.getSubscriberCount(AlertRaised.class));
    }
    
    @Test
    public void testPublishWithNoSubscribers() {
        AlertRaised event = new AlertRaised("Test", "Test message", 
                                           AlertRaised.Severity.INFO, "Test");
        
        // Should not throw exception
        assertDoesNotThrow(() -> eventBus.publish(event));
    }
    
    @Test
    public void testPublishNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> eventBus.publish(null));
    }
    
    @Test
    public void testSubscribeWithNullEventType() {
        assertThrows(IllegalArgumentException.class, 
                    () -> eventBus.subscribe(null, e -> {}));
    }
    
    @Test
    public void testSubscribeWithNullHandler() {
        assertThrows(IllegalArgumentException.class, 
                    () -> eventBus.subscribe(AlertRaised.class, null));
    }
    
    @Test
    public void testMultipleEventTypes() {
        List<AlertRaised> alertEvents = new ArrayList<>();
        List<DeliveryStatusUpdated> deliveryEvents = new ArrayList<>();
        
        eventBus.subscribe(AlertRaised.class, alertEvents::add);
        eventBus.subscribe(DeliveryStatusUpdated.class, deliveryEvents::add);
        
        AlertRaised alert = new AlertRaised("Test", "Test message", 
                                           AlertRaised.Severity.INFO, "Test");
        DeliveryStatusUpdated delivery = new DeliveryStatusUpdated(
            "SHIP-001", "IN_TRANSIT", "Location A", "Details");
        
        eventBus.publish(alert);
        eventBus.publish(delivery);
        
        assertEquals(1, alertEvents.size());
        assertEquals(1, deliveryEvents.size());
    }
    
    @Test
    public void testHandlerException() {
        List<AlertRaised> receivedEvents = new ArrayList<>();
        
        // First handler throws exception
        eventBus.subscribe(AlertRaised.class, e -> {
            throw new RuntimeException("Handler error");
        });
        
        // Second handler should still receive event
        eventBus.subscribe(AlertRaised.class, receivedEvents::add);
        
        AlertRaised event = new AlertRaised("Test", "Test message", 
                                           AlertRaised.Severity.INFO, "Test");
        eventBus.publish(event);
        
        // Second handler should have received the event despite first handler failing
        assertEquals(1, receivedEvents.size());
    }
    
    @Test
    public void testGetSubscriberCount() {
        assertEquals(0, eventBus.getSubscriberCount(AlertRaised.class));
        
        eventBus.subscribe(AlertRaised.class, e -> {});
        assertEquals(1, eventBus.getSubscriberCount(AlertRaised.class));
        
        eventBus.subscribe(AlertRaised.class, e -> {});
        assertEquals(2, eventBus.getSubscriberCount(AlertRaised.class));
    }
    
    @Test
    public void testClearAll() {
        eventBus.subscribe(AlertRaised.class, e -> {});
        eventBus.subscribe(DeliveryStatusUpdated.class, e -> {});
        
        assertTrue(eventBus.getSubscriberCount(AlertRaised.class) > 0);
        
        eventBus.clearAll();
        
        assertEquals(0, eventBus.getSubscriberCount(AlertRaised.class));
        assertEquals(0, eventBus.getSubscriberCount(DeliveryStatusUpdated.class));
    }
}
