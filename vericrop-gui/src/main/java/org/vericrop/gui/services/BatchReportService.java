package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.BatchRecord;
import org.vericrop.gui.util.ReportGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating batch reports with aggregated metrics.
 * Computes average quality%, prime%, rejection% and generates exportable reports.
 */
public class BatchReportService {
    private static final Logger logger = LoggerFactory.getLogger(BatchReportService.class);
    
    // Quality thresholds for classification
    private static final double PRIME_QUALITY_THRESHOLD = 0.8;  // 80% quality score
    private static final double REJECT_QUALITY_THRESHOLD = 0.5;  // 50% quality score
    
    /**
     * Generate aggregated metrics for a list of batches
     * 
     * @param batches List of batches to analyze
     * @return Map of aggregated metrics
     */
    public Map<String, Object> computeAggregatedMetrics(List<BatchRecord> batches) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        
        if (batches == null || batches.isEmpty()) {
            logger.warn("No batches provided for aggregated metrics");
            metrics.put("total_batches", 0);
            metrics.put("avg_quality_percent", 0.0);
            metrics.put("prime_percent", 0.0);
            metrics.put("rejection_percent", 0.0);
            return metrics;
        }
        
        // Filter batches with quality scores
        List<BatchRecord> batchesWithQuality = batches.stream()
            .filter(b -> b.getQualityScore() != null && b.getQualityScore() > 0)
            .collect(Collectors.toList());
        
        int totalBatches = batches.size();
        int batchesWithQualityCount = batchesWithQuality.size();
        
        if (batchesWithQualityCount == 0) {
            logger.warn("No batches with quality scores found");
            metrics.put("total_batches", totalBatches);
            metrics.put("avg_quality_percent", 0.0);
            metrics.put("prime_percent", 0.0);
            metrics.put("rejection_percent", 0.0);
            return metrics;
        }
        
        // Compute average quality
        double avgQuality = batchesWithQuality.stream()
            .mapToDouble(BatchRecord::getQualityScore)
            .average()
            .orElse(0.0);
        
        // Compute prime percentage
        long primeCount = batchesWithQuality.stream()
            .filter(b -> b.getQualityScore() >= PRIME_QUALITY_THRESHOLD)
            .count();
        double primePercent = (primeCount * 100.0) / batchesWithQualityCount;
        
        // Compute rejection percentage
        long rejectionCount = batchesWithQuality.stream()
            .filter(b -> b.getQualityScore() < REJECT_QUALITY_THRESHOLD)
            .count();
        double rejectionPercent = (rejectionCount * 100.0) / batchesWithQualityCount;
        
        // Build metrics map
        metrics.put("total_batches", totalBatches);
        metrics.put("batches_with_quality", batchesWithQualityCount);
        metrics.put("avg_quality_percent", avgQuality * 100);  // Convert to percentage
        metrics.put("prime_count", primeCount);
        metrics.put("prime_percent", primePercent);
        metrics.put("rejection_count", rejectionCount);
        metrics.put("rejection_percent", rejectionPercent);
        metrics.put("acceptable_count", batchesWithQualityCount - primeCount - rejectionCount);
        metrics.put("acceptable_percent", 100.0 - primePercent - rejectionPercent);
        
        logger.info("Computed aggregated metrics: {} batches, avg quality: {:.2f}%, prime: {:.2f}%, rejection: {:.2f}%",
            totalBatches, avgQuality * 100, primePercent, rejectionPercent);
        
