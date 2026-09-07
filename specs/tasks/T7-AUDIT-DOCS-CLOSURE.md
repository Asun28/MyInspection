---
id: T7-AUDIT-DOCS-CLOSURE
title: 需求审校交付二：需求与页面版本一致性
status: todo
depends_on: [T7-AUDIT-CARDS-CLOSURE]
allow_paths:
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
  - docs/inspection-app-requirements.md
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

# T7-AUDIT-DOCS-CLOSURE

把已交付卡片的版本、安全、基线与验收合同同步到需求、TASK-BOARD、DESIGN、Elements及CLAUDE。关闭前继阶段保留的旧叙述；不实施产品功能。

用户2026-09-07要求 finish with task loop；本卡为该批已批准文档变更的交付包装。超过完整diff预算而拆两步；依赖顺序交付，不降低评审阈值。卡自身meta在main登记，实际交付在独立worktree；使用本地ship并保留正式R3，不把独立只读复核当成R3。
