# VeriCrop Production Deployment Guide

This guide provides comprehensive instructions for deploying VeriCrop in production environments, including Docker Compose, Kubernetes, and cloud platforms.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building Docker Images](#building-docker-images)
- [Configuration & Secrets Management](#configuration--secrets-management)
- [Docker Compose Deployment](#docker-compose-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Monitoring & Observability](#monitoring--observability)
- [Security](#security)
- [Backup & Recovery](#backup--recovery)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- **Docker**: 20.10+ with Docker Compose
- **Kubernetes**: 1.24+ (for K8s deployment)
- **Helm**: 3.8+ (optional, for Helm deployment)
- **kubectl**: matching your cluster version
- **Java**: 17+ (for local builds)
- **Gradle**: 8.0+ (included via wrapper)

### Infrastructure Requirements

**Minimum Production Requirements:**
- CPU: 8 cores
- RAM: 16 GB
- Storage: 100 GB (plus separate volumes for data)
- Network: 1 Gbps

**Recommended Production Setup:**
- CPU: 16 cores
- RAM: 32 GB
- Storage: 500 GB SSD (plus separate volumes)
- Network: 10 Gbps

## Building Docker Images

### 1. Build Java Artifacts

```bash
# From project root
./gradlew clean build -x test

# Verify build
ls -lh vericrop-gui/build/libs/vericrop-gui-*.jar
```

### 2. Build Docker Images

```bash
# Build all images
docker-compose -f docker-compose.prod.yml build

# Or build individual services
docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile .
docker build -t vericrop-ml-service:latest -f docker/ml-service/Dockerfile docker/ml-service
docker build -t vericrop-kafka-service:latest -f kafka-service/Dockerfile .
docker build -t vericrop-airflow:latest -f airflow/Dockerfile airflow
```

### 3. Tag and Push Images (if using registry)

```bash
# Tag images
docker tag vericrop-gui:latest your-registry.com/vericrop-gui:1.0.0
docker tag vericrop-ml-service:latest your-registry.com/vericrop-ml-service:1.0.0

# Push to registry
docker push your-registry.com/vericrop-gui:1.0.0
docker push your-registry.com/vericrop-ml-service:1.0.0
```

## Configuration & Secrets Management

### Environment Variables

VeriCrop uses environment variables for configuration. **Never commit secrets to version control.**

#### 1. Create Production Environment File

```bash
# Copy example file
cp .env.production.example .env.production

# Edit with your production values
nano .env.production
```

#### 2. Critical Configuration Items

```bash
# Database (use strong passwords)
POSTGRES_USER=vericrop_prod
POSTGRES_PASSWORD=<strong-random-password>
POSTGRES_DB=vericrop_production
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
KAFKA_ENABLED=true
KAFKA_ACKS=all
KAFKA_IDEMPOTENCE=true

# ML Service
ML_SERVICE_URL=http://ml-service:8000
ML_SERVICE_TIMEOUT=30000
ML_SERVICE_RETRIES=3

# Application Mode
VERICROP_MODE=prod  # Full blockchain validation
KAFKA_ENABLED_FALLBACK=true

# Security
JWT_SECRET=<generate-strong-secret>  # Use: openssl rand -base64 32
SESSION_TIMEOUT=3600  # seconds
ENABLE_TLS=true

# Monitoring
ENABLE_METRICS=true
PROMETHEUS_PORT=9090
LOG_LEVEL=INFO
LOG_FORMAT=json
```

### Secrets Management Options

#### Option 1: Docker Secrets (Swarm)

```bash
# Create secrets
echo "<db-password>" | docker secret create postgres_password -
echo "<jwt-secret>" | docker secret create jwt_secret -

# Reference in docker-compose.yml
services:
  vericrop-gui:
    secrets:
      - postgres_password
      - jwt_secret
```

#### Option 2: Kubernetes Secrets

```bash
# Create secret
kubectl create secret generic vericrop-secrets \
  --from-literal=postgres-password='<strong-password>' \
  --from-literal=jwt-secret='<strong-secret>' \
  --namespace=vericrop

# Reference in deployment manifests (see k8s/ directory)
```

#### Option 3: External Secrets Manager

- **AWS Secrets Manager**: Use AWS Secrets Manager with IAM roles
- **Azure Key Vault**: Use Azure Key Vault with managed identities
- **HashiCorp Vault**: Use Vault for centralized secret management
- **GCP Secret Manager**: Use GCP Secret Manager with service accounts

Example for AWS Secrets Manager:

```bash
# Store secret
aws secretsmanager create-secret \
  --name vericrop/postgres/password \
  --secret-string '<strong-password>'

# Retrieve in application (using AWS SDK)
# Configure IAM role with secretsmanager:GetSecretValue permission
```

### TLS Certificate Setup

```bash
# Generate self-signed certificates (dev/testing only)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=vericrop.example.com"

# For production, use Let's Encrypt or your organization's CA
# Place certificates in:
# - ./certs/tls.key
# - ./certs/tls.crt
# - ./certs/ca.crt (if using custom CA)
```

## Docker Compose Deployment

### Production Docker Compose Setup

#### 1. Prepare Configuration

```bash
# Create production env file
cp .env.production.example .env.production

# Edit with production values (never commit this file)
nano .env.production

# Verify configuration
docker-compose -f docker-compose.prod.yml config
```

#### 2. Initialize Volumes

```bash
# Create persistent volumes
docker volume create vericrop_postgres_data
docker volume create vericrop_kafka_data
docker volume create vericrop_zookeeper_data
docker volume create vericrop_airflow_logs
docker volume create vericrop_ledger_data

# Verify volumes
docker volume ls | grep vericrop
```

#### 3. Start Services

```bash
# Start all services
docker-compose -f docker-compose.prod.yml up -d

# Watch logs
docker-compose -f docker-compose.prod.yml logs -f

# Check service health
docker-compose -f docker-compose.prod.yml ps
```

#### 4. Verify Deployment

```bash
# Test health endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health

# Test database connection
docker exec -it vericrop-postgres-prod psql -U vericrop -d vericrop -c "SELECT version();"

# Test Kafka
docker exec -it vericrop-kafka-prod kafka-topics \
  --list --bootstrap-server localhost:9092
```

#### 5. Database Initialization

Database migrations run automatically on startup via Flyway. To verify:

```bash
# Check migrations
docker exec -it vericrop-postgres-prod psql -U vericrop -d vericrop -c \
  "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

### Scaling Services

```bash
# Scale ML service for higher throughput
docker-compose -f docker-compose.prod.yml up -d --scale ml-service=3

# Verify scaling
docker-compose -f docker-compose.prod.yml ps ml-service
```

### Updating Services

```bash
# Pull latest images
docker-compose -f docker-compose.prod.yml pull

# Restart with zero downtime (requires load balancer)
docker-compose -f docker-compose.prod.yml up -d --no-deps --build vericrop-gui

# Verify update
docker-compose -f docker-compose.prod.yml ps
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (EKS, GKE, AKS, or on-premise)
- kubectl configured to access your cluster
- Sufficient cluster resources (see requirements above)

### Namespace Setup

```bash
# Create namespace
kubectl create namespace vericrop

# Set as default
kubectl config set-context --current --namespace=vericrop
```

### Deploy with Kubernetes Manifests

#### 1. Create Secrets

```bash
# Create secrets from env file
kubectl create secret generic vericrop-secrets \
  --from-env-file=.env.production \
  --namespace=vericrop

# Or create individual secrets
kubectl create secret generic postgres-credentials \
  --from-literal=username=vericrop_prod \
  --from-literal=password='<strong-password>' \
  --namespace=vericrop
```

#### 2. Apply ConfigMaps

```bash
# Create ConfigMap for application config
kubectl apply -f k8s/configmap.yaml

# Verify
kubectl get configmap -n vericrop
```

#### 3. Deploy PostgreSQL

```bash
# Apply PostgreSQL StatefulSet
kubectl apply -f k8s/postgres-statefulset.yaml

# Verify
kubectl get statefulset postgres -n vericrop
kubectl get pvc -n vericrop
```

#### 4. Deploy Kafka

```bash
# Apply Kafka and Zookeeper
kubectl apply -f k8s/zookeeper-statefulset.yaml
kubectl apply -f k8s/kafka-statefulset.yaml

# Verify
kubectl get pods -l app=kafka -n vericrop
```

#### 5. Deploy Application Services

```bash
# Deploy ML Service
kubectl apply -f k8s/ml-service-deployment.yaml

# Deploy vericrop-gui
kubectl apply -f k8s/vericrop-gui-deployment.yaml

# Deploy Airflow
kubectl apply -f k8s/airflow-deployment.yaml

# Verify all deployments
kubectl get deployments -n vericrop
kubectl get pods -n vericrop
```

#### 6. Expose Services

```bash
# Apply Services
kubectl apply -f k8s/services.yaml

# Apply Ingress (if using ingress controller)
kubectl apply -f k8s/ingress.yaml

# Get service endpoints
kubectl get svc -n vericrop
```

### Kubernetes Configuration Files

See the `k8s/` directory for complete manifests:

- `k8s/namespace.yaml` - Namespace definition
- `k8s/configmap.yaml` - Application configuration
- `k8s/secrets.yaml.example` - Secret templates
- `k8s/postgres-statefulset.yaml` - PostgreSQL deployment
- `k8s/kafka-statefulset.yaml` - Kafka deployment
- `k8s/vericrop-gui-deployment.yaml` - Main application
- `k8s/ml-service-deployment.yaml` - ML service
- `k8s/airflow-deployment.yaml` - Airflow deployment
- `k8s/services.yaml` - Service definitions
- `k8s/ingress.yaml` - Ingress rules
- `k8s/hpa.yaml` - Horizontal Pod Autoscalers

### Autoscaling

```bash
# Apply Horizontal Pod Autoscalers
kubectl apply -f k8s/hpa.yaml

# Monitor autoscaling
kubectl get hpa -n vericrop -w

# Manual scaling
kubectl scale deployment vericrop-gui --replicas=5 -n vericrop
```

### Rolling Updates

```bash
# Update image version
kubectl set image deployment/vericrop-gui \
  vericrop-gui=your-registry.com/vericrop-gui:1.1.0 \
  -n vericrop

# Monitor rollout
kubectl rollout status deployment/vericrop-gui -n vericrop

# Rollback if needed
kubectl rollout undo deployment/vericrop-gui -n vericrop
```

## Monitoring & Observability

### Health Checks

All services expose standard health endpoints:

```bash
# vericrop-gui
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness

# ML Service
curl http://localhost:8000/health

# Kafka (via Kafka manager or health check script)
./scripts/kafka-health-check.sh
```

### Prometheus Metrics

```bash
# Metrics endpoints
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8000/metrics

# Example Prometheus configuration (prometheus.yml)
scrape_configs:
  - job_name: 'vericrop-gui'
    static_configs:
      - targets: ['vericrop-gui:8080']
    metrics_path: '/actuator/prometheus'
  
  - job_name: 'ml-service'
    static_configs:
      - targets: ['ml-service:8000']
    metrics_path: '/metrics'
```

### Centralized Logging

#### JSON Logging Configuration

All services are configured for JSON logging to stdout. Configure log aggregation:

**Option 1: ELK Stack (Elasticsearch, Logstash, Kibana)**

```bash
# Deploy ELK stack
kubectl apply -f k8s/logging/elasticsearch.yaml
kubectl apply -f k8s/logging/logstash.yaml
kubectl apply -f k8s/logging/kibana.yaml
```

**Option 2: Loki + Grafana**

```bash
# Deploy Loki
helm repo add grafana https://grafana.github.io/helm-charts
helm install loki grafana/loki-stack -n vericrop
```

**Option 3: Cloud Provider Logging**

- **AWS**: CloudWatch Logs with Fluent Bit
- **GCP**: Cloud Logging with Fluentd
- **Azure**: Azure Monitor with Fluentd

### Application Performance Monitoring (APM)

Consider integrating APM tools:

- **New Relic**: Java agent for vericrop-gui
- **Datadog**: APM integration for all services
- **Elastic APM**: Native integration with ELK stack
- **Prometheus + Grafana**: Open-source monitoring stack

## Security

### Network Security

#### 1. Network Policies (Kubernetes)

```bash
# Apply network policies
kubectl apply -f k8s/network-policies.yaml

# Verify
kubectl get networkpolicy -n vericrop
```

#### 2. TLS Configuration

**Kafka TLS:**

```bash
# Generate Kafka keystore and truststore
keytool -genkey -keyalg RSA -keystore kafka.server.keystore.jks \
  -validity 365 -storepass <password> -keypass <password>

# Configure in kafka environment
KAFKA_SSL_KEYSTORE_LOCATION=/certs/kafka.server.keystore.jks
KAFKA_SSL_KEYSTORE_PASSWORD=<password>
KAFKA_SSL_TRUSTSTORE_LOCATION=/certs/kafka.server.truststore.jks
KAFKA_SSL_TRUSTSTORE_PASSWORD=<password>
```

**PostgreSQL TLS:**

```bash
# Place certificates in postgres config
# - server.crt
# - server.key
# - root.crt

# Enable SSL in postgresql.conf
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'
ssl_ca_file = 'root.crt'
```

#### 3. Service Authentication

**Inter-service Authentication:**

- Use mTLS for service-to-service communication
- Configure JWT tokens for REST API calls
- Use Kafka SASL for authenticated messaging

Example JWT configuration:

```bash
# Generate strong JWT secret
openssl rand -base64 32

# Configure in application
JWT_SECRET=<generated-secret>
JWT_EXPIRATION=3600
```

### Access Control

```bash
# Database: Use least-privilege accounts
CREATE USER vericrop_app WITH PASSWORD '<password>';
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO vericrop_app;

# Kafka: Use ACLs for topic access
kafka-acls --bootstrap-server localhost:9092 \
  --add --allow-principal User:vericrop-app \
  --operation Read --operation Write \
  --topic batch-events
```

### Security Scanning

```bash
# Scan Docker images for vulnerabilities
docker scan vericrop-gui:latest

# Or use Trivy
trivy image vericrop-gui:latest

# Kubernetes security scanning
kubectl kube-bench run --targets master,node
```

## Backup & Recovery

### Database Backups

```bash
# Automated backup script (daily)
#!/bin/bash
BACKUP_DIR=/backups/postgres
DATE=$(date +%Y%m%d_%H%M%S)

docker exec vericrop-postgres-prod pg_dump -U vericrop vericrop | \
  gzip > $BACKUP_DIR/vericrop_$DATE.sql.gz

# Keep last 30 days
find $BACKUP_DIR -name "vericrop_*.sql.gz" -mtime +30 -delete
```

**Restore:**

```bash
# Restore from backup
gunzip < vericrop_20240115_120000.sql.gz | \
  docker exec -i vericrop-postgres-prod psql -U vericrop -d vericrop
```

### Ledger Data Backups

```bash
# Backup blockchain ledger
docker cp vericrop-gui:/app/ledger ./backups/ledger_$(date +%Y%m%d)

# Restore
docker cp ./backups/ledger_20240115 vericrop-gui:/app/ledger
```

### Disaster Recovery Plan

1. **RTO (Recovery Time Objective)**: < 4 hours
2. **RPO (Recovery Point Objective)**: < 1 hour

**Recovery Steps:**

```bash
# 1. Provision infrastructure
terraform apply -var-file=prod.tfvars

# 2. Restore database
./scripts/restore-database.sh <backup-file>

# 3. Restore configuration
kubectl apply -f k8s/ -n vericrop

# 4. Restore application state
./scripts/restore-ledger.sh <ledger-backup>

# 5. Verify services
./scripts/health-check-all.sh
```

## Troubleshooting

### Common Issues

#### Services Won't Start

```bash
# Check logs
docker-compose -f docker-compose.prod.yml logs <service-name>

# Or in Kubernetes
kubectl logs -f deployment/vericrop-gui -n vericrop

# Check resource limits
kubectl describe pod <pod-name> -n vericrop
```

#### Database Connection Issues

```bash
# Test connection from container
docker exec -it vericrop-gui bash
nc -zv postgres 5432

# Check database logs
docker-compose -f docker-compose.prod.yml logs postgres

# Verify credentials
echo $POSTGRES_PASSWORD
```

#### Kafka Connection Issues

```bash
# Test Kafka connectivity
docker exec -it vericrop-kafka-prod kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# List topics
docker exec -it vericrop-kafka-prod kafka-topics \
  --list --bootstrap-server localhost:9092

# Check consumer lag
docker exec -it vericrop-kafka-prod kafka-consumer-groups \
  --bootstrap-server localhost:9092 --describe --all-groups
```

#### ML Service Issues

```bash
# Check model file exists
docker exec -it ml-service ls -lh /app/model/

# Test prediction endpoint
curl -X POST -F "file=@test-image.jpg" http://localhost:8000/predict

# Check memory usage (model requires ~2GB)
docker stats ml-service
```

### Performance Tuning

#### JVM Tuning (vericrop-gui)

```bash
# Set in docker-compose or K8s deployment
JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

#### PostgreSQL Tuning

```sql
-- Adjust based on available RAM
ALTER SYSTEM SET shared_buffers = '4GB';
ALTER SYSTEM SET effective_cache_size = '12GB';
ALTER SYSTEM SET maintenance_work_mem = '1GB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
SELECT pg_reload_conf();
```

#### Kafka Tuning

```bash
# Increase batch size for throughput
KAFKA_BATCH_SIZE=32768
KAFKA_LINGER_MS=10
KAFKA_BUFFER_MEMORY=67108864

# Increase partitions for parallelism
kafka-topics --alter --topic batch-events \
  --partitions 10 --bootstrap-server localhost:9092
```

### Getting Help

1. Check logs first: `docker-compose logs` or `kubectl logs`
2. Review [README.md](README.md) for basic troubleshooting
3. Search [GitHub Issues](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
4. Open new issue with:
   - Detailed description
   - Log outputs
   - Environment details
   - Steps to reproduce

## Additional Resources

- [README.md](README.md) - General project documentation
- [vericrop-gui/README.md](vericrop-gui/README.md) - GUI module details
- [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - Kafka setup
- [Docker Documentation](https://docs.docker.com/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

**Deployment Checklist:**

- [ ] Build all Docker images
- [ ] Create production `.env` file with strong secrets
- [ ] Configure TLS certificates
- [ ] Set up database backups
- [ ] Configure monitoring and alerting
- [ ] Test all health endpoints
- [ ] Perform load testing
- [ ] Document incident response procedures
- [ ] Train operations team
- [ ] Set up on-call rotation

For production deployment assistance, contact the maintainers or refer to the GitHub repository.
