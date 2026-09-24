---
id: T5-PROPERTY-SNAPSHOT-CLOSURE
title: 按物业备份的逐表闭包与媒体双向核验
status: todo
depends_on: [T5-BACKUP-FORMAT-V2, T5-BACKUP-IO, T5-MEDIA-ARCHIVE-CONTRACT]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/backup/scope/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/scope/
  - android/core/src/main/sqldelight/
  - docs/adr/0011-property-snapshot-queries.md
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当系统构建物业包时，系统应从同一一致性源快照按 ADR-0006 的逐表规则生成该物业的数据闭包。"
  - "R2 当系统重新打开 staging 时，系统应比较源与 staging 每表的主键及 canonical-row-hash 集合，并验证逻辑引用、活跃媒体与 manifest 的双向完备。"
  - "R3 如果出现无规则表、漏行、多行、改值、跨物业数据、孤儿关系或活跃媒体缺失，则系统应拒绝验证该物业包。"
  - "R4 当物业没有巡检或包含历史/软删记录时，系统应按合同保留活跃定义、已引用历史版本及墓碑元数据，并按锁定版本/hash 重建内置短语。"
  - "R5 当闭包需要新增或修改查询时，系统应先完成 SQLDelight 冻结契约版本评审，再通过生成的类型化查询 API 访问受限快照，不得在 adapter 中另建 SQL 查询路径。"
acceptance:
  - "A1 [R1] 多物业固定库仅导出选定物业允许的行，快照期间并发写入不能混入另一时点。"
  - "A2 [R2] 每张非空表分别漏/加/改一行及每类引用造孤儿都失败，集合验证不是单纯计数。"
  - "A3 [R3] manifest 少/多文件、路径越界、活跃记录缺字节和其他物业残留均拒绝，不产生隔离回执。"
  - "A4 [R4] 空物业恢复后可开始巡检；历史模板可重渲；仅墓碑引用且合同允许缺字节的媒体不被误判为活跃缺失。"
  - "A5 [R5] 版本评审列出精确冻结路径、逐表历史/软删覆盖、查询合同、迁移与schema快照；真实SQLDelight查询与迁移检查通过。无版本变化的情况提供现有API覆盖全部所需行的证据，不接受手写SQL替代。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.scope.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
dod_exit: 0
dod_assert: SQLDelight查询版本评审获批、冻结路径/迁移/schema快照证据齐全，core check包含真实verifyMigrations；每张表漏/加/改及每种引用孤儿都拒绝；PK+版本化row hash精确相等，媒体/manifest双向完备；空物业/历史模板/墓碑成功，活跃缺字节失败；SnapshotRows只用批准的生成API，不接受live DB/任意路径/SQL
review_gate: codex {verdict:pass}
version_review: this card = the version review
---

# T5-PROPERTY-SNAPSHOT-CLOSURE

版本：V1 发布范围，依赖经过评审的 format v2。复用 T5-BACKUP-IO 的一致性快照能力；本卡只拥有 scope 投影与 closure verifier，不另造归档加密/SAF writer。

生产 v2 writer/SAF 导出与最终对象关闭重开全验由 T5-PROPERTY-RESTORE-INTEGRATION 接线验收；本卡通过不代表物业备份端到端可用。

逐表选择规则必须逐字核对 ADR-0006，新增表无规则即失败。格式级摘要与数据闭包完整性是两个不同证明，不能只凭 manifest hash 通过闭包。


## 受限快照读取与行编码

使用内部 `SnapshotRows` adapter，只消费 T5-BACKUP-IO 签发的一致性私有快照引用，通过批准的 SQLDelight 生成 API 参数绑定物业值并返回 typed rows；不接受 live DB、任意文件路径、SQL 或 identifier。表/列与历史/软删查询的唯一权威仍是受版本评审约束的 `.sq`，禁止运行时表列 registry 拼装独立 SQL。staging 写入也只经批准 API，且限定私有副本。

先提交 `0011-property-snapshot-queries` 版本评审，盘点现有 API 是否完整覆盖全部所需行；新增/改查询必须明确 `.sq` 冻结路径、契约兼容性及所需 `.sqm`/schema 快照。批准后方可实现；无结构变化也须说明迁移/快照不变的证据，并运行 `:core:check` 的真实迁移验证。版本号/精确SQL及快照内容待评审决定，不以本规划卡预批准；ADR号若已占用先修卡换空号。不得改变 finalized 写权限或用原生 driver SQL 绕过冻结边界。

`canonical-row-hash` 沿用 ADR-0006 的验证要求，其精确编码由前置 FORMAT-V2 版本评审定义：字段顺序、NULL/类型标签、整数/文本编码、schema 绑定、软删字段入域及独立黄金向量。它与冻结 inspection canonical 分域且分版本；本卡不能自行发明或改写 data_hash 编码。
