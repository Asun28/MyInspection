---
id: T2-PHOTO-PIPELINE
title: 照片管线：存储布局 + EXIF 转正（8 向）+ 内容哈希去重 + 导入
depends_on: [T1-SCHEMA-CORE]
parallelizable_with: [T2-ROUTINE-CONTENT, T2-CAPTURE-CORE, T2-PHRASELIB]
status: todo
branch: T2-PHOTO-PIPELINE
worktree: C:\wt\T2-PHOTO-PIPELINE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
forbid:
  - DB 存 BLOB（硬边界）；移动/改写用户原始文件（导入=复制）
non_goals:
  - 相机 UI（T2-CAPTURE-UI）；ghost overlay（T3-HISTORY-COMPARE）
  - 批量导入分配界面（v1 单项导入为主，批量列 v1.1）
  - 跨 FS+DB 的**共享临界区**式真原子性（要动已冻结的 sqldelight/ + app/ 调度接线；用户 2026-08-17 裁定 → TD14）
  - 编码字节上界的**形式证明**（要把 JPEG 改成边写盘边摘要、重构两条管线字节流向；用户 2026-08-17 裁定 → TD15）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"
dod_exit: 0
dod_assert: 路径派生纯函数测试绿（photos/{propertyId}/{inspectionId}/{photoId}.jpg，禁手拼路径）；SHA-256 与去重逻辑测试绿（同哈希复用资产、只建关联）；EXIF 8 方向（含镜像）转正矩阵测试绿（JVM 侧用矩阵数学断言，位图操作薄壳放 :app）
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T2-PHOTO-PIPELINE

## 产出
`:core/media`（路径派生、哈希、去重决策、EXIF 方向→矩阵纯逻辑）+ `:app/media`（Bitmap 解码/转正烘焙/JPEG 重编码、ExifInterface 读取、文件落盘）。

## 上下文包（执行模型必读）
- **存储布局（唯一派生点）**：app 私有外部存储根下 `photos/{propertyId}/{inspectionId}/{photoId}.jpg`、`audio/...` 同构；rel_path 入库、根路径运行时注入——**全仓禁手拼路径**（CLAUDE.md 关键不变量）。
- **拍摄保存**：CameraX 出片 → 立即转正烘焙（读 EXIF orientation → Matrix 旋转/镜像 → JPEG quality 92 重编码 → 清 orientation 标记）→ SHA-256（**烘焙后字节**）→ 落盘 + 入库（source=CAMERA）。此后 overlay/PDF 永不再考虑旋转（ADR-0003）。
- **导入**：SAF 选文件 → 复制原字节到临时私有文件、同时算**原始字节** SHA-256 → 若哈希已存在：不再复制，复用既有 photo 资产、只建新关联（Codex：一份物理资产多处关联分离）→ 否则转正烘焙存正式位。ExifInterface 读拍摄时间存 exif_time_ms（与巡检时间分开，需求 §5）；source=IMPORTED。**8 种 EXIF 方向含 4 种镜像全处理**（androidx.exifinterface）。
- 缩略图：交给消费端按需降采样（inSampleSize），不预生成派生文件（YAGNI；PDF/UI 各自决定目标尺寸）。
- 孤儿清理（[adopt]，调研要点 5）：照片资产被去关联（软删/去重复用后关联删除）后成孤儿——提供 `orphanedAssets()` 查询 + 清理用例（只删无任何关联且非 FINALIZED 巡检证据的文件）；finalize 过的巡检照片永不清。
- :core/:app 切分：一切可判定逻辑（路径、去重决策、orientation→Matrix 参数表、哈希）留 :core 纯 JVM 测；:app 只做 Bitmap/文件 IO 薄壳。

## 验收 / 执行建议
dod 见 front-matter；:app 薄壳另以 `:app:assembleDebug` 编译绿佐证（评审核）。
首选 Sonnet 5 · max（android 位图/EXIF 细节多）；备选 DeepSeek V4 Pro。难度 M。

## 用户裁决 2026-08-17（R3 触轮次上限后转人裁 · 选项①：合并 + 两条登记为 TD）
R3 在第 9 轮触到 `ReviewRoundCap`（2/2），按 `docs/QUALITY-RUBRIC.md` §5 转人裁。用户裁定**选项①**：
本卡合并，剩余两条评审意见登记为技术债、由后续卡偿还。

**裁决依据（非「差不多了」，是 rubric §0 的口径）**：这两条的修法**都落在本卡 `allow_paths` 之外或已冻结面上**——
① 共享临界区要动**已冻结**的 `sqldelight/`（且 `OrphanedAssetCleanup` 的 WorkManager 接线在 `app/` 调度侧、
不在本卡三条 allow_paths 内）；② 编码字节上界的证明要把 JPEG 改成边写盘边摘要，是跨 `core/media`+`app/media`
两侧的字节流向重构。按 §0「不得给卡加范围」，**它们是 `[FOLLOW-UP]`、不构成本卡的 block 理由**，
已各自记为 **TD14 / TD15** 并同时写进上方 `non_goals`。

**已在本卡内封死的部分不受此裁决影响**（即：不是把问题整个推走）：两条具体的丢数据路径已各自封死——
补偿绝不删仍被活跃行引用的路径（同 photoId 重试时赢家那行正引用它，判据是已冻结的
`selectActiveAssetsByContentHash`），复用路径本次不写字节故永不补偿；编码预算余量已从 2 提到 4 B/px
覆盖 `ByteArrayOutputStream` 底层数组 + `toByteArray()` 复制，注释亦已如实改口为「有依据的余量、
**不是**可证明上界」。TD14/TD15 记的是**剩下的那部分**，不是全部。

**合并时的证据水位**：`:core:check` + `:app:assembleDebug` 全绿 · media 套件全绿 · **24/24 变异逐一击杀**
（判据分类器 = 非零**且**命中指定测试名才算 KILLED，每枚跑完核 SHA 回基线，L165/L196）·
范围闸 27 文件全在 allow_paths 内 · 许可闸 PASS（Gradle 覆盖缺口 = 既有 TD2）· 防泄露闸 PASS。
