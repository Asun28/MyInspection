---
version: beta
name: MyInspection Field Ledger
description: A daylight-readable, evidence-first design system for a local-first Android property inspection tool.
colors:
  primary: "#0B5D52"
  on-primary: "#FFFFFF"
  primary-container: "#C9ECE5"
  on-primary-container: "#073B35"
  secondary: "#3E5B67"
  on-secondary: "#FFFFFF"
  secondary-container: "#D9EAF1"
  on-secondary-container: "#183842"
  tertiary: "#8B5C00"
  on-tertiary: "#FFFFFF"
  tertiary-container: "#FFDEA8"
  on-tertiary-container: "#352000"
  surface: "#F7F9F7"
  surface-container-low: "#FFFFFF"
  surface-container: "#EEF2EF"
  surface-container-high: "#E2E8E4"
  on-surface: "#17201D"
  on-surface-variant: "#44504B"
  outline: "#6F7C76"
  outline-variant: "#C3CCC7"
  error: "#B3261E"
  on-error: "#FFFFFF"
  error-container: "#FFDAD5"
  on-error-container: "#410002"
  privacy: "#60458E"
  on-privacy: "#FFFFFF"
  privacy-container: "#EADDFF"
  on-privacy-container: "#241047"
dark-colors:
  primary: "#94D7CA"
  on-primary: "#003730"
  primary-container: "#0B5D52"
  on-primary-container: "#C9ECE5"
  secondary: "#B8CBD4"
  on-secondary: "#233E49"
  secondary-container: "#314E59"
  on-secondary-container: "#D9EAF1"
  tertiary: "#F1BD68"
  on-tertiary: "#4A3300"
  tertiary-container: "#5E4100"
  on-tertiary-container: "#FFDEA8"
  surface: "#0F1513"
  surface-container-low: "#151D1A"
  surface-container: "#1C2622"
  surface-container-high: "#26312D"
  on-surface: "#E0E8E4"
  on-surface-variant: "#BEC9C3"
  outline: "#89968F"
  outline-variant: "#3F4B46"
  error: "#FFB4AB"
  on-error: "#690005"
  error-container: "#93000A"
  on-error-container: "#FFDAD5"
  privacy: "#D1BCFF"
  on-privacy: "#35205A"
  privacy-container: "#48306D"
  on-privacy-container: "#EADDFF"
typography:
  display-md:
    fontFamily: sans-serif-condensed
    fontSize: 32px
    fontWeight: 700
    lineHeight: 38px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: sans-serif
    fontSize: 28px
    fontWeight: 700
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: sans-serif
    fontSize: 24px
    fontWeight: 700
    lineHeight: 30px
  title-lg:
    fontFamily: sans-serif
    fontSize: 20px
    fontWeight: 700
    lineHeight: 26px
  title-md:
    fontFamily: sans-serif
    fontSize: 17px
    fontWeight: 600
    lineHeight: 24px
  body-lg:
    fontFamily: sans-serif
    fontSize: 18px
    fontWeight: 400
    lineHeight: 27px
  body-md:
    fontFamily: sans-serif
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
  body-sm:
    fontFamily: sans-serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  label-lg:
    fontFamily: sans-serif
    fontSize: 16px
    fontWeight: 700
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: sans-serif-condensed
    fontSize: 13px
    fontWeight: 700
    lineHeight: 18px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: sans-serif
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: 0.02em
  data-lg:
    fontFamily: sans-serif-condensed
    fontSize: 28px
    fontWeight: 700
    lineHeight: 32px
    letterSpacing: -0.01em
rounded:
  none: 0px
  sm: 4px
  md: 8px
  lg: 12px
  xl: 16px
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 40px
  touch: 48px
  action: 56px
  screen-gutter: 16px
iconography:
  family: Material Symbols
  defaultStyle: outlined
  selectedStyle: filled
  sizes:
    sm: 18px
    md: 24px
    lg: 32px
  strokeWeight: 400
interaction:
  minTouchTarget: 48px
  adjacentTargetGap: 8px
  stateLayers:
    pressedOpacity: 0.12
    focusedOpacity: 0.12
    draggedOpacity: 0.16
    disabledContentOpacity: 0.38
    disabledContainerOpacity: 0.12
  focusRingWidth: 3px
  cameraScrim: "#000000"
  cameraScrimOpacity: 0.64
  onCameraScrim: "#FFFFFF"
