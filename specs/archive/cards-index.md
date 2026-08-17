# 已归档任务卡索引（merged cards · cold storage）

> 一行一条已 `merged` 的卡，共 19 张；完整卡在 `specs/archive/tasks/<id>.md`。
> 由 `scripts/archive.ps1` 从 `specs/archive/tasks/` 投影生成，勿手工编辑。

| id | 状态 | 标题 |
|---|---|---|
| T0-DEBT-CASE-PROBE-CLOSURE-SCOPE | merged | Make 17cc case mutation probes host-independent (repay TD25) |
| T0-DEBT-REFERENCE-INTEGRITY | merged | Authority TD reference integrity (repay TD16) |
| T0-DEBT-SEEDED-CLOSURE-SCOPE | merged | Make seeded mutation closures self-contained (repay TD23) |
| T0-DEBT-TASK-INVENTORY | merged | Remove hand-maintained task-card inventory (repay TD21) |
| T0-GATE-FIXFORWARD | merged | 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径（T0-GATE-HARDENING 事后 R3 两条 block 的 fix-forward） |
| T0-GATE-HARDENING | merged | 许可闸看得见 Gradle + verify 确定性 + 两枚闸门自测（从 T0-TOOLCHAIN 拆出） |
| T0-HARNESS-PERF | merged | 横切优化 selftest 与 CI 墙钟时间（约 300 行 harness/测试改动） |
| T0-TOOLCHAIN | merged | 本机 Android 工具链 + android/ Gradle 双模块骨架空编译绿 + verify/CI 收紧 |
| T1-CANON-HASH | merged | canonical JSON 序列化 + SHA-256 + 黄金向量（★冻结点） |
| T1-SCHEMA-CORE | merged | SQLDelight 全量 schema + UUIDv7 + 基线迁移 + JVM 测试（★冻结点） |
| T1-SKELETON-E2E | merged | 一次性走通骨架：建巡检 → 加一项 → 拍一张 → 导出一份 PDF（真机可见，用完即弃） |
| T1-TEMPLATE-ENGINE | merged | 模板 JSON schema + 加载器 + stable-id/版本对齐 + 按类型枚举校验（★冻结点） |
| T2-CAPTURE-CORE | merged | 采集领域核：巡检生命周期状态机 + 房间粒度草稿自动保存仓储（:core） |
| T2-PHOTO-PIPELINE | merged | 照片管线：存储布局 + EXIF 转正（8 向）+ 内容哈希去重 + 导入 |
| T2-PHRASELIB | merged | 双语短语库种子内容 + 查询接口 |
| T2-ROUTINE-CONTENT | merged | Routine 双语模板内容（80–120 项）+ schema 校验绿 |
| T3-FINALIZE | merged | finalize 事务：完备性校验 → canonical 哈希落库 → 只读强制 + Supplement 哈希链 |
| T5-BACKUP-FORMAT | merged | 加密备份归档格式：流式 ZIP+AES-GCM + manifest + 防篡改/错口令测试（★冻结点） |
| T5-RETENTION | merged | 租客数据保留期 + 一键清理（Privacy Act 2020） |
