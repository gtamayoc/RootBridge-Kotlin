<div align="center">
  <img src="https://raw.githubusercontent.com/gtamayoc/RootBridge-Kotlin/refs/heads/REFACTOR/docs/assets/logo.png" alt="RootBridge-Kotlin Logo" width="200"/>
  <h1>RootBridge Kotlin</h1>
  <p><strong>Advanced Memory Diagnostic & Quality Assurance Tool for Android</strong></p>

  <p>
    <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
    <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4CAF50.svg?logo=android" alt="Jetpack Compose"></a>
    <a href="#"><img src="https://img.shields.io/badge/Architecture-MVVM-orange.svg" alt="Architecture"></a>
    <a href="#"><img src="https://img.shields.io/badge/Status-Active-brightgreen.svg" alt="Status"></a>
  </p>
</div>

---

## 📌 Overview

**RootBridge Kotlin** is a precision engineering tool built to monitor, debug, and validate dynamic memory allocation in Android applications and games. Designed specifically for developers and QA engineers, it provides a stable bridge between standard Android environments and low-level system processes.

It features a dual-engine abstraction capable of operating safely in both **ROOT** and **NO-ROOT** environments, leveraging modern Android architecture to provide real-time memory analytics without compromising system stability.

> ⚠️ **Disclaimer**: This tool is strictly developed for **educational purposes, debugging, and Quality Assurance (QA)** of your own software. Do not use this tool in third-party applications where it violates Terms of Service. The developers assume no liability for misuse.

## ✨ Core Features

* **Dual-Environment Engine**: Automatically detects and operates securely in Root environments (via `su` and native execution) or standard environments (via Virtual Space sandboxing).
* **High-Performance Memory Scanning**: Fast parallel scanning of `Dword`, `Qword`, `Float`, and `Double` values in Heap and Anonymous memory regions.
* **Modern Compose UI**: A fully native, highly responsive dark-themed interface built entirely with Jetpack Compose and Material 3.
* **StateFlow Reactivity**: Uses Kotlin Coroutines and StateFlow to maintain completely synchronous memory states between the background worker and the UI.
* **Floating Overlay Monitor**: Keep track of dynamic values and addresses with a minimal, non-intrusive floating overlay (requires `SYSTEM_ALERT_WINDOW`).

## 📸 Screenshots

> 🔜 **Coming in next release** — QA screenshots using a dedicated sample app (`RootBridge-SampleApp`) are pending and will be added in the next commit.

| Dashboard & Setup | Memory Scanner | Result Analysis | Overlay Monitor |
|:---:|:---:|:---:|:---:|
| *(Pending)* | *(Pending)* | *(Pending)* | *(Pending)* |
| Process selector and environment (Root/No-Root) status. | Type selection and parallel value search. | Discovered Hex addresses and live values. | Real-time tracking of isolated variables. |

## 🏗 Architecture & Tech Stack

This application is built on **Clean Architecture** principles to ensure absolute separation between UI logic and underlying C++ memory engines.

* **UI Layer**: Jetpack Compose, Material 3, `ViewModel`.
* **State Management**: `StateFlow`, Kotlin Coroutines (`Dispatchers.IO` for binary execution).
* **Data / Domain**: Hybrid `MemoryEngine` handling `su` commands, pointer tracking, and NDK binary execution.
* **Service Layer**: `LifecycleService` for maintaining the persistent Floating Overlay.
* **Native**: C++ (NDK) for the raw `mem_scanner` binary.

## 🚀 Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/gtamayoc/RootBridge-Kotlin.git
   ```
2. **Open the project** in Android Studio (Jellyfish or newer recommended).
3. **Build the project**:
   Sync Gradle and build the debug APK.
   ```bash
   ./gradlew assembleDebug
   ```
4. **Deploy**:
   * **Root Devices**: Ensure Magisk/KernelSU is installed and grant SuperUser permission upon app launch.
   * **Non-Root Devices**: You must install RootBridge inside a Virtual Space (e.g., F1VM, VPhoneGaga) alongside your target test app.

## 🕹 Basic Usage (QA / Testing)

1. Launch **RootBridge Kotlin**.
2. Select your target application/process from the dropdown list.
3. Use the **Scan Engine** to search for a known numerical value (e.g., state variables in your test app).
4. Change the value in your test app, then perform a **Next Scan** (Refinement) to isolate the memory pointer.
5. Once isolated, you can use the **Freeze** feature to lock the value, or modify it to validate your app's state handling.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
