# Multi-stage: build with Maven + full JDK, run with just a slim JRE so
# the final image doesn't carry the whole build toolchain around.

# Pinned by digest (not the floating `21-jdk` tag) - Lombok's annotation
# processing is sensitive to the exact JDK patch build, and this is the
# JDK 21.0.12.1 build verified locally to compile this project cleanly.
FROM eclipse-temurin@sha256:3e3c176ffed168beb42c607be9bc1639b466cf00261a0fb04425562c9d0c5c2b AS build
WORKDIR /app

# Confirmed (via diagnostic build output) that Render's amd64 JVM build
# of this exact JDK version silently skips Lombok's annotation
# processing - no error, no warning, javac just runs without it - while
# the aarch64 build (local) runs it fine. This is Lombok's own
# documented fix: without these opens, its runtime self-patching of
# javac's internals doesn't always auto-apply depending on platform/JVM
# build specifics.
ENV MAVEN_OPTS="--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED"

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
