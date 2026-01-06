# 🌾 VeriCrop Mini-Project

> **AI-Powered Agricultural Supply Chain Management with Quality Control and Blockchain Transparency**

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Python](https://img.shields.io/badge/Python-3.11-green.svg)
![Kafka](https://img.shields.io/badge/Kafka-3.4.0-black.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)

---

> 📚 **[View Complete Documentation Index](docs/README.md)** - Organized guides, deployment docs, and implementation details

---

## 📋 Table of Contents

**Quick Access:**
- [Project Summary](#project-summary) - What is VeriCrop?
- [Quick Start](#quick-start-5-minutes) - Get running in 5 minutes
- [Architecture](#architecture) - System overview
- [Documentation Index](docs/README.md) - 📚 Complete documentation library

**Essential Guides:**
- [Setup Guide](docs/guides/SETUP.md) - Detailed installation
- [Deployment Guide](docs/deployment/DEPLOYMENT.md) - Production deployment
- [Demo Mode](docs/guides/DEMO_MODE_GUIDE.md) - No-infrastructure demo

<details>
<summary><b>Full Table of Contents</b> (click to expand)</summary>

- [Components](#components)
- [Simulation Features](#simulation-features)
- [Database Setup & User Provisioning](#database-setup--user-provisioning)
- [Local Development](#local-development)
- [Verify Services](#verify-services)
- [Configuration](#configuration)
- [Authentication and Messaging](#authentication-and-messaging)
- [ML Service Contract](#ml-service-contract)
- [Running Tests](#running-tests)
- [Stopping and Cleaning](#stopping-and-cleaning)
- [Contributing](#contributing)
- [Troubleshooting](#troubleshooting)

</details>

## 🎯 Project Summary

VeriCrop is a comprehensive mini-project that demonstrates modern supply chain management for agricultural products.

### Key Features

| Feature | Description |
|---------|-------------|
| 🤖 **AI Quality Assessment** | Machine learning service for fruit quality classification |
| 🔗 **Blockchain Tracking** | Immutable ledger for supply chain transparency |
| ⚡ **Real-time Messaging** | Kafka-based event streaming |
| 🖥️ **Interactive GUI** | JavaFX desktop app for farm management & logistics |
| 🔄 **Workflow Orchestration** | Apache Airflow for automated pipelines |

### Goals

✅ Ensure food quality and safety through AI-powered classification  
✅ Provide end-to-end traceability from farm to consumer  
✅ Enable real-time monitoring of supply chain conditions  
✅ Demonstrate integration of modern technologies  
✅ Build trust in agricultural supply chains through transparency

---

## ⚡ Quick Start (5 Minutes)

### Option 1: Demo Mode (Easiest - No Setup Required!)

Perfect for quick demonstrations without any infrastructure setup.

```bash
# Unix/Linux/Mac - Starts 3 GUI instances in demo mode
./start-all.sh demo

# Windows - Starts 3 GUI instances in demo mode
start-all.bat demo
```

**That's it!** 🎉 No Docker, databases, or services needed.

<details>
<summary>📖 What is Demo Mode?</summary>

Demo mode runs the VeriCrop GUI completely standalone with:
- ✅ In-memory blockchain (no external network)
- ✅ Mock ML predictions
- ✅ Demo data in all screens
- ✅ Fully functional delivery simulator
- ✅ QR code generation
- ✅ All UI flows operational

Perfect for: presentations, testing, offline scenarios
</details>

### Option 2: Full Stack with Services

For development with real databases and services.

```bash
# Start all services with one command
./start-all.sh           # Unix/Linux/Mac
start-all.bat            # Windows
```

**Services started:**
- PostgreSQL (database)
- Kafka (messaging)
- ML Service (AI predictions)
- Airflow (workflow orchestration)
- GUI Application

---

## 🎮 Simulation Features

VeriCrop includes powerful simulation capabilities for testing and demonstration.

### Quick Demo with Example Files

Three pre-configured simulations ready to run (~3 minutes each):

| Simulation | Duration | Description |
|------------|----------|-------------|
| `example_1_farmer_to_consumer.json` | ~3 min | Complete journey: farm → warehouse → retail |
| `example_2_producer_local.json` | ~1.5 min | Short local delivery to distribution center |
| `example_3_long_route.json` | ~3 min | Extended delivery with temperature monitoring |

📍 **Location**: `src/vericrop-gui/main/resources/simulations/`

<details>
<summary>🔧 Advanced: Simulation Configuration & API</summary>

**Key Features**:
- **Time Scaling**: Default 10x speed makes simulations complete in ~3 minutes
- **Quality Decay**: Tracks quality degradation based on temperature and time
- **Lifecycle States**: AVAILABLE → IN_TRANSIT → APPROACHING → COMPLETED
- **Smooth Animation**: Map markers interpolate smoothly along route waypoints

**Loading Simulations**:

```java
import org.vericrop.service.simulation.SimulationLoader;

// Load a specific simulation
SimulationDefinition sim = SimulationLoader.loadFromResource(
    "/simulations/example_1_farmer_to_consumer.json");

// Get simulation config with time scaling
SimulationConfig config = sim.toSimulationConfig();

// Generate batch ID
String batchId = sim.generateBatchId();
```

**Adjusting Time Scale**:
- `timeScale: 1.0` = real-time (30-min route takes 30 minutes)
- `timeScale: 10.0` = 10x speed (30-min route in 3 minutes) **[Default]**
- `timeScale: 30.0` = 30x speed (30-min route in 1 minute)

</details>

<details>
<summary>🗺️ Map Simulation & Scenario Management</summary>

VeriCrop includes integrated map simulation with grid-based visualization of entity positions.

**Key Features:**
- MapSimulator: Grid-based real-time tracking
- ScenarioManager: Pre-configured delivery scenarios
- REST API: Access map state via `/api/simulation/map`
- Three Scenarios: Normal, Cold Storage, Hot Transport

**Using the API:**

```bash
# Get current map state
curl http://localhost:8080/api/simulation/map

# List available scenarios
curl http://localhost:8080/api/simulation/scenarios

# Start simulation (requires supplier and consumer usernames)
curl -X POST http://localhost:8080/api/simulation/start \
  -H "Content-Type: application/json" \
  -d '{
    "supplierUsername": "supplier",
    "consumerUsername": "farmer",
    "title": "Apple Delivery Simulation",
    "scenario_id": "scenario-02"
  }'
```

**Full API documentation available in detailed view above.**

</details>

<details>
<summary>🔄 Kafka-Backed Shared Simulation State</summary>

VeriCrop now supports **shared simulation state** across multiple running instances. This enables different users (farmer, supplier, admin, consumer) to observe and modify the same simulation state in real time.

**Key Features:**
- 📡 **Server-Sent Events (SSE)**: Real-time state streaming to connected clients
- 🔄 **Kafka Event Sourcing**: State changes are published to Kafka for multi-instance synchronization
- 👥 **Multi-Role Support**: Different users with different roles can participate in the same simulation
- 🔐 **Role-Based Actions**: Each role can perform specific state modifications

**REST API Endpoints:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/simulation/state` | GET | Get current shared simulation state |
| `/api/simulation/state/update` | POST | Submit a state change event |
| `/api/simulation/stream` | GET (SSE) | Subscribe to real-time state updates |

**Getting Current State:**

```bash
curl http://localhost:8080/api/simulation/state
```

**Response:**
```json
{
  "simulation_active": false,
  "current_step": 0,
  "temperature": 4.0,
  "humidity": 85.0,
  "location": "Origin",
  "quality_score": 100.0,
  "participants": {},
  "batches": [],
  "alerts": [],
  "last_updated": 1700000000000,
  "version": 1
}
```

**Updating State (Role-Based):**

```bash
curl -X POST http://localhost:8080/api/simulation/state/update \
  -H "Content-Type: application/json" \
  -d '{
    "event_type": "STEP_UPDATE",
    "role": "supplier",
    "data": {
      "step": 5,
      "temperature": 4.5,
      "location": "Highway Mile 20"
    }
  }'
```

**Subscribing to State Updates (SSE):**

```bash
# Connect to SSE stream
curl -N http://localhost:8080/api/simulation/stream
```

**JavaScript Client Example:**
```javascript
const eventSource = new EventSource('/api/simulation/stream');

eventSource.addEventListener('state', (event) => {
  const state = JSON.parse(event.data);
  console.log('State update:', state);
  // Update UI with new state
  updateDashboard(state);
});

eventSource.onerror = (error) => {
  console.error('SSE connection error:', error);
};
```

**Supported Event Types:**
- `SIMULATION_STARTED`: Simulation has started
- `SIMULATION_STOPPED`: Simulation has stopped
- `STEP_UPDATE`: Environmental data update (temperature, humidity, location)
- `PARTICIPANT_JOINED`: A user joined the simulation
- `PARTICIPANT_LEFT`: A user left the simulation
- `ALERT_ADDED`: A new alert was generated
- `BATCH_ADDED`: A new batch was created
- `STATE_UPDATE`: Generic state modification

**Running Kafka Locally:**

Use Docker Compose to start Kafka and Zookeeper:

```bash
docker-compose -f docker-compose-kafka.yml up -d
```

Or use the full stack:

```bash
docker-compose up -d kafka zookeeper
```

**Environment Variables:**

```bash
# Enable Kafka for state synchronization
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

**Multi-Instance Setup:**

1. Start Kafka: `docker-compose up -d kafka zookeeper`
2. Start first instance: `./gradlew :vericrop-gui:run` (port 8080)
3. Start second instance: `SERVER_PORT=8081 ./gradlew :vericrop-gui:run` (port 8081)
4. Both instances will share simulation state through Kafka

### Running Multiple Controller Instances (Role-Based Coordination)

VeriCrop supports running multiple controller instances locally for testing multi-party supply chain scenarios. Each controller (Producer, Logistics, Consumer) registers with a central instance registry using Kafka heartbeats.

**Instance Registration:**

Each controller automatically registers its role with the InstanceRegistry on startup:
- **ProducerController**: Registers as `PRODUCER` role
- **LogisticsController**: Registers as `LOGISTICS` role
- **ConsumerController**: Registers as `CONSUMER` role

**Simulation Coordination:**

For a simulation to start, the InstanceRegistry must detect at least one active instance of each required role:
- ✅ PRODUCER (creates batches and starts simulations)
- ✅ LOGISTICS (tracks deliveries and routes)
- ✅ CONSUMER (verifies and receives batches)

If any required role is missing, the ProducerController will block simulation start with an error message like:
```
Cannot start simulation: missing required controller roles.
Active roles: PRODUCER
Missing roles: LOGISTICS, CONSUMER
```

**Running Multiple Instances Locally:**

1. **Start Infrastructure**:
   ```bash
   docker-compose up -d kafka zookeeper postgres
   ```

2. **Start Producer Instance** (Terminal 1):
   ```bash
   # Default port 8080
   ./gradlew :vericrop-gui:run
   # Login and navigate to Producer screen
   ```

3. **Start Logistics Instance** (Terminal 2):
   ```bash
   # Use different port to avoid conflict
   SERVER_PORT=8081 ./gradlew :vericrop-gui:run
   # Login and navigate to Logistics screen
   ```

4. **Start Consumer Instance** (Terminal 3):
   ```bash
   # Use another different port
   SERVER_PORT=8082 ./gradlew :vericrop-gui:run
   # Login and navigate to Consumer screen
   ```

5. **Start Simulation**:
   - In the Producer instance (Terminal 1), click "Start Simulation"
   - The simulation will now proceed since all required roles are present
   - All three instances will receive simulation updates via Kafka

**Instance Registry Heartbeats:**

- Each instance sends heartbeats every 5 seconds
- Instances are considered stale after 15 seconds of no heartbeat
- Heartbeat messages include: instance ID, role, host, port, timestamp

**Troubleshooting Multiple Instances:**

| Issue | Solution |
|-------|----------|
| "Missing roles" error | Ensure all three controller types are running |
| Port conflict | Use `SERVER_PORT=XXXX` environment variable |
| Instances not detecting each other | Verify Kafka is running with `docker-compose ps kafka` |
| Stale instances shown | Wait 15 seconds for cleanup or restart instances |

**Environment Variables:**

```bash
# Set different ports for multiple instances
SERVER_PORT=8081  # Web server port

# Custom instance ID (auto-generated if not set)
VERICROP_INSTANCE_ID=producer-instance-1

# Kafka settings (should be same for all instances)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=true
```

**Synchronized Simulation Validation Steps:**

To verify that Producer, Logistics, and Consumer controllers are running in synchronized lockstep:

1. **Start All Three Controller Instances** (as described above in three terminals)

2. **In Producer Instance (Terminal 1):**
   - Click "Start Simulation" to begin a delivery
   - Note the batch ID and start time in the status area

3. **In Logistics Instance (Terminal 2):**
   - ✅ Verify Live Route shows truck marker at origin, then animates toward warehouse
   - ✅ Verify Timeline updates progressively (Created → In Transit → Approaching → Delivered)
   - ✅ Verify Active Shipments table shows the batch with updating status/location
   - ✅ Verify Temperature chart shows new data points appearing as the simulation runs

4. **In Consumer Instance (Terminal 3):**
   - ✅ Verify Product Journey steps update (step icons change from ⏳ to ✅)
   - ✅ Verify verification history shows progress milestones (25%, 50%, 75%, 100%)
   - ✅ Verify Final Quality score is computed and displayed when delivery completes
   - ✅ Verify Average Temperature is shown after delivery

5. **All Instances Should Progress in Lockstep:**
   - Progress percentages should match within ±5% across all three instances
   - Status transitions should occur at approximately the same time
   - Completion should trigger final quality display in Consumer view

**Automated Test Validation:**

Run the synchronized simulation lifecycle tests to verify the multi-listener coordination:

```bash
./gradlew :vericrop-core:test --tests "org.vericrop.service.simulation.SynchronizedSimulationLifecycleTest"
```

These tests verify:
- Multiple listeners can subscribe to the same simulation tick source
- All registered listeners receive identical tick events
- Late-joining listeners receive current state notifications
- Event order is maintained (START → PROGRESS → STOP)

### Realtime Simulation Updates (NEW)

VeriCrop now features **realtime simulation with live UI updates** that animate delivery progress across Producer, Logistics, and Consumer views.

**Key Features:**
- 🚚 **Live Route Tracking**: Moving marker animates along the route between Farm and Warehouse
- 🌡️ **Temperature Monitoring**: Real-time temperature chart updates during transit
- 📊 **Progress Tracking**: Shipment status updates every second (configurable)
- 🗺️ **GPS Coordinate Streaming**: Waypoint-level position updates for smooth animation
- ⏱️ **Timeline Updates**: Status changes (Created, In Transit, At Warehouse, Delivered) with timestamps
- 🔄 **Multi-View Sync**: All UI screens (Producer, Logistics, Consumer) update simultaneously

**How It Works:**

1. **Event Emission**:
   - SimulationService emits `SimulationEvent` objects at 1-second intervals (configurable via `simulation.emit.intervalMs`)
   - Each event contains: GPS coordinates, temperature, humidity, status, ETA, progress%

2. **Event Distribution**:
   - SimulationManager receives events and notifies all registered `SimulationListener` instances
   - Controllers implement `SimulationListener` interface to receive updates

3. **UI Updates** (Thread-Safe via Platform.runLater()):
   - **ProducerController**: Updates simulation status label with progress percentage
   - **LogisticsController**: Animates map marker, updates temperature chart, refreshes shipments table
   - **ConsumerController**: Shows shipment status in verification history

**Configuration:**

Edit `application.yml` or set environment variables to customize simulation behavior:

```yaml
simulation:
  emit:
    intervalMs: 1000  # Emit events every 1 second
  animation:
    smooth: true      # Enable smooth marker transitions
    durationMs: 800   # Animation duration per waypoint
```

**Event Flow:**

```
ProducerController.startSimulation()
  ↓
SimulationManager.startSimulation()
  ↓
SimulationService.startSimulation() [emits every 1s]
  ↓ SimulationEvent
SimulationManager.handleSimulationEvent()
  ↓ notifyProgress()
All Controllers [onProgressUpdate()]
  ↓ Platform.runLater()
UI Updates (map animation, charts, tables)
```

**Manual Testing:**

1. Start the VeriCrop GUI application
2. Login with credentials (e.g., `farmer` / `farmer123`)
3. Navigate to **Producer** screen
4. Upload a product image and create a batch
5. Click **"Start Simulation"** button
6. Navigate to **Logistics** screen:
   - ✅ Observe animated marker moving from Farm (left) to Warehouse (right)
   - ✅ See progress percentage updating every second
   - ✅ Watch temperature chart populate with real-time data
   - ✅ View shipments table updating with current location and status
7. Navigate to **Consumer** screen:
   - ✅ See shipment status updates in verification history
8. Return to **Producer** screen:
   - ✅ Observe simulation progress in status label

**Stopping Simulation:**
- Click **"Stop Simulation"** button in Producer screen
- Marker remains at last position for completed deliveries
- Marker removed immediately for manual stops

**Selecting Scenarios:**

When starting a simulation through the ProducerController, the system defaults to `scenario-01` (Normal). The three available scenarios are:

1. **scenario-01 (Normal Cold-Chain)**: Target 2-5°C, allows one short temperature spike
2. **scenario-02 (Strict Cold-Chain)**: Target 1-4°C, no spikes allowed, strict compliance
3. **scenario-03 (High-Risk Delivery)**: Target 0-6°C, multiple temperature events for stress testing

The MapSimulator is initialized with the selected scenario and steps forward in sync with the delivery simulation, updating entity positions each tick. Temperature compliance monitoring generates alerts for violations based on scenario thresholds.

### Simulation Orchestration and Controller Isolation

VeriCrop implements a robust simulation orchestration system that ensures independent, thread-safe execution of simulations across multiple controllers.

**Key Orchestration Features:**

1. **Independent Controller Instances**
   - Each simulation batch runs in its own isolated execution context
   - ProducerController, LogisticsController, and ConsumerController operate independently
   - Controller state is not shared between simulations

2. **Thread-Safe Execution**
   - SimulationManager uses `AtomicBoolean` flags for thread-safe state management
   - ExecutorService pools handle concurrent simulation tasks
   - ConcurrentHashMap is used for tracking active simulations

3. **QualityAssessmentService Integration**
   - ConsumerController uses QualityAssessmentService to compute final quality on delivery
   - Quality computation aggregates sensor data (temperature, humidity) from the entire route
   - Final quality grade is based on temperature violations, humidity exceedances, and transit time

**Component Interaction:**

```
ProducerController.startSimulation()
    ↓
SimulationManager (thread-safe singleton)
    ↓ Creates isolated simulation context
DeliverySimulator (generates waypoints and events)
    ↓ Emits SimulationEvent
┌─────────────────────────────────────┐
│  LogisticsController               │ → Temperature monitoring graph
│  (SimulationListener)               │ → Live route tracing animation
│                                     │ → Timeline updates
│                                     │ → Alert generation
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  ConsumerController                 │ → Product journey tracking
│  (SimulationListener)               │ → Final quality computation
│                                     │ → Verification history
└─────────────────────────────────────┘
```

**Configuration Options:**

| Property | Default | Description |
|----------|---------|-------------|
| `simulation.emit.intervalMs` | 1000 | Interval between event emissions |
| `simulation.waypoints.count` | 10 | Number of waypoints per route |
| `simulation.temperature.min` | 2.0 | Minimum cold-chain temperature (°C) |
| `simulation.temperature.max` | 8.0 | Maximum cold-chain temperature (°C) |
| `simulation.humidity.min` | 65.0 | Minimum humidity threshold (%) |
| `simulation.humidity.max` | 90.0 | Maximum humidity threshold (%) |

**Quality Assessment Formula:**

The final quality score is calculated by:
1. Starting with initial quality (100%)
2. Applying quality decay based on transit time and average temperature
3. Subtracting penalty for temperature violations (0.5% per violation)
4. Subtracting penalty for critical temperature exceedances (2.0% per critical violation)
5. Subtracting penalty for humidity violations (0.3% per violation)

Quality grades are assigned based on final score:
- **PRIME**: ≥90%
- **EXCELLENT**: ≥80%
- **GOOD**: ≥70%
- **ACCEPTABLE**: ≥60%
- **MARGINAL**: ≥40%
- **REJECTED**: <40%

---

## 🏗️ Architecture

VeriCrop follows a microservices architecture with event-driven communication:

```
┌─────────────────────────────────────────┐
│       JavaFX GUI Application             │
│  (Farm, Logistics, Consumer, Analytics) │
└──────────────┬──────────────────────────┘
               │ REST API / Kafka
               │
┌──────────────▼──────────────────────────┐
│       VeriCrop Core Services             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐│
│  │  Batch   │ │Blockchain│ │ Quality  ││
│  │Management│ │  Ledger  │ │Evaluation││
│  └──────────┘ └──────────┘ └──────────┘│
└─────┬──────────┬──────────┬────────────┘
      │          │          │
      ▼          ▼          ▼
┌──────────┐ ┌──────┐ ┌────────────┐
│PostgreSQL│ │Kafka │ │ ML Service │
│(Metadata)│ │(Event│ │(FastAPI)   │
└──────────┘ │Stream│ └────────────┘
             └──┬───┘
                │
           ┌────▼────┐
           │ Airflow │
           │(Workflow│
           └─────────┘
```

<details>
<summary>📊 Key Data Flows</summary>

1. **Batch Creation**: GUI → ML Service (quality prediction) → PostgreSQL + Kafka → Blockchain
2. **Quality Evaluation**: Airflow DAG → Kafka → GUI Service → ML Service → Results
3. **Supply Chain Tracking**: GUI → Blockchain → Kafka → Analytics Dashboard

</details>

---

## 📦 Components

### Core Modules

| Module | Description | Tech Stack |
|--------|-------------|------------|
| **vericrop-gui** | JavaFX desktop application | Java 17, JavaFX, Spring Boot |
| **vericrop-core** | Shared business logic | Java 17, Jackson |
| **kafka-service** | Event-driven messaging | Spring Boot, Spring Kafka |
| **ml-service** | AI quality prediction | Python 3.11, FastAPI, ONNX |
| **airflow** | Workflow orchestration | Apache Airflow 2.7.1 |

<details>
<summary>🖥️ VeriCrop GUI - Detailed Features</summary>

**Location**: `vericrop-gui/`

JavaFX desktop application with Spring Boot integration. Provides four interactive dashboards plus user authentication and messaging:
- **User Authentication**: Secure login/registration with BCrypt password hashing
- **User Messaging**: In-app messaging system with inbox and compose features
- **Farm Management**: Batch creation, quality assessment, **QR code generation**
- **Logistics Tracking**: Shipment monitoring, condition alerts, **delivery simulation**
- **Consumer Verification**: QR code scanning, product journey
- **Analytics Dashboard**: KPI monitoring, trend analysis
- **Real-Time Alerts**: AlertService for notifications across all screens
- **Reports Generation**: CSV/JSON reports for quality, journey, and analytics

**New Features:**
- 🔳 **QR Code Generation**: Scannable RGB PNG QR codes for product traceability
- 🚀 **Delivery Simulator**: Real-time simulation with GPS tracking and environmental monitoring
- 📊 **Report Generator**: CSV/JSON/PDF reports with journey data, quality metrics, analytics
- 🔔 **Alert System**: Real-time alerts with severity levels and acknowledgement tracking
- 🚪 **Logout Buttons**: Consistent logout functionality across all user screens
- 💬 **Messages Navigation**: Easy access to messaging system from all dashboards
- 📦 **Batch Metrics**: Automatic computation of prime%, rejection% based on quality classification
- 🔄 **Quality Decay Tracking**: Real-time quality degradation during storage and transit
- 📈 **Aggregated Reports**: Supply chain summaries with average quality, prime%, rejection%
- 🎯 **Quality Disclosure**: Automated quality disclosure to buyers for price negotiation

**Tech Stack**: Java 17, JavaFX, Spring Boot, HikariCP, BCrypt, Flyway, Kafka Client, ZXing (QR codes), iText (PDF generation)

**Details**: See [vericrop-gui/README.md](vericrop-gui/README.md) and [docs/GUI-setup.md](docs/GUI-setup.md)

### vericrop-core

**Location**: `vericrop-core/`

Core business logic library shared across modules:
- Quality evaluation service (deterministic scoring)
- Blockchain ledger implementation (SHA-256 hashing)
- DTOs for batch, shipment, and evaluation records
- File-based immutable ledger (JSONL format)

**Tech Stack**: Java 17, Jackson, SLF4J

### kafka-service

**Location**: `kafka-service/`

Kafka messaging service for event-driven communication:
- Producer service for publishing batch events, quality alerts, shipment records
- Consumer service for processing evaluation requests
- In-memory mode for development without Kafka

**Tech Stack**: Spring Boot, Spring Kafka

**Topics**: 
- `evaluation-requests`: Quality evaluation requests from Airflow
- `evaluation-results`: Quality results from GUI service
- `shipment-records`: Immutable ledger records
- `quality-alerts`: Quality threshold alerts
- `logistics-events`: Shipment tracking events
- `blockchain-events`: Blockchain transactions
- `batch-created-events`: Batch creation notifications
- `order-events`: Order placement and processing events
- `scenario-events`: Scenario execution lifecycle events (NEW)
- `temperature-events`: Temperature monitoring and violation events (NEW)
- `supplier-compliance-events`: Supplier compliance status updates (NEW)

**Concurrent Scenario Orchestration** (NEW):
- 🎯 **ScenarioController**: Orchestrates concurrent execution across 6 domains (scenarios, delivery, map, temperature, supplier_compliance, simulations)
- 🔄 **Parallel Execution**: Multiple scenarios (NORMAL, HOT_TRANSPORT, COLD_STORAGE, HUMID_ROUTE, EXTREME_DELAY) run concurrently using CompletableFuture
- 📊 **Domain Integration**: TemperatureService monitors temp compliance, MapService generates routes, SupplierComplianceService tracks performance
- 📡 **Event Publishing**: Kafka events published at start/complete/fail for each scenario and domain
- ⚡ **Result Aggregation**: Cross-domain status monitoring and result collection

**New Features**:
- 🎯 **Order Processing**: OrderEventConsumer handles orders with quality disclosure
- 💰 **Dynamic Pricing**: Price adjustments based on quality metrics (prime/rejection rates)
- 📦 **Batch Tracking**: BatchCreatedEvent for real-time batch notifications
- 🌡️ **Temperature Monitoring**: Real-time cold chain compliance tracking
- 🗺️ **Route Generation**: Automated route planning with environmental conditions
- 📈 **Supplier Compliance**: Performance metrics and compliance evaluation

### ml-service

**Location**: `docker/ml-service/`

FastAPI-based machine learning service for fruit quality prediction:
- ResNet18 ONNX model (99.06% accuracy)
- Quality classification: Fresh, Good, Fair, Poor
- Batch management API
- Dashboard data generation

**Tech Stack**: Python 3.11, FastAPI, ONNX Runtime, Pillow, NumPy

**Key File**: [docker/ml-service/app.py](docker/ml-service/app.py)

### airflow

**Location**: `airflow/dags/`

Apache Airflow workflow orchestration:
- `vericrop_dag.py`: End-to-end evaluation pipeline
  - Produces evaluation requests to Kafka
  - Calls REST API for quality evaluation
  - Verifies ledger records
  - Generates pipeline summary
- `delivery_simulation_dag.py`: Batch delivery simulation (NEW)
  - Simulates environmental conditions during transit
  - Applies quality decay based on temperature/humidity
  - Publishes delivery events to Kafka
  - Generates delivery reports with quality metrics

**Tech Stack**: Apache Airflow 2.7.1, Kafka Python Client

**New Features**:
- 🌡️ **Environmental Monitoring**: Temperature and humidity tracking at waypoints
- 📉 **Quality Decay Simulation**: Real-time quality degradation during transit
- 📊 **Delivery Reports**: Comprehensive reports with quality metrics and violations

### docker

**Location**: `docker/`

Docker configurations and compose files:
- `docker-compose.yml`: Complete stack (PostgreSQL, Kafka, ML Service, Airflow)
- `docker-compose-kafka.yml`: Kafka-only setup
- `docker-compose-simulation.yml`: Multi-instance simulation environment
- `ml-service/Dockerfile`: ML service container

## Multi-Instance Simulation Scenarios

VeriCrop includes end-to-end simulation scenarios that demonstrate multiple instances (3) of each controller type running in parallel. Each scenario runs for approximately 2 minutes and integrates Kafka for messaging and Airflow for orchestration.

### Scenarios

| Scenario | Description | Expected Outcome |
|----------|-------------|------------------|
| **Normal Transit** | All shipments complete successfully | High final quality (>90%) for all batches |
| **Temperature Breach** | One shipment exceeds temperature thresholds | Alerts triggered, quality degradation on affected batch |
| **Route Disruption** | Simulated delay affects one delivery | Extended transit time, moderate quality impact |

### Quick Start (2 minutes)

```bash
# Start infrastructure (Kafka, PostgreSQL, Airflow)
docker-compose -f docker-compose-simulation.yml up -d

# Run normal scenario (default: 2 minutes)
./scripts/run-multi-instance-simulation.sh normal

# Run temperature breach scenario
./scripts/run-multi-instance-simulation.sh temperature_breach

# Run route disruption scenario
./scripts/run-multi-instance-simulation.sh route_disruption

# Run all scenarios sequentially
./scripts/run-multi-instance-simulation.sh all
```

### Configuration Options

```bash
# Custom duration (in seconds)
./scripts/run-multi-instance-simulation.sh normal --duration 90

# Custom number of instances
./scripts/run-multi-instance-simulation.sh normal --instances 5

# Use existing Kafka (don't start Docker)
./scripts/run-multi-instance-simulation.sh normal --no-docker

# Trigger via Airflow DAG
./scripts/run-multi-instance-simulation.sh normal --airflow
```

### Airflow DAGs

Three DAGs are available in the Airflow UI (`http://localhost:8080`):
- `vericrop_multi_instance_normal`
- `vericrop_multi_instance_temperature_breach`
- `vericrop_multi_instance_route_disruption`

Each DAG follows this workflow:
1. **Initialize Scenario** - Create batch configurations
2. **Start Producers** - Publish batch creation events
3. **Start Simulation Control** - Broadcast start events to all instances
4. **Simulate Logistics Updates** - Publish map/temperature events for 2 minutes
5. **Stop Simulation Control** - Broadcast stop events
6. **Compute Final Quality** - Aggregate quality scores across instances
7. **Generate Report** - Create scenario summary

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `simulation-control` | Start/stop simulation commands |
| `map-simulation` | Map position and location updates |
| `temperature-compliance` | Temperature monitoring events |
| `quality-alerts` | Temperature breach and delay alerts |
| `batch-updates` | Batch creation and status updates |
| `instance-registry` | Instance heartbeats for coordination |

### GUI Features During Simulation

During simulation runtime, the GUI displays:

**LogisticsController:**
- Timeline with real-time shipment events
- Active Shipments table with status and ETA
- Live Route Mapping with animated markers
- Temperature monitoring chart
- Alert banners for breaches and delays

**ConsumerController:**
- Product Journey visualization (4-step timeline)
- Final Quality score with grade (PRIME/STANDARD/REJECT)
- Average temperature during transit

**ProducerController:**
- Batch creation with quality metrics
- Simulation control buttons (Start/Stop)
- Dashboard with KPIs

### Verification

After running a simulation, verify the results:

```bash
# Verify normal scenario (mock data for testing without Kafka)
python scripts/verify-simulation.py --scenario normal --mock

# Verify with Kafka connection
python scripts/verify-simulation.py --scenario temperature_breach

# Save report to file
python scripts/verify-simulation.py --scenario route_disruption --output report.json
```

### Documentation

See [docs/simulation_notes.md](docs/simulation_notes.md) for detailed implementation notes.

---

## 🚀 Quickstart (10 Minutes)

### Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| **Java** | 17+ | `java -version` |
| **Docker** | 20.10+ | `docker --version` |
| **Docker Compose** | 2.0+ | `docker-compose --version` |
| **Gradle** | 8.0+ | `./gradlew --version` |
| **Python** | 3.11 | `python3 --version` |

<details>
<summary>📖 Detailed Prerequisites Check</summary>


1. **Git** - Version control
   ```bash
   git --version
   # Expected: git version 2.30 or higher
   ```

2. **Java 11+** (Java 17 recommended)
   ```bash
   java -version
   # Expected: openjdk version "17.0.x"
   
   # Verify JAVA_HOME is set
   echo $JAVA_HOME        # Unix/Mac
   echo %JAVA_HOME%       # Windows
   ```

3. **Gradle** (wrapper included, no separate install needed)
   ```bash
   ./gradlew --version    # Unix/Mac
   gradlew.bat --version  # Windows
   # Expected: Gradle 8.x or higher
   ```

4. **Docker & Docker Compose**
   ```bash
   docker --version
   # Expected: Docker version 20.10+
   
   docker-compose --version
   # Expected: Docker Compose version 2.0+
   
   # Verify Docker is running
   docker ps
   ```

5. **Python 3.11** (for local ML service development)
   ```bash
   python3 --version      # Unix/Mac
   python --version       # Windows
   # Expected: Python 3.11.x
   ```

</details>

### One-Command Start

**Unix/Linux/Mac:**
```bash
./start-all.sh
```

**Windows:**
```cmd
start-all.bat
```

**What this does:**
1. ✅ Starts PostgreSQL database
2. ✅ Starts Kafka messaging
3. ✅ Starts ML Service
4. ✅ Starts Airflow orchestrator
5. ✅ Launches GUI application

**Service URLs:**

| Service | URL | Credentials |
|---------|-----|-------------|
| **Kafka UI** | http://localhost:8081 | - |
| **Airflow UI** | http://localhost:8082 | admin / admin |
| **ML Service** | http://localhost:8000/health | - |
| **PostgreSQL** | localhost:5432 | vericrop / vericrop123 |

<details>
<summary>⚙️ Advanced: Start Options & Manual Setup</summary>

**Available Start Modes:**

```bash
# Start modes
./start-all.sh full         # All services (default)
./start-all.sh demo         # Demo mode (no Docker)
./start-all.sh infra        # Infrastructure only
./start-all.sh kafka        # Kafka stack only
./start-all.sh simulation   # Simulation environment
./start-all.sh build        # Build Java artifacts
./start-all.sh docker-build # Build Docker images
./start-all.sh all-build    # Build everything
./start-all.sh run          # Run GUI only
```

---

## 💾 Database Setup & User Provisioning

VeriCrop uses PostgreSQL with automatic schema initialization.

### Quick Overview

✅ **Automatic Setup** - Schema creates automatically on first run  
✅ **Pre-configured Users** - Demo users ready for testing  
✅ **Flyway Migrations** - Versioned database changes  
✅ **Secure Authentication** - BCrypt password hashing

### Default Users

| Username | Password | Role | Use For |
|----------|----------|------|---------|
| `admin` | `admin123` | ADMIN | Full system access |
| `farmer` | `farmer123` | FARMER | Producer operations |
| `supplier` | `supplier123` | SUPPLIER | Logistics operations |
| `producer_demo` | `DemoPass123!` | PRODUCER | Demo producer |
| `logistics_demo` | `DemoPass123!` | LOGISTICS | Demo logistics |
| `consumer_demo` | `DemoPass123!` | CONSUMER | Demo consumer |

⚠️ **Security Note**: Change these passwords in production!

<details>
<summary>🔧 Advanced: Schema Details & Manual User Management</summary>


**Automatic Schema Initialization:**

Tables created on first run:
- `users` - Authentication and profiles
- `batches` - Batch metadata and quality
- `shipments` - Shipment tracking with blockchain
- `messages` - User-to-user messaging
- `participants` - GUI instance tracking
- `simulations` - Simulation metadata
- `simulation_batches` - Simulation batch data

**Database Migrations:**

Flyway migrations in `src/vericrop-gui/main/resources/db/migration/`:
- V1: Batches and quality tracking
- V2: User authentication with BCrypt
- V3: Shipment tracking with blockchain
- V7: Simulation persistence

**Adding New Users Manually:**

```sql
-- Connect to PostgreSQL
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

-- Insert new user (generate BCrypt hash first)
INSERT INTO users (username, password_hash, email, full_name, role, status)
VALUES ('newuser', '$2a$10$YOUR_BCRYPT_HASH', 'user@example.com', 'Full Name', 'USER', 'active');
```

**Authentication Features:**
- BCrypt password hashing (never plaintext)
- Failed login tracking (locks after 5 attempts)
- 30-minute automatic unlock
- Role-based access control
- Database fallback mode

</details>

### Verify Database Setup

```bash
# Check migrations
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT version, description FROM flyway_schema_history;"

# Verify users
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, role FROM users;"
```

---
```

## Local Development

For active development without running all services in Docker containers. This approach gives you faster iteration cycles and better debugging capabilities.

### Step 1: Start Infrastructure Services Only

Start only PostgreSQL, Kafka, and Zookeeper (not the full stack):

```bash
# Start only infrastructure services
docker-compose up -d postgres kafka zookeeper

# Expected output:
# Creating vericrop-postgres ... done
# Creating vericrop-zookeeper ... done
# Creating vericrop-kafka ... done

# Verify services are running
docker-compose ps

# Expected: postgres, kafka, zookeeper should be "Up" or "Up (healthy)"
```

### Step 2: Configure Environment Variables

Copy and customize the environment file:

```bash
# Copy example configuration
cp .env.example .env

# Edit .env with your settings
nano .env       # Unix/Mac
notepad .env    # Windows
```

**Key configuration options for local development**:

```bash
# PostgreSQL (must match docker-compose settings)
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=vericrop
POSTGRES_PASSWORD=vericrop123
POSTGRES_DB=vericrop

# Kafka (must match docker-compose settings)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=true

# ML Service (will run separately)
ML_SERVICE_URL=http://localhost:8000
VERICROP_LOAD_DEMO=true  # Use demo mode if ONNX model unavailable

# Application Mode
VERICROP_MODE=dev
```

⚠️ **Remember**: Never commit your `.env` file to version control!

### Step 3: Run the ML Service

You have two options for running the ML service:

#### Option A: Using Docker (Recommended)

```bash
# Navigate to ML service directory
cd docker/ml-service

# Build the Docker image
docker build -t vericrop-ml .

# Expected output: Successfully built <image-id>, Successfully tagged vericrop-ml:latest

# Run the ML service container
docker run -d -p 8000:8000 --name vericrop-ml vericrop-ml

# Expected output: <container-id>

# Verify ML service is healthy
curl http://localhost:8000/health

# Expected output: {"status":"healthy"}

# Return to project root
cd ../..
```

**Troubleshooting**: If you get "model files missing" error:
- Set `VERICROP_LOAD_DEMO=true` in `.env` or pass as environment variable:
  ```bash
  docker run -d -p 8000:8000 -e VERICROP_LOAD_DEMO=true --name vericrop-ml vericrop-ml
  ```

#### Option B: Local Python Virtual Environment

```bash
# Navigate to ML service directory
cd docker/ml-service

# Create a Python 3.11 virtual environment
python3 -m venv venv

# Activate the virtual environment
# Unix/Mac:
source venv/bin/activate
# Windows (Command Prompt):
venv\Scripts\activate.bat
# Windows (PowerShell):
venv\Scripts\Activate.ps1

# Verify Python version
python --version
# Expected: Python 3.11.x

# Install dependencies
pip install -r requirements.txt

# Expected output: Successfully installed fastapi uvicorn onnxruntime pillow numpy...

# Run the FastAPI service
uvicorn app:app --host 0.0.0.0 --port 8000

# Expected output:
# INFO:     Started server process [xxxxx]
# INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)

# Test the service (in a new terminal)
curl http://localhost:8000/health
# Expected: {"status":"healthy"}

# Return to project root (when done)
cd ../..
```

**Troubleshooting ML Service Issues**:

- **Model files not found**: Ensure `VERICROP_LOAD_DEMO=true` in environment
  ```bash
  export VERICROP_LOAD_DEMO=true  # Unix/Mac
  set VERICROP_LOAD_DEMO=true     # Windows CMD
  uvicorn app:app --host 0.0.0.0 --port 8000
  ```

- **Port 8000 already in use**: Check for existing processes
  ```bash
  # Unix/Mac
  lsof -i :8000
  # Windows
  netstat -ano | findstr :8000
  ```

- **Import errors**: Verify all dependencies installed
  ```bash
  pip list | grep -E "(fastapi|uvicorn|onnx)"
  ```

### Step 4: Build Java Artifacts

From the project root, build all Java modules:

```bash
# Build all modules
./gradlew build

# Expected output: BUILD SUCCESSFUL in XXs

# Windows
gradlew.bat build
```

**Build targets**:
- `vericrop-core`: Core business logic
- `vericrop-gui`: JavaFX application
- `kafka-service`: Kafka integration

### Step 5: Run the JavaFX GUI Application

From the project root:

```bash
# Run the GUI application
./gradlew :vericrop-gui:run

# Windows
gradlew.bat :vericrop-gui:run

# Expected output:
# > Task :vericrop-gui:run
# [JavaFX Application Thread] INFO  org.vericrop.gui.VericropGuiApplication - Starting VeriCrop GUI...
# [main] INFO  o.s.b.SpringApplication - Started VericropGuiApplication in X.XXX seconds
```

**What happens on startup**:
1. ✓ Connects to PostgreSQL at `localhost:5432`
2. ✓ Runs Flyway database migrations (creates users, batches, shipments tables)
3. ✓ Connects to Kafka at `localhost:9092`
4. ✓ Connects to ML Service at `localhost:8000`
5. ✓ Launches JavaFX login window

**Login with demo users**:
- Admin: `admin` / `admin123`
- Farmer: `farmer` / `farmer123`
- Supplier: `supplier` / `supplier123`

**Windows users**: If you encounter module errors, ensure JAVA_HOME points to JDK 17:
```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat :vericrop-gui:run
```

### Step 6: Run Airflow (Optional)

For workflow orchestration and automated quality evaluation pipelines:

```bash
# Start Airflow services via docker-compose
docker-compose up -d airflow-webserver airflow-scheduler postgres-airflow

# Expected output:
# Creating postgres-airflow ... done
# Creating vericrop-airflow-scheduler ... done
# Creating vericrop-airflow-webserver ... done

# Wait ~30 seconds for Airflow to initialize

# Access Airflow UI
# URL: http://localhost:8080
# Username: admin
# Password: admin (or check AIRFLOW_ADMIN_PASSWORD in .env)

# Enable the VeriCrop DAG
# 1. Navigate to "DAGs" tab
# 2. Find "vericrop_evaluation_pipeline"
# 3. Toggle the switch to "On"
# 4. Click "Trigger DAG" to run manually
```

**Note**: Airflow may take 1-2 minutes to initialize the database and load DAGs.

### Development Workflow

Recommended workflow for iterative development:

```bash
# 1. Make code changes in your IDE (IntelliJ IDEA, VS Code, etc.)

# 2. Build affected modules
./gradlew :vericrop-core:build        # If you changed core
./gradlew :vericrop-gui:build         # If you changed GUI
./gradlew :kafka-service:build        # If you changed Kafka integration

# 3. Run tests for changed modules
./gradlew :vericrop-core:test
./gradlew :vericrop-gui:test

# 4. Run the application to test changes
./gradlew :vericrop-gui:run

# 5. View application logs
tail -f logs/vericrop-gui.log         # Unix/Mac
type logs\vericrop-gui.log            # Windows CMD
Get-Content logs\vericrop-gui.log -Tail 50  # Windows PowerShell

# 6. Clean build artifacts when needed
./gradlew clean
```

**Tips for faster iteration**:
- Keep infrastructure services running (`docker-compose up -d postgres kafka zookeeper`)
- Only restart the GUI application when you make code changes
- Use `./gradlew :vericrop-gui:run --continuous` for continuous build (experimental)
- Check logs in real-time for debugging

## Generated Output Directories

<details>
<summary><b>View Generated Output Directories</b> (QR codes, reports, ledgers, logs)</summary>

The VeriCrop GUI generates various output files during operation:

### QR Codes
- **Location**: `generated_qr/`
- **Format**: RGB PNG images (300x300px)
- **Naming**: `product_{batchId}.png`, `shipment_{shipmentId}.png`
- **Content**: JSON payload with product/shipment details
- **Usage**: Scannable by QR readers for product verification

### Reports
- **Location**: `generated_reports/`
- **Formats**: CSV, JSON
- **Types**:
  - Journey reports: `journey_report_{shipmentId}_{timestamp}.csv/json`
  - Quality reports: `quality_report_{batchId}_{timestamp}.csv`
  - Analytics reports: `analytics_{reportName}_{timestamp}.csv`
- **Usage**: Export data for analysis, auditing, compliance

### Message Ledger
- **Location**: `ledger/messages.jsonl`
- **Format**: JSON Lines (one message per line)
- **Content**: All user-to-user and system messages
- **Persistence**: Automatic, survives application restarts

### Blockchain Ledger
- **Location**: `blockchain.json`, `vericrop_chain.json`
- **Format**: JSON
- **Content**: Immutable blockchain records for batches
- **Usage**: Audit trail, verification

### Concurrent Scenario Execution (NEW)

VeriCrop supports concurrent execution of multiple delivery scenarios with integrated monitoring across all domains. This feature enables running different environmental conditions simultaneously and comparing results.

#### Architecture

The system coordinates 6 domains for concurrent scenario execution:
1. **Scenarios** - Environmental condition presets (NORMAL, HOT_TRANSPORT, COLD_STORAGE, HUMID_ROUTE, EXTREME_DELAY)
2. **Delivery** - Shipment simulation with quality decay
3. **Map** - Route generation with waypoints and geographic data
4. **Temperature** - Cold chain compliance monitoring
5. **Supplier Compliance** - Performance metrics and evaluation
6. **Simulations** - Orchestrated parallel execution management

#### Components

**ScenarioController** (`src/vericrop-core/main/java/org/vericrop/service/ScenarioController.java`)
- Orchestrates concurrent execution across all 6 domains
- Uses CompletableFuture for non-blocking parallel processing
- Aggregates results from all domains
- Provides cross-domain status monitoring API

**Domain Services:**
- **TemperatureService**: Tracks temperature readings, violations, and compliance
- **MapService**: Generates routes with environmental conditions applied by scenario
- **SupplierComplianceService**: Evaluates supplier performance (success rate, spoilage rate, quality decay)

**SimulationOrchestrator** (Enhanced)
- Manages thread pool for concurrent scenario execution (pool size: 20)
- Publishes Kafka events for scenario lifecycle (STARTED, RUNNING, COMPLETED, FAILED)
- Monitors active orchestrations and publishes periodic status updates

#### Usage Example

```java
// Initialize services
MessageService messageService = new MessageService();
AlertService alertService = new AlertService();
DeliverySimulator deliverySimulator = new DeliverySimulator(messageService, alertService);
SimulationOrchestrator orchestrator = new SimulationOrchestrator(deliverySimulator, alertService, messageService);
TemperatureService temperatureService = new TemperatureService();
MapService mapService = new MapService();
SupplierComplianceService complianceService = new SupplierComplianceService();

ScenarioController controller = new ScenarioController(
    orchestrator, deliverySimulator, temperatureService, 
    mapService, complianceService, messageService);

// Define scenarios to run concurrently
List<Scenario> scenarios = Arrays.asList(
    Scenario.NORMAL,
    Scenario.HOT_TRANSPORT,
    Scenario.COLD_STORAGE
);

// Execute scenarios concurrently
GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "New York");
GeoCoordinate destination = new GeoCoordinate(42.3601, -71.0589, "Boston");

CompletableFuture<ScenarioExecutionResult> future = controller.executeScenarios(
    origin, destination, 
    5,        // waypoints
    60.0,     // avg speed km/h
    "FARMER_001",
    scenarios,
    1000      // update interval ms
);

ScenarioExecutionResult result = future.get();

// Check results
System.out.println("Success: " + result.isSuccess());
System.out.println("Scenarios started: " + result.getScenarioBatchIds().size());

// Get real-time status
Map<String, Object> status = controller.getExecutionStatus(result.getExecutionId());
System.out.println("Running scenarios: " + status.get("scenario_count"));
```

#### Kafka Event Streams

**scenario-events** topic:
- `STARTED`: Scenario execution began
- `RUNNING`: Scenario is actively running
- `COMPLETED`: Scenario finished successfully
- `FAILED`: Scenario encountered an error

**temperature-events** topic:
- `MONITORING_STARTED`: Temperature monitoring activated for batch
- `READING`: Temperature reading recorded
- `VIOLATION`: Temperature outside safe range (2-8°C)
- `COMPLIANT`: Batch remained within temperature thresholds
- `MONITORING_STOPPED`: Monitoring ended

**supplier-compliance-events** topic:
- `DELIVERY_RECORDED`: Delivery outcome recorded
- `COMPLIANCE_UPDATED`: Compliance status recalculated
- `WARNING`: Supplier performance degrading
- `NON_COMPLIANT`: Supplier below compliance thresholds

#### Testing

Comprehensive unit tests verify concurrent execution:
- **TemperatureServiceTest**: 7 tests for monitoring and violations
- **MapServiceTest**: 8 tests for route generation with scenarios
- **SupplierComplianceServiceTest**: 10 tests for compliance tracking
- **ScenarioControllerTest**: 6 tests including full 6-domain integration

Run tests:
```bash
./gradlew :vericrop-core:test --tests "*ScenarioControllerTest"
```

### Persistence and Report Export (NEW)

VeriCrop now includes a complete data persistence layer and multi-format report export functionality.

#### Persistence Features

- **Location**: `data/` directory
- **Files**:
  - `shipments.json` - Persisted shipment records
  - `simulations.json` - Persisted simulation run logs
- **Features**:
  - Automatic persistence of shipment updates during simulations
  - Complete simulation run history with temperature series
  - Date range queries for historical data
  - JSON-based storage with automatic initialization

#### Report Export Features

- **Location**: `generated_reports/` directory
- **Supported Formats**: TXT (human-readable), CSV (spreadsheet-compatible)
- **Report Types**:
  - **Shipment Summary**: Complete shipment list with status and environmental data
  - **Temperature Log**: Temperature and humidity readings over time
  - **Quality Compliance**: Compliance status and violation counts
  - **Delivery Performance**: Duration, quality, and temperature metrics
  - **Simulation Log**: Detailed simulation run history
- **Filename Format**: `{report-type}_{start-date}_to_{end-date}.{format}`
  - Example: `temperature-log_2025-11-01_to_2025-11-30.csv`

#### Using Report Export

1. Navigate to the **Logistics** screen
2. Select the **Reports** tab
3. Choose a **Report Type** from the dropdown
4. Select **Start Date** and **End Date** (optional, defaults to last 30 days)
5. Choose **Format** (TXT or CSV) from the format dropdown
6. Click **Export Report** to generate and save the file

#### Temperature Graph in Simulations

Simulation responses now include `temperatureSeries` data that populates the live temperature monitoring graph:

```json
{
  "batch_id": "BATCH_001",
  "temperature_series": [
    {"timestamp": 1700000000000, "temperature": 4.5, "location": "Highway Mile 10"},
    {"timestamp": 1700000001000, "temperature": 4.8, "location": "Highway Mile 20"}
  ],
  "avg_temperature": 4.65,
  "compliance_status": "COMPLIANT"
}
```

**Note**: All generated directories (`data/`, `generated_reports/`) are excluded from version control via `.gitignore`.

### Logistics and Supply Chain Directory (NEW)
- **Location**: `logistics-and-supply-chain/`
- **Purpose**: Centralized export location for all supply chain reports and data
- **Structure**:
  - `reports/`: CSV and PDF reports (journey, quality, shipment)
  - `summaries/`: Aggregated metrics and KPI summaries
  - `batches/`: Individual batch data exports with complete metadata
  - `supply-chain-events/`: Timeline logs of all supply chain events
- **Formats**: CSV (machine-readable), PDF (human-readable), JSON (structured data)
- **Features**:
  - Aggregated metrics: average quality%, prime%, rejection%
  - Timeline logs with timestamps for full traceability
  - Batch summaries with quality metrics, QR codes, and image paths
  - Supply chain event logs (creation, transit, delivery)

**Note**: All generated directories are excluded from version control via `.gitignore`.

</details>

## Configuration

<details>
<summary><b>View Configuration Details</b> (environment variables, settings, connection pools)</summary>

VeriCrop uses environment variables for configuration. All settings have sensible defaults for local development.

### Environment Variables

Copy `.env.example` to `.env` and customize as needed:

```bash
cp .env.example .env
```

### Key Configuration Sections

#### PostgreSQL (Batch Metadata)

```bash
POSTGRES_USER=vericrop           # Database user
POSTGRES_PASSWORD=vericrop123    # Database password
POSTGRES_DB=vericrop             # Database name
POSTGRES_HOST=localhost          # Database host
POSTGRES_PORT=5432               # Database port

# Connection Pool (HikariCP)
DB_POOL_SIZE=10                  # Connection pool size
DB_CONNECTION_TIMEOUT=30000      # Timeout in milliseconds
```

**Note**: PostgreSQL stores batch metadata. Blockchain ledger and shipment records are stored in file-based JSONL format.

#### Kafka (Event Messaging)

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Kafka broker address
KAFKA_ENABLED=true                      # Enable/disable Kafka
KAFKA_ACKS=all                         # Producer acknowledgment (all/1/0)
KAFKA_RETRIES=3                        # Retry attempts
KAFKA_IDEMPOTENCE=true                 # Idempotent producer

# Kafka Topics
KAFKA_TOPIC_BATCH_EVENTS=batch-events
KAFKA_TOPIC_QUALITY_ALERTS=quality-alerts
KAFKA_TOPIC_LOGISTICS_EVENTS=logistics-events
KAFKA_TOPIC_BLOCKCHAIN_EVENTS=blockchain-events
```

**Note**: Set `KAFKA_ENABLED=false` to run without Kafka. The application will use in-memory messaging.

#### ML Service (FastAPI)

```bash
ML_SERVICE_URL=http://localhost:8000    # ML service base URL
ML_SERVICE_TIMEOUT=30000                # HTTP timeout (ms)
ML_SERVICE_RETRIES=3                    # Retry attempts
VERICROP_LOAD_DEMO=true                 # Enable demo mode
```

**Note**: When `VERICROP_LOAD_DEMO=true`, the ML service returns mock predictions if the ONNX model is unavailable.

#### Application Settings

```bash
VERICROP_MODE=dev                       # dev or prod
SERVER_PORT=8080                        # REST API port
LOG_LEVEL=INFO                          # Logging level (DEBUG, INFO, WARN, ERROR)
QUALITY_PASS_THRESHOLD=0.7              # Quality threshold (0.0-1.0)
LEDGER_PATH=/app/ledger                 # Path for ledger storage
```

#### Airflow Configuration

```bash
AIRFLOW_ADMIN_USERNAME=admin            # Airflow UI username
AIRFLOW_ADMIN_PASSWORD=admin            # Airflow UI password
AIRFLOW_DB_USER=airflow                 # Airflow metadata DB user
AIRFLOW_DB_PASSWORD=airflow123          # Airflow metadata DB password
```

### Configuration Files

- **`.env.example`**: Template with all available configuration options
- **`src/vericrop-gui/main/resources/application.yml`**: Spring Boot application configuration (overridden by environment variables)
- **`docker-compose.yml`**: Docker Compose service configuration

### Using .env.example

The `.env.example` file contains all configuration options with comments explaining each setting:

```bash
# View the example configuration
cat .env.example

# Copy and customize
cp .env.example .env
nano .env
```

**Important**: Never commit `.env` files with real credentials to version control. The `.env.example` is tracked for reference only.

</details>

## Authentication and Messaging

<details>
<summary><b>View Authentication and Messaging Details</b> (user auth, REST API, security)</summary>

VeriCrop includes a complete user authentication and messaging system with PostgreSQL backend and REST API support.

### Features

- **User Registration**: Create accounts with username, email, password, and role selection
- **Secure Authentication**: BCrypt password hashing, account lockout protection
- **JWT Token Authentication**: REST API endpoints secured with JWT tokens
- **Role-Based Access**: Four role types (PRODUCER, CONSUMER, ADMIN, LOGISTICS)
- **User Messaging**: Send and receive messages between users
- **Inbox/Sent Items**: Manage messages with read/unread status
- **Session Management**: Secure session handling with role-based navigation

### REST API Endpoints

VeriCrop provides REST API endpoints for authentication:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Register a new user account |
| `/api/auth/login` | POST | Authenticate and receive JWT token |
| `/api/auth/validate` | GET | Validate a JWT token |
| `/api/auth/me` | GET | Get current user info from token |
| `/api/auth/health` | GET | Health check for auth service |

#### Registration Example

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "password": "SecurePass123",
    "fullName": "New User",
    "role": "PRODUCER"
  }'
```

**Password Requirements:**
- Minimum 8 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one digit (0-9)

**Response (HTTP 201 Created):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "username": "newuser",
  "role": "PRODUCER"
}
```

#### Login Example

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "SecurePass123"
  }'
```

**Response (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "newuser",
  "role": "PRODUCER",
  "fullName": "New User",
  "expiresIn": 86400
}
```

#### Using JWT Token

Include the JWT token in the Authorization header for authenticated requests:

```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Quick Start (GUI)

1. **Start the application** - Login screen appears automatically
2. **Use demo accounts** or register a new account:
   - Username: `admin`, Password: `admin123` (ADMIN role)
   - Username: `farmer`, Password: `farmer123` (FARMER role)
   - Username: `supplier`, Password: `supplier123` (SUPPLIER role)
3. **Access messaging** - Navigate to inbox to send/receive messages

### Database Migrations

Four Flyway migrations manage the database schema:

- **V1**: Batches and quality tracking tables
- **V2**: Users table with BCrypt authentication
- **V3**: Shipments tracking table
- **V4**: Messages table for user-to-user messaging

Migrations run automatically on application startup. No manual setup required!

### Authentication Features

- **BCrypt Password Hashing**: Secure password storage (never plaintext)
- **JWT Token Authentication**: Stateless authentication for REST API (24-hour expiration)
- **Failed Login Tracking**: Account locks after 5 failed attempts
- **Lockout Duration**: 30 minutes automatic unlock
- **Role-Based Access**: Different dashboards for each role
- **Session Persistence**: Sessions maintained until application close

### Security Considerations

1. **Password Security**: Passwords are hashed with BCrypt (cost factor 10)
2. **Token Security**: JWT tokens are signed with HMAC-SHA256
3. **Account Protection**: Automatic lockout after failed attempts
4. **Error Messages**: Generic error messages prevent user enumeration
5. **Input Validation**: Server-side validation for all inputs

For production deployment, configure the JWT secret:
```bash
export JWT_SECRET="your-very-long-and-secure-secret-key-min-32-chars"
```

### Messaging Features

- **Compose Messages**: Send messages to any active user
- **Inbox View**: List of received messages with unread count
- **Sent Items**: View all messages you've sent
- **Mark as Read/Unread**: Toggle message read status
- **Reply**: Quick reply with pre-filled recipient and subject
- **Delete**: Soft delete (hidden but preserved for audit)
- **Message Preview**: See first 100 characters in message list
- **Auto-mark Read**: Inbox messages marked read when viewed

### Contacts and Messaging Features (NEW)

- **Participant Discovery**: Automatic discovery of other connected GUI instances
- **Contact List**: View all active participants with online/offline status indicators
- **Real-Time Status**: Online status updates with 5-minute activity threshold
- **Contact-Based Messaging**: Send messages directly to contacts from contacts view
- **Message History**: View complete conversation history with each contact
- **Auto-Refresh**: Contacts list automatically refreshes every 5 seconds
- **Role-Based Display**: See participant roles (FARMER, SUPPLIER, CONSUMER, ADMIN)
- **Connection Tracking**: Store and retrieve connection information for future features

📖 **[Contacts and Messaging Guide](vericrop-gui/CONTACTS_AND_MESSAGING.md)** - Complete guide for contacts and messaging system

### Documentation

For detailed setup instructions, user guides, and troubleshooting:

📖 **[GUI Setup Guide](docs/GUI-setup.md)** - Complete guide for authentication and messaging

### Security Best Practices

For production deployment:

1. **Change default passwords** in V2 migration or via SQL
2. **Use environment variables** for database credentials
3. **Enable SSL/TLS** for database connections
4. **Review audit logs** regularly (check `last_login` and `failed_login_attempts`)
5. **Update passwords** periodically

</details>

## Verify Services

After starting services, use these commands to verify everything is running correctly.

### Quick Health Checks

```bash
# Check all Docker containers
docker-compose ps

# Expected: All services should show "Up" or "Up (healthy)" status
```

### ML Service Health Check

```bash
# Test ML service health endpoint
curl http://localhost:8000/health

# Expected output:
# {"status":"healthy"}

# Test with detailed output
curl -v http://localhost:8000/health

# If using Windows PowerShell:
Invoke-WebRequest -Uri http://localhost:8000/health
```

**Troubleshooting**:
- If connection refused: Check if ML service container is running (`docker ps | grep ml-service`)
- If unhealthy: Check logs (`docker logs vericrop-ml-service`)
- If model errors: Ensure `VERICROP_LOAD_DEMO=true`

### PostgreSQL Database Verification

```bash
# Check if PostgreSQL is accepting connections
docker exec -it vericrop-postgres pg_isready -U vericrop

# Expected output: vericrop-postgres:5432 - accepting connections

# Connect to database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

# Inside psql, verify tables exist:
# \dt
# Expected: batches, users, shipments, messages, flyway_schema_history

# Verify Flyway migrations applied
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"

# Expected output:
#  version |          description           |        installed_on        
# ---------+--------------------------------+----------------------------
#  1       | create batches table           | 2024-XX-XX XX:XX:XX.XXXXXX
#  2       | create users table             | 2024-XX-XX XX:XX:XX.XXXXXX
#  3       | create shipments table         | 2024-XX-XX XX:XX:XX.XXXXXX
#  4       | create messages table          | 2024-XX-XX XX:XX:XX.XXXXXX

# Verify demo users exist
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, role, status, created_at FROM users;"

# Expected output: admin, farmer, supplier users

# Check batch count
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT COUNT(*) as batch_count FROM batches;"

# Check database size
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT pg_size_pretty(pg_database_size('vericrop')) as db_size;"
```

### Kafka Verification

```bash
# Check Kafka broker is running
docker ps | grep kafka

# List Kafka topics
docker exec -it vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092

# Expected topics (may not exist until first use):
# - batch-events
# - quality-alerts
# - logistics-events
# - blockchain-events
# - evaluation-requests
# - evaluation-results

# Describe a topic (if it exists)
docker exec -it vericrop-kafka kafka-topics \
  --describe --topic batch-events \
  --bootstrap-server localhost:9092

# Check Kafka logs
docker logs vericrop-kafka --tail 50

# Access Kafka UI (if running)
# Open browser: http://localhost:8081
```

**Troubleshooting Kafka**:
- If topics don't exist: They are created automatically on first message
- If connection timeout: Ensure Zookeeper is running (`docker ps | grep zookeeper`)
- If broker not available: Check `KAFKA_ADVERTISED_LISTENERS` in docker-compose.yml

### Airflow Verification

```bash
# Check Airflow services are running
docker-compose ps | grep airflow

# Expected: airflow-webserver and airflow-scheduler both "Up"

# Check Airflow webserver logs
docker-compose logs airflow-webserver --tail 50

# Access Airflow UI
# Open browser: http://localhost:8080
# Login: admin / admin (or your AIRFLOW_ADMIN_PASSWORD)

# Verify DAGs loaded
docker exec -it vericrop-airflow-scheduler airflow dags list

# Expected output should include:
# vericrop_evaluation_pipeline

# Check DAG status
docker exec -it vericrop-airflow-scheduler \
  airflow dags show vericrop_evaluation_pipeline
```

### Docker Container Logs

View logs for troubleshooting:

```bash
# View logs for specific service
docker logs vericrop-postgres
docker logs vericrop-kafka
docker logs vericrop-ml-service
docker logs vericrop-zookeeper

# Follow logs in real-time
docker logs -f vericrop-ml-service

# View last 100 lines
docker logs --tail 100 vericrop-postgres

# View logs from all services
docker-compose logs

# Follow logs from all services
docker-compose logs -f

# View logs for specific service via docker-compose
docker-compose logs ml-service
```

### Service Port Verification

Verify all services are listening on expected ports:

```bash
# Check all listening ports
docker-compose ps

# Check specific ports (Unix/Mac)
lsof -i :5432   # PostgreSQL
lsof -i :9092   # Kafka
lsof -i :2181   # Zookeeper
lsof -i :8000   # ML Service
lsof -i :8080   # Airflow UI
lsof -i :8081   # Kafka UI

# Windows
netstat -ano | findstr :5432
netstat -ano | findstr :9092
netstat -ano | findstr :8000
netstat -ano | findstr :8080
```

### Full Stack Verification Script

Run all verification checks at once:

```bash
# Create a verification script in project directory
cat > ./scripts/verify_vericrop.sh << 'EOF'
#!/bin/bash
echo "=== VeriCrop Service Verification ==="
echo ""
echo "1. Docker Containers:"
docker-compose ps
echo ""
echo "2. ML Service Health:"
curl -s http://localhost:8000/health
echo ""
echo "3. PostgreSQL Connection:"
docker exec vericrop-postgres pg_isready -U vericrop
echo ""
echo "4. Kafka Topics:"
docker exec vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null || echo "No topics yet (will be created on first use)"
echo ""
echo "5. Database Tables:"
docker exec vericrop-postgres psql -U vericrop -d vericrop -c "\dt"
echo ""
echo "6. Demo Users:"
docker exec vericrop-postgres psql -U vericrop -d vericrop -c "SELECT username, role FROM users;"
echo ""
echo "=== Verification Complete ==="
EOF

chmod +x ./scripts/verify_vericrop.sh
./scripts/verify_vericrop.sh

# Windows (PowerShell) - Run commands individually
```

### Expected Results Summary

When all services are healthy, you should see:

✅ **Docker Compose**: All containers "Up" or "Up (healthy)"
✅ **ML Service**: `{"status":"healthy"}` response on port 8000
✅ **PostgreSQL**: Accepting connections, migrations applied, demo users exist
✅ **Kafka**: Broker running, topics created (or ready to create)
✅ **Airflow**: UI accessible, DAGs loaded
✅ **No port conflicts**: All services bound to their respective ports

If any checks fail, see the [Troubleshooting](#troubleshooting) section.

## ML Service Contract

<details>
<summary><b>View ML Service API Details</b> (REST endpoints, prediction API, batch management)</summary>

The ML Service provides REST API endpoints for quality prediction and dashboard data.

### Base URL

```
http://localhost:8000
```

### Endpoints

#### Health Check

```bash
GET /health

# Response
{
  "status": "healthy"
}
```

#### Predict Quality (Image Upload)

```bash
POST /predict
Content-Type: multipart/form-data

# Request (Form Data)
file: <image-file>

# Response
{
  "quality_score": 0.92,
  "quality_label": "Fresh",
  "confidence": 0.95,
  "metadata": {
    "color_consistency": 0.88,
    "size_uniformity": 0.85,
    "defect_density": 0.02
  }
}
```

#### List Batches

```bash
GET /batches

# Response
{
  "batches": [
    {
      "batch_id": "BATCH_001",
      "name": "Apple Batch 001",
      "farmer": "John Farmer",
      "quality_score": 0.92,
      "timestamp": "2024-01-15T10:30:00Z"
    }
  ]
}
```

#### Farm Dashboard Data

```bash
GET /dashboard/farm

# Response
{
  "total_batches": 45,
  "avg_quality": 0.87,
  "recent_batches": [...]
}
```

#### Analytics Dashboard Data

```bash
GET /dashboard/analytics

# Response
{
  "kpi_metrics": {
    "total_batches": 45,
    "avg_quality": 0.87,
    "spoilage_rate": 0.03
  },
  "quality_trends": [...]
}
```

### Integration with GUI and CLI

The ML Service is called by:

1. **JavaFX GUI**: `vericrop-gui` uses `MLClientService` to communicate with the ML service
2. **REST API**: Spring Boot controllers proxy requests to the ML service
3. **Airflow DAG**: Workflow tasks call endpoints directly

### Implementation Details

For detailed implementation, inspect:
- **ML Service**: [docker/ml-service/app.py](docker/ml-service/app.py)
- **Java Client**: `src/vericrop-gui/main/java/org/vericrop/gui/clients/MLClientService.java`
- **Model Logic**: `docker/ml-service/app.py` (prediction functions and ONNX model integration)

</details>

## Producer API (Blockchain Record Creation)

<details>
<summary><b>View Producer API Details</b> (blockchain records, REST endpoints)</summary>

The Producer API provides REST endpoints for creating and managing blockchain records for agricultural products.

### Base URL

```
http://localhost:8080/producer
```

### Endpoints

#### Create Blockchain Record

Create a new immutable blockchain record for a producer's batch/crop registration.

```bash
POST /producer/blockchain-record
Content-Type: application/json

# Request
{
  "producerId": "FARMER_001",
  "batchName": "Apple Batch 2024-01",
  "productType": "Apple",
  "quantity": 500,
  "qualityScore": 0.92,
  "location": "Sunny Valley Farm",
  "additionalData": {
    "variety": "Honeycrisp",
    "harvestDate": "2024-01-15"
  }
}

# Response (HTTP 201 Created)
{
  "success": true,
  "record_id": "BATCH_1700000000001",
  "block_index": 5,
  "block_hash": "a1b2c3d4e5f6...",
  "previous_hash": "f6e5d4c3b2a1...",
  "data_hash": "sha256_of_payload...",
  "timestamp": 1700000000001,
  "ledger_id": "SHIP_1700000000001",
  "message": "Blockchain record created successfully"
}
```

**Required Fields:**
- `producerId`: Producer/farmer identifier
- `batchName`: Name/description of the batch
- `productType`: Type of product (e.g., Apple, Banana)

**Optional Fields:**
- `batchId`: Custom batch ID (auto-generated if not provided)
- `quantity`: Quantity in units
- `qualityScore`: Quality score from 0.0 to 1.0
- `location`: Location/origin of the batch
- `additionalData`: Additional metadata as key-value pairs

#### Get Blockchain Status

```bash
GET /producer/blockchain?limit=10

# Response
{
  "success": true,
  "total_blocks": 15,
  "chain_valid": true,
  "recent_blocks": [
    {
      "index": 14,
      "hash": "a1b2c3d4e5f6...",
      "previous_hash": "f6e5d4c3b2a1...",
      "timestamp": 1700000000001,
      "transaction_count": 1,
      "participant": "FARMER_001"
    }
  ],
  "timestamp": 1700000000001
}
```

#### Validate Blockchain Integrity

```bash
GET /producer/blockchain/validate

# Response
{
  "success": true,
  "valid": true,
  "block_count": 15,
  "message": "Blockchain is valid and tamper-free",
  "timestamp": 1700000000001
}
```

#### Get Batch Transactions

```bash
GET /producer/batch/{batchId}

# Response
{
  "success": true,
  "batch_id": "BATCH_001",
  "transaction_count": 1,
  "transactions": [
    {
      "type": "CREATE_BATCH",
      "from": "FARMER_001",
      "to": "blockchain",
      "batch_id": "BATCH_001",
      "data": "{...}"
    }
  ]
}
```

#### Health Check

```bash
GET /producer/health

# Response
{
  "status": "UP",
  "service": "producer-api",
  "blockchain_blocks": 15,
  "blockchain_valid": true,
  "kafka_enabled": true,
  "timestamp": 1700000000001
}
```

### Example: Create Blockchain Record with curl

```bash
# Create a blockchain record for a new batch
curl -X POST http://localhost:8080/producer/blockchain-record \
  -H "Content-Type: application/json" \
  -d '{
    "producerId": "FARMER_001",
    "batchName": "Organic Apple Batch",
    "productType": "Apple",
    "quantity": 1000,
    "qualityScore": 0.95,
    "location": "Sunny Valley Farm"
  }'

# Validate the blockchain
curl http://localhost:8080/producer/blockchain/validate

# Get blockchain status
curl http://localhost:8080/producer/blockchain?limit=5
```

### Error Responses

```json
// HTTP 400 - Validation Error
{
  "success": false,
  "error": "Validation failed",
  "details": "producerId is required",
  "timestamp": 1700000000001
}

// HTTP 500 - Internal Server Error
{
  "success": false,
  "error": "Internal server error",
  "details": "An unexpected error occurred",
  "timestamp": 1700000000001
}

// HTTP 504 - Gateway Timeout
{
  "success": false,
  "error": "Blockchain operation timed out",
  "details": "The blockchain operation took too long. Please try again.",
  "timestamp": 1700000000001
}
```

</details>

---

## 🧪 Running Tests

VeriCrop includes comprehensive test suites. Always run tests before committing changes.

### Quick Test Commands

```bash
# Run all Java tests
./gradlew test

# Run all Python tests (from docker/ml-service directory)
pytest

# Run with coverage
./gradlew test jacocoTestReport
pytest --cov=. --cov-report=html
```

<details>
<summary>📖 Detailed Testing Guide</summary>

### Java Tests

**Run by Module:**
```bash
./gradlew :vericrop-core:test      # Core business logic
./gradlew :vericrop-gui:test       # GUI application
./gradlew :kafka-service:test      # Kafka service
```

**Run Specific Tests:**
```bash
./gradlew test --tests "*BlockchainTest"
./gradlew test --tests "*MLServiceClientTest"
./gradlew test --tests "*QualityEvaluationServiceTest"
```

**View Reports:**
```bash
# Reports generated at: <module>/build/reports/tests/test/index.html
open vericrop-core/build/reports/tests/test/index.html  # Unix/Mac
start build\reports\tests\test\index.html              # Windows
```

### Python Tests

**Setup:**
```bash
cd docker/ml-service
source venv/bin/activate  # Unix/Mac
venv\Scripts\activate     # Windows
pip install -r requirements-test.txt
```

**Run Tests:**
```bash
pytest                              # All tests
pytest -v                           # Verbose
pytest tests/test_predict.py        # Specific file
pytest -k "health"                  # Pattern match
```

**Coverage:**
```bash
pytest --cov=. --cov-report=html    # HTML report
pytest --cov=. --cov-report=term    # Terminal report
# View at: htmlcov/index.html
```

</details>

### Manual Service Testing

```bash
# ML Service
curl http://localhost:8000/health
curl -X POST -F "file=@examples/sample.jpg" http://localhost:8000/predict

# Database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT COUNT(*) FROM batches;"

# Kafka
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic batch-events --from-beginning
```

### Test Coverage Summary

| Component | Coverage | Status |
|-----------|----------|--------|
| Blockchain operations | ✅ Full | Comprehensive unit tests |
| Quality evaluation service | ✅ Core | Logic tested |
| File ledger service | ✅ Full | Read/write tested |
| ML service health | ✅ Full | API contract tested |
| Batch operations | ✅ Full | Database ops tested |
| User authentication | ✅ Full | BCrypt & validation |
| Kafka messaging | ✅ Full | Message handling |
| Integration tests | ⚠️ Partial | Service interactions |
| GUI controllers | ⚠️ Manual | JavaFX UI testing |

---

# Run with short output
pytest -q

# Run and show print statements
pytest -s

# Run and stop on first failure
pytest -x

# Run and show local variables on failure
pytest -l
```

### Integration Tests

Test the interaction between services:

```bash
# Ensure all services are running
docker-compose up -d

# Wait for services to be healthy
sleep 30

# Run integration tests (if available)
./gradlew :vericrop-gui:integrationTest

# Or run manual integration tests
./gradlew test --tests "*IntegrationTest"
```

### Manual Testing

Verify services manually with curl commands:

#### Test ML Service

```bash
# Health check
curl http://localhost:8000/health
# Expected: {"status":"healthy"}

# Predict quality (requires image file)
curl -X POST -F "file=@examples/sample.jpg" http://localhost:8000/predict
# Expected: {"quality_score":0.XX,"quality_label":"Fresh","confidence":0.XX,...}

# List batches
curl http://localhost:8000/batches
# Expected: {"batches":[...]}

# Get dashboard data
curl http://localhost:8000/dashboard/farm
curl http://localhost:8000/dashboard/analytics
```

#### Test PostgreSQL

```bash
# Count batches
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT COUNT(*) FROM batches;"

# List users
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, role FROM users;"

# Check migrations
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT version, description FROM flyway_schema_history;"
```

#### Test Kafka

```bash
# List topics
docker exec -it vericrop-kafka kafka-topics \
  --list --bootstrap-server localhost:9092

# Describe topic
docker exec -it vericrop-kafka kafka-topics \
  --describe --topic batch-events \
  --bootstrap-server localhost:9092

# Produce test message
echo "test-message" | docker exec -i vericrop-kafka kafka-console-producer \
  --broker-list localhost:9092 --topic batch-events

# Consume messages (Ctrl+C to stop)
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic batch-events --from-beginning
```

### Continuous Testing

For continuous development:

```bash
# Run tests automatically on file changes (experimental)
./gradlew test --continuous

# Run specific module continuously
./gradlew :vericrop-core:test --continuous
```

### Test Coverage Summary

Current test coverage across modules:

- ✅ **Blockchain operations** - Full unit test coverage
- ✅ **Quality evaluation service** - Core logic tested
- ✅ **File ledger service** - Read/write operations tested
- ✅ **ML service health endpoints** - API contract tested
- ✅ **Batch creation and retrieval** - Database operations tested
- ✅ **User authentication** - BCrypt hashing and validation tested
- ✅ **Kafka producers/consumers** - Message handling tested
- ⚠️ **Integration tests** - Partial coverage (services interaction)
- ⚠️ **GUI controllers** - Primarily manual testing (JavaFX UI)

### Running Tests in CI/CD

Tests are automatically run in GitHub Actions on pull requests:

```bash
# View CI configuration
cat .github/workflows/ci.yml

# Tests run on:
# - Push to main branch
# - Pull requests
# - Manual workflow dispatch
```

### Troubleshooting Test Failures

If tests fail:

1. **Check test logs**:
   ```bash
   ./gradlew test --info
   # or
   pytest -vv
   ```

2. **Clean and rebuild**:
   ```bash
   ./gradlew clean build
   ```

3. **Ensure services are available** (for integration tests):
   ```bash
   docker-compose ps
   curl http://localhost:8000/health
   ```

4. **Check for port conflicts**:
   ```bash
   lsof -i :8080  # or other test ports
   ```

5. **Review test reports**:
   ```bash
   # Java reports
   open build/reports/tests/test/index.html
   
   # Python coverage
   open htmlcov/index.html
   ```

## Simulation Features

VeriCrop includes a comprehensive simulation system for testing and demonstration purposes. The simulation integrates:
- Map-based entity tracking (producers, vehicles, warehouses, consumers)
- Real-time temperature and humidity monitoring
- Three pre-configured delivery scenarios
- REST API for programmatic access

For detailed simulation testing procedures, see [TESTING_SIMULATION.md](docs/guides/TESTING_SIMULATION.md).

## Stopping and Cleaning

Properly stop services and clean up resources when done.

### Stop All Services

```bash
# Stop all Docker Compose services
docker-compose down

# Expected output:
# Stopping vericrop-ml-service ... done
# Stopping vericrop-kafka ... done
# Stopping vericrop-postgres ... done
# Stopping vericrop-zookeeper ... done
# Removing vericrop-ml-service ... done
# Removing vericrop-kafka ... done
# Removing vericrop-postgres ... done
# Removing vericrop-zookeeper ... done
# Removing network vericrop-miniproject_vericrop-network
```

### Stop and Remove Volumes

**⚠️ Warning**: This removes all data (databases, Kafka logs, etc.)

```bash
# Stop services and remove volumes
docker-compose down -v

# Expected output: Same as above, plus:
# Removing volume vericrop-miniproject_postgres_data
# Removing volume vericrop-miniproject_postgres_airflow_data
```

Use this when you want a completely fresh start.

### Stop Specific Services

```bash
# Stop only specific services
docker-compose stop postgres
docker-compose stop kafka
docker-compose stop ml-service

# Restart specific services
docker-compose restart postgres
docker-compose restart ml-service
```

### Clean Gradle Build Artifacts

```bash
# Clean all build artifacts
./gradlew clean

# Expected output:
# BUILD SUCCESSFUL
# Removes: build/ directories, compiled classes, JARs

# Clean specific module
./gradlew :vericrop-core:clean
./gradlew :vericrop-gui:clean

# Windows
gradlew.bat clean
```

### Clean Python Virtual Environment

```bash
# Remove Python virtual environment
cd docker/ml-service
rm -rf venv                # Unix/Mac
rmdir /s venv              # Windows CMD
Remove-Item -Recurse venv  # Windows PowerShell

# Remove Python cache
find . -type d -name "__pycache__" -exec rm -rf {} +  # Unix/Mac
rm -rf __pycache__
rm -rf .pytest_cache
rm -rf htmlcov
rm -rf .coverage

# Windows
for /d /r . %d in (__pycache__) do @if exist "%d" rd /s /q "%d"
```

### Remove Docker Images

```bash
# List VeriCrop images
docker images | grep vericrop

# Remove ML service image
docker rmi vericrop-ml

# Remove all VeriCrop images
docker rmi $(docker images | grep vericrop | awk '{print $3}')

# Remove unused images (careful - removes all unused images)
docker image prune -a

# Remove all stopped containers
docker container prune
```

### Complete Cleanup

**⚠️⚠️⚠️ DANGER WARNING ⚠️⚠️⚠️**: This removes ALL Docker resources system-wide, not just VeriCrop!

**This will delete**:
- All stopped containers (all projects)
- All networks not in use (all projects)  
- All images without containers (all projects)
- All build cache (all projects)
- All volumes not in use (all projects)

**Only use if you want to completely reset Docker on your machine!**

```bash
# Nuclear option - removes EVERYTHING Docker-related on your system
docker system prune -a --volumes

# You will be prompted to confirm. Type 'y' only if you're absolutely sure!
# This affects ALL Docker projects, not just VeriCrop!
```

### Clean Logs

```bash
# Remove application logs
rm -rf logs/                    # Unix/Mac
rmdir /s /q logs\              # Windows CMD
Remove-Item -Recurse logs\     # Windows PowerShell

# Clean Airflow logs
rm -rf airflow/logs/*

# Clean Docker logs (requires restart)
docker-compose down
docker-compose up -d
```

### Verify Cleanup

```bash
# Check no containers are running
docker ps -a

# Check no volumes exist
docker volume ls

# Check no networks exist
docker network ls

# Check disk space freed
df -h                           # Unix/Mac
wmic logicaldisk get size,freespace,caption  # Windows
```

### Typical Cleanup Workflow

For daily development:

```bash
# Soft stop - preserves data
docker-compose down
./gradlew clean
```

For fresh restart:

```bash
# Hard stop - removes all data
docker-compose down -v
./gradlew clean
rm -rf logs/

# Then start fresh
docker-compose up -d
./gradlew build
```

### Stop ML Service (Local Python)

If running ML service locally (not in Docker):

```bash
# Find ML service process
ps aux | grep uvicorn          # Unix/Mac
tasklist | findstr python      # Windows

# Kill process
kill <PID>                     # Unix/Mac
taskkill /PID <PID> /F        # Windows

# Or use Ctrl+C in the terminal where uvicorn is running
```

### Stop GUI Application

If GUI is running:

```bash
# Stop with Ctrl+C in terminal
# or close the JavaFX window

# If process hangs, find and kill
ps aux | grep vericrop-gui     # Unix/Mac
tasklist | findstr java        # Windows

kill <PID>                     # Unix/Mac
taskkill /PID <PID> /F        # Windows
```

## Contributing

We welcome contributions to VeriCrop! Here's how to get started.

### Development Setup

1. **Fork the repository**
   ```bash
   # Fork via GitHub UI, then clone
   git clone https://github.com/YOUR_USERNAME/vericrop-miniproject.git
   cd vericrop-miniproject
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Set up development environment**
   ```bash
   # Install dependencies
   ./gradlew build
   
   # Start services
   docker-compose up -d postgres kafka ml-service
   ```

4. **Make changes and test**
   ```bash
   # Make changes in your IDE
   
   # Build and test
   ./gradlew test
   ./gradlew :vericrop-gui:run
   ```

5. **Commit and push**
   ```bash
   git add .
   git commit -m "feat: Brief description of changes"
   git push origin feature/your-feature-name
   ```

6. **Open a Pull Request**
   - Navigate to GitHub and create a PR
   - Provide clear description of changes
   - Reference any related issues

### Code Style

- **Java**: Follow existing code style (Spring Boot conventions)
- **Python**: PEP 8 style guide
- **Commit Messages**: Use conventional commits format
  - `feat:` New feature
  - `fix:` Bug fix
  - `docs:` Documentation changes
  - `test:` Test additions/changes
  - `refactor:` Code refactoring

### Testing Requirements

- Add unit tests for new features
- Ensure all tests pass before submitting PR
- Include integration tests for service interactions

### Documentation

- Update README.md if adding new features
- Add inline comments for complex logic
- Update module-specific READMEs (e.g., vericrop-gui/README.md)

### Code Review Process

1. Automated tests must pass
2. At least one maintainer approval required
3. Address review comments
4. Squash commits before merge (optional)

## Troubleshooting

<details>
<summary><b>View Troubleshooting Guide</b> (common issues, solutions, debugging)</summary>

Common issues and solutions with step-by-step resolution.

### Application Won't Start

**Symptom**: JavaFX GUI fails to launch or crashes immediately

**Reproduction**:
```bash
./gradlew :vericrop-gui:run
# Error: Could not find or load main class...
# or: Module not found error
```

**Resolution**:
1. **Check Java version** (must be 11+, 17 recommended):
   ```bash
   java -version
   # If wrong version, set JAVA_HOME:
   export JAVA_HOME=/path/to/jdk-17  # Unix/Mac
   set JAVA_HOME=C:\path\to\jdk-17   # Windows
   ```

2. **Verify services are running**:
   ```bash
   docker-compose ps
   # All services should show "Up" or "Up (healthy)"
   ```

3. **Check detailed logs**:
   ```bash
   ./gradlew :vericrop-gui:run --info --stacktrace
   # Review error messages
   ```

4. **Ensure JAVA_HOME is set correctly**:
   ```bash
   echo $JAVA_HOME           # Unix/Mac
   echo %JAVA_HOME%          # Windows CMD
   $env:JAVA_HOME            # Windows PowerShell
   
   # Should point to JDK directory (not JRE)
   ```

5. **Clean and rebuild**:
   ```bash
   ./gradlew clean build
   ./gradlew :vericrop-gui:run
   ```

**One-line fix** (if Java version is correct):
```bash
./gradlew clean :vericrop-gui:run
```

### Compilation Errors: Package org.vericrop.dto Does Not Exist

**Symptom**: Build fails with "package org.vericrop.dto does not exist" errors in kafka-service module

**Reproduction**:
```bash
./gradlew run
# Error: package org.vericrop.dto does not exist
# Cannot find symbol: class MapSimulationEvent
# Cannot find symbol: class EvaluationRequest
# Cannot find symbol: class TemperatureComplianceEvent
# ... (32 errors total)
```

**Root Cause**: 
- Corrupted Gradle build cache
- Stale build artifacts from incomplete previous builds
- Modules built out of dependency order (kafka-service before vericrop-core)

**Resolution**:
1. **Clean and rebuild** (recommended first step):
   ```bash
   ./gradlew clean build
   ```

2. **If still failing, clear Gradle cache**:
   ```bash
   # Unix/Linux/Mac
   rm -rf ~/.gradle/caches
   rm -rf .gradle
   ./gradlew clean build
   
   # Windows
   rmdir /s /q %USERPROFILE%\.gradle\caches
   rmdir /s /q .gradle
   gradlew.bat clean build
   ```

3. **Verify build order**:
   ```bash
   # Build modules in correct dependency order
   ./gradlew :vericrop-core:build
   ./gradlew :kafka-service:build
   ./gradlew :vericrop-gui:build
   ```

**One-line fix** (works in 99% of cases):
```bash
./gradlew clean build
```

**Why this works**: 
- `clean` removes all compiled classes and build artifacts
- Gradle rebuilds modules in correct dependency order
- vericrop-core compiles first, generating DTO classes
- kafka-service can then find and reference the DTO classes

### Database Connection Failed

**Symptom**: `Connection refused`, `Authentication failed`, or `org.postgresql.util.PSQLException`

**Reproduction**:
```bash
./gradlew :vericrop-gui:run
# Error: Connection to localhost:5432 refused
```

**Resolution**:
1. **Verify PostgreSQL is running**:
   ```bash
   docker-compose ps postgres
   # Should show "Up (healthy)"
   
   docker-compose logs postgres --tail 50
   # Check for error messages
   ```

2. **Check connection settings in `.env`**:
   ```bash
   cat .env | grep POSTGRES
   # Verify: POSTGRES_HOST=localhost, POSTGRES_PORT=5432
   # Credentials must match docker-compose.yml
   ```

3. **Test connection directly**:
   ```bash
   docker exec -it vericrop-postgres psql -U vericrop -d vericrop
   # If this fails, PostgreSQL isn't accepting connections
   ```

4. **Check if port 5432 is available**:
   ```bash
   lsof -i :5432              # Unix/Mac
   netstat -ano | findstr :5432  # Windows
   # If another process is using it, stop that process or change port
   ```

5. **Reset PostgreSQL**:
   ```bash
   docker-compose down
   docker-compose up -d postgres
   # Wait 10 seconds for initialization
   docker-compose logs postgres
   ```

**One-line fix** (nuclear option - loses all data):
```bash
docker-compose down -v && docker-compose up -d postgres && sleep 10
```

### Kafka Connection Failed

**Symptom**: `TimeoutException`, `Node not available`, or `org.apache.kafka.common.errors.TimeoutException`

**Reproduction**:
```bash
./gradlew :vericrop-gui:run
# Error: Failed to update metadata after 60000 ms
```

**Resolution**:
1. **Verify Kafka and Zookeeper are running**:
   ```bash
   docker-compose ps kafka zookeeper
   # Both should show "Up" or "Up (healthy)"
   
   docker-compose logs kafka --tail 50
   docker-compose logs zookeeper --tail 50
   ```

2. **Check bootstrap servers in `.env`**:
   ```bash
   cat .env | grep KAFKA
   # Should be: KAFKA_BOOTSTRAP_SERVERS=localhost:9092
   ```

3. **Test Kafka directly**:
   ```bash
   docker exec -it vericrop-kafka kafka-broker-api-versions \
     --bootstrap-server localhost:9092
   # If this fails, Kafka isn't accepting connections
   ```

4. **Disable Kafka for testing** (temporary workaround):
   ```bash
   export KAFKA_ENABLED=false
   ./gradlew :vericrop-gui:run
   # Application will use in-memory messaging
   ```

5. **Restart Kafka services**:
   ```bash
   docker-compose restart zookeeper
   sleep 10
   docker-compose restart kafka
   sleep 20
   docker-compose logs kafka --tail 50
   ```

**One-line fix**:
```bash
docker-compose restart zookeeper && sleep 10 && docker-compose restart kafka
```

### ML Service Unavailable

**Symptom**: `Connection refused` on port 8000, `ML service health check failed`

**Reproduction**:
```bash
curl http://localhost:8000/health
# curl: (7) Failed to connect to localhost port 8000: Connection refused
```

**Resolution**:
1. **Verify ML service is running**:
   ```bash
   docker ps | grep ml-service
   # Should show container running
   
   curl http://localhost:8000/health
   # Should return: {"status":"healthy"}
   
   docker-compose logs ml-service --tail 50
   ```

2. **Check if model files exist** (if not using demo mode):
   ```bash
   ls -la docker/ml-service/model/
   # If empty or missing, enable demo mode
   ```

3. **Enable demo mode**:
   ```bash
   # Edit .env
   echo "VERICROP_LOAD_DEMO=true" >> .env
   
   # Restart ML service
   docker-compose up -d ml-service --force-recreate
   ```

4. **Rebuild ML service**:
   ```bash
   docker-compose build --no-cache ml-service
   docker-compose up -d ml-service
   docker-compose logs -f ml-service
   # Wait for: "Application startup complete"
   ```

5. **Check port 8000 is not in use**:
   ```bash
   lsof -i :8000              # Unix/Mac
   netstat -ano | findstr :8000  # Windows
   ```

**One-line fix**:
```bash
docker-compose up -d ml-service --force-recreate && sleep 10 && curl http://localhost:8000/health
```

### Build Fails

**Symptom**: Gradle build errors, compilation failures

**Reproduction**:
```bash
./gradlew build
# FAILURE: Build failed with an exception
```

**Resolution**:
1. **Clean build artifacts**:
   ```bash
   ./gradlew clean build
   ```

2. **Clear Gradle cache**:
   ```bash
   rm -rf ~/.gradle/caches
   ./gradlew build --refresh-dependencies
   ```

3. **Check Java version compatibility**:
   ```bash
   java -version
   # Must be Java 11 or higher (17 recommended)
   ```

4. **Check for dependency issues**:
   ```bash
   ./gradlew dependencies --configuration runtimeClasspath
   # Look for version conflicts
   ```

5. **Build with detailed logging**:
   ```bash
   ./gradlew build --info --stacktrace
   # Review full error details
   ```

**One-line fix**:
```bash
./gradlew clean build --refresh-dependencies
```

### Tests Fail

**Symptom**: Unit or integration tests fail unexpectedly

**Reproduction**:
```bash
./gradlew test
# Tests FAILED
```

**Resolution**:
1. **Run tests with verbose output**:
   ```bash
   ./gradlew test --info --stacktrace
   # Identify which tests are failing
   ```

2. **Check test dependencies**:
   ```bash
   ./gradlew :vericrop-core:dependencies --configuration testRuntimeClasspath
   ```

3. **Ensure test database is accessible** (for integration tests):
   ```bash
   docker-compose ps postgres
   docker exec -it vericrop-postgres psql -U vericrop -d vericrop -c "SELECT 1;"
   ```

4. **Run specific failing test**:
   ```bash
   ./gradlew test --tests "*FailingTestClass" --info
   ```

5. **Review test reports**:
   ```bash
   open build/reports/tests/test/index.html
   # Check failure details and stack traces
   ```

**One-line fix**:
```bash
./gradlew clean test --refresh-dependencies
```

### Port Conflicts

**Symptom**: `Address already in use`, `Bind for 0.0.0.0:XXXX failed: port is already allocated`

**Reproduction**:
```bash
docker-compose up -d
# Error: Bind for 0.0.0.0:5432 failed: port is already allocated
```

**Resolution**:
1. **Identify process using the port**:
   ```bash
   # Unix/Mac
   lsof -i :5432   # PostgreSQL
   lsof -i :9092   # Kafka
   lsof -i :8000   # ML Service
   lsof -i :8080   # Airflow/GUI
   
   # Windows
   netstat -ano | findstr :5432
   ```

2. **Stop conflicting service**:
   ```bash
   # If it's another PostgreSQL instance
   sudo systemctl stop postgresql  # Linux
   brew services stop postgresql   # Mac
   
   # Or kill the process (try graceful shutdown first)
   kill <PID>                      # Unix/Mac - SIGTERM (graceful)
   # If process doesn't stop after 10 seconds:
   kill -9 <PID>                   # Unix/Mac - SIGKILL (force)
   
   taskkill /PID <PID>             # Windows - graceful
   taskkill /PID <PID> /F          # Windows - force
   ```

3. **Change port in `.env`** (alternative solution):
   ```bash
   # Edit .env
   POSTGRES_PORT=5433  # Change from 5432
   # Update docker-compose.yml ports mapping accordingly
   ```

4. **Use different port mapping**:
   ```bash
   # Temporarily use different port
   docker run -p 5433:5432 postgres  # Maps internal 5432 to host 5433
   ```

**One-line fix**:
```bash
# Try graceful shutdown first
lsof -ti :5432 | xargs kill  # Unix/Mac - graceful shutdown
# If that doesn't work after 10 seconds:
lsof -ti :5432 | xargs kill -9  # Unix/Mac - force kill
```

### Docker Compose Issues

**Symptom**: Services fail to start, stuck in "starting" state, or are unhealthy

**Reproduction**:
```bash
docker-compose up -d
docker-compose ps
# Some services show "Exited (1)" or "Unhealthy"
```

**Resolution**:
1. **Check service logs**:
   ```bash
   docker-compose logs postgres
   docker-compose logs kafka
   docker-compose logs ml-service
   # Look for ERROR or FATAL messages
   ```

2. **Check Docker daemon is running**:
   ```bash
   docker ps
   # If error: Cannot connect to Docker daemon
   sudo systemctl start docker  # Linux
   # Or start Docker Desktop (Mac/Windows)
   ```

3. **Restart specific unhealthy service**:
   ```bash
   docker-compose restart postgres
   docker-compose restart kafka
   ```

4. **Complete restart with fresh volumes**:
   ```bash
   docker-compose down -v
   docker-compose up --build -d
   docker-compose ps
   ```

5. **Check disk space**:
   ```bash
   df -h  # Unix/Mac
   # Ensure sufficient space for Docker volumes
   
   docker system df  # Check Docker disk usage
   docker system prune -a  # Clean up (careful!)
   ```

6. **Check Docker resource limits**:
   ```bash
   # In Docker Desktop: Settings → Resources
   # Ensure: CPU: 4+, Memory: 4GB+, Swap: 1GB+
   ```

**One-line fix**:
```bash
docker-compose down -v && docker-compose up --build -d
```

### Python Module Not Found

**Symptom**: `ModuleNotFoundError` when running ML service locally

**Reproduction**:
```bash
cd docker/ml-service
python app.py
# ModuleNotFoundError: No module named 'fastapi'
```

**Resolution**:
1. **Ensure virtual environment is activated**:
   ```bash
   source venv/bin/activate  # Unix/Mac
   venv\Scripts\activate     # Windows
   
   # Verify activation (should show venv path)
   which python              # Unix/Mac
   where python              # Windows
   ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   pip list  # Verify packages installed
   ```

3. **Use correct Python version**:
   ```bash
   python3 --version
   # Must be Python 3.11
   
   # Create venv with specific version
   python3.11 -m venv venv
   ```

**One-line fix**:
```bash
python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt
```

### Common Error Messages

#### `JAVA_HOME not set`

**Error**: `ERROR: JAVA_HOME is not set and no 'java' command could be found`

**Fix**:
```bash
# Unix/Mac
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk  # or your JDK path
export PATH=$JAVA_HOME/bin:$PATH

# Windows (CMD)
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

# Windows (PowerShell)
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

# Make permanent (Unix/Mac)
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
source ~/.bashrc
```

#### `Module not found` (JavaFX)

**Error**: `Error: JavaFX runtime components are missing`

**Fix**:
```bash
# JavaFX is bundled with the project, but if issues persist:
./gradlew :vericrop-gui:run --add-modules=javafx.controls,javafx.fxml
```

#### `Cannot connect to Docker daemon`

**Error**: `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`

**Fix**:
```bash
# Linux
sudo systemctl start docker
sudo systemctl enable docker  # Auto-start on boot

# Add user to docker group (avoid sudo)
sudo usermod -aG docker $USER
newgrp docker

# Mac/Windows
# Start Docker Desktop application
```

#### `Permission denied` (Docker)

**Error**: `Permission denied while trying to connect to Docker daemon`

**Fix**:
```bash
# Linux - Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Or use sudo (not recommended for regular use)
sudo docker-compose up -d
```

#### `Port already allocated`

**Error**: `Bind for 0.0.0.0:5432 failed: port is already allocated`

**Fix**: See [Port Conflicts](#port-conflicts) section above.

#### `Out of memory` (Docker)

**Error**: `java.lang.OutOfMemoryError` or Docker container exits with code 137

**Fix**:
```bash
# Increase Docker memory limit
# Docker Desktop → Settings → Resources → Memory: 4GB+

# Or set Java heap size
export JAVA_OPTS="-Xmx2g -Xms512m"
./gradlew :vericrop-gui:run
```

#### `Flyway migration failed`

**Error**: `FlywayException: Unable to obtain connection from database`

**Fix**:
```bash
# Verify PostgreSQL is running
docker-compose ps postgres

# Check migration status
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT * FROM flyway_schema_history;"

# Reset database (⚠️ loses all data)
docker-compose down -v
docker-compose up -d postgres
# Migrations will run automatically on next GUI startup
```

### Docker Resources Issues

**Symptom**: Containers running slowly, timeouts, or OOM errors

**Resolution**:
1. **Check Docker resource allocation** (Docker Desktop):
   - Go to Settings → Resources
   - Recommended: CPUs: 4+, Memory: 4GB+, Swap: 1GB+, Disk: 60GB+

2. **Monitor resource usage**:
   ```bash
   docker stats
   # Shows real-time CPU, memory, network, disk I/O
   ```

3. **Clean up unused resources**:
   ```bash
   docker system prune -a --volumes
   # Removes all unused containers, images, volumes
   ```

### Getting Help

If you encounter issues not covered here:

1. **Check existing issues**:
   - [GitHub Issues](https://github.com/imperfectperson-max/vericrop-miniproject/issues)

2. **Review detailed documentation**:
   - [vericrop-gui/README.md](vericrop-gui/README.md) - GUI module details
   - [KAFKA_INTEGRATION.md](docs/implementation/KAFKA_INTEGRATION.md) - Kafka setup and usage
   - [DEPLOYMENT.md](docs/deployment/DEPLOYMENT.md) - Production deployment
   - [docs/GUI-setup.md](docs/GUI-setup.md) - GUI setup guide

3. **Enable debug logging**:
   ```bash
   export LOG_LEVEL=DEBUG
   ./gradlew :vericrop-gui:run --info --stacktrace
   ```

4. **Open a new issue** with:
   - Clear description of the problem
   - Steps to reproduce
   - Complete error messages and logs
   - Environment details:
     ```bash
     echo "OS: $(uname -a)"
     echo "Java: $(java -version 2>&1 | head -n 1)"
     echo "Docker: $(docker --version)"
     echo "Docker Compose: $(docker-compose --version)"
     ```
   - Relevant configuration (`.env` without secrets)

</details>

## License

**TODO**: License to be determined. Please contact the maintainer for licensing information.

## Maintainers

- **GitHub**: [@imperfectperson-max](https://github.com/imperfectperson-max)
- **Repository**: [vericrop-miniproject](https://github.com/imperfectperson-max/vericrop-miniproject)

## Acknowledgements

- **Fruits-360 Dataset**: ML model training data
- **Apache Kafka**: Event streaming platform
- **FastAPI**: Modern Python web framework
- **Spring Boot**: Java application framework
- **JavaFX**: Desktop GUI framework
- **ONNX Runtime**: Cross-platform ML inference

---

**Made with ❤️ for sustainable agriculture and transparent supply chains**

[Report Bug](https://github.com/imperfectperson-max/vericrop-miniproject/issues) · [Request Feature](https://github.com/imperfectperson-max/vericrop-miniproject/issues) · [Documentation](https://github.com/imperfectperson-max/vericrop-miniproject)
