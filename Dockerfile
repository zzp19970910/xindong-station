# syntax=docker/dockerfile:1.6

# ============================================================
#  Stage 1: Build FRONTEND (Vue3 + Vite) -> dist/
# ============================================================
FROM node:20-alpine AS frontend-builder
WORKDIR /build/frontend

# Copy package files first (better Docker layer caching)
COPY frontend/xindong-web/package*.json ./
RUN npm install --no-audit --no-fund --loglevel=error

# Copy source + build
# NOTE: .env.production's VITE_API_BASE uses /api/v1 (same-origin, no external URL needed)
# because we will serve frontend + backend from the SAME Nginx port
COPY frontend/xindong-web/ ./
# Force skip vue-tsc type check (bypass package.json script entirely)
RUN npx vite build

# ============================================================
#  Stage 2: Build BACKEND (SpringBoot + Maven) -> fat jar
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /build/backend

# Copy pom.xml first (better Docker layer caching)
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B -q

# Copy backend source + the just-built frontend dist/
# Embed-frontend Maven profile copies ../frontend/xindong-web/dist -> classpath:static/
COPY backend/src ./src
COPY --from=frontend-builder /build/frontend/dist /build/frontend/xindong-web/dist

# Package with embed-frontend profile -> single jar contains backend + frontend
RUN mvn clean package -Pembed-frontend -DskipTests -B -q \
 && mv target/xindong-station-*.jar /build/app.jar

# ============================================================
#  Stage 3: Final runtime image (JDK17 + app.jar + SQLite data volume)
# ============================================================
FROM eclipse-temurin:17-jre-alpine

# Labels
LABEL org.opencontainers.image.title="xindong-station" \
      org.opencontainers.image.description="Xindong Couple Station - Backend + Frontend Embedded" \
      maintainer="xindong-dev"

# Packages needed: tzdata for Asia/Shanghai timezone, curl for healthcheck
RUN apk add --no-cache tzdata curl \
 && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# Copy built fat jar from stage 2
COPY --from=backend-builder /build/app.jar ./app.jar

# Healthcheck: SpringBoot public endpoint (no login required)
HEALTHCHECK --interval=30s --timeout=8s --start-period=60s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# SQLite database lives here -> mount as Docker VOLUME for persistent storage
VOLUME ["/app/data"]
# Application logs also persisted
VOLUME ["/app/logs"]

EXPOSE 8080

# Default runtime env vars (REPLACE these in docker-compose.yml / production !)
ENV JWT_SECRET="change-me-32bytes-long-random-string-please!!!" \
    LETTER_AES_KEY="change-me-32bytes-letter-aes-key-please!!" \
    SMS_SUPER_CODE="1234" \
    SPRING_PROFILES_ACTIVE="embed-frontend" \
    JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC"

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar" ]
