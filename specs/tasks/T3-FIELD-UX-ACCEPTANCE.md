---
id: T3-FIELD-UX-ACCEPTANCE
title: Field Ledger 真机 UX 验收：日光、单手、无障碍与相机取证
depends_on: [T2-CAPTURE-UI, T3-HISTORY-COMPARE]
status: todo
branch: T3-FIELD-UX-ACCEPTANCE
worktree: C:\wt\T3-FIELD-UX-ACCEPTANCE
allow_paths:
  - docs/ux/
  - specs/tech-debt-tracker.md
forbid:
  - 在验收卡内顺手修改生产 UI；发现项登记成独立 TD/卡
  - 复制 Luosunce/material-design-data 的代码、图片或 CC BY-NC-SA 内容
  - 用模拟器截图代替日光、单手、TalkBack 与相机真机证据
non_goals:
  - 平板/横屏重设计（首版仍按单手竖屏）
  - 用户研究招募、遥测平台或远程分析服务
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; if (-not (Test-Path docs/ux/FIELD-UX-ACCEPTANCE.md)) { exit 1 }; if (Select-String -Path docs/ux/FIELD-UX-ACCEPTANCE.md -Pattern '⬜|PENDING|待验证') { exit 1 }
dod_exit: 0
dod_assert: 真机报告包含 PERF-01..11、THEME-01..07、STATE-01..08、NAV-01..10、JOURNEY-01..04、FIELD-01..07、A11Y-01..06、CAM-01..03 共 56 个具名用例且全部 PASS；每项含设备/构建/主题/字号/刷新率/步骤/实测值/截图或录屏；所有 P0/P1 发现都有偿还指针
review_gate: codex {verdict:pass}
hygiene: 重复证据合并；每个发现只保留能证明风险的一组最小截图/录屏（R4）
doc_sync: context/DESIGN.md 只同步经证据确认的规则；TASK-BOARD 记录验收结论（R5）
---

# On-Device UX Verification Card — T3-FIELD-UX-ACCEPTANCE

## Output and evidence format

The card produces `docs/ux/FIELD-UX-ACCEPTANCE.md`. Every test row contains `testId`, device model, Android version, build SHA, theme, font scale, precondition, steps, measured result, `PASS|FAIL`, and evidence path. `PENDING`, skipped rows, and simulator-only evidence fail the card.

## Save/restore state model

```kotlin
enum class SaveState { CLEAN, DIRTY, SAVING, SAVED, FAILED, RESTORING }

data class RouteSnapshot(
    val pageId: String,
    val arguments: Map<String, String>,
    val triggerFocusKey: String?
)

data class NavigationSnapshot(
    val selectedTopLevelDestination: String,
    val propertiesStack: List<RouteSnapshot>,
    val scheduleStack: List<RouteSnapshot>,
    val settingsStack: List<RouteSnapshot>,
    val pendingSystemRequestId: String?
)

data class DraftUiSnapshot(
    val schemaVersion: Int = 1,
    val propertyId: String,
    val inspectionId: String,
    val navigation: NavigationSnapshot,
    val roomInstanceId: String?,
    val expandedStableId: String?,
    val listAnchorStableId: String?,
    val listOffsetPx: Int,
    val focusKey: String?,
    val pendingText: String?,
    val pendingTempAssetId: String?,
    val editRevision: Long,
    val savedRevision: Long
)
```

Storage ownership is fixed:

| State | Store | Rule |
| --- | --- | --- |
| Inspection status, note, photo association, privacy | SQLDelight/domain store | Authoritative durable data |
| Selected destination, three route stacks, route IDs/arguments, room, expansion, list anchor/offset, focus key | `SavedStateHandle` + app-private resume snapshot | Small restoration metadata only; every restored route is validated against the page registry |
| In-progress text newer than durable revision | `SavedStateHandle.pendingText` | Restored only when `editRevision > savedRevision` |
| Temporary captured photo | App-private temp file + `pendingTempAssetId` | Never treated as evidence before commit |
| Bitmap, full item list, report, camera object | Nowhere | Reconstructed from IDs; never placed in Bundle/snapshot |

