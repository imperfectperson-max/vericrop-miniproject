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
