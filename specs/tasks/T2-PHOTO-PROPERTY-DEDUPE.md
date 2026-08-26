---
id: T2-PHOTO-PROPERTY-DEDUPE
title: 照片物理去重限定在同一物业（偿还 TD24，隔离物业级媒体生命周期）
depends_on: [T2-PHOTO-PIPELINE, T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
status: todo
branch: T2-PHOTO-PROPERTY-DEDUPE
worktree: C:\wt\T2-PHOTO-PROPERTY-DEDUPE
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/media/
forbid:
  - 绕过 FrozenPaths；改变备份 format_version；删除或移动历史照片
  - 以跨连接场景重新阻断单连接契约（TD10 仲裁仍有效）
non_goals:
  - format v2 按物业导出/恢复（TD12）；孤儿清理调度（TD14）；流式编码（TD15）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 同一物业同 hash 仍复用；A 物业路径作为 B 物业候选时必须写 B 的新路径；交换候选/插入顺序结果不变；只读审计能列出既有跨物业共享 rel_path 而不修改它；删除物业过滤的单点变异使测试翻红；版本评审记录、core 测试与 assembleDebug 全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TD24 状态与 TASK-BOARD 备注（R5）
---

# T2-PHOTO-PROPERTY-DEDUPE

## 产出
把活动资产复用查询与 `PhotoIngest` 候选契约收窄为同一 `property_id`。历史跨物业共享路径只报告，不在本卡迁移或复制；新写入从本卡起不再制造该状态。

## 冻结物版本评审
`Photo.sq` 已冻结。开卡第一步须提交“新增/替换物业过滤查询”的版本评审，获准后临时摘除精确 FrozenPath、改动、合并后重新登记；不得绕过 `guard-frozen`。若查询形态不改表结构，不制造空 `.sqm`；若评审发现必须改表，则暂停并提交完整迁移方案。

## 关键边界
- 物业归属从 photo → inspection → property 的权威关系派生，不信调用方随手传的 owner 字符串。
- 现有历史异常先只读统计并暴露给 T5-MEDIA-ARCHIVE-CONTRACT / T5-LOCAL-MEDIA-RETENTION；自动复制会改变已 finalize 证据路径，故不在本卡做。
- 直接收益是物业 A 的本机清理不会误删物业 B 仍引用的同一路径；format v2 将来若恢复物业级导出，也可复用这一属主边界。format v1 全量备份不以此假装成物业包。

## 验收
见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 · max。难度 M。
