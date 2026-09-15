# Pre-review checklists (PR review v2, Phase 1a)

Worker-facing. Written as data: this file enters the discovery pack as `checklists.md` and the policy hash
(`policy_hash` = SHA-256 over this file plus `specs/prereview-record.schema.json` at the merge-base commit, the
FACTS-LIB card), so any later wording change here is a policy change by construction. Protocol:
`docs/PREREVIEW-PROTOCOL.md`; record contract: `specs/prereview-record.schema.json`.

How to read a check: `- C{n}` is the category a finding files under; one of the tokens in parentheses is the
record's `contract_ref` (`lesson:L{n}` from `docs/lessons/LEDGER.md`, `rubric:{n}` from `docs/QUALITY-RUBRIC.md`
sections 1 and 2). A check that fires is filed as candidate records, one per site. A check that is run and finds
nothing still counts toward the unit's `categories_checked`. Each `## Lens:` section applies to the path class
named under its heading; the prompt carries the sections whose class appears among the changed files (the card's
own `specs/tasks` file, `_local/**` and `.review/**` excluded), or the list the card pins with `review_lens`
(`Select-PrereviewLensSections`, the PROMPT card), and the discoverer can read the whole file in the pack.

## Lens: code

Applies to `android/**` production sources (`*Test*` files are the tests class).

