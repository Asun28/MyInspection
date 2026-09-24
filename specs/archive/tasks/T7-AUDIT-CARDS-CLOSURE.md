---
id: T7-AUDIT-CARDS-CLOSURE
title: 需求审校交付一：卡片与安全模块接口
status: merged
superseded_by: T7-AUDIT-REMOTE-CARDS
depends_on: []
allow_paths:
  - specs/android-module-boundaries.md
  - specs/tasks/T1-APP-BOUNDARY-ASSEMBLY.md
  - specs/tasks/T1-LOCAL-DATA-SECURITY.md
  - specs/tasks/T2-AUDIO-EVIDENCE.md
  - specs/tasks/T2-BULK-PHOTO-ASSIGNMENT.md
  - specs/tasks/T2-CAPTURE-UI.md
  - specs/tasks/T2-MEDIA-ACCESS-BOUNDARY.md
  - specs/tasks/T2-ONDEVICE-DICTATION.md
  - specs/tasks/T3-FIELD-UX-ACCEPTANCE.md
  - specs/tasks/T3-PDF-RENDER-DEVICE.md
  - specs/tasks/T3-REPORT-EXPORT-CORE.md
  - specs/tasks/T3-REPORT-EXPORT-UI.md
  - specs/tasks/T4-COMPLIANCE-OVERRIDE-IMPORT.md
  - specs/tasks/T4-COMPLIANCE-UPDATE-TRUST.md
  - specs/tasks/T5-BACKUP-FORMAT-V2.md
  - specs/tasks/T5-BACKUP-IO.md
  - specs/tasks/T5-PROPERTY-RESTORE-INTEGRATION.md
  - specs/tasks/T5-PROPERTY-SNAPSHOT-CLOSURE.md
  - specs/tasks/T6-TEMPLATES-REST.md
  - specs/tasks/T7-REMEDIATION-PROVIDER-DECISION.md
  - specs/tasks/T7-REMEDIATION.md
  - specs/tasks/T7-SMOKE-POLISH.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
requirements:
  - "R1 当交付本轮审校时，规格应遵守用户已定的V1预设/键盘、V1.1批量、V2语音，并保留provider、规则信任及格式细节的待澄清。"
  - "R2 当审校变更进入主分支时，系统应保持产品源代码、冻结协议及其他任务的改动不受本卡影响。"
acceptance:
  - "A1 [R1] 卡片结构检查通过；引用和依赖无环，V1不依赖未来语音/批量卡，备份format v2与产品V2分开。"
  - "A2 [R1] 未把待澄清标为已选；接口草案不冒充实现，严重缺陷不以仅登记指针算关闭。"
  - "A3 [R2] 实际diff限于allow_paths，无产品源码或冻结文件变化，完整diff通过正式R3；后续功能卡保持原有未实现状态。"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: check-cards与diff检查通过；依赖/引用/版本一致性复核记录和实际diff接受R3评审；无源码修改或功能完成虚假声明
review_gate: codex {verdict:pass}
non_goals:
  - 实现任何Android功能、改脚手架或冻结协议、替用户选择provider/信任根、同步分叉远端
hygiene: 纯文档交付，使用SkipRed记录非TDD；复用现有检查，不新增镜像测试
doc_sync: 仅将本收尾卡标为merged并归档；业务功能卡保持原状态，记录真实R3和本地合并证据
---

# T7-AUDIT-CARDS-CLOSURE

交付已批准的11张新产品卡、尚未提交的原卡改善、安全接口规格，以及版本决定的最小索引。功能卡仍为todo，不实施产品功能。T1-SPIKE-PLATFORM 的原修订已由其他任务提交，不重复搬回。全量叙述同步由后继卡完成。

用户2026-09-07要求 finish with task loop；本卡为该批已批准文档变更的交付包装。超过完整diff预算而拆两步；依赖顺序交付，不降低评审阈值。卡自身meta在main登记，实际交付在独立worktree；使用本地ship并保留正式R3，不把独立只读复核当成R3。

## 交付证据（2026-09-07）

正式 R3 第2轮 pass 于 `5bccf3ef`，本地 merge `d6e22084`。首轮指出 v2 物业生产导出无归属，已补入整合卡的接线、关闭重开全验、回执与往返验收，并禁止新建 v1 property 包。DoD、Android core check、Golden Evidence JVM Core E2E、范围/许可/防泄露全通过；完整评审632 changed lines / 59,190 chars。242个依赖节点无缺失/环，11张新产品卡保持todo。本卡无产品源码修改；纯文档使用SkipRed，R4不新增镜像测试。全量叙述由后继收尾卡同步，尚不代表功能实现或发布。

## Reconcile note (2026-09-24)

Origin delivered this audit's card and interface changes before the 2026-09 local/origin reconcile, as `T7-AUDIT-REMOTE-FOUNDATION` ([PR #240](https://github.com/Asun28/MyInspection/pull/240)) and `T7-AUDIT-REMOTE-CARDS` ([PR #247](https://github.com/Asun28/MyInspection/pull/247)); both are archived there as merged. `status: merged` records closure through those remote cards. Local master merged its own version of this card later, on 2026-09-07 (`d6e22084`), which the next reconcile slice carries.
