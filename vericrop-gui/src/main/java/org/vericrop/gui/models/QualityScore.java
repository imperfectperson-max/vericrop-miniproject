package org.vericrop.gui.models;

/**
 * Data Transfer Object for Quality Score information.
 * Encapsulates quality scoring data with thresholds and classifications.
 */
public class QualityScore {
    
    private Double score;
    private String label;
    private String classification;  // PRIME, STANDARD, SUB_STANDARD
    
    // Quality thresholds
    public static final double PRIME_THRESHOLD = 0.8;
    public static final double STANDARD_THRESHOLD = 0.6;

    public QualityScore() {
    }

    public QualityScore(Double score, String label) {
        this.score = score;
        this.label = label;
        this.classification = classifyQuality(score);
    }

    /**
     * Classify quality based on score
     */
    private String classifyQuality(Double score) {
        if (score == null) return "UNKNOWN";
        if (score >= PRIME_THRESHOLD) return "PRIME";
        if (score >= STANDARD_THRESHOLD) return "STANDARD";
        return "SUB_STANDARD";
    }

    /**
     * Check if the quality score passes the threshold
     */
    public boolean passes(double threshold) {
        return score != null && score >= threshold;
    }

    /**
     * Check if this is prime quality
     */
    public boolean isPrime() {
        return "PRIME".equals(classification);
    }

    /**
     * Check if this is standard quality
     */
    public boolean isStandard() {
        return "STANDARD".equals(classification);
    }

    /**
     * Check if this is sub-standard quality
     */
    public boolean isSubStandard() {
        return "SUB_STANDARD".equals(classification);
    }

    // Getters and Setters
    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
        this.classification = classifyQuality(score);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getClassification() {
        return classification;
    }

    @Override
    public String toString() {
        return "QualityScore{" +
                "score=" + score +
                ", label='" + label + '\'' +
                ", classification='" + classification + '\'' +
                '}';
    }
}
