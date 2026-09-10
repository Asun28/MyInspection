#requires -Version 7
<#
.SYNOPSIS
  脚手架的**唯一项目配置点**。新项目只改这一个文件（或跑 init-scaffold.ps1 自动填）。
  所有脚本/钩子 dot-source 本文件取项目级常量，避免把项目名/账号/冻结路径散落硬编码到各处。

.DESCRIPTION
  - 被 _guard.ps1 / task.ps1 / review.ps1 / gh-bootstrap.ps1 / check-licenses.ps1 与
    .claude/hooks/guard-frozen.ps1 dot-source。
  - 返回一个 $ScaffoldConfig 哈希表；调用方按需取字段。
  - 故意 fail-closed：GhAccount 未配置（留空）时，账号守卫会拒绝一切 gh 写操作，
    强制你先配置，杜绝误推到错误账号。
#>

$script:ScaffoldConfig = @{

  # 本项目唯一允许的 GitHub 个人账号。账号守卫(_guard.ps1)会拒绝任何其它账号的 gh 写操作。
  # 留空 '' => 守卫直接报错并提示先配置（fail-closed）。
  GhAccount = 'Asun28'

  # 仓库 / 项目名。留空 '' => 各脚本自动用仓库根目录名（Split-Path -Leaf）。
  # 一般留空即可；仅当目录名与期望仓库名不一致时才显式填。
  ProjectName = 'MyInspection'

  # 后端 Python 版本（setup/task 建 venv 用）。无 Python 后端则忽略。
  PythonVersion = '3.13'

  # ── T296-VERIFY-THREE-STATES (TD224): the integration / e2e closure that verify.ps1 gate 2 runs ──
  # An argv ARRAY, never a shell string - the same posture the toolchain descriptors take for their command
  # fields (docs/TOOLCHAIN-INTERFACE.md): no shell interpolation, no quoting bugs. Element 0 is the driver
  # that must resolve on PATH, and a DECLARED command whose driver does not resolve is fail-closed RED -
  # exactly what TD43 forced on the ruff/pytest and npm branches rather than letting them skip silently green.
  #
  # EMPTY @() => NOT CONFIGURED, which is a THIRD state and not a pass. verify.ps1 then reports
  # `verify: NOT CONFIGURED` and can never report `verify: PASS`, because PASS is a claim about gates that
  # actually ran. It still exits 0 by default: ci.yml runs verify.ps1 on every push to THIS repo, where
  # there is no product to close a loop over and gate 2 cannot be wired, so escalating by default would red
  # master to buy honesty. `verify.ps1 -Strict` is the opt-in escalation that turns this state non-zero,
  # and it is the same lever check-secrets.ps1 already uses for its pre-public run.
  #
  # A downstream fills this in once it has a loop worth closing, e.g.
  #   E2ECommand = @('uv','run','python','-m','pytest','tests/e2e','-q')
  # The hard boundary is inherited from verify.ps1's own header and does not relax here: whatever goes in
  # must be deterministic and offline - no external services, no network, no GPU. See docs/DELIVERY-OPS.md.
  E2ECommand = @()

  # 开发 worktree 的父目录（R1：每张卡一个 <root>\<TaskId>）。浅路径规避 Windows MAX_PATH。
  # 留空 '' => 按 OS 自动取默认（Windows: <系统盘>\wt，如 C:\wt；macOS/Linux: ~/.wt）——见 Get-ScaffoldWorktreeRoot。
  # 默认走 $env:SystemDrive（不硬编码 D:），修「单盘机器无 D: → 首次 start 崩」(C02) 与「mac/Linux 吃 D: 盘符」两个可移植性坑。
  # 显式填路径即覆盖自动值。selftest 闸⑮ 按该字段的单引号字面量注入临时根，留空仍可被注入。
  # T217: set explicitly for THIS machine. `C:\wt` is shared with other repos here (a MyInspection
  # worktree appeared in it mid-session while ~130 directories were swept away, twice deleting a live
  # worktree out from under a running card), and C: sits at ~10 GB free. The DEFAULT above is unchanged
  # and still right for downstream: leave this empty and you get <SystemDrive>\wt / ~/.wt.
  WorktreeRoot = 'D:\wt'

  # ── 冻结物（一等资产）：契约 / schema 一旦冻结，演进须走版本评审 ──
  # guard-frozen 钩子(PreToolUse)与 review.ps1 据此拒绝就地编辑这些文件。
  # 用「仓库相对、正斜杠」的正则片段；空数组 @() => 不启用冻结守卫（项目还没冻结点时留空）。
  # 示例（取消注释并改成你项目的冻结文件）：
  #   'backend/app/providers/contract\.py',
  #   'backend/app/schemas/manifest',
  FrozenPaths = @(
    'android/core/src/main/kotlin/nz/myinspection/core/backup/format/',
    'android/core/src/test/kotlin/nz/myinspection/core/backup/format/',
    'android/core/src/main/sqldelight/',
    'android/core/src/main/kotlin/nz/myinspection/core/canon/',
    'android/core/src/test/kotlin/nz/myinspection/core/canon/',
    'android/core/src/main/kotlin/nz/myinspection/core/template/Template\.kt'
  )

  # ── ADR 0016 (T273-TIER-CORE): the two path lists a card's ACCEPTANCE TIER is computed from ──
  # A card's tier is derived from its `allow_paths`, never chosen: Get-ScaffoldCardTier (scripts/_cards.ps1)
  # matches each declared path against these lists, all entries repo-relative with forward slashes - a file
  # entry is exact, an entry ending in '/' is a directory prefix, an entry carrying a wildcard is a glob.
  # It is BUILT ON the scope gate's matcher and is deliberately NOT the same question, so do not read the
  # two as interchangeable (T274/T280): a Tier-S entry matches by containment in BOTH directions, because
  # the tier asks whether a card could REACH the entry; a Tier-0 wildcard naming no directory is anchored
  # to the repo root; and a FrozenPaths entry, folded in by the accessor below, is the regex FRAGMENT its
  # own contract declares it to be - one that will not compile raises the card to S, never lowers it.
  # A card may DECLARE `tier:` only to RAISE; a declaration below the computed value is a check-cards
  # block ([CARD-TIER-LOWER]).
  #
  # TierSPaths - ANY allow_path matching one entry makes the card Tier S: the merge path, the enforcers and
  # the frozen contracts, where the full 17-gate acceptance keeps its exposure. Empty @() => tiering is OFF
  # and every card is S, which is byte for byte today's bar (empty means off, never broken). The same
  # reversal spelled positively is a single '**' entry. FrozenPaths is folded in by the accessor below.
  TierSPaths = @(
    'scripts/_guard.ps1', 'scripts/_scope.ps1', 'scripts/_ci.ps1', 'scripts/_config.ps1',
    'scripts/task.ps1', 'scripts/review.ps1', 'scripts/verify.ps1', 'scripts/check-scope.ps1',
    'scripts/check-secrets.ps1', 'scripts/check-licenses.ps1', 'scripts/selftest.ps1',
    'init-scaffold.ps1', '.github/workflows/', '.claude/hooks/', '.claude/settings.json',
    'specs/verdict.schema.json'
  )

  # Tier0Paths - a card is Tier 0 only when EVERY allow_path matches an entry here: the doc, card and ledger
  # commits that are 80% of this repo's history, whose acceptance is ci.yml alone. Empty @() => no card is
  # ever 0, which is again the conservative reading of empty. '*.md' is the ADR's ROOT-markdown entry and is
  # matched as one since T274: a wildcard entry naming no directory covers only a path naming no directory,
  # so scripts/README.md is Tier 1. Any TierS entry a card also declares wins outright (any-match beats
  # all-match), so this list can only ever cheapen a card that declares nothing else.
  Tier0Paths = @('docs/', 'specs/', 'context/', 'CHANGELOG.md', '*.md')

  # T38-DOCDRIFT：源脚本变更时须同步触及的权威文档（仓库相对、正斜杠正则；空表 => 不启用）。
  DocSyncMap = @{
    'scripts/task\.ps1'           = @('docs/DEVOPS-WORKFLOW.md')
    'scripts/review\.ps1'         = @('docs/QUALITY-RUBRIC.md')
    'scripts/check-licenses\.ps1' = @('docs/LICENSE-POLICY.md')
    # check-scope.ps1 是 DEVOPS-WORKFLOW「已推送恢复」序列第 3 步的可执行投影，二者今后必须同步改（TD93 item①）。
    'scripts/check-scope\.ps1'    = @('docs/DEVOPS-WORKFLOW.md')
    # check-adr.ps1 是 docs/adr/README.md 所写契约的可执行投影——与上一行 check-scope.ps1↔DEVOPS-WORKFLOW
    # 完全同构的关系，却一直没有配对，故二者可静默漂移（TD145 item②）。
    'scripts/check-adr\.ps1'      = @('docs/adr/README.md')

    # T115/TD154: eight more couplings, each measured against one bar - the paired doc STATES the
    # script's contract, so a behaviour change plausibly makes that prose stale. A doc that merely
    # NAMES the script does not qualify: that pairing costs friction on every diff and catches no
    # drift, and repeated friction turns the `[doc-sync:none]` escape hatch into a reflex, which
    # would hollow out the gate for the pairs that do matter. Two candidates were measured and
    # REJECTED on exactly that bar, recorded here so a later reader does not re-propose them:
    #   * scripts/verify\.ps1 -> docs/DEVOPS-WORKFLOW.md - the doc names it 验收总闸门 in a one-line
    #     table row that survives almost any change to the script.
    #   * scripts/selftest\.ps1 -> anything - it changes on nearly every card.
    # Each key below is ALSO named in selftest's production-map assertion, so deleting one from this
    # table turns 14f red instead of silently shrinking the gate's coverage.
    'scripts/lessons\.ps1'        = @('docs/LESSONS.md')          # the doc names lessons.ps1 the operator and states its subcommand contract (archive demotes, search spans hot+cold)
    'scripts/check-cards\.ps1'    = @('specs/README.md')          # the doc enumerates the guard list check-cards runs, and states that check-cards CONSUMES the Should/ShouldNot table rather than holding it
    'scripts/mutate\.ps1'         = @('specs/mutations/README.md')# the doc states the CLI surface, the registry format and the batch-safety contract (.mutbak self-heal, byte-compare restore)
    'scripts/triage\.ps1'         = @('docs/LOOP-ENGINEERING.md') # the doc carries the probe count and the section describing the heartbeat's contract
    'scripts/handoff\.ps1'        = @('docs/HANDOFF.md')          # the doc states the twelve-field block and the conditions under which `handoff.ps1 check` exits non-zero
    'scripts/archive\.ps1'        = @('specs/README.md')          # the doc states both halves of what archive.ps1 does - merged cards to cold store, whole tech-debt rows to cold store
    'scripts/gh-bootstrap\.ps1'   = @('docs/SECURITY.md')         # the doc states the pre-push hook install and the public-repo -Strict history scan (TD62)
  }

  # T231-CARD-BUDGET (TD235): the DEFAULT net-line budget a task card is judged against when it declares
  # no `budget:` of its own, and the FRACTION of a budget at which the meter trips.
  #
  # CardBudgetDefault = 0 => OFF: a card with no `budget:` is reported on and never tripped or blocked.
  # That is the same "empty means off, never broken" degradation FrozenPaths / DocSyncMap / DocBudgets
  # give a freshly initialised downstream, and here it is also what keeps cards written before the field
  # existed shippable - which matters, because cards are in flight in this repo continuously.
  # 400 is not a new number: it is the top of the default tier the size DEFINITION SITE already states
  # (.claude/workflows/decompose-cards.mjs). Declaring it here does not tighten the standard, it makes
  # the standard the tree already teaches machine-readable.
  #
  # CardBudgetTripFraction is the whole anti-Goodhart move and is why this is not just a smaller ceiling.
  # A ceiling of N makes N-1 a pass; what is wanted is a signal EARLY enough that splitting is still
  # cheap, so the meter speaks at 0.6 of the card's own declared budget and asks for a recorded decision
  # (split, or raise the budget on the base card with a reason) rather than returning a pass/fail.
  # 0 or empty => never trip, report only. Values outside (0,1] are clamped by the consumer, not here.
  CardBudgetDefault      = 0
  CardBudgetTripFraction = 0.0

  # T89-DOCBUDGETS: per-file size ceilings for the standing docs that cost context on every turn or on every
  # task (repo-relative path with forward slashes => maximum CHARACTER count). Empty @{} => the budget gate is
  # off, the same graceful degradation FrozenPaths and DocSyncMap give a freshly initialized downstream.
  # Read through Get-ScaffoldDocBudgets, judged by Test-ScaffoldDocBudget (scripts/_guard.ps1), enforced by
  # selftest sub-gate 14g. Characters, not words: this repo's docs mix English and Chinese and CJK runs carry
  # no whitespace, so a word count ranks the largest doc as one of the smallest; bytes would over-penalise
  # Chinese roughly threefold. Ceilings are seeded from the measured size plus roughly 5% headroom and are
  # meant to RATCHET DOWN as docs shrink (lowering one is a human decision in a later diff, never automatic).
  # Over a ceiling: relocate the content into a docs/ file or condense it first; raising a ceiling is allowed
  # but requires a stated reason in the PR that raises it.
  UpstreamDocBudgets = @{
    'CLAUDE.md'               = 15000
    # T242/TD248 raised this by 100: the file measured 26200 of 26200 and a stale claim in it had to be
    # corrected (ship DOES invoke R3 for a card declaring review_gate). The clause was condensed first -
    # it asserted 'not a merge gate' three times and now says it once - and this covers the remainder.
    # RAISED 2026-09-05 with the three below (same measurement): 26207/26300 and 25059/25100 at the end of the
    # ADR 0016 arc; both are downstream payload and both were edited by replacement only.
    'CLAUDE.template.md'      = 27000
    # RAISED 25800 -> 26100 by T296 (TD224), with the reason this field's own note requires. It was not a
    # verbosity problem: the file measured 25687/25800, i.e. 113 characters of headroom, while THIS FILE'S
    # field table is the declared authority for every `_config.ps1` field - so adding one field to the
    # config and documenting it here are the same obligation, and no honest row for `E2ECommand` fits in
    # 113 characters. That is the T270 deadlock recurring on a second key file, and it is recorded as such
    # rather than absorbed silently. Raised by 300 (measured 26014, leaving 86) rather than reseeding at
    # measured-plus-5%, for the reason T270 and the 2026-09-05 raise both give: a generous margin hides the
    # next saturation instead of surfacing it, and this file is now the tightest budgeted doc in the repo.
    # RAISED 26100 -> 27800 by T301, the same obligation recurring on the same file for the third time: the
    # note above left 86 characters of headroom, and `ReviewIntensityByTier` is a dial whose row has to
    # carry the values, the only-lower rule, the base-card binding, the skip's verdict shape and BOTH of its
    # floors AND its complete precedence - the last of those added because R3 found the row's stated order
    # omitted ReviewSkipWhen, which decides first and exits, making the documented remedy for a Tier-0 skip
    # wrong for exactly the diffs it was written for. 1585 characters measured, and no honest shorter row
    # exists for a field that decides whether a review happens at all. Measured 27599, leaving 201. Raised
    # by 1700 rather than reseeding at measured-plus-5% (about 1380 over the old value), for the reason
    # every raise above gives: a generous margin hides the next saturation instead of surfacing it.
    'TEMPLATE-README.md'      = 27800
    # RAISED 28100 -> 28600 by T270, with the reason this field's own note asks for. It was NOT a verbosity
    # problem and the measurement is the point: at 28097/28100 this file had THREE characters of headroom,
    # while `DocSyncMap` above makes a change to it MANDATORY whenever scripts/task.ps1 changes (sub-gate
    # 14f). So the two machine rules contradicted each other - 14f demanded an edit, 14g refused every edit
    # that would fit, and even a single 112-character sentence did not. Any card touching task.ps1 would
    # have hit it. Raised by 500 rather than to the usual measured-plus-5%, which would be about 1400: the
    # deadlock is registered as a debt of its own, and a generous margin here would hide its next recurrence
    # on the next saturated key file instead of surfacing it.
    # RAISED 2026-09-05 (user decision, after the ADR 0016 arc): DEVOPS-WORKFLOW 28600 -> 29500, QUALITY-RUBRIC
    # 25100 -> 26300, SELFTEST 5500 -> 5800. Measured at the end of the arc: 28590/28600, 25096/25100 and
    # 5497/5500 - three saturated key files at once, each DocSyncMap-paired with a script the next cards touch
    # (task.ps1, review.ps1) or the acceptance doc every selftest card edits. Every arc edit replaced sentences
    # rather than appending, and the three files still ended within ten characters of their ceilings, so the
    # deadlock the T270 note describes was about to recur three times over. Raised by roughly 3-5 %, not the
    # measured-plus-5 % reseed, for the same reason T270 gave: a generous margin hides the next saturation.
    'docs/DEVOPS-WORKFLOW.md' = 29500
    # RAISED 26300 -> 27300 by T301, measured 27103 leaving 197. The 2026-09-05 raise left 132 characters
    # here, and this card had to state the intensity dial in the one place a reviewer and an operator both
    # read - beside the wall-clock budget, since both answer "how much review does this diff get". The last
    # 274 of it are R3's correction: the first draft told an operator to set Tier 0 to advisory if the
    # trade-off hurt, which does nothing for the markdown-only diffs ReviewSkipWhen routes away before this
    # dial is consulted. Raised by 1000 rather than measured-plus-5% (about 1355), same reason as above.
    'docs/QUALITY-RUBRIC.md'  = 27300
    'docs/DELIVERY-CHAINS.md' = 16500
    'docs/SELFTEST.md'        = 5800
    'docs/PR-CONVENTIONS.md'  = 2800
  }

  # ── T150-RESIDENT-BUDGET: the ALWAYS-ON payload, which DocBudgets does not describe ──
  # DocBudgets ratchets six STANDING DOCS. This ratchets what actually rides in the system prompt on EVERY
  # turn. The two overlap on CLAUDE.md and are otherwise different questions, which is why they are separate
  # tables rather than one: the 17 skill `description:` blocks are resident on every turn and had never been
  # counted, budgeted or reviewed, while CLAUDE.md sat at 98.7% of a machine-enforced ceiling. The surface
  # under a ratchet was being squeezed hard while the surface beside it grew freely.
  #
  # TWO CEILINGS, NEVER ONE POOL. They trade against each other, and pooling them would let whoever edits
  # last arbitrate that trade silently - buying CLAUDE.md headroom by shortening descriptions degrades
  # TRIGGERING, which is the failure mode that costs most for a skill. Separate ceilings keep the tension
  # visible. Nothing in the implementation sums them or moves budget between them.
  #
  # CHARACTERS ARE A PROXY FOR TOKENS, AND A POOR ONE for CJK-heavy text where the ratio differs sharply
  # from English. The unit is chosen for comparability with DocBudgets above, not for accuracy: a doc that
  # halves its characters by switching language has not halved its token cost. Treat these numbers as a
  # RATCHET AGAINST GROWTH, never as a measurement of spend.
  #
  # Seeded from the measured size plus roughly 5% headroom, same as DocBudgets, and meant to ratchet DOWN.
  # Empty => the check is off, exactly like FrozenPaths / DocSyncMap / DocBudgets.
  # ── T158-SELFTEST-SCAN-SKIP：本项目声明「哪些 selftest **冒烟**扫描对它是冗余的」──
  # 只能从 _guard.ps1 里**代码写死的封闭枚举**中选，config 选不出枚举外的东西：开放式跳过列表等于让数据文件
  # 解除任意闸门，正是闸 9e 要防的静默解锁类。枚举每一项都**点名仍覆盖它的执行臂**，那份点名就是允许跳过的
  # 全部理由。列表里出现枚举外的 id 是**硬错误**、不是静默 no-op。
  # **空 = 什么都不跳**，且 init-scaffold 会清空它——下游必须**带着武装**到货。
  # 注意：这跳过的是**验收跑里的一条冒烟断言**，不是安全本身——ship/pre-push/CI/建仓四条执行臂照跑。
  SelftestSkipScans = @()

  # ── T211-MUT-ANCHOR-RATCHET (TD206): registry entries whose anchor is KNOWN not to resolve ──
  # A mutation entry names an exact source line. When that line is later reworded the entry stops
  # resolving, and it then produces NO evidence - not a wrong verdict, which would argue with you, but
  # silence, while keeping its seat in the batch. The runner already fails closed on this (mutate.ps1
  # counts ANCHOR-MISS into its failure total), but nothing re-reads the corpus, so decay accumulates
  # between the rare moments somebody touches one registry. Sub-gate 14o closes that: every entry is
  # resolved through the PRODUCTION matcher (Get-MutatedVariant via mutate.ps1 -AsLibrary), so the gate
  # and the runner can never disagree about what resolves.
  #
  # This list is the DATED BASELINE, measured 2026-08-29 over 253 entries in 67 registries: 237 resolved,
  # these 16 did not. The count was reproduced independently by a second session, from the same production
  # matcher but its own code, and agreed entry-for-entry.
  #
  # DECAY IS CLUSTERED, NOT UNIFORM, and that is the more useful shape than the flat 16/253: T70 alone
  # carries six of the sixteen and T114 three, because a registry rots WHOLESALE when the file it anchors
  # gets reworked. So the risk is not "some entries drift" - it is "one refactor silently retires a whole
  # card's evidence". 86 of the 253 anchor lines in _guard.ps1 or selftest.ps1, the two files nearly every
  # gate card edits.
  #
  # It is not an allowlist. Per ADR 0011 it ratchets in THREE directions - an entry that
  # decays without being listed FAILS, a listed entry that resolves again is reported STALE, and a listed
  # KEY whose entry was RETIRED from its registry is reported ORPHAN (T235/TD209), so the
  # list cannot outlive the work it excuses in either of the two ways it can. Entries leave by being re-anchored, never by being edited to
  # match a broken consumer, and never by growing the list to reach green.
  #
  # Format 'registry-basename:entry-id'. Empty => the check still runs and simply excuses nothing, which
  # is the correct freshly-initialised downstream state (its specs/mutations/ holds only README.md).
  UpstreamMutationAnchorPending = @(
    'T159-CARD-REF-INTEGRITY:M2'
    'T178-WIRING-SELF-SAT:M2'
    'T179-HANDOFF-TAB-GUARD:M2'
    'T182-SUBGATE-FORM-RATCHET:M2'
    'T185-SUBGATE-FORM-DRAIN:M2'
    'T63-TD119-MUTATION-RUNNER:N18'
    # T215 drained the T70 registry: all six of its entries were re-anchored and its batch re-run, so their
    # exemptions are gone from this list in the same diff (14o reds either way round). Sixteen -> ten.
    # T216 drained T114: of its three, one was re-anchored onto the bounded extractor T171 introduced and
    # two were retired - one as a mutation-survivor duplicate of that seat, one because its line was already
    # anchored byte-identically by another registry. First worked example of the retire branch.
    # T219 drained T110, a one-entry registry and the small end of the clustering above: M1 was RETIRED,
    # because TD194 replaced the line it deleted and T200 M3 already anchors the replacement with the same
    # fixture in mustFind. The same batch pruned M4, which was not decayed but shared a byte-identical
    # anchor with T200 M5. Retiring beats re-pointing whenever another registry already owns the line.
  )

  # -- T220-MUT-EVIDENCE-COVERAGE (TD212): the EVIDENCE channel's pending list, sibling of the anchor one --
  # MutationAnchorPending above holds entries whose ANCHOR stopped resolving. This list holds the other
  # channel: entries whose stored evidence no longer covers the mustFind they claim. The two fail
  # INDEPENDENTLY, which is the whole reason TD212 exists - T110 M4 held a byte-perfect anchor and dead
  # evidence at the same time, so it was absent from the sixteen TD207 enumerated.
  # Format 'registry-basename:entry-id', same as above. Empty => sub-gate 14p still runs and excuses
  # nothing, which is the correct freshly-initialised downstream state.
  # MEASURED 2026-08-30 over the whole corpus (270 entries, 515 mustFind literals): 3 uncovered literals
  # across 2 entries. T63-TD119-MUTATION-RUNNER:N18 is deliberately NOT listed here - its anchor does not
  # resolve, so it is already excused above, and 14p skips anchor-excused entries rather than letting two
  # gates argue about one debt. That left exactly one seat, and T295 DRAINED it: T202 N4 claimed 'below the
  # floor of 20' while its stored row proved 'found:below the floor of 13', because T203 raised that floor
  # on 2026-08-29 and T202's entry was edited to match without re-running its batch. Re-running it under
  # -IncludeMeta recorded 'found:below the floor of 20', so that claim is proven and that seat is gone.
  # The drain is not bookkeeping: a covered-again entry that STAYS declared reds 14p as
  # [MUT-EVIDENCE-STALE], gate 14 then exits 1, and sub-gate 8.2j - which runs '-Only 14' with git hidden
  # and requires a GRACEFUL exit 0 - reads that as the offline-degradation promise being false and reds
  # gate 8 too. Measured here: T203 and T213's batches both fail-closed [MUT-PRISTINE-RED] on gate 8 until
  # this line went. The SAME card then opened the two seats below for a different reason (TD283), so the
  # list is short and non-empty rather than empty; an empty list would be the correct freshly-initialised
  # state, never a disabled check. Do not restate the seat COUNT in prose here - it is exactly the volatile
  # value TD282 is about, and the entries below are the only honest census.
  UpstreamMutationEvidencePending = @(
    # T295/TD283: T98 M2 deletes the -RunCommandBound clause from mutate.ps1's root guard, after which the
    # guard refuses EVERY -Root mismatch - including the temp-tree fixtures that drive gate 17ac itself.
    # Its own assertion still fires (17ac(t) reports the over-refusal), but sub-cases w1/w2/w5, added by
    # T294 hours earlier, then read '<case>/reg-results.tsv' UNCONDITIONALLY at selftest.ps1:14856/14873/
    # 14914 - before the arms that ask whether the seeded batch ran at all. A refused batch writes no such
    # file, so Get-Content throws under Set-StrictMode, the run dies, and the closing
    # [SELFTEST-ONLY-FAIL] sentinel this entry names never prints. So the entry is BAD-EVIDENCE: red for a
    # crash rather than for a verdict (L167). The claim is NOT weakened to buy a green - shortening it to
    # '17ac(t)' alone is exactly the prefix decay T227/TD219 exists to refuse. The seat holds until TD283
    # guards those three reads, which is a scripts/selftest.ps1 change and so a card of its own.
    'T98-MUT-ROOT-GUARD:M2'
    # T295/TD283, second instance, same throw at selftest.ps1:14856 and confirmed by capturing the probe
    # output rather than inferring it from the shared symptom: N24 deletes '$resultsId = Get-FileIdentity
    # $resultsFull', so the seeded batch dies before writing its results file. Its own assertion fires -
    # 17ac(l) appears five times in the captured output - and only the closing sentinel is missing, because
    # the run is already dead by then. Both seats leave together when TD283 guards the three reads.
    'T63-TD119-MUTATION-RUNNER:N24'
  )

  # ── T152-PLAN-IN-REPO：计划/漏斗产物落在哪里 ──
  # CLAUDE.md 与 specs/README.md 都把计划称作**唯一真相源**，而 .gitignore 让它**不可达**：
  # `git check-ignore -v _local/PLAN.md` 命中 `_local/` 规则，selftest 也把 _local 从每一次扫描里排除。
  # 于是整条前漏斗产出的那件东西，对每一道闸、每个 CI 作业、每个评审者、每个未来会话都是**看不见的**。
  # 代价已经写进历史：两张归档卡的 plan_ref 里带着**道歉文字**（说被引计划已 gitignored、故本卡自足），
  # 而卡模板里 plan_ref 的示例指向 `docs/PLAN.md`——一个本仓**从未存在过**的路径。
  # **留空 = 保持今天的行为，一个字节都不动**：`.gitignore` 的「内部计划永不入库」是有意的红线，
  # 下游的计划也可能载有商业敏感内容。想让计划可达的项目自己设这个字段；不想的什么都不用改。
  # 这**不会**让任何计划变好、变新或真被人读——一份没人更新的已提交计划是**陈旧**的真相源，
  # 比缺席更糟。真正能治那个的是「实现与计划必须同 diff」（DocSyncMap 已对 docs 实现的那套耦合），
  # 本卡**不建**它：产物根本不可达之前建不了。
  # 2026-09-05 (user decision, ADR 0016 item 5 follow-through): set to `docs/plans` in THIS repo so a brief or
  # plan is reachable by gates, reviewers and later sessions, and a card's `plan_ref` can point at something
  # that exists. Rubric #7's traceability chain stops being circular at the card. Downstream projects keep the
  # empty default because a product plan may carry commercially sensitive text: T289-PLANDIR-INIT-RESET makes
  # init-scaffold.ps1 write '' here for a generated project (shipped in 0.47.0), and T292 anchors that
  # rewrite to this assignment line, so a look-alike inside a comment is never touched.
  MutationAnchorPending = @()
  MutationEvidencePending = @()
  PlanDir = ''

  UpstreamResidentBudgets = @{
    # Must stay equal to DocBudgets['CLAUDE.md'] - the resident view of the same file. The guard reports a
    # disagreement rather than silently preferring one, so the two copies cannot drift apart.
    'CLAUDE.md'         = 15000
    # Sum of every .claude/skills/*/SKILL.md `description:` block, measured live and never from a list.
    'SkillDescriptions' = 10250
  }

  # ── 本项目是否**分发**软件（许可闸 GPL 触发点判定，⚖️ 非法律意见）──
  # 默认 $true（保守/fail-closed，行为与今日一致）。GPL 系 copyleft 的义务触发点是**分发**——
  # 若你的项目**从不分发软件**（纯内部工具 / 纯 SaaS 后端且不随产品交付二进制），设 $false 会让
  # check-licenses.ps1 把**纯 GPL**依赖从致命降为黄牌（人工确认）。**注意仅纯 GPL**：
  # AGPL/Affero(网络触发)、SSPL(SaaS 触发)、EUPL(分发+通信触发)、non-commercial/研究限(用途触发)
  # 触发点与分发无关，**一律仍致命、本旗不降级**。变 public（开源=分发源码）请用 -Strict 复核。
  Distributes = $true

  # ── R3 第二评审后端（L26 模型无关）──：留空 '' => 内置默认 = codex CLI。
  # 设为任意命令模板即换后端（如自托管模型 / 另一家 CLI）：该命令须**从 stdin 读 prompt**、
  # 把裁决 JSON（{"verdict":"pass|block","reasons":[]}）**写到 $env:REVIEW_OUT 指向的路径**
  # （$env:REVIEW_WT = 被审工作树）。review.ps1 据此解析裁决，与 codex 默认路径同构。
  # 核心闸门「第二独立模型对抗评审」是方法论；codex 只是当前默认实现，可随时替换。
  # 安全告知（TD20）：自定义评审后端**无沙箱**（对比默认 codex 的 workspace-write 沙箱——写面=工作树+系统临时、网络关闭），
  # 且运行于含攻击者可控 diff 的 prompt 上，对 $env:REVIEW_WT 指向的工作树有全读写与网络能力——
  # 接入方宜自加进程隔离/只读挂载等约束。
  ReviewCommand = ''

  # ── R3 评审状态检查名（L26 工具无关）──：commit-status 的 context 名，由 review.ps1（回贴状态）与
  # gh-bootstrap.ps1（分支规则集必需检查名）**从这一处**读取，治「工具名 'codex-review' 硬编码进永久契约 +
  # 在两处重复的魔法字面量」。留空 '' => 回退向后兼容默认 'codex-review'；换非 codex 后端可改名（如 'r3-review'）。
  ReviewStatusContext = ''

  # ── R3 评审模型 / 推理档位（当前默认后端 codex 的启动参数）──
  # T66（2026-08-15）：**不再传 --ignore-user-config**——五探针实证该 flag 把 codex 声明沙箱恒降为
  # read-only（-s workspace-write / --add-dir / -c sandbox_mode / trust_level 全被无视），而模型按启动横幅
  # 自审，时而拒做验证（「无法验证测试证据」偶发 block）⇒ 同 SHA 裁决掷骰。钉住的免疫面如实收窄为
  # 「model/effort 两键经 argv 送达」（CLI flag 优先于用户级 config，17z 锁送达）；用户级其余键
  # （mcp_servers / notify 等）重新参与——solo 威胁模型下接受，记账见 docs/TRUST-MANIFEST.md。
  # 留空 '' => 沿用评审后端自身的默认（codex 读**用户级** ~/.codex/config.toml）。**建议显式钉住**：
  # 那个文件是用户级、且会被 Codex 桌面应用改写——2026-07-10 实测桌面端把 model 改成当时 CLI 不支持的值，
  # 评审者启动即 400、review.ps1 fail-closed block，**合并闸对所有 PR 静默失效**。钉在项目配置里即免疫此类外部漂移。
  # 优先级：review.ps1 的 -Model/-Effort 参数 > 本两项 > 后端自身默认（留空即后者，保「空配置仍可跑」）。
  # L26 工具无关：换 ReviewCommand 后端后，本两项只经 $env:REVIEW_MODEL / $env:REVIEW_EFFORT 透传，
  # 由该后端自行解释（不做 codex 的枚举校验——别人的档位命名不归 codex 管）。
  ReviewModel = 'gpt-5.6-sol'

  # 推理档位。留空 '' => 后端默认。**合法值随模型而异**，本仓刻意不硬编码枚举：
  #   实测（2026-07-10，codex-cli 0.144.1）gpt-5.6-sol / -luna 接受 max、却**拒** minimal，
  #   而 API 的通用参数报错又把 minimal 列为合法——两者不同源，任何静态列表都会误拒/误放。
  # 填错即评审者启动失败 → 写不出裁决 → review.ps1 既有 fail-closed 路径 block（控制台可见后端原文报错）。
  # 本仓 PR 常触及闸/评审者本体（高风险面），故取 high。T104 起这是**基准档**：留空或 ReviewEffortBySize
  # 为空时对每个 diff 一律用它（T104 之前的唯一行为）。
  ReviewEffort = 'high'

  # ── T104: size-keyed reasoning effort (upstream issue #205) ──
  # Empty @{} (the shipped default) => ReviewEffort above applies to every diff, unchanged.
  # Non-empty => a two-line doc edit stops buying the same reasoning spend as a 2,700-line format change.
  #   Buckets are by changed lines: small <= 100, medium <= 800, large above. The thresholds live with the
  #   resolver (Resolve-ScaffoldReviewEffort, scripts/_guard.ps1) so this manifest stays data. An
  #   undeclared bucket falls back to the LARGEST declared effort, never to the base or the backend
  #   default: a gap must not quietly buy a cheaper review than the author declared.
  # Shipped EMPTY on purpose, for the same reason gate 17z has init-scaffold clear ReviewModel/ReviewEffort:
  #   legal effort names vary by model and are only ever validated against the CLI version measured here,
  #   so a populated map must not ride the template downstream. Fill it in per project after testing.
  #   Example: @{ small = 'low'; medium = 'medium'; large = 'high' }
  # T162/TD162：**填上**这张表。T104 把机器造好了（both-shapes 契约、缺档回退到已声明的最高档、闸 17z 四条断言），
  # 但表是空的下发的，于是 `ReviewEffort = 'high'` 仍适用于每一个 diff——一次 10 行的 lessons 编辑，
  # 与一次 600 行的闸门改动，用同一档推理。
  # **只有 small 变便宜**：medium/large 仍是 high，因为 CLAUDE.md 记的理由（本仓 PR 常触及闸门、评审者本体、
  # 模板面）对 small 以上的任何 diff 都没变。三档**全部显式写出**，不靠「缺档回退到最高档」补中间那档——
  # 让读者一眼看见的是意图，而不是一个恰好等价的兜底。
  # 桶边界用解析器的既有默认（SmallMax=100 / MediumMax=800 行），本卡不动它：挪边界是另一个判断、另一份证据负担。
  # 空 => 后端默认（T104 之前的行为），且 init-scaffold 会清空它——<模型,档位> 只验证于上游当时的 codex CLI 版本。
  ReviewEffortBySize = @{ small = 'low'; medium = 'high'; large = 'high' }

  # R3 评审者（单次意见/评审）wall-clock 超时秒数 = 单轮成本上限。留空/0 => review.ps1 内建 600s。
  # 优先级：review.ps1 的 -TimeoutSec 参数 > 本项 > 内建 600s。
  # T68 取 1200（旧值 3600 按 t36set 时代「沙箱内全量 selftest ~13min ×2」校准；T63/T65 提速后全量 ~550s，
  # 且提示词已引导滤过跑）——1200 容一遍全量 + 读 diff + 出裁决；个别大 diff 超时用 -TimeoutSec 单次放宽，不动全局。
  # 2026-09-03 (user decision): 1200 -> 2400. Measured: the in-ship R3 hit [R3-REVIEWER-TIMEOUT] at 1200 s on
  # T274 (PR #358, ~700-line diff) and again on T280 (PR #360); the 2400 s re-run returned a real verdict both
  # times. Under ADR 0016 a Tier-S spec-axis block is binding (T277), so a timeout must not stand in for a verdict.
  ReviewTimeoutSec = 2400

  # Unsuccessful reviewer attempts allowed on one branch before human adjudication. Every post-invocation
  # block spends the legacy counter, including timeout/no-output/malformed/tool failures; early setup failures
  # and routed skips do not. 0 disables the cap. `review.ps1 -ResetRounds` clears only the local throttle
  # counter and retains round/history artifacts, then returns without invoking the reviewer.
  ReviewRoundCap = 2

  # ── R3 评审的角色（T68：用户 2026-08-15 减法裁定「合并闸=确定性闸；越用越薄」）──
  # 留空 ''（默认）= **意见模式**：ship 不调评审、不因评审阻断；要第二意见随时手动跑 scripts/review.ps1
  # （完整裁决 JSON + 可选 -PostStatus 回贴，只输出意见）。'required' = 旧强制闸行为（opt-in：ship 内
  # 评审 block 即停、CI 检查闸绑定被评审 sha、远端 ship 无后端 fail-fast）。
  ReviewGate = 'required'

  # ── T104: content-derived review routing (upstream issue #205) ──
  # Empty @{} (and any downstream that predates this field) => every diff draws a review, the same
  # "empty means off" degradation FrozenPaths / DocSyncMap / DocBudgets give a freshly initialized repo.
  # Non-empty => a diff may skip the review only when ALL THREE hold: every changed path matches
  #   AllPathsMatch, none sits under a NeverPrefixes directory, and none is a NeverPaths file.
  # The decision is derived from DIFF CONTENT and is not reachable from any caller flag. That is exactly
  #   the line between this and -SkipReview: -SkipReview is caller-REQUESTED, so it must exit non-zero
  #   (skip is not approval); this one can only be EARNED by the diff being markdown-only and gate-free.
  # A skip skips the advisory second-model review ONLY. check-secrets, check-scope and every deterministic
  #   gate run regardless, so a gate-read markdown file is still gated by whichever gate reads it.
  # NeverPrefixes is not redundant with AllPathsMatch: a .ps1 is already excluded for not being markdown,
  #   so what this list actually decides is MARKDOWN LIVING UNDER A GATE-BEARING DIRECTORY.
  # Judged by Get-ScaffoldReviewRouteDecision (scripts/_guard.ps1); consumed by scripts/review.ps1.
  ReviewSkipWhen = @{}

  # ── T301: review INTENSITY, keyed on the tier the card's allow_paths COMPUTE (ADR 0016 item 1) ──
  # Empty @{} (and any downstream that predates this field) => today's behaviour exactly: every reviewed
  # diff draws the one adversarial pass it draws now. Non-empty => a map from computed tier ('S' / '1' /
  # '0') to a class in 'adversarial' / 'advisory' / 'skip'. This is the ONLY tier-to-class table in the
  # repo; review.ps1 resolves it and holds no second copy.
  # The dial can only LOWER. The effective class is the LOWER of this map's value and the ceiling today's
  # behaviour sets ('adversarial'), so no value here buys a card a DEEPER read than it already gets, and a
  # class name this list does not know resolves to that ceiling rather than to the cheaper neighbour.
  # The tier is computed from the BASE card, the copy the ship's scope gate reads, so a branch cannot
  # re-tier itself into a cheaper review by editing its own allow_paths.
  # Why tier and not size: ReviewEffortBySize already keys on changed lines, and a 300-line doc card draws
  # the same 40-minute adversarial read as a 300-line enforcer change. 80% of the last 813 commits touch no
  # code file (ADR 0016), so most R3 minutes are spent where the blast radius is a document.
  # Consumed by scripts/review.ps1, sentinel [R3-INTENSITY]. The values below are THIS repo's decision
  # (T301), and the trade-off they buy is recorded on that card and in docs/QUALITY-RUBRIC.md section 4.
  ReviewIntensityByTier = @{ 'S' = 'adversarial'; '1' = 'adversarial'; '0' = 'advisory' }

  # 经验系统「必须层」（CLAUDE.md 经验铁律）封顶条数。超限须淘汰最不活跃项回按需层。
  LessonsMustCap = 10

  # ── 项目规模档位（软提示）──：建议「跳过哪些交付链」，治「小项目被全套流程拖慢」。
  # T0 极简（脚本/玩具/一次性）· T1 标准（多数项目）· T2 完整（大/长周期/团队/合规）。
  # 注：T2 的「团队/合规」指**项目复杂度**（更重流程/审计），非「脚手架提供多人组织治理」——
  #   git 层控制仍锁单个人账号（见 _guard.ps1 + docs/SECURITY.md §4 + tech-debt TD14）。
  # 纯软提示：AI/人据此裁剪流程；**不做强制机制、不做 init 物理裁剪**。按规模档位表见 docs/IDEA-TO-PLAN.md。
  # TD180：`plan-forge.mjs` 的审计**深度**也按档位路由（T0 跳过 / T1 三个 lens / T2 全套对抗），但它读的是
  #   调用方传进 args 的 `tier` **字面量**，本字段只是那个参数的**起点建议**、**不被自动读取**。两点理由，
  #   都别改回去：① 工作流脚本没有文件系统访问权，读不了本文件（与 PlanDir→planPath 同一个耦合形态）；
  #   ② 更要紧的是**语义不同**——本字段答的是「跳过哪些交付链」（它的 T1 行恰恰**要跑**那个漏斗），
  #   而审计深度是**逐计划**的判断。已量过的反面：`Get-ScaffoldProjectTier` 在键缺失**和值为空**时都回退
  #   'T1'，本仓的值又正是 'T1'，所以把它内插进调用点等于给这里每份计划静默选最浅档。用法见 docs/PLAN-FORGE.md。
  ProjectTier = 'T1'

  # ── 脚手架版本（溯源 + fleet 回填锚点）──：本模板自身的版本，**非**项目级可填项。
  # init-scaffold 把它戳进下游 CLAUDE.md footer，便于日后对照上游、回填脚手架改进。
  # 发布脚手架改进的仪式（TD12）：① 在此 bump（semver x.y.z）；② 在 CHANGELOG.md 顶部加一条
  #   `## [x.y.z] - YYYY-MM-DD`（selftest 闸 ⑧ 8.0c 强制：顶条目须 == 本字段）；③ 合并后打 git tag `vx.y.z` 并推送
  #   （下游据 tag + CHANGELOG 回填，见 TEMPLATE-README「升级已 init 的下游」）。selftest 闸 ⑧ 校验格式并验证戳入下游。
  ScaffoldVersion = '0.47.0'

  # ── 生成溯源（不可变 · T190/上游 issue #266）──：本项目**被生成时**的脚手架版本。
  # `ScaffoldVersion` 会随回填前进（下游据此表达「已评估到哪一版」）；本字段**永不改**，
  # 因为它回答的是另一个问题：「当初从哪一版长出来的」。CLAUDE.template.md 的下游 footer
  # 早就承诺了这一点（「它不会因你回填过而改变」），但同一个字段同时被 scaffold-sync /
  # triage 当作 fleet 账本的 **fail-closed 下限**用——账本读不出来时（缺哨兵/重复哨兵/坏行）
  # 回落到它。下限若能随回填上升，「读不出来就当你是最新的」，正好把 fail-closed 变成 fail-open。
  # 故：**下限绑在这个不会动的值上**。留空 '' => 回退 `ScaffoldVersion`（老的单字段下游行为不变）。
  ScaffoldOriginVersion = '0.29.0'

  # -- Upstream scaffold (the fleet loop's far end) --: the repository this project was generated
  # from, as '<owner>/<repo>'. Read by scripts/scaffold-sync.ps1 for BOTH directions of the loop:
  # staleness checks against its release tags, and filing an issue back when a scaffold-level defect
  # is found downstream. Empty '' => the scaffold's own home is used. Forked the scaffold? Point this
  # at your fork, so downstream projects report to the fork that actually maintains them.
  # In the scaffold repository itself this equals `origin`, which is how the triage probe knows it is
  # looking at the upstream and stays silent instead of reporting the scaffold as stale against itself.
  UpstreamRepo = 'Asun28/claude-devops-scaffold'
}

