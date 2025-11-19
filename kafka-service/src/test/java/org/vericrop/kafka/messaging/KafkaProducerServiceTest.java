package org.vericrop.kafka.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
import org.vericrop.dto.ShipmentRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaProducerService.
 * Tests message production in in-memory mode (Kafka not required).
 */
class KafkaProducerServiceTest {
    
    private KafkaProducerService service;
    
    @BeforeEach
    void setUp() {
        // Use in-memory mode (no actual Kafka connection)
        service = new KafkaProducerService(false);
    }
    
    @Test
    void testSendEvaluationRequest() {
        // Given
        EvaluationRequest request = new EvaluationRequest();
        request.setBatchId("TEST_BATCH_001");
        request.setProductType("apple");
        request.setFarmerId("farmer_test");
        request.setImagePath("/test/image.jpg");
        
        // When
        boolean result = service.sendEvaluationRequest(request);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testSendEvaluationResult() {
        // Given
        EvaluationResult result = new EvaluationResult();
        result.setBatchId("TEST_BATCH_002");
        result.setQualityScore(0.85);
        result.setPassFail("PASS");
        result.setPrediction("Fresh");
        result.setConfidence(0.92);
        result.setDataHash("abc123");
        
        // When
        boolean sendResult = service.sendEvaluationResult(result);
        
        // Then
        assertTrue(sendResult);
    }
    
    @Test
    void testSendShipmentRecord() {
        // Given
        ShipmentRecord record = new ShipmentRecord();
        record.setShipmentId("SHIP_TEST_001");
        record.setBatchId("BATCH_TEST_001");
        record.setFromParty("farmer_001");
        record.setToParty("warehouse_001");
        record.setStatus("CREATED");
        record.setQualityScore(0.88);
        record.setLedgerId("LEDGER_001");
        record.setLedgerHash("hash123");
        
        // When
        boolean result = service.sendShipmentRecord(record);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testSendNullEvaluationRequest() {
        // When
        boolean result = service.sendEvaluationRequest(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testSendNullEvaluationResult() {
        // When
        boolean result = service.sendEvaluationResult(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testSendNullShipmentRecord() {
        // When
        boolean result = service.sendShipmentRecord(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsKafkaEnabled() {
        // When
        boolean enabled = service.isKafkaEnabled();
        
        // Then
        assertFalse(enabled); // We created service with kafkaEnabled=false
    }
    
    @Test
    void testFlush() {
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> service.flush());
    }
    
    @Test
    void testClose() {
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> service.close());
    }
    
    @Test
    void testMultipleMessages() {
        // Given
        EvaluationRequest request1 = new EvaluationRequest("BATCH_001", "/img1.jpg", "apple");
        EvaluationRequest request2 = new EvaluationRequest("BATCH_002", "/img2.jpg", "orange");
        EvaluationRequest request3 = new EvaluationRequest("BATCH_003", "/img3.jpg", "banana");
        
        // When
        boolean result1 = service.sendEvaluationRequest(request1);
        boolean result2 = service.sendEvaluationRequest(request2);
        boolean result3 = service.sendEvaluationRequest(request3);
        
        // Then
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
    }
}