        return metrics;
    }
    
    /**
     * Generate a comprehensive batch summary report
     * 
     * @param batch The batch to report on
     * @return Map of batch summary data
     */
    public Map<String, Object> generateBatchSummary(BatchRecord batch) {
        Map<String, Object> summary = new LinkedHashMap<>();
        
        summary.put("batch_id", batch.getBatchId());
        summary.put("name", batch.getName());
        summary.put("farmer", batch.getFarmer());
        summary.put("product_type", batch.getProductType());
        summary.put("quantity", batch.getQuantity());
        summary.put("quality_score", batch.getQualityScore() != null ? batch.getQualityScore() : "N/A");
        summary.put("quality_label", batch.getQualityLabel() != null ? batch.getQualityLabel() : "unknown");
        summary.put("quality_percent", batch.getQualityScore() != null ? batch.getQualityScore() * 100 : 0.0);
        summary.put("status", batch.getStatus());
        summary.put("timestamp", batch.getTimestamp());
        summary.put("data_hash", batch.getDataHash());
        
        // Classify batch
        if (batch.getQualityScore() != null) {
            if (batch.getQualityScore() >= PRIME_QUALITY_THRESHOLD) {
                summary.put("classification", "PRIME");
            } else if (batch.getQualityScore() >= REJECT_QUALITY_THRESHOLD) {
                summary.put("classification", "ACCEPTABLE");
            } else {
                summary.put("classification", "REJECTED");
            }
        } else {
            summary.put("classification", "UNASSESSED");
        }
        
        // Add prime and rejection rates if available
        if (batch.getPrimeRate() != null) {
            summary.put("prime_rate", batch.getPrimeRate() * 100);
        }
        if (batch.getRejectionRate() != null) {
            summary.put("rejection_rate", batch.getRejectionRate() * 100);
        }
        
        logger.debug("Generated batch summary for: {}", batch.getBatchId());
        return summary;
    }
    
    /**
     * Generate and export batch summary reports (CSV and PDF)
     * 
     * @param batch The batch to report on
     * @return Paths to generated reports
     * @throws IOException if report generation fails
     */
    public Map<String, Path> exportBatchSummary(BatchRecord batch) throws IOException {
        Map<String, Path> exportedFiles = new HashMap<>();
        
        Map<String, Object> summary = generateBatchSummary(batch);
        
        // Export as CSV
        Path csvPath = ReportGenerator.generateBatchSummaryCSV(batch.getBatchId(), summary);
        exportedFiles.put("csv", csvPath);
        
        // Export as PDF
        Path pdfPath = ReportGenerator.generateBatchSummaryPDF(batch.getBatchId(), summary);
        exportedFiles.put("pdf", pdfPath);
        
        logger.info("Exported batch summary for {}: CSV={}, PDF={}", 
            batch.getBatchId(), csvPath.getFileName(), pdfPath.getFileName());
        
        return exportedFiles;
    }
    
    /**
     * Generate and export aggregated metrics report
     * 
     * @param reportName Name of the report
     * @param batches List of batches to analyze
     * @return Path to generated CSV report
     * @throws IOException if report generation fails
     */
    public Path exportAggregatedMetrics(String reportName, List<BatchRecord> batches) throws IOException {
        Map<String, Object> metrics = computeAggregatedMetrics(batches);
        
        Path csvPath = ReportGenerator.generateAggregatedMetricsCSV(reportName, metrics);
        
        logger.info("Exported aggregated metrics report: {}", csvPath.getFileName());
        
        return csvPath;
    }
    
    /**
     * Generate timeline of batch quality changes
     * 
     * @param batches List of batches with timestamps
     * @return List of timeline events
     */
    public List<Map<String, Object>> generateQualityTimeline(List<BatchRecord> batches) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        
        for (BatchRecord batch : batches) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", batch.getTimestamp());
            event.put("batch_id", batch.getBatchId());
            event.put("farmer", batch.getFarmer());
            event.put("quality_score", batch.getQualityScore());
            event.put("quality_label", batch.getQualityLabel());
            event.put("status", batch.getStatus());
            timeline.add(event);
        }
        
        // Sort by timestamp
        timeline.sort((e1, e2) -> {
            String t1 = (String) e1.get("timestamp");
            String t2 = (String) e2.get("timestamp");
            return t1.compareTo(t2);
        });
        
        logger.debug("Generated quality timeline with {} events", timeline.size());
        return timeline;
    }
    
    /**
     * Export quality timeline as JSON
     * 
     * @param batches List of batches
     * @return Path to generated JSON file
     * @throws IOException if export fails
     */
    public Path exportQualityTimeline(List<BatchRecord> batches) throws IOException {
        List<Map<String, Object>> timeline = generateQualityTimeline(batches);
        
        Path jsonPath = ReportGenerator.generateSupplyChainTimelineJSON("quality_assessment", timeline);
        
        logger.info("Exported quality timeline: {}", jsonPath.getFileName());
        
        return jsonPath;
    }
    
    /**
     * Get quality thresholds
     */
    public static Map<String, Double> getQualityThresholds() {
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("prime", PRIME_QUALITY_THRESHOLD);
        thresholds.put("reject", REJECT_QUALITY_THRESHOLD);
        return thresholds;
    }
}
