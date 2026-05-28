# XNAgent

Android AI-agent chat app. Kotlin + Jetpack Compose (Material3), single `:app` module.

## Build & Run

- **JDK 21 required** (`gradle/gradle-daemon-jvm.properties`); `org.gradle.java.installations.auto-download=false`
- `.\gradlew.bat assembleDebug` — build
- `.\gradlew.bat installDebug` — build & install on device/emulator
- `.\gradlew.bat test` — unit tests (placeholder only currently)
- `.\gradlew.bat connectedAndroidTest` — instrumented tests (requires device/emulator)

## Toolchain

- AGP **9.1.1**, Kotlin **2.2.10**, Compose BOM **2026.02.01**
- `compileSdk` uses AGP 9.x DSL: `release(36) { minorApiLevel = 1 }` — don't revert to plain integer
- `targetSdk = 36`, `minSdk = 24`; Java source/target: **11** (not 21)
- `buildConfig = true` enabled in `app/build.gradle.kts`
- Aliyun Maven mirror configured in `settings.gradle.kts:20` for faster access in China
- Version catalog at `gradle/libs.versions.toml` — add deps there, reference via `libs.*`

## Architecture

### App entry & navigation
- `App` (`@HiltAndroidApp`) — sets `kotlinx.coroutines.debug` on; Hilt modules in `App.kt`: `AppModule`, `NetworkModule`, `DataModule`
- `MainActivity` (`@AndroidEntryPoint`) → `MainViewModel` routes between `HomeScreen`, `SettingsScreen`, `LoginScreen`
- `MainViewModel` observes `AuthRepository.session` StateFlow to drive `MainDestination`

### Home screen (MVI-like pattern)
- `HomeScreen` → `HomeViewModel` → `data/repository/HomeRepository`
- `HomeIntent` sealed class: `Initialize`, `SendMessage`, `SelectModel`, `SelectSession`, `CreateNewChat`, `ToggleDeepThinking`, `EditUserMessage`, `RegenerateAssistantMessage`, `DeleteMessage`, `FavoriteMessage`
- `HomeViewModel.uiState`: `StateFlow<HomeUiState>` — ViewModel is the single source of truth
- `HomeScreenContent` is a pure Composable (no Hilt dependency) for Preview support

### Repositories (duplicate interfaces — gotcha)

There are **two** identical `HomeRepository` interfaces + implementations side by side:
- **Active:** `data/repository/HomeRepository` / `data/repository/HomeRepositoryImpl` (used by `HomeViewModel`)
- **Stale:** `ui/screen/home/HomeRepository` / `ui/screen/home/HomeRepositoryImpl` (same code, likely old copy — don't use)

When adding new methods, only modify the `data/repository/` pair.

Other repositories:
- `data/repository/AuthRepository` / `AuthRepositoryImpl` — login, register (captcha-based), guest mode, logout
- `data/repository/FavoriteRepository` / `FavoriteRepositoryImpl` — favorite messages (stored in JSON file via assets)

### Data layer
- **Room** (`XNDatabase`): `Session` + `ChatMessage` entities, `ChatDao` — schema exists and is used for local storage
- **Remote API**: Retrofit + `kotlinx.serialization`, base URL `https://xiaoniu.tech/` (`NetworkConfig.BASE_URL`)
- **SSE streaming**: Dedicated OkHttp client in `NetworkModule` (no `HttpLoggingInterceptor` — it buffers response body and breaks SSE); uses `HttpStreamingLoggingInterceptor` instead
- **Dual storage**: Chat messages are persisted both locally (Room) and remotely; `syncLocalChatsToRemote()` runs on login
- **Model config** loaded from `assets/model_config.json` (local fallback); servers list from `GET /api/chat/models`

### Auth
- `AuthSession` (sealed-like data class with `isGuest`, `isLoggedIn`, `canEnterHome`)
- `AuthStore` persists token to encrypted SharedPreferences
- Guest mode: max **10 messages** per session (`GUEST_USER_MESSAGE_LIMIT`)

### UI layer
- `ui/screen/home/` — HomeScreen, HomeViewModel, HomeIntent
- `ui/screen/login/` — LoginScreen, LoginViewModel, LoginIntent
- `ui/screen/settings/` — SettingsScreen, SettingsViewModel
- `ui/component/` — ChatMessageList, ChatInputBar, DropdownSelector, MarkdownText, UserAvatar
- `ui/model/` — HomeUiState, ChatMessage, ChatUiModel, ModelUiModel, AgentMode, SessionUiModel, SendToLLMResult
- **Markwon** library used for Markdown rendering in ChatMessageList

## Conventions

- Comments, KDoc, and UI strings are in **Chinese** — keep consistent
- Compose + Material3 throughout; `@OptIn(ExperimentalMaterial3Api::class)` for experimental APIs
- Hilt + KSP enabled; Hilt modules define singletons in `App.kt`
- `doc/api/` contains API documentation (chat and auth endpoints)
- `/doc/tasks/` is gitignored — task artifacts should not be committed
- `kotlinx.serialization` plugin is enabled globally
