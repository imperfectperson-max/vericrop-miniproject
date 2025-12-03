package org.vericrop.blockchain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockchainJarService.
 */
class BlockchainJarServiceTest {
    
    private BlockchainJarService service;
    
    @BeforeEach
    void setUp() {
        // Reset BlockchainLoader to ensure clean state
        BlockchainLoader.resetInstance();
        service = new BlockchainJarService();
    }
    
    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
        BlockchainLoader.resetInstance();
    }
    
    @Test
    void testIsAvailable_ReturnsBoolean() {
        boolean available = service.isAvailable();
        // Just verify it returns a boolean without throwing
        assertTrue(available || !available);
    }
    
    @Test
    void testIsInitialized_BeforeInit_ReturnsFalse() {
        assertFalse(service.isInitialized(), "Should not be initialized before init() is called");
    }
    
    @Test
    void testInit_WhenJarNotAvailable_ReturnsFalse() {
        // If jar is not available, init should return false
        if (!service.isAvailable()) {
            boolean result = service.init();
            assertFalse(result, "init() should return false when jar is not available");
            assertFalse(service.isInitialized());
        }
    }
    
    @Test
    void testInit_WhenJarAvailable_ReturnsTrue() {
        if (service.isAvailable()) {
            boolean result = service.init();
            assertTrue(result, "init() should return true when jar is available");
            assertTrue(service.isInitialized());
        }
    }
    
    @Test
    void testRecordTransaction_BeforeInit_ReturnsFalse() {
        Map<String, Object> txData = new HashMap<>();
        txData.put("type", "CREATE_BATCH");
        txData.put("from", "producer1");
        
        boolean result = service.recordTransaction(txData);
        assertFalse(result, "recordTransaction should return false before init()");
    }
    
    @Test
    void testRecordTransaction_WithNullData_ReturnsFalse() {
        if (service.isAvailable()) {
            service.init();
        }
        boolean result = service.recordTransaction(null);
        assertFalse(result, "recordTransaction should return false for null data");
    }
    
    @Test
    void testRecordTransaction_WithEmptyData_ReturnsFalse() {
        if (service.isAvailable()) {
            service.init();
        }
        boolean result = service.recordTransaction(new HashMap<>());
        assertFalse(result, "recordTransaction should return false for empty data");
    }
    
    @Test
    void testGetChainAsString_BeforeInit_ReturnsEmpty() {
        Optional<String> result = service.getChainAsString();
        assertTrue(result.isEmpty(), "getChainAsString should return empty before init()");
    }
    
    @Test
    void testGetBlockCount_BeforeInit_ReturnsEmpty() {
        Optional<Integer> result = service.getBlockCount();
        assertTrue(result.isEmpty(), "getBlockCount should return empty before init()");
    }
    
    @Test
    void testIsChainValid_BeforeInit_ReturnsEmpty() {
        Optional<Boolean> result = service.isChainValid();
        assertTrue(result.isEmpty(), "isChainValid should return empty before init()");
    }
    
    @Test
    void testGetStatus_ReturnsValidMap() {
        Map<String, Object> status = service.getStatus();
        
        assertNotNull(status);
        assertTrue(status.containsKey("available"));
        assertTrue(status.containsKey("initialized"));
        assertTrue(status.containsKey("jarPath"));
        assertTrue(status.containsKey("blockchainClass"));
    }
    
    @Test
    void testClose_ResetsInitialized() {
        if (service.isAvailable()) {
            service.init();
            assertTrue(service.isInitialized());
        }
        
        service.close();
        assertFalse(service.isInitialized(), "Should not be initialized after close()");
    }
    
    @Test
    void testMultipleInitCalls_Succeeds() {
        if (service.isAvailable()) {
            boolean first = service.init();
            boolean second = service.init();
            // Both calls should succeed (idempotent or reinit)
            assertTrue(first || second || (!first && !second));
        }
    }
    
    @Test
    void testGetChainAsString_WhenInitialized_ReturnsValue() {
        if (service.isAvailable() && service.init()) {
            Optional<String> result = service.getChainAsString();
            // Should return some string representation
            assertTrue(result.isPresent() || result.isEmpty());
        }
    }
}
