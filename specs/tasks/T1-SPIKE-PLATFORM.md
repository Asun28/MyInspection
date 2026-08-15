---
id: T1-SPIKE-PLATFORM
title: 真机平台可行性 spike ×4（overlay / 离线听写 / SAF / PDF 压力）+ 结论报告
depends_on: [T0-TOOLCHAIN]
parallelizable_with: [T1-SCHEMA-CORE]
status: todo
branch: T1-SPIKE-PLATFORM
worktree: C:\wt\T1-SPIKE-PLATFORM
allow_paths:
  - android/app/src/main/
  - docs/spike/
forbid:
  - 碰 android/core/（并行卡领地）与构建文件
  - 把 spike 代码当生产代码（正式实现各归其卡；spike 代码可留 debug 入口但不接主流程）
non_goals:
  - 生产级 UI/架构（一个 debug Activity 串四项探测即可）
  - 照片入库/数据模型（读写 app 私有目录裸文件即可）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; if (-not (Test-Path docs/spike/PLATFORM-SPIKE.md)) { exit 1 }
dod_exit: 0
dod_assert: assembleDebug 绿；docs/spike/PLATFORM-SPIKE.md 存在且四节各有真机结论（成立/降级 + 截图或参数记录）；overlay 结论明确二选一：ghost overlay 成立 / 降级「拍完并排比对」
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段 + TASK-BOARD 备注 + 若降级须改 T3-HISTORY-COMPARE 卡上下文（R5）
---

# T1-SPIKE-PLATFORM

## 产出
一个 debug Activity + 真机验证报告 `docs/spike/PLATFORM-SPIKE.md`，对四个 [验] 风险各下二值结论。

## 上下文包（执行模型必读）
- **①Ghost overlay（主目的，需求 §2 [验]）**：CameraX `Preview` + `ImageCapture` 绑同一 `UseCaseGroup` 且共 `ViewPort`（Codex 要点：buffer 与 display 坐标系不同，用 PreviewView 的 transform/`CoordinateTransform` 对位，不做手写 center-crop 算术）；PreviewView 上叠一张历史照（Compose Image，alpha 0.25–0.4，ContentScale 匹配 FILL_CENTER 语义）；拍一张存 app 私有目录（`getExternalFilesDir`）再读回显示。锁竖屏。判据：取景时历史照与实景可对位、拍存读回不变形。**跑不通 → 结论=降级「拍完并排比对」（两条路都零风险，需求原文）**。
- **②离线听写**：`SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` 探测 + `createOnDeviceSpeechRecognizer` 试转一句；记录该真机结果。不可用不算失败——键盘输入恒为兜底（计划立场），只记录事实。
- **③SAF**：`ACTION_OPEN_DOCUMENT_TREE` 选目录 → `takePersistableUriPermission` → 写一个测试文件再读回；重启 app 后授权仍在。为 T5-BACKUP-IO 提供真机参数。
- **④PDF 压力**：用 `android.graphics.pdf.PdfDocument` 循环渲染 80 张占位图（BitmapFactory inSampleSize 降采样、逐页 recycle）+ 一页中英混排文本（**打包 DroidSansFallback.ttf 试字形**，Typeface.createFromAsset）；记录峰值内存与耗时。为 T3-PDF-RENDERER 提供参数。
- 设备 = 用户本人手机（USB 调试）；执行者产出 APK + 操作指引，真机步骤由用户按指引点按，结论回填报告（人工环节，写清「请用户做什么、看什么」）。
- 报告模板（四节各含：做法 / 真机结果 / 结论二值 / 给正式卡的参数）。

## 卡片修订 2026-08-15（施工中实测 · `dod_command` 补 `-p android`）
原 `dod_command` 写作 `cmd /c android\gradlew.bat --offline --no-daemon -q :app:assembleDebug`（无 `-p`），
**不可运行**：Gradle 的 project dir 取自**当前工作目录**、而非 wrapper 脚本自身位置，
而相位命令的 cwd 是仓库根/worktree 根（那里没有 settings 文件），故恒报
`Directory '<repo root>' does not contain a Gradle build` 并退出 1——
即「DoD 永远红，且红的原因不是测试失败」。实测：原式 exit 1；补 `-p android` 后 exit 0。
**修订范围只有一件事**：在 `gradlew.bat` 后加 `-p android`。

> **同型缺陷遍及 `specs/tasks/` 全部 26 张卡**（`T1-CANON-HASH` / `T1-SCHEMA-CORE` / `T2-*` / `T3-*` …
> 每张的 `dod_command` 都是同一形态），另 `CLAUDE.md`「命令」节与 `T0-TOOLCHAIN` 第 99 行的人工核验命令
> 同样如此。`scripts/verify.ps1` 不受影响（它 `Push-Location android` 后再调，cwd 正确）。
> 本次**只改本卡**（改全量属跨卡 meta 手术，须编排者裁决），其余登记待扫——见交接报告。

## 验收
见 dod_command / dod_assert。

## 执行建议（TASK-BOARD）
首选 Opus 5 · max（新颖平台单点）；备选 Sonnet 5 max。难度 H。真机环节需用户配合（约 15 分钟点按）。
