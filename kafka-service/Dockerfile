# Multi-stage build for Kafka Service
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files for dependency caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy required modules
COPY vericrop-core ./vericrop-core
COPY kafka-service ./kafka-service

# Build the kafka-service jar
RUN ./gradlew :kafka-service:jar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built jar from builder stage
COPY --from=builder /app/kafka-service/build/libs/kafka-service-*.jar kafka-service.jar
COPY --from=builder /app/vericrop-core/build/libs/vericrop-core-*.jar vericrop-core.jar

# Copy dependencies
COPY --from=builder /root/.gradle/caches/modules-2/files-2.1 /opt/libs

# Expose any ports if needed (kafka-service is primarily a library, but might have management port)
EXPOSE 8081

# Health check - since this is a library module, we'll create a simple check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD test -f /app/kafka-service.jar || exit 1

# Note: This is a library module, not a standalone service
# It will be used by vericrop-gui, so this Dockerfile may not be directly used
# Keeping it for potential future standalone kafka-service deployment
CMD ["echo", "Kafka-service is a library module. Use vericrop-gui to run the application."]
