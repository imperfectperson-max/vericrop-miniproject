package org.vericrop.gui.util;

import com.google.zxing.BinaryBitmap;
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
import java.nio.file.Path;

/**
 * Utility class for decoding QR codes from images using computer vision.
 * Supports decoding batch IDs and other information from QR code images.
 */
public class QRDecoder {
    private static final Logger logger = LoggerFactory.getLogger(QRDecoder.class);
    
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
        
        // Decode the QR code
        try {
            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(bitmap);
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
}
