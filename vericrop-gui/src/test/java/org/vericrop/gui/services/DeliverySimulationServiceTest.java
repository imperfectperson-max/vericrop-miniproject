package org.vericrop.gui.services;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.gui.services.DeliverySimulationService.DeliveryEvent;
import org.vericrop.gui.services.DeliverySimulationService.DeliveryStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliverySimulationService
 * Note: These tests require JavaFX Platform to be initialized
 */
public class DeliverySimulationServiceTest {
    
    @BeforeAll
    public static void initJavaFX() {
        // Initialize JavaFX toolkit (required for Platform.runLater)
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Platform already initialized
        }
    }
    
    private DeliverySimulationService service;
    private List<DeliveryEvent> receivedEvents;
    
    @BeforeEach
    public void setUp() {
        service = DeliverySimulationService.getInstance();
        receivedEvents = new ArrayList<>();
        
        // Set faster timing for tests
        service.setSimulationSpeed(100); // 100x faster
    }
    
    @AfterEach
    public void tearDown() {
        service.stopSimulation();
        // Clean up any active shipments
        service.getActiveShipments().clear();
    }
    
    @Test
    public void testStartShipment() {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        service.addEventListener(event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        // Act
        String shipmentId = service.startShipment("BATCH-001", "Farm A", "Store B");
        
        // Assert
        assertNotNull(shipmentId);
        assertTrue(shipmentId.startsWith("SHIP-"));
        
        // Wait for event
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Event not received in time");
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        assertFalse(receivedEvents.isEmpty());
        DeliveryEvent firstEvent = receivedEvents.get(0);
        assertEquals(DeliveryStatus.CREATED, firstEvent.getStatus());
        assertEquals(shipmentId, firstEvent.getShipmentId());
        assertEquals("BATCH-001", firstEvent.getBatchId());
    }
    
    @Test
    public void testEventSequence() {
        // Arrange
        CountDownLatch latch = new CountDownLatch(2); // Wait for at least CREATED and PICKED_UP
        service.addEventListener(event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        // Act
        service.startShipment("BATCH-002", "Farm C", "Store D");
        
        // Assert - wait for first two events
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Events not received in time");
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        assertTrue(receivedEvents.size() >= 2, "Expected at least 2 events");
        assertEquals(DeliveryStatus.CREATED, receivedEvents.get(0).getStatus());
        assertEquals(DeliveryStatus.PICKED_UP, receivedEvents.get(1).getStatus());
    }
    
    @Test
    public void testSeedTestShipments() {
        // Act
        List<String> shipmentIds = service.seedTestShipments(3);
        
        // Assert
        assertEquals(3, shipmentIds.size());
        for (String id : shipmentIds) {
            assertNotNull(id);
            assertTrue(id.startsWith("SHIP-"));
        }
        
        // Check active shipments
        assertEquals(3, service.getActiveShipments().size());
    }
    
    @Test
    public void testEventListenerRegistration() {
        // Arrange
        List<DeliveryEvent> events1 = new ArrayList<>();
        List<DeliveryEvent> events2 = new ArrayList<>();
        
        // Act
        service.addEventListener(events1::add);
        service.addEventListener(events2::add);
        service.startShipment("BATCH-003", "Origin", "Destination");
        
        // Wait a bit for event propagation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        // Assert - both listeners should receive the event
        assertFalse(events1.isEmpty(), "First listener didn't receive events");
        assertFalse(events2.isEmpty(), "Second listener didn't receive events");
        assertEquals(events1.size(), events2.size(), "Both listeners should receive same number of events");
    }
    
    @Test
    public void testSimulationSpeedConfiguration() {
        // Act
        service.setSimulationSpeed(10);
        // No assertion needed - just verify it doesn't throw
        
        service.setSimulationSpeed(1);
        // Verify minimum speed is respected
    }
    
    @Test
    public void testTimingConfiguration() {
        // Act
        service.setTimingConfig(10, 20, 30, 0.1);
        
        // Start a shipment to verify configuration is applied
        String shipmentId = service.startShipment("BATCH-004", "A", "B");
        assertNotNull(shipmentId);
    }
    
    @Test
    public void testActiveShipmentsTracking() {
        // Act
        String id1 = service.startShipment("BATCH-005", "A", "B");
        String id2 = service.startShipment("BATCH-006", "C", "D");
        
        // Assert
        assertEquals(2, service.getActiveShipments().size());
        assertTrue(service.getActiveShipments().containsKey(id1));
        assertTrue(service.getActiveShipments().containsKey(id2));
    }
    
    @Test
    public void testShipmentSimulationData() {
        // Act
        String shipmentId = service.startShipment("BATCH-007", "Farm E", "Store F");
        
        // Assert
        var activeShipments = service.getActiveShipments();
        assertTrue(activeShipments.containsKey(shipmentId));
        
        var simulation = activeShipments.get(shipmentId);
        assertEquals(shipmentId, simulation.getShipmentId());
        assertEquals("BATCH-007", simulation.getBatchId());
        assertEquals("Farm E", simulation.getOrigin());
        assertEquals("Store F", simulation.getDestination());
        assertEquals(DeliveryStatus.CREATED, simulation.getStatus());
        assertNotNull(simulation.getCreatedAt());
    }
    
    @Test
    public void testEventMessage() {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        service.addEventListener(event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        // Act
        service.startShipment("BATCH-008", "Origin", "Destination");
        
        // Assert
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        DeliveryEvent event = receivedEvents.get(0);
        assertNotNull(event.getMessage());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getOrigin());
        assertNotNull(event.getDestination());
    }
}
