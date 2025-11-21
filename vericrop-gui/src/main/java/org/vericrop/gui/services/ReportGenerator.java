package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.Message;
import org.vericrop.gui.events.EventBus;
import org.vericrop.gui.events.ReportReady;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating reports in various formats (CSV, HTML).
 */
public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static ReportGenerator instance;
    
    private final EventBus eventBus;
    private final Path reportsDirectory;
    private final DateTimeFormatter dateFormatter;
    
    private ReportGenerator() {
        this.eventBus = EventBus.getInstance();
        this.reportsDirectory = Paths.get("reports");
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                              .withZone(ZoneId.systemDefault());
        
        try {
            Files.createDirectories(reportsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create reports directory", e);
        }
        
        logger.info("ReportGenerator initialized, reports directory: {}", 
                   reportsDirectory.toAbsolutePath());
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized ReportGenerator getInstance() {
        if (instance == null) {
            instance = new ReportGenerator();
        }
        return instance;
    }
    
    /**
     * Generate a CSV report from generic data.
     * 
     * @param reportName The name of the report
     * @param headers Column headers
     * @param rows Data rows (each row is a list of values)
     * @return The path to the generated report
     */
    public Path generateCSVReport(String reportName, List<String> headers, 
                                 List<List<String>> rows) throws IOException {
        String fileName = String.format("%s_%s.csv", 
                                       reportName, 
                                       System.currentTimeMillis());
        Path filePath = reportsDirectory.resolve(fileName);
        
        logger.info("Generating CSV report: {}", fileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Write headers
            writer.write(String.join(",", headers));
            writer.newLine();
            
            // Write rows
            for (List<String> row : rows) {
                writer.write(String.join(",", escapeCsvValues(row)));
                writer.newLine();
            }
        }
        
        logger.info("CSV report generated: {}", filePath.toAbsolutePath());
        
        // Publish event
        String reportId = UUID.randomUUID().toString();
        eventBus.publish(new ReportReady(reportId, "CSV", 
                                        filePath.toString(), fileName));
        
        return filePath;
    }
    
    /**
     * Generate an HTML report from generic data.
     * 
     * @param reportName The name of the report
     * @param title Report title
     * @param headers Column headers
     * @param rows Data rows
     * @return The path to the generated report
     */
    public Path generateHTMLReport(String reportName, String title,
                                  List<String> headers, List<List<String>> rows) throws IOException {
        String fileName = String.format("%s_%s.html", 
                                       reportName, 
                                       System.currentTimeMillis());
        Path filePath = reportsDirectory.resolve(fileName);
        
        logger.info("Generating HTML report: {}", fileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n");
            writer.write("<title>" + escapeHtml(title) + "</title>\n");
            writer.write("<style>\n");
            writer.write("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            writer.write("h1 { color: #2E8B57; }\n");
            writer.write("table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
            writer.write("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }\n");
            writer.write("th { background-color: #2E8B57; color: white; }\n");
            writer.write("tr:nth-child(even) { background-color: #f2f2f2; }\n");
            writer.write(".footer { margin-top: 20px; font-size: 12px; color: #666; }\n");
            writer.write("</style>\n");
            writer.write("</head>\n<body>\n");
            
            // Title
            writer.write("<h1>" + escapeHtml(title) + "</h1>\n");
            writer.write("<p>Generated: " + dateFormatter.format(Instant.now()) + "</p>\n");
            
            // Table
            writer.write("<table>\n<thead>\n<tr>\n");
            for (String header : headers) {
                writer.write("<th>" + escapeHtml(header) + "</th>\n");
            }
            writer.write("</tr>\n</thead>\n<tbody>\n");
            
            for (List<String> row : rows) {
                writer.write("<tr>\n");
                for (String cell : row) {
                    writer.write("<td>" + escapeHtml(cell) + "</td>\n");
                }
                writer.write("</tr>\n");
            }
            
            writer.write("</tbody>\n</table>\n");
            writer.write("<div class=\"footer\">VeriCrop Supply Chain Management System</div>\n");
            writer.write("</body>\n</html>\n");
        }
        
        logger.info("HTML report generated: {}", filePath.toAbsolutePath());
        
        // Publish event
        String reportId = UUID.randomUUID().toString();
        eventBus.publish(new ReportReady(reportId, "HTML", 
                                        filePath.toString(), fileName));
        
        return filePath;
    }
    
    /**
     * Generate a deliveries report.
     */
    public Path generateDeliveriesReport(String format, List<Map<String, String>> deliveries) 
            throws IOException {
        List<String> headers = List.of("Shipment ID", "Status", "Origin", "Destination", 
                                       "Start Time", "Current Location");
        List<List<String>> rows = deliveries.stream()
            .map(d -> List.of(
                d.getOrDefault("shipmentId", "N/A"),
                d.getOrDefault("status", "N/A"),
                d.getOrDefault("origin", "N/A"),
                d.getOrDefault("destination", "N/A"),
                d.getOrDefault("startTime", "N/A"),
                d.getOrDefault("currentLocation", "N/A")
            ))
            .toList();
        
        if ("HTML".equalsIgnoreCase(format)) {
            return generateHTMLReport("deliveries", "Deliveries Report", headers, rows);
        } else {
            return generateCSVReport("deliveries", headers, rows);
        }
    }
    
    /**
     * Generate a messages report.
     */
    public Path generateMessagesReport(String format, List<Message> messages) throws IOException {
        List<String> headers = List.of("Message ID", "From", "To", "Subject", 
                                       "Timestamp", "Status");
        List<List<String>> rows = messages.stream()
            .map(m -> {
                List<String> row = List.of(
                    m.getMessageId(),
                    m.getSenderRole() + ":" + m.getSenderId(),
                    m.getRecipientRole() + (m.getRecipientId() != null ? ":" + m.getRecipientId() : ""),
                    m.getSubject() != null ? m.getSubject() : "N/A",
                    dateFormatter.format(Instant.ofEpochMilli(m.getTimestamp())),
                    m.getStatus() != null ? m.getStatus() : "sent"
                );
                return row;
            })
            .toList();
        
        if ("HTML".equalsIgnoreCase(format)) {
            return generateHTMLReport("messages", "Messages Report", headers, rows);
        } else {
            return generateCSVReport("messages", headers, rows);
        }
    }
    
    /**
     * Escape CSV values (handle commas, quotes, newlines).
     */
    private List<String> escapeCsvValues(List<String> values) {
        return values.stream()
            .map(v -> {
                if (v == null) return "";
                if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                    return "\"" + v.replace("\"", "\"\"") + "\"";
                }
                return v;
            })
            .toList();
    }
    
    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Get the reports directory path.
     */
    public Path getReportsDirectory() {
        return reportsDirectory;
    }
}
