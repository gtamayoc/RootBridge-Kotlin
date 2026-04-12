# Conventions Overview

## Code Style
- **Kotlin**: Follow official Kotlin coding conventions, use `val`/`var` appropriately, prefer expression bodies.
- **Naming**: PascalCase for classes/objects, camelCase for functions/properties, UPPER_SNAKE_CASE for constants.
- **Formatting**: Use `ktlint` with Android style; line length 100.

## Architecture Conventions
- Clean Architecture layers as defined in `AGENTS.md`.
- Dependency direction: UI → Domain → Data → Core.
- Use `object` for singletons (e.g., `RootChecker`).
- Interfaces for repositories and data sources.

## Coroutines & Flow
- Use `viewModelScope.launch` for UI‑side work.
- Use `withContext(Dispatchers.IO)` for blocking I/O.
- Expose UI state via `StateFlow` (private `MutableStateFlow`).
- Use `Flow` for streaming large result sets, limit with `MAX_RESULTS`.

## Testing Conventions
- Unit tests in `app/src/test/java` using JUnit5 and MockK.
- Instrumented tests in `app/src/androidTest/java` using AndroidX Test.
- Keep tests focused on a single use‑case; mock external dependencies.
- Aim for >80% coverage; run with Gradle `test` task.

## Logging & Error Handling
- Use `Timber` for logging; tag with class name.
- Wrap risky operations in `try/catch` and surface errors via sealed UI state.

## Security & Anti‑Manipulation
- Detect hooking frameworks (Frida, Xposed) in `RootChecker`.
- Validate checksums for critical binaries.
- Randomize offsets for memory scans.
