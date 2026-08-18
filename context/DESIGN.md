---
version: alpha
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
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    height: "{spacing.action}"
  button-secondary:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    height: "{spacing.action}"
  button-destructive:
    backgroundColor: "{colors.error}"
    textColor: "{colors.on-error}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    height: "{spacing.action}"
  status-ok:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "{spacing.touch}"
  status-attention:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "{spacing.touch}"
  status-critical:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "{spacing.touch}"
  status-not-applicable:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "{spacing.touch}"
  status-privacy:
    backgroundColor: "{colors.privacy-container}"
    textColor: "{colors.on-privacy-container}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "{spacing.sm}"
  missing-strip:
    backgroundColor: "{colors.tertiary}"
    textColor: "{colors.on-tertiary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.none}"
    padding: "{spacing.md}"
    height: "{spacing.touch}"
  compliance-block:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
  card-item:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  card-room-summary:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  input-field:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "{spacing.action}"
  bottom-action-dock:
    backgroundColor: "{colors.secondary}"
    textColor: "{colors.on-secondary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.none}"
    padding: "{spacing.lg}"
  camera-shutter:
    backgroundColor: "{colors.on-primary}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
    size: 72px
  privacy-action:
    backgroundColor: "{colors.privacy}"
    textColor: "{colors.on-privacy}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
  divider:
    backgroundColor: "{colors.outline-variant}"
    size: 1px
  focus-indicator:
    backgroundColor: "{colors.outline}"
    size: 3px
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
