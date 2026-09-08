---
id: T4-COMPLIANCE-TRUST-REMOTE-RECORD
title: 同步规则信任决策的远端任务记录
status: merged
depends_on: []
allow_paths:
  - specs/tasks/T4-COMPLIANCE-TRUST-REMOTE-RECORD.md
  - specs/tasks/T4-COMPLIANCE-UPDATE-TRUST.md
requirements:
  - "R1 远端任务卡应反映用户已批准的发布责任与首次信任安排。"
  - "R2 决策记录应保留实际确认问题和答复，不能宣称真实制品已经存在。"
  - "R3 设计正文仍由原任务单独评审合并，此记录不解除导入依赖。"
acceptance:
  - "A1 [R1] 原任务卡删除尚未选定发布者或协议的过时描述。"
  - "A2 [R2] 原任务卡记录实际问题与用户答复，并明确制品证据尚未交付。"
  - "A3 [R3] 原任务保持 todo，ADR 和实现不进入此元数据 diff，原有 DoD 和范围不放宽。"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check
dod_exit: 0
dod_assert: 仅两张卡的规划记录；用户确认问题与答复齐全，原任务保持 todo 且原验收和范围不变，无虚报功能或制品完成
review_gate: codex {verdict:pass}
---

# T4-COMPLIANCE-TRUST-REMOTE-RECORD

用户于 2026-09-08 请求将已本地交付的 T4-COMPLIANCE-UPDATE-TRUST 通过 task-loop 合到 remote。远端尚保留批准前描述，本卡只同步决策事实；设计正文在原卡随后运行正式远端 ship。

PR #255 的首轮 R3 要求显式卡片范围和可核对的决策证据；本卡按 L101 补齐，承接该轮 block，不重置轮次。L18 要求原任务自身的规划修改先进入基线，故与原任务功能 diff 分开。原本地设计通过 R3 的 SHA 为 3e5d2bf23d119bd73c1951a2495bc98627cf093c；此历史记录不代替远端 R3/CI。

## 远端交付（2026-09-08）

PR #256 已合入 origin/master（c50fb95），正式 R3 pass 于 391b70cfc25928e2ff2c200332aa6a4a71e20ff8，所有确定性闸和同一 head 的 CI verify 通过。PR #255 由 #256 替代并关闭；两轮阻断后用户明确批准一次计数重置，保留历史裁决并补齐任务范围、相邻问答及责任声明边界。本卡只同步原任务规划事实；后续设计 PR #257 已独立通过 R3 和 CI 并合并。原卡保持 todo 的 A3 是本前置 PR 合并时的验收事实，随后设计交付后由 R5 改为 merged。
