package org.vericrop.gui.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vericrop.gui.persistence.PersistedShipment;
import org.vericrop.gui.persistence.PersistedSimulation;
import org.vericrop.gui.persistence.ShipmentPersistenceService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReportExportService.
 */
class ReportExportServiceTest {
    
    @TempDir
    Path tempDir;
    
    private ShipmentPersistenceService persistenceService;
    private ReportExportService exportService;
    
    @BeforeEach
    void setUp() {
        persistenceService = new ShipmentPersistenceService(tempDir.toString());
        exportService = new ReportExportService(persistenceService);
        
        // Add test data
        setupTestData();
    }
    
    @AfterEach
    void tearDown() {
        if (persistenceService != null) {
            persistenceService.clearAllData();
        }
    }
    
    private void setupTestData() {
        // Add shipments
        PersistedShipment shipment1 = new PersistedShipment(
                "BATCH_001", "IN_TRANSIT", "Highway Mile 10", 4.5, 65.0, "30 min", "TRUCK_001"
        );
        shipment1.setFarmerId("FARMER_A");
        persistenceService.saveShipment(shipment1);
        
        PersistedShipment shipment2 = new PersistedShipment(
                "BATCH_002", "DELIVERED", "Warehouse", 4.0, 62.0, "DELIVERED", "TRUCK_002"
        );
        shipment2.setFarmerId("FARMER_B");
        persistenceService.saveShipment(shipment2);
        
        // Add simulations
        PersistedSimulation sim1 = new PersistedSimulation("BATCH_001", "FARMER_A", "NORMAL");
        sim1.setStatus("COMPLETED");
        sim1.setCompleted(true);
        sim1.setFinalQuality(95.0);
        sim1.setInitialQuality(100.0);
        sim1.setAvgTemperature(4.5);
        sim1.setMinTemperature(3.5);
        sim1.setMaxTemperature(5.5);
        sim1.setAvgHumidity(65.0);
        sim1.setWaypointsCount(10);
        sim1.setViolationsCount(0);
        sim1.setComplianceStatus("COMPLIANT");
        sim1.setEndTime(System.currentTimeMillis());
        persistenceService.saveSimulation(sim1);
        
        PersistedSimulation sim2 = new PersistedSimulation("BATCH_002", "FARMER_B", "HOT_WEATHER");
        sim2.setStatus("COMPLETED");
        sim2.setCompleted(true);
        sim2.setFinalQuality(85.0);
        sim2.setInitialQuality(100.0);
        sim2.setAvgTemperature(7.0);
        sim2.setMinTemperature(5.0);
        sim2.setMaxTemperature(9.0);
        sim2.setAvgHumidity(70.0);
        sim2.setWaypointsCount(12);
        sim2.setViolationsCount(2);
        sim2.setComplianceStatus("NON_COMPLIANT");
        sim2.setEndTime(System.currentTimeMillis());
        persistenceService.saveSimulation(sim2);
    }
    
    // ==================== FILENAME TESTS ====================
    
    @Test
    void testGenerateFilename_Txt() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        
        String filename = exportService.generateFilename(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertEquals("shipment-summary_2025-01-01_to_2025-01-31.txt", filename);
    }
    
    @Test
    void testGenerateFilename_Csv() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        
        String filename = exportService.generateFilename(
                ReportExportService.ReportType.TEMPERATURE_LOG, start, end,
                ReportExportService.ExportFormat.CSV);
        
