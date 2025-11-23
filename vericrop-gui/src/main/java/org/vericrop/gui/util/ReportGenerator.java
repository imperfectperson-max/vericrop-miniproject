package org.vericrop.gui.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.DeliverySimulator.RouteWaypoint;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.colors.ColorConstants;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Utility class for generating reports in various formats (PDF, CSV, JSON).
 * Reports include quality reports, journey reports, and analytics summaries.
 * Exports to both generated_reports/ (legacy) and logistics-and-supply-chain/ directories.
 */
public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final String REPORTS_OUTPUT_DIR = "generated_reports";
    private static final String LOGISTICS_REPORTS_DIR = "logistics-and-supply-chain/reports";
    private static final String LOGISTICS_SUMMARIES_DIR = "logistics-and-supply-chain/summaries";
    private static final String LOGISTICS_BATCHES_DIR = "logistics-and-supply-chain/batches";
    private static final String LOGISTICS_EVENTS_DIR = "logistics-and-supply-chain/supply-chain-events";
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    /**
     * Report types
     */
    public enum ReportType {
        QUALITY,
        JOURNEY,
        ANALYTICS,
        SHIPMENT
    }
    
    /**
     * Generate a journey report as CSV
     * Exports to both generated_reports/ and logistics-and-supply-chain/reports/
     * 
     * @param shipmentId The shipment ID
     * @param waypoints List of route waypoints
     * @return Path to the generated CSV file
     * @throws IOException if file creation fails
     */
    public static Path generateJourneyReportCSV(String shipmentId, List<RouteWaypoint> waypoints) throws IOException {
        ensureReportsDirectory();
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("journey_report_%s_%s.csv", shipmentId, timestamp);
        Path outputPath = Paths.get(REPORTS_OUTPUT_DIR, fileName);
        Path logisticsPath = Paths.get(LOGISTICS_REPORTS_DIR, fileName);
        
        // Write to both directories
        for (Path path : new Path[]{outputPath, logisticsPath}) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                // Write CSV header
                writer.write("Timestamp,Location,Latitude,Longitude,Temperature (°C),Humidity (%)\n");
                
                // Write waypoint data
                for (RouteWaypoint waypoint : waypoints) {
                    String line = String.format("%d,%s,%.6f,%.6f,%.2f,%.2f\n",
                        waypoint.getTimestamp(),
                        escapeCSV(waypoint.getLocation().getName()),
                        waypoint.getLocation().getLatitude(),
                        waypoint.getLocation().getLongitude(),
                        waypoint.getTemperature(),
                        waypoint.getHumidity()
                    );
                    writer.write(line);
                }
            }
        }
        
        logger.info("Generated journey CSV report: {} (also exported to logistics)", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a journey report as JSON
     * 
     * @param shipmentId The shipment ID
     * @param waypoints List of route waypoints
     * @return Path to the generated JSON file
     * @throws IOException if file creation fails
     */
    public static Path generateJourneyReportJSON(String shipmentId, List<RouteWaypoint> waypoints) throws IOException {
        ensureReportsDirectory();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("journey_report_%s_%s.json", shipmentId, timestamp);
        Path outputPath = Paths.get(REPORTS_OUTPUT_DIR, fileName);
        
        // Create report structure
        Map<String, Object> report = Map.of(
            "reportType", "journey",
            "shipmentId", shipmentId,
            "generatedAt", timestamp,
            "waypointCount", waypoints.size(),
            "waypoints", waypoints
        );
        
        objectMapper.writeValue(outputPath.toFile(), report);
        
        logger.info("Generated journey JSON report: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a quality report as CSV
     * Exports to both generated_reports/ and logistics-and-supply-chain/reports/
     * 
     * @param batchId The batch ID
     * @param qualityData Map of quality metrics
     * @return Path to the generated CSV file
     * @throws IOException if file creation fails
     */
    public static Path generateQualityReportCSV(String batchId, Map<String, Object> qualityData) throws IOException {
        ensureReportsDirectory();
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("quality_report_%s_%s.csv", batchId, timestamp);
        Path outputPath = Paths.get(REPORTS_OUTPUT_DIR, fileName);
        Path logisticsPath = Paths.get(LOGISTICS_REPORTS_DIR, fileName);
        
        // Write to both directories
        for (Path path : new Path[]{outputPath, logisticsPath}) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                // Write CSV header
                writer.write("Metric,Value\n");
                
                // Write quality metrics
                writer.write(String.format("Batch ID,%s\n", batchId));
                writer.write(String.format("Report Timestamp,%s\n", timestamp));
                
                for (Map.Entry<String, Object> entry : qualityData.entrySet()) {
                    writer.write(String.format("%s,%s\n", 
                        escapeCSV(entry.getKey()), 
                        escapeCSV(String.valueOf(entry.getValue()))
                    ));
                }
            }
        }
        
        logger.info("Generated quality CSV report: {} (also exported to logistics)", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate an analytics report as CSV
     * 
     * @param reportName The report name/title
     * @param data List of data rows (each row is a map)
     * @param columns Ordered list of column names
     * @return Path to the generated CSV file
     * @throws IOException if file creation fails
     */
    public static Path generateAnalyticsReportCSV(String reportName, List<Map<String, Object>> data, List<String> columns) throws IOException {
        ensureReportsDirectory();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("analytics_%s_%s.csv", 
            reportName.toLowerCase().replaceAll("\\s+", "_"), 
            timestamp
        );
        Path outputPath = Paths.get(REPORTS_OUTPUT_DIR, fileName);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
            // Write CSV header
            writer.write(String.join(",", columns) + "\n");
            
            // Write data rows
            for (Map<String, Object> row : data) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) line.append(",");
                    Object value = row.get(columns.get(i));
                    line.append(escapeCSV(value != null ? String.valueOf(value) : ""));
                }
                line.append("\n");
                writer.write(line.toString());
            }
        }
        
        logger.info("Generated analytics CSV report: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a shipment summary report as JSON
     * 
     * @param shipmentId The shipment ID
     * @param summary Map containing shipment summary data
     * @return Path to the generated JSON file
     * @throws IOException if file creation fails
     */
    public static Path generateShipmentReportJSON(String shipmentId, Map<String, Object> summary) throws IOException {
        ensureReportsDirectory();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("shipment_report_%s_%s.json", shipmentId, timestamp);
        Path outputPath = Paths.get(REPORTS_OUTPUT_DIR, fileName);
        
        // Add metadata
        summary.put("reportType", "shipment");
        summary.put("generatedAt", timestamp);
        
        objectMapper.writeValue(outputPath.toFile(), summary);
        
        logger.info("Generated shipment JSON report: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Get the absolute path of the reports output directory
     * 
     * @return Path to the reports output directory
     */
    public static Path getReportsDirectory() {
        return Paths.get(REPORTS_OUTPUT_DIR).toAbsolutePath();
    }
    
    /**
     * Ensure the reports directory exists
     */
    private static void ensureReportsDirectory() throws IOException {
        Path reportsDir = Paths.get(REPORTS_OUTPUT_DIR);
        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
            logger.info("Created reports directory: {}", reportsDir.toAbsolutePath());
        }
    }
    
    /**
     * Ensure logistics directories exist
     */
    private static void ensureLogisticsDirectories() throws IOException {
        for (String dir : new String[]{LOGISTICS_REPORTS_DIR, LOGISTICS_SUMMARIES_DIR, 
                                        LOGISTICS_BATCHES_DIR, LOGISTICS_EVENTS_DIR}) {
            Path dirPath = Paths.get(dir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                logger.info("Created logistics directory: {}", dirPath.toAbsolutePath());
            }
        }
    }
    
    /**
     * Generate a batch summary report with aggregated metrics (CSV and PDF)
     * Exports to logistics-and-supply-chain/batches/
     * 
     * @param batchId The batch ID
     * @param batchData Complete batch data including quality metrics
     * @return Path to the generated CSV file
     * @throws IOException if file creation fails
     */
    public static Path generateBatchSummaryCSV(String batchId, Map<String, Object> batchData) throws IOException {
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("batch_summary_%s_%s.csv", batchId, timestamp);
        Path outputPath = Paths.get(LOGISTICS_BATCHES_DIR, fileName);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
            writer.write("Metric,Value\n");
            writer.write(String.format("Batch ID,%s\n", batchId));
            writer.write(String.format("Report Timestamp,%s\n", timestamp));
            
            for (Map.Entry<String, Object> entry : batchData.entrySet()) {
                writer.write(String.format("%s,%s\n", 
                    escapeCSV(entry.getKey()), 
                    escapeCSV(String.valueOf(entry.getValue()))
                ));
            }
        }
        
        logger.info("Generated batch summary CSV: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a batch summary report as PDF
     * Exports to logistics-and-supply-chain/batches/
     * 
     * @param batchId The batch ID
     * @param batchData Complete batch data including quality metrics
     * @return Path to the generated PDF file
     * @throws IOException if file creation fails
     */
    public static Path generateBatchSummaryPDF(String batchId, Map<String, Object> batchData) throws IOException {
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("batch_summary_%s_%s.pdf", batchId, timestamp);
        Path outputPath = Paths.get(LOGISTICS_BATCHES_DIR, fileName);
        
        try (PdfWriter writer = new PdfWriter(outputPath.toFile());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            // Title
            Paragraph title = new Paragraph("VeriCrop Batch Summary Report")
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
            document.add(title);
            
            // Metadata
            document.add(new Paragraph("Batch ID: " + batchId).setFontSize(12));
            document.add(new Paragraph("Generated: " + timestamp).setFontSize(10));
            document.add(new Paragraph(" "));
            
            // Data table
            Table table = new Table(2);
            table.addHeaderCell(new Cell().add(new Paragraph("Metric")).setBackgroundColor(ColorConstants.LIGHT_GRAY).setBold());
            table.addHeaderCell(new Cell().add(new Paragraph("Value")).setBackgroundColor(ColorConstants.LIGHT_GRAY).setBold());
            
            for (Map.Entry<String, Object> entry : batchData.entrySet()) {
                table.addCell(new Cell().add(new Paragraph(entry.getKey())));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(entry.getValue()))));
            }
            
            document.add(table);
        }
        
        logger.info("Generated batch summary PDF: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate an aggregated metrics report with average quality%, prime%, rejection%
     * Exports to logistics-and-supply-chain/summaries/
     * 
     * @param reportName The name of the report
     * @param metrics Map of aggregated metrics
     * @return Path to the generated CSV file
     * @throws IOException if file creation fails
     */
    public static Path generateAggregatedMetricsCSV(String reportName, Map<String, Object> metrics) throws IOException {
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("aggregated_metrics_%s_%s.csv", 
            reportName.toLowerCase().replaceAll("\\s+", "_"), 
            timestamp
        );
        Path outputPath = Paths.get(LOGISTICS_SUMMARIES_DIR, fileName);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
            writer.write("Metric,Value\n");
            writer.write(String.format("Report Name,%s\n", reportName));
            writer.write(String.format("Generated At,%s\n", timestamp));
            
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                writer.write(String.format("%s,%s\n", 
                    escapeCSV(entry.getKey()), 
                    escapeCSV(String.valueOf(entry.getValue()))
                ));
            }
        }
        
        logger.info("Generated aggregated metrics CSV: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a supply chain timeline log
     * Exports to logistics-and-supply-chain/supply-chain-events/
     * 
     * @param eventType Type of event (e.g., "batch_created", "quality_assessed")
     * @param events List of timeline events
     * @return Path to the generated JSON file
     * @throws IOException if file creation fails
     */
    public static Path generateSupplyChainTimelineJSON(String eventType, List<Map<String, Object>> events) throws IOException {
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("timeline_%s_%s.json", 
            eventType.toLowerCase().replaceAll("\\s+", "_"), 
            timestamp
        );
        Path outputPath = Paths.get(LOGISTICS_EVENTS_DIR, fileName);
        
        Map<String, Object> timeline = Map.of(
            "eventType", eventType,
            "generatedAt", timestamp,
            "eventCount", events.size(),
            "events", events
        );
        
        objectMapper.writeValue(outputPath.toFile(), timeline);
        
        logger.info("Generated supply chain timeline JSON: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Generate a quality report as PDF
     * Exports to logistics-and-supply-chain/reports/
     * 
     * @param batchId The batch ID
     * @param qualityData Map of quality metrics
     * @return Path to the generated PDF file
     * @throws IOException if file creation fails
     */
    public static Path generateQualityReportPDF(String batchId, Map<String, Object> qualityData) throws IOException {
        ensureLogisticsDirectories();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("quality_report_%s_%s.pdf", batchId, timestamp);
        Path outputPath = Paths.get(LOGISTICS_REPORTS_DIR, fileName);
        
        try (PdfWriter writer = new PdfWriter(outputPath.toFile());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {
            
            // Title
            Paragraph title = new Paragraph("VeriCrop Quality Assessment Report")
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
            document.add(title);
            
            // Metadata
            document.add(new Paragraph("Batch ID: " + batchId).setFontSize(12));
            document.add(new Paragraph("Generated: " + timestamp).setFontSize(10));
            document.add(new Paragraph(" "));
            
            // Quality metrics table
            Table table = new Table(2);
            table.addHeaderCell(new Cell().add(new Paragraph("Metric")).setBackgroundColor(ColorConstants.LIGHT_GRAY).setBold());
            table.addHeaderCell(new Cell().add(new Paragraph("Value")).setBackgroundColor(ColorConstants.LIGHT_GRAY).setBold());
            
            for (Map.Entry<String, Object> entry : qualityData.entrySet()) {
                table.addCell(new Cell().add(new Paragraph(entry.getKey())));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(entry.getValue()))));
            }
            
            document.add(table);
        }
        
        logger.info("Generated quality PDF report: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
    
    /**
     * Escape CSV values to handle commas, quotes, and newlines
     */
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        
        // If value contains comma, quote, or newline, wrap in quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // Escape quotes by doubling them
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        
        return value;
    }
}
