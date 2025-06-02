# Use official Eclipse Temurin JDK 21 image
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy project files into the container
COPY . .

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application (skip tests and checks for CI deploy speed)
RUN ./gradlew clean build -x test -x check

# Set the command to run the application JAR
CMD ["java", "-jar", "build/libs/app.jar"]
