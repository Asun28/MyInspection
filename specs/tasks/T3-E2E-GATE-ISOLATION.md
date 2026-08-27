---
id: T3-E2E-GATE-ISOLATION
title: 将 Golden Evidence 拆入独立 e2eTest source set 并由 Gate 2 单独执行
depends_on: [T3-E2E-CORE]
status: todo
branch: T3-E2E-GATE-ISOLATION
worktree: C:\wt\T3-E2E-GATE-ISOLATION
allow_paths:
  - android/core/build.gradle.kts
  - android/core/src/e2eTest/kotlin/nz/myinspection/core/e2e/
  - android/core/src/e2eTest/resources/e2e/
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/
  - android/core/src/test/resources/e2e/
  - scripts/verify.ps1
  - scripts/selftest.ps1
  - scripts/fixtures/gate2/android/gradlew.bat
forbid:
  - 把 e2eTest 挂入 check 或让普通 :core:test/:core:check 执行 Golden Evidence
  - Gate 2 依赖网络、Android UI、权限、TalkBack、模拟器、真机或进程死亡
  - 修改 Golden Evidence fixture、hash/redaction 断言或生产业务逻辑以迁就隔离
non_goals:
  - 新增 E2E 场景、调整 canonical hash/report 契约、设备侧冒烟
acceptance:
  - "A1 :core 建立独立 e2eTest source set 与同名 Test task；Golden Evidence Kotlin/fixture 资源迁出默认 test source set"
  - "A2 :core:test 与 :core:check 不执行 Golden Evidence，check 不依赖 e2eTest；用默认 test 的 e2e 包选择器无匹配红灯证明隔离"
  - "A3 :core:e2eTest 继续完整验证 DB data_hash、报告页脚 hash、独立重算 hash 与 tenant sentinel redaction"
  - "A4 Gate 2 精确执行 :core:e2eTest，保留 --offline --no-daemon；wrapper 缺失、命令未执行或任务非零继续 fail-closed"
  - "A5 selftest 锁定 source set 布局、Gate 1/Gate 2 完整参数序列及 success/failure/missing/命令变异"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.*"; if ($LASTEXITCODE -eq 0) { exit 1 }; pwsh -NoProfile -File scripts\selftest.ps1 -Shard workflow
dod_exit: 0
dod_assert: :core:check 与 :core:e2eTest 各自全绿；默认 :core:test 精确选择 e2e 包必须无匹配非零；workflow selftest 锁定独立 source set/task 与 Gate 2 fail-closed
review_gate: codex {verdict:pass}
hygiene: source set/layout、Gate 2 task 名、额外参数、失败传导、执行哨兵与命令删除均有单点变异或反向断言（R4）
doc_sync: CLAUDE.md W5 状态与本卡 status（R5）
---

# T3-E2E-GATE-ISOLATION

## 产出
把已验收的 Golden Evidence JVM Core E2E 从默认 `test` source set 迁入独立 `e2eTest`，让普通
`:core:check` 保持快速，只有 verify Gate 2 自动执行该闭环。

## 禁止
- 不修改 Golden fixture、hash/redaction 断言或生产业务行为。
- 不接入 Android UI、权限、TalkBack、模拟器/真机或进程死亡。
- 不把 `e2eTest` 重新挂入 `check`。

## 非目标（本卡刻意不做的能力）
- 不新增 Golden Evidence 场景或设备侧测试。

## 验收（DoD = 命令 + 退出码 + 断言）
```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.*"
pwsh -NoProfile -File scripts\selftest.ps1 -Shard workflow
```
- 期望退出码：前两项与 selftest 为 0；默认 test 的 e2e 选择器必须因无匹配而非零。
- 断言：默认测试与 Golden Evidence 执行隔离；Gate 2 保持离线、确定性、fail-closed。
