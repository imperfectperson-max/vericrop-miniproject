package org.vericrop.gui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.services.AuthService;

import java.util.Optional;

/**
 * Base controller with shared functionality for all screen controllers.
 * Provides authentication checking and logout functionality.
 */
public abstract class BaseController {
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    protected AuthService authService;
    protected ApplicationContext appContext;
    
    /**
     * Initialize the base controller with application context.
     * Call this from subclass initialize() methods.
     */
    protected void initializeBaseController() {
        this.appContext = ApplicationContext.getInstance();
        this.authService = appContext.getAuthService();
        
        // Verify authentication
        if (!authService.isAuthenticated()) {
            logger.warn("User not authenticated, redirecting to login");
            Platform.runLater(this::showLoginScreen);
        } else {
            logger.info("Controller initialized for user: {} (role: {})", 
                       authService.getCurrentUser(), authService.getCurrentRole());
        }
    }
    
    /**
     * Handle logout action
     */
    protected void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Confirm Logout");
        alert.setContentText("Are you sure you want to logout?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logger.info("User {} logging out", authService.getCurrentUser());
            authService.logout();
            showLoginScreen();
        }
    }
    
    /**
     * Navigate to login screen
     */
    protected void showLoginScreen() {
        Platform.runLater(() -> {
            MainApp mainApp = MainApp.getInstance();
            if (mainApp != null) {
                mainApp.showLoginScreen();
            }
        });
    }
    
    /**
     * Get current username for display
     */
    protected String getCurrentUsername() {
        if (authService != null && authService.isAuthenticated()) {
            String fullName = authService.getCurrentFullName();
            return fullName != null ? fullName : authService.getCurrentUser();
        }
        return "Guest";
    }
    
    /**
     * Get current user role for display
     */
    protected String getCurrentRole() {
        if (authService != null && authService.isAuthenticated()) {
            return authService.getCurrentRole();
        }
        return "Unknown";
    }
}
