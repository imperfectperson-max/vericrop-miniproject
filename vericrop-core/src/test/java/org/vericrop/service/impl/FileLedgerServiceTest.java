package org.vericrop.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.ShipmentRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileLedgerService.
 * Tests append-only ledger functionality and record integrity.
 */
class FileLedgerServiceTest {
    
    private FileLedgerService service;
    private String testLedgerDir;
    
    @BeforeEach
    void setUp() {
        // Use unique directory for each test
        testLedgerDir = "test-ledger-" + System.currentTimeMillis();
        service = new FileLedgerService(testLedgerDir);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up test ledger directory
        Path ledgerPath = service.getLedgerPath();
        if (Files.exists(ledgerPath)) {
            Files.delete(ledgerPath);
        }
        Path ledgerDir = Paths.get(testLedgerDir);
        if (Files.exists(ledgerDir)) {
            Files.delete(ledgerDir);
        }
    }
    
    @Test
    void testRecordShipment() {
        // Given
        ShipmentRecord record = new ShipmentRecord();
        record.setShipmentId("SHIP_001");
        record.setBatchId("BATCH_001");
        record.setFromParty("farmer_001");
        record.setToParty("warehouse_001");
        record.setStatus("CREATED");
        record.setQualityScore(0.85);
        
        // When
        ShipmentRecord result = service.recordShipment(record);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getLedgerId());
        assertNotNull(result.getLedgerHash());
        assertEquals("SHIP_001", result.getShipmentId());
        assertEquals("BATCH_001", result.getBatchId());
    }
    
    @Test
    void testGetShipmentByLedgerId() {
        // Given
        ShipmentRecord record = new ShipmentRecord();
        record.setShipmentId("SHIP_002");
        record.setBatchId("BATCH_002");
        record.setFromParty("farmer_002");
        record.setToParty("warehouse_002");
        record.setStatus("IN_TRANSIT");
        record.setQualityScore(0.92);
        
        ShipmentRecord recorded = service.recordShipment(record);
        String ledgerId = recorded.getLedgerId();
        
        // When
        ShipmentRecord retrieved = service.getShipmentByLedgerId(ledgerId);
        
        // Then
        assertNotNull(retrieved);
        assertEquals(ledgerId, retrieved.getLedgerId());
        assertEquals("SHIP_002", retrieved.getShipmentId());
        assertEquals("BATCH_002", retrieved.getBatchId());
        assertEquals(0.92, retrieved.getQualityScore(), 0.001);
    }
    
    @Test
    void testGetShipmentsByBatchId() {
        // Given - Create multiple shipments for same batch
        ShipmentRecord record1 = new ShipmentRecord();
        record1.setShipmentId("SHIP_003A");
        record1.setBatchId("BATCH_003");
        record1.setFromParty("farmer_003");
        record1.setToParty("warehouse_003");
        record1.setStatus("CREATED");
        record1.setQualityScore(0.88);
        
        ShipmentRecord record2 = new ShipmentRecord();
        record2.setShipmentId("SHIP_003B");
        record2.setBatchId("BATCH_003");
        record2.setFromParty("warehouse_003");
        record2.setToParty("retailer_003");
        record2.setStatus("DELIVERED");
        record2.setQualityScore(0.87);
        
        service.recordShipment(record1);
        service.recordShipment(record2);
        
        // When
        List<ShipmentRecord> records = service.getShipmentsByBatchId("BATCH_003");
        
        // Then
        assertNotNull(records);
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> "SHIP_003A".equals(r.getShipmentId())));
        assertTrue(records.stream().anyMatch(r -> "SHIP_003B".equals(r.getShipmentId())));
    }
    
    @Test
    void testGetAllShipments() {
        // Given
        service.recordShipment(createTestShipment("SHIP_004A", "BATCH_004A"));
        service.recordShipment(createTestShipment("SHIP_004B", "BATCH_004B"));
        service.recordShipment(createTestShipment("SHIP_004C", "BATCH_004C"));
        
        // When
        List<ShipmentRecord> allShipments = service.getAllShipments();
        
        // Then
        assertNotNull(allShipments);
        assertEquals(3, allShipments.size());
    }
    
    @Test
    void testVerifyRecordIntegrity() {
        // Given
        ShipmentRecord record = createTestShipment("SHIP_005", "BATCH_005");
        ShipmentRecord recorded = service.recordShipment(record);
        
        // When
        boolean isValid = service.verifyRecordIntegrity(recorded);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    void testVerifyRecordIntegrityTampered() {
        // Given
        ShipmentRecord record = createTestShipment("SHIP_006", "BATCH_006");
        ShipmentRecord recorded = service.recordShipment(record);
        
        // When - Tamper with the record
        recorded.setQualityScore(0.99);
        boolean isValid = service.verifyRecordIntegrity(recorded);
        
        // Then - Should fail integrity check
        assertFalse(isValid);
    }
    
    @Test
    void testRecordNullShipment() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            service.recordShipment(null);
        });
    }
    
    @Test
    void testGetShipmentNullId() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            service.getShipmentByLedgerId(null);
        });
    }
    
    @Test
    void testGetShipmentEmptyId() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            service.getShipmentByLedgerId("");
        });
    }
    
    @Test
    void testGetShipmentNotFound() {
        // When
        ShipmentRecord result = service.getShipmentByLedgerId("NON_EXISTENT_ID");
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testGetRecordCount() {
        // Given
        service.recordShipment(createTestShipment("SHIP_007A", "BATCH_007"));
        service.recordShipment(createTestShipment("SHIP_007B", "BATCH_007"));
        service.recordShipment(createTestShipment("SHIP_007C", "BATCH_007"));
        
        // When
        int count = service.getRecordCount();
        
        // Then
        assertEquals(3, count);
    }
    
    @Test
    void testImmutability() {
        // Given
        ShipmentRecord record1 = createTestShipment("SHIP_008", "BATCH_008");
        service.recordShipment(record1);
        
        // When - Try to record another shipment (append-only)
        ShipmentRecord record2 = createTestShipment("SHIP_009", "BATCH_009");
        service.recordShipment(record2);
        
        // Then - Both should be retrievable
        List<ShipmentRecord> allRecords = service.getAllShipments();
        assertEquals(2, allRecords.size());
        
        // Verify first record is unchanged
        ShipmentRecord retrieved1 = service.getShipmentsByBatchId("BATCH_008").get(0);
        assertEquals("SHIP_008", retrieved1.getShipmentId());
    }
    
    private ShipmentRecord createTestShipment(String shipmentId, String batchId) {
        ShipmentRecord record = new ShipmentRecord();
        record.setShipmentId(shipmentId);
        record.setBatchId(batchId);
        record.setFromParty("farmer_test");
        record.setToParty("warehouse_test");
        record.setStatus("TEST");
        record.setQualityScore(0.80);
        return record;
    }
}
