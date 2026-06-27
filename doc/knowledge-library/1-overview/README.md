# 项目总览

> 1-overview 是 XNAgent 项目的**第一站**：本 README 给出项目简介与导航，更深入的架构 / 技术栈内容请进入下方对应文档。

## 项目简介

XNAgent 是一款基于 Android 平台的 **AI 智能体聊天客户端**（包名 `tech.xiaoniu.xnagent`）。

- **能力**：多模型流式聊天、公开智能体（Identity）绑定、邮箱注册登录、游客模式、会话历史、消息编辑 / 重新生成 / 删除 / 收藏；
- **架构**：单 Activity + Jetpack Compose（Material3）+ MVI-like（Intent / UiState / ViewModel），Hilt 做依赖注入；
- **存储**：本地 Room（聊天会话与消息）+ SharedPreferences（认证 token、收藏 JSON）+ assets（兜底模型配置）；
- **网络**：Retrofit + OkHttp + kotlinx.serialization；普通接口与 SSE 流式接口使用**两套独立 OkHttp 客户端**，避免 `HttpLoggingInterceptor(BODY)` 破坏流式消费；
- **认证**：v2 双 token（accessToken + refreshToken） + deviceId 设备绑定 + Token Rotation，401 由拦截器自动刷新；
- **构建环境**：JDK 21、AGP 9.1.1、Kotlin 2.2.10、Compose BOM 2026.02.01、`compileSdk` 使用 AGP 9.x DSL（`release(36) { minorApiLevel = 1 }`）、minSdk 24 / targetSdk 36、Java 字节码 11。

详细版本与选型理由见 [tech-stack.md](./tech-stack.md)。

## 文档列表

| 文档 | 适用场景 | 是否需读 |
| --- | --- | --- |
| [architecture.md](./architecture.md) | 了解模块划分、分层结构、状态管理、数据流方向 | 入门必读 |
| [tech-stack.md](./tech-stack.md) | 查阅依赖库版本、选型理由与升级注意事项 | 添加/升级依赖前查阅 |

## 阅读顺序建议

1. 先读 [architecture.md](./architecture.md) 了解整体分层、模块职责、状态管理（MVI-like）与网络/数据流；
2. 再读 [tech-stack.md](./tech-stack.md) 了解主要依赖的作用与版本约束；
3. 进入具体模块开发时，跳转至：
   - 项目结构与代码规范：[`../2-convention/README.md`](../2-convention/README.md)
   - 模块职责：[`../3-modules/README.md`](../3-modules/README.md)
   - 业务逻辑：[`../4-biz/README.md`](../4-biz/README.md)
   - UI 设计与导航：[`../5-ui/README.md`](../5-ui/README.md)
