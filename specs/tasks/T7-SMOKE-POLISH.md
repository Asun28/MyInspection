---
id: T7-SMOKE-POLISH
title: 真机全流程冒烟清单（产出 docs/SMOKE-CHECKLIST.md）+ 微修捆绑
depends_on: [T3-E2E-CORE, T5-BACKUP-IO, T5-LOCAL-DATA-ERASURE, T4-NOTICES, T2-CAPTURE-UI, T3-FIELD-UX-ACCEPTANCE, T7-REMEDIATION, T7-LOCAL-HEALTH-RELEASE, T3-REPORT-EXPORT-UI, T3-REPORT-IMPORT-UI, T5-PROPERTY-RESTORE-INTEGRATION, T4-COMPLIANCE-OVERRIDE-IMPORT, T6-TEMPLATES-REST, T6-HHC]
status: todo
branch: T7-SMOKE-POLISH
worktree: C:\wt\T7-SMOKE-POLISH
plan_ref: context/DESIGN.md#primary-inspection-journey
acceptance:
  - "A1 verify the complete flight-mode end-to-end inspection journey without hidden connectivity assumptions"
  - "A2 exercise provider, permission, storage, and process-death failures with deterministic recovery evidence"
  - "A3 exercise backup, restore, erase, health, and share boundaries without claiming external delivery"
  - "A4 verify TalkBack, 200% font scaling, light and dark theme, and measured performance on the target device"
  - "A5 attach device/build evidence for every result; unresolved P0/P1 findings require verified closure or explicit user risk acceptance within hard boundaries before release, not only a card link"
  - "A6 [R1] 两受众两格式重开/分享、隐私确认切换、DOCX 映射→草稿→正常 finalize 有同 build 证据；坏 DOCX/拒权/进程死亡不留半草稿。"
  - "A7 [R2] format v1 full/v2 full/property 恢复、v1 property 拒绝、Keystore 失效、规则激活失败与恢复、擦除 journal 都有设备证据；缓存结果、文件存在及待办指针不替代通过。"
  - "A8 [R3] V1 无 app-owned 录音/听写或批量照片入口，备注可用；未来两里程碑的卡不得倒挂为本卡依赖。"
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
dod_assert: docs/SMOKE-CHECKLIST.md 存在且含全流程条目（建物业→Ingoing→Routine（草稿恢复/杀进程）→拍照 overlay→预设短语/键盘→finalize→双受众 PDF/HTML→DOCX 导入普通草稿→通知生成回记→备份导出→清库恢复→保留期清理），每条有真机勾选结果；发现的缺陷逐条列出并标注（本卡内修 / 立新卡）；verify 全绿
requirements:
  - "R1 当验收 V1 发布时，系统应通过双受众 PDF/HTML 与 DOCX 导入普通草稿的真实往返，不得用库测试绿代替真机接线结果。"
  - "R2 当执行发布清单时，报告应逐项记录通过、失败或明确不适用及证据；没有验证的项目或仅登记的严重缺陷不应被算作发布通过。"
  - "R3 在 V1 采集流程中，系统应提供预设选项和键盘；批量照片 V1.1 与语音 V2 不应作为 V1 冒烟前置。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段（MVP 冒烟通过）+ TASK-BOARD（R5）
---

# T7-SMOKE-POLISH

## 产出
可复用的真机冒烟清单（此后每次发布前走一遍）+ 首轮执行记录 + 同类微修捆绑（每项在 dod_assert 逐条列断言）。

## 上下文包（执行模型必读）
- 清单结构照 PLAN §2 设备侧冒烟 + Codex Q7 建议面（相机旋转/overlay 对位/预设短语与键盘/Drive-OneDrive 导出恢复/**进程死亡**/PDF 目检中文字形）。
- 微修捆绑纪律（PLAN-TEMPLATE §7 捆绑卡例外）：同类小修共用本 worktree/一次评审/一个 PR；跨子系统缺陷或能力级问题 → 登记 tech-debt-tracker 或新卡，不塞本卡。
- 执行形态：模型出 APK + 清单，用户真机走查回填结果（人工环节），模型按结果修文案/空态/错误信息类问题。错误信息标准：**指名字段+位置+下一步**（Property Inspect 2.2★ 教训：「validation error 但不说哪错」是差评首因，docs/research/property-inspect.md F）。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · medium；备选 DeepSeek V4 Pro。难度 S（但含用户配合环节）。

## 2026-09-06 发布规划

发布范围见 TASK-BOARD 的版本计划。发现清单须分开“已经采集证据”和“可以发布”；P0/P1 关闭/明确风险接受记录都绑定实际构建与复测。新增能力跨子系统修复由独立卡承接；本卡不因题为微修而扩大修改范围。
