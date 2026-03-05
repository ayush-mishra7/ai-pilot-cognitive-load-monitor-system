# ──────────────────────────────────────────────
#  AI-PCLM Backend — Multi-stage Docker Build
# ──────────────────────────────────────────────
#  Stage 1: Maven build  (JDK 17)
#  Stage 2: Runtime      (JRE 17-slim)
# ──────────────────────────────────────────────

# ── Stage 1: Build ──
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    if [ -f mvnw ]; then chmod +x mvnw && ./mvnw dependency:go-offline -B; \
    else apt-get update && apt-get install -y maven && mvn dependency:go-offline -B; fi

# Copy source and build
COPY src ./src
RUN if [ -f mvnw ]; then ./mvnw package -DskipTests -B; \
    else mvn package -DskipTests -B; fi

# ── Stage 2: Runtime ──
FROM eclipse-temurin:17-jre

LABEL maintainer="Ayush Mishra <ayush-mishra7>"
LABEL description="AI-PCLM Backend — Spring Boot 3.3.4"

WORKDIR /app

# Create non-root user
RUN groupadd -r aipclm && useradd -r -g aipclm -s /bin/false aipclm

# Copy the fat JAR
COPY --from=builder /build/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/api/test/simulation/health || exit 1

# Use non-root
USER aipclm

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
