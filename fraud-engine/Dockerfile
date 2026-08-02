# ============================================
# Stage 1: BUILD \u2014 compile the JAR inside Docker
# ============================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# 1. Copy the Maven wrapper and parent POM first
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 2. Copy all module POMs
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY fraud-engine/pom.xml fraud-engine/pom.xml

# 3. Download dependencies
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -pl fraud-engine -am -B

# 4. Copy source code
COPY fraud-engine/src fraud-engine/src

# 5. Build the JAR
RUN ./mvnw package -pl fraud-engine -am -DskipTests -B

# ============================================
# Stage 2: RUN \u2014 tiny runtime image
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/fraud-engine/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
