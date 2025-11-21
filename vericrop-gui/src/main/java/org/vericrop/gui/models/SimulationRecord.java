package org.vericrop.gui.models;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Model class for displaying simulation records in the table.
 */
public class SimulationRecord {
    private final StringProperty shipmentId;
    private final StringProperty status;
    private final StringProperty origin;
    private final StringProperty destination;
    private final StringProperty currentLocation;
    private final StringProperty progress;
    private final StringProperty startTime;
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    
    public SimulationRecord(String shipmentId, String status, String origin, 
                           String destination, String currentLocation, 
                           String progress, long startTimeMillis) {
        this.shipmentId = new SimpleStringProperty(shipmentId);
        this.status = new SimpleStringProperty(status);
        this.origin = new SimpleStringProperty(origin);
        this.destination = new SimpleStringProperty(destination);
        this.currentLocation = new SimpleStringProperty(currentLocation);
        this.progress = new SimpleStringProperty(progress);
        this.startTime = new SimpleStringProperty(
            TIME_FORMATTER.format(Instant.ofEpochMilli(startTimeMillis)));
    }
    
    // Property getters (required for TableView binding)
    public StringProperty shipmentIdProperty() { return shipmentId; }
    public StringProperty statusProperty() { return status; }
    public StringProperty originProperty() { return origin; }
    public StringProperty destinationProperty() { return destination; }
    public StringProperty currentLocationProperty() { return currentLocation; }
    public StringProperty progressProperty() { return progress; }
    public StringProperty startTimeProperty() { return startTime; }
    
    // Value getters
    public String getShipmentId() { return shipmentId.get(); }
    public String getStatus() { return status.get(); }
    public String getOrigin() { return origin.get(); }
    public String getDestination() { return destination.get(); }
    public String getCurrentLocation() { return currentLocation.get(); }
    public String getProgress() { return progress.get(); }
    public String getStartTime() { return startTime.get(); }
    
    // Value setters
    public void setStatus(String value) { status.set(value); }
    public void setCurrentLocation(String value) { currentLocation.set(value); }
    public void setProgress(String value) { progress.set(value); }
}
