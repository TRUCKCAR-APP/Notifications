# =============================================================================
# Stage 1: Build
# =============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Cache dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build application
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create unprivileged user
RUN groupadd -r truckcar && useradd -r -g truckcar truckcar

COPY --from=builder /app/target/notifications-*.jar app.jar
RUN chown -R truckcar:truckcar /app

USER truckcar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV SERVER_PORT=8083

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
