---
id: T2-CAPTURE-UI
title: Field Ledger Compose 走查：房间导航 + 状态/证据 + 备注/拍照
depends_on: [T2-CAPTURE-CORE, T2-PHOTO-PIPELINE, T1-SPIKE-PLATFORM, T1-SHARE-SCREEN-PRIVACY, T2-FIELD-LEDGER-THEME, T2-REPEATABLE-ROOM-RUNTIME]
parallelizable_with: [T3-REPORT-COMPOSER, T3-FINALIZE]
status: todo
branch: T2-CAPTURE-UI
worktree: C:\wt\T2-CAPTURE-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - android/app/src/main/kotlin/nz/myinspection/app/skeleton/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/
  - android/app/src/main/kotlin/nz/myinspection/app/media/camera/
  - android/app/src/main/res/
forbid:
  - 业务判断写进 Composable/ViewModel（判定一律调 :core；UI 只呈现与转发）
  - 下拉框选状态（需求 §5：大按钮）
  - 主线程读取数据库/文件/EXIF、算哈希或做复杂序列化；全尺寸图片直接进入列表；自建相册扫描器
non_goals:
  - ghost overlay 与历史条（T3-HISTORY-COMPARE 在本卡骨架上加）
  - 报告/导出入口（T3/T5 各卡）；平板重设计（横屏只交付不重叠的确定性回退）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.capture.*"
dod_exit: 0
dod_assert: app 主题/语义单测 + route stack/CameraState 合法与非法转移/重复拍摄提交拒绝/focus key/系统 Photo Picker 单测 + assembleDebug + capture 核测试全绿（UI 未旁路核心规则）；Continue→Capture、Take photo→Shutter→Use photo、Review→Finalize confirm→Report handoff 均不超过 3 个用户决策步骤；非敏感有效表单/房间/锚点/焦点恢复，主线程零 I/O，缩略图按显示尺寸解码并受有界 LRU 管理；真机两房间 fixture 覆盖 light/dark、TalkBack/200% 字号、短语/听写、全景与不利发现拍照、Back、保存失败、相机中断及杀进程恢复；记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# Collection UI Card — T2-CAPTURE-UI

## Output boundary

This card implements the existing collection journey only: property selection → inspection setup → room capture → completeness review. It also implements the shared `FieldLedgerAppShell` contract needed to mount that journey. Schedule and Settings are declared destinations but their page content remains owned by their task cards. Reports, backup, history overlay, new business destinations, and tablet redesign remain outside this card.

The UI renders and forwards `:core` state. It never computes allowed status, missing evidence, baseline, photo requirement, or finalize eligibility.

Production NFR ownership is explicit: this card implements the <=3-decision capture paths, non-sensitive form/context restoration, system-only selected-media import, zero main-thread I/O, size-aware thumbnail decode, and bounded image LRU. `T3-FIELD-UX-ACCEPTANCE` owns measured cold-start/frame/I/O/cache evidence; this card must satisfy that downstream gate and may not defer a known miss to final smoke.

## Pre-implementation completeness gate

The repository currently has Property/Tenancy tables but no declared user journey or domain service for creating and editing them. This card must not seed demo data, call SQLDelight directly from UI, or present selectors that can never be populated.

Before implementation starts, the task board must assign an owner and dependency for all four operations below. Each operation is assigned either to one focused predecessor card or to this card through an explicit scope amendment before approval; none remains implicit in Compose:

| Required operation | Minimum contract | Why it blocks this card |
| --- | --- | --- |
| Create/edit property | Address, `RENTAL / OWNER_OCCUPIED`, boarding-house flag, validation, soft-delete policy | `No properties yet → Add property` otherwise dead-ends on first launch |
| Create/edit tenancy | Property, tenant display name/contact when provided, start/end dates, active/ended state | Routine/Ingoing/Exit setup cannot present a valid tenancy choice |
| Select/designate baseline | Existing finalized inspection candidates and explicit “no baseline” warning state | Exit setup cannot explain or persist its evidence basis |
| Query property-hub facts | Draft pointer, due date, last finalized inspection, last verified backup, blockers | Property cards otherwise invent business aggregation in ViewModel |

Schedule and Settings destinations also cannot be dead taps. Before their owners land, shell tests use registered placeholder test routes. A release build exposes a destination only when its declared root route exists.

Recommended default: create one predecessor card `T2-PROPERTY-TENANCY-FLOW` owning the four operations above plus first-run Add/Edit UI, then make this card depend on it. Do not enlarge `T2-CAPTURE-UI` with database and tenancy lifecycle logic.

