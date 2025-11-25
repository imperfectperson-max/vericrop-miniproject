# Simulation Testing Guide

This document describes how to test the integrated simulation functionality, including map simulation, scenario management, and active shipments tracking.

## Prerequisites

Before testing, ensure you have:
- Java 17 or higher
- Gradle 8.x
- Python 3.11+ (for ML service, optional)
- PostgreSQL (optional, can run in demo mode)
- Kafka (optional, can run without messaging)

## Build and Run Tests

### 1. Build the Project

```bash
./gradlew clean build
```

Expected output: `BUILD SUCCESSFUL`

### 2. Run Unit Tests

```bash
./gradlew test
```

Expected output: All tests pass

### 3. Run Integration Tests

```bash
./gradlew :vericrop-core:test --tests "*Integration*"
```

This runs integration tests including:
- `SimulationIntegrationTest`: End-to-end simulation flow
- `MapSimulationIntegrationTest`: Map simulation with scenarios
- `SimulationOrchestratorIntegrationTest`: Concurrent scenario orchestration

## Manual Testing

### Option 1: GUI Testing (Recommended)

**Step 1: Start the Application**

```bash
# With demo data
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run

# Or without demo data
./gradlew :vericrop-gui:run
```

**Step 2: Navigate to Producer Screen**
- Login or skip login (in demo mode)
- Go to "Producer" screen
- Upload an image for quality assessment
- Fill in batch details (name, farmer, product type, quantity)
- Click "Create Batch"

**Step 3: Start Simulation**
- Click "Start Simulation" button
- Select a batch from dropdown (or use current batch)
- Simulation starts automatically

**Step 4: View in Logistics Screen**
- Navigate to "Logistics" screen
- Observe:
  - Shipments table shows active shipment with real-time updates
  - Map visualization shows moving vehicle icon
  - Alerts list shows environmental alerts (temperature, humidity)
  - Progress updates every 2 seconds

**Step 5: Stop Simulation**
- Click "Stop Simulation" in Producer screen
- Or wait for simulation to complete naturally

**Expected Behavior:**
- ✅ Simulation starts and batch ID is displayed
- ✅ Progress updates appear in real-time
- ✅ Map shows vehicle moving from origin to destination
- ✅ Temperature and humidity readings update
- ✅ Alerts generated for environmental violations
- ✅ Simulation stops cleanly

### Option 2: REST API Testing

**Step 1: Start Spring Boot Application**

```bash
./gradlew :vericrop-gui:bootRun
```

Wait for application to start (look for "Started VeriCropApiApplication")

**Step 2: Query Available Scenarios**

```bash
curl http://localhost:8080/api/simulation/scenarios
```

Expected response:
```json
{
  "scenarios": [
    {
      "scenario_id": "scenario-01",
      "scenario_name": "NORMAL",
      "display_name": "Normal",
      "description": "Normal cold-chain: target 2-5 C, short spike allowed",
      "parameters": {...},
      "initial_map_setup": {...}
    },
    {
      "scenario_id": "scenario-02",
      "scenario_name": "COLD_STORAGE",
      "display_name": "Cold Storage",
      "description": "Strict cold-chain: target 1-4 C, no spikes",
      "parameters": {...},
      "initial_map_setup": {...}
    },
    {
      "scenario_id": "scenario-03",
      "scenario_name": "HOT_TRANSPORT",
      "display_name": "Hot Transport",
      "description": "High-risk delivery: target 0-6 C, multiple temperature events",
      "parameters": {...},
      "initial_map_setup": {...}
    }
  ],
  "default_scenario": "scenario-01"
}
```

**Step 3: Get Specific Scenario Info**

```bash
curl http://localhost:8080/api/simulation/scenarios/scenario-02
```

**Step 4: Start Simulation (via GUI Required)**

Note: The REST API `/api/simulation/start` endpoint provides information but requires the GUI ProducerController to actually start the simulation with full integration.

Start simulation via GUI as described above, then query:

```bash
curl http://localhost:8080/api/simulation/active-shipments
```

