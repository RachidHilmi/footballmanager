# Use Gradle to build the project
FROM gradle:8.4.0-jdk17 AS builder

# Set work directory inside container
WORKDIR /app

# Copy everything
COPY . .

# Build the app without running tests
RUN gradle build -x test

# Use lightweight Java image for production
FROM eclipse-temurin:17-jdk-alpine

# Set work directory
WORKDIR /app

# Copy the built jar from the builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
