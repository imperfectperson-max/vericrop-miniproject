# VeriCrop Deployment Guide

This guide provides detailed instructions for deploying the VeriCrop platform using Docker Compose.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Configuration](#configuration)
4. [Service Details](#service-details)
5. [Testing the Deployment](#testing-the-deployment)
6. [Troubleshooting](#troubleshooting)
7. [Maintenance](#maintenance)

## Prerequisites

### System Requirements

- **Operating System**: Linux, macOS, or Windows with WSL2
- **RAM**: Minimum 8GB, Recommended 16GB
- **Disk Space**: Minimum 20GB free space
- **CPU**: Minimum 4 cores

### Software Requirements

- **Docker**: Version 20.10 or later
- **Docker Compose**: Version 2.0 or later
- **Git**: For cloning the repository

### Verify Installation

```bash
docker --version
# Should output: Docker version 20.10.x or later

docker-compose --version
# Should output: Docker Compose version 2.x.x or later
```

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject
```

### 2. Build Java Artifacts

The Java services need to be built before running Docker Compose:

```bash
./gradlew build
```

This will:
- Build all Java modules (vericrop-core, kafka-service, ml-client, vericrop-gui)
- Run all tests
- Generate JAR files in `build/libs/` directories

**Expected output**: `BUILD SUCCESSFUL`

### 3. Start the Stack

```bash
docker-compose up --build
```

Or in detached mode (background):

```bash
docker-compose up --build -d
```

### 4. Wait for Services to Initialize

The stack takes 2-3 minutes to fully initialize. You can monitor progress:

```bash
# Watch all service logs
docker-compose logs -f

# Watch specific service
docker-compose logs -f vericrop-gui
docker-compose logs -f kafka
docker-compose logs -f airflow-webserver
```

### 5. Verify Deployment

Run the smoke test script:

```bash
./scripts/smoke_test.sh
```

## Configuration

### Environment Variables

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` to customize settings:
   ```bash
   # PostgreSQL Configuration
   POSTGRES_USER=vericrop
   POSTGRES_PASSWORD=vericrop123
   POSTGRES_DB=vericrop
   
   # Kafka Configuration
   KAFKA_BOOTSTRAP_SERVERS=kafka:29092
   
   # Airflow Configuration
   AIRFLOW_ADMIN_USERNAME=admin
   AIRFLOW_ADMIN_PASSWORD=admin
   
   # VeriCrop API Configuration
   VERICROP_API_URL=http://vericrop-gui:8080
   ML_SERVICE_URL=http://ml-service:8000
   
   # Quality Evaluation
   QUALITY_PASS_THRESHOLD=0.7
   ```

3. Restart services to apply changes:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

### Custom Configuration

For advanced configuration, modify:
- `src/vericrop-gui/main/resources/application.yml` - Spring Boot settings
- `docker-compose.yml` - Service configuration
- `airflow/dags/vericrop_dag.py` - Airflow workflow settings

## Service Details

### Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Network                           │
│                      (vericrop-network)                         │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │Zookeeper │  │  Kafka   │  │ Kafka UI │  │ VeriCrop │      │
│  │  :2181   │←→│  :9092   │←→│  :8081   │  │GUI :8080 │      │
│  └──────────┘  └──────────┘  └──────────┘  └────┬─────┘      │
│                      ↑                            │            │
│  ┌──────────┐  ┌────┴─────┐  ┌──────────┐  ┌────┴─────┐      │
│  │PostgreSQL│  │ Airflow  │  │   ML     │  │ Ledger   │      │
│  │  :5432   │←→│  :8082   │  │ Service  │  │ Volume   │      │
│  └──────────┘  └──────────┘  │  :8000   │  └──────────┘      │
│                               └──────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

### Service Ports

| Service | Internal Port | External Port | Description |
|---------|--------------|---------------|-------------|
| VeriCrop GUI | 8080 | 8080 | REST API and health endpoint |
| ML Service | 8000 | 8000 | Python FastAPI ML predictions |
| Kafka | 9092 | 9092 | Message broker (PLAINTEXT_HOST) |
| Kafka | 29092 | - | Message broker (PLAINTEXT, internal) |
| Kafka UI | 8080 | 8081 | Web UI for Kafka monitoring |
| Zookeeper | 2181 | 2181 | Kafka coordination |
| Airflow Webserver | 8080 | 8082 | Airflow UI |
| PostgreSQL | 5432 | 5432 | Application database |
| PostgreSQL (Airflow) | 5432 | - | Airflow metadata (internal) |
| Redis | 6379 | - | Airflow backend (internal) |
| Mosquitto | 1883 | 1883 | MQTT broker |

### Kafka Topics

The following topics are automatically created on startup:

| Topic | Partitions | Purpose |
|-------|-----------|---------|
| `vericrop-input` | 3 | General input messages |
| `vericrop-output` | 3 | General output messages |
| `vericrop-events` | 3 | System events |
| `evaluation-requests` | 3 | Quality evaluation requests |
| `evaluation-results` | 3 | Quality evaluation results |
| `shipment-records` | 3 | Ledger shipment records |
| `quality-alerts` | 3 | Quality threshold alerts |
| `logistics-events` | 3 | Logistics tracking events |
| `blockchain-events` | 3 | Blockchain transactions |

### Persistent Volumes

Data is persisted in the following Docker volumes:

- `postgres_data` - Application database
- `postgres_airflow_data` - Airflow metadata
- `kafka_data` - Kafka message logs
- `zookeeper_data` - Zookeeper state
- `ledger_data` - VeriCrop ledger files

To view volumes:
```bash
docker volume ls | grep vericrop
```

To inspect a volume:
```bash
docker volume inspect vericrop-miniproject_kafka_data
```

## Testing the Deployment

### 1. Health Checks

Check all service health:

```bash
# VeriCrop API
curl http://localhost:8080/api/health

# ML Service
curl http://localhost:8000/health

# Kafka UI (web interface)
curl http://localhost:8081

# Airflow UI (web interface)
curl http://localhost:8082/health
```

### 2. API Testing

Test the VeriCrop evaluation API:

```bash
# Create evaluation request
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "TEST_001",
    "product_type": "apple",
    "farmer_id": "test_farmer"
  }'

# Expected response includes:
# - quality_score
# - pass_fail
# - ledger_id
```

### 3. Kafka Topic Verification

List Kafka topics:

```bash
docker exec vericrop-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

### 4. Airflow DAG Testing

1. Open Airflow UI: http://localhost:8082
2. Login with `admin` / `admin`
3. Enable the `vericrop_evaluation_pipeline` DAG
4. Trigger the DAG manually
5. Monitor execution in the Graph or Grid view

### 5. End-to-End Smoke Test

Run the comprehensive smoke test:

```bash
./scripts/smoke_test.sh
```

This will:
- Check all service health endpoints
- Test API endpoints
- Verify Kafka topics
- Display service URLs

## Troubleshooting

### Common Issues

#### 1. Services Won't Start

**Problem**: Services fail to start or are unhealthy

**Solution**:
```bash
# Check service status
docker-compose ps

# View logs for problematic service
docker-compose logs <service-name>

# Restart specific service
docker-compose restart <service-name>

# Full restart
docker-compose down
docker-compose up -d
```

#### 2. Port Already in Use

**Problem**: Error: "Bind for 0.0.0.0:8080 failed: port is already allocated"

**Solution**:
```bash
# Find process using the port
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process or change the port in docker-compose.yml
```

#### 3. Out of Memory

**Problem**: Services crash with OutOfMemoryError

**Solution**:
```bash
# Increase Docker memory limit (Docker Desktop > Settings > Resources)
# Or reduce running services:
docker-compose up -d zookeeper kafka vericrop-gui ml-service
```

#### 4. Build Failures

**Problem**: Gradle build fails or Docker build fails

**Solution**:
```bash
# Clean build
./gradlew clean build --no-daemon

# If still failing, check Java version
java -version  # Should be 17 or higher

# Check Docker disk space
docker system df
docker system prune  # Clean up if needed
```

#### 5. Kafka Connection Issues

**Problem**: Services can't connect to Kafka

**Solution**:
```bash
# Check Kafka health
docker exec vericrop-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Restart Kafka and dependent services
docker-compose restart kafka
docker-compose restart vericrop-gui
```

### Debug Mode

Enable debug logging:

1. Edit `src/vericrop-gui/main/resources/application.yml`:
   ```yaml
   logging:
     level:
       root: DEBUG
       org.vericrop: TRACE
   ```

2. Rebuild and restart:
   ```bash
   ./gradlew :vericrop-gui:bootJar
   docker-compose up --build vericrop-gui
   ```

### Viewing Service Logs

```bash
# All services
docker-compose logs -f

# Specific service with tail
docker-compose logs -f --tail=100 vericrop-gui

# Since a specific time
docker-compose logs --since 10m vericrop-gui

# Save logs to file
docker-compose logs > vericrop-logs.txt
```

## Maintenance

### Stopping Services

```bash
# Stop all services (containers remain)
docker-compose stop

# Stop and remove containers
docker-compose down

# Stop and remove containers and volumes (DANGER: deletes data)
docker-compose down -v
```

### Updating Services

```bash
# Pull latest code
git pull origin main

# Rebuild and restart
./gradlew clean build
docker-compose up --build -d
```

### Backup

#### Backup Volumes

```bash
# Backup PostgreSQL data
docker run --rm -v vericrop-miniproject_postgres_data:/data \
  -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz /data

# Backup Kafka data
docker run --rm -v vericrop-miniproject_kafka_data:/data \
  -v $(pwd):/backup alpine tar czf /backup/kafka-backup.tar.gz /data

# Backup ledger
docker run --rm -v vericrop-miniproject_ledger_data:/data \
  -v $(pwd):/backup alpine tar czf /backup/ledger-backup.tar.gz /data
```

#### Restore Volumes

```bash
# Restore PostgreSQL data
docker run --rm -v vericrop-miniproject_postgres_data:/data \
  -v $(pwd):/backup alpine sh -c "cd /data && tar xzf /backup/postgres-backup.tar.gz --strip 1"
```

### Monitoring

#### Resource Usage

```bash
# View resource usage
docker stats

# View specific service
docker stats vericrop-gui
```

#### Service Health

```bash
# Check all container health
docker-compose ps

# Inspect specific container
docker inspect vericrop-gui | grep -A 10 Health
```

### Cleanup

```bash
# Remove stopped containers
docker-compose rm

# Clean up Docker system
docker system prune -a

# Remove unused volumes (DANGER: permanent data loss)
docker volume prune
```

## Production Considerations

For production deployment, consider:

1. **Security**:
   - Change default passwords
   - Use secrets management (Docker secrets, Kubernetes secrets)
   - Enable TLS/SSL for all services
   - Configure firewall rules

2. **Scalability**:
   - Increase Kafka partitions
   - Scale VeriCrop GUI replicas
   - Use external PostgreSQL cluster
   - Configure proper resource limits

3. **High Availability**:
   - Use Kafka cluster (3+ brokers)
   - PostgreSQL replication
   - Load balancer for API endpoints
   - Container orchestration (Kubernetes)

4. **Monitoring**:
   - Add Prometheus + Grafana
   - Configure alerting
   - Log aggregation (ELK stack)
   - APM tools (New Relic, Datadog)

5. **Backup**:
   - Automated backup schedules
   - Off-site backup storage
   - Regular restore testing

## Support

For issues and questions:

- GitHub Issues: https://github.com/imperfectperson-max/vericrop-miniproject/issues
- Documentation: [README.md](../../README.md)
- Kafka Integration: [KAFKA_INTEGRATION.md](../implementation/KAFKA_INTEGRATION.md)
