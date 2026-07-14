# Voice Diary

Voice Diary is a self-hosted voice journal built with Kotlin Multiplatform and
Compose Multiplatform. It has Android and macOS clients backed by a Ktor server
that stores metadata in SQLite and recordings as WAV files.

There is currently no iOS target.
The JVM client and development workflow support macOS only; Linux and Windows
desktop hosts are not supported.

## Project layout

- `androidApp` is the thin Android application and contains the Android entry
  point, manifest, launcher resources, and instrumented tests.
- `composeApp` contains client-only UI and behavior shared by Android and macOS,
  plus platform implementations for those two targets.
- `shared` contains only models and contracts shared by clients and the server.
- `server` contains the Ktor API and persistence layer.

## Requirements

- macOS on Intel or Apple silicon
- JDK 21
- Android Studio with Android SDK Platform 37 and an API 37 emulator image
- Docker Desktop, if running the server in a container
- Optional macOS transcription: `whisper-cli` from
  [whisper.cpp](https://github.com/ggerganov/whisper.cpp) available on `PATH`

Android supports API 34 through 37. The Gradle wrapper downloads the required
Gradle distribution; a system Gradle installation is not required.

## Run locally

Start the server on port 8888:

```bash
./gradlew :server:run
```

Start the macOS client in a second terminal:

```bash
./gradlew :composeApp:run
```

Install the Android app on a running emulator or connected device:

```bash
./gradlew :androidApp:installDebug
```

The macOS client connects to `http://localhost:8888`. Android emulator builds
connect to the host through `http://10.0.2.2:8888`. To use a physical Android
device, put the server's LAN address in `local.properties` and rebuild:

```properties
androidBackendUrl=http://192.168.1.10:8888
```

Use `desktopBackendUrl` for a macOS-only override, or `backendUrl` to apply
the same override to both clients.

The Android app requests microphone access when recording starts. When required
by API 37, it also requests local-network access before connecting to a server on
the LAN. The self-hosted development connection intentionally uses cleartext HTTP,
so expose the server only on a trusted network or place it behind a secured proxy.

On macOS, transcription is enabled when `whisper-cli` is available. The first
transcription downloads the large `ggml-large-v3-turbo` model into the app's data
directory and reuses it afterwards.

## Run the server with Docker

Build and start the server:

```bash
docker compose up --build -d
```

Check its status and health endpoint:

```bash
docker compose ps
curl --fail http://localhost:8888/health
```

The container stores its SQLite database and uploaded WAV files in the repository's
`data/` directory, mounted at `/data`. Back up that directory before replacing or
moving a production instance.

Stop the server with `docker compose down`. That command leaves the persisted data
in place.

## Package the macOS client

Build the native DMG package on macOS:

```bash
./gradlew :composeApp:packageDmg
```

DMG is the only supported desktop distribution format.

## Verify changes

On macOS, format and run the project checks:

```bash
./gradlew ktlintFormat
./gradlew check
```

With an API 37 emulator running, execute Android instrumented tests with:

```bash
./gradlew :androidApp:connectedDebugAndroidTest
```
