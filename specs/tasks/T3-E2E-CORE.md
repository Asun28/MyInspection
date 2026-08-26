---
id: T3-E2E-CORE
title: 将已验收 Golden Evidence JVM E2E fail-closed 接入 verify Gate 2
depends_on: [T3-E2E-TENANT-REDACTION]
status: todo
branch: T3-E2E-CORE
worktree: C:\wt\T3-E2E-CORE
allow_paths:
  - scripts/verify.ps1
  - scripts/selftest.ps1
forbid:
  - Gate 2 依赖网络/模拟器/真机（纯 JVM 确定性——verify 硬边界）
  - 为过闸放宽既有断言
non_goals:
  - 修改 Golden Evidence fixture/E2E 断言；设备侧冒烟（T7-SMOKE-POLISH）
acceptance:
  - "A1 Gate 2 精确执行 :core:test --tests nz.myinspection.core.e2e.*，并保留 --offline --no-daemon"
  - "A2 Gate 2 命令未执行、测试选择缺失或 Gradle 非零时 verify 必须退出非零，不允许 warning 后继续假绿"
  - "A3 selftest 以临时假 gradlew 行为验证 Gate 2 success/failure/missing 三态，且不写真实 verify.ps1"
  - "A4 gate2Pending 与未接 warning 被移除；Android :core:check 的既有 Gate 1 行为不变"
dod_command: pwsh -NoProfile -File scripts\selftest.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: selftest 证明 Gate 2 success/failure/missing 三态；verify 真跑已合并的 e2e 包，保留 --offline --no-daemon，缺失或失败均 FAIL，且不再出现 pending 占位
review_gate: codex {verdict:pass}
hygiene: Gate 2 调用、非零传播与执行哨兵各有单点变异；真实 verify.ps1 全程只读验证
doc_sync: CLAUDE.md 当前阶段与 TASK-BOARD W5（verify 收紧为完整闭环）（R5）
---

# T3-E2E-CORE

## 产出
只负责把前三张卡已合并的 Golden Evidence JVM E2E 接入 `scripts/verify.ps1` Gate 2，并在
`scripts/selftest.ps1` 锁死 fail-closed 行为。

## 上下文包（执行模型必读）
- 这是计划 §2 的「最小可验收闭环」变闸时刻：Gate 2 执行 `cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.*"`，非零即 verify FAIL。
- verify.ps1 属 harness 脚本：改动仅限闸门 2 那一段 TODO 区（别动闸门 1 与 Android 闸已有逻辑）；改后 `pwsh -File scripts\selftest.ps1` 必须仍 PASS（工作流自检纪律，CLAUDE.md 命令节）。
- selftest 用临时仓与假 `gradlew.bat` 执行真实 verify 副本，验证 success/failure/missing；变异只写临时副本，不碰生产文件。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max。难度 M。
