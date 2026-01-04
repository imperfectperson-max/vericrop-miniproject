# 📨 Kafka Service

> Spring Kafka-based messaging for event-driven communication in VeriCrop

---

## 📋 Overview

The Kafka Service provides reliable, high-throughput messaging for:
- Batch quality evaluation events
- Supply chain tracking events
- Real-time alerts and notifications
- Blockchain ledger updates

---

## ✨ Features

- **Idempotent Producers**: Prevents duplicate messages
- **Durable Consumer Groups**: Reliable message processing with offset management
- **Retry Logic**: Automatic retry with exponential backoff using Resilience4j
- **Circuit Breaker**: Prevents cascading failures
- **Schema Validation**: Support for Confluent Schema Registry (optional)
- **TLS/SSL**: Encrypted communication
- **SASL Authentication**: Secure authentication
- **Metrics**: Prometheus metrics for monitoring

---

## ⚡ Quick Start

### 1. Start Kafka

```bash
# Using Docker Compose
docker-compose up -d kafka zookeeper

# Verify Kafka is running
docker exec -it vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 2. Configure Environment

```bash
# Copy example configuration
cp .env.example .env

# Edit configuration
nano .env
```

### 3. Run the Service

```bash
# Build the service
./gradlew :kafka-service:build

# The service is used as a library by vericrop-gui
# It does not run standalone
```

## Configuration

See [.env.example](.env.example) for all configuration options.

### Basic Configuration

```bash
# Kafka Broker
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=true

# Producer Settings (Production)
KAFKA_ACKS=all
KAFKA_RETRIES=3
KAFKA_IDEMPOTENCE=true

# Consumer Settings
KAFKA_AUTO_OFFSET_RESET=earliest
KAFKA_ENABLE_AUTO_COMMIT=false
```

### Security Configuration

#### TLS/SSL Encryption

```bash
# Generate keystores (one-time setup)
keytool -genkey -alias kafka-client -keyalg RSA \
  -keystore client.keystore.jks -storepass <password> \
  -validity 365

# Configure in .env
KAFKA_SECURITY_PROTOCOL=SSL
KAFKA_SSL_TRUSTSTORE_LOCATION=/path/to/truststore.jks
KAFKA_SSL_TRUSTSTORE_PASSWORD=<password>
KAFKA_SSL_KEYSTORE_LOCATION=/path/to/keystore.jks
KAFKA_SSL_KEYSTORE_PASSWORD=<password>
```

#### SASL Authentication

```bash
# PLAIN (simplest, use with TLS)
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="user" password="password";

# SCRAM-SHA-512 (recommended)
KAFKA_SASL_MECHANISM=SCRAM-SHA-512
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="user" password="password";
```

### Schema Registry (Optional)

For schema validation using Confluent Schema Registry:

```bash
# Start Schema Registry
docker run -d --name schema-registry \
  -p 8081:8081 \
  -e SCHEMA_REGISTRY_HOST_NAME=schema-registry \
  -e SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=kafka:9092 \
  confluentinc/cp-schema-registry:7.5.0

# Configure in .env
SCHEMA_REGISTRY_URL=http://localhost:8081
```

## Topics

| Topic | Description | Partitions | Replication |
|-------|-------------|------------|-------------|
| `batch-events` | Batch creation and updates | 3 | 1 |
| `quality-alerts` | Quality threshold alerts | 3 | 1 |
| `logistics-events` | Shipment tracking events | 3 | 1 |
| `blockchain-events` | Blockchain transactions | 3 | 1 |
| `evaluation-requests` | Quality evaluation requests | 3 | 1 |
| `evaluation-results` | Quality evaluation results | 3 | 1 |
| `shipment-records` | Immutable shipment records | 3 | 1 |

### Topic Management

```bash
# Create topic
kafka-topics --create --topic batch-events \
  --partitions 3 --replication-factor 1 \
  --bootstrap-server localhost:9092

# Describe topic
kafka-topics --describe --topic batch-events \
  --bootstrap-server localhost:9092

# Delete topic (if needed)
kafka-topics --delete --topic batch-events \
  --bootstrap-server localhost:9092
