# VeriCrop GUI - Enterprise Supply Chain Management Application

The VeriCrop GUI is a JavaFX desktop application with enterprise-grade architecture for managing agricultural supply chain quality, tracking, and analytics.

## 🏗️ Architecture

The application follows a clean, layered architecture:

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (JavaFX Controllers)                          │
│  - LoginController, ProducerController, etc.            │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│  Service Layer (Business Logic)                         │
│  - BatchService, AnalyticsService, AuthService          │
└──────┬──────────────────┬──────────────────────┬────────┘
       │                  │                      │
┌──────▼────┐   ┌─────────▼────────┐   ┌────────▼────────┐
│ ML Client │   │ Kafka Messaging  │   │ Postgres Repo   │
│ (FastAPI) │   │ (Event Stream)   │   │ (Persistence)   │
└───────────┘   └──────────────────┘   └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Java 17 or later
- Docker & Docker Compose (for external services)
- Gradle 8.0+ (included via wrapper)

### 1. Start External Services

```bash
# Start Postgres, Kafka, and ML Service
docker-compose up -d postgres kafka zookeeper ml-service

# Verify services are running
docker-compose ps
```

### 2. Configure Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your settings (or use defaults)
# Key settings:
# - POSTGRES_USER=vericrop
# - POSTGRES_PASSWORD=vericrop123
# - KAFKA_ENABLED=true
# - ML_SERVICE_URL=http://localhost:8000
```

### 3. Initialize Database

```bash
# Database schema is automatically loaded via docker-compose volume mount
# Schema file: vericrop-gui/src/main/resources/db/schema.sql
```

### 4. Run the Application

```bash
# From repository root
./gradlew :vericrop-gui:run
```

The application will:
1. Initialize ApplicationContext
2. Connect to Postgres, Kafka, and ML Service
3. Launch the JavaFX GUI

## ⚙️ Configuration

### Environment Variables

Configuration is managed through `application.properties` with environment variable override support.

#### Database (PostgreSQL)
```bash
POSTGRES_HOST=localhost          # Database host
POSTGRES_PORT=5432               # Database port
POSTGRES_DB=vericrop             # Database name
POSTGRES_USER=vericrop           # Database user
POSTGRES_PASSWORD=vericrop123    # Database password

DB_POOL_SIZE=10                  # HikariCP pool size
DB_CONNECTION_TIMEOUT=30000      # Connection timeout (ms)
```

#### Kafka Messaging
```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Kafka broker
KAFKA_ENABLED=true                       # Enable/disable Kafka
KAFKA_ACKS=all                          # Producer acks (all/1/0)
KAFKA_RETRIES=3                         # Retry attempts
KAFKA_IDEMPOTENCE=true                  # Idempotent producer

# Topic names
KAFKA_TOPIC_BATCH_EVENTS=batch-events
KAFKA_TOPIC_QUALITY_ALERTS=quality-alerts
KAFKA_TOPIC_LOGISTICS_EVENTS=logistics-events
```

#### ML Service (FastAPI)
```bash
ML_SERVICE_URL=http://localhost:8000    # ML service base URL
ML_SERVICE_TIMEOUT=30000                # HTTP timeout (ms)
ML_SERVICE_RETRIES=3                    # Retry attempts
VERICROP_LOAD_DEMO=true                 # Use demo mode if model unavailable
```

#### Application Settings
```bash
VERICROP_MODE=dev                # dev or prod
SERVER_PORT=8080                 # REST API port (if enabled)
LOG_LEVEL=INFO                   # Logging level
QUALITY_PASS_THRESHOLD=0.7       # Quality threshold (0-1)
```

## 📦 Components

### Services Layer

#### BatchService
Manages batch creation, updates, and queries. Coordinates ML predictions, Kafka events, and database persistence.

```java
// Example usage
BatchRecord batch = new BatchRecord.Builder()
    .name("Apple Batch 001")
    .farmer("John Farmer")
    .productType("Apple")
    .quantity(100)
    .build();

