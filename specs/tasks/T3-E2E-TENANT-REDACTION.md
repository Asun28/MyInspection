---
id: T3-E2E-TENANT-REDACTION
title: Golden Evidence tenant report landlord/private sentinel 防泄露
depends_on: [T3-E2E-HASH]
status: todo
branch: T3-E2E-TENANT-REDACTION
worktree: C:\wt\T3-E2E-TENANT-REDACTION
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreHarness.kt
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceTenantRedactionE2ETest.kt
forbid:
  - 通过全局删除用户客观备注来让泄露断言假绿
  - 网络、Android、模拟器或真机依赖
non_goals:
  - 新增或修改 report composer 生产规则
  - Android PDF renderer、UI、权限、TalkBack 或进程死亡
acceptance:
  - "A1 同一 Golden Evidence 生成的 LANDLORD plan 精确包含 fixture 冻结的 landlord-only sentinel，证明夹具确实把敏感值送到分离边界"
  - "A2 TENANT plan 无 RemediationBlock、无 wearOrDamage 内部判断，所有结构字段与绘制 textRuns 均不含 fixture 的 landlord/private forbidden sentinels"
  - "A3 TENANT plan 仍包含 fixture 的 public objective sentinel，证明测试守的是定向 redaction 而非清空报告"
  - "A4 tenant plan 与每页 FooterBlock 仍携带同一 expected_data_hash"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.GoldenEvidenceTenantRedactionE2ETest"
dod_exit: 0
dod_assert: landlord-only sentinel 在房东版可见；tenant 版保留 public sentinel，但 landlord/private sentinel、remediation 与内部判断均为 0 命中
review_gate: codex {verdict:pass}
hygiene: public-present 与 private-absent 两侧断言分别做单点变异，避免空输出假绿
doc_sync: TASK-BOARD W5 redaction 节点（R5）
---

# T3-E2E-TENANT-REDACTION

只验证报告受众边界。Android renderer 和设备行为继续留给 T7-SMOKE-POLISH。
