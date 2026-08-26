---
id: T3-HISTORY-COMPARE
title: 历史对比：采集页历史条（前次状态/备注/缩略图/左右滑）+ ghost overlay 集成 + 双轨基线
depends_on: [T2-CAPTURE-UI, T1-SPIKE-PLATFORM, T2-REPEATABLE-ROOM-RUNTIME]
parallelizable_with: [T3-PDF-RENDERER, T5-BACKUP-IO]
status: todo
branch: T3-HISTORY-COMPARE
worktree: C:\wt\T3-HISTORY-COMPARE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/history/
  - android/core/src/test/kotlin/nz/myinspection/core/history/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/capture/history/
  - android/app/src/main/kotlin/nz/myinspection/app/media/camera/
forbid:
  - 差异对比 PDF / 只读历史报告查看器 / 照片自动比对（需求 §6 明确不做）
non_goals:
  - 冷启动前 1–2 次巡检历史为空——接受，不造假数据填充（需求 §6）
plan_ref: context/DESIGN.md#history-evidence-and-media-component-matrix
acceptance:
  - "A1 previous and baseline history distinguish empty and archived evidence"
  - "A2 navigation uses visible controls for every history step"
  - "A3 comparison is preview-only and restores focus return"
  - "A4 history supports offline read access"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.history.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 历史解析测试绿（按 stable_id 对齐取前 N 次状态/备注/照片引用；Exit 模式默认 baseline=tenancy Ingoing、Routine 默认 previous；模板版本变更后按 alignHistory 交集对齐）；assembleDebug 绿；真机冒烟：走查某项见上次状态与缩略图、左右滑看更早、拍照页 overlay 半透明历史照可对位（或按 spike 结论走并排比对降级），记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T3-HISTORY-COMPARE

## 产出
`core/history`（历史序列解析，按 stable_id 对齐 + 双轨基线选择）+ 采集页历史条 UI + 拍照页 overlay（按 spike 结论实装或降级）。

## 上下文包（执行模型必读）
- **范围收窄立场（需求 §6 [定]）**：只做「拍的时候能看到前几次」。历史条出现在项目卡片内：上次 status 徽标 + note 摘要 + 缩略图，横滑看更早（倒序取 3–5 次足够）。
- 双轨（T2-CAPTURE-CORE 已解析引用）：`historyFor(stableId, mode)`——EXIT 默认 baseline（tenancy 的 Ingoing）、可切 previous；ROUTINE/ANNUAL 默认 previous。模板版本差异用 T1-TEMPLATE-ENGINE 的 alignHistory：交集项正常、移除项标「旧模板项」、新增项无历史（空态）。
- **Overlay（spike ① 结论驱动）**：成立 → 拍照页可选「叠层」开关：上次同项照片（room 级用全景）alpha ~0.3 叠 PreviewView，沿 spike 记录的 UseCaseGroup/ViewPort 参数；照片已转正（T2-PHOTO-PIPELINE 烘焙），不再旋转。降级 → 拍照页分屏并排（上历史下取景）。**读 docs/spike/PLATFORM-SPIKE.md 第①节定走哪条，卡不重开决策**。
- ROOM_PANORAMA 是 overlay 主战场（需求 §5：机位一致性在全景上价值最大）——房间全景拍摄默认开叠层，项目级默认关（可开）。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 H。
