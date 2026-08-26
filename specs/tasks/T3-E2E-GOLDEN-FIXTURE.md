---
id: T3-E2E-GOLDEN-FIXTURE
title: 冻结 JVM Core E2E 的 canonical Golden Evidence Fixture
depends_on: [T2-ROUTINE-CONTENT]
status: todo
branch: T3-E2E-GOLDEN-FIXTURE
worktree: C:\wt\T3-E2E-GOLDEN-FIXTURE
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceFixture.kt
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceFixtureTest.kt
  - android/core/src/test/resources/e2e/golden-inspection-v1.json
forbid:
  - 从当前生产输出回抄 expected_data_hash 或报告期望
  - 网络、Android、模拟器或真机依赖
non_goals:
  - 建库、finalize、报告 compose 或 verify Gate 2 接线
acceptance:
  - "A1 fixture 精确引用真实 /routine-v1.json，并固定 property、tenancy、inspection、room/item 回答与确定性 id/time"
  - "A2 photo evidence 固定假 JPEG 字节、source、EXIF、room/item 归属与 privacy 标记；字节不从网络或设备读取"
  - "A3 expected_data_hash 是外部独立预计算并写死的 64 位小写 SHA-256 字面量，不由生产 canonical/hash API 生成"
  - "A4 landlord report 期望、tenant 必须保留的 public sentinel 与必须排除的 landlord/private sentinel 均在 fixture 中显式冻结"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.GoldenEvidenceFixtureTest"
dod_exit: 0
dod_assert: fixture 资源可严格解析，真实模板引用/固定证据/expected hash/双版本 sentinel 期望逐字段与字面量一致
review_gate: codex {verdict:pass}
hygiene: 每个 fixture 断言须能被对应字段单点变异击杀；不做纯镜像断言
doc_sync: TASK-BOARD W5 拆分登记（R5）
---

# T3-E2E-GOLDEN-FIXTURE

只冻结一份可跨层复用的证据输入与手工期望，不执行业务闭环。真实模板内容继续以
`data/templates/routine-v1.json` 为唯一真相源；fixture 仅引用它并选择确定性的房间/条目。

