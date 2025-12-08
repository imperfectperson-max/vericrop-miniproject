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
     * Convert HTML content to PDF (simple implementation)
     * Note: This is a placeholder. For production, use a library like iText or Flying Saucer
     */
    private String convertHtmlToPdf(String htmlContent) {
        // For now, return HTML wrapped with PDF note
        // In production, integrate with a PDF library
        return "<!-- PDF conversion requires additional library -->\n" + htmlContent;
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
        
        sb.append("-------------------------------------------------\n");
        sb.append("COMPLIANCE SUMMARY\n");
        sb.append("-------------------------------------------------\n\n");
        sb.append("Compliant Runs:     ").append(compliant).append("\n");
        sb.append("Non-Compliant Runs: ").append(nonCompliant).append("\n");
        sb.append("Compliance Rate:    ").append(String.format("%.1f%%", complianceRate)).append("\n\n");
        
        sb.append("-------------------------------------------------\n");
        sb.append("DETAILED COMPLIANCE DATA\n");
        sb.append("-------------------------------------------------\n\n");
        
        for (PersistedSimulation simulation : simulations) {
            String status = simulation.isCompleted() ? "✓ COMPLETED" : "○ IN PROGRESS";
            String compliance = "COMPLIANT".equals(simulation.getComplianceStatus()) ? "✓" : "✗";
            
            sb.append(String.format("[%s] Batch: %s\n", compliance, simulation.getBatchId()));
            sb.append(String.format("    Status: %s | Quality: %.1f%% | Violations: %d\n",
                    status, simulation.getFinalQuality(), simulation.getViolationsCount()));
            sb.append(String.format("    Temp Range: %.1f°C - %.1f°C (avg: %.1f°C)\n",
                    simulation.getMinTemperature(), simulation.getMaxTemperature(), simulation.getAvgTemperature()));
            sb.append("\n");
        }
        
        return sb.toString();
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
    
    // Stub implementations for JSON/HTML formats for remaining report types
    // These follow the same pattern as Shipment Summary but are simplified for brevity
    
    private String generateTemperatureLogJson(List<PersistedShipment> shipments, 
                                               List<PersistedSimulation> simulations,
                                               LocalDate startDate, LocalDate endDate) {
        // Simple JSON implementation - can be expanded
        return "{ \"reportType\": \"Temperature Log\", \"dateRange\": { \"start\": \"" + 
               startDate.format(DATE_FORMATTER) + "\", \"end\": \"" + endDate.format(DATE_FORMATTER) + 
               "\" }, \"shipments\": " + shipments.size() + ", \"simulations\": " + simulations.size() + " }";
    }
    
    private String generateTemperatureLogHtml(List<PersistedShipment> shipments, 
                                               List<PersistedSimulation> simulations,
                                               LocalDate startDate, LocalDate endDate) {
        // Reuse TXT format wrapped in basic HTML
        String txtContent = generateTemperatureLogTxt(shipments, simulations, startDate, endDate);
        return wrapInBasicHtml("Temperature Log Report", txtContent);
    }
    
    private String generateQualityComplianceJson(List<PersistedSimulation> simulations, 
                                                  LocalDate startDate, LocalDate endDate) {
        return "{ \"reportType\": \"Quality Compliance\", \"dateRange\": { \"start\": \"" + 
               startDate.format(DATE_FORMATTER) + "\", \"end\": \"" + endDate.format(DATE_FORMATTER) + 
               "\" }, \"simulations\": " + simulations.size() + " }";
    }
    
    private String generateQualityComplianceHtml(List<PersistedSimulation> simulations, 
                                                  LocalDate startDate, LocalDate endDate) {
        String txtContent = generateQualityComplianceTxt(simulations, startDate, endDate);
        return wrapInBasicHtml("Quality Compliance Report", txtContent);
    }
    
    private String generateDeliveryPerformanceJson(List<PersistedSimulation> simulations,
                                                    List<PersistedShipment> shipments,
                                                    LocalDate startDate, LocalDate endDate) {
        return "{ \"reportType\": \"Delivery Performance\", \"dateRange\": { \"start\": \"" + 
               startDate.format(DATE_FORMATTER) + "\", \"end\": \"" + endDate.format(DATE_FORMATTER) + 
               "\" }, \"simulations\": " + simulations.size() + ", \"shipments\": " + shipments.size() + " }";
    }
    
    private String generateDeliveryPerformanceHtml(List<PersistedSimulation> simulations,
                                                    List<PersistedShipment> shipments,
                                                    LocalDate startDate, LocalDate endDate) {
        String txtContent = generateDeliveryPerformanceTxt(simulations, shipments, startDate, endDate);
        return wrapInBasicHtml("Delivery Performance Report", txtContent);
    }
    
    private String generateSimulationLogJson(List<PersistedSimulation> simulations, 
                                              LocalDate startDate, LocalDate endDate) {
        return "{ \"reportType\": \"Simulation Log\", \"dateRange\": { \"start\": \"" + 
               startDate.format(DATE_FORMATTER) + "\", \"end\": \"" + endDate.format(DATE_FORMATTER) + 
               "\" }, \"simulations\": " + simulations.size() + " }";
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
}
