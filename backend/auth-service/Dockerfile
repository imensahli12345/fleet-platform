# syntax=docker/dockerfile:1
# Build from backend/:  docker build -f auth-service/Dockerfile -t fleet/auth-service .

# ---- Stage 1: build the executable jar (only this module and what it depends on) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth-service -am package -DskipTests

# ---- Stage 2: small runtime image, non-root ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
