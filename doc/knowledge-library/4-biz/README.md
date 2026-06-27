# 业务

本目录按业务域描述 XNAgent 的核心业务流程。新增业务或修改现有流程时，请同步更新本目录的对应文档。

## 业务域列表

| 业务 | 文档 | 关联接口 |
| --- | --- | --- |
| 认证（注册 / 登录 / 游客 / 登出） | [auth.md](./auth.md) | [`../../api/auth.md`](../../api/auth.md) |
| 聊天（模型 / 智能体 / 会话 / SSE 流式） | [chat.md](./chat.md) | [`../../api/chat.md`](../../api/chat.md) |
| 收藏 | 由 [chat.md](./chat.md) 的「消息收藏」一节覆盖 | — |
| 设置 | 由 [`../5-ui/navigation.md`](../5-ui/navigation.md) 与 [chat.md](./chat.md) 的「智能体与收藏」一节覆盖 | — |

## 阅读顺序

1. 先读 [auth.md](./auth.md) 了解认证态、Token Rotation、游客态；
2. 再读 [chat.md](./chat.md) 了解模型、会话、SSE 流式、收藏、智能体；
3. 修改代码时，对应业务文档先于代码改动更新。
