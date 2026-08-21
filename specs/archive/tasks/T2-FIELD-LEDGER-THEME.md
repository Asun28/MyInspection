---
id: T2-FIELD-LEDGER-THEME
title: Field Ledger Material 3 主题契约：light/dark token 与语义状态角色
depends_on: [T1-SPIKE-PLATFORM]
status: merged
branch: T2-FIELD-LEDGER-THEME
worktree: C:\wt\T2-FIELD-LEDGER-THEME
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/kotlin/nz/myinspection/app/ui/theme/
  - android/app/src/test/kotlin/nz/myinspection/app/ui/theme/
forbid:
  - 改 MainActivity、capture 组件、相机、导航或 :core
  - 动态壁纸取色改变 OK/attention/error/privacy 的稳定语义
  - 新增外部字体、图标或未在版本目录中钉住的依赖
non_goals:
  - 把主题接到一次性 skeleton 或真实 capture root（T2-CAPTURE-UI）
  - inspection item、evidence rail、按钮与 sheet 的组件实现
  - 间距 API、动画系统或平板布局
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: light/dark ColorScheme、Typography、Shapes 与五个语义状态角色单测全绿；assembleDebug 绿；仅使用已钉住的 Compose Material 3 与 TestNG 依赖；无 capture/root 行为变化
review_gate: codex {verdict:pass}
hygiene: 每个 token 只由一条最小断言钉住，删除重复映射测试（R4）
doc_sync: T2-CAPTURE-UI 标记主题前置已满足；若实现需偏离 context/DESIGN.md，先同步设计理由（R5）
---

# T2-FIELD-LEDGER-THEME

## 产出
把 `context/DESIGN.md` 的视觉决策变成可编译、可测试的 Compose Material 3 主题契约，但不接入任何生产页面。

## RED-first
先增加 app 的 TestNG 测试能力，再写 `FieldLedgerThemeContractTest`。首个测试直接引用尚不存在的 `fieldLedgerLightColorScheme`，断言 primary、primaryContainer、secondary、tertiaryContainer、background/onBackground 与 error；编译失败即 RED，并由 `task.ps1 -Phase red` 留证。随后才实现主题。

## 固定语义角色
- OK：primary container/foreground/rail。
- Needs attention：tertiary container/foreground/rail。
- Critical：error container/foreground/rail。
- Not applicable：surface container high + on-surface-variant + outline。
- Privacy：独立 violet container/foreground/rail，不与 defect 共色。

light 值来自 `context/DESIGN.md`；dark 值必须逐对做对比度核验。capture 中禁用动态色，避免现场证据语义随壁纸变化。

## 验收
见 front-matter。该卡只冻结主题契约；`T2-CAPTURE-UI` 负责在真实 app root 接入并删除 skeleton。

## R3 round-cap 后续

PR #41 两轮 R3 后仍剩一类完整性缺口：未显式映射的 Material 3 `ColorScheme`/`Typography` 角色会继承库默认值，可能让标准组件重新出现未批准的紫色/中性色或字号。按两轮上限停止扩张本卡；人裁本 PR 后由 `T2-FIELD-LEDGER-THEME-R3-CLOSURE` 精确补齐，已通过的 light/dark 核心 palette、五个语义状态、AA 对比度与 shape 合同不重审。

## R5

原主题合同由 PR #41 / master `f2f32d0` 合并；round-cap 遗漏由 PR #55 / master `cc4c67c` 闭合，`T2-CAPTURE-UI` 已改以前置完整主题合同为准。
