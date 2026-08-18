#/Dockerfile (same pattern for all services)

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
COPY lib/watchlog-appender-0.1.0.jar /tmp/watchlog-appender-0.1.0.jar
RUN mvn install:install-file \
    -Dfile=/tmp/watchlog-appender-0.1.0.jar \
    -DgroupId=com.datalog \
    -DartifactId=watchlog-appender \
    -Dversion=0.1.0 \
    -Dpackaging=jar

RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 4003
# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:4003/transaction/health || exit 1


ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]