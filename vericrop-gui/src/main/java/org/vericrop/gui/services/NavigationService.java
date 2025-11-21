package org.vericrop.gui.services;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;

import java.io.IOException;
import java.net.URL;

/**
 * Centralized navigation service for managing view transitions.
 */
public class NavigationService {
    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);
    private static NavigationService instance;
    
    private Parent appShell;
    private Parent contentArea;
    
    private NavigationService() {
        logger.info("NavigationService initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized NavigationService getInstance() {
        if (instance == null) {
            instance = new NavigationService();
        }
        return instance;
    }
    
    /**
     * Set the AppShell container.
     */
    public void setAppShell(Parent appShell) {
        this.appShell = appShell;
        logger.debug("AppShell container set");
    }
    
    /**
     * Set the content area where views will be loaded.
     */
    public void setContentArea(Parent contentArea) {
        this.contentArea = contentArea;
        logger.debug("Content area set");
    }
    
    /**
     * Navigate to a view by loading its FXML file.
     * 
     * @param fxmlFile The FXML file name (e.g., "producer.fxml")
     */
    public void navigateTo(String fxmlFile) {
        try {
            logger.info("Navigating to: {}", fxmlFile);
            
            // Get the main app instance
            MainApp mainApp = MainApp.getInstance();
            if (mainApp != null) {
                mainApp.switchToScreen(fxmlFile);
            } else {
                logger.error("MainApp instance not available");
            }
            
        } catch (Exception e) {
            logger.error("Failed to navigate to {}", fxmlFile, e);
            throw new RuntimeException("Navigation failed: " + fxmlFile, e);
        }
    }
    
    /**
     * Load FXML file and return the root node.
     * 
     * @param fxmlFile The FXML file name
     * @return The loaded Parent node
     */
    public Parent loadFXML(String fxmlFile) throws IOException {
        URL fxmlUrl = getClass().getResource("/fxml/" + fxmlFile);
        if (fxmlUrl == null) {
            fxmlUrl = getClass().getResource("fxml/" + fxmlFile);
        }
        if (fxmlUrl == null) {
            fxmlUrl = getClass().getResource(fxmlFile);
        }
        if (fxmlUrl == null) {
            throw new IOException("FXML file not found: " + fxmlFile);
        }
        
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        return loader.load();
    }
    
    /**
     * Navigate back to the previous view (placeholder for future implementation).
     */
    public void navigateBack() {
        logger.warn("navigateBack() not yet implemented");
    }
}