## Save state machine

| Current | Event | Next | Required side effect |
| --- | --- | --- | --- |
| `CLEAN` or `SAVED` | `EDIT(revision+1)` | `DIRTY` | Update in-memory UI and `SavedStateHandle` immediately |
| `DIRTY` | 300ms idle, field blur, room switch, route Back, Review entry, or `ON_STOP` | `SAVING` | Capture `requestRevision`; start one conflated domain write |
| `SAVING` | New `EDIT` | `DIRTY` | Increment revision; keep in-flight result but mark it stale |
| `SAVING` | `SAVE_OK(resultRevision == editRevision)` | `SAVED` | Set `savedRevision`; clear pending text; persist resume pointer |
| `SAVING` | `SAVE_OK(resultRevision < editRevision)` | `DIRTY` | Ignore stale completion; start latest save after current write ends |
| `SAVING` | `SAVE_ERROR(resultRevision == editRevision)` | `FAILED` | Preserve input; persist pending text; show persistent Retry banner |
| `SAVING` | `SAVE_ERROR(resultRevision < editRevision)` | `DIRTY` | Keep latest input; save latest revision next |
| `FAILED` | `RETRY` | `SAVING` | Retry current revision only |
| `FAILED` | `EDIT` | `DIRTY` | Keep error visible until a later save succeeds |
| Any | Activity/process recreation | `RESTORING` | Load durable domain data, then apply valid snapshot metadata |
| `RESTORING` | Domain + snapshot valid | `SAVED` or `DIRTY` | `DIRTY` only when pending revision is newer than durable revision |
| `RESTORING` | Snapshot invalid or wrong inspection | `CLEAN` | Discard snapshot; open Property hub with Continue card |

Only one write runs per inspection. Duplicate callbacks are rejected by revision. Route Pop and room change wait for the save result. On failure, navigation remains on the current surface.

## Interruption and restoration rules

| Interruption | Restoration result |
| --- | --- |
| Configuration change | Same route, room, expanded item, list anchor/offset, pending text, and focus key |
| Background then system process kill | Recreate same route from `SavedStateHandle`; reload domain state; apply pending revision; announce `Restored {room}` once |
| User leaves Capture through Back | Complete save barrier; return Property hub; Continue card targets saved room/item context |
| Cold launch after reboot or user task dismissal | Open Properties; show Continue card; never auto-open camera or capture |
| Kill during Camera Preview with no temp | Return Capture; focus original trigger; show `Camera session ended` info banner |
| Kill during Camera Review with valid temp | Restore CameraReview with the same temp, privacy state, Retake, and Use photo |
| Kill during Camera Review with missing/corrupt temp | Delete stale pointer; return Capture; show error with Retake/Import |
| Kill during Commit | Query evidence association by operation ID; success returns Capture with tile, absence restores Review temp |
| Theme change | Recompose colors only; route, text, scroll, focus, camera state, and save revision remain unchanged |

Sheets, routine dialogs, transition progress, and pressed state are never restored. Process recreation closes them and focuses their stored trigger. `COMMITTING` finalize, restore, and photo operations restore by operation ID and query the durable result before exposing another action.

## Focus fallback algorithm

```text
focus(requestedKey):
  if node(requestedKey).exists && visible && enabled: focus requestedKey
  else if nearestEnabledSiblingAfter(requestedKey) exists: focus it
  else if nearestEnabledSiblingBefore(requestedKey) exists: focus it
  else if owningSectionHeading exists: focus heading
  else if screenHeading exists: focus heading
  else if primaryAction exists: focus primaryAction
  else: focus selectedTopLevelDestination
```

