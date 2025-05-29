# Stage 1: Build with Gradle using preinstalled JDK
FROM gradle:8.2.1-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build -x test --no-daemon

# Stage 2: Run the built JAR
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
