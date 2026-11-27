# syntax=docker/dockerfile:1
# --- Build: Gradle wrapper + JDK 21 -------------------------------------------------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY config config
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*-SNAPSHOT.jar app.jar

# --- Run: JRE 21, unprivileged user, uploads on a volume ----------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 --home /app portfolio \
    && mkdir -p /data/uploads && chown portfolio /data/uploads \
    && apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER portfolio
ENV UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
VOLUME /data/uploads
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
