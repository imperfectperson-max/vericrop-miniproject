package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ReportGenerationService handles PDF and CSV report generation.
 * Provides quality reports, delivery reports, and supply chain traceability reports.
 */
public class ReportGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);
    private static ReportGenerationService instance;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String DEFAULT_REPORT_DIR = System.getProperty("user.home") + "/VeriCrop/Reports";
    
    private String reportDirectory = DEFAULT_REPORT_DIR;
    
    private ReportGenerationService() {
        ensureReportDirectoryExists();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized ReportGenerationService getInstance() {
        if (instance == null) {
            instance = new ReportGenerationService();
        }
        return instance;
    }
    
    /**
     * Ensure report directory exists
     */
    private void ensureReportDirectoryExists() {
        File dir = new File(reportDirectory);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                logger.info("Created report directory: {}", reportDirectory);
            } else {
                logger.error("Failed to create report directory: {}", reportDirectory);
            }
        }
    }
    
    /**
     * Set custom report directory
     */
    public void setReportDirectory(String directory) {
        this.reportDirectory = directory;
        ensureReportDirectoryExists();
    }
    
    /**
     * Generate quality report as CSV
     */
    public String generateQualityReportCSV(String batchId, Map<String, Object> data) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("Quality_Report_%s_%s.csv", batchId, timestamp);
        String filepath = reportDirectory + File.separator + filename;
        
        try (FileWriter writer = new FileWriter(filepath)) {
            // CSV Header
            writer.write("VeriCrop Quality Report\n");
            writer.write("Generated: " + LocalDateTime.now() + "\n");
            writer.write("\n");
            
            // Batch information
            writer.write("Batch ID," + batchId + "\n");
            writer.write("Batch Name," + data.getOrDefault("batchName", "N/A") + "\n");
            writer.write("Farmer," + data.getOrDefault("farmer", "N/A") + "\n");
            writer.write("Product Type," + data.getOrDefault("productType", "N/A") + "\n");
            writer.write("\n");
            
            // Quality metrics
            writer.write("Metric,Value\n");
            writer.write("Quality Score," + data.getOrDefault("qualityScore", "N/A") + "\n");
            writer.write("Quality Label," + data.getOrDefault("qualityLabel", "N/A") + "\n");
            writer.write("Confidence," + data.getOrDefault("confidence", "N/A") + "\n");
            writer.write("Color Consistency," + data.getOrDefault("colorConsistency", "N/A") + "\n");
            writer.write("Size Uniformity," + data.getOrDefault("sizeUniformity", "N/A") + "\n");
            writer.write("Defect Density," + data.getOrDefault("defectDensity", "N/A") + "\n");
            writer.write("\n");
            
            // Blockchain verification
            writer.write("Blockchain Hash," + data.getOrDefault("blockchainHash", "N/A") + "\n");
            writer.write("Timestamp," + data.getOrDefault("timestamp", "N/A") + "\n");
            
            logger.info("Quality report generated: {}", filepath);
            return filepath;
            
        } catch (IOException e) {
            logger.error("Error generating quality report CSV", e);
            return null;
        }
    }
    
    /**
     * Generate delivery report as CSV
     */
    public String generateDeliveryReportCSV(List<Map<String, Object>> deliveries) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("Delivery_Report_%s.csv", timestamp);
        String filepath = reportDirectory + File.separator + filename;
        
        try (FileWriter writer = new FileWriter(filepath)) {
            // CSV Header
            writer.write("VeriCrop Delivery Report\n");
            writer.write("Generated: " + LocalDateTime.now() + "\n");
            writer.write("\n");
            
            // Column headers
            writer.write("Shipment ID,Batch ID,Origin,Destination,Status,Pickup Time,Delivery Time,Duration\n");
            
            // Data rows
            for (Map<String, Object> delivery : deliveries) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    csvEscape(delivery.getOrDefault("shipmentId", "")),
                    csvEscape(delivery.getOrDefault("batchId", "")),
                    csvEscape(delivery.getOrDefault("origin", "")),
                    csvEscape(delivery.getOrDefault("destination", "")),
                    csvEscape(delivery.getOrDefault("status", "")),
                    csvEscape(delivery.getOrDefault("pickupTime", "")),
                    csvEscape(delivery.getOrDefault("deliveryTime", "")),
                    csvEscape(delivery.getOrDefault("duration", ""))
                ));
            }
            
            logger.info("Delivery report generated: {}", filepath);
            return filepath;
            
        } catch (IOException e) {
            logger.error("Error generating delivery report CSV", e);
            return null;
        }
    }
    
    /**
     * Generate supply chain traceability report as CSV
     */
    public String generateTraceabilityReportCSV(String batchId, List<Map<String, Object>> events) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("Traceability_Report_%s_%s.csv", batchId, timestamp);
        String filepath = reportDirectory + File.separator + filename;
        
        try (FileWriter writer = new FileWriter(filepath)) {
            // CSV Header
            writer.write("VeriCrop Supply Chain Traceability Report\n");
            writer.write("Batch ID: " + batchId + "\n");
            writer.write("Generated: " + LocalDateTime.now() + "\n");
            writer.write("\n");
            
            // Column headers
            writer.write("Event Type,Timestamp,Location,Actor,Details,Blockchain Hash\n");
            
            // Event rows
            for (Map<String, Object> event : events) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s\n",
                    csvEscape(event.getOrDefault("eventType", "")),
                    csvEscape(event.getOrDefault("timestamp", "")),
                    csvEscape(event.getOrDefault("location", "")),
                    csvEscape(event.getOrDefault("actor", "")),
                    csvEscape(event.getOrDefault("details", "")),
                    csvEscape(event.getOrDefault("blockchainHash", ""))
                ));
            }
            
            logger.info("Traceability report generated: {}", filepath);
            return filepath;
            
        } catch (IOException e) {
            logger.error("Error generating traceability report CSV", e);
            return null;
        }
    }
    
    /**
     * Generate analytics summary report as CSV
     */
    public String generateAnalyticsSummaryCSV(Map<String, Object> kpis) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("Analytics_Summary_%s.csv", timestamp);
        String filepath = reportDirectory + File.separator + filename;
        
        try (FileWriter writer = new FileWriter(filepath)) {
            // CSV Header
            writer.write("VeriCrop Analytics Summary Report\n");
            writer.write("Generated: " + LocalDateTime.now() + "\n");
            writer.write("\n");
            
            // KPI metrics
            writer.write("Metric,Value\n");
            writer.write("Total Batches," + kpis.getOrDefault("totalBatches", 0) + "\n");
            writer.write("Average Quality Score," + kpis.getOrDefault("avgQuality", 0.0) + "\n");
            writer.write("Quality Pass Rate," + kpis.getOrDefault("passRate", 0.0) + "%\n");
            writer.write("Average Delivery Time," + kpis.getOrDefault("avgDeliveryTime", 0.0) + " hours\n");
            writer.write("On-Time Delivery Rate," + kpis.getOrDefault("onTimeRate", 0.0) + "%\n");
            writer.write("Delayed Shipments," + kpis.getOrDefault("delayedShipments", 0) + "\n");
            writer.write("Delayed Shipments Percentage," + kpis.getOrDefault("delayedPercentage", 0.0) + "%\n");
            writer.write("Spoilage Rate," + kpis.getOrDefault("spoilageRate", 0.0) + "%\n");
            
            logger.info("Analytics summary generated: {}", filepath);
            return filepath;
            
        } catch (IOException e) {
            logger.error("Error generating analytics summary CSV", e);
            return null;
        }
    }
    
    /**
     * Generate simple text report
     */
    public String generateTextReport(String title, String content) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("%s_%s.txt", 
            title.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
        String filepath = reportDirectory + File.separator + filename;
        
        try (FileWriter writer = new FileWriter(filepath)) {
            writer.write(title + "\n");
            writer.write("=".repeat(title.length()) + "\n\n");
            writer.write("Generated: " + LocalDateTime.now() + "\n\n");
            writer.write(content);
            
            logger.info("Text report generated: {}", filepath);
            return filepath;
            
        } catch (IOException e) {
            logger.error("Error generating text report", e);
            return null;
        }
    }
    
    /**
     * Escape CSV special characters
     */
    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
    
    /**
     * Get report directory
     */
    public String getReportDirectory() {
        return reportDirectory;
    }
    
    /**
     * Open report directory in file explorer
     */
    public void openReportDirectory() {
        try {
            File dir = new File(reportDirectory);
            if (dir.exists()) {
                java.awt.Desktop.getDesktop().open(dir);
                logger.info("Opened report directory: {}", reportDirectory);
            } else {
                logger.warn("Report directory does not exist: {}", reportDirectory);
            }
        } catch (Exception e) {
            logger.error("Error opening report directory", e);
        }
    }
}
