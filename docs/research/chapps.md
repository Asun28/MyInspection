# Chapps (chapps.com) — competitive UX research（Opus 5 深挖 · 2026-08-14）

Evidence base: chapps.com, rentalinspectionapp.com, support.chapps.com, + 3 sample PDFs text-extracted (move-in #67525, move-out #67878, job list, 63-page Belgian Dutch "plaatsbeschrijving").

**Context:** Chapps NV is Belgian; Rental Inspector is contractually blocked for Belgian/Luxembourg estate agents (CheckNet exclusivity); only social housing, care organisations, public bodies and owner-operators may use it there.

## 1. Rating scales (verbatim)

Element condition "Evaluation" — glossary printed inside every report (page 2):

| EN | NL |
|---|---|
| New : the element is new | Nieuw |
| Very good : very good condition | Zeer goed |
| Good : good condition, no visible damage | Goed |
| Mediocre : signs of usage or wear / moderate condition | Matig |
| Bad : poor condition | Slecht |

Five values; "Mediocre" is explicitly the wear bucket.

Action/follow-up vocabulary: to repair / to replace / to settle ("damages must be paid for or settled otherwise") / on job list / suggestion / for the record ("needs no immediate attention, but is noted to be complete").

Condition vs cleanliness separated across three carriers:
- Room-level `General Condition`: `Orderliness` (Ok, Great disorder), `Cleanliness` (Ok, Not Maintained, Dirty), `Paint` (New, Ok, To retouch, To renew).
- Room-level `Structural Condition`: `Ventilation` (Sufficient), `Electrical Installation` (Compliant), `Humidity` (None), `Woodcondition` (Ok, Mold).
- Element-level: single `Evaluation` (5-value). Elements never receive cleanliness scores; dirtiness on an element = an **Issue** of nature Cleanliness.

Issue fields (0..n child of element): nature = Damage/Cleanliness/Markings; action = To repair/To replace/To settle; Urgency = Suggestion/On job list; Cost; **Responsible = Tenant/Landlord/Undetermined**; Comments free text **plus structured damage counters**: `Scratch(es): 2`, `Spot(s): 1`, `Smudge(s): 2`, `Dent(s): 3`, `Hole(s): 1`, flags `Fracture/Cracked`, `Not Functioning`, `Missing`, `Dirty`, `Not working properly`.

Functioning is a four-state flag: checked-ok / checked-not-ok / not checked / check not possible (+ NL "further evaluation not needed").

Wear-and-tear/depreciation: shallow. Move-out prints arithmetic inline: `Cost £40.00 (£50.00 - 20.00% depreciation)`. No published lifespan/depreciation table found; fair wear and tear lives in legal prose only.

## 2. Check-in vs check-out comparison

**Most important finding: NO side-by-side columns, no per-item before/after table.** The check-out report is one merged element tree; carried-over issues re-print in place with **the first-recorded date appended to the "Issue" heading** (`Issue 06/11/2018`); new issues print bare. Carried-over issues get Cost and Responsible stripped at check-out so only new damage is chargeable. **The date stamp is the entire delta mechanism** — survives N interim inspections.

The one true before/after column is **meters**: move-out adds `Previous Reading` printed as `600 (11/8/2018)`.

Tenant cost summary = a filter on `Responsible = Tenant` (flat table Issue|Cost with breadcrumb paths, total). Separate **job-list PDF grouped by contractor** (per-contractor subtotals). Photos are **inline beside each element, no appendix**; photo-only PDF on demand.

**No ghost image, no photo overlay, no side-by-side previous-condition view anywhere** — well-evidenced absence; arguably their biggest hole.

## 3. Hierarchy and observation model

"Checklists consist of rooms and floors. Rooms consist of element groups and elements. Elements consist of attributes."

Realised tree: Report → Keys / Meters / Interior → Floor → Room (General+Structural Condition) → **four fixed element groups** (`Basic` / `Specifics` / `Conformity` / `Appliances`) → elements (Floor/Ceiling/Wall/Door/Window/Heating) → Characteristics, sub-components (NL), Evaluation + Functioning, Issues (each with own photos/cost/responsible) → Building's Envelope / Technical Installations / Tenant cost summary / Agreement & Signatures.

`Conformity` reliably holds regulated items (smoke detector, sockets, lighting). NL wall-numbering convention: street-side wall = "muur 1", then clockwise.

Photos: up to 6 per room, 6 per element, 6 per issue. Annotation is Pro-tier. v4.0 added optional native-camera "Advanced Photo Mode" + volume-button shutter.

Speed features + key safety rule: tap-to-select predefined characteristics; "Set as default" (define one door, apply to all); duplicate rooms/elements, copy between floors. Stated three times: **attributes copy, assessment never does** ("features are taken over but not the pictures, evaluation, operation or any fixes").

Offline: full offline capture, auto-sync. **Voice/dictation, 360, video: no evidence. No mandatory-photo enforcement documented.**

Retention as policy: photos auto-deleted after 24 months; PDF reports after 84 months; export €750 excl. VAT.

## 4. Multi-language

Rental Inspector iOS: EN/NL/FR/DE/ES. Report legal text is **localised per region, not merely translated** (NL cites Burgerlijk Wetboek art. 1730, 10-day remark window; EN generic 15-day). Signatures: on-device (Tenant #1/#2, Landlord, Executor + Executed on); post-processed reports cannot be re-signed in-app.

## 5. Pricing
Pro: €260/user/year + €7.50/report credit (volume-tiered to €3.60); credits die with subscription. Premium: quote-only. Building Inspector: different model (€26/mo/user + €10/mo/building, no credits).

## 6. Reviews
Very thin: Capterra 5.0/5 from 2 reviews (2016); Building Inspector US App Store 1.0/5 from 2 ratings; institutional testimonials (City of Antwerp, University of Ottawa, French Army).

## 7. Worth stealing (ranked, as delivered)
1. Four fixed element groups per room (compliance items get a permanent home).
2. Issue as first-class 0..n child with orthogonal axes (nature × action × urgency), own photos/cost/responsible.
3. Structured damage counters (quantified severity without prose).
4. `Responsible: Tenant/Landlord/Undetermined` at capture time → cost summary is a pure filter.
5. **Attributes copy, assessment never does** — adopt verbatim as a safety default.
6. Date-stamp carried-over issues instead of comparison columns (pair it with the previous-photo view they lack).
7. `Previous Reading` only on meters.
8. Meters module (EAN, day/night split, photo, location path). 9. Keys module. 10. Functioning as four states (incl. "check not possible"). 11. Documents section (EPC, boiler records). 12. Job list grouped by contractor. 13. **Clause library per language/region + rating glossary printed inside every report as a trust device.** 14. `Circumstances` block (occupied/furnished, who was present). 15. Building Inspector: three renderings of one inspection (Full / Compact=negatives only / Issue=problems only). 18. Retention as a product decision.

## Could NOT find
Ghost overlay/side-by-side previous-condition view (absent); voice/dictation; 360/video; mandatory-photo enforcement; depreciation mechanism; floor plans in Rental Inspector; Google Play review corpus.