- C2 Guards on every entry point: for each guard the unit adds or relies on, list every public entry that reaches the same state and confirm each one carries it; a rule installed on one entry point and absent on another is a defect a red test can show (lesson:L228).
- C2 Empty, null, zero, negative and overflow inputs: walk each parameter with the boundary values its type admits (the empty collection or string, null where the type is nullable, 0, a negative value and the type's MAX for a number) and each arithmetic step with its operands at MAX; a bound or containment check computed without saturation or a wider sum can wrap and pass (lesson:L228).
- C2 Fail-open branches: a classification, completeness or validation gate derives its universe from the contract source, enumerates exhaustively and treats an unclassified value as a defect; a default or else branch that returns a permissive value instead of throwing, a swallowed exception, or a universe derived from the data under check is fail-open (lesson:L228, rubric:9).
- C2 Ordering assumptions: a collection that feeds a hash, a receipt, a canonical serialisation or a persisted order has a total order enforced in code (a sort call or a sorted collection type counts); iterating an unordered set or map into one of those is hidden non-determinism (rubric:10).
- C2 API level against minSdk: every `java.*`, `javax.*` or `android.*` call new to the unit is checked against the minSdk pinned in `android/app/build.gradle.kts` and the desugaring list; `:core` tests run on a JDK, so a JVM test cannot detect an API missing at the target level (lesson:L217, rubric:15).
- C2 Published judgement sets: a read-only collection is not an immutable one; an allowed-set or domain-set published as a collection backed by a mutable JVM type can be cast back and extended, so the unit exposes a predicate instead (lesson:L295).
- C3 A test that goes red without this change: for each behavioural change, name at least one test in the unit's test file that fails when the change is reverted; none, or only one that lives in a source set no task runs, is missing evidence (rubric:6, lesson:L281, lesson:L280).
- C1 KDoc and comments inside the changed code: each sentence stating a guarantee (never, always, every, cannot) is checked against the code below it and against every other site in the tree it quantifies over (lesson:L309, lesson:L224).
- C7 Named by the worker: a hard-boundary crossing (runtime network, account, server-side state) or a new dependency without a licence check; state the rule applied (rubric:2, rubric:4).

## Lens: tests

Applies to `*Test*` files, selftest fixtures and receipts (mutation, DoD and selftest receipts).

- C3 Assertion face equals the contract: a substring or keyword match over whole stdout, a match on localised text, a keyword count, or "any non-zero exit" stays green while the contract is absent; compare the verdict line (or the exact whole output), match ASCII sentinels, and make the negative case reach the guarded statement (lesson:L165).
- C3 One mutant per claimed assertion: where a receipt, card row or DoD claims that an assertion is mutation-covered, that assertion names a single-point mutation that only it catches; an earlier assertion in short-circuit order shields a later one, and a mutant's expected failure code is anchored, not a bare substring (lesson:L225).
- C3 Compile-kill is not a kill: a mutant that fails to compile proves nothing about the assertion; a receipt claiming zero compile-kills needs a compile-only probe per mutant with exit 0 recorded (lesson:L282).
- C3 Expected values come from the contract: an expected value computed by calling the production code under test (a round trip compared with the original input is not this) follows the mutation and stays green; one transcribed from the implementation's current output encodes its defects as expected (lesson:L165, rubric:6).
- C3 Vacuous pass: a transformation test whose fixture lacks the input being transformed, an empty test body, or a test the DoD command never executes (wrong source set, no task runs it) is not evidence (rubric:6, lesson:L19, lesson:L280).
- C3 Negative case in the sibling file: for each new guard, the unit's test file (the existing one for an existing unit, not a parallel new file) holds a case that reaches the guard with an input tailored to it (parseable but wrong for a comparison, null or empty for a null or empty guard, MAX for an overflow guard) rather than one that stops at an earlier check (lesson:L165, rubric:11).
- C3 Receipt matches what it describes: a mutation receipt pins the SHA-256 of the production files as they are in this tree, lists each mutant's selector, expected failure and the killing test by name, and every named test exists in the file it names (lesson:L270, lesson:L281).
- C1 Test names and receipt prose: a test name or receipt sentence that claims more than the assertion checks (CR and CRLF while only CRLF is built, "exact bytes" while only strings are compared) is a written guarantee exceeding evidence (lesson:L317).

## Lens: prose

Applies to comments, KDoc, `docs/**`, `context/**` and `specs/**` (cards, schemas, acceptance rows).

- C1 Universal claim: each every/never/all/always/only sentence is instantiated against the tree: list the instances it quantifies over (grep the invariant, not the symptom word) and check each; one contradicting instance makes the claim a finding (lesson:L309).
- C1 Sibling clause: a clause added to or changed in an enumerating sentence is checked against the other clauses of the same sentence and against the parallel section it mirrors; copying the nouns without the qualifier turns a scoped statement into a universal one (lesson:L321).
- C1 Acceptance rows and `dod_assert` are declarations that need evidence: each row marked automated points to the evidence shape the card declares for it (a test, a mutant, a receipt, or the card's own DoD command, which the rubric accepts as self-verification), present in this diff; a "covered" or "verified" phrase that can point to none of these is a finding (lesson:L317).
- C1 Stale narration after a structural change: header comments, exported-name lists, failure text and summary lines that describe the previous shape; grep the old identifiers for residue (lesson:L224).
- C1 Guarantee wider than its mechanism: a sentence promising a property (excluded, cannot, unique, only writer) is checked against the exact mechanism that provides it; where the mechanism covers a subset (untracked files only, one entry point only) the sentence names the subset (lesson:L309, lesson:L321).
- C4 Evidence outside the diff: a claim whose evidence lives in a PR body, a chat transcript or another tree is not evidence for this tree; a receipt pinned to a commit this branch does not contain is drift (lesson:L227, lesson:L310).
- C5 Scope and placement: a file outside the card's `allow_paths`, a capability the card's `non_goals` exclude, a new file in the repository root, a name outside the naming table in `CLAUDE.md`, or card front-matter that drifts from the card id (rubric:1, rubric:14).
- C7 Two authorities for one rule: a second copy of a table, list or threshold that already has a truth source, or a rule with no decidable check; name the existing source or the missing check (lesson:L97, rubric:8).

## Lens: scripts

Applies to `scripts/**`, `.github/**` and `.claude/hooks/**` (PowerShell, workflows, hooks).

- C2 Every error branch open or closed: for each catch, each `-ErrorAction`, each empty-result path and each native call, state whether the branch fails closed (non-zero exit with a sentinel) or open (continues on a default); an open branch on a gate path that the script does not document as intended is a defect, and "0 items processed, exit 0" is the standard fake green (lesson:L228, lesson:L235, rubric:9).
- C2 Every exit code reachable and read correctly: each exit code the script documents has an input that produces it; `$LASTEXITCODE` is read right after the native call, never after `Select-Object -First N`, and every native call whose result feeds a decision is guarded (lesson:L93, lesson:L49).
- C2 Every ASCII code unique by meaning: each `[NAME-...]` sentinel stands for exactly one documented meaning and no two meanings share a code (branches that report the same meaning may print the same code), and the table that documents it, where one exists, lists it once; a sentinel that is a prefix of another needs anchored matching (lesson:L165, lesson:L225).
- C2 Case semantics: a comparison that carries case meaning uses `-cmatch`, `-ceq` or explicit regex options, and a negative fixture shows the lowercase or mixed form is still admitted where it should be (lesson:L159).
- C2 Shell boundary: a `.ps1` invoked through bash, or a backslash-bearing string passed through bash to another interpreter, is a silent-failure path (lesson:L17).
- C3 Gate passes on a positive sentinel: a gate or DoD that passes on "no failure record" instead of on a success line only a real run prints is fake green; removing the gate from the suite must turn the DoD red (lesson:L264, lesson:L207).
- C3 Localised text as a machine-checked assertion: a gate that matches non-ASCII output turns red or green with the console encoding chain; it matches an ASCII sentinel and leaves the localised line to the human reader (lesson:L165, lesson:L17).
- C1 Header comments, parameter lists and failure text of the changed script still describe this version; a failure message that hardcodes a cause goes stale, so it reports the observed data instead (lesson:L224).

## Coverage rules

The discoverer returns one coverage record per unit in `units.json`, with `categories_checked` covering C1, C2 and C3
for every unit; RECORDS synthesises a `missing` coverage row for any unit whose discoverer coverage lacks one of
them. C1 to C3 need the tree, which the discoverer has; the lens's coverage is additive. Candidates use only C1, C2,
C3, C4, C5 and C7. C6 (repeated dispute) is derived from dispute records only and is never emitted by a worker. A
check that is run and finds nothing is recorded in `categories_checked`. Report `status` per unit, not per check:
`finding` when a candidate of the batch names the unit, `checked_no_finding` when none does, and `blocked` with the
location in `missing_context` when a location the worker needed could not be read.