# 便捷解析：取 ProjectName（留空则回退仓库目录名）。$RepoRoot 由调用方传入。
function Get-ScaffoldProjectName {
  param([Parameter(Mandatory)][string]$RepoRoot)
  # ContainsKey guard (T113): StrictMode 下取缺失键会抛 PropertyNotFoundException——旧/裁剪过的下游 _config 须优雅降级，非崩溃。
  if ($script:ScaffoldConfig.ContainsKey('ProjectName') -and $script:ScaffoldConfig.ProjectName) { return $script:ScaffoldConfig.ProjectName }
  return (Split-Path $RepoRoot -Leaf)
}

# 便捷解析：取经配置的 GhAccount，未配置即 fail-closed 报错（守卫调用）。
function Get-ScaffoldGhAccount {
  # ContainsKey guard (T113): StrictMode 下取缺失键会抛 PropertyNotFoundException——旧/裁剪过的下游 _config 须优雅降级，非崩溃。 本项 fail-closed 的是**缺账号**这件事本身，抛的必须是下面这条可操作文案，而不是 StrictMode 的属性错误。
  $a = if ($script:ScaffoldConfig.ContainsKey('GhAccount')) { $script:ScaffoldConfig.GhAccount } else { '' }
  if (-not $a) {
    throw "脚手架未配置 GitHub 账号：请编辑 scripts\_config.ps1 的 GhAccount（或跑 init-scaffold.ps1）。这是 fail-closed 设计，避免误推到错误账号。"
  }
  return $a
}