motion:
  pressFeedbackMs: 100
  stateChangeMs: 180
  expandMs: 200
  sheetEnterMs: 250
  exitMs: 150
  easingEnter: material-emphasized-decelerate
  easingExit: material-emphasized-accelerate
  reducedMotionTranslation: 0px
components:
  app-shell:
    compose: Scaffold
    codeName: FieldLedgerAppShell
    regions: [TOP_APP_BAR, CONTENT, BOTTOM_NAVIGATION, OVERLAY_HOST]
    destinations: [PROPERTIES, SCHEDULE, SETTINGS]
    states: [ROOT, HUB, TRANSITIONING, RESTORED]
  detail-scaffold:
    compose: Scaffold
    codeName: FieldLedgerDetailScaffold
    regions: [TOP_APP_BAR, CONTENT, FEEDBACK_HOST]
    bottomNavigation: hidden
    states: [READY, LOADING, ERROR]
  task-scaffold:
    compose: Scaffold
    codeName: FieldLedgerTaskScaffold
    regions: [TASK_APP_BAR, CONTENT, BOTTOM_ACTION_DOCK, FEEDBACK_HOST]
    bottomNavigation: hidden
    states: [CLEAN, DIRTY, COMMITTING, ERROR]
  inspection-capture-scaffold:
    compose: Scaffold
    codeName: InspectionCaptureScaffold
    regions: [TOP_APP_BAR, MISSING_EVIDENCE_STRIP, ROOM_PROGRESS_STRIP, CONTENT, CAPTURE_ACTION_DOCK]
    bottomNavigation: hidden
    states: [READY, SAVING, SAVE_FAILED, RESTORED]
  camera-capture-scaffold:
    compose: Box
    codeName: CameraCaptureScaffold
    regions: [LIVE_PREVIEW, TOP_CONTROLS, OVERLAY_CONTROL, SHUTTER, REVIEW_BAR]
    edgeToEdge: true
    states: [OPENING, PREVIEW_READY, CAPTURING, REVIEW, COMMITTING, ERROR]
  modal-sheet:
    compose: ModalBottomSheet
    codeName: FieldLedgerModalSheet
    regions: [DRAG_HANDLE, HEADER, CONTENT, OPTIONAL_FOOTER]
    states: [OPENING, OPEN, COMMITTING, CLOSING]
  alert-dialog:
    compose: AlertDialog
    codeName: FieldLedgerAlertDialog
    regions: [TITLE, CONSEQUENCE, ACTIONS]
    dismissOnScrim: false
    states: [OPEN, CONFIRMING, ERROR, CLOSED]
  navigation-bar:
    compose: NavigationBar
    codeName: FieldLedgerNavigationBar
    destinations: [PROPERTIES, SCHEDULE, SETTINGS]
    height: 80px
    labelVisibility: always
    states: [INACTIVE, ACTIVE, DISABLED]
  navigation-destination:
    compose: NavigationBarItem
    codeName: FieldLedgerNavigationDestination
    iconSize: "{iconography.sizes.md}"
    minTarget: "{interaction.minTouchTarget}"
    states: [INACTIVE, ACTIVE, PRESSED, FOCUSED, DISABLED]
  top-app-bar:
    compose: TopAppBar
    codeName: FieldLedgerTopAppBar
    height: 64px
    actionsMax: 2
    states: [DEFAULT, SCROLLED]
  room-progress-strip:
    compose: LazyRow
    codeName: RoomProgressStrip
    itemGap: "{spacing.sm}"
    edgeControls: required
    states: [READY, SCROLLING, FOCUSED]
  room-progress-segment:
    compose: FilterChip
    codeName: RoomProgressSegment
    height: "{spacing.touch}"
    states: [INCOMPLETE, COMPLETE, CURRENT, BLOCKED]
  missing-evidence-strip:
    compose: Surface
    codeName: MissingEvidenceStrip
    backgroundColor: "{colors.tertiary}"
    textColor: "{colors.on-tertiary}"
    typography: "{typography.label-lg}"
    heightMin: "{spacing.touch}"
    states: [HIDDEN, VISIBLE, FOCUSED]
  button-primary:
    compose: Button
    codeName: FieldLedgerPrimaryButton
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    heightMin: "{spacing.action}"
    states: [ENABLED, PRESSED, FOCUSED, BUSY, DISABLED]
  button-secondary:
    compose: FilledTonalButton
    codeName: FieldLedgerSecondaryButton
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    heightMin: "{spacing.action}"
    states: [ENABLED, PRESSED, FOCUSED, BUSY, DISABLED]
  button-destructive:
    compose: Button
    codeName: FieldLedgerDestructiveButton
    backgroundColor: "{colors.error}"
    textColor: "{colors.on-error}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    heightMin: "{spacing.action}"
    states: [ENABLED, PRESSED, FOCUSED, BUSY, DISABLED]
  icon-button:
    compose: IconButton
    codeName: FieldLedgerIconButton
    iconSize: "{iconography.sizes.md}"
    targetSize: "{interaction.minTouchTarget}"
    variants: [STANDARD, TONAL, CAMERA]
    states: [ENABLED, PRESSED, FOCUSED, SELECTED, DISABLED]
  status-choice:
    compose: Surface selectableGroup
    codeName: StatusChoice
    variants: [OK, ATTENTION, CRITICAL, NOT_APPLICABLE]
    heightMin: "{spacing.action}"
    states: [UNSELECTED, SELECTED, PRESSED, FOCUSED, DISABLED]
  privacy-chip:
    compose: FilterChip
    codeName: PrivacyChip
    label: Contains tenant belongings
    backgroundColor: "{colors.privacy-container}"
    textColor: "{colors.on-privacy-container}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    states: ['OFF', 'ON', PRESSED, FOCUSED, DISABLED]
  evidence-rail:
    compose: custom merged-semantics Layout
    codeName: EvidenceRail
    width: 6px
    segmentGap: 2px
    segmentOrder: [STATUS, PHOTO, NOTE]
    segmentStates: [COMPLETE, MISSING_REQUIRED, BLOCKED, OPTIONAL, NOT_APPLICABLE]
    states: [READY, UPDATING]
  inspection-item-card:
    compose: Surface
    codeName: InspectionItemCard
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
    variants: [DEFAULT, ATTENTION, READ_ONLY]
    states: [COLLAPSED, EXPANDED, FOCUSED, SAVE_FAILED]
  property-summary-card:
    compose: Surface
    codeName: PropertySummaryCard
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
    variants: [START, CONTINUE, DUE, BLOCKED]
    states: [DEFAULT, PRESSED, FOCUSED]
  photo-evidence-tile:
    compose: Surface
    codeName: PhotoEvidenceTile
    aspectRatio: 1.3333
    rounded: "{rounded.md}"
    states: [EMPTY_OPTIONAL, EMPTY_REQUIRED, TEMPORARY, PRESENT, PRIVACY, ARCHIVED, FAILED]
  save-status:
    compose: Row liveRegion-polite
    codeName: SaveStateIndicator
    typography: "{typography.label-sm}"
    states: [CLEAN, DIRTY, SAVING, SAVED, FAILED, RECOVERED]
  feedback-banner:
    compose: Surface liveRegion-polite
    codeName: FeedbackBanner
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    variants: [INFO, SUCCESS, WARNING, ERROR, BLOCKING]
    states: [VISIBLE, ACTION_BUSY, DISMISSED]
  compliance-block:
    compose: Surface liveRegion-polite
    codeName: ComplianceBlock
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    states: [BLOCKED, CORRECTING, CLEARED]
  input-field:
    compose: OutlinedTextField
    codeName: FieldLedgerInputField
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    heightMin: "{spacing.action}"
    states: [EMPTY, FOCUSED, FILLED, ERROR, DISABLED]
  phrase-sheet:
    compose: ModalBottomSheet
    codeName: PhraseSheet
    rounded: "{rounded.xl}"
    states: [OPENING, OPEN, FILTERED, EMPTY, CLOSING]
  confirmation-dialog:
    compose: AlertDialog
    codeName: ConfirmationDialog
    variants: [FINALIZE, DISCARD_TEMP_PHOTO, CLEAR_CONTACT, REMOVE_LOCAL_MEDIA]
    states: [OPEN, CONFIRMING, ERROR, CLOSED]
  undo-snackbar:
    compose: Snackbar
    codeName: UndoSnackbar
    action: UNDO
    timeoutMs: 5000
    states: [VISIBLE, ACTION_BUSY, DISMISSED]
  bottom-action-dock:
    compose: Surface
    codeName: FieldLedgerBottomActionDock
    backgroundColor: "{colors.secondary}"
    textColor: "{colors.on-secondary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.none}"
    padding: "{spacing.lg}"
    states: [NEXT_ROOM, REVIEW_MISSING, FINALIZE_READY, BUSY]
  camera-control:
    compose: IconButton
    codeName: CameraControl
    backgroundColor: "{interaction.cameraScrim}"
    textColor: "{interaction.onCameraScrim}"
    targetSize: "{interaction.minTouchTarget}"
    states: ['OFF', 'ON', PRESSED, FOCUSED, DISABLED]
  camera-shutter:
    compose: custom Button
    codeName: CameraShutter
    backgroundColor: "{colors.on-primary}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
    size: 72px
    states: [READY, PRESSED, CAPTURING, DISABLED]
  camera-review-bar:
    compose: Surface
    codeName: CameraReviewBar
    backgroundColor: "{interaction.cameraScrim}"
    textColor: "{interaction.onCameraScrim}"
    actions: [RETAKE, PRIVACY, USE_PHOTO]
    states: [READY, COMMITTING, ERROR]
  privacy-action:
    compose: FilledTonalButton
    codeName: PrivacyAction
    backgroundColor: "{colors.privacy}"
    textColor: "{colors.on-privacy}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    states: ['OFF', 'ON', PRESSED, FOCUSED, DISABLED]
  divider:
    compose: HorizontalDivider
    codeName: FieldLedgerDivider
    backgroundColor: "{colors.outline-variant}"
    size: 1px
    states: [VISIBLE]
  focus-indicator:
    compose: Modifier.drawBehind
    codeName: FieldLedgerFocusIndicator
    backgroundColor: "{colors.primary}"
    width: "{interaction.focusRingWidth}"
    offset: 2px
    states: [HIDDEN, VISIBLE]
  camera-overlay-control:
    compose: Row
    codeName: CameraOverlayControl
    controls: [Switch, Slider]
    backgroundColor: "{interaction.cameraScrim}"
    textColor: "{interaction.onCameraScrim}"
    states: [UNAVAILABLE, 'OFF', 'ON', ADJUSTING, DISABLED]
  section-header:
    compose: Row
    codeName: FieldLedgerSectionHeader
    typography: "{typography.title-md}"
    variants: [STANDARD, DATE, DANGER]
    states: [DEFAULT, ACTION_AVAILABLE, COLLAPSED, EXPANDED]
  result-list-row:
    compose: ListItem
    codeName: FieldLedgerResultRow
    variants: [PROPERTY, HISTORY, SCHEDULE, NOTICE]
    heightMin: "{spacing.action}"
    states: [DEFAULT, PRESSED, FOCUSED, SELECTED, UNAVAILABLE]
  settings-row:
    compose: ListItem
    codeName: FieldLedgerSettingsRow
    variants: [NAVIGATION, VALUE, TOGGLE, DANGER]
    heightMin: "{spacing.action}"
    states: [DEFAULT, PRESSED, FOCUSED, BUSY, DISABLED]
  metadata-row:
    compose: Row
    codeName: FieldLedgerMetadataRow
    typography: "{typography.body-sm}"
    variants: [ICON_TEXT, LABEL_VALUE, SOURCE_TIME]
    states: [DEFAULT, WARNING, ERROR]
  overflow-menu:
    compose: DropdownMenu
    codeName: FieldLedgerOverflowMenu
    states: [CLOSED, OPEN, ITEM_FOCUSED, ACTION_BUSY]
  tooltip:
    compose: PlainTooltip
    codeName: FieldLedgerTooltip
    states: [HIDDEN, VISIBLE]
  state-badge:
    compose: Badge
    codeName: FieldLedgerStateBadge
    variants: [COUNT, DOT, STATUS, SOURCE]
    states: [NEUTRAL, DUE, ATTENTION, BLOCKED, PRIVATE, VERIFIED]
  search-field:
    compose: SearchBar
    codeName: FieldLedgerSearchField
    heightMin: "{spacing.action}"
    states: [COLLAPSED, FOCUSED, QUERY, NO_RESULTS, DISABLED]
  filter-chip-group:
    compose: LazyRow selectableGroup
    codeName: FieldLedgerFilterChipGroup
    states: [READY, FILTERED, FOCUSED, DISABLED]
  switch-row:
    compose: ListItem + Switch
    codeName: FieldLedgerSwitchRow
    heightMin: "{spacing.action}"
    states: ['OFF', 'ON', PRESSED, FOCUSED, DISABLED]
  checkbox-row:
    compose: Row + Checkbox
    codeName: FieldLedgerCheckboxRow
    heightMin: "{spacing.touch}"
    states: [UNCHECKED, CHECKED, INDETERMINATE, FOCUSED, DISABLED]
  radio-group:
    compose: Column selectableGroup
    codeName: FieldLedgerRadioGroup
    states: [UNSELECTED, SELECTED, ERROR, DISABLED]
  segmented-control:
    compose: SingleChoiceSegmentedButtonRow
    codeName: FieldLedgerSegmentedControl
    states: [UNSELECTED, SELECTED, FOCUSED, DISABLED]
  choice-field:
    compose: ExposedDropdownMenuBox
    codeName: FieldLedgerChoiceField
    heightMin: "{spacing.action}"
    states: [EMPTY, OPEN, SELECTED, ERROR, DISABLED]
  date-time-field:
    compose: OutlinedTextField readOnly
    codeName: FieldLedgerDateTimeField
    heightMin: "{spacing.action}"
    states: [EMPTY, SELECTED, FOCUSED, ERROR, DISABLED]
  secure-input-field:
    compose: OutlinedTextField
    codeName: FieldLedgerSecureInputField
    variants: [PASSPHRASE, API_KEY]
    heightMin: "{spacing.action}"
    states: [EMPTY, HIDDEN, REVEALED, ERROR, VERIFIED, DISABLED]
  confirmation-input:
    compose: OutlinedTextField
    codeName: FieldLedgerConfirmationInput
    variants: [RESTORE, ERASE, CLEAR]
    heightMin: "{spacing.action}"
    states: [EMPTY, MISMATCH, MATCHED, DISABLED]
  slider-field:
    compose: Column + Slider
    codeName: FieldLedgerSliderField
    states: [READY, ADJUSTING, FOCUSED, DISABLED]
  empty-state-panel:
    compose: Column
    codeName: FieldLedgerEmptyStatePanel
    variants: [FIRST_RUN, NO_CONTENT, NO_RESULTS, NO_HISTORY]
    states: [VISIBLE, ACTION_BUSY]
  loading-indicator:
    compose: CircularProgressIndicator
    codeName: FieldLedgerLoadingIndicator
    variants: [INDETERMINATE, DETERMINATE, INLINE]
    states: [HIDDEN, DELAYED, VISIBLE, COMPLETE]
  task-progress-card:
    compose: Surface liveRegion-polite
    codeName: FieldLedgerTaskProgressCard
    variants: [BACKUP, RESTORE, EXPORT, ERASE, MEDIA_RECOVERY]
    rounded: "{rounded.lg}"
    states: [PREPARING, RUNNING, VERIFYING, SUCCEEDED, FAILED, CANCELLED]
  validation-summary:
    compose: Surface liveRegion-polite
    codeName: FieldLedgerValidationSummary
    states: [HIDDEN, INVALID, FOCUSED, CLEARED]
  recovery-panel:
    compose: Surface liveRegion-polite
    codeName: FieldLedgerRecoveryPanel
    variants: [PERMISSION, PROVIDER, LOW_STORAGE, INTEGRITY, ARCHIVED_MEDIA, RESTORED_SESSION]
    rounded: "{rounded.md}"
    states: [VISIBLE, ACTION_BUSY, RESOLVED]
  verification-receipt:
    compose: Surface
    codeName: VerificationReceiptCard
    variants: [BACKUP, EXPORT, RESTORE, INTEGRITY]
    rounded: "{rounded.lg}"
    states: [VERIFIED, STALE, FAILED, UNAVAILABLE]
  history-evidence-strip:
    compose: LazyRow
    codeName: HistoryEvidenceStrip
    states: [EMPTY, READY, SCROLLING, BASELINE_SELECTED, PREVIOUS_SELECTED, ARCHIVED]
  review-gap-row:
    compose: ListItem
    codeName: ReviewGapRow
    heightMin: "{spacing.action}"
    states: [MISSING_STATUS, MISSING_PHOTO, MISSING_NOTE, BLOCKED, FIXING]
  summary-stat:
    compose: Column
    codeName: FieldLedgerSummaryStat
    typography: "{typography.data-lg}"
    states: [NEUTRAL, COMPLETE, ATTENTION, BLOCKED]
  evidence-grid:
    compose: LazyVerticalGrid
    codeName: EvidenceGrid
    states: [EMPTY, READY, SELECTION, ARCHIVED, LOADING]
  media-source-sheet:
    compose: ModalBottomSheet
    codeName: MediaSourceSheet
    variants: [SINGLE_PHOTO, BULK_PHOTO, AUDIO]
    states: [OPEN, CAMERA_AVAILABLE, IMPORT_ONLY, COMMITTING, ERROR]
  media-assignment-row:
    compose: ListItem
    codeName: MediaAssignmentRow
    states: [UNASSIGNED, ASSIGNED, DUPLICATE, INVALID, SAVING]
  audio-evidence-control:
    compose: Surface
    codeName: AudioEvidenceControl
    states: [IDLE, LISTENING, PROCESSING_ON_DEVICE, SAVED, PLAYING, FAILED, UNAVAILABLE, READ_ONLY]
  media-preview:
    compose: Dialog
    codeName: EvidenceMediaPreview
    variants: [PHOTO, AUDIO]
    states: [LOADING, READY, PRIVACY, ARCHIVED, ERROR]
  backup-health-card:
    compose: Surface
    codeName: BackupHealthCard
    rounded: "{rounded.lg}"
    states: [NOT_CONFIGURED, READY, RUNNING, VERIFIED, STALE, FAILED]
  destination-row:
    compose: ListItem
    codeName: BackupDestinationRow
    states: [NOT_SELECTED, AVAILABLE, PROVIDER_OFFLINE, ACCESS_REVOKED, LOW_SPACE]
  task-stepper:
    compose: Column
    codeName: FieldLedgerTaskStepper
    variants: [BACKUP, RESTORE, ERASE]
    states: [UPCOMING, CURRENT, COMPLETE, FAILED]
  preflight-summary:
    compose: Surface
    codeName: FieldLedgerPreflightSummary
    variants: [RESTORE, ERASE, MEDIA_CLEANUP, SHARE]
    rounded: "{rounded.lg}"
    states: [CHECKING, READY, BLOCKED, STALE]
  disclosure-list:
    compose: Column
    codeName: FieldLedgerDisclosureList
    variants: [INCLUDED, EXCLUDED, IMPACT, RETAINED]
    states: [COLLAPSED, EXPANDED]
  health-issue-row:
    compose: ListItem
    codeName: HealthIssueRow
    states: [BACKUP_STALE, BACKUP_FAILED, INTEGRITY_FAILED, RESTORE_ROLLED_BACK, PREVIOUS_CRASH, STARTUP_SLOW]
  share-boundary-callout:
    compose: Surface
    codeName: ShareBoundaryCallout
    variants: [PDF, NOTICE, DIAGNOSTIC]
    states: [VISIBLE, ACKNOWLEDGED]
  notice-delivery-row:
    compose: ListItem
    codeName: NoticeDeliveryRow
    states: [DRAFT, VALID, BLOCKED, COPIED, RECORDED]
  compliance-check-row:
    compose: ListItem
    codeName: ComplianceCheckRow
    states: [NOT_CHECKED, PASS, FAIL, NOT_APPLICABLE, CORRECTING]
  remediation-suggestion-card:
    compose: Surface
    codeName: RemediationSuggestionCard
    variants: [ON_DEVICE, REMOTE]
    states: [READY, GENERATING, ACCEPTED, REJECTED, FAILED, OFFLINE]
  report-action-sheet:
    compose: ModalBottomSheet
    codeName: ReportActionSheet
    actions: [OPEN_PDF, SHARE, EXPORT_ANOTHER_QUALITY]
    states: [OPEN, PREPARING, HANDING_OFF, ERROR, CLOSED]
