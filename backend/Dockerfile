# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# dependências em camada separada para cache do Docker
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN addgroup --system onda && adduser --system --ingroup onda onda
USER onda

COPY --from=builder /app/target/marketplace-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-jar", "app.jar"]
