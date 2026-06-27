# CLAUDE.md

## 项目说明

本文件仅作为 XNAgent 项目的文档导航入口，**不存放具体开发规范**。具体规范、架构、UI 设计等内容按需读取下方表格中的对应文档。

在开始分析、修改代码或新增功能前，请先判断任务所属模块，并**仅按需读取相关文档**，避免一次性加载无关内容。

## 基础规则（必读）

见：[doc/knowledge-library/2-convention/ai.md](doc/knowledge-library/2-convention/ai.md)

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

## 示例

- 「修改登录失败 3 次后弹出图形验证码的逻辑」→ 仅需 [4-biz/auth.md](doc/knowledge-library/4-biz/auth.md) + [doc/api/auth.md](doc/api/auth.md)；
- 「修复 SSE 流式解析时丢帧的 bug」→ 仅需 [4-biz/chat.md](doc/knowledge-library/4-biz/chat.md) + [doc/api/chat.md](doc/api/chat.md)；
- 「新增一个智能体入口图标」→ [5-ui/navigation.md](doc/knowledge-library/5-ui/navigation.md) + [5-ui/design-system.md](doc/knowledge-library/5-ui/design-system.md)。

注：以上所有流程均需要先阅读基础规则，再根据任务判断是否需要其他文档。
