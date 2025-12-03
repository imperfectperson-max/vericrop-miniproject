package org.vericrop.gui.util;

import com.google.zxing.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QRDecoder
 */
class QRDecoderTest {
    
    @AfterEach
    void cleanup() throws IOException {
        // Clean up generated QR codes after each test
        Path qrDir = QRGenerator.getQROutputDirectory();
        if (Files.exists(qrDir)) {
            Files.walk(qrDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".png"))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
    
    @Test
    void testDecodeQRCode_ProductQR() throws Exception {
        // Generate a QR code for testing
        String productId = "BATCH_A2386";
        String farmerId = "farmer123";
        Path qrPath = QRGenerator.generateProductQR(productId, farmerId);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        assertNotNull(decoded, "Decoded content should not be null");
        assertTrue(decoded.contains(productId), "Decoded content should contain product ID");
        assertTrue(decoded.contains(farmerId), "Decoded content should contain farmer ID");
        assertTrue(decoded.contains("\"type\":\"product\""), "Decoded content should have product type");
    }
    
    @Test
    void testDecodeQRCode_ShipmentQR() throws Exception {
        // Generate a shipment QR code for testing
        String shipmentId = "SHIP-001";
        String origin = "Farm A";
        String destination = "Market B";
        Path qrPath = QRGenerator.generateShipmentQR(shipmentId, origin, destination);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        assertNotNull(decoded, "Decoded content should not be null");
        assertTrue(decoded.contains(shipmentId), "Decoded content should contain shipment ID");
        assertTrue(decoded.contains(origin), "Decoded content should contain origin");
        assertTrue(decoded.contains(destination), "Decoded content should contain destination");
    }
    
    @Test
    void testDecodeQRCode_NonExistentFile() {
        Path nonExistentPath = Path.of("non_existent_qr.png");
        
        assertThrows(IOException.class, () -> {
            QRDecoder.decodeQRCode(nonExistentPath);
        }, "Should throw IOException for non-existent file");
    }
    
    @Test
    void testDecodeQRCode_NullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            QRDecoder.decodeQRCode((Path) null);
        }, "Should throw IllegalArgumentException for null path");
    }
    
    @Test
    void testExtractBatchId_FromJSON() {
        String jsonContent = "{\"type\":\"product\",\"id\":\"BATCH_A2386\",\"farmer\":\"farmer123\"}";
        
        String batchId = QRDecoder.extractBatchId(jsonContent);
        
        assertEquals("BATCH_A2386", batchId, "Should extract batch ID from JSON");
    }
    
    @Test
    void testExtractBatchId_PlainText() {
        String plainText = "BATCH_A2385";
        
        String batchId = QRDecoder.extractBatchId(plainText);
        
        assertEquals("BATCH_A2385", batchId, "Should return plain text as batch ID");
    }
    
    @Test
    void testExtractBatchId_EmptyString() {
        String empty = "";
        
        String batchId = QRDecoder.extractBatchId(empty);
        
        assertNull(batchId, "Should return null for empty string");
    }
    
    @Test
    void testExtractBatchId_Null() {
        String batchId = QRDecoder.extractBatchId(null);
        
        assertNull(batchId, "Should return null for null input");
    }
    
    @Test
    void testDecodeAndExtract_Integration() throws Exception {
        // Generate a product QR code
        String productId = "BATCH_A2384";
        String farmerId = "farmer456";
        Path qrPath = QRGenerator.generateProductQR(productId, farmerId);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        // Extract batch ID
        String extractedId = QRDecoder.extractBatchId(decoded);
        
        assertEquals(productId, extractedId, "Extracted ID should match original product ID");
    }
    
    // ========== Tests for versioned batch QR with quality ==========
    
    @Test
    void testDecodeQRCode_BatchQRWithQuality() throws Exception {
        // Generate a versioned batch QR code with quality
        String batchId = "BATCH_Q001";
        String farmerId = "farmer_quality";
        String quality = "PRIME";
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        assertNotNull(decoded, "Decoded content should not be null");
        assertTrue(decoded.contains(batchId), "Decoded content should contain batch ID");
        assertTrue(decoded.contains(quality), "Decoded content should contain quality");
        assertTrue(decoded.contains("\"type\":\"vericrop-batch\""), "Decoded content should have vericrop-batch type");
    }
    
    @Test
    void testParsePayload_VericropBatchFormat() {
        // Test the new versioned batch format with quality
        String jsonContent = "{\"type\":\"vericrop-batch\",\"version\":1,\"batchId\":\"BATCH_V1\",\"seed\":\"farm123\",\"quality\":\"PRIME\",\"timestamp\":\"2024-01-01T12:00:00Z\"}";
        
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(jsonContent);
        
        assertNotNull(payload, "Payload should not be null");
        assertEquals("vericrop-batch", payload.getType(), "Type should be vericrop-batch");
        assertEquals(1, payload.getVersion(), "Version should be 1");
        assertEquals("BATCH_V1", payload.getBatchId(), "Batch ID should match");
        assertEquals("PRIME", payload.getQuality(), "Quality should be PRIME");
        assertEquals("farm123", payload.getFarmerId(), "Farmer ID should match");
        assertTrue(payload.isVersionedBatch(), "Should be identified as versioned batch");
        assertFalse(payload.isLegacyProduct(), "Should not be identified as legacy product");
    }
    
    @Test
    void testParsePayload_LegacyProductFormat() {
        // Test backward compatibility with legacy product format
        String jsonContent = "{\"type\":\"product\",\"id\":\"BATCH_LEGACY\",\"farmer\":\"old_farmer\"}";
        
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(jsonContent);
        
        assertNotNull(payload, "Payload should not be null");
        assertEquals("product", payload.getType(), "Type should be product");
        assertEquals("BATCH_LEGACY", payload.getBatchId(), "Batch ID should match");
        assertNull(payload.getQuality(), "Quality should be null for legacy format");
        assertEquals("old_farmer", payload.getFarmerId(), "Farmer ID should match");
        assertFalse(payload.isVersionedBatch(), "Should not be identified as versioned batch");
        assertTrue(payload.isLegacyProduct(), "Should be identified as legacy product");
    }
    
    @Test
    void testParsePayload_PlainText() {
        // Test plain text batch ID
        String plainText = "BATCH_PLAIN";
        
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(plainText);
        
        assertNotNull(payload, "Payload should not be null");
        assertNull(payload.getType(), "Type should be null for plain text");
        assertEquals("BATCH_PLAIN", payload.getBatchId(), "Batch ID should match the plain text");
        assertNull(payload.getQuality(), "Quality should be null for plain text");
    }
    
    @Test
    void testParsePayload_Null() {
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(null);
        assertNull(payload, "Payload should be null for null input");
    }
    
    @Test
    void testParsePayload_EmptyString() {
        QRDecoder.QRPayload payload = QRDecoder.parsePayload("");
        assertNull(payload, "Payload should be null for empty string");
    }
    
    @Test
    void testExtractQuality_FromVersionedBatch() {
        String jsonContent = "{\"type\":\"vericrop-batch\",\"version\":1,\"batchId\":\"BATCH_Q2\",\"quality\":\"STANDARD\"}";
        
        String quality = QRDecoder.extractQuality(jsonContent);
        
        assertEquals("STANDARD", quality, "Should extract quality from versioned batch");
    }
    
    @Test
    void testExtractQuality_FromLegacyProduct() {
        String jsonContent = "{\"type\":\"product\",\"id\":\"BATCH_LEGACY\",\"farmer\":\"farm1\"}";
        
        String quality = QRDecoder.extractQuality(jsonContent);
        
        assertNull(quality, "Should return null for legacy format without quality");
    }
    
    @Test
    void testExtractQualityOrUnknown_WithQuality() {
        String jsonContent = "{\"type\":\"vericrop-batch\",\"version\":1,\"batchId\":\"B1\",\"quality\":\"SUB-STANDARD\"}";
        
        String quality = QRDecoder.extractQualityOrUnknown(jsonContent);
        
        assertEquals("SUB-STANDARD", quality, "Should return the quality value");
    }
    
    @Test
    void testExtractQualityOrUnknown_WithoutQuality() {
        String jsonContent = "{\"type\":\"product\",\"id\":\"B2\"}";
        
        String quality = QRDecoder.extractQualityOrUnknown(jsonContent);
        
        assertEquals("Unknown", quality, "Should return 'Unknown' when quality is missing");
    }
    
    @Test
    void testExtractQualityOrUnknown_Null() {
        String quality = QRDecoder.extractQualityOrUnknown(null);
        
        assertEquals("Unknown", quality, "Should return 'Unknown' for null input");
    }
    
    @Test
    void testQRPayload_GetQualityOrUnknown() {
        // Test with quality
        QRDecoder.QRPayload payloadWithQuality = new QRDecoder.QRPayload(
            "vericrop-batch", 1, "B1", "PRIME", "farmer", "2024-01-01", "raw");
        assertEquals("PRIME", payloadWithQuality.getQualityOrUnknown(), "Should return actual quality");
        
        // Test without quality
        QRDecoder.QRPayload payloadWithoutQuality = new QRDecoder.QRPayload(
            "product", 0, "B2", null, "farmer", null, "raw");
        assertEquals("Unknown", payloadWithoutQuality.getQualityOrUnknown(), "Should return 'Unknown' for null quality");
        
        // Test with empty quality
        QRDecoder.QRPayload payloadWithEmptyQuality = new QRDecoder.QRPayload(
            "product", 0, "B3", "  ", "farmer", null, "raw");
        assertEquals("Unknown", payloadWithEmptyQuality.getQualityOrUnknown(), "Should return 'Unknown' for empty quality");
    }
    
    @Test
    void testDecodeAndExtract_BatchQRWithQuality_Integration() throws Exception {
        // Generate a versioned batch QR code with quality
        String batchId = "BATCH_INTEG";
        String farmerId = "farmer_integ";
        String quality = "PRIME";
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        // Parse the payload
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(decoded);
        
        assertNotNull(payload, "Payload should not be null");
        assertEquals(batchId, payload.getBatchId(), "Batch ID should match");
        assertEquals(quality, payload.getQuality(), "Quality should match");
        assertEquals(farmerId, payload.getFarmerId(), "Farmer ID should match");
        assertTrue(payload.isVersionedBatch(), "Should be versioned batch");
        
        // Also test extractBatchId for backward compatibility
        String extractedId = QRDecoder.extractBatchId(decoded);
        assertEquals(batchId, extractedId, "extractBatchId should still work with new format");
        
        // Test extractQuality
        String extractedQuality = QRDecoder.extractQuality(decoded);
        assertEquals(quality, extractedQuality, "extractQuality should return the quality");
    }
    
    @Test
    void testDecodeAndExtract_BatchQRWithNullQuality_Integration() throws Exception {
        // Generate a batch QR code without quality
        String batchId = "BATCH_NOQAL";
        String farmerId = "farmer_noqal";
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, null);
        
        // Decode the QR code
        String decoded = QRDecoder.decodeQRCode(qrPath);
        
        // Parse the payload
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(decoded);
        
        assertNotNull(payload, "Payload should not be null");
        assertEquals(batchId, payload.getBatchId(), "Batch ID should match");
        assertNull(payload.getQuality(), "Quality should be null");
        assertEquals("Unknown", payload.getQualityOrUnknown(), "getQualityOrUnknown should return 'Unknown'");
    }
    
    @Test
    void testParsePayload_SpecialCharactersInBatchId() throws Exception {
        // Test batch ID with special characters
        String batchId = "BATCH-ABC_123";
        String farmerId = "farm/test";
        String quality = "PRIME";
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
        
        String decoded = QRDecoder.decodeQRCode(qrPath);
        QRDecoder.QRPayload payload = QRDecoder.parsePayload(decoded);
        
        assertNotNull(payload, "Payload should not be null");
        assertEquals(batchId, payload.getBatchId(), "Batch ID with special chars should match");
        assertEquals(quality, payload.getQuality(), "Quality should match");
    }
}