        assertEquals("temperature-log_2025-01-01_to_2025-01-31.csv", filename);
    }
    
    // ==================== SHIPMENT SUMMARY TESTS ====================
    
    @Test
    void testExportShipmentSummary_Txt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("SHIPMENT SUMMARY REPORT"));
        assertTrue(content.contains("BATCH_001"));
        assertTrue(content.contains("BATCH_002"));
        assertTrue(content.contains("IN_TRANSIT"));
        assertTrue(content.contains("DELIVERED"));
    }
    
    @Test
    void testExportShipmentSummary_Csv() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.CSV);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        // CSV should have header
        assertTrue(content.contains("Batch ID,Status,Location,Temperature"));
        // CSV should have data
        assertTrue(content.contains("BATCH_001"));
        assertTrue(content.contains("BATCH_002"));
    }
    
    // ==================== TEMPERATURE LOG TESTS ====================
    
    @Test
    void testExportTemperatureLog_Txt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.TEMPERATURE_LOG, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("TEMPERATURE LOG REPORT"));
        assertTrue(content.contains("SHIPMENT TEMPERATURE DATA"));
        assertTrue(content.contains("SIMULATION TEMPERATURE SUMMARY"));
    }
    
    @Test
    void testExportTemperatureLog_Csv() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.TEMPERATURE_LOG, start, end,
                ReportExportService.ExportFormat.CSV);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("Batch ID,Timestamp,Temperature"));
    }
    
    // ==================== QUALITY COMPLIANCE TESTS ====================
    
    @Test
    void testExportQualityCompliance_Txt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.QUALITY_COMPLIANCE, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("QUALITY COMPLIANCE REPORT"));
        assertTrue(content.contains("COMPLIANCE SUMMARY"));
        assertTrue(content.contains("Compliant Runs:"));
    }
    
    @Test
    void testExportQualityCompliance_Csv() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.QUALITY_COMPLIANCE, start, end,
                ReportExportService.ExportFormat.CSV);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("Batch ID,Scenario,Status,Completed,Final Quality"));
        assertTrue(content.contains("COMPLIANT"));
        assertTrue(content.contains("NON_COMPLIANT"));
    }
    
    // ==================== CSV FORMAT VALIDATION TESTS ====================
    
    @Test
    void testCsvContainsSameFieldsAsTxt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File txtFile = exportService.exportReport(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.TXT);
        
        File csvFile = exportService.exportReport(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.CSV);
        
        String txtContent = Files.readString(txtFile.toPath());
        String csvContent = Files.readString(csvFile.toPath());
        
        // Both should contain the same batch IDs
        assertTrue(txtContent.contains("BATCH_001"));
        assertTrue(csvContent.contains("BATCH_001"));
        assertTrue(txtContent.contains("BATCH_002"));
        assertTrue(csvContent.contains("BATCH_002"));
        
        // Both should contain temperature values
        assertTrue(txtContent.contains("4.5") || txtContent.contains("4,5")); // Handle locale differences
        assertTrue(csvContent.contains("4.5"));
    }
    
    @Test
    void testCsvHasProperHeader() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File csvFile = exportService.exportReport(
                ReportExportService.ReportType.SHIPMENT_SUMMARY, start, end,
                ReportExportService.ExportFormat.CSV);
        
        String content = Files.readString(csvFile.toPath());
        String[] lines = content.split("\n");
        
        // First line should be the header
        assertTrue(lines.length > 1);
        String header = lines[0];
        assertTrue(header.contains("Batch ID"));
        assertTrue(header.contains("Status"));
        assertTrue(header.contains("Temperature"));
        assertTrue(header.contains("Humidity"));
    }
    
    // ==================== SIMULATION LOG TESTS ====================
    
    @Test
    void testExportSimulationLog_Txt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.SIMULATION_LOG, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("SIMULATION LOG REPORT"));
        assertTrue(content.contains("SIMULATION DETAILS"));
        assertTrue(content.contains("BATCH_001"));
        assertTrue(content.contains("NORMAL"));
        assertTrue(content.contains("HOT_WEATHER"));
    }
    
    @Test
    void testExportSimulationLog_Csv() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.SIMULATION_LOG, start, end,
                ReportExportService.ExportFormat.CSV);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        // Should have temperature-related columns
        assertTrue(content.contains("Avg Temp"));
        assertTrue(content.contains("Min Temp"));
        assertTrue(content.contains("Max Temp"));
    }
    
    // ==================== DELIVERY PERFORMANCE TESTS ====================
    
    @Test
    void testExportDeliveryPerformance_Txt() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.DELIVERY_PERFORMANCE, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        assertTrue(content.contains("DELIVERY PERFORMANCE REPORT"));
        assertTrue(content.contains("PERFORMANCE METRICS"));
    }
    
    // ==================== FIX VALIDATION TESTS ====================
    
    /**
     * Test that PDF format exports use .html extension for browser compatibility.
     * This fixes the issue where PDF files couldn't be opened.
     */
    @Test
    void testPdfExportUsesHtmlExtension() throws IOException {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.QUALITY_COMPLIANCE, start, end,
                ReportExportService.ExportFormat.PDF);
        
        assertTrue(exportedFile.exists());
        // PDF exports should use .html extension for browser compatibility
        assertTrue(exportedFile.getName().endsWith(".html"), 
                "PDF export should use .html extension for browser compatibility");
        
        // Verify it's valid HTML content
        String content = Files.readString(exportedFile.toPath());
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("</html>"));
    }
    
    /**
     * Test that Quality Compliance report includes simulation type breakdown.
     * This fixes the issue where the 3 simulation examples weren't properly interpreted.
     */
    @Test
    void testQualityComplianceIncludesSimulationTypes() throws IOException {
        // Add simulation data with the 3 types
        PersistedSimulation apples = new PersistedSimulation("BATCH_APPLES_001", "FARMER_A", "example_1");
        apples.setStatus("COMPLETED");
        apples.setCompleted(true);
        apples.setFinalQuality(95.0);
        apples.setInitialQuality(100.0);
        apples.setAvgTemperature(4.5);
        apples.setMinTemperature(3.5);
        apples.setMaxTemperature(5.5);
        apples.setComplianceStatus("COMPLIANT");
        apples.setEndTime(System.currentTimeMillis());
        persistenceService.saveSimulation(apples);
        
        PersistedSimulation carrots = new PersistedSimulation("BATCH_CARROTS_001", "FARMER_B", "example_2");
        carrots.setStatus("COMPLETED");
        carrots.setCompleted(true);
        carrots.setFinalQuality(90.0);
        carrots.setInitialQuality(100.0);
        carrots.setAvgTemperature(4.0);
        carrots.setMinTemperature(3.0);
        carrots.setMaxTemperature(5.0);
        carrots.setComplianceStatus("COMPLIANT");
        carrots.setEndTime(System.currentTimeMillis());
        persistenceService.saveSimulation(carrots);
        
        PersistedSimulation veggies = new PersistedSimulation("BATCH_VEGGIES_001", "FARMER_C", "example_3");
        veggies.setStatus("COMPLETED");
        veggies.setCompleted(true);
        veggies.setFinalQuality(85.0);
        veggies.setInitialQuality(100.0);
        veggies.setAvgTemperature(6.0);
        veggies.setMinTemperature(4.0);
        veggies.setMaxTemperature(8.0);
        veggies.setComplianceStatus("COMPLIANT");
        veggies.setEndTime(System.currentTimeMillis());
        persistenceService.saveSimulation(veggies);
        
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        
        File exportedFile = exportService.exportReport(
                ReportExportService.ReportType.QUALITY_COMPLIANCE, start, end,
                ReportExportService.ExportFormat.TXT);
        
        assertTrue(exportedFile.exists());
        String content = Files.readString(exportedFile.toPath());
        
        // Should include breakdown by simulation type
        assertTrue(content.contains("COMPLIANCE BY SIMULATION TYPE") || 
                   content.contains("BY SIMULATION TYPE"), 
                   "Quality Compliance report should include simulation type breakdown");
        assertTrue(content.contains("Farm to Consumer") || content.contains("Apples"));
        assertTrue(content.contains("Local Producer") || content.contains("Carrots"));
        assertTrue(content.contains("Cross-Region") || content.contains("Vegetables"));
        
        // Verify all 3 examples are counted
        assertTrue(content.contains("Example 1"));
        assertTrue(content.contains("Example 2"));
        assertTrue(content.contains("Example 3"));
    }
}