# 便捷解析：取脚手架版本（溯源戳；未设回退 'unknown'）。
function Get-ScaffoldVersion {
  # ContainsKey guard (T113): StrictMode 下取缺失键会抛 PropertyNotFoundException——旧/裁剪过的下游 _config 须优雅降级，非崩溃。
  if (-not $script:ScaffoldConfig.ContainsKey('ScaffoldVersion')) { return 'unknown' }
  $v = $script:ScaffoldConfig.ScaffoldVersion
  if (-not $v) { return 'unknown' }
  return $v
}

# Convenience: the IMMUTABLE generation provenance (T190, upstream issue #266). This is the value the fleet
# ledger's fail-closed floor is bound to, and the reason it is a separate field is that a floor which can
# rise is not a floor: Get-SyncedVersion answers $Fallback on every refusal path, so if $Fallback were the
# current evaluated version, an unreadable ledger would report the project CURRENT instead of reporting
# work to do. Empty or missing returns Get-ScaffoldVersion, which is what makes a legacy one-field
# downstream behave exactly as it did before - origin and current are the same thing until someone splits
# them. ContainsKey guard for the same StrictMode reason every accessor above carries one (T113).
function Get-ScaffoldOriginVersion {
  if (-not $script:ScaffoldConfig.ContainsKey('ScaffoldOriginVersion')) { return (Get-ScaffoldVersion) }
  $ov = $script:ScaffoldConfig.ScaffoldOriginVersion
  if (-not $ov) { return (Get-ScaffoldVersion) }
  return $ov
}

