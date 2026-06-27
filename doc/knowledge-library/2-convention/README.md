# 规范

本目录汇总 XNAgent 的代码规范与项目结构约定。所有新代码与文档变更都应符合本目录的约定。

## 文档列表

| 文档 | 适用场景 | 是否需读 |
| --- | --- | --- |
| [coding.md](./coding.md) | 编写 Kotlin / Compose / 协程代码时 | 提交前自查 |
| [naming.md](./naming.md) | 命名包名、类名、变量、资源文件、Compose 入口 | 任何命名相关问题 |
| [git.md](./git.md) | 分支、Commit、PR 流程 | 提交代码前 |
| [project-structure.md](./project-structure.md) | 源码放置位置、新增模块位置 | 新增文件 / 模块时 |

## 阅读顺序建议

1. 先读 [project-structure.md](./project-structure.md) 了解整体包结构；
2. 再读 [naming.md](./naming.md) 明确命名风格；
3. 编写代码时参照 [coding.md](./coding.md)；
4. 提交流程遵循 [git.md](./git.md)。
