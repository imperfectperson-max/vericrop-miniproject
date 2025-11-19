# Dockerfile for kafka-service
# Note: This is a library module without a main class.
# Build the library first using: ./gradlew :kafka-service:build
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built JAR
COPY kafka-service/build/libs/kafka-service-1.0.0-plain.jar kafka-service.jar

# This is a library module - no ENTRYPOINT defined
# To use this image, extend it or mount the JAR in another service
CMD ["echo", "kafka-service is a library module. Use it as a dependency in other services."]