## Route metadata

```kotlin
sealed interface CollectionRoute {
    data object Properties : CollectionRoute                  // properties
    data class PropertyHub(val propertyId: String) : CollectionRoute
    data class Setup(val propertyId: String) : CollectionRoute
    data class Capture(val inspectionId: String) : CollectionRoute
    data class Review(val inspectionId: String) : CollectionRoute
    data class Camera(
        val inspectionId: String,
        val targetType: TargetType,
        val targetId: String
    ) : CollectionRoute
    data class CameraReview(
        val inspectionId: String,
        val tempAssetId: String
    ) : CollectionRoute
}

enum class TargetType { ROOM_PANORAMA, INSPECTION_ITEM }
sealed interface CollectionSheet {
    data class Status(val itemId: String) : CollectionSheet
    data class Phrase(val fieldId: String) : CollectionSheet
}
enum class CollectionDialog { BULK_STATUS, DISCARD_SETUP, DISCARD_TEMP_PHOTO, FINALIZE }

sealed interface CollectionNavigationEvent {
    data class Open(val route: CollectionRoute) : CollectionNavigationEvent
    data object Back : CollectionNavigationEvent
    data class InspectionFinalized(val inspectionId: String) : CollectionNavigationEvent
    data class ShowSheet(val sheet: CollectionSheet) : CollectionNavigationEvent
    data class ShowDialog(val dialog: CollectionDialog) : CollectionNavigationEvent
}
```

Navigation events are single-consumption commands. The producer writes them to a non-replaying event channel; the navigation host consumes each event once and records its event ID until the operation commits. UI state flows never carry Push, Pop, Sheet, or Dialog commands, so recomposition and process restoration cannot repeat a route operation.

Route paths are fixed:

| Route | Path | Top-level navigation |
| --- | --- | --- |
| `Properties` | `properties` | Visible, Properties selected |
| `PropertyHub` | `properties/{propertyId}` | Visible, Properties selected |
| `Setup` | `properties/{propertyId}/inspection/new` | Hidden |
| `Capture` | `inspections/{inspectionId}/capture` | Hidden |
| `Review` | `inspections/{inspectionId}/review` | Hidden |
| `Camera` | `inspections/{inspectionId}/camera/{targetType}/{targetId}` | Hidden, edge-to-edge |
| `CameraReview` | `inspections/{inspectionId}/camera-review/{tempAssetId}` | Hidden, edge-to-edge |

## App shell and bar contract

| Route | Container | Leading action | Title | Trailing action | Bottom navigation |
| --- | --- | --- | --- | --- | --- |
| `Properties` | `FieldLedgerAppShell` | None | `Properties` | Add property | Visible; Properties selected |
| `PropertyHub` | `FieldLedgerAppShell` | Back | Property short address | Overflow when two or more commands exist | Visible; Properties selected |
| `Setup` | `FieldLedgerTaskScaffold` | Cancel | `New inspection` | None | Hidden |
| `Capture` | `InspectionCaptureScaffold` | Back, label `Save and exit` | Current room | Overflow only | Hidden |
| `Review` | `FieldLedgerTaskScaffold` | Back | `Review inspection` | None | Hidden |
| `Camera` / `CameraReview` | `CameraCaptureScaffold` | Overlay Close | None | Flash in Preview only | Hidden |

All titles are start-aligned. Setup and Review place their primary action in the bottom dock. Capture Back runs the save barrier before Pop. The app shell owns system insets once and restores independent Properties, Schedule, and Settings stacks exactly as specified in `context/DESIGN.md`; child content never consumes those insets again.

## Screen-level contract

| Route | Required order | Primary interaction | Deterministic edge states |
| --- | --- | --- | --- |
| `Properties` | Heading → active-draft card if present → other property cards → Add property | A card is a structural Surface with one full-width `Continue inspection` or `Open property` action; no nested clickable card | Sort draft first, then due date, then address. Search appears only above eight active properties. Empty state ends in Add property |
| `PropertyHub` | Address → compliance block → Start/Continue hero → due/last inspection/backup/notice facts → dated history | Exactly one Start/Continue hero; supporting rows navigate to their owning later tasks | Missing tenancy or baseline is explained beside Start, not in a generic banner. No draft means Start, never a disabled Continue |
| `Setup` | Type → tenancy/baseline → date/time → template → inline validation | Large labelled choices for inspection type; ordinary finite metadata uses exposed selection controls, but status never does | Conditional fields stay adjacent to their cause. Errors include entered value and correction. CTA remains above IME and system bars |
| `Capture` | Missing strip → room progress → panorama → eligible bulk action → item stream → bottom dock | One physical next action; exact gap jump uses room → item → Status/Photo/Note order | The item/list anchor is stable across save, room switch, camera, theme, and process restoration |
| `Review` | Completion count → groups by room → exact missing rows or complete evidence summary | Each missing row has `Fix` and returns focus to the exact control; complete state hands off to Finalize | No disabled Finish, no error summary without locations, and no silent scroll to an approximate card |

