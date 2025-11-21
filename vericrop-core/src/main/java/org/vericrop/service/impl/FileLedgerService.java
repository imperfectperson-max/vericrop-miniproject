package org.vericrop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.vericrop.dto.ShipmentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * File-based ledger service for storing immutable shipment records.
 * Simulates blockchain behavior with append-only JSON storage.
 */
public class FileLedgerService {
    private static final Logger logger = LoggerFactory.getLogger(FileLedgerService.class);
    private final Path ledgerPath;
    private final ObjectMapper objectMapper;

    /**
     * Create ledger service with default ledger directory.
     */
    public FileLedgerService() {
        this("ledger");
    }

    /**
     * Create ledger service with custom ledger directory.
     *
     * @param ledgerDirectory Directory to store ledger files
     */
    public FileLedgerService(String ledgerDirectory) {
        this.ledgerPath = Paths.get(ledgerDirectory, "shipment_ledger.jsonl");
        this.objectMapper = new ObjectMapper();
        initializeLedger();
    }

    /**
     * Initialize ledger directory and file.
     */
    private void initializeLedger() {
        try {
            // Create ledger directory if it doesn't exist
            Files.createDirectories(ledgerPath.getParent());

            // Create ledger file if it doesn't exist
            if (!Files.exists(ledgerPath)) {
                Files.createFile(ledgerPath);
                logger.info("Created new ledger file: {}", ledgerPath);
            } else {
                logger.info("Using existing ledger file: {}", ledgerPath);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize ledger: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize ledger", e);
        }
    }

    /**
     * Record a shipment in the immutable ledger with atomic file operations.
     * Uses file locking to prevent concurrent write corruption.
     *
     * @param record The shipment record to store
     * @return The record with ledger ID and hash assigned
     */
    public ShipmentRecord recordShipment(ShipmentRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Shipment record cannot be null");
        }

        try {
            // Generate unique ledger ID if not present
            if (record.getLedgerId() == null || record.getLedgerId().isEmpty()) {
                record.setLedgerId(UUID.randomUUID().toString());
            }

            // Get previous record's hash for chain verification
            String previousHash = getLastRecordHash();
            
            // Compute SHA-256 hash of record content including previous hash (chain)
            String recordHash = computeRecordHash(record, previousHash);
            record.setLedgerHash(recordHash);

            // Serialize to JSON
            String jsonLine = objectMapper.writeValueAsString(record) + System.lineSeparator();

            // Use file locking for atomic append operation with try-with-resources
            try (RandomAccessFile raf = new RandomAccessFile(ledgerPath.toFile(), "rw");
                 java.nio.channels.FileChannel channel = raf.getChannel();
                 java.nio.channels.FileLock lock = channel.lock()) {
                
                // Move to end of file and append
                raf.seek(raf.length());
                raf.write(jsonLine.getBytes(StandardCharsets.UTF_8));
                
                // Force writes to disk for durability
                channel.force(true);
            }

            logger.info("Recorded shipment {} in ledger with hash {} (chain: {})",
                    record.getShipmentId(), recordHash.substring(0, 8), 
                    previousHash != null ? previousHash.substring(0, 8) : "genesis");

            return record;

        } catch (IOException e) {
            logger.error("Failed to record shipment {}: {}", record.getShipmentId(), e.getMessage());
            throw new RuntimeException("Failed to record shipment in ledger", e);
        }
    }

