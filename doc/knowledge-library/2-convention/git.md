# Git 规范

> 本规范对 AI 与人类开发者统一执行。来源：[`doc/.ai/dev/git_convention_improve.md`](../../.ai/dev/git_convention_improve.md)

## 1. 分支策略

采用轻量 Git Flow：

| 分支 | 用途 | 命名 |
| --- | --- | --- |
| `master` | 稳定发布分支，受保护 | — |
| `develop` | 集成分支（可选） | — |
| `feature/*` | 功能开发 | `feature/chat-streaming` |
| `fix/*` | 缺陷修复 | `fix/login-token-expire` |
| `hotfix/*` | 紧急修复 | `hotfix/crash-startup` |

- 所有变更必须通过 PR 合并；
- `feature/*` / `fix/*` 默认从 `master` 拉出；若项目维护 `develop` 集成分支，则从 `develop` 拉出并合回 `develop`；
- `hotfix/*` 从 `master` 切出，验证后同时合回 `master`（与 `develop`，若存在）。

## 2. 分支规则（强制）

- **禁止** 直接 push `master`；
- **禁止** force push 到任何远程分支；
- **必须** 通过 PR 合并所有变更；
- **必须** 在 `feature/*` 或 `fix/*` 分支上开发。

## 3. 分支命名规范

```
feature/<module>-<desc>
fix/<module>-<desc>
hotfix/<desc>
```

示例：

- `feature/chat-streaming`
- `fix/login-token-expire`
- `hotfix/crash-startup`

`<module>` 与 `<desc>` 仅使用小写字母、数字、`-`，避免下划线与缩写歧义。

## 4. Commit Message

使用 **Conventional Commits**：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 4.1 Type

| Type | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 修复缺陷 |
| `docs` | 仅修改文档 |
| `refactor` | 重构（既不是新功能也不是修 bug） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建 / 工具 / 依赖 |
| `style` | 仅格式调整（不影响逻辑） |
| `revert` | 回退 |

### 4.2 Scope

可选，建议使用模块名：`home`、`login`、`settings`、`chat`、`network`、`data`、`auth`、`ui`、`docs`、`build`。

### 4.3 Subject

- 必须使用英文；
- 不超过 **50 字符**；
- 动词开头，第一人称单数不用「我」；
- 不加句号。

### 4.4 Body / Footer

- 解释「为什么」而非「做什么」；
- 关联 Issue：`Refs: #123` 或 `Closes: #123`；
- 破坏性变更：`BREAKING CHANGE: <说明>`。

### 4.5 单一目的

**每个 commit 必须只做一件事**。拆分原则：

- 不同性质的变更（功能、修复、重构、格式化）拆为不同 commit；
- 同一类型下若涉及多个独立子项，也应拆分；
- 便于 `git revert` / `git bisect` / cherry-pick。

### 4.6 示例

```
feat(chat): 支持消息流式输出

- 重构消息渲染逻辑为流式更新
- 支持逐字返回 AI 输出
- 优化 UI 刷新性能

Refs: #42
```

## 5. PR 规范

### 5.1 标题

`<type>(<scope>): <subject>`，与首个 commit 风格一致。

### 5.2 必含内容

1. **Why** —— 改动目的与动机；
2. **What** —— 主要改动点（可列子项）；
3. **How** —— 测试方式（手动 / 自动化）；
4. **截图 / 录屏** —— UI 变更必须提供；
5. **关联 Issue** —— `Refs:` / `Closes:`。

### 5.3 限制

- **禁止** 混入与主题无关的改动（请单独提交 `style:` / `refactor:` commit 或独立 PR）；
- **UI 改动必须独立 PR**，禁止与逻辑变更混提；
- CI（Build / Test / Lint）必须全绿。

### 5.4 合并策略

| 策略 | 是否允许 | 适用场景 |
| --- | --- | --- |
| Squash Merge | ✅ 默认 | 单个 PR 一个语义单元时 |
| Rebase Merge | ✅ 可用 | 需保留线性 commit 历史时 |
| Merge Commit | ❌ 禁止 | — |

## 6. 提交流程

```bash
# 1. 同步远端
git pull --rebase origin master

# 2. 创建功能分支
git checkout -b feature/<module>-<desc>   # 或 fix/<module>-<desc>

# 3. 本地开发

# 4. 本地验证
./gradlew assembleDebug

# 5. 按 Conventional Commits 提交并推送（单一目的）
git add <files>
git commit -m "feat(chat): 支持消息流式输出"
git push -u origin feature/<module>-<desc>

# 6. 创建 PR

# 7. CI + Review 通过后合并
```

## 7. PR 创建规范

使用 GitHub CLI 创建 PR，不要直接 push 到主分支。

### 基本格式

```bash
gh pr create \
  --title "type(scope): description" \
  --body "## 变更内容\n\n## 测试说明\n\n## 相关 Issue" \
  --base master \
  --head $(git branch --show-current)
```

### Commit 类型

- `feat` 新功能
- `fix` Bug 修复
- `refactor` 重构
- `docs` 文档
- `chore` 构建/依赖

### 示例

```bash
gh pr create \
  --title "feat(auth): add refresh token support" \
  --body "## 变更内容\n- 新增 refresh token 逻辑\n\n## 测试说明\n- 已测试 token 过期场景" \
  --base master \
  --head feature/refresh-token
```

### 注意事项

- 创建 PR 前确保本地分支已 push：`git push -u origin HEAD`
- PR 标题遵循 Conventional Commits 格式
- body 必须包含变更内容和测试说明


## 8. CI / 自动化要求

- **Build** 必须通过；
- **Test** 必须通过；
- **Lint** 必须通过（如已配置）；
- **禁止绕过 CI**（不使用 `--no-verify`、跳过 workflow 等手段）；
- PR 在 CI 全绿前不得合并。

## 9. AI / 自动化工具规则

### 9.1 禁止行为

- push 到 `master` / `master`；
- force push 到任何远程分支；
- rebase 已发布分支（`master` / 已合并的 `develop`）；
- 绕过 PR 流程直接合并；
- 自动合并未通过 CI 的 PR。

### 9.2 允许行为

- 在 `feature/*` / `fix/*` 分支上 commit、push；
- 创建 / 更新 PR；
- 生成 commit / PR 文案草稿；
- 运行本地 build / lint / test 验证。

### 9.3 AI 工作流

```
code change
  → feature/fix branch
  → commit (Conventional + 单一目的)
  → push feature/*
  → PR (含 Why / What / How)
  → CI + Review
  → merge
```

## 10. 注意事项

- **不提交** 到仓库：
  - `local.properties`
  - `build/`
  - `.gradle/`
  - `*.iml`
  - `node_modules/`
- **禁止** 遗留调试代码：
  - `println` / `console.log` / `Log.d` 等裸调试输出；
  - 临时 `TODO` / `FIXME`（未被 Issue 跟踪的）。
- 每个 commit 必须单一目的（见 [4.5](#45-单一目的)）。
