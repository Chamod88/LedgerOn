# ============================================
# Stage 1: BUILD — compile the JAR inside Docker
# ============================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# 1. Copy the Maven wrapper and parent POM first (for dependency caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 2. Copy both module POMs (Maven needs all modules defined in parent POM)
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

# 3. Fix Windows line endings and download dependencies (cached if POMs don't change)
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -pl ledger-service -am -B

# 4. Now copy the actual source code
COPY ledger-service/src ledger-service/src

# 5. Build the JAR (skip tests — tests run in CI, not in Docker build)
RUN ./mvnw package -pl ledger-service -am -DskipTests -B

# ============================================
# Stage 2: RUN — tiny runtime image
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the built JAR from Stage 1
COPY --from=builder /workspace/ledger-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
