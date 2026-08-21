FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew gradle.properties settings.gradle.kts settings-server.gradle.kts build.gradle.kts ./
COPY shared ./shared
COPY server ./server

RUN chmod +x ./gradlew && ./gradlew \
    --settings-file settings-server.gradle.kts \
    -Paita.serverOnly=true \
    --no-daemon \
    --no-configuration-cache \
    :server:buildFatJar

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV AITA_PORT=8080
ENV AITA_ENV=production
EXPOSE 8080

COPY --from=builder /workspace/server/build/libs/aita-server-all.jar /app/aita-server-all.jar

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC", "-XX:+ExitOnOutOfMemoryError", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/aita-server-all.jar"]
