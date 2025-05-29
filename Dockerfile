# Stage 1: build
FROM gradle:8.2.1-jdk17 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build -x test --stacktrace

# Stage 2: run
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
