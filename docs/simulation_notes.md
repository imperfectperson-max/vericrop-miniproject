# Simulation Notes - Discovery and Implementation Guide

## Overview

This document summarizes the findings from exploring the existing vericrop-gui codebase to understand preexisting simulations, data models, Kafka topics, and GUI components. These notes inform the implementation of multi-instance simulation scenarios.

## Existing Simulation Infrastructure

### Controller Files

The three main controller types are located in `src/vericrop-gui/main/java/org/vericrop/gui/`:

1. **LogisticsController.java** (~2400 lines)
   - Handles shipment tracking, timeline visualization, and live route mapping
   - Features:
     - Timeline events with real-time updates
     - Active Shipments table (batchId, status, location, temperature, humidity, ETA, vehicle)
     - Live Route Mapping using SVG-based map visualization
     - Temperature monitoring chart with alerts
     - Report generation and export
   - Key components:
     - `MapVisualization` inner class for map markers and trails
     - `ShipmentEnvironmentalData` for temperature/humidity tracking
     - Integration with `MapSimulationEventConsumer` and `TemperatureComplianceEventConsumer`
     - `QualityAlertProducer` for publishing temperature breach alerts
     - `InstanceRegistry` for multi-instance coordination (LOGISTICS role)

2. **ConsumerController.java** (~1270 lines)
   - Handles product journey visualization and final quality display
   - Features:
     - Product Journey visualization with 4 steps (Created → In Transit → Approaching → Delivered)
     - Final Quality score display with color-coded status
     - Average Temperature display during transit
     - Verification history tracking
   - Key components:
     - `QualityAssessmentService` integration for final quality computation
     - `SimulationControlConsumer` for cross-instance events
     - `InstanceRegistry` for multi-instance coordination (CONSUMER role)

3. **ProducerController.java** (~2470 lines)
   - Handles batch creation and simulation control
   - Features:
     - Batch creation with quality metrics
     - Blockchain integration for batch recording
     - Simulation start/stop controls
     - Dashboard with KPIs (total batches, avg quality, prime percentage, rejection rate)
   - Key components:
     - `LogisticsEventProducer`, `BlockchainEventProducer`, `BatchUpdateEventProducer`
     - `SimulationControlProducer` for broadcasting start/stop events
     - `InstanceRegistry` for multi-instance coordination (PRODUCER role)
     - `TemperatureComplianceService` and `LogisticsService`

### Simulation Services (vericrop-core)

Located in `src/vericrop-core/main/java/org/vericrop/service/`:

1. **SimulationManager.java**
   - Singleton manager for simulation lifecycle
   - Handles simulation state persistence across GUI navigation
   - Manages route generation, temperature monitoring, and alert services
   - Configuration via `SimulationConfig` for timing parameters

2. **SimulationConfig.java**
   - Default simulation duration: 180,000ms (3 minutes)
   - Time scale factor: 10x (accelerated simulation)
   - States: AVAILABLE → IN_TRANSIT → APPROACHING → COMPLETED/STOPPED
   - Builder pattern for customization

3. **DeliverySimulator.java**
   - Handles delivery simulation with route generation
   - Temperature and humidity variations
   - Progress callbacks to listeners

### Kafka Topics (from KafkaConfig.java)

| Topic Name | Purpose |
|------------|---------|
| `logistics-tracking` | General logistics events |
| `quality-alerts` | Quality breach notifications |
| `blockchain-events` | Blockchain record events |
| `environmental-data` | Temperature/humidity readings |
| `consumer-verifications` | Consumer verification events |
| `batch-updates` | Batch status updates |
| `temperature-compliance` | Temperature compliance events |
| `map-simulation` | Map position updates |
| `simulation-control` | Start/stop simulation commands |
| `instance-registry` | Instance heartbeats for coordination |

### Existing Airflow DAGs

Located in `airflow/dags/`:

1. **vericrop_dag.py** - Evaluation pipeline with Kafka integration
2. **delivery_simulation_dag.py** - Delivery simulation with quality decay
3. **quality_monitoring.py** - Quality monitoring workflows
4. **logistics_tracking.py** - Logistics tracking workflows
5. **supply_chain_analytics.py** - Analytics workflows
6. **alert_management.py** - Alert management workflows
7. **blockchain_etl.py** - Blockchain ETL workflows

### GUI Components

The GUI uses JavaFX with FXML layouts. Key views:

- Producer Dashboard: `producer.fxml`
- Logistics Dashboard: `logistics.fxml`
- Consumer View: `consumer.fxml`

Map visualization uses an SVG-based approach with animated markers and trail points.

## Multi-Instance Simulation Design

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Airflow DAG Orchestrator                │
│   (multi_instance_simulation_dag.py)                        │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                         Kafka Cluster                        │
│  Topics: simulation-control, map-simulation,                 │
│          temperature-compliance, quality-alerts              │
└─────────────┬───────────────────────────────────────────────┘
              │
     ┌────────┼────────┐
     ▼        ▼        ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│Producer │ │Producer │ │Producer │
│   #1    │ │   #2    │ │   #3    │
└────┬────┘ └────┬────┘ └────┬────┘
     │           │           │
     ▼           ▼           ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│Logistics│ │Logistics│ │Logistics│
│   #1    │ │   #2    │ │   #3    │
└────┬────┘ └────┬────┘ └────┬────┘
     │           │           │
     ▼           ▼           ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│Consumer │ │Consumer │ │Consumer │
│   #1    │ │   #2    │ │   #3    │
└─────────┘ └─────────┘ └─────────┘
```

### Scenarios

1. **Scenario A: Normal Transit**
   - 3 producers create batches with optimal temperature tolerances
   - 3 logistics instances route shipments with no breaches
   - 3 consumers receive and compute high final quality (>90%)

2. **Scenario B: Temperature Breach**
   - 1 or more shipments exceed temperature thresholds
   - Triggers quality alerts and quality degradation
   - Final quality varies based on breach severity

3. **Scenario C: Route Disruption**
   - Simulated delay or reroute triggers alerts
   - Extended transit affects final quality
   - Demonstrates alert visualization

### Configuration

Default timing for ~2 minute scenarios:
- Simulation duration: 120,000ms (2 minutes)
- Time scale: 15x (faster than default 10x)
- Progress update interval: 1000ms
- Temperature compliance check: every 15 seconds

## Files to Create/Modify

### New Files

1. `docs/simulation_notes.md` - This document ✓
2. `airflow/dags/multi_instance_simulation_dag.py` - Orchestration DAG
3. `docker-compose-simulation.yml` - Simulation-specific Docker setup
4. `scripts/run-multi-instance-simulation.sh` - Runner script
5. `scripts/verify-simulation.py` - Verification script
6. `src/vericrop-core/main/java/org/vericrop/service/simulation/MultiInstanceScenario.java` - Scenario definitions

### Files to Extend

1. `SimulationConfig.java` - Add 2-minute scenario preset
2. `README.md` - Add simulation running instructions
