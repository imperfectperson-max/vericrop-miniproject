# VeriCrop GUI Features Documentation

## Overview
This document describes the enhanced features implemented in the VeriCrop JavaFX GUI application.

## Architecture

### Core Infrastructure

#### EventBus
- **Location**: `org.vericrop.gui.events.EventBus`
- **Purpose**: Thread-safe publish/subscribe event system for decoupled component communication
- **Features**:
  - Singleton pattern for global access
  - Automatic JavaFX thread dispatching for UI updates
  - Type-safe event subscriptions
  - Multiple subscribers per event type
- **Usage**:
  ```java
  EventBus eventBus = EventBus.getInstance();
  
  // Subscribe to events
  eventBus.subscribe(AlertRaised.class, this::handleAlert, true);
  
  // Publish events
  eventBus.publish(new AlertRaised("Title", "Message", Severity.INFO, "Source"));
  ```

#### NavigationService
- **Location**: `org.vericrop.gui.services.NavigationService`
- **Purpose**: Centralized view navigation and screen management
- **Features**:
  - Singleton pattern
  - FXML loading with error handling
  - Dynamic content area updates
- **Usage**:
  ```java
  NavigationService nav = NavigationService.getInstance();
  nav.navigateTo("producer.fxml");
  ```

#### SessionManager
- **Location**: `org.vericrop.gui.services.SessionManager`
- **Purpose**: User authentication state management
- **Features**:
  - Current user tracking
  - Session token management
  - Arbitrary session data storage
  - Session clearing on logout
- **Usage**:
  ```java
  SessionManager session = SessionManager.getInstance();
  session.setCurrentUser(user);
  boolean loggedIn = session.isLoggedIn();
  session.clearSession();
  ```

### Services

#### AlertService
- **Location**: `org.vericrop.gui.services.AlertService`
- **Purpose**: User notification system with persistent alert history
- **Features**:
  - Transient popup notifications in screen corner
  - Severity-based styling (INFO, WARNING, ERROR, CRITICAL)
  - Alert history tracking
  - Enable/disable toggle
  - Auto-hide after 5 seconds
- **Event Integration**: Listens for `AlertRaised` events
- **Usage**:
  ```java
  AlertService alerts = AlertService.getInstance();
  alerts.setAlertsEnabled(true);
  alerts.raiseAlert("Title", "Message", Severity.WARNING, "Source");
  ```

#### ReportGenerator
- **Location**: `org.vericrop.gui.services.ReportGenerator`
- **Purpose**: Generate reports in CSV and HTML formats
- **Features**:
  - CSV export with proper escaping
  - HTML export with styled tables
  - Pre-built templates for deliveries and messages
  - Publishes `ReportReady` events
- **Usage**:
  ```java
  ReportGenerator generator = ReportGenerator.getInstance();
  
  // Generate CSV
  Path csvPath = generator.generateCSVReport("report_name", headers, rows);
  
  // Generate HTML
  Path htmlPath = generator.generateHTMLReport("report_name", "Title", headers, rows);
  ```

#### DeliverySimulatorService
- **Location**: `org.vericrop.gui.services.DeliverySimulatorService`
- **Purpose**: Simulate delivery routes with environmental monitoring
- **Features**:
  - Multi-waypoint route generation
  - Configurable speed and update intervals
  - Temperature and humidity simulation
  - Real-time location updates
  - Lifecycle events (STARTED, IN_TRANSIT, DELIVERED, STOPPED)
- **Event Integration**: Publishes `DeliveryStatusUpdated` events
- **Usage**:
  ```java
  DeliverySimulatorService simulator = DeliverySimulatorService.getInstance();
  simulator.startSimulation(shipmentId, origin, destination, waypoints, speed, interval);
  simulator.stopSimulation(shipmentId);
  ```

### UI Components

#### AppShell
- **FXML**: `appshell.fxml`
- **Controller**: `org.vericrop.gui.controller.AppShellController`
- **Purpose**: Main application shell with navigation bar and content area
- **Features**:
  - Top navigation bar with logo and title
  - Quick navigation buttons (Dashboard, Analytics, Logistics, Messages)
  - Alert enable/disable toggle
  - User avatar and info display
  - Logout button
  - Dynamic content area for loading views
  - Status bar with connection indicator
- **Styling**: Uses `vericrop.css` for consistent branding

#### Delivery Simulation View
- **FXML**: `delivery_simulation.fxml`
- **Controller**: `org.vericrop.gui.controller.DeliverySimulationController`
- **Purpose**: Interactive delivery simulation control panel
- **Features**:
  - Simulation parameters configuration
    - Shipment ID
    - Origin and destination
    - Number of waypoints (5-50)
    - Average speed (10-200 km/h)
    - Update interval (500-10000 ms)
  - Start/Stop controls
  - Real-time table of active simulations
  - Progress tracking with percentage
  - Event log showing recent updates
  - Auto-generated shipment IDs
