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
- [Local Development](#local-development)
- [Configuration](#configuration)
- [ML Service Contract](#ml-service-contract)
- [Testing](#testing)
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

JavaFX desktop application with Spring Boot integration. Provides four interactive dashboards:
- **Farm Management**: Batch creation, quality assessment
- **Logistics Tracking**: Shipment monitoring, condition alerts
- **Consumer Verification**: QR code scanning, product journey
- **Analytics Dashboard**: KPI monitoring, trend analysis

**Tech Stack**: Java 17, JavaFX, Spring Boot, HikariCP, Kafka Client

**Details**: See [vericrop-gui/README.md](vericrop-gui/README.md)

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

**Tech Stack**: Apache Airflow 2.7.1, Kafka Python Client

### docker

**Location**: `docker/`

Docker configurations and compose files:
- `docker-compose.yml`: Complete stack (PostgreSQL, Kafka, ML Service, Airflow)
- `docker-compose-kafka.yml`: Kafka-only setup
- `ml-service/Dockerfile`: ML service container

## Quickstart

Get VeriCrop running in under 5 minutes using Docker Compose.

### Prerequisites

Before you begin, ensure you have:

- **Java 11+** (Java 17 recommended)
  ```bash
  java -version  # Should show 11 or higher
  ```

- **Gradle** (included via wrapper)
  ```bash
  ./gradlew --version
  ```

- **Docker & Docker Compose**
  ```bash
  docker --version        # 20.10+
  docker-compose --version  # 2.0+
  ```

- **Git**
  ```bash
  git --version
  ```

### Running the Complete Stack

```bash
# 1. Clone the repository
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject

# 2. Build Java artifacts (required before docker-compose)
./gradlew build

# 3. Start all services with Docker Compose
docker-compose up --build -d

# 4. Wait for services to be healthy (2-3 minutes)
docker-compose ps

# 5. Verify services are running
./scripts/smoke_test.sh  # If available
```

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

## Local Development

For active development without Docker containers.

### 1. Start External Services

Start only the infrastructure services (PostgreSQL, Kafka, Zookeeper):

```bash
# Start just PostgreSQL and Kafka
docker-compose up -d postgres kafka zookeeper

# Verify services
docker-compose ps
```

### 2. Configure Environment

Copy the example environment file and customize:

```bash
# Copy example configuration
cp .env.example .env

# Edit .env with your settings (optional, defaults work for local dev)
nano .env
```

Key configuration options:
- `POSTGRES_HOST=localhost` - Database host
- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` - Kafka broker
- `ML_SERVICE_URL=http://localhost:8000` - ML service URL
- `VERICROP_LOAD_DEMO=true` - Use demo mode if ML model unavailable

### 3. Run the ML Service

**Option A: Using Docker (Recommended)**

```bash
cd docker/ml-service
docker build -t vericrop-ml .
docker run -d -p 8000:8000 --name vericrop-ml vericrop-ml

# Verify
curl http://localhost:8000/health
```

**Option B: Local Python Environment**

```bash
cd docker/ml-service

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run service
uvicorn app:app --host 0.0.0.0 --port 8000
```

### 4. Run the JavaFX GUI Application

From the project root:

```bash
# Run the GUI application
./gradlew :vericrop-gui:run

# Or on Windows
gradlew.bat :vericrop-gui:run
```

The application will:
1. Connect to PostgreSQL for batch metadata
2. Connect to Kafka for event streaming
3. Connect to ML Service for quality predictions
4. Launch the JavaFX GUI with four dashboards

### 5. Run Airflow (Optional)

For workflow orchestration:

```bash
# Airflow is included in docker-compose
docker-compose up -d airflow-webserver airflow-scheduler

# Access UI at http://localhost:8082
# Login: admin / admin

# Enable the VeriCrop DAG
# Navigate to DAGs → vericrop_evaluation_pipeline → Toggle On
```

### Development Workflow

```bash
# 1. Make code changes in your IDE

# 2. Build and test
./gradlew build
./gradlew test

# 3. Run the application
./gradlew :vericrop-gui:run

# 4. Test specific modules
./gradlew :vericrop-core:test
./gradlew :kafka-service:test

# 5. View logs
tail -f logs/vericrop-gui.log
```

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
- **Model Logic**: `docker/ml-service/app.py` (lines 100-200, prediction logic)

## Testing

VeriCrop includes comprehensive test suites for Java and Python components.

### Java Tests

Run all tests:

```bash
# Run all Java tests
./gradlew test

# Run tests for specific module
./gradlew :vericrop-core:test
./gradlew :vericrop-gui:test
./gradlew :kafka-service:test

# Run specific test class
./gradlew test --tests "*.BlockchainTest"
./gradlew test --tests "*.MLServiceClientTest"

# Run with detailed output
./gradlew test --info
```

View test reports:
```bash
# HTML reports generated at:
open build/reports/tests/test/index.html
```

### Python Tests

Run ML service tests:

```bash
cd docker/ml-service

# Install test dependencies
pip install -r requirements-test.txt

# Run all tests
pytest

# Run with coverage
pytest --cov=. --cov-report=html

# Run specific test file
pytest tests/test_predict.py

# Run with verbose output
pytest -v
```

### Contract Tests

Test the integration between services:

```bash
# Start services
docker-compose up -d

# Run contract tests (if available)
./gradlew :vericrop-gui:integrationTest
```

### Manual Testing

```bash
# Test ML Service
curl http://localhost:8000/health
curl -X POST -F "file=@examples/sample.jpg" http://localhost:8000/predict

# Test PostgreSQL
docker exec -it vericrop-postgres psql -U vericrop -d vericrop -c "SELECT COUNT(*) FROM batches;"

# Test Kafka
docker exec -it vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Unit Test Coverage

Current test coverage:
- ✅ Blockchain operations
- ✅ Quality evaluation service
- ✅ File ledger service
- ✅ ML service health endpoints
- ✅ Batch creation and retrieval
- ⚠️ Integration tests (partial)
- ⚠️ GUI controllers (manual testing)

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

Common issues and solutions.

### Application won't start

**Symptom**: JavaFX GUI fails to launch

**Solutions**:
1. Check Java version: `java -version` (requires 11+, 17 recommended)
2. Verify services are running: `docker-compose ps`
3. Check logs: `./gradlew :vericrop-gui:run --info`
4. Ensure `JAVA_HOME` is set correctly

### Database connection failed

**Symptom**: `Connection refused` or `Authentication failed`

**Solutions**:
1. Verify PostgreSQL is running:
   ```bash
   docker-compose ps postgres
   docker-compose logs postgres
   ```
2. Check connection settings in `.env`
3. Test connection:
   ```bash
   docker exec -it vericrop-postgres psql -U vericrop -d vericrop
   ```
4. Reset PostgreSQL:
   ```bash
   docker-compose down -v
   docker-compose up -d postgres
   ```

### Kafka connection failed

**Symptom**: `TimeoutException` or `Node not available`

**Solutions**:
1. Verify Kafka is running:
   ```bash
   docker-compose ps kafka zookeeper
   ```
2. Check bootstrap servers in `.env`
3. Disable Kafka for testing:
   ```bash
   export KAFKA_ENABLED=false
   ./gradlew :vericrop-gui:run
   ```
4. Restart Kafka:
   ```bash
   docker-compose restart kafka zookeeper
   ```

### ML Service unavailable

**Symptom**: `Connection refused` on port 8000

**Solutions**:
1. Verify ML service is running:
   ```bash
   curl http://localhost:8000/health
   docker-compose logs ml-service
   ```
2. Enable demo mode:
   ```bash
   export VERICROP_LOAD_DEMO=true
   docker-compose up -d ml-service
   ```
3. Rebuild ML service:
   ```bash
   docker-compose build ml-service
   docker-compose up -d ml-service
   ```

### Build fails

**Symptom**: Gradle build errors

**Solutions**:
1. Clean build:
   ```bash
   ./gradlew clean build
   ```
2. Clear Gradle cache:
   ```bash
   rm -rf ~/.gradle/caches
   ./gradlew build --refresh-dependencies
   ```
3. Check Java version compatibility

### Tests fail

**Symptom**: Unit or integration tests fail

**Solutions**:
1. Run tests with verbose output:
   ```bash
   ./gradlew test --info
   ```
2. Check test dependencies are installed
3. Ensure test database is accessible (for integration tests)
4. Review test logs in `build/reports/tests/`

### Port conflicts

**Symptom**: `Address already in use`

**Solutions**:
1. Check which process is using the port:
   ```bash
   lsof -i :8080  # or :8000, :5432, :9092
   ```
2. Stop conflicting service or change port in `.env`
3. Kill conflicting process:
   ```bash
   kill -9 <PID>
   ```

### Docker Compose issues

**Symptom**: Services fail to start or are unhealthy

**Solutions**:
1. Check service logs:
   ```bash
   docker-compose logs <service-name>
   ```
2. Restart services:
   ```bash
   docker-compose restart <service-name>
   ```
3. Clean slate:
   ```bash
   docker-compose down -v
   docker-compose up --build -d
   ```
4. Check disk space: `df -h`

### Common Errors

#### `JAVA_HOME not set`
```bash
# Linux/Mac
export JAVA_HOME=/path/to/java
# Windows
set JAVA_HOME=C:\path\to\java
```

#### `Module not found` (JavaFX)
```bash
# Add JavaFX modules explicitly
./gradlew :vericrop-gui:run --add-modules=javafx.controls,javafx.fxml
```

#### `Cannot connect to Docker daemon`
```bash
# Start Docker
sudo systemctl start docker  # Linux
# Or start Docker Desktop (Mac/Windows)
```

### Getting Help

If you encounter issues not covered here:

1. Check existing [GitHub Issues](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
2. Review detailed documentation:
   - [vericrop-gui/README.md](vericrop-gui/README.md) - GUI module details
   - [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - Kafka setup and usage
   - [DEPLOYMENT.md](DEPLOYMENT.md) - Production deployment
3. Open a new issue with:
   - Clear description of the problem
   - Steps to reproduce
   - Error messages and logs
   - Environment details (OS, Java version, Docker version)

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
