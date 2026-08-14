# Property Inspect (propertyinspect.com) — competitive UX research（Opus 5 深挖 · 2026-08-14）

All findings WebFetch-verified with URLs.

## A. Brand structure
Trading name of **Radweb Ltd** (Portsmouth, UK), same company/codebase as InventoryBase (both iOS apps v6.2.10, same publisher). Split: InventoryBase = UK letting agents; Property Inspect = international/commercial/enterprise/US housing compliance. Founder-owned, no acquisition; 2025 Reapit partnership (PI + PayProp across USA/Canada/South Africa).

## B. Verticals
- Commercial (CBRE, JLL, Cushman & Wakefield, Savills customers): snagging, fire risk assessments with risk matrices, asset condition surveys, PPM, reinstatement cost assessments, dilapidations evidence.
- Facility management: "standardised FM inspections across 18 markets. What used to take three days now takes three hours."
- Housing: HHSRS 29 hazard categories, Decent Homes, Awaab's Law; US public housing (City of Baltimore, All Chicago, Arlington County); Chicago claims onboarding "from 180+ days to under 30 days" via remote self-assessments.

## C. NSPIRE / HUD (US compliance moat)
Official HUD reports (unit threshold, inspection score, property grade, pass/fail). NSPIRE-V voucher deficiency capture = Yes/No/Not applicable/blank against predefined deficiencies + HCV severity/correction-timeframe columns. "Lite" readiness inspections reorder criteria "life-threatening and severe items first". Dynamic checklists adapting to responses.

## D. InspectAI
Claims 50-70% admin reduction; report writing 2-3h → 45-60min. Shipped: Executive Summaries; AI Smart Fill (photos → descriptions and conditions); Section Summaries; Item Descriptions from photo analysis; Photo Captions. Consumable credits. Cannot process Keys/Meter/Alarms/Manual sections. **No native live speech-to-text**: field voice = OS keyboard mic or recorded audio to a **human-typist transcription service at 35p/minute** (foot-pedal setup docs prove human), same-day to 3-day turnaround.

## E. US pricing
Solo $49/mo (1 user, 100 properties) / Standard $97 / Pro $275 (white-label, MRI/Yardi, vendor portal) / Enterprise custom (SSO, SOC 2). Add-ons: property overages $0.25-0.45, transcription per-minute, SD/HD video premium add-on, Advanced Template add-on for custom answer sets, AI credits.

## F. Reviews / app stores
- **Google Play 2.2 stars, 41 reviews, 10K+ installs.** Recovered verbatim complaint: "Report upload failed due to validation error - but then no mention of what or where the error is?" Also: forced N/A entry for empty fields, login failures.
- Apple 5.0 but only 5 ratings (praise: "We use it for HUD compliance"). Platform split = the finding: **Android (the field device) is the weak build.**
- Review sites near-zero: one 2022 Capterra review; no G2/Trustpilot. Sibling InventoryBase: iOS 3.8, Trustpilot 2.8, complaints of cluttered UI, defections to InventoryHive/GoCanvas. Most-praised attribute: UK human support.

## H. Core UX (from their docs)
1. Hierarchy: Property > Inspection > Section/Block > Item. **Eight block types**: DETAILED (item+description+condition), NOTES, SIMPLIFIED (multi-select toggles, default "Clean / Undamaged / Working", max 6 labels), CHECKLIST (Yes/No/N-A; N-A hides from final report), RATINGS (3 or 5 stars glossed Good/Fair/Poor), METERS, KEYS, MANUALS. No separate condition-vs-cleanliness graded scales.
2. **Dictionary**: contextual autocomplete scoped room>item>condition with typed shortcuts — verbatim example "'FWT' for 'Fair wear and tear'". That shortcut is the ONLY wear-and-tear feature: no depreciation tables, only a 4-way liability tag (Landlord / Tenant / Investigate / N-A).
3. Offline: manual pre-download ("Fetch"), sync removes report from device on success, sync can be blocked by back-office state. Docs teach a manual integrity check (signatures sync last).
4. Photos: per-room primary + per-item; **not mandatory on defects by default** (only via conditional-logic templates). Annotation web-only (no freehand/text).
5. Comparison: previous-inspection photos = a **blue dot** in-app; "Retaking the photos in the app is required for the comparison to appear in the final report" (silent failure mode); output side-by-side Move-In vs Move-Out columns with orange change flags. **No ghost-image alignment aid exists.**
6. Output: interactive web report + PDF; photos inline 1-4 per row; condensed variants "Changes Only" / "Actions Only"; e-signatures 0-10 lines; tenant comments + photo uploads; share-open tracking; workflow PENDING>ASSIGNED>ACTIVE>COMPLETE>CLOSED.

## Could NOT confirm
Play review corpus text; real sample reports (all 404); multi-language (iOS listing English-only); ghost alignment/floor plans/LiDAR (absent); wear-tear logic (absent); NSPIRE scoring formulas.

## Three actionable gaps for our product
1. **Ghost-overlay re-shoot with auto photo pairing** beats their best comparison feature's silent failure mode ("retake required or no comparison").
2. **Native on-device streaming speech-to-text** obsoletes their 35p/minute typist pipeline.
3. **Android field reliability + clear validation errors** attacks the 2.2-star soft underbelly.
