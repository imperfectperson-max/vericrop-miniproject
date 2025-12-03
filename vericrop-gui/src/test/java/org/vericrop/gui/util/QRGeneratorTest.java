package org.vericrop.gui.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QRGenerator
 */
class QRGeneratorTest {
    
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
    void testGenerateProductQR() throws Exception {
        String productId = "BATCH-001";
        String farmerId = "farmer123";
        
        Path qrPath = QRGenerator.generateProductQR(productId, farmerId);
        
        assertNotNull(qrPath);
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        assertTrue(qrPath.toString().endsWith(".png"), "QR code should be PNG format");
        assertTrue(Files.size(qrPath) > 0, "QR code file should not be empty");
        
        // Verify QR code is scannable
        String decoded = decodeQRCode(qrPath);
        assertNotNull(decoded, "QR code should be decodable");
        assertTrue(decoded.contains(productId), "QR code should contain product ID");
        assertTrue(decoded.contains(farmerId), "QR code should contain farmer ID");
        assertTrue(decoded.contains("\"type\":\"product\""), "QR code should have product type");
    }
    
    @Test
    void testGenerateShipmentQR() throws Exception {
        String shipmentId = "SHIP-001";
        String origin = "Farm A";
        String destination = "Market B";
        
        Path qrPath = QRGenerator.generateShipmentQR(shipmentId, origin, destination);
        
        assertNotNull(qrPath);
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        // Verify QR code is scannable
        String decoded = decodeQRCode(qrPath);
        assertNotNull(decoded, "QR code should be decodable");
        assertTrue(decoded.contains(shipmentId), "QR code should contain shipment ID");
        assertTrue(decoded.contains(origin), "QR code should contain origin");
        assertTrue(decoded.contains(destination), "QR code should contain destination");
        assertTrue(decoded.contains("\"type\":\"shipment\""), "QR code should have shipment type");
    }
    
    @Test
    void testGenerateQRCodeWithCustomSize() throws Exception {
        String payload = "Custom test payload";
        String fileName = "test_custom_size.png";
        int customSize = 500;
        
        Path qrPath = QRGenerator.generateQRCode(payload, fileName, customSize);
        
        assertNotNull(qrPath);
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        // Verify image dimensions
        BufferedImage image = ImageIO.read(qrPath.toFile());
        assertEquals(customSize, image.getWidth(), "QR code width should match requested size");
        assertEquals(customSize, image.getHeight(), "QR code height should match requested size");
        
        // Verify QR code is scannable
        String decoded = decodeQRCode(qrPath);
        assertEquals(payload, decoded, "Decoded content should match original payload");
    }
    
    @Test
    void testQROutputDirectory() {
        Path qrDir = QRGenerator.getQROutputDirectory();
        assertNotNull(qrDir);
        assertTrue(qrDir.isAbsolute(), "QR output directory should be absolute path");
    }
    
    @Test
    void testGenerateProductId() {
        String id1 = QRGenerator.generateProductId();
        String id2 = QRGenerator.generateProductId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Generated IDs should be unique");
        assertTrue(id1.contains("-"), "Product ID should be UUID format");
    }
    
    // ========== Tests for versioned batch QR with quality ==========
    
    @Test
    void testGenerateBatchQR_WithQuality() throws Exception {
        String batchId = "BATCH_Q001";
        String farmerId = "farmer_quality";
        String quality = "PRIME";
        
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
        
        assertNotNull(qrPath, "QR code path should not be null");
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        assertTrue(qrPath.toString().endsWith(".png"), "QR code should be PNG format");
        assertTrue(qrPath.toString().contains("batch_"), "File name should start with batch_");
        
        // Verify QR code is scannable and contains expected fields
        String decoded = decodeQRCode(qrPath);
        assertNotNull(decoded, "QR code should be decodable");
        assertTrue(decoded.contains("\"type\":\"vericrop-batch\""), "Should have vericrop-batch type");
        assertTrue(decoded.contains("\"version\":1"), "Should have version 1");
        assertTrue(decoded.contains("\"batchId\":\"" + batchId + "\""), "Should contain batch ID");
        assertTrue(decoded.contains("\"quality\":\"" + quality + "\""), "Should contain quality");
        assertTrue(decoded.contains("\"seed\":\"" + farmerId + "\""), "Should contain farmer ID as seed");
        assertTrue(decoded.contains("\"timestamp\":"), "Should contain timestamp");
    }
    
