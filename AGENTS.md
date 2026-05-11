# XNAgent

Android AI-agent chat app. Kotlin + Jetpack Compose (Material3), single `:app` module.

## Build & Run

- **JDK 21 required** (enforced by `gradle/gradle-daemon-jvm.properties`)
- `.\gradlew.bat assembleDebug` — build
- `.\gradlew.bat installDebug` — build & install on device/emulator
- `.\gradlew.bat test` — unit tests (placeholder only currently)
- `.\gradlew.bat connectedAndroidTest` — instrumented tests (requires device/emulator)

## Toolchain (cutting-edge)

- AGP **9.1.1**, Kotlin **2.2.10**, Compose BOM **2026.02.01**
- `compileSdk` uses the new AGP 9.x DSL: `release(36) { minorApiLevel = 1 }` — don't revert to plain integer
- `targetSdk = 36`, `minSdk = 24`
- Java source/target compatibility: **11** (not 21)

## Architecture

- Package: `tech.xiaoniu.xnagent`
- Entrypoint: `MainActivity` → `HomeScreen` (Compose)
- UI structure: `ui/screen/`, `ui/component/`, `ui/model/`, `ui/theme/`
- `HomeViewModel` exists at `ui/screen/home/HomeViewModel.kt` (currently placeholder); main UI state still lives in `remember { mutableStateOf(...) }` in `HomeScreen.kt`
- No networking/repository layer yet; chat messages are local UI state in `HomeScreen.kt`, and UI models live in `ui/model/ChatUiModel.kt`
- Code comments and UI strings are in **Chinese**

## Conventions

- Compose + Material3 throughout; use `@OptIn(ExperimentalMaterial3Api::class)` for experimental APIs
- KDoc comments on public types; keep Chinese-language comments consistent with existing style
- Version catalog at `gradle/libs.versions.toml` — add dependencies there, reference via `libs.*`
- Hilt + KSP are already enabled in `app/build.gradle.kts`; use existing `libs.hilt*` and `libs.hilt.ext.*` aliases when extending DI/work integration