# Convenience: the upstream scaffold repository (fleet loop). ContainsKey guard so that an older
# downstream _config.ps1 predating this field still runs under StrictMode; missing or empty returns
# '' and the caller falls back to its own default.
function Get-ScaffoldUpstreamRepo {
  if (-not $script:ScaffoldConfig.ContainsKey('UpstreamRepo')) { return '' }
  $r = $script:ScaffoldConfig.UpstreamRepo
  if (-not $r) { return '' }
  return $r
}

# 便捷解析：取开发 worktree 根。配置非空即用之；留空则按 OS 取默认（可移植：mac/Linux 不再吃 D:\ 盘符）。
function Get-ScaffoldWorktreeRoot {
  # ContainsKey guard (T113): StrictMode 下取缺失键会抛 PropertyNotFoundException——旧/裁剪过的下游 _config 须优雅降级，非崩溃。
  $w = if ($script:ScaffoldConfig.ContainsKey('WorktreeRoot')) { $script:ScaffoldConfig.WorktreeRoot } else { '' }
  # T218: a configured root is honoured only on a platform that can USE it. A drive-shaped value is
  # meaningless off Windows - Join-Path throws and every task.ps1 leg dies (measured: post-merge matrix
  # 33277797926, ubuntu seed-post, four gate-17aa legs at task.ps1:88). 8.0b guards the DEFAULT branch
  # below against the same C02 hazard; this guards the escape hatch from it. Non-drive roots (a POSIX
  # absolute path) stay honoured everywhere, so the hatch itself is not closed.
  if ($w) {
    $looksLikeDrive = $w -match '^[A-Za-z]:'
    if ($IsWindows -or -not $looksLikeDrive) { return $w }
  }
  if ($IsWindows) {
    # 系统盘根的浅目录（规避 Windows MAX_PATH），用 $env:SystemDrive 而非硬编码 D:——
    # 治「单盘机器（只有 C:）首次 task.ps1 -Phase start 的 New-Item 抛 DriveNotFoundException、
    # 错误既不提 WorktreeRoot 也不提 _config」（30-lens C02）。要用别的盘显式填 WorktreeRoot。
    $sysDrive = if ($env:SystemDrive) { $env:SystemDrive } else { 'C:' }
    return (Join-Path $sysDrive 'wt')
  }
  return (Join-Path $HOME '.wt')
}

