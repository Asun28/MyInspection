# 0001 — 技术路线：原生 Kotlin + Jetpack Compose（2 个 Gradle 模块）

日期：2026-08-14 · 状态：accepted · 决策方式：3 方独立评审一致（Claude Fable 5 / DeepSeek V4 Pro / Codex GPT-5.6，round 1 全票）

## 背景
需求（`docs/inspection-app-requirements.md` §2）原以「开发者会不会 Kotlin」为判据留下 [待]。实施改为 AI 模型舰队分卡编码，判据变为：哪条路线让 AI 以小步可评审增量交付无 bug 结果最可靠。

## 决策
1. **原生 Kotlin + Jetpack Compose**。ghost overlay（CameraX PreviewView + UseCaseGroup/ViewPort）、离线听写（SpeechRecognizer）、SAF、EXIF、PdfDocument 全为第一方 API；Capacitor 反正要为相机叠层/听写写自定义 Kotlin 插件，徒增 JS↔Kotlin 桥接与两语言评审面。
2. **右尺寸 = 2 个 Gradle 模块**：`:core`（kotlin("jvm")，全部领域逻辑，无 android import，包级围栏 model/db/template/compliance/report/backup/canon）+ `:app`（薄 Android 壳）。依赖方向 app → core，无环。
3. 工程整体落在新顶层目录 `android/`（selftest `$RootAllow` 已登记）。

## 备选方案
- Capacitor + Web UI：被 3 方一致否决（桥接复杂度、插件质量、双语言）。
- 9 模块六边形拆分（Codex 方案）：包级职责全盘采纳，Gradle 模块数按右尺寸纪律（PLAN-TEMPLATE §4.5：小 MVP 默认模块化单体）2-1 收敛为 2 个；将来真需要再拆是机械搬移。

## 后果
- 全部重逻辑纯 JVM 可测（无模拟器）；verify 闸 = `gradlew --offline --no-daemon :core:check`。
- `frontend/` 骨架与其 rule 移除；Compose UI 卡由中档模型执行 + R3 评审。
- 本机需引导 JDK17 + Android SDK（T0-TOOLCHAIN，机器现状零工具链已核实）。
