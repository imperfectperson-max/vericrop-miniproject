package org.vericrop.gui.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.Message;
import org.vericrop.gui.events.EventBus;
import org.vericrop.gui.events.ReportReady;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReportGenerator.
 */
public class ReportGeneratorTest {
    
    private ReportGenerator generator;
    private EventBus eventBus;
    private List<ReportReady> reportReadyEvents;
    
    @BeforeEach
    public void setUp() {
        generator = ReportGenerator.getInstance();
        eventBus = EventBus.getInstance();
        reportReadyEvents = new ArrayList<>();
        
        // Subscribe to ReportReady events
        eventBus.subscribe(ReportReady.class, reportReadyEvents::add);
    }
    
    @AfterEach
    public void tearDown() {
        eventBus.clearAll();
        
        // Clean up generated reports
        try {
            Path reportsDir = generator.getReportsDirectory();
            if (Files.exists(reportsDir)) {
                Files.list(reportsDir)
                     .filter(p -> p.toString().endsWith(".csv") || p.toString().endsWith(".html"))
                     .forEach(p -> {
                         try {
                             Files.deleteIfExists(p);
                         } catch (IOException e) {
                             // Ignore
                         }
                     });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
    
    @Test
    public void testSingleton() {
        ReportGenerator instance1 = ReportGenerator.getInstance();
        ReportGenerator instance2 = ReportGenerator.getInstance();
        assertSame(instance1, instance2);
    }
    
    @Test
    public void testGenerateCSVReport() throws IOException {
        List<String> headers = List.of("Column1", "Column2", "Column3");
        List<List<String>> rows = List.of(
            List.of("Value1", "Value2", "Value3"),
            List.of("Value4", "Value5", "Value6")
        );
        
        Path reportPath = generator.generateCSVReport("test_report", headers, rows);
        
        assertNotNull(reportPath);
        assertTrue(Files.exists(reportPath));
        assertTrue(reportPath.toString().endsWith(".csv"));
        
        // Verify content
        String content = Files.readString(reportPath);
        assertTrue(content.contains("Column1,Column2,Column3"));
        assertTrue(content.contains("Value1,Value2,Value3"));
        assertTrue(content.contains("Value4,Value5,Value6"));
        
        // Verify event was published
        assertEquals(1, reportReadyEvents.size());
        assertEquals("CSV", reportReadyEvents.get(0).getReportType());
    }
    
    @Test
    public void testGenerateHTMLReport() throws IOException {
        List<String> headers = List.of("Column1", "Column2", "Column3");
        List<List<String>> rows = List.of(
            List.of("Value1", "Value2", "Value3"),
            List.of("Value4", "Value5", "Value6")
        );
        
        Path reportPath = generator.generateHTMLReport("test_report", "Test Report", headers, rows);
        
        assertNotNull(reportPath);
        assertTrue(Files.exists(reportPath));
        assertTrue(reportPath.toString().endsWith(".html"));
        
        // Verify content
        String content = Files.readString(reportPath);
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("<title>Test Report</title>"));
        assertTrue(content.contains("<th>Column1</th>"));
        assertTrue(content.contains("<td>Value1</td>"));
        
        // Verify event was published
        assertEquals(1, reportReadyEvents.size());
        assertEquals("HTML", reportReadyEvents.get(0).getReportType());
    }
    
    @Test
    public void testCSVEscaping() throws IOException {
        List<String> headers = List.of("Name", "Description");
        List<List<String>> rows = List.of(
            List.of("Test, Inc", "Value with \"quotes\""),
            List.of("Normal", "Simple value")
        );
        
        Path reportPath = generator.generateCSVReport("escape_test", headers, rows);
        
        String content = Files.readString(reportPath);
        assertTrue(content.contains("\"Test, Inc\""));
        assertTrue(content.contains("\"Value with \"\"quotes\"\"\""));
    }
    
    @Test
    public void testHTMLEscaping() throws IOException {
        List<String> headers = List.of("Name");
        List<List<String>> rows = List.of(
            List.of("<script>alert('xss')</script>"),
            List.of("Normal & Safe")
        );
        
        Path reportPath = generator.generateHTMLReport("xss_test", "XSS Test", headers, rows);
        
        String content = Files.readString(reportPath);
        assertFalse(content.contains("<script>"));
        assertTrue(content.contains("&lt;script&gt;"));
        assertTrue(content.contains("&amp;"));
    }
    
    @Test
    public void testGenerateDeliveriesReport() throws IOException {
        List<Map<String, String>> deliveries = List.of(
            createDeliveryMap("SHIP-001", "IN_TRANSIT", "Farm A", "Warehouse B", "2024-01-01", "Highway 1"),
            createDeliveryMap("SHIP-002", "DELIVERED", "Farm C", "Store D", "2024-01-02", "Store D")
        );
        
        Path reportPath = generator.generateDeliveriesReport("CSV", deliveries);
        
        assertNotNull(reportPath);
        assertTrue(Files.exists(reportPath));
        
        String content = Files.readString(reportPath);
        assertTrue(content.contains("SHIP-001"));
        assertTrue(content.contains("IN_TRANSIT"));
    }
    
    @Test
    public void testGenerateMessagesReport() throws IOException {
        List<Message> messages = List.of(
            createMessage("farmer", "farmer_001", "supplier", "supplier_001", "Test Subject 1"),
            createMessage("supplier", "supplier_001", "logistics", "logistics_001", "Test Subject 2")
        );
        
        Path reportPath = generator.generateMessagesReport("HTML", messages);
        
        assertNotNull(reportPath);
        assertTrue(Files.exists(reportPath));
        assertTrue(reportPath.toString().endsWith(".html"));
        
        String content = Files.readString(reportPath);
        assertTrue(content.contains("Test Subject 1"));
        assertTrue(content.contains("farmer:farmer_001"));
    }
    
    private Map<String, String> createDeliveryMap(String shipmentId, String status, 
                                                   String origin, String destination,
                                                   String startTime, String currentLocation) {
        Map<String, String> delivery = new HashMap<>();
        delivery.put("shipmentId", shipmentId);
        delivery.put("status", status);
        delivery.put("origin", origin);
        delivery.put("destination", destination);
        delivery.put("startTime", startTime);
        delivery.put("currentLocation", currentLocation);
        return delivery;
    }
    
    private Message createMessage(String senderRole, String senderId, 
                                 String recipientRole, String recipientId, String subject) {
        return new Message(senderRole, senderId, recipientRole, recipientId, subject, "Test content");
    }
}
