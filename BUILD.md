# VeriCrop Build and Docker Guide

This document provides instructions for building the VeriCrop project and creating Docker images.

## Prerequisites

- **Java 17** or later (OpenJDK or Eclipse Temurin recommended)
- **Docker** (for containerization)
- **Docker Compose** (for multi-container deployments)
- **Git** (for version control)

## Project Structure

The VeriCrop project is organized as a multi-module Gradle project:

```
vericrop-miniproject/
├── vericrop-core/       # Core domain models and utilities (library)
├── kafka-service/       # Kafka messaging integration (library)
├── vericrop-gui/        # JavaFX GUI application (Spring Boot app)
├── ml-client/           # ML service client (library, no source)
├── docker/              # Docker configurations for Python ML service
└── settings.gradle      # Multi-project configuration
```

## Building with Gradle

### 1. Build All Modules

```bash
./gradlew clean build
```

This command:
- Compiles all Java source code
- Runs tests
- Creates JAR files in `*/build/libs/`

### 2. Build Individual Modules

```bash
# Build vericrop-core
./gradlew :vericrop-core:build

# Build kafka-service
./gradlew :kafka-service:build

# Build vericrop-gui
./gradlew :vericrop-gui:build
```

### 3. Build Without Tests

```bash
./gradlew build -x test
```

### 4. View Build Artifacts

After building, JAR files are located at:
- `vericrop-core/build/libs/vericrop-core-1.0.0.jar`
- `kafka-service/build/libs/kafka-service-1.0.0-plain.jar`
- `vericrop-gui/build/libs/vericrop-gui-1.0.0.jar` (Spring Boot fat JAR)

## Docker Build Instructions

### Prerequisites for Docker Builds

**Important:** Build the JARs first before building Docker images:

```bash
./gradlew clean build -x test
```

### Building Individual Docker Images

#### 1. VeriCrop GUI (Main Application)

```bash
docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile .
```

#### 2. Kafka Service (Library)

```bash
docker build -t kafka-service:latest -f kafka-service/Dockerfile .
```

#### 3. VeriCrop Core (Library)

```bash
docker build -t vericrop-core:latest -f vericrop-core/Dockerfile .
```

### Building All Images at Once

```bash
# Build all Java modules first
./gradlew clean build -x test

# Build all Docker images
docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile .
docker build -t kafka-service:latest -f kafka-service/Dockerfile .
docker build -t vericrop-core:latest -f vericrop-core/Dockerfile .
```

## Docker Compose

### Using docker-compose-java.yml

This compose file includes the Java services with Kafka:

```bash
# Build JARs first
./gradlew clean build -x test

# Start all services
docker-compose -f docker-compose-java.yml up --build

# Run in detached mode
docker-compose -f docker-compose-java.yml up -d --build

# Stop services
docker-compose -f docker-compose-java.yml down
```

### Using docker-compose-kafka.yml

For Kafka infrastructure only:

```bash
docker-compose -f docker-compose-kafka.yml up -d
```

### Using docker-compose.yml

For the complete stack (ML service, databases, Airflow, Kafka):

```bash
docker-compose up -d
```

## Validation Commands

### Verify Gradle Build

```bash
# Run from repository root
./gradlew wrapper && ./gradlew clean build --no-daemon
```

Expected output: `BUILD SUCCESSFUL`

### Verify Module Builds

```bash
./gradlew :vericrop-core:build :kafka-service:build :vericrop-gui:build
```

### Verify Docker Builds

```bash
# Build and verify each service
docker build -t vericrop-gui-test -f vericrop-gui/Dockerfile . && echo "✓ vericrop-gui"
docker build -t kafka-service-test -f kafka-service/Dockerfile . && echo "✓ kafka-service"
docker build -t vericrop-core-test -f vericrop-core/Dockerfile . && echo "✓ vericrop-core"
```

### Verify Docker Compose

```bash
# Start services
docker-compose -f docker-compose-java.yml up -d

# Check service status
docker-compose -f docker-compose-java.yml ps

# View logs
docker-compose -f docker-compose-java.yml logs vericrop-gui

# Stop services
docker-compose -f docker-compose-java.yml down
```

## Troubleshooting

### Gradle Build Issues

**Problem:** Permission denied on gradlew
```bash
chmod +x gradlew
```

**Problem:** Java version mismatch
```bash
# Verify Java version
java -version
# Should show Java 17 or later
```

### Docker Build Issues

**Problem:** JAR file not found during Docker build
```bash
# Solution: Build JARs first
./gradlew clean build -x test
```

**Problem:** Docker build fails with certificate errors
```bash
# This shouldn't happen with the current Dockerfile approach
# which uses pre-built JARs
```

### Docker Compose Issues

**Problem:** Port already in use
```bash
# Change port mappings in docker-compose-java.yml
# Or stop conflicting services
```

## CI/CD

The project includes a GitHub Actions workflow (`.github/workflows/ci.yml`) that:
1. Builds all modules with Gradle
2. Runs tests
3. Builds Docker images
4. Uploads artifacts

## Additional Notes

- **vericrop-core** and **kafka-service** are library modules without main classes
- **vericrop-gui** is the main runnable application (Spring Boot + JavaFX)
- **ml-client** module exists in settings but has no source files (placeholder)
- All modules use Java 17 and are built with Gradle 8.14

## Next Steps

After successful build and Docker image creation:
1. Deploy services using Docker Compose
2. Configure Kafka topics and consumers
3. Integrate with ML service for quality prediction
4. Set up monitoring and logging
5. Configure production deployment environment

For more information, see:
- Main README.md
- IMPLEMENTATION_SUMMARY.md
- KAFKA_INTEGRATION.md