---

# MyInspection Field Ledger

## Overview

MyInspection is a **field instrument, not an office dashboard**. Its primary user is a New Zealand landlord or property operator walking through a home with one hand occupied, variable light, intermittent connectivity, and limited time. The interface should feel calm, exact, and trustworthy enough to support evidence that may later be printed or reviewed in a dispute.

The visual direction is **Field Ledger**: the clarity of a paper inspection sheet combined with the immediacy of a camera viewfinder. Cool mineral surfaces, deep fern green, measured amber, compact metadata, and firm rectangular controls make the app feel durable without becoming industrial or severe.

The signature device is the **evidence rail**. Inspection item cards carry a narrow leading rail whose segments encode status, photo, and note completeness. The same visual grammar appears in room progress and the camera capture sequence. It is functional navigation, never decoration, and must always pair color with an icon or label.

This document describes the target production UI for `T2-CAPTURE-UI` and later UI cards. The current `skeleton` package is a disposable end-to-end proof and is not a visual precedent.

## Colors

The palette is light-first for daylight legibility. Large fields of pure white are avoided; the cool stone background reduces glare while keeping dark text crisp.

- **Primary — fern ink (`#0B5D52`):** main actions, completed progress, selected controls, and camera alignment guidance. Use it sparingly enough that it still signals commitment.
- **Secondary — survey slate (`#3E5B67`):** navigation and structural controls. It should feel quieter than the primary action.
- **Tertiary — site amber (`#8B5C00`):** incomplete evidence, attention states, and the persistent missing-items strip. Amber means “resolve before completion,” not generic emphasis.
- **Error — ledger red (`#B3261E`):** legal/compliance blocks, destructive actions, and significant defects. Never use it for ordinary validation hints.
- **Privacy — archive violet (`#60458E`):** tenant-property privacy flags and report-exclusion controls. Keeping privacy distinct from defects prevents semantic confusion.
- **Surfaces:** use `surface` for the screen, `surface-container-low` for active item cards, and darker container steps for grouping and pressed states. Borders use `outline-variant`; `outline` is reserved for focus and high-contrast separation.

