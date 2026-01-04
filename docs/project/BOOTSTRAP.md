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
├── docker/ml-service/              # FastAPI ML service (canonical)
│   ├── app.py                      # FastAPI application with /predict endpoint
│   ├── requirements.txt            # Python dependencies
│   ├── Dockerfile                  # Docker configuration
│   ├── tests/                      # Test suite
│   └── weights/                    # Directory for model weights (gitignored)
│
├── vericrop-core/                  # Core blockchain module
│   ├── build.gradle
│   └── src/
│       ├── main/java/org/vericrop/blockchain/
│       │   ├── Block.java          # Block class with SHA-256 hashing
│       │   └── Blockchain.java     # Blockchain with validation
│       └── test/java/org/vericrop/blockchain/
│           └── BlockchainTest.java
│
├── vericrop-gui/                   # JavaFX GUI module (Producer app)
│   ├── build.gradle
│   └── src/main/java/org/untitled/gui/
│       └── Main.java               # Producer application
│
├── .github/workflows/
│   └── ci-ghcr.yml                 # CI workflow for GHCR
│
├── docker-compose.yml              # Orchestrates ml-service container
├── settings.gradle                 # Gradle project configuration
└── ISSUES.md                       # GitHub issues tracking
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
# Build Docker image
cd docker/ml-service
docker build -t vericrop-ml-service .

# Run with docker-compose
docker-compose up ml-service

# Test the endpoint
curl -X POST http://localhost:8000/predict \
  -F "file=@/path/to/image.jpg"
```

### Adding Model Weights

Place your trained model weights at `docker/ml-service/weights/`. The service currently uses dummy predictions. For PyTorch model integration, see `docker/ml-service/legacy/` which contains the PyTorch-based implementation.

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

JavaFX Producer application with blockchain integration.

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
docker-compose up

# Start specific service
docker-compose up ml-service
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
# Build ML service image
cd docker/ml-service
docker build -t vericrop-ml-service .

# Or use docker-compose
docker-compose build ml-service
```

## Next Steps

1. Create GitHub issues from `ISSUES.md` template
2. Add trained model weights to `docker/ml-service/weights/`
3. Download and integrate Fruits-360 dataset
4. Enhance vericrop-gui with additional VeriCrop functionality
5. Integrate blockchain with ML service results
6. Add comprehensive unit and integration tests
7. Set up development and production environments

## Notes

- The FastAPI endpoint uses dummy predictions suitable for MVP demo
- Legacy PyTorch implementation available in `docker/ml-service/legacy/` for reference
- The Java modules include full Producer app functionality
- The CI workflow requires packages write permission (configured in the workflow)
- Repository uses single canonical locations: `docker/ml-service` and `vericrop-gui`

## Contributing

Please follow the existing code style and add tests for any new functionality.
