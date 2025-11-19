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
     * Record a shipment in the immutable ledger.
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

            // Compute SHA-256 hash of record content
            String recordHash = computeRecordHash(record);
            record.setLedgerHash(recordHash);

            // Serialize to JSON
            String jsonLine = objectMapper.writeValueAsString(record) + System.lineSeparator();

            // Append to ledger file (immutable append-only)
            Files.write(ledgerPath, jsonLine.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);

            logger.info("Recorded shipment {} in ledger with hash {}",
                    record.getShipmentId(), recordHash.substring(0, 8));

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
     *
     * @param record The record to verify
     * @return true if the record's hash matches its content
     */
    public boolean verifyRecordIntegrity(ShipmentRecord record) {
        if (record == null || record.getLedgerHash() == null) {
            return false;
        }

        try {
            String computedHash = computeRecordHash(record);
            return computedHash.equals(record.getLedgerHash());
        } catch (Exception e) {
            logger.error("Failed to verify record integrity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Compute SHA-256 hash of shipment record content.
     */
    private String computeRecordHash(ShipmentRecord record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Create canonical representation of record for hashing
            StringBuilder content = new StringBuilder();
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

            return (int) Files.lines(ledgerPath)
                    .filter(line -> !line.trim().isEmpty())
                    .count();

        } catch (IOException e) {
            logger.error("Failed to count records: {}", e.getMessage());
            return 0;
        }
    }
}