# 0003 — 数据契约：SQLDelight · 自研 UUIDv7 · canonical 哈希 · CJK 字体

日期：2026-08-14 · 状态：accepted · 决策方式：3 方评审合成（分歧点已注明票型）

## 背景
「检查项稳定 ID / 模板版本 / finalize 哈希 / 备份包」这组不变量需要一等契约层；执行者是多个模型，契约必须显式、可机检、冻结。

## 决策
1. **DB = SQLDelight 2.x**（Apache-2.0；3 方一致）：`.sq` = schema 真相源、`.sqm` 显式迁移、`verifySqlDelightMigration` 迁移夹具校验；JVM 测试走 `JdbcSqliteDriver`（Xerial，Apache-2.0），生产走 `AndroidSqliteDriver`。UUID 以 canonical 小写 TEXT 存储（AI 写迁移/诊断/备份互换都更稳，Codex 建议采纳）。
2. **UUIDv7 = 自研 ~30 行**（RFC 9562；2-1 票，Codex 荐 `uuid-creator`(MIT)）：SecureRandom + AtomicReference 同毫秒单调 + 时钟回拨冻结语义；测试全单采 Codex 清单（固定向量 / version 位 / variant 位 / 唯一性 / 非降序 / 回拨行为）。**swap 路径**：R3 或后续缺陷判自研不可靠时，换 `UuidCreator.getTimeOrderedEpoch()` 为一行级改动。
3. **finalize 哈希 = SHA-256(canonical JSON)**：RFC 8785 风格——NFC 归一、键排序、epoch 毫秒整数、数组按显式序数再 UUID；覆盖巡检/物业/tenancy 快照、模板 id+版本、条目状态/备注、baseline 引用、照片与音频文件哈希；排除 updated_at/路径/UI 态/PDF 元数据/LLM 建议；**不哈希 PDF 字节**。finalize 事务化：校验完备→物化 manifest→算哈希→置 `finalized_at`；此后一切变更 SQL 恒带 `finalized_at IS NULL`；Supplement append-only 且以 finalize 哈希为根做链。
4. **PDF 双语字形**：打包 `DroidSansFallback.ttf`（Apache-2.0）随 app 分发，composer 量宽用注入的 measurer（JVM 可测）；不依赖 OEM 字体（Codex 捕获的真缺陷：部分 ROM 的 PdfDocument canvas 无中文字形）。
5. 照片规范化：入库即转正（烘焙 EXIF 旋转、清 orientation 标记，8 种方向含镜像全处理）；content_hash = 原始字节 SHA-256 于导入时计算，同哈希去重复用同一物理资产、关联另建。

## 备选方案
Room（训练语料更多但测试推向 Robolectric/instrumented，弃）；raw SQLite（迁移全手工，弃）；哈希覆盖 PDF 字节（渲染非确定性，弃）。

## 后果
schema/canon/备份格式三处合并即入 `FrozenPaths`；改动走版本评审，全下游返工按冻结点纪律计价。
