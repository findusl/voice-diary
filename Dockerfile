FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon

FROM eclipse-temurin:25-jre-jammy
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/server/build/libs/*-all.jar app.jar
ENV VOICE_DIARY_DB_PATH=/data
VOLUME /data
EXPOSE 8888
HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
    CMD curl --fail --silent --show-error http://localhost:8888/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