```

## Usage

### Producer Example

```java
import org.vericrop.kafka.messaging.KafkaProducerService;
import org.vericrop.dto.EvaluationRequest;

// Initialize producer
KafkaProducerService producer = new KafkaProducerService(true);

// Send message
EvaluationRequest request = new EvaluationRequest();
request.setBatchId("BATCH_001");
request.setProductType("apple");

producer.sendEvaluationRequest(request);
```

### Consumer Example

```java
import org.vericrop.kafka.messaging.KafkaConsumerService;

// Initialize consumer
KafkaConsumerService consumer = new KafkaConsumerService();

// Consume messages
consumer.consumeEvaluationRequests((request) -> {
    System.out.println("Received: " + request.getBatchId());
    // Process message
});
```

## Reliability Features

### Idempotent Producer

Prevents duplicate messages even on retries:

```bash
KAFKA_IDEMPOTENCE=true
KAFKA_ACKS=all
KAFKA_RETRIES=3
```

### Manual Offset Management

Ensures at-least-once delivery:

```bash
KAFKA_ENABLE_AUTO_COMMIT=false
```

Commit offsets manually after processing:

```java
consumer.commitSync();
```

### Retry Logic

Resilience4j retry with exponential backoff:

```bash
RETRY_MAX_ATTEMPTS=3
RETRY_WAIT_DURATION=1000  # milliseconds
```

### Circuit Breaker

Prevents cascading failures:

```bash
CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD=50  # percentage
CIRCUIT_BREAKER_WAIT_DURATION_IN_OPEN_STATE=60000  # ms
```

## Monitoring

### JMX Metrics

```bash
# Enable JMX
KAFKA_JMX_PORT=9999

# Connect with JConsole
jconsole localhost:9999
```

### Prometheus Metrics

```bash
# Enable Prometheus metrics
ENABLE_METRICS=true
PROMETHEUS_PORT=9090

# Scrape endpoint
curl http://localhost:9090/metrics
```

### Consumer Lag Monitoring

```bash
# Check consumer lag
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group vericrop-consumer-group
```

## Troubleshooting

### Connection Issues

```bash
# Test broker connectivity
nc -zv localhost 9092

# Check broker logs
docker logs vericrop-kafka
```

### Consumer Not Receiving Messages

```bash
# Check consumer group status
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group vericrop-consumer-group

# Reset consumer offset (if needed)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group vericrop-consumer-group \
  --topic batch-events \
  --reset-offsets --to-earliest --execute
```

### Message Serialization Errors

```bash
# Enable debug logging
LOG_LEVEL=DEBUG

# Check producer/consumer logs
```

## Performance Tuning

### Producer Performance

```bash
# Increase batch size for throughput
KAFKA_BATCH_SIZE=32768  # 32KB
KAFKA_LINGER_MS=10  # Wait 10ms for batching

# Enable compression
KAFKA_COMPRESSION_TYPE=snappy  # or lz4, zstd
```

### Consumer Performance

```bash
# Increase fetch size
KAFKA_MAX_POLL_RECORDS=500  # Messages per poll

# Tune session timeout
KAFKA_SESSION_TIMEOUT_MS=30000
KAFKA_HEARTBEAT_INTERVAL_MS=3000
```

## Production Deployment

### Multi-Broker Setup

```bash
# Configure multiple brokers
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092

# Increase replication factor
KAFKA_TOPIC_REPLICATION_FACTOR=3
```

### Security Checklist

- [ ] Enable TLS/SSL for encryption
- [ ] Configure SASL authentication
- [ ] Use separate credentials per service
- [ ] Rotate credentials regularly
- [ ] Enable access control lists (ACLs)
- [ ] Monitor for security events

### High Availability

- [ ] Use at least 3 Kafka brokers
- [ ] Set replication factor to 3
- [ ] Configure min.insync.replicas=2
- [ ] Use durable consumer groups
- [ ] Enable idempotent producers
- [ ] Set up monitoring and alerting

## Additional Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

## Support

For issues or questions:
- Check the main [README](../README.md)
- Review [KAFKA_INTEGRATION.md](../docs/implementation/KAFKA_INTEGRATION.md)
- Open an issue on [GitHub](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
