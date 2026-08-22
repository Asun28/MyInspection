---
id: T0-DEBT-SELFTEST-MUTATION-BUDGET
title: 将 skip mutation 证明收敛到紧凑身份清单
depends_on: [T0-DEBT-SELFTEST-NOGIT-ROUTING]
status: todo
branch: T0-DEBT-SELFTEST-MUTATION-BUDGET
worktree: C:\wt\T0-DEBT-SELFTEST-MUTATION-BUDGET
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 保留数百份完整 selftest 源码副本
  - 为降低资源而删掉 reason、gate、batch truncation 或 FAIL/SKIP overlap 的变异证明
  - 改变 skip 的运行时语义
non_goals:
  - 生产 no-git 路由行为
  - workflow/seeded/core 重分片
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-mutation-budget
dod_exit: 0
dod_assert: skip 接线由 parse-once 的紧凑身份清单加有界代表性变异证明；core 不物化或重解析数百份 11k 行整脚本；预算诊断输出稳定 ASCII 哨兵，删除任一必要变异仍翻红。
review_gate: codex {verdict:pass}
hygiene: `-Fixture skip-mutation-budget` 在进入任何 shard 前退出；记录候选 mutation 数、实际执行数与峰值集合大小；用上界断言锁住回归，不以单机偶然耗时作为唯一判据；完整 8.2e 只作附加证据。
doc_sync: 合并后更新 TD9 指针；TD9 仍保持 carded，等待 load-stability 与 post-merge core。
---

# T0-DEBT-SELFTEST-MUTATION-BUDGET

## 根因

当前 harness 为每个 reason/gate 变异物化一份完整 selftest 字符串，并对每份重跑 whole-AST 扫描。R3 实测约 1.6 GB 工作集与 500+ CPU 秒，确定性受 runner 资源影响。

## 目标形态

生产接线先 parse once 投影为紧凑 identity inventory；行为变异只保留能独立杀死 reason、gate、batch truncation 与 outcome overlap 的代表集合，并用机器预算哨兵锁定候选数和在存集合上界。
