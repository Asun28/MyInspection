---
paths: android/**/*.{kt,kts}
---

# Kotlin / Android 细则（ADR-0001 · 只在 Read 到 android/ 源码时注入）

- **模块红线**：`:core` 禁止任何 `android.*`/`androidx.*` import（纯 JVM，可测性即生命线）；平台适配只进 `:app`。依赖方向 app → core，无环。
- **包级围栏** = 卡的 allow_paths 粒度：`core/.../{model,db,template,compliance,report,backup,canon}`、`app/.../{media,export,feature}`——跨包改动须卡内声明。
- 命名走 Kotlin 官方风格（类 PascalCase / 函数与属性 camelCase / 常量 UPPER_SNAKE）；DB 层标识符按 `.sq` 文件 snake_case，映射由 SQLDelight 生成，别手写桥接。
- **冻结物**：`android/core/src/main/sqldelight/**`、`core/canon/`、`core/backup/format/`（合并后登记 FrozenPaths，guard-frozen 拒改；演进走版本评审）。
- 时间一律 UTC epoch 毫秒入库；展示层才转 Pacific/Auckland。金额/坐标不涉本项目。
- 写第三方 API 调用前按 `android/gradle/libs.versions.toml` pinned 版本核文档（task-loop R2 纪律）；Compose/CameraX/SQLDelight 版本升级 = 独立卡，不顺手升。
- finalize 后数据只读：任何 UPDATE/DELETE 语句恒带 `finalized_at IS NULL` 谓词（评审 #按 ADR-0003 查）。