# 便捷解析：取「是否分发软件」旗（许可闸 GPL 触发点判定）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——保守回退 $true（视为分发 → GPL 仍致命）。
function Get-ScaffoldDistributes {
  if ($script:ScaffoldConfig.ContainsKey('Distributes')) {
    return [bool]$script:ScaffoldConfig.Distributes
  }
  return $true
}

# 便捷解析：取项目规模档位（软提示；未设回退 'T1' 标准档）。
function Get-ScaffoldProjectTier {
  # ContainsKey guard (T113): StrictMode 下取缺失键会抛 PropertyNotFoundException——旧/裁剪过的下游 _config 须优雅降级，非崩溃。
  if (-not $script:ScaffoldConfig.ContainsKey('ProjectTier')) { return 'T1' }
  $t = $script:ScaffoldConfig.ProjectTier
  if (-not $t) { return 'T1' }
  return $t
}

# 便捷解析：取 R3 评审状态检查名（单一来源；留空回退向后兼容默认 'codex-review'）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——优雅退回默认。
function Get-ScaffoldReviewStatusContext {
  if ($script:ScaffoldConfig.ContainsKey('ReviewStatusContext') -and $script:ScaffoldConfig.ReviewStatusContext) {
    return $script:ScaffoldConfig.ReviewStatusContext
  }
  return 'codex-review'
}

