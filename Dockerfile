# Use official Eclipse Temurin JDK 21 image
FROM eclipse-temurin:21-jdk

# ENV GRADLE_OPTS="-Xmx512m -Dorg.gradle.jvmargs=-Xmx256m"

# Optimized JVM args for memory-limited containers
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseSerialGC -XX:+AlwaysPreTouch -XX:MaxMetaspaceSize=128m"

# Set working directory
WORKDIR /app

# Copy project files into the container
COPY . .

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application (skip tests and checks for CI deploy speed)
# RUN ./gradlew clean build -x test -x check
RUN ./gradlew clean build -x test -x check --no-daemon -Dorg.gradle.daemon=false -Dorg.gradle.jvmargs="-Xmx256m"

# Set the command to run the application JAR
# CMD ["java", "-XX:+UseSerialGC", "-Xms64m", "-Xmx128m", "-Dspring.profiles.active=prod", "-jar", "build/libs/app.jar"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=prod -jar build/libs/app.jar"]
