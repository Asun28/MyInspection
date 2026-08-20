---
id: T2-PHOTO-QUALITY-PROFILES
title: 新照片四档质量：Low / Medium / High / Extra High（默认 Medium）
depends_on: [T2-PHOTO-STREAMING-ENCODE]
status: merged
branch: T2-PHOTO-QUALITY-PROFILES
worktree: C:\wt\T2-PHOTO-QUALITY-PROFILES
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/media/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/settings/media/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/settings/media/
forbid:
  - 重压、替换或删除已存照片；修改 finalized 记录/哈希
  - 把 PDF 质量与照片存储质量合并成一个设置
  - 改变相机 content_hash=最终 JPEG、导入 content_hash=原始 source bytes 的既有双哈希语义
non_goals:
  - PDF 导出档位（T3-PDF-RENDERER）；云端上传；按房间/检查项单独覆盖质量
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: LOW=1280px/q75、MEDIUM=1920/q82、HIGH=2560/q88、EXTRA_HIGH=4096/q92 的初始契约集中在一个纯 core 定义且默认 MEDIUM；新拍与新导入共同消费该设置、EXIF 转正后再缩放、绝不放大小图；切换只影响以后写入；相机 DB 哈希仍取最终缩放 JPEG，导入 DB 哈希仍取原始 source bytes，导入的 staged digest 只校验最终 JPEG；固定房间全景/铭牌小字/低光/高熵夹具证明尺寸上限与总体大小单调，High/Extra High 可读性人工记录附 PR；core 测试与 assembleDebug 全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: 需求 §4/§5、TD131 与 TASK-BOARD 备注（R5）
---

# T2-PHOTO-QUALITY-PROFILES

## 产出
一个持久化的“新照片质量”设置和两条 ingest 管线共享的尺寸/编码契约。初始参数来自行业常见长边档位，最终是否调整必须用同一真机夹具给出证据，不能凭肉眼随意改数。

## 上下文包
- 当前 app 不保留相机 RAW/DNG；所谓原图是 EXIF 转正后以 q92 保存的 JPEG。
- 质量值是编码器提示，不等于可预测的文件大小；验收以像素帽、可读性和固定夹具总体大小为准。
- 设置只在开始一次捕获/导入时读取并冻结到该操作，处理中途变更不能造成同一批次参数漂移。
- 缩放须保持比例、不得上采样。相机的 DB `content_hash` 覆盖缩放/编码后的最终 JPEG；导入路径继续以
  原始 source bytes 作为 DB `content_hash`（TD15 已冻结的去重语义），另以 staged digest 校验最终 JPEG，
  不得借质量档切换偷偷改写导入去重身份。

## 右尺寸说明
六条 allow_paths 是一个不可分割的纵切：纯 core 参数、两条既有 app ingest 消费点、一个设置薄壳及各自测试。拆成“参数/设置/接线”三卡会留下不可用的半功能并重复改同一媒体路径；本卡仍只有一个用户可见产出。

## 验收
见 front-matter。首选 Sonnet 5 · max；备选 GPT-5.6 Terra · high。难度 M。

## 合并记录

PR #30 以 master `703af59` squash 合并；最终功能提交为 `716034c`。四档契约集中在纯 core：Low
1280/q75、Medium 1920/q82（默认）、High 2560/q88、Extra High 4096/q92。相机与导入各在操作入口只读取
一次持久设置，EXIF 转正后按比例缩小且不放大；相机 DB 哈希仍取最终 JPEG，导入 DB 哈希仍取原始 source
bytes。峰值预算按 source + 按需 EXIF bake + 按需 scale 的真实位图集合饱和计算。

卡片 DoD、verify、范围、许可、secrets 全绿。R3 首轮要求补真实 Android 编码证据；同一 Android SDK 35
设备上的四个固定夹具经生产 `PhotoBitmapScaler`、`PhotoJpegEncoder`、`VerifiedAssetWorkflow` 与真实 stager
跑完 16 个输出，总体字节严格 Low < Medium < High < Extra High，High/Extra High 铭牌字形人工可读，第二轮
R3 `pass`。TD131 已偿还。
