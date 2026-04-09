# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
# Copy the POM and download dependencies first to utilize Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code and package the Spring Boot JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create a minimal JRE image for deployment
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the compiled JAR from the builder stage
COPY --from=builder /app/target/aipclm-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose standard Spring Boot port
EXPOSE 8080

# Run the backend
ENTRYPOINT ["java", "-jar", "app.jar"]
