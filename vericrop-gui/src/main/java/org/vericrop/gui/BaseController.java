package org.vericrop.gui;

import javafx.scene.control.Alert;
import java.util.Map;

/**
 * Base controller class providing common UI helper methods for all controllers.
 */
public class BaseController {
    
    /**
     * Display an error alert to the user.
     * 
     * @param message the error message to display
     */
    protected void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Display a success alert to the user.
     * 
     * @param message the success message to display
     */
    protected void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Display a warning alert to the user.
     * 
     * @param message the warning message to display
     */
    protected void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Safely get a String value from a map, returning a default value if not found.
     * 
     * @param map the map to retrieve from
     * @param key the key to look up
     * @param defaultValue the default value to return if key is not found
     * @return the value from the map or the default value
     */
    protected String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Safely get a String value from a map, returning empty string if not found.
     * 
     * @param map the map to retrieve from
     * @param key the key to look up
     * @return the value from the map or empty string
     */
    protected String safeGetString(Map<String, Object> map, String key) {
        return safeGetString(map, key, "");
    }
    
    /**
     * Safely get a Double value from a map, returning a default value if not found.
     * 
     * @param map the map to retrieve from
     * @param key the key to look up
     * @param defaultValue the default value to return if key is not found
     * @return the value from the map or the default value
     */
    protected double safeGetDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Safely get an Integer value from a map, returning a default value if not found.
     * 
     * @param map the map to retrieve from
     * @param key the key to look up
     * @param defaultValue the default value to return if key is not found
     * @return the value from the map or the default value
     */
    protected int safeGetInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Navigate to the Producer screen.
     */
    protected void handleBackToProducer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showProducerScreen();
        }
    }
    
    /**
     * Navigate to the Analytics screen.
     */
    protected void handleShowAnalytics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showAnalyticsScreen();
        }
    }
    
    /**
     * Navigate to the Logistics screen.
     */
    protected void handleShowLogistics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showLogisticsScreen();
        }
    }
    
    /**
     * Navigate to the Consumer screen.
     */
    protected void handleShowConsumer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showConsumerScreen();
        }
    }
}
