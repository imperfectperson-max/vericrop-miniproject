package org.vericrop.gui.services;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing and displaying real-time alerts in the UI.
 * Alerts can be created for quality issues, delays, temperature violations, etc.
 */
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    private static AlertService instance;
    
    private final ObservableList<Alert> alerts;
    private final List<AlertListener> listeners;
    
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
        private boolean acknowledged;
        
        public Alert(String title, String message, Severity severity, String source) {
            this.id = UUID.randomUUID().toString();
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.source = source;
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
        public boolean isAcknowledged() { return acknowledged; }
        
        public void acknowledge() {
            this.acknowledged = true;
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
        logger.info("AlertService initialized");
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
        Alert alert = new Alert(title, message, severity, source);
        
        // Add to list on JavaFX thread
        Platform.runLater(() -> {
            alerts.add(0, alert); // Add to beginning for newest first
            logger.info("Alert created: {}", alert);
        });
        
        // Notify listeners
        notifyListeners(alert);
        
        return alert;
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
