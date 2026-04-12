# Testing Overview

## Unit Testing
- Framework: JUnit5 + Kotlin test extensions.
- Mocking: MockK for mocking dependencies.
- Coroutines: `runTest` from `kotlinx-coroutine-test` for suspend functions.
- Coverage: Use `kover` Gradle plugin, target >80%.
- Location: `app/src/test/java/com/gtc/rootbridgekotlin/`.

## Instrumented Testing
- Framework: AndroidX Test + Espresso.
- UI Tests: Compose testing library (`createComposeRule`).
- Device Requirements: Min SDK 24, use emulator or physical device.
- Location: `app/src/androidTest/java/com/gtc/rootbridgekotlin/`.

## Integration Tests
- Test repository implementations with fake data sources.
- Verify ROOT vs NO‑ROOT behavior using dependency injection.

## Continuous Integration
- GitHub Actions workflow runs `./gradlew test connectedAndroidTest` on each PR.
- Lint checks with `ktlintCheck` and `detekt`.