BatchRecord created = batchService.createBatch(batch, imageFile);
```

#### AnalyticsService
Provides analytics and dashboard data from ML service.

```java
DashboardData dashboard = analyticsService.getDashboardData();
List<BatchRecord> batches = analyticsService.getAllBatches();
```

#### KafkaMessagingService
Publishes events to Kafka with idempotent producer configuration.

```java
kafkaService.sendBatch(batchRecord);
kafkaService.sendQualityAlert(batchId, alertData);
```

#### AuthenticationService
Manages user authentication and session state.

```java
authService.login("john@farm.com", "farmer");
boolean authenticated = authService.isAuthenticated();
```

### Clients Layer

#### MLClientService
HTTP client for FastAPI ML service with retry logic.

**Endpoints:**
- `health()` - Health check
- `createBatch(BatchRecord)` - Create batch
- `listBatches()` - List all batches
- `predictImage(File)` - Predict quality from image
- `getDashboardFarm()` - Get farm dashboard data

### Persistence Layer

#### PostgresBatchRepository
JDBC repository with HikariCP connection pooling for batch metadata.

**Methods:**
- `create(BatchRecord)` - Insert new batch
- `findByBatchId(String)` - Find by batch ID
- `findAll()` - List all batches
- `update(BatchRecord)` - Update batch
- `delete(String)` - Delete batch

## 🗄️ Database Schema

```sql
CREATE TABLE batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    farmer VARCHAR(255) NOT NULL,
    product_type VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    quality_score DECIMAL(5, 4),
    quality_label VARCHAR(50),
    data_hash VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 🧪 Testing

### Unit Tests

```bash
# Run all tests
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew :vericrop-gui:test --tests BatchServiceTest
```

### Integration Tests

```bash
# Start test containers (Postgres, Kafka)
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
./gradlew :vericrop-gui:integrationTest
```

### Manual Testing

```bash
# Test ML Service connection
curl http://localhost:8000/health

# Test database connection
docker exec -it vericrop-postgres psql -U vericrop -d vericrop -c "SELECT COUNT(*) FROM batches;"

# Test Kafka producer
docker exec -it vericrop-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic batch-events --from-beginning
```

## 🐳 Docker Deployment

### Build Docker Image

```bash
# Build image
docker build -t vericrop-gui -f vericrop-gui/Dockerfile .
```

### Run with Docker Compose

```bash
# Full stack deployment
docker-compose up -d

# View logs
docker-compose logs -f vericrop-gui

# Stop services
docker-compose down
```

## 📊 Monitoring & Logging

### Application Logs

Logs are written to:
- Console (stdout)
- File: `logs/vericrop-gui.log` (configurable)

### Log Levels

```bash
# Set log level via environment variable
export LOG_LEVEL=DEBUG  # DEBUG, INFO, WARN, ERROR
```

### Health Checks

Monitor service health:

```bash
# ML Service
curl http://localhost:8000/health

# Postgres (via docker)
docker exec vericrop-postgres pg_isready -U vericrop

# Kafka
docker exec vericrop-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

## 🔒 Security Considerations

- Database passwords should be stored in environment variables or secrets manager
- Kafka SASL/SSL should be configured for production
- ML service should use HTTPS in production
- Implement proper authentication/authorization (currently simplified)

## 🐛 Troubleshooting

### Application won't start

1. Check Java version: `java -version` (requires 17+)
2. Verify services are running: `docker-compose ps`
3. Check logs: `./gradlew :vericrop-gui:run --info`

### Database connection failed

1. Verify Postgres is running: `docker-compose ps postgres`
2. Check connection settings in `.env`
3. Test connection: `docker exec -it vericrop-postgres psql -U vericrop`

### Kafka connection failed

1. Verify Kafka is running: `docker-compose ps kafka`
2. Set `KAFKA_ENABLED=false` to disable Kafka
3. Check bootstrap servers configuration

### ML Service unavailable

1. Verify ML service is running: `curl http://localhost:8000/health`
2. Set `VERICROP_LOAD_DEMO=true` for demo mode
3. Check ML service logs: `docker-compose logs ml-service`

## 📚 Additional Documentation

- [Main README](../README.md) - Project overview
- [Deployment Guide](../DEPLOYMENT.md) - Production deployment
- [ML Service API](../docker/ml-service/README.md) - ML service documentation
- [Database Schema](src/main/resources/db/schema.sql) - Database structure

## 🤝 Contributing

When making changes:
1. Follow the existing architecture patterns
2. Add unit tests for new services
3. Update this README if adding new features
4. Test locally before committing

## 📝 License

See [LICENSE](../LICENSE) in the repository root.
