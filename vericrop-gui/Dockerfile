# Multi-stage Dockerfile for VeriCrop GUI (Spring Boot REST API)
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Install Gradle
RUN apt-get update && apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-7.6-bin.zip && \
    unzip gradle-7.6-bin.zip && \
    rm gradle-7.6-bin.zip

ENV GRADLE_HOME=/workspace/gradle-7.6
ENV PATH=$GRADLE_HOME/bin:$PATH

# Copy build files
COPY build.gradle .
COPY settings.gradle .
COPY vericrop-core vericrop-core/
COPY kafka-service kafka-service/
COPY ml-client ml-client/
COPY vericrop-gui vericrop-gui/

# Build using installed Gradle
RUN gradle :vericrop-gui:bootJar --no-daemon -x test

# Stage 2: Runtime image (same as before)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /appc
RUN apt-get update && apt-get instalsl -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /workspace/vericrop-gui/build/libs/vericrop-gui-*.jar app.jar
RUN mkdir -p /app/ledger /app/data
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

ENV KAFKA_ENABLED=false \
    VERICROP_MODE=dev \
    SERVER_PORT=8080

ENTRYPOINT ["java", "-jar", "app.jar"]