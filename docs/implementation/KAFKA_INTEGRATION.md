# VeriCrop Kafka Integration Guide

## Overview

This guide explains how to use the VeriCrop platform's Kafka messaging integration for real-time quality evaluation and supply chain tracking.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Airflow DAG (vericrop_dag.py)                 │
│              Produces EvaluationRequest Messages                │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Kafka Topics                              │
│   • evaluation-requests      • evaluation-results               │
│   • shipment-records         • quality-alerts                   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
         ┌────────────────┴─────────────────┐
         ▼                                  ▼
┌──────────────────┐            ┌──────────────────────┐
│  Kafka Consumer  │            │  REST API            │
│  (Java Service)  │            │  (Spring Boot)       │
└────────┬─────────┘            └──────────┬───────────┘
         │                                  │
         ▼                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│              VeriCrop Core Services                             │
│   • QualityEvaluationService  • FileLedgerService              │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. DTOs (Data Transfer Objects)

Located in `src/vericrop-core/main/java/org/vericrop/dto/`:

- **EvaluationRequest**: Request for fruit quality evaluation
- **EvaluationResult**: Result of quality evaluation with score and metadata
- **ShipmentRecord**: Immutable ledger record for supply chain tracking

### 2. Services

#### QualityEvaluationService
- **Location**: `src/vericrop-core/main/java/org/vericrop/service/`
- **Purpose**: Deterministic quality evaluation using image hash
- **Features**:
  - Deterministic scoring (same input = same output)
  - Quality score: 0.0 to 1.0
  - Pass threshold: 0.7
  - Categories: Fresh, Good, Fair, Poor
  - Metadata: color_consistency, size_uniformity, defect_density

#### FileLedgerService
- **Location**: `src/vericrop-core/main/java/org/vericrop/service/impl/`
- **Purpose**: Append-only ledger for immutable shipment records
- **Features**:
  - SHA-256 hash integrity verification
  - JSON Lines (JSONL) storage format
  - Query by ledger ID or batch ID
  - Immutable record storage

### 3. Kafka Messaging

#### KafkaProducerService
- **Location**: `src/kafka-service/main/java/org/vericrop/kafka/messaging/`
- **Topics**:
  - `evaluation-requests`: Quality evaluation requests
  - `evaluation-results`: Quality evaluation results
  - `shipment-records`: Ledger records
- **Features**:
  - In-memory mode for testing (no Kafka required)
  - JSON serialization with Jackson
  - Configurable via `application.yml`

#### KafkaConsumerService
- **Location**: `src/kafka-service/main/java/org/vericrop/kafka/messaging/`
- **Features**:
  - Message handlers for different types
  - Graceful shutdown
  - Consumer group configuration

### 4. REST API

#### EvaluationController
- **Location**: `src/vericrop-gui/main/java/org/vericrop/gui/controller/`
- **Base URL**: `http://localhost:8080/api`

**Endpoints**:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/evaluate` | Evaluate fruit quality (multipart or JSON) |
| GET | `/api/shipments/{id}` | Get shipment record by ledger ID |
| GET | `/api/shipments?batch_id={id}` | Get all shipments for a batch |
| GET | `/api/health` | Health check |

### 5. Airflow DAG

**File**: `airflow/dags/vericrop_dag.py`

**Features**:
- Produces evaluation requests to Kafka
- Calls REST API for evaluation
- Verifies ledger records
- Generates pipeline summary
- Runs every hour (configurable)

## Setup and Configuration

### 1. Kafka (Optional)

If you want to use actual Kafka messaging:

```bash
# Using Docker Compose
docker-compose up -d kafka zookeeper

# Or install Kafka locally
# Download from https://kafka.apache.org/downloads
# Start Zookeeper:
bin/zookeeper-server-start.sh config/zookeeper.properties
# Start Kafka:
bin/kafka-server-start.sh config/server.properties
```

### 2. Configuration

Edit `src/vericrop-gui/main/resources/application.yml`:

```yaml
kafka:
  enabled: true  # Set to true when Kafka is available
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

### 3. Build and Run

```bash
# Build the project
./gradlew build

# Run the REST API (Spring Boot)
./gradlew :vericrop-gui:bootRun

# Or run the JAR directly
java -jar vericrop-gui/build/libs/vericrop-gui-1.0.0.jar
```

The API will start on `http://localhost:8080`

## Usage Examples

### 1. Evaluate Fruit Quality (REST API)

#### With JSON:

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH_001",
    "product_type": "apple",
    "farmer_id": "farmer_001",
    "image_path": "/data/samples/apple.jpg"
  }'
```

#### With File Upload:

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -F "file=@/path/to/apple.jpg" \
  -F "batch_id=BATCH_002" \
  -F "product_type=apple" \
  -F "farmer_id=farmer_002"
```

**Response:**

