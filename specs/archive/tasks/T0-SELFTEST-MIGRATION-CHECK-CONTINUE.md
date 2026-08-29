---
id: T0-SELFTEST-MIGRATION-CHECK-CONTINUE
title: 让 seeded migration 负例在 core:test 失败后继续跑真实 verifyMigrations task
depends_on: []
parallelizable_with: []
status: merged
branch: T0-SELFTEST-MIGRATION-CHECK-CONTINUE
worktree: C:\\wt\\T0-SELFTEST-MIGRATION-CHECK-CONTINUE
allow_paths:
  - scripts/selftest.ps1
  - specs/tasks/T0-SELFTEST-MIGRATION-CHECK-CONTINUE.md
forbid:
  - 给 17a3 migration fixture 之外的 Gradle 调用增加 --continue
  - 放宽 verifyMainMyInspectionDatabaseMigration、ADDED、REMOVED 或 probe 名称的精确诊断判据
  - 把真实 migration verifier 替换为静态 grep、mock task 或仅凭 :core:test 非零判绿
  - 修改 Android 生产代码、SQLDelight schema、Gradle 配置或其它 selftest 闸
non_goals:
  - 改变 :core:test 对错误 1.sqm 的失败行为
  - 优化 seeded 分片耗时或重构 17a3 migration fixture
  - 承接 T0-R3-FLOW-ENUM-SYNC 的枚举同步改动
diagnosis: released base d0de0501 上，17a3 的错误 1.sqm 同时触发 :core:test 与 verifyMainMyInspectionDatabaseMigration；Gradle 默认在前者失败后停止，导致真实 migration verifier 未运行、精确 REMOVED oracle 消失。直接基线 :core:test 通过，故不是仓库既有测试损坏；这是 seeded fixture 缺少继续执行参数的编排回归。
acceptance:
  - "A1 先记录 released base d0de0501 的真实 RED：直接 `:core:test` exit 0；`-Shard seeded` 至少连续两次非零，错误 1.sqm 输出含 :core:test 失败但不含 verifyMainMyInspectionDatabaseMigration，证明是 Gradle 短路而非基线测试常红"
  - "A2 17a3 migration fixture 的 Windows `gradlew.bat` 与 POSIX `sh gradlew` 两条真实调用都采用已验证的 `--no-daemon --continue -q :core:check` 参数顺序；该参数在两条调用中各恰好一次，且不扩散到其它 Gradle/selftest 路径"
  - "A3 缺 migration 与错误 1.sqm 两个真实 detached-worktree 负例仍均非零；输出分别精确命中 verifyMainMyInspectionDatabaseMigration + probe 名 + ADDED/REMOVED，不以 :core:test 的先行失败冒充 migration oracle"
  - "A4 selftest 对两条 OS 的完整调用分别保留锚定源码契约；只删除任一条调用的 `--continue` 时，其专属契约必须变红，且别处的同名参数不能满足。另在 detached fixture 同时注入稳定的 :core:test 失败与 missing-migration probe：无参数时输出有 test marker 且没有 migration marker；加参数后退出仍非零、两个 marker 都在场，证明真实任务图继续执行"
  - "A5 夹具 cleanup 的目录与 Git worktree 登记仍清除；失败时保留既有逐次诊断，不新增跳过或 fail-open 分支"
  - "A6 `pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded` exit 0；`scripts/verify.ps1` 与 R3 均通过"
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded -Fixture canary-harness
dod_exit: 0
dod_assert: focused canary 执行两条 OS scoped source/delete/relocate/diffuse 变异且零退出；正常 seeded 入口也强制调用该 canary，真实 behavior/cleanup 与聚合零退出由 A3–A6 的 seeded 收据证明。
review_gate: codex {verdict:pass}
hygiene: 扩展既有 17a3 与 canary source contracts，不建平行测试文件；两条源码单句删除、一枚移位/扩散、以及真实 no-continue 同时失败变异分别证明静态接线和行为 oracle，变异后逐字节还原。
doc_sync: 合并后在 master 归档本卡，并在 TASK-BOARD 记录 PR、merge SHA、R5 与 selftest release receipt；无用户文档改动。
---

# T0-SELFTEST-MIGRATION-CHECK-CONTINUE

## 目标与验收口径

修复 17a3 的测试编排：错误 `1.sqm` 先让 `:core:test` 失败时，Gradle 仍继续执行真实
`verifyMainMyInspectionDatabaseMigration`，让 migration oracle 本身决定该负例是否被精确击杀。

## Light Plan Forge

1. 在 released base `d0de0501` 记录直接 `:core:test` 绿、seeded wrong-`1.sqm` 缺 verifier marker 的 RED。
2. 先补 Windows/POSIX 两条完整调用的锚定契约与删除变异，再只给 17a3 的真实 `:core:check` 加参数。
3. 跑真实 missing/wrong migration 负例、删除参数变异、seeded 全分片与 verify。
4. 过 scope、R3、PR/merge、cleanup、R5；释放 `scripts/selftest.ps1` 给 PR #200。

## 被否决方案

- 只接受 `:core:test` 非零：无法证明 SQLDelight migration verifier 执行，oracle 变宽。
- 改任务顺序或禁用 `:core:test`：改变 `:core:check` 的真实图，不再代表生产闸。
- 把参数留在 PR #200：与枚举同步无关，违反该卡范围与 R3 的 drive-by 禁止。
