---
id: T2-FIELD-LEDGER-THEME-R3-CLOSURE
title: Field Ledger Material 3 全角色显式映射（PR #41 R3 收口）
depends_on: [T2-FIELD-LEDGER-THEME]
status: merged
branch: T2-FIELD-LEDGER-THEME-R3-CLOSURE
worktree: C:\wt\T2-FIELD-LEDGER-THEME-R3-CLOSURE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/ui/theme/
  - android/app/src/test/kotlin/nz/myinspection/app/ui/theme/
forbid:
  - 改 MainActivity、capture 组件、相机、导航、:core 或 Gradle 依赖
  - 接入动态色或让语义状态随壁纸改变
  - 重开原卡已闭合的核心 palette、五个语义状态、AA 对比度与 shape 合同
non_goals:
  - 把主题接入 app root 或实现 inspection UI 组件
  - 新增字体、图标、间距 API、动画或平板布局
  - 改 `context/DESIGN.md` 的已批准视觉方向
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: ColorScheme 的 inverse/fixed accent、surfaceVariant、inverse surface、surfaceBright/surfaceDim、surfaceContainerLowest/Highest 及 Typography 的 displayLarge/displaySmall/headlineSmall/titleSmall 均显式映射到 Field Ledger token，逐角色确定性测试全绿；无 Material 默认紫色/中性色或默认字号回落
review_gate: codex {verdict:pass}
hygiene: 只补 PR #41 round-cap 点名的遗漏角色；每个新增角色一条最小断言，不复制已闭合测试
doc_sync: 合并后把 TD140 置 paid，归档本卡，并在 T2-CAPTURE-UI 前置说明中记录主题契约完整
---

# T2-FIELD-LEDGER-THEME-R3-CLOSURE

## 根因

Material 3 的 `ColorScheme` 与 `Typography` 包含比 `context/DESIGN.md` 首批点名更多的标准角色。PR #41 只覆盖了现场核心 palette；构造器未传的角色会继承 Material 3 库默认值，标准组件仍可能消费这些默认紫色/中性色或默认字号。

## 单一产出

显式映射 R3 点名的全部遗漏角色，并用真实 `ColorScheme`/`Typography` 值逐项断言。颜色从既有 Field Ledger light/dark token 组合或派生，不引入第三套 palette；排版从既有层级映射，不新增字体资产。

## RED-first

先新增一组会在当前 PR #41 实现上失败的角色断言，确认至少一项仍等于 Material 默认值或不等于批准的 Field Ledger 映射；记录正式 RED 后才补生产映射。

## 边界

本卡只接住 PR #41 第 2 轮唯一剩余 finding。原卡已通过的 palette 精确值、语义角色关系、AA 对比度、Typography 既有层级和 Shapes 不重新设计；主题接入由 `T2-CAPTURE-UI` 负责。
