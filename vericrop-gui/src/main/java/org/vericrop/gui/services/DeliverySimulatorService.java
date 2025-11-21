package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MessageService;
import org.vericrop.service.DeliverySimulator.GeoCoordinate;
import org.vericrop.service.DeliverySimulator.RouteWaypoint;
import org.vericrop.service.DeliverySimulator.SimulationStatus;
import org.vericrop.gui.events.EventBus;
import org.vericrop.gui.events.DeliveryStatusUpdated;
import org.vericrop.gui.events.AlertRaised;
import org.vericrop.dto.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI service wrapper for DeliverySimulator from vericrop-core.
 * Bridges simulator events to the GUI EventBus.
 */
public class DeliverySimulatorService {
    private static final Logger logger = LoggerFactory.getLogger(DeliverySimulatorService.class);
    private static DeliverySimulatorService instance;
    
    private final DeliverySimulator simulator;
    private final EventBus eventBus;
    private final MessageService messageService;
    private final Map<String, SimulationMetadata> activeSimulations;
    
    /**
     * Metadata for tracking active simulations.
     */
    public static class SimulationMetadata {
        private final String shipmentId;
        private final String origin;
        private final String destination;
        private final long startTime;
        
        public SimulationMetadata(String shipmentId, String origin, String destination, long startTime) {
            this.shipmentId = shipmentId;
            this.origin = origin;
            this.destination = destination;
            this.startTime = startTime;
        }
        
        public String getShipmentId() { return shipmentId; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public long getStartTime() { return startTime; }
    }
    
    private DeliverySimulatorService() {
        this.messageService = new MessageService(false); // Use in-memory only
        this.simulator = new DeliverySimulator(messageService);
        this.eventBus = EventBus.getInstance();
        this.activeSimulations = new ConcurrentHashMap<>();
        
        // Subscribe to message service events and bridge to EventBus
        startMessageBridge();
        
        logger.info("DeliverySimulatorService initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized DeliverySimulatorService getInstance() {
        if (instance == null) {
            instance = new DeliverySimulatorService();
        }
        return instance;
    }
    
    /**
     * Start a delivery simulation.
     * 
     * @param shipmentId Unique shipment identifier
     * @param origin Origin location
     * @param destination Destination location
     * @param numWaypoints Number of waypoints in the route
     * @param avgSpeedKmh Average speed in km/h
     * @param updateIntervalMs Update interval in milliseconds
     */
    public void startSimulation(String shipmentId, String origin, String destination,
                               int numWaypoints, double avgSpeedKmh, long updateIntervalMs) {
        logger.info("Starting simulation for shipment: {}", shipmentId);
        
        // Create geographic coordinates
        GeoCoordinate originCoord = createRandomCoordinate(origin);
        GeoCoordinate destCoord = createRandomCoordinate(destination);
        
        // Generate route
        List<RouteWaypoint> route = simulator.generateRoute(
            originCoord, destCoord, numWaypoints, 
            System.currentTimeMillis(), avgSpeedKmh);
        
        // Store metadata
        activeSimulations.put(shipmentId, new SimulationMetadata(
            shipmentId, origin, destination, System.currentTimeMillis()));
        
        // Start simulation
        simulator.startSimulation(shipmentId, route, updateIntervalMs);
        
        // Publish initial event
        eventBus.publish(new DeliveryStatusUpdated(
            shipmentId, "STARTED", origin, "Simulation started"));
    }
    
    /**
     * Stop a simulation.
     */
    public void stopSimulation(String shipmentId) {
        logger.info("Stopping simulation for shipment: {}", shipmentId);
        simulator.stopSimulation(shipmentId);
        activeSimulations.remove(shipmentId);
        
        eventBus.publish(new DeliveryStatusUpdated(
            shipmentId, "STOPPED", "", "Simulation stopped"));
    }
    
    /**
     * Get status of a simulation.
     */
    public SimulationStatus getSimulationStatus(String shipmentId) {
        return simulator.getSimulationStatus(shipmentId);
    }
    
    /**
     * Get metadata for an active simulation.
     */
    public SimulationMetadata getSimulationMetadata(String shipmentId) {
        return activeSimulations.get(shipmentId);
    }
    
    /**
     * Get all active simulation IDs.
     */
    public List<String> getActiveSimulations() {
        return List.copyOf(activeSimulations.keySet());
    }
    
    /**
     * Create a coordinate with some randomness for demo purposes.
     */
    private GeoCoordinate createRandomCoordinate(String locationName) {
        // Use simple hash-based coordinates for demo
        int hash = locationName.hashCode();
        double lat = 30.0 + (hash % 30);
        double lon = -100.0 + ((hash / 100) % 50);
        return new GeoCoordinate(lat, lon, locationName);
    }
    
    /**
     * Start a background thread to bridge MessageService to EventBus.
     */
    private void startMessageBridge() {
        Thread bridgeThread = new Thread(() -> {
            logger.debug("Message bridge thread started");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Poll for new messages periodically
                    Thread.sleep(500);
                    
                    List<org.vericrop.dto.Message> messages = messageService.getAllMessages();
                    for (org.vericrop.dto.Message msg : messages) {
                        // Check if this is a simulator message
                        if ("delivery_simulator".equals(msg.getSenderId())) {
                            String subject = msg.getSubject();
                            String content = msg.getContent();
                            
                            // Parse and publish as DeliveryStatusUpdated
                            String shipmentId = msg.getShipmentId();
                            if (shipmentId != null) {
                                eventBus.publish(new DeliveryStatusUpdated(
                                    shipmentId, parseStatus(subject), 
                                    parseLocation(content), content));
                                
                                // Check for alerts
                                if (subject != null && subject.contains("Alert")) {
                                    eventBus.publish(new AlertRaised(
                                        subject, content, 
                                        AlertRaised.Severity.WARNING,
                                        "DeliverySimulator"));
                                }
                            }
                        }
                    }
                    
                    // Clear processed messages to avoid reprocessing
                    // Note: This is a simple approach; production would use message IDs
                    if (!messages.isEmpty()) {
                        messageService.clearAll();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in message bridge", e);
                }
            }
            logger.debug("Message bridge thread stopped");
        });
        bridgeThread.setDaemon(true);
        bridgeThread.setName("DeliverySimulator-MessageBridge");
        bridgeThread.start();
    }
    
    /**
     * Parse status from message subject.
     */
    private String parseStatus(String subject) {
        if (subject == null) return "UNKNOWN";
        if (subject.contains("started")) return "IN_TRANSIT";
        if (subject.contains("Complete")) return "DELIVERED";
        if (subject.contains("Alert")) return "ALERT";
        if (subject.contains("Update")) return "IN_TRANSIT";
        if (subject.contains("stopped")) return "STOPPED";
        return "UNKNOWN";
    }
    
    /**
     * Parse location from message content.
     */
    private String parseLocation(String content) {
        if (content == null) return "";
        // Extract location from "Location: XYZ | ..." format
        if (content.contains("Location:")) {
            int start = content.indexOf("Location:") + 9;
            int end = content.indexOf("|", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return "";
    }
    
    /**
     * Shutdown the simulator.
     */
    public void shutdown() {
        logger.info("Shutting down DeliverySimulatorService");
        simulator.shutdown();
    }
}
