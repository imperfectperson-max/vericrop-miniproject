# 🍎 VeriCrop - Supply Chain Intelligence Platform
https://img.shields.io/badge/version-2.0.0-blue.svg
https://img.shields.io/badge/Java-17-orange.svg
https://img.shields.io/badge/Python-3.11-green.svg
https://img.shields.io/badge/AI-Enabled-red.svg
https://img.shields.io/badge/Blockchain-Secure-yellow.svg

## End-to-End Supply Chain Management with AI-Powered Quality Control and Blockchain Transparency
## 🎯 Key Features
🤖 AI Quality Classification - 99.06% accurate fruit quality detection using ResNet18

⛓️ Blockchain Security - Immutable supply chain records

📊 Four Interactive Dashboards - Farm, Logistics, Consumer, Analytics

🌡️ Real-time Monitoring - Temperature, humidity, and quality tracking

📱 Consumer Verification - QR code scanning for product authenticity

📈 Advanced Analytics - Performance metrics and predictive insights

## 🚀 Quick Start
Prerequisites
Java 17+

Python 3.11

Docker
1. Start ML Service
cd docker/ml-service
docker build -t vericrop-ml .
docker run -d -p 8000:8000 --name vericrop-ml-service vericrop-ml

2.  Run Core Application
cd vericrop-core
./gradlew run

3. Access the Application
Open the JavaFX application

Default URL: http://localhost:8000 (ML Service)

Four dashboards will be available

## 🏗️ System Architecture
┌─────────────────────────────────────────────────────────────────┐
│                      JavaFX Client Application                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────┐ │
│  │   Farm      │  │ Logistics   │  │  Consumer   │  │ Analytics│ │
│  │  Dashboard  │  │  Dashboard  │  │   Portal    │  │ Dashboard│ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────┘ │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    VeriCrop Core (Java)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ ML Service  │  │ Blockchain  │  │ Database    │              │
│  │  Client     │  │   Service   │  │   Layer     │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Python ML Service (FastAPI)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  ResNet18   │  │   REST API  │  │ SQLite      │              │
│  │   Model     │  │  Endpoints  │  │  Database   │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└─────────────────────────────────────────────────────────────────┘

# 📋 Core Components
## 🎛️ Dashboards
1. Farm Management Dashboard
Batch Creation: Upload fruit images and create quality-assessed batches

Real-time Analytics: Quality distribution, performance metrics

Blockchain Integration: Immutable batch records

Quality Assessment: AI-powered fruit classification (Fresh/Low Quality/Rotten)

2. Logistics Tracking Dashboard
Real-time Monitoring: Temperature, humidity, location tracking

Route Optimization: ETA predictions and route mapping

Condition Alerts: Temperature breach notifications

Quality Degradation Tracking: Real-time quality monitoring during transit

3. Consumer Verification Portal
QR Code Scanning: Instant product authentication

Product Journey: Complete supply chain transparency

Quality Metrics: Color consistency, size uniformity, defect density

Shelf Life Prediction: Remaining freshness estimation

4. Analytics & Reporting Dashboard
KPI Monitoring: Total batches, average quality, spoilage rates

Supplier Performance: Quality ratings and reliability scores

Trend Analysis: Quality trends over time

Temperature Compliance: Environmental condition monitoring

## 🔧 Technical Components
Backend Services
ML Service: FastAPI with ONNX ResNet18 model (99.06% accuracy)

Blockchain: Custom implementation with SHA-256 hashing

Database: SQLite with connection pooling

REST API: Comprehensive endpoints for all dashboards

Frontend Application
JavaFX: Modern, responsive user interface

Charts & Visualizations: Real-time data representation

Multi-threading: Non-blocking UI with background processing

Configuration Management: Typesafe config with environment support

# 🛠️ API Endpoints
## ML Service Endpoints
POST /predict           # Fruit quality prediction
GET  /dashboard/farm    # Farm dashboard data
GET  /dashboard/analytics # Analytics data
GET  /health           # Service health check
GET  /batches          # Batch management

Example Usage
# Test prediction
curl -X POST -F "file=@fruit.jpg" http://localhost:8000/predict

# Check health
curl http://localhost:8000/health

# Get analytics
curl http://localhost:8000/dashboard/analytics

# 📊 Machine Learning Model
## Model Specifications
Architecture: ResNet18

Accuracy: 99.06% validation accuracy

Classes: Fresh, Low Quality, Rotten

Input Size: 224x224 RGB images

Framework: ONNX Runtime

Training Data: 2,671 samples (balanced)

Quality Metrics
Color Consistency: 92% average

Size Uniformity: 87% average

Defect Density: 3% average

Confidence Threshold: 85% for Prime grade

# ⛓️ Blockchain Implementation
## Features
Immutable Records: SHA-256 hashing for data integrity

Supply Chain Tracking: Complete product journey

Smart Transactions: CREATE_BATCH, TRANSFER, QUALITY_CHECK

Validation: Chain integrity verification

Persistent Storage: JSON-based chain files

Transaction Types
CREATE_BATCH     // Farmer creates new batch
QUALITY_CHECK    // Quality assessment recording  
TRANSFER         // Ownership transfer between parties
DELIVERY         // Final delivery confirmation

# 🗃️ Database Schema
Core Tables
batches              # Batch information and quality scores
supply_chain_events  # Timeline of supply chain events
analytics            # Performance metrics and KPI data

# 🧪 Testing
# Run all tests
./gradlew test

# Run specific test suite
./gradlew test --tests "*.BlockchainTest"
./gradlew test --tests "*.MLServiceClientTest"

Test Coverage
Unit tests for blockchain operations

Integration tests for ML service communication

Database operation tests

Service layer validation

📦 Deployment
Docker Deployment
# ML Service
FROM python:3.11-slim
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0"]

# Java Application  
java -jar vericrop-core-1.0.0.jar
Gradle 7+

Installation & Running
# ML Service
FROM python:3.11-slim
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0"]

# Java Application  
java -jar vericrop-core-1.0.0.jar