| Event | Primary focus target | Fallback input to algorithm |
| --- | --- | --- |
| Route Push | New screen heading | First primary action |
| Route Pop | Stored trigger `focusKey` from parent | Parent screen heading |
| Top-level destination switch | Restored semantic key for that stack | Destination heading |
| Active destination reselect | Destination root heading after scroll-to-top | Selected navigation item |
| Deep-link entry | Deep-linked page heading after the declared parent stack is built | First primary action |
| Bottom sheet close | Sheet trigger | Owning item title |
| Dialog Cancel or successful Confirm | Dialog trigger; completed camera commit targets new photo tile | Owning section heading |
| Missing-evidence jump | Exact missing status/photo/note control | Owning item title |
| Item removed/suppressed | Next item by sort; otherwise previous item | Room heading |
| Item collapses | Item title | Room heading |
| Photo commit succeeds | New `photo-evidence-tile` | Owning item title |
| Camera closes without photo | Original Take photo trigger | Owning item title |
| Save error appears | Keep current focus; announce banner politely | Retry receives focus only after explicit navigation |
| Compliance block appears | Keep current focus; announce block politely | Correction action moves focus to exact invalid field |
| System process restoration | Last valid `focusKey` | Screen heading |
| Cold Continue action | Capture screen heading, then restore scroll anchor | Current room heading |
| Theme or dynamic content update | Keep current focus key | Apply algorithm only if node disappears |
| Return from system picker/viewer | Stored system-request trigger key | Page heading |

`liveRegion=Assertive` is forbidden. `Polite` is restricted to save failure/recovery, compliance block, camera failure, and one-time restoration. Saved autosave events are silent.

## On-device acceptance checklist

### Performance

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `PERF-01` | Record 20 status taps at 240fps | Visible state-layer response begins within `100ms` for every tap |
| `PERF-02` | Run a two-room, 40-item capture flow at the device's highest supported refresh rate and collect frame data | Janky frames `<1%`; frozen frames `>700ms = 0`; P95 frame time `≤16.7ms` at 60Hz or `≤8.3ms` at 120Hz |
| `PERF-03` | Measure 10 warm room switches | Content settles within `300ms` P95; no scroll-position jump |
| `PERF-04` | Measure 10 Camera opens from Capture | First usable preview frame within `1200ms` P95 |
| `PERF-05` | Measure 10 captures using the fixed photo fixture | Shutter-to-review within `1500ms` P95; duplicate tap creates exactly one temp asset |
| `PERF-06` | Force a save to exceed 300ms | Saving appears at `300±50ms`; UI remains editable; no layout shift |
| `PERF-07` | Repeat flow at 200% font scale | No clipped action, count, date, status, camera control, or dock overlap |
| `PERF-08` | From force-stop, measure 20 cold launches on the reference phone with the same fixture | TTID P95 `≤1000ms`; median target `≤500ms`; first frame never waits on third-party, DB, file, media decode, or network initialization |
| `PERF-09` | Run cold launch, 40-item scroll, room switch, camera open, import, and history load with main-thread I/O detection enabled | Zero main-thread DB/file/network/EXIF/hash/complex-serialization violations; no violation is allowlisted to make the test pass |
| `PERF-10` | Scroll a 100-thumbnail fixture, background/foreground, then send moderate and critical memory-trim signals | Cache stays `≤min(64MiB, maxHeap×10%)`, evicts least-recent entries deterministically, releases on trim, reloads correct orientation/asset, and never retains full-size list bitmaps |
| `PERF-11` | Inject >1s delay into save, camera bind/photo commit, and history decode independently | Stable skeleton/previous content remains; local progress appears by `300±50ms`, TalkBack announces phase after 1s without spam, duplicate action is rejected, and cancel/back semantics match the owning state machine |

### Theme and visual contrast

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `THEME-01` | Run CI contrast metadata against every light/dark pair | Normal text `≥4.5`, large text/essential UI `≥3.0`; no undeclared pair |
| `THEME-02` | Inspect dark mode at 20% brightness in a dark room | No pure-black surface, white body-text halation, or lost elevation step |
| `THEME-03` | Inspect light and dark at 100% brightness outdoors/in bright shade | Status, focus, privacy, error, and missing evidence remain distinguishable by text/icon and color |
| `THEME-04` | Toggle system theme on every route while mode=`SYSTEM` | Theme changes without route, scroll, focus, edit, save, or camera-state reset |
| `THEME-05` | Set manual Light/Dark, then change system theme and restart | Manual mode persists and system changes do not override it |
| `THEME-06` | Apply three different Android wallpapers on API 31+ | Field Ledger token values remain byte-identical; dynamic color never activates |
| `THEME-07` | Inspect focus/input/card boundaries in dark mode | Essential boundary uses ratio `≥3.0`; `outline-variant` appears only decoratively |

