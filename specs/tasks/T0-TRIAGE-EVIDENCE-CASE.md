---
id: T0-TRIAGE-EVIDENCE-CASE
title: triage 裁决证据按实际目录大小写语义确定身份
depends_on: [T0-LESSONS-CAP-TRIAGE-SPLIT]
status: todo
branch: T0-TRIAGE-EVIDENCE-CASE
worktree: C:\wt\T0-TRIAGE-EVIDENCE-CASE
allow_paths:
  - scripts/triage.ps1
  - scripts/selftest.ps1
  - specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md
forbid:
  - 从 Windows/macOS/Linux 名称猜测卷或目录的大小写语义
  - 让同来源 pass 因枚举顺序遮住 block，或改变 worktree 优先于 local 的既有来源顺序
  - 网络访问、发布动作、评审 schema 变更或 R3 预算放宽
non_goals:
  - lessons parser、探针 roster、文档教学面或其它探针语义
  - 重写 PR #127 / #137 历史
diagnosis: 大小写敏感性属于具体文件系统/目录而非 OS；OS 推断会错误折叠或拆分裁决路径，且同来源冲突可让 pass 按枚举顺序遮住 block
dod_command: pwsh -NoProfile -File scripts/triage.ps1 selfcheck
dod_exit: 0
dod_assert: actual-root 敏感/不敏感夹具均证明物理证据身份正确；同来源冲突 pass/block 必定选 block；删除身份归一化或冲突优先级时自检必红
review_gate: codex {verdict:pass}
hygiene: 从 PR #137 的 exact extraction 独立承接；复用既有 triage selfcheck，不建平行测试文件
doc_sync: none（探针名称、数量与用户命令不变，仅修裁决证据身份）
---

# T0-TRIAGE-EVIDENCE-CASE

修复 R3 在 PR #137 点出的 per-directory 大小写语义与冲突裁决确定性，不扩大 exact extraction 卡。