User-facing dates are locale-formatted and include an absolute date. Raw ISO strings, UUIDs, enum names, database field names, and operation IDs never render.

## First-run and permission contract

`Properties` empty state is the first-run experience; no carousel, account prompt, sample property, or permission wall precedes it. It contains `Add your first property`, `No account. Inspection data stays on this device.`, and secondary `Restore encrypted backup` when the restore route is registered.

Permissions are requested just in time and never chained:

| Permission/capability | Request point | Denied result |
| --- | --- | --- |
| Camera | First `Take photo` | Explain the need; offer Allow, Import photo, and Back |
| Microphone / speech | First Voice action | Keep phrase and keyboard entry; show Open settings only after permanent denial |
| Notifications | Schedule reminder enable action, outside this card | Schedule page remains usable without system alerts |
| SAF provider | Explicit export/backup action, outside this card | Local inspection and reports remain intact |

Returning from a permission or system surface restores the exact property, room, item, scroll anchor, and focus trigger. A denial is not shown again until the user invokes the dependent action.

## Setup readiness summary

Setup remains one route. Immediately above `Start inspection`, render an inline `Ready to inspect` summary with property, inspection type, tenancy/baseline when applicable, local date/time, and template. Blocking compliance failures replace readiness with the exact correction; non-blocking baseline warnings remain visible in the summary.

The summary is not another confirmation dialog. Editing any upstream field updates it in place. `Start inspection` performs one final core validation and, on rejection, focuses the exact invalid field without clearing valid entries.

## Routine fast path

For Routine only, place `Mark {N} remaining items OK` after the panorama and before the item stream once core returns at least one eligible unrated item. Its count updates from core; the app never infers eligibility.

The intended rhythm is panorama → mark visible exceptions `Needs attention` → bulk-mark remaining eligible items OK → complete exception evidence. The shortcut never copies history, never touches an already rated/suppressed/ineligible item, and is absent when `N=0`. Undo remains available until the room save barrier commits; after commit, ordinary per-item Change remains available.

## Ready-to-leave checkpoint

Review renders `Ready to leave the property` only when `missingTotal=0`, save state is `SAVED`, and no camera/temp commit is pending. It lists:

- `All required evidence captured`
- `Saved on this device`
- `{N} items need attention` or `No items need attention`

This state never says report ready or backed up. `Finish inspection` opens the existing permanence dialog. Back returns to editable Capture; finalize remains a separate deliberate action.

## Navigation stack contract

| User event | Stack operation | Resulting stack tail | Transition |
| --- | --- | --- | --- |
| Cold launch | Reset | `Properties` | None |
| Select property | Push | `Properties → PropertyHub` | Forward 200ms |
| Start inspection | Push | `PropertyHub → Setup` | Forward 200ms |
| Setup succeeds | Replace top | `PropertyHub → Capture` | Forward 200ms |
| Change room | No route operation | `Capture` | Content crossfade 120ms; room state changes after save barrier |
| Open review | Push | `Capture → Review` | Forward 200ms |
| Review finds gap | Pop | `Review → Capture` | Back 150ms; scroll and focus exact gap |
| Review Back | Pop | `Review → Capture` | Back 150ms; restore anchor |
| Finalize succeeds | Emit `InspectionFinalized(inspectionId)` once | T3 replaces the Capture task subgraph with `ReportExport` | Forward 200ms; closing export returns to PropertyHub |
| Open camera | Push | `Capture → Camera` | Fade 150ms |
| Capture callback succeeds | Push | `Camera → CameraReview` | Fade 150ms |
| Retake | Pop | `CameraReview → Camera` | Fade 150ms after temp deletion |
| Use photo succeeds | Pop CameraReview and Camera | `Capture` | Fade 150ms; focus new evidence tile |
| Close camera | Pop | `Camera → Capture` | Fade 150ms; focus original Take photo trigger |

Reduced-motion mode replaces every route transition with a 100ms crossfade and zero translation. Predictive Back previews the exact destination named in the table.

