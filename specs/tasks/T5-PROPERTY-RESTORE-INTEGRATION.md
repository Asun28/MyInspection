---
id: T5-PROPERTY-RESTORE-INTEGRATION
title: 物业包导出、隔离替换与启动恢复接线
status: todo
depends_on: [T5-PROPERTY-SNAPSHOT-CLOSURE, T5-LOCAL-DATA-ERASURE, T1-APP-BOUNDARY-ASSEMBLY]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/backup/restore/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/restore/
  - android/app/src/main/kotlin/nz/myinspection/app/export/backup/
  - android/app/src/test/kotlin/nz/myinspection/app/export/backup/
  - android/app/src/main/kotlin/nz/myinspection/app/platform/composition/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/composition/
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当用户选择 v2 property 包时，系统应展示包实际范围及替换当前全部本机数据的影响；如果当前存在其他数据，则系统应先提示生成全量备份。"
  - "R2 只有当包身份、闭包、资源检查及绑定该预检的用户确认均有效时，系统才应以隔离快照替换当前数据，不得隐式合并。"
  - "R3 如果替换过程中发生进程中断，则系统应先恢复 journal 再开放业务页面，且不暴露新旧混合状态。"
  - "R4 如果包是 v1 property、不支持的格式/范围或与确认时内容不同，则系统应拒绝恢复并保持当前数据。"
  - "R5 当用户导出选定物业时，系统应将一致性快照经闭包校验、v2 writer 和生产 ArchiveStore 写入最终 SAF 对象，不得生成 v1 property 包。"
  - "R6 当导出关闭后，系统应重新打开最终对象，验证格式、范围、闭包及全部资产的路径/hash/size 后才写 VerifiedBackupReceipt；如果任一步失败，则系统应保留旧回执且不得新增成功回执。"
acceptance:
  - "A1 [R1] 多物业当前库恢复单物业包前清晰提示全部替换，备份提示和取消保持原库不变。"
  - "A2 [R2] 完整预检后成功只含选定物业；篡改源/过期确认/低空间/撤权不越过替换点。"
  - "A3 [R3] 每个替换阶段杀进程并重启，验证 journal、回执、DB、媒体及重新装配的一致状态；覆盖与擦除恢复同时存在的优先级。"
  - "A4 [R4] v1 full 与 v2 full/property 的批准矩阵往返，v1 property 恒拒绝且旧数据指纹不变。"
  - "A5 [R5] 从多物业源库经生产装配入口导出选定物业，实际 v2 包恢复后仅含目标闭包；绕过 v2 writer、错用 full 范围或断开 SAF 接线均使测试失败。"
  - "A6 [R6] 最终对象关闭重开后全验成功才生成回执；撤权、截断、错包、漏资产及内容篡改均失败；删除重开或全验即测试失败。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.restore.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app测试经生产装配执行多物业源→选定物业闭包→v2 writer→最终SAF对象关闭重开全验→回执→恢复，A5/A6故障与删除变异均拒绝；旧v1 property恒拒绝；v1 full/v2 full/property正向往返；换包/过期确认/低空间/撤权保持原数据，每个journal阶段进程死亡后DB/媒体/回执/重装配一致
review_gate: codex {verdict:pass}
---

# T5-PROPERTY-RESTORE-INTEGRATION

版本：V1 发布范围，format v2 的导出与恢复应用整合；不依赖产品 V2。复用前置快照、闭包、v2 writer/reader、ArchiveStore、回执及 v1 恢复状态机，本卡负责生产接线；不能用只测 core 投影代替导出往返。

六条路径覆盖同一恢复行为的 core、Android adapter 与启动装配及测试。恢复成功后重新构建使用 DB 的用例；不得让旧连接继续服务 UI。风险提示不等于取消既有恢复能力；全量备份是提示，不能擅自改成不可跳过的新业务门槛。
