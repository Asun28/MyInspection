---
id: T5-PROPERTY-SNAPSHOT-CLOSURE
title: 按物业备份的逐表闭包与媒体双向核验
status: todo
depends_on: [T5-BACKUP-FORMAT-V2, T5-BACKUP-IO, T5-MEDIA-ARCHIVE-CONTRACT]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/backup/scope/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/scope/
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当系统构建物业包时，系统应从同一一致性源快照按 ADR-0006 的逐表规则生成该物业的数据闭包。"
  - "R2 当系统重新打开 staging 时，系统应比较源与 staging 每表的主键及 canonical-row-hash 集合，并验证逻辑引用、活跃媒体与 manifest 的双向完备。"
  - "R3 如果出现无规则表、漏行、多行、改值、跨物业数据、孤儿关系或活跃媒体缺失，则系统应拒绝验证该物业包。"
  - "R4 当物业没有巡检或包含历史/软删记录时，系统应按合同保留活跃定义、已引用历史版本及墓碑元数据，并按锁定版本/hash 重建内置短语。"
acceptance:
  - "A1 [R1] 多物业固定库仅导出选定物业允许的行，快照期间并发写入不能混入另一时点。"
  - "A2 [R2] 每张非空表分别漏/加/改一行及每类引用造孤儿都失败，集合验证不是单纯计数。"
  - "A3 [R3] manifest 少/多文件、路径越界、活跃记录缺字节和其他物业残留均拒绝，不产生隔离回执。"
  - "A4 [R4] 空物业恢复后可开始巡检；历史模板可重渲；仅墓碑引用且合同允许缺字节的媒体不被误判为活跃缺失。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.scope.*"
dod_exit: 0
dod_assert: 每张表漏/加/改及每种引用孤儿都拒绝；源/staging 的 PK+版本化 row hash 精确相等，媒体/manifest 双向完备；空物业/历史模板/墓碑成功，活跃缺字节失败；SnapshotRows 不接受 live DB/任意路径/SQL
review_gate: codex {verdict:pass}
---

# T5-PROPERTY-SNAPSHOT-CLOSURE

版本：V1 发布范围，依赖经过评审的 format v2。复用 T5-BACKUP-IO 的一致性快照能力；本卡只拥有 scope 投影与 closure verifier，不另造归档加密/SAF writer。

生产 v2 writer/SAF 导出与最终对象关闭重开全验由 T5-PROPERTY-RESTORE-INTEGRATION 接线验收；本卡通过不代表物业备份端到端可用。

逐表选择规则必须逐字核对 ADR-0006，新增表无规则即失败。格式级摘要与数据闭包完整性是两个不同证明，不能只凭 manifest hash 通过闭包。


## 受限快照读取与行编码

使用本卡内部 `SnapshotRows` adapter，只能消费 T5-BACKUP-IO 完成一致性检查后签发的私有快照引用；不得接受 live DB、调用方文件路径或外部 SQL。固定版本 registry 决定表名/列名，property 值使用参数绑定，返回 typed rows；对 staging 的写入仅限备份私有副本。测试必须证明任意文件、live DB 和用户提供 identifier/SQL 都无法进入此入口。

无需为本方案增加业务 `.sq/.sqm` 或改变 finalized 写权限；如果开工调查否定这一前提，先修订任务范围并完成冻结评审，不能临时绕过权限。

`canonical-row-hash` 沿用 ADR-0006 的验证要求，其精确编码由前置 FORMAT-V2 版本评审定义：字段顺序、NULL/类型标签、整数/文本编码、schema 绑定、软删字段入域及独立黄金向量。它与冻结 inspection canonical 分域且分版本；本卡不能自行发明或改写 data_hash 编码。
