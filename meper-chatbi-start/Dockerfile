FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=12.5 -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

COPY target/meper-chatbi-start-*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
