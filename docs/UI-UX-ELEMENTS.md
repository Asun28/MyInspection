# MyInspection UI/UX Elements 覆盖索引

> 状态：v1 设计覆盖索引
>
> Normative source: `context/DESIGN.md`
>
> 本文用途：检查每个页面是否使用了完整、统一的 Elements；不复制颜色、字号、间距、状态值或交互细节，不形成第二套 design system。

## 1. 设计系统服从关系

所有 UI 必须沿用 `MyInspection Field Ledger`：

- 颜色只使用 `colors` / `dark-colors` 语义 token；禁止页面自定义色值。
- 字体只使用 Android `sans-serif` / `sans-serif-condensed` 和已声明 typography token。
- 间距遵循 4dp 基线，屏幕边距 16dp；触控区域与主操作尺寸服从设计系统的无障碍合同。
- 普通图标只用 Material Symbols Outlined；已选顶层导航和已提交状态可用同名 Filled。
- 控件默认 8dp、卡片 12dp、大型 sheet 16dp 圆角；除标签和相机快门外不使用胶囊/圆形容器。
- 普通页面靠 tonal surface、边线和 evidence rail 建立层级，不用悬浮卡片阴影。
- 应用文案使用英文、结果导向、可操作；本文中文只用于设计协作。

## 2. Element 分层

### 2.1 容器与导航

`app-shell`、`detail-scaffold`、`task-scaffold`、`inspection-capture-scaffold`、`camera-capture-scaffold`、`modal-sheet`、`alert-dialog`、`navigation-bar`、`navigation-destination`、`top-app-bar`、`bottom-action-dock`、`overflow-menu`、`tooltip`。

### 2.2 内容、列表与状态表达

`section-header`、`result-list-row`、`settings-row`、`metadata-row`、`state-badge`、`property-summary-card`、`summary-stat`、`feedback-banner`、`compliance-block`、`save-status`、`verification-receipt`、`disclosure-list`、`health-issue-row`、`divider`。

### 2.3 表单与选择

`button-primary`、`button-secondary`、`button-destructive`、`icon-button`、`input-field`、`search-field`、`filter-chip-group`、`switch-row`、`checkbox-row`、`radio-group`、`segmented-control`、`choice-field`、`date-time-field`、`secure-input-field`、`confirmation-input`、`slider-field`、`status-choice`、`privacy-chip`、`validation-summary`。

同一决策区只允许一个 `button-primary`。可逆替代动作使用 `button-secondary`；不可逆动作只有在影响预览与确认条件满足后才使用 `button-destructive`；纯图标操作必须使用 `icon-button` 并同时提供 tooltip 和无障碍名称。

### 2.4 巡检、证据与媒体

`room-progress-strip`、`room-progress-segment`、`missing-evidence-strip`、`inspection-item-card`、`evidence-rail`、`photo-evidence-tile`、`evidence-grid`、`history-evidence-strip`、`review-gap-row`、`phrase-sheet`、`media-source-sheet`、`media-assignment-row`、`audio-evidence-control`、`media-preview`、`camera-control`、`camera-shutter`、`camera-overlay-control`、`camera-review-bar`、`privacy-action`。

### 2.5 长任务、安全与外部边界

`empty-state-panel`、`loading-indicator`、`task-progress-card`、`recovery-panel`、`backup-health-card`、`destination-row`、`task-stepper`、`preflight-summary`、`import-mapping-summary`、`import-mapping-row`、`confirmation-dialog`、`undo-snackbar`、`share-boundary-callout`、`report-action-sheet`、`notice-delivery-row`、`compliance-check-row`、`remediation-suggestion-card`、`focus-indicator`。

## 3. 页面 → Elements 覆盖表

表内 Elements 均为必需，除非标注“条件”。每页还必须满足 `context/DESIGN.md` 的 Element completeness gate。

