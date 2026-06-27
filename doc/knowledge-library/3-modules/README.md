# 模块

XNAgent 当前为单模块项目（`:app`）。本文档说明模块拆分原则、依赖关系以及当前模块的职责。

## 模块列表

| 模块 | 路径 | 职责 | 文档 |
| --- | --- | --- | --- |
| `:app` | `app/` | 唯一应用模块，承载 UI、数据层、Hilt 入口与所有业务 | [app.md](./app.md) |

## 拆分原则（远期规划）

当出现以下情况时考虑拆模块：

- `:app` 编译时间超过 60s；
- 不同业务线并行迭代，需要独立编译 / 独立发版；
- 多团队协作时模块边界需要代码权限隔离。

预期拆分方向：

- `:core:network`：OkHttp / Retrofit / TokenRefreshHandler；
- `:core:database`：Room / 实体 / DAO；
- `:core:auth`：AuthStore / AuthRepository / AuthApi；
- `:core:ui`：Material3 主题、复用 Composable（ChatMessageList、ChatInputBar 等）；
- `:feature:home` / `:feature:login` / `:feature:settings`：各顶级页面。

模块间依赖方向：

```
:feature:*  →  :core:ui  →  :core:network
                       ↘  :core:database
                       ↘  :core:auth
```

`:app` 仅做模块组合与入口装配。

## 依赖关系图（当前）

```
:app
├── Compose BOM（platform）
├── Material3 / Material Icons Extended
├── Hilt（含 hilt-navigation-compose 预留）
├── Room（runtime / ktx / compiler via KSP）
├── Retrofit + OkHttp + kotlinx.serialization
├── Markwon
└── 单元测试 / 仪器测试依赖
```

详细版本号见 [`../1-overview/tech-stack.md`](../1-overview/tech-stack.md)。
