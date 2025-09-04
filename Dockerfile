# --- build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# Copy project sources (including Gradle wrapper)
COPY . .
RUN chmod +x gradlew

# Build the server fat JAR using the wrapper
RUN ./gradlew :server:buildFatJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/server/build/libs/*-all.jar app.jar
ENV VOICE_DIARY_DB_PATH=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