    @Test
    void testGenerateBatchQR_WithNullQuality() throws Exception {
        String batchId = "BATCH_NOQAL";
        String farmerId = "farmer_noqal";
        
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, null);
        
        assertNotNull(qrPath, "QR code path should not be null");
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        // Verify QR code is scannable
        String decoded = decodeQRCode(qrPath);
        assertNotNull(decoded, "QR code should be decodable");
        assertTrue(decoded.contains("\"batchId\":\"" + batchId + "\""), "Should contain batch ID");
        assertFalse(decoded.contains("\"quality\":"), "Should NOT contain quality field when null");
    }
    
    @Test
    void testGenerateBatchQR_WithEmptyQuality() throws Exception {
        String batchId = "BATCH_EMPTYQAL";
        String farmerId = "farmer_emptyqal";
        
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, "   ");
        
        assertNotNull(qrPath, "QR code path should not be null");
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        // Verify QR code is scannable
        String decoded = decodeQRCode(qrPath);
        assertNotNull(decoded, "QR code should be decodable");
        assertTrue(decoded.contains("\"batchId\":\"" + batchId + "\""), "Should contain batch ID");
        assertFalse(decoded.contains("\"quality\":"), "Should NOT contain quality field when empty");
    }
    
    @Test
    void testGenerateBatchQR_AllQualityLevels() throws Exception {
        String[] qualityLevels = {"PRIME", "STANDARD", "SUB-STANDARD"};
        
        for (String quality : qualityLevels) {
            String batchId = "BATCH_" + quality.replace("-", "_");
            String farmerId = "farmer_" + quality.toLowerCase();
            
            Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
            
            assertNotNull(qrPath, "QR code path should not be null for " + quality);
            assertTrue(Files.exists(qrPath), "QR code file should exist for " + quality);
            
            String decoded = decodeQRCode(qrPath);
            assertTrue(decoded.contains("\"quality\":\"" + quality + "\""), 
                "Should contain correct quality level: " + quality);
        }
    }
    
    @Test
    void testGenerateBatchQR_CustomSize() throws Exception {
        String batchId = "BATCH_SIZE";
        String farmerId = "farmer_size";
        String quality = "PRIME";
        int customSize = 400;
        
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality, customSize);
        
        assertNotNull(qrPath, "QR code path should not be null");
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        // Verify image dimensions
        BufferedImage image = ImageIO.read(qrPath.toFile());
        assertEquals(customSize, image.getWidth(), "QR code width should match requested size");
        assertEquals(customSize, image.getHeight(), "QR code height should match requested size");
    }
    
    @Test
    void testGenerateBatchQR_UTF8Encoding() throws Exception {
        // Test with UTF-8 characters
        String batchId = "BATCH_UTF8";
        String farmerId = "Farmer Café";
        String quality = "PRIME";
        
        Path qrPath = QRGenerator.generateBatchQR(batchId, farmerId, quality);
        
        assertNotNull(qrPath, "QR code path should not be null");
        assertTrue(Files.exists(qrPath), "QR code file should exist");
        
        String decoded = decodeQRCode(qrPath);
        assertTrue(decoded.contains("Caf"), "Should properly encode UTF-8 characters");
    }
    
    @Test
    void testQRFormatVersion() {
        assertEquals(1, QRGenerator.QR_FORMAT_VERSION, "QR format version should be 1");
    }
    
    @Test
    void testTypeConstants() {
        assertEquals("vericrop-batch", QRGenerator.TYPE_VERICROP_BATCH, "TYPE_VERICROP_BATCH should match");
        assertEquals("product", QRGenerator.TYPE_PRODUCT, "TYPE_PRODUCT should match");
    }
    
    /**
     * Helper method to decode a QR code from an image file
     */
    private String decodeQRCode(Path imagePath) throws Exception {
        BufferedImage image = ImageIO.read(imagePath.toFile());
        BinaryBitmap bitmap = new BinaryBitmap(
            new HybridBinarizer(
                new BufferedImageLuminanceSource(image)
            )
        );
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