    /**
     * Retrieve a shipment record by ledger ID.
     *
     * @param ledgerId The unique ledger identifier
     * @return The shipment record, or null if not found
     */
    public ShipmentRecord getShipmentByLedgerId(String ledgerId) {
        if (ledgerId == null || ledgerId.isEmpty()) {
            throw new IllegalArgumentException("Ledger ID cannot be null or empty");
        }

        try {
            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                if (ledgerId.equals(record.getLedgerId())) {
                    return record;
                }
            }

            logger.warn("Shipment record not found for ledger ID: {}", ledgerId);
            return null;

        } catch (IOException e) {
            logger.error("Failed to retrieve shipment {}: {}", ledgerId, e.getMessage());
            throw new RuntimeException("Failed to retrieve shipment from ledger", e);
        }
    }

    /**
     * Retrieve all shipment records for a specific batch.
     *
     * @param batchId The batch identifier
     * @return List of shipment records for the batch
     */
    public List<ShipmentRecord> getShipmentsByBatchId(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }

        List<ShipmentRecord> records = new ArrayList<>();

        try {
            if (!Files.exists(ledgerPath)) {
                return records;
            }

            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                if (batchId.equals(record.getBatchId())) {
                    records.add(record);
                }
            }

            logger.info("Found {} shipment records for batch {}", records.size(), batchId);
            return records;

        } catch (IOException e) {
            logger.error("Failed to retrieve shipments for batch {}: {}", batchId, e.getMessage());
            throw new RuntimeException("Failed to retrieve shipments from ledger", e);
        }
    }

    /**
     * Get all shipment records in the ledger.
     *
     * @return List of all shipment records
     */
    public List<ShipmentRecord> getAllShipments() {
        List<ShipmentRecord> records = new ArrayList<>();

        try {
            if (!Files.exists(ledgerPath)) {
                return records;
            }

            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                records.add(record);
            }

            logger.info("Retrieved {} total shipment records from ledger", records.size());
            return records;

        } catch (IOException e) {
            logger.error("Failed to retrieve all shipments: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve shipments from ledger", e);
        }
    }

    /**
     * Verify integrity of a shipment record.
     * Note: This method uses chain verification, so it's recommended to use
     * verifyChainIntegrity() for validating the entire ledger.
     *
     * @param record The record to verify
     * @return true if the record's hash matches its content (without chain context)
     */
    public boolean verifyRecordIntegrity(ShipmentRecord record) {
        if (record == null || record.getLedgerHash() == null) {
            return false;
        }

        try {
            // For individual record verification, we need to find its previous hash
            // This is a simplified check that verifies content without full chain context
            String previousHash = findPreviousHashForRecord(record);
            String computedHash = computeRecordHash(record, previousHash);
            return computedHash.equals(record.getLedgerHash());
        } catch (Exception e) {
            logger.error("Failed to verify record integrity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Find the hash of the record immediately before the given record.
     * 
     * @param targetRecord The record whose predecessor we're looking for
     * @return The hash of the previous record, or null if this is the first record
     */
    private String findPreviousHashForRecord(ShipmentRecord targetRecord) {
        try {
            if (!Files.exists(ledgerPath)) {
                return null;
            }

            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
            String previousHash = null;

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                
                // If this is our target record, return the previous hash
                if (record.getLedgerId() != null && 
                    record.getLedgerId().equals(targetRecord.getLedgerId())) {
                    return previousHash;
                }
                
                // Update previous hash for next iteration
                previousHash = record.getLedgerHash();
            }

            return null;

        } catch (IOException e) {
            logger.warn("Could not find previous hash for record: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compute SHA-256 hash of shipment record content with chain verification.
     * Includes previous hash to create an immutable chain.
     * 
     * @param record The shipment record to hash
     * @param previousHash Hash of the previous record in the chain (null for first record)
     * @return SHA-256 hash as hex string
     */
    private String computeRecordHash(ShipmentRecord record, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Create canonical representation of record for hashing
            StringBuilder content = new StringBuilder();
            // Include previous hash in computation to create chain
            content.append(previousHash != null ? previousHash : "genesis").append("|");
            content.append(record.getShipmentId()).append("|");
            content.append(record.getBatchId()).append("|");
            content.append(record.getFromParty()).append("|");
            content.append(record.getToParty()).append("|");
            content.append(record.getStatus()).append("|");
            content.append(record.getQualityScore()).append("|");
            content.append(record.getTimestamp());

            byte[] hashBytes = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Get the hash of the last record in the ledger for chain verification.
     * 
     * @return The hash of the last record, or null if ledger is empty
     */
    private String getLastRecordHash() {
        try {
            if (!Files.exists(ledgerPath)) {
                return null;
            }

            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
            
            // Read from end to find last non-empty line
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                    return record.getLedgerHash();
                }
            }
            
            return null;

        } catch (IOException e) {
            logger.warn("Could not read last record hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Get the path to the ledger file.
     */
    public Path getLedgerPath() {
        return ledgerPath;
    }

    /**
     * Get the number of records in the ledger.
     */
    public int getRecordCount() {
        try {
            if (!Files.exists(ledgerPath)) {
                return 0;
            }

            try (java.util.stream.Stream<String> lines = Files.lines(ledgerPath)) {
                return (int) lines
                        .filter(line -> !line.trim().isEmpty())
                        .count();
            }

        } catch (IOException e) {
            logger.error("Failed to count records: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Verify the integrity of the entire ledger chain.
     * Checks that each record's hash is correctly computed and that the chain is unbroken.
     * 
     * @return true if the entire chain is valid, false otherwise
     */
    public boolean verifyChainIntegrity() {
        try {
            if (!Files.exists(ledgerPath)) {
                // Empty ledger is valid
                return true;
            }

            List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
            String previousHash = null;
            int recordCount = 0;

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                recordCount++;
                ShipmentRecord record = objectMapper.readValue(line, ShipmentRecord.class);
                
                // Verify this record's hash
                String computedHash = computeRecordHash(record, previousHash);
                if (!computedHash.equals(record.getLedgerHash())) {
                    logger.error("Chain integrity check failed at record {}: hash mismatch", recordCount);
                    logger.error("  Expected: {}", record.getLedgerHash());
                    logger.error("  Computed: {}", computedHash);
                    return false;
                }

                // Update previous hash for next iteration
                previousHash = record.getLedgerHash();
            }

            logger.info("✅ Chain integrity verified for {} records", recordCount);
            return true;

        } catch (IOException e) {
            logger.error("Failed to verify chain integrity: {}", e.getMessage());
            return false;
        }
    }
}