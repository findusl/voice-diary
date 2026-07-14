# Agent Instructions

- The `androidApp` module is the thin Android application. Android entry points,
  the application manifest, launcher resources, and instrumented tests belong there.
- The JVM client supports Linux and macOS. Native desktop packaging is limited to
  DEB and DMG unless another platform is explicitly added.
- The `shared` module contains only code shared between client and server, such as data models.
- Client-only logic must live in the `composeApp` module.
- Server-only code must reside in the `server` module.
- New features should include tests, but do not write tests for log output.
- Compose UI tests should not use `waitForIdle`; prefer `waitUntil` or one of its variants.
- Group commits by topic (for example, documentation-only changes should be separate from code changes; refactors/renames should be separate from behavioral changes).

Before committing, run `./gradlew ktlintFormat`.

To verify changes, run `./gradlew check`.

CI runs project checks, builds the Android app and test APKs, and packages the
Linux DEB. Testing steps used for full local validation on macOS:

1. `./gradlew check`
2. `nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > /tmp/voice-diary-emulator.log 2>&1 &`
3. `./gradlew :composeApp:run` (stop after startup is confirmed)
4. `./gradlew :server:run` (stop after startup is confirmed)
5. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 wait-for-device`
6. `for i in {1..30}; do boot=$(~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r'); if [ "$boot" = "1" ]; then echo "booted"; exit 0; fi; sleep 2; done; echo "not_booted"; exit 1`
7. `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest`
8. `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:installDebug`
9. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am start -n de.lehrbaum.voiry/de.lehrbaum.voiry.MainActivity`
10. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu kill`

If no code changes were made (documentation-only or metadata-only changes), the full test cycle above is not required.
