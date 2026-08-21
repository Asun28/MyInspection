---
id: T2-PHOTO-DIRECTORY-DURABILITY
title: 照片 sidecar 的目录级 crash durability 收口
depends_on: [T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER]
status: merged
branch: T2-PHOTO-DIRECTORY-DURABILITY
worktree: C:\wt\T2-PHOTO-DIRECTORY-DURABILITY
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
forbid:
  - 改 schema、依赖、照片路径/哈希/finalized 语义或 scripts/selftest.ps1
  - 扩张为 native openat/unlinkat 或防御不遵守 app lease 的外部恶意进程
non_goals:
  - 重做 TD14 worker、调度、DB 查询或 marker 协议
  - 修复 cross-ID rel_path alias
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 首次创建照片目录层级时，marker publish 前按依赖顺序 fsync 所有新目录项；补偿/worker 删除 JPEG 后先 fsync 其父目录，成功后才清 marker；任一 sync 失败保留 marker 并保持既有主异常/重试语义；两条顺序与失败路径有确定性行为测试
review_gate: codex {verdict:pass}
hygiene: 仅保留能分别杀掉祖先目录 sync、JPEG-delete sync、sync-failure marker-retain 三项守卫的聚焦测试
doc_sync: TD137 状态与 TASK-BOARD 备注（R5）
---

# T2-PHOTO-DIRECTORY-DURABILITY

## 来源与边界

PR #32 的最终 R3 指出两项独立于功能正确性的掉电窗口：marker 只同步最深父目录，无法证明首次创建的祖先目录项已落盘；JPEG 删除后若未先同步同目录就移除 marker，掉电可能恢复出“JPEG 仍在、marker 已没”。本卡只补目录级 durability 顺序，不重开 TD14 的 worker/lease 设计。

## 验收

- 行为测试记录 marker force 后从最深目录到既有 durability root 的完整同步顺序。
- ingest 已确认补偿与 worker 删除 JPEG 都必须在 marker 清除前同步 JPEG 父目录。
- 同步失败时 marker 留存；已完成 DB 结果不被伪装成失败，未完成清理按既有 retry/failure 分类。
