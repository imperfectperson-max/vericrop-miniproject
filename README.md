# VeriCrop Mini-Project

> AI-Powered Agricultural Supply Chain Management with Quality Control and Blockchain Transparency

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Python](https://img.shields.io/badge/Python-3.11-green.svg)
![Kafka](https://img.shields.io/badge/Kafka-3.4.0-black.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)

## Table of Contents

- [Project Summary](#project-summary)
- [Architecture](#architecture)
- [Components](#components)
- [Quickstart](#quickstart)
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

## Project Summary

VeriCrop is a comprehensive mini-project that demonstrates modern supply chain management for agricultural products. The platform combines:

- **AI-Powered Quality Assessment**: Machine learning service for fruit quality classification
- **Blockchain Transparency**: Immutable ledger for supply chain tracking
- **Real-time Messaging**: Kafka-based event streaming for supply chain events
- **Interactive GUI**: JavaFX desktop application for farm management, logistics, consumer verification, and analytics
- **Workflow Orchestration**: Apache Airflow for automated quality evaluation pipelines

### Goals

- Ensure food quality and safety through AI-powered classification
- Provide end-to-end traceability from farm to consumer
- Enable real-time monitoring of supply chain conditions
- Demonstrate integration of modern technologies (Java, Python, Kafka, PostgreSQL, Docker)
- Build trust in agricultural supply chains through transparency

### Demo Mode

VeriCrop GUI can run in a **self-contained demo mode** without requiring external services (PostgreSQL, Kafka, ML Service). This is perfect for:
- Quick demonstrations
- Development without infrastructure setup
- Testing UI flows in isolation
- Offline scenarios

**To enable demo mode:**
```bash
# Using environment variable
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run

# Using system property
./gradlew :vericrop-gui:run --args="-Dvericrop.loadDemo=true"

# Windows PowerShell
$env:VERICROP_LOAD_DEMO="true"
./gradlew :vericrop-gui:run
```

**Demo mode features:**
- In-memory blockchain (no external network)
- Mock ML predictions
- Demo data in all screens (Analytics, Logistics, Consumer)
- Fully functional delivery simulator
- QR code generation
- All UI flows operational
- Full screen display on PC (maximized window)

See [vericrop-gui/README.md](vericrop-gui/README.md) for detailed demo mode documentation.

## Architecture

VeriCrop follows a microservices architecture with event-driven communication:

```
┌─────────────────────────────────────────────────────────────────┐
│                    JavaFX GUI Application                       │
│           (Farm, Logistics, Consumer, Analytics)                │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   │ REST API / Kafka
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│                   VeriCrop Core Services                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐   │
│  │   Batch      │ │  Blockchain  │ │   Quality           │   │
│  │  Management  │ │   Ledger     │ │  Evaluation         │   │
│  └──────────────┘ └──────────────┘ └──────────────────────┘   │
└────────┬──────────────────┬──────────────────┬─────────────────┘
         │                  │                  │
         ▼                  ▼                  ▼
┌─────────────────┐ ┌──────────────┐ ┌─────────────────────┐
│   PostgreSQL    │ │    Kafka     │ │   ML Service        │
│   (Metadata)    │ │  (Events)    │ │   (FastAPI/ONNX)    │
└─────────────────┘ └──────────────┘ └─────────────────────┘
         │                  │                  │
         │                  ▼                  │
         │         ┌──────────────┐            │
         │         │   Airflow    │            │
         └─────────│ (Workflows)  │────────────┘
                   └──────────────┘
```

### Key Data Flows

1. **Batch Creation**: GUI → ML Service (quality prediction) → PostgreSQL + Kafka → Blockchain Ledger
2. **Quality Evaluation**: Airflow DAG → Kafka (evaluation requests) → GUI Service → ML Service → Kafka (results)
3. **Supply Chain Tracking**: GUI → Blockchain Ledger → Kafka (shipment events) → Analytics Dashboard

## Components

### vericrop-gui

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
- `batch-created-events`: Batch creation notifications (NEW)
- `order-events`: Order placement and processing events (NEW)

**New Features**:
- 🎯 **Order Processing**: OrderEventConsumer handles orders with quality disclosure
- 💰 **Dynamic Pricing**: Price adjustments based on quality metrics (prime/rejection rates)
- 📦 **Batch Tracking**: BatchCreatedEvent for real-time batch notifications

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
- `ml-service/Dockerfile`: ML service container

## Quickstart

Get VeriCrop running in under 10 minutes using Docker Compose.

### Prerequisites

Before you begin, ensure you have the following tools installed with the specified versions:

#### Required Tools

1. **Git** - Version control
   ```bash
   # Check version
   git --version
   # Expected: git version 2.30 or higher
   ```

2. **Java 11+** (Java 17 recommended)
   ```bash
   # Check version
   java -version
   # Expected: openjdk version "17.0.x" or "11.0.x"
   
   # Verify JAVA_HOME is set
   echo $JAVA_HOME        # Unix/Mac
   echo %JAVA_HOME%       # Windows
   ```

3. **Gradle** (wrapper included, no separate install needed)
   ```bash
   # Unix/Mac
   ./gradlew --version
   
   # Windows
   gradlew.bat --version
   
   # Expected: Gradle 8.x or higher
   ```

4. **Docker & Docker Compose**
   ```bash
   # Check Docker version
   docker --version
   # Expected: Docker version 20.10+ or higher
   
   # Check Docker Compose version
   docker-compose --version
   # Expected: Docker Compose version 2.0+ or higher
   
   # Verify Docker is running
   docker ps
   ```

5. **Python 3.11** (for local ML service development)
   ```bash
   # Check version
   python3 --version
   # Expected: Python 3.11.x
   
   # Windows
   python --version
   ```

6. **Git LFS** (optional, only if large model files are tracked)
   ```bash
   # Check if installed
   git lfs version
   # Expected: git-lfs/3.x or higher
   
   # If not installed, see: https://git-lfs.github.com/
   ```

#### Verify All Prerequisites

Run all checks individually (recommended for troubleshooting):

```bash
# Check each tool individually
git --version
java -version
./gradlew --version
docker --version
docker-compose --version
python3 --version
```

Or run all checks at once (requires all tools in PATH):

```bash
# Unix/Mac - Combined check (may fail silently if tools missing)
echo "Git: $(git --version 2>/dev/null || echo 'NOT FOUND')" && \
echo "Java: $(java -version 2>&1 | head -n 1)" && \
echo "Gradle: $(./gradlew --version 2>/dev/null | grep Gradle || echo 'NOT FOUND')" && \
echo "Docker: $(docker --version 2>/dev/null || echo 'NOT FOUND')" && \
echo "Docker Compose: $(docker-compose --version 2>/dev/null || echo 'NOT FOUND')" && \
echo "Python: $(python3 --version 2>/dev/null || echo 'NOT FOUND')"

# Windows (PowerShell)
Write-Host "Git: $(git --version)"; `
Write-Host "Java: $(java -version 2>&1 | Select-String 'version')"; `
Write-Host "Docker: $(docker --version)"; `
Write-Host "Docker Compose: $(docker-compose --version)"; `
Write-Host "Python: $(python --version)"
```

### Running the Complete Stack

Follow these step-by-step instructions to get VeriCrop running:

#### Step 1: Clone the Repository

```bash
# Clone the repository
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject

# Expected output: Repository cloned successfully
```

#### Step 2: Configure Environment Variables

```bash
# Copy the example environment file
cp .env.example .env

# Unix/Mac - Edit with your preferred editor
nano .env
# or
vim .env

# Windows
notepad .env
```

**Important**: Review and update these required variables in `.env`:

- **PostgreSQL Settings**: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_HOST`, `POSTGRES_PORT`
- **Kafka Settings**: `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_ENABLED`
- **ML Service**: `ML_SERVICE_URL`, `VERICROP_LOAD_DEMO` (set to `true` if model files missing)
- **Airflow**: `AIRFLOW_ADMIN_USERNAME`, `AIRFLOW_ADMIN_PASSWORD`

⚠️ **Security Warnings**:
- Never commit `.env` files to version control! The `.gitignore` already excludes it.
- For production environments, use strong passwords (16+ characters, mixed case, numbers, special chars).
- Default credentials in `.env.example` are for development only - change them for production!

#### Step 3: Build Java Artifacts

```bash
# Build all Java modules using Gradle wrapper
./gradlew build

# Expected output: BUILD SUCCESSFUL in XXs
# This compiles vericrop-core, vericrop-gui, kafka-service modules

# Windows
gradlew.bat build
```

If build fails, see [Troubleshooting](#troubleshooting) section.

#### Step 4: Start All Services with Docker Compose

```bash
# Start all services (PostgreSQL, Kafka, ML Service, Airflow)
docker-compose up --build -d

# Expected output:
# Creating vericrop-postgres ... done
# Creating vericrop-zookeeper ... done
# Creating vericrop-kafka ... done
# Creating vericrop-ml-service ... done
# ...
```

#### Step 5: Wait for Services to be Healthy

```bash
# Check service status (wait 2-3 minutes for all to be healthy)
docker-compose ps

# Expected output: All services should show "Up" or "Up (healthy)"
# Example:
# NAME                     STATUS
# vericrop-postgres        Up (healthy)
# vericrop-kafka           Up (healthy)
# vericrop-ml-service      Up (healthy)
# ...
```

#### Step 6: Verify Services Are Running

See the [Verify Services](#verify-services) section below for detailed health checks.

### Accessing the Services

| Service | URL | Credentials |
|---------|-----|-------------|
| **Kafka UI** | http://localhost:8081 | - |
| **Airflow UI** | http://localhost:8082 | admin / admin |
| **ML Service** | http://localhost:8000/health | - |
| **PostgreSQL** | localhost:5432 | vericrop / vericrop123 |

### Quick Test

```bash
# Test ML service health
curl http://localhost:8000/health
# Expected: {"status":"healthy"}

# Run the JavaFX GUI (from project root)
./gradlew :vericrop-gui:run
```

### Stopping the Stack

```bash
# Stop all services
docker-compose down

# Stop and remove all data
docker-compose down -v
```

For detailed cleanup instructions, see the [Stopping and Cleaning](#stopping-and-cleaning) section.

## Database Setup & User Provisioning

VeriCrop uses PostgreSQL for metadata storage with Flyway for automatic migrations.

### Database Migrations

Database schema is managed through Flyway migrations located in `vericrop-gui/src/main/resources/db/migration/`:

- **V1__create_batches_table.sql**: Batches and quality tracking tables
- **V2__create_users_table.sql**: User authentication with BCrypt hashing
- **V3__create_shipments_table.sql**: Shipment tracking with blockchain integration

Migrations run automatically on application startup when `spring.flyway.enabled=true` (default).

### Pre-configured Users

The V2 migration creates demo users for testing:

| Username | Password | Role | Description |
|----------|----------|------|-------------|
| admin | admin123 | ADMIN | Full system access |
| farmer | farmer123 | FARMER | Farm/producer operations |
| supplier | supplier123 | SUPPLIER | Logistics operations |

**⚠️ Security Note**: Change these passwords in production! Passwords are BCrypt hashed with cost factor 10.

### Adding New Users

To add users manually:

```sql
-- Connect to PostgreSQL
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

-- Generate BCrypt hash (use online tool or bcrypt CLI)
-- For password "mypassword": $2a$10$...

-- Insert new user
INSERT INTO users (username, password_hash, email, full_name, role, status)
VALUES (
    'newuser',
    '$2a$10$YOUR_BCRYPT_HASH_HERE',
    'user@example.com',
    'User Full Name',
    'USER',
    'active'
);
```

### Authentication Features

- **BCrypt Password Hashing**: Secure password storage (never plaintext)
- **Failed Login Tracking**: Account locks after 5 failed attempts
- **Lockout Duration**: 30 minutes automatic unlock
- **Role-Based Access**: ADMIN, FARMER, SUPPLIER, CONSUMER, USER
- **Last Login Tracking**: Monitors user activity
- **Database Fallback**: Simple auth mode when database unavailable

### Verifying Database Setup

```bash
# Check migrations applied
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"

# Verify users exist
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, role, status, created_at FROM users;"

# Check batches table
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT COUNT(*) as batch_count FROM batches;"
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

## Configuration

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
- **`vericrop-gui/src/main/resources/application.yml`**: Spring Boot application configuration (overridden by environment variables)
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

## Authentication and Messaging

VeriCrop now includes a complete user authentication and messaging system with PostgreSQL backend.

### Features

- **User Registration**: Create accounts with username, email, password, and role selection
- **Secure Authentication**: BCrypt password hashing, account lockout protection
- **Role-Based Access**: Four role types (FARMER, CONSUMER, ADMIN, SUPPLIER)
- **User Messaging**: Send and receive messages between users
- **Inbox/Sent Items**: Manage messages with read/unread status
- **Session Management**: Secure session handling with role-based navigation

### Quick Start

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
- **Failed Login Tracking**: Account locks after 5 failed attempts
- **Lockout Duration**: 30 minutes automatic unlock
- **Role-Based Access**: Different dashboards for each role
- **Session Persistence**: Sessions maintained until application close

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
- **Java Client**: `vericrop-gui/src/main/java/org/vericrop/gui/clients/MLClientService.java`
- **Model Logic**: `docker/ml-service/app.py` (prediction functions and ONNX model integration)

## Running Tests

VeriCrop includes comprehensive test suites for Java and Python components. Always run tests before committing changes.

### Java Tests

#### Run All Tests

```bash
# Run all Java tests across all modules
./gradlew test

# Expected output: BUILD SUCCESSFUL
# Results: XXX tests, XXX passed, 0 failed, 0 skipped

# Windows
gradlew.bat test
```

#### Run Tests for Specific Modules

```bash
# Test core business logic
./gradlew :vericrop-core:test

# Test GUI application
./gradlew :vericrop-gui:test

# Test Kafka service
./gradlew :kafka-service:test

# Test ML client
./gradlew :ml-client:test
```

#### Run Specific Test Classes

```bash
# Run blockchain tests
./gradlew test --tests "*BlockchainTest"

# Run ML service client tests
./gradlew test --tests "*MLServiceClientTest"

# Run quality evaluation tests
./gradlew test --tests "*QualityEvaluationServiceTest"

# Run authentication tests
./gradlew test --tests "*AuthenticationServiceTest"
```

#### Run with Coverage

```bash
# Run tests with JaCoCo coverage report
./gradlew test jacocoTestReport

# Expected output: BUILD SUCCESSFUL
# Coverage report: build/reports/jacoco/test/html/index.html

# View coverage report (Unix/Mac)
open build/reports/jacoco/test/html/index.html

# Windows
start build\reports\jacoco\test\html\index.html
```

#### Run with Detailed Output

```bash
# Run with verbose logging
./gradlew test --info

# Run with debug output
./gradlew test --debug

# Run and show standard output
./gradlew test --console=verbose
```

#### View Test Reports

```bash
# HTML test reports are generated automatically
# Location: <module>/build/reports/tests/test/index.html

# View vericrop-core test report
open vericrop-core/build/reports/tests/test/index.html

# View vericrop-gui test report
open vericrop-gui/build/reports/tests/test/index.html

# View all test reports
find . -name "index.html" -path "*/reports/tests/*"
```

### Python Tests

#### Setup Test Environment

```bash
# Navigate to ML service directory
cd docker/ml-service

# Activate virtual environment (if using local setup)
source venv/bin/activate  # Unix/Mac
venv\Scripts\activate     # Windows

# Install test dependencies
pip install -r requirements-test.txt

# Expected packages: pytest, pytest-cov, httpx
```

#### Run All Python Tests

```bash
# Run all tests
pytest

# Expected output:
# ======================== test session starts =========================
# collected XX items
# tests/test_app.py ........                                    [100%]
# ========================= XX passed in X.XXs =========================

# Run with verbose output
pytest -v

# Run with extra verbosity
pytest -vv
```

#### Run with Coverage

```bash
# Run tests with coverage report
pytest --cov=. --cov-report=html

# Expected output: Coverage report generated at htmlcov/index.html

# View coverage report
open htmlcov/index.html        # Unix/Mac
start htmlcov\index.html       # Windows

# Generate terminal coverage report
pytest --cov=. --cov-report=term

# Expected output shows coverage percentage per file
```

#### Run Specific Test Files

```bash
# Run specific test file
pytest tests/test_predict.py

# Run specific test function
pytest tests/test_predict.py::test_predict_endpoint

# Run tests matching pattern
pytest -k "health"

# Run tests by marker (if configured)
pytest -m "unit"
```

#### Run with Different Output Formats

```bash
# Run with detailed output
pytest -v

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
   - [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - Kafka setup and usage
   - [DEPLOYMENT.md](DEPLOYMENT.md) - Production deployment
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
