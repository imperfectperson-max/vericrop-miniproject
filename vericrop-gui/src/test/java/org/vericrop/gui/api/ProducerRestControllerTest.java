package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProducerRestController.
 * 
 * Tests the blockchain record creation endpoint and related functionality.
 */
class ProducerRestControllerTest {
    
    private ProducerRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ProducerRestController();
    }
    
    // ==================== Happy Path Tests ====================
    
    @Test
    void testCreateBlockchainRecord_Success() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("record_id"));
        assertNotNull(response.getBody().get("block_hash"));
        assertNotNull(response.getBody().get("block_index"));
        assertEquals("Blockchain record created successfully", response.getBody().get("message"));
    }
    
    @Test
    void testCreateBlockchainRecord_WithCustomBatchId() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setBatchId("CUSTOM_BATCH_001");
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("CUSTOM_BATCH_001", response.getBody().get("record_id"));
    }
    
    @Test
    void testCreateBlockchainRecord_WithAllFields() {
        ProducerRestController.BlockchainRecordRequest request = new ProducerRestController.BlockchainRecordRequest();
        request.setProducerId("FARMER_001");
        request.setBatchName("Premium Apple Batch");
        request.setProductType("Apple");
        request.setQuantity(1000);
        request.setQualityScore(0.95);
        request.setLocation("Sunny Valley Farm");
        
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("variety", "Honeycrisp");
        additionalData.put("harvestDate", "2024-01-15");
        additionalData.put("organic", true);
        request.setAdditionalData(additionalData);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
    
    @Test
    void testCreateBlockchainRecord_MultipleRecords() {
        // Create first record
        ProducerRestController.BlockchainRecordRequest request1 = createValidRequest();
        request1.setProducerId("FARMER_001");
        ResponseEntity<Map<String, Object>> response1 = controller.createBlockchainRecord(request1);
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        int blockIndex1 = (Integer) response1.getBody().get("block_index");
        
        // Create second record
        ProducerRestController.BlockchainRecordRequest request2 = createValidRequest();
        request2.setProducerId("FARMER_002");
        ResponseEntity<Map<String, Object>> response2 = controller.createBlockchainRecord(request2);
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        int blockIndex2 = (Integer) response2.getBody().get("block_index");
        
        // Verify block indices are sequential
        assertEquals(blockIndex1 + 1, blockIndex2);
    }
    
    // ==================== Validation Tests ====================
    
    @Test
    void testCreateBlockchainRecord_NullRequest() {
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(null);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals("Validation failed", response.getBody().get("error"));
    }
    
    @Test
    void testCreateBlockchainRecord_MissingProducerId() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setProducerId(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("details").toString().contains("producerId"));
    }
    
    @Test
    void testCreateBlockchainRecord_EmptyProducerId() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setProducerId("   ");
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }
    
    @Test
    void testCreateBlockchainRecord_MissingBatchName() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setBatchName(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("batchName"));
    }
    
    @Test
    void testCreateBlockchainRecord_MissingProductType() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setProductType(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("productType"));
    }
    
    @Test
    void testCreateBlockchainRecord_NegativeQuantity() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setQuantity(-100);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("quantity"));
    }
    
    @Test
    void testCreateBlockchainRecord_InvalidQualityScore_TooLow() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setQualityScore(-0.1);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("qualityScore"));
    }
    
    @Test
    void testCreateBlockchainRecord_InvalidQualityScore_TooHigh() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setQualityScore(1.5);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("qualityScore"));
    }
    
    @Test
    void testCreateBlockchainRecord_ValidQualityScoreBoundary_Zero() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setQualityScore(0.0);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
    
    @Test
    void testCreateBlockchainRecord_ValidQualityScoreBoundary_One() {
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setQualityScore(1.0);
        
        ResponseEntity<Map<String, Object>> response = controller.createBlockchainRecord(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
    
    // ==================== Blockchain Status Tests ====================
    
    @Test
    void testGetBlockchainStatus() {
        // First create some records
        controller.createBlockchainRecord(createValidRequest());
        controller.createBlockchainRecord(createValidRequest());
        
        ResponseEntity<Map<String, Object>> response = controller.getBlockchainStatus(10);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue((Integer) response.getBody().get("total_blocks") >= 3); // genesis + 2 created
        assertTrue((Boolean) response.getBody().get("chain_valid"));
        assertNotNull(response.getBody().get("recent_blocks"));
    }
    
    @Test
    void testGetBlockchainStatus_WithLimit() {
        // Create multiple records
        for (int i = 0; i < 5; i++) {
            ProducerRestController.BlockchainRecordRequest request = createValidRequest();
            request.setProducerId("FARMER_" + i);
            controller.createBlockchainRecord(request);
        }
        
        ResponseEntity<Map<String, Object>> response = controller.getBlockchainStatus(3);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<?> recentBlocks = (List<?>) response.getBody().get("recent_blocks");
        assertTrue(recentBlocks.size() <= 3);
    }
    
    // ==================== Blockchain Validation Tests ====================
    
    @Test
    void testValidateBlockchain_Valid() {
        // Create some records
        controller.createBlockchainRecord(createValidRequest());
        
        ResponseEntity<Map<String, Object>> response = controller.validateBlockchain();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue((Boolean) response.getBody().get("valid"));
        assertTrue(response.getBody().get("message").toString().contains("valid"));
    }
    
    // ==================== Batch Transactions Tests ====================
    
    @Test
    void testGetBatchTransactions_Success() {
        // Create a record with specific batch ID
        ProducerRestController.BlockchainRecordRequest request = createValidRequest();
        request.setBatchId("BATCH_FOR_LOOKUP");
        controller.createBlockchainRecord(request);
        
        ResponseEntity<Map<String, Object>> response = controller.getBatchTransactions("BATCH_FOR_LOOKUP");
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("BATCH_FOR_LOOKUP", response.getBody().get("batch_id"));
        assertTrue((Integer) response.getBody().get("transaction_count") > 0);
    }
    
    @Test
    void testGetBatchTransactions_NotFound() {
        ResponseEntity<Map<String, Object>> response = controller.getBatchTransactions("NON_EXISTENT_BATCH");
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("No transactions found"));
    }
    
    // ==================== Health Check Tests ====================
    
    @Test
    void testHealthCheck() {
        ResponseEntity<Map<String, Object>> response = controller.health();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("producer-api", response.getBody().get("service"));
        assertNotNull(response.getBody().get("blockchain_blocks"));
        assertNotNull(response.getBody().get("blockchain_valid"));
    }
    
    // ==================== Batch Creation Tests ====================
    
    @Test
    void testCreateBatch_Success() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("batch_id"));
        assertEquals("FARMER_001", response.getBody().get("producer_id"));
        assertEquals("Test Apple Batch", response.getBody().get("name"));
        assertEquals("Apple", response.getBody().get("product_type"));
        assertEquals(100, response.getBody().get("quantity"));
        assertEquals("created", response.getBody().get("status"));
        assertEquals("Batch created successfully", response.getBody().get("message"));
    }
    
    @Test
    void testCreateBatch_WithCustomBatchId() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setBatchId("CUSTOM_BATCH_001");
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("CUSTOM_BATCH_001", response.getBody().get("batch_id"));
        assertEquals("FARMER_001", response.getBody().get("producer_id"));
    }
    
    @Test
    void testCreateBatch_WithQualityScoreAndLabel() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQualityScore(0.95);
        request.setQualityLabel("FRESH");
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(0.95, response.getBody().get("quality_score"));
        assertEquals("FRESH", response.getBody().get("quality_label"));
    }
    
    @Test
    void testCreateBatch_NullRequest() {
        ResponseEntity<Map<String, Object>> response = controller.createBatch(null);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals("Validation failed", response.getBody().get("error"));
    }
    
    @Test
    void testCreateBatch_MissingProducerId() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setProducerId(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("details").toString().contains("producerId"));
    }
    
    @Test
    void testCreateBatch_EmptyProducerId() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setProducerId("   ");
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }
    
    @Test
    void testCreateBatch_MissingName() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setName(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("name"));
    }
    
    @Test
    void testCreateBatch_MissingProductType() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setProductType(null);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("productType"));
    }
    
    @Test
    void testCreateBatch_NegativeQuantity() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQuantity(-100);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("quantity"));
    }
    
    @Test
    void testCreateBatch_InvalidQualityScore_TooLow() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQualityScore(-0.1);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("qualityScore"));
    }
    
    @Test
    void testCreateBatch_InvalidQualityScore_TooHigh() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQualityScore(1.5);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("details").toString().contains("qualityScore"));
    }
    
    @Test
    void testCreateBatch_ValidQualityScoreBoundary_Zero() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQualityScore(0.0);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
    
    @Test
    void testCreateBatch_ValidQualityScoreBoundary_One() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        request.setQualityScore(1.0);
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
    
    @Test
    void testCreateBatch_MultipleBatches_SameProducer() {
        // Create first batch
        ProducerRestController.BatchCreationRequest request1 = createValidBatchRequest();
        request1.setName("Batch 1");
        ResponseEntity<Map<String, Object>> response1 = controller.createBatch(request1);
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        String batchId1 = (String) response1.getBody().get("batch_id");
        
        // Create second batch for same producer
        ProducerRestController.BatchCreationRequest request2 = createValidBatchRequest();
        request2.setName("Batch 2");
        ResponseEntity<Map<String, Object>> response2 = controller.createBatch(request2);
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        String batchId2 = (String) response2.getBody().get("batch_id");
        
        // Verify both have same producer but different batch IDs
        assertEquals("FARMER_001", response1.getBody().get("producer_id"));
        assertEquals("FARMER_001", response2.getBody().get("producer_id"));
        assertNotEquals(batchId1, batchId2);
    }
    
    @Test
    void testCreateBatch_MultipleBatches_DifferentProducers() {
        // Create batch for first producer
        ProducerRestController.BatchCreationRequest request1 = createValidBatchRequest();
        request1.setProducerId("FARMER_001");
        ResponseEntity<Map<String, Object>> response1 = controller.createBatch(request1);
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        
        // Create batch for second producer
        ProducerRestController.BatchCreationRequest request2 = createValidBatchRequest();
        request2.setProducerId("FARMER_002");
        ResponseEntity<Map<String, Object>> response2 = controller.createBatch(request2);
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        
        // Verify different producers
        assertEquals("FARMER_001", response1.getBody().get("producer_id"));
        assertEquals("FARMER_002", response2.getBody().get("producer_id"));
    }
    
    @Test
    void testCreateBatch_ResponseContainsTimestamp() {
        ProducerRestController.BatchCreationRequest request = createValidBatchRequest();
        
        ResponseEntity<Map<String, Object>> response = controller.createBatch(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("timestamp"));
    }
    
    // ==================== Helper Methods ====================
    
    private ProducerRestController.BlockchainRecordRequest createValidRequest() {
        ProducerRestController.BlockchainRecordRequest request = new ProducerRestController.BlockchainRecordRequest();
        request.setProducerId("FARMER_TEST");
        request.setBatchName("Test Batch");
        request.setProductType("Apple");
        request.setQuantity(100);
        request.setQualityScore(0.85);
        request.setLocation("Test Farm");
        return request;
    }
    
    private ProducerRestController.BatchCreationRequest createValidBatchRequest() {
        ProducerRestController.BatchCreationRequest request = new ProducerRestController.BatchCreationRequest();
        request.setProducerId("FARMER_001");
        request.setName("Test Apple Batch");
        request.setProductType("Apple");
        request.setQuantity(100);
        return request;
    }
}