Status must never rely on color alone. Pair every status with a label and stable symbol: check for OK, exclamation for attention, cross/octagon for blocked, dash for not applicable, and shield for privacy.

## Typography

Use Android system families only: `sans-serif` for readable prose and controls, and `sans-serif-condensed` for room labels, counts, timestamps, and evidence metadata. This avoids a bundled-font dependency while giving field data a compact, instrument-like voice.

- **Headlines:** bold, sentence case, and brief. A room name or next action should be understood at a glance.
- **Body:** never below 16px for primary instructions. Use 14px only for supporting metadata that is not needed to complete the current step.
- **Labels:** buttons remain sentence case. Condensed labels may use modest tracking, but do not use all caps for sentences.
- **Data:** counts such as `3 photos` or `8 of 12 complete` use `data-lg` when they are the screen’s decision-driving fact.
- **Compose mapping:** treat token `px` values as density-independent `sp` for text and `dp` for spacing, size, and radius. Respect the user’s font scale; do not clamp text or hide overflow that contains a requirement, date, or status.

## Layout

Design portrait-first for a compact Android handset. Tablet and landscape optimisation are outside the current UI card, but content must remain structurally responsive rather than depending on fixed screen coordinates.

- Use a `16dp` screen gutter and a strict `4dp` base rhythm.
- Keep primary controls at least `48dp` high; primary actions and status choices are `56dp` high.
- Put the current room, missing-evidence strip, and room progress near the top. Put the next physical action in a bottom dock within thumb reach.
- Use one dominant vertical list. Horizontal scrolling is reserved for room navigation and chronological history, where direction has meaning.
- Item cards reveal detail progressively: name and current status first; note, phrase, voice, photo, and history controls only when relevant.
- Leave enough bottom inset for system navigation and enough space above the action dock that the final card is not obscured.

