---
description: "Project Initialization and Base Module Setup"
wave: 1
depends_on: []
files_modified:
  - gradle/libs.versions.toml
  - build.gradle.kts
  - app/build.gradle.kts
  - settings.gradle.kts
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/gtc/rootbridgekotlin/MainActivity.kt
  - app/src/main/java/com/gtc/rootbridgekotlin/AppNavHost.kt
  - .github/workflows/ci.yml
autonomous: true
---

# Phase 1: Project Initialization

## Implementation Plan

```xml
<task>
  <description>Create the Gradle Version Catalog (`libs.versions.toml`) to centrally manage dependencies.</description>
  <action>
    Create or update the file `gradle/libs.versions.toml`.
    Define the following [versions]:
    - agp = "8.2.0" # Compatible with Android Studio Iguana (2023.2.1) and newer
    - kotlin = "1.9.22"
    - coreKtx = "1.12.0"
    - lifecycleRuntimeKtx = "2.7.0"
    - activityCompose = "1.8.2"
    - composeBom = "2024.02.00"

    Define the [libraries] using these versions. Include:
    - `androidx-core-ktx`
    - `androidx-lifecycle-runtime-ktx`
    - `androidx-activity-compose`
    - `androidx-compose-bom`
    - `androidx-ui`
    - `androidx-ui-graphics`
    - `androidx-ui-tooling-preview`
    - `androidx-material3`

    Define [plugins]:
    - `androidApplication` with id `com.android.application` and version `agp`
    - `jetbrainsKotlinAndroid` with id `org.jetbrains.kotlin.android` and version `kotlin`
  </action>
  <read_first>
    - .planning/phases/01-project-initialization/01-CONTEXT.md
  </read_first>
  <acceptance_criteria>
    - `cat gradle/libs.versions.toml | grep -q '\[versions\]'`
    - `cat gradle/libs.versions.toml | grep -q 'agp = "'`
  </acceptance_criteria>
</task>

<task>
  <description>Setup ROOT project Gradle scripts (`settings.gradle.kts` and `build.gradle.kts`).</description>
  <action>
    Check if `settings.gradle.kts` and `build.gradle.kts` exist. If they do, modify them; otherwise, create them.
    
    Ensure `settings.gradle.kts` configures the plugin management and includes the `:app` module:
    ```kotlin
    pluginManagement {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
        }
    }
    rootProject.name = "RootBridge-Kotlin"
    include(":app")
    ```

    Ensure the root `build.gradle.kts` contains:
    ```kotlin
    buildscript {
        dependencies {
            // ... required classpath configuration if any
        }
    }
    plugins {
        alias(libs.plugins.androidApplication) apply false
        alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    }
    ```
  </action>
  <read_first>
    - settings.gradle.kts
    - build.gradle.kts
  </read_first>
  <acceptance_criteria>
    - `cat settings.gradle.kts | grep -q 'include(":app")'`
    - `cat build.gradle.kts | grep -q 'alias(libs.plugins.androidApplication)'`
  </acceptance_criteria>
</task>

<task>
  <description>Create the `:app` module build file and Android Manifest.</description>
  <action>
    Create `app/build.gradle.kts` using the Android Application plugin and Kotlin Android plugin.
    Set the compileSdk to 36, minSdk to 24, targetSdk to 35.
    Set applicationId to `com.gtc.rootbridgekotlin`.
    Enable `buildFeatures { compose = true }`.
    Set `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }`.
    Add dependencies referring to `libs.*` from the version catalog.

    Create `app/src/main/AndroidManifest.xml` with `<manifest package="com.gtc.rootbridgekotlin">` and an `<application>` and `<activity android:name=".MainActivity" android:exported="true">` with the `MAIN` action and `LAUNCHER` category.
  </action>
  <read_first>
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
  </read_first>
  <acceptance_criteria>
    - `cat app/build.gradle.kts | grep -q 'compileSdk = 36'`
    - `cat app/build.gradle.kts | grep -q 'minSdk = 24'`
    - `cat app/src/main/AndroidManifest.xml | grep -q 'com.gtc.rootbridgekotlin'`
  </acceptance_criteria>
</task>

<task>
  <description>Initialize Clean Architecture Kotlin package structure and Compose UI.</description>
  <action>
    Create the folders:
    - `app/src/main/java/com/gtc/rootbridgekotlin/ui`
    - `app/src/main/java/com/gtc/rootbridgekotlin/core`
    - `app/src/main/java/com/gtc/rootbridgekotlin/domain`
    - `app/src/main/java/com/gtc/rootbridgekotlin/data`
    - `app/src/main/java/com/gtc/rootbridgekotlin/overlay`

    Create `app/src/main/java/com/gtc/rootbridgekotlin/MainActivity.kt` taking advantage of `ComponentActivity()` and loading an `AppNavHost()` inside `setContent`.
    
    Create `app/src/main/java/com/gtc/rootbridgekotlin/AppNavHost.kt` with a basic Compose `@Composable fun AppNavHost() { Text("RootBridge Initialized") }` to serve as a placeholder.
  </action>
  <read_first>
    - app/src/main/java/com/gtc/rootbridgekotlin/MainActivity.kt
    - app/src/main/java/com/gtc/rootbridgekotlin/AppNavHost.kt
  </read_first>
  <acceptance_criteria>
    - `ls app/src/main/java/com/gtc/rootbridgekotlin/ui`
    - `ls app/src/main/java/com/gtc/rootbridgekotlin/core`
    - `ls app/src/main/java/com/gtc/rootbridgekotlin/domain`
    - `cat app/src/main/java/com/gtc/rootbridgekotlin/MainActivity.kt | grep -q 'ComponentActivity'`
  </acceptance_criteria>
</task>

<task>
  <description>Create the GitHub Actions workflow stub.</description>
  <action>
    Create the file `.github/workflows/ci.yml`.
    Provide a standard Android compilation workflow template:
    - runs `on: push` and `on: pull_request`
    - `runs-on: ubuntu-latest`
    - Check out the repository (`actions/checkout@v4`)
    - Set up JDK 17 (`actions/setup-java@v4`)
    - Initialize gradle (`gradle/actions/setup-gradle@v3`)
    - Run `./gradlew assembleDebug`
  </action>
  <read_first>
    - .github/workflows/ci.yml
  </read_first>
  <acceptance_criteria>
    - `cat .github/workflows/ci.yml | grep -q 'assembleDebug'`
    - `cat .github/workflows/ci.yml | grep -q 'setup-java'`
  </acceptance_criteria>
</task>
```

## Verification
- All files exist and are populated correctly.
- Ensure the project can successfully evaluate Gradle scripts (no syntax issues).

## must_haves
- Version catalog is used for all Android dependency versions.
- Standard project layouts are explicitly initialized.
- GitHub Action CI file created.
