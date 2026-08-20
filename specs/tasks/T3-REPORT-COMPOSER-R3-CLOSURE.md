---
id: T3-REPORT-COMPOSER-R3-CLOSURE
title: 报告布局 R3 收口：40mm 内联缩略图、不可拆图槽、可读时间与引用完整性
depends_on: [T3-REPORT-COMPOSER]
parallelizable_with: []
status: todo
branch: T3-REPORT-COMPOSER-R3-CLOSURE
worktree: C:\wt\T3-REPORT-COMPOSER-R3-CLOSURE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/
  - android/core/src/test/kotlin/nz/myinspection/core/report/
forbid:
  - android import、PDF 字节渲染、数据库/schema 改动
  - 扩回已由 T3-REPORT-COMPOSER 解决的状态域、房客内部判断、长文本分页问题
non_goals:
  - PDF renderer；报告输入的 DB 组装；UI
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: 内联项目照片在项目表行内有约 40mm 的固定 x/y/width/height；长 caption 不拆 ImageSlot；实际页脚只绘短哈希；空房标题与首张全景同页；引用/层级输入 fail-closed；封面数字与照片时间使用确定性可读文本
review_gate: codex {verdict:pass}
hygiene: 只修下列六个 R3 round-cap finding；每个守卫配行为测试/单句变异，不再重审原卡已闭合事项
doc_sync: R5 时同步 T3-REPORT-COMPOSER 的人裁结果、PR 与 TD139 状态
---

# T3-REPORT-COMPOSER-R3-CLOSURE

## 起因

`T3-REPORT-COMPOSER` PR #39 已完成 DoD、verify、范围、许可、防泄露，并在第 1 轮集中修复七项真实问题；第 2 轮仍被 R3 以六项新布局细节 block。仓库 `ReviewRoundCap = 2`，因此停止继续追评，由人裁决定原 PR 是否合并；本卡承接余项，避免把同一 PR 继续滚大。

## 唯一范围

1. 项目照片的 `INLINE` 图槽必须进入项目表行的约 40mm 列，计划中直接给出 renderer-ready 的 x/y/width/height；附录图仍是独立大图。
2. `ImageSlotBlock` 永远不可拆。caption 超长时采用确定性有界/截断表示，并保留完整 `reference/source/capturedAt` 结构字段；长 caption 边界不得制造两个同 photoId 图槽。
3. 页脚实际 `TextRun` 只绘 `dataHash.take(12)`，完整 64 位哈希仍保留在 `FooterBlock.dataHash` 元数据供验证。
4. 无项目房间的标题须与首张可见全景成组；无项目且无照片的空房输入 fail-closed，不产生孤标题。
5. 校验 room/item id 非空、photo reference 唯一；room 级照片必须 `isRoomLevel=true`，item 级必须为 false。
6. 封面实际绘制带标签的不利/待处理数量；巡检时间与照片时间用固定 UTC、locale-independent 的 ISO-8601 文本，不直接绘 epoch。

## 验收证据

- 黄金树断言内联缩略图与表格列的精确几何，以及页脚实际绘制短哈希。
- 对抗长 reference/caption，INLINE/APPENDIX 每种用途各只有一个不可拆图槽且不溢出。
- 构造临界剩余高度的空房，标题与首图同页；空房无图明确拒绝。
- blank/duplicate/wrong-level 五类投影输入逐类拒绝。
- 固定 epoch 的封面/照片 caption 断言 exact ISO-8601；封面 exact totals 可见。
- 删除上述各判据一句，专属测试变红；不运行全 selftest。

## 被否决方案

- 不把这些细节塞进 `T3-PDF-RENDERER`：布局决策属于 composer，renderer 只绘 plan。
- 不继续第 3 轮 R3：已触发轮次封顶，余项由本卡和人裁显式承接。