# 便捷解析：取 R3 评审模型（留空 '' => 由评审后端自身默认决定，不传 -m）。
# ContainsKey 守卫：旧 _config（未含该键）在 StrictMode 下直接取键会抛——优雅退回 ''（= 后端默认，行为不变）。
function Get-ScaffoldReviewModel {
  if ($script:ScaffoldConfig.ContainsKey('ReviewModel')) { return [string]$script:ScaffoldConfig.ReviewModel }
  return ''
}

# 便捷解析：取源脚本 ↔ 权威文档耦合表；旧配置无此键时优雅降级为空表。
function Get-ScaffoldDocSyncMap {
  if ($script:ScaffoldConfig.ContainsKey('DocSyncMap') -and $script:ScaffoldConfig.DocSyncMap) { return $script:ScaffoldConfig.DocSyncMap }
  return @{}
}

# Convenience: the per-file doc character ceilings (T89-DOCBUDGETS). ContainsKey guard so an older downstream
# _config.ps1 predating this field still runs under StrictMode; missing or empty returns @{} and the gate is off.
# T231-CARD-BUDGET (TD235): the default card net-line budget. ContainsKey guard so a downstream _config.ps1
# predating this field still runs under StrictMode; a missing key, an empty value or a non-integer returns
# 0, which the decision core reads as "no default" - report the count, trip nothing, block nothing.
function Get-ScaffoldCardBudgetDefault {
  if ($script:ScaffoldConfig.ContainsKey('CardBudgetDefault') -and $script:ScaffoldConfig.CardBudgetDefault) {
    $n = 0
    if ([int]::TryParse([string]$script:ScaffoldConfig.CardBudgetDefault, [ref]$n) -and $n -gt 0) { return $n }
  }
  return 0
}

# The fraction of a card's declared budget at which the meter trips. Same degradation: missing, empty or
# out of the (0,1] range returns 0, which the decision core reads as "never trip". Clamping lives HERE
# rather than in the core so every consumer gets the same answer from one place - a second entry point
# that clamped differently is exactly how the meter and the ship gate would silently disagree.
function Get-ScaffoldCardBudgetTripFraction {
  if ($script:ScaffoldConfig.ContainsKey('CardBudgetTripFraction') -and $script:ScaffoldConfig.CardBudgetTripFraction) {
    $d = 0.0
    if ([double]::TryParse([string]$script:ScaffoldConfig.CardBudgetTripFraction, [ref]$d) -and $d -gt 0 -and $d -le 1) { return $d }
  }
  return 0.0
}

function Get-ScaffoldDocBudgets {
  if ($script:ScaffoldConfig.ContainsKey('DocBudgets') -and $script:ScaffoldConfig.DocBudgets) { return $script:ScaffoldConfig.DocBudgets }
  return @{}
}

# T150: the always-on payload ceilings. Degrades to an empty map on a missing key or an empty value, which
# is how a freshly initialised downstream keeps the check off - the same contract every other budget has.
# ADR 0016 / T273: the Tier-S path list, read by Get-ScaffoldCardTier. ContainsKey guard so a downstream
# _config.ps1 predating this field still runs under StrictMode; missing or empty returns @(), which the
# decision reads as "tiering is off" and answers S for every card - today's bar, and the safe direction.
#
# THE FROZEN FOLD HAPPENS HERE, in the accessor, and deliberately not in the decision: a frozen contract is
# Tier S by definition, and folding it in at the config edge keeps Get-ScaffoldCardTier a pure function of
# its arguments, so its declared examples never have to read a real configuration.
#
# T274 (ADR 0016 item 8) MARKS what it folds. A FrozenPaths entry is a REGEX FRAGMENT by its own contract
# (.claude/hooks/guard-frozen.ps1 matches it with -match) while a tier entry is a path glob, and T273 folded
# the two into one flat list of strings where nothing downstream could tell them apart - so every fragment
# written with a metacharacter silently stopped raising the tier. The marker is a declared 'frozen:' prefix,
# a shape no repo-relative path can take, and it is read in exactly one place: Test-ScaffoldTierSEntryMatch
# in scripts/_cards.ps1, which matches the remainder as a regex fragment. It also lets the decision keep the
# off switch honest - a list holding ONLY folded entries is still an empty TierSPaths, so a project that
# empties the list while holding frozen contracts is OFF and every card is S, today's bar, rather than the
# frozen entries alone deciding tiers for a project that asked for no tiering at all.
function Get-ScaffoldTierSPaths {
  $entries = @()
  if ($script:ScaffoldConfig.ContainsKey('TierSPaths') -and $script:ScaffoldConfig.TierSPaths) { $entries += @($script:ScaffoldConfig.TierSPaths) }
  if ($script:ScaffoldConfig.ContainsKey('FrozenPaths') -and $script:ScaffoldConfig.FrozenPaths) { $entries += @(@($script:ScaffoldConfig.FrozenPaths) | ForEach-Object { "frozen:$_" }) }
  return @($entries)
}

# ADR 0016 / T273: the Tier-0 path list. Same ContainsKey degradation; empty means no card computes 0, so
# an older downstream keeps the stricter answer rather than silently dropping cards to the cheapest tier.
function Get-ScaffoldTier0Paths {
  if ($script:ScaffoldConfig.ContainsKey('Tier0Paths') -and $script:ScaffoldConfig.Tier0Paths) { return @($script:ScaffoldConfig.Tier0Paths) }
  return @()
}

# T296/TD224: the integration-e2e closure command for verify.ps1 gate 2. A missing key or an empty value
# degrades to EMPTY, which verify.ps1 reads as the explicit NOT CONFIGURED state - off, never broken, and
# never a PASS. Returned as an ARRAY so the caller splats argv and never builds a shell string.
function Get-ScaffoldE2ECommand {
  if ($script:ScaffoldConfig.ContainsKey('E2ECommand') -and $script:ScaffoldConfig.E2ECommand) { return @($script:ScaffoldConfig.E2ECommand) }
  return @()
}

