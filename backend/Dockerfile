# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────
# Barbershop backend — production image, multi-stage:
#   1. build the jar with Maven (dependency cache via BuildKit)
#   2. split it into Spring Boot layers (dependencies change rarely → cached)
#   3. small JRE image, fixed non-root user, healthcheck
# Build:  docker build -t barbershop-backend backend
# ─────────────────────────────────────────────────────────────────────────
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/barbershop-backend-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine-3.22
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="barbershop-backend" \
      org.opencontainers.image.description="Barbershop booking API (Spring Boot)" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
# fixed uid/gid: platforms with runAsNonRoot / volume permissions can rely on it
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app \
    && apk upgrade --no-cache
WORKDIR /app
# least → most frequently changing layer
COPY --from=build --chown=app:app /src/extracted/dependencies/ ./
COPY --from=build --chown=app:app /src/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /src/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /src/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
# prod profile by default (the local docker-compose.yml overrides it with "demo").
# JVM: container-aware heap; crash fast on OOM so the platform restarts the container;
# UTC for deterministic time handling (shop times use the tenant's zone).
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Duser.timezone=UTC -Djava.io.tmpdir=/tmp -Dfile.encoding=UTF-8"
# readiness = DB reachable and app started (actuator, no details exposed); busybox wget
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
# exec form: java is PID 1 and receives SIGTERM → Spring's graceful shutdown
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