Core capture shape:

```text
┌────────────────────────────┐
│ Kitchen          8/12 done │
│ 2 photos · 1 note missing  │  ← persistent amber strip when incomplete
├────────────────────────────┤
│ ▌Bench top                 │
│ ▌ Previous: OK · 3 mo ago │  ← evidence rail + optional history
│ ▌ [ OK ] [ Needs attention]│
│ ▌ Photo · Phrase · Voice   │
├────────────────────────────┤
│ ▌Sink and taps             │
│ ▌ ...                      │
└────────────────────────────┘
│        Next room →          │  ← bottom action dock
└────────────────────────────┘
```

The camera screen is the exception to the surface layout: the live preview fills the screen, with only capture-critical controls over it. Room panoramas may default to the history overlay; item photos do not. The overlay control, privacy flag, and shutter stay in the lower reach zone.

## Elevation & Depth

Use **tonal layers and rails**, not floating-card shadows, to express hierarchy. The app will often be used in bright conditions where subtle shadows disappear.

- Screen background → grouped room surface → active item card is the normal three-layer stack.
- Cards use a 1px `outline-variant` edge only when adjacent tones do not separate clearly.
- Dialogs and bottom sheets may use standard Material 3 elevation because they represent a true modal layer.
- Pressed state is a darker tonal container plus immediate haptic feedback where Android conventions allow it. Do not animate cards upward.

