# Git 规范

## 1. 分支策略

采用简化版 Git Flow：

| 分支 | 用途 | 命名 |
| --- | --- | --- |
| `master` | 发布分支，受保护 | — |
| `develop` | 日常集成 | — |
| `feature/*` | 单个功能 | `feature/home-message-edit` |
| `fix/*` | 缺陷修复 | `fix/sse-token-refresh-deadlock` |
| `chore/*` | 杂项（依赖、文档、构建） | `chore/bump-compose-bom` |
| `release/*` | 发布准备 | `release/1.2.0` |
| `hotfix/*` | 紧急修复 | `hotfix/1.2.1-token-leak` |

- `feature/*` / `fix/*` / `chore/*` 都从 `develop` 拉出，通过 PR 合回 `develop`；
- 发布时从 `develop` 切 `release/*`，验证完成后合并到 `master` 与 `develop`；
- 紧急修复从 `master` 切 `hotfix/*`，验证完成后同时合并到 `master` 与 `develop`。

## 2. Commit Message

使用 **Conventional Commits** 风格：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 2.1 Type

| Type | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 修复缺陷 |
| `docs` | 仅修改文档（不影响代码） |
| `refactor` | 重构（既不是新功能也不是修 bug） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建 / 工具 / 依赖 |
| `style` | 仅格式调整（不影响逻辑） |
| `revert` | 回退 |

### 2.2 Scope

可选，建议使用模块名：`home`、`login`、`settings`、`network`、`data`、`auth`、`ui`、`docs`、`build`。

### 2.3 Subject

- 中文或英文均可，推荐中文；
- 不超过 **50 字符**；
- 动词开头，第一人称单数不用「我」；
- 不加句号。

### 2.4 Body / Footer

- 解释「为什么」而非「做什么」；
- 关联 Issue：`Refs: #123` 或 `Closes: #123`；
- 破坏性变更：`BREAKING CHANGE: <说明>`。

### 2.5 示例

```
feat(home): 支持用户消息编辑后重新生成

- 编辑用户消息后从该消息处截断上下文
- 重新生成时仅保留「上一条用户消息及其之前上下文」

Refs: #42
```

## 3. PR 规范

- 标题与首个 commit 风格一致：`<type>(<scope>): <subject>`；
- 描述必须包含：
  1. 改动目的与动机；
  2. 主要改动点（可列子项）；
  3. 截图 / 录屏（UI 变更）；
  4. 测试方式（手动 / 自动化）；
  5. 关联 Issue。
- 单 PR 不超过 **500 行** 净变更（不含生成代码 / 资源）；
- 至少 1 位 Reviewer 通过；
- CI（编译 / lint）必须全绿；
- 禁止在 PR 中混入与主题无关的格式化变更（请单独提交 `style:` commit）。

## 4. 提交流程

1. `git pull --rebase` 同步远端；
2. 本地完成开发，确保 `./gradlew.bat assembleDebug` 通过；
3. 按 Conventional Commits 写 commit message；
4. 推送分支并创建 PR；
5. 处理 Review 意见，CI 通过后合并（默认 Squash Merge，保留清晰 commit 历史可用 Rebase Merge）。

## 5. 注意事项

- **不要** 把 `local.properties`、`build/`、`.gradle/`、`*.iml` 等提交到仓库（已在 `.gitignore`）；
- `doc/.ai/` 目录在 `.gitignore` 内，任务工作区文件不提交；
- 提交前确认没有遗留的 `TODO` / `println` / 调试代码。
