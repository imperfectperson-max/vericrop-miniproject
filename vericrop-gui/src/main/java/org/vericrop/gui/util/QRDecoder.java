package org.vericrop.gui.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for decoding QR codes from images using computer vision.
 * Supports decoding batch IDs, quality, and other information from QR code images.
 * 
 * <p>Supported QR payload formats:</p>
 * <ul>
 *   <li><b>vericrop-batch (v1)</b>: Versioned JSON with batchId, quality, timestamp</li>
 *   <li><b>product (legacy)</b>: JSON with id, farmer, verifyUrl</li>
 *   <li><b>Plain text</b>: Raw batch ID string</li>
 * </ul>
 */
public class QRDecoder {
    private static final Logger logger = LoggerFactory.getLogger(QRDecoder.class);
    
    /**
     * Result object containing decoded QR data including batch ID and quality.
     */
    public static class QRPayload {
        private final String type;
        private final int version;
        private final String batchId;
        private final String quality;
        private final String farmerId;
        private final String timestamp;
        private final String rawContent;
        
        public QRPayload(String type, int version, String batchId, String quality, 
                        String farmerId, String timestamp, String rawContent) {
            this.type = type;
            this.version = version;
            this.batchId = batchId;
            this.quality = quality;
            this.farmerId = farmerId;
            this.timestamp = timestamp;
            this.rawContent = rawContent;
        }
        
        public String getType() { return type; }
        public int getVersion() { return version; }
        public String getBatchId() { return batchId; }
        public String getQuality() { return quality; }
        public String getFarmerId() { return farmerId; }
        public String getTimestamp() { return timestamp; }
        public String getRawContent() { return rawContent; }
        
        /**
         * Returns the quality string, or "Unknown" if quality is null or empty.
         */
        public String getQualityOrUnknown() {
            return (quality != null && !quality.trim().isEmpty()) ? quality : "Unknown";
        }
        
        /**
         * Check if this is a versioned vericrop-batch QR code.
         */
        public boolean isVersionedBatch() {
            return QRGenerator.TYPE_VERICROP_BATCH.equals(type);
        }
        
        /**
         * Check if this is a legacy product QR code.
         */
        public boolean isLegacyProduct() {
            return QRGenerator.TYPE_PRODUCT.equals(type);
        }
        
        @Override
        public String toString() {
            return String.format("QRPayload{type='%s', version=%d, batchId='%s', quality='%s'}", 
                type, version, batchId, quality);
        }
    }
    
    /**
     * Decode a QR code from an image file.
     * 
     * @param imagePath Path to the image file containing the QR code
     * @return The decoded text from the QR code
     * @throws IOException if the image cannot be read
     * @throws NotFoundException if no QR code is found in the image
     */
    public static String decodeQRCode(Path imagePath) throws IOException, NotFoundException {
        if (imagePath == null) {
            throw new IllegalArgumentException("Image path cannot be null");
        }
        
        File imageFile = imagePath.toFile();
        if (!imageFile.exists()) {
            throw new IOException("Image file does not exist: " + imagePath);
        }
        
        return decodeQRCode(imageFile);
    }
    
    /**
     * Decode a QR code from an image file.
     * 
     * @param imageFile Image file containing the QR code
     * @return The decoded text from the QR code
     * @throws IOException if the image cannot be read
     * @throws NotFoundException if no QR code is found in the image
     */
    public static String decodeQRCode(File imageFile) throws IOException, NotFoundException {
        if (imageFile == null) {
            throw new IllegalArgumentException("Image file cannot be null");
        }
        
        logger.debug("Attempting to decode QR code from: {}", imageFile.getAbsolutePath());
        
        // Read the image
        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            throw new IOException("Failed to read image file: " + imageFile.getAbsolutePath());
        }
        