## Shapes

Shapes are **sturdy and measured**: `8dp` for controls, `12dp` for cards, and `16dp` only for large sheets. Avoid pill-shaped containers except compact tags such as privacy or source labels.

The evidence rail and progress segments are square-ended. This deliberate contrast with softly rounded cards makes completion state read like a checklist rather than decoration. Camera shutters remain circular because that form is a learned platform convention.

## Components

### App bars and progress

The top app bar names the current property or room and exposes only navigation and one overflow menu. The missing-evidence strip sits immediately below it whenever work remains. Its copy names the exact next gap, for example `2 photos and 1 note still needed`; tapping moves focus to the next missing item.

Room navigation is a horizontally scrollable row of labelled progress segments. Each room shows name plus completion count. Do not reduce rooms to unlabeled dots.

### Inspection item card

An item card is the central component. Its default state shows the item name, evidence rail, prior status if available, and two equal-width primary choices: `OK` and `Needs attention`. `Needs attention` reveals the allowed detailed statuses, suggested phrases, note, and required-photo affordance. `Not applicable` and `Not present at this property` remain in overflow because they are less frequent and have different persistence semantics.

The evidence rail has three semantic segments in a stable order: status, photo, note. A complete segment uses primary; missing-required evidence uses amber; a compliance-blocked segment uses red; optional/irrelevant evidence uses neutral with a dash. Add a short accessible description such as `Status complete, photo missing, note complete`.

