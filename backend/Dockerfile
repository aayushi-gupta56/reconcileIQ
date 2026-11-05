
# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven configuration first for dependency caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy application source
COPY src ./src

# Build executable Spring Boot JAR
RUN mvn clean package -DskipTests -B \
    && JAR_FILE=$(find target -maxdepth 1 -type f -name "*.jar" ! -name "*.jar.original" | head -n 1) \
    && cp "$JAR_FILE" /app/app.jar


# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy executable JAR from build stage
COPY --from=build /app/app.jar app.jar

# Render provides the PORT environment variable
EXPOSE 8080

# JVM memory configuration suitable for a small instance
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar app.jar --server.port=${PORT:-8080}"]