---
id: T0-TOOLCHAIN
title: 本机 Android 工具链 + android/ Gradle 双模块骨架空编译绿 + verify/CI 收紧
depends_on: []
status: todo
branch: T0-TOOLCHAIN
worktree: C:\wt\T0-TOOLCHAIN
allow_paths:
  - android/
  - .github/workflows/ci.yml
  - scripts/check-licenses.ps1
  - docs/LICENSE-POLICY.md
  - scripts/verify.ps1
  - scripts/selftest.ps1
forbid:
  - 未授权的运行期出站网络（Gradle 首次拉依赖属引导步、允许；此后 verify 恒 --offline）
  - 改动冻结契约 / 写登录态 / 自动发布
  - scripts/ 下除 check-licenses.ps1 外的任何脚本（见下「卡片修订 2026-08-15」）
non_goals:
  - 任何业务代码（schema/UI/相机都不在本卡）
  - Robolectric / Compose UI 测试基建（计划明拒，v1 不做）
  - 签名/发布配置（T7 后按需）
dod_command: pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: verify 输出含「Android :core check 全绿」（闸从「跳过」变「收紧」）；android/gradlew.bat 存在；:core 与 :app 空编译绿；check-licenses.ps1 输出**检出 Gradle 依赖清单**（不再是「未发现其它生态依赖清单」）；gradle-wrapper.properties 含 distributionSha256Sum
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段 + TASK-BOARD 备注（R5）
---

# T0-TOOLCHAIN

## 产出
本机可构建的 Android 工程底座：JDK 17 + Android SDK（命令行工具链）+ `android/` Gradle 骨架（`:core` 纯 JVM + `:app` Android 壳，均空编译绿）+ 全量依赖目录 pin + CI 收紧。

## 上下文包（执行模型必读）
- **机器现状（2026-08-14 已核）**：Windows 11，PowerShell 7、git、node 22、choco 在位；**无 java / 无 Android SDK / 无 gradle / 无 Android Studio**。磁盘 C: 34GB / D: 32GB 可用。
- 装法（choco 优先，winget 备选）：`choco install temurin17 -y`；SDK 用 Google commandline-tools（下载 zip 解到 C:\Android\cmdline-tools\latest），`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`，接受 licenses。**用 setx 设用户级 ANDROID_HOME 与 JAVA_HOME**（worktree 里没有 local.properties，全靠环境变量）。
- 工程形态（ADR-0001）：目录 `android/`（selftest $RootAllow 已登记）。`settings.gradle.kts` 含 `:core`、`:app`。`:core` = `kotlin("jvm")`，包根 `nz.myinspection.core`，禁 android import。`:app` = 最小 Compose Activity（空屏即可），包根 `nz.myinspection.app`，minSdk 26 / target 35。
- **本卡一次性 pin 全项目依赖目录** `android/gradle/libs.versions.toml`（后续卡只动源码目录、不再碰构建文件，避免并行卡 allow_paths 撞车）：Kotlin 2.x、AGP、Compose BOM、activity-compose、CameraX、androidx.exifinterface、kotlinx-serialization-json、SQLDelight 2.x（runtime + sqlite-driver(JVM 测试) + android-driver）、WorkManager、kotlin.test。全部 Apache-2.0/MIT；**JUnit=EPL 属测试期依赖**——跑 `pwsh -File scripts\check-licenses.ps1` 核口径，被拒即改纯 kotlin.test 断言。
- 引导时在线 `gradlew build` 一次填依赖缓存（用户级 ~/.gradle，worktree 共享）；此后 `scripts/verify.ps1` 的 Android 闸执行 `cmd /c gradlew.bat --offline --no-daemon -q :core:check` 必须全绿。
- CI：`.github/workflows/ci.yml` 的 verify job 加 `actions/setup-java`（temurin 17）+ `android-actions/setup-android`（或 sdkmanager 步）+ gradle 缓存；**PR 侧不加 path filter**（必需检查约束，见 CLAUDE.md CI 节）。
- `:core:check` 任务链 = test（+后续卡挂上的 SQLDelight verifySqlDelightMigration）；本卡先保证空工程下 check 绿。

## 卡片修订 2026-08-15（编排者裁决 · R3 第二轮 finding #2）
R3 连续两轮指出：`scripts/check-licenses.ps1` 的「其它生态清单探针」只按**固定路径**找 `build.gradle(.kts)`，
够不着本项目的**嵌套**清单（`android/app|core/build.gradle.kts`、`android/gradle/libs.versions.toml`），
于是对整个 Android 依赖图**零覆盖却报 PASS**。本卡一次性 pin 全项目 ~20 个依赖，而 CLAUDE.md 硬边界写死
「每加依赖跑 check-licenses.ps1」——闸看不见这一面，等于该边界从第 1 张卡起就是**假绿**（L165 同型：
断言面窄于契约、契约撤掉也照绿）。故**破例**把 `scripts/check-licenses.ps1` 纳入 allow_paths，范围**严格限定**：
- **要做**：探针改**递归发现**（排除 `.gradle/`、`build/` 等缓存/产物目录），使本仓跑出「检出 Gradle 依赖清单」
  并按既有语义进 `$coverageGap`（正常运行告警、`-Strict` 失败）；`docs/LICENSE-POLICY.md` 同步记一段
  「Gradle 生态当前=人工核验 + 覆盖缺口告警」（DocSyncMap 已把二者绑定，改脚本必须同步改文档）。
