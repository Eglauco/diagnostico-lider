# syntax=docker/dockerfile:1
# =====================================================================
# Diagnóstico do Líder — imagem única (Angular embutido no Spring Boot)
# Railway detecta este Dockerfile na raiz e faz o build automaticamente.
# =====================================================================

# ---------- 1) Front: Angular 22 ----------
FROM node:26-slim AS front
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# ---------- 2) Back: Spring Boot 4.2 / Java 27, com o front em /static ----------
FROM eclipse-temurin:27-jdk AS back
WORKDIR /app/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
COPY --from=front /app/frontend/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package

# ---------- 3) Execução ----------
FROM eclipse-temurin:27-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=back /app/backend/target/diagnostico-lider.jar app.jar
USER app

# Em produção o PDF sai pelo serviço Gotenberg (rede privada do Railway).
ENV PDF_MOTOR=gotenberg \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