Back evaluates guards in this fixed order: camera commit lock → temporary camera bytes → dirty task payload → capture save barrier → route Pop. A failed save barrier cancels Pop, retains the current route and data, and focuses the persistent error banner.

## Capture surface contract

| Area | Required components | State source | Interaction |
| --- | --- | --- | --- |
| Header | `top-app-bar`, `missing-evidence-strip`, `room-progress-strip` | Core completeness + route state | Missing strip jumps to first gap using fixed ordering |
| Room panorama | `photo-evidence-tile`, `button-secondary` | `ROOM_PANORAMA` requirement | Opens Camera with `TargetType.ROOM_PANORAMA` |
| Inspection item | `inspection-item-card`, `evidence-rail`, two `status-choice` controls | Core item state | `OK` writes Good/No issue; `Needs attention` opens detailed status sheet |
| Notes | `phrase-sheet`, voice action, `input-field` | Phrase library + spike capability | Entry order is phrase → on-device voice → keyboard |
| Privacy | `privacy-chip` | `privacy_flag` | Label `Contains tenant belongings`; when on, state that both reports exclude it by default |
| Room bulk action | `button-secondary`, `confirmation-dialog` | Core list of unrated eligible items | Confirm once, then write each eligible item through core |
| Bottom action | `bottom-action-dock` | Core completeness | Next room, Review missing, or Finish; never inert |

`N_A` and `Not present at this property` live in item overflow. `Not present` writes `property_item_override`; the confirmation names that it persists for future inspections. Every target is at least `48dp`; state selection never uses a dropdown.

## Inspection item card state contract

Only one item is expanded; `expandedStableId` is the restoration source of truth. Opening another item crosses the save barrier before changing expansion.

| State | Visible result | Transition rule |
| --- | --- | --- |
| `UNRATED` | Title, evidence rail, prior summary when available, `OK`, `Needs attention` | Title toggles detail but never changes status |
| `OK_COMPACT` | `OK`, retained photo/note counts, Change | Status change preserves every existing note/photo |
| `ATTENTION_EXPANDED` | Detailed status sheet result, exact photo/note requirement, phrase/voice/keyboard, evidence actions | Never auto-advance or auto-collapse |
| `ATTENTION_COMPACT` | Detailed status plus `Photo needed` / `Evidence complete`, Review | Amber comes only from core missing-required state |
| `NOT_APPLICABLE` | Explicit label, retained optional evidence, Change | No evidence deletion |
| `SAVE_FAILED` | Current controls plus persistent Retry/Keep editing banner | No collapse, navigation, or loss of focus |

Detailed status selection completes in the sheet, closes to the same item, and focuses the newly required evidence control. Switching back to OK/N-A recomputes requirements but retains evidence. At narrow width or 200% font scale, paired choices stack vertically instead of truncating.

`Mark remaining items OK` names the exact current-room count, affects only eligible unrated items, never overwrites an existing status, and offers Undo. Confirmation copy is fixed: `Mark {N} unrated {room} items OK? Existing ratings will not change.`

## Collection sheet and dialog contract

| Trigger | Overlay | Selection/confirm result | Scrim / swipe / Back | Focus after close |
| --- | --- | --- | --- | --- |
| `Needs attention` detail control | `STATUS_SHEET(itemId)` | Persist selected status, reveal required evidence, dismiss | All dismiss without change | Trigger status group; selected label announced |
| `Insert phrase` | `PHRASE_SHEET(fieldId)` | Insert at cursor, dismiss, show 5-second Undo | All dismiss without change | Text field at inserted phrase |
| Room bulk `Mark remaining items OK` | `BULK_STATUS_CONFIRMATION` | Write only eligible IDs returned by core | Scrim has no effect; Back equals Cancel | Bulk action or first failed item |
| Setup Cancel with dirty payload | `DISCARD_SETUP` | Discard task payload and Pop | Scrim has no effect; Back equals Cancel | Property primary card |
| Camera Back with temp bytes | `DISCARD_TEMP_PHOTO` | Delete temp bytes and return to Preview | Scrim has no effect; Back equals Cancel | Camera review heading or shutter |
| Finalize | `FINALIZE_CONFIRMATION` | Enter non-dismissible finalize commit | Scrim has no effect; Back equals Cancel before commit | Finalize button on Cancel; progress heading on Confirm |

Choice sheets expose a drag handle, start-aligned pane title, and explicit Close button. Destructive dialogs default accessibility focus to Cancel. Overlay close restores the semantic trigger key; when that trigger no longer exists, focus resolves to the next sibling, previous sibling, then containing heading.

