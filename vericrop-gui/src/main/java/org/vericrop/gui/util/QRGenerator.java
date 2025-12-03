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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
    
    /** Current version of the QR payload format */
    public static final int QR_FORMAT_VERSION = 1;
    
    /** Type identifier for batch QR codes */
    public static final String TYPE_VERICROP_BATCH = "vericrop-batch";
    
    /** Type identifier for legacy product QR codes */
    public static final String TYPE_PRODUCT = "product";
    
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
     * Generate a versioned QR code for a batch with quality information.
     * Uses a stable, versioned JSON format for forward compatibility.
     * 
     * <p>The QR payload format (v1):</p>
     * <pre>
     * {
     *   "type": "vericrop-batch",
     *   "version": 1,
     *   "batchId": "BATCH_123",
     *   "seed": "farmer_id",
     *   "quality": "PRIME",
     *   "timestamp": "2024-01-01T12:00:00Z"
     * }
     * </pre>
     * 
     * @param batchId The unique batch ID
     * @param farmerId The farmer/producer ID
     * @param quality The quality classification (e.g., "PRIME", "STANDARD", "SUB-STANDARD", or null for unknown)
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateBatchQR(String batchId, String farmerId, String quality) throws IOException, WriterException {
        return generateBatchQR(batchId, farmerId, quality, DEFAULT_QR_SIZE);
    }
    
    /**
     * Generate a versioned QR code for a batch with quality information and custom size.
     * 
     * @param batchId The unique batch ID
     * @param farmerId The farmer/producer ID
     * @param quality The quality classification (e.g., "PRIME", "STANDARD", "SUB-STANDARD", or null for unknown)
     * @param size The width and height of the QR code in pixels
     * @return Path to the generated QR code PNG file
     * @throws IOException if file creation fails
     * @throws WriterException if QR encoding fails
     */
    public static Path generateBatchQR(String batchId, String farmerId, String quality, int size) throws IOException, WriterException {
        // Build the versioned JSON payload
        String qualityValue = (quality != null && !quality.trim().isEmpty()) ? quality.trim() : null;
        String timestamp = Instant.now().toString();
        
        // Create payload with stable, versioned JSON format
        // Using explicit key order for consistency: type, version, batchId, seed, quality, timestamp
        StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append("{");
        payloadBuilder.append("\"type\":\"").append(TYPE_VERICROP_BATCH).append("\",");
        payloadBuilder.append("\"version\":").append(QR_FORMAT_VERSION).append(",");
        payloadBuilder.append("\"batchId\":\"").append(escapeJsonString(batchId)).append("\",");
        payloadBuilder.append("\"seed\":\"").append(escapeJsonString(farmerId != null ? farmerId : "unknown")).append("\",");
        if (qualityValue != null) {
            payloadBuilder.append("\"quality\":\"").append(escapeJsonString(qualityValue)).append("\",");
        }
        payloadBuilder.append("\"timestamp\":\"").append(timestamp).append("\"");
        payloadBuilder.append("}");
        
        String payload = payloadBuilder.toString();
        
        // Ensure payload is UTF-8 encoded
        byte[] utf8Bytes = payload.getBytes(StandardCharsets.UTF_8);
        String utf8Payload = new String(utf8Bytes, StandardCharsets.UTF_8);
        
        String fileName = String.format("batch_%s.png", batchId);
        
        logger.info("Generating batch QR: batchId={}, quality={}, payload length={} chars", 
            batchId, qualityValue != null ? qualityValue : "null", utf8Payload.length());
        
        return generateQRCode(utf8Payload, fileName, size);
    }
    
    /**
     * Escape special characters in a JSON string value.
     * Handles quotes, backslashes, and control characters.
     * 
     * @param value The string value to escape
     * @return The escaped string safe for JSON embedding
     */
    private static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        // Control characters as unicode escape
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
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
