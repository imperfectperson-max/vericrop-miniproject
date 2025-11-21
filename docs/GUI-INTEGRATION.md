# VeriCrop GUI Integration Guide

This document describes the comprehensive GUI integration features added to VeriCrop, including navigation, delivery simulation, real-time alerts, messaging, and analytics.

## Table of Contents

- [Overview](#overview)
- [Services Architecture](#services-architecture)
- [Delivery Simulation](#delivery-simulation)
- [Real-Time Alerts](#real-time-alerts)
- [Messaging System](#messaging-system)
- [Report Generation](#report-generation)
- [Navigation](#navigation)
- [Usage Examples](#usage-examples)
- [Testing](#testing)

## Overview

The GUI integration provides a complete end-to-end experience for all user types (farmers, quality inspectors, shippers, retailers, admins) with the following key features:

- **Centralized Navigation**: Single service manages all screen transitions and session state
- **Delivery Simulation**: Real-time simulation of delivery lifecycle events
- **Alerts System**: Role-based alerts with severity filtering
- **Enhanced Messaging**: User-to-user messaging with unread counts
- **Report Generation**: CSV/PDF exports for quality, delivery, and traceability
- **Supply Chain Dashboard**: Real-time shipment tracking and visualization

## Services Architecture

All services follow the **singleton pattern** for easy global access and are initialized through `ApplicationContext`.

### Core Services

1. **NavigationService**: Screen navigation and session management
2. **DeliverySimulationService**: Delivery lifecycle simulation
3. **AlertsService**: Real-time alert management
4. **MessageServiceEnhanced**: Enhanced messaging wrapper
5. **ReportGenerationService**: Report exports

### Service Initialization

Services are automatically initialized in `ApplicationContext`:

```java
ApplicationContext appContext = ApplicationContext.getInstance();
NavigationService nav = appContext.getNavigationService();
DeliverySimulationService sim = appContext.getDeliverySimulationService();
AlertsService alerts = appContext.getAlertsService();
```

## Delivery Simulation

### Overview

The `DeliverySimulationService` simulates the full delivery lifecycle with configurable timings and events.

### Delivery Lifecycle

1. **CREATED**: Shipment created and awaiting pickup
2. **PICKED_UP**: Package picked up from origin
3. **IN_TRANSIT**: Package in transit to destination
4. **DELAYED** (optional): Delivery delayed due to conditions
5. **DELIVERED**: Package delivered successfully

### Usage

#### Start a Shipment

```java
DeliverySimulationService service = DeliverySimulationService.getInstance();
String shipmentId = service.startShipment("BATCH-123", "Farm A", "Store B");
```

#### Listen to Events

```java
service.addEventListener(event -> {
    System.out.println("Shipment: " + event.getShipmentId());
    System.out.println("Status: " + event.getStatus());
    System.out.println("Message: " + event.getMessage());
    System.out.println("Timestamp: " + event.getTimestamp());
});
```

#### Seed Test Data

```java
// Create 5 test shipments
List<String> shipmentIds = service.seedTestShipments(5);
```

#### Configure Timing

```java
// Set custom timing (in seconds)
service.setTimingConfig(
    30,  // pickup delay
    60,  // in-transit delay
    90,  // delivery delay
    0.15 // delay probability (15%)
);

// Set simulation speed
service.setSimulationSpeed(10); // 10x faster than real-time
```

### Integration with UI

The LogisticsController automatically subscribes to simulation events and updates the UI in real-time:

```java
deliverySimulationService.addEventListener(event -> {
    Platform.runLater(() -> {
        // Update shipments table
        updateShipmentFromEvent(event);
        
        // Add alert to list
        String timestamp = event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        alerts.add(0, String.format("[%s] %s: %s", 
            timestamp, event.getShipmentId(), event.getMessage()));
    });
});
```

## Real-Time Alerts

### Overview

The `AlertsService` manages real-time alerts with severity levels and role-based targeting.

### Alert Severity Levels

- **INFO**: Informational messages
- **WARNING**: Warnings requiring attention
- **ERROR**: Errors requiring immediate action
- **CRITICAL**: Critical system-wide issues

### Usage

#### Create an Alert

```java
AlertsService alerts = AlertsService.getInstance();

// General alert (all users)
alerts.createAlert(
    "Temperature Warning",
    "Shipment SHIP-123 temperature at 7.5°C",
    AlertSeverity.WARNING,
    "TemperatureMonitor"
);

// Role-specific alert
alerts.createAlert(
    "Quality Issue",
    "Batch BATCH-456 below threshold",
    AlertSeverity.ERROR,
    "QualityControl",
    "FARMER" // Only farmers see this
);
```

#### Listen to Alerts

```java
alerts.addAlertListener(alert -> {
    System.out.println(alert.getTitle());
    System.out.println(alert.getMessage());
    System.out.println("Severity: " + alert.getSeverity());
});
```

#### Filter Alerts

```java
// Get by severity
List<Alert> warnings = alerts.getAlertsBySeverity(AlertSeverity.WARNING);

// Get by role
List<Alert> farmerAlerts = alerts.getAlertsByRole("FARMER");

// Get unread alerts
List<Alert> unread = alerts.getUnreadAlerts();
int unreadCount = alerts.getUnreadCount();
```

#### Mark as Read

```java
// Mark specific alert as read
alerts.markAsRead(alertId);

// Mark all as read
alerts.markAllAsRead();
```

### Specialized Alerts

#### Quality Alert

```java
alerts.createQualityAlert("BATCH-123", 0.55, "Low quality detected");
// Creates ERROR or WARNING alert based on score
```

#### Temperature Alert

```java
alerts.createTemperatureAlert("SHIP-456", 8.5, 5.0);
// Creates ERROR alert for temperature breach
```

## Messaging System

### Overview

The `MessageServiceEnhanced` wraps `MessageDao` and provides enhanced functionality with current user context.

### Usage

#### Initialize

```java
MessageServiceEnhanced msgService = MessageServiceEnhanced.getInstance();
msgService.initialize(messageDao);
msgService.setCurrentUser(currentUser);
```

#### Send a Message

```java
Message message = msgService.sendMessage(
    recipientUserId,
    "Subject",
    "Message body"
);
```

#### Get Messages

```java
// Inbox (received messages)
List<Message> inbox = msgService.getInbox();

// Sent messages
List<Message> sent = msgService.getSentMessages();

// Unread count
int unreadCount = msgService.getUnreadCount();
```

#### Message Operations

```java
// Get specific message
Message msg = msgService.getMessage(messageId);

// Mark as read
msgService.markAsRead(messageId);

// Delete (soft delete)
msgService.deleteMessage(messageId);

// Get conversation
List<Message> conversation = msgService.getConversation(otherUserId);
```

## Report Generation

### Overview

The `ReportGenerationService` generates CSV reports for quality, delivery, and traceability.

### Usage

#### Quality Report

```java
ReportGenerationService reports = ReportGenerationService.getInstance();

Map<String, Object> data = new HashMap<>();
data.put("batchName", "Organic Apples Batch 1");
data.put("farmer", "John Doe");
data.put("qualityScore", 0.92);
data.put("qualityLabel", "Fresh");
// ... add more data

String filepath = reports.generateQualityReportCSV("BATCH-123", data);
System.out.println("Report saved to: " + filepath);
```

#### Delivery Report

```java
List<Map<String, Object>> deliveries = new ArrayList<>();
// Add delivery records...

String filepath = reports.generateDeliveryReportCSV(deliveries);
```

#### Traceability Report

```java
List<Map<String, Object>> events = new ArrayList<>();
// Add supply chain events...

String filepath = reports.generateTraceabilityReportCSV("BATCH-123", events);
```

#### Analytics Summary

```java
Map<String, Object> kpis = new HashMap<>();
kpis.put("totalBatches", 1247);
kpis.put("avgQuality", 0.87);
kpis.put("passRate", 95.5);
kpis.put("avgDeliveryTime", 48.5);
// ... add more KPIs

String filepath = reports.generateAnalyticsSummaryCSV(kpis);
```

#### Configure Report Directory

```java
reports.setReportDirectory("/custom/path/to/reports");

// Open reports directory in file explorer
reports.openReportDirectory();
```

## Navigation

### Overview

The `NavigationService` centralizes all screen navigation and session management.

### Usage

#### Initialize

```java
NavigationService nav = NavigationService.getInstance();
nav.initialize(primaryStage);
```

#### Navigate to Screens

```java
// Predefined screen methods
nav.navigateToLogin();
nav.navigateToRegister();
nav.navigateToProducer();
nav.navigateToLogistics();
nav.navigateToConsumer();
nav.navigateToAnalytics();
nav.navigateToInbox();

// Generic navigation
nav.navigateTo("custom.fxml");
```

#### Session Management

```java
// Set current user after login
nav.setCurrentUser(user);

// Get current user
User currentUser = nav.getCurrentUser();

// Check if logged in
boolean loggedIn = nav.isLoggedIn();

// Logout (clears session and returns to login)
nav.logout();
```

#### Role-Based Navigation

```java
// Navigate to appropriate dashboard based on user role
nav.navigateToRoleDashboard(user.getRole());
// ADMIN → Analytics
// FARMER/PRODUCER → Producer
// SUPPLIER/SHIPPER → Logistics
// CONSUMER/RETAILER → Consumer
```

## Usage Examples

### Complete Workflow Example

```java
// 1. Initialize services
ApplicationContext appContext = ApplicationContext.getInstance();
NavigationService nav = appContext.getNavigationService();
DeliverySimulationService sim = appContext.getDeliverySimulationService();
AlertsService alerts = appContext.getAlertsService();

// 2. Set up alert listener
alerts.addAlertListener(alert -> {
    System.out.println("New alert: " + alert.getTitle());
    // Update UI badge count
});

// 3. Set up simulation listener
sim.addEventListener(event -> {
    System.out.println("Delivery event: " + event.getStatus());
    // Update UI shipments table
});

// 4. Start simulation
sim.setSimulationSpeed(10); // 10x faster for demo
sim.seedTestShipments(3); // Create 3 test shipments

// 5. Create custom alert
alerts.createAlert(
    "System Ready",
    "All services initialized successfully",
    AlertSeverity.INFO,
    "System"
);

// 6. Navigate based on user role
User user = nav.getCurrentUser();
if (user != null) {
    nav.navigateToRoleDashboard(user.getRole());
}
```

### Controller Integration Example

```java
public class MyController extends BaseController {
    
    @Override
    protected void initializeController() {
        // Services already initialized in BaseController
        
        // Subscribe to simulation events
        deliverySimulationService.addEventListener(this::handleDeliveryEvent);
        
        // Subscribe to alerts
        alertsService.addAlertListener(this::handleAlert);
        
        // Load initial data
        loadData();
    }
    
    private void handleDeliveryEvent(DeliveryEvent event) {
        Platform.runLater(() -> {
            // Update UI
            statusLabel.setText("Status: " + event.getStatus());
        });
    }
    
    private void handleAlert(Alert alert) {
        Platform.runLater(() -> {
            // Show toast notification
            showNotification(alert.getTitle(), alert.getMessage());
        });
    }
    
    @FXML
    private void handleExportReport() {
        Map<String, Object> data = collectReportData();
        String filepath = reportGenerationService.generateQualityReportCSV(batchId, data);
        
        if (filepath != null) {
            showInfo("Export Complete", "Report saved to: " + filepath);
        }
    }
}
```

## Testing

### Unit Tests

Unit tests are provided for core services:

- `DeliverySimulationServiceTest`: 9 tests covering shipment lifecycle, events, and configuration
- `AlertsServiceTest`: 13 tests covering alert creation, filtering, and management

### Running Tests

```bash
# Run all GUI tests
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew :vericrop-gui:test --tests DeliverySimulationServiceTest
./gradlew :vericrop-gui:test --tests AlertsServiceTest
```

**Note**: Tests require JavaFX platform initialization and may need headless configuration for CI/CD environments.

### Manual Testing

1. **Start the Application**:
   ```bash
   ./gradlew :vericrop-gui:run
   ```

2. **Test Delivery Simulation**:
   - Navigate to Logistics dashboard
   - Click "Seed Test Data" to create test shipments
   - Observe real-time status updates in the shipments table
   - Check alerts list for delivery events

3. **Test Alerts**:
   - Navigate to any dashboard
   - Observe alert badge count in navigation bar
   - Trigger events that create alerts (delays, quality issues)

4. **Test Messaging**:
   - Navigate to Inbox
   - Send a message to another user
   - Check message badge count updates

5. **Test Reports**:
   - Navigate to Logistics or Analytics
   - Click "Export Report"
   - Verify CSV file is created in reports directory

## Troubleshooting

### Services Not Initialized

**Problem**: Services return null or throw NullPointerException

**Solution**: Ensure `ApplicationContext.getInstance()` is called before accessing services

```java
// Always initialize ApplicationContext first
ApplicationContext appContext = ApplicationContext.getInstance();
```

### UI Not Updating

**Problem**: Events received but UI doesn't update

**Solution**: Ensure Platform.runLater is used for UI updates from background threads

```java
service.addEventListener(event -> {
    Platform.runLater(() -> {
        // Update UI here
        label.setText(event.getMessage());
    });
});
```

### Simulation Events Not Firing

**Problem**: Shipments created but no events received

**Solution**: Verify event listener is registered before starting shipment

```java
// Register listener first
sim.addEventListener(this::handleEvent);

// Then start shipment
String shipmentId = sim.startShipment(...);
```

### Reports Not Generated

**Problem**: Export button doesn't create report file

**Solution**: Check logs for errors and verify report directory permissions

```java
// Set custom directory if needed
reportGenerationService.setReportDirectory("/writable/path");

// Check the default directory
String dir = reportGenerationService.getReportDirectory();
System.out.println("Reports directory: " + dir);
```

## Best Practices

1. **Use Services via ApplicationContext**: Always get services from `ApplicationContext.getInstance()` rather than creating new instances

2. **Clean Up Listeners**: Remove event listeners when controllers are destroyed to prevent memory leaks

3. **Use Platform.runLater**: Always wrap UI updates from background threads in `Platform.runLater()`

4. **Handle Errors Gracefully**: Check for null returns and handle exceptions appropriately

5. **Log Important Events**: Use SLF4J logger for debugging and monitoring

6. **Test with Simulation Speed**: Use `setSimulationSpeed()` to speed up testing during development

## Future Enhancements

Potential improvements for future iterations:

- **Persistent Alerts View**: Dedicated screen for viewing and managing all alerts
- **Toast Notifications**: Visual popup notifications for important alerts
- **Map View**: Interactive map showing shipment locations
- **Advanced Analytics**: Real-time charts and KPIs dashboard
- **PDF Reports**: Add PDF generation alongside CSV
- **Email Notifications**: Send alerts and reports via email
- **Mobile App Integration**: Extend services to mobile platforms

---

**For more information, see:**
- [Main README](../README.md)
- [GUI Setup Guide](GUI-setup.md)
- [API Documentation](../vericrop-gui/README.md)