### Buttons and selection controls

Use full-width or paired large buttons, never dropdowns, for condition/status choices. Only one visually primary action appears in a decision region. Secondary actions use a filled tonal treatment; tertiary actions use text plus icon without adding another card.

Button labels describe the result: `Start inspection`, `Take room photo`, `Mark remaining items OK`, `Finish inspection`, and `Clear contact info`. Keep the same verb in confirmation and success feedback.

### Notes, phrases, and voice

The input order is phrase first, voice second, keyboard last. Suggested phrases open in a bottom sheet grouped by purpose and filtered by the current item and status. Inserting a phrase is immediate but reversible. The microphone control states whether on-device recognition is available; when unavailable, hide it and keep keyboard entry usable.

Voice recording and transcription states are explicit: `Listening`, `Processing on device`, `Saved with this item`, or a specific recovery action. Never represent recording only with a pulsing color.

### Photos and camera

Photo slots use a 4:3 thumbnail, source label (`Camera` or `Imported`), capture time, and privacy state. Required photos show an amber empty slot with the exact reason. Do not use a generic image placeholder when the user needs to know whether a room panorama or defect close-up is missing.

The camera view keeps the shutter as the largest control. Ghost overlay is approximately 30% opacity with a labelled slider and `Overlay off/on`; it must never be baked into the saved photo. After capture, show rotation-correct preview, retake, privacy flag, and confirm before leaving the item.

