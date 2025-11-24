package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Scenario;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Simulates a grid-based map tracking positions of producers, consumers, and resources
 * as the delivery simulation progresses. Thread-safe for concurrent access.
 */
public class MapSimulator {
    private static final Logger logger = LoggerFactory.getLogger(MapSimulator.class);
    
    // Map configuration
    private static final int DEFAULT_GRID_WIDTH = 20;
    private static final int DEFAULT_GRID_HEIGHT = 20;
    
    // Entity types
    public enum EntityType {
        PRODUCER, CONSUMER, WAREHOUSE, RESOURCE, DELIVERY_VEHICLE
    }
    
    /**
     * Represents an entity on the map.
     */
    public static class MapEntity {
        private final String id;
        private final EntityType type;
        private int x;
        private int y;
        private final Map<String, Object> metadata;
        
        public MapEntity(String id, EntityType type, int x, int y) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.metadata = new HashMap<>();
        }
        
        public String getId() { return id; }
        public EntityType getType() { return type; }
        public int getX() { return x; }
        public int getY() { return y; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        @Override
        public String toString() {
            return String.format("%s[%s]@(%d,%d)", type, id, x, y);
        }
    }
    
    /**
     * Immutable snapshot of the map state at a point in time.
     */
    public static class MapSnapshot {
        private final long timestamp;
        private final int simulationStep;
        private final int gridWidth;
        private final int gridHeight;
        private final List<MapEntity> entities;
        private final String scenarioId;
        private final Map<String, Object> metadata;
        
        public MapSnapshot(long timestamp, int simulationStep, int gridWidth, int gridHeight,
                          List<MapEntity> entities, String scenarioId, Map<String, Object> metadata) {
            this.timestamp = timestamp;
            this.simulationStep = simulationStep;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.entities = new ArrayList<>(entities);
            this.scenarioId = scenarioId;
            this.metadata = new HashMap<>(metadata);
        }
        
        public long getTimestamp() { return timestamp; }
        public int getSimulationStep() { return simulationStep; }
        public int getGridWidth() { return gridWidth; }
        public int getGridHeight() { return gridHeight; }
        public List<MapEntity> getEntities() { return new ArrayList<>(entities); }
        public String getScenarioId() { return scenarioId; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        /**
         * Convert snapshot to a serializable map structure.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", timestamp);
            result.put("simulation_step", simulationStep);
            result.put("grid_width", gridWidth);
            result.put("grid_height", gridHeight);
            result.put("scenario_id", scenarioId);
            result.put("metadata", new HashMap<>(metadata));
            
            List<Map<String, Object>> entitiesData = new ArrayList<>();
            for (MapEntity entity : entities) {
                Map<String, Object> entityData = new HashMap<>();
                entityData.put("id", entity.getId());
                entityData.put("type", entity.getType().name());
                entityData.put("x", entity.getX());
                entityData.put("y", entity.getY());
                entityData.put("metadata", entity.getMetadata());
                entitiesData.add(entityData);
            }
            result.put("entities", entitiesData);
            
            return result;
        }
    }
    
    // Instance state
    private final int gridWidth;
    private final int gridHeight;
    private final Map<String, MapEntity> entities;
    private final ReadWriteLock lock;
    private int currentStep;
    private String currentScenarioId;
    private final Map<String, Object> simulationMetadata;
    
    /**
     * Create a new MapSimulator with default grid dimensions.
     */
    public MapSimulator() {
        this(DEFAULT_GRID_WIDTH, DEFAULT_GRID_HEIGHT);
    }
    
    /**
     * Create a new MapSimulator with custom grid dimensions.
     */
    public MapSimulator(int gridWidth, int gridHeight) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.entities = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.currentStep = 0;
        this.currentScenarioId = "NORMAL";
        this.simulationMetadata = new ConcurrentHashMap<>();
        
        logger.info("MapSimulator initialized with grid {}x{}", gridWidth, gridHeight);
    }
    
