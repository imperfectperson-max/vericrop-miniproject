package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Alert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service for managing alerts generated during delivery simulations.
 * Provides in-memory storage and listener registration for real-time notifications.
 */
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    
    // In-memory alert storage: batchId -> List of alerts
    private final Map<String, List<Alert>> alertsByBatch;
    
    // All alerts in chronological order
    private final List<Alert> allAlerts;
    
    // Alert listeners
    private final List<Consumer<Alert>> listeners;
    
    // Maximum alerts to keep in memory
    private static final int MAX_ALERTS = 10000;
    
    public AlertService() {
        this.alertsByBatch = new ConcurrentHashMap<>();
        this.allAlerts = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
        
        logger.info("AlertService initialized");
    }
    
    /**
     * Record a new alert and notify all listeners.
     */
    public void recordAlert(Alert alert) {
        if (alert == null) {
            logger.warn("Attempted to record null alert");
            return;
        }
        
        // Add to batch-specific list
        alertsByBatch.computeIfAbsent(alert.getBatchId(), k -> new CopyOnWriteArrayList<>())
                     .add(alert);
        
        // Add to global list
        allAlerts.add(alert);
        
        // Trim if exceeds max
        if (allAlerts.size() > MAX_ALERTS) {
            allAlerts.remove(0);
        }
        
        logger.debug("Alert recorded: {}", alert);
        
        // Notify listeners
        for (Consumer<Alert> listener : listeners) {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                logger.error("Error notifying alert listener", e);
            }
        }
    }
    
    /**
     * Register a listener to be notified of new alerts.
     */
    public void addListener(Consumer<Alert> listener) {
        if (listener != null) {
            listeners.add(listener);
            logger.debug("Alert listener registered");
        }
    }
    
    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<Alert> listener) {
        listeners.remove(listener);
        logger.debug("Alert listener removed");
    }
    
    /**
     * Get all alerts for a specific batch.
     */
    public List<Alert> getAlertsByBatch(String batchId) {
        return new ArrayList<>(alertsByBatch.getOrDefault(batchId, Collections.emptyList()));
    }
    
    /**
     * Get all alerts.
     */
    public List<Alert> getAllAlerts() {
        return new ArrayList<>(allAlerts);
    }
    
    /**
     * Get recent alerts (last N).
     */
    public List<Alert> getRecentAlerts(int count) {
        int size = allAlerts.size();
        int fromIndex = Math.max(0, size - count);
        return new ArrayList<>(allAlerts.subList(fromIndex, size));
    }
    
    /**
     * Get alerts by severity.
     */
    public List<Alert> getAlertsBySeverity(Alert.Severity severity) {
        return allAlerts.stream()
                        .filter(a -> a.getSeverity() == severity)
                        .collect(Collectors.toList());
    }
    
    /**
     * Get alerts by type.
     */
    public List<Alert> getAlertsByType(Alert.AlertType type) {
        return allAlerts.stream()
                        .filter(a -> a.getType() == type)
                        .collect(Collectors.toList());
    }
    
    /**
     * Clear all alerts.
     */
    public void clearAll() {
        alertsByBatch.clear();
        allAlerts.clear();
        logger.info("All alerts cleared");
    }
    
    /**
     * Clear alerts for a specific batch.
     */
    public void clearBatch(String batchId) {
        alertsByBatch.remove(batchId);
        allAlerts.removeIf(a -> a.getBatchId().equals(batchId));
        logger.info("Alerts cleared for batch: {}", batchId);
    }
    
    /**
     * Get alert count for a batch.
     */
    public int getAlertCount(String batchId) {
        return alertsByBatch.getOrDefault(batchId, Collections.emptyList()).size();
    }
    
    /**
     * Get total alert count.
     */
    public int getTotalAlertCount() {
        return allAlerts.size();
    }
}
