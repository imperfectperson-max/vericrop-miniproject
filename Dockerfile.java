# Multi-stage build for VeriCrop Java Service
FROM gradle:8.5-jdk17 AS build

# Set working directory
WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy source code
COPY vericrop-core ./vericrop-core
COPY kafka-service ./kafka-service
COPY ml-client ./ml-client
COPY vericrop-gui ./vericrop-gui

# Build the application
RUN ./gradlew :vericrop-gui:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/vericrop-gui/build/libs/vericrop-gui-*.jar app.jar

# Expose port
EXPOSE 8080

# Environment variables
ENV KAFKA_BOOTSTRAP_SERVERS=localhost:9092
ENV KAFKA_ENABLED=false
ENV DATABASE_URL=jdbc:h2:mem:vericrop

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
