---
id: T0-TRIAGE-EVIDENCE-CASE
title: triage 裁决证据身份、HEAD 绑定与失败可观测性
depends_on: [T0-LESSONS-CAP-TRIAGE-SPLIT]
status: merged
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
dod_command: $t=(& pwsh -NoProfile -File scripts/triage.ps1 selfcheck 2>&1 | Out-String); $x=$LASTEXITCODE; Write-Host $t; if ($x -ne 0 -or $t -cnotmatch '(?m)^triage selfcheck: PASS(?=（|[ \t\r]|$)' -or $t -cmatch '(?m)^[ \t]*(?:triage selfcheck: FAIL\b|FAIL\b)') { exit 1 }
dod_exit: 0
dod_assert: actual-root 敏感/不敏感夹具证明证据身份；同来源冲突必选 block；不同 root 使用不同 SHA 且 review→triage 精确绑定被测；相关证据 unreadable/unknown 时仍 exit 0 但产出明确 finding；删除任一守卫时自检必红
review_gate: codex {verdict:pass}
hygiene: 从 PR #137 的 exact extraction 独立承接；复用既有 triage selfcheck，不建平行测试文件
doc_sync: none（探针名称、数量与用户命令不变；仅修裁决证据身份、per-root HEAD 绑定与静默失败可观测性）
---

# T0-TRIAGE-EVIDENCE-CASE

修复 R3 在 PR #137 点出的 per-directory 大小写语义、冲突裁决确定性、per-root HEAD 绑定与静默失败，
不扩大 exact extraction 卡。

## 2026-09-09 current-source R4 evidence

此状态为当前交付投影；原验收契约与历史记录未改。证据绑定的是 HEAD
`53e7796b59460510fccc051fb069f619ef812551` 上的**未提交完整工作源文件**
`scripts/triage.ps1` SHA-256 `7429C4A7BA262E5E286AC89B94E20A481293EB0A671827EFF988E556B88B4A16`，
不是仅 HEAD 中的版本。baseline native exit=`0` 且精确 PASS、无 FAIL；13 枚当前目标变异和
8 枚经 AST 定位、整条删除唯一 `Add-Finding` CommandAst 的 guard-removal 变异均 native exit=`0`，
但各自有原具名 FAIL、无 PASS，故报告器 exit 0 没有被当作语义通过。完整 runner、manifest、raw stdout、
source copy 与逐文件 hash/bytes 镜像位于
`.review/current-source-r4-20260909/`；该镜像还保留本卡写入前的原始字节。R4 未运行 full selftest、verify、
R3、ship、网络或阶段命令；HEAD、RED receipt 与本卡 T35 receipt 状态在批前后未变。
