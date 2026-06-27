# 代码风格

本文档约定 XNAgent 项目的 Kotlin / Compose / 协程代码风格。如与 Android 官方 Kotlin Style Guide 冲突，以本文档为准。

## 1. 通用

- 使用 **4 空格** 缩进，禁止 Tab；
- 文件末尾保留 **一个空行**；
- 行宽建议控制在 **120 列** 以内（Kotlin 默认 120，但允许在字符串/长链式调用处适度超出）；
- `import` 按 Kotlin 默认排序（不强制分组），禁止通配符 `import x.*`；
- 代码与 KDoc / 注释使用**中文**；
- 字符串、UI 文案、Log Tag 可用中文，标识符用英文。

## 2. Kotlin 语言特性

### 2.1 优先使用

- `data class` / `sealed class` / `sealed interface` 表达状态与事件；
- `object` 表达无状态单例（如 `NetworkConfig`）；
- `companion object` 仅放常量与私有工厂，不放业务逻辑；
- `when` 配合 sealed 类型穷尽分支，禁止 `else` 兜底空逻辑；
- 扩展函数放在接收方同包或 `common/util` 下，不要新建 `extensions` 大杂烩包；
- `Flow` / `StateFlow` / `SharedFlow` 取代回调。

### 2.2 避免

- 不要使用全局可变 `var`；
- 不要在 `init` 块中启动协程，统一放进 ViewModel `init` 内的 `viewModelScope.launch`；
- 不要在 Composable 中启动协程但忘记 `rememberCoroutineScope`；
- 不要直接访问 `Context`，尽量通过 Hilt 注入 `@ApplicationContext`。

## 3. Compose 规范

### 3.1 Screen / Content 分离

每个 Screen 都按如下分层：

```kotlin
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreenContent(
        uiState = uiState,
        onAction = viewModel::dispatch,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState = HomeUiState(),
    onAction: (HomeIntent) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) { /* 纯 UI，不依赖 Hilt */ }
```

`XxxContent` **不依赖 Hilt**，便于 Compose Preview。

### 3.2 状态与副作用

- 状态一律从 `StateFlow` / `MutableState` 派生；
- `LaunchedEffect(key)` 内放置「启动时执行一次」或「依赖某个 key 重启」的副作用；
- `rememberCoroutineScope` 用于用户主动触发的异步操作（滚动、发请求）；
- 避免在 `Composable` 内做重型计算，必要时使用 `remember(key)` 缓存。

### 3.3 性能

- 列表使用 `LazyColumn` + `key`，并指定 `contentType`；
- 复杂 Item 拆分为 `XxxItem` 子 Composable；
- 不要在 `Modifier` 链中调用 `weight(...)` 之外的高开销函数；
- 避免在 Composable 中直接 new `MutableState`（仅在 `remember { }` 内）。

### 3.4 预览

- 每个纯 Content 都要提供 `@Preview`；
- 复杂 Screen 可在 `HomeScreenContent` 上加多个 Preview，覆盖空态、加载态、错误态。

## 4. MVI-like 模式

### 4.1 Intent / UiState / ViewModel

- Intent 统一 `sealed class XxxIntent`，所有 UI 事件都封装为 Intent；
- UiState 统一 `data class XxxUiState`，不可变；
- ViewModel 暴露 `dispatch(XxxIntent)` 入口与 `StateFlow<XxxUiState>` 出口；
- ViewModel 内部状态用 `private val _uiState = MutableStateFlow(...)`，外部只读 `val uiState`。

### 4.2 异步更新

- 内部状态变更统一通过 `_uiState.update { it.copy(...) }`；
- IO 协程 `withContext(Dispatchers.IO)` 或 `flowOn(Dispatchers.IO)`；
- 仓库调用统一在 `viewModelScope.launch` 内，并对失败做 `runCatching` 收口。

### 4.3 避免

- 不要在 ViewModel 中持有 `Context`（Hilt 注入应使用 `@ApplicationContext`）；
- 不要在 Composable 中直接调用 `viewModel.someMethod()`，必须通过 `dispatch(Intent)`。

## 5. 协程

- 默认 `viewModelScope`；
- 长时间运行 / 跨 ViewModel 的任务（如 SSE 接收）允许在 `viewModelScope` 内启动独立协程；
- 取消：`onCleared()` 由 `ViewModel` 自行处理，子协程应当 `ensureActive()` 或响应父作用域取消；
- 调试：保持 `App.onCreate` 中的 `System.setProperty("kotlinx.coroutines.debug", "on")`，**不要在 release 关闭**（便于线上排障）。

## 6. 错误处理

- 仓库方法使用 `runCatching`，ViewModel 收到 `Result.failure` 时记录 `Log.w(tag, "...", it)` 并更新错误态；
- UI 通过 `uiState.errorMessage` 展示，使用一次性事件时在 Composable 消费后调用 `clearError` 类 Intent；
- 解析 SSE 等流式数据时，单条 `data: {...}` 解析失败应忽略该条，**不要中断整次流**。

## 7. 日志

- 使用 `android.util.Log`；
- Tag 优先使用 `javaClass.simpleName`，便于 grep；
- 不在 `Log.d` 中打印大段 content，敏感信息（密码、token）禁止出现在日志中。

## 8. 资源

- 业务色放 `res/values/colors.xml`（如 `chat_bubble_bg_user`、`chat_send_button_bg`），不要写死在 Composable 中；
- 文本如需国际化，**当前统一中文写死**到 Composable 字符串，未来需要 i18n 时再迁移到 `strings.xml`；
- 资源命名全部小写 + 下划线：`ic_action_menu`、`ic_xn_launcher`、`chat_bubble_bg_user`。

## 9. 单元测试（占位）

- `app/src/test` 当前为空；新增 ViewModel / Repository 单元测试时请：
  - 使用 `MainDispatcherRule` 或 `kotlinx-coroutines-test`；
  - Repository 单元测试应 mock 远程 API（建议引入 MockWebServer）。
