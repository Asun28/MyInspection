---
name: spec-ears
description: >-
  Rewrite a vague requirement into EARS lines a task card can carry: one `shall` each, an `R<n>` id an
  `acceptance:` item cites as `[R<n>]`, and `[TBD: <question>]` where a value is unknown instead of an
  invented default. Use before writing or refining a card, or when one reads as goals. Triggers on:
  "EARS", "make this testable", "rewrite the requirements", "refine the card", "需求改写", "可测试规格".
  Not for design decisions (grill-design) or plan audit (plan-forge).
---

# spec-ears — vague ask to testable requirement lines

> Requirements engineering is upstream of prompt engineering. Ambiguity resolved here is ambiguity the
> coding agent does not fill with fabrication. **Never invent a business rule, number, endpoint or string
> the source does not contain** - it goes to an open decision. Prefer one more open decision over one guess.

Sits between `shape-idea` (what/why) and `grill-design` (how). Those settle intent and design; this one
settles the *sentence*, so `requirements:` in the card is something a test can be written from.

## Three readers this output serves

A reviewer confirms it stays faithful and adds no rule the user did not state. A tester writes cases
without guessing. A coding agent implements without coming back with questions. If any of the three
would have to guess, the line is not finished.

## Stage 0 — inventory before writing

1. Read the source in full: the card, the plan section, the user's words. Give every existing statement a
   stable id (`R1` onward, or continue the numbering already there). Note the front-matter fields and
   section order - you preserve both.
2. Read the code the requirement is about. Enumerate every surface and every state (empty, loading, error,
   offline, conflict, selected, read-only). Tag each with `[code:path:lines]`.
3. Extract the constants that already exist: tokens, limits, timeouts, formats, exit codes. **You may only
   use a number you found.** Record where.
4. Name the governing standard if one applies, and record which section you actually opened.

## Stage 1 — rewrite into EARS

Fix the subject first: it is a **system or component, never a user role**. "User taps X" is the trigger;
"the schedule view shall …" is the response.

Then split: one action, one exception, one state transition per line. Normal and error paths never share
a line.

Then pick the pattern:

```
discrete trigger event?
├─ yes ─ also a persistent state or feature flag? ─ yes ─ SPLIT into a state line + a trigger line
│                                                 └─ no  ─ Event-Driven
└─ no  ─ persistent state? ─ yes ─ State-Driven
                           └─ no  ─ error / fault / invalid input / boundary? ─ yes ─ Unwanted
                                    └─ no ─ depends on an optional feature? ─ yes ─ Optional
                                                                            └─ no  ─ Ubiquitous
```

| Pattern | Skeleton |
|---|---|
| Ubiquitous | `The <system> shall <response>.` |
| Event-Driven | `WHEN <trigger>, the <system> shall <response>.` |
| State-Driven | `WHILE <state>, the <system> shall <response>.` |
| Unwanted | `IF <condition>, THEN the <system> shall <response>.` |
| Optional | `WHERE <feature> is enabled, the <system> shall <response>.` |

**Five patterns, and this repo carries no sixth.** A line that seems to need both a state and a trigger is
two requirements, not one compound - the same rule as one `shall` per line, applied to conditions instead
of responses. Two behaviours either side of a state change are likewise two lines.

Canonical EARS does define a sixth, `Complex` (`WHILE <state>, WHEN <trigger>, …`). It is deliberately left
out here, and that omission is not an oversight to be helpfully corrected: every downstream contract that
consumes these lines - `specs/tasks/_TEMPLATE.md`, `.claude/workflows/decompose-cards.mjs`,
`docs/PLAN-TEMPLATE.md`, `specs/README.md` - declares exactly five, and two incompatible authoring
contracts cost more than one missing shape. Adding it back means changing all five faces in one diff.

## Stage 2 — quality gates, applied to every line

- Exactly one `shall`. Anything joined by "and" or "also" splits into another `R<n>`.
- Active voice. `the system shall reject`, never `the input is rejected`.
- A specific trigger or state. Not "when something happens", not "if there is a problem".
- An **observable** response: display, reject, write, send, set state to, return code, exit non-zero,
  render. A test can watch it happen.
- Every number carries its unit and its boundary (`≤` or `<`), and comes from the card, the code, or a
  section you opened. Otherwise `[TBD: <closed question>]`.
- No implementation detail (component name, framework call, table name) unless the source already treats
  it as a contract.

**Banned in a requirement body** - any of these means it is not yet quantified: should, could, might, can,
appropriately, quickly, promptly, sufficient, reasonable, friendly, proper, handle, optimise, improve,
ensure, support (with no object), robust, fast, intuitive, clean, simple, minimal, elegant, modern,
polished, seamless, world-class, best practice. Rewrite to something checkable, or move it to an open
decision with a `[TBD: X]` placeholder in the body.

Turning a goal into constraints: "world-class and simple" is not a requirement. Ask what a test could
watch - a count, a duration, a token scale, a contrast ratio, a target size - and write that. If the
number is not available, the constraint is still written and the number is `[TBD:`.

## Stage 3 — evidence tags

Every claim carries where it came from, and an untagged claim is a claim you have not earned:

`[code:path:lines]` · `[card:section]` · `[SOURCE: standard, section, URL]` · `[INFERRED]` · `[TBC]` ·
`[TBD: <closed question>]`

Cite only a section you opened **this session**. Before finishing, audit each line's tag against something
you actually read, and downgrade anything that fails.

## Stage 4 — land it in the card

```yaml
requirements:
  - R1. The card validator shall report one finding per dangling requirement citation.
  - R2. WHEN a card declares `requirements:` and no acceptance line cites it, the validator shall pass.
  - R3. IF a `[R<n>]` citation resolves to no item, THEN the validator shall block with [CARD-REQ-DANGLING].
acceptance:
  - 1. A dangling citation blocks and names the id. [R3] [dod arms 6-8]
```

Each `acceptance:` item cites the requirement it closes **and** the arm that verifies it. Cannot close the
set? The card is too big - split it, do not manufacture a list. Nothing machine-checks the *shape* of a
requirement sentence; the one guard is that `[R<n>]` must resolve.

Keep the card's existing field names. If the card would need a field its schema does not have, list it
under "Schema gaps" in the recap - do not invent a field.

## Stage 5 — output

Write the requirements back into the card. Then in chat, standing on its own:

1. One paragraph: what the card covered before, what it covers now.
2. Change table: id, before (original phrase or "new"), after (one line), reason.
3. Open decisions, numbered, each naming the `R<n>` ids it blocks. Closed questions with candidate
   answers - never "please provide more detail".
4. Decision log: every place you reworded the user's intent to make it testable, original and rewritten.
5. Follow-ups noticed but not touched.

## Self-check before you hand it back

1. Can a tester write steps without guessing a business rule?
2. When a test fails, is it clear whether the implementation is wrong or the requirement underspecified?
3. Would two readers agree on each trigger and each response?

Any "no" means back to Stage 1, or one more open decision.

## Downstream projects working in another language

The skeletons above are the shipped form (this repo is English-first). A project writing requirements in
Chinese swaps the skeleton table for 「<系统>应<响应>」/「当<事件>时，…」/「在<状态>时，…」/「如果<条件>，则…」/
「在已启用<功能>时，…」, keeps the pattern names in English as labels, and keeps every rule in Stage 2
unchanged - one 「应」 per line, and the banned list gains 应该、尽量、及时、合理、友好、妥善、处理、优化、
完善、简洁、优雅 and 无宾语的「支持」.
