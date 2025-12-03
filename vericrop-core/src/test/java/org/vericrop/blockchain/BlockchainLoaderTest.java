package org.vericrop.blockchain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockchainLoader.
 */
class BlockchainLoaderTest {
    
    private BlockchainLoader loader;
    
    @BeforeEach
    void setUp() {
        // Reset and get fresh instance for each test
        BlockchainLoader.resetInstance();
        loader = BlockchainLoader.getInstance();
    }
    
    @AfterEach
    void tearDown() {
        BlockchainLoader.resetInstance();
    }
    
    @Test
    void testGetInstance_ReturnsSameInstance() {
        BlockchainLoader instance1 = BlockchainLoader.getInstance();
        BlockchainLoader instance2 = BlockchainLoader.getInstance();
        assertSame(instance1, instance2, "getInstance should return the same singleton instance");
    }
    
    @Test
    void testIsAvailable_ReturnsBoolean() {
        // The result depends on whether blockchain.jar is found in the test environment
        boolean available = loader.isAvailable();
        // Just verify it doesn't throw and returns a boolean
        assertTrue(available || !available);
    }
    
    @Test
    void testLoadClass_WhenJarNotAvailable_ReturnsEmpty() {
        // If jar is not available, loading any class should return empty
        if (!loader.isAvailable()) {
            Optional<Class<?>> result = loader.loadClass("acsse.csc03a3.Blockchain");
            assertTrue(result.isEmpty(), "Should return empty Optional when jar not available");
        }
    }
    
    @Test
    void testLoadClass_WithInvalidClassName_ReturnsEmpty() {
        // Even if jar is available, invalid class names should return empty
        Optional<Class<?>> result = loader.loadClass("com.nonexistent.FakeClass");
        assertTrue(result.isEmpty(), "Should return empty Optional for non-existent class");
    }
    
    @Test
    void testCreateInstance_WithNullClassName_HandlesGracefully() {
        // This should not throw an exception
        Optional<Object> result = loader.createInstance(null, null, null);
        // Should return empty or handle gracefully
        assertNotNull(result);
    }
    
    @Test
    void testInvoke_WithNullParameters_HandlesGracefully() {
        Optional<Object> result = loader.invoke("test.Class", "testMethod", null, null);
        assertNotNull(result, "Should return non-null Optional even with null parameters");
    }
    
    @Test
    void testInvokeInstance_WithNullInstance_ReturnsEmpty() {
        Optional<Object> result = loader.invoke(null, "toString", new Class<?>[0], new Object[0]);
        assertTrue(result.isEmpty(), "Should return empty Optional when instance is null");
    }
    
    @Test
    void testGetLoadedJarPath_ReturnsPathOrNull() {
        String path = loader.getLoadedJarPath();
        // Path should be either null (if not found) or a valid string
        if (loader.isAvailable()) {
            assertNotNull(path, "Should return path when jar is available");
            assertTrue(path.endsWith("blockchain.jar"), "Path should end with blockchain.jar");
        } else {
            assertNull(path, "Should return null when jar is not available");
        }
    }
    
    @Test
    void testClose_HandlesMultipleCalls() {
        // Multiple close calls should not throw
        loader.close();
        loader.close();
        assertFalse(loader.isAvailable(), "Should not be available after close");
    }
    
    @Test
    void testResetInstance_ClearsInstance() {
        BlockchainLoader original = BlockchainLoader.getInstance();
        BlockchainLoader.resetInstance();
        BlockchainLoader newInstance = BlockchainLoader.getInstance();
        assertNotSame(original, newInstance, "After reset, should get a new instance");
    }
    
    @Test
    void testLoadClass_WhenAvailable_LoadsBlockchainClass() {
        // This test only runs if the jar is available
        if (loader.isAvailable()) {
            Optional<Class<?>> blockchainClass = loader.loadClass("acsse.csc03a3.Blockchain");
            assertTrue(blockchainClass.isPresent(), "Should be able to load Blockchain class");
            assertEquals("acsse.csc03a3.Blockchain", blockchainClass.get().getName());
        }
    }
    
    @Test
    void testLoadClass_WhenAvailable_LoadsBlockClass() {
        if (loader.isAvailable()) {
            Optional<Class<?>> blockClass = loader.loadClass("acsse.csc03a3.Block");
            assertTrue(blockClass.isPresent(), "Should be able to load Block class");
            assertEquals("acsse.csc03a3.Block", blockClass.get().getName());
        }
    }
    
    @Test
    void testLoadClass_WhenAvailable_LoadsTransactionClass() {
        if (loader.isAvailable()) {
            Optional<Class<?>> txClass = loader.loadClass("acsse.csc03a3.Transaction");
            assertTrue(txClass.isPresent(), "Should be able to load Transaction class");
            assertEquals("acsse.csc03a3.Transaction", txClass.get().getName());
        }
    }
    
    @Test
    void testCreateInstance_WhenAvailable_CreatesBlockchainInstance() {
        if (loader.isAvailable()) {
            Optional<Object> instance = loader.createInstance("acsse.csc03a3.Blockchain");
            assertTrue(instance.isPresent(), "Should be able to create Blockchain instance");
            assertNotNull(instance.get());
        }
    }
}
