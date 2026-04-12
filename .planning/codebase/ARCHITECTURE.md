# Architecture Overview

- **Layered Clean Architecture**
  - UI Layer: Jetpack Compose screens, ViewModels with StateFlow
  - Core Layer: Core utilities, singleton objects (e.g., `RootChecker`, `MemoryEngine`)
  - Domain Layer: Pure business logic, Use‑Cases, model definitions
  - Data Layer: Repository interfaces & implementations for ROOT and NO‑ROOT memory access
  - Overlay Layer: System overlay service, permission handling, widget integration
- **Dependency Flow**: UI → ViewModel → UseCase → Repository → DataSource → NDK native layer
- **Modularity**: Potential future modules (`:app`, `:core`, `:domain`, `:data`, `:overlay`)