# T158: 本项目声明为冗余的 selftest 冒烟扫描 id 列表。缺键/空值一律退化为**空**（什么都不跳）。
function Get-ScaffoldSelftestSkipScans {
  if ($script:ScaffoldConfig.ContainsKey('SelftestSkipScans') -and $script:ScaffoldConfig.SelftestSkipScans) { return @($script:ScaffoldConfig.SelftestSkipScans) }
  return @()
}

# T211/TD206: the dated baseline of registry entries known not to resolve. Degrades to EMPTY on a missing
# key or an empty value - and empty means the check still runs and excuses nothing, which is the opposite
# of the other tables above: an empty DocBudgets turns its gate off, an empty list here turns it fully on.
# That asymmetry is deliberate. A downstream arriving with no exemptions should be held to a clean corpus,
# not have the check silently disabled, and its specs/mutations/ ships holding only README.md anyway.
function Get-ScaffoldMutationAnchorPending {
  if ($script:ScaffoldConfig.ContainsKey('MutationAnchorPending') -and $script:ScaffoldConfig.MutationAnchorPending) { return @($script:ScaffoldConfig.MutationAnchorPending) }
  return @()
}

# Same asymmetry as the anchor list above and for the same reason: a downstream arriving with no
# exemptions is held to a clean corpus rather than having the check silently disabled.
function Get-ScaffoldMutationEvidencePending {
  if ($script:ScaffoldConfig.ContainsKey('MutationEvidencePending') -and $script:ScaffoldConfig.MutationEvidencePending) { return @($script:ScaffoldConfig.MutationEvidencePending) }
  return @()
}

# T152：漏斗产物目录。**留空 => '_local'**，即今天的位置（gitignored），行为逐字不变。
# 设成别的（如 'docs/plan'）即让计划进入受追踪面，从而对闸/CI/评审者/后续会话可达。
function Get-ScaffoldPlanDir {
  if ($script:ScaffoldConfig.ContainsKey('PlanDir') -and -not [string]::IsNullOrWhiteSpace($script:ScaffoldConfig.PlanDir)) { return [string]$script:ScaffoldConfig.PlanDir }
  return '_local'
}

function Get-ScaffoldResidentBudgets {
  if ($script:ScaffoldConfig.ContainsKey('ResidentBudgets') -and $script:ScaffoldConfig.ResidentBudgets) { return $script:ScaffoldConfig.ResidentBudgets }
  return @{}
}

# 便捷解析：取 R3 推理档位（留空 '' => 后端默认）。合法值校验在 review.ps1（只对默认 codex 路径生效，L26）。
function Get-ScaffoldReviewEffort {
  if ($script:ScaffoldConfig.ContainsKey('ReviewEffort')) { return [string]$script:ScaffoldConfig.ReviewEffort }
  return ''
}

# T104: the size-keyed effort map (empty => the flat ReviewEffort above applies to every diff).
# Reading only; the size-to-bucket resolution is Resolve-ScaffoldReviewEffort in scripts/_guard.ps1.
function Get-ScaffoldReviewEffortBySize {
  if ($script:ScaffoldConfig.ContainsKey('ReviewEffortBySize') -and $script:ScaffoldConfig.ReviewEffortBySize) { return $script:ScaffoldConfig.ReviewEffortBySize }
  return @{}
}

# T104: the content-derived review-routing predicate (empty => every diff draws a review).
# Reading only; the decision is Get-ScaffoldReviewRouteDecision in scripts/_guard.ps1.
function Get-ScaffoldReviewSkipWhen {
  if ($script:ScaffoldConfig.ContainsKey('ReviewSkipWhen') -and $script:ScaffoldConfig.ReviewSkipWhen) { return $script:ScaffoldConfig.ReviewSkipWhen }
  return @{}
}

# T301: the tier-keyed review-intensity map (empty => today's behaviour, one adversarial pass per diff).
# Reading only; the class resolution and the lowest-wins rule are in scripts/review.ps1, sentinel [R3-INTENSITY].
function Get-ScaffoldReviewIntensityByTier {
  if ($script:ScaffoldConfig.ContainsKey('ReviewIntensityByTier') -and $script:ScaffoldConfig.ReviewIntensityByTier) { return $script:ScaffoldConfig.ReviewIntensityByTier }
  return @{}
}

# -- T113-CORE-SELFCHECK-CONFIG (TD140 / ADR 0011): the declared self-check for the accessors --
# Fourteen thin readers over one hashtable, and every one of them owes the same contract - the hardest-edged
# configuration rule in this repo: **empty or missing means OFF, gracefully**. `FrozenPaths = @()` turns the
# frozen guard off, `DocSyncMap = @{}` turns the doc-drift gate off, `GhAccount = ''` fails closed instead of
# throwing, and a freshly initialised downstream walks every one of those paths on its first run.
# Two failure modes, and both are what the -Variant shapes below name:
#   missing-key-throws   - read the key without a ContainsKey guard. Under Set-StrictMode -Version Latest a
#                          missing hashtable key raises PropertyNotFoundException, so an older downstream
#                          _config.ps1 that predates a field does not degrade - it crashes.
#   empty-is-configured  - treat an empty value as configured rather than as off. That is what would arm the
#                          account guard with no account, or hand a caller '' where it expected a default.
# ADR 0011 rejected one self-check per accessor as ceremony, and rejected asserting the SHIPPED VALUES as
# actively wrong: a case pinning GhAccount to this repo's account would fail in every downstream project,
# which is the opposite of what _config.ps1 is for. So the cases below swap $script:ScaffoldConfig for a
# local hashtable and restore it in a finally - the real configuration is never read.
function Test-ScaffoldConfigAccessorVia($Accessor, $ConfigHash, $Variant) {
  $saved = $script:ScaffoldConfig
  try {
    $script:ScaffoldConfig = $ConfigHash
    if ($Variant -eq 'missing-key-throws') {
      # The unguarded read every guarded accessor exists to avoid.
      $key = $Accessor -replace '^Get-Scaffold', ''
      return $ConfigHash.$key
    }
    if ($Variant -eq 'empty-is-configured') {
      $key = $Accessor -replace '^Get-Scaffold', ''
      if ($ConfigHash.ContainsKey($key)) { return $ConfigHash[$key] }
      return (& $Accessor)
    }
    return (& $Accessor)
  }
  finally { $script:ScaffoldConfig = $saved }
}

