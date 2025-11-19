package org.vericrop.service;

import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Quality Evaluation Service with deterministic stub model.
 * Uses image hash to generate consistent quality scores for testing.
 */
public class QualityEvaluationService {
    private static final Logger logger = LoggerFactory.getLogger(QualityEvaluationService.class);
    private static final double PASS_THRESHOLD = 0.7;
    
    /**
     * Evaluates the quality of produce based on image data.
     * Uses a deterministic hash-based algorithm for consistent results.
     * 
     * @param request The evaluation request containing image data
     * @return EvaluationResult with quality score and pass/fail status
     */
    public EvaluationResult evaluate(EvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Evaluation request cannot be null");
        }
        
        logger.info("Evaluating quality for batch: {}", request.getBatchId());
        
        try {
            // Compute deterministic hash from image data
            String dataHash = computeImageHash(request);
            
            // Generate quality score from hash (deterministic)
            double qualityScore = hashToScore(dataHash);
            
            // Determine pass/fail
            String passFail = qualityScore >= PASS_THRESHOLD ? "PASS" : "FAIL";
            
            // Create result
            EvaluationResult result = new EvaluationResult(
                request.getBatchId(),
                qualityScore,
                passFail,
                dataHash
            );
            
            // Set additional fields
            result.setPrediction(scoreToCategory(qualityScore));
            result.setConfidence(0.85 + (qualityScore * 0.15)); // Scale confidence between 0.85-1.0
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("color_consistency", Math.min(1.0, qualityScore + 0.1));
            metadata.put("size_uniformity", Math.max(0.0, qualityScore - 0.05));
            metadata.put("defect_density", Math.max(0.0, 1.0 - qualityScore));
            metadata.put("evaluation_method", "deterministic_stub");
            result.setMetadata(metadata);
            
            logger.info("Evaluation complete for batch {}: score={}, passFail={}", 
                request.getBatchId(), qualityScore, passFail);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error evaluating quality for batch {}: {}", request.getBatchId(), e.getMessage());
            throw new RuntimeException("Quality evaluation failed", e);
        }
    }
    
    /**
     * Computes SHA-256 hash of image data for deterministic scoring.
     */
    private String computeImageHash(EvaluationRequest request) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // Hash image data based on what's available
        if (request.getImagePath() != null && !request.getImagePath().isEmpty()) {
            File imageFile = new File(request.getImagePath());
            if (imageFile.exists()) {
                return hashFile(imageFile, digest);
            } else {
                // File doesn't exist, hash the path itself
                logger.warn("Image file not found: {}, hashing path instead", request.getImagePath());
                return hashString(request.getImagePath(), digest);
            }
        } else if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            return hashString(request.getImageBase64(), digest);
        } else {
            // No image data, hash batch ID for deterministic result
            logger.warn("No image data provided, using batch ID for hash");
            return hashString(request.getBatchId() != null ? request.getBatchId() : "default", digest);
        }
    }
    
    /**
     * Hash a file's contents.
     */
    private String hashFile(File file, MessageDigest digest) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return bytesToHex(digest.digest());
    }
    
    /**
     * Hash a string.
     */
    private String hashString(String input, MessageDigest digest) {
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }
    
    /**
     * Convert hash bytes to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Convert hash to quality score (0.0 to 1.0).
     * Uses hash values to generate deterministic scores.
     */
    private double hashToScore(String hash) {
        // Use first 8 characters of hash to generate score
        String hashPrefix = hash.substring(0, Math.min(8, hash.length()));
        long hashValue = Long.parseLong(hashPrefix, 16);
        
        // Normalize to 0.0-1.0 range
        // Add slight bias toward higher scores for more realistic results
        double rawScore = (double)(hashValue % 1000) / 1000.0;
        double biasedScore = 0.3 + (rawScore * 0.7); // Scale to 0.3-1.0 range
        
        return Math.round(biasedScore * 100.0) / 100.0; // Round to 2 decimals
    }
    
    /**
     * Convert numeric score to quality category.
     */
    private String scoreToCategory(double score) {
        if (score >= 0.85) {
            return "Fresh";
        } else if (score >= 0.7) {
            return "Good";
        } else if (score >= 0.5) {
            return "Fair";
        } else {
            return "Poor";
        }
    }
    
    /**
     * Get the pass threshold for quality evaluation.
     */
    public double getPassThreshold() {
        return PASS_THRESHOLD;
    }
}