## Camera lifecycle

```kotlin
enum class CameraState {
    IDLE,
    PERMISSION_REQUIRED,
    STARTING,
    PREVIEW,
    CAPTURING,
    REVIEW,
    COMMITTING,
    COMPLETED,
    ABANDONING,
    ERROR_PERMISSION,
    ERROR_START,
    ERROR_CAPTURE,
    ERROR_COMMIT
}
```

| Current state | Event | Next state | Visual feedback | Available actions |
| --- | --- | --- | --- | --- |
| `IDLE` | `TRIGGER` + permission granted | `STARTING` | Edge-to-edge black surface, centered progress after 300ms | Back |
| `IDLE` | `TRIGGER` + permission absent | `PERMISSION_REQUIRED` | In-context permission rationale | Allow, Import, Back |
| `PERMISSION_REQUIRED` | Grant | `STARTING` | Progress after 300ms | Back |
| `PERMISSION_REQUIRED` | Deny | `ERROR_PERMISSION` | Persistent error with exact recovery | Open settings, Import, Back |
| `STARTING` | Preview bound | `PREVIEW` | First frame replaces progress; controls fade in 120ms | Shutter, Flash, Import, Close |
| `STARTING` | Bind failure | `ERROR_START` | Persistent banner over opaque surface | Try again, Import, Back |
| `PREVIEW` | Shutter | `CAPTURING` | Shutter enters progress state; all capture triggers reject duplicate taps | Close queued, no second capture |
| `CAPTURING` | File callback success | `REVIEW` | Rotation-correct 4:3 preview and review bar | Retake, Privacy, Use photo |
| `CAPTURING` | File callback failure | `ERROR_CAPTURE` | Preview remains; persistent banner | Try again, Import, Back |
| `REVIEW` | Retake | `ABANDONING` | Progress on Retake; temp file deleted | None until deletion completes |
| `ABANDONING` | Temp deleted | `PREVIEW` | Live preview restored | Normal preview actions |
| `REVIEW` | Use photo | `COMMITTING` | Use photo progress; preview and privacy state remain visible | No duplicate commit |
| `COMMITTING` | Pipeline success | `COMPLETED` | Brief `Photo added` state; Pop to Capture | None |
| `COMMITTING` | Pipeline failure | `ERROR_COMMIT` | Temp retained; persistent error | Try again, Retake, Back confirmation |
| Any nonterminal state | Fatal lifecycle loss | matching `ERROR_*` | Current item context remains; no blank route | Retry/import/back per row above |

Camera controls always render on `#000000` at `64%` opacity with `#FFFFFF` content. Overlay is absent in this card; the camera contract exposes an `overlaySlot` consumed only by `T3-HISTORY-COMPARE`.

## Camera Back and abandon rules

| State | Back result |
| --- | --- |
| `PERMISSION_REQUIRED`, `ERROR_PERMISSION`, `ERROR_START`, `PREVIEW`, `ERROR_CAPTURE` | Pop Camera; return focus to original photo trigger |
| `CAPTURING` | Set `abandonAfterCallback=true`; show `Finishing capture`; delete callback result; Pop Camera |
| `REVIEW`, `ERROR_COMMIT` | Open `DISCARD_TEMP_PHOTO` dialog; Cancel returns Review; Discard deletes temp then returns Preview |
| `COMMITTING` | Reject Back until callback; announce `Finishing photo`; success Pops to Capture, failure returns Review |
| `COMPLETED` | Pop CameraReview and Camera to Capture |

Only `Use photo` creates the evidence association. `Retake`, confirmed discard, and queued abandon delete the temporary file and leave no photo row.

## Save and feedback handoff

Room switch, Capture Back, app background, and Review entry trigger the save barrier defined in `T3-FIELD-UX-ACCEPTANCE`. A failed barrier keeps the user on the current route, preserves input, and shows `feedback-banner(ERROR)` with `Try again` and `Keep editing`.

`SAVING` appears only after 300ms. `SAVED` is quiet metadata. Incomplete review uses `Review N missing items`; complete review uses `Finish inspection`. No disabled Finish control is rendered.

## Verification handoff

The PR evidence covers every route operation, every camera state, light/dark mode, Back, process recreation, save failure, 200% font scale, and focus return. Automated tests assert route-stack tails, illegal camera transitions, duplicate capture/commit rejection, and semantic labels. On-device thresholds live only in `T3-FIELD-UX-ACCEPTANCE`.
