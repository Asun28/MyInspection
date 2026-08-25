---
id: T0-RECONCILE-DESIGN-JOURNEYS
title: 补齐 Field Ledger 信息架构、导航、恢复与离线隐私旅程
depends_on: [T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEYS
worktree: C:\wt\T0-RECONCILE-DESIGN-JOURNEYS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码或机读组件 id
  - 把需要网络的 provider 行为伪装为核心离线流程
non_goals:
  - 逐组件视觉合同、motion token 或 Compose 实现
  - 新增 Dashboard、Reports 顶级页或导航抽屉
acceptance:
  - "A1 页面闭包：21 个 production pageId 各有 route、page type、parent、bottom-nav 可见性与 owner；顶级仅 Properties/Schedule/Settings，无 Dashboard/Reports/drawer"
  - "A2 容器与导航：9 个 page type 的容器、返回、持久状态和 exit rule 完整；三栈 bottom navigation、inset owner 与 PUSH/POP/sheet/dialog/camera transitions 都有确定行为"
  - "A3 路由与 overlay：核心和支持触发逐项声明 precondition、action、target、transition、退出/焦点；6 类 overlay/back/drag/scrim 拦截状态无缺口"
  - "A4 恢复与焦点：interaction state 表、10 个 focus lifecycle 事件、主巡检 resume/save barrier/missing-item jump/finalize/report handoff 均有唯一恢复动作"
  - "A5 离线与数据保护：6 类 capability 明确核心可用/局部降级；备份 10 个状态各有消息和动作，provider/remote remediation 失败不阻断巡检、finalize、历史或 PDF"
dod_command: pwsh -NoProfile -Command "if (-not (((Select-String -Path 'context/DESIGN.md' -Pattern '^\| [123] \| `(PROPERTIES_ROOT|SCHEDULE_ROOT|SETTINGS_ROOT|PROPERTY_HUB|INSPECTION_SETUP|INSPECTION_CAPTURE|INSPECTION_REVIEW|REPORT_EXPORT|NOTICE_CENTER|NOTICE_COMPOSE|HHC_CAPTURE|BACKUP_SETTINGS|RESTORE_TASK|QUALITY_SETTINGS|LOCAL_MEDIA_SETTINGS|HEALTH_STATUS|DIAGNOSTIC_EXPORT|LOCAL_DATA_ERASURE|REMEDIATION_SETTINGS|CAMERA_CAPTURE|CAMERA_REVIEW)` \|').Count -eq 21) -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| `(ROOT_STATIC|HUB_STATIC|PUSH_DETAIL|STREAM_CAPTURE|FULLSCREEN_TASK|CAMERA_TASK|MODAL_SHEET|ALERT_DIALOG|SYSTEM_SURFACE)` \|').Count -eq 9) -and (Select-String -Path 'context/DESIGN.md' -Pattern '^### Core routes$') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^### Supporting routes and overlays$') -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| (Choice/action sheet|Phrase sheet|Destructive confirmation|Finalize confirmation|Camera with uncommitted photo|Restore while `COMMITTING`) \|').Count -eq 6) -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| (Push / deep-link entry|Pop|Top-level switch|Active destination reselect|Sheet/dialog opens|Sheet/dialog closes|Camera Use photo|Missing-item jump|Dynamic insertion/removal|Save failure blocks exit) \|').Count -eq 10) -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| (Local inspection, history, rules, finalize, PDF|Voice without an installed offline recognizer|Local/USB backup|Cloud SAF backup/restore|Offline remediation seed match|Remote remediation) \|').Count -eq 6) -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| `(NOT_CONFIGURED|READY|RUNNING|VERIFIED|PROVIDER_UNAVAILABLE|AUTHORIZATION_REVOKED|NEEDS_UNLOCK|NEEDS_PASSPHRASE|LOW_STORAGE|FAILED)` \|').Count -eq 10))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 exact matrix 通过：21 pages、9 page types、6 overlay rules、10 focus events、6 offline capabilities、10 backup states，并存在 core/support route 表
review_gate: codex {verdict:pass}
hygiene: 同一页面只有一个路由/返回合同；离线正常态不使用持续错误横幅
doc_sync: 与机读组件 id 和当前产品需求保持一致（R5）
---

# T0-RECONCILE-DESIGN-JOURNEYS

## 产出

把 Field Ledger 从视觉语言补全为可实现的应用体验合同：页面类型、导航栈、触发入口、焦点恢复、证据采集主流程，以及离线安全/备份/恢复/清除的用户旅程。

## 验收

执行 front matter 的 `dod_command`，并确认所有路线都有返回语义、所有失败状态都有恢复动作。