| Page ID | 首要目标 | 必需 Elements | 条件/异常 Elements | Owner |
| --- | --- | --- | --- | --- |
| `PROPERTIES_ROOT` | 找到物业并开始/继续工作 | `app-shell`, `top-app-bar`, `navigation-bar`, `property-summary-card`, `result-list-row` | `search-field`（>8 个活跃物业）, `filter-chip-group`, `empty-state-panel`, `feedback-banner`, `recovery-panel` | `T2-CAPTURE-UI` |
| `SCHEDULE_ROOT` | 查看到期物业并进入巡检 | `app-shell`, `top-app-bar`, `navigation-bar`, `section-header`, `result-list-row`, `state-badge`, `metadata-row` | `empty-state-panel`, `filter-chip-group`, `feedback-banner` | `T4-SCHEDULE` |
| `SETTINGS_ROOT` | 管理本地数据、安全和偏好 | `app-shell`, `top-app-bar`, `navigation-bar`, `section-header`, `settings-row`, `state-badge` | `feedback-banner`; Danger zone 必须在末尾且使用 `DANGER` header/row | Shared settings shell |
| `PROPERTY_HUB` | 围绕一个物业做下一项工作 | `app-shell`, `top-app-bar`, `property-summary-card`, `summary-stat`, `section-header`, `result-list-row`, `metadata-row` | `compliance-block`, `backup-health-card`, `notice-delivery-row`, `empty-state-panel`, `feedback-banner` | `T2-CAPTURE-UI` |
| `INSPECTION_SETUP` | 以最少步骤建立有效巡检 | `task-scaffold`, `radio-group` 或 `segmented-control`, `date-time-field`, `choice-field`, `input-field`, `bottom-action-dock` | `validation-summary`, `compliance-block`, `recovery-panel` | `T2-CAPTURE-UI` |
| `INSPECTION_CAPTURE` | 在当前房间完成证据 | `inspection-capture-scaffold`, `top-app-bar`, `missing-evidence-strip`, `room-progress-strip`, `room-progress-segment`, `inspection-item-card`, `evidence-rail`, `status-choice`, `photo-evidence-tile`, `save-status`, `bottom-action-dock` | `history-evidence-strip`, `phrase-sheet`, `audio-evidence-control`, `media-source-sheet`, `feedback-banner`, `recovery-panel`, `undo-snackbar` | `T2-CAPTURE-UI` |
| `INSPECTION_REVIEW` | 找齐缺失证据并安全 finalize | `task-scaffold`, `summary-stat`, `section-header`, `review-gap-row`, `bottom-action-dock` | 完整时显示证据摘要；缺失时显示 `review-gap-row`; finalize 使用 `confirmation-dialog`; 失败使用 `feedback-banner` | `T2-CAPTURE-UI` / `T3-FINALIZE` |
| `REPORT_IMPORT` | 审核 Routine DOCX 并创建可编辑草稿 | `task-scaffold`, `task-stepper`, `choice-field`, `date-time-field`, `metadata-row`, `preflight-summary`, `disclosure-list`, `import-mapping-summary`, `import-mapping-row`, `bottom-action-dock` | `evidence-grid`, `media-preview`, `validation-summary`, `task-progress-card`, `recovery-panel`; 有 active draft 或任一 blocker 时不可提交 | `T3-REPORT-IMPORT-UI` |
| `REPORT_EXPORT` | 选择受众/格式并生成一致报告 | `task-scaffold`, `radio-group`, `segmented-control`, `disclosure-list`, `share-boundary-callout`, `bottom-action-dock` | PDF 默认并显示质量选择；HTML 不显示质量；另有 `remediation-suggestion-card`（房东版）, `task-progress-card`, `verification-receipt`, `recovery-panel` | `T3-REPORT-EXPORT-UI` |
| `NOTICE_CENTER` | 查看通知记录并新建通知 | `detail-scaffold`, `top-app-bar`, `section-header`, `notice-delivery-row` | `empty-state-panel`, `state-badge`, `feedback-banner` | `T4-NOTICES` |
| `NOTICE_COMPOSE` | 生成合规通知并记录送达 | `task-scaffold`, `date-time-field`, `choice-field`, `input-field`, `compliance-block`, `share-boundary-callout`, `bottom-action-dock` | `validation-summary`, `notice-delivery-row`, `confirmation-dialog` | `T4-NOTICES` |
| `HHC_CAPTURE` | 完成五类 Healthy Homes 快照 | `task-scaffold`, `section-header`, `compliance-check-row`, `input-field`, `bottom-action-dock` | `photo-evidence-tile`, `evidence-grid`, `compliance-block`, `recovery-panel` | `T6-HHC` |
| `BACKUP_SETTINGS` | 看清备份健康并创建可验证备份 | `detail-scaffold`, `backup-health-card`, `destination-row`, `secure-input-field`, `task-stepper`, `disclosure-list` | `task-progress-card`, `verification-receipt`, `recovery-panel`, `feedback-banner` | `T5-BACKUP-IO` |
| `RESTORE_TASK` | 验证包后安全替换本机数据 | `task-scaffold`, `task-stepper`, `secure-input-field`, `preflight-summary`, `disclosure-list`, `confirmation-input`, `bottom-action-dock` | `task-progress-card`, `verification-receipt`, `recovery-panel`; `COMMITTING` 后禁止 Back/Cancel | `T5-BACKUP-IO` |
| `QUALITY_SETTINGS` | 设定未来照片和每次 PDF 质量 | `detail-scaffold`, `settings-row`, `segmented-control`, `metadata-row` | `feedback-banner`; 照片/PDF 是两组独立选择，不用 slider | `T2-PHOTO-QUALITY-PROFILES` / `T3-PDF-RENDERER` |
| `LOCAL_MEDIA_SETTINGS` | 预览并安全释放可恢复照片空间 | `detail-scaffold`, `segmented-control` 或 `radio-group`, `verification-receipt`, `preflight-summary`, `disclosure-list` | `evidence-grid`, `confirmation-dialog`, `task-progress-card`, `recovery-panel`, `media-preview` | `T5-LOCAL-MEDIA-RETENTION` |
| `HEALTH_STATUS` | 处理本机可行动健康问题 | `detail-scaffold`, `section-header` | `health-issue-row`（有问题）, `empty-state-panel`（无行动项）, `verification-receipt`, `feedback-banner` | `T7-LOCAL-HEALTH-RELEASE` |
| `DIAGNOSTIC_EXPORT` | 明示范围后离线导出脱敏诊断 | `detail-scaffold`, `segmented-control`, `disclosure-list`, `verification-receipt`, `share-boundary-callout` | `task-progress-card`, `recovery-panel`, `feedback-banner` | `T5-DIAGNOSTIC-EXPORT` |
| `LOCAL_DATA_ERASURE` | 理解影响并物理清除 app 本地数据 | `task-scaffold`, `preflight-summary`, `disclosure-list`, `confirmation-input`, `bottom-action-dock` | `backup-health-card`, `task-progress-card`, `recovery-panel`; 执行中不显示旧业务内容 | `T5-LOCAL-DATA-ERASURE` |
| `REMEDIATION_SETTINGS` | 配置可选 provider，不影响离线主流程 | `detail-scaffold`, `secure-input-field`, `settings-row`, `disclosure-list` | `task-progress-card`, `recovery-panel`, `verification-receipt` | `T7-REMEDIATION` |
| `CAMERA_CAPTURE` | 高对比、单手取证 | `camera-capture-scaffold`, `camera-control`, `camera-shutter` | `camera-overlay-control`, `recovery-panel`; Import 始终是权限/相机失败的可用替代（平台允许时） | `T2-CAPTURE-UI` |
| `CAMERA_REVIEW` | 审核临时照片再提交为证据 | `camera-capture-scaffold`, `media-preview`, `camera-review-bar`, `privacy-action`, `metadata-row` | `task-progress-card`, `recovery-panel`, `confirmation-dialog`（丢弃临时照片） | `T2-CAPTURE-UI` |

