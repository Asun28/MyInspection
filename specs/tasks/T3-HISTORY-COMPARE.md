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
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.history.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 历史解析测试绿（按 stable_id 对齐取前 N 次状态/备注/照片引用；Exit 默认 baseline、Routine/Annual 默认 previous；模板版本变更按 alignHistory 交集对齐）；UI 语义覆盖绝对日期/来源标签、无历史、无照片、本机字节已归档、隐私照片、基线切换与非手势导航；assembleDebug 绿；真机冒烟覆盖历史条与 overlay/并排降级、Back/恢复、200% 字号，记录附 PR
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

## History source contract

The source is always explicit. Relative time supports an absolute date and never replaces it.

| Inspection mode | Default source | Available source control | Empty result |
| --- | --- | --- | --- |
| `ROUTINE` | Previous finalized Routine | No Baseline toggle; earlier previous inspections may be paged | `No earlier Routine inspection for this item` |
| `ANNUAL` | Previous finalized Annual | No Baseline toggle | `No earlier Annual inspection for this item` |
| `EXIT` | Tenancy Baseline, normally Ingoing | Segmented `Baseline / Previous`; selected source persists for the current capture session | `No Ingoing baseline` remains a prominent warning; Previous never silently substitutes |
| `INGOING` | None | No history controls | `This inspection creates the tenancy baseline` |

Each entry is identified by source and date, for example `Baseline · Ingoing · 12 February 2025` or `Previous · Routine · 19 May 2026`. Template label changes never alter matching; the UI may show `Current template has no earlier record` for a new stable ID but never guesses by name.

## In-card history strip

History is evidence inside the owning item card, not a second global report viewer.

| Region | Required content | Behaviour |
| --- | --- | --- |
| Collapsed summary | Source label, absolute date, prior status, photo count | One `Review earlier evidence` action expands the strip; no thumbnail-only affordance |
| History entry | Source/date, status icon+label, note summary, 4:3 thumbnail or exact media state | Entries are newest-first within the chosen source; show 3 initially and at most 5 in this card |
| Navigation | Visible Previous/Next 48dp controls plus horizontal swipe | Swipe is optional; controls update page position and TalkBack announces `History {n} of {total}` |
| Note | Two-line visual preview and `Read full note` when longer | TalkBack description includes the full note without making ellipsized text the only access |
| Privacy photo | Violet shield and `Contains tenant belongings` | It remains available for private in-app comparison but stays excluded from reports by default |
| Archived local bytes | Metadata tile `Photo archived — not on this device` | Overlay is unavailable. Show `Restore from backup` only when the owning restore capability is registered; never a dead action |

The strip does not auto-play, auto-scroll, or change the current item status. Opening and closing it preserves item position and returns focus to its trigger. At 200% font scale entries become a vertical pager; dates, status, and navigation labels do not truncate.

## Overlay control contract

| State | Visual/control result | Rule |
| --- | --- | --- |
| `UNAVAILABLE_NO_HISTORY` | `No earlier photo for this view`; overlay switch absent | Camera remains fully usable |
| `UNAVAILABLE_ARCHIVED` | `Earlier photo is archived`; optional Restore action outside capture | No hidden network/provider launch from the shutter screen |
| `OFF` | Live preview only; switch reads `Historical overlay off` | Item photos default here |
| `ON` | Historical photo aligned to the same ViewPort at 30% | Room panoramas default here when bytes are local |
| `ADJUSTING` | Labelled opacity slider, 10–70%, 5% steps; current percentage announced | Slider changes preview only and preserves the last value for this camera session |
| `ERROR_DECODE` | Overlay turns off; persistent `Couldn’t load earlier photo` with Continue without overlay | Camera preview and shutter remain available |

The historical bitmap uses the same rotation-correct crop, ViewPort, and aspect transform as the live preview. Letterboxing or a mismatched crop is a failure, not an acceptable approximation. Overlay pixels never enter the capture pipeline, temporary photo, thumbnail, or exported bytes.

Overlay switch, opacity, source, and current historical date stay visible together when the control is expanded. Back returns to the item without changing its evidence. Process recreation restores the selected source and opacity only when the same inspection/target is still active; otherwise defaults are reapplied.

## Accessibility and feedback

- The collapsed summary is one node; each expanded entry is one group followed by its separate Previous/Next controls.
- A photo description names room/item, source inspection type, absolute date, status, and privacy/archived state.
- Status is never inferred from thumbnail colour. Baseline and Previous use text, not two nearly identical icons.
- Reduced motion removes pager translation and crossfades at 100ms; selection, date, and position remain explicit.
- History loading reserves its final space. Local decode delay over 300ms shows `Loading earlier evidence`; failure never collapses the current item or shifts focus.

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 H。