### Save and restoration

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `STATE-01` | Edit note, rotate device during DIRTY/SAVING/SAVED | Text and revision survive; one final durable value; no duplicate save |
| `STATE-02` | Background app during text edit, kill process, relaunch task | Same route/room/item/anchor restored; pending text retained; one restoration announcement |
| `STATE-03` | Press Back during a forced save failure | Route does not Pop; input remains; Retry completes then Back succeeds |
| `STATE-04` | Edit while previous revision is SAVING | Stale callback never overwrites latest edit; latest revision becomes durable |
| `STATE-05` | Cold launch after device reboot with a draft | Properties opens; Continue card identifies property and room; no automatic camera/capture entry |
| `STATE-06` | Kill in Camera Preview | Capture restores with original photo trigger and no orphan evidence/temp |
| `STATE-07` | Kill in Camera Review | Valid temp restores; corrupt/missing temp returns Capture with recovery action |
| `STATE-08` | Kill during photo Commit at both sides of association write | Exactly one association exists; UI resolves to tile or retained Review, never duplicate/lost ambiguity |

### Navigation and containers

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `NAV-01` | Create distinct scroll/focus/depth state in Properties, Schedule, and Settings; switch among all three twice | Each destination restores its own stack, scroll, and semantic focus key; no route is pushed into another stack |
| `NAV-02` | Reselect each active destination at root and depth; include Properties with PropertyHub on top | Root scrolls to top; deep route Pops to destination root; PropertyHub Pops to Properties; destination is never duplicated |
| `NAV-03` | Traverse every declared `pageType` | Bottom navigation is visible only on `ROOT_STATIC` and `HUB_STATIC`; hidden before deeper-page entry and restored after Pop completes |
| `NAV-04` | Inspect every top app bar and camera surface | Title/leading/trailing/action placement matches the page-type matrix; no centered title, Home, hamburger, direct destructive action, or autosave button appears |
| `NAV-05` | Open every supported deep link from cold, background, and another selected tab | Minimal declared parent stack is built; Back follows that stack; invalid IDs resolve to the owning root with one actionable error |
| `NAV-06` | Double-tap every route trigger and rotate during transition | Exactly one navigation event commits; no duplicate page, sheet, dialog, camera, or finalize event exists |
| `NAV-07` | Close each choice sheet and destructive dialog through every allowed mechanism | Choice sheets dismiss by scrim/swipe/Back/Close; destructive dialogs ignore scrim and Back equals Cancel; focus follows the matrix |
| `NAV-08` | Press Back during dirty Setup, dirty Capture, save failure, temp Camera Review, and every `COMMITTING` state | Setup uses discard guard; Capture runs save barrier; failure blocks Pop; camera confirms temp deletion; committing blocks Back with one announcement |
| `NAV-09` | Exercise gesture Back and button Back at every route, overlay, and root | Predictive preview names the exact destination; overlays close first; root Back exits app and never reveals prior tab history |
| `NAV-10` | Run gesture and 3-button navigation, IME open/closed, cutout device, landscape, and 200% font | Insets are consumed once; no content or control is obscured; focused field stays `≥16dp` above IME; final item scrolls above dock |

