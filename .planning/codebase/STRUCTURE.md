# Structure Overview

## Project Modules (planned)
- `:app` – Android application module containing UI and entry point.
- `:core` – Core utilities, singleton objects, root checking, logging.
- `:domain` – Pure business logic, use‑cases, model definitions.
- `:data` – Repository interfaces and implementations for ROOT and NO‑ROOT memory access.
- `:overlay` – System overlay service, permission handling, widget integration.

## Package Layout
```
com.gtc.rootbridgekotlin
├─ ui
│   ├─ screen
│   ├─ viewmodel
│   └─ theme
├─ core
│   ├─ root
│   └─ memory
├─ domain
│   ├─ model
│   └─ usecase
├─ data
│   ├─ memory
│   └─ repository
└─ overlay
    └─ service
```

## Build Files
- `app/build.gradle.kts` – Application module configuration.
- `core/build.gradle.kts`, `domain/build.gradle.kts`, etc. – Library module configurations.
- `settings.gradle.kts` – Includes all modules.
