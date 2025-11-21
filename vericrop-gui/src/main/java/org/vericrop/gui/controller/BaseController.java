package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.services.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * BaseController provides common navigation functionality for all controllers.
 * Controllers should extend this class to get navigation bar functionality.
 */
public abstract class BaseController implements Initializable {
    protected static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    // Navigation buttons (may not be present in all controllers)
    @FXML protected Button navProducerButton;
    @FXML protected Button navLogisticsButton;
    @FXML protected Button navConsumerButton;
    @FXML protected Button navAnalyticsButton;
    @FXML protected Button navAlertsButton;
    @FXML protected Button navInboxButton;
    @FXML protected Button logoutButton;
    
    // Badges
    @FXML protected Label alertsBadge;
    @FXML protected Label messagesBadge;
    @FXML protected Label usernameLabel;
    
    // Services
    protected NavigationService navigationService;
    protected AlertsService alertsService;
    protected MessageServiceEnhanced messageService;
    protected DeliverySimulationService deliverySimulationService;
    protected ReportGenerationService reportGenerationService;
    
    // Badge update timer
    private Timer badgeUpdateTimer;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing {}", this.getClass().getSimpleName());
        
        // Get services from ApplicationContext
        ApplicationContext appContext = ApplicationContext.getInstance();
        this.navigationService = appContext.getNavigationService();
        this.alertsService = appContext.getAlertsService();
        this.messageService = appContext.getMessageServiceEnhanced();
        this.deliverySimulationService = appContext.getDeliverySimulationService();
        this.reportGenerationService = appContext.getReportGenerationService();
        
        // Update username label if present
        if (usernameLabel != null && navigationService.getCurrentUser() != null) {
            usernameLabel.setText(navigationService.getCurrentUser().getUsername());
        }
        
        // Start badge update timer
        startBadgeUpdateTimer();
        
        // Call subclass initialization
        initializeController();
    }
    
    /**
     * Subclasses override this for their specific initialization
     */
    protected abstract void initializeController();
    
    /**
     * Start timer to update badges periodically
     */
    private void startBadgeUpdateTimer() {
        badgeUpdateTimer = new Timer(true);
        badgeUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> updateBadges());
            }
        }, 0, 5000); // Update every 5 seconds
    }
    
    /**
     * Update alert and message count badges
     */
    protected void updateBadges() {
        // Update alerts badge
        if (alertsBadge != null && alertsService != null) {
            int unreadAlerts = alertsService.getUnreadCount();
            if (unreadAlerts > 0) {
                alertsBadge.setText(String.valueOf(unreadAlerts));
                alertsBadge.setVisible(true);
            } else {
                alertsBadge.setVisible(false);
            }
        }
        
        // Update messages badge
        if (messagesBadge != null && messageService != null && messageService.isUserLoggedIn()) {
            int unreadMessages = messageService.getUnreadCount();
            if (unreadMessages > 0) {
                messagesBadge.setText(String.valueOf(unreadMessages));
                messagesBadge.setVisible(true);
            } else {
                messagesBadge.setVisible(false);
            }
        }
    }
    
    // Navigation handlers
    
    @FXML
    protected void handleNavProducer() {
        logger.info("Navigating to Producer dashboard");
        navigationService.navigateToProducer();
    }
    
    @FXML
    protected void handleNavLogistics() {
        logger.info("Navigating to Logistics dashboard");
        navigationService.navigateToLogistics();
    }
    
    @FXML
    protected void handleNavConsumer() {
        logger.info("Navigating to Consumer dashboard");
        navigationService.navigateToConsumer();
    }
    
    @FXML
    protected void handleNavAnalytics() {
        logger.info("Navigating to Analytics dashboard");
        navigationService.navigateToAnalytics();
    }
    
    @FXML
    protected void handleNavAlerts() {
        logger.info("Showing alerts");
        // TODO: Implement alerts view
        showInfo("Alerts", "Alerts view coming soon!");
    }
    
    @FXML
    protected void handleNavInbox() {
        logger.info("Navigating to Inbox");
        navigationService.navigateToInbox();
    }
    
    @FXML
    protected void handleLogout() {
        logger.info("User logging out");
        
        // Stop badge timer
        if (badgeUpdateTimer != null) {
            badgeUpdateTimer.cancel();
        }
        
        // Logout via navigation service (clears session)
        navigationService.logout();
    }
    
    /**
     * Show info message (can be overridden by subclasses)
     */
    protected void showInfo(String title, String message) {
        logger.info("{}: {}", title, message);
        // Subclasses can override for visual alerts
    }
    
    /**
     * Show error message (can be overridden by subclasses)
     */
    protected void showError(String title, String message) {
        logger.error("{}: {}", title, message);
        // Subclasses can override for visual alerts
    }
    
    /**
     * Cleanup when controller is destroyed
     */
    public void cleanup() {
        if (badgeUpdateTimer != null) {
            badgeUpdateTimer.cancel();
        }
    }
}
