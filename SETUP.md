# VeriCrop Setup Guide

Complete setup guide for the VeriCrop supply chain management system.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Docker Deployment](#docker-deployment)
- [Service Configuration](#service-configuration)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

- **Java Development Kit (JDK) 17 or later**
  ```bash
  java -version
  # Should show version 17 or higher
  ```

- **Docker & Docker Compose**
  ```bash
  docker --version
  docker-compose --version
  ```

- **Git** (for cloning the repository)
  ```bash
  git --version
  ```

### Recommended

- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
- **Database Client**: DBeaver, pgAdmin, or psql CLI
- **HTTP Client**: Postman, Insomnia, or curl

## Local Development Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject
```

### Step 2: Configure Environment

Create `.env` file from the template:

```bash
cp .env.example .env
```

Edit `.env` with your preferred settings:

```bash
# Database Configuration
POSTGRES_USER=vericrop
POSTGRES_PASSWORD=vericrop123
POSTGRES_DB=vericrop

# Kafka Configuration
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# ML Service Configuration
ML_SERVICE_URL=http://localhost:8000
VERICROP_LOAD_DEMO=true
```

### Step 3: Start External Services

Start Postgres, Kafka, and ML Service using Docker Compose:

```bash
# Start all services
docker-compose up -d

# Verify services are running
docker-compose ps

# Check service logs
docker-compose logs -f
```

**Services started:**
- PostgreSQL (port 5432)
- Kafka (port 9092)
- Zookeeper (port 2181)
- ML Service (port 8000)
- Kafka UI (port 8081)

### Step 4: Initialize Database

The database schema is automatically initialized when Postgres starts.
Verify the initialization:

```bash
# Connect to database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

# Check tables
\dt

# View batches table schema
\d batches

# Exit
\q
```

### Step 5: Build the Application

```bash
# Build all modules
./gradlew clean build

# Build without tests (faster)
./gradlew clean build -x test
```

### Step 6: Run the Application

```bash
# Run JavaFX GUI
./gradlew :vericrop-gui:run

# Or run as Spring Boot application (REST API mode)
./gradlew :vericrop-gui:bootRun
```

The application will:
1. Load configuration from `application.properties` and environment variables
2. Initialize ApplicationContext with all services
3. Connect to Postgres, Kafka, and ML Service
4. Launch the JavaFX user interface

### Step 7: Verify Setup

**Check Application Logs:**
```bash
# Look for successful initialization messages:
# ✅ ConfigService initialized
# ✅ MLClientService initialized
# ✅ KafkaMessagingService initialized
# ✅ PostgresBatchRepository initialized
# ✅ ML Service connection: OK
# ✅ Database connection: OK
# ✅ Kafka connection: OK
```

**Test ML Service:**
```bash
curl http://localhost:8000/health
# Should return: {"status":"ok","model_loaded":true,...}
```

**Test Database:**
```bash
docker exec -it vericrop-postgres psql -U vericrop -d vericrop -c "SELECT COUNT(*) FROM batches;"
```

**Test Kafka:**
```bash
# Access Kafka UI at http://localhost:8081
# Or use kafka-console-consumer:
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic batch-events \
  --from-beginning
```

## Docker Deployment

### Build Application Docker Image

```bash
# Build Docker image
docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile .

# Verify image
docker images | grep vericrop-gui
```

### Run Full Stack

```bash
# Start all services including GUI
docker-compose up -d

# View all running containers
docker-compose ps

# Follow logs for all services
docker-compose logs -f

# Follow logs for specific service
docker-compose logs -f vericrop-gui
```

### Access Services

- **JavaFX GUI**: Runs in Docker container (headless mode not recommended for GUI)
- **REST API**: http://localhost:8080
- **ML Service**: http://localhost:8000
- **Kafka UI**: http://localhost:8081
- **Postgres**: localhost:5432

### Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v

# Stop specific service
docker-compose stop vericrop-gui
```

## Service Configuration

### PostgreSQL Configuration

**Connection Settings:**
```
Host: localhost
Port: 5432
Database: vericrop
Username: vericrop
Password: vericrop123
```

**Connection String:**
```
jdbc:postgresql://localhost:5432/vericrop
```

**Environment Variables:**
```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=vericrop
POSTGRES_USER=vericrop
POSTGRES_PASSWORD=vericrop123
DB_POOL_SIZE=10
```

### Kafka Configuration

**Bootstrap Servers:**
```
localhost:9092
```

**Topics:**
- `batch-events` - Batch creation and updates
- `quality-alerts` - Quality alert notifications
- `logistics-events` - Shipment tracking events
- `blockchain-events` - Blockchain transactions

**Environment Variables:**
```bash
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ACKS=all
KAFKA_RETRIES=3
KAFKA_IDEMPOTENCE=true
```

### ML Service Configuration

**Base URL:**
```
http://localhost:8000
```

**Endpoints:**
- `GET /health` - Health check
- `POST /batches` - Create batch
- `GET /batches` - List batches
- `POST /predict` - Quality prediction
- `GET /dashboard/farm` - Dashboard data

**Environment Variables:**
```bash
ML_SERVICE_URL=http://localhost:8000
ML_SERVICE_TIMEOUT=30000
ML_SERVICE_RETRIES=3
VERICROP_LOAD_DEMO=true
```

## Verification

### Verify All Services

Run this comprehensive verification script:

```bash
#!/bin/bash

echo "=== VeriCrop Service Verification ==="

# Check Postgres
echo -n "Postgres: "
docker exec vericrop-postgres pg_isready -U vericrop && echo "✅ OK" || echo "❌ FAILED"

# Check Kafka
echo -n "Kafka: "
docker exec vericrop-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

# Check ML Service
echo -n "ML Service: "
curl -s http://localhost:8000/health | grep -q "ok" && echo "✅ OK" || echo "❌ FAILED"

# Check Database Schema
echo -n "Database Schema: "
docker exec vericrop-postgres psql -U vericrop -d vericrop -c "\dt batches" | grep -q "batches" && echo "✅ OK" || echo "❌ FAILED"

echo "=== Verification Complete ==="
```

### Manual Verification

**1. Create Test Batch:**
```bash
curl -X POST http://localhost:8000/batches \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Batch",
    "farmer": "Test Farmer",
    "product_type": "Apple",
    "quantity": 100
  }'
```

**2. List Batches:**
```bash
curl http://localhost:8000/batches
```

**3. Check Database:**
```bash
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT batch_id, name, status FROM batches;"
```

**4. View Kafka Messages:**
```bash
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic batch-events \
  --from-beginning
```

## Troubleshooting

### Issue: Application Won't Start

**Symptoms:** Application crashes on startup or fails to connect to services.

**Solutions:**
1. Verify Java version: `java -version` (must be 17+)
2. Check if services are running: `docker-compose ps`
3. Review application logs: `./gradlew :vericrop-gui:run --info`
4. Verify environment variables are set correctly

### Issue: Database Connection Failed

**Symptoms:** "Connection refused" or "Authentication failed" errors.

**Solutions:**
1. Verify Postgres is running: `docker-compose ps postgres`
2. Check credentials in `.env` file
3. Test connection manually:
   ```bash
   docker exec -it vericrop-postgres psql -U vericrop -d vericrop
   ```
4. Check if database is initialized:
   ```bash
   docker-compose logs postgres | grep "database system is ready"
   ```

### Issue: Kafka Connection Failed

**Symptoms:** Kafka producer initialization fails or messages not sent.

**Solutions:**
1. Verify Kafka is running: `docker-compose ps kafka`
2. Check Zookeeper is running: `docker-compose ps zookeeper`
3. Test Kafka connectivity:
   ```bash
   docker exec vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092
   ```
4. Temporarily disable Kafka: Set `KAFKA_ENABLED=false` in `.env`

### Issue: ML Service Unavailable

**Symptoms:** Health check fails or predictions return errors.

**Solutions:**
1. Verify ML service is running: `docker-compose ps ml-service`
2. Check ML service logs: `docker-compose logs ml-service`
3. Test health endpoint: `curl http://localhost:8000/health`
4. Enable demo mode: Set `VERICROP_LOAD_DEMO=true` in `.env`

### Issue: Port Already in Use

**Symptoms:** "Address already in use" error when starting services.

**Solutions:**
1. Check what's using the port:
   ```bash
   lsof -i :5432  # Postgres
   lsof -i :9092  # Kafka
   lsof -i :8000  # ML Service
   ```
2. Stop conflicting services or change ports in `docker-compose.yml`
3. Use different ports:
   ```yaml
   ports:
     - "15432:5432"  # External:Internal
   ```

### Issue: Docker Compose Fails

**Symptoms:** Services fail to start or health checks fail.

**Solutions:**
1. Check Docker is running: `docker ps`
2. Update Docker Compose: `docker-compose version`
3. Clean up old containers:
   ```bash
   docker-compose down -v
   docker system prune -a
   ```
4. Rebuild images:
   ```bash
   docker-compose build --no-cache
   docker-compose up -d
   ```

## Next Steps

After successful setup:

1. **Explore the UI**: Run the JavaFX application and navigate through different screens
2. **Create Test Data**: Use the Producer screen to create test batches
3. **Monitor Events**: Watch Kafka UI to see events flowing through the system
4. **Check Database**: Query the database to see persisted batch data
5. **Review Logs**: Check application logs for detailed operation information

## Additional Resources

- [VeriCrop GUI README](vericrop-gui/README.md) - GUI-specific documentation
- [ML Service Documentation](docker/ml-service/README.md) - ML service API reference
- [Database Schema](src/vericrop-gui/main/resources/db/schema.sql) - Database structure
- [Docker Compose Reference](docker-compose.yml) - Service configuration

## Getting Help

If you encounter issues not covered in this guide:

1. Check the application logs for detailed error messages
2. Review the troubleshooting section above
3. Check service-specific logs: `docker-compose logs <service-name>`
4. Open an issue on the GitHub repository with logs and error details
