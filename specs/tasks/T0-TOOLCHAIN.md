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
forbid:
  - 未授权的运行期出站网络（Gradle 首次拉依赖属引导步、允许；此后 verify 恒 --offline）
  - 改动冻结契约 / 写登录态 / 自动发布
non_goals:
  - 任何业务代码（schema/UI/相机都不在本卡）
  - Robolectric / Compose UI 测试基建（计划明拒，v1 不做）
  - 签名/发布配置（T7 后按需）
dod_command: pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: verify 输出含「Android :core check 全绿」（闸从「跳过」变「收紧」）；android/gradlew.bat 存在；:core 与 :app 空编译绿
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

## 禁止
- 把 SDK/JDK 装进仓库目录；密钥/证书入库；改 scripts/（verify 已提前接好，本卡不动 harness 脚本）。

## 非目标
见 front-matter non_goals。

## 验收
```powershell
pwsh -NoProfile -File scripts\verify.ps1
```
- 期望退出码 0；断言见 dod_assert。另人工核：`cmd /c android\gradlew.bat --offline --no-daemon -q :app:assembleDebug` 绿（装机包能出）。

## 执行建议（TASK-BOARD）
首选 Sonnet 5 · max（本机装环境，交互性强，宜在 Claude Code 会话内执行）；备选 Opus 5。难度 M。执行者须把本卡「上下文包」整段读入再动手。
