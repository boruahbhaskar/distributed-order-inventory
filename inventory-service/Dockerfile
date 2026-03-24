# inventory-service/Dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Download dependencies first (Docker layer cache optimization)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q

# Stage 2: Runtime
# WHY eclipse-temurin:21-jre-alpine (not JDK):
#   JRE = only what's needed to run. JDK includes compiler + tools.
#   JRE image is ~100MB smaller — smaller attack surface, faster pulls.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security: don't run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the fat jar from builder stage
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Expose port and set JVM options for containers
# WHY -XX:+UseContainerSupport: JVM reads cgroup limits (memory/CPU)
# instead of host machine limits — prevents OOM in limited containers.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]