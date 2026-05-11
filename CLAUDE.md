# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

- JDK 21 required (enforced by `gradle/gradle-daemon-jvm.properties`)
- `./gradlew assembleDebug` — build
- `./gradlew installDebug` — build & install on device/emulator
- `./gradlew test` — unit tests
- `./gradlew connectedAndroidTest` — instrumented tests (requires device/emulator)

## Toolchain Constraints

- AGP 9.1.1, Kotlin 2.2.10, Compose BOM 2026.02.01
- `compileSdk` uses the AGP 9.x DSL: `release(36) { minorApiLevel = 1 }` — do not revert to a plain integer
- `targetSdk = 36`, `minSdk = 24`
- Java source/target compatibility: **11** (not 21)
- Dependencies are managed in the version catalog at `gradle/libs.versions.toml` — always add new deps there and reference via `libs.*`

## Architecture

Package: `tech.xiaoniu.xnagent`

**MVI-like UI layer:**
- `MainActivity` (Hilt entry point) → `HomeScreen` composable
- `HomeViewModel` holds a `MutableStateFlow<HomeUiState>` and processes intents via `dispatch(HomeIntent)`
- `HomeScreenContent` is a pure Composable separated from Hilt/ViewModel — used for Compose Preview
- `HomeRepository` (interface) / `HomeRepositoryImpl` handles data access (currently loads model config from assets, LLM send is a placeholder)

**Data layer:**
- `data/Model.kt` — `ModelConfig`, `ModelProvider`, `Model` (deserialized from `assets/model_config.json` via kotlinx.serialization)
- `data/LLMMessage.kt` — simple role/content message DTO for API calls
- Room database: `data/entity/` (Session, ChatMessage) and `data/dao/ChatDao` — **schema exists but not yet wired into the UI flow**
- `ModelProviderApi` is a sealed class supporting OpenAI-compatible chat endpoints (`/chat/completions`)

**UI structure:**
- `ui/screen/home/` — HomeScreen, HomeViewModel, HomeIntent, HomeRepository
- `ui/component/` — ChatMessageList, ChatInputBar, DropdownSelector (reusable composables)
- `ui/model/` — HomeUiState, ChatMessage, ChatUiModel, ModelUiModel, AgentMode, SendToLLMResult

**State flow:** User action → `HomeIntent` dispatched to ViewModel → ViewModel updates `HomeUiState` → Compose recomposes

## Conventions

- Comments, KDoc, and UI strings are in **Chinese** — keep new code consistent
- Compose + Material3 throughout; use `@OptIn(ExperimentalMaterial3Api::class)` for experimental Material3 APIs
- Hilt + KSP are enabled; Hilt modules define singletons in `App.kt` (`AppModule`)
- Repository pattern with Hilt injection: provide interfaces in Hilt modules, inject implementations into ViewModels
- The `model_config.json` in assets defines available LLM providers and models — each provider has a `baseUrl` and `api` type
