# 项目结构

## 1. 顶层结构

```
XNAgent/
├── app/                            # :app 模块
│   ├── build.gradle.kts            # 模块构建配置
│   ├── proguard-rules.pro          # Release 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/             # 内置静态资源（model_config.json 等）
│       │   ├── java/               # Kotlin 源码
│       │   └── res/                # Android 资源
│       ├── androidTest/            # 仪器测试
│       ├── debug/                  # debug 变体资源
│       └── test/                   # 单元测试
├── doc/
│   ├── api/                        # 服务端接口文档（auth.md、chat.md）
│   ├── knowledge-library/          # 项目知识库
│   │   ├── 1-overview/
│   │   ├── 2-convention/
│   │   ├── 3-modules/
│   │   ├── 4-biz/
│   │   └── 5-ui/
│   └── .ai/                      # AI 任务工作区（已 gitignore）
├── gradle/
│   ├── gradle-daemon-jvm.properties
│   ├── libs.versions.toml          # 版本目录
│   └── wrapper/
├── gradle.properties
├── gradlew / gradlew.bat
├── settings.gradle.kts             # 仓库配置（含 Aliyun 镜像）
├── AGENTS.md
├── CLAUDE.md
└── local.properties                # 本地 SDK 路径（已 gitignore）
```

## 2. 源码包结构

包名固定为 `tech.xiaoniu.xnagent`，子包约定如下：

```
tech.xiaoniu.xnagent/
├── App.kt                  # @HiltAndroidApp + AppModule/NetworkModule/DataModule
├── MainActivity.kt         # 唯一 Activity
├── MainViewModel.kt        # 根路由 ViewModel
├── common/
│   └── util/               # 通用工具（DateUtil 等）
├── data/
│   ├── LLMMessage.kt
│   ├── Model.kt            # 资产/远端共享的模型定义
│   ├── local/
│   │   ├── AuthStore.kt
│   │   ├── TokenRefreshHandler.kt
│   │   ├── XNDatabase.kt
│   │   ├── dao/ChatDao.kt
│   │   ├── entity/         # Session / ChatMessage
│   │   └── network/        # NetworkConfig / Interceptor
│   ├── mock/               # 假数据 / 桩
│   ├── remote/
│   │   ├── api/            # AuthApi / ChatApi / StreamChatApi
│   │   └── dto/            # 请求/响应 DTO
│   └── repository/         # HomeRepository / AuthRepository / FavoriteRepository
└── ui/
    ├── UiExts.kt
    ├── component/          # ChatMessageList / ChatInputBar / DropdownSelector /
    │                       # MarkdownText / UserAvatar
    ├── model/              # HomeUiState / ChatMessage / ModelUiModel /
    │                       # LoginUiState / SessionUiModel / AgentMode …
    ├── screen/
    │   ├── home/           # HomeScreen + HomeViewModel + HomeIntent
    │   ├── login/          # LoginScreen + LoginViewModel + LoginIntent
    │   └── settings/       # SettingsScreen + SettingsViewModel
    └── theme/              # Color.kt / Type.kt / Theme.kt
```

## 3. 文件放置规则

| 类型 | 位置 |
| --- | --- |
| Application 类 | 根包 `tech.xiaoniu.xnagent` |
| Activity / 顶级 ViewModel | 根包 |
| Composable Screen | `ui/screen/<feature>/` |
| Composable Content | 与 Screen 同文件（同包）或 `ui/screen/<feature>/` 内单独文件 |
| Composable Component | `ui/component/` |
| UiState / UI 模型 | `ui/model/` |
| Intent | 与所属 Screen 同包（`ui/screen/<feature>/`） |
| ViewModel | 与所属 Screen 同包（`ui/screen/<feature>/`） |
| Repository 接口与实现 | `data/repository/`（**仅一套**，不要新增 `ui/screen/<feature>/XxxRepository`） |
| Retrofit API | `data/remote/api/` |
| DTO | `data/remote/dto/` |
| Room Entity / Dao / Database | `data/local/entity/` / `data/local/dao/` / `data/local/` |
| Hilt 模块 | 集中在 `App.kt` |
| 资源 | `res/values/colors.xml`（业务色）、`res/values/strings.xml`、`res/drawable/`、`res/xml/` |

## 4. 严禁的反模式

- ❌ 在 `ui/screen/<feature>/` 下新建 `XxxRepository` / `XxxRepositoryImpl`；
  → 统一放 `data/repository/`，目前 `ui/screen/home/HomeRepository` 是 Stale 副本，不再使用。
- ❌ 把 DTO 暴露给 UI 层；
  → UI 一律使用 `ui/model/` 下的 UiModel，Repository 完成 DTO ↔ UiModel 映射。
- ❌ 把 Composable 与 ViewModel 写在同一文件；
  → 拆为 `XxxScreen.kt`（含 `XxxContent`）和 `XxxViewModel.kt`。
- ❌ 业务逻辑写在 Activity / Composable 内；
  → 全部下沉到 ViewModel，必要时下沉到 Repository。

## 5. 资源与版本管理

- 依赖版本：**全部** 在 `gradle/libs.versions.toml` 中维护，模块通过 `libs.*` 引用；
- `compileSdk` 使用 AGP 9.x DSL：`release(36) { minorApiLevel = 1 }`，不要退回普通整数；
- 业务色统一放 `res/values/colors.xml`，由 Composable 通过 `colorResource(R.color.xxx)` 引用；
- 启动图标：`drawable/ic_xn_launcher.xml` + 各密度 mipmap。

## 6. 文档

- 文档集中在 `doc/`：
  - `doc/api/`：服务端 API 文档（`auth.md`、`chat.md`）；
  - `doc/knowledge-library/`：项目知识库（按 `1-overview` / `2-convention` / `3-modules` / `4-biz` / `5-ui` 分目录）；
  - `doc/.ai/`：AI 任务工作区，**已 gitignore**，不提交；
- 修改代码后，**必须**评估是否要同步更新知识库对应文档（见 [`../../../CLAUDE.md`](../../../CLAUDE.md)）。
