# Delivery Simulator Integration Guide

## Overview

The comprehensive DeliverySimulator service provides:
- Real-time delivery route simulation with environmental monitoring
- Quality decay and spoilage tracking
- Alert generation for threshold breaches
- Supplier performance metrics
- Delivery report generation
- Optional Kafka and Airflow integration

## Core Components

### Model Classes (org.vericrop.service.models)

1. **GeoCoordinate** - Geographic location with latitude, longitude, name
2. **RouteWaypoint** - Point along route with location, temperature, humidity, timestamp
3. **SimulationStatus** - Current status of active simulation
4. **Scenario** - Pre-defined scenarios (NORMAL, HOT_TRANSPORT, COLD_STORAGE, HUMID_ROUTE, EXTREME_DELAY)
5. **Alert** - Alert with type, severity, message, thresholds
6. **DeliveryReport** - Comprehensive delivery report with metrics
7. **SupplierMetrics** - Farmer performance KPIs

### Services

1. **DeliverySimulator** - Main simulation engine
2. **AlertService** - In-memory alert storage and listener management

## Basic Usage

### 1. Initialize Services

```java
MessageService messageService = new MessageService(false);
AlertService alertService = new AlertService();
DeliverySimulator simulator = new DeliverySimulator(messageService, alertService);
```

### 2. Generate a Route

```java
// Using legacy inner classes for backwards compatibility
DeliverySimulator.GeoCoordinate origin = 
    new DeliverySimulator.GeoCoordinate(42.3601, -71.0589, "Farm");
DeliverySimulator.GeoCoordinate destination = 
    new DeliverySimulator.GeoCoordinate(42.3736, -71.1097, "Warehouse");

// Generate route with scenario
List<DeliverySimulator.RouteWaypoint> route = 
    simulator.generateRoute(origin, destination, 10, 
                          System.currentTimeMillis(), 50.0, Scenario.NORMAL);
```

### 3. Start Simulation

```java
String batchId = "BATCH_001";
String farmerId = "FARMER_A";
long updateIntervalMs = 10000; // 10 seconds

simulator.startSimulation(batchId, farmerId, route, 
                         updateIntervalMs, Scenario.NORMAL);
```

### 4. Monitor Status

```java
DeliverySimulator.SimulationStatus status = 
    simulator.getSimulationStatus(batchId);

System.out.println("Running: " + status.isRunning());
System.out.println("Progress: " + status.getCurrentWaypoint() + 
                   "/" + status.getTotalWaypoints());
```

### 5. Generate Report

```java
DeliveryReport report = simulator.generateDeliveryReport(batchId);
System.out.println("Final Quality: " + report.getFinalQualityScore());
System.out.println("Spoilage Probability: " + report.getSpoilageProbability());
System.out.println("Alerts: " + report.getAlerts().size());

// Export to JSON
simulator.exportReportToJson(report, "/tmp/reports/delivery_" + batchId + ".json");
```

### 6. Stop Simulation

```java
simulator.stopSimulation(batchId);
```

### 7. Cleanup

```java
simulator.cleanup();
```

## Alert Handling

### Listen to Alerts

```java
alertService.addListener(alert -> {
    System.out.println("Alert: " + alert.getType() + " - " + alert.getMessage());
    
    // Take action based on alert
    if (alert.getSeverity() == Alert.Severity.CRITICAL) {
        // Handle critical alert
    }
});
```

### Get Alerts for a Batch

```java
List<Alert> batchAlerts = alertService.getAlertsByBatch(batchId);
```

## Kafka Integration

The DeliverySimulator doesn't have a hard dependency on Kafka to keep vericrop-core standalone.
Instead, integrate via AlertService listeners in your GUI/API layer:

```java
// In ProducerController or similar
if (logisticsProducer != null && qualityAlertProducer != null) {
    alertService.addListener(alert -> {
        try {
            switch (alert.getType()) {
                case TEMPERATURE_HIGH:
                case TEMPERATURE_LOW:
                case HUMIDITY_HIGH:
                case HUMIDITY_LOW:
                    QualityAlertEvent qae = new QualityAlertEvent(
                        alert.getBatchId(),
                        alert.getType().name(),
                        alert.getSeverity().name(),
                        alert.getMessage(),
                        alert.getCurrentValue(),
                        alert.getThresholdValue()
                    );
                    qae.setLocation(alert.getLocation());
                    qae.setTimestamp(alert.getTimestamp());
                    qualityAlertProducer.sendQualityAlert(qae);
                    break;
                    
                case DELIVERY_DELAY:
                case SPOILAGE_RISK:
                    LogisticsEvent le = new LogisticsEvent(
                        alert.getBatchId(),
                        "ALERT_" + alert.getType().name(),
                        0, 0,
                        alert.getLocation()
                    );
                    le.setTimestamp(alert.getTimestamp());
                    logisticsProducer.sendLogisticsEvent(le);
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to publish alert to Kafka", e);
        }
    });
}
```

## Airflow Integration

Configure Airflow webhook via system property:

```bash
-Dvericrop.airflow.hook=http://airflow-server:8080/api/v1/dags/vericrop_alerts/dagRuns
```

The simulator will automatically POST critical events (spoilage > 50%, delays > 30min) to this endpoint.

Payload format:
```json
{
  "batch_id": "BATCH_001",
  "event_type": "CRITICAL_SPOILAGE",
  "value": 52.5,
  "timestamp": 1700000000000
}
```

## Deterministic Testing

For repeatable test scenarios, set a random seed:

```bash
-Dvericrop.sim.seed=12345
```

## Supplier Performance Metrics

```java
// Get metrics for specific farmer
SupplierMetrics metrics = simulator.getSupplierMetrics("FARMER_A");
System.out.println("Total Deliveries: " + metrics.getTotalDeliveries());
System.out.println("Success Rate: " + (metrics.getSuccessRate() * 100) + "%");
System.out.println("Avg Quality Decay: " + metrics.getAverageQualityDecay() + "%");

// Get all supplier metrics
List<SupplierMetrics> allMetrics = simulator.getAllSupplierMetrics();
```

## Pre-defined Scenarios

### NORMAL
- Stable temperature around ideal (5°C)
- Normal humidity (~80%)
- No delays
- Low spoilage rate (1% per hour)

### HOT_TRANSPORT
- Temperature +8°C drift
- Increased spoilage risk (5% per hour)
- Good for testing heat stress

### COLD_STORAGE
- Temperature -5°C drift
- Risk of freezing damage
- 2% spoilage rate

### HUMID_ROUTE
- Normal temperature
- +15% humidity drift
- Mold and decay risk (4% per hour)

### EXTREME_DELAY
- Normal environmental conditions
- 60% speed reduction (major delays)
- 6% spoilage rate due to extended time

## Demo Program

Run the included demo to see all scenarios:

```bash
./gradlew :vericrop-core:compileJava
java -cp vericrop-core/build/classes/java/main org.vericrop.service.DeliverySimulatorDemo
```

## Backwards Compatibility

The simulator maintains backwards compatibility with existing code through deprecated inner classes:
- `DeliverySimulator.GeoCoordinate`
- `DeliverySimulator.RouteWaypoint`
- `DeliverySimulator.SimulationStatus`

New code should use the model classes in `org.vericrop.service.models` package.

## Thread Safety

- DeliverySimulator is thread-safe and supports multiple concurrent simulations
- AlertService uses concurrent collections for thread-safe alert storage
- Alert listeners are called synchronously, so avoid blocking operations

## Error Handling

The simulator is defensive and continues operation even if optional components fail:
- Kafka producers unavailable: Logs warning, continues
- Airflow endpoint unreachable: Logs warning, continues
- Message service unavailable: Logs error, continues

This ensures simulation robustness in production environments.
