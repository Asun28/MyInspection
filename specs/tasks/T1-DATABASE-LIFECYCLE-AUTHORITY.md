---
id: T1-DATABASE-LIFECYCLE-AUTHORITY
title: 数据库生命周期写权限：活跃/历史读取分流 + 基线与清理终态守卫
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
plan_ref: docs/DATABASE-DESIGN.md#5-active-versus-historical-reads
status: todo
branch: T1-DATABASE-LIFECYCLE-AUTHORITY
worktree: C:\wt\T1-DATABASE-LIFECYCLE-AUTHORITY
allow_paths:
  - android/core/src/main/sqldelight/nz/myinspection/core/db/
  - android/core/src/main/kotlin/nz/myinspection/core/capture/
  - android/core/src/main/kotlin/nz/myinspection/core/retention/
  - android/core/src/test/kotlin/nz/myinspection/core/capture/
  - android/core/src/test/kotlin/nz/myinspection/core/retention/
  - android/core/src/test/kotlin/nz/myinspection/core/db/
forbid:
  - 未经版本评审改哈希域、finalized 证据形状或已有备份格式
  - 新增账号、角色、ACL、admin 绕过或物理级联删除
  - 顺手修改本卡未点名的表族；迁移缺失或跳过 verifyMigrations
  - 禁止未经授权的运行期出站网络、账号/RBAC、遥测；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - 操作日志表与诊断导出
  - 多连接并发契约（TD10）或重复房间运行时（TD26）
diagnosis:
  root_cause: 生成查询把 active 与 historical 两种语义折叠为 selectById，且 tenancy baseline 的机械 setter 暴露得比领域权限更宽；UI 过滤无法构成写权限边界。
  same_class: 本卡只闭合 db/capture/retention 允许路径内的 parent 点查、软删后写入、终态字段回填与 baseline 赋值；现有 finalize/InspectionSnapshotAssembler 与 template/TemplateStore 是只读 historical 消费者，登记为不改清单，任何需改动的其他 caller 必须另开有对应实现/测试路径的卡。
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.db.*" --tests "nz.myinspection.core.capture.*" --tests "nz.myinspection.core.retention.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
dod_exit: 0
dod_assert: 新建巡检/物业抑制只接受活跃 property/tenancy/template；historical 历史报告与已软删 tenancy 联系方式清理仍可读取 any-lifecycle 行；purged_at 后联系方式不可回填；初始 INGOING 与 finalized fallback baseline 各有独立守卫，跨物业/跨租约/不合格类型均拒绝；deleted override 不可更新；所有影响输出/哈希/进度的新增批量查询以主排序键相同且反向插入的夹具证明 ID 全序完全一致，删除 ORDER BY 或唯一 tie-breaker 即 RED；对应单句删除变异逐一使测试翻红；迁移验证绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD160 置 paid；docs/DATABASE-DESIGN.md 与 TASK-BOARD 记录证据（R5）
version_review: this card = the version review
---

# T1-DATABASE-LIFECYCLE-AUTHORITY

## 产出

把“字段权限”落实成窄写入口，而不是账号/RBAC：

1. 为 property、tenancy、template 增加语义明确的 `selectActiveById`；历史/保留期路径使用明确的 any-lifecycle 读取。
2. 本卡允许路径内所有开始新工作的入口改用 active parent；报告、链复验和隐私到期清理通过 any-lifecycle 查询继续读软删历史。`core/finalize/InspectionSnapshotAssembler.kt` 与 `core/template/TemplateStore.kt` 只列入 historical caller 清单，不在本卡修改；扫描发现的其他越界 caller 必须先登记独立卡，不能偷带进本卡。
3. 用两个具名 baseline 操作替代通用 setter：仅在空指针时立当前 INGOING；或把同 property/tenancy 的合格 finalized 检查指定为存量租约基线。
4. 数据库约束钉死 `purged_at != NULL => tenant_name/contact 均为 NULL`，并阻止 deleted override 更新。
5. 为受输出、哈希、进度影响的新增批量查询提供显式全序；不依赖索引偶然返回序。

## 版本评审

本卡不得与 `T2-ROOM-REPEATABLE` 抢同一迁移版本：后者必须先合并。本卡启动时读取其已登记、已追踪且经精确 allowlist 放行的当前 schema snapshot（预期为 `databases/2.db`）作为迁移输入，只按届时 schema version 提交下一号 `.sqm`，不新建或改写 snapshot/allowlist。若实际版本或该输入不成立，立即停止并先登记独立的 snapshot/allowlist 扩展卡；不得扩大本卡路径。TD6/TD7/TD8 也不进入本卡功能 diff。

## 验收

见 front-matter。测试必须证明：删除 active predicate、终态 CHECK、same-property/tenancy 检查或 deleted-row guard 中任一句，均有对应红灯；不得仅验证异常类型或影响行数而不读回最终状态。
