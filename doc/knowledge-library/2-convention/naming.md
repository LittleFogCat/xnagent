# 命名规范

## 1. 包名

- 根包名固定 `tech.xiaoniu.xnagent`；
- 子包按职责命名，单数优先：

| 子包 | 用途 |
| --- | --- |
| `data` | 数据层根（再细分 `data.local` / `data.remote` / `data.repository`） |
| `data.local` | Room、SharedPreferences、AuthStore、网络工具等本地数据 |
| `data.remote` | Retrofit 接口、DTO |
| `data.repository` | 仓库接口与实现 |
| `ui` | UI 层根 |
| `ui.component` | 复用 Composable |
| `ui.model` | UI 模型（UiState、ChatMessage、ModelUiModel） |
| `ui.screen.<feature>` | 每个 Feature 的 Screen / ViewModel / Intent |
| `ui.theme` | Material3 主题 |
| `common.util` | 通用工具 |

## 2. 类 / 接口

| 类型 | 命名风格 | 示例 |
| --- | --- | --- |
| ViewModel | `<Screen>ViewModel` | `HomeViewModel` |
| 屏幕 Composable | `<Screen>Screen` | `HomeScreen` |
| 纯 UI Composable | `<Screen>Content` | `HomeScreenContent` |
| UiState | `<Screen>UiState` | `HomeUiState` |
| Intent | `<Screen>Intent`（`sealed class`） | `HomeIntent` |
| Repository 接口 | `<Name>Repository` | `HomeRepository` |
| Repository 实现 | `<Name>RepositoryImpl` | `HomeRepositoryImpl` |
| API 接口 | `<Resource>Api` | `AuthApi` / `ChatApi` / `StreamChatApi` |
| DTO | `<Name>Dto` 或 `<Name>Request/Response` | `LoginV2Request` / `LoginV2Response` |
| Room Entity | 名词，**不带** `Entity` 后缀（与表名一致） | `Session` / `ChatMessage` |
| Room DAO | `<Resource>Dao` | `ChatDao` |
| Room Database | `<App>Database` | `XNDatabase` |
| 业务枚举 | `PascalCase` | `MessageRole` / `AgentMode` / `MainDestination` |
| 单例配置 | `object` 名词 | `NetworkConfig` |
| 拦截器 | `<Purpose>Interceptor` | `HttpStreamingLoggingInterceptor` |
| Hilt 模块 | `<Layer>Module` | `AppModule` / `NetworkModule` / `DataModule` |

## 3. 函数 / 属性

| 元素 | 风格 | 示例 |
| --- | --- | --- |
| Composable 函数 | `PascalCase` | `HomeScreen` / `ChatMessageItem` |
| 普通函数 | `camelCase` | `sendConversation` / `applySessionList` |
| 私有函数 | `camelCase` | `observeModels` / `readRefreshToken` |
| 扩展函数 | `camelCase`，文件名前缀反映接收方类型 | `String.toMessageRole()` |
| 属性 | `camelCase` | `isResponding` / `currentModel` |
| 私有属性 | `_camelCase` + `MutableStateFlow`，对外只暴露 `camelCase` | `_uiState` / `uiState` |
| 布尔属性 | `is/has/can` 前缀 | `isLoggedIn` / `isGuest` / `canEnterHome` |
| 常量 | `UPPER_SNAKE_CASE` | `KEY_TOKEN` / `PREF_NAME` / `GUEST_USER_MESSAGE_LIMIT` |

## 4. 资源

- 颜色：`业务名_属性_状态`（如 `chat_bubble_bg_user`、`deepthink_bg_color_checked`）；
- 字符串：snake_case（`app_name`），中文文案暂不抽到 `strings.xml`；
- 图标：`ic_<用途>`（如 `ic_action_menu`、`ic_add`、`ic_xn_launcher`）。

## 5. Compose 入口

- 每个 Screen 必须成对出现 `<Screen>Screen` 与 `<Screen>Content`；
- `XxxContent` 默认参数必须使用空态 `UiState`，方便 Preview；
- 事件回调命名为 `on<Action>`（如 `onOpenSettings`、`onSessionClick`）。

## 6. 注释 / KDoc

- 顶层 public 类、Composable、Repository 方法必须写 KDoc；
- 内部 `private` 函数可视情况省略，但复杂业务逻辑（`sendConversation`、`upsertAssistantMessage`）建议保留；
- 注释使用中文，必要时附代码示例。

## 7. 反例

```kotlin
// ❌ 驼峰不一致
val GuestUserMessageCount = 10

// ❌ 布尔值未使用 is/has 前缀
val responding = false

// ❌ Composable 与 Content 命名混乱
fun HomeUi() {}           // 应改为 HomeScreen
fun HomeScreenPure() {}   // 应改为 HomeScreenContent

// ❌ Repository 接口用 Impl 后缀
interface HomeRepositoryImpl {} // 应当是 HomeRepository
```
