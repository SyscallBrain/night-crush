# Contributing to NightCrush

Thanks for your interest in improving NightCrush. Bug reports, translations, accessibility feedback and pull requests are all welcome.

## Reporting bugs

Please open an issue with:

- what you did, what you expected and what happened instead;
- the game version (shown in **Settings → About**), your device model and Android version;
- for crashes, the file from **Settings → About → Export diagnostic**. It holds only the stack trace and basic device information; read it before attaching.

## Development setup

1. Install a JDK (17 or newer; the project is built with JDK 21) and the Android SDK with platform `android-37.1`.
2. Point Gradle at them:

   ```bash
   export JAVA_HOME=/path/to/jdk
   export ANDROID_HOME=/path/to/android-sdk
   ```

3. Build and test:

   ```bash
   ./gradlew :core:test :leveltool:test :app:testDebugUnitTest
   ./gradlew :app:assembleDebug
   ```

   Or run everything the CI runs, including lint and certificate replay:

   ```bash
   bash tools/verify.sh
   ```

No emulator is needed. Game rules belong in `:core` and are tested on the JVM. The screens can be rendered with Robolectric (`./gradlew :app:testDebugUnitTest -PncShots --tests '*ScreenshotTest*'`) and are checked on a real device.

## Ground rules

- All code, identifiers, comments and log messages are in **English**. All user-visible text lives in `strings.xml` (English default) and `values-pt/strings.xml` (European Portuguese). No visible text hardcoded in Kotlin.
- `:core` is pure Kotlin/JVM: no Android, no wall clock, no platform `Random`. Use the versioned `SplitMix64` only.
- **One engine only.** The game, the solver and the certificate verifier use the same `:core`. Never duplicate rule logic.
- Any observable change to the engine bumps `engineVersion` and forces certificates to be regenerated.
- The app asks for the `VIBRATE` permission only. No internet permission, no ads, no accounts, no third-party SDKs.
- Write tests in `:core` and `:leveltool` for every rule.
- Run `bash tools/verify.sh` before every commit that touches rules or content.

## Pull requests

- Create a branch from `main` and keep each pull request focused on one change.
- Commit messages are short, imperative and in English.
- New content must pass `validate --strict` and ship with reproducible certificates.