# Declared examples for the accessor contract. Returns findings as strings and never throws - a case that
# raises is caught and reported as a finding, because "it threw" IS the defect this table is about.
# Hermetic: every config is a literal hashtable, and the real one is restored in a finally.
function Test-ScaffoldConfigAccessorExamples {
  [CmdletBinding()]
  param([ValidateSet('missing-key-throws', 'empty-is-configured')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  # One case per accessor CLASS, chosen so the whole degradation matrix is covered without 14 near-copies:
  # a string reader with a fallback, a string reader with an empty fallback, a map reader, a bool reader,
  # and the two readers whose fallback is computed rather than constant.
  $cases = @(
    @{ what = 'a map accessor on a config with the key MISSING returns an empty map, not a throw'; accessor = 'Get-ScaffoldDocSyncMap'; config = @{}; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 0) } }
    @{ what = 'a map accessor on an EMPTY map returns empty - empty means the gate is off'; accessor = 'Get-ScaffoldDocSyncMap'; config = @{ DocSyncMap = @{} }; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 0) } }
    @{ what = 'a map accessor on a populated map returns it unchanged'; accessor = 'Get-ScaffoldDocSyncMap'; config = @{ DocSyncMap = @{ 'a' = @('b') } }; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 1) -and ($r.ContainsKey('a')) } }
    @{ what = 'the budgets accessor degrades the same way on a missing key'; accessor = 'Get-ScaffoldDocBudgets'; config = @{}; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 0) } }
    # T301: the intensity dial owes the same three, and the first two are load-bearing rather than
    # ceremonial - review.ps1 reads an empty map as "today's behaviour, one adversarial pass", so an
    # accessor that threw or answered anything else on a downstream that never set the field would change
    # what every card there draws, in the direction nobody asked for.
    @{ what = 'the review-intensity accessor returns an empty map on a MISSING key, which review.ps1 reads as today behaviour and never as a cheaper class'; accessor = 'Get-ScaffoldReviewIntensityByTier'; config = @{}; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 0) } }
    @{ what = 'the review-intensity accessor reads an EMPTY map as off, the same way every other dial does'; accessor = 'Get-ScaffoldReviewIntensityByTier'; config = @{ ReviewIntensityByTier = @{} }; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 0) } }
    @{ what = 'the review-intensity accessor returns a populated tier-to-class map unchanged'; accessor = 'Get-ScaffoldReviewIntensityByTier'; config = @{ ReviewIntensityByTier = @{ '0' = 'skip' } }; check = { param($r) ($r -is [hashtable]) -and ($r.Count -eq 1) -and ($r['0'] -eq 'skip') } }
    @{ what = 'a string accessor with a named default falls back when the key is MISSING'; accessor = 'Get-ScaffoldReviewStatusContext'; config = @{}; check = { param($r) $r -eq 'codex-review' } }
    @{ what = 'a string accessor with a named default falls back when the value is EMPTY'; accessor = 'Get-ScaffoldReviewStatusContext'; config = @{ ReviewStatusContext = '' }; check = { param($r) $r -eq 'codex-review' } }
    @{ what = 'a string accessor with a named default returns a configured value'; accessor = 'Get-ScaffoldReviewStatusContext'; config = @{ ReviewStatusContext = 'my-check' }; check = { param($r) $r -eq 'my-check' } }
    @{ what = 'a string accessor whose default is EMPTY returns empty on a missing key (backend decides)'; accessor = 'Get-ScaffoldReviewModel'; config = @{}; check = { param($r) $r -eq '' } }
    @{ what = 'the upstream-repo accessor returns empty on a missing key and on an empty value alike'; accessor = 'Get-ScaffoldUpstreamRepo'; config = @{ UpstreamRepo = '' }; check = { param($r) $r -eq '' } }
    @{ what = 'a bool accessor falls back CONSERVATIVELY on a missing key - distribution is assumed, so GPL stays fatal'; accessor = 'Get-ScaffoldDistributes'; config = @{}; check = { param($r) $r -eq $true } }
    @{ what = 'a bool accessor honours an explicit false'; accessor = 'Get-ScaffoldDistributes'; config = @{ Distributes = $false }; check = { param($r) $r -eq $false } }
    @{ what = 'the tier accessor falls back to the standard tier on a missing key'; accessor = 'Get-ScaffoldProjectTier'; config = @{}; check = { param($r) $r -eq 'T1' } }
    @{ what = 'the tier accessor honours a configured tier'; accessor = 'Get-ScaffoldProjectTier'; config = @{ ProjectTier = 'T2' }; check = { param($r) $r -eq 'T2' } }
    # ADR 0016 / T273. The two list accessors are a class the table did not yet cover: a LIST reader whose
    # empty answer is a live decision rather than a switched-off gate (empty TierSPaths => every card is
    # Tier S), and the only accessor that FOLDS a second key into its answer.
    # The empty cases test for $null as well as for an empty array, and not out of caution: a function that
    # outputs @() emits NOTHING, so the value reaching a caller that did not wrap the call in @() is $null.
    # That is the contract every caller here honours (`@(Get-ScaffoldTierSPaths).Count`), and writing the
    # case as `@($r).Count -eq 0` alone would have asserted a shape the accessor cannot return.
    @{ what = 'the Tier-S accessor returns EMPTY on a missing key - the decision reads that as tiering off, never as a crash'; accessor = 'Get-ScaffoldTierSPaths'; config = @{}; check = { param($r) ($null -eq $r) -or (@($r).Count -eq 0) } }
    # T274: the fold now MARKS what it folded, because a FrozenPaths entry is a regex fragment and a tier
    # entry is a path glob - flattened together, nothing downstream could match either one correctly. The
    # marker is asserted here rather than described: it is the wire between this accessor and the decision.
    @{ what = 'the Tier-S accessor FOLDS FrozenPaths in, MARKED, so a frozen contract is Tier S without being listed twice and is still matched as the regex fragment it is'; accessor = 'Get-ScaffoldTierSPaths'; config = @{ TierSPaths = @('scripts/task.ps1'); FrozenPaths = @('contracts/') }; check = { param($r) (@($r).Count -eq 2) -and (@($r) -contains 'frozen:contracts/') -and (@($r) -contains 'scripts/task.ps1') } }
    @{ what = 'the Tier-S accessor folds a frozen path in even when the tier list itself is empty - the DECISION reads the marker to keep the off switch, so this list never has to lie about what is configured'; accessor = 'Get-ScaffoldTierSPaths'; config = @{ TierSPaths = @(); FrozenPaths = @('contracts/') }; check = { param($r) (@($r).Count -eq 1) -and (@($r) -contains 'frozen:contracts/') } }
    @{ what = 'the Tier-0 accessor returns EMPTY on a missing key, so an older downstream keeps the stricter answer'; accessor = 'Get-ScaffoldTier0Paths'; config = @{}; check = { param($r) ($null -eq $r) -or (@($r).Count -eq 0) } }
    @{ what = 'the Tier-0 accessor returns a configured list unchanged'; accessor = 'Get-ScaffoldTier0Paths'; config = @{ Tier0Paths = @('docs/', 'specs/') }; check = { param($r) @($r).Count -eq 2 } }
    # T296/TD224: the three that decide whether verify.ps1 gate 2 is in its NOT CONFIGURED state. The first
    # two are the whole point of the third verdict - an unconfigured gate must be reportable as unconfigured,
    # so neither shape may return anything the caller could mistake for a configured, passing command.
    @{ what = 'the e2e-command accessor returns EMPTY on a missing key, which verify.ps1 reports as NOT CONFIGURED rather than as a pass'; accessor = 'Get-ScaffoldE2ECommand'; config = @{}; check = { param($r) ($null -eq $r) -or (@($r).Count -eq 0) } }
    @{ what = 'the e2e-command accessor treats an EMPTY array as unconfigured too, so a downstream that cleared the field gets the third state and not a silent green'; accessor = 'Get-ScaffoldE2ECommand'; config = @{ E2ECommand = @() }; check = { param($r) ($null -eq $r) -or (@($r).Count -eq 0) } }
    @{ what = 'a configured e2e command comes back as an ARGV ARRAY with its driver first, never flattened into a shell string'; accessor = 'Get-ScaffoldE2ECommand'; config = @{ E2ECommand = @('uv', 'run', 'pytest') }; check = { param($r) (@($r).Count -eq 3) -and (@($r)[0] -eq 'uv') } }
    @{ what = 'the worktree-root accessor computes an OS default on a missing key rather than returning empty'; accessor = 'Get-ScaffoldWorktreeRoot'; config = @{}; check = { param($r) ($r -is [string]) -and ($r.Length -gt 0) } }
    @{ what = 'the worktree-root accessor honours a DRIVE-shaped configured root only where a drive means something (T218)'; accessor = 'Get-ScaffoldWorktreeRoot'; config = @{ WorktreeRoot = 'X:\somewhere' }; check = { param($r) if ($IsWindows) { $r -eq 'X:\somewhere' } else { $r -ne 'X:\somewhere' } } }
    @{ what = 'a NON-drive configured root stays honoured on every platform, so the escape hatch is scoped and not closed (T218)'; accessor = 'Get-ScaffoldWorktreeRoot'; config = @{ WorktreeRoot = '/srv/wt' }; check = { param($r) $r -eq '/srv/wt' } }
    @{ what = 'the account accessor fails CLOSED on a missing key, with the actionable message rather than a StrictMode property error'; accessor = 'Get-ScaffoldGhAccount'; config = @{}; throws = '_config' }
    @{ what = 'the account accessor fails closed on an EMPTY value too'; accessor = 'Get-ScaffoldGhAccount'; config = @{ GhAccount = '' }; throws = '_config' }
    @{ what = 'the account accessor returns a configured account'; accessor = 'Get-ScaffoldGhAccount'; config = @{ GhAccount = 'someone' }; check = { param($r) $r -eq 'someone' } }
    @{ what = 'the version accessor falls back to unknown on a missing key'; accessor = 'Get-ScaffoldVersion'; config = @{}; check = { param($r) $r -eq 'unknown' } }
    # T190 (#266): the origin accessor is the only one whose fallback is ANOTHER ACCESSOR rather than a
    # constant, which is what keeps a legacy one-field downstream behaving identically after the split.
    @{ what = 'the origin accessor falls back to the CURRENT version on a missing key (config predating the split)'; accessor = 'Get-ScaffoldOriginVersion'; config = @{ ScaffoldVersion = '0.45.0' }; check = { param($r) $r -eq '0.45.0' } }
    @{ what = 'the origin accessor falls back to current on an EMPTY value too'; accessor = 'Get-ScaffoldOriginVersion'; config = @{ ScaffoldOriginVersion = ''; ScaffoldVersion = '0.45.0' }; check = { param($r) $r -eq '0.45.0' } }
    @{ what = 'a configured origin does NOT track the current version - that divergence is the whole point'; accessor = 'Get-ScaffoldOriginVersion'; config = @{ ScaffoldOriginVersion = '0.29.0'; ScaffoldVersion = '0.45.0' }; check = { param($r) $r -eq '0.29.0' } }
  )
  $findings = @()
  foreach ($c in $cases) {
    $r = $null
    $threwType = $null
    $threwMsg = ''
    try { $r = Test-ScaffoldConfigAccessorVia $c.accessor $c.config $v }
    catch { $threwType = $_.Exception.GetType().Name; $threwMsg = [string]$_.Exception.Message }
    $wantThrow = ($c.Keys -contains 'throws')
    if ($wantThrow) {
      # One accessor is SUPPOSED to throw when unset - the account guard, whose whole design is fail-closed.
      # What it must never throw is PropertyNotFoundException: that is StrictMode reporting an unguarded key
      # read, and it tells the operator nothing about what to configure.
      if (-not $threwType) { $findings += "[CONFIG-ACCESSOR-EXAMPLE] case '$($c.what)' returned '$r' instead of failing closed. [FIX] fix the accessor, never the example." }
      elseif ($threwMsg -notmatch $c.throws) { $findings += "[CONFIG-ACCESSOR-EXAMPLE] case '$($c.what)' threw $threwType, but the message does not match the expected guidance - a StrictMode PropertyNotFoundException from an unguarded key read is NOT a fail-closed design, it is a crash that names nothing an operator can act on. [FIX] guard the read with ContainsKey and throw the actionable error." }
    }
    elseif ($threwType) { $findings += "[CONFIG-ACCESSOR-EXAMPLE] case '$($c.what)' THREW $threwType instead of degrading - empty or missing must mean off, gracefully, because a freshly initialised downstream walks exactly these paths on its first run. [FIX] guard the read with ContainsKey and return the documented default." }
    elseif (($c.Keys -contains 'check') -and (-not (& $c.check $r))) { $findings += "[CONFIG-ACCESSOR-EXAMPLE] case '$($c.what)' returned '$r', which does not satisfy the case. [FIX] fix the accessor, never the example." }
  }
  return $findings
}
