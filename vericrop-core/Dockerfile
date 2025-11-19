# Dockerfile for vericrop-core
# Note: This is a library module without a main class.
# Build the library first using: ./gradlew :vericrop-core:build
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built JAR
COPY vericrop-core/build/libs/vericrop-core-1.0.0.jar vericrop-core.jar

# This is a library module - no ENTRYPOINT defined
# To use this image, extend it or mount the JAR in another service
CMD ["echo", "vericrop-core is a library module. Use it as a dependency in other services."]
