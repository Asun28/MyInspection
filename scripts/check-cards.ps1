#requires -Version 7
<#
.SYNOPSIS
  任务卡静态校验：在动手前/CI 里机检 specs\tasks\*.md 的 front-matter 自洽，
  把「卡写错到 ship 才暴露」提前到 start/selftest/CI。

.DESCRIPTION
  卡片是「计划 → 可执行」的薄投影，task.ps1 / review.ps1 全程以**卡 id** 派生
  分支名 / worktree / 卡路径（specs\tasks\<id>.md），却**忽略**卡内 branch/worktree 字段——
  故 id 与文件名/branch 漂移会**静默**让 review.ps1 找错卡。本校验守住这层耦合。

  上游规则目录：除本文件后文“本地兼容边界”明确停用的项目外，以下错误 exit 1。
    - 无 front-matter（须 --- 包裹的 YAML 头）。
    - id 缺失 / 与文件名不一致 / 含非法字符（分支·worktree·文件名共用，禁空白与 \ / : ~ ^ ? * [ ]）。
    - id 不符规范命名 'T<阶段号>-<大写短横名>'（正则 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$；
      示例 T0-SCAFFOLD / T2-API / T3-REVIEW-GATE；反例 t1-foo（小写）/ T1_FOO（下划线）/ my-task（缺 T 阶段号）。
      此规范让 AI 编码 agent 能确定性地派生/自检卡名，而非靠看示例猜。The pattern and the examples it must /
      must not match are declared together as the 'card-id' entry of Get-ScaffoldCardRules in _cards.ps1;
      a downstream project that wants a different id scheme edits that one entry (T88)）。
    - status 缺失或不属 todo|in-progress|in-review|merged。
    - branch 存在却 ≠ id；worktree 末段 ≠ id（漂移）。
    - dod_command 缺失/空（task.ps1 ship 的 DoD 闸门据此执行）。
    - dod_command 是 no-op（echo/true/exit 0/Write-Host…，且无 && / | 串接真命令）——DoD 须真跑验证，
      否则卡可「假绿」过 ship（修「TDD 未被 DoD 强制」）。
    - dod_command 嵌套 `pwsh … -Command "…$var…"` 且内含会被内插的 $ 变量——task.ps1 双层 `pwsh -Command` 执行下
      内层 $var 被中间 shell 内插成空串、孙 shell ParserError exit 1，`-Phase red` 误当合法 RED（vacuous RED；TD69/L95）。
    - dod_command 嵌套 `pwsh … -Command "…"` 且该载荷**自身 spawn pwsh/powershell**、却不以显式 `exit` 收尾——
      pwsh -Command 块无显式 exit 时返回**最后一个原生命令**的退出码，故孙进程的非零成了本块的退出码：断言全过、
      命令仍非零，GREEN 不可达而 `-Phase red` 把它当合法 RED（vacuous RED 的第三扇门，与 TD69 同后果、不同成因——
      载荷里没有任何 `$`、也完全解析得通；TD153/L245）。错误信息含 ASCII 哨兵 [CARD-DOD-EXIT]，修法是给载荷补一条
      显式 `exit 0`（分支里的 exit 不算：断言全过时那条分支恰恰不执行）。判定见 _cards.ps1 的 Test-ScaffoldDodExit，
      取 dod **文本**故无需落盘卡片即可测；selftest 闸 10f(f29-f32) 回归。
    - dod_command calls a repo function (the `<Verb>-Scaffold<Noun>` convention) without asserting anywhere
      that the function exists. The three rules above all catch a vacuous RED - a dod that exits NON-ZERO
      without running. This is the mirror and it is worse. Until T270 `-Phase red` executed the field
      unwrapped while `-Phase ship` wrapped it (the premise that this was FORCED - that red must read a
      non-zero exit rather than throw - was false: that is the PARENT's preference, not the regime inside
      the child payload), and under the child's default Continue a
      CommandNotFoundException is terminating for the STATEMENT only - the enclosing
      `if (-not (Get-Foo).Bar) { exit 1 }` is abandoned, control falls through to the trailing `exit 0`,
      and the dod exits 0 with every arm silently no-op'd. A false RED is contradicted at the first GREEN
      attempt; this false GREEN is banked as a passing DoD gate and contradicts nothing, and `-Phase red`
      reports the card is already GREEN and refuses to record evidence. Measured on T242 (TD250/L308).
      T270 aligned the two phases - both build their text with Get-ScaffoldDodPayload - so a missing repo
      function now ends the unit non-zero on its own and this rule is no longer the only thing standing
      between a card and a vacuous GREEN. What it still buys is the EARLIER refusal: at card-validation
      time, with a printed repair, instead of at run time inside whichever phase ran first.
      错误信息含 ASCII 哨兵 [CARD-DOD-FN-EXISTS]，修法是每个函数补一条 `if (-not (Get-Command <name>
      -ErrorAction SilentlyContinue)) { exit 1 }`（位置不限：晚到的那条仍会被落空的语句穿透执行到）。
      判定见 _cards.ps1 的 Test-ScaffoldDodFnExists，取 dod **文本**、按 AST 判「是不是调用」（名字只出现在
      字符串字面量里不算，T234 的实卡就是这形态）；selftest 闸 10f(f33-f36) + 10r 回归。
    - allow_paths 缺失（review.ps1 据此判越界）。
    - 卡文含模板占位符 token 字面量（双大括号包裹**大写蛇形**名，-cmatch 严格大写）——真 token 只应出现在模板产物；
      混进卡文，init 干跑冒烟会替换污染卡 / 留残留占位符触发失败，卡登记直推 master 即 CI 红。错误信息含 ASCII
      哨兵 [CARD-TOKEN-LITERAL]（L61/TD111；selftest 闸10g 回归）。
    - front-matter 内出现「非缩进且不含冒号」的垃圾行（整行注释——行首 `#`——豁免，故从 _TEMPLATE.md 复制保留
      指引注释的合法卡不受影响）——错误信息含 ASCII 哨兵 [CARD-FM-GARBAGE]（消息即修法；TD112；selftest 闸10d 回归）。
    - 跨卡规则（两种模式都跑，见文末 T177/TD173）：two live cards CLAIM the same technical-debt row and that row does not declare the split.
      A claim is read from the card id and title only, never the body (body-wide, TD69 appears in 35
      archived cards and TD88 in 76 as lineage references, not as claims). A debt legitimately maps to
      several cards - the tracker's shape is 1 TD -> 1..N cards, and TD88 became nine of them - so the
      pair is accepted when the row's repayment-pointer column names every claiming card, or when one
      card carries `superseded_by`. Neither declaration surface is a new front-matter field. Reported
      with the ASCII sentinel [CARD-TD-DUP] and exits 1; judged by Get-ScaffoldDupTdClaimErrors in
      _cards.ps1, which takes the tracker TEXT so the rule is testable without a tracker on disk
      (selftest sub-gate 10j) and which switches ITSELF off when that text is empty - no tracker means
      no declaration surface, the same call the reference rule below makes on the same input (TD174).
      This is the dispatch-time half of the multi-session problem L114 covers
      only from the ship-base angle (TD147); it cannot see a collision between two sessions that have
      neither committed yet, and does not claim to.
    - 跨卡规则：parallelizable_with（可选字段）声明并行的卡对，allow_paths 归一化前缀重叠——
      对称处理（单向声明即比对）；并行 worktree 互不重叠是并行前提。
      重叠判定与列表解析带内建种子自检（同 selftest 闸 17 思路），逻辑退化即整体 FAIL。
      T177/TD173：跨卡规则**在两种模式下都跑**。-TaskId 只窄化「报告哪张卡」（scope），绝不窄化
      「id 对着什么语料解析」（corpus）——两条轴由 Get-ScaffoldCardCheckScope（_cards.ps1）分开判定。
      此前整块跨卡检查藏在 `if (-not $TaskId)` 后，而 task.ps1 三个相位都传 -TaskId，故本地闭环一条
      跨卡规则都跑不到、CI 比 ship 严格。实测：一张卡的完整式 id 解析不到任何卡，start/red/ship 全过、
      CI 连红两次。两个被否决的设计（corpus 随 scope 收窄 / 不做 scope 过滤）由声明式样例各自钉死。
    - A card declaring MORE THAN FIVE allow_paths carries no non-empty `sweep:` field (T94): above that
      threshold a card is cross-cutting by shape (L97), so it must record the grep it ran and the teaching
      faces that grep found. Reported with the ASCII sentinel [CARD-SWEEP] and exits 1; judged by
      Test-ScaffoldCardSweep in _cards.ps1, which takes card TEXT so the rule is testable without a card
      file on disk (selftest gate 10h regression). The field records THAT a sweep happened - whether it was
      exhaustive is not machine-checkable and nothing here claims to check it.
    - A card whose `hygiene` promises a **mutation-evidence batch** (L165) while `allow_paths` has nowhere
      for BOTH artifacts to land (T110/TD145, tightened by T200/TD194): the ship scope gate reads allow_paths
      from the BASE card, so widening it in-branch is inert by design — the unscoped file would have to run
      from a scratchpad and could never be committed, which is precisely what `specs/mutations/README.md`
      exists to stop (measured on T90, unnoticed by anything). `mutate.ps1` writes TWO tracked files per
      batch, the registry `.psd1` and its `-results.tsv`, so the bare `specs/mutations/` directory satisfies
      the rule and a single `.psd1` does not — the any-half form accepted a card whose results TSV was then
      refused at ship, after the batch had run (T191 paid for it live). Reported with the ASCII sentinel
      [CARD-HYGIENE-MUT] and exits 1; judged by Test-ScaffoldCardMutationPaths in _cards.ps1, which takes
      card TEXT so the rule is testable without a card file on disk (selftest sub-gate 10l). The
      «promises a batch» half is the
      CARD-HYGIENE-MUT entry of the rule table below, because it must separate two shapes that both contain
      the word *mutation*: a promised batch owes a registry, the template's `mutation-survivor` pruning line
      (carried by 17 cards) owes nothing. This is the ONLY machine check on `hygiene`; the field is
      otherwise advisory prose and nothing here judges whether the promised batch was any good.
    - An `allow_paths` entry that RESOLVES TO NOTHING while a path one small edit away does exist
      (T174/TD166). Every existing guard judged the SHAPE of allow_paths and never resolved an entry, so
      a path that was never there passed validation and the defect surfaced later as a confusing scope
      verdict instead of «this card names a file that does not exist». Two instances, both found by hand:
      T158 declared `scripts/init-scaffold.ps1` (that script lives at the repo root) and T153 declared
      `docs/adr/0013-no-trajectory-or-replay-view.md` against an actual `0013-no-trajectory-replay-view.md`.
      The near-neighbour half is NOT stylistic: a bare existence rule rejects 12 of the last 14 archived
      cards at `-Phase start`, because a card is written before the files it creates exist (most often its
      own `specs/mutations/<id>.psd1` and `<id>-results.tsv`). Reported with the ASCII sentinel
      [CARD-PATH-NEARMISS] and exits 1; judged by Test-ScaffoldCardPathNearMiss in _cards.ps1, which takes
      card TEXT plus the tree as a LIST so the rule is testable without touching disk (selftest sub-gate
      10m). A NEIGHBOUR is the same file name elsewhere **when that name is unique in the tree**, or a
      sibling in the same directory at absolute edit distance ≤ 3. Uniqueness is load-bearing: a name that
      occurs many times says nothing about where the author meant (`README.md` occurs 14 times here,
      `SKILL.md` 17), and without it a card declaring a root `README.md` it is about to create is flagged
      against fourteen unrelated files — which is how the first version of this rule took gate 15 red.
      The rule cannot tell a typo from a deliberate near-name — it names the neighbour it found and
      stops there. Globs are never resolved, and the archive is never swept: this script enumerates
      `specs/tasks/` only, and an archived card's own `specs/tasks/<id>.md` is a near-miss BY DESIGN.
    - A DECLARED `budget:` whose value cannot be a line count - a non-integer, zero, or a negative
      (T233/TD235). Reported with the ASCII sentinel [CARD-BUDGET] and exits 1; judged by
      Get-ScaffoldCardBudgetFinding in _cards.ps1, which takes the front-matter TEXT so the rule is
      testable without a card file on disk. ABSENCE IS LEGAL AND SILENT and that is the contract, not a
      gap: `budget:` is deliberately NOT a required field, because cards are in flight continuously and a
      rule that failed every card omitting it would red a peer's card mid-acceptance over a field their
      card predates. An absent key - and an EMPTY value, which Get-ScaffoldCardBudgetValue also reads as
      absent - degrades to the reporting-only meter, the same "empty means off, never broken" degradation
      FrozenPaths / DocSyncMap / DocBudgets give a freshly initialised downstream. What is rejected is the
      state in between: a value that reads as a budget to a human and as ABSENT to every gate, so the card
      looks governed and is not. Nothing here judges whether the NUMBER is well chosen - that is measured
      against the live diff by scripts/check-budget.ps1 and blocked at ship by [CARD-BUDGET-OVER].
    - A DECLARED `tier:` BELOW the tier this card's `allow_paths` compute (T273/ADR 0016). Reported with the
      ASCII sentinel [CARD-TIER-LOWER] and exits 1; judged by Get-ScaffoldCardTier in _cards.ps1, which
      takes the paths and the two config lists as ARGUMENTS so the rule is testable without a card file or
      a configuration on disk (selftest sub-gate 10t). The tier itself is COMPUTED, never chosen: any
      allow_path matching a `TierSPaths` entry makes the card S, every path matching a `Tier0Paths` entry
      makes it 0, anything else is 1, matched by a rule BUILT ON the scope gate's matcher and deliberately
      not identical to it - a Tier-S entry matches by containment in BOTH directions (the tier asks whether
      the card could REACH the entry), a Tier-0 wildcard naming no directory is anchored to the repo root,
      and a folded FrozenPaths entry raises by two rules that read no regex text (T282): a DIRECTORY entry
      is S whenever the frozen list holds anything, a FILE entry is S when a fragment matches it. A card may
      declare `tier:` only to RAISE - it is the card's own statement that it is riskier than its paths look
      - because a tier an author can lower under pressure is a tier that gets lowered, while the computed
      value is bound to allow_paths, which the ship scope gate reads from the BASE card. An ABSENT `tier:`
      is legal and silent, the normal case: the field is not required and nothing here asks for it. The
      computed tier of EVERY validated card is printed as `[CARD-TIER] id=<id> tier=<S|1|0> reason=<...>`,
      so `task.ps1 -Phase start` and `-Phase ship` both show it. Empty `TierSPaths` - or a _config.ps1 this
      script could not source - means tiering is off and every card is S, today's bar unchanged.
    - A DECLARED `tier:` VALUE outside S, 1 and 0 (T276/ADR 0016). Reported with the ASCII sentinel
      [CARD-TIER-BADVALUE] and exits 1, judged by the same Get-ScaffoldCardTier. T273 dropped such a value
      with a note in the printed reason and blocked nothing, so a card declaring `tier: 2` read as governed
      to a human and as undeclared to every gate - the in-between state [CARD-BUDGET] already refuses for a
      declared budget that cannot be a line count. The computed tier stands either way; what changes is
      that the typo is refused with the repair printed rather than noted in a line nobody greps. The legal
      values are the CARD-TIER row of the rule table and are case-sensitive.
    - An `acceptance:` item that CITES a requirement the card does not declare (T276/ADR 0016 item 5).
      `requirements:` is an OPTIONAL block list of `R<n>. text` items, and an acceptance item may name the
      one it closes as `[R<n>]` so the closed list points at a stated requirement instead of restating it.
      A citation with no matching item is reported with the ASCII sentinel [CARD-REQ-DANGLING] and exits 1,
      judged by Get-ScaffoldCardRequirementFinding in _cards.ps1, which takes card TEXT so the rule is
      testable without a card file on disk (selftest sub-gate 10u). BOTH LISTS ARE READ FROM THE FRONT
      MATTER ONLY and an `R<n>.` item with no text after the dot is not a requirement (T282): a list in the
      body neither resolves a citation nor raises one, and a citation cannot land on a stated nothing.
      ABSENCE IS LEGAL AND SILENT: a card
      with no `requirements:` and no citations is valid, because the field is optional and a rule demanding
      it would red every card written before it existed. Whether the list is COMPLETE, and whether the
      acceptance items cover it, are planning judgements - not machine-checkable, and nothing here claims
      to check them. Only acceptance items are scanned: a bracketed R-number in prose or in dod text is not
      a citation. The citation shape is the CARD-REQ-DANGLING row of the rule table, where `[dod arm 1]`
      and `[FOLLOW-UP]` - the bracketed tokens acceptance lines already carry - sit in its ShouldNot list.
    - A malformed `arbitration:` entry (T285/ADR 0016 item 4): the OPTIONAL front-matter block list recording
      the ruling CLAUDE.md's execution boundary calls for when maker and checker cannot agree after two
      rounds. Reported with the ASCII sentinel [CARD-ARBITRATION] and exits 1, judged by
      Get-ScaffoldCardArbitration in _cards.ps1, which takes card TEXT so the rule is testable without a card
      file on disk (selftest sub-gate 10u). ABSENCE IS LEGAL AND SILENT, the normal case for every card, and
      nothing here judges whether the ruling is RIGHT - only whether it can BIND. The four shape rules and
      what each one prevents are in specs/README.md's field table and in the finding text itself.
    - Rule-table self-check (T88), run BEFORE any card is validated: every rule declared in _cards.ps1's
      Get-ScaffoldCardRules must match its own Should examples and must not match its ShouldNot examples;
      a disagreement is reported with the ASCII sentinel [CARD-RULE-EXAMPLE] and exits 1. The id,
      [CARD-FM-GARBAGE], [CARD-TOKEN-LITERAL] and [CARD-PLACEHOLDER] patterns used below are READ from that
      table - it is the single source, not a copy - so a pattern that silently stopped matching goes red on
      the next run.
  本次上游采纳的本地兼容边界（优先于上面描述的上游后续政策）：
    - 已启用：本仓原有卡片语法、DoD、范围、token、并发/TD 检查，以及 tier 计算和 ASCII 结果面。
    - 未启用：`CARD-DOD-FN-EXISTS`、budget、sweep、mutation-path、path-near-miss、requirement-citation、
      arbitration 与 cross-corpus dangling-reference。对应 helper 可随上游模块保留，但本仓入口不调用；
      这些政策需要单独的历史卡迁移后才能成为阻断规则。
    - acceptance 保留本仓旧行为：缺失仅 advisory；已声明时仍校验块式双引号 `A1..An` 形状。

  警告（仍 exit 0）：title 缺失；[CARD-ID-REUSE] a live card's number is already carried by another card
    in `specs/tasks/` or `specs/archive/tasks/`. Advisory by design and never blocking: `T<阶段号>` is a
    STAGE number and `specs/README.md` lists same-stage ids as compliant, so reuse is legal - but since
    T57 the number has in practice been an allocation sequence, and two sessions allocating one number is
    how T98 came to name two different cards (TD147 arm (a), owner decision 2026-08-23)；[CARD-PLACEHOLDER] a field still holds an unfilled placeholder - the
    `path/to/` marker anywhere in the front matter, or an angle-bracketed run that is the ENTIRE value of a
    field or of a block-list item. An angle-bracketed fragment inside prose is legitimate card text and is
    NOT reported (L226/T98)；[CARD-ACCEPTANCE] a card declares `review_gate` but carries no `acceptance:`
    block list, or carries the key with no items under it (T103). Rubric #6 is decided against whatever
    the card says done means, so without an enumeration it is judged against an OPEN set and each review
    round can name another untested branch. Advisory and never blocking: the field is optional by design
    so every pre-T103 card keeps working. Judged by Test-ScaffoldCardAcceptance in _cards.ps1, which takes
    card TEXT so the rule is testable without a card file on disk (selftest sub-gate 10k). The field
    records WHICH facts constitute done - whether that list is COMPLETE is a planning judgement, is not
    machine-checkable, and nothing here claims to check it；allow_paths > 5 entries - a WIDTH hint and
    deliberately not the size gate: what a card costs is measured against its declared 'budget:' by
    scripts/check-budget.ps1 while the card is being written and blocked at merge by [CARD-BUDGET-OVER],
    so this count is a second, different signal and neither one substitutes for the other (T233/TD235)；
    动 frontend/ 的卡其 dod_command 未含前端测试闸（verify/vitest/playwright）。

  仅校验真实卡，跳过 _TEMPLATE.md（其 T?-EXAMPLE 占位故意违规）。无真实卡 => PASS（脚手架期）。
  This script sources _config.ps1 FAIL-OPEN, for the two tier lists and for nothing else (T273): with it
  absent, unreadable, or carrying neither key, the lists are empty, tiering is off, every card reads Tier S
  and every other rule here is unchanged. So it still dry-runs under a default configuration, and it still
  runs in a tree holding nothing but itself and _cards.ps1.

