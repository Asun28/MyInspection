---
id: T0-HARNESS-PERF
title: 横切优化 selftest 与 CI 墙钟时间（约 280 行 harness/测试改动）
depends_on: [T0-GATE-HARDENING]
status: in-review
branch: T0-HARNESS-PERF
worktree: C:\wt\T0-HARNESS-PERF
allow_paths:
  - scripts/selftest.ps1
  - .github/workflows/scaffold-selftest.yml
  - CLAUDE.md
  - docs/DELIVERY-CHAINS.md
  - docs/scaffold-architecture.html
forbid:
  - 跳过、弱化或改成 fail-open 的既有闸门
  - 减少 Windows/Ubuntu 任一平台的闸门覆盖
  - 新增运行时依赖、网络调用、登录态写入或自动发布
non_goals:
  - 改写单个业务闸门的判定逻辑
  - 按改动路径跳过 PR 必需检查
  - 优化 R3 模型评审耗时
dod_command: if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch 'function Invoke-SelftestAll') -and (Select-String -Path .github/workflows/scaffold-selftest.yml -SimpleMatch 'shard: seeded'))) { exit 1 }
dod_exit: 0
dod_assert: core 分片退出 0；8.2e 证明 2 OS×3 分片显式接线、矩阵变异必红、all 聚合器并发 barrier、Git/非 Git 快照、失败传播、StrictLint 转发及清理。完整 all 另须退出 0；无竞争同机样本不高于 300 秒。
review_gate: codex {verdict:pass}
hygiene: 聚合器与 CI 接线均用行为/变异夹具锁定，冗余断言经 R4 检视
doc_sync: CLAUDE.md、docs/DELIVERY-CHAINS.md、docs/scaffold-architecture.html
---

# T0-HARNESS-PERF

## 产出与尺寸声明

这是横切 harness 性能卡，明确批准约 280 行生产/回归测试改动，范围限于 selftest 分片聚合、CI 接线与三份权威说明。当前顺序基线为 552.4 秒；目标是在不减少任何闸门或 OS 覆盖的前提下，把默认本地墙钟降到 300 秒以内。

## 验收

```powershell
pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
pwsh -NoProfile -File scripts/selftest.ps1
pwsh -NoProfile -File scripts/verify.ps1
```

- 三条命令退出码均为 0。
- 默认 `all` 同时运行 `core`、`workflow`、`seeded`，任一失败则整体失败。
- 实测 `all` 不高于 300 秒；记录同机前后值，不把单次计时作为唯一正确性判据。

## 性能证据（同机、同持久化 JAVA_HOME/ANDROID_HOME、无其它 selftest 竞争）

| 版本 | 命令 | 墙钟 |
|---|---|---:|
| 顺序基线 `5ba3319` | `pwsh -NoProfile -File scripts/selftest.ps1` | 552.4 秒 |
| 并行候选（第一次完整绿） | 同上 | 268.6 秒 |
| 并行候选（文档/日志收口后） | 同上 | 282.8 秒 |
| 并行候选 `dc0fe3b` 前身代码 | 同上 | 276.4 秒 |
| helper 拆分 + seeded 优先启动的最终候选 | 同上 | 291.5 秒 |

四次优化样本均低于 300 秒，较顺序基线缩短约 47–51%。正确性的可复现代理不是易受负载影响的硬计时，而是 selftest 8.2e 的三进程 barrier：任一子进程若在另外两个启动前被等待，5 秒内确定性退出 29 并令聚合器失败。

## 非目标

不改变闸门内容，不引入路径跳过，不优化 R3，也不新增依赖。
