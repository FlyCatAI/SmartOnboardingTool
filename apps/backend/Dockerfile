# syntax=docker/dockerfile:1.6
#
# Multi-stage build for the RM backend Spring Boot fat jar.
#   D1 Spring Boot 3.2.5 / Spring MVC, D3 JDK 17 LTS, D4 MyBatis 3.0.3.
#
# Build (from repo root):
#   docker build -t rm-backend:local -f apps/backend/Dockerfile .
# Run (requires a reachable Postgres, see deploy/docker-compose.test.yml):
#   docker run --rm -p 8080:8080 \
#     -e DB_URL=jdbc:postgresql://postgres:5432/rm \
#     -e DB_USERNAME=rm -e DB_PASSWORD=rm \
#     rm-backend:local

ARG MAVEN_IMAGE=maven:3.9.6-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

COPY apps/backend/pom.xml apps/backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f apps/backend/pom.xml dependency:go-offline

COPY apps/backend/src apps/backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f apps/backend/pom.xml -DskipTests package \
    && cp apps/backend/target/rm-backend-*.jar /workspace/app.jar

FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 --home /app --shell /sbin/nologin rm \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R rm:rm /app

USER rm

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
