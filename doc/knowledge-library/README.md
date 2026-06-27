# 文档库（Knowledge Library）

本目录是 XNAgent 项目的**唯一项目级文档入口**，所有架构、规范、模块、业务与 UI 文档都按主题分目录组织在这里。

> 阅读原则：**按需读取**。先判断任务所属模块，再只读相关文档；不要一次加载所有内容。
> 入口约定：根目录的 [CLAUDE.md](../../CLAUDE.md) 与 [AGENTS.md](../../AGENTS.md) 也仅作为导航，不存放具体规范。

## 项目简介

XNAgent 是一款基于 Android 平台的 **AI 智能体聊天客户端**：

- 包名 `tech.xiaoniu.xnagent`，单 `:app` 模块；
- 技术栈：Kotlin + Jetpack Compose（Material3）+ Hilt + Room + Retrofit/OkHttp + kotlinx.serialization + Markwon；
- 核心能力：多模型流式对话、公开智能体绑定、邮箱注册登录（v2 双 token + 设备绑定 + Token Rotation）、游客模式、会话历史与消息收藏；
- 构建环境：JDK 21、AGP 9.1.1、Kotlin 2.2.10、Compose BOM 2026.02.01、minSdk 24 / targetSdk 36。

完整项目说明见根目录 [README.md](../../README.md)（若有）与 [AGENTS.md](../../AGENTS.md)；技术细节见下方各子目录。

## 子目录结构

```
doc/knowledge-library/
├── 1-overview/   # 项目总览（架构、技术栈、入口）
├── 2-convention/ # 开发规范（代码风格、命名、Git、项目结构）
├── 3-modules/    # 模块职责（当前仅 :app）
├── 4-biz/        # 业务流程（认证、聊天 / 模型 / 智能体 / 收藏）
└── 5-ui/         # UI 设计（导航、设计系统）
```

## 各目录职责与入口

| 目录 | 一句话 | 入口 |
| --- | --- | --- |
| `1-overview/` | 项目总览：架构图、技术栈一览、关键设计 | [README.md](./1-overview/README.md) |
| `2-convention/` | 代码风格、命名、Git 规范、项目结构 | [README.md](./2-convention/README.md) |
| `3-modules/` | 模块拆分原则与各模块职责 | [README.md](./3-modules/README.md) |
| `4-biz/` | 业务域划分与具体业务流程 | [README.md](./4-biz/README.md) |
| `5-ui/` | 页面导航、主题、设计系统 | [README.md](./5-ui/README.md) |

## 推荐阅读路径

新成员入门：

1. [1-overview/README.md](./1-overview/README.md) → 项目简介 + 技术栈；
2. [1-overview/architecture.md](./1-overview/architecture.md) → 整体架构与数据流；
3. [2-convention/project-structure.md](./2-convention/project-structure.md) → 包结构与文件放置；
4. [2-convention/coding.md](./2-convention/coding.md) → Kotlin / Compose 规范；
5. [2-convention/git.md](./2-convention/git.md) → 分支与提交流程。

按任务类型：

- 修改业务（认证 / 聊天 / 收藏 / 智能体）→ [4-biz/README.md](./4-biz/README.md)；
- 修改 UI / 视觉 → [5-ui/README.md](./5-ui/README.md)；
- 新增文件 / 模块 → [2-convention/project-structure.md](./2-convention/project-structure.md)；
- 提 PR → [2-convention/git.md](./2-convention/git.md)。

## 相关目录

- 服务端 API 定义：[`../api/`](../api/)（`auth.md`、`chat.md`）；
- 任务工作区（已 gitignore）：[`../tasks/`](../tasks/)；
- 根文档导航：[`../../CLAUDE.md`](../../CLAUDE.md) / [`../../AGENTS.md`](../../AGENTS.md)。

## 文档维护

> 修改代码后，请**同步评估**是否需要更新本目录中对应文档；详细约定见根 [CLAUDE.md](../../CLAUDE.md)「文档维护约定」一节。