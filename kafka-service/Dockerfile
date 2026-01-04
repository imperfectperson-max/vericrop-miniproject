# Multi-stage Dockerfile for Kafka Service
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy only what's needed for building
COPY build.gradle settings.gradle ./
COPY gradle gradle/
COPY kafka-service kafka-service/
COPY vericrop-core vericrop-core/
COPY src src/

# Use gradle command
RUN gradle :kafka-service:jar --no-daemon --build-cache

# Runtime stage
FROM eclipse-temurin:17-jre-jam

WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/kafka-service/build/libs/kafka-service-*.jar app.jar
EXPOSE 9092

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:9092/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]