package org.vericrop.gui.util;

/**
 * Utility class for normalizing and working with quality labels.
 * Ensures consistent handling of quality classification strings.
 */
public class QualityLabelUtil {
    
    /**
     * Normalize a quality label to a consistent format.
     * Converts to uppercase and replaces spaces with underscores.
     * 
     * @param label The quality label to normalize
     * @return Normalized label (e.g., "LOW_QUALITY")
     */
    public static String normalize(String label) {
        if (label == null) {
            return "UNKNOWN";
        }
        return label.trim().toUpperCase().replace(" ", "_");
    }
    
    /**
     * Check if a quality label represents fresh/high quality.
     * 
     * @param label The quality label
     * @return true if fresh quality
     */
    public static boolean isFresh(String label) {
        String normalized = normalize(label);
        return "FRESH".equals(normalized) || "HIGH_QUALITY".equals(normalized) || 
               "EXCELLENT".equals(normalized);
    }
    
    /**
     * Check if a quality label represents low quality.
     * 
     * @param label The quality label
     * @return true if low quality
     */
    public static boolean isLowQuality(String label) {
        String normalized = normalize(label);
        return "LOW_QUALITY".equals(normalized) || "FAIR".equals(normalized) || 
               "POOR".equals(normalized);
    }
    
    /**
     * Check if a quality label represents rotten/rejected quality.
     * 
     * @param label The quality label
     * @return true if rotten/rejected
     */
    public static boolean isRotten(String label) {
        String normalized = normalize(label);
        return "ROTTEN".equals(normalized) || "REJECTED".equals(normalized) || 
               "SPOILED".equals(normalized);
    }
}
