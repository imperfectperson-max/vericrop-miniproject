package org.vericrop.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    
    @TempDir
    Path tempDir;
    
    private FileLedgerService service;
    private String testLedgerDir;
    
    @BeforeEach
    void setUp() {
        // Use JUnit 5 @TempDir for platform-independent temporary directory
        testLedgerDir = tempDir.resolve("test-ledger").toString();
        service = new FileLedgerService(testLedgerDir);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up is handled automatically by @TempDir
        // But we explicitly clean up the ledger file for good measure
        Path ledgerPath = service.getLedgerPath();
        if (Files.exists(ledgerPath)) {
            try {
                Files.delete(ledgerPath);
            } catch (IOException e) {
                // Ignore - @TempDir will clean up anyway
            }
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
    
    @Test
    void testChainIntegrity() {
        // Given - Create a chain of records
        service.recordShipment(createTestShipment("SHIP_CHAIN_001", "BATCH_CHAIN_001"));
        service.recordShipment(createTestShipment("SHIP_CHAIN_002", "BATCH_CHAIN_002"));
        service.recordShipment(createTestShipment("SHIP_CHAIN_003", "BATCH_CHAIN_003"));
        
        // When
        boolean isValid = service.verifyChainIntegrity();
        
        // Then
        assertTrue(isValid, "Chain integrity should be valid for unmodified ledger");
    }
    
    @Test
    void testEmptyLedgerChainIntegrity() {
        // Given - Empty ledger
        
        // When
        boolean isValid = service.verifyChainIntegrity();
        
        // Then
        assertTrue(isValid, "Empty ledger should be considered valid");
    }
    
    @Test
    void testChainHashingLinks() {
        // Given - Create multiple records
        ShipmentRecord record1 = service.recordShipment(createTestShipment("SHIP_LINK_001", "BATCH_LINK_001"));
        ShipmentRecord record2 = service.recordShipment(createTestShipment("SHIP_LINK_002", "BATCH_LINK_002"));
        ShipmentRecord record3 = service.recordShipment(createTestShipment("SHIP_LINK_003", "BATCH_LINK_003"));
        
        // Then - Each record should have a different hash (proving chain linkage)
        assertNotEquals(record1.getLedgerHash(), record2.getLedgerHash());
        assertNotEquals(record2.getLedgerHash(), record3.getLedgerHash());
        assertNotEquals(record1.getLedgerHash(), record3.getLedgerHash());
        
        // Verify all records have hashes
        assertNotNull(record1.getLedgerHash());
        assertNotNull(record2.getLedgerHash());
        assertNotNull(record3.getLedgerHash());
    }
    
    @Test
    void testConcurrentWritesWithFileLocking() throws Exception {
        // Given - Multiple threads trying to write simultaneously
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        // Use CountDownLatch to ensure all threads start at the same time for better concurrency testing
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.ConcurrentHashMap<Integer, String> errors = new java.util.concurrent.ConcurrentHashMap<>();
        
        // When - Spawn threads to write concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    // Wait for all threads to be ready before starting
                    startLatch.await();
                    
                    ShipmentRecord record = createTestShipment(
                        "SHIP_CONCURRENT_" + threadId,
                        "BATCH_CONCURRENT_" + threadId
                    );
                    service.recordShipment(record);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.put(threadId, "Interrupted: " + e.getMessage());
                } catch (Exception e) {
                    // Capture the full exception for debugging
                    errors.put(threadId, e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }
        
        // Release all threads at once for maximum concurrency
        startLatch.countDown();
        
        // Wait for all threads to complete with timeout
        boolean completed = doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(completed, "All threads should complete within timeout");
        
        // Print any errors for debugging
        if (!errors.isEmpty()) {
            System.err.println("Errors encountered during concurrent writes:");
            errors.forEach((id, error) -> System.err.println("  Thread " + id + ": " + error));
        }
        
        // Then - All records should be written successfully
        List<ShipmentRecord> allRecords = service.getAllShipments();
        assertEquals(threadCount, allRecords.size(), 
            String.format("All concurrent writes should succeed with file locking. Success count: %d, Records: %d, Errors: %s", 
                successCount.get(), allRecords.size(), errors.toString()));
        
        // Verify chain integrity - this is the critical test
        // If file locking works properly, the chain should remain valid
        assertTrue(service.verifyChainIntegrity(), 
            "Chain should remain valid after concurrent writes due to file locking");
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
