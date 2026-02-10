# Agent Instructions

- The `shared` module contains only code shared between client and server, such as data models.
- Client-only logic must live in the `composeApp` module.
- Server-only code must reside in the `server` module.
- New features should include tests, but do not write tests for log output.
- Compose UI tests should not use `waitForIdle`; prefer `waitUntil` or one of its variants.
- Group commits by topic (for example, documentation-only changes should be separate from code changes; refactors/renames should be separate from behavioral changes).

Before commit please run `./gradlew ktlintFormat`
To verify changes run `./gradlew check`
Testing steps used for validation:
1. `./gradlew check`
2. `nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > /tmp/voice-diary-emulator.log 2>&1 &`
3. `./gradlew :composeApp:run` (stop after startup is confirmed)
4. `./gradlew :server:run` (stop after startup is confirmed)
5. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 wait-for-device`
6. `for i in {1..30}; do boot=$(/Users/sebastian.lehrbaum/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r'); if [ "$boot" = "1" ]; then echo "booted"; exit 0; fi; sleep 2; done; echo "not_booted"; exit 1`
7. `ANDROID_SERIAL=emulator-5554 ./gradlew :composeApp:installDebug`
8. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am start -n de.lehrbaum.voiry/de.lehrbaum.voiry.MainActivity`
9. `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 emu kill`
If no code changes were made (documentation-only or metadata-only changes), the full test cycle above is not required.