```json
{
  "success": true,
  "batch_id": "BATCH_001",
  "quality_score": 0.85,
  "pass_fail": "PASS",
  "prediction": "Fresh",
  "confidence": 0.92,
  "metadata": {
    "color_consistency": 0.95,
    "size_uniformity": 0.80,
    "defect_density": 0.15,
    "evaluation_method": "deterministic_stub"
  },
  "ledger_id": "550e8400-e29b-41d4-a716-446655440000",
  "ledger_hash": "a3c7f9...",
  "timestamp": 1700398765432
}
```

### 2. Get Shipment Record

```bash
# By ledger ID
curl http://localhost:8080/api/shipments/550e8400-e29b-41d4-a716-446655440000

# By batch ID
curl http://localhost:8080/api/shipments?batch_id=BATCH_001
```

**Response:**

```json
{
  "success": true,
  "shipment_id": "SHIP_1700398765432",
  "batch_id": "BATCH_001",
  "from_party": "farmer_001",
  "to_party": "warehouse",
  "status": "EVALUATED",
  "quality_score": 0.85,
  "ledger_id": "550e8400-e29b-41d4-a716-446655440000",
  "ledger_hash": "a3c7f9...",
  "timestamp": 1700398765432,
  "verified": true
}
```

### 3. Health Check

```bash
curl http://localhost:8080/api/health
```

**Response:**

```json
{
  "status": "healthy",
  "service": "vericrop-evaluation-api",
  "kafka_enabled": false,
  "timestamp": 1700398765432
}
```

### 4. Run Airflow DAG

```bash
# Prerequisites: Install Airflow and kafka-python
pip install apache-airflow kafka-python

# Initialize Airflow (first time only)
airflow db init

# Start Airflow webserver
airflow webserver --port 8081

# Start Airflow scheduler (in another terminal)
airflow scheduler

# Access Airflow UI
# Open browser: http://localhost:8081
# Enable DAG: vericrop_evaluation_pipeline
# Trigger manually or wait for scheduled run
```

**DAG Tasks**:
1. `produce_kafka_message`: Sends evaluation request to Kafka
2. `call_rest_api`: Calls REST API for evaluation
3. `verify_ledger`: Verifies record in ledger
4. `generate_summary`: Creates execution summary

## Testing

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test Suites

```bash
# Quality Evaluation Service tests
./gradlew :vericrop-core:test --tests QualityEvaluationServiceTest

# File Ledger Service tests
./gradlew :vericrop-core:test --tests FileLedgerServiceTest

# Kafka Producer Service tests
./gradlew :kafka-service:test --tests KafkaProducerServiceTest
```

### Test Coverage

- **Total Tests**: 31
  - QualityEvaluationService: 9 tests
  - FileLedgerService: 12 tests
  - KafkaProducerService: 10 tests

## Development Modes

### 1. In-Memory Mode (Default)

No Kafka required. Messages are logged but not sent to actual topics.

```yaml
kafka:
  enabled: false
```

**Use Case**: Local development, testing, CI/CD

### 2. Kafka Mode

Requires Kafka broker running.

```yaml
kafka:
  enabled: true
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

**Use Case**: Production, integration testing

## Troubleshooting

### Issue: REST API not starting

**Solution**: Check if port 8080 is available
```bash
lsof -i :8080
# Or use a different port in application.yml
server:
  port: 8081
```

### Issue: Kafka connection failed

**Solution**: 
1. Check if Kafka is running: `docker ps` or `ps aux | grep kafka`
2. Verify bootstrap-servers in application.yml
3. Use in-memory mode: Set `kafka.enabled: false`

### Issue: Airflow DAG not appearing

**Solution**:
1. Check DAG file location: `airflow/dags/vericrop_dag.py`
2. Verify DAG path in `airflow.cfg`: `dags_folder = /path/to/airflow/dags`
3. Check for syntax errors: `python airflow/dags/vericrop_dag.py`

### Issue: Tests failing

**Solution**:
1. Clean build: `./gradlew clean build`
2. Check test logs: `build/reports/tests/test/index.html`
3. Remove test ledger directories: `rm -rf test-ledger-*`

## Docker Compose Example

Create `docker-compose-kafka.yml`:

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"

  vericrop-api:
    build: .
    depends_on:
      - kafka
    environment:
      KAFKA_ENABLED: "true"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    ports:
      - "8080:8080"
```

Run with:
```bash
docker-compose -f docker-compose-kafka.yml up -d
```

## Next Steps

1. **Replace Stub Model**: Integrate real ML model in QualityEvaluationService
2. **Add Authentication**: Secure REST API with Spring Security
3. **Add Monitoring**: Integrate Prometheus and Grafana for metrics
4. **Scale Services**: Deploy with Kubernetes for horizontal scaling
5. **Add More Topics**: Create separate topics for alerts, analytics, etc.
6. **Implement Event Sourcing**: Store all events for audit trail

## References

- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