.PARAMETER TaskId  给定则只校验 specs\tasks\<TaskId>.md；否则校验全部真实卡。
.EXAMPLE
  pwsh -File scripts\check-cards.ps1
  pwsh -File scripts\check-cards.ps1 -TaskId T1-FOO
#>
[CmdletBinding()]
param([string]$TaskId)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$TasksDir = Join-Path $RepoRoot 'specs/tasks'
. (Join-Path $PSScriptRoot '_cards.ps1')
# ADR 0016 / T273: the two tier lists live in _config.ps1. Sourced FAIL-OPEN, like _encoding.ps1 above and
# for a concrete reason: the seeded card fixtures in selftest gate 10 build a tree holding check-cards.ps1
# and _cards.ps1 only, and this script must keep validating cards there. Absent config => empty lists =>
# tiering off => every card is Tier S, which is the same conservative answer an empty TierSPaths gives.
try { . (Join-Path $PSScriptRoot '_config.ps1') } catch { }

$validStatus = @('todo', 'in-progress', 'in-review', 'merged')
# T88: the three load-bearing card regexes, and the examples each must / must not match, are declared
# together in _cards.ps1's Get-ScaffoldCardRules. This script CONSUMES that table - it carries no second
# copy of any pattern, which is the drift the table exists to remove. Downstream projects that want a
# different id scheme edit the 'card-id' entry there, pattern and examples in one place.
# A missing entry is fail-closed by design: the lookup throws under StrictMode rather than silently
# validating cards against $null.
$cardRules = @{}
foreach ($rule in @(Get-ScaffoldCardRules)) { $cardRules[$rule.Id] = $rule }
$idPattern = $cardRules['card-id'].Pattern
$fmGarbagePattern = $cardRules['CARD-FM-GARBAGE'].Pattern
$tokenLiteralPattern = $cardRules['CARD-TOKEN-LITERAL'].Pattern
$placeholderPattern = $cardRules['CARD-PLACEHOLDER'].Pattern
# Validate the table before validating any card: a rule that stopped matching what it must catch, or
# grew wider than its contract, goes red here - before it can wave a malformed card through.
$ruleFindings = @(Test-ScaffoldCardRuleExamples)
# T102: the two cross-card rules ([CARD-TD-DUP], [CARD-ID-REUSE]) carry declared examples too, and they are
# validated here for the same reason - a rule that stopped discriminating must go red before it waves a
# duplicate card through, not at some unknown future date. Same fail-fast block, one findings list.
$ruleFindings += @(Test-ScaffoldCardCrossRefExamples)
# T174: same fail-fast stance for the path near-miss rule. It BLOCKS a card, so a predicate that stopped
# discriminating must go red here rather than either waving a dead entry through or - the expensive
# direction - rejecting a card whose paths are fine.
$ruleFindings += @(Test-ScaffoldCardPathNearMissExamples)
if ($ruleFindings.Count -gt 0) {
  Write-Host '错误：' -ForegroundColor Red
  $ruleFindings | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  Write-Host "`ncheck-cards: FAIL" -ForegroundColor Red
  exit 1
}
$cardErrors = @()
$cardWarns = @()
$cardMeta = @{}   # 卡名 → @{ allow=<allow_paths 项>; par=<parallelizable_with 项> }，供跨卡检查；**始终收全部在飞卡**（T177）

