# VeriCrop Demo Guide

This guide explains how to run the VeriCrop demonstration simulations optimized for university presentations (3-4 minutes).

## Quick Start

### Prerequisites
- Java 17+
- Gradle 8+
- Recommended screen resolution: 1366x768 or higher

### Running the Application

```bash
# Build the project
./gradlew build -x test

# Run with demo mode enabled (loads sample data)
./gradlew :vericrop-gui:run --args="--load-demo"

# Or set environment variable
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run
```

## Demo Simulations

Three pre-configured demo simulations are available, optimized for presentation timing:

### 1. Quick Demo - 3 Minutes (`demo_short_3min.json`)
- **Duration**: 3 minutes real-time
- **Simulated Journey**: 30 minutes
- **Best For**: Quick demonstrations, time-constrained presentations
- **Features**: 
  - Farm → Distribution Center → Retail Store
  - 10 waypoints with GPS tracking
  - Temperature monitoring (optimal conditions)
  - Full lifecycle: Available → In Transit → Approaching → Completed

### 2. Standard Demo - 4 Minutes (`demo_medium_4min.json`)
- **Duration**: 4 minutes real-time  
- **Simulated Journey**: 48 minutes
- **Best For**: Balanced demonstrations showing temperature variations
- **Features**:
  - Organic farm to premium grocery
  - 13 waypoints with detailed tracking
  - Temperature variation event (highway congestion)
  - Quality decay demonstration

### 3. Full Journey Demo - 4 Minutes (`demo_full_journey.json`)
- **Duration**: 4 minutes real-time
- **Simulated Journey**: 60 minutes
- **Best For**: Comprehensive demonstrations
- **Features**:
  - Complete producer-to-consumer journey
  - 16 waypoints with quality checkpoints
  - Multiple temperature events
  - Quality decay with configurable parameters
  - Alerts and notifications
  - Consumer view with final quality score

## Demo Workflow

### Producer Screen
1. Upload a food image for AI quality analysis
2. Create a batch record on the blockchain
3. Generate QR code for the batch
4. Click **Start Simulation** to begin delivery tracking

### Logistics Screen
1. Navigate to Logistics tab to see:
   - Live route tracking map
   - Temperature chart (updates in real-time)
   - Shipment table with environmental data
   - Timeline showing delivery stages

### Consumer Screen
1. Navigate to Consumer tab to see:
   - Product Journey with lifecycle stages
   - Final quality score (displayed on completion)
   - Verification history

## UI Settings for Laptop Screens

For optimal display on laptop screens (1366x768):

### Enable Compact Mode
Add the `compact` style class to the root element by passing JVM argument:
```bash
./gradlew :vericrop-gui:run --args="--compact"
```

Or programmatically in MainApp.java:
```java
scene.getRoot().getStyleClass().add("compact");
```

### Recommended Window Size
- **Minimum**: 1024x600
- **Recommended**: 1280x720 or 1366x768
- **Optimal**: 1440x900 or higher

## Time Scale Factor

The `timeScale` controls how fast simulations run:

| Factor | 30min Journey | 60min Journey |
|--------|--------------|---------------|
| 10x    | 3 minutes    | 6 minutes     |
| 12x    | 2.5 minutes  | 5 minutes     |
| 15x    | 2 minutes    | 4 minutes     |

Demo simulations use 10x-15x compression for 3-4 minute demonstrations.

## Simulation States

All simulations progress through these states:

1. **Available** (0-5%): Batch created, ready for pickup
2. **In Transit** (5-80%): Active delivery with GPS tracking
3. **Approaching** (80-95%): Nearing destination
4. **Completed** (95-100%): Delivery finished, final quality calculated

## Quality Decay Calculation

Quality degrades based on:
- **Base decay**: 0.5% per simulated hour in ideal conditions
- **Temperature penalty**: 0.3% per degree outside 4-8°C range
- **Humidity penalty**: 0.1% per percent outside 70-85% range

Formula: `finalQuality = initialQuality - (decayRate × hoursElapsed)`

## Troubleshooting

### Simulation Not Starting
- Ensure no other simulation is running
- Check the status label in Producer screen
- Verify SimulationManager is initialized

### Map Not Updating
- Kafka may not be running (graceful fallback to SimulationListener)
- Check console for connection errors

### Temperature Chart Empty
- Wait for simulation to start generating data
- Refresh the Logistics screen

### UI Elements Overlapping
- Enable compact mode for smaller screens
- Increase window size if possible

## API Reference

### Starting a Demo Simulation Programmatically

```java
// Load a demo simulation definition
SimulationDefinition demo = SimulationLoader.loadQuickDemo();

// Create simulation config
SimulationConfig config = demo.toSimulationConfig();

// Start simulation
simulationManager.startSimulation(
    demo.generateBatchId(),
    "Demo Farmer",
    new GeoCoordinate(demo.getOrigin()),
    new GeoCoordinate(demo.getDestination()),
    demo.getWaypointsPerSegment(),
    demo.getSpeedKmh(),
    1000L // update interval
);
```

### Loading Custom Simulations

Place JSON files in `src/main/resources/simulations/` following the schema of existing demo files.

## Contact

For issues or questions about the demo, refer to the main README.md or open a GitHub issue.
