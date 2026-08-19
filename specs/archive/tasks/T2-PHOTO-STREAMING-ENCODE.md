---
id: T2-PHOTO-STREAMING-ENCODE
title: 照片流式编码：去掉整份 JPEG ByteArray 内存峰值（偿还 TD15）
depends_on: [T2-PHOTO-PIPELINE]
status: merged
branch: T2-PHOTO-STREAMING-ENCODE
worktree: C:\wt\T2-PHOTO-STREAMING-ENCODE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/media/
forbid:
  - 为编码重新创建等尺寸 JPEG ByteArray 或调用 ByteArrayOutputStream.toByteArray()
  - 改变照片路径、内容哈希或 finalized 只读语义
non_goals:
  - 四档质量 UI/参数（T2-PHOTO-QUALITY-PROFILES）；跨物业去重（T2-PHOTO-PROPERTY-DEDUPE）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 相机与导入两条管线都以同一次流写完成 JPEG 落盘和 SHA-256，临时文件仅在关闭并校验后原子就位；编码/写入/摘要任一步失败不产生 DB 行且清理临时文件；高熵 4096px 夹具证明不再分配整份 JPEG ByteArray，既有媒体 JVM 测试与 assembleDebug 全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TD15 状态与 TASK-BOARD 备注（R5）
---

# T2-PHOTO-STREAMING-ENCODE

## 产出
把 `Bitmap.compress()` 直接写入“摘要 + 文件”的有界流，替代 `PhotoJpegEncoder.encode(): ByteArray`。临时文件关闭后核对摘要/大小，再沿既有落盘→入库补偿契约原子就位。

## 上下文包
- 当前实现以质量 92 压到 `ByteArrayOutputStream`，随后 `toByteArray()` 再复制一份；Extra High 档会放大 TD15 的 OOM 风险。
- 哈希必须覆盖最终落盘的确切 JPEG 字节；不得先编码一次算哈希、再编码一次写盘。
- 输出流异常、磁盘满、编码返回 false、哈希结束失败都走同一失败清理路径；不吞掉原始异常。
- 这是 TD15 的专属偿还卡，不顺手改变质量、尺寸或去重语义。

## 验收
见 front-matter。首选 Sonnet 5 · max；备选 GPT-5.6 Terra · high。难度 M。

## 合并记录

PR #29 以 master `c8f3b63` squash 合并；最终功能提交为 `a952763`。相机与导入均通过同一
`VerifiedAssetWorkflow` 把 JPEG 单次编码到摘要化临时文件，关闭后逐块复读核验 size/SHA，再按既有
no-overwrite publish → DB 补偿契约落定。相机 `content_hash` 仍取最终 JPEG，导入仍取原始 source bytes。
4096²/16 MiB 高熵真实文件夹具、digest-finalization/verification-read 故障、发布/记录顺序与清理变异均通过；
卡片 DoD、verify、范围、许可、secrets 全绿。R3 首轮要求把源码字符串证据升级为生产共用的可执行 seam，
补强后第二轮 `pass`。TD15 已偿还。
