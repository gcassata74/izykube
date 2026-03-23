# IzyKube
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License,
# or (at your option) any later version.

# ─────────────────────────────────────────────
# Stage 1 – Build
# Maven downloads Node/Yarn internally via frontend-maven-plugin,
# so this single stage handles both Angular and Spring Boot.
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy Maven wrappers and pom files first for better layer caching
COPY pom.xml ./
COPY backend/pom.xml backend/
COPY frontend/pom.xml frontend/

# Copy full source and build
# frontend-maven-plugin downloads Node/Yarn automatically during this step
COPY backend/ backend/
COPY frontend/ frontend/

RUN mvn -DskipTests clean package

# ─────────────────────────────────────────────
# Stage 2 – Runtime
# Minimal JRE image, no build tools
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S izykube && adduser -S izykube -G izykube
USER izykube

COPY --from=build /build/backend/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
