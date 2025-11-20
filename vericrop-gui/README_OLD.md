# VeriCrop GUI - Interactive Supply Chain Management Interface

The VeriCrop GUI provides both a JavaFX desktop application and a Spring Boot REST API for quality evaluation and supply chain management.

## Features

- 🖥️ **JavaFX Desktop Application** - Interactive dashboards for producers, logistics, consumers, and analytics
- 🌐 **REST API** - Quality evaluation and shipment management endpoints
- ⛓️ **Blockchain Integration** - Immutable record-keeping with configurable initialization modes
- 📨 **Kafka Integration** - Event-driven architecture with graceful fallback
- 🚀 **Fast Dev Mode** - Quick iteration without waiting for full blockchain initialization

## Quick Start

### Running the Desktop GUI

```bash
# From repository root
./gradlew :vericrop-gui:run
```

### Running the REST API

```bash
# From repository root
./gradlew :vericrop-gui:bootRun
```

The REST API will start on port 8080 (configurable via `SERVER_PORT` environment variable).

## Configuration

### Development Mode (Default)

By default, the application runs in **dev mode** for fast iteration:

```bash
# Runs with lightweight blockchain and disabled Kafka
./gradlew :vericrop-gui:run
```

**Dev mode features:**
- Fast blockchain initialization (< 1 second)
- In-memory blockchain with sample data
- Kafka disabled by default (falls back to in-memory stub)
- No external service dependencies

### Production Mode

For production deployment with full features:

```bash
# Set environment variables
export VERICROP_MODE=prod
export KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Run the application
./gradlew :vericrop-gui:bootRun
```

**Production mode features:**
- Full blockchain initialization with validation
- Persistent blockchain storage
- Full Kafka integration
- Complete ledger functionality

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VERICROP_MODE` | `dev` | Application mode: `dev` or `prod` |
| `KAFKA_ENABLED` | `false` | Enable/disable Kafka integration |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SERVER_PORT` | `8080` | REST API port |
| `LEDGER_PATH` | `ledger` | Directory for ledger files |
| `QUALITY_PASS_THRESHOLD` | `0.7` | Quality score threshold (0-1) |
| `ML_SERVICE_URL` | `http://localhost:8000` | ML service endpoint |

## Application Configuration

Edit `src/main/resources/application.yml` to change defaults:

```yaml
# VeriCrop application mode
vericrop:
  mode: ${VERICROP_MODE:dev}  # dev or prod

# Kafka configuration
kafka:
  enabled: ${KAFKA_ENABLED:false}
  topics:
    evaluation-request: evaluation-requests
    evaluation-result: evaluation-results
    shipment-record: shipment-records
```

## REST API Endpoints

### Health Check
```bash
GET /api/health
```

### Quality Evaluation
```bash
POST /api/evaluate
Content-Type: application/json

{
  "batch_id": "BATCH_001",
  "product_type": "apple",
  "farmer_id": "farmer_001",
  "image_path": "/path/to/image.jpg"
}
```

### Shipment Management
```bash
# Get all shipments
GET /api/shipments

# Get shipments for a specific batch
GET /api/shipments?batch_id=BATCH_001

# Get specific shipment
GET /api/shipments/{ledger_id}
```

## Docker Deployment

### Building the Docker Image

```bash
# Build from repository root
docker build -t vericrop-gui -f vericrop-gui/Dockerfile .
```

The Dockerfile uses a **multi-stage build**:
1. **Build stage**: Compiles the application with Gradle
2. **Runtime stage**: Minimal JRE image with only the JAR

**Important**: Blockchain initialization happens at **container startup**, not during image build. This ensures:
- Faster image builds
- Consistent initialization across environments
- Configuration can be applied at runtime

### Running the Container

```bash
# Development mode (default)
docker run -p 8080:8080 vericrop-gui

# Production mode with Kafka
docker run -p 8080:8080 \
  -e VERICROP_MODE=prod \
  -e KAFKA_ENABLED=true \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  vericrop-gui
```

### Docker Compose

See the main repository `docker-compose.yml` for full stack deployment:

```bash
# From repository root
docker-compose up
```

## Kafka Integration

### Enabling Kafka

Set the `KAFKA_ENABLED` environment variable:

