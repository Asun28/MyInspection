---
id: T0-RECONCILE-UI-COVERAGE
title: 建立 UI/UX elements 页面与组件覆盖索引
depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-COVERAGE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE
allow_paths:
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 在任务卡复制 DESIGN.md 的完整组件规格
  - 修改产品代码、已归档任务卡或已合并主题/照片去重卡
non_goals:
  - 修改下游实现卡、实现 UI、生成截图或建立第二套设计 token
  - 改动数据库/安全权威文档
acceptance:
  - "A1 单向服从：索引明确 context/DESIGN.md 是唯一 normative source，本文件只投影 coverage/owner，不得定义第二套 token、状态或行为"
  - "A2 81 Elements：五个分层清单按 13/14/19/19/16 精确列出全部 81 component ids，无遗漏、重复或未注册 id"
  - "A3 21 页面：全部 production pageId 各有目标、必需 Elements、条件/异常 Elements；owner 可追到 Task Board/card，删除任一页即 RED"
  - "A4 12 overlays + 13 states：系统/模态界面各有类型、elements/约束和焦点返回；empty/loading/long-running/validation/failure/compliance/offline/permission/storage/restore/success/destructive/handoff 构成 13 条闭包"
  - "A5 可访问/响应与排除：48dp、200%、TalkBack/focus、Reduce Motion、compact/medium/expanded 响应规则完整；明确排除 FAB/drawer/carousel/charts/global snackbar/remote telemetry 等不适用元素"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^> Normative source: `context/DESIGN.md`') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^`app-shell`.*`tooltip`。$') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^`section-header`.*`divider`。$') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^`button-primary`.*`validation-summary`。$') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^`room-progress-strip`.*`privacy-action`。$') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^`empty-state-panel`.*`focus-indicator`。$') -and ((Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^\| `(PROPERTIES_ROOT|SCHEDULE_ROOT|SETTINGS_ROOT|PROPERTY_HUB|INSPECTION_SETUP|INSPECTION_CAPTURE|INSPECTION_REVIEW|REPORT_EXPORT|NOTICE_CENTER|NOTICE_COMPOSE|HHC_CAPTURE|BACKUP_SETTINGS|RESTORE_TASK|QUALITY_SETTINGS|LOCAL_MEDIA_SETTINGS|HEALTH_STATUS|DIAGNOSTIC_EXPORT|LOCAL_DATA_ERASURE|REMEDIATION_SETTINGS|CAMERA_CAPTURE|CAMERA_REVIEW)` \|').Count -eq 21) -and ((Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^\| (`(REPORT_ACTION_SHEET|THEME_MODE_SHEET|STATUS_SHEET|PHRASE_SHEET|MEDIA_SOURCE_SHEET)`|finalize / discard / clear / remove confirmation|restore replacement confirmation|Android permission dialog|Folder/file/create picker|PDF viewer / Sharesheet|Android app settings|Speech recognizer) \|').Count -eq 12) -and ((Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^\| (Empty|Local loading|Long-running|Inline validation|Recoverable failure|Blocking compliance/integrity|Offline|Permission denied|Low storage|Restored after interruption|Success|Destructive|External handoff) \|').Count -eq 13) -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^## 6\. 无障碍与响应式验收$') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^## 7\. 明确排除的 Elements$'))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 通过：五条 exact component inventory、21 page rows、12 overlay rows、13 state rows、accessibility/responsive 与 exclusions 章节完整；索引不覆盖 DESIGN 真相源
review_gate: codex {verdict:pass}
hygiene: 组件索引是覆盖投影而非真相源；删除重复 prose 后仍可从卡定位到设计合同
doc_sync: DESIGN.md、UI-UX-ELEMENTS 与下游卡指针闭环（R5）
---

# T0-RECONCILE-UI-COVERAGE

## 产出

增加一份完整但非规范性的 UI/UX elements 覆盖索引，将页面、overlay、状态、组件 id 与 owning card 连成可检查的投影；设计细节仍只由 `context/DESIGN.md` 定义。

## 验收

执行 front matter 的 `dod_command`，并确认索引不复制 canonical token/行为正文。
