FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S -g 10001 bolpay && adduser -S -D -H -u 10001 -G bolpay bolpay
COPY --from=build --chown=bolpay:bolpay /app/target/bolpay-backend-1.0.0.jar app.jar
COPY --chown=bolpay:bolpay docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0555 /app/docker-entrypoint.sh

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=15.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

USER 10001:10001
ENTRYPOINT ["/app/docker-entrypoint.sh"]
