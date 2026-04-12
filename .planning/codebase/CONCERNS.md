# Concerns Overview

## Security & Anti‑Manipulation
- Detect hooking frameworks (Frida, Xposed, ptrace) in `RootChecker`.
- Verify binary checksums at startup.
- Randomize memory scan offsets and use decoy values.
- Enforce root detection before privileged operations.

## Performance
- Heavy memory scanning delegated to NDK native code.
- Use parallel coroutines and `Flow` to stream results.
- Limit results with `MAX_RESULTS = 1000` and implement paging.
- Cache scan results where appropriate.

## Compatibility
- Minimum SDK 24, target SDK 35, compile SDK 36.
- Support both ROOT and NO‑ROOT environments via `MemoryProvider` abstraction.
- Ensure graceful fallback when root access is unavailable.

## Reliability
- Handle `SecurityException` and permission denials gracefully.
- Provide clear error states via sealed UI state classes.
- Include comprehensive unit and instrumented tests for edge cases.

## Maintainability
- Follow Clean Architecture layers.
- Keep repository interfaces small and mockable.
- Document public APIs and module boundaries.
- Use `Timber` for structured logging.
