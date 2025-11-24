package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.SupplierMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking and evaluating supplier compliance with quality and delivery standards.
 * Monitors supplier performance and generates compliance reports.
 */
public class SupplierComplianceService {
    private static final Logger logger = LoggerFactory.getLogger(SupplierComplianceService.class);
    
    // Compliance thresholds
    private static final double MIN_SUCCESS_RATE = 0.85; // 85%
    private static final double MAX_SPOILAGE_RATE = 0.10; // 10%
    private static final double MAX_QUALITY_DECAY = 20.0; // 20% average decay
    
    private final Map<String, SupplierComplianceData> supplierData;
    
    /**
     * Compliance data for a supplier.
     */
    public static class SupplierComplianceData {
        private final String farmerId;
        private int totalDeliveries;
        private int successfulDeliveries;
        private int spoiledShipments;
        private double totalQualityDecay;
        private double totalDeliveryTime;
        private boolean compliant;
        private String complianceStatus;
        
        public SupplierComplianceData(String farmerId) {
            this.farmerId = farmerId;
            this.totalDeliveries = 0;
            this.successfulDeliveries = 0;
            this.spoiledShipments = 0;
            this.totalQualityDecay = 0.0;
            this.totalDeliveryTime = 0.0;
            this.compliant = true;
            this.complianceStatus = "NEW";
        }
        
        public void recordDelivery(boolean success, boolean spoiled, double qualityDecay, double deliveryTime) {
            totalDeliveries++;
            if (success) successfulDeliveries++;
            if (spoiled) spoiledShipments++;
            totalQualityDecay += qualityDecay;
            totalDeliveryTime += deliveryTime;
            updateComplianceStatus();
        }
        
        private void updateComplianceStatus() {
            double successRate = totalDeliveries > 0 ? (double) successfulDeliveries / totalDeliveries : 1.0;
            double spoilageRate = totalDeliveries > 0 ? (double) spoiledShipments / totalDeliveries : 0.0;
            double avgQualityDecay = totalDeliveries > 0 ? totalQualityDecay / totalDeliveries : 0.0;
            
            if (successRate >= MIN_SUCCESS_RATE && spoilageRate <= MAX_SPOILAGE_RATE && avgQualityDecay <= MAX_QUALITY_DECAY) {
                compliant = true;
                complianceStatus = "COMPLIANT";
            } else if (successRate >= 0.70 && spoilageRate <= 0.20) {
                compliant = false;
                complianceStatus = "WARNING";
            } else {
                compliant = false;
                complianceStatus = "NON_COMPLIANT";
            }
        }
        
        public String getFarmerId() { return farmerId; }
        public int getTotalDeliveries() { return totalDeliveries; }
        public int getSuccessfulDeliveries() { return successfulDeliveries; }
        public int getSpoiledShipments() { return spoiledShipments; }
        public double getTotalQualityDecay() { return totalQualityDecay; }
        public double getTotalDeliveryTime() { return totalDeliveryTime; }
        public boolean isCompliant() { return compliant; }
        public String getComplianceStatus() { return complianceStatus; }
        
        public double getSuccessRate() {
            return totalDeliveries > 0 ? (double) successfulDeliveries / totalDeliveries : 0.0;
        }
        
        public double getSpoilageRate() {
            return totalDeliveries > 0 ? (double) spoiledShipments / totalDeliveries : 0.0;
        }
        
        public double getAverageQualityDecay() {
            return totalDeliveries > 0 ? totalQualityDecay / totalDeliveries : 0.0;
        }
        
        public double getAverageDeliveryTime() {
            return totalDeliveries > 0 ? totalDeliveryTime / totalDeliveries : 0.0;
        }
        
        public SupplierMetrics toSupplierMetrics() {
            return new SupplierMetrics(farmerId, totalDeliveries, successfulDeliveries, 
                                      spoiledShipments, getAverageQualityDecay(), getAverageDeliveryTime());
        }
    }
    
    public SupplierComplianceService() {
        this.supplierData = new ConcurrentHashMap<>();
    }
    
    /**
     * Record a delivery for compliance tracking.
     */
    public void recordDelivery(String farmerId, boolean success, boolean spoiled, 
                              double qualityDecay, double deliveryTimeHours) {
        SupplierComplianceData data = supplierData.computeIfAbsent(farmerId, SupplierComplianceData::new);
        data.recordDelivery(success, spoiled, qualityDecay, deliveryTimeHours);
        
        logger.info("Recorded delivery for supplier {} - Status: {}, Success: {}, Spoiled: {}",
                   farmerId, data.getComplianceStatus(), success, spoiled);
    }
    
    /**
     * Get compliance data for a supplier.
     */
    public SupplierComplianceData getComplianceData(String farmerId) {
        return supplierData.get(farmerId);
    }
    
    /**
     * Get metrics for a supplier.
     */
    public SupplierMetrics getSupplierMetrics(String farmerId) {
        SupplierComplianceData data = supplierData.get(farmerId);
        return data != null ? data.toSupplierMetrics() : null;
    }
    
    /**
     * Check if supplier is compliant.
     */
    public boolean isSupplierCompliant(String farmerId) {
        SupplierComplianceData data = supplierData.get(farmerId);
        return data != null && data.isCompliant();
    }
    
    /**
     * Get all supplier compliance data.
     */
    public Map<String, SupplierComplianceData> getAllSupplierData() {
        return new ConcurrentHashMap<>(supplierData);
    }
    
    /**
     * Clear compliance data for a supplier.
     */
    public void clearSupplierData(String farmerId) {
        supplierData.remove(farmerId);
        logger.info("Cleared compliance data for supplier: {}", farmerId);
    }
}
