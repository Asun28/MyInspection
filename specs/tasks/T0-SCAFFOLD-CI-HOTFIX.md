---
id: T0-SCAFFOLD-CI-HOTFIX
title: 修复合并后 scaffold-selftest 的跨分支与跨 PowerShell 回归
depends_on: []
plan_ref: docs/TASK-BOARD.md#current-stage
status: in-progress
branch: T0-SCAFFOLD-CI-HOTFIX
worktree: C:\wt\T0-SCAFFOLD-CI-HOTFIX
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - scripts/license-scanner-check.ps1
  - scripts/selftest.ps1
  - specs/tasks/T0-SCAFFOLD-CI-HOTFIX.md
forbid:
  - 放宽探针清单、许可证分类或离线扫描的 fail-closed 语义
  - 改动产品代码、文档内容或新增扫描能力
non_goals:
  - 重构 selftest 或许可证扫描器
  - 修复与 GitHub run 32872898282 无关的失败
diagnosis:
  root_cause: PR #131 将 triage roster 从 10 枚扩为 11 枚，但随后合并的 PR #49 保留了 selftest 14g 的旧边界；同一 run 的 Ubuntu graph mutation 子进程又通过 Write-Error 输出长 prose，格式化换行把父进程依赖的完整 Expected 子串拆开，真实 RED 被误报为 mutation survivor；Windows seeded 的完整 mutation matrix 已超过统一 20 分钟硬上限而被 runner 取消；放宽后又证明 npm handoff 仅靠 --prefix 指定范围在 Windows runner 上不稳定：既出现过临时 node_modules/marker 在负例准备前已缺失，也出现过 npm 转而从仓库 cwd 寻找 package.json。
  same_class: 14g 的五个 roster 边界全部按 11 枚同步；graph suite 的全部 mutation 消费者统一改锚短 ASCII assertion code，覆盖 parser、wrapper、cache、invocation 与 subprocess 断言，不改生产 parser 或许可决策；仅 seeded 分片放宽到 30 分钟、其余分片仍为 20 分钟，并由 CI wiring contract 及“退回全 20 / 放宽全 30”双向变异钉死；npm handoff 显式进入各 fixture package root 执行，两项负例准备均改为 race-safe 幂等删除，并以稳定码另断言终态确实缺失，后续 missing-tree fail-closed 断言不变。
dod_command: pwsh -NoProfile -Command "& ./scripts/license-scanner-check.ps1 -Suite integration -SkipRealScan && ./scripts/selftest.ps1 -Shard core"
dod_exit: 0
dod_assert: 11 探针的五处权威 roster 重新一致；graph 全部 mutation 消费者在 Windows 与 Ubuntu 均能按稳定 ASCII assertion code 识别真实 RED；npm handoff 在显式 fixture cwd 内执行且负例准备终态缺失，不因 Windows 参数范围/异步清理假红；生产 parser 与许可决策不变；seeded 独享 30 分钟而其他分片仍为 20 分钟，且全 20 / 全 30 两种回归均被 wiring mutation 拦截；目标 integration 与 core 验收退出 0。
review_gate: codex {verdict:pass}
hygiene: 以失败 run 的现有断言作 RED；只运行 integration 与 core 两个直接相关验收，再由 GitHub scaffold-selftest 覆盖两 OS seeded。
doc_sync: none
---

# T0-SCAFFOLD-CI-HOTFIX

修复 PR #131 与 #49 顺序合并后首次完整 scaffold-selftest 暴露的两处确定性回归，不扩大范围。
