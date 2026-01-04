# 🌾 VeriCrop GUI

> JavaFX desktop application for agricultural supply chain management with AI-powered quality assessment

---

## ⚡ Quick Start (2 Minutes)

### Option 1: Demo Mode (No Setup!)

```bash
# Unix/Linux/Mac
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run

# Windows PowerShell
$env:VERICROP_LOAD_DEMO="true"
./gradlew :vericrop-gui:run
```

✅ No Docker, PostgreSQL, Kafka, or ML Service needed!

### Option 2: Full Stack

```bash
# 1. Start services
docker-compose up -d postgres kafka ml-service

# 2. Configure environment
cp .env.example .env

# 3. Run application
./gradlew :vericrop-gui:run
```

---

## 📦 What You Get

| Feature | Description |
|---------|-------------|
| 🤖 **AI Quality Assessment** | Upload images for quality predictions |
| 🔳 **QR Code Generation** | Scannable codes for product tracking |
| 🚚 **Delivery Simulator** | Real-time route and temperature tracking |
| 📊 **Analytics Dashboard** | KPI monitoring and trend analysis |
| 🔐 **User Authentication** | Secure login with role-based access |
| 💬 **Messaging System** | In-app communication |
| 📈 **Reports** | CSV/JSON/PDF export |

---

## 🏗️ Architecture

<details>
<summary>View Architecture Diagram</summary>

```
┌─────────────────────────────────────────┐
│  UI Layer (JavaFX Controllers)          │
│  - LoginController, ProducerController  │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Service Layer (Business Logic)         │
│  - BatchService, AnalyticsService       │
└──────┬──────────┬──────────┬────────────┘
       │          │          │
   ┌───▼───┐  ┌───▼───┐  ┌──▼──────┐
   │ML Svc │  │ Kafka │  │Postgres │
   └───────┘  └───────┘  └─────────┘
```

</details>

---

## 🎯 Key Features


### 🔳 QR Code Generation

Generate scannable QR codes for product traceability.

**How to Use:**
1. Create a batch with product details
2. Click **"🔳 Generate Product QR Code"** button
3. QR code saved to `generated_qr/product_{batchId}.png`

**Output:** RGB PNG images (300x300px)

<details>
<summary>📖 QR Code Payload Format</summary>

```json
{
  "type": "product",
  "id": "BATCH-1234567890",
  "farmer": "farmer123",
  "verifyUrl": "https://vericrop.app/verify?id=BATCH-1234567890"
}
```

</details>

### 🚚 Delivery Simulator

Real-time delivery simulation for supply chain testing.

**How to Use:**
1. Create a batch
2. Click **"▶ Start Simulation"** button
3. Watch the route animation in Logistics screen

**Features:**
- ✅ Simulated GPS coordinates
- ✅ Temperature & humidity readings
- ✅ Real-time alerts for violations
- ✅ 10-second update intervals
- ✅ 10 waypoints per route

<details>
<summary>🔧 Advanced: Async Simulation API</summary>


Start simulations asynchronously via REST API (non-blocking).

**Start Async Simulation:**
```bash
curl -X POST http://localhost:8080/api/simulation/start-async \
  -H "Content-Type: application/json" \
  -d '{"batch_id": "BATCH_001", "farmer_id": "FARMER_001"}'
```

**Poll Status:**
```bash
curl http://localhost:8080/api/simulation/{simulation_id}/status
```

**Response:**
```json
{
  "simulation_id": "SIM_123",
  "status": "RUNNING",
  "progress": 45.5,
  "current_location": "In transit"
}
```

</details>

### 📊 Reports Generation

Generate CSV, JSON, and PDF reports.

**Available Reports:**
- Journey Reports (waypoints, temperature, humidity)
- Quality Reports (batch metrics, scores)
- Analytics Reports (custom data and columns)

**Output Location:** `generated_reports/` directory

<details>
<summary>📖 Usage Example</summary>

```java
// Generate journey report
Path report = ReportGenerator.generateJourneyReportCSV(shipmentId, waypoints);

// Generate quality report
Path report = ReportGenerator.generateQualityReportCSV(batchId, qualityData);
```

</details>

### 🔔 Real-Time Alerts

AlertService provides real-time notifications.

**Alert Types:** INFO, WARNING, ERROR, CRITICAL

**Features:**
- Observable alerts for UI binding
- Alert listener pattern
- Acknowledgement tracking
- Filtering by severity

---

## ⚙️ Configuration

<details>
<summary>View Environment Variables</summary>


### Environment Variables

#### Database (PostgreSQL)
```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=vericrop
POSTGRES_USER=vericrop
POSTGRES_PASSWORD=vericrop123
DB_POOL_SIZE=10
```

#### Kafka Messaging
```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=true
KAFKA_ACKS=all
KAFKA_RETRIES=3
```

#### ML Service (FastAPI)
```bash
ML_SERVICE_URL=http://localhost:8000
ML_SERVICE_TIMEOUT=30000
VERICROP_LOAD_DEMO=true  # Use demo mode if model unavailable
```

#### Application Settings
```bash
VERICROP_MODE=dev         # dev or prod
SERVER_PORT=8080
LOG_LEVEL=INFO
QUALITY_PASS_THRESHOLD=0.7
```

</details>

---

## 📦 Components

<details>
<summary>🔧 Services Layer</summary>


**BatchService:** Manages batch creation, updates, and queries.

```java
BatchRecord batch = new BatchRecord.Builder()
    .name("Apple Batch 001")
    .farmer("John Farmer")
    .productType("Apple")
    .build();
BatchRecord created = batchService.createBatch(batch, imageFile);
```

**AnalyticsService:** Provides analytics and dashboard data.

**KafkaMessagingService:** Publishes events to Kafka.

**AuthenticationService:** Manages user authentication and sessions.

</details>

<details>
<summary>🔌 Clients Layer</summary>

**MLClientService:** HTTP client for FastAPI ML service.

Endpoints: `health()`, `createBatch()`, `listBatches()`, `predictImage()`, `getDashboardFarm()`

</details>

<details>
<summary>💾 Persistence Layer</summary>

**PostgresBatchRepository:** JDBC repository with HikariCP pooling.

Methods: `create()`, `findByBatchId()`, `findAll()`, `update()`, `delete()`

**Database Schema:**
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
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

</details>

---

## 🧪 Testing

```bash
# Run all tests
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew :vericrop-gui:test --tests BatchServiceTest

# Integration tests
docker-compose -f docker-compose.test.yml up -d
./gradlew :vericrop-gui:integrationTest
```

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| App won't start | Check Java version (`java -version`), verify services running |
| Database connection failed | `docker-compose ps postgres`, check `.env` settings |
| Kafka connection failed | `docker-compose ps kafka`, set `KAFKA_ENABLED=false` to test |
| ML Service unavailable | `curl http://localhost:8000/health`, set `VERICROP_LOAD_DEMO=true` |

---

## 📚 Additional Documentation

- [Main README](../README.md) - Project overview
- [Deployment Guide](../DEPLOYMENT.md) - Production deployment
- [ML Service API](../docker/ml-service/README.md) - ML service docs
- [Database Schema](src/main/resources/db/schema.sql) - Database structure

---

**Made with ❤️ for sustainable agriculture**
