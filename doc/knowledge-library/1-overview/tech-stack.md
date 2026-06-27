# 技术栈

本文档梳理 XNAgent 使用的主要依赖及其选型理由。版本统一在 `gradle/libs.versions.toml` 中维护，**新增或升级依赖时同步更新本文档**。

## 编译与运行环境

| 项目 | 值 | 备注 |
| --- | --- | --- |
| JDK | **21** | `gradle/gradle-daemon-jvm.properties` 强制；`org.gradle.java.installations.auto-download=false` |
| AGP | **9.1.1** | `compileSdk` 必须使用 AGP 9.x DSL：`release(36) { minorApiLevel = 1 }`，不要退回普通整数 |
| Kotlin | **2.2.10** | 启用 Compose Compiler Plugin、Kotlin Serialization |
| `compileSdk` | `release(36) { minorApiLevel = 1 }` | 见上 |
| `targetSdk` | 36 | |
| `minSdk` | 24 | Android 7.0 |
| Java 源码 / 字节码 | **11** | 不是 21 |
| `buildConfig` | 启用 | `buildFeatures { buildConfig = true }` |
| 仓库镜像 | CI：官方源；本地：Aliyun | 根据 `CI` 环境变量自动切换；CI 用 `google()` + `mavenCentral()` 官方源规避阿里云 502 故障，本地用阿里云镜像加速 |

## 主要依赖

### 核心 / 基础

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.18.0 | KTX 扩展 | |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.10.0 | Lifecycle / ViewModel | |
| `androidx.activity:activity-compose` | 1.13.0 | `setContent`、`enableEdgeToEdge` | |

### UI（Jetpack Compose + Material3）

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| Compose BOM | **2026.02.01** | 统一管理 Compose 系列 | 通过 `platform(libs.androidx.compose.bom)` 引入 |
| `androidx.compose.ui:ui` / `ui-graphics` / `ui-tooling-preview` | BOM | UI 基础 | |
| `androidx.compose.material3:material3` | BOM | 主题、Surface、Scaffold、TopAppBar、ModalNavigationDrawer、TextField | 主题入口 `XNAgentTheme` 在 `ui/theme/Theme.kt` |
| `androidx.compose.material:material-icons-extended` | BOM | 扩展图标（Send / Lightbulb / Bookmark…） | |
| `androidx.compose.ui:ui-tooling` | BOM | Compose Preview（仅 `debugImplementation`） | |
| `androidx.compose.ui:ui-test-manifest` / `ui-test-junit4` | BOM | UI 测试 | 仅 debug / androidTest |

#### 设计 tokens

- 颜色：`res/values/colors.xml`（聊天气泡、发送按钮、深度思考开关等业务色）；`ui/theme/Color.kt` 提供 Material3 调色板。
- 字体：`ui/theme/Type.kt`（Typography），沿用 Material3 默认 Typography。
- 间距 / 圆角：直接使用 `Modifier.padding(16.dp)`、`RoundedCornerShape(20.dp)` 等；统一在 UI 层就近设置。
- 动态取色：Android 12+ 自动启用 `dynamicLightColorScheme/dynamicDarkColorScheme`（`XNAgentTheme(dynamicColor = true)`）。

### 依赖注入

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `com.google.dagger:hilt-android` | **2.59.2** | DI 容器 | `@HiltAndroidApp` 在 `App` 上 |
| `com.google.dagger:hilt-android-compiler` | 2.59.2 | 注解处理 | 通过 KSP 应用 |
| `androidx.hilt:hilt-compiler` | 1.3.0 | hilt-ext 注解处理 | |
| `androidx.hilt:hilt-navigation-compose` | 1.3.0 | `hiltViewModel()` 辅助 | 主入口依赖，本项目未使用 Navigation 库，但保留以便将来扩展 |
| `androidx.hilt:hilt-work` | 2.9.0 | WorkManager 集成 | 暂未使用，预留 |

Hilt 模块集中在 `App.kt`：`AppModule`（业务 / JSON / 配置）、`NetworkModule`（OkHttp / Retrofit）、`DataModule`（Database / Dao / API）。

