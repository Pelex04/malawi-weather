# ── Build stage ──────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY api/pom.xml .
RUN mvn dependency:go-offline -q
COPY api/src ./src
RUN mvn clean package -DskipTests -q

# ── Run stage ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/weather-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
