# Demo Mode - Simulation Time Compression

This document describes how to run the VeriCrop simulation in demo mode, which compresses the simulation time to complete within 3-4 minutes for demonstrations.

## Overview

Demo mode provides:
- **10x speed factor** - Simulation runs 10 times faster than normal
- **1 second update interval** (vs 5 seconds in normal mode)
- **Visible quality decay** - Quality drops noticeably during the simulation
- **Complete lifecycle demonstration** - All state transitions (Available → In Transit → Approaching → Completed) occur within the demo window

## Enabling Demo Mode

### Method 1: Environment Variable (Recommended)

```bash
# Unix/Linux/Mac
export VERICROP_DEMO_MODE=true
./gradlew :vericrop-gui:run

# Windows PowerShell
$env:VERICROP_DEMO_MODE="true"
./gradlew :vericrop-gui:run

# Windows Command Prompt
set VERICROP_DEMO_MODE=true
gradlew.bat :vericrop-gui:run
```

### Method 2: System Property

```bash
./gradlew :vericrop-gui:run --args="-Dvericrop.demoMode=true"
```

### Method 3: With Load Demo Data

For full demo experience with pre-populated data:

```bash
export VERICROP_LOAD_DEMO=true
export VERICROP_DEMO_MODE=true
./gradlew :vericrop-gui:run
```

## Configuration Details

| Parameter | Normal Mode | Demo Mode |
|-----------|-------------|-----------|
| Update Interval | 5000ms | 1000ms |
| Speed Factor | 1.0x | 10.0x |
| Waypoint Count | 20 | 20 |
| Decay Rate | 0.001/sec | 0.002/sec |
| Est. Completion | ~16 min | ~2 min |

## Lifecycle States

The simulation progresses through these states:

1. **AVAILABLE** (0-10% progress)
   - Initial state after batch creation
   - Vehicle at origin/farm location

2. **IN_TRANSIT** (10-70% progress)
   - Vehicle is moving from origin to destination
   - Environmental monitoring active
   - Quality decay occurring

3. **APPROACHING** (70-95% progress)
   - Vehicle nearing destination
   - Final leg of journey

4. **COMPLETED** (95-100% progress)
   - Delivery complete
   - Final quality calculated and displayed

## Quality Decay

Quality is calculated using an exponential decay formula:

```
finalQuality = initialQuality × e^(-decayRate × exposureSeconds)
```

In demo mode (0.002/sec decay rate):
- After 1 minute: ~88% quality
- After 2 minutes: ~78% quality
- After 3 minutes: ~70% quality

## Running a Demo

1. **Start the application in demo mode:**
   ```bash
   export VERICROP_DEMO_MODE=true
   export VERICROP_LOAD_DEMO=true
   ./gradlew :vericrop-gui:run
   ```

2. **Navigate to Producer screen:**
   - Create a new batch or use existing demo batch

3. **Start simulation:**
   - Click "Start Simulation" button
   - Observe progress in the UI

4. **Navigate to Logistics screen:**
   - Watch live map tracking
   - See temperature chart updates
   - Monitor alerts panel

5. **Navigate to Consumer screen:**
   - Watch Product Journey updates
   - See final quality when delivery completes

6. **Simulation completes in ~3-4 minutes:**
   - All controllers update with final state
   - Final quality displayed to consumer

## Programmatic Usage

You can also use demo mode programmatically:

```java
import com.vericrop.simulation.*;

// Create demo configuration
SimulationConfig demoConfig = SimulationConfig.createDemoMode();

// Or from environment
SimulationConfig config = SimulationConfig.fromEnvironment();

// Check if demo mode
if (config.isDemoMode()) {
    System.out.println("Running in demo mode");
}

// Get estimated completion time
double minutes = config.getEstimatedCompletionMinutes();
System.out.println("Estimated completion: " + minutes + " minutes");
```

## Troubleshooting

### Simulation doesn't complete

- Check that demo mode is enabled: look for log message containing `demoMode=true`
- Verify network/Kafka not blocking (demo mode should work without Kafka)

### Quality not decaying visibly

- Demo mode uses 0.002/sec decay rate
- Verify initial quality is 100%
- Check console for quality updates

### Map animation too fast/slow

- In demo mode, updates occur every 100ms (effective interval)
- If animation is jittery, check for multiple concurrent simulations

## See Also

- [DEMO_MODE_GUIDE.md](../DEMO_MODE_GUIDE.md) - General demo mode guide
- [TESTING_SIMULATION.md](TESTING_SIMULATION.md) - Simulation testing guide
- [README.md](README.md) - Main documentation
