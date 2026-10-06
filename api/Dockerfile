# Production image for the TwistMeet API (Spring Boot). Multi-stage: build with the full JDK,
# run on a minimal JRE. Build context is this `api/` directory (see docker-compose.production.yml
# and DEPLOYMENT.md). Does not create or require any cloud resource — this is a local image build
# only, run with `docker build -f api/Dockerfile api/`.

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cache dependency resolution separately from source changes.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --console=plain || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test --console=plain

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre AS runtime

# curl is needed for the container HEALTHCHECK below (actuator's /actuator/health); the
# eclipse-temurin JRE base image doesn't include it. Installed and cache-cleaned in one layer to
# keep the final image small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root runtime user — the application never needs root, and shouldn't run as it.
RUN groupadd --system twistmeet \
    && useradd --system --gid twistmeet --home-dir /app --shell /usr/sbin/nologin twistmeet

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown -R twistmeet:twistmeet /app
USER twistmeet

# Always run under the `production` Spring profile in this image — see
# application-production.yml and ScrambleEncryptionService's production fail-fast check. All
# other configuration (DB URL/credentials, mail host, the scramble encryption key, the web app's
# origin for CORS) is supplied via environment variables at `docker run`/Compose time — see
# DEPLOYMENT.md for the full list. Nothing is baked into this image.
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
