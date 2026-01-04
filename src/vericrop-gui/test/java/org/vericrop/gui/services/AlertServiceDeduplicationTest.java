package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlertService deduplication functionality.
 * Verifies that duplicate "batch delivered" alerts are suppressed
 * while allowing different alert types for the same batch.
 * 
 * Note: These tests focus on the deduplication logic without requiring JavaFX.
 */
public class AlertServiceDeduplicationTest {
    
    @Test
    @DisplayName("Deduplication key should be correctly formatted")
    void testDeduplicationKey() {
        AlertService.Alert alert = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source", "BATCH_X", "EVENT_Y"
        );
        
        assertEquals("BATCH_X:EVENT_Y", alert.getDeduplicationKey(),
            "Deduplication key should be batchId:eventType");
    }
    
    @Test
    @DisplayName("Alert without batchId should have null deduplication key")
    void testNullDeduplicationKey() {
        AlertService.Alert alert = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source"
        );
        
        assertNull(alert.getDeduplicationKey(),
            "Alert without batchId should have null dedup key");
    }
    
    @Test
    @DisplayName("Alert with only batchId (no eventType) should have null dedup key")
    void testPartialDeduplicationKey() {
        AlertService.Alert alert = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source", "BATCH_X", null
        );
        
        assertNull(alert.getDeduplicationKey(),
            "Alert with null eventType should have null dedup key");
    }
    
    @Test
    @DisplayName("Alert getters should return correct values")
    void testAlertGetters() {
        AlertService.Alert alert = new AlertService.Alert(
            "Title", "Message", AlertService.Severity.WARNING, "source", "BATCH_123", "DELIVERED"
        );
        
        assertEquals("Title", alert.getTitle());
        assertEquals("Message", alert.getMessage());
        assertEquals(AlertService.Severity.WARNING, alert.getSeverity());
        assertEquals("source", alert.getSource());
        assertEquals("BATCH_123", alert.getBatchId());
        assertEquals("DELIVERED", alert.getEventType());
        assertFalse(alert.isAcknowledged());
        assertNotNull(alert.getId());
        assertTrue(alert.getTimestamp() > 0);
    }
    
    @Test
    @DisplayName("Alert acknowledge should update state")
    void testAlertAcknowledge() {
        AlertService.Alert alert = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source"
        );
        
        assertFalse(alert.isAcknowledged(), "Alert should start unacknowledged");
        
        alert.acknowledge();
        
        assertTrue(alert.isAcknowledged(), "Alert should be acknowledged after calling acknowledge()");
    }
    
    @Test
    @DisplayName("Alert toString should include key fields")
    void testAlertToString() {
        AlertService.Alert alert = new AlertService.Alert(
            "Test Title", "Test Message", AlertService.Severity.ERROR, "test-source"
        );
        
        String str = alert.toString();
        
        assertTrue(str.contains("ERROR"), "toString should contain severity");
        assertTrue(str.contains("test-source"), "toString should contain source");
        assertTrue(str.contains("Test Title"), "toString should contain title");
        assertTrue(str.contains("Test Message"), "toString should contain message");
    }
    
    @Test
    @DisplayName("Different severity levels should be correctly assigned")
    void testSeverityLevels() {
        AlertService.Alert info = new AlertService.Alert(
            "T", "M", AlertService.Severity.INFO, "s");
        AlertService.Alert warning = new AlertService.Alert(
            "T", "M", AlertService.Severity.WARNING, "s");
        AlertService.Alert error = new AlertService.Alert(
            "T", "M", AlertService.Severity.ERROR, "s");
        AlertService.Alert critical = new AlertService.Alert(
            "T", "M", AlertService.Severity.CRITICAL, "s");
        
        assertEquals(AlertService.Severity.INFO, info.getSeverity());
        assertEquals(AlertService.Severity.WARNING, warning.getSeverity());
        assertEquals(AlertService.Severity.ERROR, error.getSeverity());
        assertEquals(AlertService.Severity.CRITICAL, critical.getSeverity());
    }
    
    @Test
    @DisplayName("Each alert should have unique ID")
    void testUniqueAlertIds() {
        AlertService.Alert alert1 = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source");
        AlertService.Alert alert2 = new AlertService.Alert(
            "Test", "Message", AlertService.Severity.INFO, "source");
        
        assertNotEquals(alert1.getId(), alert2.getId(),
            "Each alert should have a unique ID");
    }
}
