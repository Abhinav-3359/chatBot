# Multi-stage: build with Maven + full JDK, run with just a slim JRE so
# the final image doesn't carry the whole build toolchain around.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Spring Boot reads PORT via this - most free PaaS platforms inject
# their own PORT env var and expect the app to bind to it.
ENV SERVER_PORT=8080
EXPOSE 8080

# Free-tier hosts (e.g. Render's 512MB plan) are tight for a JVM by
# default - cap the heap so the process doesn't get OOM-killed instead
# of just running a bit slower. Override via JAVA_OPTS if you move to a
# host with more headroom.
ENV JAVA_OPTS="-Xmx350m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-$SERVER_PORT}"]
