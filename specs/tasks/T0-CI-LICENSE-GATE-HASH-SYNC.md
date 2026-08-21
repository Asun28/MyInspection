---
id: T0-CI-LICENSE-GATE-HASH-SYNC
title: 同步 docs-only License gate 的 8.2b2 规范块哈希
depends_on: [T0-CI-DOCS-FAST-PATH]
status: todo
branch: T0-CI-LICENSE-GATE-HASH-SYNC
worktree: C:\wt\T0-CI-LICENSE-GATE-HASH-SYNC
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 修改 ci.yml 或 License gate 生产内容/顺序
  - 删除 8.2b2 哈希、warm-up 顺序或逆向移动变异断言
  - 用宽泛正则取代规范 UTF-8/LF 块哈希
non_goals:
  - 修改 docs-only 快速通道、Gradle 预热或许可扫描逻辑
diagnosis:
  root_cause: PR #114 在 License gate 块中新增 docs_only if，但 8.2b2 仍钉旧规范块 SHA-256 95C793...，令 master 的 Windows/Linux core shard 同时假红。
  same_class: 实际块保持 fail-closed 且仍位于 online warm-up 后、E2E 前；仅规范块哈希需同步为 DD4E2D...。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
dod_exit: 0
dod_assert: Windows/Linux 同构的 core shard 中 8.2b2 通过；License gate 规范块 SHA-256 匹配当前 docs-only if 内容；warm-up 前移动变异仍翻红
review_gate: codex {verdict:pass}
hygiene: 只更新单一规范块 SHA-256 字面量，并以 core shard 验证
doc_sync: 合并后归档本卡，TASK-BOARD 记录 run 32535429955 与 PR/commit/R3 证据
---

# T0-CI-LICENSE-GATE-HASH-SYNC

只同步 8.2b2 对当前 License gate 规范 UTF-8/LF 块的 SHA-256，不修改 workflow 或任何 gate 语义。
