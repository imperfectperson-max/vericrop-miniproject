package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.persistence.PersistedShipment;
import org.vericrop.gui.persistence.PersistedSimulation;
import org.vericrop.gui.persistence.ShipmentPersistenceService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for exporting reports in various formats (TXT, CSV).
 * Generates downloadable reports for shipments and simulations.
 */
public class ReportExportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportExportService.class);
    
    private static final String REPORTS_DIRECTORY = "generated_reports";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // CSV Header constants for maintainability
    private static final String CSV_HEADER_SHIPMENT_SUMMARY = 
            "Batch ID,Status,Location,Temperature (°C),Humidity (%),Vehicle,ETA,Created At,Updated At,Origin,Destination,Quality Score";
    private static final String CSV_HEADER_TEMPERATURE_LOG = 
            "Batch ID,Timestamp,Temperature (°C),Humidity (%),Status,Location,Source";
    private static final String CSV_HEADER_QUALITY_COMPLIANCE = 
            "Batch ID,Simulation Type,Scenario,Status,Completed,Final Quality (%),Initial Quality (%),Violations,Compliance Status,Avg Temp (°C),Min Temp (°C),Max Temp (°C),Start Time,End Time";
    private static final String CSV_HEADER_DELIVERY_PERFORMANCE = 
            "Batch ID,Type,Status,Duration (min),Final Quality (%),Avg Temperature (°C),Waypoints,Start Time,End Time";
    private static final String CSV_HEADER_SIMULATION_LOG = 
            "ID,Batch ID,Farmer ID,Scenario,Status,Completed,Initial Quality (%),Final Quality (%),Waypoints,Avg Temp (°C),Min Temp (°C),Max Temp (°C),Avg Humidity (%),Violations,Compliance,Origin,Destination,Start Time,End Time";
    
    private final ShipmentPersistenceService persistenceService;
    private final Path reportsDirectory;
    
    /**
     * Supported export formats
     */
    public enum ExportFormat {
        TXT,
        CSV,
        JSON,
        HTML,
        PDF
    }
    
    /**
     * Report types
     */
    public enum ReportType {
        SHIPMENT_SUMMARY("Shipment Summary"),
        TEMPERATURE_LOG("Temperature Log"),
        QUALITY_COMPLIANCE("Quality Compliance"),
        DELIVERY_PERFORMANCE("Delivery Performance"),
        SIMULATION_LOG("Simulation Log");
        
        private final String displayName;
        
        ReportType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Constructor
     */
    public ReportExportService(ShipmentPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        this.reportsDirectory = Paths.get(REPORTS_DIRECTORY);
        initializeReportsDirectory();
    }
    
    /**
     * Initialize reports directory
     */
    private void initializeReportsDirectory() {
        try {
            if (!Files.exists(reportsDirectory)) {
                Files.createDirectories(reportsDirectory);
                logger.info("Created reports directory: {}", reportsDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create reports directory", e);
        }
    }
    
    /**
     * Generate a report filename with type and date range
     */
    public String generateFilename(ReportType reportType, LocalDate startDate, LocalDate endDate, ExportFormat format) {
        String typeName = reportType.name().toLowerCase().replace("_", "-");
        String start = startDate.format(DATE_FORMATTER);
        String end = endDate.format(DATE_FORMATTER);
        String extension = getFileExtension(format);
        return String.format("%s_%s_to_%s.%s", typeName, start, end, extension);
    }
    
    /**
     * Get file extension for export format
     */
    private String getFileExtension(ExportFormat format) {
        switch (format) {
            case TXT: return "txt";
            case CSV: return "csv";
            case JSON: return "json";
            case HTML: return "html";
            case PDF: return "pdf";
            default: return "txt";
        }
    }
    
    /**
     * Export a report to the specified format
     */
    public File exportReport(ReportType reportType, LocalDate startDate, LocalDate endDate, ExportFormat format) 
            throws IOException {
        String filename = generateFilename(reportType, startDate, endDate, format);
        Path filePath = reportsDirectory.resolve(filename);
        
        String content;
        switch (reportType) {
            case SHIPMENT_SUMMARY:
                content = generateShipmentSummaryReport(startDate, endDate, format);
                break;
            case TEMPERATURE_LOG:
                content = generateTemperatureLogReport(startDate, endDate, format);
                break;
            case QUALITY_COMPLIANCE:
                content = generateQualityComplianceReport(startDate, endDate, format);
                break;
            case DELIVERY_PERFORMANCE:
                content = generateDeliveryPerformanceReport(startDate, endDate, format);
                break;
            case SIMULATION_LOG:
                content = generateSimulationLogReport(startDate, endDate, format);
                break;
            default:
                throw new IllegalArgumentException("Unknown report type: " + reportType);
        }
        
        // For PDF, we need to convert HTML to PDF
        if (format == ExportFormat.PDF) {
            String htmlContent = content;
            content = convertHtmlToPdf(htmlContent);
        }
        
        Files.writeString(filePath, content);
        logger.info("Exported report: {} ({} format)", filePath, format);
        
        return filePath.toFile();
    }
    
    /**
     * Convert HTML content to PDF.
     * Note: For true PDF generation, this would require integrating a library like Apache PDFBox, iText, or Flying Saucer.
     * For now, we return the HTML content with a note that PDF format is not fully supported.
     * This allows the file to be opened in a browser and manually saved as PDF if needed.
     */
    private String convertHtmlToPdf(String htmlContent) {
        // Add a prominent note at the top of the HTML content indicating it's HTML format
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>VeriCrop Report (HTML Format)</title>\n");
        sb.append("<style>\n");
        sb.append(".pdf-notice { background-color: #fff3cd; border: 2px solid #ffc107; padding: 15px; margin: 20px; border-radius: 5px; }\n");
        sb.append(".pdf-notice h3 { margin-top: 0; color: #856404; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"pdf-notice\">\n");
        sb.append("<h3>⚠️ PDF Export Notice</h3>\n");
        sb.append("<p>This report was exported in HTML format with a .pdf extension. ");
        sb.append("To view as PDF:</p>\n");
        sb.append("<ol>\n");
        sb.append("<li>Open this file in a web browser (Chrome, Firefox, Edge)</li>\n");
        sb.append("<li>Use the browser's Print function (Ctrl+P or Cmd+P)</li>\n");
        sb.append("<li>Select 'Save as PDF' as the destination</li>\n");
        sb.append("</ol>\n");
        sb.append("<p>For true PDF generation, integrate a PDF library like Apache PDFBox or iText.</p>\n");
        sb.append("</div>\n");
        
        // Extract the body content from the HTML and append it
        int bodyStart = htmlContent.indexOf("<body>");
        int bodyEnd = htmlContent.indexOf("</body>");
        if (bodyStart >= 0 && bodyEnd > bodyStart) {
            sb.append(htmlContent.substring(bodyStart + 6, bodyEnd));
        } else {
            // If no body tags, append the whole content
            sb.append(htmlContent);
        }
        
        sb.append("\n</body>\n</html>");
        
        logger.info("PDF export generated as HTML with instructions for manual conversion.");
        return sb.toString();
    }
    
    /**
     * Generate Shipment Summary Report
     */
    private String generateShipmentSummaryReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedShipment> shipments = persistenceService.getShipmentsByDateRange(startDate, endDate);
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        
        switch (format) {
            case CSV:
                return generateShipmentSummaryCsv(shipments, startDate, endDate);
            case JSON:
                return generateShipmentSummaryJson(shipments, simulations, startDate, endDate);
            case HTML:
            case PDF:
                return generateShipmentSummaryHtml(shipments, simulations, startDate, endDate);
            default:
                return generateShipmentSummaryTxt(shipments, startDate, endDate);
        }
    }
    
    private String generateShipmentSummaryTxt(List<PersistedShipment> shipments, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("           SHIPMENT SUMMARY REPORT\n");
        sb.append("=================================================\n\n");
        sb.append("Date Range: ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Generated: ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n");
        sb.append("Total Shipments: ").append(shipments.size()).append("\n\n");
        
        sb.append("-------------------------------------------------\n");
        sb.append("SHIPMENT DETAILS\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedShipment shipment : shipments) {
            sb.append("Batch ID:     ").append(shipment.getBatchId()).append("\n");
            
            // Add scenario-specific insights based on batch prefix
            String batchId = shipment.getBatchId();
            if (batchId.contains("APPLES")) {
                sb.append("Type:         Farm to Consumer Direct (Summer Apples)\n");
                sb.append("Route:        Warehouse stop included, optimal cold chain\n");
            } else if (batchId.contains("CARROTS")) {
                sb.append("Type:         Local Producer Delivery (Organic Carrots)\n");
                sb.append("Route:        Short direct route, no warehouse\n");
            } else if (batchId.contains("VEGGIES") || batchId.contains("VEGETABLES")) {
                sb.append("Type:         Cross-Region Long Haul (Mixed Vegetables)\n");
                sb.append("Route:        Extended delivery with environmental events\n");
            }
            
            sb.append("Status:       ").append(shipment.getStatus()).append("\n");
            sb.append("Location:     ").append(shipment.getLocation()).append("\n");
            sb.append("Temperature:  ").append(String.format("%.1f°C", shipment.getTemperature())).append("\n");
            sb.append("Humidity:     ").append(String.format("%.1f%%", shipment.getHumidity())).append("\n");
            sb.append("Vehicle:      ").append(shipment.getVehicle() != null ? shipment.getVehicle() : "N/A").append("\n");
            sb.append("Created:      ").append(formatTimestamp(shipment.getCreatedAt())).append("\n");
            sb.append("Updated:      ").append(formatTimestamp(shipment.getUpdatedAt())).append("\n");
            sb.append("\n");
        }
        
        // Summary statistics
        sb.append("-------------------------------------------------\n");
        sb.append("SUMMARY STATISTICS\n");
        sb.append("-------------------------------------------------\n\n");
        
        if (!shipments.isEmpty()) {
            double avgTemp = shipments.stream().mapToDouble(PersistedShipment::getTemperature).average().orElse(0);
            double avgHumidity = shipments.stream().mapToDouble(PersistedShipment::getHumidity).average().orElse(0);
            long inTransit = shipments.stream().filter(s -> "IN_TRANSIT".equalsIgnoreCase(s.getStatus())).count();
            long delivered = shipments.stream().filter(s -> "DELIVERED".equalsIgnoreCase(s.getStatus())).count();
            
            // Count shipments by type
            long apples = shipments.stream().filter(s -> s.getBatchId().contains("APPLES")).count();
            long carrots = shipments.stream().filter(s -> s.getBatchId().contains("CARROTS")).count();
            long veggies = shipments.stream().filter(s -> 
                s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES")).count();
            
            sb.append("Average Temperature: ").append(String.format("%.1f°C", avgTemp)).append("\n");
            sb.append("Average Humidity:    ").append(String.format("%.1f%%", avgHumidity)).append("\n");
            sb.append("In Transit:          ").append(inTransit).append("\n");
            sb.append("Delivered:           ").append(delivered).append("\n\n");
            
            sb.append("By Delivery Type:\n");
            sb.append("  Farm to Consumer (Apples):    ").append(apples).append("\n");
            sb.append("  Local Producer (Carrots):     ").append(carrots).append("\n");
            sb.append("  Cross-Region (Vegetables):    ").append(veggies).append("\n");
        }
        
        return sb.toString();
    }
    
    private String generateShipmentSummaryCsv(List<PersistedShipment> shipments, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_SHIPMENT_SUMMARY).append("\n");
        
        // CSV Data
        for (PersistedShipment shipment : shipments) {
            sb.append(escapeCsv(shipment.getBatchId())).append(",");
            sb.append(escapeCsv(shipment.getStatus())).append(",");
            sb.append(escapeCsv(shipment.getLocation())).append(",");
            sb.append(String.format("%.1f", shipment.getTemperature())).append(",");
            sb.append(String.format("%.1f", shipment.getHumidity())).append(",");
            sb.append(escapeCsv(shipment.getVehicle())).append(",");
            sb.append(escapeCsv(shipment.getEta())).append(",");
            sb.append(formatTimestamp(shipment.getCreatedAt())).append(",");
            sb.append(formatTimestamp(shipment.getUpdatedAt())).append(",");
            sb.append(escapeCsv(shipment.getOrigin())).append(",");
            sb.append(escapeCsv(shipment.getDestination())).append(",");
            sb.append(String.format("%.1f", shipment.getQualityScore())).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate Shipment Summary in JSON format
     */
    private String generateShipmentSummaryJson(List<PersistedShipment> shipments, 
                                                List<PersistedSimulation> simulations,
                                                LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportType\": \"Shipment Summary\",\n");
        sb.append("  \"dateRange\": {\n");
        sb.append("    \"start\": \"").append(startDate.format(DATE_FORMATTER)).append("\",\n");
        sb.append("    \"end\": \"").append(endDate.format(DATE_FORMATTER)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"generated\": \"").append(Instant.now().toString()).append("\",\n");
        sb.append("  \"totalShipments\": ").append(shipments.size()).append(",\n");
        sb.append("  \"shipmentsByType\": {\n");
        
        long apples = shipments.stream().filter(s -> s.getBatchId().contains("APPLES")).count();
        long carrots = shipments.stream().filter(s -> s.getBatchId().contains("CARROTS")).count();
        long veggies = shipments.stream().filter(s -> 
            s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES")).count();
        
        sb.append("    \"farmToConsumer\": ").append(apples).append(",\n");
        sb.append("    \"localProducer\": ").append(carrots).append(",\n");
        sb.append("    \"crossRegion\": ").append(veggies).append("\n");
        sb.append("  },\n");
        sb.append("  \"shipments\": [\n");
        
        for (int i = 0; i < shipments.size(); i++) {
            PersistedShipment shipment = shipments.get(i);
            sb.append("    {\n");
            sb.append("      \"batchId\": \"").append(escapeJson(shipment.getBatchId())).append("\",\n");
            
            // Determine delivery type
            String deliveryType = "Other";
            if (shipment.getBatchId().contains("APPLES")) {
                deliveryType = "Farm to Consumer Direct";
            } else if (shipment.getBatchId().contains("CARROTS")) {
                deliveryType = "Local Producer Delivery";
            } else if (shipment.getBatchId().contains("VEGGIES") || shipment.getBatchId().contains("VEGETABLES")) {
                deliveryType = "Cross-Region Long Haul";
            }
            
            sb.append("      \"deliveryType\": \"").append(deliveryType).append("\",\n");
            sb.append("      \"status\": \"").append(escapeJson(shipment.getStatus())).append("\",\n");
            sb.append("      \"location\": \"").append(escapeJson(shipment.getLocation())).append("\",\n");
            sb.append("      \"temperature\": ").append(String.format("%.1f", shipment.getTemperature())).append(",\n");
            sb.append("      \"humidity\": ").append(String.format("%.1f", shipment.getHumidity())).append(",\n");
            sb.append("      \"vehicle\": \"").append(escapeJson(shipment.getVehicle())).append("\",\n");
            sb.append("      \"createdAt\": \"").append(formatTimestamp(shipment.getCreatedAt())).append("\",\n");
            sb.append("      \"updatedAt\": \"").append(formatTimestamp(shipment.getUpdatedAt())).append("\"\n");
            sb.append("    }");
            if (i < shipments.size() - 1) sb.append(",");
            sb.append("\n");
        }
        
        sb.append("  ]\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    /**
     * Generate Shipment Summary in HTML format
     */
    private String generateShipmentSummaryHtml(List<PersistedShipment> shipments,
                                                List<PersistedSimulation> simulations,
                                                LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Shipment Summary Report</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        sb.append("    .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("    h1 { color: #2563eb; border-bottom: 3px solid #2563eb; padding-bottom: 10px; }\n");
        sb.append("    h2 { color: #1e40af; margin-top: 30px; }\n");
        sb.append("    .header-info { background-color: #eff6ff; padding: 15px; border-radius: 5px; margin-bottom: 20px; }\n");
        sb.append("    table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        sb.append("    th { background-color: #2563eb; color: white; padding: 12px; text-align: left; }\n");
        sb.append("    td { padding: 10px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("    tr:hover { background-color: #f9fafb; }\n");
        sb.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n");
        sb.append("    .badge-apples { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-carrots { background-color: #fef3c7; color: #92400e; }\n");
        sb.append("    .badge-veggies { background-color: #e0e7ff; color: #3730a3; }\n");
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>📦 Shipment Summary Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Total Shipments:</strong> ").append(shipments.size()).append("\n");
        sb.append("    </div>\n");
        
        // Statistics
        long apples = shipments.stream().filter(s -> s.getBatchId().contains("APPLES")).count();
        long carrots = shipments.stream().filter(s -> s.getBatchId().contains("CARROTS")).count();
        long veggies = shipments.stream().filter(s -> 
            s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES")).count();
        long inTransit = shipments.stream().filter(s -> "IN_TRANSIT".equalsIgnoreCase(s.getStatus())).count();
        long delivered = shipments.stream().filter(s -> "DELIVERED".equalsIgnoreCase(s.getStatus())).count();
        
        sb.append("    <h2>📊 Statistics</h2>\n");
        sb.append("    <div class=\"stats\">\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(apples).append("</div><div class=\"stat-label\">Farm to Consumer (Apples)</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(carrots).append("</div><div class=\"stat-label\">Local Producer (Carrots)</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(veggies).append("</div><div class=\"stat-label\">Cross-Region (Vegetables)</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(inTransit).append("</div><div class=\"stat-label\">In Transit</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(delivered).append("</div><div class=\"stat-label\">Delivered</div></div>\n");
        sb.append("    </div>\n");
        
        // Shipment table
        sb.append("    <h2>📋 Shipment Details</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Batch ID</th><th>Type</th><th>Status</th><th>Location</th><th>Temperature</th><th>Humidity</th><th>Vehicle</th></tr>\n");
        
        for (PersistedShipment shipment : shipments) {
            sb.append("      <tr>\n");
            sb.append("        <td>").append(escapeHtml(shipment.getBatchId())).append("</td>\n");
            
            // Determine type with badge
            String badgeClass = "";
            String typeName = "Other";
            if (shipment.getBatchId().contains("APPLES")) {
                badgeClass = "badge-apples";
                typeName = "Farm to Consumer";
            } else if (shipment.getBatchId().contains("CARROTS")) {
                badgeClass = "badge-carrots";
                typeName = "Local Producer";
            } else if (shipment.getBatchId().contains("VEGGIES") || shipment.getBatchId().contains("VEGETABLES")) {
                badgeClass = "badge-veggies";
                typeName = "Cross-Region";
            }
            
            sb.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(typeName).append("</span></td>\n");
            sb.append("        <td>").append(escapeHtml(shipment.getStatus())).append("</td>\n");
            sb.append("        <td>").append(escapeHtml(shipment.getLocation())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f°C", shipment.getTemperature())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", shipment.getHumidity())).append("</td>\n");
            sb.append("        <td>").append(escapeHtml(shipment.getVehicle())).append("</td>\n");
            sb.append("      </tr>\n");
        }
        
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
    }
    
    /**
     * Generate Temperature Log Report
     */
    private String generateTemperatureLogReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedShipment> shipments = persistenceService.getShipmentsByDateRange(startDate, endDate);
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        
        switch (format) {
            case CSV:
                return generateTemperatureLogCsv(shipments, simulations, startDate, endDate);
            case JSON:
                return generateTemperatureLogJson(shipments, simulations, startDate, endDate);
            case HTML:
            case PDF:
                return generateTemperatureLogHtml(shipments, simulations, startDate, endDate);
            default:
                return generateTemperatureLogTxt(shipments, simulations, startDate, endDate);
        }
    }
    
    private String generateTemperatureLogTxt(List<PersistedShipment> shipments, 
                                              List<PersistedSimulation> simulations,
                                              LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("           TEMPERATURE LOG REPORT\n");
        sb.append("=================================================\n\n");
        sb.append("Date Range: ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Generated: ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n\n");
        
        sb.append("-------------------------------------------------\n");
        sb.append("SHIPMENT TEMPERATURE DATA\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedShipment shipment : shipments) {
            sb.append(String.format("%-20s | %s | %.1f°C | %.1f%%\n",
                    shipment.getBatchId(),
                    formatTimestamp(shipment.getUpdatedAt()),
                    shipment.getTemperature(),
                    shipment.getHumidity()));
        }
        
        sb.append("\n-------------------------------------------------\n");
        sb.append("SIMULATION TEMPERATURE SUMMARY\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append("Batch ID:        ").append(simulation.getBatchId()).append("\n");
            sb.append("Scenario:        ").append(simulation.getScenarioId()).append("\n");
            sb.append("Avg Temperature: ").append(String.format("%.1f°C", simulation.getAvgTemperature())).append("\n");
            sb.append("Min Temperature: ").append(String.format("%.1f°C", simulation.getMinTemperature())).append("\n");
            sb.append("Max Temperature: ").append(String.format("%.1f°C", simulation.getMaxTemperature())).append("\n");
            sb.append("Violations:      ").append(simulation.getViolationsCount()).append("\n");
            sb.append("Compliance:      ").append(simulation.getComplianceStatus()).append("\n");
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private String generateTemperatureLogCsv(List<PersistedShipment> shipments, 
                                              List<PersistedSimulation> simulations,
                                              LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_TEMPERATURE_LOG).append("\n");
        
        // Shipment temperature data
        for (PersistedShipment shipment : shipments) {
            sb.append(escapeCsv(shipment.getBatchId())).append(",");
            sb.append(formatTimestamp(shipment.getUpdatedAt())).append(",");
            sb.append(String.format("%.1f", shipment.getTemperature())).append(",");
            sb.append(String.format("%.1f", shipment.getHumidity())).append(",");
            sb.append(escapeCsv(shipment.getStatus())).append(",");
            sb.append(escapeCsv(shipment.getLocation())).append(",");
            sb.append("Shipment\n");
        }
        
        // Simulation temperature data (summary)
        for (PersistedSimulation simulation : simulations) {
            sb.append(escapeCsv(simulation.getBatchId())).append(",");
            sb.append(formatTimestamp(simulation.getStartTime())).append(",");
            sb.append(String.format("%.1f", simulation.getAvgTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getAvgHumidity())).append(",");
            sb.append(escapeCsv(simulation.getStatus())).append(",");
            sb.append(escapeCsv(simulation.getOrigin() != null ? simulation.getOrigin() : "N/A")).append(",");
            sb.append("Simulation\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate Quality Compliance Report
     */
    private String generateQualityComplianceReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        
        switch (format) {
            case CSV:
                return generateQualityComplianceCsv(simulations, startDate, endDate);
            case JSON:
                return generateQualityComplianceJson(simulations, startDate, endDate);
            case HTML:
            case PDF:
                return generateQualityComplianceHtml(simulations, startDate, endDate);
            default:
                return generateQualityComplianceTxt(simulations, startDate, endDate);
        }
    }
    
    private String generateQualityComplianceTxt(List<PersistedSimulation> simulations, 
                                                 LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("         QUALITY COMPLIANCE REPORT\n");
        sb.append("=================================================\n\n");
        sb.append("Date Range: ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Generated: ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n");
        sb.append("Total Simulations: ").append(simulations.size()).append("\n\n");
        
        // Separate simulations by type using helper method
        List<PersistedSimulation> applesSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_1_APPLES);
        List<PersistedSimulation> carrotsSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_2_CARROTS);
        List<PersistedSimulation> veggiesSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_3_VEGETABLES);
        
        long compliant = simulations.stream()
            .filter(s -> "COMPLIANT".equals(s.getComplianceStatus()))
            .count();
        long nonCompliant = simulations.size() - compliant;
        double complianceRate = simulations.isEmpty() ? 0 : (compliant * 100.0 / simulations.size());
        
        sb.append("-------------------------------------------------\n");
        sb.append("OVERALL COMPLIANCE SUMMARY\n");
        sb.append("-------------------------------------------------\n\n");
        sb.append("Compliant Runs:     ").append(compliant).append("\n");
        sb.append("Non-Compliant Runs: ").append(nonCompliant).append("\n");
        sb.append("Compliance Rate:    ").append(String.format("%.1f%%", complianceRate)).append("\n\n");
        
        // Breakdown by simulation type
        if (!applesSimulations.isEmpty() || !carrotsSimulations.isEmpty() || !veggiesSimulations.isEmpty()) {
            sb.append("-------------------------------------------------\n");
            sb.append("COMPLIANCE BY SIMULATION TYPE\n");
            sb.append("-------------------------------------------------\n\n");
            
            if (!applesSimulations.isEmpty()) {
                appendSimulationTypeComplianceText(sb, "Example 1 - Farm to Consumer (Apples)", 
                    applesSimulations, "30min warehouse route, optimal cold chain");
            }
            
            if (!carrotsSimulations.isEmpty()) {
                appendSimulationTypeComplianceText(sb, "Example 2 - Local Producer (Carrots)", 
                    carrotsSimulations, "15min direct route, strict temp control");
            }
            
            if (!veggiesSimulations.isEmpty()) {
                appendSimulationTypeComplianceText(sb, "Example 3 - Cross-Region (Vegetables)", 
                    veggiesSimulations, "45min extended route, temp spike events");
            }
        }
        
        sb.append("-------------------------------------------------\n");
        sb.append("DETAILED COMPLIANCE DATA\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            String status = simulation.isCompleted() ? "✓ COMPLETED" : "○ IN PROGRESS";
            String compliance = "COMPLIANT".equals(simulation.getComplianceStatus()) ? "✓" : "✗";
            
            // Determine simulation type using helper method
            SimulationType simType = SimulationType.fromSimulation(simulation);
            String simTypeLabel = simType == SimulationType.OTHER ? "[Other]" : 
                                 "[" + simType.getDisplayName().split(" - ")[0] + "]";
            
            sb.append(String.format("[%s] %s Batch: %s\n", compliance, simTypeLabel, simulation.getBatchId()));
            sb.append(String.format("    Status: %s | Quality: %.1f%% | Violations: %d\n",
                    status, simulation.getFinalQuality(), simulation.getViolationsCount()));
            sb.append(String.format("    Temp Range: %.1f°C - %.1f°C (avg: %.1f°C)\n",
                    simulation.getMinTemperature(), simulation.getMaxTemperature(), simulation.getAvgTemperature()));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Helper method to append simulation type compliance statistics in text format.
     */
    private void appendSimulationTypeComplianceText(StringBuilder sb, String typeName, 
            List<PersistedSimulation> simulations, String description) {
        // Guard against empty list to prevent division by zero
        if (simulations.isEmpty()) {
            return;
        }
        
        long compliant = simulations.stream()
                .filter(s -> "COMPLIANT".equals(s.getComplianceStatus()))
                .count();
        double complianceRate = (compliant * 100.0 / simulations.size());
        double avgFinalQuality = simulations.stream()
                .mapToDouble(PersistedSimulation::getFinalQuality)
                .average().orElse(0.0);
        int totalViolations = simulations.stream()
                .mapToInt(PersistedSimulation::getViolationsCount)
                .sum();
        
        sb.append(typeName).append("\n");
        sb.append("  Description:        ").append(description).append("\n");
        sb.append("  Runs:               ").append(simulations.size()).append("\n");
        sb.append("  Compliant:          ").append(compliant).append("/").append(simulations.size())
          .append(" (").append(String.format("%.1f%%", complianceRate)).append(")\n");
        sb.append("  Avg Final Quality:  ").append(String.format("%.1f%%", avgFinalQuality)).append("\n");
        sb.append("  Total Violations:   ").append(totalViolations).append("\n\n");
    }
    
    private String generateQualityComplianceCsv(List<PersistedSimulation> simulations, 
                                                 LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_QUALITY_COMPLIANCE).append("\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append(escapeCsv(simulation.getBatchId())).append(",");
            
            // Determine simulation type using helper method
            SimulationType simType = SimulationType.fromSimulation(simulation);
            sb.append(escapeCsv(simType.getDisplayName())).append(",");
            sb.append(escapeCsv(simulation.getScenarioId())).append(",");
            sb.append(escapeCsv(simulation.getStatus())).append(",");
            sb.append(simulation.isCompleted()).append(",");
            sb.append(String.format("%.1f", simulation.getFinalQuality())).append(",");
            sb.append(String.format("%.1f", simulation.getInitialQuality())).append(",");
            sb.append(simulation.getViolationsCount()).append(",");
            sb.append(escapeCsv(simulation.getComplianceStatus())).append(",");
            sb.append(String.format("%.1f", simulation.getAvgTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getMinTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getMaxTemperature())).append(",");
            sb.append(formatTimestamp(simulation.getStartTime())).append(",");
            sb.append(formatTimestamp(simulation.getEndTime())).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate Delivery Performance Report
     */
    private String generateDeliveryPerformanceReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        List<PersistedShipment> shipments = persistenceService.getShipmentsByDateRange(startDate, endDate);
        
        switch (format) {
            case CSV:
                return generateDeliveryPerformanceCsv(simulations, shipments, startDate, endDate);
            case JSON:
                return generateDeliveryPerformanceJson(simulations, shipments, startDate, endDate);
            case HTML:
            case PDF:
                return generateDeliveryPerformanceHtml(simulations, shipments, startDate, endDate);
            default:
                return generateDeliveryPerformanceTxt(simulations, shipments, startDate, endDate);
        }
    }
    
    private String generateDeliveryPerformanceTxt(List<PersistedSimulation> simulations,
                                                   List<PersistedShipment> shipments,
                                                   LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("        DELIVERY PERFORMANCE REPORT\n");
        sb.append("=================================================\n\n");
        sb.append("Date Range: ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Generated: ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n\n");
        
        long completed = simulations.stream().filter(PersistedSimulation::isCompleted).count();
        long delivered = shipments.stream()
            .filter(s -> "DELIVERED".equalsIgnoreCase(s.getStatus()))
            .count();
        
        double avgQuality = simulations.stream()
            .filter(PersistedSimulation::isCompleted)
            .mapToDouble(PersistedSimulation::getFinalQuality)
            .average()
            .orElse(0);
        
        sb.append("-------------------------------------------------\n");
        sb.append("PERFORMANCE METRICS\n");
        sb.append("-------------------------------------------------\n\n");
        sb.append("Total Simulations:      ").append(simulations.size()).append("\n");
        sb.append("Completed Simulations:  ").append(completed).append("\n");
        sb.append("Delivered Shipments:    ").append(delivered).append("\n");
        sb.append("Average Final Quality:  ").append(String.format("%.1f%%", avgQuality)).append("\n\n");
        
        sb.append("-------------------------------------------------\n");
        sb.append("DELIVERY DETAILS\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            if (simulation.isCompleted()) {
                long durationMs = simulation.getEndTime() - simulation.getStartTime();
                long durationMinutes = durationMs / (1000 * 60);
                
                sb.append(String.format("Batch: %s | Duration: %d min | Quality: %.1f%%\n",
                        simulation.getBatchId(), durationMinutes, simulation.getFinalQuality()));
            }
        }
        
        return sb.toString();
    }
    
    private String generateDeliveryPerformanceCsv(List<PersistedSimulation> simulations,
                                                   List<PersistedShipment> shipments,
                                                   LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_DELIVERY_PERFORMANCE).append("\n");
        
        for (PersistedSimulation simulation : simulations) {
            long durationMs = simulation.getEndTime() - simulation.getStartTime();
            long durationMinutes = durationMs > 0 ? durationMs / (1000 * 60) : 0;
            
            sb.append(escapeCsv(simulation.getBatchId())).append(",");
            sb.append("Simulation,");
            sb.append(escapeCsv(simulation.getStatus())).append(",");
            sb.append(durationMinutes).append(",");
            sb.append(String.format("%.1f", simulation.getFinalQuality())).append(",");
            sb.append(String.format("%.1f", simulation.getAvgTemperature())).append(",");
            sb.append(simulation.getWaypointsCount()).append(",");
            sb.append(formatTimestamp(simulation.getStartTime())).append(",");
            sb.append(formatTimestamp(simulation.getEndTime())).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate Simulation Log Report
     */
    private String generateSimulationLogReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        
        switch (format) {
            case CSV:
                return generateSimulationLogCsv(simulations, startDate, endDate);
            case JSON:
                return generateSimulationLogJson(simulations, startDate, endDate);
            case HTML:
            case PDF:
                return generateSimulationLogHtml(simulations, startDate, endDate);
            default:
                return generateSimulationLogTxt(simulations, startDate, endDate);
        }
    }
    
    private String generateSimulationLogTxt(List<PersistedSimulation> simulations, 
                                             LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("           SIMULATION LOG REPORT\n");
        sb.append("=================================================\n\n");
        sb.append("Date Range: ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Generated: ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n");
        sb.append("Total Simulations: ").append(simulations.size()).append("\n\n");
        
        sb.append("-------------------------------------------------\n");
        sb.append("SIMULATION DETAILS\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append("ID:              ").append(simulation.getId()).append("\n");
            sb.append("Batch ID:        ").append(simulation.getBatchId()).append("\n");
            sb.append("Farmer ID:       ").append(simulation.getFarmerId()).append("\n");
            sb.append("Scenario:        ").append(simulation.getScenarioId()).append("\n");
            sb.append("Status:          ").append(simulation.getStatus()).append("\n");
            sb.append("Completed:       ").append(simulation.isCompleted() ? "Yes" : "No").append("\n");
            sb.append("Initial Quality: ").append(String.format("%.1f%%", simulation.getInitialQuality())).append("\n");
            sb.append("Final Quality:   ").append(String.format("%.1f%%", simulation.getFinalQuality())).append("\n");
            sb.append("Waypoints:       ").append(simulation.getWaypointsCount()).append("\n");
            sb.append("Temperature:     ").append(String.format("%.1f°C (min: %.1f, max: %.1f)",
                    simulation.getAvgTemperature(), simulation.getMinTemperature(), simulation.getMaxTemperature())).append("\n");
            sb.append("Humidity:        ").append(String.format("%.1f%%", simulation.getAvgHumidity())).append("\n");
            sb.append("Violations:      ").append(simulation.getViolationsCount()).append("\n");
            sb.append("Compliance:      ").append(simulation.getComplianceStatus()).append("\n");
            sb.append("Origin:          ").append(simulation.getOrigin() != null ? simulation.getOrigin() : "N/A").append("\n");
            sb.append("Destination:     ").append(simulation.getDestination() != null ? simulation.getDestination() : "N/A").append("\n");
            sb.append("Start Time:      ").append(formatTimestamp(simulation.getStartTime())).append("\n");
            sb.append("End Time:        ").append(formatTimestamp(simulation.getEndTime())).append("\n");
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private String generateSimulationLogCsv(List<PersistedSimulation> simulations, 
                                             LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_SIMULATION_LOG).append("\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append(escapeCsv(simulation.getId())).append(",");
            sb.append(escapeCsv(simulation.getBatchId())).append(",");
            sb.append(escapeCsv(simulation.getFarmerId())).append(",");
            sb.append(escapeCsv(simulation.getScenarioId())).append(",");
            sb.append(escapeCsv(simulation.getStatus())).append(",");
            sb.append(simulation.isCompleted()).append(",");
            sb.append(String.format("%.1f", simulation.getInitialQuality())).append(",");
            sb.append(String.format("%.1f", simulation.getFinalQuality())).append(",");
            sb.append(simulation.getWaypointsCount()).append(",");
            sb.append(String.format("%.1f", simulation.getAvgTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getMinTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getMaxTemperature())).append(",");
            sb.append(String.format("%.1f", simulation.getAvgHumidity())).append(",");
            sb.append(simulation.getViolationsCount()).append(",");
            sb.append(escapeCsv(simulation.getComplianceStatus())).append(",");
            sb.append(escapeCsv(simulation.getOrigin())).append(",");
            sb.append(escapeCsv(simulation.getDestination())).append(",");
            sb.append(formatTimestamp(simulation.getStartTime())).append(",");
            sb.append(formatTimestamp(simulation.getEndTime())).append("\n");
        }
        
        return sb.toString();
    }
    
    // Simplified JSON/HTML implementations for remaining report types
    // These provide basic JSON structure and reuse TXT content for HTML
    // Can be expanded in future for more detailed JSON/HTML representations
    
    private String generateTemperatureLogJson(List<PersistedShipment> shipments, 
                                               List<PersistedSimulation> simulations,
                                               LocalDate startDate, LocalDate endDate) {
        // Basic JSON summary - can be expanded with full data structure
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportType\": \"Temperature Log\",\n");
        sb.append("  \"dateRange\": {\n");
        sb.append("    \"start\": \"").append(startDate.format(DATE_FORMATTER)).append("\",\n");
        sb.append("    \"end\": \"").append(endDate.format(DATE_FORMATTER)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"shipmentCount\": ").append(shipments.size()).append(",\n");
        sb.append("  \"simulationCount\": ").append(simulations.size()).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private String generateTemperatureLogHtml(List<PersistedShipment> shipments, 
                                               List<PersistedSimulation> simulations,
                                               LocalDate startDate, LocalDate endDate) {
        String txtContent = generateTemperatureLogTxt(shipments, simulations, startDate, endDate);
        return wrapInBasicHtml("Temperature Log Report", txtContent);
    }
    
    private String generateQualityComplianceJson(List<PersistedSimulation> simulations, 
                                                  LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportType\": \"Quality Compliance\",\n");
        sb.append("  \"dateRange\": {\n");
        sb.append("    \"start\": \"").append(startDate.format(DATE_FORMATTER)).append("\",\n");
        sb.append("    \"end\": \"").append(endDate.format(DATE_FORMATTER)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"generated\": \"").append(Instant.now().toString()).append("\",\n");
        sb.append("  \"simulationCount\": ").append(simulations.size()).append(",\n");
        
        // Overall compliance stats
        long compliant = simulations.stream()
            .filter(s -> "COMPLIANT".equals(s.getComplianceStatus()))
            .count();
        double complianceRate = simulations.isEmpty() ? 0 : (compliant * 100.0 / simulations.size());
        
        sb.append("  \"overallCompliance\": {\n");
        sb.append("    \"compliant\": ").append(compliant).append(",\n");
        sb.append("    \"nonCompliant\": ").append(simulations.size() - compliant).append(",\n");
        sb.append("    \"complianceRate\": ").append(String.format("%.1f", complianceRate)).append("\n");
        sb.append("  },\n");
        
        // Breakdown by simulation type using helper method
        long applesCount = filterSimulationsByType(simulations, SimulationType.EXAMPLE_1_APPLES).size();
        long carrotsCount = filterSimulationsByType(simulations, SimulationType.EXAMPLE_2_CARROTS).size();
        long veggiesCount = filterSimulationsByType(simulations, SimulationType.EXAMPLE_3_VEGETABLES).size();
        
        sb.append("  \"bySimulationType\": {\n");
        sb.append("    \"example1Apples\": ").append(applesCount).append(",\n");
        sb.append("    \"example2Carrots\": ").append(carrotsCount).append(",\n");
        sb.append("    \"example3Vegetables\": ").append(veggiesCount).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private String generateQualityComplianceHtml(List<PersistedSimulation> simulations, 
                                                  LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Quality Compliance Report</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        sb.append("    .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("    h1 { color: #2563eb; border-bottom: 3px solid #2563eb; padding-bottom: 10px; }\n");
        sb.append("    h2 { color: #1e40af; margin-top: 30px; }\n");
        sb.append("    .header-info { background-color: #eff6ff; padding: 15px; border-radius: 5px; margin-bottom: 20px; }\n");
        sb.append("    table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        sb.append("    th { background-color: #2563eb; color: white; padding: 12px; text-align: left; }\n");
        sb.append("    td { padding: 10px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("    tr:hover { background-color: #f9fafb; }\n");
        sb.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n");
        sb.append("    .badge-compliant { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-non-compliant { background-color: #fee2e2; color: #991b1b; }\n");
        sb.append("    .badge-apples { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-carrots { background-color: #fef3c7; color: #92400e; }\n");
        sb.append("    .badge-veggies { background-color: #e0e7ff; color: #3730a3; }\n");
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>📊 Quality Compliance Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Total Simulations:</strong> ").append(simulations.size()).append("\n");
        sb.append("    </div>\n");
        
        // Overall compliance stats
        long compliant = simulations.stream()
            .filter(s -> "COMPLIANT".equals(s.getComplianceStatus()))
            .count();
        double complianceRate = simulations.isEmpty() ? 0 : (compliant * 100.0 / simulations.size());
        
        sb.append("    <h2>📈 Overall Compliance</h2>\n");
        sb.append("    <div class=\"stats\">\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(compliant).append("</div><div class=\"stat-label\">Compliant Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(simulations.size() - compliant).append("</div><div class=\"stat-label\">Non-Compliant</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f%%", complianceRate)).append("</div><div class=\"stat-label\">Compliance Rate</div></div>\n");
        sb.append("    </div>\n");
        
        // By simulation type using helper method
        List<PersistedSimulation> applesSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_1_APPLES);
        List<PersistedSimulation> carrotsSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_2_CARROTS);
        List<PersistedSimulation> veggiesSimulations = filterSimulationsByType(simulations, SimulationType.EXAMPLE_3_VEGETABLES);
        
        if (!applesSimulations.isEmpty() || !carrotsSimulations.isEmpty() || !veggiesSimulations.isEmpty()) {
            sb.append("    <h2>🎯 Compliance by Simulation Type</h2>\n");
            sb.append("    <div class=\"stats\">\n");
            
            if (!applesSimulations.isEmpty()) {
                long applesCompliant = applesSimulations.stream()
                    .filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
                sb.append("      <div class=\"stat-card\">\n");
                sb.append("        <div class=\"stat-value\">").append(applesCompliant).append("/").append(applesSimulations.size()).append("</div>\n");
                sb.append("        <div class=\"stat-label\">Example 1 - Apples<br>Farm to Consumer</div>\n");
                sb.append("      </div>\n");
            }
            
            if (!carrotsSimulations.isEmpty()) {
                long carrotsCompliant = carrotsSimulations.stream()
                    .filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
                sb.append("      <div class=\"stat-card\">\n");
                sb.append("        <div class=\"stat-value\">").append(carrotsCompliant).append("/").append(carrotsSimulations.size()).append("</div>\n");
                sb.append("        <div class=\"stat-label\">Example 2 - Carrots<br>Local Producer</div>\n");
                sb.append("      </div>\n");
            }
            
            if (!veggiesSimulations.isEmpty()) {
                long veggiesCompliant = veggiesSimulations.stream()
                    .filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
                sb.append("      <div class=\"stat-card\">\n");
                sb.append("        <div class=\"stat-value\">").append(veggiesCompliant).append("/").append(veggiesSimulations.size()).append("</div>\n");
                sb.append("        <div class=\"stat-label\">Example 3 - Vegetables<br>Cross-Region</div>\n");
                sb.append("      </div>\n");
            }
            
            sb.append("    </div>\n");
        }
        
        // Detailed table
        sb.append("    <h2>📋 Detailed Compliance Data</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Batch ID</th><th>Type</th><th>Compliance</th><th>Final Quality</th><th>Violations</th><th>Avg Temp</th></tr>\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append("      <tr>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getBatchId())).append("</td>\n");
            
            // Determine type with badge using helper method
            SimulationType simType = SimulationType.fromSimulation(simulation);
            String badgeClass = "";
            String typeName = simType.getDisplayName();
            
            switch (simType) {
                case EXAMPLE_1_APPLES:
                    badgeClass = "badge-apples";
                    typeName = "Example 1 - Apples";
                    break;
                case EXAMPLE_2_CARROTS:
                    badgeClass = "badge-carrots";
                    typeName = "Example 2 - Carrots";
                    break;
                case EXAMPLE_3_VEGETABLES:
                    badgeClass = "badge-veggies";
                    typeName = "Example 3 - Vegetables"; // Consistent with enum display name
                    break;
                default:
                    badgeClass = "";
                    typeName = "Other";
            }
            
            sb.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(typeName).append("</span></td>\n");
            
            String complianceBadge = "COMPLIANT".equals(simulation.getComplianceStatus()) ? 
                "badge-compliant\">✓ Compliant" : "badge-non-compliant\">✗ Non-Compliant";
            sb.append("        <td><span class=\"badge ").append(complianceBadge).append("</span></td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", simulation.getFinalQuality())).append("</td>\n");
            sb.append("        <td>").append(simulation.getViolationsCount()).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f°C", simulation.getAvgTemperature())).append("</td>\n");
            sb.append("      </tr>\n");
        }
        
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
    }
    
    private String generateDeliveryPerformanceJson(List<PersistedSimulation> simulations,
                                                    List<PersistedShipment> shipments,
                                                    LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportType\": \"Delivery Performance\",\n");
        sb.append("  \"dateRange\": {\n");
        sb.append("    \"start\": \"").append(startDate.format(DATE_FORMATTER)).append("\",\n");
        sb.append("    \"end\": \"").append(endDate.format(DATE_FORMATTER)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"simulationCount\": ").append(simulations.size()).append(",\n");
        sb.append("  \"shipmentCount\": ").append(shipments.size()).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private String generateDeliveryPerformanceHtml(List<PersistedSimulation> simulations,
                                                    List<PersistedShipment> shipments,
                                                    LocalDate startDate, LocalDate endDate) {
        String txtContent = generateDeliveryPerformanceTxt(simulations, shipments, startDate, endDate);
        return wrapInBasicHtml("Delivery Performance Report", txtContent);
    }
    
    private String generateSimulationLogJson(List<PersistedSimulation> simulations, 
                                              LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportType\": \"Simulation Log\",\n");
        sb.append("  \"dateRange\": {\n");
        sb.append("    \"start\": \"").append(startDate.format(DATE_FORMATTER)).append("\",\n");
        sb.append("    \"end\": \"").append(endDate.format(DATE_FORMATTER)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"simulationCount\": ").append(simulations.size()).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private String generateSimulationLogHtml(List<PersistedSimulation> simulations, 
                                              LocalDate startDate, LocalDate endDate) {
        String txtContent = generateSimulationLogTxt(simulations, startDate, endDate);
        return wrapInBasicHtml("Simulation Log Report", txtContent);
    }
    
    /**
     * Wrap text content in basic HTML template
     */
    private String wrapInBasicHtml(String title, String textContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: 'Courier New', monospace; margin: 20px; background-color: #f5f5f5; }\n");
        sb.append("    .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("    pre { white-space: pre-wrap; word-wrap: break-word; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <pre>").append(escapeHtml(textContent)).append("</pre>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Format timestamp to readable string
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "N/A";
        }
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DATETIME_FORMATTER);
    }
    
    /**
     * Escape a value for CSV format
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Escape a value for JSON format
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Escape a value for HTML format
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Get the reports directory path
     */
    public String getReportsDirectoryPath() {
        return reportsDirectory.toAbsolutePath().toString();
    }
    
    // ==================== SIMULATION TYPE CLASSIFICATION ====================
    
    /**
     * Enum representing the 3 simulation types from ProducerController.
     */
    private enum SimulationType {
        EXAMPLE_1_APPLES("Example 1 - Farm to Consumer (Apples)", "example_1", "APPLES"),
        EXAMPLE_2_CARROTS("Example 2 - Local Producer (Carrots)", "example_2", "CARROTS"),
        EXAMPLE_3_VEGETABLES("Example 3 - Cross-Region (Vegetables)", "example_3", "VEGGIES", "VEGETABLES"),
        OTHER("Other", null);
        
        private final String displayName;
        private final String scenarioId;
        private final String[] batchIdKeywords;
        
        SimulationType(String displayName, String scenarioId, String... batchIdKeywords) {
            this.displayName = displayName;
            this.scenarioId = scenarioId;
            this.batchIdKeywords = batchIdKeywords;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        /**
         * Determine simulation type from a PersistedSimulation.
         */
        public static SimulationType fromSimulation(PersistedSimulation simulation) {
            // Defensive: handle null simulation or null batch ID
            if (simulation == null || simulation.getBatchId() == null) {
                return OTHER;
            }
            
            for (SimulationType type : values()) {
                if (type == OTHER) continue; // Skip OTHER, it's the default fallback
                
                // Check scenario ID
                if (type.scenarioId != null && simulation.getScenarioId() != null && 
                    simulation.getScenarioId().contains(type.scenarioId)) {
                    return type;
                }
                
                // Check batch ID keywords (batch ID is guaranteed non-null here)
                if (type.batchIdKeywords != null) {
                    for (String keyword : type.batchIdKeywords) {
                        if (simulation.getBatchId().contains(keyword)) {
                            return type;
                        }
                    }
                }
            }
            return OTHER;
        }
    }
    
    /**
     * Filter simulations by type.
     * Helper method to avoid duplication.
     */
    private List<PersistedSimulation> filterSimulationsByType(List<PersistedSimulation> simulations, 
                                                                SimulationType type) {
        return simulations.stream()
                .filter(s -> SimulationType.fromSimulation(s) == type)
                .collect(Collectors.toList());
    }
}
