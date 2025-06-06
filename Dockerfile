# Stage 1: Build Spring Boot JAR
FROM eclipse-temurin:21-jdk as builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test -x check

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Make sure logs are streamed and port is set correctly
CMD ["sh", "-c", "echo Starting app on port $PORT && java -Xms64m -Xmx128m -Dspring.profiles.active=prod -Dserver.port=$PORT -jar app.jar"]
