# Dockerfile for vericrop-gui
# Note: Build the application first using: ./gradlew :vericrop-gui:build
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built JAR (Spring Boot fat JAR)
# Use the fat JAR (not the -plain.jar) which includes all dependencies
COPY vericrop-gui/build/libs/vericrop-gui-1.0.0.jar app.jar

# Expose default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
