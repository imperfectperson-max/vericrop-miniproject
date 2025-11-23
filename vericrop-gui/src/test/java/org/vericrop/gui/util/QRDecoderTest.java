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
}
