# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────
# Barbershop backend — multi-stage build:
#   1. build the jar with Maven (dependency cache via BuildKit)
#   2. split it into Spring Boot layers (dependencies change rarely → cached)
#   3. small JRE image, non-root user
# ─────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/barbershop-backend-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER app
EXPOSE 8080
# UTC JVM: deterministic time handling regardless of the host
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "org.springframework.boot.loader.launch.JarLauncher"]
