# 🍎 VeriCrop - Supply Chain Intelligence Platform

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Python](https://img.shields.io/badge/Python-3.11-green.svg)
![AI Enabled](https://img.shields.io/badge/AI-Enabled-red.svg)
![Blockchain](https://img.shields.io/badge/Blockchain-Secure-yellow.svg)
![License](https://img.shields.io/badge/license-TBD-lightgrey.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)

> End-to-End Supply Chain Management with AI-Powered Quality Control and Blockchain Transparency

## 📑 Table of Contents

- [Background and Motivation](#background-and-motivation)
- [Key Features](#key-features)
- [Technologies and Dependencies](#technologies-and-dependencies)
- [System Architecture](#system-architecture)
- [**Building and Docker** (see BUILD.md)](#building-and-docker)
- [Installation](#installation)
- [Usage](#usage)
- [Running Tests](#running-tests)
- [Core Components](#core-components)
- [API Endpoints](#api-endpoints)
- [Machine Learning Model](#machine-learning-model)
- [Blockchain Implementation](#blockchain-implementation)
- [Database Schema](#database-schema)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [License](#license)
- [Maintainers and Contact](#maintainers-and-contact)
- [Acknowledgements](#acknowledgements)

## 🌱 Background and Motivation

VeriCrop addresses critical challenges in agricultural supply chain management by combining artificial intelligence, blockchain technology, and real-time monitoring to ensure food quality and transparency. The platform empowers farmers, logistics providers, and consumers with tools to track produce quality from farm to table, reducing food waste and building trust in the supply chain.

The project was developed as a comprehensive mini-project demonstrating the integration of modern technologies including:
- AI-powered quality assessment for agricultural products
- Blockchain-based immutable record keeping
- Real-time IoT monitoring and analytics
- Multi-stakeholder dashboard interfaces
## 🎯 Key Features

- 🤖 **AI Quality Classification** - 99.06% accurate fruit quality detection using ResNet18
- ⛓️ **Blockchain Security** - Immutable supply chain records with SHA-256 hashing
- 📊 **Four Interactive Dashboards** - Farm, Logistics, Consumer, and Analytics interfaces
- 🌡️ **Real-time Monitoring** - Temperature, humidity, and quality tracking throughout the supply chain
- 📱 **Consumer Verification** - QR code scanning for instant product authenticity verification
- 📈 **Advanced Analytics** - Performance metrics, KPI monitoring, and predictive insights
- 🔄 **Complete Traceability** - End-to-end tracking from producer to consumer
- 🚨 **Automated Alerts** - Temperature breach notifications and quality degradation warnings

## 💻 Technologies and Dependencies

### Backend Technologies
- **Java 17+** - Core application backend and business logic
- **JavaFX** - Desktop GUI framework for interactive dashboards
- **Gradle 7+** - Build automation and dependency management
- **SQLite** - Lightweight database with connection pooling
- **Custom Blockchain** - SHA-256 based immutable ledger implementation

### Machine Learning Service
- **Python 3.11** - ML service runtime environment
- **FastAPI** - High-performance REST API framework
- **ONNX Runtime** - Optimized ML model inference
- **ResNet18** - Pre-trained computer vision model (transfer learning)
- **PyTorch/TensorFlow** - Model training frameworks
- **Pillow & NumPy** - Image processing libraries

### Development & Testing
- **JUnit 5** - Java unit testing framework
- **pytest** - Python testing framework
- **Docker** - Containerization for ML service
- **Docker Compose** - Multi-container orchestration

## 🔨 Building and Docker

For detailed instructions on building the project with Gradle and creating Docker images, see **[BUILD.md](BUILD.md)**.

**Quick Start:**
```bash
# Build all Java modules
./gradlew clean build

# Build Docker images
./gradlew build -x test
docker compose -f docker-compose-java.yml build

# Run with Docker Compose
docker compose -f docker-compose-java.yml up
```

### Key Dependencies
**Java (build.gradle):**
- JavaFX 17+
- JUnit Jupiter 5.9.3

**Python (requirements.txt):**
- fastapi
- uvicorn[standard]
- pillow
- numpy
- onnx
- onnxruntime
- pytest (testing)

## 🚀 Installation

### Prerequisites

Before installing VeriCrop, ensure you have the following installed:

- **Java Development Kit (JDK) 17 or higher**
  ```bash
  java -version  # Should show version 17 or higher
  ```

- **Python 3.11**
  ```bash
  python3 --version  # Should show version 3.11.x
  ```

- **Docker and Docker Compose** (for ML service containerization)
  ```bash
  docker --version
  docker-compose --version
  ```

- **Git** (for cloning the repository)
  ```bash
  git --version
  ```

### Step-by-Step Installation

#### 1. Clone the Repository

```bash
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject
```

#### 2. Set Up the ML Service (Python)

**Option A: Using Docker (Recommended)**

```bash
cd docker/ml-service
docker build -t vericrop-ml .
docker run -d -p 8000:8000 --name vericrop-ml-service vericrop-ml
```

**Option B: Local Python Environment**

```bash
cd docker/ml-service

# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run the ML service
uvicorn app:app --host 0.0.0.0 --port 8000
```

#### 3. Build the Java Application

```bash
# Return to project root
cd /path/to/vericrop-miniproject

# Build the project
./gradlew build

# Or on Windows:
gradlew.bat build
```

#### 4. Verify Installation

Check that the ML service is running:
```bash
curl http://localhost:8000/health
```

Expected response: `{"status":"healthy"}`

## 🚀 Usage

### Starting the Application

VeriCrop provides two interfaces:
1. **JavaFX Desktop Application** - Interactive dashboards for farm, logistics, consumer, and analytics
2. **REST API** - RESTful endpoints for quality evaluation and shipment management

#### Option 1: REST API (Spring Boot)

```bash
# Build and run the REST API
./gradlew :vericrop-gui:bootRun

# Or run the JAR directly
java -jar vericrop-gui/build/libs/vericrop-gui-1.0.0.jar
```

The API will start on `http://localhost:8080`

**Quick Test:**
```bash
# Health check
curl http://localhost:8080/api/health

# Evaluate fruit quality
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH_001",
    "product_type": "apple",
    "farmer_id": "farmer_001"
  }'
```

#### Option 2: JavaFX Desktop Application

```bash
# From project root
./gradlew :vericrop-gui:run

# Or on Windows:
gradlew.bat :vericrop-gui:run
```

The JavaFX desktop application will launch with four available dashboards.

#### Option 3: ML Service (Optional)

If you want to use the external ML service:

Using Docker:
```bash
cd docker/ml-service
docker start vericrop-ml-service

# Or build and run if first time:
docker build -t vericrop-ml .
docker run -d -p 8000:8000 --name vericrop-ml-service vericrop-ml
```

Verify the service is healthy:
```bash
curl http://localhost:8000/health
# Expected: {"status":"healthy"}
```

### Using the Dashboards

#### Farm Management Dashboard
- Upload fruit images for quality assessment
- Create batches with AI-powered classification
- View quality distribution and analytics
- Generate blockchain records for batches

#### Logistics Tracking Dashboard
- Monitor shipments in real-time
- Track temperature and humidity
- View route information and ETAs
- Receive condition alerts

#### Consumer Verification Portal
- Scan QR codes to verify product authenticity
- View complete supply chain journey
- Check quality metrics and shelf life predictions

#### Analytics & Reporting Dashboard
- Monitor KPIs and performance metrics
- Analyze supplier performance
- View quality trends over time
- Check temperature compliance

### API Usage Examples

#### VeriCrop REST API (Port 8080)

```bash
# Health check
curl http://localhost:8080/api/health

# Evaluate fruit quality
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH_001",
    "product_type": "apple",
    "farmer_id": "farmer_001"
  }'

# Get shipment record
curl http://localhost:8080/api/shipments/550e8400-e29b-41d4-a716-446655440000

# Get all shipments for a batch
curl http://localhost:8080/api/shipments?batch_id=BATCH_001
```

#### ML Service API (Port 8000 - Optional)

```bash
# Test fruit quality prediction
curl -X POST -F "file=@examples/sample.jpg" http://localhost:8000/predict

# Get farm dashboard data
curl http://localhost:8000/dashboard/farm

# Get analytics data
curl http://localhost:8000/dashboard/analytics

# Get batch information
curl http://localhost:8000/batches
```

**📚 For comprehensive API documentation, examples, and Kafka integration guide, see [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md)**

### Configuration

The application can be configured via `vericrop-gui/src/main/resources/application.yml`:

```yaml
server:
  port: 8080  # REST API port

kafka:
  enabled: false  # Set to true when Kafka is available
  
spring:
  kafka:
    bootstrap-servers: localhost:9092

ledger:
  path: ledger  # Path to store shipment records

quality:
  evaluation:
    pass-threshold: 0.7
```

**Note:** See [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) for detailed configuration options.

## 🧪 Running Tests

### Java Tests

Run all Java tests using Gradle:

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :vericrop-core:test
./gradlew :vericrop-gui:test

# Run specific test class
./gradlew test --tests "*.BlockchainTest"
./gradlew test --tests "*.MLServiceClientTest"

# Run tests with detailed output
./gradlew test --info
```

View test reports:
```bash
# Reports are generated at:
# build/reports/tests/test/index.html
```

### Python Tests

Run Python tests for the ML service:

```bash
cd docker/ml-service

# Install test dependencies (if not already installed)
pip install -r requirements-test.txt

# Run all tests
pytest

# Run with coverage
pytest --cov=. --cov-report=html

# Run specific test file
pytest tests/test_predict.py
pytest tests/test_health.py

# Run with verbose output
pytest -v

# Run with detailed output showing print statements
pytest -s
```

View coverage reports:
```bash
# HTML coverage report generated at:
# htmlcov/index.html
```

### Testing Checklist

The test suite covers:
- ✅ Unit tests for blockchain operations
- ✅ Integration tests for ML service communication
- ✅ Database operation tests
- ✅ Service layer validation
- ✅ API endpoint functionality
- ✅ Model inference accuracy

**Note:** Currently, some test files may be placeholders. Contributors should add comprehensive tests as features are implemented.

### Airflow DAG Testing

VeriCrop includes an Airflow DAG for end-to-end quality evaluation pipeline:

```bash
# Install Airflow and dependencies
pip install apache-airflow kafka-python

# Initialize Airflow
airflow db init

# Start Airflow webserver
airflow webserver --port 8081

# Start Airflow scheduler (in another terminal)
airflow scheduler

# Access UI at http://localhost:8081
# Enable and trigger: vericrop_evaluation_pipeline
```

The DAG performs:
1. Produces evaluation requests to Kafka
2. Calls REST API for evaluation
3. Verifies ledger records
4. Generates pipeline summary

### Running Linters

**Java:**
```bash
# Build with warnings
./gradlew build --warning-mode all
```

**Python:**
```bash
# Check Airflow DAG syntax
python airflow/dags/vericrop_dag.py
```

## 🏗️ System Architecture
```bash
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
```
## 📋 Core Components

## 🎛️ Dashboards

### 1. Farm Management Dashboard
- **Batch Creation**: Upload fruit images and create quality-assessed batches
- **Real-time Analytics**: Quality distribution, performance metrics
- **Blockchain Integration**: Immutable batch records
- **Quality Assessment**: AI-powered fruit classification (Fresh/Low Quality/Rotten)

### 2. Logistics Tracking Dashboard
- **Real-time Monitoring**: Temperature, humidity, location tracking
- **Route Optimization**: ETA predictions and route mapping
- **Condition Alerts**: Temperature breach notifications
- **Quality Degradation Tracking**: Real-time quality monitoring during transit

### 3. Consumer Verification Portal
- **QR Code Scanning**: Instant product authentication
- **Product Journey**: Complete supply chain transparency
- **Quality Metrics**: Color consistency, size uniformity, defect density
- **Shelf Life Prediction**: Remaining freshness estimation

### 4. Analytics & Reporting Dashboard
- **KPI Monitoring**: Total batches, average quality, spoilage rates
- **Supplier Performance**: Quality ratings and reliability scores
- **Trend Analysis**: Quality trends over time
- **Temperature Compliance**: Environmental condition monitoring

## 🔧 Technical Components

### Backend Services
- **ML Service**: FastAPI with ONNX ResNet18 model (99.06% accuracy)
- **Blockchain**: Custom implementation with SHA-256 hashing
- **Database**: SQLite with connection pooling
- **REST API**: Comprehensive endpoints for all dashboards

### Frontend Application
- **JavaFX**: Modern, responsive user interface
- **Charts & Visualizations**: Real-time data representation
- **Multi-threading**: Non-blocking UI with background processing
- **Configuration Management**: Typesafe config with environment support

## 🛠️ API Endpoints

### ML Service Endpoints

```
POST /predict              # Fruit quality prediction
GET  /dashboard/farm       # Farm dashboard data
GET  /dashboard/analytics  # Analytics data
GET  /health              # Service health check
GET  /batches             # Batch management
```

### Example Usage

```bash
# Test prediction
curl -X POST -F "file=@fruit.jpg" http://localhost:8000/predict

# Check health
curl http://localhost:8000/health

# Get analytics
curl http://localhost:8000/dashboard/analytics
```

## 📊 Machine Learning Model

### Model Specifications
- **Architecture**: ResNet18 (transfer learning)
- **Accuracy**: 99.06% validation accuracy
- **Classes**: Fresh, Low Quality, Rotten
- **Input Size**: 224x224 RGB images
- **Framework**: ONNX Runtime for optimized inference
- **Training Data**: 2,671 samples (balanced dataset)

### Quality Metrics
- **Color Consistency**: 92% average
- **Size Uniformity**: 87% average
- **Defect Density**: 3% average
- **Confidence Threshold**: 85% for Prime grade classification

### Model Performance
The model was trained using transfer learning on the ResNet18 architecture, achieving state-of-the-art accuracy for fruit quality classification. The ONNX format enables efficient cross-platform deployment.

## ⛓️ Blockchain Implementation

### Features
- **Immutable Records**: SHA-256 hashing for data integrity
- **Supply Chain Tracking**: Complete product journey from farm to consumer
- **Smart Transactions**: CREATE_BATCH, TRANSFER, QUALITY_CHECK, DELIVERY
- **Validation**: Chain integrity verification
- **Persistent Storage**: JSON-based chain files for distributed ledger

### Transaction Types

```java
CREATE_BATCH     // Farmer creates new batch
QUALITY_CHECK    // Quality assessment recording  
TRANSFER         // Ownership transfer between parties
DELIVERY         // Final delivery confirmation
```

### Blockchain Structure
Each block contains:
- Previous block hash
- Timestamp
- Transaction data
- Data hash (SHA-256)
- Merkle root for data integrity

## 🗃️ Database Schema

### Core Tables

```sql
batches              # Batch information and quality scores
supply_chain_events  # Timeline of supply chain events
analytics            # Performance metrics and KPI data
```

**Note**: Detailed schema documentation is available in the source code database initialization files.

## 📦 Deployment

### Docker Deployment

#### ML Service
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
COPY ml-models/ ./ml-models/
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

Build and run:
```bash
cd docker/ml-service
docker build -t vericrop-ml .
docker run -d -p 8000:8000 --name vericrop-ml-service vericrop-ml
```

#### Using Docker Compose

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f
```

### Java Application Deployment

```bash
# Build JAR
./gradlew :vericrop-core:jar

# Run JAR
java -jar vericrop-core/build/libs/vericrop-core-0.1.0.jar
```

### Production Considerations

**TODO**: Add production deployment guidelines including:
- Environment-specific configurations
- Scaling strategies
- Security best practices
- Monitoring and logging setup
- Database migration procedures

## 🤝 Contributing

We welcome contributions to VeriCrop! Here's how you can help:

### How to Contribute

1. **Fork the repository**
   ```bash
   # Click the 'Fork' button on GitHub
   ```

2. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/vericrop-miniproject.git
   cd vericrop-miniproject
   ```

3. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Make your changes**
   - Write clear, documented code
   - Follow existing code style and conventions
   - Add tests for new features
   - Update documentation as needed

5. **Test your changes**
   ```bash
   ./gradlew test  # Java tests
   cd docker/ml-service && pytest  # Python tests
   ```

6. **Commit and push**
   ```bash
   git add .
   git commit -m "Add: Brief description of your changes"
   git push origin feature/your-feature-name
   ```

7. **Open a Pull Request**
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your fork and branch
   - Provide a clear description of your changes

### Contribution Guidelines

- **Code Style**: Follow Java and Python best practices
- **Testing**: All new features must include tests
- **Documentation**: Update README and inline comments
- **Commits**: Use clear, descriptive commit messages
- **Issues**: Check existing issues before creating new ones

### Areas for Contribution

- 🐛 Bug fixes and error handling improvements
- ✨ New features and enhancements
- 📝 Documentation improvements
- 🧪 Additional test coverage
- 🎨 UI/UX improvements
- 🔒 Security enhancements

**Note**: If this repository has a `CONTRIBUTING.md` file, please refer to it for detailed contribution guidelines.

## 📜 Code of Conduct

We are committed to providing a welcoming and inclusive experience for everyone. We expect all contributors to:

- Be respectful and considerate
- Use inclusive language
- Accept constructive feedback gracefully
- Focus on what is best for the community
- Show empathy towards other community members

**Note**: A formal Code of Conduct document is planned. In the meantime, please follow general open-source community standards and GitHub's Community Guidelines.

**TODO**: Add link to `CODE_OF_CONDUCT.md` when available.

## 📄 License

**TODO**: This project's license has not been specified yet. Please contact the maintainer for licensing information before using or distributing this software.

Common options for consideration:
- MIT License (permissive)
- Apache 2.0 (permissive with patent grant)
- GPL v3 (copyleft)

## 👥 Maintainers and Contact

### Project Owner
- **GitHub**: [@imperfectperson-max](https://github.com/imperfectperson-max)
- **Repository**: [vericrop-miniproject](https://github.com/imperfectperson-max/vericrop-miniproject)

### Getting Help

- 🐛 **Bug Reports**: [Open an issue](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
- 💡 **Feature Requests**: [Open an issue](https://github.com/imperfectperson-max/vericrop-miniproject/issues) with the "enhancement" label
- 💬 **Questions**: Use GitHub Discussions or contact the maintainer

### Response Time
We aim to respond to issues and pull requests within 48-72 hours. Please be patient as this is an educational project.

## 🙏 Acknowledgements

### Datasets and Resources
- **Fruits-360 Dataset**: Fruit image dataset for ML model training
- **PlantVillage Dataset**: Plant disease and quality assessment datasets

### Technologies and Frameworks
- **PyTorch/TensorFlow**: Machine learning frameworks
- **FastAPI**: Modern Python web framework
- **JavaFX**: Rich client application platform
- **ONNX**: Open Neural Network Exchange format

### Inspiration
This project was inspired by the need for transparency and quality assurance in agricultural supply chains, combining cutting-edge AI and blockchain technologies to create a trustworthy platform.

### References
- Transfer Learning with ResNet: [Deep Residual Learning](https://arxiv.org/abs/1512.03385)
- Supply Chain Management: Best practices in agricultural logistics
- Blockchain in Agriculture: Research papers on blockchain applications in food supply chains

---

## 📸 Screenshots and Media

**TODO**: Add screenshots of the application dashboards and features here.

### Adding Screenshots

To add images to this README:

1. Create a `/docs/images` directory in the repository
2. Add your screenshots there
3. Reference them in the README:

```markdown
![Dashboard Screenshot](docs/images/dashboard.png)
```

### Suggested Screenshots
- [ ] Farm Management Dashboard
- [ ] Logistics Tracking Interface
- [ ] Consumer Verification Portal
- [ ] Analytics Dashboard
- [ ] QR Code Scanning Demo
- [ ] Blockchain Transaction View

---

## 🚧 Development Roadmap

See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the detailed development timeline and weekly plan.

---

<div align="center">

**Made with ❤️ for sustainable agriculture and transparent supply chains**

[Report Bug](https://github.com/imperfectperson-max/vericrop-miniproject/issues) · [Request Feature](https://github.com/imperfectperson-max/vericrop-miniproject/issues) · [Contribute](https://github.com/imperfectperson-max/vericrop-miniproject/pulls)

</div>
