# Multi-stage Dockerfile for VeriCrop GUI (Spring Boot REST API)
# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle .
COPY settings.gradle .

# Copy all module sources
COPY vericrop-core vericrop-core/
COPY kafka-service kafka-service/
COPY ml-client ml-client/
COPY vericrop-gui vericrop-gui/

# Build the application (skip tests for faster builds)
RUN chmod +x gradlew && ./gradlew :vericrop-gui:bootJar --no-daemon -x test

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built jar from builder stage
COPY --from=builder /workspace/vericrop-gui/build/libs/vericrop-gui-*.jar app.jar

# Create directories for runtime data
# Blockchain initialization happens at runtime, not build time
RUN mkdir -p /app/ledger /app/data

# Expose application port
EXPOSE 8080

# Health check - increased start period for blockchain initialization
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# Environment variables with safe defaults for dev
ENV KAFKA_ENABLED=false \
    VERICROP_MODE=dev \
    SERVER_PORT=8080

# Run the application
# Blockchain initialization will happen at startup based on VERICROP_MODE
ENTRYPOINT ["java", "-jar", "app.jar"]
