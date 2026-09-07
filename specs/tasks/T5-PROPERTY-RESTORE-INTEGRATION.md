---
id: T5-PROPERTY-RESTORE-INTEGRATION
title: 物业包隔离替换、确认与启动恢复接线
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
acceptance:
  - "A1 [R1] 多物业当前库恢复单物业包前清晰提示全部替换，备份提示和取消保持原库不变。"
  - "A2 [R2] 完整预检后成功只含选定物业；篡改源/过期确认/低空间/撤权不越过替换点。"
  - "A3 [R3] 每个替换阶段杀进程并重启，验证 journal、回执、DB、媒体及重新装配的一致状态；覆盖与擦除恢复同时存在的优先级。"
  - "A4 [R4] v1 full 与 v2 full/property 的批准矩阵往返，v1 property 恒拒绝且旧数据指纹不变。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.restore.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 多物业当前库→单物业隔离替换，旧 v1 property 恒拒绝；v1 full/v2 full/property 正向往返；换包/过期确认/低空间/撤权保持原数据，每个 journal 阶段进程死亡后 DB/媒体/回执/重装配一致
review_gate: codex {verdict:pass}
---

# T5-PROPERTY-RESTORE-INTEGRATION

版本：V1 发布范围，format v2 的应用整合；不依赖产品 V2。复用 v1 恢复状态机，本卡新增 v2 分流与真实接线，不能另实现一套“简化恢复”。

六条路径覆盖同一恢复行为的 core、Android adapter 与启动装配及测试。恢复成功后重新构建使用 DB 的用例；不得让旧连接继续服务 UI。风险提示不等于取消既有恢复能力；全量备份是提示，不能擅自改成不可跳过的新业务门槛。
