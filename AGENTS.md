# Agent Instructions

- The `shared` module contains only code shared between client and server, such as data models.
- Client-only logic must live in the `composeApp` module.
- Server-only code must reside in the `server` module.
- New features should include tests, but do not write tests for log output.

Before commit please run `./gradlew ktlintFormat`
To verify changes run `./gradlew checkAgentsEnvironment`
