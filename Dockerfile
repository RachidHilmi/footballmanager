FROM eclipse-temurin:21-jdk as builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test -x check

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
CMD ["sh", "-c", "java -Xms64m -Xmx128m -Dspring.profiles.active=prod -jar app.jar"]
