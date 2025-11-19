#!/bin/bash
# Build script for VeriCrop Docker images
# Builds all Java artifacts and then creates Docker images

set -e

echo "================================================"
echo "Building VeriCrop Docker Images"
echo "================================================"
echo ""

# Build Java artifacts
echo "Step 1: Building Java artifacts with Gradle..."
./gradlew clean build --no-daemon

echo ""
echo "Step 2: Building Docker images..."

# Build vericrop-gui
echo "Building vericrop-gui..."
docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile .

# Build ml-service
echo "Building ml-service..."
docker build -t vericrop-ml-service:latest -f docker/ml-service/Dockerfile docker/ml-service

echo ""
echo "================================================"
echo "Build completed successfully!"
echo "================================================"
echo ""
echo "Available images:"
docker images | grep -E "vericrop-gui|vericrop-ml-service" || echo "No images found"
echo ""
echo "To start the stack:"
echo "  docker-compose up -d"
echo ""