Expected response when simulation is running:
```json
{
  "active_shipments": [
    {
      "batch_id": "BATCH_1732442123456",
      "running": true,
      "current_waypoint": 8,
      "total_waypoints": 20,
      "progress_percent": 40.0,
      "current_location": {
        "name": "Highway Rest Stop",
        "temperature": 4.5,
        "humidity": 65.0,
        "timestamp": 1732442123456
      },
      "farmer_id": "John Doe",
      "progress_manager": 40.0,
      "current_location_name": "Highway Rest Stop"
    }
  ],
  "count": 1,
  "timestamp": 1732442123456,
  "simulation_running": true
}
```

**Step 5: Get Map State**

```bash
curl http://localhost:8080/api/simulation/map
```

Expected response:
```json
{
  "timestamp": 1732442123456,
  "simulation_step": 8,
  "grid_width": 20,
  "grid_height": 20,
  "scenario_id": "NORMAL",
  "entities": [
    {
      "id": "producer-BATCH_1732442123456",
      "type": "PRODUCER",
      "x": 2,
      "y": 10,
      "metadata": {
        "batch_id": "BATCH_1732442123456",
        "scenario": "NORMAL"
      }
    },
    {
      "id": "vehicle-BATCH_1732442123456",
      "type": "DELIVERY_VEHICLE",
      "x": 12,
      "y": 10,
      "metadata": {
        "batch_id": "BATCH_1732442123456",
        "current_waypoint": 8,
        "phase": "In transit - midpoint",
        "progress": 40.0
      }
    },
    {
      "id": "warehouse-central",
      "type": "WAREHOUSE",
      "x": 10,
      "y": 10,
      "metadata": {
        "capacity": 1000,
        "temperature_controlled": true
      }
    },
    {
      "id": "consumer-BATCH_1732442123456",
      "type": "CONSUMER",
      "x": 17,
      "y": 10,
      "metadata": {
        "batch_id": "BATCH_1732442123456",
        "scenario": "NORMAL"
      }
    }
  ],
  "metadata": {}
}
```

**Step 6: Get Simulation Status**

```bash
curl http://localhost:8080/api/simulation/status
```

**Step 7: Health Check**

```bash
curl http://localhost:8080/api/simulation/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "simulation-api",
  "timestamp": 1732442123456
}
```

## Scenario Testing

### Test Scenario 1: Normal Cold-Chain (scenario-01)

**Setup:**
- Start simulation via GUI
- Uses default scenario-01

**Expected Behavior:**
- Temperature range: 2-5°C with occasional spikes
- One temperature spike around minute 12 (10°C for 2 minutes)
- Simulation duration: ~30 minutes
- Alerts generated for spike event
- Map shows smooth progression

**Verification:**
```bash
# Monitor active shipments
watch -n 2 'curl -s http://localhost:8080/api/simulation/active-shipments | jq .active_shipments[0].current_location.temperature'
```

### Test Scenario 2: Strict Cold-Chain (scenario-02)

**Setup:**
- Modify ProducerController to use scenario-02
- Or implement scenario selection UI

**Expected Behavior:**
- Temperature range: 1-4°C (stricter than normal)
- No temperature spikes
- Duration: ~45 minutes
- Fewer alerts (strict compliance)
- Map shows same progression pattern

**Verification:**
```bash
curl http://localhost:8080/api/simulation/scenarios/scenario-02
```

### Test Scenario 3: High-Risk Delivery (scenario-03)

**Setup:**
- Modify ProducerController to use scenario-03
- Or implement scenario selection UI

**Expected Behavior:**
- Temperature range: 0-6°C (wider tolerance)
- Multiple temperature spikes (at minutes 15 and 40)
- Spike 1: 9°C for 3 minutes at minute 15
- Spike 2: 8.5°C for 5 minutes at minute 40
- Duration: ~60 minutes
- Multiple alerts for temperature violations
- Map shows same progression with longer duration

**Verification:**
```bash
# Check scenario details
curl http://localhost:8080/api/simulation/scenarios/scenario-03
```

## Troubleshooting

### Issue: "Simulation already running"

**Solution:** Stop the current simulation first
```bash
# Via GUI: Click "Stop Simulation" button
# Or wait for current simulation to complete
```

### Issue: Active shipments endpoint returns empty array

**Possible causes:**
1. No simulation is currently running
2. SimulationManager not initialized properly
3. DeliverySimulator not started

**Solution:**
- Start a new simulation via GUI
- Check logs for initialization errors
- Verify Spring beans are properly wired

