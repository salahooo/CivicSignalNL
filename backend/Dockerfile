FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 civic && adduser -S -D -H -u 10001 -G civic civic
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/civic-signal-api-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
