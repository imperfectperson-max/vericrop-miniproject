package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QualityEvaluationService.
 * Tests the deterministic stub model behavior.
 */
class QualityEvaluationServiceTest {
    
    private QualityEvaluationService service;
    
    @BeforeEach
    void setUp() {
        service = new QualityEvaluationService();
    }
    
    @Test
    void testEvaluateWithBatchId() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("TEST_BATCH_001");
        request.setProductType("apple");
        request.setFarmerId("farmer_001");
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then
        assertNotNull(result);
        assertEquals("TEST_BATCH_001", result.getBatchId());
        assertTrue(result.getQualityScore() >= 0.0 && result.getQualityScore() <= 1.0);
        assertNotNull(result.getPassFail());
        assertNotNull(result.getPrediction());
        assertNotNull(result.getDataHash());
        assertTrue(result.getConfidence() >= 0.85 && result.getConfidence() <= 1.0);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("color_consistency"));
        assertTrue(result.getMetadata().containsKey("size_uniformity"));
        assertTrue(result.getMetadata().containsKey("defect_density"));
    }
    
    @Test
    void testEvaluateDeterministic() {
        // Given - Same batch ID should produce same result
        EvaluationRequest request1 = new EvaluationRequest();
        request1.setBatchId("DETERMINISTIC_BATCH");
        
        EvaluationRequest request2 = new EvaluationRequest();
        request2.setBatchId("DETERMINISTIC_BATCH");
        
        // When
        EvaluationResult result1 = service.evaluate(request1);
        EvaluationResult result2 = service.evaluate(request2);
        
        // Then - Results should be identical for same batch ID
        assertEquals(result1.getQualityScore(), result2.getQualityScore());
        assertEquals(result1.getPassFail(), result2.getPassFail());
        assertEquals(result1.getPrediction(), result2.getPrediction());
        assertEquals(result1.getDataHash(), result2.getDataHash());
    }
    
    @Test
    void testEvaluatePassThreshold() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("BATCH_PASS_TEST");
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then
        if (result.getQualityScore() >= 0.7) {
            assertEquals("PASS", result.getPassFail());
        } else {
            assertEquals("FAIL", result.getPassFail());
        }
    }
    
    @Test
    void testEvaluatePredictionCategories() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("CATEGORY_TEST");
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then
        String prediction = result.getPrediction();
        assertTrue(
            "Fresh".equals(prediction) || 
            "Good".equals(prediction) || 
            "Fair".equals(prediction) || 
            "Poor".equals(prediction),
            "Prediction should be one of: Fresh, Good, Fair, Poor"
        );
    }
    
    @Test
    void testEvaluateWithImagePath() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("BATCH_WITH_IMAGE");
        request.setImagePath("/nonexistent/path/image.jpg");
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then - Should handle non-existent image gracefully
        assertNotNull(result);
        assertNotNull(result.getDataHash());
    }
    
    @Test
    void testEvaluateWithImageBase64() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("BATCH_BASE64");
        request.setImageBase64("aGVsbG8gd29ybGQ="); // base64 for "hello world"
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getDataHash());
    }
    
    @Test
    void testEvaluateNullRequest() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            service.evaluate(null);
        });
    }
    
    @Test
    void testGetPassThreshold() {
        // When
        double threshold = service.getPassThreshold();
        
        // Then
        assertEquals(0.7, threshold, 0.001);
    }
    
    @Test
    void testMetadataValues() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("METADATA_TEST");
        
        // When
        EvaluationResult result = service.evaluate(request);
        
        // Then
        double colorConsistency = (Double) result.getMetadata().get("color_consistency");
        double sizeUniformity = (Double) result.getMetadata().get("size_uniformity");
        double defectDensity = (Double) result.getMetadata().get("defect_density");
        
        assertTrue(colorConsistency >= 0.0 && colorConsistency <= 1.0);
        assertTrue(sizeUniformity >= 0.0 && sizeUniformity <= 1.0);
        assertTrue(defectDensity >= 0.0 && defectDensity <= 1.0);
        assertEquals("deterministic_stub", result.getMetadata().get("evaluation_method"));
    }
}
