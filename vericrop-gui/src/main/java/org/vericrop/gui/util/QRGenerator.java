package org.vericrop.gui.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for generating QR codes as scannable RGB PNG images.
 * QR codes contain product/shipment information that can be scanned by consumers or transporters.
 */
public class QRGenerator {
    private static final Logger logger = LoggerFactory.getLogger(QRGenerator.class);
    private static final String QR_OUTPUT_DIR = "generated_qr";
    private static final int DEFAULT_QR_SIZE = 300;
    
    /**
     * Generate a QR code for a product with the given ID.
     * 
     * @param productId The unique product/batch ID
     * @param farmerId The farmer ID who created the product
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateProductQR(String productId, String farmerId) throws IOException, WriterException {
        return generateProductQR(productId, farmerId, DEFAULT_QR_SIZE);
    }
    
    /**
     * Generate a QR code for a product with the given ID and custom size.
     * 
     * @param productId The unique product/batch ID
     * @param farmerId The farmer ID who created the product
     * @param size The width and height of the QR code in pixels
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateProductQR(String productId, String farmerId, int size) throws IOException, WriterException {
        // Create payload in JSON-like format that can be scanned
        String payload = String.format(
            "{\"type\":\"product\",\"id\":\"%s\",\"farmer\":\"%s\",\"verifyUrl\":\"https://vericrop.app/verify?id=%s\"}",
            productId, farmerId, productId
        );
        
        String fileName = String.format("product_%s.png", productId);
        return generateQRCode(payload, fileName, size);
    }
    
    /**
     * Generate a QR code for a shipment with the given token.
     * 
     * @param shipmentId The unique shipment ID
     * @param origin Origin location
     * @param destination Destination location
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateShipmentQR(String shipmentId, String origin, String destination) throws IOException, WriterException {
        return generateShipmentQR(shipmentId, origin, destination, DEFAULT_QR_SIZE);
    }
    
    /**
     * Generate a QR code for a shipment with the given token and custom size.
     * 
     * @param shipmentId The unique shipment ID
     * @param origin Origin location
     * @param destination Destination location
     * @param size The width and height of the QR code in pixels
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateShipmentQR(String shipmentId, String origin, String destination, int size) throws IOException, WriterException {
        // Create payload with shipment details
        String payload = String.format(
            "{\"type\":\"shipment\",\"id\":\"%s\",\"origin\":\"%s\",\"destination\":\"%s\",\"trackUrl\":\"https://vericrop.app/track?id=%s\"}",
            shipmentId, origin, destination, shipmentId
        );
        
        String fileName = String.format("shipment_%s.png", shipmentId);
        return generateQRCode(payload, fileName, size);
    }
    
    /**
     * Generate a QR code with custom payload and filename.
     * 
     * @param payload The data to encode in the QR code
     * @param fileName The output filename (should end with .png)
     * @param size The width and height of the QR code in pixels
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateQRCode(String payload, String fileName, int size) throws IOException, WriterException {
        // Ensure output directory exists
        Path outputDir = Paths.get(QR_OUTPUT_DIR);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            logger.info("Created QR output directory: {}", outputDir.toAbsolutePath());
        }
        
        // Set QR code encoding hints for better quality
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1); // Quiet zone margin
        
        // Generate QR code
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
        
        // Write to file as PNG (RGB format)
        Path outputPath = outputDir.resolve(fileName);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", outputPath);
        
        logger.info("Generated QR code: {} ({}x{} px, {} bytes)", 
            outputPath.toAbsolutePath(), size, size, payload.length());
        
        return outputPath;
    }
    
    /**
     * Generate a random UUID-based product ID for testing.
     * 
     * @return A new UUID string
     */
    public static String generateProductId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Get the absolute path of the QR output directory.
     * 
     * @return Path to the QR output directory
     */
    public static Path getQROutputDirectory() {
        return Paths.get(QR_OUTPUT_DIR).toAbsolutePath();
    }
}