    /**
     * Initialize the map for a specific scenario with entity placements.
     */
    public void initializeForScenario(Scenario scenario, String batchId, int numWaypoints) {
        lock.writeLock().lock();
        try {
            entities.clear();
            currentStep = 0;
            currentScenarioId = scenario != null ? scenario.name() : "NORMAL";
            simulationMetadata.clear();
            
            // Add producer at origin (left side)
            MapEntity producer = new MapEntity(
                "producer-" + batchId, 
                EntityType.PRODUCER, 
                2, 
                gridHeight / 2
            );
            producer.setMetadata("batch_id", batchId);
            producer.setMetadata("scenario", currentScenarioId);
            entities.put(producer.getId(), producer);
            
            // Add warehouse at midpoint
            MapEntity warehouse = new MapEntity(
                "warehouse-central",
                EntityType.WAREHOUSE,
                gridWidth / 2,
                gridHeight / 2
            );
            warehouse.setMetadata("capacity", 1000);
            warehouse.setMetadata("temperature_controlled", true);
            entities.put(warehouse.getId(), warehouse);
            
            // Add consumer at destination (right side)
            MapEntity consumer = new MapEntity(
                "consumer-" + batchId,
                EntityType.CONSUMER,
                gridWidth - 3,
                gridHeight / 2
            );
            consumer.setMetadata("batch_id", batchId);
            entities.put(consumer.getId(), consumer);
            
            // Add delivery vehicle at producer location
            MapEntity vehicle = new MapEntity(
                "vehicle-" + batchId,
                EntityType.DELIVERY_VEHICLE,
                2,
                gridHeight / 2
            );
            vehicle.setMetadata("batch_id", batchId);
            vehicle.setMetadata("speed_multiplier", scenario != null ? scenario.getSpeedMultiplier() : 1.0);
            vehicle.setMetadata("current_waypoint", 0);
            vehicle.setMetadata("total_waypoints", numWaypoints);
            entities.put(vehicle.getId(), vehicle);
            
            // Add resource at producer
            MapEntity resource = new MapEntity(
                "resource-" + batchId,
                EntityType.RESOURCE,
                2,
                gridHeight / 2
            );
            resource.setMetadata("batch_id", batchId);
            resource.setMetadata("quality", 1.0);
            if (scenario != null) {
                resource.setMetadata("temperature_drift", scenario.getTemperatureDrift());
                resource.setMetadata("spoilage_rate", scenario.getSpoilageRate());
            }
            entities.put(resource.getId(), resource);
            
            simulationMetadata.put("batch_id", batchId);
            simulationMetadata.put("scenario", currentScenarioId);
            simulationMetadata.put("num_waypoints", numWaypoints);
            simulationMetadata.put("initialized_at", System.currentTimeMillis());
            
            logger.info("Map initialized for scenario {} with {} entities", 
                       currentScenarioId, entities.size());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Advance the simulation by one step, updating entity positions and states.
     * This is called each simulation tick.
     * 
     * @param progressPercent Current progress percentage (0-100)
     * @return MapSnapshot representing the current state
     */
    public MapSnapshot step(double progressPercent) {
        lock.writeLock().lock();
        try {
            currentStep++;
            
            // Update delivery vehicle position based on progress
            for (MapEntity entity : entities.values()) {
                if (entity.getType() == EntityType.DELIVERY_VEHICLE) {
                    updateVehiclePosition(entity, progressPercent);
                }
                if (entity.getType() == EntityType.RESOURCE) {
                    updateResourcePosition(entity, progressPercent);
                    updateResourceQuality(entity, progressPercent);
                }
            }
            
            simulationMetadata.put("last_step_time", System.currentTimeMillis());
            simulationMetadata.put("progress_percent", progressPercent);
            
            return createSnapshot();
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update vehicle position based on progress percentage.
     */
    private void updateVehiclePosition(MapEntity vehicle, double progressPercent) {
        // Vehicle moves from producer (left) to consumer (right) along the middle row
        int startX = 2;
        int endX = gridWidth - 3;
        int targetX = startX + (int) ((endX - startX) * (progressPercent / 100.0));
        
        // Clamp to grid bounds
        targetX = Math.max(0, Math.min(gridWidth - 1, targetX));
        
        vehicle.setPosition(targetX, gridHeight / 2);
        
        // Update waypoint metadata
        Object totalWaypointsObj = vehicle.getMetadata().get("total_waypoints");
        if (totalWaypointsObj instanceof Integer) {
            int totalWaypoints = (Integer) totalWaypointsObj;
            int currentWaypoint = (int) (totalWaypoints * (progressPercent / 100.0));
            vehicle.setMetadata("current_waypoint", currentWaypoint);
        }
        
        // Determine current location phase
        String phase;
        if (progressPercent < 30) {
            phase = "En route from producer";
        } else if (progressPercent < 50) {
            phase = "Approaching warehouse";
        } else if (progressPercent < 70) {
            phase = "Departed warehouse";
        } else if (progressPercent < 100) {
            phase = "Approaching consumer";
        } else {
            phase = "Delivered";
        }
        vehicle.setMetadata("phase", phase);
    }
    
    /**
     * Update resource position to follow vehicle.
     */
    private void updateResourcePosition(MapEntity resource, double progressPercent) {
        // Resource follows the vehicle
        for (MapEntity entity : entities.values()) {
            if (entity.getType() == EntityType.DELIVERY_VEHICLE) {
                Object batchIdObj = entity.getMetadata().get("batch_id");
                Object resourceBatchIdObj = resource.getMetadata().get("batch_id");
                if (batchIdObj != null && batchIdObj.equals(resourceBatchIdObj)) {
                    resource.setPosition(entity.getX(), entity.getY());
                    break;
                }
            }
        }
    }
    
    /**
     * Update resource quality based on spoilage rate and progress.
     */
    private void updateResourceQuality(MapEntity resource, double progressPercent) {
        Object qualityObj = resource.getMetadata().get("quality");
        Object spoilageRateObj = resource.getMetadata().get("spoilage_rate");
        
        if (qualityObj instanceof Number && spoilageRateObj instanceof Number) {
            double currentQuality = ((Number) qualityObj).doubleValue();
            double spoilageRate = ((Number) spoilageRateObj).doubleValue();
            
            // Quality degrades over time - simulate time passing with progress
            double timeFactor = progressPercent / 100.0; // 0 to 1
            double qualityLoss = spoilageRate * timeFactor * 0.1; // Scaled degradation
            double newQuality = Math.max(0.0, currentQuality - qualityLoss);
            
            resource.setMetadata("quality", newQuality);
        }
    }
    
    /**
     * Get a thread-safe snapshot of the current map state.
     */
    public MapSnapshot getSnapshot() {
        lock.readLock().lock();
        try {
            return createSnapshot();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Create a snapshot from current state (must be called with lock held).
     */
    private MapSnapshot createSnapshot() {
        List<MapEntity> entitiesCopy = new ArrayList<>();
        
        for (MapEntity entity : entities.values()) {
            MapEntity copy = new MapEntity(entity.getId(), entity.getType(), entity.getX(), entity.getY());
            for (Map.Entry<String, Object> entry : entity.getMetadata().entrySet()) {
                copy.setMetadata(entry.getKey(), entry.getValue());
            }
            entitiesCopy.add(copy);
        }
        
        return new MapSnapshot(
            System.currentTimeMillis(),
            currentStep,
            gridWidth,
            gridHeight,
            entitiesCopy,
            currentScenarioId,
            simulationMetadata
        );
    }
    
    /**
     * Reset the simulation to initial state.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            entities.clear();
            currentStep = 0;
            currentScenarioId = "NORMAL";
            simulationMetadata.clear();
            logger.info("MapSimulator reset");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current simulation step number.
     */
    public int getCurrentStep() {
        lock.readLock().lock();
        try {
            return currentStep;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get number of entities currently on the map.
     */
    public int getEntityCount() {
        lock.readLock().lock();
        try {
            return entities.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
