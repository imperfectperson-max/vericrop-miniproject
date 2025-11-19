# Dockerfile for VeriCrop GUI (Spring Boot REST API)
# Build the jar first with: ./gradlew :vericrop-gui:bootJar
# Then build the Docker image: docker build -t vericrop-gui -f vericrop-gui/Dockerfile .

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the pre-built jar (must be built before docker build)
COPY vericrop-gui/build/libs/vericrop-gui-*.jar app.jar

# Create ledger directory
RUN mkdir -p /app/ledger

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
