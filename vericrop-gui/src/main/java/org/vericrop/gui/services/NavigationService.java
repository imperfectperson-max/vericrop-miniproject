package org.vericrop.gui.services;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.User;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * NavigationService centralizes screen switching and session management.
 * Handles FXML loading, CSS application, and maintains current user session.
 */
public class NavigationService {
    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);
    private static NavigationService instance;
    
    private Stage primaryStage;
    private User currentUser;
    private final Map<String, String> screenCache = new HashMap<>();
    
    // Screen definitions
    public static final String SCREEN_LOGIN = "login.fxml";
    public static final String SCREEN_REGISTER = "register.fxml";
    public static final String SCREEN_PRODUCER = "producer.fxml";
    public static final String SCREEN_CONSUMER = "consumer.fxml";
    public static final String SCREEN_LOGISTICS = "logistics.fxml";
    public static final String SCREEN_ANALYTICS = "analytics.fxml";
    public static final String SCREEN_INBOX = "inbox.fxml";
    
    private NavigationService() {
        // Private constructor for singleton
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized NavigationService getInstance() {
        if (instance == null) {
            instance = new NavigationService();
        }
        return instance;
    }
    
    /**
     * Initialize the navigation service with primary stage
     */
    public void initialize(Stage stage) {
        this.primaryStage = stage;
        logger.info("NavigationService initialized with primary stage");
    }
    
    /**
     * Navigate to a screen by filename
     */
    public void navigateTo(String fxmlFile) {
        if (primaryStage == null) {
            logger.error("Primary stage not initialized. Call initialize() first.");
            return;
        }
        
        Platform.runLater(() -> {
            try {
                logger.info("Navigating to screen: {}", fxmlFile);
                
                // Load FXML
                URL fxmlUrl = getClass().getResource("/fxml/" + fxmlFile);
                if (fxmlUrl == null) {
                    throw new RuntimeException("FXML file not found: " + fxmlFile);
                }
                
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                
                // Create scene
                Scene scene = new Scene(root, 1400, 900);
                
                // Load CSS
                loadCSS(scene);
                
                // Update stage
                primaryStage.setScene(scene);
                primaryStage.setMinWidth(1200);
                primaryStage.setMinHeight(800);
                
                if (!primaryStage.isShowing()) {
                    primaryStage.show();
                }
                
                logger.info("Successfully navigated to: {}", fxmlFile);
                
            } catch (Exception e) {
                logger.error("Error navigating to screen {}: {}", fxmlFile, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Navigate to login screen
     */
    public void navigateToLogin() {
        navigateTo(SCREEN_LOGIN);
    }
    
    /**
     * Navigate to register screen
     */
    public void navigateToRegister() {
        navigateTo(SCREEN_REGISTER);
    }
    
    /**
     * Navigate to producer dashboard
     */
    public void navigateToProducer() {
        navigateTo(SCREEN_PRODUCER);
    }
    
    /**
     * Navigate to consumer dashboard
     */
    public void navigateToConsumer() {
        navigateTo(SCREEN_CONSUMER);
    }
    
    /**
     * Navigate to logistics dashboard
     */
    public void navigateToLogistics() {
        navigateTo(SCREEN_LOGISTICS);
    }
    
    /**
     * Navigate to analytics dashboard
     */
    public void navigateToAnalytics() {
        navigateTo(SCREEN_ANALYTICS);
    }
    
    /**
     * Navigate to inbox/messaging screen
     */
    public void navigateToInbox() {
        navigateTo(SCREEN_INBOX);
    }
    
    /**
     * Navigate based on user role after login
     */
    public void navigateToRoleDashboard(String role) {
        if (role == null) {
            navigateToProducer(); // Default
            return;
        }
        
        switch (role.toUpperCase()) {
            case "ADMIN":
                navigateToAnalytics();
                break;
            case "FARMER":
            case "PRODUCER":
                navigateToProducer();
                break;
            case "SUPPLIER":
            case "SHIPPER":
                navigateToLogistics();
                break;
            case "CONSUMER":
            case "RETAILER":
                navigateToConsumer();
                break;
            default:
                navigateToProducer();
                break;
        }
    }
    
    /**
     * Logout: clear session and return to login
     */
    public void logout() {
        logger.info("Logging out user: {}", currentUser != null ? currentUser.getUsername() : "unknown");
        currentUser = null;
        navigateToLogin();
    }
    
    /**
     * Set current logged in user
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        logger.info("Current user set: {}", user != null ? user.getUsername() : "null");
    }
    
    /**
     * Get current logged in user
     */
    public User getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    /**
     * Load CSS stylesheets for a scene
     */
    private void loadCSS(Scene scene) {
        try {
            URL cssUrl = getClass().getResource("/css/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                logger.debug("CSS loaded successfully");
            } else {
                logger.warn("CSS file not found, using fallback");
                scene.getStylesheets().add(createFallbackCSS());
            }
        } catch (Exception e) {
            logger.warn("Error loading CSS: {}", e.getMessage());
            scene.getStylesheets().add(createFallbackCSS());
        }
    }
    
    /**
     * Create fallback inline CSS
     */
    private String createFallbackCSS() {
        return "data:text/css," +
                ".root { -fx-font-family: 'Segoe UI', Arial, sans-serif; }" +
                ".button { -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-border-radius: 4px; }" +
                ".text-field { -fx-border-color: #ccc; -fx-border-radius: 4px; }";
    }
    
    /**
     * Get primary stage
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
}
