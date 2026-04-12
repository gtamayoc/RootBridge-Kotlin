# Phase 1: Project Initialization Context

## Decisions
- **Gradle Setup Strategy**: Use a strict Version Catalog (`gradle/libs.versions.toml`) desde el primer día para prevenir desajustes de dependencias (confirmado: Sí).
- **Initial Entry Point Setup**: Configuración personalizada desde cero. Se descartan las plantillas pesadas de Android Studio para inicializar directamente la estructura de carpetas de la Clean Architecture (`ui`, `core`, `domain`, `data`, `overlay`) en el módulo `:app`.
- **CI Pipeline**: Se utilizará GitHub Actions para el flujo de CI inicial.

## Canonical Refs
- `.planning/ROADMAP.md`
- `AGENTS.md`
