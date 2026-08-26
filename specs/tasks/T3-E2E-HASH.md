---
id: T3-E2E-HASH
title: Golden Evidence JVM 闭环与 DB/报告/独立重算三源 hash
depends_on: [T3-E2E-GOLDEN-FIXTURE, T3-FINALIZE, T3-REPORT-COMPOSER, T2-CAPTURE-CORE, T2-PHOTO-PIPELINE]
status: todo
branch: T3-E2E-HASH
worktree: C:\wt\T3-E2E-HASH
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreHarness.kt
  - android/core/src/test/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreE2ETest.kt
forbid:
  - 调用生产 canonicalJson 或 sha256Hex 生成独立重算期望
  - 测试后门、网络、Android、模拟器或真机依赖
  - 为过闸放宽既有 hash/finalize/report 断言
non_goals:
  - tenant sentinel redaction（T3-E2E-TENANT-REDACTION）
  - verify Gate 2 接线（T3-E2E-CORE）
acceptance:
  - "A1 JVM 测试加载真实 routine-v1，经 TemplateStore、InspectionRepository、StreamDigests/PhotoIngest/PhotoAssociationRecorder、FinalizeInspectionUseCase 使用内存 DB 与临时文件完成真实链"
  - "A2 fixture 的 item/room 照片实际写入临时目录并流式计算真 SHA-256，finalize 后 DB data_hash 精确等于冻结 expected_data_hash"
  - "A3 landlord DocumentPlan.dataHash 与每页 FooterBlock.dataHash 等于 DB data_hash，页脚绘制短 hash 前缀"
  - "A4 独立重算从 finalize 后 DB 重读并由测试自有 canonical byte builder + JDK MessageDigest 计算，不调用生产 canonical/hash helper，结果等于 DB data_hash"
  - "A5 所有命令保持 --offline --no-daemon，测试包不含 android/androidx import"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.e2e.GoldenEvidenceCoreE2ETest"
dod_exit: 0
dod_assert: 真实 fixture 闭环绿；expected fixture hash、DB data_hash、landlord plan/footer hash、独立重算 hash 四者逐字一致
review_gate: codex {verdict:pass}
hygiene: 三源断言各配能独立击杀的单点变异；共享搭建只进 harness，不复制测试逻辑
doc_sync: TASK-BOARD W5 hash 节点（R5）
---

# T3-E2E-HASH

这是纯 JVM 核心业务闭环，不是 Android UI E2E。报告输入只从 finalize 后持久化数据与真实模板投影；
独立重算刻意不复用生产 canonical/hash 实现，避免同一个 bug 自证。

