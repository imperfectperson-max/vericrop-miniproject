package org.vericrop.gui.services;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing and displaying real-time alerts in the UI.
 * Alerts can be created for quality issues, delays, temperature violations, etc.
 * 
 * Features deduplication to prevent duplicate alerts for the same batch + event type
 * within a configurable time window (default: 30 seconds).
 */
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    private static AlertService instance;
    
    /** Maximum number of alerts to retain in history */
    private static final int MAX_ALERTS = 100;
    
    /** Time window in milliseconds to consider alerts as duplicates (30 seconds) */
    private static final long DEDUP_WINDOW_MS = 30_000;
    
    private final ObservableList<Alert> alerts;
    private final List<AlertListener> listeners;
    
    /** 
     * Cache for deduplication: key = batchId + ":" + eventType, value = timestamp.
     * Used to prevent duplicate "batch delivered" and similar alerts.
     */
    private final Map<String, Long> recentAlertCache;
    
    /**
     * Alert severity levels
     */
    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    /**
     * Alert data class
     */
    public static class Alert {
        private final String id;
        private final String title;
        private final String message;
        private final Severity severity;
        private final long timestamp;
        private final String source;
        private final String batchId;     // Optional: for batch-specific alerts
        private final String eventType;   // Optional: for deduplication
        private boolean acknowledged;
        
        public Alert(String title, String message, Severity severity, String source) {
            this(title, message, severity, source, null, null);
        }
        
        public Alert(String title, String message, Severity severity, String source, 
                     String batchId, String eventType) {
            this.id = UUID.randomUUID().toString();
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.source = source;
            this.batchId = batchId;
            this.eventType = eventType;
            this.timestamp = Instant.now().toEpochMilli();
            this.acknowledged = false;
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        public String getBatchId() { return batchId; }
        public String getEventType() { return eventType; }
        public boolean isAcknowledged() { return acknowledged; }
        
        public void acknowledge() {
            this.acknowledged = true;
        }
        
        /**
         * Get deduplication key for this alert (batchId:eventType).
         * Returns null if not applicable for deduplication.
         */
        public String getDeduplicationKey() {
            if (batchId != null && eventType != null) {
                return batchId + ":" + eventType;
            }
            return null;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s - %s", severity, source, title, message);
        }
    }
    
    /**
     * Listener interface for alert notifications
     */
    public interface AlertListener {
        void onAlertCreated(Alert alert);
    }
    
    private AlertService() {
        this.alerts = FXCollections.observableArrayList();
        this.listeners = new CopyOnWriteArrayList<>();
        this.recentAlertCache = new ConcurrentHashMap<>();
        logger.info("AlertService initialized with deduplication support");
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized AlertService getInstance() {
        if (instance == null) {
            instance = new AlertService();
        }
        return instance;
    }
    
    /**
     * Create a new alert
     */
    public Alert createAlert(String title, String message, Severity severity, String source) {
        return createAlert(title, message, severity, source, null, null);
    }
    
    /**
     * Create a new alert with batch ID and event type for deduplication.
     * 
     * @param title Alert title
     * @param message Alert message
     * @param severity Alert severity
     * @param source Alert source (e.g., "simulator", "producer")
     * @param batchId Batch ID for deduplication (optional)
     * @param eventType Event type for deduplication (e.g., "BATCH_DELIVERED")
     * @return The created alert, or null if deduplicated (duplicate within time window)
     */
    public Alert createAlert(String title, String message, Severity severity, String source,
                            String batchId, String eventType) {
        // Check for duplicate if batchId and eventType are provided
        if (batchId != null && eventType != null) {
            String dedupKey = batchId + ":" + eventType;
            long now = System.currentTimeMillis();
            
            // Check if we've seen this alert recently
            Long lastSeen = recentAlertCache.get(dedupKey);
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                logger.debug("Duplicate alert suppressed: {} for batch {}", eventType, batchId);
                return null;
            }
            
            // Record this alert in the cache
            recentAlertCache.put(dedupKey, now);
            
            // Cleanup old entries periodically (every ~10 alerts)
            if (recentAlertCache.size() > 100) {
                cleanupRecentAlertCache();
            }
        }
        
        Alert alert = new Alert(title, message, severity, source, batchId, eventType);
        
        // Add to list on JavaFX thread
        Platform.runLater(() -> {
            alerts.add(0, alert); // Add to beginning for newest first
            
            // Keep alerts list bounded
            while (alerts.size() > MAX_ALERTS) {
                alerts.remove(alerts.size() - 1);
            }
            
            logger.info("Alert created: {}", alert);
        });
        
        // Notify listeners
        notifyListeners(alert);
        
        return alert;
    }
    
    /**
     * Create a batch-delivered alert with deduplication.
     * This alert will only be created once per batch within the dedup window.
     * 
     * @param batchId The batch ID
     * @param source Alert source
     * @return The created alert, or null if a duplicate
     */
    public Alert batchDelivered(String batchId, String source) {
        return createAlert(
            "Batch Delivered",
            "Batch " + batchId + " has been delivered successfully",
            Severity.INFO,
            source,
            batchId,
            "BATCH_DELIVERED"
        );
    }
    
    /**
     * Create a batch-dispatched alert with deduplication.
     * 
     * @param batchId The batch ID
     * @param source Alert source
     * @return The created alert, or null if a duplicate
     */
    public Alert batchDispatched(String batchId, String source) {
        return createAlert(
            "Batch Dispatched",
            "Batch " + batchId + " has been dispatched for delivery",
            Severity.INFO,
            source,
            batchId,
            "BATCH_DISPATCHED"
        );
    }
    
    /**
     * Clean up expired entries from the recent alert cache.
     */
    private void cleanupRecentAlertCache() {
        long now = System.currentTimeMillis();
        recentAlertCache.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > DEDUP_WINDOW_MS);
        logger.debug("Cleaned up recent alert cache, remaining entries: {}", recentAlertCache.size());
    }
    
    /**
     * Create an info alert
     */
    public Alert info(String title, String message, String source) {
        return createAlert(title, message, Severity.INFO, source);
    }
    
    /**
     * Create a warning alert
     */
    public Alert warning(String title, String message, String source) {
        return createAlert(title, message, Severity.WARNING, source);
    }
    
    /**
     * Create an error alert
     */
    public Alert error(String title, String message, String source) {
        return createAlert(title, message, Severity.ERROR, source);
    }
    
    /**
     * Create a critical alert
     */
    public Alert critical(String title, String message, String source) {
        return createAlert(title, message, Severity.CRITICAL, source);
    }
    
    /**
     * Get all alerts
     */
    public ObservableList<Alert> getAlerts() {
        return alerts;
    }
    
    /**
     * Get unacknowledged alerts
     */
    public List<Alert> getUnacknowledgedAlerts() {
        List<Alert> unacked = new ArrayList<>();
        for (Alert alert : alerts) {
            if (!alert.isAcknowledged()) {
                unacked.add(alert);
            }
        }
        return unacked;
    }
    
    /**
     * Get alerts by severity
     */
    public List<Alert> getAlertsBySeverity(Severity severity) {
        List<Alert> filtered = new ArrayList<>();
        for (Alert alert : alerts) {
            if (alert.getSeverity() == severity) {
                filtered.add(alert);
            }
        }
        return filtered;
    }
    
    /**
     * Get alerts by batch ID.
     * 
     * @param batchId The batch ID to filter by
     * @return List of alerts for the specified batch
     */
    public List<Alert> getAlertsByBatchId(String batchId) {
        List<Alert> filtered = new ArrayList<>();
        for (Alert alert : alerts) {
            if (batchId != null && batchId.equals(alert.getBatchId())) {
                filtered.add(alert);
            }
        }
        return filtered;
    }
    
    /**
     * Check if an alert of a specific type for a batch was recently created.
     * Used for deduplication checks.
     * 
     * @param batchId The batch ID
     * @param eventType The event type
     * @return true if a similar alert was recently created
     */
    public boolean isDuplicate(String batchId, String eventType) {
        if (batchId == null || eventType == null) {
            return false;
        }
        String dedupKey = batchId + ":" + eventType;
        Long lastSeen = recentAlertCache.get(dedupKey);
        if (lastSeen == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastSeen) < DEDUP_WINDOW_MS;
    }
    
    /**
     * Acknowledge an alert by ID
     */
    public void acknowledgeAlert(String alertId) {
        for (Alert alert : alerts) {
            if (alert.getId().equals(alertId)) {
                alert.acknowledge();
                logger.info("Alert acknowledged: {}", alertId);
                break;
            }
        }
    }
    
    /**
     * Acknowledge all alerts
     */
    public void acknowledgeAll() {
        for (Alert alert : alerts) {
            alert.acknowledge();
        }
        logger.info("All alerts acknowledged");
    }
    
    /**
     * Clear all alerts
     */
    public void clearAll() {
        Platform.runLater(() -> {
            alerts.clear();
            logger.info("All alerts cleared");
        });
    }
    
    /**
     * Add an alert listener
     */
    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove an alert listener
     */
    public void removeListener(AlertListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of a new alert
     */
    private void notifyListeners(Alert alert) {
        for (AlertListener listener : listeners) {
            try {
                listener.onAlertCreated(alert);
            } catch (Exception e) {
                logger.error("Error notifying alert listener", e);
            }
        }
    }
}
