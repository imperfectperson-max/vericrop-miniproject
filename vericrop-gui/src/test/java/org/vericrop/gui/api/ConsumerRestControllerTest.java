package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.api.ConsumerRestController.BatchVerificationRequest;
import org.vericrop.gui.api.ConsumerRestController.ComplianceStatus;
import org.vericrop.gui.api.ConsumerRestController.VerificationResponse;
import org.vericrop.gui.api.ConsumerRestController.ViolationDetail;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ConsumerRestController
 */
class ConsumerRestControllerTest {
    
    private ConsumerRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ConsumerRestController();
    }
    
    // ==================== Verification Endpoint Tests ====================
    
    @Test
    void testVerifyBatch_ValidBatch() {
        BatchVerificationRequest request = createValidBatchRequest();
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().isEmpty());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.getBody().getBatchId());
    }
    
    @Test
    void testVerifyBatch_ValidBatchWithAllFields() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setProductType("Apple");
        request.setProducerId("FARMER_001");
        request.setBatchName("Apple Batch 2024-01");
        request.setQuantity(500);
        request.setLocation("Sunny Valley Farm");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        
        // Check details
        Map<String, Object> details = response.getBody().getDetails();
        assertEquals(85, details.get("qualityScore"));
        assertEquals("Good", details.get("qualityLabel"));
        assertEquals("Apple", details.get("productType"));
        assertEquals("FARMER_001", details.get("producerId"));
    }
    
    @Test
    void testVerifyBatch_NullRequest() {
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(null);
        
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().contains("Request body is required"));
    }
    
    @Test
    void testVerifyBatch_MissingId() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setId(null);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("id is required")));
    }
    
    @Test
    void testVerifyBatch_EmptyId() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setId("   ");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("id is required")));
    }
    
    @Test
    void testVerifyBatch_InvalidIdFormat() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setId("not-a-valid-uuid");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("valid UUID format")));
    }
    
    @Test
    void testVerifyBatch_MissingQualityScore() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQualityScore(null);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("qualityScore is required")));
    }
    
    @Test
    void testVerifyBatch_QualityScoreTooLow() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQualityScore(-1);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("between 0 and 100")));
    }
    
    @Test
    void testVerifyBatch_QualityScoreTooHigh() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQualityScore(101);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("between 0 and 100")));
    }
    
    @Test
    void testVerifyBatch_QualityScoreBoundary_Zero() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQualityScore(0);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        assertEquals("Poor", response.getBody().getDetails().get("qualityLabel"));
    }
    
    @Test
    void testVerifyBatch_QualityScoreBoundary_Hundred() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQualityScore(100);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        assertEquals("Fresh", response.getBody().getDetails().get("qualityLabel"));
    }
    
    @Test
    void testVerifyBatch_MissingQrCode() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQrCode(null);
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("qrCode is required")));
    }
    
    @Test
    void testVerifyBatch_EmptyQrCode() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQrCode("   ");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("qrCode is required")));
    }
    
    @Test
    void testVerifyBatch_InvalidQrCode() {
        BatchVerificationRequest request = createValidBatchRequest();
        request.setQrCode("!!!invalid base64!!!");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().stream()
            .anyMatch(r -> r.contains("valid base64")));
    }
    
    @Test
    void testVerifyBatch_ValidBase64QrCode() {
        BatchVerificationRequest request = createValidBatchRequest();
        // Valid base64 encoded JSON
        String payload = "{\"id\":\"test\",\"quality\":85}";
        request.setQrCode(Base64.getEncoder().encodeToString(payload.getBytes()));
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        // Check that payload was decoded
        assertNotNull(response.getBody().getDetails().get("qrPayload"));
    }
    
    @Test
    void testVerifyBatch_ValidDataUrlQrCode() {
        BatchVerificationRequest request = createValidBatchRequest();
        // Valid PNG data URL format (with minimal valid base64)
        request.setQrCode("data:image/png;base64,iVBORw0KGgo=");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        assertEquals("[PNG Image Data]", response.getBody().getDetails().get("qrPayload"));
    }
    
    @Test
    void testVerifyBatch_MultipleErrors() {
        BatchVerificationRequest request = new BatchVerificationRequest();
        // All fields are null
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().isVerified());
        // Should have at least 3 reasons
        assertTrue(response.getBody().getReasons().size() >= 3);
    }
    
    @Test
    void testVerifyBatch_QualityLabels() {
        // Test all quality label boundaries
        int[] scores = {95, 85, 65, 30};
        String[] expectedLabels = {"Fresh", "Good", "Fair", "Poor"};
        
        for (int i = 0; i < scores.length; i++) {
            BatchVerificationRequest request = createValidBatchRequest();
            request.setQualityScore(scores[i]);
            
            ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
            
            assertTrue(response.getBody().isVerified());
            assertEquals(expectedLabels[i], response.getBody().getDetails().get("qualityLabel"),
                "Expected label " + expectedLabels[i] + " for score " + scores[i]);
        }
    }
    
    // ==================== Integration Test with Producer Simulation ====================
    
    @Test
    void testVerifyBatch_WithSimulatedProducerBatch() {
        // Simulate what ProducerRestController.simulateBatch() would produce
        BatchVerificationRequest request = new BatchVerificationRequest();
        request.setId(java.util.UUID.randomUUID().toString());
        request.setQualityScore(85);
        
        // Create QR payload similar to producer simulation
        String payload = "{\"id\":\"" + request.getId() + "\",\"qualityScore\":85}";
        request.setQrCode(Base64.getEncoder().encodeToString(payload.getBytes()));
        request.setProductType("Apple");
        request.setProducerId("FARMER_001");
        
        ResponseEntity<VerificationResponse> response = controller.verifyBatch(request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isVerified());
        assertTrue(response.getBody().getReasons().isEmpty());
        assertEquals(request.getId(), response.getBody().getBatchId());
    }
    
    // ==================== Validation Helper Method Tests ====================
    
    @Test
    void testIsValidUUID_ValidUUIDs() {
        assertTrue(ConsumerRestController.isValidUUID("550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(ConsumerRestController.isValidUUID("123e4567-e89b-12d3-a456-426614174000"));
        assertTrue(ConsumerRestController.isValidUUID("00000000-0000-0000-0000-000000000000"));
        assertTrue(ConsumerRestController.isValidUUID("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"));
    }
    
    @Test
    void testIsValidUUID_InvalidUUIDs() {
        assertFalse(ConsumerRestController.isValidUUID(null));
        assertFalse(ConsumerRestController.isValidUUID(""));
        assertFalse(ConsumerRestController.isValidUUID("   "));
        assertFalse(ConsumerRestController.isValidUUID("not-a-uuid"));
        assertFalse(ConsumerRestController.isValidUUID("550e8400-e29b-41d4-a716-44665544")); // too short
        assertFalse(ConsumerRestController.isValidUUID("550e8400e29b41d4a716446655440000")); // no hyphens
    }
    
    @Test
    void testIsValidQualityScore_Valid() {
        assertTrue(ConsumerRestController.isValidQualityScore(0));
        assertTrue(ConsumerRestController.isValidQualityScore(50));
        assertTrue(ConsumerRestController.isValidQualityScore(100));
    }
    
    @Test
    void testIsValidQualityScore_Invalid() {
        assertFalse(ConsumerRestController.isValidQualityScore(null));
        assertFalse(ConsumerRestController.isValidQualityScore(-1));
        assertFalse(ConsumerRestController.isValidQualityScore(101));
        assertFalse(ConsumerRestController.isValidQualityScore(-100));
        assertFalse(ConsumerRestController.isValidQualityScore(1000));
    }
    
    @Test
    void testIsValidQRCode_Valid() {
        assertTrue(ConsumerRestController.isValidQRCode("SGVsbG8gV29ybGQ=")); // "Hello World"
        assertTrue(ConsumerRestController.isValidQRCode("data:image/png;base64,iVBORw0KGgo="));
        assertTrue(ConsumerRestController.isValidQRCode("dGVzdA==")); // "test"
    }
    
    @Test
    void testIsValidQRCode_Invalid() {
        assertFalse(ConsumerRestController.isValidQRCode(null));
        assertFalse(ConsumerRestController.isValidQRCode(""));
        assertFalse(ConsumerRestController.isValidQRCode("   "));
        assertFalse(ConsumerRestController.isValidQRCode("!!!")); // invalid base64 chars
        assertFalse(ConsumerRestController.isValidQRCode("ab")); // too short
    }
    
    // ==================== Compliance Endpoint Tests ====================
    
    @Test
    void testGetBatchCompliance_NonExistent() {
        ResponseEntity<ComplianceStatus> response = controller.getBatchCompliance("NON_EXISTENT");
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("NON_EXISTENT", response.getBody().getBatchId());
        assertNull(response.getBody().getCompliant());
        assertEquals("No compliance data available", response.getBody().getDetails());
    }
    
    @Test
    void testUpdateBatchCompliance() {
        ComplianceStatus status = new ComplianceStatus("TEST_BATCH_001", true, 0, "All checks passed");
        
        ResponseEntity<Void> updateResponse = controller.updateBatchCompliance("TEST_BATCH_001", status);
        
        assertEquals(200, updateResponse.getStatusCodeValue());
        
        // Verify status was stored
        ResponseEntity<ComplianceStatus> getResponse = controller.getBatchCompliance("TEST_BATCH_001");
        assertNotNull(getResponse.getBody());
        assertEquals("TEST_BATCH_001", getResponse.getBody().getBatchId());
        assertTrue(getResponse.getBody().getCompliant());
        assertEquals(0, getResponse.getBody().getViolationCount());
        assertEquals("All checks passed", getResponse.getBody().getDetails());
    }
    
    @Test
    void testUpdateBatchCompliance_WithViolations() {
        ComplianceStatus status = new ComplianceStatus("TEST_BATCH_002", false, 2, "Temperature violations detected");
        
        List<ViolationDetail> violations = new ArrayList<>();
        violations.add(new ViolationDetail(System.currentTimeMillis(), 10.5, 120, "Temp exceeded 10°C"));
        violations.add(new ViolationDetail(System.currentTimeMillis(), 9.8, 60, "Temp exceeded 9°C"));
        status.setViolations(violations);
        
        controller.updateBatchCompliance("TEST_BATCH_002", status);
        
        // Verify violations were stored
        ResponseEntity<ComplianceStatus> getResponse = controller.getBatchCompliance("TEST_BATCH_002");
        assertNotNull(getResponse.getBody());
        assertFalse(getResponse.getBody().getCompliant());
        assertEquals(2, getResponse.getBody().getViolationCount());
        assertEquals(2, getResponse.getBody().getViolations().size());
    }
    
    @Test
    void testGetAllBatchCompliance_Empty() {
        ResponseEntity<List<ComplianceStatus>> response = controller.getAllBatchCompliance();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
    
    @Test
    void testGetAllBatchCompliance_Multiple() throws InterruptedException {
        // Add multiple compliance statuses
        ComplianceStatus status1 = new ComplianceStatus("BATCH_001", true, 0, "Compliant");
        controller.updateBatchCompliance("BATCH_001", status1);
        
        Thread.sleep(10);
        
        ComplianceStatus status2 = new ComplianceStatus("BATCH_002", false, 1, "Minor violation");
        controller.updateBatchCompliance("BATCH_002", status2);
        
        Thread.sleep(10);
        
        ComplianceStatus status3 = new ComplianceStatus("BATCH_003", true, 0, "Compliant");
        controller.updateBatchCompliance("BATCH_003", status3);
        
        // Get all
        ResponseEntity<List<ComplianceStatus>> response = controller.getAllBatchCompliance();
        List<ComplianceStatus> statuses = response.getBody();
        
        assertEquals(3, statuses.size());
        // Should be sorted by timestamp descending
        assertEquals("BATCH_003", statuses.get(0).getBatchId());
        assertEquals("BATCH_002", statuses.get(1).getBatchId());
        assertEquals("BATCH_001", statuses.get(2).getBatchId());
    }
    
    @Test
    void testBatchIdOverride() {
        // Create status with different batch ID
        ComplianceStatus status = new ComplianceStatus("WRONG_ID", true, 0, "Test");
        
        // Should be overridden by path variable
        controller.updateBatchCompliance("CORRECT_ID", status);
        
        ResponseEntity<ComplianceStatus> response = controller.getBatchCompliance("CORRECT_ID");
        assertEquals("CORRECT_ID", response.getBody().getBatchId());
    }
    
    // ==================== Helper Methods ====================
    
    private BatchVerificationRequest createValidBatchRequest() {
        BatchVerificationRequest request = new BatchVerificationRequest();
        request.setId("550e8400-e29b-41d4-a716-446655440000");
        request.setQualityScore(85);
        // Valid base64 encoded JSON
        String payload = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"quality\":85}";
        request.setQrCode(Base64.getEncoder().encodeToString(payload.getBytes()));
        return request;
    }
}