# --- 选卡 ---
# T177/TD173: the LIVE set is enumerated UNCONDITIONALLY. -TaskId narrows which card is validated in detail
# and reported on; it must never narrow the corpus the cross-card rules resolve ids against. Collapsing
# those two axes into one flag is what left the whole cross-card block unreachable in the single-card loop.
$liveCards = @(Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne '_TEMPLATE.md' } | ForEach-Object FullName)
if ($TaskId) {
  $card = Join-Path $TasksDir "$TaskId.md"
  if (-not (Test-Path $card)) { throw "任务卡不存在: $card" }
  $cards = @($card)
} else {
  $cards = @($liveCards)
}
if (-not $cards -or $cards.Count -eq 0) {
  Write-Host '（specs\tasks\ 无真实任务卡，仅 _TEMPLATE.md）——跳过，视为通过。' -ForegroundColor DarkGray
  exit 0
}

# T174: the tree that allow_paths entries are resolved against, built ONCE for the whole run - files plus
# every directory implied by them, repo-relative with forward slashes, so a directory form such as
# `specs/mutations/` resolves the same as a file (31 archived entries have that shape).
# Source is git, not a filesystem walk, for three reasons: it honours .gitignore for free, so the walk
# never descends into node_modules or .venv; `--others --exclude-standard` includes files that exist in
# the worktree but are not staged yet, which is exactly the state a card is validated in; and an ignored
# path is one a card could never legitimately declare, since allow_paths names what the diff will commit.
# Fail-open by design: if git cannot answer, the list is empty, every entry resolves to nothing, and with
# no tree there is no neighbour either - so the rule reports nothing rather than blocking every card.
$treePaths = @()
try {
  $gitFiles = @(& git -C $RepoRoot ls-files 2>$null) + @(& git -C $RepoRoot ls-files --others --exclude-standard 2>$null)
  $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  foreach ($gf in $gitFiles) {
    $p = ([string]$gf -replace '\\', '/').Trim()
    if (-not $p) { continue }
    if ($seen.Add($p)) { $treePaths += $p }
    # every ancestor directory, so a directory entry resolves without git having to list directories
    $cut = $p.LastIndexOf('/')
    while ($cut -gt 0) {
      $p = $p.Substring(0, $cut)
      if (-not $seen.Add($p)) { break }        # this ancestor chain is already recorded
      $treePaths += $p
      $cut = $p.LastIndexOf('/')
    }
  }
} catch { $treePaths = @() }

# ADR 0016 / T273: the tier lists, resolved ONCE for the whole run. Read through the accessors when they are
# there and left empty when they are not - a Get-Command probe rather than a try/catch per card, so the
# degraded case costs one lookup and never a swallowed error inside the loop.
$tierSPaths = @()
$tier0Paths = @()
if (Get-Command Get-ScaffoldTierSPaths -ErrorAction SilentlyContinue) { $tierSPaths = @(Get-ScaffoldTierSPaths) }
if (Get-Command Get-ScaffoldTier0Paths -ErrorAction SilentlyContinue) { $tier0Paths = @(Get-ScaffoldTier0Paths) }

