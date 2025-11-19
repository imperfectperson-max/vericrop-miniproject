# VeriCrop Testing Guide

This guide provides instructions for testing all components of the VeriCrop platform.

## Quick Start

### 1. Start the Infrastructure

```bash
# Start Kafka, Airflow, and supporting services
docker-compose -f docker-compose-kafka-airflow.yml up -d

# Wait for services to be healthy (30-60 seconds)
docker-compose -f docker-compose-kafka-airflow.yml ps
```

### 2. Run the Java Service

```bash
# With Kafka enabled
KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./gradlew :vericrop-gui:bootRun

# Or with default settings (Kafka disabled)
./gradlew :vericrop-gui:bootRun
```

### 3. Verify Services

```bash
# Java API
curl http://localhost:8080/api/health

# Kafka UI
open http://localhost:8090

# Airflow UI
open http://localhost:8081  # user: admin, password: admin
```

## Testing Components

### Customer API

Use the provided test script:

```bash
# Make sure Java service is running first
./test-customer-api.sh
```

Or test manually:

```bash
# Create customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Customer",
    "email": "test@example.com",
    "customerType": "FARMER",
    "active": true
  }'

# List all customers
curl http://localhost:8080/api/customers

# Get customer by ID
curl http://localhost:8080/api/customers/1

# Update customer
curl -X PUT http://localhost:8080/api/customers/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Customer",
    "email": "test@example.com",
    "customerType": "FARMER",
    "active": true
  }'

# Delete customer
curl -X DELETE http://localhost:8080/api/customers/1
```

### Quality Evaluation API

```bash
# Submit evaluation request
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH_TEST_001",
    "product_type": "apple",
    "farmer_id": "farmer_test_001"
  }'

# Get shipment records
curl http://localhost:8080/api/shipments?batch_id=BATCH_TEST_001
```

### Kafka Integration

#### View Messages in Kafka UI

1. Open http://localhost:8090
2. Navigate to Topics
3. Select a topic (e.g., `vericrop.fruit-quality`)
4. View messages

#### Consume Messages via CLI

```bash
# Consume fruit quality events
docker exec vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.fruit-quality \
  --from-beginning

# Consume supply chain events
docker exec vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.supplychain-events \
  --from-beginning
```

#### Produce Test Messages

```bash
# Produce a test message
echo '{"batch_id":"TEST_001","quality_score":0.85}' | \
docker exec -i vericrop-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.fruit-quality
```

### Airflow DAGs

#### Trigger DAG via UI

1. Open http://localhost:8081
2. Login with admin/admin
3. Enable the DAG: `vericrop_java_integration`
4. Click "Trigger DAG" button
5. Monitor progress in Graph view

#### Trigger DAG via CLI

```bash
# Trigger the integration DAG
docker exec vericrop-airflow-scheduler \
  airflow dags trigger vericrop_java_integration

# Check DAG status
docker exec vericrop-airflow-scheduler \
  airflow dags list

# View task logs
docker exec vericrop-airflow-scheduler \
  airflow tasks logs vericrop_java_integration call_rest_api <execution_date>
```

### Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew test --tests CustomerControllerTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Integration Testing

#### End-to-End Workflow

1. Start all services
2. Trigger Airflow DAG
3. Verify Kafka messages in UI
4. Check Java service logs
5. Query customer and shipment APIs

```bash
# Complete integration test
./gradlew :vericrop-gui:bootRun &
JAVA_PID=$!

# Wait for service to start
sleep 10

# Run tests
./test-customer-api.sh

# Trigger Airflow DAG
docker exec vericrop-airflow-scheduler \
  airflow dags trigger vericrop_java_integration

# Check Kafka topics
echo "Checking Kafka topics..."
docker exec vericrop-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list

# Stop Java service
kill $JAVA_PID
```

## Performance Testing

### Load Testing with Apache Bench

```bash
# Install apache2-utils if not available
# sudo apt-get install apache2-utils

# Test customer list endpoint
ab -n 1000 -c 10 http://localhost:8080/api/customers

# Test health endpoint
ab -n 10000 -c 100 http://localhost:8080/api/health
```

### Kafka Performance

```bash
# Producer performance test
docker exec vericrop-kafka kafka-producer-perf-test \
  --topic vericrop.fruit-quality \
  --num-records 10000 \
  --record-size 1000 \
  --throughput -1 \
  --producer-props bootstrap.servers=localhost:9092

# Consumer performance test
docker exec vericrop-kafka kafka-consumer-perf-test \
  --bootstrap-server localhost:9092 \
  --topic vericrop.fruit-quality \
  --messages 10000 \
  --threads 1
```

## Troubleshooting

### Service Not Starting

```bash
# Check logs
docker-compose -f docker-compose-kafka-airflow.yml logs -f

# Check specific service
docker-compose -f docker-compose-kafka-airflow.yml logs kafka

# Check Java service logs
./gradlew :vericrop-gui:bootRun --info
```

### Kafka Connection Issues

```bash
# Verify Kafka is running
docker exec vericrop-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Check consumer groups
docker exec vericrop-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list
```

### Database Issues

```bash
# Check H2 console (if enabled)
open http://localhost:8080/h2-console

# Connection settings:
# JDBC URL: jdbc:h2:mem:vericrop
# User: sa
# Password: (empty)
```

### Port Conflicts

If ports are already in use, update `.env` file:

```bash
# Edit .env
nano .env

# Change conflicting ports
# Then restart services
docker-compose -f docker-compose-kafka-airflow.yml down
docker-compose -f docker-compose-kafka-airflow.yml up -d
```

## Cleanup

```bash
# Stop all services
docker-compose -f docker-compose-kafka-airflow.yml down

# Remove volumes (clean slate)
docker-compose -f docker-compose-kafka-airflow.yml down -v

# Clean Gradle cache
./gradlew clean
```

## CI/CD Testing

### GitHub Actions

The repository includes CI workflows that run:
- Unit tests on every push
- Integration tests on PR
- Security scans (CodeQL)

### Local CI Simulation

```bash
# Run the same tests as CI
./gradlew clean test --no-daemon

# Check for security issues
# (CodeQL requires GitHub environment)
```

## Test Coverage

View test coverage reports after running tests:

```bash
./gradlew test jacocoTestReport

# Open coverage report
open vericrop-gui/build/reports/jacoco/test/html/index.html
```

## Additional Resources

- [README.md](README.md) - Main documentation
- [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - Kafka integration details
- [.env.sample](.env.sample) - Environment configuration
- [docker-compose-kafka-airflow.yml](docker-compose-kafka-airflow.yml) - Infrastructure setup
