# AI 执行基础规则

## 描述

本文定义了 AI 工具的执行基础规则。

## 工作原则

1. **不要一次读取所有文档。**
2. 先理解任务，再判断需要哪些文档。
3. **仅加载当前任务所需的文档。**
4. 如果多个模块均受影响，可同时读取多个相关文档。
5. 不要凭经验猜测项目规范，如存在对应文档，应优先阅读后再进行实现。
6. 若任务涉及的规范文档不存在，可结合现有代码实现保持一致的编码风格。
7. **构建验证**：任何修改代码的操作之后，**必须**在本地跑一次最小编译验证，确认没有编译错误后再交付。最小成本命令为 `./gradlew.bat :app:compileDebugKotlin`（只编译 Kotlin，不打包 APK、不跑 lint/test；可加 `--offline` 跳过依赖解析）。仅当改动涉及资源、AndroidManifest、多模块或需要验证运行期行为时，再补跑一次 `./gradlew.bat assembleDebug`（详见 [app.md](doc/knowledge-library/3-modules/app.md) 第 6 节）做完整验证。
8. 当用户给出文件名，要求执行该文件描述的任务时，优先从 `/doc/.ai/dev` 目录下寻找对应的文档。
9. 当对用户提出的任务进行评估之后，认为该任务非常复杂，需要详细计划，则先在 `/doc/.ai/plan` 目录下生成计划文件，再进行任务的执行。文件名称格式为 `plan_{topic}_yyMMdd_HHmm.md`。
10. 当执行了 review 任务后，需要在 `/doc/.ai/review` 目录下新建 review 文件，文件名格式为 `review_{topic}_yyMMdd_HHmm.md`。
11. 当执行了对 review 的评估后，需要在 `/doc/.ai/review` 目录下新建评估文件，文件名格式为 `{review_file_name}_eval.md`。其中 `{review_file_name}` 为 review 文件的文件名（不包含扩展名）。
12. 当用户要求 git 提交，则按照 git 规范文档中的 `提交流程` 进行执行。

## 文档维护约定

- 在开发或修改了代码之后，如果对应的文档需要增、删、改，则**必须**及时更新。例如：
    - 修改 `gradle/libs.versions.toml` 或 `app/build.gradle.kts` → 更新 [tech-stack.md](doc/knowledge-library/1-overview/tech-stack.md)；
    - 新增 / 删除 / 重命名模块、调整包结构 → 更新 [project-structure.md](doc/knowledge-library/2-convention/project-structure.md) 与 [3-modules/app.md](doc/knowledge-library/3-modules/app.md)；
    - 修改 ViewModel / Repository / Composable 公共接口 → 更新 [architecture.md](doc/knowledge-library/1-overview/architecture.md)；
    - 修改业务流程（如注册、Token Rotation、登录保护、SSE 解析、会话同步、收藏）→ 更新 [4-biz/](../doc/knowledge-library/4-biz/README.md) 对应文档；
    - 修改路由 / 抽屉 / 登录跳转 → 更新 [5-ui/navigation.md](doc/knowledge-library/5-ui/navigation.md)；
    - 修改颜色 / 字体 / 形状 / 间距 → 更新 [5-ui/design-system.md](doc/knowledge-library/5-ui/design-system.md)；
    - 新增 / 修改服务端接口 → 更新 [doc/api/](../doc/api/) 对应文档。