---
id: T0-GRADLE-RUNTIME-FILE-INPUTS
title: 让 Gradle 看见测试真正的输入，消除两类「改了东西仍报绿、测试其实没跑」
depends_on: []
parallelizable_with: []
status: todo
branch: T0-GRADLE-RUNTIME-FILE-INPUTS
worktree: C:\wt\T0-GRADLE-RUNTIME-FILE-INPUTS
allow_paths:
  - android/core/build.gradle.kts
  # 同一根因的第三条腿：走 Gradle 的卡，其 dod_command 也可能报绿而测试没跑。
  # 这些卡文件不在任何实现卡的 allow_paths 内（各卡都改不了自己的 dod_command），故由本卡统一收口。
  - specs/tasks/T3-REPORT-COMPOSER.md
  - specs/tasks/T4-COMPLIANCE-ENGINE.md
  - android/core/src/test/kotlin/nz/myinspection/core/
forbid:
  - 关闭或绕过 Gradle 增量构建 / 缓存（`--rerun-tasks` 不是修法，是当前唯一的绕法）
  - 放宽 verifyMigrations、许可闸或防泄露闸
  - 改 configs/compliance/ 或 data/templates/ 里任何权威内容的取值
non_goals:
  - 把这些文件改成 classpath 资源（那会改动测试读取路径，属各自领域卡的设计决定）
  - 修 ComplianceEngineTest 里 `findRepositoryFile` 从 user.dir 向上走的实现形态（本卡只补输入声明，路径解析形态另议）
  - 给 android/app/ 或其它模块做同类排查
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试。
  - "A1 缺陷可复现（RED 先行）：在补声明之前，改动 configs/compliance/nz-rules-v1.json 的 exemptTypes 后直接跑 `:core:test --tests \"nz.myinspection.core.compliance.*\"`，记录其 **exit 0 且 task 报 UP-TO-DATE**；同一改动加 `--rerun-tasks` 后 exit 1 且失败文案含 `[INGOING, EXIT, ANNUAL]`——两条退出码与两段文案都落进卡片证据，证明假绿真实存在而非推测"
  - "A2 声明后同一复现必红：补上输入声明后，**不加** `--rerun-tasks` 重跑同一改动，exit 必须为 1；还原改动后 exit 回 0。两次都断言退出码精确值"
  - "A3 声明面取自实测而非猜测：用 `grep -rn 'findRepositoryFile\\|user.dir\\|File(\"\\.\\./' android/core/src/test` 的全量命中列出**每一处**在运行期读仓内文件的测试，逐处判定其读取目标并登记；清单落进卡片，新增同类读取须同时登记声明"
  - "A4 声明的是文件而非目录通配：`configs/compliance/` 与 `data/templates/` 各按**具体文件**（或显式 `include` 模式）声明为 `:core:test` 的输入，断言 `./gradlew :core:test --dry-run` 后改动**清单外**的仓内文件不会使 task 失效——否则每次无关改动都全量重跑，等于用性能换正确性"
  - "A5 data/templates 侧同样可证：改动 data/templates/routine-v1.json 的任一双语文案后，不加 `--rerun-tasks` 跑 `:core:test --tests \"nz.myinspection.core.content.*\"` 必须 exit 1（该目录已被 build.gradle.kts 挂成 main 资源目录，但**资源目录不等于测试输入声明**，须实测确认哪一侧生效）"
  - "A6 CI 语义不变：CI 是干净检出、无可 UP-TO-DATE 的基线，故本卡不改变 CI 行为；一条断言证明 `:core:check` 在干净树上的退出码与本卡前后一致（本卡只修本地增量路径的假绿）"
  - "A7 单句删除变异：删掉输入声明那一句后，A2 的断言必须变红（判据分类器：非零**且**命中 A2 的专属失败文本才算击杀）"
  - "A8 第二类假绿——构建缓存服务了纯文本断言：`android/gradle.properties` 设了 `org.gradle.caching=true`，而只读源码文本的测试（如 report 包的 source-purity 扫描）在**字节码不变**的编辑下会被缓存直接服务、断言根本不执行。RED 先行：往被扫描文件里插入一处违例，不加 `--no-build-cache` 跑 `:core:test` 记录其 **exit 0**；加 `--no-build-cache` 记录 exit 1。补上源码树的 `inputs.files(...)` 声明后，不加该开关的同一改动必须 exit 1"
  - "A9 两类机制在卡内分别命名，不混为一谈：① 运行期从 user.dir 向上走读到的仓内文件（Gradle 不知道它是输入）；② 断言只读文本、字节码不变故缓存命中（Gradle 知道输入但认为无需重跑）。两者的修法都落在 build.gradle.kts，但复现命令与判据不同，卡内各留一段可复算记录"
  - "A10 走 Gradle 的卡其 dod_command 必须真的跑：本仓所有 dod_command 里带 `gradlew` 的卡（现为 T3-REPORT-COMPOSER 与 T4-COMPLIANCE-ENGINE，判定面用 grep 全量取，不写死清单）均须带 `--rerun-tasks --no-build-cache`——两卡原命令带 `-q` 且无这两个 flag，`:core:test` 一旦 UP-TO-DATE 或 FROM-CACHE 就一行不打、直接以 0 退出，DoD 遂在**一个测试都没跑**的情况下报绿。RED 先行：改动被测源后不加 flag 跑其 dod_command 记录 exit 0，加上后记录 exit 1；补 flag 后不加也必须 exit 1。**只准追加这两个 flag**，命令主体与 `--tests` 选择器不动，故契约面（跑哪些测试、要什么退出码）逐字节等价，改的只是「保证它真的跑」"