        // Create a luminance source from the image
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        
        // Create a binary bitmap
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        
        // Configure decoder hints for UTF-8 character set
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        
        // Decode the QR code
        try {
            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(bitmap, hints);
            String decodedText = result.getText();
            
            // Log only metadata to avoid exposing sensitive information
            logger.info("Successfully decoded QR code from {} (content length: {} characters)", 
                imageFile.getName(), decodedText.length());
            
            return decodedText;
        } catch (NotFoundException e) {
            logger.warn("No QR code found in image: {}", imageFile.getAbsolutePath());
            throw e;
        }
    }
    
    /**
     * Parse the QR code content into a structured QRPayload object.
     * Supports versioned vericrop-batch format, legacy product format, and plain text.
     * 
     * @param qrContent The decoded QR code content
     * @return QRPayload object with extracted fields, or null if content is null/empty
     */
    public static QRPayload parsePayload(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            logger.warn("QR content is null or empty, cannot parse payload");
            return null;
        }
        
        String content = qrContent.trim();
        logger.debug("Parsing QR payload: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);
        
        // Try to parse as JSON (starts with '{')
        if (content.startsWith("{")) {
            return parseJsonPayload(content);
        }
        
        // Fallback: treat as plain text batch ID
        logger.debug("QR content is not JSON, treating as plain text batch ID");
        return new QRPayload(null, 0, content, null, null, null, content);
    }
    
    /**
     * Parse JSON payload into QRPayload object.
     * Handles both vericrop-batch (v1) and legacy product formats.
     */
    private static QRPayload parseJsonPayload(String jsonContent) {
        try {
            // Extract type first to determine format
            String type = extractJsonField(jsonContent, "type");
            
            if (QRGenerator.TYPE_VERICROP_BATCH.equals(type)) {
                // Parse versioned vericrop-batch format
                int version = parseIntField(extractJsonField(jsonContent, "version"), 1);
                String batchId = extractJsonField(jsonContent, "batchId");
                String quality = extractJsonField(jsonContent, "quality");
                String seed = extractJsonField(jsonContent, "seed"); // farmerId in v1 format
                String timestamp = extractJsonField(jsonContent, "timestamp");
                
                logger.debug("Parsed vericrop-batch QR: batchId={}, quality={}, version={}", 
                    batchId, quality, version);
                
                return new QRPayload(type, version, batchId, quality, seed, timestamp, jsonContent);
                
            } else if (QRGenerator.TYPE_PRODUCT.equals(type)) {
                // Parse legacy product format
                String id = extractJsonField(jsonContent, "id");
                String farmer = extractJsonField(jsonContent, "farmer");
                
                // Legacy format doesn't have quality, so it will be null
                logger.debug("Parsed legacy product QR: id={}, farmer={}", id, farmer);
                
                return new QRPayload(type, 0, id, null, farmer, null, jsonContent);
                
            } else {
                // Unknown JSON format, try to extract id/batchId
                String batchId = extractJsonField(jsonContent, "batchId");
                if (batchId == null || batchId.isEmpty()) {
                    batchId = extractJsonField(jsonContent, "id");
                }
                String quality = extractJsonField(jsonContent, "quality");
                
                logger.debug("Parsed unknown JSON QR: batchId={}, quality={}", batchId, quality);
                
                return new QRPayload(type, 0, batchId, quality, null, null, jsonContent);
            }
            
        } catch (Exception e) {
            logger.error("Error parsing JSON QR payload: {}", e.getMessage());
            // Fallback: try to extract batch ID using legacy method
            String batchId = extractBatchId(jsonContent);
            return new QRPayload(null, 0, batchId, null, null, null, jsonContent);
        }
    }
    
    /**
     * Extract a string field value from JSON content using simple string parsing.
     * This avoids the need for a full JSON parser for simple field extraction.
     * 
     * @param json The JSON string
     * @param fieldName The field name to extract
     * @return The field value, or null if not found
     */
    private static String extractJsonField(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return null;
        }
        
        // Look for "fieldName": pattern
        String searchPattern = "\"" + fieldName + "\":";
        int fieldStart = json.indexOf(searchPattern);
        if (fieldStart < 0) {
            return null;
        }
        
        int valueStart = fieldStart + searchPattern.length();
        
        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) {
            return null;
        }
        
        char firstChar = json.charAt(valueStart);
        
        // Handle string value (quoted)
        if (firstChar == '"') {
            int stringStart = valueStart + 1;
            int stringEnd = findClosingQuote(json, stringStart);
            if (stringEnd > stringStart) {
                return unescapeJsonString(json.substring(stringStart, stringEnd));
            }
            return null;
        }
        
        // Handle number value (unquoted)
        if (Character.isDigit(firstChar) || firstChar == '-') {
            int numEnd = valueStart;
            while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '.' || json.charAt(numEnd) == '-')) {
                numEnd++;
            }
            return json.substring(valueStart, numEnd);
        }
        
        // Handle boolean or null
        if (json.substring(valueStart).startsWith("true")) {
            return "true";
        }
        if (json.substring(valueStart).startsWith("false")) {
            return "false";
        }
        if (json.substring(valueStart).startsWith("null")) {
            return null;
        }
        
        return null;
    }
    
    /**
     * Find the closing quote of a JSON string, handling escape sequences.
     */
    private static int findClosingQuote(String json, int startIndex) {
        int i = startIndex;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return i;
            }
            if (c == '\\' && i + 1 < json.length()) {
                // Skip escaped character
                i += 2;
            } else {
                i++;
            }
        }
        return -1;
    }
    
    /**
     * Unescape a JSON string value.
     */
    private static String unescapeJsonString(String value) {
        if (value == null || !value.contains("\\")) {
            return value;
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    case '/': result.append('/'); i += 2; break;
                    case 'b': result.append('\b'); i += 2; break;
                    case 'f': result.append('\f'); i += 2; break;
                    case 'n': result.append('\n'); i += 2; break;
                    case 'r': result.append('\r'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < value.length()) {
                            try {
                                int unicode = Integer.parseInt(value.substring(i + 2, i + 6), 16);
                                result.append((char) unicode);
                                i += 6;
                            } catch (NumberFormatException e) {
                                result.append(c);
                                i++;
                            }
                        } else {
                            result.append(c);
                            i++;
                        }
                        break;
                    default:
                        result.append(c);
                        i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
    
    /**
     * Parse an integer from a string with a default fallback.
     */
    private static int parseIntField(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Extract batch ID from decoded QR code content.
     * Supports both JSON format and plain text batch IDs.
     * 
     * @param qrContent The decoded QR code content
     * @return The extracted batch ID, or the original content if no ID pattern is found
     */
    public static String extractBatchId(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            return null;
        }
        
        // Try parsing as structured payload first
        QRPayload payload = parsePayload(qrContent);
        if (payload != null && payload.getBatchId() != null && !payload.getBatchId().isEmpty()) {
            return payload.getBatchId();
        }
        
        // Fallback to legacy extraction for backward compatibility
        // Try to extract ID from JSON format: {"type":"product","id":"BATCH_A2386",...}
        if (qrContent.contains("\"id\":")) {
            int idStart = qrContent.indexOf("\"id\":\"") + 6;
            int idEnd = qrContent.indexOf("\"", idStart);
            if (idStart > 5 && idEnd > idStart) {
                String extractedId = qrContent.substring(idStart, idEnd);
                logger.debug("Extracted batch ID from JSON: {}", extractedId);
                return extractedId;
            }
        }
        
        // If not JSON, assume the entire content is the batch ID
        logger.debug("Using entire QR content as batch ID: {}", qrContent);
        return qrContent.trim();
    }
    
    /**
     * Extract quality from decoded QR code content.
     * 
     * @param qrContent The decoded QR code content
     * @return The extracted quality string, or null if not found
     */
    public static String extractQuality(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            return null;
        }
        
        QRPayload payload = parsePayload(qrContent);
        return payload != null ? payload.getQuality() : null;
    }
    
    /**
     * Extract quality from decoded QR code content, returning "Unknown" if not found.
     * 
     * @param qrContent The decoded QR code content
     * @return The extracted quality string, or "Unknown" if not found
     */
    public static String extractQualityOrUnknown(String qrContent) {
        String quality = extractQuality(qrContent);
        return (quality != null && !quality.trim().isEmpty()) ? quality : "Unknown";
    }
}