### Issue: Map state returns null

**Possible causes:**
1. MapSimulator not initialized
2. No active simulation
3. Simulation hasn't started stepping yet

**Solution:**
- Ensure simulation is running
- Check that MapSimulator is initialized in ApplicationContext
- Verify SimulationManager.initializeForScenario was called

### Issue: Temperature readings not updating

**Possible causes:**
1. TemperatureService not monitoring
2. Route not recorded properly
3. DeliverySimulator not progressing

**Solution:**
- Check that temperatureService.startMonitoring was called
- Verify route was recorded with recordRoute
- Check DeliverySimulator logs for progress updates

## Performance Testing

### Load Test: Multiple Concurrent Queries

```bash
# Use Apache Bench or similar tool
ab -n 1000 -c 10 http://localhost:8080/api/simulation/active-shipments
```

**Expected:**
- Response time: < 100ms average
- No errors
- Consistent data

### Stress Test: Rapid Start/Stop

1. Start simulation
2. Immediately stop simulation
3. Start again
4. Repeat 10 times

**Expected:**
- No crashes
- Clean state transitions
- No memory leaks

## Integration Points Verification

### ✅ ProducerController → SimulationManager
- [ ] ProducerController calls startSimulation
- [ ] SimulationManager initializes MapSimulator
- [ ] Temperature compliance monitoring starts
- [ ] Events sent to listeners

### ✅ LogisticsController ← SimulationManager
- [ ] LogisticsController registered as listener
- [ ] Receives onSimulationStarted event
- [ ] Receives onProgressUpdate events
- [ ] Receives onSimulationStopped event
- [ ] Updates UI in response to events

### ✅ SimulationRestController → SimulationManager
- [ ] Active shipments query returns correct data
- [ ] Map state reflects current simulation step
- [ ] Scenario info matches loaded scenarios

### ✅ ScenarioManager → MapSimulator
- [ ] Scenarios loaded from JSON files
- [ ] MapSimulator initialized with correct scenario
- [ ] Scenario parameters applied (temp drift, speed multiplier)

## Enhanced Simulation Components

The enhanced simulation system includes:

### DeliveryState Enum
Robust lifecycle states with progress-based transitions:
- `AVAILABLE` (0-10%): Initial state
- `IN_TRANSIT` (10-70%): Delivery in progress
- `APPROACHING` (70-95%): Nearing destination
- `COMPLETED` (95-100%): Delivery complete

### SimulationConfig
Configuration for normal and demo modes:
- Demo mode: 10x speed factor, completes in ~3-4 minutes
- Normal mode: Standard timing for production use

### DeliveryEvent
Event model with:
- State transitions
- Quality tracking
- Final quality on completion
- Coordinates and environmental data

### Quality Decay
Exponential decay formula:
```
finalQuality = initialQuality × exp(-decayRate × exposureSeconds)
```

### Running Enhanced Simulation Tests

```bash
# Run new simulation tests
./gradlew :vericrop-gui:test --tests "com.vericrop.simulation.*"

# Run quality decay tests
./gradlew :vericrop-gui:test --tests "*QualityDecayTest*"

# Run lifecycle state tests
./gradlew :vericrop-gui:test --tests "*LifecycleStateTest*"

# Run integration tests
./gradlew :vericrop-gui:test --tests "*SimulationIntegrationTest*"
```

## Success Criteria

All of the following must be true:

- ✅ Build succeeds without errors
- ✅ All unit tests pass
- ✅ All integration tests pass
- ✅ GUI simulation starts and runs to completion
- ✅ Active shipments endpoint returns real-time data
- ✅ Map state updates during simulation
- ✅ All three scenarios load successfully
- ✅ Temperature monitoring generates appropriate alerts
- ✅ Simulation stops cleanly without errors
- ✅ No memory leaks after multiple simulation cycles
- ✅ Quality decay formula is correctly implemented
- ✅ State transitions occur exactly once per state
- ✅ Demo mode completes within 4 minutes

## Next Steps

If all tests pass:
1. Open pull request
2. Request code review
3. Address any feedback
4. Merge to main branch

If tests fail:
1. Check logs for error details
2. Review troubleshooting section
3. Fix issues and re-test
4. Update documentation if needed