dod_command: pwsh -NoProfile -Command "if (-not (Select-String -Path android/core/build.gradle.kts -SimpleMatch 'nz-rules-v1.json' -Quiet)) { exit 1 }"
dod_exit: 0
dod_assert: android/core/build.gradle.kts 显式把权威合规配置声明为 :core:test 的输入；A1–A10 每条都有可证伪证据，其中 A1/A2/A8 的六个退出码为实测记录。
review_gate: codex {verdict:pass}
hygiene: A2/A5/A8 各留一枚最小复现；A7 一枚单句删除变异；不为此新增测试框架或 Gradle 插件
doc_sync: 无（构建配置改动本体即文档）；若确认 data/templates 侧另有语义，在本卡正文记录实测结论

---

# T0-GRADLE-RUNTIME-FILE-INPUTS

## 起因（实测，非推测）

2026-08-23 修 `T4-COMPLIANCE-ENGINE` 时，一枚本该被杀死的变异报告「SURVIVED, exit 0」。
它没有存活——**测试任务根本没跑**：

```
mutated config（把 ANNUAL 加回 exemptTypes）
  plain run        exit=0     <- UP-TO-DATE，直接复用上一次的绿色 XML
  --rerun-tasks    exit=1     <- expected [[INGOING, EXIT]] but found [[INGOING, EXIT, ANNUAL]]
```

`configs/compliance/nz-rules-v1.json` 由 `ComplianceEngineTest` 的 `findRepositoryFile` 在**运行期**
从 `user.dir` 向上走目录找到并读取。Gradle 因此不知道它是 `:core:test` 的输入，改它不会让任务失效。

## 第二类假绿：构建缓存服务了纯文本断言（2026-08-23 同日第二次实测）

修 `T3-REPORT-COMPOSER` 时，一枚本该被杀死的变异同样报告 SURVIVED，机制却不同：

```
往被扫描的源文件插入一处违例
  plain run            exit=0     <- FROM-CACHE，字节码没变，任务被缓存直接服务
  --no-build-cache     exit=1     <- source-purity 扫描真跑了，断言变红
```

`android/gradle.properties` 设了 `org.gradle.caching=true`。一个**只读源码文本**的断言
（report 包的 source-purity 扫描）在注释级编辑下产出完全相同的字节码，于是 Gradle 认定
无需重跑、直接交出上次的绿色结果——**断言从未执行**。

两类机制必须分开命名（A9），因为复现命令与判据都不同：

| | Gradle 的认知 | 复现开关 | 修法 |
|---|---|---|---|
| ① 运行期读仓内文件 | **不知道**它是输入 | `--rerun-tasks` | 声明为 `:core:test` 的输入 |
| ② 纯文本断言被缓存 | 知道输入，但认为**无需重跑** | `--no-build-cache` | 把源码树也声明成输入 |

共同后果只有一个，而且正是变异测试最怕的那个：**批次分不清「守卫扛住了」与「什么都没跑」**。
两次实测里，第一反应都是「这枚变异存活了、守卫有洞」——真相是守卫好好的，测试没上场。

## 为什么值得单开一卡

被影响的是**权威法律配置**：48 小时通知下限、08:00–19:00 时窗、4 周频率上限、豁免类型集合都写在那份
JSON 里。今天的状态是——**改完那份文件、跑一遍卡片自己的 DoD 命令，可以在测试从未执行的情况下报绿**。

CI 是干净检出、没有可复用的基线，所以线上是安全的；不安全的是**本地增量路径**，而那正是作者自检与
评审者复核会走的路径。rubric §4 明确鼓励「评审者真跑一遍仓库自检来验证 diff」——本缺陷恰恰让那次
验证可能什么都没验证。

## 为什么不在 T4-COMPLIANCE-ENGINE 里顺手修

修法在 `android/core/build.gradle.kts`，不在那张卡的 `allow_paths` 内。按 rubric 立场
「不得给卡加范围」，越界的修法只能记 `[FOLLOW-UP]` 另开卡——就是本卡。

## 同类面必须一起查

`data/templates/` 已被 `build.gradle.kts` 挂成 `:core` 的 main 资源目录（`RoutineContentTest` 会加载真实模板），
但**资源目录声明与测试输入声明不是一回事**，须实测确认哪一侧生效（A5）。A3 要求用 grep 把
「测试在运行期读仓内文件」的**全部**命中列出来，避免只修一处、下次换个文件再犯——这是 L97 的同一形态。

## 被否决的替代

- **一律 `--rerun-tasks`**：那是绕法不是修法，且会让每次本地自检付出全量重跑的代价，实际结果是没人跑。
- **把文件搬进 test resources**：会改测试的读取路径与真实运行形态，属各自领域卡的设计决定，见 `non_goals`。