foreach ($cf in $cards) {
  $name = [IO.Path]::GetFileNameWithoutExtension($cf)
  $raw = Get-Content $cf -Raw
  $fm = Get-FrontMatter $raw
  if (-not $fm) { $cardErrors += "[$name] front-matter 缺失（须以 --- 包裹的 YAML 头）"; continue }

  # TD112：拒 front-matter「非缩进且不含冒号」的垃圾行——合法形态只有 'key: value'、缩进列表项、空行与整行
  # 注释（行首 #；_TEMPLATE.md 自带此类指引注释故豁免。取值器在注释行仍照旧终止扫描，fail-closed 契约不变）。
  # 建卡期即拒，免得畸形卡拖到 ship 范围闸才被兜住（晚且贵）。夹具见 selftest 闸 10d 族（负例·正例·修复回路）。
  $fmLines = @($fm -split '\r?\n')
  $fmGarbageIdx = @(); for ($gi = 0; $gi -lt $fmLines.Count; $gi++) { if ($fmLines[$gi] -match $fmGarbagePattern) { $fmGarbageIdx += $gi } }
  if ($fmGarbageIdx.Count -gt 0) {
    # R3（T59 终轮→take-5 收敛）：「加 '  - ' 前缀」只在修出的项**真会被取值器收进某个列表值键**时才是合法修法，
    # 故列表上下文按取值器自己的终止语义推导**属主顶层键**（_cards.ps1 三取值器一致：任何非缩进行——含顶层注释——
    # 即终止，缩进行/空行跳过）：从垃圾行向上取**最近的非缩进行**。顶层注释也终止扫描、不得越过（越过会在已被
    # 注释截断的块上教列表前缀错药）；中间的缩进列表项不构成上下文（其属主可能是标量键，项本被静默跳过）。
    # 该最近非缩进行恰为**空值块式形态的列表值键**时才是列表上下文，否则一律 no-list-context 二选（take-4/take-5 #9）。
    # 列表值键全集以 _TEMPLATE.md 卡 schema 为准（take-5 #9：漏 depends_on/forbid/non_goals 曾在这三键下给错二选）；
    # 行内 flow 形态（如 `depends_on: []`）非空值故不匹配——新项挂不进行内列表，归二选属正确。
    $gListKeys = 'allow_paths|parallelizable_with|depends_on|forbid|non_goals'
    $g0 = $fmGarbageIdx[0]
    $gPrev = ''
    for ($gj = $g0 - 1; $gj -ge 0; $gj--) { $gl = $fmLines[$gj]; if ($gl -match '^\S') { $gPrev = $gl; break } }
    $gListCtx = ($gPrev -match "^($gListKeys)\s*:\s*(#.*)?$")
    # take-7/take-8 #9：垃圾行**自身形状**也参与给药。横线形（`^-`）按**有无真实值**二分：
    # ① 带真实值的 `- 项` → 正药只补缩进（再加 '  - ' 前缀会修成 '  - - 项' 双横线改值）；
    # ② 无真实值形（孤 `-` / `- # 注释` / `- ''` / `- ""`）→ 缩进/前缀都只造出形状合法但值为注释/空串/横线
    #    字面量的项（过 Get-YamlListCount 形状检查、在取值器里毁掉实际值），正药 = 换成带真实值的项或删行。
    $gIsDashForm = $fmLines[$g0] -match '^-(\s|$)'
    $gDashVal = if ($gIsDashForm) { ($fmLines[$g0] -replace '^-\s*', '').Trim() } else { '' }
    # take-9 #9 终结修：「有无真实值」不再枚举拼写（take-8 的四拼写白名单漏 `- '' # 注释`/引号内纯空白等），
    # 直接**复用取值器同一条归一化路径**（Get-UncommentedValue 注释语义 → Trim('"')/Trim("'") 引号语义 →
    # Trim() 空白语义，与 Get-Yaml*ListItems 逐步同序）——分类器与取值器由同一段代码判定，构造上不能再漂移。
    # 唯一补充：GUV 输出以 '#' 开头（行首注释无前置空白、GUV 刻意不剥）也归无值——那是注释载荷不是值；
    # 引号包裹的 '#…'（如 `- '#tag'`）GUV 输出以引号开头、不触发此补充，仍按带值处理（与取值器一致）。
    $gNormPre = Get-UncommentedValue $gDashVal
    $gNorm = if ($null -ne $gNormPre) { $gNormPre.Trim('"').Trim("'").Trim() } else { '' }
    $gDashNoValue = $gIsDashForm -and ((-not $gNorm) -or $gNormPre.StartsWith('#'))
    $gIsBareItem = $gIsDashForm -and -not $gDashNoValue
    $gFix = if ($gDashNoValue -and $gListCtx) { "Fix (two options, no-list-value): rewrite it as an indented list item carrying a REAL value ('  - <value>'; the value must not be empty, a '#' comment, or an empty-string literal - merely indenting or prefixing makes the line land in the extractor as a comment/empty-string/dash literal, which passes the shape check while destroying the list's actual value); or delete the line" }
    elseif ($gIsBareItem -and $gListCtx) { "Fix (two options, bare-list-item): indent the line by two spaces, KEEPING its existing '- ' prefix (do not add another '  - ', that would rewrite it as '  - - ...' and change the list value) so it becomes a valid item of the preceding block-style list key; or delete the line" }
    elseif ($gListCtx) { "Fix (three options, fix-3way): add a '  - ' prefix (fix-add-list-prefix) so the line becomes a valid item of the preceding block-style list key; or rewrite it as a valid top-level key in 'key: value' form; or delete the line" }
    else { "Fix (two options, no-list-context): rewrite it as a valid top-level key in 'key: value' form; or delete the line (the owning context derived from the extractor's termination semantics is not an empty block-style list value key $($gListKeys -replace '\|','/') - an indented or prefixed line cannot attach there and stays a malformed line the extractor silently skips)" }
    # take-7/8 #9：尾注只对「非横线形」的垃圾行成立——横线形的给药已按有无真实值内嵌各自的仅缩进警示，
    # 通用尾注在 bare-list-item（正药恰是只补缩进）与 no-list-value（有专用警示）两形状下都会自相矛盾/冗余。
    $gTail = if ($gIsDashForm) { '' } else { 'Note (fix-indent-alone-insufficient): indenting alone is not enough - an indented non-list-item line is silently skipped by the extractor and the card stays malformed.' }
    $cardErrors += "[$name] [CARD-FM-GARBAGE] front-matter 内出现非缩进且不含冒号的垃圾行：'$($fmLines[$g0])'——[RULE] 违反规则：front-matter 顶层键须形如 'key: value'（含冒号），列表项须缩进书写（'  - item'）；[WHERE] 出现位置：$name 的 front-matter 内该行顶格书写，既不是合法键（无冒号）也不是缩进列表项；[FIX] $gFix. $gTail"
    continue
  }

  $id = Get-UncommentedValue (Get-Scalar $fm 'id')
  $title = Get-UncommentedValue (Get-Scalar $fm 'title')
  $status = Get-UncommentedValue (Get-Scalar $fm 'status')
  $branch = Get-UncommentedValue (Get-Scalar $fm 'branch')
  $wt = Get-UncommentedValue (Get-Scalar $fm 'worktree')
  $dod = Get-Scalar $fm 'dod_command'    # 不剥注释，仅判非空

  # id：必填 + 与文件名一致 + 字符合法
  if (-not $id) { $cardErrors += "[$name] id 缺失" }
  else {
    if ($id -ne $name) { $cardErrors += "[$name] id='$id' 与文件名不一致（应为 '$name'；task.ps1/review.ps1 以文件名=id 定位）" }
    if ($id -match '[\s\\/:~^?*\[\]]') {
      $cardErrors += "[$name] id='$id' 含非法字符（分支/worktree/文件名共用，禁空白与 \ / : ~ ^ ? * [ ]）"
    } elseif ($id -cnotmatch $idPattern) {   # -cnotmatch：大小写敏感，否则 [A-Z] 会放过小写
      $cardErrors += "[$name] id='$id' 不符规范命名 'T<阶段号>-<大写短横名>'（正则 $idPattern；示例 T0-SCAFFOLD / T2-API / T3-REVIEW-GATE；反例 t1-foo / T1_FOO / my-task）"
    }
  }
  if (-not $title) { $cardWarns += "[$name] title 缺失（一句话产出）" }

  # status 枚举
  if (-not $status) { $cardErrors += "[$name] status 缺失" }
  elseif ($status -notin $validStatus) { $cardErrors += "[$name] status='$status' 非法（应 ∈ $($validStatus -join ' | ')）" }

  # branch / worktree 一致性（二者会被 task.ps1 忽略 → 漂移即隐患）
  if ($branch -and $id -and ($branch -ne $id)) { $cardErrors += "[$name] branch='$branch' 与 id='$id' 不一致（task.ps1/review.ps1 以 id 为准，卡内 branch 会被忽略 → 漂移）" }
  if ($wt -and $id) { $leaf = Split-Path $wt -Leaf; if ($leaf -ne $id) { $cardErrors += "[$name] worktree 末段 '$leaf' 与 id='$id' 不一致" } }

  # dod_command：必填非空
  if ([string]::IsNullOrWhiteSpace($dod)) { $cardErrors += "[$name] dod_command 缺失/为空（task.ps1 ship 的 DoD 闸门据此执行）" }
  # TD63 item5：YAML block-scalar（`dod_command: |` / `>` 等）会被本文件的单行取值器（Get-Scalar，非多行 YAML
  # 解析）截断成裸的指示符字面量（如 `|`），既通过上面的非空校验，又通过下方的 no-op 判定（分段后两侧皆空、
  # $dodSegments.Count=0 使 no-op 分支不触发）——静默放行成一张「看似合法」的卡，ship 阶段真执行这个裸管道符
  # 会产生诡异解析错误（远且贵）。校验期直接拒绝，给出可操作错误信息。
  elseif ($dod.Trim() -match '^[|>][+\-]?[0-9]*\s*(#.*)?$') {
    $cardErrors += "[$name] dod_command='$($dod.Trim())' 是 YAML block-scalar 指示符——本校验器只支持单行标量，写成 dod_command: | 或 dod_command: > 这类多行 block-scalar 会被截断成裸的 '$($dod.Trim())' 字面量（而非其后续缩进内容），ship 执行时会因前导管道符产生诡异解析错误。请把 dod_command 改写为单行命令（多条命令用 ; 或 && 串接）。"
  }
  else {
    # 不得是 no-op：DoD 闸门须真跑验证；echo/true/exit 0/Write-Host 之类空命令会让卡「假绿」过 ship。
    # 串接守卫按「分段」判定（TD46）：把 dod_command 按 && / || / ; / | 切成段，逐段判断是否 no-op——
    # 只要「至少一段」不是 no-op（如 pytest/verify.ps1 等真命令）就判「真 DoD」放行；
    # 只有「每一段」都命中 no-op 模式（如 `echo a; echo b`、`echo x && echo done`）才拒绝。
    # 此前的实现只判「是否存在 && 或 ;」就整体豁免、从不看分隔符右侧内容——`echo a; echo b` 这类
    # 纯 no-op 链能骗过闸、假绿过 ship（TD46）。单命令（无分隔符）行为不变：整串即唯一一段。
    $dodCmd = (Get-UncommentedValue $dod)
    if ($dodCmd) { $dodCmd = $dodCmd.Trim().Trim('"').Trim("'").Trim() }
    $noopPattern = '(?i)^(echo|true|exit\s+0|rem|write-host)\b|^:(\s|$)'
    if ($dodCmd) {
      $dodSegments = @($dodCmd -split '&&|\|\||;|\|' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
      $hasRealSegment = @($dodSegments | Where-Object { $_ -notmatch $noopPattern }).Count -gt 0
      if ($dodSegments.Count -gt 0 -and -not $hasRealSegment) {
        $cardErrors += "[$name] dod_command='$dodCmd' 是 no-op——DoD 须真跑验证（pytest/vitest/playwright/verify 等），否则卡可假绿过 ship。"
      }
    }
    # TD33/L60：dod_command 内嵌重型套件（selftest/pytest/npm test）→ 评审沙箱（ConstrainedLanguage/只读）可能不可复跑，
    # 评审者无法复跑 DoD 即按「不确定即 block」连环挡（L60 症状）。建议性（不阻断，同 >5 allow_paths 形态）：
    # DoD 宜留沙箱可复跑的轻量静态断言（Select-String/Test-Path 类），重型套件的强制点放 CI 闸。
    # 命中「调用」重型套件（-File …selftest.ps1 / pytest / npm test），不误伤「引用其路径」的轻量断言
    # （如 `Select-String -Path scripts/selftest.ps1`——正是 L60 推荐的沙箱可复跑形态，不该 warn）。
    if ($dodCmd -and ($dodCmd -match '(?i)\bpytest\b|\bnpm\s+(run\s+)?test\b')) {
      $cardWarns += "[$name] dod_command 内嵌重型套件（pytest/npm test）——评审沙箱可能不可复跑，宜留沙箱可复跑的轻量静态断言（Select-String/Test-Path），重型套件的强制点放 CI 闸（见 L60/L62）"
    }
    # T144 [CARD-DOD-SUITE]: the selftest half of the old warning, now ANSWERABLE. It was routinely accepted
    # because it named no alternative and offered no way to say "yes, on purpose" - so the pattern became the
    # norm (81 of 144 live and archived cards). The judgement is the declared rule in _cards.ps1, never a
    # second copy here, and the two escapes (-Only, suite-required) live in that rule's own lookahead.
    if ($dodCmd -and (Test-ScaffoldDodSuiteCost -DodText $dodCmd)) {
      $cardWarns += "[$name] [CARD-DOD-SUITE] dod_command drives the FULL selftest suite, so this card pays the suite wall TWICE: once when the DoD runs and again at the mandatory pre-ship acceptance run over the same tree. Cheaper shapes, in order: (a) SCOPE it - add -Only <ids> for the regions this card actually touches; the [GATE-MAP] table in scripts\selftest.ps1's header names them per change surface. (b) ASSERT STATICALLY - Select-String the shard list or the gate-id literal instead of executing it; that is also the only shape a review sandbox can re-run (L60). (c) If this card genuinely must drive the whole suite - it edits selftest.ps1 itself, so no scoped run can prove the union - write 'suite-required: <reason>' in the dod_command and this stops being reported. Asking for a reason, not for abstinence."
    }

    # TD69/L95：dod_command 嵌套 `pwsh … -Command "…"` 且**双引号载荷内**含会被内插的 `$` → 双层包裹下静默铸成
    # vacuous RED。task.ps1 以 `& pwsh -NoProfile -Command <dod 原文>` 执行本字段（red 相 / ship 相皆然）：中间
    # shell **解析并执行** <dod>，届时 `pwsh -Command "…$ok…"` 里的双引号载荷会被中间 shell 先行内插——`$ok` 变空
    # 串，孙 shell 只收到 `if (-not ) { … }` → ParserError → exit 1；`-Phase red` 只看退出码非零、遂把「命令根本
    # 没跑起来」当合法 RED 收下（vacuous RED，GREEN 永不可达）。校验期确定性拒绝、给可操作修法。
    #
    # 判定用 **PowerShell 自己的解析器**（= 中间 shell 的真语义；codex R3 九轮 + Fable 5 复审后从正则改为真解析——正则
    # 做不到反引号奇偶转义、多段字符串边界、括号/拼接包裹、脚本块边界、-Command 的多种拼写）：先看 ParseInput 有无语法
    # 错误（dod 本身解析不通 → 中间 shell 直接 ParserError → vacuous RED 的另一扇门，Fable R3），无错再逐调用分析：
    # 载荷收集（哪些实参会成为孙 shell 的脚本：-Command 的各种拼写 / -File 边界 / -CommandWithArgs 的实参）
    # 已抽到 _cards.ps1 的 Get-ScaffoldDodNestedPayloadArg，与 TD153 的退出码继承规则共用同一份判定——两条规则
    # 若各自遍历，就会对「载荷是什么」给出两个答案，只有一方认得的形态就只被一方判。拼写规则与其静态限的
    # 全部说明在那个函数的头注释里，此处不留第二份。本处只判：载荷子树里有没有会被中间 shell 内插、且不在
    # 脚本块体内的节点（变量 $x / 子表达式 $(…)）。
    $td69Tok = $null; $td69Err = $null
    $dodAst = [System.Management.Automation.Language.Parser]::ParseInput($dod, [ref]$td69Tok, [ref]$td69Err)
    if ($td69Err -and $td69Err.Count -gt 0) {
      # dod 本身语法就不通 → task.ps1 双层 pwsh 执行时中间 shell 直接 ParserError exit 1、被 -Phase red 误当合法 RED
      # （vacuous RED 的另一扇门，同 TD69/L95 一类；Fable R3）。dod 惯用形态 `pwsh -Command "…"` 是合法 PS 命令行、必解析通过。
      $cardErrors += "[$name] " + ('dod_command 本身无法被 PowerShell 解析（首个语法错误：' + $td69Err[0].Message + '）——task.ps1 以 `& pwsh -NoProfile -Command <dod>` 执行时中间 shell 会 ParserError exit 1、被 -Phase red 误当合法 RED（vacuous RED，同 TD69/L95 一类）。修正 dod_command 语法。')
    }
    $dodVarHazard = $false
    foreach ($arg in @(Get-ScaffoldDodNestedPayloadArg -Ast $dodAst)) {
      if ($dodVarHazard) { break }
      foreach ($node in $arg.FindAll({ param($n) ($n -is [System.Management.Automation.Language.VariableExpressionAst]) -or ($n -is [System.Management.Automation.Language.SubExpressionAst]) }, $true)) {
        $inSb = $false; $anc = $node
        while ($anc) {
          if ($anc -is [System.Management.Automation.Language.ScriptBlockExpressionAst]) { $inSb = $true; break }
          if ([object]::ReferenceEquals($anc, $arg)) { break }
          $anc = $anc.Parent
        }
        if (-not $inSb) { $dodVarHazard = $true; break }
      }
    }
    if ($dodVarHazard) {
      $cardErrors += "[$name] " + 'dod_command 嵌套 pwsh/powershell 调用，其 -Command 载荷（任意拼写 -c/-Command/--Command）含会被中间 shell 内插的 $ 变量（如 $ok/$_/$env:/$(…)）——task.ps1 以 `& pwsh -NoProfile -Command <dod>` 双层执行本字段，载荷里的 $ 会被中间 shell 内插成空串、孙 shell 收到坏语法 → ParserError exit 1，而 -Phase red 只看退出码非零会把它误当合法 RED（vacuous RED，GREEN 永不可达；TD69/L95）。改用无变量写法：把判断内联进 if，如 pwsh -NoProfile -Command "if (-not ((Select-String …) -and …)) { exit 1 }"（单引号载荷 / 反引号转义 / scriptblock / -File 变量实参 / 载荷外的 $ 均不受限）。'
    }
    # TD153/L245 (T109): the SECOND way a nested payload mints a vacuous RED, and it carries no `$` and
    # parses cleanly, so neither guard above sees it. A `pwsh -Command` block that ends without an
    # explicit `exit` returns the exit code of the last NATIVE command it ran - so a payload that spawns
    # pwsh to observe a refusal (the ordinary way to assert a guard fires) ends holding that refusal's
    # non-zero and reports it as its own verdict. Judged by Test-ScaffoldDodExit (scripts/_cards.ps1),
    # which takes the dod TEXT - this is its only call site, and the rule is testable without a card on
    # disk. Seeded regression: selftest gate 10f (f29-f32).
    foreach ($dodExitFinding in @(Test-ScaffoldDodExit -Text $dod)) { $cardErrors += "[$name] $dodExitFinding" }
    # T243-DOD-FN-EXISTS-ARM (TD250/L245's mirror, L308): the three rules above all catch a vacuous RED -
    # a dod that exits NON-ZERO without running. This one catches the vacuous GREEN: a dod calling a repo
    # function that does not exist USED TO exit ZERO with every arm abandoned, because -Phase red ran the
    # field unwrapped and a CommandNotFoundException is terminating for the STATEMENT only. T270 aligned the
    # two phases, so that path is closed and this rule now buys the EARLIER refusal - at validation, with a
    # printed repair, rather than at run time. Judged by
    # Test-ScaffoldDodFnExists (scripts/_cards.ps1), which takes the dod TEXT so the rule is testable
    # without a card on disk, and which reads the SAME payload traversal as the two rules above.
    # Seeded regression: selftest gate 10f (f33-f36); declared examples: gate 10r.
    # Local compatibility: function-existence arms await a dedicated legacy-card migration.
  }

  # allow_paths：评审越界判定所需
  if ($fm -notmatch '(?m)^allow_paths\s*:') { $cardErrors += "[$name] allow_paths 缺失（review.ps1 据此判越界）" }
  else {
    # TD60/TD-123：此前只判「键存在」，未判「有块式列表项」——`allow_paths:`（空）与行内 flow
    # `allow_paths: [a, b]` 都能让上面的键存在性检查通过，但 task.ps1 ship 阶段的范围闸提取器
    # （镜像本文件的块式行走）只认块式列表（每项一行 `  - path`）、不识别行内/空值——两者的落差
    # 此前要等 DoD/verify/commit 全部跑完、到 ship 范围闸才 fail-closed 抛（晚且贵）。
    # Get-YamlListCount 本就只数块式项（不识别行内 `[...]`），故直接拿它当「ship 能否解析」的权威判据：
    # 空值与行内 flow 两种写法在它眼里都是 0 项，一并在此拒绝。
    $apCount = Get-YamlListCount $fm 'allow_paths'
    if ($apCount -eq 0) {
      $cardErrors += "[$name] allow_paths 无块式列表项（为空，或用了 ship 提取器不识别的行内 flow 语法 '[a, b]'）——ship 范围闸只认块式列表，否则 fail-closed 拒绝合并。请改写为块式，至少一项：allow_paths:`n  - path1"
    } elseif ($apCount -gt 5) {
      # Advisory, and since T233/TD235 no longer the only size signal it once implied it was. This counts
      # the WIDTH of a card's declared surface; what the card actually COSTS is measured against its
      # declared `budget:` - by check-budget.ps1 while the card is being written, and by the ship gate's
      # [CARD-BUDGET-OVER] at merge. The two answer different questions and neither substitutes for the
      # other: a card can be wide and cheap (the L97 sweep shape, whose oversized allow_paths is inherent
      # rather than a scoping mistake) or narrow and enormous. So this stays a hint and does not block -
      # two size signals arriving as one block would be a second judgement carrying a second evidence
      # burden, and the deterministic one is the budget.
      $cardWarns += "[$name] allow_paths has $apCount entries (>5) - a WIDTH hint, not the size gate. It says how much surface this card declares, not what it costs: the cost is measured against the card's declared 'budget:' (scripts/check-budget.ps1 while you work; [CARD-BUDGET-OVER] at ship). [FIX] if the width is real - an L97 cross-cutting sweep is wide by nature - keep it and record the sweep in 'sweep:'; if it is not, split by the right-sizing standard (.claude/workflows/decompose-cards.mjs)."
    }
  }

  # ADR 0016 / T273: the card's ACCEPTANCE TIER, computed from its allow_paths and PRINTED for every card
  # this run validates - task.ps1 runs check-cards at -Phase start and again at -Phase ship, so both phases
  # show the line without task.ps1 being edited. Judged by Get-ScaffoldCardTier (scripts/_cards.ps1), which
  # takes the paths and the lists as ARGUMENTS - this script holds no copy of the rule and no pattern of its
  # own. The declaration may only RAISE; a lowering one comes back as a finding and blocks
  # ([CARD-TIER-LOWER]), because a tier a card can talk itself down to is a tier that gets talked down.
  # Seeded regression: selftest sub-gate 10t.
  $cardTier = Get-ScaffoldCardTier -AllowPaths @(Get-YamlListItems $fm 'allow_paths') -TierSPaths $tierSPaths -Tier0Paths $tier0Paths -Declared (Get-UncommentedValue (Get-Scalar $fm 'tier'))
  Write-Host "[CARD-TIER] id=$name tier=$($cardTier.Tier) reason=$($cardTier.Reason)" -ForegroundColor DarkGray
  if ($cardTier.Finding) { $cardErrors += "[$name] $($cardTier.Finding)" }

  # T233/TD235: the `budget:` VALUE shape. Judged by Get-ScaffoldCardBudgetFinding (scripts/_cards.ps1),
  # which takes the FRONT MATTER - this is its only call site. Blocking for a declared-but-unusable value,
  # entirely silent for an absent one; the reasoning for that asymmetry is in this file's header.
  # Local compatibility: budget enforcement awaits a dedicated corpus migration.

  # T94: a card above the cross-cutting threshold must record its L97 sweep. Judged by
  # Test-ScaffoldCardSweep (scripts/_cards.ps1), which takes card TEXT - this is its only call site, and
  # the shared parser inside it is the same one every other card rule uses. What the rule can and cannot
  # show is stated in the finding text itself, so the message never overstates the guarantee.
  # Local compatibility: retain the pre-existing width advisory above.

  # T110/TD145 + T200/TD194: a card whose hygiene promises a mutation-evidence batch must have room in
  # allow_paths for BOTH files the batch writes - the registry and its results TSV - or that half has
  # nowhere to be committed: the ship scope gate reads allow_paths from the BASE card, so widening it
  # in-branch is inert by design and the unscoped file ends up in a scratchpad.
  # Judged by Test-ScaffoldCardMutationPaths (scripts/_cards.ps1), which takes card TEXT - this is its
  # only call site. The "promises a batch" pattern is the CARD-HYGIENE-MUT entry of the T88 rule table,
  # so the distinction from the template's mutation-survivor pruning line carries its own examples.
  # Seeded regression: selftest sub-gate 10k.
  # Local compatibility: mutation-path enforcement awaits a dedicated corpus migration.

  # T174/TD166: an allow_paths entry that resolves to nothing while a path one small edit away does exist.
  # Judged by Test-ScaffoldCardPathNearMiss (scripts/_cards.ps1), which takes card TEXT and the tree as a
  # LIST - this is its only call site, and passing the tree in is what keeps the decision testable against
  # a synthetic tree. BLOCKING: forbid #4 of T174 says a flagged card is what gets fixed, never the rule.
  # Seeded regression: selftest sub-gate 10m.
  # Local compatibility: retain the pre-existing allow_paths validation policy.

  # Preserve the existing local acceptance grammar: absence is advisory; a
  # declared block must retain its established shape and A1..An numbering.
  $acKeyLine = -1; $acKeyCount = 0; $acKeyGlued = $false; $acLines = @($fm -split '\r?\n')
  for ($acI = 0; $acI -lt $acLines.Count; $acI++) {
    if ($acLines[$acI] -match '^acceptance\s*:(?<sep>[ \t]*)(?<inline>.*)$') {
      $acKeyCount++
      if ($acKeyLine -lt 0) {
        $acKeyLine = $acI; $acInline = $Matches['inline']
        $acKeyGlued = $Matches['sep'].Length -eq 0 -and $acInline.StartsWith('#')
      }
    }
  }
  if ($acKeyLine -lt 0) {
    $cardWarns += "[CARD-ACCEPTANCE-ADVISORY] [$name] acceptance missing (optional author declaration)"
  } else {
    $acEntry = 0; $acIndent = $null; $acShapeBad = if ($acKeyGlued -or $acKeyCount -gt 1) { 1 } else { 0 }; $acNumberBad = 0; $acNumberActual = ''
    $acInlineValue = ($acInline -replace '^\s*#.*$', '' -replace '\s+#.*$', '').Trim()
    if ($acInlineValue) { $acShapeBad = 1 }
    for ($acI = $acKeyLine + 1; $acI -lt $acLines.Count; $acI++) {
      $acLine = $acLines[$acI]
      if ($acLine -match '^[^\s#].*?:') { break }
      if ([string]::IsNullOrWhiteSpace($acLine) -or $acLine -match '^\s*#') { continue }
      $acEntry++
      $acShape = if ($acLine -match '^(?<indent> +)- +(?<value>.+?)\s*$') {
        $acThisIndent = $Matches['indent']; $acValue = $Matches['value']
        if ($null -eq $acIndent) { $acIndent = $acThisIndent }
        if ($acThisIndent -cne $acIndent) { [regex]::Match('', '(?!)') }
        else { [regex]::Match($acValue, '^"(?<label>A[0-9]+)\s+(?:[^"\\]|\\(?:[ 0abtnvfre"/N_LP\\]|x[0-9A-Fa-f]{2}|u(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|U(?:0000(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|00(?:0[1-9A-Fa-f]|10)[0-9A-Fa-f]{4})))+"(?:[ \t]+#.*|[ \t]*)$') }
      } else { [regex]::Match('', '(?!)') }
      if (-not $acShape.Success -and $acShapeBad -eq 0) { $acShapeBad = $acEntry }
      if ($acShape.Success -and $acShape.Groups['label'].Value -cne "A$acEntry" -and $acNumberBad -eq 0) { $acNumberBad = $acEntry; $acNumberActual = $acShape.Groups['label'].Value }
    }
    if ($acEntry -lt 3 -and $acShapeBad -eq 0) { $acShapeBad = $acEntry + 1 }
    if ($acShapeBad -gt 0) { $cardErrors += "[CARD-ACCEPTANCE-INVALID] [$name] entry=$acShapeBad reason=shape expected=>=3-block-double-quoted-strings" }
    if ($acNumberBad -gt 0) { $cardErrors += "[CARD-ACCEPTANCE-INVALID] [$name] entry=$acNumberBad reason=number expected=A$acNumberBad actual=$acNumberActual" }
  }

  # Upstream's later acceptance policy is intentionally inactive for the
  # legacy corpus; the block above is the preserved local behavior.
  # T103: a card whose diff a second reviewer will judge should close its own acceptance set, so rubric #6
  # is decided against an enumeration rather than an open one. WARNING, never an error - the field is
  # optional by design so every pre-T103 card keeps working, and adoption is a nudge rather than a wall.
  # Judged by Test-ScaffoldCardAcceptance (scripts/_cards.ps1), which takes card TEXT - this is its only
  # call site. What the rule can and cannot show is stated in the finding text, never in this comment.
  # T161/TD160: BLOCKING since T161, warning-only under T103. Measured adoption while it was a nudge was
  # 18 of 143 archived cards (13%), against rubric #6 accounting for 20 of 34 findings across 39 stored
  # verdicts - 17 of those 20 raised against code that already had a passing test. Without an
  # enumeration "a test is missing" is decided against an OPEN set, so every round can legitimately name
  # another untested branch and the review never reaches a fixed point. The rule keeps its T103 contract
  # exactly: it fires only where review_gate is declared, and counts ITEMS, not key presence.
  # Local compatibility: legacy review cards retain their existing acceptance handling.

  # T276/ADR 0016 item 5: an acceptance item may CITE an item of the card's optional `requirements:` list
  # as [R<n>], and a citation that resolves to nothing is refused. Judged by
  # Get-ScaffoldCardRequirementFinding (scripts/_cards.ps1), which takes card TEXT - this is its only call
  # site. BLOCKING, and narrow on purpose: it refuses a link that goes nowhere and asks nothing about
  # whether the requirements list is complete or covered, which are planning judgements the way the closed
  # acceptance list itself is. A card declaring no requirements and citing none is silent.
  # Seeded regression: selftest sub-gate 10u.
  # Local compatibility: legacy cards use requirement citations without the later front-matter field.

  # T285/ADR 0016 item 4: an optional `arbitration:` entry records the ruling that ends a maker-checker
  # impasse, and the ship reads it from the BASE card to make ONE reviewed Tier-S spec block advisory.
  # Judged by Get-ScaffoldCardArbitration (scripts/_cards.ps1) - this is its only call site outside the ship,
  # which is why it returns objects and this loop reads only the Finding half. BLOCKING for an entry that
  # cannot bind, silent for an absent field. Seeded regression: selftest sub-gate 10u.
  # Local compatibility: arbitration entries await a dedicated local policy card.

  # 前端测试启发式（建议性，不阻断）：动 frontend/ 的卡，其 dod_command 宜含确定性前端测试闸
  # （npm run verify / vitest / playwright）。漏了 → 前端卡可能没真测试就过 DoD（见 frontend/README.md「前端测试」）。
  # TD63 item6：此前对**任意**键下提到 frontend/ 的列表项都命中（如 forbid/non_goals 里写「不动 frontend/」
  # 这类无关列表项也会误触发）——收窄到目标键 allow_paths（这才是「卡真的改动 frontend/」的权威来源）。
  $allowItemsFe = Get-YamlListItems $fm 'allow_paths'
  if (($allowItemsFe -match 'frontend/') -and $dod -and ($dod -notmatch '(?i)verify|vitest|playwright|test')) {
    $cardWarns += "[$name] 卡改动 frontend/ 但 dod_command 未含前端测试闸（npm run verify / vitest / playwright）——前端卡宜跑确定性测试再过 DoD（见 frontend/README.md「前端测试」）"
  }

  # [CARD-PLACEHOLDER] unfilled placeholder (advisory). Pattern READ from the rule table (L226/T98) - the
  # angle half is anchored to a whole value, so an angle-bracketed fragment inside prose is not reported.
  if ($fm -match $placeholderPattern) { $cardWarns += "[$name] [CARD-PLACEHOLDER] a front-matter field still looks like an unfilled placeholder: it carries the 'path/to/' marker, or an angle-bracketed run that is the ENTIRE value of a field or of a block-list item. [FIX] replace it with the real value. An angle-bracketed fragment inside a sentence is legitimate card text and is not reported." }

  # TD111/L61（拒绝式，exit 1）：卡文不得含双大括号大写蛇形 token 形态字面量（真 token 只应出现在模板产物）——
  # 混进卡文会被 init 干跑冒烟（selftest 闸 8）替换真 token 污染卡、或留非真 token 触发残留占位符失败。用 **-cmatch**
  # （大小写敏感）：-match 会把 [A-Z_] 当 [A-Za-z_]、误拒小写/混合合法形态。复发案例/背景见卡 T52。selftest 闸10g 回归。
  if ($raw -cmatch $tokenLiteralPattern) {
    $tok111 = $Matches[0]
    $cardErrors += "[$name] [CARD-TOKEN-LITERAL] card text contains a template placeholder literal '$tok111' (double braces around an UPPER_SNAKE name) - real tokens belong only in template artifacts. Inside card text, the init dry-run smoke (selftest gate 8) either substitutes a real token and pollutes the card, or leaves a non-real token behind and trips the leftover-placeholder failure; card registration pushes straight to master, so that lands as a red CI (L61/TD111). [FIX] describe it in words instead (e.g. 'double braces around an UPPER_SNAKE name') - never write the real literal in a card."
  }

  # 收集跨卡检查所需字段（parallelizable_with 为可选字段，缺失即空列表、不受影响）。窄化模式下，
  # 其余在飞卡由本循环之后的补收 pass 填入——跨卡规则的语料永远是全部在飞卡（T177）。
  # T102: tds = the debt rows this card CLAIMS to repay, read from its id and title only (never the body -
  # body-wide, TD69 appears in 35 archived cards as lineage rather than as a claim); superseded = the card
  # has been retired in favour of another, so it is not an active claim.
  $cardMeta[$name] = @{
    allow      = @(Get-YamlListItems $fm 'allow_paths')
    par        = @(Get-YamlListItems $fm 'parallelizable_with')
    tds        = @(Get-ScaffoldCardTdClaims -Id $name -Title $title)
    superseded = [bool](Get-UncommentedValue (Get-Scalar $fm 'superseded_by'))
  }
}

# T177/TD173: fill the cross-card corpus for every OTHER live card. The detailed per-card validation above
# stays narrowed to $cards; this pass reads FRONT MATTER ONLY, which is all four cross-card rules consume.
# A neighbour whose front matter is missing or malformed is skipped rather than reported here - it gets its
# own findings when IT is the card being validated, and reporting it under someone else's ship would block
# on a file outside that card's allow_paths.
foreach ($lf in $liveCards) {
  $lname = [IO.Path]::GetFileNameWithoutExtension($lf)
  if ($cardMeta.ContainsKey($lname)) { continue }
  $lfm = Get-FrontMatter (Get-Content $lf -Raw)
  if (-not $lfm) { continue }
  $cardMeta[$lname] = @{
    allow      = @(Get-YamlListItems $lfm 'allow_paths')
    par        = @(Get-YamlListItems $lfm 'parallelizable_with')
    tds        = @(Get-ScaffoldCardTdClaims -Id $lname -Title (Get-UncommentedValue (Get-Scalar $lfm 'title')))
    superseded = [bool](Get-UncommentedValue (Get-Scalar $lfm 'superseded_by'))
  }
}

# --- 跨卡检查：声明并行的卡对 allow_paths 必须互不重叠 ---
# 对称处理：任一方在 parallelizable_with 声明另一方即比对（单向声明即生效——手写卡常见形态）。
# 比对规则：路径归一化（正斜杠、去尾斜杠）后做段级前缀重叠（a/b 与 a/b/c 重叠；a/b 与 a/bc 不重叠）。
# 重叠 = 并行前提被破坏（并行 worktree 合并会撞）。
# T177: $scope narrows which PAIRS are reported, never which cards are compared - an empty scope reports all.
function Get-ParallelOverlapErrors($meta, $scope = @()) {
  $errs = @(); $seen = @{}
  foreach ($a in @($meta.Keys)) {
    foreach ($b in @($meta[$a].par)) {
      if ($b -eq $a -or -not $meta.ContainsKey($b)) { continue }   # 自引用/未收集的卡 id：跳过
      $pairKey = (@($a, $b) | Sort-Object) -join '|'
      if ($seen.ContainsKey($pairKey)) { continue }                # 互声明的卡对只比对/报告一次
      $seen[$pairKey] = $true
      $overlaps = @()
      foreach ($pa in @($meta[$a].allow)) {
        $na = ($pa -replace '\\', '/').TrimEnd('/')
        foreach ($pb in @($meta[$b].allow)) {
          $nb = ($pb -replace '\\', '/').TrimEnd('/')
          if ($na -eq $nb -or $na.StartsWith("$nb/") -or $nb.StartsWith("$na/")) { $overlaps += "$pa ↔ $pb" }
        }
      }
      if ($overlaps.Count -gt 0) {
        if ($scope.Count -gt 0 -and $scope -cnotcontains $a -and $scope -cnotcontains $b) { continue }
        $errs += "[$a ∥ $b] 声明可并行（parallelizable_with）但 allow_paths 重叠：$($overlaps -join '；')——并行卡必须互不重叠（防并行 worktree 合并冲突）"
      }
    }
  }
  return $errs
}
# T177/TD173: this block used to sit behind `if (-not $TaskId)`, which made all four cross-card rules
# unreachable from task.ps1 - every phase passes a task id - so CI enforced a strictly stronger contract
# than ship. The corpus/scope split below is judged by Get-ScaffoldCardCheckScope in _cards.ps1, with the
# two rejected designs pinned by its declared examples. The seed self-check runs in BOTH modes now: an
# anti-vacuous guard that is itself switched off in the mode the loop actually uses guards nothing.
$ccScope = Get-ScaffoldCardCheckScope -LiveId @($cardMeta.Keys) -TaskId $TaskId
$scopeIds = @($ccScope.Scope)
# 内建种子自检（同 selftest 闸 17 的种子缺陷思路）：列表解析 + 重叠判定先对已知输入自证，再校验真实卡——
# 已知重叠（反斜杠+深一级+单向声明）必须恰报 1 条；同前缀字符串不同路径段不得误报；未知卡 id 须跳过。
# 逻辑退化 → 立即 FAIL，防「闸静默失效却全绿」。每次全卡运行都跑（task start / selftest 闸 ⑩ / CI），确定性覆盖。
$seedFm = "parallelizable_with: [T9-SEED-B]   # 行内列表`nallow_paths:`n  - scripts/foo/   # 块式列表+行内注释`n  - docs/x"
$seedMeta = @{
  'T9-SEED-A' = @{ allow = @(Get-YamlListItems $seedFm 'allow_paths'); par = @(Get-YamlListItems $seedFm 'parallelizable_with') + 'T9-MISSING' }
  'T9-SEED-B' = @{ allow = @('scripts\foo\bar.ps1'); par = @() }       # B 未回声明 A → 验证单向声明即比对
  'T9-SEED-C' = @{ allow = @('scripts/foobar'); par = @('T9-SEED-A') } # scripts/foobar 与 scripts/foo 同前缀字符串但不同路径段 → 不得误报
}
$seedErrs = @(Get-ParallelOverlapErrors $seedMeta)
if ($seedErrs.Count -ne 1 -or $seedErrs[0] -notmatch 'T9-SEED-A ∥ T9-SEED-B') {
  Write-Host "错误：`n  - 内建种子自检失败：并行重叠判定/列表解析逻辑异常（期望恰 1 条 [T9-SEED-A ∥ T9-SEED-B] 重叠错误，实得 $($seedErrs.Count) 条）" -ForegroundColor Red
  Write-Host "`ncheck-cards: FAIL" -ForegroundColor Red
  exit 1
}
$cardErrors += @(Get-ParallelOverlapErrors $cardMeta $scopeIds)

# --- T102-TD147: two live cards must not claim one debt row without a declared split ---------------
# Judged by Get-ScaffoldDupTdClaimErrors in _cards.ps1, which takes the tracker TEXT so the rule is
# testable without a tracker on disk. A MISSING tracker switches the rule off inside that function - the
# same call $hasDebtCorpus makes for the reference rule below, and for the same reason: the message would
# otherwise name a file to declare the split in that is not on disk, which is a freshly initialised
# downstream and, since T177, one this rule reaches at -Phase start (TD174). A tracker that exists and
# does not declare the split still fails - that pair is the rule, and TD144 is what it cost.
# The id-reuse half is ADVISORY by owner decision (2026-08-23): T<n> is a stage number and specs/README.md
# lists same-stage ids as compliant, so reuse cannot block without rewriting the primary-key contract.
# The corpus it compares against includes the cold store, which check-cards never validates but which is
# exactly where an already-allocated number hides.
$trackerPath = Join-Path $RepoRoot 'specs/tech-debt-tracker.md'
$trackerText = if (Test-Path $trackerPath) { Get-Content $trackerPath -Raw } else { '' }
$cardErrors += @(Get-ScaffoldDupTdClaimErrors -Meta $cardMeta -TrackerText $trackerText -ScopeId $scopeIds)
# T177: the corpus is EVERY live card plus the cold store, never just the scoped one - that is the whole
# point of the split, and narrowing it here would report each of the scoped card's references to its live
# neighbours as dangling.
$corpusIds = @($ccScope.Corpus) + @(
  Get-ChildItem (Join-Path $RepoRoot 'specs/archive/tasks') -Filter *.md -ErrorAction SilentlyContinue |
    ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_.Name) }
)
$cardWarns += @(Get-ScaffoldCardIdReuseWarnings -LiveIds $scopeIds -CorpusIds $corpusIds)