```bash
export KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Kafka Topics

The application publishes to and consumes from these topics:

- `evaluation-requests` - Quality evaluation requests
- `evaluation-results` - Evaluation results
- `shipment-records` - Shipment tracking records
- `logistics-tracking` - Logistics events
- `quality-alerts` - Quality alert notifications
- `blockchain-events` - Blockchain transaction events

### Graceful Kafka Fallback

When Kafka is disabled or unavailable:
- Application continues to function normally
- Events are logged instead of published
- No blocking or errors from Kafka connection failures
- UI operations remain responsive

## Airflow Integration

The VeriCrop DAG (`airflow/dags/vericrop_dag.py`) demonstrates end-to-end integration:

1. Produces evaluation requests to Kafka
2. Calls REST API for evaluation
3. Verifies ledger records
4. Generates pipeline summary

### Running the Airflow DAG

```bash
# Ensure VeriCrop API is running
./gradlew :vericrop-gui:bootRun

# Start Airflow (from airflow directory)
airflow standalone

# Trigger the DAG
airflow dags trigger vericrop_evaluation_pipeline
```

The DAG has graceful fallbacks:
- Runs in simulation mode if Kafka is unavailable
- Handles API connection failures
- Continues with available services

## Building and Testing

### Build the Module

```bash
# Build only vericrop-gui
./gradlew :vericrop-gui:build

# Build with tests
./gradlew :vericrop-gui:test

# Create bootable JAR
./gradlew :vericrop-gui:bootJar
```

### Run Tests

```bash
# Run all tests
./gradlew :vericrop-gui:test

# Run with code coverage
./gradlew :vericrop-gui:jacocoTestReport
```

### Smoke Testing

```bash
# Run smoke tests (if available)
./scripts/smoke_test.sh
```

## Troubleshooting

### Application Won't Start

1. **Check Java version**: Requires Java 17+
   ```bash
   java -version
   ```

2. **Check port availability**: Default port 8080
   ```bash
   lsof -i :8080
   ```

3. **Check logs**: Enable debug logging
   ```bash
   export LOGGING_LEVEL_ORG_VERICROP=DEBUG
   ./gradlew :vericrop-gui:bootRun
   ```

### Kafka Connection Issues

If you see Kafka connection errors but want to continue:

```bash
# Disable Kafka
export KAFKA_ENABLED=false
./gradlew :vericrop-gui:bootRun
```

The application will fall back to in-memory mode.

### Blockchain Initialization Slow

In production mode, blockchain validation can take time. Use dev mode for faster iteration:

```bash
export VERICROP_MODE=dev
./gradlew :vericrop-gui:bootRun
```

## Architecture

```
vericrop-gui/
├── src/main/java/org/vericrop/gui/
│   ├── MainApp.java              # JavaFX application entry point
│   ├── VeriCropApiApplication.java  # Spring Boot REST API
│   ├── ProducerController.java    # Farm dashboard controller
│   ├── LogisticsController.java   # Logistics dashboard
│   ├── ConsumerController.java    # Consumer dashboard
│   ├── AnalyticsController.java   # Analytics dashboard
│   ├── config/
│   │   └── AppConfiguration.java  # Spring configuration
│   ├── controller/
│   │   └── EvaluationController.java  # REST API controller
│   └── util/
│       └── BlockchainInitializer.java  # Blockchain setup utility
├── src/main/resources/
│   ├── application.yml            # Spring Boot configuration
│   ├── fxml/                      # JavaFX UI layouts
│   └── css/                       # Stylesheets
└── Dockerfile                     # Multi-stage Docker build
```

## Performance Considerations

### Dev Mode
- Blockchain init: < 1 second
- Memory usage: ~200 MB
- Startup time: ~5 seconds

### Production Mode
- Blockchain init: 5-10 seconds (depends on chain size)
- Memory usage: ~400 MB
- Startup time: ~15 seconds

## Contributing

When making changes to vericrop-gui:

1. Maintain dev mode for fast iteration
2. Ensure Kafka fallback works (test with `KAFKA_ENABLED=false`)
3. Keep REST API backward compatible
4. Test both JavaFX GUI and REST API modes
5. Update this README if adding new features

## Related Documentation

- [Main README](../README.md) - Project overview
- [Docker Implementation](../DOCKER_IMPLEMENTATION.md) - Deployment details
- [Kafka Integration](../KAFKA_INTEGRATION.md) - Kafka setup guide
- [Deployment Guide](../DEPLOYMENT.md) - Production deployment

## License

See [LICENSE](../LICENSE) in the repository root.
