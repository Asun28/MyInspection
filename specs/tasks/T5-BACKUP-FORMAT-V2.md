---
id: T5-BACKUP-FORMAT-V2
title: 备份格式 v2 版本评审及兼容读写
status: todo
depends_on: [T5-BACKUP-FORMAT]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/backup/format/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/format/
  - docs/adr/0010-backup-format-v2.md
  - specs/backup-format.md
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当新写入器生成包时，系统应使用已完成版本评审的 format v2 表达 full 或 property 范围，不得就地重解释 v1。"
  - "R2 当新读取器接收包时，系统应仅接受 v1 full、v2 full 和 v2 property；旧读取器应拒绝 v2。"
  - "R3 如果版本、scope、认证、manifest、资源限额或流结束检查不满足批准合同，则系统应拒绝包，不交付已验证恢复结果。"
acceptance:
  - "A1 [R1] 先有经批准的 header/manifest/兼容性版本评审，再提交新黄金向量；独立 v1 reader fixture 保持原拒绝行为。"
  - "A2 [R2] v1/v2/full/property/未知版本与范围矩阵逐项证明接受或拒绝，不从文件名猜测范围。"
  - "A3 [R3] 认证字段逐处篡改、截断、追加、重复/恶意条目和各资源边界均被拒绝；黄金向量独立计算。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.format.*"
dod_exit: 0
dod_assert: 具体 format/scope-row 编码版本评审获批后，独立 v1 reader 拒 v2，新 reader 只接受 v1 full/v2 full/property；黄金向量及篡改/截断/追加/资源边界测试通过，不改 inspection canonical
review_gate: codex {verdict:pass}
version_review: this card = the version review
---

# T5-BACKUP-FORMAT-V2

版本：V1 发布范围中的按物业备份前置；这里的 format v2 与产品 V2 语音不是同一个版本。ADR-0006 已批准恢复范围，没有批准冻结字节布局。

开工分两阶段：先提出具体布局/兼容向量并按冻结合同完成版本评审，再实现格式。待澄清：header 与 manifest 的精确定义及兼容布局。格式/测试目录已核对为现有 backup/format/；不得扩成物业闭包和整合恢复大卡。


版本评审同时定义独立 scope row 编码（schema binding、字段序、NULL/类型标签、整数/文本编码、软删字段、域和版本标签）及独立黄金向量。这里只定义 closure verifier 的契约，行读取和实际集合核验仍由 SNAPSHOT-CLOSURE 实现，不修改 frozen inspection canonical。

本卡 `version_review` 是按 ADR-0006 登记评审职责，不等于用户已经批准具体字节布局。ADR 路径 0010 为本计划预留；开工时若已被占用，先改卡到下一空号，禁止覆盖既有 ADR。

版本评审须盘点执行时 schema 的全部表，显式补齐 ADR-0006 之后新增表的 include/exclude、引用和回执处理规则（例如 template_room_def、property_room_config、归档回执相关表）；当前未决定的规则标待澄清，未关闭不能开始闭包实现。不得自行丢弃新表或导出目的地秘密。
