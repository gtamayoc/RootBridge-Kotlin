# Phase 1 Research: Project Initialization

## Technical Context
The goal of this phase is to establish the base project skeleton for RootBridge-Kotlin, using Modern Android Development (MAD) practices. 
- **Language**: Kotlin
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts` files).
- **Dependency Management**: Strictly using a `libs.versions.toml` file in the `gradle/` directory.

## Implementation Details
1. **Module Structure**: 
   - A single `app` module will be set up initially.
   - Clean Architecture directory layout will be created inside the main package (`com.gtc.rootbridgekotlin`):
     - `ui`
     - `core`
     - `domain`
     - `data`
     - `overlay`
2. **Compose Entry Point**:
   - `MainActivity.kt` with a `setContent` block.
   - A placeholder `AppNavHost.kt` and a simple placeholder Screen.
3. **Continuous Integration**:
   - `.github/workflows/ci.yml` will be created with steps to check out the repo, set up JDK 17, and run `./gradlew assembleDebug`.

## Validation Architecture
- **Compilation Check**: The project must successfully compile with `./gradlew clean assembleDebug`.
- **Directory Structure Check**: The `ui`, `core`, `domain`, `data`, and `overlay` directories must exist in the expected package path.
- **Dependency Graph**: Dependencies must be correctly sourced from `gradle/libs.versions.toml` without version drifts.
- **CI Configuration**: `.github/workflows/ci.yml` must exist and be a valid YAML workflow for Android.
