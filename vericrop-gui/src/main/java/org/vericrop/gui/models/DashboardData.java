package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for Dashboard Data.
 * Represents the response from the ML service /dashboard/farm endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardData {
    
    @JsonProperty("kpis")
    private Map<String, Object> kpis;
    
    @JsonProperty("counts")
    private Map<String, Integer> counts;
    
    @JsonProperty("quality_distribution")
    private Map<String, Integer> qualityDistribution;
    
    @JsonProperty("recent_batches")
    private List<Map<String, Object>> recentBatches;
    
    @JsonProperty("timestamp")
    private String timestamp;

    // Constructors
    public DashboardData() {
    }

    // Getters and Setters
    public Map<String, Object> getKpis() {
        return kpis;
    }

    public void setKpis(Map<String, Object> kpis) {
        this.kpis = kpis;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts;
    }

    public Map<String, Integer> getQualityDistribution() {
        return qualityDistribution;
    }

    public void setQualityDistribution(Map<String, Integer> qualityDistribution) {
        this.qualityDistribution = qualityDistribution;
    }

    public List<Map<String, Object>> getRecentBatches() {
        return recentBatches;
    }

    public void setRecentBatches(List<Map<String, Object>> recentBatches) {
        this.recentBatches = recentBatches;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DashboardData{" +
                "kpis=" + kpis +
                ", counts=" + counts +
                ", qualityDistribution=" + qualityDistribution +
                ", recentBatchesCount=" + (recentBatches != null ? recentBatches.size() : 0) +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