- **不做**（留 TD2 走独立卡）：完整的 Gradle 许可扫描器 / CI 强制 allowlist / 逐坐标许可查表。那是选型活，不是 T0 的副本。
- **附加闸**：动了 `scripts/` 就必须 `pwsh -NoProfile -File scripts\selftest.ps1` 全绿；**不得**为了让它绿而弱化任何既有断言。

## 卡片修订 2026-08-15 之二（编排者裁决 · R3 第三轮 finding #5）
本卡上文（「上下文包」第 5 条）白纸黑字写着 Android 闸执行 `gradlew.bat --offline --no-daemon -q :core:check`，
但 `scripts/verify.ps1:98` 实际是 `gradlew.bat --offline -q :core:check`——**少了 `--no-daemon`**：文档契约与
可执行投影漂移，且本卡的 `dod_command` 就是 verify.ps1，等于本卡自己的验收命令带着守护进程复用的非确定性
（现场实证：本机已有一个 CPU 760s 的残留 Gradle daemon）。故把 `scripts/verify.ps1` 纳入 allow_paths，**范围只有一件事**：
- **要做**：给 line 98 那一处 gradlew 调用补 `--no-daemon`（与卡片 prose、CLAUDE.md「命令」节口径一致）。
- **不做**：verify.ps1 的任何其它改动（闸门结构、闸 2 占位、uv/前端分支一律不碰）。
- **附加闸**：同上，`selftest.ps1` 须全绿且不得弱化断言（15f(a) 一类常设断言若与本改动冲突，报告我，别改断言）。

## 卡片修订 2026-08-15 之三（编排者裁决 · R3 第四轮 finding #3）
第四轮指出：本卡改了两处**闸门行为**（check-licenses 递归发现 · verify.ps1 `--no-daemon`），却一枚测试都没配——
两处改动**整段删掉**，既有 CoreModuleTest 与 verify 照样绿。这正是本仓 Tier-1 铁律 **L165** 说的失效形态
（守卫没被人看着红过 = 不算数）。把它推给技术债 = 明知违反自家必须层铁律还合并，对后面 24 张卡是坏先例；
另开一张卡又要为约 20 行断言走整套 R1–R5，而 T0 卡着全部依赖。故**第三次破例**纳入 `scripts/selftest.ps1`，范围**只有两枚断言**：
- **要做**：① 嵌套 Gradle 清单发现（**须含 `libs.versions.toml`**，见同轮 finding #2）的**行为断言**——夹具里放一个嵌套清单，
  跑 check-licenses 须报「检出」；② `verify.ps1` 的 Android 闸调用**含 `--no-daemon`** 的断言。
  **两枚各配一枚单句删除变异**：删掉被测那一句，断言必须变红（且非零退出须**确实来自该断言**、不是语法坏或更早的闸抢先中断 —— L165 的判据分类器要求）。变异结果贴进报告。
- **不做**：selftest 的任何其它改动；不重构既有闸；**绝不为了让它绿而弱化任何既有断言**。

## 评审者读到的是**工作树里的卡**（R3 第三轮 finding #1 的根因 · 见 TD3）
本卡在施工中被修订过两次，而修订按 L18 只落 master、不进功能分支——于是 `review.ps1` 交给评审者的工作树里
仍是**开卡时那份旧卡**，评审者据旧 allow_paths 判「越界」，与 ship 自己的范围闸（读 base ref 上的新卡、PASS）
直接矛盾。**处置**：把 master 合并进本卡分支，让工作树的卡与 master 一致后再 ship（本卡 `-SkipRed`，无 RED 证据可被
合并打乱，L148 的顺序禁忌在此不适用）。**判定权威始终是 base ref 上的卡**，不是工作树那份。

## 禁止
- 把 SDK/JDK 装进仓库目录；密钥/证书入库；改 `scripts/` 下除 `check-licenses.ps1` 外的任何脚本（verify 已提前接好）。

## 非目标
见 front-matter non_goals。

## 验收
```powershell
pwsh -NoProfile -File scripts\verify.ps1
```
- 期望退出码 0；断言见 dod_assert。另人工核：`cmd /c android\gradlew.bat --offline --no-daemon -q :app:assembleDebug` 绿（装机包能出）。

## 执行建议（TASK-BOARD）
首选 Sonnet 5 · max（本机装环境，交互性强，宜在 Claude Code 会话内执行）；备选 Opus 5。难度 M。执行者须把本卡「上下文包」整段读入再动手。
