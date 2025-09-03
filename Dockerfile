# --- build stage ---
FROM gradle:8.5-jdk17 AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/server/build/libs/*-all.jar app.jar
ENV VOICE_DIARY_DB_PATH=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

