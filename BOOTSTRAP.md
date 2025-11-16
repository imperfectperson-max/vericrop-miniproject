# VeriCrop Bootstrap MVP

This document describes the bootstrap scaffolding added in this PR.

## Overview

This PR adds the foundational structure for the VeriCrop MVP project, including:
- PyTorch-based ML service with FastAPI
- Java multi-module project structure (vericrop-core and vericrop-gui)
- Blockchain core implementation
- Docker and docker-compose configuration
- CI/CD workflow for building and publishing Docker images to GHCR
- GitHub issues template for tracking progress

## Project Structure

```
vericrop-miniproject/
├── docker/
│   └── ml-service/                # FastAPI ML service
│       ├── app.py                 # FastAPI application with /predict endpoint
│       ├── requirements.txt       # Python dependencies
│       ├── Dockerfile             # Docker configuration
│       ├── requirements-test.txt  # Testing dependencies
│       ├── weights/               # Directory for PyTorch model weights
│       └── tests/                 # Integration tests
│
├── vericrop-core/                 # Core blockchain module
│   ├── build.gradle
│   └── src/
│       ├── main/java/org/vericrop/blockchain/
│       │   ├── Block.java         # Block class with SHA-256 hashing
│       │   └── Blockchain.java    # Blockchain with validation
│       └── test/java/org/vericrop/blockchain/
│           └── BlockchainTest.java
│
├── vericrop-gui/                  # JavaFX GUI module
│   ├── build.gradle
│   └── src/main/java/org/vericrop/gui/
│       └── MainApp.java           # Minimal JavaFX Hello World
│
├── .github/workflows/
│   └── ci-ghcr.yml                # CI workflow for GHCR
│
├── docker-compose.yml             # Orchestrates the ml-service container
├── settings.gradle                # Gradle multi-module configuration
└── ISSUES.md                      # GitHub issues to create
```

## ML Service (FastAPI + PyTorch)

### Features
- FastAPI application with `/predict` endpoint
- Accepts image files and returns predictions
- PyTorch model loading with fallback to placeholder predictions
- Image preprocessing with standard transforms
- Health check endpoint

### Usage

```bash
# Build and run with docker-compose
docker compose up -d ml-service

# Test the health endpoint
curl http://localhost:8000/health

# Test the predict endpoint
curl -X POST http://localhost:8000/predict \
  -F "file=@/path/to/image.jpg"
```

### Adding Model Weights

Place your trained PyTorch model at `docker/ml-service/weights/model.pt`. The service will automatically load it on startup. If no weights are found, it will use placeholder predictions.

## Java Modules

### vericrop-core

Core blockchain functionality with:
- `Block.java`: Immutable block with SHA-256 hashing
- `Blockchain.java`: Chain management with validation
- Unit tests for all functionality

```bash
# Build and test
./gradlew :vericrop-core:build

# Run tests
./gradlew :vericrop-core:test
```

### vericrop-gui

Minimal JavaFX GUI application.

```bash
# Build
./gradlew :vericrop-gui:build

# Run the GUI
./gradlew :vericrop-gui:run
```

## Docker Compose

The `docker-compose.yml` includes:
- **ml-service**: FastAPI ML service (port 8000)
- **postgres**: PostgreSQL database
- **mosquitto**: MQTT broker

```bash
# Start all services
docker compose up

# Start specific service
docker compose up -d ml-service
```

## CI/CD Pipeline

### GHCR Workflow (`.github/workflows/ci-ghcr.yml`)

Automatically builds and pushes the ML service Docker image to GitHub Container Registry (GHCR) when:
- Code is pushed to `main` branch
- Changes are made under `docker/ml-service/**`

**Image Tags:**
- `ghcr.io/<owner>/vericrop-ml-service:main` (latest main branch)
- `ghcr.io/<owner>/vericrop-ml-service:sha-<commit-sha>` (specific commit)

**Authentication:**
Uses `GITHUB_TOKEN` by default. If permissions are insufficient, create a Personal Access Token with `write:packages` scope and add it as a secret named `GHCR_TOKEN`.

## GitHub Issues

The file `ISSUES.md` contains templates for 6 GitHub issues to track MVP development:

1. **MVP: Scaffold ml_service (FastAPI + Docker)**
2. **MVP: Scaffold Java modules (vericrop-core, vericrop-gui)**
3. **MVP: Create blockchain core (Block, Blockchain)**
4. **MVP: CI for GHCR build and push**
5. **MVP: Docker Compose integration**
6. **MVP: Dataset loader for Fruits-360**

Create these issues using the GitHub web interface or the `gh` CLI commands provided in `ISSUES.md`.

## Building the Project

### Prerequisites
- Java 17+
- Gradle 8.14+
- Docker and Docker Compose
- Python 3.11+ (for local ML service development)

### Build All Modules

```bash
# Build all Java modules
./gradlew build

# Run all tests
./gradlew test

# Build specific module
./gradlew :vericrop-core:build
./gradlew :vericrop-gui:build
```

### Build Docker Images

```bash
# Build ML service image using docker-compose
docker compose build ml-service

# Or build directly
cd docker/ml-service
docker build -t vericrop-ml-service .
```

## Next Steps

1. Create GitHub issues from `ISSUES.md` template
2. Add trained PyTorch model weights to `docker/ml-service/weights/model.pt`
3. Download and integrate Fruits-360 dataset
4. Enhance vericrop-gui with actual VeriCrop functionality
5. Integrate blockchain with ML service results
6. Add comprehensive unit and integration tests
7. Set up development and production environments

## Notes

- The FastAPI endpoint uses placeholder predictions if model weights are not available
- The Java modules are intentionally minimal to establish the structure
- The CI workflow requires packages write permission (configured in the workflow)
- The ML service is located at `docker/ml-service/` for consistency

## Contributing

Please follow the existing code style and add tests for any new functionality.
