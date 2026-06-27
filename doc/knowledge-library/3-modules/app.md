# :app 模块

`:app` 是 XNAgent 唯一的应用模块，承载 UI 层、ViewModel、Repository、本地存储与网络层，并负责应用入口与 Hilt 装配。

## 1. 模块职责

- **应用入口**：`App`（`@HiltAndroidApp`）、`MainActivity`（`@AndroidEntryPoint`，唯一 Activity）；
- **根路由**：`MainViewModel` 持有 `MainDestination`，驱动首页 / 设置页 / 登录页的切换；
- **业务实现**：首页、登录、设置三大 Feature 的 Screen / ViewModel / Intent；
- **数据层**：所有 Repository、API、本地存储；
- **Hilt 模块**：`AppModule` / `NetworkModule` / `DataModule` 集中在 `App.kt`；
- **资源**：主题、颜色、字符串、图标。

## 2. 构建配置

`app/build.gradle.kts` 关键项：

- `namespace` / `applicationId`：`tech.xiaoniu.xnagent`
- `compileSdk`：`release(36) { minorApiLevel = 1 }`（AGP 9.x DSL，**不要**改成普通整数）
- `targetSdk = 36`、`minSdk = 24`
- `versionCode = 1`、`versionName = "1.0"`
- `compileOptions`：Java 11（**不是 21**）
- `buildFeatures { compose = true; buildConfig = true }`
- `debug`：可调试，关闭 minify / shrink
- `release`：默认 ProGuard 规则 + `proguard-rules.pro`

应用插件：

- `com.android.application`
- `org.jetbrains.kotlin.plugin.compose`
- `org.jetbrains.kotlin.plugin.serialization`
- `com.google.dagger.hilt.android`
- `com.google.devtools.ksp`
- `androidx.room`（KSP 处理）

## 3. 关键文件

| 文件 | 职责 |
| --- | --- |
| `App.kt` | `@HiltAndroidApp`，开启 `kotlinx.coroutines.debug`，声明 3 个 Hilt 模块 |
| `MainActivity.kt` | 唯一 Activity，订阅 `MainViewModel.uiState` 并按 `destination` 渲染 |
| `MainViewModel.kt` | 根路由 ViewModel，订阅 `AuthSession` 切换 `MainDestination` |
| `data/repository/HomeRepositoryImpl.kt` | 主页数据源实现：模型 / 智能体 / 聊天 CRUD / SSE 解析 / 本地落盘 / 同步 |
| `data/repository/AuthRepositoryImpl.kt` | 认证仓库：v2 登录、注册三步流程、游客、登出 |
| `data/repository/FavoriteRepositoryImpl.kt` | 收藏仓库：SharedPreferences JSON 持久化 |
| `data/local/AuthStore.kt` | 本地认证状态 + deviceId 持久化 |
| `data/local/TokenRefreshHandler.kt` | 401 自动刷新 token，使用 `ReentrantLock` 单飞 |
| `data/local/XNDatabase.kt` + `dao/ChatDao.kt` + `entity/ChatEntity.kt` | 本地聊天数据（Session + ChatMessage） |
| `data/local/network/NetworkConfig.kt` | `BASE_URL = "https://xiaoniu.tech/"` |
| `data/local/network/HttpStreamingLoggingInterceptor.kt` | SSE 客户端专用日志拦截器 |
| `data/remote/api/AuthApi.kt` / `ChatApi.kt` / `StreamChatApi.kt` | Retrofit 接口 |
| `ui/screen/home/HomeScreen.kt` / `HomeViewModel.kt` / `HomeIntent.kt` | 首页 |
| `ui/screen/login/LoginScreen.kt` / `LoginViewModel.kt` / `LoginIntent.kt` | 登录/注册/游客 |
| `ui/screen/settings/SettingsScreen.kt` / `SettingsViewModel.kt` | 设置页 |
| `ui/component/ChatMessageList.kt` | 消息列表 + 消息项（长按菜单、Markdown 渲染、重新生成、收藏等） |
| `ui/component/ChatInputBar.kt` | 输入条（如有需要可单独抽离） |
| `ui/component/DropdownSelector.kt` / `MarkdownText.kt` / `UserAvatar.kt` | 复用 Composable |
| `ui/theme/Theme.kt` / `Color.kt` / `Type.kt` | Material3 主题 |

## 4. 资源

- 颜色：`res/values/colors.xml`（聊天气泡、发送按钮、深度思考开关等业务色）；
- 字符串：`res/values/strings.xml`（当前仅 `app_name`，中文文案暂写在 Composable 内）；
- 主题：`res/values/themes.xml`（`Theme.XNAgent`）；
- 图标：`res/drawable/` + 各密度 mipmap，启动图标 `ic_xn_launcher`。

## 5. 测试

- `app/src/test`：单元测试目录，当前为空；
- `app/src/androidTest`：仪器测试目录，当前为空；
- 已配置依赖：`junit`、`hilt-android-testing`、`androidx.test.ext:junit`、`espresso-core`，可按需补齐。

## 6. 构建命令

```powershell
# 构建 Debug APK
.\gradlew.bat assembleDebug

# 构建并安装到设备/模拟器
.\gradlew.bat installDebug

# 单元测试
.\gradlew.bat test

# 仪器测试（需要设备/模拟器）
.\gradlew.bat connectedAndroidTest
```

> 始终使用 `.\gradlew.bat`，**不要加** `--no-daemon`；若 Android Studio 已启动 Gradle 守护进程，请直接复用。
