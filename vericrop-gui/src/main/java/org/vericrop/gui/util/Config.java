package org.vericrop.gui.util;

/**
 * Centralized configuration utility for VeriCrop GUI.
 * Manages demo mode detection and ML service URL configuration.
 */
public class Config {
    
    /**
     * Check if demo mode is enabled.
     * Checks both environment variable (VERICROP_LOAD_DEMO) and system property (vericrop.loadDemo).
     * 
     * @return true if demo mode is enabled, false otherwise
     */
    public static boolean isDemoMode() {
        // Check environment variable first
        String envDemo = System.getenv("VERICROP_LOAD_DEMO");
        if ("true".equalsIgnoreCase(envDemo)) {
            return true;
        }
        
        // Check system property (set from UI)
        String propDemo = System.getProperty("vericrop.loadDemo");
        return "true".equalsIgnoreCase(propDemo);
    }
    
    /**
     * Set demo mode via system property.
     * This is typically called from the Login screen when the user toggles demo mode.
     * 
     * @param enabled true to enable demo mode, false to disable
     */
    public static void setDemoMode(boolean enabled) {
        System.setProperty("vericrop.loadDemo", String.valueOf(enabled));
    }
    
    /**
     * Get the base URL for the ML service.
     * Can be overridden via VERICROP_ML_SERVICE_URL environment variable.
     * 
     * @return the ML service base URL
     */
    public static String getMlServiceUrl() {
        String envUrl = System.getenv("VERICROP_ML_SERVICE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl.trim();
        }
        return "http://localhost:8000";
    }
    
    /**
     * Get the full URL for a specific ML service endpoint.
     * 
     * @param endpoint the endpoint path (e.g., "/predict", "/batches")
     * @return the complete URL
     */
    public static String getMlServiceEndpoint(String endpoint) {
        String baseUrl = getMlServiceUrl();
        // Ensure no double slashes
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        return baseUrl + endpoint;
    }
}
