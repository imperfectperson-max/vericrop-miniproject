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
        CSV
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
        String extension = format.name().toLowerCase();
        return String.format("%s_%s_to_%s.%s", typeName, start, end, extension);
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
        
        Files.writeString(filePath, content);
        logger.info("Exported report: {} ({} format)", filePath, format);
        
        return filePath.toFile();
    }
    
    /**
     * Generate Shipment Summary Report
     */
    private String generateShipmentSummaryReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedShipment> shipments = persistenceService.getShipmentsByDateRange(startDate, endDate);
        
        if (format == ExportFormat.CSV) {
            return generateShipmentSummaryCsv(shipments, startDate, endDate);
        } else {
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
            
            sb.append("Average Temperature: ").append(String.format("%.1f°C", avgTemp)).append("\n");
            sb.append("Average Humidity:    ").append(String.format("%.1f%%", avgHumidity)).append("\n");
            sb.append("In Transit:          ").append(inTransit).append("\n");
            sb.append("Delivered:           ").append(delivered).append("\n");
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
     * Generate Temperature Log Report
     */
    private String generateTemperatureLogReport(LocalDate startDate, LocalDate endDate, ExportFormat format) {
        List<PersistedShipment> shipments = persistenceService.getShipmentsByDateRange(startDate, endDate);
        List<PersistedSimulation> simulations = persistenceService.getSimulationsByDateRange(startDate, endDate);
        
        if (format == ExportFormat.CSV) {
            return generateTemperatureLogCsv(shipments, simulations, startDate, endDate);
        } else {
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
        
        if (format == ExportFormat.CSV) {
            return generateQualityComplianceCsv(simulations, startDate, endDate);
        } else {
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
        
        if (format == ExportFormat.CSV) {
            return generateDeliveryPerformanceCsv(simulations, shipments, startDate, endDate);
        } else {
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
        
        if (format == ExportFormat.CSV) {
            return generateSimulationLogCsv(simulations, startDate, endDate);
        } else {
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
     * Get the reports directory path
     */
    public String getReportsDirectoryPath() {
        return reportsDirectory.toAbsolutePath().toString();
    }
}
