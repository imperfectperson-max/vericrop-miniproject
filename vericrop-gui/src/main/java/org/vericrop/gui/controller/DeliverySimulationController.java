package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.events.DeliveryStatusUpdated;
import org.vericrop.gui.events.EventBus;
import org.vericrop.gui.models.SimulationRecord;
import org.vericrop.gui.services.DeliverySimulatorService;
import org.vericrop.service.DeliverySimulator.SimulationStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Delivery Simulation view.
 */
public class DeliverySimulationController {
    private static final Logger logger = LoggerFactory.getLogger(DeliverySimulationController.class);
    
    @FXML private TextField shipmentIdField;
    @FXML private TextField originField;
    @FXML private TextField destinationField;
    @FXML private Spinner<Integer> waypointsSpinner;
    @FXML private Spinner<Double> speedSpinner;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Label statusLabel;
    @FXML private TableView<SimulationRecord> simulationsTable;
    @FXML private ListView<String> eventsListView;
    
    private DeliverySimulatorService simulatorService;
    private EventBus eventBus;
    private ObservableList<SimulationRecord> simulations;
    private ObservableList<String> events;
    private Map<String, SimulationRecord> simulationMap;
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    
    @FXML
    public void initialize() {
        logger.info("Initializing DeliverySimulationController");
        
        // Get services
        simulatorService = DeliverySimulatorService.getInstance();
        eventBus = EventBus.getInstance();
        
        // Initialize collections
        simulations = FXCollections.observableArrayList();
        events = FXCollections.observableArrayList();
        simulationMap = new HashMap<>();
        
        // Bind to UI
        simulationsTable.setItems(simulations);
        eventsListView.setItems(events);
        
        // Configure spinners
        waypointsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 50, 10));
        speedSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(10.0, 200.0, 60.0, 10.0));
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(500, 10000, 2000, 500));
        
        // Set default values
        shipmentIdField.setText("SHIP-" + System.currentTimeMillis() % 10000);
        originField.setText("Farm Location A");
        destinationField.setText("Warehouse B");
        
        // Subscribe to delivery events
        eventBus.subscribe(DeliveryStatusUpdated.class, this::handleDeliveryStatusUpdate, true);
        
        logger.info("DeliverySimulationController initialized");
    }
    
    @FXML
    private void handleStartSimulation() {
        try {
            // Validate inputs
            String shipmentId = shipmentIdField.getText().trim();
            String origin = originField.getText().trim();
            String destination = destinationField.getText().trim();
            
            if (shipmentId.isEmpty() || origin.isEmpty() || destination.isEmpty()) {
                updateStatus("Error: All fields are required");
                showAlert("Validation Error", "Please fill in all fields", Alert.AlertType.WARNING);
                return;
            }
            
            // Check if simulation already exists
            if (simulationMap.containsKey(shipmentId)) {
                updateStatus("Error: Simulation already exists for " + shipmentId);
                showAlert("Duplicate Shipment", 
                         "A simulation for " + shipmentId + " is already running", 
                         Alert.AlertType.WARNING);
                return;
            }
            
            // Get parameters
            int waypoints = waypointsSpinner.getValue();
            double speed = speedSpinner.getValue();
            int interval = intervalSpinner.getValue();
            
            // Start simulation
            simulatorService.startSimulation(shipmentId, origin, destination, waypoints, speed, interval);
            
            // Add to table
            SimulationRecord record = new SimulationRecord(
                shipmentId, "STARTED", origin, destination, origin, "0%", System.currentTimeMillis());
            simulations.add(record);
            simulationMap.put(shipmentId, record);
            
            // Add event
            addEvent(String.format("[%s] Started simulation: %s -> %s", 
                    getCurrentTime(), origin, destination));
            
            // Update UI
            updateStatus("Simulation started: " + shipmentId);
            startButton.setDisable(false);
            stopButton.setDisable(false);
            
            // Generate new shipment ID for next simulation
            shipmentIdField.setText("SHIP-" + System.currentTimeMillis() % 10000);
            
            logger.info("Started simulation: {}", shipmentId);
            
        } catch (Exception e) {
            logger.error("Failed to start simulation", e);
            updateStatus("Error starting simulation: " + e.getMessage());
            showAlert("Error", "Failed to start simulation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleStopSimulation() {
        SimulationRecord selected = simulationsTable.getSelectionModel().getSelectedItem();
        
        if (selected == null) {
            updateStatus("Error: No simulation selected");
            showAlert("No Selection", "Please select a simulation to stop", Alert.AlertType.WARNING);
            return;
        }
        
        String shipmentId = selected.getShipmentId();
        
        try {
            // Stop simulation
            simulatorService.stopSimulation(shipmentId);
            
            // Update record
            selected.setStatus("STOPPED");
            
            // Add event
            addEvent(String.format("[%s] Stopped simulation: %s", 
                    getCurrentTime(), shipmentId));
            
            // Update UI
            updateStatus("Simulation stopped: " + shipmentId);
            
            logger.info("Stopped simulation: {}", shipmentId);
            
        } catch (Exception e) {
            logger.error("Failed to stop simulation", e);
            updateStatus("Error stopping simulation: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleClearAll() {
        // Stop all simulations
        for (SimulationRecord record : simulations) {
            try {
                simulatorService.stopSimulation(record.getShipmentId());
            } catch (Exception e) {
                logger.warn("Error stopping simulation {}", record.getShipmentId(), e);
            }
        }
        
        // Clear UI
        simulations.clear();
        simulationMap.clear();
        events.clear();
        
        updateStatus("All simulations cleared");
        addEvent(String.format("[%s] All simulations cleared", getCurrentTime()));
        
        logger.info("Cleared all simulations");
    }
    
    /**
     * Handle delivery status update events.
     */
    private void handleDeliveryStatusUpdate(DeliveryStatusUpdated event) {
        Platform.runLater(() -> {
            String shipmentId = event.getShipmentId();
            SimulationRecord record = simulationMap.get(shipmentId);
            
            if (record != null) {
                // Update record
                record.setStatus(event.getStatus());
                record.setCurrentLocation(event.getLocation());
                
                // Update progress
                SimulationStatus status = simulatorService.getSimulationStatus(shipmentId);
                if (status != null && status.getTotalWaypoints() > 0) {
                    int progress = (status.getCurrentWaypoint() * 100) / status.getTotalWaypoints();
                    record.setProgress(progress + "%");
                }
                
                // Refresh table
                simulationsTable.refresh();
            }
            
            // Add event to log
            addEvent(String.format("[%s] %s: %s - %s", 
                    getCurrentTime(), shipmentId, event.getStatus(), event.getDetails()));
            
            // Keep only last 50 events
            if (events.size() > 50) {
                events.remove(0);
            }
        });
    }
    
    /**
     * Add an event to the events list.
     */
    private void addEvent(String event) {
        events.add(event);
        // Auto-scroll to bottom
        eventsListView.scrollTo(events.size() - 1);
    }
    
    /**
     * Update the status label.
     */
    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + message);
        }
    }
    
    /**
     * Get current time as formatted string.
     */
    private String getCurrentTime() {
        return TIME_FORMATTER.format(Instant.now());
    }
    
    /**
     * Show an alert dialog.
     */
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
