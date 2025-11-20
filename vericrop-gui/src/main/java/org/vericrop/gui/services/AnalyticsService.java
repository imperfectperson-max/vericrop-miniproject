package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.clients.MLClientService;
import org.vericrop.gui.models.DashboardData;
import org.vericrop.gui.models.BatchRecord;
import org.vericrop.gui.models.QualityScore;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Service for aggregating and computing analytics data.
 * Integrates with MLClientService to fetch quality predictions and dashboard data.
 * 
 * Provides business logic for:
 * - Dashboard KPI calculations
 * - Quality distribution analysis
 * - Batch statistics and trends
 */
public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    
    private final MLClientService mlClient;

    public AnalyticsService(MLClientService mlClient) {
        this.mlClient = mlClient;
        logger.info("✅ AnalyticsService initialized");
    }

    /**
     * Get dashboard data from ML service
     */
    public DashboardData getDashboardData() {
        try {
            DashboardData data = mlClient.getDashboardFarm();
            logger.debug("Retrieved dashboard data: {}", data);
            return data;
        } catch (IOException e) {
            logger.error("Failed to retrieve dashboard data from ML service", e);
            return createEmptyDashboard();
        }
    }

    /**
     * Get all batches from ML service
     */
    public List<BatchRecord> getAllBatches() {
        try {
            List<BatchRecord> batches = mlClient.listBatches();
            logger.debug("Retrieved {} batches from ML service", batches.size());
            return batches;
        } catch (IOException e) {
            logger.error("Failed to retrieve batches from ML service", e);
            return List.of();
        }
    }

    /**
     * Calculate quality statistics from dashboard data
     */
    public Map<String, Double> calculateQualityStats(DashboardData dashboard) {
        Map<String, Double> stats = new HashMap<>();
        
        try {
            Map<String, Object> kpis = dashboard.getKpis();
            
            if (kpis != null) {
                // Extract KPI values
                Object avgQuality = kpis.get("average_quality");
                Object primePercentage = kpis.get("prime_percentage");
                Object rejectionRate = kpis.get("rejection_rate");
                
                stats.put("average_quality", convertToDouble(avgQuality));
                stats.put("prime_percentage", convertToDouble(primePercentage));
                stats.put("rejection_rate", convertToDouble(rejectionRate));
            }
            
            // Extract count data
            Map<String, Integer> counts = dashboard.getCounts();
            if (counts != null) {
                stats.put("prime_count", counts.getOrDefault("prime_count", 0).doubleValue());
                stats.put("rejected_count", counts.getOrDefault("rejected_count", 0).doubleValue());
                stats.put("total_count", counts.getOrDefault("total_count", 0).doubleValue());
            }
            
        } catch (Exception e) {
            logger.error("Error calculating quality statistics", e);
        }
        
        return stats;
    }

    /**
     * Calculate quality distribution from dashboard data
     */
    public Map<String, Integer> getQualityDistribution(DashboardData dashboard) {
        if (dashboard != null && dashboard.getQualityDistribution() != null) {
            return dashboard.getQualityDistribution();
        }
        
        // Return empty distribution
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("prime", 0);
        distribution.put("standard", 0);
        distribution.put("sub_standard", 0);
        return distribution;
    }

    /**
     * Calculate prime rate and rejection rate from counts
     * Uses canonical formula: rate = count / total_count
     */
    public Map<String, Double> calculateRates(int primeCount, int rejectedCount) {
        Map<String, Double> rates = new HashMap<>();
        
        int totalCount = primeCount + rejectedCount;
        
        if (totalCount == 0) {
            rates.put("prime_rate", 0.0);
            rates.put("rejection_rate", 0.0);
        } else {
            double primeRate = (primeCount * 100.0) / totalCount;
            double rejectionRate = (rejectedCount * 100.0) / totalCount;
            rates.put("prime_rate", primeRate);
            rates.put("rejection_rate", rejectionRate);
        }
        
        return rates;
    }

    /**
     * Classify batch quality based on quality score
     */
    public String classifyBatchQuality(double qualityScore) {
        if (qualityScore >= QualityScore.PRIME_THRESHOLD) {
            return "PRIME";
        } else if (qualityScore >= QualityScore.STANDARD_THRESHOLD) {
            return "STANDARD";
        } else {
            return "SUB_STANDARD";
        }
    }

    /**
     * Check if ML service is healthy and available
     */
    public boolean isMLServiceHealthy() {
        return mlClient.testConnection();
    }

    /**
     * Create empty dashboard with default values
     */
    private DashboardData createEmptyDashboard() {
        DashboardData dashboard = new DashboardData();
        
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("total_batches_today", 0);
        kpis.put("average_quality", 0.0);
        kpis.put("prime_percentage", 0.0);
        kpis.put("rejection_rate", 0.0);
        dashboard.setKpis(kpis);
        
        Map<String, Integer> counts = new HashMap<>();
        counts.put("prime_count", 0);
        counts.put("rejected_count", 0);
        counts.put("total_count", 0);
        dashboard.setCounts(counts);
        
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("prime", 0);
        distribution.put("standard", 0);
        distribution.put("sub_standard", 0);
        dashboard.setQualityDistribution(distribution);
        
        return dashboard;
    }

    /**
     * Convert Object to Double safely
     */
    private Double convertToDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
