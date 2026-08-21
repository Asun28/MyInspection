---
id: T2-PHOTO-PROPERTY-DEDUPE
title: 照片物理去重限定在同一物业（偿还 TD24，保证按物业备份闭包）
depends_on: [T2-PHOTO-PIPELINE, T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
status: todo
branch: T2-PHOTO-PROPERTY-DEDUPE
worktree: C:\wt\T2-PHOTO-PROPERTY-DEDUPE
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/db/DbDownstreamQueriesTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/media/
forbid:
  - 绕过 FrozenPaths；改变备份 format_version；删除或移动历史照片
  - 以跨连接场景重新阻断单连接契约（TD10 仲裁仍有效）
non_goals:
  - 按物业恢复（TD12）；孤儿清理调度（TD14）；流式编码（TD15）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*" --tests "nz.myinspection.core.db.DbDownstreamQueriesTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 同一物业同 hash 仍复用；A 物业路径作为 B 物业候选时必须写 B 的新路径；交换候选/插入顺序结果不变；只读审计能列出既有跨物业共享 rel_path 而不修改它；删除物业过滤的单点变异使测试翻红；版本评审记录、DbDownstreamQueriesTest、core media 测试与 assembleDebug 全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TD24 状态与 TASK-BOARD 备注（R5）
---

# T2-PHOTO-PROPERTY-DEDUPE

## 产出
把活动资产复用查询与 `PhotoIngest` 候选契约收窄为同一 `property_id`。历史跨物业共享路径只报告，不在本卡迁移或复制；新写入从本卡起不再制造该状态。

## 冻结物版本评审
`Photo.sq` 已冻结。本卡就是这次查询级版本评审，批准面严格限定为：

1. 把 `selectActiveAssetsByContentHash(content_hash)` 替换成接收 `property_id + content_hash` 的同物业查询；通过 `photo → room_instance → inspection` 的既有逻辑关联取物业，不改表、列、索引或既有行。
2. 新增只读审计查询，按 `rel_path` 列出仍被多个物业活跃引用的历史状态；它只报告，不迁移、复制或删除文件。
3. 同步现有 SQLDelight 下游查询测试及 media/app 调用契约。`DbDownstreamQueriesTest.kt` 已有旧查询的编译期调用，因此必须明确纳入范围；遗漏它会迫使实施越界或保留不安全旧 API。

获准后才可临时摘除 `android/core/src/main/sqldelight/` 这一条精确 FrozenPath，实施并在合并前原样重新登记；不得停用或绕过 `guard-frozen`。本次无 DDL 变化，不制造空 `.sqm`；若实现中发现必须改表或索引，立即暂停并另提完整迁移方案。

## 关键边界
- 物业归属从 photo → inspection → property 的权威关系派生，不信调用方随手传的 owner 字符串。
- 现有历史异常先只读统计并暴露给 T5-BACKUP-IO；自动复制会改变已 finalize 证据路径，故不在本卡做。
- 备份格式仍使用既有单值 `ownerPropertyId`，本卡通过禁止未来跨物业共享使其语义成立。

## 验收
见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 · max。难度 M。
