package org.vericrop.gui.services;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.gui.services.AlertsService.Alert;
import org.vericrop.gui.services.AlertsService.AlertSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlertsService
 * Note: These tests require JavaFX Platform to be initialized
 */
public class AlertsServiceTest {
    
    @BeforeAll
    public static void initJavaFX() {
        // Initialize JavaFX toolkit (required for Platform.runLater)
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Platform already initialized
        }
    }
    
    private AlertsService service;
    private List<Alert> receivedAlerts;
    
    @BeforeEach
    public void setUp() {
        service = AlertsService.getInstance();
        receivedAlerts = new ArrayList<>();
        
        // Clear any existing alerts
        service.clearAlerts();
    }
    
    @Test
    public void testCreateAlert() {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        service.addAlertListener(alert -> {
            receivedAlerts.add(alert);
            latch.countDown();
        });
        
        // Act
        service.createAlert("Test Title", "Test Message", AlertSeverity.INFO, "TestSource");
        
        // Assert
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Alert not received");
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        assertFalse(receivedAlerts.isEmpty());
        Alert alert = receivedAlerts.get(0);
        assertEquals("Test Title", alert.getTitle());
        assertEquals("Test Message", alert.getMessage());
        assertEquals(AlertSeverity.INFO, alert.getSeverity());
        assertEquals("TestSource", alert.getSource());
        assertFalse(alert.isRead());
    }
    
    @Test
    public void testCreateAlertWithRole() {
        // Act
        service.createAlert("Role Alert", "Message", AlertSeverity.WARNING, "Source", "ADMIN");
        
        // Assert
        List<Alert> alerts = service.getAllAlerts();
        assertFalse(alerts.isEmpty());
        Alert alert = alerts.get(0);
        assertEquals("ADMIN", alert.getTargetRole());
    }
    
    @Test
    public void testGetAlertsBySeverity() {
        // Arrange
        service.createAlert("Info Alert", "Message", AlertSeverity.INFO, "Source");
        service.createAlert("Warning Alert", "Message", AlertSeverity.WARNING, "Source");
        service.createAlert("Error Alert", "Message", AlertSeverity.ERROR, "Source");
        
        // Act
        List<Alert> infoAlerts = service.getAlertsBySeverity(AlertSeverity.INFO);
        List<Alert> warningAlerts = service.getAlertsBySeverity(AlertSeverity.WARNING);
        List<Alert> errorAlerts = service.getAlertsBySeverity(AlertSeverity.ERROR);
        
        // Assert
        assertEquals(1, infoAlerts.size());
        assertEquals(1, warningAlerts.size());
        assertEquals(1, errorAlerts.size());
        assertEquals(AlertSeverity.INFO, infoAlerts.get(0).getSeverity());
        assertEquals(AlertSeverity.WARNING, warningAlerts.get(0).getSeverity());
        assertEquals(AlertSeverity.ERROR, errorAlerts.get(0).getSeverity());
    }
    
    @Test
    public void testGetAlertsByRole() {
        // Arrange
        service.createAlert("Admin Alert", "Message", AlertSeverity.INFO, "Source", "ADMIN");
        service.createAlert("Farmer Alert", "Message", AlertSeverity.INFO, "Source", "FARMER");
        service.createAlert("General Alert", "Message", AlertSeverity.INFO, "Source", null);
        
        // Act
        List<Alert> adminAlerts = service.getAlertsByRole("ADMIN");
        List<Alert> farmerAlerts = service.getAlertsByRole("FARMER");
        
        // Assert
        // Admin should see admin-specific and general alerts
        assertEquals(2, adminAlerts.size());
        
        // Farmer should see farmer-specific and general alerts
        assertEquals(2, farmerAlerts.size());
    }
    
    @Test
    public void testUnreadAlerts() {
        // Arrange
        service.createAlert("Alert 1", "Message", AlertSeverity.INFO, "Source");
        service.createAlert("Alert 2", "Message", AlertSeverity.INFO, "Source");
        
        // Act
        List<Alert> unreadAlerts = service.getUnreadAlerts();
        int unreadCount = service.getUnreadCount();
        
        // Assert
        assertEquals(2, unreadAlerts.size());
        assertEquals(2, unreadCount);
        
        // Mark one as read
        Alert firstAlert = service.getAllAlerts().get(0);
        service.markAsRead(firstAlert.getId());
        
        // Check again
        assertEquals(1, service.getUnreadCount());
    }
    
    @Test
    public void testMarkAsRead() {
        // Arrange
        service.createAlert("Test Alert", "Message", AlertSeverity.INFO, "Source");
        Alert alert = service.getAllAlerts().get(0);
        assertFalse(alert.isRead());
        
        // Act
        service.markAsRead(alert.getId());
        
        // Assert
        Alert updatedAlert = service.getAllAlerts().get(0);
        assertTrue(updatedAlert.isRead());
    }
    
    @Test
    public void testMarkAllAsRead() {
        // Arrange
        service.createAlert("Alert 1", "Message", AlertSeverity.INFO, "Source");
        service.createAlert("Alert 2", "Message", AlertSeverity.INFO, "Source");
        service.createAlert("Alert 3", "Message", AlertSeverity.INFO, "Source");
        
        // Act
        service.markAllAsRead();
        
        // Assert
        assertEquals(0, service.getUnreadCount());
        List<Alert> unreadAlerts = service.getUnreadAlerts();
        assertTrue(unreadAlerts.isEmpty());
    }
    
    @Test
    public void testClearAlerts() {
        // Arrange
        service.createAlert("Alert 1", "Message", AlertSeverity.INFO, "Source");
        service.createAlert("Alert 2", "Message", AlertSeverity.INFO, "Source");
        assertEquals(2, service.getAllAlerts().size());
        
        // Act
        service.clearAlerts();
        
        // Assert
        assertTrue(service.getAllAlerts().isEmpty());
        assertEquals(0, service.getUnreadCount());
    }
    
    @Test
    public void testQualityAlert() {
        // Act
        service.createQualityAlert("BATCH-123", 0.5, "Low quality detected");
        
        // Assert
        List<Alert> alerts = service.getAllAlerts();
        assertFalse(alerts.isEmpty());
        Alert alert = alerts.get(0);
        assertEquals(AlertSeverity.ERROR, alert.getSeverity());
        assertTrue(alert.getMessage().contains("BATCH-123"));
        assertTrue(alert.getMessage().contains("50"));
    }
    
    @Test
    public void testTemperatureAlert() {
        // Act
        service.createTemperatureAlert("SHIP-456", 8.5, 5.0);
        
        // Assert
        List<Alert> alerts = service.getAllAlerts();
        assertFalse(alerts.isEmpty());
        Alert alert = alerts.get(0);
        assertEquals(AlertSeverity.ERROR, alert.getSeverity());
        assertTrue(alert.getMessage().contains("SHIP-456"));
        assertTrue(alert.getMessage().contains("8.5"));
        assertTrue(alert.getMessage().contains("5.0"));
    }
    
    @Test
    public void testAlertListenerMultiple() {
        // Arrange
        List<Alert> listener1Alerts = new ArrayList<>();
        List<Alert> listener2Alerts = new ArrayList<>();
        
        service.addAlertListener(listener1Alerts::add);
        service.addAlertListener(listener2Alerts::add);
        
        // Act
        service.createAlert("Test", "Message", AlertSeverity.INFO, "Source");
        
        // Wait for propagation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        // Assert
        assertFalse(listener1Alerts.isEmpty());
        assertFalse(listener2Alerts.isEmpty());
        assertEquals(listener1Alerts.size(), listener2Alerts.size());
    }
    
    @Test
    public void testAlertSeverityColors() {
        // Arrange
        service.createAlert("Info", "Msg", AlertSeverity.INFO, "Src");
        service.createAlert("Warning", "Msg", AlertSeverity.WARNING, "Src");
        service.createAlert("Error", "Msg", AlertSeverity.ERROR, "Src");
        service.createAlert("Critical", "Msg", AlertSeverity.CRITICAL, "Src");
        
        // Act & Assert
        List<Alert> alerts = service.getAllAlerts();
        for (Alert alert : alerts) {
            assertNotNull(alert.getSeverityColor());
            assertNotNull(alert.getSeverityIcon());
        }
    }
    
    @Test
    public void testUnreadCountForRole() {
        // Arrange
        service.createAlert("Admin Alert 1", "Msg", AlertSeverity.INFO, "Src", "ADMIN");
        service.createAlert("Admin Alert 2", "Msg", AlertSeverity.INFO, "Src", "ADMIN");
        service.createAlert("Farmer Alert", "Msg", AlertSeverity.INFO, "Src", "FARMER");
        service.createAlert("General Alert", "Msg", AlertSeverity.INFO, "Src", null);
        
        // Act
        int adminUnread = service.getUnreadCountForRole("ADMIN");
        int farmerUnread = service.getUnreadCountForRole("FARMER");
        
        // Assert
        assertEquals(3, adminUnread); // 2 admin-specific + 1 general
        assertEquals(2, farmerUnread); // 1 farmer-specific + 1 general
    }
}
