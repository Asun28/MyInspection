---
id: T0-SCAFFOLD-SYNC-045
title: 区分 scaffold origin/current，并推进到 v0.45.0
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
  - 丢失 origin v0.29.0；自动发布/开 issue、改登录态、stale fetch 或整文件覆盖本地分叉
non_goals:
  - 实现 group 1/3/4 余项（shard split 除外）或改 Android/冻结契约/schema/依赖/运行时
diagnosis:
  root_cause: ScaffoldVersion 混用了 origin/current；Windows seeded 实测 22m34s，超过 20 分钟预算
  same_class: 已扫配置、check/report、triage、init、gate 8/17、license 接线、CI 与权威文档
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-SYNC-045; if ($LASTEXITCODE) { exit 1 }; & pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck; if ($LASTEXITCODE) { exit 1 }; $out = (& pwsh -NoProfile -File scripts/scaffold-sync.ps1 check 2>&1 | Out-String); if ($LASTEXITCODE -or $out -notmatch 'evaluated up to v0\.45\.0; no newer upstream release') { exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Shard core -Fixture through-gate8; if ($LASTEXITCODE) { exit 1 }; foreach ($s in 'git','remote','scanner') { & pwsh -NoProfile -File scripts/selftest.ps1 -Shard "seeded-$s"; if ($LASTEXITCODE) { exit 1 } }
dod_exit: 0
dod_assert: origin=0.29.0、current=0.45.0；缺账回退 origin；core 1–8（init/CI/17 闸）与三个 seeded 子片绿
acceptance:
  - A1 双版本、旧配置兼容；child origin=current；账本 fail-closed
  - A2 v0.45 partial 钉 tag；group 2 equivalent、1/3 partial、4 deferred
  - A3 CI 2 OS×5；legacy 完整；region 精确收据/缺失变异；子片低于 20m
  - A4 仅 allow_paths；不改产品、依赖或冻结物
review_gate: codex {verdict:pass}
hygiene: RED/GREEN 覆盖路由、init、CI、收据；group 2 不回归
doc_sync: CLAUDE + sync/delivery/workflow/architecture + 旧 fleet 卡
---

# T0-SCAFFOLD-SYNC-045
