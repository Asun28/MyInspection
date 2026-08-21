---
id: T0-DEBT-SELFTEST-NOGIT-ROUTING
title: 用有界生产夹具证明 seeded no-git 路由
depends_on: [T0-DEBT-SELFTEST-SKIP-VISIBILITY]
status: todo
branch: T0-DEBT-SELFTEST-NOGIT-ROUTING
worktree: C:\wt\T0-DEBT-SELFTEST-NOGIT-ROUTING
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 从 core 启动完整 seeded 分片
  - 以自由文本或部分 OK 文案推断 PASS/SKIP/FAIL
  - 改变既有 gate 编号、分片归属或真实 git 检测语义
non_goals:
  - mutation harness 的内存与 CPU 预算收敛
  - 8.2e rendezvous 负载稳定性
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture seeded-nogit-routing
dod_exit: 0
dod_assert: 专用有界 fixture mode 直接走生产 routing；git-present 控制组 skip count=0，git-absent 组输出完整机器记录与准确摘要；每个登记 gate 的 PASS/SKIP/FAIL 互斥，夹具在 routing 后立即退出且不进入完整 seeded 套件。
review_gate: codex {verdict:pass}
hygiene: `-Fixture seeded-nogit-routing` 在生产 routing 后、进入 seeded 套件前退出；先以反转生产路由条件的单句变异证明旧接线可逃逸；断言机器 ledger，不枚举易漂移的人类 OK 文案；完整 8.2e 只作附加证据。
doc_sync: 合并后更新 TD9 指针；TD9 仍保持 carded，等待 mutation-budget、load-stability 与 post-merge core。
---

# T0-DEBT-SELFTEST-NOGIT-ROUTING

## 根因

helper 级环境缺失夹具只能证明 skip primitive，不能证明 seeded 的生产路由条件确实调用该 primitive；反转条件仍可能令现有静态 hash 与 helper 夹具全绿。

## 有界边界

夹具复用生产路由判定与机器 outcome ledger，但在该路由完成后立即终止。禁止为了“真实”而从 core 重跑完整 seeded。
