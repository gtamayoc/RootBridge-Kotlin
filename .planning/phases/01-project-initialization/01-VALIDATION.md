# Phase 1: project-initialization Validation Strategy

## Approach
This phase establishes the baseline Android app module. Validation focuses on build correctness and directory structure verification. The codebase will be validated to compile correctly and have the proper Clean Architecture directories.

## Flow
1. **Build/Compile Check**
   - Run: `./gradlew assembleDebug`
   - Expectation: BUILD SUCCESSFUL output.
2. **Directory Structure Sanity Check**
   - Check existence of: `app/src/main/java/com/gtc/rootbridgekotlin/ui`
   - Check existence of: `app/src/main/java/com/gtc/rootbridgekotlin/core`
   - Check existence of: `app/src/main/java/com/gtc/rootbridgekotlin/domain`
   - Check existence of: `app/src/main/java/com/gtc/rootbridgekotlin/data`
   - Check existence of: `app/src/main/java/com/gtc/rootbridgekotlin/overlay`
   - Expectation: All directories successfully created.
3. **Dependency Integrity**
   - Read `gradle/libs.versions.toml`
   - Verify that Jetpack Compose, Kotlin, and Gradle plugin versions are declared within the version catalog instead of hardcoded in build scripts.
4. **CI Strategy**
   - Read `.github/workflows/ci.yml`
   - Verify it defines a workflow with a trigger (like `on: push`) and an execution step wrapping `./gradlew test` and/or `./gradlew assembleDebug`.
