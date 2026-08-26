---
id: T0-SCAFFOLD-SYNC-045
title: 拆分脚手架原始/当前版本语义，并把当前版本与 fleet 高水位推进到 v0.45.0
depends_on: [T0-SCAFFOLD-FLEET-LOOP]
parallelizable_with: []
status: in-progress
branch: T0-SCAFFOLD-SYNC-045
worktree: C:\wt\T0-SCAFFOLD-SYNC-045
allow_paths:
  - scripts/_config.ps1
  - scripts/scaffold-sync.ps1
  - scripts/triage.ps1
  - scripts/selftest.ps1
  - scripts/license-scanner-check.ps1
  - init-scaffold.ps1
  - .github/workflows/scaffold-selftest.yml
  - docs/SCAFFOLD-SYNC.md
  - docs/DELIVERY-CHAINS.md
  - docs/DEVOPS-WORKFLOW.md
  - docs/scaffold-architecture.html
  - CLAUDE.md
  - specs/tasks/T0-SCAFFOLD-FLEET-LOOP.md
  - specs/tasks/T0-SCAFFOLD-SYNC-045.md
forbid:
  - 丢失 MyInspection 由 v0.29.0 生成的原始 provenance；该值须迁入 ScaffoldOriginVersion 并保持不可变
  - 自动发布、自动开 issue、改写登录态，或让只读 stale 探针联网 fetch
  - 用上游整文件覆盖本项目已经分叉并加固的 scripts、workflow 或文档
non_goals:
  - 除 selftest shard split 外，在本卡实现 v0.45.0 group 1/3/4 的剩余能力；它们与本地 lessons/license/R3 在飞工作重叠，须另卡逐项回填
  - 修改 Android 产品代码、冻结契约、schema、依赖或运行期行为
diagnosis:
  root_cause: ScaffoldVersion 同时承担「最初生成来源」与「当前已裁决上游版本」两种会分叉的事实；同时 CI 把全部 seeded 缺陷夹具塞进单个 Windows job，最近一次实测 22m34s，超过 20 分钟 job 预算
  same_class: 已扫 _config getter、scaffold-sync check/report/selfcheck、triage stale、init 下游 stamping、selftest gate 8/17、license scanner 的 selftest liveness 契约、CI shard matrix、CLAUDE/footer、sync 文档与旧 fleet 卡
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-SYNC-045; if ($LASTEXITCODE -ne 0) { exit 1 }; & pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck; if ($LASTEXITCODE -ne 0) { exit 1 }; $out = (& pwsh -NoProfile -File scripts/scaffold-sync.ps1 check 2>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or $out -notmatch '\[FLEET-CURRENT\] evaluated up to v0\.45\.0; no newer upstream release on disk\.') { $out; exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git; if ($LASTEXITCODE -ne 0) { exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-remote; if ($LASTEXITCODE -ne 0) { exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-scanner
dod_exit: 0
dod_assert: 单卡形态合法；current=0.45.0、origin=0.29.0；缺/坏 ledger 仍回退 origin；check 精确报告 evaluated up to v0.45.0；fleet selfcheck 与三个 seeded 子片全绿
acceptance:
  - "A1 双字段：scripts/_config.ps1 的 ScaffoldOriginVersion 精确为 0.29.0，ScaffoldVersion 精确为 0.45.0，两者各有 getter 且 semver 机检"
  - "A2 fail-closed：缺 marker、marker-only、重复 marker、坏 decision 或版本缺口时账本不得 fallback 到 current 0.45.0；缺/空账本只回退 origin 0.29.0"
  - "A3 兼容性：旧 _config 只有 Get-ScaffoldVersion 时，Resolve-ScaffoldConfiguration 把该值同时视为 current/origin；坏配置仍阻断 public send"
  - "A4 init stamping：从 current=0.45.0 的源树生成新项目时，生成物的 ScaffoldOriginVersion 与 ScaffoldVersion 都为 0.45.0，不继承 MyInspection 的 0.29.0 origin"
  - "A5 决策账恰有一条 v0.45.0 partial 行，日期为 2026-08-26，并钉住 release tag db835867e6f1bab740f13b48e4bae009a34521ef"
  - "A6 group 2 明记为本地等价实现已取；group 1/3 明记部分等价与缺口；group 4 明记 deferred，不把未实现能力伪报 applied"
  - "A7 CLAUDE、sync/交付/workflow 文档、architecture 与旧 fleet 卡统一解释 origin/current 与十分片 CI；不再称 ScaffoldVersion 为不可变生成来源"
  - "A8 selftest 分片：CI 每个 OS 跑五片；legacy seeded/all 仍覆盖完整 gate 17；四个实际 region 各在末尾写收据，缺任一收据的变异必须翻红"
  - "A9 运行时验收：三个新 seeded 子片分别独立全绿；任何子片仍沿用 20 分钟硬上限，解决最近 Windows seeded 22m34s 的超时风险"
  - "A10 diff 只含 allow_paths，不改产品代码、依赖或冻结物"
review_gate: codex {verdict:pass}
hygiene: RED 覆盖 current/origin/init/CI 四腿；GREEN 后删除 origin fallback、init 改写或任一 region 收据必红；既有 group 2 自检不得回归
doc_sync: CLAUDE.md + docs/SCAFFOLD-SYNC.md + docs/DELIVERY-CHAINS.md + docs/DEVOPS-WORKFLOW.md + docs/scaffold-architecture.html + 旧 fleet 卡语义；合并后按常规 R5 归档本卡
---

# T0-SCAFFOLD-SYNC-045

## 产出

保留 origin v0.29.0，将 current 推进到 v0.45.0；group 2 已有本地等价实现，group 1/3 partial，
group 4 deferred。Windows seeded 22m34s 拆为 12m39s / 3m45s / 8m28s，且每个实际 region
以末尾收据证明执行。权威验收命令与断言见 front matter。
