# VeriCrop Production Deployment Guide

This guide provides step-by-step instructions for deploying VeriCrop to production environments.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Deployment Options](#deployment-options)
- [Docker Compose Deployment](#docker-compose-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Post-Deployment Validation](#post-deployment-validation)
- [Monitoring Setup](#monitoring-setup)
- [Backup & Disaster Recovery](#backup--disaster-recovery)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### System Requirements

**Minimum (Development/Staging):**
- CPU: 4 cores
- RAM: 8 GB
- Disk: 50 GB
- OS: Linux (Ubuntu 20.04+, RHEL 8+, or compatible)

**Recommended (Production):**
- CPU: 8+ cores
- RAM: 16+ GB
- Disk: 100+ GB (SSD preferred)
- OS: Linux (Ubuntu 22.04 LTS recommended)

### Software Requirements

- **Docker**: 20.10+ with Docker Compose v2.0+
- **Kubernetes** (for K8s deployment): 1.24+
- **kubectl**: 1.24+ (for K8s deployment)
- **Helm** (optional): 3.0+ (for K8s deployment)

### Network Requirements

- **Inbound Ports**:
  - 80/443 (HTTP/HTTPS) - Application access
  - 8080 (TCP) - VeriCrop GUI API
  - 8000 (TCP) - ML Service
  
- **Internal Ports** (should NOT be exposed to internet):
  - 5432 (TCP) - PostgreSQL
  - 9092 (TCP) - Kafka
  - 2181 (TCP) - Zookeeper
  - 8082 (TCP) - Airflow Web UI

## Deployment Options

VeriCrop supports three deployment architectures:

1. **Docker Compose** - Simple, single-server deployment (recommended for staging)
2. **Kubernetes** - Highly available, multi-server deployment (recommended for production)
3. **Hybrid** - External managed services (e.g., AWS RDS, Amazon MSK) + containerized app

## Docker Compose Deployment

### Step 1: Prepare the Server

```bash
# Update system
sudo apt-get update && sudo apt-get upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Logout and login to apply group changes
exit
```

### Step 2: Clone Repository

```bash
# Clone the repository
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject

# Checkout the desired version/tag
git checkout tags/v1.0.0  # Replace with actual version
```

### Step 3: Configure Environment

```bash
# Copy environment template
cp .env.template .env

# Edit environment variables
nano .env
```

**Critical variables to change:**
```bash
# Database
POSTGRES_PASSWORD=YOUR_SECURE_PASSWORD_HERE

# Airflow
AIRFLOW_ADMIN_PASSWORD=YOUR_AIRFLOW_PASSWORD_HERE
AIRFLOW_DB_PASSWORD=YOUR_AIRFLOW_DB_PASSWORD_HERE
AIRFLOW__WEBSERVER__SECRET_KEY=YOUR_LONG_RANDOM_SECRET_HERE

# JWT
JWT_SECRET_KEY=YOUR_JWT_SECRET_HERE

# Email (if using)
SMTP_PASSWORD=YOUR_EMAIL_PASSWORD_HERE
```

**Generate secure passwords:**
```bash
# Generate a 32-character random password
openssl rand -base64 32

# Or use pwgen
pwgen -s 32 1
```

### Step 4: Build Docker Images

```bash
# Build Java services
./gradlew clean build

# Build Docker images
docker-compose -f docker-compose.prod.yml build

# Verify images
docker images | grep vericrop
```

### Step 5: Initialize Database

```bash
# Start only PostgreSQL first
docker-compose -f docker-compose.prod.yml up -d postgres

# Wait for PostgreSQL to be ready (check logs)
docker-compose -f docker-compose.prod.yml logs -f postgres

# Database migrations will run automatically when GUI service starts
```

### Step 6: Start All Services

```bash
# Start all services in detached mode
docker-compose -f docker-compose.prod.yml up -d

# Monitor startup
docker-compose -f docker-compose.prod.yml logs -f

# Check service status
docker-compose -f docker-compose.prod.yml ps
```

### Step 7: Verify Deployment

```bash
# Check all services are healthy
docker-compose -f docker-compose.prod.yml ps

# Test ML Service
curl http://localhost:8000/health
# Expected: {"status":"healthy"}

# Test GUI API health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Test PostgreSQL connection
docker exec -it vericrop-postgres-prod psql -U vericrop -d vericrop -c "SELECT version();"

# Test Kafka
docker exec -it vericrop-kafka-prod kafka-topics --list --bootstrap-server localhost:9092
```

### Step 8: Configure Reverse Proxy (Nginx)

```nginx
# /etc/nginx/sites-available/vericrop
server {
    listen 80;
    server_name vericrop.example.com;
    
    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name vericrop.example.com;
    
    # SSL certificates (use Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/vericrop.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/vericrop.example.com/privkey.pem;
    
    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # Proxy to VeriCrop GUI
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket support (if needed)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
    
    # Proxy to Airflow (optional, restrict access)
    location /airflow/ {
        proxy_pass http://localhost:8082/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        
        # Restrict access to admin IPs only
        allow 203.0.113.0/24;  # Replace with your admin IP range
        deny all;
    }
    
    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
}
```

**Enable the site:**
```bash
sudo ln -s /etc/nginx/sites-available/vericrop /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

**Get SSL certificate with Let's Encrypt:**
```bash
sudo apt-get install certbot python3-certbot-nginx
sudo certbot --nginx -d vericrop.example.com
```

## Kubernetes Deployment

### Step 1: Prepare Kubernetes Cluster

**Option A: Managed Kubernetes (Recommended)**
- AWS: Amazon EKS
- Azure: Azure Kubernetes Service (AKS)
- Google Cloud: Google Kubernetes Engine (GKE)

**Option B: Self-hosted**
```bash
# Install kubeadm, kubelet, kubectl (Ubuntu)
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update
sudo apt-get install -y kubelet kubeadm kubectl
```

### Step 2: Configure kubectl

```bash
# Verify cluster access
kubectl cluster-info
kubectl get nodes
```

### Step 3: Create Namespace

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: vericrop
  labels:
    name: vericrop
    environment: production
EOF
```

### Step 4: Create Secrets

**DO NOT store secrets in Git!** Use one of these methods:

**Method A: Kubernetes Secrets (Basic)**
```bash
# Create secrets from command line
kubectl create secret generic vericrop-gui-secrets \
  --from-literal=POSTGRES_USER=vericrop \
  --from-literal=POSTGRES_PASSWORD=YOUR_SECURE_PASSWORD \
  --from-literal=JWT_SECRET_KEY=YOUR_JWT_SECRET \
  -n vericrop
```

**Method B: External Secrets Operator (Recommended)**
```yaml
# external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: vericrop-secrets
  namespace: vericrop
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager  # or vault, gcpsm, etc.
    kind: SecretStore
  target:
    name: vericrop-gui-secrets
  data:
  - secretKey: POSTGRES_PASSWORD
    remoteRef:
      key: vericrop/production/db/password
```

### Step 5: Create Persistent Volumes

```yaml
# pv.yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: vericrop-ledger-pv
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  storageClassName: standard
  nfs:  # Or use cloud provider storage (EBS, Azure Disk, etc.)
    path: /exports/vericrop/ledger
    server: nfs-server.example.com
```

### Step 6: Deploy PostgreSQL (StatefulSet)

```bash
# Using Helm (recommended)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install postgres bitnami/postgresql \
  --namespace vericrop \
  --set auth.username=vericrop \
  --set auth.password=YOUR_SECURE_PASSWORD \
  --set auth.database=vericrop \
  --set primary.persistence.size=20Gi \
  --set resources.requests.memory=1Gi \
  --set resources.requests.cpu=500m
```

### Step 7: Deploy Kafka (StatefulSet)

```bash
# Using Strimzi Kafka Operator
helm repo add strimzi https://strimzi.io/charts/
helm install kafka strimzi/strimzi-kafka-operator \
  --namespace vericrop

# Create Kafka cluster
kubectl apply -f - <<EOF
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: vericrop-kafka
  namespace: vericrop
spec:
  kafka:
    version: 3.5.0
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: persistent-claim
      size: 20Gi
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 10Gi
EOF
```

### Step 8: Deploy VeriCrop Services

```bash
# Update image references in k8s/vericrop-gui-deployment.yaml
# Replace 'vericrop-gui:1.0.0' with your registry URL:
# e.g., 'myregistry.azurecr.io/vericrop-gui:1.0.0'

# Apply the deployment
kubectl apply -f k8s/vericrop-gui-deployment.yaml

# Verify deployment
kubectl get deployments -n vericrop
kubectl get pods -n vericrop
kubectl get services -n vericrop

# Check pod logs
kubectl logs -f deployment/vericrop-gui -n vericrop
```

### Step 9: Configure Ingress

```yaml
# ingress.yaml (using cert-manager for TLS)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: vericrop-ingress
  namespace: vericrop
  annotations:
    kubernetes.io/ingress.class: "nginx"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/rate-limit: "100"
spec:
  tls:
  - hosts:
    - vericrop.example.com
    secretName: vericrop-tls
  rules:
  - host: vericrop.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: vericrop-gui-service
            port:
              number: 80
```

```bash
kubectl apply -f ingress.yaml
```

### Step 10: Verify Kubernetes Deployment

```bash
# Check all resources
kubectl get all -n vericrop

# Check ingress
kubectl get ingress -n vericrop
kubectl describe ingress vericrop-ingress -n vericrop

# Check HPA
kubectl get hpa -n vericrop

# Test application
curl https://vericrop.example.com/actuator/health
```

## Post-Deployment Validation

### Health Checks

```bash
# GUI Service
curl https://vericrop.example.com/actuator/health
curl https://vericrop.example.com/actuator/health/liveness
curl https://vericrop.example.com/actuator/health/readiness

# ML Service
curl http://ml-service:8000/health

# PostgreSQL
psql -h postgres-host -U vericrop -d vericrop -c "SELECT 1;"

# Kafka
kafka-topics.sh --list --bootstrap-server kafka:9092
```

### Smoke Tests

```bash
# 1. Create a test batch
curl -X POST https://vericrop.example.com/api/batches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"name":"Test Batch","farmer":"Test Farmer","productType":"Apple","quantity":100}'

# 2. Verify blockchain integrity
curl https://vericrop.example.com/api/blockchain/validate

# 3. Check analytics endpoint
curl https://vericrop.example.com/api/analytics/dashboard

# 4. Test ML prediction
curl -X POST http://ml-service:8000/predict \
  -F "file=@test-image.jpg"
```

## Monitoring Setup

### Prometheus & Grafana

**Install Prometheus Operator:**
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace
```

**Create ServiceMonitor for VeriCrop:**
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: vericrop-gui-monitor
  namespace: vericrop
spec:
  selector:
    matchLabels:
      app: vericrop-gui
  endpoints:
  - port: metrics
    path: /actuator/prometheus
    interval: 30s
```

**Access Grafana:**
```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
# Open http://localhost:3000 (admin/prom-operator)
```

### Centralized Logging (ELK Stack)

**Install Elasticsearch, Logstash, Kibana:**
```bash
helm repo add elastic https://helm.elastic.co
helm install elasticsearch elastic/elasticsearch --namespace logging --create-namespace
helm install kibana elastic/kibana --namespace logging
helm install filebeat elastic/filebeat --namespace logging
```

## Backup & Disaster Recovery

### Database Backups

**Automated Backup Script:**
```bash
#!/bin/bash
# backup-postgres.sh

BACKUP_DIR="/backups/postgres"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/vericrop_$DATE.sql.gz"

# Create backup
docker exec vericrop-postgres-prod pg_dump -U vericrop vericrop | gzip > "$BACKUP_FILE"

# Upload to S3 (optional)
aws s3 cp "$BACKUP_FILE" s3://vericrop-backups/postgres/

# Delete backups older than 30 days
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete

echo "Backup completed: $BACKUP_FILE"
```

**Schedule with cron:**
```bash
# Run daily at 2 AM
0 2 * * * /usr/local/bin/backup-postgres.sh >> /var/log/vericrop-backup.log 2>&1
```

### Ledger Backups

```bash
#!/bin/bash
# backup-ledger.sh

LEDGER_DIR="/app/ledger"
BACKUP_DIR="/backups/ledger"
DATE=$(date +%Y%m%d_%H%M%S)

# Create tarball
tar -czf "$BACKUP_DIR/ledger_$DATE.tar.gz" "$LEDGER_DIR"

# Upload to S3
aws s3 cp "$BACKUP_DIR/ledger_$DATE.tar.gz" s3://vericrop-backups/ledger/

# Delete old backups
find "$BACKUP_DIR" -name "*.tar.gz" -mtime +90 -delete
```

### Disaster Recovery Procedure

**1. Database Restoration:**
```bash
# Stop application
docker-compose -f docker-compose.prod.yml stop vericrop-gui

# Restore database
gunzip < vericrop_20240115_020000.sql.gz | \
  docker exec -i vericrop-postgres-prod psql -U vericrop vericrop

# Restart application
docker-compose -f docker-compose.prod.yml start vericrop-gui
```

**2. Ledger Restoration:**
```bash
# Extract ledger backup
tar -xzf ledger_20240115_020000.tar.gz -C /app/ledger

# Verify blockchain integrity
curl https://vericrop.example.com/api/blockchain/validate
```

## Troubleshooting

### Common Issues

**1. Service Won't Start**
```bash
# Check logs
docker-compose -f docker-compose.prod.yml logs service-name

# Check resource usage
docker stats

# Check disk space
df -h
```

**2. Database Connection Errors**
```bash
# Verify PostgreSQL is running
docker exec -it vericrop-postgres-prod pg_isready -U vericrop

# Check connection from application
docker exec -it vericrop-gui-container psql -h postgres -U vericrop -d vericrop
```

**3. Kafka Connection Issues**
```bash
# Check Kafka is healthy
docker exec -it vericrop-kafka-prod kafka-broker-api-versions --bootstrap-server localhost:9092

# List topics
docker exec -it vericrop-kafka-prod kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
docker exec -it vericrop-kafka-prod kafka-consumer-groups --list --bootstrap-server localhost:9092
```

**4. High Memory Usage**
```bash
# Check Java heap usage
docker exec vericrop-gui-container jcmd 1 VM.native_memory summary

# Adjust JVM memory settings in .env
JAVA_OPTS=-Xms512m -Xmx2g
```

**5. Slow ML Predictions**
```bash
# Check ML service logs
docker logs vericrop-ml-service

# Increase workers
docker-compose -f docker-compose.prod.yml exec ml-service \
  sh -c "UVICORN_WORKERS=8 uvicorn app:app --host 0.0.0.0 --port 8000"
```

### Getting Help

- **Documentation**: Check README.md and service-specific docs
- **Logs**: Always check logs first (`docker-compose logs` or `kubectl logs`)
- **Health Endpoints**: Use `/actuator/health` endpoints for diagnostics
- **GitHub Issues**: https://github.com/imperfectperson-max/vericrop-miniproject/issues

---

**Version**: 1.0.0  
**Last Updated**: 2024-01-15  
**Maintained By**: VeriCrop DevOps Team
