# syntax=docker/dockerfile:1
# Build from backend/:  docker build -f api-gateway/Dockerfile -t fleet/api-gateway .

# ---- Stage 1: build the executable jar (only this module and what it depends on) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl api-gateway -am package -DskipTests

# ---- Stage 2: small runtime image, non-root ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8089
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
