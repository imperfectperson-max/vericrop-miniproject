package org.vericrop.gui.services;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AlertsService manages real-time alerts and notifications.
 * Subscribes to simulation events and backend events.
 * Provides filtering by severity and user role.
 */
public class AlertsService {
    private static final Logger logger = LoggerFactory.getLogger(AlertsService.class);
    private static AlertsService instance;
    
    private final List<Alert> alerts = new CopyOnWriteArrayList<>();
    private final List<Consumer<Alert>> alertListeners = new CopyOnWriteArrayList<>();
    private int maxAlerts = 500; // Maximum alerts to retain
    
    private AlertsService() {
        // Subscribe to delivery simulation events
        DeliverySimulationService.getInstance().addEventListener(this::handleDeliveryEvent);
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized AlertsService getInstance() {
        if (instance == null) {
            instance = new AlertsService();
        }
        return instance;
    }
    
    /**
     * Add an alert listener
     */
    public void addAlertListener(Consumer<Alert> listener) {
        alertListeners.add(listener);
        logger.info("Alert listener registered. Total listeners: {}", alertListeners.size());
    }
    
    /**
     * Remove an alert listener
     */
    public void removeAlertListener(Consumer<Alert> listener) {
        alertListeners.remove(listener);
    }
    
    /**
     * Create and emit an alert
     */
    public void createAlert(String title, String message, AlertSeverity severity, String source) {
        createAlert(title, message, severity, source, null);
    }
    
    /**
     * Create and emit an alert with user role filter
     */
    public void createAlert(String title, String message, AlertSeverity severity, 
                          String source, String targetRole) {
        Alert alert = new Alert(
            UUID.randomUUID().toString(),
            title,
            message,
            severity,
            source,
            targetRole,
            LocalDateTime.now(),
            false
        );
        
        alerts.add(0, alert); // Add to beginning
        
        // Trim old alerts
        if (alerts.size() > maxAlerts) {
            alerts.subList(maxAlerts, alerts.size()).clear();
        }
        
        logger.debug("Alert created: {} - {}", severity, title);
        
        // Notify listeners on JavaFX thread
        Platform.runLater(() -> {
            for (Consumer<Alert> listener : alertListeners) {
                try {
                    listener.accept(alert);
                } catch (Exception e) {
                    logger.error("Error in alert listener", e);
                }
            }
        });
    }
    
    /**
     * Handle delivery simulation events and convert to alerts
     */
    private void handleDeliveryEvent(DeliverySimulationService.DeliveryEvent event) {
        AlertSeverity severity;
        String title;
        
        switch (event.getStatus()) {
            case DELAYED:
                severity = AlertSeverity.WARNING;
                title = "Delivery Delayed";
                createAlert(title, event.getMessage(), severity, "DeliverySimulation", "SUPPLIER");
                break;
                
            case DELIVERED:
                severity = AlertSeverity.INFO;
                title = "Delivery Completed";
                createAlert(title, 
                    String.format("Shipment %s delivered to %s", 
                        event.getShipmentId(), event.getDestination()),
                    severity, "DeliverySimulation", null);
                break;
                
            case PICKED_UP:
                severity = AlertSeverity.INFO;
                title = "Package Picked Up";
                createAlert(title,
                    String.format("Shipment %s picked up from %s",
                        event.getShipmentId(), event.getOrigin()),
                    severity, "DeliverySimulation", "SUPPLIER");
                break;
                
            default:
                // Don't create alerts for other statuses
                break;
        }
    }
    
    /**
     * Create quality alert
     */
    public void createQualityAlert(String batchId, double qualityScore, String issue) {
        AlertSeverity severity = qualityScore < 0.6 ? AlertSeverity.ERROR : AlertSeverity.WARNING;
        String title = severity == AlertSeverity.ERROR ? "Critical Quality Issue" : "Quality Warning";
        String message = String.format("Batch %s: %s (Score: %.1f%%)", batchId, issue, qualityScore * 100);
        
        createAlert(title, message, severity, "QualityControl", "FARMER");
    }
    
    /**
     * Create temperature alert
     */
    public void createTemperatureAlert(String shipmentId, double temperature, double threshold) {
        AlertSeverity severity = AlertSeverity.ERROR;
        String title = "Temperature Threshold Exceeded";
        String message = String.format("Shipment %s: Temperature %.1f°C exceeds threshold %.1f°C",
            shipmentId, temperature, threshold);
        
        createAlert(title, message, severity, "TemperatureMonitor", "SUPPLIER");
    }
    
    /**
     * Get all alerts
     */
    public List<Alert> getAllAlerts() {
        return new ArrayList<>(alerts);
    }
    
    /**
     * Get alerts filtered by severity
     */
    public List<Alert> getAlertsBySeverity(AlertSeverity severity) {
        return alerts.stream()
            .filter(alert -> alert.getSeverity() == severity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get alerts filtered by role
     */
    public List<Alert> getAlertsByRole(String role) {
        if (role == null) {
            return getAllAlerts();
        }
        
        return alerts.stream()
            .filter(alert -> alert.getTargetRole() == null || 
                           alert.getTargetRole().equalsIgnoreCase(role))
            .collect(Collectors.toList());
    }
    
    /**
     * Get unread alerts
     */
    public List<Alert> getUnreadAlerts() {
        return alerts.stream()
            .filter(alert -> !alert.isRead())
            .collect(Collectors.toList());
    }
    
    /**
     * Get unread alert count
     */
    public int getUnreadCount() {
        return (int) alerts.stream()
            .filter(alert -> !alert.isRead())
            .count();
    }
    
    /**
     * Get unread alert count for specific role
     */
    public int getUnreadCountForRole(String role) {
        return (int) alerts.stream()
            .filter(alert -> !alert.isRead())
            .filter(alert -> alert.getTargetRole() == null || 
                           alert.getTargetRole().equalsIgnoreCase(role))
            .count();
    }
    
    /**
     * Mark alert as read
     */
    public void markAsRead(String alertId) {
        alerts.stream()
            .filter(alert -> alert.getId().equals(alertId))
            .findFirst()
            .ifPresent(alert -> alert.setRead(true));
    }
    
    /**
     * Mark all alerts as read
     */
    public void markAllAsRead() {
        alerts.forEach(alert -> alert.setRead(true));
    }
    
    /**
     * Clear all alerts
     */
    public void clearAlerts() {
        alerts.clear();
        logger.info("All alerts cleared");
    }
    
    /**
     * Clear old alerts (older than specified days)
     */
    public void clearOldAlerts(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        alerts.removeIf(alert -> alert.getTimestamp().isBefore(cutoff));
        logger.info("Cleared alerts older than {} days", daysOld);
    }
    
    /**
     * Alert severity enum
     */
    public enum AlertSeverity {
        INFO,       // Informational
        WARNING,    // Warning, attention needed
        ERROR,      // Error, immediate action required
        CRITICAL    // Critical, system-wide issue
    }
    
    /**
     * Alert class
     */
    public static class Alert {
        private final String id;
        private final String title;
        private final String message;
        private final AlertSeverity severity;
        private final String source;
        private final String targetRole; // null = all roles
        private final LocalDateTime timestamp;
        private boolean read;
        
        public Alert(String id, String title, String message, AlertSeverity severity,
                    String source, String targetRole, LocalDateTime timestamp, boolean read) {
            this.id = id;
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.source = source;
            this.targetRole = targetRole;
            this.timestamp = timestamp;
            this.read = read;
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public AlertSeverity getSeverity() { return severity; }
        public String getSource() { return source; }
        public String getTargetRole() { return targetRole; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public boolean isRead() { return read; }
        
        // Setter
        public void setRead(boolean read) { this.read = read; }
        
        /**
         * Get severity color for UI
         */
        public String getSeverityColor() {
            switch (severity) {
                case INFO: return "#2196F3";
                case WARNING: return "#FF9800";
                case ERROR: return "#F44336";
                case CRITICAL: return "#B71C1C";
                default: return "#757575";
            }
        }
        
        /**
         * Get severity icon for UI
         */
        public String getSeverityIcon() {
            switch (severity) {
                case INFO: return "ℹ️";
                case WARNING: return "⚠️";
                case ERROR: return "❌";
                case CRITICAL: return "🚨";
                default: return "•";
            }
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s - %s",
                timestamp, severity, title, message);
        }
    }
}
