# Project Brief — MyInspection

> **What this is.** A structured *product brief*: what you're building and why, in the user's terms.
> It is the **upstream input** to the planning harness — richer than a one-line idea, lighter than a full plan.
>
> **How to use.**
> 1. Copy this file (e.g. to `_local/<product>-brief.md`; `_local/` is gitignored) and fill every `‹…›`.
> 2. Hand-expand it into `_local/PLAN.md` via `docs/PLAN-TEMPLATE.md`.
> 3. Keep this brief **product-focused** (what & why, in user language). The *how* — tech, contracts, task cards,
>    machine-checkable acceptance — belongs in the PLAN, not here.
>
> **Placeholders.** `‹…›` = fill in per product. `MyInspection` / `MyInspection` are scaffold tokens
> (`init-scaffold.ps1` substitutes them downstream); overwrite directly if you're using this brief standalone.
> Delete these guidance blockquotes once filled.

## Overview
‹2–4 sentences: what the product is, what it combines or enables, and its core value proposition.›
‹1–2 sentences: who it's for and the experience/feeling it targets.›

## Target Users
- ‹persona 1 — who they are, what they're trying to do, why existing tools fall short›
- ‹persona 2 — …›

## Goals & Non-Goals
- **In scope (this version):** ‹the concrete capabilities this milestone delivers›
- **Out of scope / deferred:** ‹explicitly list what you are NOT doing this version — each with a one-line reason (this is your main defense against scope creep)›
- **Success criteria:** ‹how you'll know this version is "done", in observable terms (these become machine-checkable acceptance in the PLAN)›

## Constraints
- **Platform / runtime:** ‹web / desktop / CLI / mobile / …›
- **Tech preferences:** ‹languages, frameworks, anything that's a hard requirement vs. a preference›
- **Licensing:** all dependencies must be permissive (MIT/BSD/Apache); no GPL/AGPL/SSPL or non-commercial code/weights/data/assets (see `docs/LICENSE-POLICY.md`)
- **Other hard constraints:** ‹offline-only, determinism, no auto-publish, no stored credentials, data residency, accessibility, …›

## Features
> One numbered subsection per feature. For each: a short purpose paragraph, user stories in the
> **"As a ‹role›, I want ‹action›, so that ‹benefit›"** form, and — where it clarifies scope — a data model.

### 1. ‹Feature Name›
‹1–3 sentences: what this feature is, and the user need it serves.›

**User stories** — As a ‹role›, I want to:
- ‹action›, so that ‹benefit›
- ‹action›, so that ‹benefit›
- ‹action (include edge cases: confirmation dialogs, empty states, undo, …)›, so that ‹benefit›

> **`so that` is mandatory** — without the benefit clause it's a task, not a user story.

**Acceptance criteria & priority** (per story — keep light; defer deep ACs to the PLAN if complex):
- **Priority** — KANO: ‹must-be / performance / delighter› · MoSCoW: ‹MUST / SHOULD / COULD / WON'T-this-time›. (Cut `indifferent`/`reverse` outright; don't gold-plate must-be.)
- **AC** (Given/When/Then, independently verifiable by a non-author): ‹Given ‹precondition›, When ‹action›, Then ‹observable outcome››
- Mark **`needs-signal`** if a priority/AC is a guess with no real evidence (AI couldn't validate it).

**Data model** (if applicable): each ‹entity› contains:
- ‹field / sub-entity — type or allowed values, notes›
- ‹…›

### 2. ‹Feature Name›
‹…repeat the purpose / user stories / data model pattern…›

### 3. ‹Feature Name›
‹…›

## Open Questions & Risks
- ‹unknowns or irreversible decisions to resolve early — data model, core contracts, third-party/licensing, compliance. These are exactly what `plan-forge.mjs` will stress-test, so surfacing them here saves rework.›

---
> **Worked reference (Anthropic's RetroForge brief).** A brief for *"RetroForge — a web-based 2D retro game maker"*
> would fill **Overview** with the studio concept and value prop; **Target Users** with retro-aesthetic hobbyists and
> indie devs; **Features** with e.g. *Project Dashboard*, *Level Editor*, *Sprite Editor*, *Entity Behavior*, *Test Mode* —
> each carrying user stories ("As a user, I want to create a new project with a name and description, so that I can begin
> designing my game") and a data model (e.g. *Project* = metadata + canvas settings + tile size + palette + associated
> sprites/tilesets/levels/entities). Notice it stays at the *what/why* altitude — no framework or file-layout decisions.
