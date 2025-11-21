package org.vericrop.gui.services;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.events.AlertRaised;
import org.vericrop.gui.events.EventBus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for displaying alerts and notifications to the user.
 * Listens for AlertRaised events and displays transient popups.
 */
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    private static AlertService instance;
    
    private final EventBus eventBus;
    private final List<AlertRecord> alertHistory;
    private boolean alertsEnabled = true;
    private Stage ownerStage;
    
    /**
     * Record of an alert for history tracking.
     */
    public static class AlertRecord {
        private final String title;
        private final String message;
        private final AlertRaised.Severity severity;
        private final String source;
        private final Instant timestamp;
        private boolean read;
        
        public AlertRecord(String title, String message, AlertRaised.Severity severity, 
                          String source, Instant timestamp) {
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.source = source;
            this.timestamp = timestamp;
            this.read = false;
        }
        
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public AlertRaised.Severity getSeverity() { return severity; }
        public String getSource() { return source; }
        public Instant getTimestamp() { return timestamp; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
    }
    
    private AlertService() {
        this.eventBus = EventBus.getInstance();
        this.alertHistory = Collections.synchronizedList(new ArrayList<>());
        
        // Subscribe to AlertRaised events
        eventBus.subscribe(AlertRaised.class, this::handleAlert, true);
        
        logger.info("AlertService initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized AlertService getInstance() {
        if (instance == null) {
            instance = new AlertService();
        }
        return instance;
    }
    
    /**
     * Set the owner stage for popups.
     */
    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }
    
    /**
     * Enable or disable alert notifications.
     */
    public void setAlertsEnabled(boolean enabled) {
        this.alertsEnabled = enabled;
        logger.info("Alerts {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Check if alerts are enabled.
     */
    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }
    
    /**
     * Get the alert history.
     */
    public List<AlertRecord> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }
    
    /**
     * Clear the alert history.
     */
    public void clearHistory() {
        alertHistory.clear();
        logger.debug("Alert history cleared");
    }
    
    /**
     * Handle an AlertRaised event.
     */
    private void handleAlert(AlertRaised alert) {
        // Add to history
        AlertRecord record = new AlertRecord(
            alert.getTitle(),
            alert.getMessage(),
            alert.getSeverity(),
            alert.getSource(),
            alert.getTimestamp()
        );
        alertHistory.add(record);
        
        logger.info("Alert raised: {} - {} ({})", alert.getSeverity(), alert.getTitle(), alert.getSource());
        
        // Show popup if enabled
        if (alertsEnabled) {
            showAlertPopup(alert);
        }
    }
    
    /**
     * Show a transient alert popup in the corner of the screen.
     */
    private void showAlertPopup(AlertRaised alert) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showAlertPopup(alert));
            return;
        }
        
        try {
            // Create popup content
            VBox content = new VBox(5);
            content.setPadding(new Insets(15));
            content.setAlignment(Pos.TOP_LEFT);
            content.setMaxWidth(300);
            content.setStyle(getStyleForSeverity(alert.getSeverity()));
            
            // Title
            Label titleLabel = new Label(alert.getTitle());
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            
            // Message
            Label messageLabel = new Label(alert.getMessage());
            messageLabel.setWrapText(true);
            messageLabel.setStyle("-fx-font-size: 12px;");
            
            // Source
            Label sourceLabel = new Label("Source: " + alert.getSource());
            sourceLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            
            content.getChildren().addAll(titleLabel, messageLabel, sourceLabel);
            
            // Create popup
            Popup popup = new Popup();
            popup.getContent().add(content);
            popup.setAutoHide(true);
            
            // Position in bottom-right corner
            if (ownerStage != null && ownerStage.isShowing()) {
                double x = ownerStage.getX() + ownerStage.getWidth() - 320;
                double y = ownerStage.getY() + ownerStage.getHeight() - 150;
                popup.show(ownerStage, x, y);
                
                // Auto-hide after a delay
                PauseTransition delay = new PauseTransition(Duration.seconds(5));
                delay.setOnFinished(e -> popup.hide());
                delay.play();
            }
            
        } catch (Exception e) {
            logger.error("Failed to show alert popup", e);
        }
    }
    
    /**
     * Get CSS style for alert severity.
     */
    private String getStyleForSeverity(AlertRaised.Severity severity) {
        switch (severity) {
            case CRITICAL:
                return "-fx-background-color: #d32f2f; -fx-text-fill: white; " +
                       "-fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);";
            case ERROR:
                return "-fx-background-color: #f44336; -fx-text-fill: white; " +
                       "-fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);";
            case WARNING:
                return "-fx-background-color: #ff9800; -fx-text-fill: white; " +
                       "-fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);";
            case INFO:
            default:
                return "-fx-background-color: #2196f3; -fx-text-fill: white; " +
                       "-fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);";
        }
    }
    
    /**
     * Manually raise an alert.
     */
    public void raiseAlert(String title, String message, AlertRaised.Severity severity, String source) {
        AlertRaised alert = new AlertRaised(title, message, severity, source);
        eventBus.publish(alert);
    }
}
