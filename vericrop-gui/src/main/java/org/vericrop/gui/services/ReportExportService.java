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
            "Batch ID,Scenario,Status,Completed,Final Quality (%),Initial Quality (%),Violations,Compliance Status,Avg Temp (°C),Min Temp (°C),Max Temp (°C),Start Time,End Time";
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
     * Note: Currently saves as HTML with .pdf extension. For true PDF generation,
     * integrate a library like Apache PDFBox, iText, or Flying Saucer.
     * This is a minimal implementation to support the PDF format option.
     */
    private String convertHtmlToPdf(String htmlContent) {
        // Save as HTML with .pdf extension - users can open in browser and print to PDF
        // Or integrate a proper PDF library for production use
        logger.warn("PDF export currently generates HTML format. Consider integrating a PDF library for production.");
        return htmlContent;
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
        
        long compliant = simulations.stream()
            .filter(s -> "COMPLIANT".equals(s.getComplianceStatus()))
            .count();
        long nonCompliant = simulations.size() - compliant;
        double complianceRate = simulations.isEmpty() ? 0 : (compliant * 100.0 / simulations.size());
        double avgInitialQuality = simulations.stream().mapToDouble(PersistedSimulation::getInitialQuality).average().orElse(0);
        double avgFinalQuality = simulations.stream().mapToDouble(PersistedSimulation::getFinalQuality).average().orElse(0);
        int totalViolations = simulations.stream().mapToInt(PersistedSimulation::getViolationsCount).sum();
        
        sb.append("-------------------------------------------------\n");
        sb.append("OVERALL COMPLIANCE SUMMARY\n");
        sb.append("-------------------------------------------------\n\n");
        sb.append("Compliant Runs:           ").append(compliant).append("\n");
        sb.append("Non-Compliant Runs:       ").append(nonCompliant).append("\n");
        sb.append("Compliance Rate:          ").append(String.format("%.1f%%", complianceRate)).append("\n");
        sb.append("Average Initial Quality:  ").append(String.format("%.1f%%", avgInitialQuality)).append("\n");
        sb.append("Average Final Quality:    ").append(String.format("%.1f%%", avgFinalQuality)).append("\n");
        sb.append("Average Quality Loss:     ").append(String.format("%.1f%%", avgInitialQuality - avgFinalQuality)).append("\n");
        sb.append("Total Violations:         ").append(totalViolations).append("\n\n");
        
        // Group simulations by type (the 3 simulation examples)
        List<PersistedSimulation> applesSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && s.getBatchId().contains("APPLES"))
            .collect(Collectors.toList());
        List<PersistedSimulation> carrotsSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && s.getBatchId().contains("CARROTS"))
            .collect(Collectors.toList());
        List<PersistedSimulation> veggiesSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && (s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES")))
            .collect(Collectors.toList());
        
        sb.append("-------------------------------------------------\n");
        sb.append("ANALYSIS BY SIMULATION TYPE\n");
        sb.append("(3 Examples from ProducerController)\n");
        sb.append("-------------------------------------------------\n\n");
        
        // Example 1: Farm to Consumer (Apples)
        if (!applesSimulations.isEmpty()) {
            sb.append("*** EXAMPLE 1: Farm to Consumer Direct (Summer Apples) ***\n");
            sb.append("Characteristics: Warehouse stops, optimal cold chain, 30-min\n\n");
            appendScenarioAnalysisTxt(sb, applesSimulations);
            sb.append("\n");
        }
        
        // Example 2: Local Producer (Carrots)
        if (!carrotsSimulations.isEmpty()) {
            sb.append("*** EXAMPLE 2: Local Producer Delivery (Organic Carrots) ***\n");
            sb.append("Characteristics: Short direct route, no warehouse, 15-min\n\n");
            appendScenarioAnalysisTxt(sb, carrotsSimulations);
            sb.append("\n");
        }
        
        // Example 3: Cross-Region (Vegetables)
        if (!veggiesSimulations.isEmpty()) {
            sb.append("*** EXAMPLE 3: Cross-Region Long Haul (Mixed Vegetables) ***\n");
            sb.append("Characteristics: Extended delivery, temp events, 45-min\n\n");
            appendScenarioAnalysisTxt(sb, veggiesSimulations);
            sb.append("\n");
        }
        
        sb.append("-------------------------------------------------\n");
        sb.append("DETAILED COMPLIANCE DATA\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            String status = simulation.isCompleted() ? "✓ COMPLETED" : "○ IN PROGRESS";
            String compliance = "COMPLIANT".equals(simulation.getComplianceStatus()) ? "✓" : "✗";
            double degradation = simulation.getInitialQuality() - simulation.getFinalQuality();
            
            // Determine type
            String type = "Other";
            if (simulation.getBatchId() != null) {
                if (simulation.getBatchId().contains("APPLES")) {
                    type = "[Ex1-Apples]";
                } else if (simulation.getBatchId().contains("CARROTS")) {
                    type = "[Ex2-Carrots]";
                } else if (simulation.getBatchId().contains("VEGGIES") || simulation.getBatchId().contains("VEGETABLES")) {
                    type = "[Ex3-Veggies]";
                }
            }
            
            sb.append(String.format("[%s] %s %s\n", compliance, type, simulation.getBatchId()));
            sb.append(String.format("    Status: %s | Quality: %.1f%% → %.1f%% (loss: %.1f%%) | Violations: %d\n",
                    status, simulation.getInitialQuality(), simulation.getFinalQuality(), degradation, simulation.getViolationsCount()));
            sb.append(String.format("    Temp Range: %.1f°C - %.1f°C (avg: %.1f°C)\n",
                    simulation.getMinTemperature(), simulation.getMaxTemperature(), simulation.getAvgTemperature()));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Append scenario-specific analysis to text report
     */
    private void appendScenarioAnalysisTxt(StringBuilder sb, List<PersistedSimulation> scenarios) {
        long compliant = scenarios.stream().filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
        double complianceRate = (compliant * 100.0) / scenarios.size();
        double avgInitialQuality = scenarios.stream().mapToDouble(PersistedSimulation::getInitialQuality).average().orElse(0);
        double avgFinalQuality = scenarios.stream().mapToDouble(PersistedSimulation::getFinalQuality).average().orElse(0);
        double avgDegradation = avgInitialQuality - avgFinalQuality;
        int totalViolations = scenarios.stream().mapToInt(PersistedSimulation::getViolationsCount).sum();
        double avgViolations = (double) totalViolations / scenarios.size();
        double avgTempMin = scenarios.stream().mapToDouble(PersistedSimulation::getMinTemperature).average().orElse(0);
        double avgTempMax = scenarios.stream().mapToDouble(PersistedSimulation::getMaxTemperature).average().orElse(0);
        double avgTemp = scenarios.stream().mapToDouble(PersistedSimulation::getAvgTemperature).average().orElse(0);
        
        sb.append("Simulations Run:         ").append(scenarios.size()).append("\n");
        sb.append("Compliance Rate:         ").append(String.format("%.1f%% (%d/%d compliant)", complianceRate, compliant, scenarios.size())).append("\n");
        sb.append("Avg Initial Quality:     ").append(String.format("%.1f%%", avgInitialQuality)).append("\n");
        sb.append("Avg Final Quality:       ").append(String.format("%.1f%%", avgFinalQuality)).append("\n");
        sb.append("Avg Quality Degradation: ").append(String.format("%.1f%%", avgDegradation)).append("\n");
        sb.append("Total Violations:        ").append(totalViolations).append(" (avg: ").append(String.format("%.1f", avgViolations)).append(" per sim)\n");
        sb.append("Temp Range:              ").append(String.format("%.1f°C - %.1f°C (avg: %.1f°C)", avgTempMin, avgTempMax, avgTemp)).append("\n");
    }
    
    private String generateQualityComplianceCsv(List<PersistedSimulation> simulations, 
                                                 LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER_QUALITY_COMPLIANCE).append("\n");
        
        for (PersistedSimulation simulation : simulations) {
            sb.append(escapeCsv(simulation.getBatchId())).append(",");
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
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Temperature Log Report</title>\n");
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
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("    .temp-ok { color: #16a34a; font-weight: bold; }\n");
        sb.append("    .temp-warning { color: #eab308; font-weight: bold; }\n");
        sb.append("    .temp-danger { color: #dc2626; font-weight: bold; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>🌡️ Temperature Log Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n");
        sb.append("    </div>\n");
        
        // Calculate statistics
        if (!shipments.isEmpty() || !simulations.isEmpty()) {
            double avgTemp = 0;
            double minTemp = Double.MAX_VALUE;
            double maxTemp = Double.MIN_VALUE;
            int violationCount = 0;
            
            for (PersistedShipment s : shipments) {
                avgTemp += s.getTemperature();
                minTemp = Math.min(minTemp, s.getTemperature());
                maxTemp = Math.max(maxTemp, s.getTemperature());
            }
            
            for (PersistedSimulation s : simulations) {
                avgTemp += s.getAvgTemperature();
                minTemp = Math.min(minTemp, s.getMinTemperature());
                maxTemp = Math.max(maxTemp, s.getMaxTemperature());
                violationCount += s.getViolationsCount();
            }
            
            int totalItems = shipments.size() + simulations.size();
            if (totalItems > 0) {
                avgTemp /= totalItems;
            }
            
            sb.append("    <h2>📊 Statistics</h2>\n");
            sb.append("    <div class=\"stats\">\n");
            sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f°C", avgTemp)).append("</div><div class=\"stat-label\">Average Temperature</div></div>\n");
            sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f°C", minTemp)).append("</div><div class=\"stat-label\">Minimum Temperature</div></div>\n");
            sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f°C", maxTemp)).append("</div><div class=\"stat-label\">Maximum Temperature</div></div>\n");
            sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(violationCount).append("</div><div class=\"stat-label\">Total Violations</div></div>\n");
            sb.append("    </div>\n");
        }
        
        // Shipment temperature data
        if (!shipments.isEmpty()) {
            sb.append("    <h2>📦 Shipment Temperature Data</h2>\n");
            sb.append("    <table>\n");
            sb.append("      <tr><th>Batch ID</th><th>Timestamp</th><th>Temperature</th><th>Humidity</th><th>Status</th><th>Location</th></tr>\n");
            
            for (PersistedShipment shipment : shipments) {
                String tempClass = getTempClass(shipment.getTemperature());
                sb.append("      <tr>\n");
                sb.append("        <td>").append(escapeHtml(shipment.getBatchId())).append("</td>\n");
                sb.append("        <td>").append(formatTimestamp(shipment.getUpdatedAt())).append("</td>\n");
                sb.append("        <td class=\"").append(tempClass).append("\">").append(String.format("%.1f°C", shipment.getTemperature())).append("</td>\n");
                sb.append("        <td>").append(String.format("%.1f%%", shipment.getHumidity())).append("</td>\n");
                sb.append("        <td>").append(escapeHtml(shipment.getStatus())).append("</td>\n");
                sb.append("        <td>").append(escapeHtml(shipment.getLocation())).append("</td>\n");
                sb.append("      </tr>\n");
            }
            
            sb.append("    </table>\n");
        }
        
        // Simulation temperature summary
        if (!simulations.isEmpty()) {
            sb.append("    <h2>🔬 Simulation Temperature Summary</h2>\n");
            sb.append("    <table>\n");
            sb.append("      <tr><th>Batch ID</th><th>Scenario</th><th>Avg Temp</th><th>Min Temp</th><th>Max Temp</th><th>Violations</th><th>Compliance</th></tr>\n");
            
            for (PersistedSimulation simulation : simulations) {
                String tempClass = getTempClass(simulation.getAvgTemperature());
                sb.append("      <tr>\n");
                sb.append("        <td>").append(escapeHtml(simulation.getBatchId())).append("</td>\n");
                sb.append("        <td>").append(escapeHtml(simulation.getScenarioId())).append("</td>\n");
                sb.append("        <td class=\"").append(tempClass).append("\">").append(String.format("%.1f°C", simulation.getAvgTemperature())).append("</td>\n");
                sb.append("        <td>").append(String.format("%.1f°C", simulation.getMinTemperature())).append("</td>\n");
                sb.append("        <td>").append(String.format("%.1f°C", simulation.getMaxTemperature())).append("</td>\n");
                sb.append("        <td>").append(simulation.getViolationsCount()).append("</td>\n");
                sb.append("        <td>").append(escapeHtml(simulation.getComplianceStatus())).append("</td>\n");
                sb.append("      </tr>\n");
            }
            
            sb.append("    </table>\n");
        }
        
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
    }
    
    /**
     * Get CSS class for temperature value
     */
    private String getTempClass(double temp) {
        if (temp >= 2.0 && temp <= 8.0) {
            return "temp-ok";
        } else if (temp >= 0.0 && temp < 2.0 || temp > 8.0 && temp <= 12.0) {
            return "temp-warning";
        } else {
            return "temp-danger";
        }
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
        sb.append("  \"simulationCount\": ").append(simulations.size()).append("\n");
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
        sb.append("    h3 { color: #3b82f6; margin-top: 20px; }\n");
        sb.append("    .header-info { background-color: #eff6ff; padding: 15px; border-radius: 5px; margin-bottom: 20px; }\n");
        sb.append("    table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        sb.append("    th { background-color: #2563eb; color: white; padding: 12px; text-align: left; }\n");
        sb.append("    td { padding: 10px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("    tr:hover { background-color: #f9fafb; }\n");
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("    .compliant { color: #16a34a; font-weight: bold; }\n");
        sb.append("    .non-compliant { color: #dc2626; font-weight: bold; }\n");
        sb.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n");
        sb.append("    .badge-apples { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-carrots { background-color: #fef3c7; color: #92400e; }\n");
        sb.append("    .badge-veggies { background-color: #e0e7ff; color: #3730a3; }\n");
        sb.append("    .scenario-section { background-color: #f9fafb; padding: 20px; border-radius: 5px; margin-top: 15px; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>✅ Quality Compliance Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Total Simulations:</strong> ").append(simulations.size()).append("\n");
        sb.append("    </div>\n");
        
        // Calculate overall compliance statistics
        long compliant = simulations.stream().filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
        long nonCompliant = simulations.size() - compliant;
        double complianceRate = simulations.isEmpty() ? 0 : (compliant * 100.0 / simulations.size());
        double avgInitialQuality = simulations.stream().mapToDouble(PersistedSimulation::getInitialQuality).average().orElse(0);
        double avgFinalQuality = simulations.stream().mapToDouble(PersistedSimulation::getFinalQuality).average().orElse(0);
        int totalViolations = simulations.stream().mapToInt(PersistedSimulation::getViolationsCount).sum();
        
        sb.append("    <h2>📊 Overall Compliance Summary</h2>\n");
        sb.append("    <div class=\"stats\">\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f%%", complianceRate)).append("</div><div class=\"stat-label\">Compliance Rate</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(compliant).append("</div><div class=\"stat-label\">Compliant Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(nonCompliant).append("</div><div class=\"stat-label\">Non-Compliant Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(totalViolations).append("</div><div class=\"stat-label\">Total Violations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f%%", avgFinalQuality)).append("</div><div class=\"stat-label\">Average Final Quality</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f%%", avgInitialQuality - avgFinalQuality)).append("</div><div class=\"stat-label\">Average Quality Degradation</div></div>\n");
        sb.append("    </div>\n");
        
        // Group simulations by type (the 3 simulation examples)
        List<PersistedSimulation> applesSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && s.getBatchId().contains("APPLES"))
            .collect(Collectors.toList());
        List<PersistedSimulation> carrotsSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && s.getBatchId().contains("CARROTS"))
            .collect(Collectors.toList());
        List<PersistedSimulation> veggiesSimulations = simulations.stream()
            .filter(s -> s.getBatchId() != null && (s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES")))
            .collect(Collectors.toList());
        
        // Add section for the 3 simulation types
        sb.append("    <h2>🍎🥕🥬 Analysis by Simulation Type (3 Examples from ProducerController)</h2>\n");
        sb.append("    <p>The following analysis breaks down compliance by the 3 simulation examples: Farm-to-Consumer (Apples), Local Producer (Carrots), and Cross-Region (Vegetables).</p>\n");
        
        // Example 1: Farm to Consumer (Apples)
        if (!applesSimulations.isEmpty()) {
            sb.append("    <div class=\"scenario-section\">\n");
            sb.append("      <h3><span class=\"badge badge-apples\">Example 1</span> Farm to Consumer Direct (Summer Apples)</h3>\n");
            sb.append("      <p><strong>Characteristics:</strong> Warehouse stops included, optimal cold chain management, 30-minute duration</p>\n");
            generateScenarioAnalysis(sb, applesSimulations);
            sb.append("    </div>\n");
        }
        
        // Example 2: Local Producer (Carrots)
        if (!carrotsSimulations.isEmpty()) {
            sb.append("    <div class=\"scenario-section\">\n");
            sb.append("      <h3><span class=\"badge badge-carrots\">Example 2</span> Local Producer Delivery (Organic Carrots)</h3>\n");
            sb.append("      <p><strong>Characteristics:</strong> Short direct route, no warehouse stops, strict temperature control, 15-minute duration</p>\n");
            generateScenarioAnalysis(sb, carrotsSimulations);
            sb.append("    </div>\n");
        }
        
        // Example 3: Cross-Region (Vegetables)
        if (!veggiesSimulations.isEmpty()) {
            sb.append("    <div class=\"scenario-section\">\n");
            sb.append("      <h3><span class=\"badge badge-veggies\">Example 3</span> Cross-Region Long Haul (Mixed Vegetables)</h3>\n");
            sb.append("      <p><strong>Characteristics:</strong> Extended delivery with temperature events, multiple environmental challenges, 45-minute duration</p>\n");
            generateScenarioAnalysis(sb, veggiesSimulations);
            sb.append("    </div>\n");
        }
        
        // Detailed compliance table
        sb.append("    <h2>📋 Detailed Compliance Data</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Batch ID</th><th>Type</th><th>Scenario</th><th>Status</th><th>Initial Quality</th><th>Final Quality</th><th>Degradation</th><th>Violations</th><th>Compliance</th></tr>\n");
        
        for (PersistedSimulation simulation : simulations) {
            String complianceClass = "COMPLIANT".equals(simulation.getComplianceStatus()) ? "compliant" : "non-compliant";
            double degradation = simulation.getInitialQuality() - simulation.getFinalQuality();
            
            // Determine type
            String badgeClass = "";
            String typeName = "Other";
            if (simulation.getBatchId() != null) {
                if (simulation.getBatchId().contains("APPLES")) {
                    badgeClass = "badge-apples";
                    typeName = "Farm to Consumer";
                } else if (simulation.getBatchId().contains("CARROTS")) {
                    badgeClass = "badge-carrots";
                    typeName = "Local Producer";
                } else if (simulation.getBatchId().contains("VEGGIES") || simulation.getBatchId().contains("VEGETABLES")) {
                    badgeClass = "badge-veggies";
                    typeName = "Cross-Region";
                }
            }
            
            sb.append("      <tr>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getBatchId())).append("</td>\n");
            sb.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(typeName).append("</span></td>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getScenarioId())).append("</td>\n");
            sb.append("        <td>").append(simulation.isCompleted() ? "✓ Completed" : "○ In Progress").append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", simulation.getInitialQuality())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", simulation.getFinalQuality())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", degradation)).append("</td>\n");
            sb.append("        <td>").append(simulation.getViolationsCount()).append("</td>\n");
            sb.append("        <td class=\"").append(complianceClass).append("\">").append(escapeHtml(simulation.getComplianceStatus())).append("</td>\n");
            sb.append("      </tr>\n");
        }
        
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
    }
    
    /**
     * Generate scenario-specific analysis section
     */
    private void generateScenarioAnalysis(StringBuilder sb, List<PersistedSimulation> scenarios) {
        if (scenarios.isEmpty()) {
            sb.append("      <p>No simulations available for this type.</p>\n");
            return;
        }
        
        long compliant = scenarios.stream().filter(s -> "COMPLIANT".equals(s.getComplianceStatus())).count();
        double complianceRate = (compliant * 100.0) / scenarios.size();
        double avgInitialQuality = scenarios.stream().mapToDouble(PersistedSimulation::getInitialQuality).average().orElse(0);
        double avgFinalQuality = scenarios.stream().mapToDouble(PersistedSimulation::getFinalQuality).average().orElse(0);
        double avgDegradation = avgInitialQuality - avgFinalQuality;
        int totalViolations = scenarios.stream().mapToInt(PersistedSimulation::getViolationsCount).sum();
        double avgViolations = (double) totalViolations / scenarios.size();
        double avgTempMin = scenarios.stream().mapToDouble(PersistedSimulation::getMinTemperature).average().orElse(0);
        double avgTempMax = scenarios.stream().mapToDouble(PersistedSimulation::getMaxTemperature).average().orElse(0);
        double avgTemp = scenarios.stream().mapToDouble(PersistedSimulation::getAvgTemperature).average().orElse(0);
        
        sb.append("      <table style=\"width: 100%; margin-top: 10px;\">\n");
        sb.append("        <tr><th>Metric</th><th>Value</th></tr>\n");
        sb.append("        <tr><td>Simulations Run</td><td>").append(scenarios.size()).append("</td></tr>\n");
        sb.append("        <tr><td>Compliance Rate</td><td>").append(String.format("%.1f%% (%d/%d)", complianceRate, compliant, scenarios.size())).append("</td></tr>\n");
        sb.append("        <tr><td>Average Initial Quality</td><td>").append(String.format("%.1f%%", avgInitialQuality)).append("</td></tr>\n");
        sb.append("        <tr><td>Average Final Quality</td><td>").append(String.format("%.1f%%", avgFinalQuality)).append("</td></tr>\n");
        sb.append("        <tr><td>Average Quality Degradation</td><td>").append(String.format("%.1f%%", avgDegradation)).append("</td></tr>\n");
        sb.append("        <tr><td>Total Violations</td><td>").append(totalViolations).append(" (avg: ").append(String.format("%.1f", avgViolations)).append(" per simulation)</td></tr>\n");
        sb.append("        <tr><td>Temperature Range</td><td>").append(String.format("%.1f°C - %.1f°C (avg: %.1f°C)", avgTempMin, avgTempMax, avgTemp)).append("</td></tr>\n");
        sb.append("      </table>\n");
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
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Delivery Performance Report</title>\n");
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
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n");
        sb.append("    .badge-apples { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-carrots { background-color: #fef3c7; color: #92400e; }\n");
        sb.append("    .badge-veggies { background-color: #e0e7ff; color: #3730a3; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>🚚 Delivery Performance Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("\n");
        sb.append("    </div>\n");
        
        // Calculate performance metrics
        long completedSimulations = simulations.stream().filter(PersistedSimulation::isCompleted).count();
        long deliveredShipments = shipments.stream().filter(s -> "DELIVERED".equalsIgnoreCase(s.getStatus())).count();
        double avgQuality = simulations.stream()
            .filter(PersistedSimulation::isCompleted)
            .mapToDouble(PersistedSimulation::getFinalQuality)
            .average().orElse(0);
        
        // Calculate average duration
        double avgDurationMinutes = 0;
        if (!simulations.isEmpty()) {
            long totalDuration = 0;
            int validCount = 0;
            for (PersistedSimulation s : simulations) {
                if (s.isCompleted() && s.getEndTime() > s.getStartTime()) {
                    totalDuration += (s.getEndTime() - s.getStartTime());
                    validCount++;
                }
            }
            if (validCount > 0) {
                avgDurationMinutes = (totalDuration / validCount) / (1000.0 * 60.0);
            }
        }
        
        sb.append("    <h2>📊 Performance Metrics</h2>\n");
        sb.append("    <div class=\"stats\">\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(simulations.size()).append("</div><div class=\"stat-label\">Total Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(completedSimulations).append("</div><div class=\"stat-label\">Completed Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(deliveredShipments).append("</div><div class=\"stat-label\">Delivered Shipments</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f%%", avgQuality)).append("</div><div class=\"stat-label\">Average Final Quality</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(String.format("%.1f min", avgDurationMinutes)).append("</div><div class=\"stat-label\">Average Duration</div></div>\n");
        sb.append("    </div>\n");
        
        // Delivery details table
        sb.append("    <h2>📋 Delivery Details</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Batch ID</th><th>Type</th><th>Status</th><th>Duration</th><th>Final Quality</th><th>Avg Temp</th><th>Waypoints</th></tr>\n");
        
        for (PersistedSimulation simulation : simulations) {
            long durationMs = simulation.getEndTime() - simulation.getStartTime();
            long durationMinutes = durationMs > 0 ? durationMs / (1000 * 60) : 0;
            
            // Determine type
            String badgeClass = "";
            String typeName = "Other";
            if (simulation.getBatchId() != null) {
                if (simulation.getBatchId().contains("APPLES")) {
                    badgeClass = "badge-apples";
                    typeName = "Farm to Consumer";
                } else if (simulation.getBatchId().contains("CARROTS")) {
                    badgeClass = "badge-carrots";
                    typeName = "Local Producer";
                } else if (simulation.getBatchId().contains("VEGGIES") || simulation.getBatchId().contains("VEGETABLES")) {
                    badgeClass = "badge-veggies";
                    typeName = "Cross-Region";
                }
            }
            
            sb.append("      <tr>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getBatchId())).append("</td>\n");
            sb.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(typeName).append("</span></td>\n");
            sb.append("        <td>").append(simulation.isCompleted() ? "✓ Completed" : "○ In Progress").append("</td>\n");
            sb.append("        <td>").append(durationMinutes).append(" min</td>\n");
            sb.append("        <td>").append(String.format("%.1f%%", simulation.getFinalQuality())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f°C", simulation.getAvgTemperature())).append("</td>\n");
            sb.append("        <td>").append(simulation.getWaypointsCount()).append("</td>\n");
            sb.append("      </tr>\n");
        }
        
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
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
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>Simulation Log Report</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        sb.append("    .container { max-width: 1400px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("    h1 { color: #2563eb; border-bottom: 3px solid #2563eb; padding-bottom: 10px; }\n");
        sb.append("    h2 { color: #1e40af; margin-top: 30px; }\n");
        sb.append("    .header-info { background-color: #eff6ff; padding: 15px; border-radius: 5px; margin-bottom: 20px; }\n");
        sb.append("    table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 13px; }\n");
        sb.append("    th { background-color: #2563eb; color: white; padding: 10px; text-align: left; }\n");
        sb.append("    td { padding: 8px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("    tr:hover { background-color: #f9fafb; }\n");
        sb.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 15px; margin-top: 20px; }\n");
        sb.append("    .stat-card { background-color: #f9fafb; padding: 15px; border-radius: 5px; border-left: 4px solid #2563eb; }\n");
        sb.append("    .stat-value { font-size: 24px; font-weight: bold; color: #1e40af; }\n");
        sb.append("    .stat-label { font-size: 14px; color: #6b7280; }\n");
        sb.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; }\n");
        sb.append("    .badge-apples { background-color: #dcfce7; color: #166534; }\n");
        sb.append("    .badge-carrots { background-color: #fef3c7; color: #92400e; }\n");
        sb.append("    .badge-veggies { background-color: #e0e7ff; color: #3730a3; }\n");
        sb.append("    .compliant { color: #16a34a; font-weight: bold; }\n");
        sb.append("    .non-compliant { color: #dc2626; font-weight: bold; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <h1>📝 Simulation Log Report</h1>\n");
        sb.append("    <div class=\"header-info\">\n");
        sb.append("      <strong>Date Range:</strong> ").append(startDate.format(DATE_FORMATTER))
          .append(" to ").append(endDate.format(DATE_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Generated:</strong> ").append(Instant.now().atZone(ZoneId.systemDefault())
          .format(DATETIME_FORMATTER)).append("<br>\n");
        sb.append("      <strong>Total Simulations:</strong> ").append(simulations.size()).append("\n");
        sb.append("    </div>\n");
        
        // Calculate summary statistics
        long completed = simulations.stream().filter(PersistedSimulation::isCompleted).count();
        long applesCount = simulations.stream().filter(s -> s.getBatchId() != null && s.getBatchId().contains("APPLES")).count();
        long carrotsCount = simulations.stream().filter(s -> s.getBatchId() != null && s.getBatchId().contains("CARROTS")).count();
        long veggiesCount = simulations.stream().filter(s -> s.getBatchId() != null && 
            (s.getBatchId().contains("VEGGIES") || s.getBatchId().contains("VEGETABLES"))).count();
        
        sb.append("    <h2>📊 Summary</h2>\n");
        sb.append("    <div class=\"stats\">\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(simulations.size()).append("</div><div class=\"stat-label\">Total Simulations</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(completed).append("</div><div class=\"stat-label\">Completed</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(applesCount).append("</div><div class=\"stat-label\">Apples (Ex1)</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(carrotsCount).append("</div><div class=\"stat-label\">Carrots (Ex2)</div></div>\n");
        sb.append("      <div class=\"stat-card\"><div class=\"stat-value\">").append(veggiesCount).append("</div><div class=\"stat-label\">Vegetables (Ex3)</div></div>\n");
        sb.append("    </div>\n");
        
        // Detailed simulation log table
        sb.append("    <h2>📋 Detailed Simulation Log</h2>\n");
        sb.append("    <table>\n");
        sb.append("      <tr><th>Batch ID</th><th>Type</th><th>Farmer</th><th>Scenario</th><th>Status</th><th>Quality</th><th>Temp</th><th>Violations</th><th>Compliance</th><th>Duration</th></tr>\n");
        
        for (PersistedSimulation simulation : simulations) {
            // Determine type
            String badgeClass = "";
            String typeName = "Other";
            if (simulation.getBatchId() != null) {
                if (simulation.getBatchId().contains("APPLES")) {
                    badgeClass = "badge-apples";
                    typeName = "Example 1";
                } else if (simulation.getBatchId().contains("CARROTS")) {
                    badgeClass = "badge-carrots";
                    typeName = "Example 2";
                } else if (simulation.getBatchId().contains("VEGGIES") || simulation.getBatchId().contains("VEGETABLES")) {
                    badgeClass = "badge-veggies";
                    typeName = "Example 3";
                }
            }
            
            String complianceClass = "COMPLIANT".equals(simulation.getComplianceStatus()) ? "compliant" : "non-compliant";
            long durationMs = simulation.getEndTime() - simulation.getStartTime();
            long durationMinutes = durationMs > 0 ? durationMs / (1000 * 60) : 0;
            
            sb.append("      <tr>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getBatchId())).append("</td>\n");
            sb.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(typeName).append("</span></td>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getFarmerId())).append("</td>\n");
            sb.append("        <td>").append(escapeHtml(simulation.getScenarioId())).append("</td>\n");
            sb.append("        <td>").append(simulation.isCompleted() ? "✓ Completed" : "○ In Progress").append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f%% → %.1f%%", simulation.getInitialQuality(), simulation.getFinalQuality())).append("</td>\n");
            sb.append("        <td>").append(String.format("%.1f°C", simulation.getAvgTemperature())).append("</td>\n");
            sb.append("        <td>").append(simulation.getViolationsCount()).append("</td>\n");
            sb.append("        <td class=\"").append(complianceClass).append("\">").append(escapeHtml(simulation.getComplianceStatus())).append("</td>\n");
            sb.append("        <td>").append(durationMinutes > 0 ? durationMinutes + " min" : "N/A").append("</td>\n");
            sb.append("      </tr>\n");
        }
        
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>\n");
        
        return sb.toString();
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
}
