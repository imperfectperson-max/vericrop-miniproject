package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Role-based navigation router.
 * Maps user roles to appropriate FXML screens.
 */
public class RoleRouter {
    private static final Logger logger = LoggerFactory.getLogger(RoleRouter.class);
    
    /**
     * Get the FXML file path for a given role
     * @param role User role (farmer, consumer, supplier, admin)
     * @return FXML file name
     */
    public String getScreenForRole(String role) {
        if (role == null) {
            logger.warn("No role provided, defaulting to consumer screen");
            return "consumer.fxml";
        }
        
        String normalizedRole = role.toLowerCase();
        logger.debug("Routing role '{}' to screen", normalizedRole);
        
        switch (normalizedRole) {
            case "farmer":
                return "producer.fxml";  // Farmer uses producer screen
            case "supplier":
            case "logistics":
                return "logistics.fxml";
            case "consumer":
                return "consumer.fxml";
            case "admin":
                return "analytics.fxml";  // Admin uses analytics screen
            default:
                logger.warn("Unknown role '{}', defaulting to consumer screen", role);
                return "consumer.fxml";
        }
    }
    
    /**
     * Check if a role is valid
     * @param role Role to check
     * @return true if role is valid
     */
    public boolean isValidRole(String role) {
        if (role == null) {
            return false;
        }
        
        String normalizedRole = role.toLowerCase();
        return normalizedRole.equals("farmer") || 
               normalizedRole.equals("consumer") || 
               normalizedRole.equals("supplier") || 
               normalizedRole.equals("logistics") ||
               normalizedRole.equals("admin");
    }
}
