---
id: T5-LOCAL-MEDIA-RETENTION
title: 本机照片空间管理：按物业保留最近 N 次、预览确认、可验证归档与回填
depends_on: [T5-BACKUP-IO, T3-PDF-RENDERER, T3-HISTORY-COMPARE, T5-MEDIA-ARCHIVE-CONTRACT]
status: todo
branch: T5-LOCAL-MEDIA-RETENTION
worktree: C:\wt\T5-LOCAL-MEDIA-RETENTION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/archive/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/
  - android/app/src/main/kotlin/nz/myinspection/app/media/archive/
  - android/app/src/test/kotlin/nz/myinspection/app/media/archive/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/settings/media/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/settings/media/
forbid:
  - 删除 inspection/photo/report/hash 行、PDF、音频或任何云端/SAF 目标文件
  - 未经预览确认自动删除本机照片；把 Google Photos 状态当归档回执
non_goals:
  - 后台无人值守自动清理；云账号/同步/云端删除；重压 finalized 照片；联系人保留策略（T5-RETENTION）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.archive.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 设置仅有 1/3/5/10/ALWAYS 且默认 3；纯 core 计划器按每物业 finalized_at 降序计代、30 天宽限，并永久排除 DRAFT/当前/previous/baseline/手动保留/无 PDF/无 exact verified receipt；预览显示巡检数、照片数、释放字节、备份目的地与风险，逐次确认后才经临时状态安全删除；中断可恢复且不伪报 ARCHIVED；选定 .mibk 回填单资产须复核 hash/size 后原子置 PRESENT；边界/变异测试与 assembleDebug 全绿，真机流程记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: 需求 §11、TD133 与 TASK-BOARD 备注（R5）
---

# T5-LOCAL-MEDIA-RETENTION

## 产出
设置页“本机照片空间”区块、确定性的候选计划器、逐次预览/确认执行器，以及从用户选择的加密 `.mibk` 回填单个已归档资产的路径。

## 产品契约
- “最近 N 次”按**每个物业的已完成巡检代数**计算，不按天数，也不删逻辑记录。选项 `1 / 3 / 5 / 10 / Always`，默认 3。
- 当前巡检、`previous_inspection_id`、`baseline_inspection_id`、手动保留、30 天内项目、PDF 未生成、或没有 exact verified receipt 的资产永不成为候选。
- 一个已验证加密备份允许用户手动归档；UI 同时提示 3-2-1-1 多副本更安全，但 v1 不强制第二份副本。默认没有无人值守自动清理。
- 清理预览必须区分 `已验证` 与 `当前可取回`：本地目录/已连接 USB 可在现场恢复；云 SAF provider 的既有回执仍证明当时内容正确，但离线/授权收回时不宣称可立即恢复。若唯一回执当前不可达，默认主动作改为 `Create another local backup`，用户仍可在明确风险确认后手动归档；不得影响巡检或既有逻辑记录。
- 删除的只是 app 私有目录里的全尺寸照片字节。DB 记录、哈希、PDF、音频和 `.mibk` 不删；Google Photos 不在证明链中。

## 历史与恢复
历史 UI 遇到 ARCHIVED 资产显示占位、哈希和“从备份恢复”，不当作损坏或消失。离线且 provider 不可达时显示 `Backup unavailable offline` + `Choose another backup`，不自动启动网络设置。用户选择包含该 exact tuple 的 `.mibk` 后，流式解密到 internal 临时文件，核对 hash/size，再原子落位并置 PRESENT；失败保持 ARCHIVED 且不影响其它数据。

## 执行安全
候选计算生成不可变计划；确认前重新检查所有保护条件和文件身份，防止预览后状态变化。删除逐文件记账，中途杀进程后可继续或准确报告剩余项。

## 右尺寸说明
六条 allow_paths 是一个安全闭环的 core 计划器、app 执行器、设置/预览薄壳及对应测试。若把删除与回填拆开，首张卡会制造“可释放但不可恢复”的危险中间状态；因此以一张 H 卡交付，并用子包边界限制改动。

## 验收
见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 H。