`REPORT_IMPORT` 的固定阶段词汇是 `Details → Choose file → Scan → Match → Review → Create draft`。`Details` 必须由用户选择/确认 tenancy 和 report date，并把 current Routine template 显示为只读 metadata；缺失或无效值在相邻字段内阻塞提交并接收焦点，任务恢复时保留已确认值、当前阶段和焦点键。

## 4. Overlay 与系统界面覆盖

| Surface | 类型 | 使用 Elements / 约束 | 返回焦点 |
| --- | --- | --- | --- |
| `REPORT_ACTION_SHEET` | `MODAL_SHEET` | `report-action-sheet`, `share-boundary-callout`, `task-progress-card` | finalized inspection row |
| `THEME_MODE_SHEET` | `MODAL_SHEET` | `modal-sheet`, `radio-group`（System/Light/Dark） | Theme settings row |
| `STATUS_SHEET` | `MODAL_SHEET` | `modal-sheet`, `radio-group`, `metadata-row` | triggering status field |
| `PHRASE_SHEET` | `MODAL_SHEET` | `phrase-sheet`, `filter-chip-group`, `search-field`（短语量大时） | note field insertion point |
| `MEDIA_SOURCE_SHEET` | `MODAL_SHEET` | `media-source-sheet`, `recovery-panel` | triggering media action |
| finalize / discard / clear / remove confirmation | `ALERT_DIALOG` | `confirmation-dialog`; 明确对象、范围、不可逆性 | triggering action |
| restore replacement confirmation | `ALERT_DIALOG` | `preflight-summary`, `confirmation-input`, `button-destructive` | Replace action or first blocker |
| Android permission dialog | `SYSTEM_SURFACE` | 进入前可显示 `recovery-panel:PERMISSION`; 拒绝后不自动重复请求 | original trigger or recovery panel |
| File/create picker | `SYSTEM_SURFACE` | DOCX 只选单文件；报告只保存已验证产物；保存 request ID，禁 raw URI/名称进入普通错误 | originating source/save action |
| Report viewer / Sharesheet | `SYSTEM_SURFACE` | PDF/HTML 前置 `share-boundary-callout`; 只授予已验证产物临时 scoped URI | originating action |
| Android app settings | `SYSTEM_SURFACE` | `recovery-panel`; 仅由用户点 `Open settings` 启动；回前台重新检查权限 | permission recovery panel |
| Speech recognizer | `SYSTEM_SURFACE` | `input-field`, `phrase-sheet`; 无离线包时隐藏/降级；不阻塞键盘和短语 | voice trigger or note field |