# T159 [CARD-REF-DANGLING]: every id a LIVE card references must resolve. Gate 16 already does exactly
# this for L-ids across skills and docs; this is the same check extended to the other two id spaces.
# Card ids resolve against live + cold store ($corpusIds above - the cold store is precisely where a
# renamed card's old id is NOT, which is the breakage). Debt ids resolve against the live tracker plus
# both cold-store debt files. ARCHIVED cards are never scanned: their references are historical records
# and specs/README.md forbids rewriting frozen history to satisfy a rule that came later.
$debtIds = @([regex]::Matches($trackerText, '(?m)^\|\s*(TD\d+)\s*\|') | ForEach-Object { $_.Groups[1].Value })
foreach ($dArch in @('specs/archive/tech-debt-archive.md', 'specs/archive/tech-debt-index.md')) {
  $dPath = Join-Path $RepoRoot $dArch
  if (Test-Path $dPath) { $debtIds += @([regex]::Matches((Get-Content $dPath -Raw), '\bTD\d+\b') | ForEach-Object { $_.Value }) }
}
$debtIds = @($debtIds | Sort-Object -Unique)
# GRACEFUL DEGRADATION, per the repo's empty-config iron rule - and it is load-bearing, not politeness.
# A reference can only be judged against a corpus that exists. A tree with no cold store cannot tell
# "renamed away" from "this project has no archive yet", and a tree with no tracker cannot resolve TD ids
# at all, so each arm switches OFF rather than blocking every card in such a tree. Measured: without this,
# gate 10c's seeded fixture went red - its LEGAL card mentions TD112 in prose, and the fixture tree has
# neither tracker nor archive, so a legal card became unshippable. A freshly initialised downstream is the
# same shape. Each arm is judged separately: a project may have a tracker but no archived cards yet.
$enableDanglingReferencePolicy = $false # Current local corpus contains historical references not yet migrated.
$hasCardCorpus = $enableDanglingReferencePolicy -and (Test-Path (Join-Path $RepoRoot 'specs/archive/tasks'))
$hasDebtCorpus = ($trackerText -ne '') -or ($debtIds.Count -gt 0)
foreach ($liveId in @($scopeIds | Sort-Object)) {
  if (-not $enableDanglingReferencePolicy) { continue }
  $liveCardPath = Join-Path $RepoRoot "specs/tasks/$liveId.md"
  if (-not (Test-Path $liveCardPath)) { continue }
  foreach ($ref in @(Get-ScaffoldDanglingCardRef -CardText (Get-Content $liveCardPath -Raw) -KnownCardId $corpusIds -KnownDebtId $debtIds -SelfId $liveId)) {
    # 'bare' degrades with the card corpus too, not just 'card': a bare T-number is resolved against the
    # NUMBERS the corpus carries, and most prose references an ARCHIVED card by number. Without a cold
    # store every such mention looks unknown, so the arm would emit a warning per card in any minimal
    # tree - which is what gate 10j's fixture showed, where the noise even tripped that gate's own
    # whole-output substring assertion because the fixture's card ids contain a TD number.
    if ($ref.Kind -in @('card', 'bare') -and -not $hasCardCorpus) { continue }
    if ($ref.Kind -eq 'debt' -and -not $hasDebtCorpus) { continue }
    if ($ref.Severity -eq 'block') {
      $what = if ($ref.Kind -eq 'debt') { 'no row in specs/tech-debt-tracker.md and no entry in the cold store' } else { 'no card in specs/tasks/ and none in specs/archive/tasks/' }
      $cardErrors += "[$liveId] [CARD-REF-DANGLING] references '$($ref.Ref)', which resolves to $what. Either the id is a typo, or the thing it named was RENAMED - renaming a card that already exists silently invalidates every reference held elsewhere, which is why [CARD-ID-REUSE] no longer tells you to renumber. [FIX] point the reference at the id that exists now, or restore the id that was renamed away."
    }
    else {
      $cardWarns += "[$liveId] [CARD-REF-DANGLING] mentions '$($ref.Ref)', and no card carries that number. A bare T-number is ambiguous - it may be a stage reference in prose - so this only warns. [FIX] if you meant a specific card, write its full id (T<n>-NAME) so the blocking arm can check it."
    }
  }
}

if ($cardWarns) { Write-Host '警告（建议处理）：' -ForegroundColor Yellow; $cardWarns | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($cardErrors) {
  Write-Host '错误：' -ForegroundColor Red
  $cardErrors | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  Write-Host "`ncheck-cards: FAIL" -ForegroundColor Red
  exit 1
}
Write-Host "check-cards: PASS（校验 $($cards.Count) 张卡）" -ForegroundColor Green
exit 0
