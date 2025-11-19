# Multi-stage build for VeriCrop GUI (Spring Boot REST API)
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files for dependency caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy all modules
COPY vericrop-core ./vericrop-core
COPY kafka-service ./kafka-service
COPY ml-client ./ml-client
COPY vericrop-gui ./vericrop-gui

# Build the application
RUN ./gradlew :vericrop-gui:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built jar from builder stage
COPY --from=builder /app/vericrop-gui/build/libs/vericrop-gui-*.jar app.jar

# Create ledger directory
RUN mkdir -p /app/ledger

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