### 协程与响应式

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `kotlinx-coroutines-android` | 由 BOM/Compose 拉取 | 协程运行时 | `App.onCreate` 中开启 `kotlinx.coroutines.debug` |
| `kotlinx-coroutines-core` | 由 BOM/Compose 拉取 | 协程核心 | |

### 持久化

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `androidx.room:room-runtime` / `room-ktx` | **2.8.3** | 本地聊天会话与消息存储 | `XNDatabase`（v1，启用 `fallbackToDestructiveMigration(dropAllTables = true)`） |
| `androidx.room:room-compiler` | 2.8.3 | 注解处理 | KSP |
| SharedPreferences | 系统内置 | `AuthStore`（auth_store）、`FavoriteRepositoryImpl`（favorite_store） | 收藏以 JSON 字符串持久化 |

### 网络

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `com.squareup.okhttp3:okhttp` | **4.11.0** | HTTP / SSE | 普通客户端 + 独立 SSE 客户端 |
| `com.squareup.okhttp3:logging-interceptor` | 4.11.0 | 普通客户端 BODY 日志 | 调试期开启 |
| `com.squareup.retrofit2:retrofit` | **2.9.0** | REST 客户端 | |
| `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | 0.8.0 | 适配 kotlinx.serialization | |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.0 | JSON 序列化 | `Json { ignoreUnknownKeys = true; encodeDefaults = true }` |
| 自研 | — | `HttpStreamingLoggingInterceptor` | SSE 专用，仅记录行级 / 帧级日志，不读取 body |

#### `BASE_URL`

`data/local/network/NetworkConfig.kt`：`https://xiaoniu.tech/`，所有接口路径前缀 `/api`。

### Markdown 渲染

| 库 | 版本 | 用途 | 备注 |
| --- | --- | --- | --- |
| `io.noties.markwon:core` | 4.6.2 | 助手消息 Markdown 渲染 | 包在 `ui/component/MarkdownText.kt` |

### 单元测试 / 仪器测试

| 库 | 版本 | 用途 |
| --- | --- | --- |
| `junit:junit` | 4.13.2 | 单元测试 |
| `com.google.dagger:hilt-android-testing` | 2.59.2 | Hilt 单元测试 |
| `androidx.test.ext:junit` | 1.3.0 | AndroidJUnit4 |
| `androidx.test.espresso:espresso-core` | 3.7.0 | Espresso |

> 当前 `app/src/test` 与 `app/src/androidTest` 仅为占位。

## 选型理由摘要

- **Compose + Material3**：声明式 UI 与项目迭代节奏匹配，3 个顶级页面的状态切换在 `MainActivity` 内集中处理，无需引入 Navigation 库；
- **Hilt + KSP**：Hilt 与官方生命周期组件契合，KSP 比 KAPT 编译更快；
- **Room + SharedPreferences**：Room 承担结构化聊天数据，SharedPreferences 承担轻量的 token / 收藏 JSON；
- **Retrofit + OkHttp + kotlinx.serialization**：与 Kotlin 协程 / Serialization 原生契合，SSE 通过 `ResponseBody` 直读减少抽象；
- **Markwon**：在 Compose 中渲染助手 Markdown 的成熟方案，体积可控。

## 升级注意事项

1. **AGP / Kotlin 大版本升级**：先确认 Compose Compiler Plugin、Compose BOM 与 Kotlin 之间的兼容矩阵；`compileSdk` 仍需用 AGP 9.x DSL。
2. **依赖清单位置**：所有版本号必须集中在 `gradle/libs.versions.toml`，**不要在 `app/build.gradle.kts` 内直接写字面量版本**。
3. **JDK**：保持 21；如切换到 24 等更新版本，需同步更新 `gradle/gradle-daemon-jvm.properties`。
4. **OkHttp / Retrofit**：注意 SSE 客户端不得引入 `HttpLoggingInterceptor` 的 BODY 级别。
5. **Hilt**：升级时同步更新 `hilt-compiler` 与 `hilt-ext-compiler` 版本，避免 KSP 处理器不匹配。