### End-to-end collection journey

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `JOURNEY-01` | Start from clean app data and create the first usable property/inspection | First viewport has Add first property, Restore backup, and local-data reassurance; no account/sample data/permission wall; camera/microphone requests occur only at their triggers |
| `JOURNEY-02` | Configure Routine, Ingoing, Exit with and without baseline, and Annual setup | `Ready to inspect` always restates the effective property/type/tenancy/baseline/date/template; validation focuses the exact field and preserves every valid entry |
| `JOURNEY-03` | Complete a two-room Routine by marking three exceptions then bulk-marking remaining eligible items OK | No history is copied; no rated/suppressed/ineligible item changes; Undo and room save barrier behave as declared; exception evidence remains explicit |
| `JOURNEY-04` | Reach zero missing items while dirty, saving, saved, and with a pending camera commit | `Ready to leave the property` appears only in saved/no-pending state and says captured/saved/attention count; it never claims report ready, backed up, or finalized before those operations |

### Field use and card behaviour

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `FIELD-01` | Complete one room once with a right-hand grip and once with a left-hand grip; exclude deliberate camera framing | Status, note, photo, missing-item, and next-room actions require no two-hand grip or unreachable top-corner action; every critical action has a visible non-gesture path |
| `FIELD-02` | Rapidly tap OK/Needs attention, expand another card, and tap the space where the next card moves | Exactly one intended status change commits; card reflow never activates the newly moved target; focus/scroll anchor remains on the acted item |
| `FIELD-03` | Add note/photo evidence, change Attention → OK → N-A → Attention, then use and undo `Mark remaining items OK` | Existing evidence is never deleted; core requirements recompute; bulk action affects only eligible unrated current-room items and Undo restores all affected states |
| `FIELD-04` | Enter the same note by phrase, on-device voice, and keyboard; repeat with microphone denied and offline | Phrase insertion is reversible; voice state is explicit; denial exposes its recovery; keyboard remains fully usable and no local capture path presents offline as an error |
| `FIELD-05` | Inspect status, privacy, missing, blocked, selected, and focus states in grayscale plus protan/deutan simulation | Every state remains identifiable by stable icon and text without colour; privacy is labelled `Contains tenant belongings`, not a defect or deletion action |
| `FIELD-06` | Render 320dp, 360dp, and 412dp widths with long address/item names, absolute dates, and 200% font | No raw ID/ISO timestamp, ellipsized requirement, horizontal page scroll, clipped status, or inaccessible action; paired choices stack when needed |
| `FIELD-07` | Run a 30-minute interrupted session with 30 captures, repeated room changes, screen lock/unlock, and three camera re-entries | No progressive input/camera slowdown, lost draft, orphan temp, changed target association, or context reset; return always names the current property/room/item |

### Focus, touch, and camera

| Test ID | Procedure | Pass threshold |
| --- | --- | --- |
| `A11Y-01` | Traverse full flow with TalkBack | Order is heading → block/missing → room → items → dock; no decorative rail child receives focus |
| `A11Y-02` | Close every sheet/dialog by Cancel and Confirm | Focus returns to stored trigger or the event-specific table target |
| `A11Y-03` | Jump through every missing-evidence type | Focus lands on exact status/photo/note control and announces reason |
| `A11Y-04` | Remove first, middle, and last item from visible sequence | Fallback selects next, previous, or room heading exactly as algorithm specifies |
| `A11Y-05` | Measure every interactive target and adjacent gap | Target `≥48×48dp`; gap `≥8dp`; no gesture-only critical action |
| `A11Y-06` | Enable reduced motion and repeat route/camera flow | Translation, pulsing, and shared-element motion are zero; state remains fully understandable |
| `CAM-01` | Exercise every CameraState transition and Back row | Result matches `T2-CAPTURE-UI`; illegal transition produces no side effect |
| `CAM-02` | Test white wall, dark room, backlight, and high-entropy preview | Controls remain readable through fixed scrim; overlay never enters saved bytes |
| `CAM-03` | Deny permission, revoke permission, fail bind/capture/commit | Each error preserves item context and exposes the exact recovery action |

## Result policy

Every test is `PASS` or `FAIL`. A `FAIL` affecting evidence loss, wrong association, inaccessible primary action, compliance bypass, or destructive ambiguity is P0/P1 and blocks release. The card does not modify production code; each P0/P1 creates a dedicated task and tracker pointer before this card can pass.
