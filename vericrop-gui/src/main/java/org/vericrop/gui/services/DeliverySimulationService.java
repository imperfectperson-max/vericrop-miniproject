package org.vericrop.gui.services;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DeliverySimulationService simulates delivery lifecycle events.
 * Events: picked up, in transit, delayed, delivered
 * Uses observable pattern to emit real-time events.
 */
public class DeliverySimulationService {
    private static final Logger logger = LoggerFactory.getLogger(DeliverySimulationService.class);
    private static DeliverySimulationService instance;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<Consumer<DeliveryEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ShipmentSimulation> activeShipments = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    private boolean running = false;
    private int simulationSpeedMultiplier = 1; // 1 = real time, 10 = 10x faster
    
    // Event timing configuration (in seconds)
    private int pickupDelay = 30;
    private int inTransitDelay = 60;
    private int deliveryDelay = 90;
    private double delayProbability = 0.15; // 15% chance of delay
    
    private DeliverySimulationService() {
        // Private constructor for singleton
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized DeliverySimulationService getInstance() {
        if (instance == null) {
            instance = new DeliverySimulationService();
        }
        return instance;
    }
    
    /**
     * Register an event listener
     */
    public void addEventListener(Consumer<DeliveryEvent> listener) {
        eventListeners.add(listener);
        logger.info("Event listener registered. Total listeners: {}", eventListeners.size());
    }
    
    /**
     * Remove an event listener
     */
    public void removeEventListener(Consumer<DeliveryEvent> listener) {
        eventListeners.remove(listener);
        logger.info("Event listener removed. Total listeners: {}", eventListeners.size());
    }
    
    /**
     * Start a new shipment simulation
     */
    public String startShipment(String batchId, String origin, String destination) {
        String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        ShipmentSimulation simulation = new ShipmentSimulation(
            shipmentId, batchId, origin, destination
        );
        
        activeShipments.put(shipmentId, simulation);
        
        // Emit created event immediately
        emitEvent(new DeliveryEvent(
            shipmentId, batchId, DeliveryStatus.CREATED, 
            origin, destination, LocalDateTime.now(),
            "Shipment created and awaiting pickup"
        ));
        
        // Schedule pickup
        schedulePickup(simulation);
        
        logger.info("Started shipment simulation: {} for batch: {}", shipmentId, batchId);
        return shipmentId;
    }
    
    /**
     * Schedule pickup event
     */
    private void schedulePickup(ShipmentSimulation simulation) {
        int delay = pickupDelay / simulationSpeedMultiplier;
        
        scheduler.schedule(() -> {
            simulation.status = DeliveryStatus.PICKED_UP;
            emitEvent(new DeliveryEvent(
                simulation.shipmentId, simulation.batchId, 
                DeliveryStatus.PICKED_UP, 
                simulation.origin, simulation.destination,
                LocalDateTime.now(),
                "Package picked up from origin"
            ));
            
            // Schedule in-transit
            scheduleInTransit(simulation);
        }, delay, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule in-transit event
     */
    private void scheduleInTransit(ShipmentSimulation simulation) {
        int delay = inTransitDelay / simulationSpeedMultiplier;
        
        scheduler.schedule(() -> {
            simulation.status = DeliveryStatus.IN_TRANSIT;
            emitEvent(new DeliveryEvent(
                simulation.shipmentId, simulation.batchId,
                DeliveryStatus.IN_TRANSIT,
                simulation.origin, simulation.destination,
                LocalDateTime.now(),
                "Package in transit to destination"
            ));
            
            // Random chance of delay
            if (random.nextDouble() < delayProbability) {
                scheduleDelay(simulation);
            } else {
                scheduleDelivery(simulation);
            }
        }, delay, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule delay event
     */
    private void scheduleDelay(ShipmentSimulation simulation) {
        int delay = 20 / simulationSpeedMultiplier; // 20 second delay
        
        scheduler.schedule(() -> {
            simulation.status = DeliveryStatus.DELAYED;
            String[] reasons = {
                "Traffic congestion on route",
                "Weather conditions",
                "Vehicle maintenance required",
                "Customs clearance delay"
            };
            String reason = reasons[random.nextInt(reasons.length)];
            
            emitEvent(new DeliveryEvent(
                simulation.shipmentId, simulation.batchId,
                DeliveryStatus.DELAYED,
                simulation.origin, simulation.destination,
                LocalDateTime.now(),
                "Delivery delayed: " + reason
            ));
            
            // Still deliver eventually
            scheduleDelivery(simulation);
        }, delay, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule delivery event
     */
    private void scheduleDelivery(ShipmentSimulation simulation) {
        int delay = deliveryDelay / simulationSpeedMultiplier;
        
        scheduler.schedule(() -> {
            simulation.status = DeliveryStatus.DELIVERED;
            emitEvent(new DeliveryEvent(
                simulation.shipmentId, simulation.batchId,
                DeliveryStatus.DELIVERED,
                simulation.origin, simulation.destination,
                LocalDateTime.now(),
                "Package delivered successfully"
            ));
            
            // Remove from active shipments
            activeShipments.remove(simulation.shipmentId);
            logger.info("Shipment {} completed and removed", simulation.shipmentId);
        }, delay, TimeUnit.SECONDS);
    }
    
    /**
     * Emit event to all listeners
     */
    private void emitEvent(DeliveryEvent event) {
        logger.debug("Emitting event: {} - {}", event.getStatus(), event.getMessage());
        
        // Ensure UI updates happen on JavaFX thread
        Platform.runLater(() -> {
            for (Consumer<DeliveryEvent> listener : eventListeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    logger.error("Error in event listener", e);
                }
            }
        });
    }
    
    /**
     * Start simulation (for testing - auto-creates sample shipments)
     */
    public void startSimulation() {
        running = true;
        logger.info("Simulation started with speed multiplier: {}x", simulationSpeedMultiplier);
    }
    
    /**
     * Stop simulation
     */
    public void stopSimulation() {
        running = false;
        logger.info("Simulation stopped");
    }
    
    /**
     * Seed test data - create multiple shipments
     */
    public List<String> seedTestShipments(int count) {
        List<String> shipmentIds = new ArrayList<>();
        String[] origins = {"Farm A", "Farm B", "Warehouse North", "Distribution Center"};
        String[] destinations = {"Market X", "Store Y", "Retailer Z", "Consumer Hub"};
        
        for (int i = 0; i < count; i++) {
            String origin = origins[random.nextInt(origins.length)];
            String destination = destinations[random.nextInt(destinations.length)];
            String batchId = "BATCH-" + (1000 + i);
            
            String shipmentId = startShipment(batchId, origin, destination);
            shipmentIds.add(shipmentId);
        }
        
        logger.info("Seeded {} test shipments", count);
        return shipmentIds;
    }
    
    /**
     * Get all active shipments
     */
    public Map<String, ShipmentSimulation> getActiveShipments() {
        return new HashMap<>(activeShipments);
    }
    
    /**
     * Configure simulation timing
     */
    public void setTimingConfig(int pickupDelaySeconds, int inTransitDelaySeconds, 
                                int deliveryDelaySeconds, double delayProbability) {
        this.pickupDelay = pickupDelaySeconds;
        this.inTransitDelay = inTransitDelaySeconds;
        this.deliveryDelay = deliveryDelaySeconds;
        this.delayProbability = delayProbability;
        logger.info("Timing configuration updated");
    }
    
    /**
     * Set simulation speed multiplier
     */
    public void setSimulationSpeed(int multiplier) {
        this.simulationSpeedMultiplier = Math.max(1, multiplier);
        logger.info("Simulation speed set to {}x", this.simulationSpeedMultiplier);
    }
    
    /**
     * Shutdown the service
     */
    public void shutdown() {
        logger.info("Shutting down DeliverySimulationService");
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        activeShipments.clear();
        eventListeners.clear();
        logger.info("DeliverySimulationService shutdown complete");
    }
    
    /**
     * Delivery status enum
     */
    public enum DeliveryStatus {
        CREATED,
        PICKED_UP,
        IN_TRANSIT,
        DELAYED,
        DELIVERED
    }
    
    /**
     * Delivery event class
     */
    public static class DeliveryEvent {
        private final String shipmentId;
        private final String batchId;
        private final DeliveryStatus status;
        private final String origin;
        private final String destination;
        private final LocalDateTime timestamp;
        private final String message;
        
        public DeliveryEvent(String shipmentId, String batchId, DeliveryStatus status,
                           String origin, String destination, LocalDateTime timestamp, String message) {
            this.shipmentId = shipmentId;
            this.batchId = batchId;
            this.status = status;
            this.origin = origin;
            this.destination = destination;
            this.timestamp = timestamp;
            this.message = message;
        }
        
        public String getShipmentId() { return shipmentId; }
        public String getBatchId() { return batchId; }
        public DeliveryStatus getStatus() { return status; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s - %s", 
                timestamp, shipmentId, status, message);
        }
    }
    
    /**
     * Shipment simulation class
     */
    public static class ShipmentSimulation {
        private final String shipmentId;
        private final String batchId;
        private final String origin;
        private final String destination;
        private DeliveryStatus status;
        private final LocalDateTime createdAt;
        
        public ShipmentSimulation(String shipmentId, String batchId, String origin, String destination) {
            this.shipmentId = shipmentId;
            this.batchId = batchId;
            this.origin = origin;
            this.destination = destination;
            this.status = DeliveryStatus.CREATED;
            this.createdAt = LocalDateTime.now();
        }
        
        public String getShipmentId() { return shipmentId; }
        public String getBatchId() { return batchId; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public DeliveryStatus getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
