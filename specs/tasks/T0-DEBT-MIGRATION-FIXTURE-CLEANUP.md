---
id: T0-DEBT-MIGRATION-FIXTURE-CLEANUP
title: 收敛 TD4 migration fixture 的 Windows worktree 清理
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
status: todo
branch: T0-DEBT-MIGRATION-FIXTURE-CLEANUP
worktree: C:\wt\T0-DEBT-MIGRATION-FIXTURE-CLEANUP
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 修改 TD4 的 allowlist、schema baseline、Gradle migration 配置或业务 schema
  - 无界重试、隐藏 git stderr，或把 cleanup 失败降级为 PASS
  - 扩大 seeded shard 的既有验收范围
non_goals:
  - 重审或重写 PR #47 已证明的 TD4 生产实现
  - 通用 worktree 生命周期框架
  - 修改 task/review/CI 行为
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: 17a3 的真实 migration 缺失/错误断言均通过；detached fixture 在 Windows 上以有界重试清理，成功后目录和 worktree 登记均不存在；终态失败保留 fixture 并输出每次 git exit/stderr；删除重试或诊断守卫的变异必须翻红
review_gate: codex {verdict:pass}
hygiene: 使用短临时路径；总重试预算不超过 10 秒；不得新增第二套 migration 夹具
doc_sync: 本卡 merge 后回到 T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST 的人裁/R5；TD4 仅在原卡完成后置 paid
---

# T0-DEBT-MIGRATION-FIXTURE-CLEANUP

## 来源

PR #47 的 TD4 实现、父级 reparse 防护、重复清单 mutation 与真实 migration ADDED/REMOVED 断言均已通过；第二轮 R3 只发现 Windows 上紧接 Gradle 负例执行 `git worktree remove --force` 偶发失败，且旧代码丢弃了 git 诊断。按两轮上限停止原卡，不在同一 PR 继续扩张。

## 单一产出

把现有 17a3 migration fixture 的清理改成短路径、有限次数重试和完整诊断保留。清理成功必须同时证明目录消失且 `git worktree list --porcelain` 不再含该路径；终态失败必须保留现场并 fail-closed。

## 顺序

本卡只在 PR #47 经人裁合并后执行。它不重新实现 TD4，也不为原卡自动重置 R3。