- **Real-time Updates**: Subscribes to `DeliveryStatusUpdated` events

### Styling

#### vericrop.css
- **Location**: `src/main/resources/css/vericrop.css`
- **Purpose**: Global application styling
- **Features**:
  - Consistent color scheme (primary green: #2E8B57)
  - Button styles (primary, secondary, success, danger)
  - Card and panel layouts with shadows
  - Form element styling
  - Table and list styling
  - Status badges
  - Chart styling
  - Responsive spacing utilities
  - Navigation bar styling
  - User avatar styling

### Domain Events

All events extend `DomainEvent` base class with:
- Unique event ID (UUID)
- Timestamp (Instant)

#### AlertRaised
- **Purpose**: Notify about system alerts
- **Fields**:
  - title: Alert title
  - message: Detailed message
  - severity: INFO, WARNING, ERROR, CRITICAL
  - source: Component that raised the alert

#### DeliveryStatusUpdated
- **Purpose**: Track delivery lifecycle changes
- **Fields**:
  - shipmentId: Unique shipment identifier
  - status: Current status
  - location: Current location description
  - details: Additional information

#### NewMessage
- **Purpose**: Notify about new messages
- **Fields**:
  - messageId, fromRole, fromId, toRole
  - subject, content

#### ReportReady
- **Purpose**: Notify when report generation completes
- **Fields**:
  - reportId, reportType (CSV/HTML)
  - filePath, fileName

#### AnalyticsUpdated
- **Purpose**: Notify about analytics data changes
- **Fields**:
  - dataType: Type of analytics data
  - data: Updated data object

## Running the Application

### Build and Run

```bash
# Build the project
./gradlew :vericrop-gui:build

# Run the application
./gradlew :vericrop-gui:run

# Or use bootRun
./gradlew :vericrop-gui:bootRun
```

### Login

The application starts with the login screen. After authentication:
- User session is established
- User info displayed in AppShell navbar
- Navigation based on user role

### Logout

Click the "🚪 Logout" button in the top navbar:
- Session cleared
- User redirected to login screen
- No protected views accessible without re-authentication

## Feature Demonstrations

### 1. Delivery Simulation

Navigate to the Delivery Simulation view (can be accessed via navigation or direct FXML loading):

1. Fill in simulation parameters
2. Click "▶️ Start Simulation"
3. Watch real-time updates in the table
4. Observe events in the log
5. Select a simulation and click "⏹️ Stop Simulation"

### 2. Real-time Alerts

Alerts can be raised by any component:
- Delivery simulator raises alerts for temperature out of range
- System components can raise alerts for errors or warnings
- Toggle alerts on/off using the "🔔 Alerts" checkbox in navbar

### 3. Report Generation

Generate reports from any view that integrates ReportGenerator:
- CSV format for data export
- HTML format for readable reports
- Reports saved to `reports/` directory
- ReportReady events published on completion

## Testing

Unit tests are provided for core components:

```bash
# Run all GUI tests
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew :vericrop-gui:test --tests EventBusTest
./gradlew :vericrop-gui:test --tests ReportGeneratorTest
```

Test Coverage:
- ✅ EventBus: Subscribe, publish, multiple subscribers, error handling
- ✅ ReportGenerator: CSV/HTML generation, escaping, custom reports

## Future Enhancements

Planned features:
- Analytics dashboard with charts (LineChart, PieChart, BarChart)
- Unified messaging panel for all user roles
- Persistent alerts view
- Enhanced analytics with real-time updates
- WebSocket integration for server-side events
- PDF report generation (requires external library)

## Architecture Decisions

### Why EventBus?
- Decouples components
- Simplifies communication between services and UI
- Makes testing easier (mock event publishers/subscribers)
- Enables real-time updates without tight coupling

### Why Singleton Services?
- Consistent state across application
- Easy access from any component
- Simplified initialization
- Resource management (single instance of expensive objects)

### Why JavaFX Properties in Models?
- Native TableView binding
- Automatic UI updates when data changes
- Clean separation of model and view

## Troubleshooting

### CSS Not Loading
- Verify `vericrop.css` exists in `src/main/resources/css/`
- Check MainApp applies CSS to Scene
- Use fallback CSS if resources not found

### Events Not Received
- Verify subscription to correct event class
- Check if handler is running on correct thread
- Use `runOnFxThread=true` for UI updates

### Simulation Not Updating
- Check DeliverySimulator thread is running
- Verify EventBus subscriptions
- Check update interval is reasonable (not too fast/slow)

## Credits

VeriCrop GUI Enhancement Implementation
- EventBus and domain events system
- Navigation and session management services
- Alert service with popup notifications
- Report generator (CSV/HTML)
- Delivery simulation integration
- AppShell with unified navigation
- Comprehensive styling with vericrop.css
