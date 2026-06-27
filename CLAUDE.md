# CLAUDE.md

## 项目说明

本文件仅作为 XNAgent 项目的文档导航入口，**不存放具体开发规范**。具体规范、架构、UI 设计等内容按需读取下方表格中的对应文档。

在开始分析、修改代码或新增功能前，请先判断任务所属模块，并**仅按需读取相关文档**，避免一次性加载无关内容。

## 通用文档（按需读取）

以下文档包含项目的整体规范与背景，仅在需要时阅读：

| 文档 | 路径 | 适用场景 |
| --- | --- | --- |
| 项目总览 | [doc/knowledge-library/1-overview/README.md](doc/knowledge-library/1-overview/README.md) | 入门、跨模块协作 |
| 架构说明 | [doc/knowledge-library/1-overview/architecture.md](doc/knowledge-library/1-overview/architecture.md) | 理解模块划分、分层、MVI-like、网络/数据流 |
| 技术栈 | [doc/knowledge-library/1-overview/tech-stack.md](doc/knowledge-library/1-overview/tech-stack.md) | 添加 / 升级依赖前查阅 |
| 代码风格 | [doc/knowledge-library/2-convention/coding.md](doc/knowledge-library/2-convention/coding.md) | 编写 Kotlin / Compose / 协程代码前 |
| 命名规范 | [doc/knowledge-library/2-convention/naming.md](doc/knowledge-library/2-convention/naming.md) | 命名包名、类名、变量、资源时 |
| Git 规范 | [doc/knowledge-library/2-convention/git.md](doc/knowledge-library/2-convention/git.md) | 提交代码、创建 PR 前 |
| 项目结构 | [doc/knowledge-library/2-convention/project-structure.md](doc/knowledge-library/2-convention/project-structure.md) | 新增文件 / 模块时 |
| UI 导航 | [doc/knowledge-library/5-ui/navigation.md](doc/knowledge-library/5-ui/navigation.md) | 路由 / 抽屉 / 登录保护相关问题 |
| UI 设计系统 | [doc/knowledge-library/5-ui/design-system.md](doc/knowledge-library/5-ui/design-system.md) | 修改颜色 / 字体 / 间距 / 组件前 |

## 业务模块文档（按需读取）

仅当任务涉及对应业务时，再读取对应文档：

| 业务 | 文档 | 接口 |
| --- | --- | --- |
| 应用模块 | [doc/knowledge-library/3-modules/app.md](doc/knowledge-library/3-modules/app.md) | — |
| 认证 | [doc/knowledge-library/4-biz/auth.md](doc/knowledge-library/4-biz/auth.md) | [doc/api/auth.md](doc/api/auth.md) |
| 聊天 / 模型 / 智能体 / 收藏 | [doc/knowledge-library/4-biz/chat.md](doc/knowledge-library/4-biz/chat.md) | [doc/api/chat.md](doc/api/chat.md) |

## 工作原则

1. **不要一次读取所有文档。**
2. 先理解任务，再判断需要哪些文档。
3. **仅加载当前任务所需的文档。**
4. 如果多个模块均受影响，可同时读取多个相关文档。
5. 不要凭经验猜测项目规范，如存在对应文档，应优先阅读后再进行实现。
6. 若任务涉及的规范文档不存在，可结合现有代码实现保持一致的编码风格。
7. **构建验证**：任何修改代码的操作之后，**必须**保证项目可以通过构建。完成代码修改后，应在本地执行一次 `./gradlew.bat assembleDebug`（详见 [app.md](doc/knowledge-library/3-modules/app.md) 第 6 节），确认没有编译错误或破坏现有功能后再交付。

## 文档维护约定

> 在开发或修改了代码之后，如果对应的文档需要增、删、改，则**必须**及时更新。

具体而言：

- 修改 `gradle/libs.versions.toml` 或 `app/build.gradle.kts` → 更新 [tech-stack.md](doc/knowledge-library/1-overview/tech-stack.md)；
- 新增 / 删除 / 重命名模块、调整包结构 → 更新 [project-structure.md](doc/knowledge-library/2-convention/project-structure.md) 与 [3-modules/app.md](doc/knowledge-library/3-modules/app.md)；
- 修改 ViewModel / Repository / Composable 公共接口 → 更新 [architecture.md](doc/knowledge-library/1-overview/architecture.md)；
- 修改业务流程（如注册、Token Rotation、登录保护、SSE 解析、会话同步、收藏）→ 更新 [4-biz/](../doc/knowledge-library/4-biz/README.md) 对应文档；
- 修改路由 / 抽屉 / 登录跳转 → 更新 [5-ui/navigation.md](doc/knowledge-library/5-ui/navigation.md)；
- 修改颜色 / 字体 / 形状 / 间距 → 更新 [5-ui/design-system.md](doc/knowledge-library/5-ui/design-system.md)；
- 新增 / 修改服务端接口 → 更新 [doc/api/](../doc/api/) 对应文档。
- 执行了 review 任务 -> 在 `/doc/.ai/review` 目录新建 review 文件，文件名格式为 `review_{topic}_yyyyMMdd_HHmmss.md`。
- 执行了对 review 的评估 -> 在 `/doc/.ai/review` 目录新建评估文件，文件名格式为 `{review_file_name}_eval.md`。其中 `{review_file_name}` 为 review 文件的文件名（不包含扩展名）。

## 示例

- 「修改登录失败 3 次后弹出图形验证码的逻辑」→ 仅需 [4-biz/auth.md](doc/knowledge-library/4-biz/auth.md) + [doc/api/auth.md](doc/api/auth.md)；
- 「修复 SSE 流式解析时丢帧的 bug」→ 仅需 [4-biz/chat.md](doc/knowledge-library/4-biz/chat.md) + [doc/api/chat.md](doc/api/chat.md)；
- 「新增一个智能体入口图标」→ [5-ui/navigation.md](doc/knowledge-library/5-ui/navigation.md) + [5-ui/design-system.md](doc/knowledge-library/5-ui/design-system.md)。



