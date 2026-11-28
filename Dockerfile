# syntax=docker/dockerfile:1
# Builds the jar inside Docker, so `docker build` and `docker compose up --build` need no local Java or Maven.
# The Maven cache mount keeps downloaded dependencies between builds on the same machine.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/folio-pro-api-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /build/app.jar app.jar
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
