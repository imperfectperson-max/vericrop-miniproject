# Multi-stage Dockerfile for VeriCrop GUI (Spring Boot REST API)
# Production-optimized with security hardening and minimal size

# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle .
COPY settings.gradle .
COPY config config/

# Copy all module sources
COPY vericrop-core vericrop-core/
COPY kafka-service kafka-service/
COPY ml-client ml-client/
COPY vericrop-gui vericrop-gui/

# Build the application with optimizations
RUN chmod +x gradlew && \
    ./gradlew :vericrop-gui:bootJar --no-daemon -x test && \
    # Extract layers for better caching
    mkdir -p /workspace/extracted && \
    cd /workspace/extracted && \
    java -Djarmode=layertools -jar /workspace/vericrop-gui/build/libs/vericrop-gui-*.jar extract

# Stage 2: Runtime image with distroless-style minimal base
FROM eclipse-temurin:17-jre-alpine

# Add metadata labels
LABEL maintainer="vericrop@example.com" \
      version="1.0.0" \
      description="VeriCrop GUI - Agricultural Supply Chain Management" \
      org.opencontainers.image.source="https://github.com/imperfectperson-max/vericrop-miniproject"

# Install curl for healthcheck (Alpine uses apk)
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1000 vericrop && \
    adduser -D -u 1000 -G vericrop vericrop

WORKDIR /app

# Create directories with proper permissions
RUN mkdir -p /app/ledger /app/data /app/logs && \
    chown -R vericrop:vericrop /app

# Copy layers from builder (optimizes Docker layer caching)
COPY --from=builder --chown=vericrop:vericrop /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=vericrop:vericrop /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=vericrop:vericrop /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=vericrop:vericrop /workspace/extracted/application/ ./

# Switch to non-root user
USER vericrop

# Expose application port
EXPOSE 8080

# Health check - increased start period for blockchain initialization
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Environment variables with safe defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    KAFKA_ENABLED=false \
    VERICROP_MODE=production \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=production

# JVM tuning for containers
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application with optimized JVM settings
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_TOOL_OPTIONS org.springframework.boot.loader.JarLauncher"]
