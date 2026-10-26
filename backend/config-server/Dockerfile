# syntax=docker/dockerfile:1
# Build from backend/:  docker build -f config-server/Dockerfile -t fleet/config-server .

# ---- Stage 1: build the executable jar (only this module and what it depends on) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl config-server -am package -DskipTests

# ---- Stage 2: small runtime image, non-root ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/config-server/target/config-server-0.0.1-SNAPSHOT.jar app.jar
# Native config backend reads file:./config-repo (relative to /app). Baked in as a default;
# can be overridden at run time by mounting the real folder on /app/config-repo.
COPY config-repo ./config-repo
USER app
EXPOSE 8888
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