## 5. 状态覆盖矩阵

| 状态 | 视觉/行为规则 | 首选 Element |
| --- | --- | --- |
| Empty | 说明事实并给下一步；不用装饰插画或假数据 | `empty-state-panel` |
| Local loading | 本地读取优先直接显示；超过 300ms 才显示稳定标签 | `loading-indicator` |
| Long-running | 显示阶段、可否取消、旧安全状态；阻止重复启动 | `task-progress-card`, `task-stepper` |
| Inline validation | 字段下方保留具体错误；多字段提交失败加摘要 | `input-field`, `validation-summary` |
| Recoverable failure | 说明原因、未受影响的范围、一个主恢复动作 | `recovery-panel`, `feedback-banner` |
| Blocking compliance/integrity | 常驻、不可关闭，操作回到具体修正点 | `compliance-block`, `recovery-panel:INTEGRITY` |
| Offline | 本地流程无全局 banner；只在联网/provider 动作旁解释 | `recovery-panel:PROVIDER` |
| Permission denied | 不重复弹系统框；提供 Open settings 和非权限替代 | `recovery-panel:PERMISSION` |
| Low storage | 标明需要空间及设备端/provider 端；当前数据保持不变 | `recovery-panel:LOW_STORAGE` |
| Restored after interruption | 恢复 property/room/item/scroll；一次性说明恢复位置 | `recovery-panel:RESTORED_SESSION` |
| Success | 普通自动保存保持安静；需要证明时显示绝对时间/计数/范围 | `save-status`, `verification-receipt` |
| Destructive | 先预览影响，再明确动词/输入确认，执行中禁止重复/Back | `preflight-summary`, `confirmation-input`, `confirmation-dialog` |
| External handoff | 明示副本将离开 app；“打开 chooser”不等于送达/保存 | `share-boundary-callout` |
| Import mapping | 摘要持续显示 mapped/excluded/blocker 数量；blocker 直达稳定行；源内容只按数据呈现，绝不成为应用指令 | `import-mapping-summary`, `import-mapping-row` |

## 6. 无障碍与响应式验收

每个页面和 Element 都必须同时通过：

1. TalkBack：名称、角色、状态、值、错误和动作完整；装饰性 icon/rail/divider 不单独聚焦。
2. 焦点：进入页先到标题或阻断摘要；sheet/dialog/system surface 关闭后回到原触发点；修复缺失证据直达具体控件。
3. 触控：目标至少 48×48dp，相邻目标至少 8dp；相机快门 72dp。
4. 字体：200% 时完整换行，底部操作垂直增长；日期、状态、错误、缺失原因不可省略号截断。
5. 主题：Light/Dark 使用同一语义 token；隐私、警告、错误、完成状态仍有文本和图标，不靠颜色。
6. 动效：Reduce Motion 下取消位移、缩放、脉冲和重复动画，只保留最多 100ms 淡入淡出及静态状态变化。
7. 方向/尺寸（compact / medium / expanded）：Compact/Medium 保持单列；Expanded 内容最大 720dp；横屏时 Back、主要状态、快门和确认操作不可被遮挡。
8. 系统区域：状态栏、手势导航、IME、安全绘制区域只由最外层容器消费一次；最后一项可滚到固定 dock 上方。

## 7. 明确排除的 Elements

v1 explicitly excludes FAB, drawer, carousel, charts, global snackbar, and remote telemetry.

v1 不设计也不预留以下入口：账户/头像、登录注册、云同步状态、团队协作、聊天/评论、通知收件箱、远程 admin、SQL/数据库编辑器、遥测开关、Dashboard 图表、只读报告查看器、自动照片差异比较、成本估算、应用内发短信/邮件、导航抽屉、汉堡菜单、平板双栏或 navigation rail。

如果未来范围变化，必须先更新产品边界与页面契约，再新增 Elements；不得通过“通用组件”偷偷引入未批准能力。
