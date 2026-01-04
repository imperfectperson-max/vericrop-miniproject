package org.vericrop.gui.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for persisting shipments and simulations to JSON files.
 * Provides read/write operations and date range queries for reports.
 */
public class ShipmentPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(ShipmentPersistenceService.class);
    
    private static final String DATA_DIRECTORY = "data";
    private static final String SHIPMENTS_FILE = "shipments.json";
    private static final String SIMULATIONS_FILE = "simulations.json";
    
    private final ObjectMapper objectMapper;
    private final Path dataDirectory;
    private final Path shipmentsFile;
    private final Path simulationsFile;
    
    // In-memory cache for fast access
    private final Map<String, PersistedShipment> shipmentsCache;
    private final Map<String, PersistedSimulation> simulationsCache;
    
    // Flag to track if data has been loaded
    private volatile boolean initialized = false;
    
    /**
     * Default constructor - uses default data directory
     */
    public ShipmentPersistenceService() {
        this(DATA_DIRECTORY);
    }
    
    /**
     * Constructor with custom data directory
     */
    public ShipmentPersistenceService(String dataDirectoryPath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        this.dataDirectory = Paths.get(dataDirectoryPath);
        this.shipmentsFile = dataDirectory.resolve(SHIPMENTS_FILE);
        this.simulationsFile = dataDirectory.resolve(SIMULATIONS_FILE);
        
        this.shipmentsCache = new ConcurrentHashMap<>();
        this.simulationsCache = new ConcurrentHashMap<>();
        
        initialize();
    }
    
    /**
     * Initialize the persistence layer - create directories and load existing data
     */
    private synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created data directory: {}", dataDirectory);
            }
            
            // Load existing shipments
            if (Files.exists(shipmentsFile)) {
                loadShipments();
            }
            
            // Load existing simulations
            if (Files.exists(simulationsFile)) {
                loadSimulations();
            }
            
            initialized = true;
            logger.info("ShipmentPersistenceService initialized with {} shipments and {} simulations",
                    shipmentsCache.size(), simulationsCache.size());
        } catch (IOException e) {
            logger.error("Failed to initialize persistence service", e);
            throw new RuntimeException("Failed to initialize persistence service", e);
        }
    }
    
    // ==================== SHIPMENT OPERATIONS ====================
    
    /**
     * Save or update a shipment
     */
    public void saveShipment(PersistedShipment shipment) {
        if (shipment == null || shipment.getBatchId() == null) {
            throw new IllegalArgumentException("Shipment and batchId cannot be null");
        }
        
        // Generate ID if not present
        if (shipment.getId() == null) {
            shipment.setId(UUID.randomUUID().toString());
        }
        
        // Update timestamp
        shipment.setUpdatedAt(System.currentTimeMillis());
        
        // Save to cache
        shipmentsCache.put(shipment.getBatchId(), shipment);
        
        // Persist to file
        persistShipments();
        
        logger.debug("Saved shipment: {}", shipment.getBatchId());
    }
    
    /**
     * Get a shipment by batch ID
     */
    public Optional<PersistedShipment> getShipment(String batchId) {
        return Optional.ofNullable(shipmentsCache.get(batchId));
    }
    
    /**
     * Get all shipments
     */
    public List<PersistedShipment> getAllShipments() {
        return new ArrayList<>(shipmentsCache.values());
    }
    
    /**
     * Get shipments by date range
     */
    public List<PersistedShipment> getShipmentsByDateRange(LocalDate startDate, LocalDate endDate) {
        long startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return shipmentsCache.values().stream()
                .filter(s -> s.getCreatedAt() >= startMillis && s.getCreatedAt() < endMillis)
                .sorted(Comparator.comparingLong(PersistedShipment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get shipments by status
     */
    public List<PersistedShipment> getShipmentsByStatus(String status) {
        return shipmentsCache.values().stream()
                .filter(s -> status.equalsIgnoreCase(s.getStatus()))
                .sorted(Comparator.comparingLong(PersistedShipment::getUpdatedAt).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Delete a shipment
     */
    public boolean deleteShipment(String batchId) {
        PersistedShipment removed = shipmentsCache.remove(batchId);
        if (removed != null) {
            persistShipments();
            logger.debug("Deleted shipment: {}", batchId);
            return true;
        }
        return false;
    }
    
    /**
     * Load shipments from file.
     * Distinguishes between 'file doesn't exist' (expected on first run) and 
     * 'file exists but is corrupted/unreadable' (data integrity issue).
     */
    private void loadShipments() throws IOException {
        if (!Files.exists(shipmentsFile)) {
            logger.info("Shipments file does not exist yet, will be created on first save");
            return;
        }
        
        try {
            List<PersistedShipment> shipments = objectMapper.readValue(
                    shipmentsFile.toFile(),
                    new TypeReference<List<PersistedShipment>>() {}
            );
            shipmentsCache.clear();
            for (PersistedShipment shipment : shipments) {
                if (shipment.getBatchId() != null) {
                    shipmentsCache.put(shipment.getBatchId(), shipment);
                }
            }
            logger.info("Loaded {} shipments from file", shipments.size());
        } catch (IOException e) {
            logger.error("Failed to parse shipments file - data may be corrupted: {}", e.getMessage());
            // Keep cache empty rather than throwing, allowing the app to continue
        }
    }
    
    /**
     * Persist shipments to file
     */
    private synchronized void persistShipments() {
        try {
            List<PersistedShipment> shipments = new ArrayList<>(shipmentsCache.values());
            objectMapper.writeValue(shipmentsFile.toFile(), shipments);
            logger.debug("Persisted {} shipments to file", shipments.size());
        } catch (IOException e) {
            logger.error("Failed to persist shipments", e);
        }
    }
    
    // ==================== SIMULATION OPERATIONS ====================
    
    /**
     * Save or update a simulation
     */
    public void saveSimulation(PersistedSimulation simulation) {
        if (simulation == null || simulation.getBatchId() == null) {
            throw new IllegalArgumentException("Simulation and batchId cannot be null");
        }
        
        // Generate ID if not present
        if (simulation.getId() == null) {
            simulation.setId(UUID.randomUUID().toString());
        }
        
        // Save to cache
        simulationsCache.put(simulation.getId(), simulation);
        
        // Persist to file
        persistSimulations();
        
        logger.debug("Saved simulation: {} for batch {}", simulation.getId(), simulation.getBatchId());
    }
    
    /**
     * Get a simulation by ID
     */
    public Optional<PersistedSimulation> getSimulation(String simulationId) {
        return Optional.ofNullable(simulationsCache.get(simulationId));
    }
    
    /**
     * Get simulations by batch ID
     */
    public List<PersistedSimulation> getSimulationsByBatchId(String batchId) {
        return simulationsCache.values().stream()
                .filter(s -> batchId.equals(s.getBatchId()))
                .sorted(Comparator.comparingLong(PersistedSimulation::getStartTime).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all simulations
     */
    public List<PersistedSimulation> getAllSimulations() {
        return new ArrayList<>(simulationsCache.values());
    }
    
    /**
     * Get simulations by date range
     */
    public List<PersistedSimulation> getSimulationsByDateRange(LocalDate startDate, LocalDate endDate) {
        long startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return simulationsCache.values().stream()
                .filter(s -> s.getStartTime() >= startMillis && s.getStartTime() < endMillis)
                .sorted(Comparator.comparingLong(PersistedSimulation::getStartTime).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get completed simulations only
     */
    public List<PersistedSimulation> getCompletedSimulations() {
        return simulationsCache.values().stream()
                .filter(PersistedSimulation::isCompleted)
                .sorted(Comparator.comparingLong(PersistedSimulation::getEndTime).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Delete a simulation
     */
    public boolean deleteSimulation(String simulationId) {
        PersistedSimulation removed = simulationsCache.remove(simulationId);
        if (removed != null) {
            persistSimulations();
            logger.debug("Deleted simulation: {}", simulationId);
            return true;
        }
        return false;
    }
    
    /**
     * Load simulations from file.
     * Distinguishes between 'file doesn't exist' (expected on first run) and 
     * 'file exists but is corrupted/unreadable' (data integrity issue).
     */
    private void loadSimulations() throws IOException {
        if (!Files.exists(simulationsFile)) {
            logger.info("Simulations file does not exist yet, will be created on first save");
            return;
        }
        
        try {
            List<PersistedSimulation> simulations = objectMapper.readValue(
                    simulationsFile.toFile(),
                    new TypeReference<List<PersistedSimulation>>() {}
            );
            simulationsCache.clear();
            for (PersistedSimulation simulation : simulations) {
                if (simulation.getId() != null) {
                    simulationsCache.put(simulation.getId(), simulation);
                }
            }
            logger.info("Loaded {} simulations from file", simulations.size());
        } catch (IOException e) {
            logger.error("Failed to parse simulations file - data may be corrupted: {}", e.getMessage());
            // Keep cache empty rather than throwing, allowing the app to continue
        }
    }
    
    /**
     * Persist simulations to file
     */
    private synchronized void persistSimulations() {
        try {
            List<PersistedSimulation> simulations = new ArrayList<>(simulationsCache.values());
            objectMapper.writeValue(simulationsFile.toFile(), simulations);
            logger.debug("Persisted {} simulations to file", simulations.size());
        } catch (IOException e) {
            logger.error("Failed to persist simulations", e);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get the count of shipments
     */
    public int getShipmentsCount() {
        return shipmentsCache.size();
    }
    
    /**
     * Get the count of simulations
     */
    public int getSimulationsCount() {
        return simulationsCache.size();
    }
    
    /**
     * Get data directory path
     */
    public String getDataDirectoryPath() {
        return dataDirectory.toAbsolutePath().toString();
    }
    
    /**
     * Clear all data (use with caution)
     */
    public void clearAllData() {
        shipmentsCache.clear();
        simulationsCache.clear();
        persistShipments();
        persistSimulations();
        logger.warn("All persistence data cleared");
    }
}
