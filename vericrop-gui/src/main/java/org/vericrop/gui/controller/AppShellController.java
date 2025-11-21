package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;
import org.vericrop.gui.models.User;
import org.vericrop.gui.services.AlertService;
import org.vericrop.gui.services.NavigationService;
import org.vericrop.gui.services.SessionManager;

import java.io.IOException;
import java.net.URL;

/**
 * Controller for the main application shell (AppShell).
 * Manages the top navigation bar, content area, and logout functionality.
 */
public class AppShellController {
    private static final Logger logger = LoggerFactory.getLogger(AppShellController.class);
    
    @FXML private Button dashboardButton;
    @FXML private Button analyticsNavButton;
    @FXML private Button logisticsNavButton;
    @FXML private Button inboxNavButton;
    @FXML private Button logoutButton;
    @FXML private CheckBox alertsToggle;
    @FXML private Label userAvatarLabel;
    @FXML private Label usernameLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label statusLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private StackPane contentArea;
    
    private SessionManager sessionManager;
    private NavigationService navigationService;
    private AlertService alertService;
    
    @FXML
    public void initialize() {
        logger.info("Initializing AppShellController");
        
        // Get service instances
        sessionManager = SessionManager.getInstance();
        navigationService = NavigationService.getInstance();
        alertService = AlertService.getInstance();
        
        // Set the content area in NavigationService
        navigationService.setContentArea(contentArea);
        
        // Update user info display
        updateUserInfo();
        
        // Set up alerts toggle
        alertsToggle.setSelected(alertService.isAlertsEnabled());
        
        logger.info("AppShellController initialized");
    }
    
    /**
     * Update user info display in the navbar.
     */
    private void updateUserInfo() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            usernameLabel.setText(currentUser.getUsername());
            userRoleLabel.setText(currentUser.getRole());
            
            // Set avatar initial (first letter of username)
            String initial = currentUser.getUsername().substring(0, 1).toUpperCase();
            userAvatarLabel.setText(initial);
        } else {
            usernameLabel.setText("Guest");
            userRoleLabel.setText("No role");
            userAvatarLabel.setText("?");
        }
    }
    
    @FXML
    private void handleDashboard() {
        logger.info("Dashboard button clicked");
        
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();
            
            // Navigate to appropriate dashboard based on role
            switch (role.toLowerCase()) {
                case "farmer":
                case "producer":
                    loadContent("producer.fxml");
                    break;
                case "logistics":
                case "transporter":
                    loadContent("logistics.fxml");
                    break;
                case "consumer":
                case "retailer":
                    loadContent("consumer.fxml");
                    break;
                default:
                    loadContent("producer.fxml"); // Default to producer
            }
        } else {
            loadContent("producer.fxml");
        }
        
        updateStatus("Dashboard loaded");
    }
    
    @FXML
    private void handleAnalytics() {
        logger.info("Analytics button clicked");
        loadContent("analytics.fxml");
        updateStatus("Analytics loaded");
    }
    
    @FXML
    private void handleLogistics() {
        logger.info("Logistics button clicked");
        loadContent("logistics.fxml");
        updateStatus("Logistics loaded");
    }
    
    @FXML
    private void handleInbox() {
        logger.info("Inbox button clicked");
        loadContent("inbox.fxml");
        updateStatus("Messages loaded");
    }
    
    @FXML
    private void handleAlertsToggle() {
        boolean enabled = alertsToggle.isSelected();
        alertService.setAlertsEnabled(enabled);
        logger.info("Alerts {}", enabled ? "enabled" : "disabled");
        updateStatus(enabled ? "Alerts enabled" : "Alerts disabled");
    }
    
    @FXML
    private void handleLogout() {
        logger.info("Logout button clicked");
        
        // Clear session
        sessionManager.clearSession();
        
        // Navigate to login screen
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            Platform.runLater(() -> {
                try {
                    mainApp.switchToScreen("login.fxml");
                } catch (Exception e) {
                    logger.error("Failed to navigate to login screen", e);
                }
            });
        }
        
        logger.info("User logged out");
    }
    
    /**
     * Load content into the content area.
     */
    private void loadContent(String fxmlFile) {
        try {
            logger.debug("Loading content: {}", fxmlFile);
            
            URL fxmlUrl = getClass().getResource("/fxml/" + fxmlFile);
            if (fxmlUrl == null) {
                logger.error("FXML file not found: {}", fxmlFile);
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent content = loader.load();
            
            // Clear existing content and add new content
            contentArea.getChildren().clear();
            contentArea.getChildren().add(content);
            
            logger.debug("Content loaded successfully: {}", fxmlFile);
            
        } catch (IOException e) {
            logger.error("Failed to load content: {}", fxmlFile, e);
            updateStatus("Error loading " + fxmlFile);
        }
    }
    
    /**
     * Update the status bar message.
     */
    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
    
    /**
     * Set the connection status indicator.
     */
    public void setConnectionStatus(boolean connected) {
        if (connectionStatusLabel != null) {
            if (connected) {
                connectionStatusLabel.setText("● Connected");
                connectionStatusLabel.setStyle("-fx-text-fill: #2E8B57; -fx-font-size: 12px; -fx-font-weight: bold;");
            } else {
                connectionStatusLabel.setText("● Disconnected");
                connectionStatusLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 12px; -fx-font-weight: bold;");
            }
        }
    }
}