### Compliance and destructive actions

A compliance failure is an in-context blocking panel, not a transient toast. State the violated rule, the entered value, and the earliest valid correction where calculable. The primary action returns to the exact field that must change. Compliance blocks cannot be dismissed or disabled.

Irreversible contact clearing uses the established type-to-confirm dialog. The dialog distinguishes the contact fields that will be cleared from inspection records, photos, reports, and hashes that remain.

### Empty, loading, and offline states

Empty states provide a next action: `No properties yet` pairs with `Add property`; an item with no history says `No earlier inspection for this item` without inventing sample data. Local reads should not show network-style spinners. Save feedback is quiet (`Saved on this device`) and only becomes prominent when a write fails.

## Do's and Don'ts

- Do optimise every capture screen for one hand, bright light, and interrupted attention.
- Do use the evidence rail consistently for status, photo, and note completion.
- Do pair every status color with a label and icon; preserve at least WCAG AA contrast.
- Do keep legal, privacy, capture, and defect meanings visually distinct.
- Do show the exact missing evidence and navigate directly to it.
- Do use plain English UI terms even when reports contain parallel English and Chinese.
- Do preserve system back, font scaling, screen-reader order, and minimum `48dp` targets.
- Don't treat the temporary walking skeleton as a component or spacing reference.
- Don't use dropdowns for inspection status or hide primary capture actions in overflow.
- Don't turn every block into a rounded card; grouping should come from spacing and tonal layers first.
- Don't use gradients, glass effects, decorative illustrations, or soft floating shadows in the capture flow.
- Don't use red for ordinary incompleteness, amber for destructive actions, or privacy violet for defects.
- Don't auto-advance after a destructive choice or a newly recorded defect; let the user verify evidence first.
- Don't imply cloud sync, automatic notice sending, diagnosis, cost estimates, or any other excluded capability.
