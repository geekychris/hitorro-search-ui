# Multi-stage build for hitorro-search-ui — one Dockerfile does the
# whole build (node → maven → runtime image). Trade-off: a full build
# is slower than reusing your local ~/.m2 cache, but produces a self-
# contained image consumers can just docker-run without any prior setup.
#
# For faster local iteration, build the fat JAR locally with
# `mvn install` in hitorro-search-ui/ first, then use the
# Dockerfile.jar-only variant below (uncommented) to skip the build.
# Uncomment only ONE FROM chain.

# ============ Stage 1: frontend build =================================
FROM node:20-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/pnpm-workspace.yaml frontend/tailwind.config.js frontend/tsconfig.base.json ./
COPY frontend/packages ./packages
RUN corepack enable && corepack prepare pnpm@8.15.4 --activate && \
    pnpm install --frozen-lockfile=false && \
    pnpm -r build

# ============ Stage 2: backend build ==================================
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src
# Pull the built React dist into the location the Maven resources plugin
# expects (../frontend/packages/demo-app/dist relative to backend/).
COPY --from=frontend /build/packages/demo-app/dist ./frontend/packages/demo-app/dist
# skip.frontend=true because we already built it in stage 1 — no need
# for Maven to redo the pnpm install/build inside the image.
RUN mvn -pl backend -am package -Dskip.frontend=true -DskipTests -B

# ============ Stage 3: runtime ========================================
FROM eclipse-temurin:21-jre
# Non-root uid 1000 — matches the mesh driver's pattern for volume-mount
# permission consistency across the family.
RUN groupadd --gid 1000 hitorro && useradd --uid 1000 --gid hitorro --home /home/hitorro --create-home --shell /usr/sbin/nologin hitorro
COPY --from=backend --chown=hitorro:hitorro /build/backend/target/hitorro-search-ui-3.0.1.jar /app/search-ui.jar
USER hitorro
WORKDIR /home/hitorro

# JVM sizing tuned for a mostly-idle proxy — 128M initial / 512M max is
# plenty since we're not holding search results in memory (fleet-retrieval
# does that work).
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
# docker profile activates the fleet URL override to talk to the
# fleet-retrieval sibling container via compose network dns.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8100

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
    CMD wget -q -O- http://localhost:8100/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/search-ui.jar"]
