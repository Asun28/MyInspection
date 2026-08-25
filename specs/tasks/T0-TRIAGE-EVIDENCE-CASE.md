---
id: T0-TRIAGE-EVIDENCE-CASE
title: triage 裁决证据身份、HEAD 绑定与失败可观测性
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
  - 以同一个 stub SHA 代替不同 evidence root 的真实 HEAD，或把 unreadable/unknown 静默吞成无发现
  - 网络访问、发布动作、评审 schema 变更或 R3 预算放宽
non_goals:
  - lessons parser、探针 roster、文档教学面或其它探针语义
  - 重写 PR #127 / #137 历史
diagnosis: 大小写敏感性属于具体目录而非 OS；HEAD 夹具对所有 root 返回同一 SHA，无法杀死错绑 RepoRoot 的变异；发现/枚举/解析/HEAD 失败被 null/continue 静默吞掉会让当前 block 消失
dod_command: pwsh -NoProfile -File scripts/triage.ps1 selfcheck
dod_exit: 0
dod_assert: actual-root 敏感/不敏感夹具证明证据身份；同来源冲突必选 block；不同 root 使用不同 SHA 且 review→triage 精确绑定被测；相关证据 unreadable/unknown 时仍 exit 0 但产出明确 finding；删除任一守卫时自检必红
review_gate: codex {verdict:pass}
hygiene: 从 PR #137 的 exact extraction 独立承接；复用既有 triage selfcheck，不建平行测试文件
doc_sync: none（探针名称、数量与用户命令不变，仅修裁决证据身份）
---

# T0-TRIAGE-EVIDENCE-CASE

修复 R3 在 PR #137 点出的 per-directory 大小写语义、冲突裁决确定性、per-root HEAD 绑定与静默失败，
不扩大 exact extraction 卡。
