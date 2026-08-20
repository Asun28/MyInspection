---
id: T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS
title: 让 selftest 分片与 all 汇总稳定点名失败闸
depends_on: [T0-DEBT-SELFTEST-CRITICAL-PATH]
status: merged
branch: T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS
worktree: C:\wt\T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 依赖本地化告警 prose、ANSI 颜色或日志行号解析失败闸
  - 只在即时 Warning 点名而让分片/聚合终态继续退化为裸 selftest FAIL
  - 改变任一既有闸的通过/失败语义、分片归属或 fail-closed 退出码
non_goals:
  - 建立 skip 台账或修改 8.2e rendezvous 时限
  - 改 CI matrix、workflow 触发器或本地 all 的错峰策略
  - 顺手迁移 TD134 的 ship/review/archive 状态码
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SELFTEST-FAILED-GATES]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SELFTEST-ALL-FAIL]'))) { exit 1 }"
dod_exit: 0
dod_assert: Fail 事件以稳定 gate id 记入去重台账；单分片非零终态输出 [SELFTEST-FAILED-GATES] shard={name} gates={ordered ids}；all 汇总从子进程结构化哨兵保留 shard/gate 关联并输出 [SELFTEST-ALL-FAIL]；缺失或畸形子哨兵仍以 shard/exit fail-closed 点名，绝不退化为仅 exit-code 列表。
review_gate: codex {verdict:pass}
hygiene: hermetic 子分片覆盖单闸、多闸、重复 Fail、无哨兵非零与全绿控制组；删除 gate 捕获、子哨兵或聚合解析任一层均有专属变异翻红
doc_sync: TD9 保持 carded；本卡只偿还失败点名，skip 与 8.2e load-flake 仍由另外两卡接续
---

# T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS

## 目标与证据

历史 run `31941736470` 的 attempt 1/2 都在 8.2e 发出 Warning，终态却只留下 `selftest: FAIL`；attempt 3 在同一 SHA 通过。让失败闸身份穿过单分片终态和 `all` 聚合，CI 摘要无需全文搜索即可定位。

## 验收边界

- gate id 必须来自结构化运行状态；允许从现有 Fail 消息的稳定前缀提取并以当前 Step id 兜底，但不得把整段 prose 当协议。
- 同一闸多次 Fail 只在有序摘要出现一次，原始 Warning 仍完整保留。
- 子进程非零但无合法失败哨兵时，聚合器仍点名 shard 与 exit code，且整体非零。
- 全绿路径不得输出失败哨兵。

## 资源冲突

本卡与 `T0-DEBT-SELFTEST-SKIP-VISIBILITY`、`T0-DEBT-SELFTEST-LOAD-STABILITY` 及 TD134 实现卡共享 `scripts/selftest.ps1`；没有业务依赖，但执行和合并宽度必须为 1。

## 合并记录

PR #31 以 master `b8dee45` squash 合并；最终实现提交为 `155d528`。单分片终态与 `all` 汇总现输出稳定的 ASCII shard/gate 哨兵，缺失或畸形子哨兵仍以 `UNKNOWN(exit=...)` fail-closed。8.2e hermetic 夹具覆盖单/多 gate、重复 Fail 去重、有效/缺失/畸形哨兵、全绿控制组及捕获/发射/聚合解析 mutation；R3 指出的既有复合前缀与运行时 prose 漂移均已纳入表驱动回归。隔离 `selftest -Shard core`、DoD、`verify`、范围、许可、密钥与最终 R3 均通过。TD9 完成第 1/3 张残债卡，保持 `carded`。
