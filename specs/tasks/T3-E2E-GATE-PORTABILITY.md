---
id: T3-E2E-GATE-PORTABILITY
title: 修复 verify Gradle wrapper 的 Windows/Linux 跨平台执行
depends_on: [T3-E2E-GATE-ISOLATION]
status: merged
branch: T3-E2E-GATE-PORTABILITY
worktree: C:\wt\T3-E2E-GATE-PORTABILITY
allow_paths:
  - scripts/verify.ps1
  - scripts/selftest.ps1
  - scripts/fixtures/gate2/android/gradlew
forbid:
  - 弱化 Gate 2 的 fail-closed、offline、no-daemon 或精确 :core:e2eTest 契约
  - 通过跳过 Ubuntu selftest、改 CI runner 或只保留 Windows 路径来消除红灯
  - 修改 Golden Evidence fixture、hash/redaction 断言、Gradle source set 或 Android UI/device 测试
non_goals:
  - 调整 Golden Evidence 内容、普通 :core:check 隔离、CI matrix 或产品运行时
acceptance:
  - "A1 verify 按主机选择 wrapper：Windows 使用 android/gradlew.bat，经 cmd 执行；非 Windows 使用 android/gradlew，经 sh 执行且不依赖 executable bit"
  - "A2 Gate 1 在两平台都精确执行 --offline --no-daemon -q :core:check；Gate 2 都精确执行 -p android --offline --no-daemon -q :core:e2eTest"
  - "A3 任一平台所需 wrapper 缺失、命令未执行或任务非零仍输出 Gate 2 marker 并使 verify 非零"
  - "A4 selftest 的真实 verify 夹具在 Windows/Linux 都执行本平台 fake wrapper，不再把非 Windows 三态跳过；错误 task、额外参数、失败传导、执行哨兵与命令删除变异继续被击杀"
  - "A5 Ubuntu core 的 12e 与 workflow 的 15f(a)/15f(c) 不再因 cmd 不存在误红；Windows 既有行为保持全绿"
dod_command: pwsh -NoProfile -File scripts\selftest.ps1 -Shard core; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts\selftest.ps1 -Shard workflow; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: core/workflow selftest 与真实 verify 全部 exit 0；本平台 wrapper 被真实执行且 Gate 1/Gate 2 参数序列精确，Linux 不依赖 cmd
review_gate: codex {verdict:pass}
hygiene: Windows/Linux wrapper 路由及 task/extra-arg/failure/sentinel/command-removal 各有反向断言或单点变异
doc_sync: CLAUDE.md 当前阶段与本卡 status（R5）
---

# T3-E2E-GATE-PORTABILITY

## 产出
让 `scripts/verify.ps1` 的两个 Gradle 闸在 Windows 与 Linux 都使用本平台 wrapper，修复默认分支
scaffold selftest 中 Ubuntu `cmd: command not found` 的误红，同时保持 Golden Evidence Gate 2 独立执行。

## 禁止
- 不跳过 Linux 分片，不把 scaffold selftest 改成允许失败。
- 不改变 `:core:e2eTest`、离线/no-daemon 或 fail-closed 契约。
- 不修改业务 E2E、Android UI/device 或生产逻辑。

## 非目标（本卡刻意不做的能力）
- 不调整 CI runner/matrix、Golden Evidence fixture、hash/redaction 或默认 `:core:check` 隔离。

## 验收（DoD = 命令 + 退出码 + 断言）
```powershell
pwsh -NoProfile -File scripts\selftest.ps1 -Shard core
pwsh -NoProfile -File scripts\selftest.ps1 -Shard workflow
pwsh -NoProfile -File scripts\verify.ps1
```
- 期望退出码：全部为 0。
- 断言：Windows/Linux 都真实执行各自 wrapper；Gate 1/Gate 2 参数与失败传导保持精确。
