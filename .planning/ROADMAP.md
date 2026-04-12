# ROADMAP

## Phase 1: Project Initialization
- **Goal**: Set up project structure, modules, and basic Android app skeleton.
- **Deliverables**:
  - Gradle project with app module.
  - Basic Compose UI with a placeholder screen.
  - Clean Architecture folder layout (ui, core, domain, data, overlay).
  - Initial CI workflow stub.

## Phase 2: SETUP BASE Y ARQUITECTURA MODULAR
- **Goal**: Establecer una base compilable con separación clara entre ROOT y NO-ROOT desde el inicio.
- **Deliverables**:
  - **UI**: Jetpack Compose + Navigation Compose, estructura inicial de `MainActivity`, `AppNavHost`, `BaseViewModel` usando `StateFlow`.
  - **Domain**: Definición de interfaces clave: `ProcessInteractor`, `MemoryInteractor`, `OverlayInteractor`.
  - **Data**: Módulos separados `data-root`, `data-noroot`; interfaces comunes en `data-common`.
  - **Core**: Configuración de Hilt con bindings por flavor (`@Qualifier RootMode`, `@Qualifier NoRootMode`), Logger con Timber.
  - **Overlay**: Servicio base `OverlayService` (Foreground Service).
  - **Gradle / Multi-module**: `:app`, `:core`, `:domain`, `:data-root`, `:data-noroot`, `:overlay`; configuración de CMake para soporte C++ futuro.

## Phase 3: ESTRATEGIA DE EJECUCIÓN ROOT VS NO-ROOT
- **Goal**: Determinar dinámicamente el modo de ejecución y enrutar lógica correctamente.
- **Deliverables**:
  - **Core**: `RootChecker` (verificación con `su`, `which su`, permisos), `ExecutionModeProvider` (`ExecutionMode.ROOT / ExecutionMode.NO_ROOT`).
  - **Domain**: Caso de uso `DetectExecutionModeUseCase`.
  - **Data**: Implementaciones `RootProcessRepository` y `NoRootProcessRepository`.
  - **UI**: `SplashScreen` que decide modo y lo propaga al ViewModel.
  - **Overlay**: Mostrar estado actual (ROOT / NO ROOT).

## Phase 4: ABSTRACCIÓN DE PROCESOS Y ACCESO A MEMORIA
- **Goal**: Unificar acceso a procesos independientemente del modo.
- **Deliverables**:
  - **Domain**: Interfaces `ProcessRepository` y `MemoryRepository`.
  - **Data (ROOT)**: Uso de `/proc`, `ptrace`, comandos `su`.
  - **Data (NO-ROOT)**: `UsageStatsManager`, `AccessibilityService`.
  - **Core**: Mapper común `ProcessMapper`.
  - **UI**: Lista de procesos detectados.

## Phase 5: INTEGRACIÓN C++ (NDK) PARA OPERACIONES CRÍTICAS
- **Goal**: Delegar operaciones de bajo nivel a C++ para performance y acceso a memoria.
- **Deliverables**:
  - **Core**: Configuración NDK (`CMakeLists.txt`, ABI filters `arm64-v8a`).
  - **Data (ROOT)**: Bridge JNI para `ptrace` y `process_vm_readv`.
  - **Domain**: Caso de uso `NativeMemoryUseCase`.
  - **UI**: Indicador de uso de modo nativo.
  - **Overlay**: Lectura en tiempo real (polling optimizado).

## Phase 6: OVERLAY INTELIGENTE (INTERACCIÓN EN TIEMPO REAL)
- **Goal**: Permitir inspección/modificación en tiempo real sobre otras apps.
- **Deliverables**:
  - **Overlay**: `OverlayService` con `WindowManager`, layout flotante draggable, `OverlayViewModel`.
  - **UI**: Compose embebido en overlay (`ComposeView`).
  - **Domain**: Casos de uso `ScanMemoryUseCase`, `UpdateValueUseCase`.
  - **Data**: ROOT acceso directo a memoria; NO-ROOT simulaciones/hooks limitados.
  - **Core**: Scheduler con `CoroutineScope` + `Dispatchers.IO`.

## Phase 7: MOTOR DE ESCANEO DE MEMORIA
- **Goal**: Implementar búsqueda eficiente de valores (tipo GameGuardian-like).
- **Deliverables**:
  - **Domain**: `MemoryScanEngine` (búsqueda por valor exacto, filtrado incremental).
  - **Data (ROOT + C++)**: Escaneo por rangos de memoria `/proc/[pid]/maps`.
  - **Core**: Estrategias First Scan / Next Scan (refinamiento).
  - **UI**: Input dinámico de valores.
  - **Overlay**: Mostrar resultados en lista flotante.

## Phase 8: PERSISTENCIA Y ESTADO DE SESIÓN
- **Goal**: Mantener resultados y configuraciones.
- **Deliverables**:
  - **Data**: Room (`ScanSession`, `MemoryResult`).
  - **Domain**: `SaveSessionUseCase`, `LoadSessionUseCase`.
  - **Core**: Serialización eficiente (`Kotlinx Serialization`).
  - **UI**: Historial de scans.
  - **Overlay**: Restauración rápida de sesiones.

## Phase 9: OPTIMIZACIÓN Y SEGURIDAD
- **Goal**: Hacer la app robusta y eficiente.
- **Deliverables**:
  - **Core**: Manejo de errores (`Result<T>`), retry policies.
  - **Data**: Protección anti-crash, validación de direcciones de memoria.
  - **Domain**: Rate limiting en escaneo.
  - **UI**: Feedback de estados (`loading`, `error`).
  - **Overlay**: Minimización de consumo de CPU.

## Phase 10: SOPORTE AVANZADO NO-ROOT (OPCIONAL)
- **Goal**: Maximizar capacidades en entornos sin root.
- **Deliverables**:
  - **Data (NO-ROOT)**: `AccessibilityService` (lectura de UI), `MediaProjection` (captura pantalla).
  - **Domain**: `ScreenAnalysisUseCase`.
  - **Core**: Integración con ML Kit (OCR).
  - **UI**: Highlight de valores detectados.
  - **Overlay**: Superposición inteligente sobre UI detectada.

## Phase 11: BUILD VARIANTS Y DISTRIBUCIÓN
- **Goal**: Separar claramente builds ROOT vs NO-ROOT.
- **Deliverables**:
  - **Core**: Product flavors `root`, `noroot`, `hybrid`.
  - **Data**: Binding automático por flavor (Hilt modules).
  - **UI**: Feature flags visibles.
  - **Overlay**: Configuración adaptable por build.
  - **Seguridad**: Ofuscación con R8, firma segura.