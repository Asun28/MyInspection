---
id: T1-APP-BOUNDARY-ASSEMBLY
title: 生产装配入口与巡检用例边界
status: todo
depends_on: [T1-LOCAL-DATA-SECURITY, T2-CAPTURE-CORE, T3-FINALIZE, T2-ROUTINE-CONTENT, T2-ROUTINE-CONTEXT-V2, T2-PHRASELIB]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/application/
  - android/core/src/test/kotlin/nz/myinspection/core/application/
  - android/app/src/main/kotlin/nz/myinspection/app/platform/composition/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/composition/
  - android/app/build.gradle.kts
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当应用装配巡检用例时，系统应由唯一装配入口提供同一数据库实例、时钟、ID 和已验证存储能力，屏幕不得取得数据库或任意文件根。"
  - "R2 当保存房间或完成巡检时，用例应调用既有 capture/finalize 规则，在事务边界重查物业、租约、草稿和完备性，不接受 UI 提供的最终哈希或时间。"
  - "R3 当首次打开空库或恢复后重新装配时，系统应按既有模板版本政策装载可用模板与短语；如果启动恢复尚未完成，则系统应保持业务写入口关闭。"
acceptance:
  - "A1 [R1] 装配测试证明默认 capture 与 finalize 共用同一 DB，UI 接口不暴露 DB、任意 SQL 或根路径。"
  - "A2 [R2] 通过真实 SQLite fixture 验证正常保存/完成、跨物业引用、已完成写入拒绝及事务回滚；保留原核心行为测试。"
  - "A3 [R3] assembled APK 的生产 assets 与 data/templates 锁定源文件逐个核对内容 hash，并用生产 AssetManager 加载；空库可开始 Routine v2，历史 v1 可读取，短语可用；资产缺失/错误版本及恢复未完成阻止相应用例，恢复后重新装配。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.application.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug :app:verifyPackagedTemplateAssets
dod_exit: 0
dod_assert: 真实 SQLite 保存/finalize 拒绝与回滚全绿；同一 DB 装配、维护中写入拒绝；assembled APK 内模板/短语 hash 对源且生产 AssetManager 加载、空库 Routine v2 与历史 v1 通过；缺资产不得伪绿
review_gate: codex {verdict:pass}
---

# T1-APP-BOUNDARY-ASSEMBLY

版本：V1。接口职责见 `specs/android-module-boundaries.md`。本卡提供装配与窄用例入口，`T2-CAPTURE-UI` 接入生产导航；backup/erase 卡注册各自启动恢复任务。

复用现有仓储和加载器，不把所有 SQL 查询复制成接口，不拆新增 Gradle 模块，不实现恢复算法。保留 TD10 单连接前提；引入第二连接时另做并发设计。模板和短语唯一来源均为 data/templates/（含 phrases-v1.json）；选择构建期有明确文件白名单的同步，将生产资产输出到生成目录并注册给 Android assets，不手工复制第二份受维护 JSON。当前 core Gradle 仅接 test resources，不能证明 APK 有资产；本卡拥有 app 构建接线及生产 AssetManager/APK 字节验收。后续三模板卡扩展同一白名单。

装配提供一致性快照/维护协调的注册口：T5 IO 与擦除/恢复卡各自接入；它们准备完成前，相应能力返回不可用，不能伪造恢复成功。生产导航接入仍由 Capture UI 拥有。

验证实现：本卡在 app Gradle 提供 verifyPackagedTemplateAssets，解开实际 assembled APK 并将每个模板/短语 entry 的字节/hash 对 data/templates 锁定白名单；缺失、额外未批准模板或错版本均失败。真正 AssetManager 调用走真机证据，JVM 用窄端口验状态，不引入 Robolectric/仪器测试。此 Gradle task 是本卡将实现的验收，不宣称当前已存在。
