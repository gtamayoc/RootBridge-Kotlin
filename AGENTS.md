# AGENTS.md - RootBridge-Kotlin Development Guide

Android/Kotlin app with Jetpack Compose supporting ROOT and NO-ROOT environments. Clean architecture with UI, core logic, and overlay layers.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run all unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run single unit test class
./gradlew test --tests "com.gtc.rootbridgekotlin.ExampleUnitTest"

# Run single unit test method
./gradlew test --tests "com.gtc.rootbridgekotlin.ExampleUnitTest.addition_isCorrect"

# Debug APK: app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

### Layers

- **UI Layer** (`ui/screen/`, `ui/viewmodel/`, `ui/theme/`) - Compose screens, ViewModels with StateFlow
- **Core Layer** (`core/memory/`, `core/root/`) - Business logic, use `object` for singletons
- **Domain Layer** (`domain/model/`, `domain/usecase/`) - Pure business logic, testable
- **Data Layer** (`data/memory/`, `data/root/`) - Data sources, repositories
- **Overlay Layer** (`overlay/`) - Overlay service and permission management

### Clean Architecture Pattern

```kotlin
// Domain - UseCase
class ScanMemoryUseCase(private val memoryRepository: MemoryRepository) {
    suspend operator fun invoke(pid: Int, value: Int): List<ScanResult> {
        return memoryRepository.scan(pid, value)
    }
}

// Data - Repository Interface
interface MemoryRepository {
    suspend fun scan(pid: Int, value: Int): List<ScanResult>
}

// Data - Implementation
class RootMemoryRepository : MemoryRepository {
    suspend fun scan(pid: Int, value: Int): List<ScanResult> { ... }
}

class NoRootMemoryRepository : MemoryRepository {
    suspend fun scan(pid: Int, value: Int): List<ScanResult> { ... }
}

// Provider - Selector
object MemoryProvider {
    fun get(): MemoryRepository = if (RootChecker.isRooted()) RootMemoryRepository() else NoRootMemoryRepository()
}
```

## ROOT vs NO-ROOT Abstraction (CRITICAL)

Always abstract ROOT vs NO-ROOT behavior to allow testing and anti-cheat protection:

```kotlin
interface MemoryDataSource {
    suspend fun scan(pid: Int, value: Int): List<ScanResult>
    suspend fun write(pid: Int, address: Long, value: Int): Boolean
}
```

## Anti-Manipulation

- Detect hooking: ptrace, frida-server, xposed
- Internal validation: checksums, backend validation
- Randomization: dynamic offsets, decoy values

## Performance & Large Data

- Use NDK (C/C++) for heavy memory scanning
- Parallelize memory reading
- Use Flow for streaming results instead of full lists
- Limit results: `const val MAX_RESULTS = 1000`
- Use Paging or lazy loading for large result sets

```kotlin
fun scan(pid: Int, value: Int): Flow<ScanResult>
```

## Code Style

### Package & Imports

- Package: `package com.gtc.rootbridgekotlin`
- Group: Kotlin stdlib, Android, Jetpack, third-party, project modules

### Naming

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `MemoryViewModel`, `ScanState` |
| Functions/Properties | camelCase | `scanValue()`, `scanState` |
| Constants | UPPER_SNAKE_CASE | `MAX_RESULTS` |
| Object singletons | PascalCase | `MemoryEngine`, `RootChecker` |
| Sealed variants | PascalCase | `ScanState.Idle`, `ScanState.Results` |

### Coroutines & Flow

- Use `viewModelScope.launch` in ViewModels
- Use `withContext(Dispatchers.IO)` for blocking I/O
- Expose state via `StateFlow` with private `MutableStateFlow`
- Collect with `collectAsState()` in Compose

### Error Handling

- Use sealed classes for state with error cases
- Handle `SecurityException` gracefully
- Provide meaningful error messages

### Compose Patterns

- Use `AnimatedContent` for navigation transitions
- Use `Surface` with theme colors for backgrounds
- Enable edge-to-edge with `enableEdgeToEdge()`

## Overlay

- Use SharedFlow/StateFlow for state sharing between overlay and ViewModel
- Build modular widget system: search, value editor, real-time monitor

## Logging

Use Timber for advanced logging:

```kotlin
object AppLogger {
    fun d(tag: String, msg: String) { ... }
    fun e(tag: String, msg: String, throwable: Throwable? = null) { ... }
}
```

Log: scans, errors, memory accesses, root checks

## Testing

- Unit tests: `app/src/test/java/`
- Instrumented tests: `app/src/androidTest/java/`
- Use mocks: `val fakeRepository = FakeMemoryRepository()`
- Test MemoryEngine, RootChecker, UseCases

## Android Config

- Min SDK: 24, Target SDK: 35, Compile SDK: 36

## Dependencies

- Jetpack Compose (Material 3)
- Kotlin Coroutines + Flow
- AndroidX Lifecycle (ViewModel, Service)
- Accompanist Permissions
- Timber (logging)

## Future Modularization

Consider splitting into modules:
- `:app` - Application
- `:core` - Core utilities
- `:domain` - Domain layer
- `:data` - Data layer
- `:overlay` - Overlay feature

## File Locations

- Main: `app/src/main/java/com/gtc/rootbridgekotlin/MainActivity.kt`
- ViewModels: `app/src/main/java/com/gtc/rootbridgekotlin/ui/viewmodel/`
- Build: `app/build.gradle.kts`
- Version catalog: `gradle/libs.versions.toml`
