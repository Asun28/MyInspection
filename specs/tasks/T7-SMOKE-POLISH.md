---
id: T7-SMOKE-POLISH
title: 真机全流程冒烟清单（产出 docs/SMOKE-CHECKLIST.md）+ 微修捆绑
depends_on: [T3-E2E-CORE, T5-BACKUP-IO, T4-NOTICES, T2-CAPTURE-UI, T7-REMEDIATION]
status: todo
branch: T7-SMOKE-POLISH
worktree: C:\wt\T7-SMOKE-POLISH
allow_paths:
  - docs/SMOKE-CHECKLIST.md
  - android/app/src/main/
  - android/core/src/
forbid:
  - 借「微修」夹带能力级新功能（捆绑卡例外只覆盖同类小修——文案/边距/空态/错误信息）
non_goals:
  - 新功能；性能优化专项（有数据再立卡）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check; if ($LASTEXITCODE -ne 0) { exit 1 }; if (-not (Test-Path docs/SMOKE-CHECKLIST.md)) { exit 1 }; pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: docs/SMOKE-CHECKLIST.md 存在且含全流程条目（建物业→Ingoing→Routine（草稿恢复/杀进程）→拍照 overlay→听写/短语→finalize→双版 PDF→通知生成回记→备份导出→清库恢复→保留期清理），每条有真机勾选结果；发现的缺陷逐条列出并标注（本卡内修 / 立新卡）；verify 全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段（MVP 冒烟通过）+ TASK-BOARD（R5）
---

# T7-SMOKE-POLISH

## 产出
可复用的真机冒烟清单（此后每次发布前走一遍）+ 首轮执行记录 + 同类微修捆绑（每项在 dod_assert 逐条列断言）。

## 上下文包（执行模型必读）
- 清单结构照 PLAN §2 设备侧冒烟 + Codex Q7 建议面（相机旋转/overlay 对位/离线听写/Drive-OneDrive 导出恢复/**进程死亡**/PDF 目检中文字形）。
- 微修捆绑纪律（PLAN-TEMPLATE §7 捆绑卡例外）：同类小修共用本 worktree/一次评审/一个 PR；跨子系统缺陷或能力级问题 → 登记 tech-debt-tracker 或新卡，不塞本卡。
- 执行形态：模型出 APK + 清单，用户真机走查回填结果（人工环节），模型按结果修文案/空态/错误信息类问题。错误信息标准：**指名字段+位置+下一步**（Property Inspect 2.2★ 教训：「validation error 但不说哪错」是差评首因，docs/research/property-inspect.md F）。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · medium；备选 DeepSeek V4 Pro。难度 S（但含用户配合环节）。
