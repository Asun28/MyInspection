---
id: T3-E2E-CORE
title: JVM e2e 闭环接入 verify 闸门 2（模板→巡检→finalize→报告→哈希断言）
depends_on: [T3-FINALIZE, T3-REPORT-COMPOSER, T2-ROUTINE-CONTENT]
status: todo
branch: T3-E2E-CORE
worktree: C:\wt\T3-E2E-CORE
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/
  - scripts/verify.ps1
forbid:
  - e2e 依赖网络/模拟器/真机（纯 JVM 确定性——verify 硬边界）
  - 为过闸放宽既有断言
non_goals:
  - 设备侧冒烟（人工清单，T7-SMOKE-POLISH）
dod_command: pwsh -NoProfile -File scripts\verify.ps1
dod_exit: 0
dod_assert: verify 闸门 2 真跑 e2e（gradle 调 e2e 测试类）且 $gate2Pending=false、不再打「未接」警告；e2e 覆盖：加载 routine-v1 真模板→建物业/tenancy/巡检→填项+照片元数据（临时目录假文件+真哈希）→finalize→composer 出房东/房客两版 plan→断言页脚哈希==DB data_hash==独立重算值、房客版无建议块
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段（verify 收紧为完整闭环）（R5）
---

# T3-E2E-CORE

## 产出
`core/e2e` 端到端 JVM 测试（真模板内容、内存 DB、临时文件系统）+ `scripts/verify.ps1` 闸门 2 接线。

## 上下文包（执行模型必读）
- 这是计划 §2 的「最小可验收闭环」变闸时刻：verify.ps1 里 `$gate2Pending` 置 false，闸门 2 执行 `cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.*"`，非零即 verify FAIL（fail-closed，别静默跳过——verify 文件头立场）。
- verify.ps1 属 harness 脚本：改动仅限闸门 2 那一段 TODO 区（别动闸门 1 与 Android 闸已有逻辑）；改后 `pwsh -File scripts\selftest.ps1` 必须仍 PASS（工作流自检纪律，CLAUDE.md 命令节）。
- e2e 数据构造复用各卡公开 API（不开测试后门）；照片=临时目录写假 JPEG 字节+真 SHA-256（管线 API）；断言三源一致：PDF plan 页脚哈希 / DB data_hash / 测试内独立 canonicalJson+sha256 重算。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max。难度 M。
