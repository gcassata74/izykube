# IzyKube
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License,
# or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

# ── Stage 1: build (Maven builds both frontend module and backend) ──────────
# frontend-maven-plugin downloads its own Node/Yarn — no pre-install needed.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy pom files first for dependency caching
COPY pom.xml .
COPY backend/pom.xml backend/
COPY frontend/pom.xml frontend/

# Pre-fetch Maven dependencies (cache layer)
RUN mvn dependency:go-offline -pl backend -am -DskipTests -q 2>/dev/null || true

# Copy source for both modules
COPY backend/src backend/src
COPY frontend/src frontend/src
COPY frontend/package.json frontend/yarn.lock frontend/
COPY frontend/angular.json frontend/tsconfig.json frontend/tsconfig.app.json \
     frontend/tsconfig.spec.json frontend/proxy.conf.json frontend/

# Full build: frontend-maven-plugin handles yarn install + ng build,
# then packages Angular dist as static resources inside the backend jar.
RUN mvn -DskipTests clean package -q

# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/backend/target/*.jar app.jar
# Default server.port is 8090 (see backend application.yaml).
EXPOSE 8090
CMD ["java", "-jar", "app.jar"]
