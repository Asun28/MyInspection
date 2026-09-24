# T46-W8-CARDSLIB

function Get-FrontMatter($raw) {
  # TD60/TD-123：闭合 `---` 须锚定到整行（其后只允许行尾空白，再接换行或文件末尾）——不锚定时，
  # front-matter 内某行只要「以 --- 开头」（哪怕后面还有文字，非真正的闭合符）就会被非贪婪 .*? 提前
  # 当作闭合命中，导致真正的闭合符之后（其实还在 front-matter 内）的键被切进「正文」而「消失」。
  # TD130: the opening anchor also accepts one optional U+FEFF. Card text fetched through a pipe
  # (git show BASEREF:specs/tasks/CARDID.md) keeps the file's UTF-8 BOM - only Get-Content's file reader
  # strips it - so a bare \A--- anchor missed, front-matter parsed as null, allow_paths came back empty and
  # the full-form check-scope fail-closed with [SCOPE-UNDECIDABLE], blaming the card for paths it declared.
  # Stripped here, in the single shared parser, so every caller heals at once (never per call site: that drifts).
  # The codepoint is written as an escape - no literal BOM byte in source (L193).
  $m = [regex]::Match($raw, '(?s)\A\uFEFF?---\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\z)')
  if ($m.Success) { return $m.Groups[1].Value }
  return $null
}
function Get-Scalar($fm, $n) {
  $m = [regex]::Match($fm, ((Get-ScaffoldScalarPattern) -f [regex]::Escape($n)))
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value
}
# -- T101-CARD-SCALAR-EMPTY: the single-line scalar reader's pattern, and the two shapes it must NOT be --
# Get-Scalar reads ONE line, `key: value`. Its colon separators must not use the shorthand whitespace class:
# that class matches a newline, so on an EMPTY field the engine walks past the line break and the capture
# binds to the FOLLOWING line's text (TD143, an instance of L230). Both rejected shapes are declared here BY
# NAME so the fixture below can prove they are caught, instead of a comment asking the next reader to
# remember them:
#   legacy-crossline - the original. \s on both sides of the colon; crosses newlines.
#   naive-narrowing  - the obvious repair. Spaces and tabs only, which leaves a stray carriage return glued
#                      to every value on a CRLF checkout - the over-wide original happened to absorb it, so
#                      this "fix" is a silent Windows regression. The live pattern keeps \r in the TRAILING
#                      class for exactly that reason, and nothing but the CRLF example proves it.
# {0} is the already-escaped field name.
function Get-ScaffoldScalarPatternSet {
  return @{
    'live'             = '(?m)^{0}[ \t]*:[ \t]*(.*?)[ \t\r]*$'
    'legacy-crossline' = '(?m)^{0}\s*:\s*(.*?)\s*$'
    'naive-narrowing'  = '(?m)^{0}[ \t]*:[ \t]*(.*?)[ \t]*$'
  }
}
function Get-ScaffoldScalarPattern { return (Get-ScaffoldScalarPatternSet)['live'] }

# Fixture card TEXT for the examples below. Built from a line array rather than a here-string so the SAME
# card can be emitted with LF or CRLF endings - the CRLF arm is the only thing that catches naive-narrowing,
# and it cannot exist if the fixture's line endings are frozen by the source file's own encoding.
# -WithDiagnosis appends the BLOCK form (bare key, children indented), which is how 19 of the 22 archived
# bugfix cards actually write it; 2 write it inline and 1 uses a block scalar.
function Get-ScaffoldScalarFixture {
  [CmdletBinding()]
  param([ValidateSet('lf', 'crlf')][string]$Newline = 'lf', [switch]$WithDiagnosis)
  $lines = @(
    '---',
    'id: T9-X',
    'status:',                    # EMPTY, immediately followed by a populated key - the reported defect
    'branch: T9-X',
    'title:',                     # EMPTY, and its consumer is Get-PrTitle's summary half
    'depends_on: [T86]',
    'worktree: C:\wt\T9-X'
  )
  if ($WithDiagnosis) { $lines += @('diagnosis:', '  root_cause: seeded', '  same_class: seeded') }
  $lines += @('---', 'body text')
  return ($lines -join $(if ($Newline -eq 'crlf') { "`r`n" } else { "`n" }))
}

# Reads one field either through the LIVE function (so the default run tests the code, not a string) or
# through a throwaway reader built from a named rejected shape (so a Variant run tests that shape).
function Get-ScaffoldScalarVia($fm, $n, $fmt) {
  if ($null -eq $fmt) { return (Get-Scalar $fm $n) }
  $m = [regex]::Match($fm, ($fmt -f [regex]::Escape($n)))
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value
}

# A front-matter key is PRESENT when its key line exists, regardless of whether the value sits inline or in
# an indented block beneath it. Get-PrTitle's diagnosis check has always meant this - a successful match
# always yields at least the empty string, so `$null -ne (Get-Scalar ...)` was already a key-presence test -
# but it only meant it by accident of the over-wide pattern. Stated explicitly here it survives the
# narrowing; rewritten as a non-empty-VALUE test it would misclassify 19 of 22 archived bugfix cards.
function Test-ScaffoldFrontMatterKey($fm, $n) {
  if ($null -eq $fm) { return $false }
  return [regex]::IsMatch($fm, "(?m)^$([regex]::Escape($n))[ \t]*:")
}

# Declared examples for the scalar reader, one per field CLASS, run under both line-ending conventions.
# Returns findings as strings and never throws (same contract as Test-ScaffoldCardRuleExamples).
# Default = exercise the live Get-Scalar. -Variant = re-run the same examples through a rejected shape,
# which must produce at least one finding; that is the negative control that keeps the default run honest.
function Test-ScaffoldScalarExamples {
  [CmdletBinding()]
  param([ValidateSet('legacy-crossline', 'naive-narrowing')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $fmt = if ($useVariant) { (Get-ScaffoldScalarPatternSet)[$Variant] } else { $null }
  $expect = [ordered]@{
    'status'     = ''            # empty inline scalar: must NOT capture the next line
    'title'      = ''            # empty, and read by Get-PrTitle
    'id'         = 'T9-X'
    'branch'     = 'T9-X'
    'depends_on' = '[T86]'
    'worktree'   = 'C:\wt\T9-X'  # last key before the closing fence: nothing follows it to capture
  }
  $findings = @()
  foreach ($nl in @('lf', 'crlf')) {
    $fm = Get-FrontMatter (Get-ScaffoldScalarFixture -Newline $nl)
    if ($null -eq $fm) { $findings += "[SCALAR-EXAMPLE] $nl fixture front matter failed to parse at all"; continue }
    foreach ($k in $expect.Keys) {
      $got = Get-ScaffoldScalarVia $fm $k $fmt
      if ($got -cne $expect[$k]) { $findings += "[SCALAR-EXAMPLE] $nl field '$k' read as '$got', expected '$($expect[$k])' - an empty field must return empty, never the following line's text (TD143). [FIX] fix the pattern, never the example." }
    }
    $absent = Get-ScaffoldScalarVia $fm 'no_such_key' $fmt
    if ($null -ne $absent) { $findings += "[SCALAR-EXAMPLE] $nl absent key read as '$absent', expected null - a missing key and an empty key are different answers." }
  }
  if (-not $useVariant) {
    # Consumer arms: these call Get-PrTitle, which reaches Get-Scalar directly, so a Variant cannot reach
    # them and they are proven by the hygiene mutations instead.
    $noDiag = Get-PrTitle -TaskId 'T9-X' -CardText (Get-ScaffoldScalarFixture)
    if ($noDiag -cne 'feat: [T9-X]') { $findings += "[SCALAR-EXAMPLE] Get-PrTitle on a card with an EMPTY title and no diagnosis returned '$noDiag', expected 'feat: [T9-X]' - an empty title must add no summary, and a card with no diagnosis key is not a fix." }
    $withDiag = Get-PrTitle -TaskId 'T9-X' -CardText (Get-ScaffoldScalarFixture -WithDiagnosis)
    if ($withDiag -cne 'fix: [T9-X]') { $findings += "[SCALAR-EXAMPLE] Get-PrTitle on a card whose diagnosis is a BARE KEY with an indented block returned '$withDiag', expected 'fix: [T9-X]' - 19 of 22 archived bugfix cards are shaped like this, so a non-empty-value presence test would reclassify them all as features." }
  }
  return $findings
}
function Get-UncommentedValue($v) {
  if ($null -eq $v) { return $null }
  return ($v -replace '\s+#.*$', '').Trim()
}
# 数某个 YAML 列表键（如 allow_paths）下的 `-` 列表项数量：从该键行起，到下一个非缩进行止。
function Get-YamlListCount($fm, $key) {
  $lines = $fm -split '\r?\n'
  $in = $false; $n = 0
  foreach ($ln in $lines) {
    if (-not $in) {
      if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }
      continue
    }
    # TD112：任何非缩进行即终止（同 Get-YamlBlockListItems；旧「须含冒号」条件会把垃圾行后的项也数入）
    if ($ln -match '^\S') { break }
    if ($ln -match '^\s*-\s+\S') { $n++ }                  # 一个列表项
  }
  return $n
}
# ── 两个列表取值器：差异是**有意设计**，勿当重复代码合并 ────────────────────────────────────
# 分工：Get-YamlListItems 服务 check-cards **校验**（要能「看见」畸形写法才能拒绝）；Get-YamlBlockListItems
# 服务 ship 范围闸**执行**（窄且 fail-closed，逐字复刻它替换掉的 task.ps1 手写行走器）。TD112 起终止规则
# 同语义（任何非缩进行即终止），唯一剩余行为差异 = 行内 flow `[a, b]`：ListItems 认（check-cards 因此能
# 拒绝它），BlockListItems 一律 0 项——**不得**合并回去，那会放宽 check-cards 对行内写法的可见性。
# （两函数项正则拼写仍异，但 `^\S` 终止先于项匹配、非缩进 `- 项` 两边都到不了项匹配器：拼写差异不可观测。）
# 机检：selftest 闸 10d 族（行内→0 项 · 块式正常 · 畸形终止 · 接线断言 + 校验侧 CARD-FM-GARBAGE 夹具）。
function Get-YamlBlockListItems($fm, $key) {
  $items = @(); $in = $false
  foreach ($ln in ($fm -split '\r?\n')) {
    if (-not $in) { if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }; continue }
    if ($ln -match '^\s+-\s+(.+)$') {
      $v = Get-UncommentedValue $Matches[1]
      if ($v) { $items += $v.Trim('"').Trim("'") }
    }
    elseif ($ln -match '^\S') { break }   # 任何非缩进行即列表结束（TD112 起校验侧取值器同语义）
  }
  return $items
}
# 块式 + 行内 `[a, b]` 皆认（行内可见才能被 check-cards 拒绝）；终止语义与唯一剩余差异见上方分工注释。
function Get-YamlListItems($fm, $key) {
  $m = [regex]::Match($fm, "(?m)^$([regex]::Escape($key))\s*:\s*(.*)$")
  if (-not $m.Success) { return @() }
  $inline = Get-UncommentedValue $m.Groups[1].Value
  if ($inline -match '^\[(.*)\]$') {
    return @($Matches[1] -split ',' | ForEach-Object { $_.Trim().Trim('"').Trim("'") } | Where-Object { $_ })
  }
  $items = @(); $in = $false
  foreach ($ln in ($fm -split '\r?\n')) {
    if (-not $in) { if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }; continue }
    if ($ln -match '^\S') { break }   # TD112：任何非缩进行即列表结束（同 Get-YamlBlockListItems）
    if ($ln -match '^\s*-\s+(.+)$') {
      $v = Get-UncommentedValue $Matches[1]
      if ($v) { $items += $v.Trim('"').Trim("'") }
    }
  }
  return $items
}

function Split-TdRow([string]$line) {
  ($line.Trim().Trim('|') -split '(?<!\\)\|') | ForEach-Object { $_.Trim() }
}

# ── TD199/T204-TD-ROW-CELL-SHAPE: a tech-debt row must split to the cell count its header declares ──────
# WHY THIS EXISTS. Split-TdRow above splits on an UNESCAPED bar, and every consumer then reads cells
# POSITIONALLY - archive.ps1 decides paid|accepted by the status cell's header index (:222), triage.ps1
# reads the same way (:167, :176). So an unescaped bar inside a cell BODY inserts a cell, shifts every later
# cell one to the right, and the consumer reads severity where status should be: it matches neither paid nor
# accepted, and archive.ps1 keeps the row hot through its deliberately conservative branch. Nothing reports a
# row it declined to sweep, so the only symptom is a sweep count one lower than expected - the defect fails
# SAFE, which is exactly why it survived. Hit live 2026-08-28 on TD194, which quoted the very predicate it
# was filed against and would not sweep.
#
# THE RULE ASSERTS THE SHAPE, NOT THE SYMPTOM, and that is the load-bearing choice rather than a stylistic
# one. Measured over the cold archive at T204: TD39 (8 cells), TD53 (15) and TD129 (9) each carry their stray
# bars in the TRAILING pointer cell, AFTER status - so all three still read `paid` at the status index and
# swept correctly for months. A rule keyed on "this row will not sweep" calls all three healthy and leaves
# the next author one column away from the fatal position. Shape is checkable; the symptom is not.
#
# A PROJECTION OF THE PARSER, NEVER A SECOND ONE (L289). The check calls Split-TdRow itself, so it cannot
# drift from the consumer it defends. Re-implementing the split here would recreate the exact second source
# of truth this rule exists to catch, which is the failure mode L289 was recorded for.
#
# THE EXPECTED COUNT IS READ OFF THE HEADER, never hardcoded (L97): the corpus states its own column count,
# so adding a column stays a header edit instead of a hunt for a literal. A file that carries data rows and
# NO header row is itself reported - with no header there is no count to judge against, and silently judging
# nothing is precisely how a guard fail-opens.
function Get-ScaffoldTdRowShapeIssue {
  [CmdletBinding()]
  # Text is the primary input so the rule is testable with no file on disk - the shape
  # Get-ScaffoldDupTdClaimErrors already uses below, and for the same reason. Path is the convenience the
  # production callers and this card's DoD use. Rel only LABELS the finding; it never changes the verdict.
  param([string]$Path = '', [string]$Text = '', [string]$Rel = '')
  if ($Path -and -not $Text) {
    # A corpus that does not exist declares nothing to judge - the same graceful degradation the sibling
    # rules give a freshly initialised downstream, where specs/archive/ has not been created yet (TD174).
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    if (-not $Rel) { $Rel = $Path }
    $Text = (Get-Content -LiteralPath $Path -Raw)
  }
  if (-not $Rel) { $Rel = '<text>' }
  if ($null -eq $Text -or $Text.Trim() -eq '') { return @() }

  $want = 0
  $dataRows = @()
  $lineNo = 0
  foreach ($ln in ($Text -split '\r?\n')) {
    $lineNo++
    if ($ln -notmatch '^\s*\|') { continue }
    $cells = @(Split-TdRow $ln)
    if ($cells.Count -lt 1) { continue }
    if ($cells[0] -eq 'id') { if ($want -eq 0) { $want = $cells.Count }; continue }
    # Separator row (|---|:--:|...): every non-empty cell is dashes with optional colons. Same predicate as
    # archive.ps1's Test-SeparatorRow - inlined rather than shared because that helper lives in the caller.
    $nonEmpty = @($cells | Where-Object { $_ -ne '' })
    if ($nonEmpty.Count -gt 0 -and @($nonEmpty | Where-Object { $_ -notmatch '^:?-+:?$' }).Count -eq 0) { continue }
    $dataRows += , @{ id = $cells[0]; count = $cells.Count; line = $lineNo }
  }
  if (-not $dataRows.Count) { return @() }
  if ($want -eq 0) {
    return @("[TD-ROW-CELLS] $Rel carries $($dataRows.Count) data row(s) but no header row starting with an 'id' cell, so there is no declared column count to judge them against. [FIX] restore the header row rather than leaving every row's shape unjudged.")
  }
  $findings = @()
  foreach ($r in $dataRows) {
    if ($r.count -eq $want) { continue }
    $findings += "[TD-ROW-CELLS] $($Rel):$($r.line) row '$($r.id)' splits into $($r.count) cells, but the header declares $want. An unescaped vertical bar in a cell inserts a cell and shifts every later cell right, so a consumer reading the status column by index reads the wrong cell and the row silently stops sweeping. [FIX] escape that literal bar as \| so the row parses as written."
  }
  return $findings
}

# Declared examples for the rule above. CONVENTION NOTE, because this differs from the -Variant convention
# used by Test-ScaffoldCardAcceptanceExamples and its siblings further down this file: there a variant
# WEAKENS the rule and the returned finding says the weakening went unnoticed. HERE a variant selects which
# fixture CORPUS to run the unmodified rule over, and what comes back is the rule's own output. No variant =
# a well-formed corpus, which must produce nothing; 'body-bar' and 'pointer-bar' = the two positions an
# unescaped bar can occupy, both of which must produce a finding. The pointer-bar example is the one that
# earns its keep: such a row still sweeps correctly today, so a rule keyed on the symptom would pass it, and
# this example is what pins the rule to the shape instead. Header cells are ASCII here on purpose - the rule
# keys only on the first cell being 'id' and on the count, so the fixture need not carry the live corpus's
# Chinese column names to be faithful to what is actually being judged.
function Test-ScaffoldTdRowShapeExamples {
  [CmdletBinding()]
  param([ValidateSet('body-bar', 'pointer-bar')][string]$Variant)
  $header = '| id | found | location | debt | severity | status | pointer |'
  $sep    = '|---|---|---|---|---|---|---|'
  $row = switch ($Variant) {
    # A bar BEFORE the status column - TD194's position, the one that shifts status out from under its
    # reader and blocks the sweep.
    'body-bar'    { '| TD901 | 2026-01-01 | `scripts/x.ps1` | an unescaped bar in the body | splits here | minor | paid | PR #1 |' }
    # A bar AFTER the status column - TD39/TD53/TD129's position. Status still reads correctly and the row
    # still sweeps, so only a shape rule reports this one.
    'pointer-bar' { '| TD902 | 2026-01-01 | `scripts/x.ps1` | a well-formed body | minor | paid | PR #1, see `a|b` |' }
    # Well formed, and its bar is escaped exactly as the [FIX] text instructs - so this row doubles as the
    # live example of the escaped form that the corpus itself currently lacks.
    default       { '| TD900 | 2026-01-01 | `scripts/x.ps1` | an escaped bar stays one cell: `(?<!\\)\|` | minor | paid | PR #1 |' }
  }
  return @(Get-ScaffoldTdRowShapeIssue -Text (@($header, $sep, $row) -join "`n") -Rel '<declared example>')
}

# PR title (R4 ship) = Conventional Commits `type(scope): [TaskId] summary`, e.g.
#   feat(scripts): [T85-PR-TITLE] generate PR titles as Conventional Commits
# Generated, never hand-typed: format compliance follows from the construction, so no gate guards it.
# The space after the colon is required by the spec - `feat(scripts):[T85-...]` is rejected by commitlint
# and by the standard PR-title actions, so it is not a cosmetic choice. The PR number is never written
# here: it does not exist before `gh pr create`, and GitHub appends `(#N)` on squash merge.
# type and scope are both DERIVED from fields the card already carries - no new front-matter field:
#   type  = fix  when the card has an uncommented `diagnosis:` block (that field is what marks a bugfix card)
#           docs when every allow_paths entry sits under docs/
#           feat otherwise
#   scope = the most frequent first path segment across allow_paths (ties resolve to the one the card
#           lists first, so the value is stable); dropped when it would merely repeat the type, or when
#           AllowPaths is empty.
# Pure by design - the caller supplies the card text and the allow_paths list, so this is directly
# dot-sourceable and the card DoD executes it rather than grepping for it. AllowPaths must come from the
# shared scope core (Get-ScaffoldCardAllowPath), which is also what guarantees forward-slash separators;
# task.ps1 never parses card lists itself (gate 10d).
# A title is not a gate: every lookup degrades to a usable string and this function never throws.
function Get-PrTitle {
  param(
    [Parameter(Mandatory)][string]$TaskId,
    [string]$CardText = '',
    [string[]]$AllowPaths = @()
  )
  $type = 'feat'; $scope = ''
  # Forward slashes only - the shared scope core normalises separators before handing the list over.
  $segs = @($AllowPaths | ForEach-Object { ("$_" -split '/')[0] } | Where-Object { $_ -and $_ -ne '.' })
  if ($segs.Count) {
    $groups = @($segs | Group-Object)
    $top = ($groups | Measure-Object -Property Count -Maximum).Maximum
    $winners = @($groups | Where-Object { $_.Count -eq $top } | ForEach-Object { $_.Name })
    $scope = @($segs | Where-Object { $winners -contains $_ })[0]
    if (-not @($segs | Where-Object { $_ -ne 'docs' }).Count) { $type = 'docs' }
  }
  $summary = ''
  $fm = Get-FrontMatter $CardText
  if ($fm) {
    if (Test-ScaffoldFrontMatterKey $fm 'diagnosis') { $type = 'fix' }   # key-presence, not value: T101
    $summary = Get-UncommentedValue (Get-Scalar $fm 'title')
  }
  # `docs(docs)` states the same thing twice - collapse a scope that merely repeats the type.
  $prefix = if ($scope -and $scope -ne $type) { "$type($scope)" } else { $type }
  if ($summary) { return "${prefix}: [$TaskId] $summary" }
  return "${prefix}: [$TaskId]"
}

# ── T88-RULE-EXAMPLES: card-validation rules declared with the examples they must / must not match ──
# Each entry carries its Pattern together with the inputs that MUST match (Should) and the inputs that
# MUST NOT (ShouldNot). check-cards.ps1 CONSUMES these patterns and validates the table BEFORE it
# validates any card, so a rule whose pattern silently stopped matching fails on the next run rather
# than at some unknown future date. Adding an entry here without wiring it into check-cards.ps1 would
# recreate the second source of truth this table exists to remove - an example that does not sit beside
# the live pattern drifts away from it, which is the exact failure this shape prevents.
# Shape borrowed from openai/codex `codex-rs/execpolicy`, whose Starlark prefix_rules declare
# `match` / `not_match` beside `pattern` and are validated at load time.
# Complementary to, never a replacement for, the single-line-deletion mutation evidence in
# specs/mutations/ (L165): examples catch a pattern that stopped matching, mutation catches a pattern
# that never mattered. Neither subsumes the other.
# Every entry spells out all five keys: check-cards.ps1 runs under Set-StrictMode -Version Latest, where
# a missing key throws instead of reading as $null. One rule per line so that deleting a single line is a
# well-formed mutation (the entries are newline-separated, not comma-separated, for the same reason).
# CaseSensitive mirrors the operator check-cards.ps1 uses at that rule's call site (-cmatch/-cnotmatch vs
# -match), so the examples are judged exactly the way the live card check judges a card. Flip one here and
# you must flip its call site too, or the table stops testing what production actually runs.
function Get-ScaffoldCardRules {
  @(
    # Canonical card id (specs/README.md "ID naming"): T<stage>-<UPPER-KEBAB>. A downstream project that
    # wants a different id scheme edits THIS entry - the pattern and its examples move together.
    @{ Id = 'card-id'; Pattern = '^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$'; CaseSensitive = $true; Should = @('T0-SCAFFOLD', 'T2-API', 'T3-REVIEW-GATE', 'T88-RULE-EXAMPLES'); ShouldNot = @('t1-foo', 'T1_FOO', 'T1 FOO', 'my-task', 'T0-scaffold', 'T-NOSTAGE') }
    # [CARD-FM-GARBAGE] (TD112): a front-matter line that is unindented, carries no colon, and is not a
    # full-line comment. Applied per line: the first character is none of '#', whitespace or ':', and no
    # colon appears anywhere after it. An empty or whitespace-only line has no first character to match
    # and is therefore exempt - that is what the '^\S' half of the original three-condition test did.
    @{ Id = 'CARD-FM-GARBAGE'; Pattern = '^[^#\s:][^:]*$'; CaseSensitive = $false; Should = @('garbage', '- scripts/foo.ps1', '-', 'depends_on T86'); ShouldNot = @('key: value', 'allow_paths:', '  - scripts/foo.ps1', '# a full-line guidance comment kept from _TEMPLATE.md', '', '   ') }
    # [CARD-TOKEN-LITERAL] (L61/TD111): a template placeholder literal - double braces around an
    # UPPER_SNAKE name - must never appear in card text. The lowercase and mixed-case ShouldNot entries
    # are the regression face of the -cmatch fix: under a case-INsensitive test [A-Z_] would swallow
    # them, and they are legitimate card text. The Actions expression entry pins the `${{ ... }}` shape,
    # which is not an init token. This file is a .ps1, which init never token-substitutes and neither
    # selftest gate 5 nor gate 8a scans, so the literals below are safe to write out in full.
    @{ Id = 'CARD-TOKEN-LITERAL'; Pattern = '\{\{[A-Z_]+\}\}'; CaseSensitive = $true; Should = @('{{PROJECT_NAME}}', 'text before {{LESSONS_MUST_CAP}} and after'); ShouldNot = @('{{project_name}}', '{{Project_Name}}', '{PROJECT_NAME}', '{{PROJECT-NAME}}', '${{ github.ref }}') }
    # [CARD-PLACEHOLDER] (L226/T98): a declared field still holding an unfilled placeholder. Two halves.
    # The path-slash-to half is unchanged and matches anywhere - it has produced no false positive. The
    # angle-bracket half is anchored to the WHOLE VALUE: a field whose value is exactly one angle-bracketed
    # run, or a block-list item that is exactly one angle-bracketed run. Before the anchor it matched any
    # angle pair anywhere in the front matter and reported three pieces of finished card text in one session
    # (L226) - those three are the first three ShouldNot entries below, and they are what the anchor buys.
    # Anchoring can only SHRINK the matched set: anything the anchored half matches still contains a bare
    # angle pair, so the narrowed rule matches a strict subset of what the old one did. [ \t] rather than \s
    # throughout, so a per-line anchor cannot walk across a newline into the next field (L230).
    # TWO SURFACES CONSUME THIS ENTRY (T106/TD150), which is why the examples below carry card lines AND
    # handoff lines: check-cards.ps1 matches it against card front matter, and handoff.ps1 matches it -
    # through Test-ScaffoldFieldPlaceholder - against one reconstructed 'KEY: value' line of a HANDOFF
    # block. handoff.ps1 used to hold its own un-narrowed copy that fired on any angle pair anywhere, and
    # it rejected finished prose twice in one session: a DO-NOT value naming a path template and an UPDATED
    # value quoting the repo's own id contract. Those two are the last two ShouldNot entries. The rule text
    # is unchanged by that adoption - the whole-value anchor transfers as-is, which was TD150's claim.
    @{ Id = 'CARD-PLACEHOLDER'; Pattern = '(?m)path/to/|^(?:[^:\r\n]+:|[ \t]*-)[ \t]*<[^>\r\n]+>[ \t]*$'; CaseSensitive = $false; Should = @('  - path/to/...', 'dod_command: <your command here>', '  - <the capability this card deliberately does not build>', 'CARD: <path to the specs/tasks card, or none>'); ShouldNot = @('dod_assert: the parser must accept a <div> tag as test data', 'hygiene: delete the loop and arm 3 <returns> zero', 'non_goals: nothing under docs/adr/<name>.md', 'DO-NOT: never run task.ps1 from inside <WorktreeRoot>\<id> - it resolves to the worktree copy', 'UPDATED: T102 step 4 - rejected the rule that ids are unique, since the part after the T is a <stage> number') }
    # [CARD-HYGIENE-MUT] (T110/TD145): does this card's hygiene promise a mutation-evidence BATCH (L165), as
    # opposed to the template's mutation-survivor PRUNING line? Both contain the word `mutation`, and only the
    # first owes a registry under specs/mutations/ - 17 live and archived cards carry the template line and owe
    # nothing, which is why the ShouldNot list below is drawn from the real corpus rather than invented. The
    # conditional half (then allow_paths must cover BOTH the registry and its results TSV) is
    # Test-ScaffoldCardMutationPathsVia, which
    # READS this pattern; a flat pattern row cannot express the conditional, so the two halves live apart on
    # purpose. Widen this to the bare word and the first two ShouldNot entries turn the table red.
    @{ Id = 'CARD-HYGIENE-MUT'; Pattern = 'single-line[- ]deletion|单句删除|删除变异|mutation[- ]evidence|变异证据|mutate\.ps1|specs/mutations|\[MUT-'; CaseSensitive = $false; Should = @('Single-line-deletion mutations traced through the arms this card runs (L165/L237).', '每道新守卫配单句删除变异（L165/L167）：17base token 表删除→选择器拒 token', '预算守卫的删除变异（注掉预算检查行→17z 预算 case 红）在 worktree 内一次性验证', 'Single-line-deletion mutation evidence registered at specs/mutations/T110-MUT-EVIDENCE-PATHS.psd1', 'evidence batch run by scripts/mutate.ps1 from this worktree', '变异证据批见登记表'); ShouldNot = @('冗余测试经 mutation-survivor 剪枝（R4）', 'redundant assertions pruned via mutation-survivor (R4)', 'N/A（纯文档卡，无生产测试；DoD 即 Select-String 断言，无冗余测试可剪）', '纯文档卡；正确性由 R3 语义评审 + selftest 哨兵闸兜底', '无新测试框架；闸 10f/15g9 复用既有 hermetic 临时目录/e2e 种子手法') }
    # [CARD-DOD-SUITE] (T144): does this dod_command DRIVE the full selftest suite? 81 of 144 live and
    # archived cards do, and the cost is paid TWICE - once when the DoD runs and again when the mandatory
    # pre-ship acceptance run covers the same ground. T118's own dod_assert records the measurement: "Total
    # DoD wall about 170s plus 230s", after which -Parallel re-ran both regions.
    # The discriminator is the INVOCATION, not the filename: `-File …selftest.ps1` RUNS it, while
    # `-Path …selftest.ps1` merely READS it, and a static literal assertion over the shard list (the cheap
    # alternative this rule steers authors toward, demonstrated in T118's own arms 7-9) costs milliseconds.
    # A pattern keyed on the bare filename would flag exactly the assertion shape it is meant to encourage,
    # which is why the third ShouldNot entry is that shape drawn verbatim from the corpus.
    # TWO ESCAPES, both in the lookahead rather than in a caller: `-Only` means the run is already scoped to
    # a region, and `suite-required` is the author's DECLARED reason. The rule asks for a reason, never for
    # abstinence - a card that edits selftest.ps1 itself genuinely cannot prove the union with a scoped run,
    # and the fourth ShouldNot entry is that card, which is what keeps this a question rather than a ban.
    @{ Id = 'CARD-DOD-SUITE'; Pattern = '-File\s+[^\s]*selftest\.ps1(?![^\r\n]*(?:-Only|suite-required))'; CaseSensitive = $false; Should = @('pwsh -File scripts/selftest.ps1', 'pwsh -NoProfile -File scripts\selftest.ps1 -Parallel', 'cmd1; pwsh -File scripts\selftest.ps1; cmd2'); ShouldNot = @('pwsh -File scripts/selftest.ps1 -Only 14', 'pwsh -NoProfile -File scripts\selftest.ps1 -Only 17t,17ac', 'pwsh -NoProfile -Command "if (-not (Select-String -Path scripts/selftest.ps1 -Pattern 17pre -Quiet)) { exit 1 }; exit 0"', 'pwsh -File scripts\selftest.ps1  # suite-required: this card edits selftest.ps1 itself, so no scoped run can prove the union') }
    # [CARD-REF-DANGLING] (T159): the SHAPE of a full-form card reference. The resolution half - does this id
    # exist in live or archive - is Get-ScaffoldDanglingCardRef, which READS this pattern; a flat row cannot
    # express "resolves against a set supplied by the caller", so the two halves live apart on purpose (same
    # split as CARD-HYGIENE-MUT). Case-SENSITIVE, because ids are uppercase by the card-id rule above and a
    # case-insensitive read would swallow lowercase prose. The ShouldNot list is what keeps the rule narrow:
    # a bare T-number is ambiguous (it may be a stage reference) and is handled as a WARN elsewhere, and TD
    # ids are a different id space with their own resolution set.
    # The (?!T\d+\b) lookahead is not decoration - it is the RANGE exclusion, and it was added because the
    # rule's first run blocked its own card. Prose says "two sessions collided on T121-T123", and without the
    # lookahead that parses as a card id whose name is "T123", since T123 is a legal [A-Z0-9]+ name segment.
    # A range is ordinary card prose, so the fix belongs in the rule. It stays narrow: a real name beginning
    # with a T-number and continuing into letters (T5-T2MIGRATION) has no word boundary after its digits, so
    # the lookahead does not fire and the id still matches.
    @{ Id = 'CARD-REF-DANGLING'; Pattern = '\bT\d+-(?!T\d+\b)[A-Z0-9]+(?:-[A-Z0-9]+)*\b'; CaseSensitive = $true; Should = @('T118-SHARD-SPLIT', 'T0-SCAFFOLD', 'depends_on: T1-ALPHA', 'see T96-LINEAGE for the precedent'); ShouldNot = @('T118', 'TD118', 't118-shard-split', 'T<n>-NAME', 'stage T1 work', 'two sessions collided on T121-T123', 'the T140-T162 arc') }
    # [CARD-TIER] (T273/ADR 0016): the legal shape of a DECLARED `tier:` VALUE - S, 1 or 0 and nothing else.
    # Case-SENSITIVE, because those three are the values check-cards prints and the ones every later tiered
    # gate reads; a lowercase 's' is a typo, and reading it as a declaration would let a card's stated intent
    # arrive through a spelling nobody checked. Get-ScaffoldCardTier READS this row and holds no second copy,
    # the same relationship Test-ScaffoldCardId has with the card-id row above.
    # The RAISE-ONLY half - is this declaration BELOW the tier the allow_paths compute - is the decision
    # function itself, reported with the sentinel [CARD-TIER-LOWER]: a flat pattern row cannot express a
    # comparison against a value derived from another field, so the two halves live apart on purpose. Same
    # split as CARD-HYGIENE-MUT (pattern here, conditional in Test-ScaffoldCardMutationPathsVia) and
    # CARD-REF-DANGLING (pattern here, resolution in Get-ScaffoldDanglingCardRef).
    @{ Id = 'CARD-TIER'; Pattern = '^(?:S|1|0)$'; CaseSensitive = $true; Should = @('S', '1', '0'); ShouldNot = @('s', '2', 'S1', '0.5', '', 'tier: S', 'Tier S') }
    # [CARD-REQ-DANGLING] (T276/ADR 0016 item 5): the SHAPE of a requirement citation on an `acceptance:`
    # line - a bracketed R followed by digits. The resolution half - does an `R<n>.` item under the card's
    # own optional `requirements:` list carry that number - is Get-ScaffoldCardRequirementFinding, which
    # READS this row; a flat pattern cannot express "resolves against a list the same card declares", so the
    # two halves live apart on purpose, the same split CARD-HYGIENE-MUT, CARD-REF-DANGLING and CARD-TIER use.
    # Case-SENSITIVE, because the ids are an uppercase R by the template's own form. The ShouldNot list is
    # drawn from the live corpus rather than invented: `[dod arm 1]` and `[FOLLOW-UP]` are the bracketed
    # tokens acceptance lines already carry, and a rule keyed on brackets alone reads both as citations.
    @{ Id = 'CARD-REQ-DANGLING'; Pattern = '\[R(\d+)\]'; CaseSensitive = $true; Should = @('[R1]', '1. the guard blocks a dangling citation. [R2]', 'x [R12] y'); ShouldNot = @('[dod arm 1]', '[FOLLOW-UP]', '[r1]', '[R]', '[RS]', 'R1. the requirement item itself') }
    # [CARD-ARBITRATION] (T285/ADR 0016 item 4): the SHAPE of the sha an `arbitration:` entry binds a ruling
    # to - a full 40-character git object name, lowercase. The other three shape rules an entry carries
    # (rounds >= 2, a ruling inside the enum, a non-empty by and reason) are Get-ScaffoldCardArbitration,
    # which READS this row; a flat pattern cannot express a numeric floor or an enum over four other keys, so
    # the halves live apart on purpose - the same split CARD-HYGIENE-MUT, CARD-REF-DANGLING, CARD-TIER and
    # CARD-REQ-DANGLING all use.
    # Case-SENSITIVE, and LOWERCASE ONLY, because that is what the ship COMPARES: review.ps1 writes the sha
    # from `git rev-parse HEAD`, which is lowercase, and the ship matches the entry's text against it. An
    # uppercase entry would parse as a sha to a human and match no verdict ever - the in-between state
    # [CARD-BUDGET] and [CARD-TIER-BADVALUE] already refuse - so it is rejected here with the repair printed
    # rather than left to fail silently at ship. The ShouldNot list carries the shapes a blanket waiver would
    # be written as: an abbreviated sha, a ref name, and the word every bypass reaches for.
    @{ Id = 'CARD-ARBITRATION'; Pattern = '^[0-9a-f]{40}$'; CaseSensitive = $true; Should = @('0123456789abcdef0123456789abcdef01234567', 'da39a3ee5e6b4b0d3255bfef95601890afd80709'); ShouldNot = @('43c6d18', '0123456789ABCDEF0123456789ABCDEF01234567', 'HEAD', 'master', 'all', '', '0123456789abcdef0123456789abcdef0123456', '0123456789abcdef0123456789abcdef012345678', 'g123456789abcdef0123456789abcdef01234567') }
  )
}

# -- T229-CARD-ID-SINGLE-SOURCE (TD231): the ONE judgement of "is this a legal card id" --
# Before this, the grammar had one authoritative declaration (the card-id row above) and THREE executable
# enforcers: check-cards read the row, while task.ps1 and check-scope.ps1 each hard-coded the same regex in
# a ValidatePattern attribute. The three literals were byte-identical and still disagreed, which is the part
# worth remembering: ValidatePattern defaults to RegexOptions.IgnoreCase, so both entry points ACCEPTED
# 't1-foo' and 'T0-scaffold' - two ids the row's own ShouldNot list names and check-cards rejects. A guard
# comparing the three literals would have called them in sync throughout. That is why the entry points now
# DERIVE instead: literal equality cannot see a difference that lives in the matching options.
#
# Both halves of the row are honoured here, not just the pattern. CaseSensitive selects -cmatch over -match,
# mirroring check-cards' own call site, so the examples in the row are judged exactly the way a live card is.
# Holds NO second copy and throws rather than falling back if the row is gone, per Test-ScaffoldDodSuiteCost.
function Test-ScaffoldCardId {
  [CmdletBinding()]
  param([AllowEmptyString()][AllowNull()][string]$Id)
  if ([string]::IsNullOrWhiteSpace($Id)) { return $false }
  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'card-id' })
  if ($rule.Count -ne 1) { throw "Test-ScaffoldCardId: rule card-id is missing from Get-ScaffoldCardRules - the declared table is the only source of this pattern and there is no fallback copy." }
  if ($rule[0].CaseSensitive) { return [bool]($Id -cmatch $rule[0].Pattern) }
  return [bool]($Id -match $rule[0].Pattern)
}

# -- T144-DOD-SELFTEST-DOUBLE-PAY: does this dod_command pay the suite wall a second time? --
# The pattern half lives in Get-ScaffoldCardRules above, beside its own Should/ShouldNot examples, per the
# T88 convention: a pattern that silently stops matching turns the table red on the next run. This is the
# consuming half, and it holds NO second copy of the pattern - it reads the declared row, exactly as
# Test-ScaffoldCardMutationPathsVia does, and throws rather than falling back if the row is gone.
function Test-ScaffoldDodSuiteCost {
  [CmdletBinding()]
  param([AllowEmptyString()][string]$DodText)
  if ([string]::IsNullOrWhiteSpace($DodText)) { return $false }
  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-DOD-SUITE' })
  if ($rule.Count -ne 1) { throw "Test-ScaffoldDodSuiteCost: rule CARD-DOD-SUITE is missing from Get-ScaffoldCardRules - the declared table is the only source of this pattern and there is no fallback copy." }
  return [bool]($DodText -match $rule[0].Pattern)
}

# T173/TD165: the negative control for the escape lookahead. This predicate had a bare param() until this
# card, so nothing could tell its silence from a dead judgement - and gate 10 RUNS it through check-cards
# but only WARNS on the answer, so deleting the decision line left gate 10 green too. 'ignore-only-escape'
# strips the -Only alternative out of the rule's own escape lookahead rather than restating the pattern
# here, so a run already scoped to regions reads as the unscoped suite and the ShouldNot example flips.
# If the rule is ever respelled so that alternative no longer appears, the strip becomes a no-op, the
# control stops flipping, and the 17z arm reddens - which is the correct outcome, not a false alarm.
function Test-ScaffoldDodSuiteCostVia($DodText, $Variant) {
  if ($Variant -ne 'ignore-only-escape') { return (Test-ScaffoldDodSuiteCost -DodText $DodText) }
  if ([string]::IsNullOrWhiteSpace($DodText)) { return $false }
  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-DOD-SUITE' })
  if ($rule.Count -ne 1) { throw "Test-ScaffoldDodSuiteCostVia: rule CARD-DOD-SUITE is missing from Get-ScaffoldCardRules - the declared table is the only source of this pattern and there is no fallback copy." }
  return [bool]($DodText -match ($rule[0].Pattern -replace '-Only\|', ''))
}

function Test-ScaffoldDodSuiteCostExamples {
  <#
  .SYNOPSIS  T144 arm 4: declared examples for the embedded-suite predicate. Returns findings; an empty
             result is green. Hermetic - synthetic dod text only, reads no card and no file.
  .DESCRIPTION
    The Should/ShouldNot table above is already exercised by Test-ScaffoldCardRuleExamples. What THIS table
    adds is the predicate's own contract at its edges: empty input is not a finding, and the two escapes
    survive being embedded in a realistic multi-command payload rather than appearing alone on a line.
    -Variant 'ignore-only-escape' drops the -Only escape and must produce a finding (T173).
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-only-escape')][string]$Variant)
  $findings = @()
  if (Test-ScaffoldDodSuiteCostVia '' $Variant) { $findings += '[CARD-DOD-SUITE-EXAMPLE] an empty dod_command was reported as driving the suite - a card with no DoD is check-cards other guards business, not this one.' }
  if (-not (Test-ScaffoldDodSuiteCostVia 'pwsh -File scripts\selftest.ps1' $Variant)) { $findings += '[CARD-DOD-SUITE-EXAMPLE] a bare full-suite invocation went unreported - that is the exact double-pay this rule exists for.' }
  if (-not (Test-ScaffoldDodSuiteCostVia 'pwsh -NoProfile -File scripts\selftest.ps1 -Parallel' $Variant)) { $findings += '[CARD-DOD-SUITE-EXAMPLE] -Parallel is still the whole suite, only faster, and went unreported.' }
  if (Test-ScaffoldDodSuiteCostVia 'pwsh -NoProfile -File scripts\selftest.ps1 -Only 14,17post' $Variant) { $findings += '[CARD-DOD-SUITE-EXAMPLE] a run already scoped to regions was reported - the rule targets the UNSCOPED suite, and flagging scoped runs would push authors back toward the expensive shape.' }
  if (Test-ScaffoldDodSuiteCostVia 'pwsh -NoProfile -Command "if (-not (Select-String -Path scripts/selftest.ps1 -Pattern 17pre -Quiet)) { exit 1 }; exit 0"' $Variant) { $findings += '[CARD-DOD-SUITE-EXAMPLE] a STATIC assertion that merely reads selftest.ps1 was reported as running it - the rule must key on the invocation (-File), never the filename, or it flags the very alternative it recommends.' }
  if (Test-ScaffoldDodSuiteCostVia 'pwsh -File scripts\selftest.ps1  # suite-required: this card edits selftest.ps1 itself' $Variant) { $findings += '[CARD-DOD-SUITE-EXAMPLE] a card that DECLARED why it must drive the suite was still reported - the rule asks for a reason, never for abstinence, and forbidding the pattern outright would block cards that change the suite itself.' }
  return $findings
}

# -- T107-ARCHIVE-WORKTREE-GUARD (L238): may this card be swept to the cold store? --
# Two conditions, and the second is the one the sweep used to miss. `status: merged` alone was the whole
# rule, but a card whose worktree is still on disk is not finished, and filing it cold hides the orphan that
# triage probes 9 and 11 exist to surface. Measured four times on this repo (T59, T63, T100, then T106 by
# the author of the lesson that records it). This predicate is what SEQUENCES the two R5 steps, and T293 did
# not change that: while the worktree is on disk the sweep cannot get ahead of the teardown. T293 fixed the
# far side of the hold - once the worktree is gone this returns true, the card moves, and `-Phase cleanup`
# used to refuse for want of it while the local branch was still held by its merge credential (issue #361).
# It no longer reads a card, so that residual can be finished.
# The judgement is a PATH TEST, not a git query: archive.ps1's header promises it runs offline with no
# git, no gh and no _config, and probe 9 in triage.ps1 already decides orphanhood the same way.
# -PathType Container is load-bearing. A bare Test-Path is the obvious implementation and it is wrong: a
# stray FILE at the worktree path would read as a live worktree and hold the card back forever, with
# cleanup unable to help because there is no worktree there to remove. That shape is declared BY NAME as
# the 'file-counts-as-worktree' variant below rather than left to a comment asking the next reader to
# remember it (same discipline as the scalar reader's rejected shapes above).
#   status-only              - the pre-T107 rule. Merged is sufficient; the worktree is never consulted.
#   file-counts-as-worktree  - the obvious repair. Existence rather than directory-ness.
function Test-ScaffoldCardSweepVia($CardText, $Variant) {
  $fm = Get-FrontMatter $CardText
  if ($null -eq $fm) { return $false }
  if ((Get-Scalar $fm 'status') -ne 'merged') { return $false }
  if ($Variant -eq 'status-only') { return $true }
  $wt = Get-Scalar $fm 'worktree'
  if ([string]::IsNullOrWhiteSpace($wt)) { return $true }
  if ($Variant -eq 'file-counts-as-worktree') { return (-not (Test-Path -LiteralPath $wt)) }
  return (-not (Test-Path -LiteralPath $wt -PathType Container))
}

# The live decision. Both consumers read it here so they cannot disagree about which cards are held:
# archive.ps1 decides whether to MOVE the card, triage.ps1 probe 11 decides which NEXT COMMAND to print.
# Judged separately they would drift, and the drift surfaces as a pending signal telling you to run the
# very command that just refused (T106 made the same two-consumer argument for the placeholder rule).
function Test-ScaffoldCardSweepable {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$CardText)
  return (Test-ScaffoldCardSweepVia $CardText $null)
}

# Declared examples for the sweep decision, one per case CLASS. Returns findings as strings and never
# throws (same contract as Test-ScaffoldCardRuleExamples). Default = exercise the live predicate.
# -Variant = re-run the same examples through a rejected shape, which MUST produce at least one finding;
# that is the negative control that keeps the default run honest, and per L239 it is the only arm that
# can fail before the function exists - so it is what a RED on this card must come from.
# The paths are hermetic without writing anything: this file's own directory is a directory that always
# exists, this file itself is a file that always exists, and a sibling name nothing creates never does.
function Test-ScaffoldCardSweepExamples {
  [CmdletBinding()]
  param([ValidateSet('status-only', 'file-counts-as-worktree')][string]$Variant)
  $liveDir = $PSScriptRoot
  $deadDir = Join-Path $PSScriptRoot 'no-such-worktree-t107'
  $aFile = Join-Path $PSScriptRoot '_cards.ps1'
  $mk = {
    param($status, $wtLine)
    (@('---', 'id: TZ-EXAMPLE', "status: $status") + @($wtLine | Where-Object { $null -ne $_ }) + @('---', 'body')) -join "`n"
  }
  $cases = @(
    @{ what = 'merged, worktree directory still on disk'; text = (& $mk 'merged' "worktree: $liveDir"); expect = $false }
    @{ what = 'merged, worktree path does not exist'; text = (& $mk 'merged' "worktree: $deadDir"); expect = $true }
    @{ what = 'merged, worktree field empty'; text = (& $mk 'merged' 'worktree:'); expect = $true }
    @{ what = 'merged, no worktree key at all (gate 12e fixture shape)'; text = (& $mk 'merged' $null); expect = $true }
    @{ what = 'todo, worktree path does not exist'; text = (& $mk 'todo' "worktree: $deadDir"); expect = $false }
    @{ what = 'merged, a FILE sits at the worktree path'; text = (& $mk 'merged' "worktree: $aFile"); expect = $true }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = Test-ScaffoldCardSweepVia $c.text $v
    if ($got -ne $c.expect) { $findings += "[CARD-SWEEPABLE-EXAMPLE] case '$($c.what)' judged sweepable=$got, expected $($c.expect) - a merged card whose worktree is still on disk must be HELD BACK, because a card still holding a worktree is unfinished and filing it cold hides the orphan (L238; since T293 this is no longer about protecting cleanup, which reads no card). [FIX] fix the predicate, never the example." }
  }
  return $findings
}

# The one judgement both card front matter and HANDOFF blocks make about a field (T106/TD150): is this
# value still an unfilled placeholder? Callers that hold a key and a value apart - handoff.ps1 does, its
# Get-Fields having already stripped the 'KEY: ' prefix - cannot match the rule directly, because the
# whole-value anchor is expressed against a whole LINE. Rebuilding that line here, once, is what lets the
# anchor transfer between surfaces instead of being re-derived per caller. A missing rule throws rather
# than falling back to a local pattern: a second copy is the exact defect this function exists to delete.
function Test-ScaffoldFieldPlaceholder {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Key,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Value
  )
  foreach ($rule in @(Get-ScaffoldCardRules)) {
    if ($rule.Id -eq 'CARD-PLACEHOLDER') { return (("${Key}: " + $Value) -match $rule.Pattern) }
  }
  throw "Test-ScaffoldFieldPlaceholder: rule CARD-PLACEHOLDER is missing from Get-ScaffoldCardRules - the declared table is the only source of this judgement and there is deliberately no fallback copy."
}

# Returns findings; an empty result means every example agrees with its pattern. With no arguments it
# walks the declared table (the load-time validation check-cards.ps1 performs). With an ad-hoc -Pattern
# plus -Should/-ShouldNot it judges that one pair, which is what makes the checker itself testable
# without seeding a fake rule into the real table.
# Findings are returned as strings and never thrown: check-cards.ps1 prints them like its other errors.
function Test-ScaffoldCardRuleExamples {
  [CmdletBinding()]
  param(
    [string]$Pattern,
    [string[]]$Should = @(),
    [string[]]$ShouldNot = @()
  )
  $rules = if ($PSBoundParameters.ContainsKey('Pattern')) {
    @(@{ Id = 'ad-hoc'; Pattern = $Pattern; CaseSensitive = $false; Should = @($Should); ShouldNot = @($ShouldNot) })
  } else { @(Get-ScaffoldCardRules) }
  $findings = @()
  foreach ($rule in $rules) {
    foreach ($ex in @($rule.Should)) { if (-not (($rule.CaseSensitive) ? ($ex -cmatch $rule.Pattern) : ($ex -match $rule.Pattern))) { $findings += "[CARD-RULE-EXAMPLE] rule '$($rule.Id)' pattern '$($rule.Pattern)' MUST match '$ex' but does not - the rule stopped catching what it exists to catch. [FIX] fix the pattern, never the example." } }
    foreach ($ex in @($rule.ShouldNot)) { if ((($rule.CaseSensitive) ? ($ex -cmatch $rule.Pattern) : ($ex -match $rule.Pattern))) { $findings += "[CARD-RULE-EXAMPLE] rule '$($rule.Id)' pattern '$($rule.Pattern)' MUST NOT match '$ex' but does - the rule grew wider than its contract. [FIX] fix the pattern, never the example." } }
  }
  return $findings
}

# -- T94-CARD-SWEEP: a cross-cutting card must record the L97 sweep it ran ---------------------------
# A card declaring MORE THAN FIVE allow_paths is cross-cutting by shape - that is the inherent shape of
# behaviorizing a rule several surfaces already teach (L97), not a scoping mistake. Such a card must carry
# a non-empty `sweep:` field naming the grep it ran and the teaching faces that grep found, written before
# the implementation starts.
# WHAT THIS CHECKS AND WHAT IT CANNOT: it checks that the declaration is present and says something.
# Whether the sweep was exhaustive is not machine-checkable and nothing here claims it is - no message may
# imply otherwise. The value is turning "I forgot to sweep" into "I have to write down that I swept",
# which is what stops the late allow_paths widening (11 such widenings across 10 cards before this rule).
# Reuses the shared parser (Get-FrontMatter / Get-YamlListCount / Get-UncommentedValue) - no second card
# parser. The sweep line keeps its own per-line regex, which is now a HISTORICAL reason rather than a live
# constraint: when this was written Get-Scalar separated the colon from the value with \s, which crosses a
# newline, so an EMPTY `sweep:` read that way returned the FOLLOWING line's text and a blank declaration
# looked filled (L230/TD143). T101-CARD-SCALAR-EMPTY repaired Get-Scalar, so reading the sweep line through
# it would be correct today; the reader is left as it is because swapping it is a behaviour change this
# rule does not need, not because Get-Scalar is still unsafe.
# Takes card TEXT rather than a path, so the rule is testable without writing a card file to disk.
# Returns findings as strings and never throws; check-cards.ps1 prints them like its other card errors.
function Test-ScaffoldCardSweep {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Text)
  $findings = @()
  $fm = Get-FrontMatter $Text
  if (-not $fm) { return $findings }            # no front-matter at all: check-cards already errors on that
  $pathCount = Get-YamlListCount $fm 'allow_paths'
  if ($pathCount -le 5) { return $findings }    # same >5 boundary the sizing warning uses; below it no sweep is required
  $sm = [regex]::Match($fm, '(?m)^sweep[ \t]*:[ \t]*(.*)$')
  $declared = if ($sm.Success) { Get-UncommentedValue $sm.Groups[1].Value } else { '' }
  if (-not $sm.Success) { $findings += "[CARD-SWEEP] declares $pathCount allow_paths (more than 5) and carries no 'sweep:' field - above that threshold a card is cross-cutting, and a cross-cutting card must declare the grep it ran and the teaching faces that grep found. The field records THAT a sweep was run; it cannot show the sweep was exhaustive and no gate can check that. [FIX] add a 'sweep:' line naming the query and the faces it found, or split the card so it declares five paths or fewer." }
  if ($sm.Success -and -not $declared) { $findings += "[CARD-SWEEP] declares $pathCount allow_paths (more than 5) and its 'sweep:' field is empty - the key on its own records nothing. The field records THAT a sweep was run; it cannot show the sweep was exhaustive and no gate can check that. [FIX] put the query you ran and the faces it found on the 'sweep:' line, or split the card so it declares five paths or fewer." }
  return $findings
}

# -- T233-CARD-BUDGET-SHIP (TD235): the `budget:` VALUE-shape rule -----------------------------------
# Three separate questions live on this field and each has ONE home:
#   * what the number MEANS (under / trip / over)  -> Get-ScaffoldCardBudgetDecision, scripts/_guard.ps1
#   * what the number IS for a live diff           -> scripts/check-budget.ps1 and the ship gate
#   * whether the declared VALUE is well formed    -> HERE, and only here.
# This one is card-time and pure, so it runs where every other card rule runs and blocks before a
# worktree exists rather than at ship, when the misdeclaration has already cost a full round.
#
# ABSENT IS LEGAL AND SILENT, and that is a contract, not an oversight. `budget:` is deliberately NOT a
# required field: cards are in flight continuously, so a rule that failed every card omitting it would
# red a peer's card mid-acceptance for a field their card predates. Absence degrades to the reporting-only
# meter - the same "empty means off, never broken" degradation FrozenPaths / DocSyncMap / DocBudgets give
# a freshly initialised downstream. An EMPTY value (`budget:` with nothing after it) reads as absent too,
# for one reason worth stating: Get-ScaffoldCardBudgetValue reads it as $null, so calling it malformed
# here would have check-cards reject a card the meter considers unbudgeted, and the two faces of one
# field would disagree about whether the card is off.
#
# What IS rejected is a value that was declared and cannot be a line count - a non-integer, zero, or a
# negative. Those are not "off"; they are a declaration that reads as a budget to a human and as absent
# to the reader, which is the worst of the two states: the card looks governed and is not.
function Get-ScaffoldCardBudgetFinding {
  <#
  .SYNOPSIS  T233/TD235: judge the SHAPE of a card's declared `budget:` value. Findings as strings, never
             throws. Absent or empty => no finding. A positive integer => no finding. Anything else => one
             finding carrying the [CARD-BUDGET] sentinel and naming the offending value.
  .DESCRIPTION
    Pure: front-matter TEXT in, findings out. No git, no IO, no config.

    The declaration is anchored the same way Get-ScaffoldCardBudgetValue anchors it - a line-start key,
    with a leading '#' reading as a comment and therefore as absent. Anchoring is what keeps `budget:`
    inside a sweep:/notes: prose block from being read as the declaration, and it is why the examples
    table below carries the commented-key and indented-key cases: they are the two shapes where a
    disagreement between this rule and the reader would be silent.

    Two variants exist, and each is a REJECTED SHAPE that makes one load-bearing rule falsifiable rather
    than merely intended:

      absent-rejected    reports a finding for a card with NO budget => proves absence does not block.
      accept-any-value   accepts every declared value                => proves malformed values are caught.

    A green table alone passes vacuously wherever the predicate is absent or matched nothing, which is the
    false-green this repo keeps paying for (L95, L239, TD236). The caller asserts BOTH directions.
  #>
  [CmdletBinding()]
  param(
    [string]$FrontMatter,
    [ValidateSet('absent-rejected', 'accept-any-value')][string]$Variant
  )
  $findings = @()
  $raw = ''
  $declared = $false
  foreach ($ln in (($FrontMatter -split "`r?`n"))) {
    if ($ln -match '^\s*#') { continue }
    if ($ln -match '^budget[ \t]*:[ \t]*([^#\r\n]*)') {
      $declared = $true
      $raw = $Matches[1].Trim().Trim('"').Trim("'")
      break
    }
  }

  if (-not $declared -or $raw -eq '') {
    if ($Variant -eq 'absent-rejected') {
      $findings += "[CARD-BUDGET] no 'budget:' declared. This finding is the REJECTED shape: a required budget would red every in-flight card that predates the field. [FIX] never make this a real finding - absence degrades to the reporting-only meter."
    }
    return $findings
  }
  if ($Variant -eq 'accept-any-value') { return $findings }

  $n = 0
  if (-not [int]::TryParse($raw, [ref]$n)) {
    $findings += "[CARD-BUDGET] budget: '$raw' is not an integer, so Get-ScaffoldCardBudgetValue reads it as ABSENT - the card looks budgeted to a reader and is unbudgeted to every gate, which is worse than declaring nothing. [FIX] write a plain integer line count (e.g. budget: 400), or delete the key to opt out on purpose."
  }
  elseif ($n -le 0) {
    $findings += "[CARD-BUDGET] budget: $n is not a usable line count - a budget at or below zero means every non-empty diff is instantly over, so the card can never ship without raising it on the base branch first. [FIX] declare the net changed lines this card is expected to cost, or delete the key to opt out on purpose."
  }
  return $findings
}

function Test-ScaffoldCardBudgetFindingExamples {
  <#
  .SYNOPSIS  Declared examples for the `budget:` value-shape rule. Returns findings as strings; never
             throws. Hermetic - every input is a literal.
  .DESCRIPTION
    The table alone is a GREEN-SIDE CONTROL and is never read on its own: an assertion that a table
    reports nothing is satisfied by a table that inspected nothing. The two variant flips are what carry
    the proof, so the caller asserts the control AND both flips as one unit of evidence.
  #>
  [CmdletBinding()]
  param([ValidateSet('absent-rejected', 'accept-any-value')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $findings = @()

  function Local-BudgetFm([string]$budgetLine) {
    $lines = @('id: TZ-EXAMPLE', 'status: todo')
    if ($budgetLine) { $lines += $budgetLine }   # $null and '' both mean "declare no key at all"
    $lines += @('allow_paths:', '  - scripts/x.ps1')
    return ($lines -join "`n")
  }

  $cases = @(
    @{ what = 'no budget key at all is legal and silent - the field is deliberately not required'; line = $null; want = $false }
    @{ what = 'a declared positive integer is the healthy shape'; line = 'budget: 400'; want = $false }
    @{ what = 'a trailing comment does not make a valid value look malformed'; line = 'budget: 250   # this card own budget'; want = $false }
    @{ what = 'an EMPTY value reads as absent, matching the reader - otherwise check-cards and the meter would disagree about whether the card is off'; line = 'budget:'; want = $false }
    @{ what = 'a commented-out key reads as absent, so the template can ship the key commented'; line = '# budget: 400'; want = $false }
    @{ what = 'an INDENTED key is not the declaration - the reader anchors to line start, and a rule that read it here would report on text no gate acts on'; line = '  budget: nonsense'; want = $false }
    @{ what = 'a non-numeric value is rejected - the reader takes it as absent, so the card looks governed and is not'; line = 'budget: soon'; want = $true }
    @{ what = 'zero is rejected - every non-empty diff would be instantly over'; line = 'budget: 0'; want = $true }
    @{ what = 'a negative value is rejected for the same reason zero is'; line = 'budget: -50'; want = $true }
  )
  foreach ($c in $cases) {
    try {
      $callArgs = @{ FrontMatter = (Local-BudgetFm $c.line) }
      if ($useVariant) { $callArgs['Variant'] = $Variant }
      $got = @(Get-ScaffoldCardBudgetFinding @callArgs)
      $reported = $got.Count -gt 0
      if ($reported -ne $c.want) {
        $verb = if ($c.want) { 'reported nothing' } else { "reported '$($got -join ' | ')'" }
        $findings += "[CARD-BUDGET-EXAMPLE] case '$($c.what)' $verb. [FIX] fix the rule, never the example."
      }
      if ($reported -and ($got -join '') -notmatch '\[CARD-BUDGET\]') {
        $findings += "[CARD-BUDGET-EXAMPLE] case '$($c.what)' reported a finding without the [CARD-BUDGET] sentinel - the sentinel is how a reader greps this class out of check-cards' output."
      }
    }
    catch {
      $findings += "[CARD-BUDGET-EXAMPLE] case '$($c.what)' THREW: $($_.Exception.Message). A card rule that throws instead of returning findings takes the whole of check-cards down with it (L167)."
    }
  }
  return $findings
}

# -- T109-DOD-EXIT-GUARD (TD153/L245): the nested -Command payload traversal, shared by both dod rules --
# task.ps1 runs `& pwsh -NoProfile -Command <dod>`, so a dod shaped `pwsh -Command "<payload>"` hands
# <payload> to a GRANDCHILD shell. Two different defects live in that payload and both need the same
# question answered first - which argument elements actually become the grandchild's script:
#   * TD69/L95: the payload carries a `$` the middle shell interpolates to an empty string.
#   * TD153/L245: the payload spawns pwsh itself and never says `exit`, so the block returns that
#     subprocess's exit code instead of the verdict of its own assertions.
# This function answers that one question. It was extracted from check-cards.ps1's TD69 scan rather than
# written afresh, because a second traversal would be a second answer: the two rules would disagree about
# what a payload IS, and a shape only one of them recognised would be judged by only one of them. Gate
# 10f's 28 seeded cases are what proves the extraction preserved the TD69 verdicts - every -Command
# spelling, every -File boundary and every safe shape below is one of those cases.
# Returns the payload ARGUMENT NODES (not their text): TD69 searches their subtrees for interpolating
# descendants, TD153 needs the string VALUE of the ones that are strings. Never throws.
# Spelling rules, all measured against real pwsh argv (codex R3 nine rounds + Fable 5 review, TD69):
#   * host   = pwsh / powershell, optionally .exe, optionally path-qualified. A variable host (& $pw) is
#              statically unresolvable and is deliberately not scanned.
#   * -File  = STOP scanning this call. What follows is a script path plus arguments the middle shell is
#              MEANT to evaluate, so an -Command appearing after it is an argument, not a payload.
#   * -CommandWithArgs (prefix commandw..., or the standalone alias cwa) = only the command string is the
#              payload; what follows are $args the grandchild evaluates on purpose.
#   * -Command family (any prefix of 'command', GNU --command, quoted "-Command", attached -Command:x)
#              = consumes EVERY following element, because an unquoted multi-token payload parses as
#              several independent elements and all of them reach the grandchild.
function Get-ScaffoldDodNestedPayloadArg {
  [CmdletBinding()]
  param([Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Ast)
  $payloadArgs = @()
  foreach ($pc in $Ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true)) {
    $cn = $pc.GetCommandName()
    if (-not $cn -or ($cn -notmatch '(?i)(^|[\\/])(pwsh|powershell)(\.exe)?$')) { continue }
    $ce = $pc.CommandElements
    for ($ei = 1; $ei -lt $ce.Count; $ei++) {   # from 1: element 0 is the command name
      $el = $ce[$ei]
      # Parameter name: a CommandParameterAst carries it directly; quoted ("-Command") and GNU (--command)
      # forms parse as bare string constants, so the leading dashes are stripped to recover the name.
      $pn = $null; $attachedArg = $null
      if ($el -is [System.Management.Automation.Language.CommandParameterAst]) { $pn = $el.ParameterName; $attachedArg = $el.Argument }
      elseif ($el -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
        $sv = $el.Value
        if ($sv -and $sv.Length -gt 1 -and $sv[0] -eq '-') { $pn = $sv.TrimStart('-') }
      }
      if (-not $pn) { continue }   # not a flag (command name / positional value / -File's path value)
      if ('file'.StartsWith($pn, [System.StringComparison]::OrdinalIgnoreCase)) { break }
      # -CommandWithArgs first: 'command' is also a prefix of 'commandwithargs' and would swallow it.
      $cmdKind = ''
      if ($pn.StartsWith('commandw', [System.StringComparison]::OrdinalIgnoreCase) -or ($pn -ieq 'cwa')) { $cmdKind = 'first' }
      elseif (('command'.StartsWith($pn, [System.StringComparison]::OrdinalIgnoreCase)) -or ($pn.StartsWith('command', [System.StringComparison]::OrdinalIgnoreCase))) { $cmdKind = 'all' }
      if (-not $cmdKind) { continue }   # some other flag (-NoProfile/-ExecutionPolicy...) - keep looking
      if ($attachedArg) { $payloadArgs += $attachedArg }
      if ($cmdKind -eq 'all') { for ($pj = $ei + 1; $pj -lt $ce.Count; $pj++) { $payloadArgs += $ce[$pj] } }
      elseif (-not $attachedArg) { if ($ei + 1 -lt $ce.Count) { $payloadArgs += $ce[$ei + 1] } }
      break   # the payload is collected; further elements are payload, not another flag
    }
  }
  return $payloadArgs
}

# -- T109-DOD-EXIT-GUARD (TD153/L245): does this dod_command inherit a subprocess's exit code? --
# A `pwsh -Command` block that ends without an explicit `exit` returns the exit code of the last NATIVE
# command it ran. A payload that spawns pwsh to OBSERVE a refusal - the ordinary way to assert that a
# guard fires - therefore ends holding that refusal's non-zero, and the block reports it as its own.
# Every assertion passes and the command still exits 1: GREEN is unreachable, and `-Phase red` banks the
# non-zero as valid RED evidence forever. Measured on T108 at its first GREEN attempt (L245). Same
# consequence as L95, different cause: no `$` anywhere, nothing unparseable.
# The judgement is two-level on purpose. Level one asks which arguments become the grandchild script
# (the shared traversal above); level two parses THAT text and asks whether it spawns a host of its own.
# Scanning the dod's string literals directly instead would flag `-Pattern 'pwsh'`, a legitimate arm that
# merely names the word - that shape is one of the declared examples below.
# A payload that spawns pwsh is required to END on an explicit exit. An `exit` inside a branch does not
# count: the branch is exactly what does not run when every assertion passes, which is the case the whole
# rule is about. The requirement is conservative by design - a payload whose subprocesses all exit 0 is
# safe in fact but not statically, and the repair costs one token (`; exit 0`).
# Only pwsh/powershell count as the spawned host. L245 states the rule as "pwsh (or any native command
# expected to exit non-zero)", but "expected to exit non-zero" is not statically decidable and a rule
# demanding a trailing exit from every payload touching any native would flag the live corpus.
# $true = hazardous. Rejected shapes, declared by name rather than left in a comment (same discipline as
# Test-ScaffoldCardSweepVia's variants):
#   ignore-trailing-exit - demand the exit unconditionally. Over-broad: it rejects the repaired form,
#                          which is the form the failure text tells the author to write.
#   exit-anywhere        - accept an `exit` sitting anywhere in the payload. Under-broad: it accepts
#                          `if (...) { exit 1 }; & pwsh -File a.ps1`, whose exit code still comes from
#                          the subprocess.
function Test-ScaffoldDodExitVia($DodText, $Variant) {
  if ([string]::IsNullOrWhiteSpace($DodText)) { return $false }
  $tok = $null; $err = $null
  $ast = [System.Management.Automation.Language.Parser]::ParseInput($DodText, [ref]$tok, [ref]$err)
  # A dod that does not parse is already rejected by check-cards' parse-error guard (TD69/Fable R3).
  # Reporting it twice would name two causes for one defect and send the author looking for both.
  if ($err -and $err.Count -gt 0) { return $false }
  foreach ($arg in @(Get-ScaffoldDodNestedPayloadArg -Ast $ast)) {
    $payload = $null
    if (($arg -is [System.Management.Automation.Language.StringConstantExpressionAst]) -or
        ($arg -is [System.Management.Automation.Language.ExpandableStringExpressionAst])) { $payload = $arg.Value }
    if ([string]::IsNullOrWhiteSpace($payload)) { continue }   # scriptblock / variable / bare token payloads carry no analysable text
    $ptok = $null; $perr = $null
    $past = [System.Management.Automation.Language.Parser]::ParseInput($payload, [ref]$ptok, [ref]$perr)
    if ($perr -and $perr.Count -gt 0) { continue }
    $spawns = $false
    foreach ($inner in $past.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true)) {
      $icn = $inner.GetCommandName()
      if ($icn -and ($icn -match '(?i)(^|[\\/])(pwsh|powershell)(\.exe)?$')) { $spawns = $true; break }
    }
    if (-not $spawns) { continue }
    if ($Variant -eq 'ignore-trailing-exit') { return $true }
    if ($Variant -eq 'exit-anywhere') {
      if (@($past.FindAll({ param($n) $n -is [System.Management.Automation.Language.ExitStatementAst] }, $true)).Count -gt 0) { continue }
      return $true
    }
    $stmts = @()
    if ($past.EndBlock) { $stmts = @($past.EndBlock.Statements) }
    if (($stmts.Count -gt 0) -and ($stmts[-1] -is [System.Management.Automation.Language.ExitStatementAst])) { continue }
    return $true
  }
  return $false
}

# The live decision. Takes the dod_command TEXT rather than a card path, so the rule is testable without
# writing a card file to disk (same contract as Test-ScaffoldCardSweep). Returns findings as strings and
# never throws; check-cards.ps1 prints them like its other card errors.
function Test-ScaffoldDodExit {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $findings = @()
  if (Test-ScaffoldDodExitVia $Text $null) {
    $findings += "[CARD-DOD-EXIT] dod_command nests a pwsh -Command payload that spawns pwsh/powershell itself and does not end with an explicit exit - a pwsh -Command block with no trailing exit returns the exit code of the last NATIVE command it ran, so the subprocess's non-zero becomes the block's, every assertion can pass and the command still exits non-zero. GREEN is unreachable and -Phase red banks that non-zero as valid RED evidence (vacuous RED, same consequence as TD69/L95 from a different cause; TD153/L245). [FIX] end the payload with an explicit 'exit 0', so the exit code states the verdict of the assertions instead of inheriting one. An 'exit' inside an 'if' does not count - that branch is exactly what does not run when every assertion passes."
  }
  return $findings
}

# Declared examples for the exit-inheritance decision, one per case CLASS. Returns findings as strings
# and never throws (same contract as Test-ScaffoldCardSweepExamples). Default = exercise the live
# predicate and expect zero findings. -Variant re-runs the same examples through a rejected shape, which
# MUST produce at least one finding; that negative control is what keeps the default run honest, and per
# L239 it is the only arm that can fail once the function exists. Hermetic: every case is text, nothing
# is written and no path is resolved.
function Test-ScaffoldDodExitExamples {
  [CmdletBinding()]
  param([ValidateSet('ignore-trailing-exit', 'exit-anywhere')][string]$Variant)
  $q = [char]34
  $nested = 'if (-not ((& pwsh -NoProfile -File a.ps1 2>&1 | Out-String) -match ' + "'x'" + ')) { exit 1 }'
  $cases = @(
    @{ what = 'canonical single-level card, no nested host in the payload'; dod = 'pwsh -NoProfile -Command ' + $q + 'if (-not (Test-Path README.md)) { exit 1 }' + $q; expect = $false }
    @{ what = 'nested pwsh, no trailing exit'; dod = 'pwsh -NoProfile -Command ' + $q + $nested + $q; expect = $true }
    @{ what = 'nested pwsh, trailing exit 0'; dod = 'pwsh -NoProfile -Command ' + $q + $nested + '; exit 0' + $q; expect = $false }
    @{ what = 'exit only inside a branch, payload ends on the nested call'; dod = 'pwsh -NoProfile -Command ' + $q + 'if (-not (Test-Path README.md)) { exit 1 }; & pwsh -NoProfile -File a.ps1' + $q; expect = $true }
    @{ what = 'pwsh named only inside a string literal in the payload'; dod = 'pwsh -NoProfile -Command ' + $q + 'if (-not (Select-String -Path README.md -Pattern ' + "'pwsh'" + ' -Quiet)) { exit 1 }' + $q; expect = $false }
    @{ what = 'nested powershell host, no trailing exit'; dod = 'pwsh -NoProfile -Command ' + $q + '& powershell -NoProfile -File a.ps1' + $q; expect = $true }
    @{ what = 'single-level -File call, no nested -Command payload'; dod = 'pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }'; expect = $false }
    @{ what = 'dod that does not parse (owned by the parse-error guard)'; dod = 'pwsh -NoProfile -Command ' + $q + 'if (-not (Test-Path'; expect = $false }
    @{ what = 'empty dod (owned by the missing/empty guard)'; dod = ''; expect = $false }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = Test-ScaffoldDodExitVia $c.dod $v
    if ($got -ne $c.expect) { $findings += "[CARD-DOD-EXIT-EXAMPLE] case '$($c.what)' judged hazardous=$got, expected $($c.expect) - a payload that spawns pwsh and does not END on an explicit exit returns that subprocess's exit code, so GREEN is unreachable (TD153/L245). [FIX] fix the predicate, never the example." }
  }
  return $findings
}

# -- T270-DOD-REGIME-PARITY (TD250 fix (b)): the ONE text both phases of task.ps1 execute --
# `dod_command` is executed at exactly two sites - `task.ps1 -Phase red` and `-Phase ship` - and each used
# to spell the error regime itself. They disagreed, and the phase whose only job is to prove FAILURE was
# the lax one, which is the root of the whole vacuous-GREEN class (TD250, and the five cards T243/T244/
# T262/T267/T268 spent standing in for it). One builder, one string, both callers.
#
# THE PREMISE THAT KEPT THEM APART WAS FALSE, and it is worth writing down because it is persuasive:
# `specs/README.md` said `-Phase red` runs the field RAW - it HAS to, because it must read a non-zero exit
# rather than throw on it. That conflates two different variables.
#   * What the DOD TEXT runs under is decided by what is prepended INSIDE the child payload - this string.
#   * Whether task.ps1 THROWS when the child exits non-zero is decided by the PARENT's own
#     $PSNativeCommandUseErrorActionPreference, which `-Phase red` already sets to $false before the call.
# Both hold at once, so red can wrap the payload AND still read the code. Measured, 5 dod shapes x 4
# regimes, parent preference $false throughout so every cell is an exit code the parent READ.
#
# BOTH ASSIGNMENTS ARE ONE CLAUSE, NOT TWO - the measurement's non-obvious half. `$PSNativeCommandUse-
# ErrorActionPreference=$true` is INERT on its own: with $ErrorActionPreference left at `Continue` a
# failing native command still does not end the unit, so `nativepref-only` reproduces the raw verdict on
# every shape measured. Reasoning about the two assignments separately gives the wrong answer, which is
# why `nativepref-only` is a declared rejected shape and not a comment (L264).
# Rejected shapes, declared by name rather than left in a comment:
#   raw              - return the dod unwrapped, which is what `-Phase red` shipped. Under it a missing
#                      command, a missing FILE (the TD236 shape) and a failing native command all leave
#                      the unit at 0 - three vacuous GREENs, measured.
#   errorpref-only   - prepend only `$ErrorActionPreference='Stop'`. Catches the missing command and the
#                      missing file, and leaves a failing NATIVE command silent, so a dod ending in a
#                      trailing `exit 0` after a failed `git` still banks green.
#   nativepref-only  - prepend only the native preference. Measured INERT: identical to `raw` on every
#                      shape, because the native error is still raised under a `Continue` policy.
# Never throws. Takes the dod TEXT and returns TEXT, so both phases and the gate test the same function.
function Get-ScaffoldDodPayload {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Dod,
        [ValidateSet('raw', 'errorpref-only', 'nativepref-only')][string]$Variant)
  # Written as two independent prefixes rather than one literal so that each half is a line whose removal
  # is a behaviour change the declared cases catch, instead of one string whose edit is invisible (L167,
  # the same reason Get-ScaffoldDodExistenceArmNameArg narrows in separate deletable lines).
  $prefix = ''
  if ($Variant -ne 'nativepref-only') { $prefix += "`$ErrorActionPreference='Stop'; " }
  if ($Variant -ne 'errorpref-only') { $prefix += "`$PSNativeCommandUseErrorActionPreference=`$true; " }
  if ($Variant -eq 'raw') { $prefix = '' }
  return ($prefix + $Dod)
}

# Declared examples for the payload builder, one per case CLASS. Returns findings as strings and never
# throws (same contract as Test-ScaffoldDodExitExamples). Default = exercise the live builder and expect
# the declared text; -Variant re-runs the SAME cases through a rejected shape, which MUST produce at least
# one finding. Hermetic: every case is text, nothing is written, no path is resolved and no process is
# started - the BEHAVIOURAL half (what each regime does to a real dod) is gate 10s's job, because that
# needs child processes and these examples must stay runnable inside a review sandbox (L60).
function Test-ScaffoldDodPayloadExamples {
  [CmdletBinding()]
  param([ValidateSet('raw', 'errorpref-only', 'nativepref-only')][string]$Variant)
  $dod = 'if (-not (Get-ScaffoldFoo).Bar) { exit 1 }; exit 0'
  # Both payloads are built ONCE, under the same variant, and handed to every case. No case re-enters the
  # builder: a case that called it again would have to reconstruct the variant binding, and a $null passed
  # to a [ValidateSet] parameter is a parameter-binding error rather than "no variant" - the exact shape
  # that turns a case into a crash instead of a verdict (L167: non-zero for the wrong reason is not
  # evidence).
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $payload = if ($useVariant) { Get-ScaffoldDodPayload -Dod $dod -Variant $Variant } else { Get-ScaffoldDodPayload -Dod $dod }
  $empty = if ($useVariant) { Get-ScaffoldDodPayload -Dod '' -Variant $Variant } else { Get-ScaffoldDodPayload -Dod '' }
  # One parameter carrying both payloads, rather than two positional ones: a case that reads only the
  # first would leave the second declared-but-unused, which PSScriptAnalyzer reports as
  # PSReviewUnusedParameter and gate 7 fails on under -StrictLint.
  $built = @{ payload = $payload; empty = $empty; dod = $dod }
  $cases = @(
    @{ what = 'the dod survives verbatim as the tail of the payload'; test = { param($b) $b.payload.EndsWith($b.dod) }; expect = $true }
    @{ what = 'the payload sets ErrorActionPreference to Stop'; test = { param($b) $b.payload -match "ErrorActionPreference='Stop'" }; expect = $true }
    @{ what = 'the payload turns the native-command preference on'; test = { param($b) $b.payload -match 'PSNativeCommandUseErrorActionPreference=\$true' }; expect = $true }
    @{ what = 'the payload is a strict PREFIX plus the dod - nothing is inserted into or after it'; test = { param($b) $b.payload.Substring($b.payload.Length - $b.dod.Length) -ceq $b.dod }; expect = $true }
    @{ what = 'an empty dod still yields the full prefix, so an empty field cannot silently mean raw'; test = { param($b) $b.empty.Length -gt 0 }; expect = $true }
  )
  $findings = @()
  foreach ($c in $cases) {
    $got = [bool](& $c.test $built)
    if ($got -ne $c.expect) { $findings += "[CARD-DOD-PAYLOAD-EXAMPLE] case '$($c.what)' judged $got, expected $($c.expect) - the two phases of task.ps1 execute whatever this function returns, so a payload that drops a preference silently restores the error-regime asymmetry TD250 exists for. [FIX] fix the builder, never the example." }
  }
  return $findings
}

# -- T243-DOD-FN-EXISTS-ARM (TD250/L308): does this dod_command call a repo function it never asserts
#    exists? --
# The three dod guards above all catch a vacuous RED - a dod that exits NON-ZERO without running. This one
# catches the mirror, a vacuous GREEN - a dod that exits ZERO without running - and that is the worse of the
# two: a false RED is contradicted at the first GREEN attempt, a false GREEN is banked as a passing DoD gate
# and contradicts nothing.
# The cause WAS an error-regime asymmetry between the two phases that execute this field, and T270 removed
# it: both now run the dod through Get-ScaffoldDodPayload, so there is one regime and one string. Until
# then `-Phase ship` wrapped the dod as `$ErrorActionPreference='Stop';
# $PSNativeCommandUseErrorActionPreference=$true; <dod>` while `-Phase red` ran it unwrapped, and under
# the child's default `Continue` a CommandNotFoundException is terminating for the STATEMENT only: the
# enclosing `if (-not (Get-Foo).Bar) { exit 1 }` is abandoned rather than evaluated, control falls through
# to the trailing `exit 0`, and every arm silently no-ops. So the phase whose only job was to prove
# FAILURE was the lax one. Measured on T242 before a line of production code existed: a four-arm dod with
# Get-ScaffoldReviewRunDecision absent exited 0, and `-Phase red` reported the card was already GREEN and
# refused to record evidence (L308).
# WHAT THIS RULE IS FOR NOW, stated plainly rather than left as an inherited habit: with the regimes
# aligned, a missing repo function ends the unit non-zero on its own, so this guard is no longer the only
# thing standing between a card and a vacuous GREEN. It keeps a narrower job - refusing at CARD-VALIDATION
# time, with a printed repair, instead of at run time inside whichever phase ran first - and that is worth
# stating because a rule whose original reason has been repaired elsewhere is exactly the rule that later
# gets defended on grounds nobody checked. Whether it still earns eleven declared rejected shapes is a
# subtraction question and has its own debt row; it is deliberately NOT answered here.
# The repair costs one arm and it is what T242 had to add mid-implementation before its RED was expressible:
#   if (-not (Get-Command <name> -ErrorAction SilentlyContinue)) { exit 1 }
# WHAT COUNTS AS A REPO FUNCTION: the `<Verb>-Scaffold<Noun>` convention, which is how all but twelve of the
# functions in scripts/_*.ps1 are named. Those twelve are each internal to one script and none appears in any
# live or archived dod; widening the pattern to every hyphenated name would flag Get-Content.
# The majority is stated WITHOUT a numerator on purpose: any card that adds a helper moves it, this card's own
# diff moved it by three, and a count nothing checks is a claim that rots (T243 R3 #16). Twelve is the stable
# half - it moves only when someone lands a helper that breaks the naming convention.
# JUDGED BY AST, NEVER BY TEXT. A name is a call only where it is the COMMAND of a CommandAst. T234's live
# dod names Get-ScaffoldHandoffStatusRules twice inside string literals handed to .Replace(...) and must
# stay green - that is the `text-match` rejected shape below, and it is a live card rather than a
# hypothetical.
# PER ANALYSED UNIT, NOT PER DOD. The dod text and each nested -Command payload are separate PROCESSES at
# run time, so a Get-Command in one does not vouch for a call in another. The payload traversal is the
# SHARED Get-ScaffoldDodNestedPayloadArg the other two dod rules use, so the three cannot disagree about
# what a payload is.
# POSITION IS DELIBERATELY NOT REQUIRED. A late existence arm still leaves the unit non-zero when the
# function is absent - since T270 because the call itself ends the unit under the aligned regime, and
# before it because the abandoned statement fell through TO the late arm. The rule is the same either way,
# which is the point: demanding the arm come first would reject the dot-source-then-assert form every such
# dod already uses, and it would have been the wrong requirement under both regimes.
# Returns the missing function names, sorted, so the failure text can name them. Never throws.
# Rejected shapes, declared by name rather than left in a comment (same discipline as Test-ScaffoldDodExit):
#   text-match     - harvest calls by regex over the unit's text instead of from CommandAst command names.
#                    Over-broad: it reads a name mentioned in a -Pattern or a .Replace() literal as a call,
#                    which is exactly T234's live dod.
#   ignore-payload - analyse only the outer AST and never re-parse nested -Command payloads. Under-broad:
#                    the canonical dod puts every assertion inside such a payload, so the rule would never
#                    fire on the shape it exists for. This is the L95 residual - "check-cards parses only
#                    the OUTER invocation" - stated as a control rather than left as a known gap.
#   assert-anywhere - read every string element of any Get-Command as a checked name, without asking what
#                    shape the call has (T243's harvest, until T244). Under-broad: a bare mention and a
#                    `-Module <name>` binding both silence the rule, which were MEASURED bypasses.
#   payload-into-parent - let the outer AST walk see through a scriptblock payload instead of sealing it
#                    off. Under-broad: an outer Get-Command then vouches for a call that runs in the
#                    CHILD process, which contradicts the per-unit rule stated above.

# -- T244-DOD-FN-EXISTS-SHAPE (TD252): which `Get-Command` calls actually ASSERT that a name exists? --
# T243's harvest asked only whether a Get-Command appeared in the unit, and then read EVERY string element
# of it as a checked name. Both halves are too loose, and both were MEASURED as live bypasses on 103c026:
#   * `Get-Command Get-ScaffoldFoo | Out-Null; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }` silenced the
#     rule - although under `-Phase red`'s raw regime that line writes a NON-terminating error and
#     execution simply continues, which is the opposite of an assertion.
#   * `Get-Command -Module Get-ScaffoldFoo` silenced it - although the name is never looked up at all.
# So the call must be the CONDITION of an arm that EXITS when the command is absent, and the name must be
# bound POSITIONALLY or to `-Name`. `throw` counts beside `exit`: both leave the dod non-zero, which is the
# property the rule is actually about.
#
# -- T262-DOD-FN-EXISTS-REACH (TD257) + T267-DOD-EXIT-ARM-SOUND (TD268): that sentence, actually enforced --
# An arm asserts nothing unless the MISSING command reaches its terminator and that terminator leaves the
# unit non-zero. So three conditions beyond the shape: the lookup must BE the condition (only a `-not` and
# single-element parens and pipelines may stand between them); the terminator must be a statement of the
# clause body ITSELF that no earlier statement diverts around; and an `exit` must carry a constant status
# in 1..255, which is the set that is non-zero on every platform this repo runs on (POSIX masks a wait
# status to 8 bits, so `exit 256` leaves the unit at 0 there while PS7 on Windows really does exit 256 -
# both measured). Every rejected shape below was a MEASURED bypass, not an imagined one; the dated numbers
# live in the T262 and T267 cards, because a count written into a comment goes stale on the next card that
# writes an arm and a stale count reads as a fact.
# Returns the name ARGUMENT nodes rather than their values, so the caller keeps the single string-element
# test the whole rule turns on, and returns nothing at all when the shape is wrong.
# CONSERVATIVE BY DESIGN, and over-broad is the one direction this may err in: `if (Get-Command <n>) { }
# else { exit 1 }`, `Get-Command <n> -ErrorAction Stop` and `if ((-not (Get-Command <n>)) -or <x>) { exit 1 }`
# are genuine assertions this does NOT accept. None appears in any live or archived dod, and the failure
# text prints the one shape that works.
# Rejected shapes, declared by name rather than left in a comment:
#   lookup-anywhere      - let the walk cross ANY node on its way to the `if`. Under-broad: a compound
#                          condition then counts, and `-and (1 -eq 2)` makes the arm unreachable.
#   pipeline-any-length  - accept the pipeline carrying the lookup however many elements it holds.
#                          Under-broad: `| Measure-Object` always emits an object, so `-not` is always
#                          false and the arm is never taken. The count is the test, never the identity of
#                          the downstream command - a denylist of spellings can always be extended by one.
#   terminator-anywhere  - find the terminator anywhere below the clause (T244's form). Under-broad: an exit
#                          in a dead nested branch and a throw its own `catch` swallows both count.
#   ignore-divert        - read the clause body's statements as a SET rather than in order. Under-broad:
#                          `{ return; exit 1 }` counts, and `return` at script scope ends the unit at 0.
#   exit-any-status      - accept an `exit` whatever status it carries. Under-broad: `exit 0` on the
#                          missing-command path IS the vacuous GREEN this rule exists to stop.
#   exit-status-unmasked - accept any non-zero constant without asking whether it survives POSIX's 8-bit
#                          mask. Under-broad on the ubuntu runner only, which is what makes it worth a
#                          name: `exit 256` is 256 here and 0 there, and CI is where it would be believed.
#   exit-skip-rejected   - walk PAST an `exit` the status tests reject and keep looking for an acceptable
#                          one. Under-broad: the first `exit` reached is the one that runs, so
#                          `{ exit 0; exit 1 }` is judged on a statement that never executes. This is the
#                          divert stop read half-way (T267's residual, TD270, four shapes measured).
# Never throws.
function Get-ScaffoldDodExistenceArmNameArg {
  [CmdletBinding()]
  param([Parameter(Mandatory)][System.Management.Automation.Language.CommandAst]$Cmd, [string]$Variant)
  # 1. SHAPE. Walk out of the call looking for the `if` whose CONDITION it IS. Meeting a block boundary on
  #    the way up means the call sits in a BODY rather than in a condition, and a body asserts nothing;
  #    meeting anything but a paren, a `-not` or the pipeline that carries them means the call is only PART
  #    of the condition, and a part is not the test (TD257). A pipeline that carries the lookup AND anything
  #    else is also only a part: whatever stands downstream decides the truth value, so `| Measure-Object`
  #    makes the condition permanently false and the arm unreachable (TD268, measured at run time).
  $negated = $false
  $child = $Cmd
  $node = $Cmd.Parent
  $ifAst = $null
  while ($node) {
    if (($node -is [System.Management.Automation.Language.StatementBlockAst]) -or
        ($node -is [System.Management.Automation.Language.ScriptBlockAst])) { break }
    if ($node -is [System.Management.Automation.Language.IfStatementAst]) { $ifAst = $node; break }
    if (($node -is [System.Management.Automation.Language.UnaryExpressionAst]) -and
        (($node.TokenKind -eq [System.Management.Automation.Language.TokenKind]::Not) -or
         ($node.TokenKind -eq [System.Management.Automation.Language.TokenKind]::Exclaim))) { $negated = -not $negated }
    elseif (($Variant -ne 'lookup-anywhere') -and (-not (($node -is [System.Management.Automation.Language.PipelineAst]) -or ($node -is [System.Management.Automation.Language.ParenExpressionAst]) -or ($node -is [System.Management.Automation.Language.CommandExpressionAst])))) { return @() }
    if (($Variant -ne 'pipeline-any-length') -and ($node -is [System.Management.Automation.Language.PipelineAst]) -and (@($node.PipelineElements).Count -ne 1)) { return @() }
    $child = $node
    $node = $node.Parent
  }
  if ((-not $ifAst) -or (-not $negated)) { return @() }
  $body = $null
  foreach ($clause in $ifAst.Clauses) { if ([object]::ReferenceEquals($clause.Item1, $child)) { $body = $clause.Item2; break } }
  if (-not $body) { return @() }
  # 2. TERMINATOR. It has to be REACHED whenever the command is absent, so it must be a statement of the
  #    clause body ITSELF. One nested in a branch that is never taken, or inside a `try` whose `catch`
  #    swallows it, leaves the unit exiting zero all the same. Membership is not enough either: the list is
  #    ORDERED, and a `return`, `break` or `continue` ahead of the terminator means control never gets
  #    there - `return` at script scope ends the unit at 0 and the `exit 1` behind it never runs (TD268,
  #    measured at run time). Reading the list IN ORDER is therefore the whole of the analysis, and it has
  #    two stopping conditions rather than one. `return`, `break` and `continue` divert AROUND what follows,
  #    so the scan stops there having found nothing. `exit` and `throw` leave the clause body too, but by
  #    ENDING the unit, so the scan stops there having found the statement that decides the arm - and it is
  #    decided on that one alone. Skipping an `exit` whose status the tests below reject, and crediting a
  #    later acceptable one, judges a statement that never runs: `{ exit 0; exit 1 }`, `{ exit; exit 1 }`,
  #    `{ exit $code; exit 1 }` and `{ exit 256; exit 1 }` all passed that way (TD270, measured, the first
  #    two at run time as well - the unit really does end at exit 0).
  #    And an `exit` has to leave the unit non-zero WHEREVER it runs: a bare `exit` is 0 even after a
  #    non-zero $LASTEXITCODE (measured), a status that is not a constant is not PROVEN non-zero (TD257),
  #    and POSIX masks a wait status to 8 bits, so only 1..255 is non-zero on the ubuntu runner too (TD268,
  #    measured on both platforms).
  #    $stmts starts as every terminator ANYWHERE below the clause and is then NARROWED twice - to the
  #    clause's own statements, and to everything up to and including the first of them that ends the unit.
  #    Written this way round on purpose (the same reason Test-ScaffoldCoreSelfCheckVia gives for $reached):
  #    each narrowing is one deletable line whose removal is a behaviour change the examples catch, rather
  #    than an assignment whose removal leaves $stmts undefined and takes the gate down with a StrictMode
  #    error - non-zero for the wrong reason, which is not evidence (L167).
  $stmts = @($body.FindAll({ param($n) ($n -is [System.Management.Automation.Language.ExitStatementAst]) -or ($n -is [System.Management.Automation.Language.ThrowStatementAst]) }, $true))
  if ($Variant -ne 'terminator-anywhere') { $stmts = @($body.Statements) }
  $upToFirst = @()
  foreach ($st in $stmts) { $upToFirst += $st; if (($st -is [System.Management.Automation.Language.ExitStatementAst]) -or ($st -is [System.Management.Automation.Language.ThrowStatementAst])) { break } }
  if ($Variant -ne 'exit-skip-rejected') { $stmts = $upToFirst }
  $terminates = $false
  foreach ($st in $stmts) {
    if (($Variant -ne 'ignore-divert') -and (($st -is [System.Management.Automation.Language.ReturnStatementAst]) -or ($st -is [System.Management.Automation.Language.BreakStatementAst]) -or ($st -is [System.Management.Automation.Language.ContinueStatementAst]))) { break }
    if ($st -is [System.Management.Automation.Language.ThrowStatementAst]) { $terminates = $true; break }
    if (-not ($st -is [System.Management.Automation.Language.ExitStatementAst])) { continue }
    if ($Variant -eq 'exit-any-status') { $terminates = $true; break }
    $status = $null
    if ($st.Pipeline -and (@($st.Pipeline.PipelineElements).Count -eq 1) -and
        ($st.Pipeline.PipelineElements[0] -is [System.Management.Automation.Language.CommandExpressionAst])) { $status = $st.Pipeline.PipelineElements[0].Expression }
    if (-not ($status -is [System.Management.Automation.Language.ConstantExpressionAst])) { continue }
    $code = 0
    if (-not [int]::TryParse([string]$status.Value, [ref]$code)) { continue }
    if ($code -lt 1) { continue }
    if (($Variant -ne 'exit-status-unmasked') -and ($code -gt 255)) { continue }
    $terminates = $true
    break
  }
  if (-not $terminates) { return @() }
  # 3. BINDING. Only the positional value(s) and an explicit -Name target are names being looked up. An
  #    argument-less switch (-All) swallows the next element here and costs a false finding - the strict
  #    direction, repaired by writing the canonical arm, which is the trade this whole rule is built on.
  $nameArgs = @()
  $pending = $null
  for ($i = 1; $i -lt $Cmd.CommandElements.Count; $i++) {
    $el = $Cmd.CommandElements[$i]
    if ($el -is [System.Management.Automation.Language.CommandParameterAst]) {
      $pn = [string]$el.ParameterName
      $isName = ($pn.Length -ge 2) -and ('name'.StartsWith($pn, [System.StringComparison]::OrdinalIgnoreCase))
      if ($el.Argument) { if ($isName) { $nameArgs += $el.Argument }; $pending = $null; continue }
      $pending = if ($isName) { 'name' } else { 'other' }
      continue
    }
    if (($null -eq $pending) -or ($pending -eq 'name')) { $nameArgs += $el }
    $pending = $null
  }
  return $nameArgs
}

# Is $Node sealed inside one of the $Shadow nodes, looking outward only as far as $Root? The scriptblock
# payload of a nested `pwsh -Command` is its OWN unit - it runs in the child process - but the outer AST
# walk sees straight through it, so without this an outer `Get-Command` vouches for a call across a process
# boundary (TD252, measured). $Root is what lets the same test serve the payload unit itself: walking out
# from a call INSIDE the payload reaches that unit's own root before it reaches the shadow node, so the
# payload is still analysed on its own terms rather than disappearing. Never throws.
function Test-ScaffoldDodAstShadowed {
  [CmdletBinding()]
  param([System.Management.Automation.Language.Ast]$Node, [System.Management.Automation.Language.Ast]$Root, $Shadow)
  $n = $Node
  while ($n -and (-not [object]::ReferenceEquals($n, $Root))) {
    foreach ($s in @($Shadow)) { if ([object]::ReferenceEquals($n, $s)) { return $true } }
    $n = $n.Parent
  }
  return $false
}

function Test-ScaffoldDodFnExistsVia($DodText, $Variant) {
  if ([string]::IsNullOrWhiteSpace($DodText)) { return @() }
  $tok = $null; $err = $null
  $ast = [System.Management.Automation.Language.Parser]::ParseInput($DodText, [ref]$tok, [ref]$err)
  # A dod that does not parse is already rejected by check-cards' parse-error guard. Reporting it twice
  # would name two causes for one defect and send the author looking for both.
  if ($err -and $err.Count -gt 0) { return @() }
  $units = @($ast)
  $shadow = @()
  if ($Variant -ne 'ignore-payload') {
    foreach ($arg in @(Get-ScaffoldDodNestedPayloadArg -Ast $ast)) {
      # A SCRIPTBLOCK payload is not unanalysable - it is already an AST - but it runs in the CHILD
      # process, so it becomes its own unit AND is sealed off from the outer one (TD252). Skipping it and
      # letting the outer walk recurse into it anyway was the second measured bypass.
      if ($arg -is [System.Management.Automation.Language.ScriptBlockExpressionAst]) {
        if ($Variant -ne 'payload-into-parent') { $units += $arg.ScriptBlock; $shadow += $arg }
        continue
      }
      $payload = $null
      if (($arg -is [System.Management.Automation.Language.StringConstantExpressionAst]) -or
          ($arg -is [System.Management.Automation.Language.ExpandableStringExpressionAst])) { $payload = $arg.Value }
      if ([string]::IsNullOrWhiteSpace($payload)) { continue }   # variable / bare token payloads carry no analysable text
      $ptok = $null; $perr = $null
      $past = [System.Management.Automation.Language.Parser]::ParseInput($payload, [ref]$ptok, [ref]$perr)
      if ($perr -and $perr.Count -gt 0) { continue }
      $units += $past
    }
  }
  # The <Verb>-Scaffold<Noun> convention, written ONCE: the live channel anchors it (a name is a call only
  # where it IS the whole command name) and the text-match rejected shape below deliberately does not, so
  # the two must differ in exactly that and in nothing else.
  $fnCore = '[A-Za-z][A-Za-z0-9]*-Scaffold[A-Za-z0-9]*'
  $fnPattern = '(?i)^' + $fnCore + '$'
  $missing = [ordered]@{}
  foreach ($unit in $units) {
    $called = @{}
    $checked = @{}
    foreach ($cmd in $unit.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true)) {
      $cn = $cmd.GetCommandName()
      if (-not $cn) { continue }
      if (Test-ScaffoldDodAstShadowed -Node $cmd -Root $unit -Shadow $shadow) { continue }
      # Get-Command IS the existence assertion, but only where it is really ASSERTING: the call must be
      # the condition of an arm that exits when the command is absent, and the name must be bound
      # positionally or to -Name. Reading every string element of every Get-Command instead - T243's
      # first form - let a bare mention and a `-Module <name>` binding silence the rule (TD252, measured).
      if ($cn -match '(?i)^(Get-Command|gcm)$') {
        $nameArgs = if ($Variant -eq 'assert-anywhere') { $cmd.CommandElements } else { @(Get-ScaffoldDodExistenceArmNameArg -Cmd $cmd -Variant $Variant) }
        foreach ($el in $nameArgs) {
          if ($el -is [System.Management.Automation.Language.StringConstantExpressionAst]) { $checked[$el.Value] = $true }
        }
        continue
      }
      if ($cn -match $fnPattern) { $called[$cn] = $true }
    }
    if ($Variant -eq 'text-match') {
      $called = @{}
      foreach ($m in [regex]::Matches($unit.Extent.Text, '(?i)' + $fnCore)) { $called[$m.Value] = $true }
    }
    foreach ($name in ($called.Keys | Sort-Object)) {
      if ($checked.ContainsKey($name)) { continue }
      $missing[$name] = $true
    }
  }
  return @($missing.Keys)
}

# The live decision. Takes the dod_command TEXT rather than a card path, so the rule is testable without
# writing a card file to disk (same contract as Test-ScaffoldDodExit). Returns findings as strings and
# never throws; check-cards.ps1 prints them like its other card errors.
function Test-ScaffoldDodFnExists {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $findings = @()
  $names = @(Test-ScaffoldDodFnExistsVia $Text $null)
  if ($names.Count) {
    $findings += "[CARD-DOD-FN-EXISTS] dod_command calls the repo function(s) " + ($names -join ', ') + " without asserting they exist. An arm that names the function it depends on says out loud what the dod needs, and is refused HERE - at card validation, with the repair printed - rather than at run time inside whichever phase ran first. Until T270 the stakes were higher: -Phase red executed the dod unwrapped while -Phase ship wrapped it, and under the child's default Continue a CommandNotFoundException is terminating for the STATEMENT only, so the enclosing if (-not (Get-Foo).Bar) { exit 1 } was abandoned, control fell through to the trailing exit 0, and every arm silently no-opped - a vacuous GREEN, the mirror of TD69/L95's vacuous RED and worse in kind, because -Phase red then reported the card was already GREEN and refused to record evidence (measured on T242; TD250/L308). Both phases now build their text with Get-ScaffoldDodPayload, so that path is closed at the source and this rule is the earlier, louder of two defences rather than the only one.[FIX] add one arm per function, anywhere in the dod: if (-not (Get-Command <name> -ErrorAction SilentlyContinue)) { exit 1 }. Position is not required - a late arm still exits non-zero, because the abandoned statement falls through to it."
  }
  return $findings
}

# Declared examples for the existence-arm decision, one per case CLASS. Returns findings as strings and
# never throws (same contract as Test-ScaffoldDodExitExamples). Default = exercise the live predicate and
# expect the declared verdict. -Variant re-runs the SAME examples through a rejected shape, which MUST
# produce at least one finding; that negative control is what keeps the default run honest, and per L239
# it is the only arm that can fail once the function exists. Hermetic: every case is text, nothing is
# written and no path is resolved.
function Test-ScaffoldDodFnExistsExamples {
  [CmdletBinding()]
  param([ValidateSet('text-match', 'ignore-payload', 'assert-anywhere', 'payload-into-parent', 'lookup-anywhere', 'terminator-anywhere', 'exit-any-status', 'pipeline-any-length', 'ignore-divert', 'exit-status-unmasked', 'exit-skip-rejected')][string]$Variant)
  $q = [char]34
  $bare = '. scripts/_cards.ps1; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'
  $armed = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'
  $cases = @(
    @{ what = 'direct call, no existence arm'; dod = $bare; expect = 1 }
    @{ what = 'direct call with the existence arm (the printed repair)'; dod = $armed; expect = 0 }
    @{ what = 'nested payload, call with no existence arm'; dod = 'pwsh -NoProfile -Command ' + $q + $bare + $q; expect = 1 }
    @{ what = 'nested payload, call with the existence arm'; dod = 'pwsh -NoProfile -Command ' + $q + $armed + $q; expect = 0 }
    @{ what = 'name appears only as a bareword argument, not as a command'; dod = 'Select-String -Path a.md -Pattern Get-ScaffoldFoo -Quiet'; expect = 0 }
    @{ what = "name appears only inside a string literal (T234's live shape)"; dod = '(Get-Content a.ps1 -Raw).Replace(' + $q + 'function Get-ScaffoldFoo {' + $q + ', ' + $q + 'function Get-ScaffoldFoo { return @();' + $q + ') | Set-Content a.ps1'; expect = 0 }
    @{ what = 'two functions called, only one asserted'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }; if (-not (Get-ScaffoldBar).Baz) { exit 1 }'; expect = 1 }
    @{ what = 'existence arm sits AFTER the call (still expresses a RED, so not a finding)'; dod = '. scripts/_cards.ps1; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }'; expect = 0 }
    @{ what = 'no repo function called at all'; dod = 'pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }'; expect = 0 }
    @{ what = 'dod that does not parse (owned by the parse-error guard)'; dod = 'pwsh -NoProfile -Command ' + $q + 'if (-not (Test-Path'; expect = 0 }
    @{ what = 'empty dod (owned by the missing/empty guard)'; dod = ''; expect = 0 }
    # T244/TD252 - ARM SHAPE. A Get-Command that does not EXIT on absence proves nothing. Under the raw
    # regime -Phase red used before T270 a bare lookup wrote a non-terminating error and execution simply
    # continued; under the aligned regime it ends the unit, but on the LOOKUP rather than on the assertion
    # the card meant to make, so the shape is still not an assertion and these cases still hold.
    @{ what = 'bare Get-Command mention, not an exit arm'; dod = '. scripts/_cards.ps1; Get-Command Get-ScaffoldFoo | Out-Null; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'Get-Command sits in the if BODY, not in its condition'; dod = '. scripts/_cards.ps1; if (1 -eq 1) { Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'arm is shaped right but its body never exits'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { Write-Host missing }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'arm whose body throws instead of exiting (still leaves the dod non-zero)'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { throw 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 0 }
    # T244/TD252 - NAME BINDING. A correctly shaped arm that binds the name to some other parameter is
    # not looking that name up at all; -Name and the positional form are the same assertion.
    @{ what = 'arm binds the name to -Module, not to -Name'; dod = '. scripts/_cards.ps1; if (-not (Get-Command -Module Get-ScaffoldFoo)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'arm binds the name to -Name explicitly'; dod = '. scripts/_cards.ps1; if (-not (Get-Command -Name Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 0 }
    # T244/TD252 - PROCESS BOUNDARY. A scriptblock payload is its own unit; an outer arm cannot vouch for
    # a call that runs in the child process, and the payload's own arm still clears it.
    @{ what = 'scriptblock payload, outer arm cannot vouch across the process boundary'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; pwsh -NoProfile -Command { if (-not (Get-ScaffoldFoo).Bar) { exit 1 } }'; expect = 1 }
    @{ what = 'scriptblock payload carrying its own existence arm'; dod = 'pwsh -NoProfile -Command { if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 } }'; expect = 0 }
    @{ what = 'the payload asserts and the OUTER unit calls - no vouching backwards either'; dod = '. scripts/_cards.ps1; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }; pwsh -NoProfile -Command { if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 } }'; expect = 1 }
    # ... and the over-broad control that keeps the two above honest: an in-process scriptblock is NOT a
    # payload. Excluding scriptblock bodies wholesale would open a hole exactly as wide as the one closed.
    @{ what = 'in-process scriptblock body is still harvested (ForEach-Object)'; dod = '. scripts/_cards.ps1; 1..2 | ForEach-Object { if (-not (Get-ScaffoldFoo).Bar) { exit 1 } }'; expect = 1 }
    @{ what = 'in-process scriptblock body, outer arm vouches (same process)'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 1 }; 1..2 | ForEach-Object { if (-not (Get-ScaffoldFoo).Bar) { exit 1 } }'; expect = 0 }
    # T262/TD257 - REACHABILITY. An arm that satisfies the shape and still no-ops at run time proves
    # nothing: the lookup has to BE the condition, the terminator has to be reached whenever the command is
    # absent, and an exit has to leave the unit non-zero. Each of the four was MEASURED on 39e3f99 silencing
    # the rule where the bare call above is flagged.
    @{ what = 'compound condition - the lookup is only PART of the arm test'; dod = '. scripts/_cards.ps1; if ((-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) -and (1 -eq 2)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the arm exits ZERO, so an absent command leaves the dod green'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 0 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the terminator sits in a nested branch that is never taken'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { if (1 -eq 2) { exit 1 } }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the throw is swallowed by its own catch'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { try { throw 1 } catch { } }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    # ... and the boundary from the other side, so the narrowing cannot quietly become "only the exact
    # canonical arm": a statement before the exit does not hide it, and 2 is as non-zero as 1.
    @{ what = 'the arm reports before it exits, and the exit is still reached'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { Write-Host missing; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 0 }
    @{ what = 'the arm exits with a non-zero status that is not 1'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 2 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 0 }
    # T267/TD268 - the two shapes T262's own reviewer measured walking through the narrowed rule, plus the
    # platform half of the exit status. Each returned 0 findings on merged 72f1b76 where the bare call
    # above returns 1. The first three were measured at RUN time as well - the unit really does end at
    # exit 0 - while `exit 256` is the one that differs BY PLATFORM, which is the whole reason it is a
    # case: PS7 on Windows really exits 256, and only the ubuntu runner masks it to 0.
    @{ what = 'the lookup is piped, so what stands downstream decides the condition'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue | Measure-Object)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'a return stands ahead of the exit, so the exit is never reached'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { return; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'a break stands ahead of the exit, so the exit is never reached'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { break; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the status is 256, which POSIX masks to 0 on the ubuntu runner'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 256 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    # ... and the two the shipped rule already refused CORRECTLY and no case declared, so nothing would have
    # noticed if a later edit dropped them. Both go red under `exit-any-status`, which is what makes them
    # cases rather than decoration.
    @{ what = 'a bare exit carries status 0, whatever $LASTEXITCODE held'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the exit status is a variable, so it is not PROVEN non-zero'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit $code }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    # ... and the boundary from the other side once more, so `1..255` cannot quietly become `only 1`: the
    # top of the range is as good an assertion as the bottom, and a statement between the two terminators
    # that does NOT divert leaves the exit reachable.
    @{ what = 'the arm exits with 255, the top of the range POSIX preserves'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 255 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 0 }
    # T268/TD270 - the FIRST reached exit decides the arm. T267 stopped the scan at the first DIVERT and
    # left it walking past an `exit` the status tests had just REJECTED, so each of these four was judged
    # on its SECOND exit although the first is the one that runs. All four measured at 0 findings on
    # merged f7f2ef4 and again on bb3ab9d, where the bare call above returns 1; the first two were
    # measured at RUN time too, ending the unit at process exit 0.
    @{ what = 'exit 0 stands ahead of an exit 1, and the exit 0 is the one that runs'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 0; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'a bare exit stands ahead of an exit 1, and carries status 0'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'a variable status stands ahead of an exit 1, and is not PROVEN non-zero'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit $code; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'exit 256 stands ahead of an exit 1, and the ubuntu runner masks it to 0'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { exit 256; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    # ... and the two shapes T267's reviewer called bypasses that re-measurement REFUTED. Both are refused
    # correctly today and were covered by no case, so nothing would have noticed if they stopped being
    # refused. They are cases at the verdict they already have, and each is load-bearing for the clause
    # that earns it: the first goes red under `ignore-divert`, the second under `pipeline-any-length`.
    @{ what = 'a continue stands ahead of the exit, so the exit is never reached'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue)) { continue; exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
    @{ what = 'the lookup is piped into Out-Null, a second downstream command'; dod = '. scripts/_cards.ps1; if (-not (Get-Command Get-ScaffoldFoo -ErrorAction SilentlyContinue | Out-Null)) { exit 1 }; if (-not (Get-ScaffoldFoo).Bar) { exit 1 }'; expect = 1 }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(Test-ScaffoldDodFnExistsVia $c.dod $v).Count
    $hit = if ($got -gt 0) { 1 } else { 0 }
    if ($hit -ne $c.expect) { $findings += "[CARD-DOD-FN-EXISTS-EXAMPLE] case '$($c.what)' judged findings=$got, expected $(if ($c.expect) { 'at least one' } else { 'none' }) - a dod calling a repo function it never asserts exists names no dependency at all, so the failure it produces is a bare CommandNotFoundException at run time instead of a refusal at validation time that prints the repair - and before T270 it did not even fail: it returned 0 with every arm abandoned, which -Phase red reported as already GREEN (TD250/L308). [FIX] fix the predicate, never the example." }
  }
  return $findings
}

# -- T110-MUT-EVIDENCE-PATHS (TD145 item 1) + T200-CARD-MUT-BOTH-PATHS (TD194): does this card promise
#    evidence it has nowhere to put? --
# A card whose `hygiene` mandates a mutation-evidence batch (L165) but whose `allow_paths` cannot hold that
# evidence never gets to commit it: the ship scope gate reads allow_paths from the BASE card, so widening it
# in-branch is inert by design. Measured on T90-ADR-FORMAT - its registry and results TSV ran from the
# session scratchpad and were never committed, contradicting specs/mutations/README.md, whose whole point is
# that evidence stops living in scratchpad. Nothing cross-referenced the two fields.
# BOTH ARTIFACTS OR NEITHER (TD194). `mutate.ps1` writes TWO tracked files per batch - the registry `.psd1`
# and `<name>-results.tsv` beside it - so "somewhere to put it" means somewhere for both. The first form of
# this rule returned satisfied on the first allow_paths entry that merely sat under specs/mutations/, which
# accepted HALF a batch: a card naming only its registry passed here and had its results TSV refused at
# ship, which is the END of the card, after the batch has already run. Measured across the 46 live and
# archived cards naming a registry, 12 named a bare `.psd1`, and 9 of those were safe only because they
# scoped the directory, which covers both; T191 was one of the 3 that were not, and paid for it with a
# mid-card amendment to its own card on master.
# This is a SELF-CONSISTENCY check between two front-matter fields, and deliberately nothing more: it does
# not ask whether the named registry exists, whether the batch ran, or whether it was any good. `hygiene`
# stays advisory prose in every other respect.
# The "promises a batch" half is a PATTERN and therefore lives in Get-ScaffoldCardRules beside the examples
# it must and must not match, because this is exactly the distinction that drifts: 17 live and archived
# cards carry the template's `mutation-survivor 剪枝` line and owe no registry, while a card saying
# `单句删除变异` or `single-line-deletion` owes one. Both shapes contain the word "mutation". Widen the
# regex to that bare word and the table itself goes red on the ShouldNot examples.
# $true = the card promises a batch and declares nowhere to put it. Rejected shapes, declared by name:
#   any-mutation-word  - match the bare word. Over-broad: it flags all 17 template-line cards.
#   ignore-allow-paths - report the finding without consulting allow_paths. Over-broad the other way: it
#                        flags a card that did declare the path, so the rule could never be satisfied.
#   any-mutation-path  - the pre-TD194 form: ANY entry under specs/mutations/ satisfies. Under-broad, and
#                        the defect this rule was actually shipped with - it accepts a card that declared
#                        the registry and nothing for the results TSV, so half the batch is unscoped at
#                        ship. Named here rather than left in prose so a later relaxation of the
#                        conjunction below goes red instead of silently reopening TD194.
# One accepted shape is deliberately NOT handled: a wildcard entry (specs/mutations/*). Zero of those 46
# cards use one, so the clause would be untested code; such an entry is refused and the message names the
# bare-directory repair, which is the shape the corpus actually uses.
function Test-ScaffoldCardMutationPathsVia($CardText, $Variant) {
  $fm = Get-FrontMatter $CardText
  if ($null -eq $fm) { return $false }            # no front matter: check-cards already errors on that
  $hyg = Get-Scalar $fm 'hygiene'
  if ([string]::IsNullOrWhiteSpace($hyg)) { return $false }   # hygiene is optional and stays optional
  $pattern = $null
  if ($Variant -eq 'any-mutation-word') { $pattern = 'mutation|变异' }
  else {
    # READ from the declared table - there is deliberately no fallback copy, the same discipline
    # Test-ScaffoldFieldPlaceholder applies to the placeholder rule.
    $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-HYGIENE-MUT' })
    if ($rule.Count -ne 1) { throw "Test-ScaffoldCardMutationPathsVia: rule CARD-HYGIENE-MUT is missing from Get-ScaffoldCardRules - the declared table is the only source of this pattern and there is no fallback copy." }
    $pattern = $rule[0].Pattern
  }
  if ($hyg -notmatch $pattern) { return $false }
  if ($Variant -eq 'ignore-allow-paths') { return $true }
  $cov = Get-ScaffoldCardMutationPathCoverage $fm
  if ($Variant -eq 'any-mutation-path') { return ($cov.Declared.Count -eq 0) }
  return -not ($cov.Registry -and $cov.Results)
}

# Which of the batch's two artifacts does allow_paths have room for? Split out of the decision because the
# FAILURE TEXT needs the same answer: a message restating a fixed cause ("allow_paths does not declare
# specs/mutations/") is simply wrong for a card that declared half of it, and re-deriving the answer inside
# the message is how the two drift apart (L97). Returns the two booleans plus the entries it judged, so the
# refusal can quote the card's own declaration back at it instead of naming a cause the card does not have.
function Get-ScaffoldCardMutationPathCoverage($FrontMatter) {
  $cov = @{ Registry = $false; Results = $false; Declared = @() }
  foreach ($p in @(Get-YamlBlockListItems $FrontMatter 'allow_paths')) {
    $n = ($p -replace '\\', '/').Trim().TrimStart('./')
    if ($n -notmatch '^specs/mutations(/|$)') { continue }
    $cov.Declared += $n
    # The directory itself, bare or trailing-slash: the ship scope matcher admits every path under it, so
    # this one entry covers both artifacts. It is the shape the failure text steers authors to, and the
    # shape 9 of the 12 exposed archived cards already used.
    if ($n -match '^specs/mutations/?$') { $cov.Registry = $true; $cov.Results = $true; continue }
    if ($n -match '\.psd1$') { $cov.Registry = $true; continue }
    if ($n -match '-results\.tsv$') { $cov.Results = $true; continue }
  }
  return $cov
}

# The live decision. Takes card TEXT rather than a path, so the rule is testable without writing a card
# file to disk (same contract as Test-ScaffoldCardSweep). Returns findings as strings and never throws
# except on a missing table entry, which is a wiring error rather than a card error.
function Test-ScaffoldCardMutationPaths {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Text)
  $findings = @()
  if (Test-ScaffoldCardMutationPathsVia $Text $null) {
    $cov = Get-ScaffoldCardMutationPathCoverage (Get-FrontMatter $Text)
    $missing = @()
    if (-not $cov.Registry) { $missing += 'the registry (specs/mutations/<card-id>.psd1)' }
    if (-not $cov.Results) { $missing += 'the results TSV (specs/mutations/<card-id>-results.tsv)' }
    $declared = if ($cov.Declared.Count -gt 0) { "it declares $($cov.Declared -join ', '), which covers only the other half" } else { 'it declares nothing under specs/mutations/ at all' }
    $findings += "[CARD-HYGIENE-MUT] the hygiene field promises a mutation-evidence batch (L165), but allow_paths has nowhere for $($missing -join ' and ') to land - $declared. mutate.ps1 writes BOTH files per batch, and the ship scope gate reads allow_paths from the BASE card, so widening it in-branch is inert by design: whichever half is unscoped is refused at ship, AFTER the batch has already run (TD194, hit live on T191; the one-artifact form of this rule was TD145/T90). That is also what specs/mutations/README.md exists to stop - evidence stops living in a scratchpad. [FIX] declare the bare directory specs/mutations/, which covers both, or name both files - or reword hygiene to describe the pruning you will actually do, since the template mutation-survivor line owes no registry and is not matched by this rule."
  }
  return $findings
}

# Declared examples for the mutation-evidence path decision, one per case CLASS. Returns findings as
# strings and never throws (same contract as Test-ScaffoldCardSweepExamples). Default = exercise the live
# predicate and expect zero findings; -Variant re-runs the same examples through a named rejected shape,
# which MUST produce at least one finding. Hermetic: every case is text, nothing is written or resolved.
# The first two cases are the real template line in both languages - 17 cards in this repo carry one of
# them - so they are the anti-over-breadth control that the any-mutation-word variant is measured against.
function Test-ScaffoldCardMutationPathsExamples {
  [CmdletBinding()]
  param([ValidateSet('any-mutation-word', 'ignore-allow-paths', 'any-mutation-path')][string]$Variant)
  $mk = {
    param($hygLine, $paths)
    (@('---', 'id: TZ-EXAMPLE', 'status: todo') + @($hygLine | Where-Object { $null -ne $_ }) +
     @('allow_paths:') + @($paths | ForEach-Object { "  - $_" }) + @('---', 'body')) -join "`n"
  }
  $cases = @(
    @{ what = 'template pruning line (Chinese), no mutations path'; text = (& $mk 'hygiene: 冗余测试经 mutation-survivor 剪枝（R4）' @('scripts/task.ps1')); expect = $false }
    @{ what = 'template pruning line (English), no mutations path'; text = (& $mk 'hygiene: redundant assertions pruned via mutation-survivor (R4)' @('scripts/task.ps1')); expect = $false }
    @{ what = 'promised batch (English), no mutations path'; text = (& $mk 'hygiene: Single-line-deletion mutations traced through the arms this card runs (L165/L237).' @('scripts/task.ps1')); expect = $true }
    @{ what = 'promised batch (Chinese), no mutations path'; text = (& $mk 'hygiene: 每道新守卫配单句删除变异（L165/L167）' @('scripts/task.ps1')); expect = $true }
    @{ what = 'promised batch, mutations directory declared'; text = (& $mk 'hygiene: Single-line-deletion mutations traced through the arms this card runs (L165/L237).' @('scripts/task.ps1', 'specs/mutations/')); expect = $false }
    @{ what = 'promised batch, ONLY the registry declared - the results TSV has nowhere to land'; text = (& $mk 'hygiene: evidence batch run by scripts/mutate.ps1 from this worktree' @('specs/mutations/TZ-EXAMPLE.psd1')); expect = $true; missing = 'results TSV' }
    @{ what = 'promised batch, ONLY the results TSV declared - the symmetric half, not a special case'; text = (& $mk 'hygiene: evidence batch run by scripts/mutate.ps1 from this worktree' @('specs/mutations/TZ-EXAMPLE-results.tsv')); expect = $true; missing = 'registry' }
    @{ what = 'promised batch, both files named explicitly'; text = (& $mk 'hygiene: evidence batch run by scripts/mutate.ps1 from this worktree' @('specs/mutations/TZ-EXAMPLE.psd1', 'specs/mutations/TZ-EXAMPLE-results.tsv')); expect = $false }
    @{ what = 'promised batch, path declared with backslashes'; text = (& $mk 'hygiene: 预算守卫的删除变异（注掉预算检查行→17z 预算 case 红）' @('specs\mutations\')); expect = $false }
    @{ what = 'no hygiene field at all'; text = (& $mk $null @('scripts/task.ps1')); expect = $false }
    @{ what = 'hygiene present but says no tests are owed'; text = (& $mk 'hygiene: 纯文档卡；正确性由 R3 语义评审 + selftest 哨兵闸兜底' @('docs/adr/README.md')); expect = $false }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = Test-ScaffoldCardMutationPathsVia $c.text $v
    if ($got -ne $c.expect) { $findings += "[CARD-HYGIENE-MUT-EXAMPLE] case '$($c.what)' judged offending=$got, expected $($c.expect) - a card promising a mutation-evidence batch must have somewhere for BOTH the registry and its results TSV to land, and a card carrying only the template's mutation-survivor pruning line must not be asked for either (TD145/TD194). [FIX] fix the predicate or the table pattern, never the example." }
    # The verdict is half the contract; the other half is that the refusal names WHICH artifact has
    # nowhere to go. A card that declared its registry and forgot the results TSV gets a message about
    # the results TSV - it is already looking at the entry the old fixed-cause text told it to add.
    # Only exercised on the half-covered cases, and skipped entirely under a -Variant, whose whole
    # purpose is to make the verdict disagree.
    if ((-not $useVariant) -and $c.ContainsKey('missing')) {
      $msg = @(Test-ScaffoldCardMutationPaths -Text $c.text) -join "`n"
      $other = if ($c.missing -eq 'registry') { 'results TSV' } else { 'registry' }
      if ($msg -notmatch [regex]::Escape("the $($c.missing) (")) { $findings += "[CARD-HYGIENE-MUT-EXAMPLE] case '$($c.what)' is refused, but the message never names the $($c.missing) as the half with nowhere to land. The refusal has to report from the card's own declared entries rather than restate a fixed cause (L97), or it tells an author to add a path they already added. [FIX] fix the message, never the example.`nActual: $msg" }
      elseif ($msg -match [regex]::Escape("the $other (")) { $findings += "[CARD-HYGIENE-MUT-EXAMPLE] case '$($c.what)' names the $other as missing, but the card declared it - the classification and the message have come apart. [FIX] fix Get-ScaffoldCardMutationPathCoverage, never the example.`nActual: $msg" }
    }
  }
  return $findings
}

# -- T102-TD147-CARD-DUP-GUARD: two live cards must not claim one debt row without declaring the split --
# A debt may legitimately be repaid by several cards - the tracker's own shape is 1 TD -> 1..N cards ->
# 1..N PRs - so multiplicity is NOT the defect. TD88 became nine cards and TD22 six, all deliberate. The
# defect is an UNDECLARED duplicate: TD144 was carded as T98-MUT-ROOT-GUARD and, from a parallel session,
# again as T99-MUTATE-ROOT-SPLIT, and one of them was fully implemented and merged before anyone compared
# the two (TD147).
# Three measurements shaped the rule, and each one is pinned by an example in
# Test-ScaffoldCardCrossRefExamples rather than left to the next reader's memory:
#   * a claim lives in the card id and title, NEVER in the body. Body-wide, TD69 appears in 35 archived
#     cards and TD88 in 76 - lineage references, not claims - so a body-wide rule is pure noise.
#   * a pair is legal when the debt row's repayment-pointer column names both card ids. That column is
#     the declaration surface the tracker already defines for status 'carded'.
#   * a card carrying superseded_by is not an active claim. T99 already carries it.
# No new front-matter field is invented: both declaration surfaces already exist in the corpus.

# Card id and title only. Case-sensitive by construction - debt ids are upper-case - and the anchor is
# pure ASCII (L167). Returns a sorted, de-duplicated list, so a card naming one debt twice claims it once.
function Get-ScaffoldCardTdClaims {
  [CmdletBinding()]
  param([string]$Id = '', [string]$Title = '')
  return @([regex]::Matches("$Id $Title", '\bTD\d+\b') | ForEach-Object { $_.Value } | Sort-Object -Unique)
}

# Reads one debt row's repayment-pointer cell. The column index is located from the header row rather than
# hardcoded, exactly the way triage.ps1 locates the status column, so re-ordering the table cannot silently
# shift it; index 6 is the fallback for the documented seven-column shape. Returns '' when the tracker, the
# row, or the cell is missing. Those three are NOT equivalent to the caller: a missing ROW or cell is an
# absent declaration inside a tracker that exists, so the pair is reported; a missing TRACKER is no
# declaration surface at all, and Get-ScaffoldDupTdClaimErrors declines to judge before it ever asks (TD174).
function Get-ScaffoldTdRepaymentPointer {
  [CmdletBinding()]
  param([string]$TrackerText = '', [Parameter(Mandatory)][string]$TdId)
  if (-not $TrackerText) { return '' }
  $lines = @($TrackerText -split '\r?\n')
  $ptrIdx = 6
  foreach ($ln in $lines) {
    if ($ln -notmatch '^\s*\|') { continue }
    $cells = Split-TdRow $ln
    if ($cells.Count -ge 1 -and $cells[0] -eq 'id') {
      $i = [array]::IndexOf($cells, '偿还指针')
      if ($i -ge 0) { $ptrIdx = $i }
      break
    }
  }
  foreach ($ln in $lines) {
    if ($ln -notmatch '^\s*\|') { continue }
    $cells = Split-TdRow $ln
    if ($cells.Count -le $ptrIdx) { continue }
    if ($cells[0] -cne $TdId) { continue }
    return $cells[$ptrIdx]
  }
  return ''
}

# $Meta = card id -> @{ tds = <claimed debt ids>; superseded = <bool> }, the same map shape check-cards.ps1
# already builds for Get-ParallelOverlapErrors. Takes the tracker TEXT rather than a path, so the rule is
# testable without a tracker file on disk. Findings are RETURNED as strings, never thrown, so the caller
# prints them like its other card errors. Both keys are required: check-cards.ps1 runs under
# Set-StrictMode -Version Latest, where an omitted key throws rather than reading as $null, and failing
# loudly on a malformed map beats silently judging every card as claiming nothing.
function Get-ScaffoldDupTdClaimErrors {
  [CmdletBinding()]
  # T177: ScopeId narrows WHICH pairs are reported, never which cards are compared. The grouping below must
  # see every live card or a duplicate claim is invisible; an empty ScopeId means report every pair.
  param([Parameter(Mandatory)]$Meta, [string]$TrackerText = '', [string[]]$ScopeId = @())
  # GRACEFUL DEGRADATION (TD174) - the same call the sibling [CARD-REF-DANGLING] arm makes on the same input
  # ($hasDebtCorpus in check-cards.ps1) and for the reason its comment gives at length: a claim can only be
  # judged against a declaration surface that EXISTS. With no tracker there is nowhere to declare a split, so
  # every claiming pair would be reported and the [FIX] would name a file to edit that is not on disk - which
  # is exactly a freshly initialised downstream, where this rule has been reachable from task.ps1 -Phase
  # start since T177. A tracker that EXISTS and does not declare the split still fails: that is the rule.
  if ($TrackerText.Trim() -eq '') { return @() }
  $findings = @()
  $byTd = @{}
  foreach ($cid in @($Meta.Keys)) {
    if ($Meta[$cid].superseded) { continue }
    foreach ($td in @($Meta[$cid].tds)) {
      if (-not $byTd.ContainsKey($td)) { $byTd[$td] = @() }
      $byTd[$td] += $cid
    }
  }
  foreach ($td in @($byTd.Keys | Sort-Object)) {
    $ids = @($byTd[$td] | Sort-Object)
    if ($ids.Count -lt 2) { continue }
    if ($ScopeId.Count -gt 0 -and -not @($ids | Where-Object { $ScopeId -ccontains $_ })) { continue }
    $ptr = Get-ScaffoldTdRepaymentPointer -TrackerText $TrackerText -TdId $td
    $missing = @()
    foreach ($cid in $ids) { if ($ptr -cnotlike "*$cid*") { $missing += $cid } }
    if ($missing.Count -eq 0) { continue }
    $findings += "[CARD-TD-DUP] $($ids -join ' and ') each claim $td, but $td's repayment-pointer column does not name $($missing -join ' or '). A debt may legitimately be repaid by several cards - the tracker's shape is 1 TD -> 1..N cards - but the split must be DECLARED, or a parallel session carding the same debt goes unnoticed and a whole card of work is wasted (TD144 was carded twice and one copy shipped before anyone noticed). [FIX] if this is a real split, set $td to status carded and list every card id in its repayment-pointer column; if it is a duplicate, drop one card or mark it superseded_by the other."
  }
  return $findings
}

# Advisory half (TD147 arm (a), owner decision 2026-08-23: warn, never block). T<n> is documented as a
# STAGE number and specs/README.md lists several same-stage ids as compliant, so reuse cannot be an error
# without rewriting the primary-key contract - eight numbers repeat across the archived corpus. Since T57
# the number has in practice been an allocation sequence, and two sessions allocating the same one is how
# T98 came to name two different cards, so it is worth saying out loud at validation time.
function Get-ScaffoldCardIdReuseWarnings {
  [CmdletBinding()]
  param([string[]]$LiveIds = @(), [string[]]$CorpusIds = @())
  $findings = @()
  foreach ($cid in @($LiveIds | Sort-Object)) {
    if ($cid -cnotmatch '^T(\d+)-') { continue }
    $n = $Matches[1]
    $others = @($CorpusIds | Where-Object { $_ -cne $cid -and $_ -cmatch "^T$n-" } | Sort-Object)
    if ($others.Count -eq 0) { continue }
    $findings += "[CARD-ID-REUSE] $cid reuses number $n, already carried by $($others -join ', '). This is LEGAL and mechanically inert: T<n> is a stage number, the archive carries duplicates at T1/T2/T3/T4/T14, and two same-numbered cards share no filename, no branch and no worktree. [FIX] usually nothing. The number is not the problem - RENAMING a card that already exists on disk is, because it silently invalidates every reference another session holds (measured 2026-08-25: two sessions collided on T121-T123, both renumbered on this advisory's own earlier wording, and each side's references to the other went stale). Once a card file exists, treat its id as immutable; rename only what nothing references yet. [CARD-REF-DANGLING] will tell you when that assumption was wrong."
  }
  return $findings
}

# -- T177-CARD-RULES-UNGATED: which cards do the cross-card rules RESOLVE against, and which do they REPORT on? --
# These are two independent questions, and check-cards used to collapse them into one flag. The task-id
# parameter was written to mean "validate this one card", so the whole cross-card block sat behind a
# not-task-id guard - and since all three task.ps1 phases pass a task id, NONE of those rules was reachable
# in the local loop. CI, which runs full-corpus, therefore enforced a STRICTLY STRONGER contract than ship
# (TD173). Measured live: a card naming a full-form id that resolved to nothing passed start, red and ship,
# then failed CI twice and needed a branch merge to clear, because the merge ref is pinned.
#
# Two designs were rejected, and each is pinned by a -Variant below because both are plausible regressions:
#   * scope-is-corpus  - narrow the resolution corpus along with the scope. This is where a naive move of
#     the block lands, and every legitimate reference from the scoped card to ANOTHER live card would then
#     resolve to nothing and be reported as dangling - a false rejection of ordinary card text.
#   * no-scope-filter  - report on every live card even when the run is narrowed. Ship would then block on
#     a defect in a card outside its own allow_paths, which the shipper cannot fix from that worktree.
#
# Pure: no IO, no git. The caller supplies the live ids, which is what makes this testable with no card
# files on disk at all.
function Get-ScaffoldCardCheckScope {
  [CmdletBinding()]
  param([string[]]$LiveId = @(), [string]$TaskId = '', [string]$Variant = '')
  $corpus = @($LiveId | Sort-Object -Unique)
  $scope = if ($TaskId) { @($TaskId) } else { @($corpus) }
  # The two rejected designs, reachable only from the declared examples that must flag them.
  if ($Variant -eq 'scope-is-corpus') { $corpus = @($scope) }
  if ($Variant -eq 'no-scope-filter') { $scope = @($corpus) }
  return [pscustomobject]@{ Corpus = @($corpus); Scope = @($scope) }
}

function Test-ScaffoldCardCheckScopeExamples {
  <#
  .SYNOPSIS  T177 declared examples for the corpus/scope split. Returns findings; an empty result is green.
             -Variant feeds the SAME examples one of the two designs this repo measured and rejected, so a
             regression to either produces findings rather than passing quietly. Hermetic - no files, no git.
  #>
  [CmdletBinding()]
  param([string]$Variant = '')
  $live = @('T1-ALPHA', 'T2-BETA', 'T3-GAMMA')
  $findings = @()

  $one = Get-ScaffoldCardCheckScope -LiveId $live -TaskId 'T2-BETA' -Variant $Variant
  # 1. A narrowed run still resolves ids against every live card. This is the arm that keeps the change
  #    shippable: shrink the corpus and ordinary inter-card references start failing.
  if (@($one.Corpus).Count -ne 3) {
    $findings += "[CARD-SCOPE-EXAMPLE] a run narrowed to one card resolved ids against $(@($one.Corpus).Count) of 3 live cards. The corpus must stay whole - shrink it and every reference from the scoped card to another LIVE card is reported as dangling. [FIX] fix the rule, never the example."
  }
  elseif (@($one.Corpus) -notcontains 'T1-ALPHA' -or @($one.Corpus) -notcontains 'T3-GAMMA') {
    $findings += '[CARD-SCOPE-EXAMPLE] a run narrowed to one card dropped an unrelated live card from the resolution corpus, so a reference to that card would be reported as dangling.'
  }

  # 2. A narrowed run reports on that card ALONE. A defect in an unrelated live card must not block this
  #    card's ship: that card is outside its allow_paths and cannot be fixed from its worktree.
  if (@($one.Scope).Count -ne 1 -or @($one.Scope)[0] -cne 'T2-BETA') {
    $findings += "[CARD-SCOPE-EXAMPLE] a run narrowed to T2-BETA reported on $(@($one.Scope) -join ', ') rather than T2-BETA alone. Reporting on other live cards blocks a ship on defects outside the shipped card's allow_paths."
  }

  # 3. An un-narrowed run is UNCHANGED: corpus and scope are identical, which is the mode selftest gate 10
  #    and CI already run.
  $all = Get-ScaffoldCardCheckScope -LiveId $live -Variant $Variant
  if (@($all.Corpus).Count -ne 3 -or @($all.Scope).Count -ne 3) {
    $findings += '[CARD-SCOPE-EXAMPLE] an un-narrowed run must resolve against and report on every live card - that is the mode selftest gate 10 and CI already run, and this change must not alter it.'
  }

  # 4. No live cards is not an error. The hot set is routinely zero between arcs, and a freshly initialised
  #    downstream is the same shape (the repo's empty-config iron rule).
  $none = Get-ScaffoldCardCheckScope -Variant $Variant
  if (@($none.Corpus).Count -ne 0 -or @($none.Scope).Count -ne 0) {
    $findings += '[CARD-SCOPE-EXAMPLE] a tree with no live cards must yield an empty corpus and an empty scope, not a throw or a phantom entry.'
  }
  return $findings
}

# -- T159-CARD-REF-INTEGRITY: does every id a live card references actually resolve? --
# Nothing anywhere checked this. depends_on was parsed for schema and never validated for existence, and no
# rule looked at card ids or TD ids mentioned in card text. Gate 16 performs exactly this check for L-ids
# across skills and docs, so the pattern is proven; the gap is that it was never extended to the other two
# id spaces. That is how specs/tasks/_TEMPLATE.md came to ship a plan_ref example pointing at a path that
# has never existed here.
#
# THE COLLISION IS NOT THE DAMAGE. Two cards sharing a number is legal - T<n> is a STAGE number, the archive
# carries duplicates at T1/T2/T3/T4/T14, and two same-numbered cards share no filename, branch or worktree.
# The dangerous operation is the RENAME: every reference another session holds is silently invalidated.
# Measured live 2026-08-25 - two sessions collided on T121-T123, both renumbered on [CARD-ID-REUSE]'s own
# advice, and each side's references to the other went stale with no gate noticing.
#
# Pure: no IO, no git. The caller supplies the resolution sets, which is what makes this testable with no
# card files on disk at all (acceptance 7).
# Severities, and the reason they differ: a FULL-FORM id (T<n>-NAME) is unambiguous, so failing to resolve
# is a defect and blocks. A BARE T<n> is ambiguous by construction - it may be a stage-number reference in
# prose - so it only warns. Archived cards are never scanned: their references are historical records.
function Get-ScaffoldDanglingCardRef {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][string]$CardText,
    [string[]]$KnownCardId = @(),
    [string[]]$KnownDebtId = @(),
    [AllowEmptyString()][string]$SelfId = ''
  )
  $findings = [System.Collections.Generic.List[object]]::new()
  if ([string]::IsNullOrWhiteSpace($CardText)) { return @($findings) }

  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-REF-DANGLING' })
  if ($rule.Count -ne 1) { throw "Get-ScaffoldDanglingCardRef: rule CARD-REF-DANGLING is missing from Get-ScaffoldCardRules - the declared table is the only source of the reference SHAPE and there is no fallback copy." }
  $known = @($KnownCardId)
  $knownNum = @($known | ForEach-Object { if ($_ -cmatch '^T(\d+)-') { $Matches[1] } } | Sort-Object -Unique)

  # 1. Full-form card ids. Unambiguous, so a miss blocks.
  foreach ($m in [regex]::Matches($CardText, $rule[0].Pattern)) {
    $ref = $m.Value
    if ($SelfId -and $ref -ceq $SelfId) { continue }
    if ($ref -cin $known) { continue }
    $findings.Add([pscustomobject]@{ Kind = 'card'; Ref = $ref; Severity = 'block' })
  }
  # 2. Debt ids. Same reasoning: TD<n> is unambiguous.
  foreach ($m in [regex]::Matches($CardText, '\bTD\d+\b')) {
    if ($m.Value -cin @($KnownDebtId)) { continue }
    $findings.Add([pscustomobject]@{ Kind = 'debt'; Ref = $m.Value; Severity = 'block' })
  }
  # 3. Bare T<n> not followed by a hyphen. Ambiguous - warns only, and never on a number some card carries.
  foreach ($m in [regex]::Matches($CardText, '\bT(\d+)\b(?!-)')) {
    if ($m.Groups[1].Value -in $knownNum) { continue }
    $findings.Add([pscustomobject]@{ Kind = 'bare'; Ref = $m.Value; Severity = 'warn' })
  }
  # De-duplicate: a reference repeated ten times in one card is one defect, not ten.
  return @($findings | Group-Object Kind, Ref | ForEach-Object { $_.Group[0] })
}

function Test-ScaffoldCardRefExamples {
  <#
  .SYNOPSIS  T159 arm 5: declared examples for the dangling-reference predicate. Returns findings; an empty
             result is green. Hermetic - synthetic card text and caller-supplied id sets, no files, no git.
  #>
  [CmdletBinding()]
  param()
  $live = @('T1-ALPHA', 'T2-BETA')
  $debts = @('TD10', 'TD11')
  $findings = @()

  $ok = @(Get-ScaffoldDanglingCardRef -CardText "depends_on:`n  - T1-ALPHA`nbody mentions T2-BETA and TD10." -KnownCardId $live -KnownDebtId $debts -SelfId 'T3-GAMMA')
  if ($ok.Count -ne 0) { $findings += "[CARD-REF-EXAMPLE] a card whose every reference resolves was reported: $(($ok | ForEach-Object { $_.Ref }) -join ', '). [FIX] fix the rule, never the example." }

  $miss = @(Get-ScaffoldDanglingCardRef -CardText 'depends_on:  - T9-NOSUCH' -KnownCardId $live -KnownDebtId $debts)
  if (@($miss | Where-Object { $_.Kind -eq 'card' -and $_.Ref -eq 'T9-NOSUCH' -and $_.Severity -eq 'block' }).Count -ne 1) {
    $findings += '[CARD-REF-EXAMPLE] a depends_on entry naming a card that does not exist was not blocked - that is the renamed-card breakage this rule exists for.'
  }
  $debtMiss = @(Get-ScaffoldDanglingCardRef -CardText 'this repays TD99.' -KnownCardId $live -KnownDebtId $debts)
  if (@($debtMiss | Where-Object { $_.Kind -eq 'debt' -and $_.Severity -eq 'block' }).Count -ne 1) {
    $findings += '[CARD-REF-EXAMPLE] a TD reference resolving to no tracker row and no archived debt was not blocked.'
  }
  $bare = @(Get-ScaffoldDanglingCardRef -CardText 'see T77 for context.' -KnownCardId $live -KnownDebtId $debts)
  if (@($bare | Where-Object { $_.Severity -eq 'warn' }).Count -ne 1) {
    $findings += '[CARD-REF-EXAMPLE] a bare T-number matching no card should WARN - it is ambiguous by construction and may be a stage-number reference in prose.'
  }
  if (@($bare | Where-Object { $_.Severity -eq 'block' }).Count -ne 0) {
    $findings += '[CARD-REF-EXAMPLE] a bare T-number was BLOCKED. Blocking on an ambiguous mention would make ordinary prose unshippable.'
  }
  $bareKnown = @(Get-ScaffoldDanglingCardRef -CardText 'stage T1 work continues.' -KnownCardId $live -KnownDebtId $debts)
  if ($bareKnown.Count -ne 0) { $findings += '[CARD-REF-EXAMPLE] a bare T-number whose number IS carried by a live card was reported.' }
  $selfRef = @(Get-ScaffoldDanglingCardRef -CardText '# T3-GAMMA does the thing' -KnownCardId $live -KnownDebtId $debts -SelfId 'T3-GAMMA')
  if ($selfRef.Count -ne 0) { $findings += '[CARD-REF-EXAMPLE] a card naming ITSELF was reported as dangling - every card states its own id in the front matter and the heading.' }
  $dup = @(Get-ScaffoldDanglingCardRef -CardText 'T9-NOSUCH here, T9-NOSUCH again, T9-NOSUCH once more.' -KnownCardId $live -KnownDebtId $debts)
  if (@($dup | Where-Object { $_.Kind -eq 'card' }).Count -ne 1) { $findings += '[CARD-REF-EXAMPLE] one broken reference repeated three times reported more than once - that is one defect, not three.' }
  return $findings
}

function Get-ScaffoldRetractionMarkerPattern {
  # T212/TD198: the ONE sentinel gate 16b reads out of specs/archive/tasks. Pure ASCII (L167) so the anchor
  # survives any encoding round-trip, and declared HERE rather than inline so the gate, the declared examples
  # and specs/README.md's prose all project from a single literal instead of three copies drifting apart.
  return '\[RETRACTED (L\d+)\]'
}

function Get-ScaffoldRetractionSection {
  # The ONE place that decides WHERE a marker counts. specs/README.md declares the form as a
  # `## Retracted claims` section; both the judge and the gate's marker counter read that decision from
  # here, so the number a run reports and the number it judged cannot disagree (L271: state the rule once,
  # never restate it in the consumer). Returns '' for a card with no such section, which is most of them.
  [CmdletBinding()]
  param([AllowEmptyString()][string]$CardText = '')
  $m = [regex]::Match($CardText, '(?ms)^##[ \t]+Retracted claims[ \t]*\r?$(?<body>.*?)(?=^##[ \t]|\z)')
  if ($m.Success) { return $m.Groups['body'].Value }
  return ''
}

function Get-ScaffoldRetractionFinding {
  <#
  .SYNOPSIS  T212/TD198: judge the annotations that link an ARCHIVED card back to the lesson which refuted
             one of its claims. Hermetic - takes text, returns findings, touches no file and no git.
  .DESCRIPTION
    An archived card is frozen: specs/README.md forbids rewriting it, and `superseded_by` only answers "a
    later CARD deleted what this card's DoD asserted". A claim refuted by a LESSON had no form at all, so the
    link ran one way - L287 names T201-DOCGATE-READS-GUARD in its refs, and T201 said nothing back. A reader
    arriving by grep at the archived card read the refuted sentence with nothing beside it.

    Five findings, in the order they matter:
      lesson-unknown    a [RETRACTED L<n>] marker names an id defined in neither LEDGER nor the cold store.
      claim-missing     a marker carries no double-quoted claim, so there is nothing to anchor.
      anchor-unresolved the quoted claim occurs NOWHERE ELSE in that same card. This is the arm that makes
                        the grep guarantee real: the quote has to be the frozen sentence verbatim, so one
                        grep of specs/archive/tasks returns claim and retraction together with no ledger
                        read and no file open. A paraphrase reads fine to a human and silently breaks that,
                        which is exactly why a human cannot be the check here.
      marker-missing    a ledger entry declares `retracts: <card-id>` and that card carries no marker for
                        that lesson - the reverse direction. Without it the form is opt-in and the NEXT
                        retraction goes unrecorded in precisely the way this one did.
      card-missing      a `retracts:` names a card that is not in the archive at all (typo, or renamed).

    DELIBERATELY NOT CHECKED: that every archived card holding a refuted claim carries a marker. Nothing can
    know which frozen sentences are false, and asserting it would put every archived card in breach of a rule
    invented after it was frozen - the retroactive rewrite specs/README.md exists to forbid. The reverse arm
    is the honest half of that: it binds only what a lesson has explicitly declared.
  #>
  [CmdletBinding()]
  param(
    [hashtable]$Card = @{},
    [AllowEmptyString()][string]$LedgerText = '',
    [string[]]$KnownLessonId = @()
  )
  $findings = [System.Collections.Generic.List[object]]::new()
  $markerRe = Get-ScaffoldRetractionMarkerPattern
  $known = @($KnownLessonId)
  # card id -> lesson ids it carries a marker for. Built by the forward pass, read by the reverse one.
  $carries = @{}

  foreach ($id in @($Card.Keys | Sort-Object)) {
    $text = [string]$Card[$id]
    $carries[$id] = @()
    # Markers are read ONLY from the card's `## Retracted claims` section - the form specs/README.md
    # declares. Scanning the whole body makes DOCUMENTING the construct indistinguishable from USING it,
    # and a card that introduces a form must show that form, so the very first card archived under this
    # rule reddened the gate with its own acceptance list (T214). Same discipline as the neighbouring
    # sentinel readers: 14i reads [GATE-MAP] between explicit delimiters, 14n reads tags at declared
    # sites. The ANCHOR search below deliberately still spans the WHOLE card, because the frozen sentence
    # being annotated lives out in the prose - that is the entire point of quoting it.
    $scan = Get-ScaffoldRetractionSection -CardText $text
    foreach ($line in ($scan -split "`r?`n")) {
      $m = [regex]::Match($line, $markerRe)
      if (-not $m.Success) { continue }
      $lid = $m.Groups[1].Value
      $carries[$id] += $lid
      if ($lid -cnotin $known) {
        $findings.Add([pscustomobject]@{ Kind = 'lesson-unknown'; Card = $id; Lesson = $lid; Claim = '' })
      }
      $q = [regex]::Match($line, '"([^"]+)"')
      if (-not $q.Success) {
        $findings.Add([pscustomobject]@{ Kind = 'claim-missing'; Card = $id; Lesson = $lid; Claim = '' })
        continue
      }
      $claim = $q.Groups[1].Value
      # >= 2, never >= 1: one occurrence is the marker quoting ITSELF, which proves nothing. The second is
      # the frozen sentence being annotated, and it is the whole deliverable - a reader's grep for the false
      # claim has to return this marker as well as the claim.
      if (@([regex]::Matches($text, [regex]::Escape($claim))).Count -lt 2) {
        $findings.Add([pscustomobject]@{ Kind = 'anchor-unresolved'; Card = $id; Lesson = $lid; Claim = $claim })
      }
    }
  }

  # Reverse direction. Block-scoped to each `## L<n>` so a `retracts:` is attributed to the lesson that
  # actually declares it - reading the ledger flat would credit every declaration to whichever id matched
  # last, which is L289's dropped-identifying-column defect arriving through a parser.
  foreach ($blk in [regex]::Matches($LedgerText, '(?ms)^##\s*(L\d+)\b.*?(?=^##\s*L\d+\b|\z)')) {
    $lid = $blk.Groups[1].Value
    foreach ($r in [regex]::Matches($blk.Value, '(?m)^-\s*retracts:\s*(\S+)\s*$')) {
      $target = $r.Groups[1].Value
      if (-not $Card.ContainsKey($target)) {
        $findings.Add([pscustomobject]@{ Kind = 'card-missing'; Card = $target; Lesson = $lid; Claim = '' })
      }
      elseif ($lid -cnotin @($carries[$target])) {
        $findings.Add([pscustomobject]@{ Kind = 'marker-missing'; Card = $target; Lesson = $lid; Claim = '' })
      }
    }
  }
  return @($findings)
}

function Test-ScaffoldRetractionExamples {
  <#
  .SYNOPSIS  T212 declared examples for the retraction predicate. Returns findings; empty is green.
             Hermetic - synthetic card text and a synthetic ledger, no files, no git.
  #>
  [CmdletBinding()]
  param()
  $known = @('L1', 'L2')
  $claim = 'the merge would surface it as a conflict'
  $goodCard = "Body text: $claim, said this card.`n`n## Retracted claims`n> **[RETRACTED L1]** `"$claim`" - refuted by measurement. See L1."
  $good = @{ 'T1-ALPHA' = $goodCard }
  $goodLedger = "## L1`n- retracts: T1-ALPHA`n- rule: measure, do not assume.`n`n## L2`n- rule: unrelated."
  $findings = @()

  $ok = @(Get-ScaffoldRetractionFinding -Card $good -LedgerText $goodLedger -KnownLessonId $known)
  if ($ok.Count -ne 0) { $findings += "[RETRACTION-EXAMPLE] a well-formed retraction was reported as broken: $(($ok | ForEach-Object { $_.Kind }) -join ', '). [FIX] fix the rule, never the example." }

  $unknown = @{ 'T1-ALPHA' = "Body text: $claim, said this card.`n`n## Retracted claims`n> **[RETRACTED L99]** `"$claim`" - refuted." }
  $u = @(Get-ScaffoldRetractionFinding -Card $unknown -LedgerText '' -KnownLessonId $known)
  if (@($u | Where-Object { $_.Kind -eq 'lesson-unknown' -and $_.Lesson -eq 'L99' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] a marker naming an L-id defined in neither the ledger nor the cold store was not reported - it points the reader at nothing.'
  }

  # The paraphrase face, and the reason this predicate exists rather than a naked convention: the annotation
  # reads perfectly to a human and the grep guarantee is already gone.
  $para = @{ 'T1-ALPHA' = "Body text: $claim, said this card.`n`n## Retracted claims`n> **[RETRACTED L1]** `"the merge surfaces a conflict`" - refuted." }
  $p = @(Get-ScaffoldRetractionFinding -Card $para -LedgerText '' -KnownLessonId $known)
  if (@($p | Where-Object { $_.Kind -eq 'anchor-unresolved' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] a marker whose quoted claim is a PARAPHRASE rather than the frozen sentence was accepted. A reader grepping the real sentence would then get the claim and not the retraction, which is the entire defect TD198 registers.'
  }

  $noQuote = @{ 'T1-ALPHA' = "Body text: $claim.`n`n## Retracted claims`n> **[RETRACTED L1]** this card was wrong." }
  $nq = @(Get-ScaffoldRetractionFinding -Card $noQuote -LedgerText '' -KnownLessonId $known)
  if (@($nq | Where-Object { $_.Kind -eq 'claim-missing' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] a marker carrying no quoted claim was accepted - there is nothing for a grep to land on and nothing to anchor.'
  }

  $bare = @{ 'T1-ALPHA' = 'Body text with no marker at all.' }
  $b = @(Get-ScaffoldRetractionFinding -Card $bare -LedgerText $goodLedger -KnownLessonId $known)
  if (@($b | Where-Object { $_.Kind -eq 'marker-missing' -and $_.Card -eq 'T1-ALPHA' -and $_.Lesson -eq 'L1' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] a ledger entry declaring retracts: against a card that carries NO marker was accepted. That is the reverse direction, and without it the whole form is opt-in.'
  }

  $wrongLesson = @{ 'T1-ALPHA' = "Body: $claim.`n`n## Retracted claims`n> **[RETRACTED L2]** `"$claim`" - refuted." }
  $wl = @(Get-ScaffoldRetractionFinding -Card $wrongLesson -LedgerText $goodLedger -KnownLessonId $known)
  if (@($wl | Where-Object { $_.Kind -eq 'marker-missing' -and $_.Lesson -eq 'L1' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] L1 declared retracts: T1-ALPHA and the card answered with a marker for a DIFFERENT lesson; the pairing must be per-lesson, not per-card.'
  }

  $gone = @(Get-ScaffoldRetractionFinding -Card @{} -LedgerText $goodLedger -KnownLessonId $known)
  if (@($gone | Where-Object { $_.Kind -eq 'card-missing' -and $_.Card -eq 'T1-ALPHA' }).Count -ne 1) {
    $findings += '[RETRACTION-EXAMPLE] a retracts: naming a card that is not in the archive at all was accepted - a typo or a rename then points at nothing.'
  }

  # The defect T214 fixes, as a declared example. A card that DOCUMENTS this form necessarily names the
  # marker in its prose - T212's own acceptance list said "carries a [RETRACTED L287] marker" with no
  # quoted claim - and scanning the whole body read that as a retraction, so the first card ever archived
  # under this rule reddened the gate it had just shipped. Describing a construct is not using it. Both
  # shapes are covered here: a code-span mention, and a full blockquote marker under a DIFFERENT heading.
  $prose = @{ 'T1-ALPHA' = "Body: $claim.`n`n## Deliverable`nThe live instance carries a ``[RETRACTED L1]`` marker, so the card points back at the lesson that refuted it.`n`n## Notes`n> **[RETRACTED L1]** ""$claim"" - named again here, still outside any Retracted claims section." }
  $pr = @(Get-ScaffoldRetractionFinding -Card $prose -LedgerText '' -KnownLessonId $known)
  if ($pr.Count -ne 0) { $findings += "[RETRACTION-EXAMPLE] a card that only MENTIONS the marker in prose, outside any ``## Retracted claims`` section, was read as carrying $($pr.Count) retraction(s): $(($pr | ForEach-Object { $_.Kind }) -join ', '). Documenting the form is not using it." }
  # Graceful degradation, the repo's empty-config iron rule: a tree with no archive and no ledger is not in
  # breach of anything. This arm is what keeps the gate OFF in a freshly initialised downstream.
  $empty = @(Get-ScaffoldRetractionFinding -Card @{} -LedgerText '' -KnownLessonId @())
  if ($empty.Count -ne 0) { $findings += '[RETRACTION-EXAMPLE] an empty tree reported findings; a downstream with no archive and no ledger must degrade OFF, not block.' }

  return $findings
}

# Fixture for the declared examples below, exposed so selftest drives the same inputs the card DoD drives.
# TD500 is a DECLARED split (its pointer names both cards). TD501 is an UNDECLARED duplicate (its pointer
# names one). TD502 is a superseded pair. T96-LINEAGE names TD500 in its BODY only, which is precisely what
# the body-wide reading gets wrong.
function Get-ScaffoldCardCrossRefFixture {
  $tracker = @(
    '| id | 发现日 | 位置 | 偏离了什么（债） | 严重度 | 状态 | 偿还指针 |',
    '|---|---|---|---|---|---|---|',
    '| TD500 | 2026-01-01 | scripts/x.ps1 | seeded | major | carded | `T90-TD500-ALPHA` then `T91-TD500-BETA` |',
    '| TD501 | 2026-01-01 | scripts/y.ps1 | seeded | major | carded | `T92-TD501-GAMMA` |',
    '| TD502 | 2026-01-01 | scripts/z.ps1 | seeded | minor | carded | `T93-TD502-EPSILON` |'
  ) -join "`n"
  $cards = [ordered]@{
    'T90-TD500-ALPHA'   = @{ Title = 'first half of the declared TD500 split'; Body = 'no debt named here'; Superseded = $false }
    'T91-TD500-BETA'    = @{ Title = 'second half of the declared TD500 split'; Body = 'no debt named here'; Superseded = $false }
    'T92-TD501-GAMMA'   = @{ Title = 'repay TD501'; Body = 'no debt named here'; Superseded = $false }
    'T94-TD501-DELTA'   = @{ Title = 'also repay TD501, opened from a parallel session'; Body = 'no debt named here'; Superseded = $false }
    'T93-TD502-EPSILON' = @{ Title = 'repay TD502'; Body = 'no debt named here'; Superseded = $false }
    'T95-TD502-ZETA'    = @{ Title = 'repay TD502, superseded by the other card'; Body = 'no debt named here'; Superseded = $true }
    'T96-LINEAGE'       = @{ Title = 'unrelated card that cites prior art'; Body = 'context: the TD500 work established this shape'; Superseded = $false }
  }
  return @{ Tracker = $tracker; Cards = $cards; Corpus = @('T90-TD500-ALPHA', 'T90-OTHER-ARCHIVED', 'T91-TD500-BETA') }
}

# Declared examples for both cross-card rules. Returns findings as strings and never throws (same contract
# as Test-ScaffoldCardRuleExamples). Default = the live rules on the live inputs, and must return nothing.
# Each -Variant feeds the SAME rules a deliberately wrong INPUT - one per design this repo measured and
# rejected - and must produce at least one finding; those are the negative controls that keep the default
# run honest. No test-only switch is threaded through the production functions to make that work.
function Test-ScaffoldCardCrossRefExamples {
  [CmdletBinding()]
  param([ValidateSet('body-wide', 'blank-pointer', 'count-superseded')][string]$Variant)
  $fx = Get-ScaffoldCardCrossRefFixture
  $meta = @{}
  foreach ($cid in @($fx.Cards.Keys)) {
    $c = $fx.Cards[$cid]
    $claims = if ($Variant -eq 'body-wide') {
      @([regex]::Matches("$cid $($c.Title) $($c.Body)", '\bTD\d+\b') | ForEach-Object { $_.Value } | Sort-Object -Unique)
    } else {
      Get-ScaffoldCardTdClaims -Id $cid -Title $c.Title
    }
    $sup = if ($Variant -eq 'count-superseded') { $false } else { [bool]$c.Superseded }
    $meta[$cid] = @{ tds = $claims; superseded = $sup }
  }
  # 'blank-pointer' feeds a tracker that EXISTS with every repayment-pointer cell emptied - the input that
  # simulates "this design does not consult the declaration" now that an ABSENT tracker switches the rule off
  # (TD174). It used to pass '' for that, which since the degradation exercises the guard and returns
  # nothing, leaving this control silently vacuous. The name moved with the input so it cannot outlive it.
  $tracker = if ($Variant -eq 'blank-pointer') { $fx.Tracker -replace '`T\d+-[A-Z0-9-]+`', '-' } else { $fx.Tracker }
  $errs = @(Get-ScaffoldDupTdClaimErrors -Meta $meta -TrackerText $tracker)
  if ($Variant) { return $errs }   # a rejected design must yield at least one finding; the caller asserts that

  $findings = @()
  $joined = $errs -join ' | '
  if ($errs.Count -ne 1) { $findings += "[CROSSREF-EXAMPLE] expected exactly one duplicate-claim error, got $($errs.Count): $joined. [FIX] fix the rule, never the example." }
  if ($joined -notmatch 'TD501') { $findings += "[CROSSREF-EXAMPLE] the undeclared duplicate on TD501 was not reported - two live cards claim it and its repayment pointer names only one of them, which is exactly the TD144 incident. Got: $joined" }
  if ($joined -notmatch 'T92-TD501-GAMMA' -or $joined -notmatch 'T94-TD501-DELTA') { $findings += "[CROSSREF-EXAMPLE] the duplicate-claim error does not name BOTH cards, so it is not traceable to the pair that has to be reconciled. Got: $joined" }
  if ($joined -match 'TD500') { $findings += "[CROSSREF-EXAMPLE] the DECLARED split on TD500 was reported - its repayment pointer names both cards, and 1 TD -> 1..N cards is the tracker's designed shape (TD88 became nine cards). Got: $joined" }
  if ($joined -match 'TD502') { $findings += "[CROSSREF-EXAMPLE] the superseded pair on TD502 was reported - a card carrying superseded_by is not an active claim. Got: $joined" }
  if ($joined -match 'T96-LINEAGE') { $findings += "[CROSSREF-EXAMPLE] T96-LINEAGE was pulled into a claim group, but it names TD500 only in its BODY - body-wide, TD69 appears in 35 archived cards and TD88 in 76, all lineage rather than claims. Got: $joined" }

  # The degradation is an EXAMPLE, not only a selftest fixture: check-cards runs this function in its own
  # fail-fast self-check on every invocation, so a regression that re-arms the rule against a tracker-less
  # tree goes red at the entry script rather than waiting for gate 10 to be run.
  $off = @(Get-ScaffoldDupTdClaimErrors -Meta $meta -TrackerText '')
  if ($off.Count -ne 0) { $findings += "[CROSSREF-EXAMPLE] with no tracker text the rule still reported $($off.Count) claim(s): $($off -join ' | '). A claim can only be judged against a declaration surface that exists - with no specs/tech-debt-tracker.md the [FIX] names a file that is not there, and since T177 the rule blocks the first two cards a downstream writes (TD174). [FIX] restore the empty-tracker guard in Get-ScaffoldDupTdClaimErrors, never the example." }

  $warn = @(Get-ScaffoldCardIdReuseWarnings -LiveIds @('T90-TD500-ALPHA') -CorpusIds $fx.Corpus)
  $warnJoined = $warn -join ' | '
  if ($warn.Count -ne 1) { $findings += "[CROSSREF-EXAMPLE] expected exactly one id-reuse warning for T90-TD500-ALPHA, got $($warn.Count): $warnJoined" }
  if ($warnJoined -notmatch 'T90-OTHER-ARCHIVED') { $findings += "[CROSSREF-EXAMPLE] the id-reuse warning does not name the other card holding that number, so it says nothing actionable. Got: $warnJoined" }
  $clean = @(Get-ScaffoldCardIdReuseWarnings -LiveIds @('T91-TD500-BETA') -CorpusIds $fx.Corpus)
  if ($clean.Count -ne 0) { $findings += "[CROSSREF-EXAMPLE] a card whose number nothing else carries was warned about: $($clean -join ' | ')" }
  return $findings
}

# -- T103-CARD-ACCEPTANCE-SET: a reviewed card should close its own acceptance set -------------------
# Rubric #6 ("tests missing or fake") is decided against whatever the card says done means. A card that
# states behaviour but never enumerates the facts constituting done leaves #6 judged against an OPEN set,
# so every review round can legitimately name another untested branch and the review has no fixed point.
# Measured across 39 stored verdicts: #6 is 20 of 34 findings, and 17 of those 20 are code that already
# had a PASSING test - the assertion surface was narrower than the contract, not absent. An enumeration
# is what makes "narrower than the contract" decidable at all (upstream issue #203).
# WHAT THIS CHECKS AND WHAT IT CANNOT: it checks that a card whose diff will be reviewed carries a
# non-empty acceptance list. Whether that list is COMPLETE is a planning judgement no gate can make, and
# no message here may imply otherwise - the same honest boundary [CARD-SWEEP] draws.
# WARN, never block: the field is optional by design so every existing card keeps working (issue #203's
# own non-goal), and adoption is a nudge rather than a wall.
# The DECISION is split out of the PARSE so the declared examples can feed it a deliberately wrong input
# instead of threading a test-only switch through production (the T102 shape).
function Get-ScaffoldCardAcceptanceError {
  [CmdletBinding()]
  param([bool]$HasReviewGate, [bool]$KeyPresent, [int]$ItemCount)
  if (-not $HasReviewGate) { return '' }   # no second reviewer reads a list here, so the rule does not fire
  if (-not $KeyPresent) { return "[CARD-ACCEPTANCE] declares review_gate but carries no 'acceptance:' field - rubric #6 is then decided against an open set, so each review round can name another untested branch and the review has no fixed point. The field records WHICH facts constitute done; it cannot show that list is complete and no gate can check that. [FIX] add an 'acceptance:' block list numbering the facts, each naming the assertion that covers it." }
  if ($ItemCount -lt 1) { return "[CARD-ACCEPTANCE] declares review_gate and an 'acceptance:' field carrying no list items - the key on its own closes nothing. The field records WHICH facts constitute done; it cannot show that list is complete and no gate can check that. [FIX] put the numbered facts under 'acceptance:' as a block list, each naming the assertion that covers it." }
  return ''
}

# Takes card TEXT rather than a path, so the rule is testable without writing a card file to disk.
# Returns findings as strings and never throws; check-cards.ps1 prints them as WARNINGS, not errors.
function Test-ScaffoldCardAcceptance {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Text)
  $fm = Get-FrontMatter $Text
  if (-not $fm) { return @() }             # no front-matter at all: check-cards already errors on that
  $gate = Get-UncommentedValue (Get-Scalar $fm 'review_gate')
  $keyPresent = [regex]::IsMatch($fm, '(?m)^acceptance[ \t]*:')
  $count = Get-YamlListCount $fm 'acceptance'
  $err = Get-ScaffoldCardAcceptanceError -HasReviewGate ([bool]$gate) -KeyPresent $keyPresent -ItemCount $count
  return @(if ($err) { $err })
}

# Four seeded cards, one per class the rule must separate. Written as text so no file touches disk.
function Get-ScaffoldCardAcceptanceFixture {
  $head = @('---', 'id: T9-SEED', 'title: seeded card', 'status: todo')
  $tail = @('dod_command: pwsh -NoProfile -Command "exit 0"', 'dod_exit: 0', '---', '', '# T9-SEED')
  $gate = 'review_gate: codex {verdict:pass}'
  $list = @('acceptance:', '  - 1. an empty inline scalar reads empty. [dod arm 1]', '  - 2. CRLF and LF read identically. [dod arm 2]')
  return [ordered]@{
    # reviewed, and the set is closed: must NOT be reported
    'full'    = (($head + $gate + $list + $tail) -join "`n")
    # reviewed, no field at all: MUST be reported
    'missing' = (($head + $gate + $tail) -join "`n")
    # reviewed, key present but nothing under it: MUST be reported
    'empty'   = (($head + $gate + @('acceptance:') + $tail) -join "`n")
    # not reviewed: rule does not fire, must NOT be reported
    'nogate'  = (($head + $tail) -join "`n")
  }
}

# Declared examples for the acceptance rule. Returns findings as strings and never throws (same contract
# as Test-ScaffoldCardRuleExamples). Default = the live decision on the live parse, and must return
# nothing. Each -Variant feeds the SAME decision a deliberately wrong INPUT - one per design this repo
# considered and rejected - and must produce at least one finding. Those two variants are also the only
# arms that can fail before this function exists, which is the property L239 requires of a working RED:
# an upper-bound arm reads identically for "healthy" and "absent".
function Test-ScaffoldCardAcceptanceExamples {
  [CmdletBinding()]
  param([ValidateSet('key-presence-only', 'ignore-review-gate')][string]$Variant)
  $fx = Get-ScaffoldCardAcceptanceFixture
  $got = [ordered]@{}
  foreach ($name in @($fx.Keys)) {
    $fm = Get-FrontMatter $fx[$name]
    $gate = [bool](Get-UncommentedValue (Get-Scalar $fm 'review_gate'))
    $keyPresent = [regex]::IsMatch($fm, '(?m)^acceptance[ \t]*:')
    $count = Get-YamlListCount $fm 'acceptance'
    if ($Variant -eq 'key-presence-only') { $count = if ($keyPresent) { 1 } else { 0 } }
    if ($Variant -eq 'ignore-review-gate') { $gate = $true }
    $got[$name] = Get-ScaffoldCardAcceptanceError -HasReviewGate $gate -KeyPresent $keyPresent -ItemCount $count
  }
  if ($Variant) {
    $f = @()
    if ($Variant -eq 'key-presence-only' -and -not $got['empty']) { $f += "[ACCEPTANCE-EXAMPLE] judging by key presence instead of item count stops reporting the empty 'acceptance:' key - the key on its own closes nothing, which is the [CARD-SWEEP] judgement applied to this field." }
    if ($Variant -eq 'ignore-review-gate' -and $got['nogate']) { $f += "[ACCEPTANCE-EXAMPLE] dropping the review_gate precondition reports a card no second reviewer will read, which is noise rather than a nudge." }
    return $f
  }
  $findings = @()
  if ($got['full']) { $findings += "[ACCEPTANCE-EXAMPLE] a card carrying review_gate and a non-empty numbered acceptance list was reported: $($got['full']) [FIX] fix the rule, never the example." }
  if (-not $got['missing']) { $findings += "[ACCEPTANCE-EXAMPLE] a card carrying review_gate and NO acceptance field was not reported - that open set is exactly what rubric #6 cannot converge against. [FIX] fix the rule, never the example." }
  if ($got['missing'] -and $got['missing'] -notmatch 'CARD-ACCEPTANCE') { $findings += "[ACCEPTANCE-EXAMPLE] the missing-field finding carries no [CARD-ACCEPTANCE] sentinel, so nothing downstream can grep for it. Got: $($got['missing'])" }
  if (-not $got['empty']) { $findings += "[ACCEPTANCE-EXAMPLE] a card carrying an EMPTY acceptance key was not reported - the key on its own closes nothing. [FIX] fix the rule, never the example." }
  if ($got['nogate']) { $findings += "[ACCEPTANCE-EXAMPLE] a card with no review_gate was reported: $($got['nogate']) - the rule must fire only where a second reviewer will actually read the list." }
  return $findings
}

# ── T276-CARD-SLIM (ADR 0016 item 5): an acceptance item may CITE a requirement, and a citation that
#    resolves to nothing is refused ────────────────────────────────────────────────────────────────────
# The template stopped demanding the fields no gate reads and gained one a gate does: an optional
# `requirements:` block list of `R<n>. text` items an `acceptance:` item may name as `[R<n>]`, so the closed
# list points at a stated requirement instead of carrying its text twice.
#
# The one thing machine-checkable about that link is whether it RESOLVES. This function refuses a citation
# with no matching item and nothing else, and the boundary is the design rather than a gap to close later:
#   * A card with NO `requirements:` and no citations is valid. The field is optional, so demanding the list
#     would red every card written before it existed - the degradation `budget:` already makes.
#   * A requirement NO acceptance item cites is not reported. Whether the list is COVERED, like whether it is
#     complete, is a planning judgement - the honest limit `sweep:` and `acceptance:` both declare.
#   * Only `acceptance:` items are read. A `[R1]` in prose or inside `dod_command` is not a citation - this
#     card's own dod builds `[R7]` as a fixture, and a whole-card scan would dangle the card introducing it.
#   * BOTH LISTS ARE READ FROM THE FRONT MATTER ONLY (T282), the region between the first two `---` lines,
#     because that is the region every other card gate reads and the body is prose. A `requirements:` list
#     in the body would otherwise RESOLVE a front-matter citation - the cheapening direction, and the one a
#     card is likeliest to reach by accident, since the body is where a card restates its own fields. A text
#     carrying NO fence has no such region, so the rule says nothing about it - a card without front matter
#     is refused by the front-matter rules long before a citation could matter, and reading its whole body
#     here would make this the one rule whose scope widens exactly where a card is least well formed.
#   * AN ITEM WITH NO TEXT AFTER THE DOT IS NOT A REQUIREMENT (T282). `R1.` states nothing, so a citation to
#     it resolves against an empty statement and the review judges the acceptance item against no text -
#     exactly the state the rule exists to refuse, arriving through a declaration instead of an omission.
# The citation SHAPE is the CARD-REQ-DANGLING row of Get-ScaffoldCardRules, READ here and never copied, so
# `[dod arm 1]` and `[FOLLOW-UP]` - the bracketed tokens live acceptance lines already carry - keep their
# declared examples beside the pattern. Resolution is by the WHOLE number: `[R1]` does not resolve against an
# `R10.` item, because a prefix match would silently accept a renumbered requirement.
# Takes card TEXT so the rule is testable without a card file on disk; returns findings as strings, one per
# unresolved citation, and never throws. Rejected shapes, declared by name:
#   ignore-dangling - the citation is never resolved, so the link is decoration: a renamed or deleted
#                     requirement leaves an acceptance item pointing at nothing and nothing says so.
#   require-list    - a card carrying no requirements and citing none is reported anyway, which turns the
#                     optional field into a required one and reds every card that predates it.
#   body-scanned    - the whole card is scanned, so a list in the BODY declares requirements and resolves
#                     citations that the front matter never made good on.
#   empty-item      - `R<n>.` with nothing after the dot counts as a requirement, so a citation resolves
#                     against a statement that is not there.
function Get-ScaffoldCardRequirementFinding {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$CardText,
    [ValidateSet('live', 'ignore-dangling', 'require-list', 'body-scanned', 'empty-item')][string]$Variant = 'live'
  )
  if ([string]::IsNullOrWhiteSpace($CardText)) { return @() }
  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-REQ-DANGLING' })
  if ($rule.Count -ne 1) { throw "Get-ScaffoldCardRequirementFinding: rule CARD-REQ-DANGLING is missing from Get-ScaffoldCardRules - the declared table is the only source of the citation shape and there is no fallback copy." }
  $scope = $CardText
  $fmScope = Get-FrontMatter $CardText
  # T282: the front matter is the region every other card gate reads, so it is the region this one reads -
  # and a text carrying NO fence has no such region, so there is nothing to judge and the rule says nothing.
  # That is not a hole: a card without front matter is refused by the front-matter rules long before a
  # citation could matter, and reading its whole body here would make this the one rule whose scope depends
  # on the card being malformed. `$scope` above is the body-scanned reading, reached only by that variant.
  if ($Variant -ne 'body-scanned') { if ($null -eq $fmScope) { return @() } else { $scope = $fmScope } }
  $declared = @{}
  foreach ($item in @(Get-YamlListItems $scope 'requirements')) {
    # [regex]::Match, not -cmatch: the number is read off the match OBJECT, so nothing here depends on
    # $Matches surviving the second test below. Static .NET matching is case-sensitive, which -cmatch was.
    $mItem = [regex]::Match($item, '^R(\d+)\.')
    if (-not $mItem.Success) { continue }
    # T282: `R<n>.` with nothing after the dot states no requirement, so a citation resolving against it
    # lands on a stated nothing - the same "judged against text that is not there" a dangling citation is.
    if ($Variant -ne 'empty-item' -and $item -cnotmatch '^R\d+\.\s*\S') { continue }
    $declared[$mItem.Groups[1].Value] = $true
  }
  $findings = @()
  if ($Variant -eq 'require-list' -and $declared.Count -eq 0) {
    $findings += '[CARD-REQ-DANGLING] the card declares no requirements list at all.'
  }
  foreach ($acc in @(Get-YamlListItems $scope 'acceptance')) {
    foreach ($m in [regex]::Matches($acc, $rule[0].Pattern)) {
      $n = $m.Groups[1].Value
      if ($declared.ContainsKey($n)) { continue }
      if ($Variant -eq 'ignore-dangling') { continue }
      $findings += "[CARD-REQ-DANGLING] an acceptance item cites '$($m.Value)' while this card declares no 'requirements:' item numbered R$($n) - the item cited is '$acc'. A citation that resolves to nothing reads as a closed link and is not one - the requirement was renumbered, renamed, or never written, and the review then judges the item against text that is not there. [FIX] add the 'R$n. <text>' item under 'requirements:' IN THE FRONT MATTER and with text after the dot - a list in the body is prose and an item stating nothing is not a requirement - or point the citation at a number the card carries. The field itself stays optional: a card that declares no requirements and cites none is valid, and nothing here asks whether the list is complete - that is a planning judgement, like the closed acceptance list itself."
    }
  }
  return @($findings)
}

# Declared examples for the citation rule. Returns findings as strings and never throws; empty is green.
# Hermetic - every case is literal card text, so nothing is read from disk. Default = the live rule, which
# must report nothing; each -Variant runs the SAME cases through one rejected shape and must produce at
# least one finding, which is the contract sub-gate 10u asserts both ways.
function Test-ScaffoldCardRequirementExamples {
  [CmdletBinding()]
  param([ValidateSet('ignore-dangling', 'require-list', 'body-scanned', 'empty-item')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $nl = [string][char]10
  $findings = @()
  # 1. THE HEALTHY LINK: a declared requirement, an acceptance item citing it. Nothing to report.
  $c1 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. the guard refuses a dangling citation$nl  - R2. the field stays optional$nl" + "acceptance:$nl  - 1. the guard refuses it. [R1]$nl" + "---$nl"))
  if ($c1.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] a citation that RESOLVES was reported: $($c1 -join ' ; '). The rule refuses a link that goes nowhere, never one that lands. [FIX] fix the rule, never the example." }
  # 2. THE DANGLING CITATION - the whole rule, and the shape 'ignore-dangling' drops.
  $c2 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. the guard refuses a dangling citation$nl" + "acceptance:$nl  - 1. done. [R7]$nl" + "---$nl"))
  if ($c2.Count -lt 1) { $findings += '[CARD-REQ-EXAMPLE] an acceptance item citing [R7] on a card whose only requirement is R1 produced no finding, so the citation is decoration: a renumbered or deleted requirement leaves the acceptance list pointing at nothing and nothing says so. [FIX] fix the rule, never the example.' }
  elseif ($c2[0] -notmatch 'CARD-REQ-DANGLING') { $findings += "[CARD-REQ-EXAMPLE] the dangling-citation finding carries no [CARD-REQ-DANGLING] sentinel, so nothing downstream can grep for it (L165: a bare finding is not evidence). Got: $($c2[0])" }
  # 3. NO LIST AND NO CITATION IS VALID - the case 'require-list' breaks. The field is optional by design,
  #    so a rule demanding it would red every card written before it existed, mid-acceptance.
  $c3 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "acceptance:$nl  - 1. done. [dod arm 1]$nl" + "---$nl"))
  if ($c3.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] a card declaring no requirements and citing none was reported: $($c3 -join ' ; '). Optional means absent is silent - the degradation 'budget:' already makes. [FIX] fix the rule, never the example." }
  # 4. THE BRACKETED TOKENS ACCEPTANCE LINES ALREADY CARRY are not citations. A rule keyed on brackets
  #    alone reads both of these as requirements and reports every card in the corpus.
  $c4 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. thing$nl" + "acceptance:$nl  - 1. done. [dod arm 1]$nl  - 2. the gap outside the list. [FOLLOW-UP]$nl" + "---$nl"))
  if ($c4.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] '[dod arm 1]' or '[FOLLOW-UP]' was read as a requirement citation: $($c4 -join ' ; '). Both are ordinary acceptance-line text and both are declared in the CARD-REQ-DANGLING row's own ShouldNot list. [FIX] fix the rule, never the example." }
  # 5. RESOLUTION IS BY THE WHOLE NUMBER: [R1] must not resolve against an R10 item. Prefix matching would
  #    accept exactly the renumbering this rule exists to catch.
  $c5 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R10. the tenth$nl" + "acceptance:$nl  - 1. done. [R1]$nl" + "---$nl"))
  if ($c5.Count -lt 1) { $findings += '[CARD-REQ-EXAMPLE] a citation of [R1] resolved against an R10 item, so the numbers are compared as prefixes. Renumbering a requirement would then leave every citation looking resolved. [FIX] fix the rule, never the example.' }
  # 6. ONE FINDING PER CITATION, so the message names each one rather than the first. A card fixing only
  #    what the failure text printed would otherwise ship the second dangling citation.
  $c6 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. thing$nl" + "acceptance:$nl  - 1. done. [R7]$nl  - 2. done. [R8]$nl" + "---$nl"))
  if ($c6.Count -ne 2) { $findings += "[CARD-REQ-EXAMPLE] two dangling citations produced $($c6.Count) finding(s), expected 2 - the rule reports per citation, so a card repairing only the one that was printed would ship the other. [FIX] fix the rule, never the example." }
  # 7. A CITATION OUTSIDE THE ACCEPTANCE LIST IS NOT ONE. This card's own dod_command builds '[R7]' as a
  #    fixture, so a whole-card scan would make the card introducing the rule dangle under its own rule.
  $c7 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. thing$nl" + "dod_assert: the fixture text [R7] is not a citation$nl" + "acceptance:$nl  - 1. done. [R1]$nl" + "---$nl"))
  if ($c7.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] a bracketed R-number OUTSIDE the acceptance list was read as a citation: $($c7 -join ' ; '). Only acceptance items cite requirements; card prose and dod text carry the same characters for other reasons. [FIX] fix the rule, never the example." }
  # -- T282: the two readings T276's advisory review measured. Every fixture above is FENCED, because the
  #    rule reads the front matter and a fixture that skips the fence would exercise a region no card has. --
  # 8. A `requirements:` LIST IN THE BODY DOES NOT RESOLVE A FRONT-MATTER CITATION. The body is where a card
  #    restates its own fields in prose, so this is the accident a card reaches by writing, not by scheming -
  #    and it resolves in the CHEAPENING direction, which is the one that must not be loose.
  $c8 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "acceptance:$nl  - 1. done. [R1]$nl" + "---$nl" + "requirements:$nl  - R1. thing$nl"))
  if ($c8.Count -lt 1) { $findings += '[CARD-REQ-EXAMPLE] a front-matter citation of [R1] was RESOLVED by a requirements list sitting in the card BODY, so a card can satisfy the guard with prose no other card gate reads. Both lists are read from the front matter, the region between the first two --- lines. [FIX] fix the rule, never the example.' }
  # 9. AN ITEM WITH NO TEXT AFTER THE DOT IS NOT A REQUIREMENT. `R1.` declares a number and states nothing,
  #    so a citation to it lands on an empty statement - the same "judged against text that is not there"
  #    the dangling citation produces, arriving as a declaration rather than as an omission.
  $c9 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1.$nl" + "acceptance:$nl  - 1. done. [R1]$nl" + "---$nl"))
  if ($c9.Count -lt 1) { $findings += '[CARD-REQ-EXAMPLE] a citation of [R1] resolved against an empty requirement item (`R1.` with nothing after the dot), so the link lands on a statement that is not there and the review has nothing to judge the acceptance item against. [FIX] fix the rule, never the example.' }
  # 10. AND THE SCOPE MUST NOT BECOME "report the body too": a second acceptance list below the fence is
  #     prose, and reading it would red the cards that restate their acceptance in the body - most of them.
  $c10 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("---$nl" + "requirements:$nl  - R1. thing$nl" + "acceptance:$nl  - 1. done. [R1]$nl" + "---$nl" + "acceptance:$nl  - 9. prose [R9]$nl"))
  if ($c10.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] a bracketed R-number in a BODY acceptance list was reported as a dangling citation: $($c10 -join ' ; '). Scoping the read to the front matter narrows the rule in both directions - the body neither resolves a citation nor raises one. [FIX] fix the rule, never the example." }
  # 11. NO FENCE AT ALL, the boundary the scope needs stated rather than inferred: there is no front matter,
  #     so there is no region this rule is defined on and it says nothing. Reading the whole text instead
  #     would make the rule's SCOPE depend on the card being malformed - widest exactly where a card is
  #     least well formed - and a card with no front matter is already refused by the front-matter rules.
  $c11 = @(Get-ScaffoldCardRequirementFinding -Variant $v -CardText ("requirements:$nl  - R1. thing$nl" + "acceptance:$nl  - 1. done. [R7]$nl"))
  if ($c11.Count -ne 0) { $findings += "[CARD-REQ-EXAMPLE] a card text carrying NO front-matter fence was scanned anyway and reported: $($c11 -join ' ; '). The rule is defined on the region between the first two --- lines; with no such region it has nothing to judge. [FIX] fix the rule, never the example." }
  return @($findings)
}

# ── T285-SPEC-ARBITRATION: the recorded ruling that ends a maker-checker impasse ──────────────────────
# CLAUDE.md's execution boundary already ends an impasse - two rounds unresolved, a person rules - and until
# T285 nothing in the ship could READ that ruling: on T279 this block came back on eight consecutive rounds
# with every deterministic gate green. The FIELD CONTRACT is stated once, in specs/README.md's field table;
# what lives here is the judgement and the three things about it a reader cannot infer from the rules.
#
# ONE, WHAT IS CHECKED IS SHAPE, NEVER JUDGEMENT. Whether a ruling is right is a person's call. The four
# shape rules each close a way a record could read as a ruling and work as a bypass, and the finding text
# below is where each one says so - a second copy here would be the pair that goes stale.
#
# TWO, FRONT MATTER ONLY, through the shared Get-FrontMatter (its BOM handling is why that parser is shared,
# TD130). Card bodies discuss arbitration in prose - this card's own does - and a whole-text scan would read
# an example as a ruling.
#
# THREE, IT RETURNS OBJECTS AND NEVER THROWS, because two consumers need different halves of one read:
# check-cards blocks on any entry whose `Finding` is non-empty, and the ship skips exactly those and matches
# the rest against the reviewed sha. Takes card TEXT, so both are testable without a card file on disk.
# Rejected shapes, declared by name:
#   unbound-sha - the sha is never checked, so a ruling can be bound to `HEAD`, to a ref, or to nothing at
#                 all: a blanket waiver wearing the shape of an arbitration.
#   first-round - the round floor is dropped, so a ruling may be recorded before the impasse it settles.
#   no-reason   - an entry with no stated reason is accepted, so a use nobody explained looks well formed.
function Get-ScaffoldCardArbitration {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$CardText,
    [ValidateSet('live', 'unbound-sha', 'first-round', 'no-reason')][string]$Variant = 'live'
  )
  if ([string]::IsNullOrWhiteSpace($CardText)) { return @() }
  $fm = Get-FrontMatter $CardText
  if ([string]::IsNullOrWhiteSpace($fm)) { return @() }
  $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-ARBITRATION' })
  if ($rule.Count -ne 1) { throw "Get-ScaffoldCardArbitration: rule CARD-ARBITRATION is missing from Get-ScaffoldCardRules - the declared table is the only source of the sha shape and there is no fallback copy." }
  # A value that is NOTHING BUT a YAML comment is an ABSENT value: `by: # nobody` names nobody. Two things
  # this deliberately does NOT do. It does not strip a `#` from INSIDE a value - a reason quoting rubric
  # dimension #6 is the common case, and a blanket strip would silently shorten the one field this record
  # exists to keep. And it decides comment-or-not on the RAW value, BEFORE unquoting, because `by: "#team"`
  # is a quoted scalar whose first character only looks like a comment; deciding after the unquote would
  # reject a legal name and a legal reason.
  $arbValue = { param($t) $v = ([string]$t).Trim(); if ($v -match '^#') { return '' }; ($v.Trim('"').Trim("'")).Trim() }
  # One block-list walk, terminating on any unindented line exactly as Get-YamlBlockListItems does (TD112).
  # A `- key: value` line opens an entry, an indented `key: value` line adds to the one open; anything else
  # inside the block is ignored rather than guessed at.
  $parsed = [System.Collections.Generic.List[object]]::new()
  $cur = $null
  $in = $false
  foreach ($ln in ($fm -split '\r?\n')) {
    if (-not $in) { if ($ln -match '^arbitration\s*:') { $in = $true }; continue }
    if ($ln -match '^\S') { break }
    if ($ln -match '^\s+-\s*(.*)$') {
      if ($null -ne $cur) { $parsed.Add($cur) }
      $cur = @{}
      if ($Matches[1] -match '^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.*)$') { $cur[$Matches[1]] = (& $arbValue $Matches[2]) }
    }
    elseif (($null -ne $cur) -and ($ln -match '^\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.*)$')) {
      $cur[$Matches[1]] = (& $arbValue $Matches[2])
    }
  }
  if ($null -ne $cur) { $parsed.Add($cur) }
  # A KEY THAT IS PRESENT AND YIELDS NOTHING is the in-between state, not an absent field: `arbitration: maker`
  # (a scalar where a list belongs) or a block whose items never parse would otherwise read as governed to a
  # human and as undeclared to every gate - what [CARD-BUDGET] and [CARD-TIER-BADVALUE] already refuse. Absent
  # stays silent; present-and-unreadable is reported, on an entry carrying no values so the ship skips it.
  if ($in -and $parsed.Count -eq 0) {
    return @([pscustomobject]@{
      Sha = ''; Rounds = 0; Ruling = ''; By = ''; Reason = ''
      Finding = "[CARD-ARBITRATION] the card declares an 'arbitration:' key with no readable entry under it. The field is a BLOCK LIST of maps - '  - sha: <40 hex>' followed by indented 'rounds:', 'ruling:', 'by:' and 'reason:' lines - and a scalar, an inline list, or an empty block declares a ruling to a reader while declaring nothing to the ship. [FIX] write the entry as a block list item, or delete the key: absence is legal and silent."
    })
  }
  $entries = @()
  foreach ($e in $parsed) {
    $sha = if ($e.ContainsKey('sha')) { [string]$e['sha'] } else { '' }
    $roundsText = if ($e.ContainsKey('rounds')) { [string]$e['rounds'] } else { '' }
    $ruling = if ($e.ContainsKey('ruling')) { [string]$e['ruling'] } else { '' }
    $by = if ($e.ContainsKey('by')) { [string]$e['by'] } else { '' }
    $reason = if ($e.ContainsKey('reason')) { [string]$e['reason'] } else { '' }
    $rounds = 0
    # Bounded digits, not `^\d+$`: this function's contract is that it NEVER throws (check-cards calls it
    # unguarded, under StrictMode with $ErrorActionPreference='Stop'), and `[int]'2147483648'` throws. A
    # number no round counter could ever reach is refused as unreadable, which is the finding below.
    if ($roundsText -match '^\d{1,9}$') { $rounds = [int]$roundsText }
    $f = @()
    if (($Variant -ne 'unbound-sha') -and ($sha -cnotmatch $rule[0].Pattern)) {
      $f += "the entry binds its ruling to '$sha', which is not a full 40-character lowercase git object name. An arbitration covers exactly one reviewed verdict; a ruling bound to an abbreviated sha, to a ref, or to nothing is a blanket waiver, and the next round would inherit it. [FIX] paste the sha the review reported for the round being arbitrated (it is in .review/<branch>.json and in the [R3-RECORD] line), lowercase and in full."
    }
    if (($Variant -ne 'first-round') -and ($rounds -lt 2)) {
      $f += "the entry records rounds='$roundsText', which is not a plain whole number of at least 2. Two consecutive spec-block rounds are what make an impasse: the first block is the reviewer's turn and the second is the maker's rebuttal, so a ruling before those is a pre-emptive waiver rather than an arbitration. [FIX] write the number of rounds as digits, at least 2, or drop the entry. The ship counts the rounds itself from the review records and this number never substitutes for that count."
    }
    if ($ruling -cnotin @('maker', 'checker')) {
      $f += "the entry records ruling='$ruling', which is neither 'maker' nor 'checker' (lowercase). A ruling outside the enum reads as a decision to a human and as nothing to the ship. [FIX] write 'maker' to make the spec block advisory for this one verdict, or 'checker' to record that the block stands."
    }
    if ([string]::IsNullOrWhiteSpace($by)) {
      $f += "the entry names nobody in 'by'. An unattributed ruling is indistinguishable from a bypass after the fact, and every use of this field is meant to be countable. [FIX] name who ruled."
    }
    if (($Variant -ne 'no-reason') -and [string]::IsNullOrWhiteSpace($reason)) {
      $f += "the entry states no 'reason'. The reason is the whole record: it is what a later reader has instead of the round it replaced. [FIX] state why the impasse was ruled this way."
    }
    $entries += [pscustomobject]@{
      Sha     = $sha
      Rounds  = $rounds
      Ruling  = $ruling
      By      = $by
      Reason  = $reason
      Finding = if ($f.Count) { '[CARD-ARBITRATION] ' + ($f -join ' ') } else { '' }
    }
  }
  return @($entries)
}

# Declared examples for the arbitration entry. Returns findings as strings and never throws; empty is green.
# Hermetic - every case is literal card text, so nothing is read from disk. Default = the live rule, which
# must report nothing; each -Variant runs the SAME cases through one rejected shape and must produce at
# least one finding, which is the contract sub-gate 10u asserts both ways.
function Test-ScaffoldCardArbitrationExamples {
  [CmdletBinding()]
  param([ValidateSet('unbound-sha', 'first-round', 'no-reason')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $nl = [string][char]10
  $good = '0123456789abcdef0123456789abcdef01234567'
  $findings = @()
  # 1. THE HEALTHY RULING, and the fields actually read back. A count alone would stay green against a
  #    parser that returned an empty entry for every list item.
  $c1 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: maker$nl    by: arc-owner$nl    reason: the three asks are outside the closed list$nl" + "---$nl"))
  if ($c1.Count -ne 1) { $findings += "[CARD-ARB-EXAMPLE] a front matter carrying one well-formed arbitration entry parsed to $($c1.Count) entry(ies), expected 1 - the block-list walk is not reading the field at all. [FIX] fix the parser, never the example." }
  elseif ($c1[0].Finding) { $findings += "[CARD-ARB-EXAMPLE] a well-formed ruling was reported as malformed: $($c1[0].Finding). The rule refuses a record that cannot bind, never one that does. [FIX] fix the rule, never the example." }
  elseif (($c1[0].Sha -cne $good) -or ($c1[0].Rounds -ne 2) -or ($c1[0].Ruling -cne 'maker') -or ($c1[0].By -cne 'arc-owner') -or ($c1[0].Reason -notmatch 'closed list')) {
    $findings += "[CARD-ARB-EXAMPLE] the parsed entry does not carry what the card declared (sha='$($c1[0].Sha)' rounds='$($c1[0].Rounds)' ruling='$($c1[0].Ruling)' by='$($c1[0].By)' reason='$($c1[0].Reason)') - the ship matches on these values, so a shape that validates and reads back wrong is worse than one that fails. [FIX] fix the parser, never the example."
  }
  # 2. A RULING BOUND TO NO REVIEWED VERDICT - the whole anti-bypass property, and the shape 'unbound-sha'
  #    drops. An abbreviated sha is the form a hand-written entry naturally reaches for.
  $c2 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: 43c6d18$nl    rounds: 2$nl    ruling: maker$nl    by: arc-owner$nl    reason: the asks are outside the closed list$nl" + "---$nl"))
  if (($c2.Finding -join '').Length -lt 1) { $findings += '[CARD-ARB-EXAMPLE] a ruling bound to an abbreviated sha produced no finding, so the binding is decoration: the entry would never match a verdict, or worse, a later widening would let it cover every round on the card. [FIX] fix the rule, never the example.' }
  elseif ($c2[0].Finding -notmatch 'CARD-ARBITRATION') { $findings += "[CARD-ARB-EXAMPLE] the malformed-entry finding carries no [CARD-ARBITRATION] sentinel, so nothing downstream can grep for it (L165: a bare finding is not evidence). Got: $($c2[0].Finding)" }
  # 3. A RULING RECORDED BEFORE THE IMPASSE - the shape 'first-round' drops. One block is the reviewer's
  #    turn; ruling on it converts the first disagreement on every Tier-S card into a waiver.
  $c3 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 1$nl    ruling: maker$nl    by: arc-owner$nl    reason: too early$nl" + "---$nl"))
  if (($c3.Finding -join '').Length -lt 1) { $findings += '[CARD-ARB-EXAMPLE] a ruling recorded at one round produced no finding, so the two-round floor CLAUDE.md states is not enforced anywhere and the first block on any Tier-S card can be waived. [FIX] fix the rule, never the example.' }
  # 4. A RULING NOBODY EXPLAINED - the shape 'no-reason' drops. The reason is what a later reader has
  #    instead of the round it replaced.
  $c4 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: maker$nl    by: arc-owner$nl    reason:$nl" + "---$nl"))
  if (($c4.Finding -join '').Length -lt 1) { $findings += '[CARD-ARB-EXAMPLE] an entry with an empty reason was accepted, so a waiver can be recorded with nothing said about why - which is the shape this field exists to make impossible. [FIX] fix the rule, never the example.' }
  # 5. A RULING OUTSIDE THE ENUM. No rejected shape drops this one: a value the ship cannot read is the
  #    in-between state [CARD-BUDGET] and [CARD-TIER-BADVALUE] already refuse - governed to a human, absent
  #    to every gate.
  $c5 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: reviewer$nl    by: arc-owner$nl    reason: the asks are outside the closed list$nl" + "---$nl"))
  if (($c5.Finding -join '').Length -lt 1) { $findings += "[CARD-ARB-EXAMPLE] ruling='reviewer' was accepted - a value outside {maker, checker} reads as a decision to a human and as nothing to the ship. [FIX] fix the rule, never the example." }
  # 6. AN UNATTRIBUTED RULING. Same class as 5, on the other required free-text field.
  $c6 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: checker$nl    by:$nl    reason: the block stands$nl" + "---$nl"))
  if (($c6.Finding -join '').Length -lt 1) { $findings += '[CARD-ARB-EXAMPLE] an entry naming nobody in `by` was accepted, so a use of this field cannot be attributed after the fact. [FIX] fix the rule, never the example.' }
  # 7. ABSENCE IS SILENT. The field is optional, so a rule reporting on its absence would red every card
  #    written before it existed - mid-acceptance, which is when cards are in flight here.
  $c7 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "id: T1-NOARB$nl" + "status: todo$nl" + "---$nl"))
  if ($c7.Count -ne 0) { $findings += "[CARD-ARB-EXAMPLE] a card declaring no arbitration parsed $($c7.Count) entry(ies): $(@($c7 | ForEach-Object { $_.Finding }) -join ' ; '). Optional means absent is silent. [FIX] fix the rule, never the example." }
  # 8. FRONT MATTER ONLY. Card bodies discuss arbitration in prose - this rule's own card does - and a
  #    whole-text scan would read the example as a ruling and hand the ship a waiver nobody wrote.
  $c8 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "id: T1-BODYARB$nl" + "---$nl" + "$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 9$nl    ruling: maker$nl    by: the body$nl    reason: prose, not a ruling$nl"))
  if ($c8.Count -ne 0) { $findings += "[CARD-ARB-EXAMPLE] an 'arbitration:' block in the card BODY was read as a ruling ($($c8.Count) entry(ies)) - prose describing the field would then arbitrate a real ship. [FIX] fix the rule, never the example." }
  # 9. ONE OBJECT PER ENTRY, so a list carrying one good and one broken ruling reports the broken one and
  #    still hands the ship the good one. A parser stopping at the first entry would ship the second silently.
  $c9 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: maker$nl    by: arc-owner$nl    reason: first ruling$nl  - sha: HEAD$nl    rounds: 2$nl    ruling: maker$nl    by: arc-owner$nl    reason: second ruling$nl" + "---$nl"))
  if ($c9.Count -ne 2) { $findings += "[CARD-ARB-EXAMPLE] a two-entry arbitration list parsed to $($c9.Count) entry(ies), expected 2 - a list read as one item hides every ruling after the first. [FIX] fix the parser, never the example." }
  elseif ($v -eq 'live') {
    if ($c9[0].Finding) { $findings += "[CARD-ARB-EXAMPLE] the FIRST of two entries was reported although it is well formed: $($c9[0].Finding). Findings are per entry, so one broken ruling must not condemn the one beside it. [FIX] fix the rule, never the example." }
    if (-not $c9[1].Finding) { $findings += '[CARD-ARB-EXAMPLE] the SECOND of two entries (bound to `HEAD`) was not reported, so only the first entry in a list is judged and every later one is unchecked. [FIX] fix the rule, never the example.' }
  }
  # 10. A VALUE THAT IS ONLY A YAML COMMENT IS ABSENT. `by: # nobody` and `reason: # omitted` read as filled
  #     to anyone scanning the card and as nothing to YAML; accepting them lets an unattributed, unexplained
  #     waiver look well formed - the exact pair rules 5 and 6 exist to refuse.
  $c10 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: maker$nl    by: # nobody$nl    reason: # omitted$nl" + "---$nl"))
  if (($c10.Finding -join '').Length -lt 1) { $findings += "[CARD-ARB-EXAMPLE] an entry whose 'by' and 'reason' are nothing but YAML comments was accepted, so a ruling can be recorded with no author and no stated reason while looking filled in. [FIX] fix the rule, never the example." }
  # 11. A QUOTED VALUE THAT OPENS ON A HASH IS NOT A COMMENT. `by: "#team"` and `reason: "#6 was rejected"`
  #     are legal YAML scalars, and a rule that unquoted before looking would call both of them empty and
  #     refuse a card for saying something ordinary.
  $c11q = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration:$nl  - sha: $good$nl    rounds: 2$nl    ruling: maker$nl    by: ""#team""$nl    reason: ""#6 was rejected as out of scope""$nl" + "---$nl"))
  if ($c11q.Count -ne 1) { $findings += "[CARD-ARB-EXAMPLE] an entry whose by and reason are quoted scalars opening on a hash parsed to $($c11q.Count) entry(ies), expected 1. [FIX] fix the parser, never the example." }
  elseif ($c11q[0].Finding) { $findings += "[CARD-ARB-EXAMPLE] a quoted value opening on a hash was read as a YAML comment and reported empty: $($c11q[0].Finding). '#team' and '#6 was rejected' are ordinary text; only a value that is nothing but a comment is absent. [FIX] fix the rule, never the example." }
  elseif (($c11q[0].By -cne '#team') -or ($c11q[0].Reason -notmatch '^#6 was rejected')) { $findings += "[CARD-ARB-EXAMPLE] the quoted values did not survive unquoting (by='$($c11q[0].By)' reason='$($c11q[0].Reason)'). [FIX] fix the parser, never the example." }
  # 12. A PRESENT KEY THAT YIELDS NO ENTRY IS REPORTED, not read as absent. `arbitration: maker` is a scalar
  #     where a block list belongs: it declares a ruling to a reader and nothing to the ship, which is the
  #     in-between state [CARD-BUDGET] and [CARD-TIER-BADVALUE] already refuse.
  $c11 = @(Get-ScaffoldCardArbitration -Variant $v -CardText ("---$nl" + "arbitration: maker$nl" + "status: todo$nl" + "---$nl"))
  if ($c11.Count -ne 1) { $findings += "[CARD-ARB-EXAMPLE] 'arbitration: maker' (a scalar where a block list belongs) parsed to $($c11.Count) entry(ies), expected exactly one carrying a finding - read as absent, a malformed governance field is silently ungoverned. [FIX] fix the rule, never the example." }
  elseif (-not $c11[0].Finding) { $findings += '[CARD-ARB-EXAMPLE] a present but unreadable `arbitration:` key produced an entry with NO finding, so it would reach the ship as a ruling carrying no sha - and be skipped there in silence. [FIX] fix the rule, never the example.' }
  return @($findings)
}

# ── T174-CARD-PATH-EXISTS: an allow_paths entry that does not resolve AND sits one small edit from a
#    path that does ──────────────────────────────────────────────────────────────────────────────────
# check-cards validated the SHAPE of allow_paths (key present, block list well formed, count against the
# sweep field) and never RESOLVED an entry, so a path that was never there passed every guard at
# card-creation time. The scope gate then judged the diff against a list carrying a dead entry and the
# defect surfaced as a confusing scope verdict instead of "this card names a file that does not exist".
# Two instances, both caught by hand at implementation time (TD166): T158 declared scripts/init-scaffold.ps1
# while that script lives at the repo root, and T153 declared docs/adr/0013-no-trajectory-or-replay-view.md
# against an actual 0013-no-trajectory-replay-view.md.
#
# WHY NOT A BARE EXISTENCE RULE. A card is written BEFORE the files it creates exist - most often its own
# specs/mutations/<id>.psd1 and <id>-results.tsv. Measured over the live corpus: a bare existence rule
# rejects 12 of the last 14 archived cards at -Phase start. What the two recorded defects share is not
# "missing" but "missing, and one edit from something real", and that is the shape this rule keys on.
#
# THE DISTANCE IS ABSOLUTE (<= 3), NEVER PROPORTIONAL. Simulating all 36 self-created mutation-registry
# entries as if at -Phase start: a 20%-of-basename-length threshold produces 2 false positives
# (T73-TD120-SHIP-CODES and T75-TD120-WAVE2-CODES flag each other at distance 6), <= 2 and <= 1 miss the
# T153 defect, and <= 3 is the only candidate catching both recorded defects with zero false positives.
# A proportional threshold scales its tolerance with name length, and this repo's longest entry names are
# exactly the registries that legitimately do not exist yet - which is what makes it the wrong shape here.
#
# A DIRECTORY FORM COUNTS AS RESOLVED. 31 archived entries are directory forms (specs/mutations/,
# docs/adr/, specs/archive/), and a plain file-list membership test calls every one of them missing -
# that alone inflates the corpus-wide nonexistent count from 90 to 121. The caller therefore passes the
# tree as a LIST containing files AND the directories they imply, which keeps this decision pure and
# lets the examples run against a synthetic tree without touching disk.
#
# THE ARCHIVE IS NEVER SWEPT, and that is the CALLER's job: check-cards enumerates specs/tasks only. The
# reason is pinned as an example rather than left to this comment - an archived card's own
# specs/tasks/<id>.md is offending BY DESIGN, dead precisely because the card was archived, and 79 of the
# 90 nonexistent archived entries have that shape.

# Levenshtein edit distance, two-row form. Used only on basenames, so the allocation is bounded by the
# longest file name in the tree rather than by the tree itself.
function Get-ScaffoldPathEditDistance {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$A,
    [Parameter(Mandatory)][AllowEmptyString()][string]$B
  )
  $n = $A.Length; $m = $B.Length
  if ($n -eq 0) { return $m }
  if ($m -eq 0) { return $n }
  $prev = New-Object 'int[]' ($m + 1)
  $cur = New-Object 'int[]' ($m + 1)
  for ($j = 0; $j -le $m; $j++) { $prev[$j] = $j }
  for ($i = 1; $i -le $n; $i++) {
    $cur[0] = $i
    $ca = $A[$i - 1]
    for ($j = 1; $j -le $m; $j++) {
      $cost = if ($ca -eq $B[$j - 1]) { 0 } else { 1 }
      $del = $prev[$j] + 1
      $ins = $cur[$j - 1] + 1
      $sub = $prev[$j - 1] + $cost
      $cur[$j] = [Math]::Min([Math]::Min($del, $ins), $sub)
    }
    $swap = $prev; $prev = $cur; $cur = $swap
  }
  return $prev[$m]
}

# One spelling for a repo-relative path: forward slashes, no leading './', no trailing '/'. Applied to
# BOTH sides (declared entry and tree member) so 'specs\mutations\' and 'specs/mutations' are one path.
function Get-ScaffoldNormalizedPath {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)
  if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
  $p = ($Path -replace '\\', '/').Trim()
  while ($p.StartsWith('./')) { $p = $p.Substring(2) }
  return $p.TrimEnd('/')
}

# The decision, one record per declared entry. Takes card TEXT and the tree as a LIST, so it resolves
# nothing itself and the examples stay hermetic (same contract as Test-ScaffoldCardSweep taking text).
# Returns records rather than strings so the examples can assert on RESOLUTION separately from the
# offending verdict - which is what lets 'ignore-comment-strip' be proven without a contrived fixture.
# Rejected shapes, declared by name rather than left to a comment:
#   bare-existence       - drop the near-neighbour requirement and reject every unresolved entry.
#   ignore-comment-strip - resolve the entry as declared, keeping a trailing '# ...' comment.
function Get-ScaffoldCardPathNearMissVia($CardText, $TreePaths, $Variant) {
  $maxEdits = 3
  $fm = Get-FrontMatter $CardText
  if ($null -eq $fm) { return @() }        # no front matter: check-cards already errors on that

  # Get-YamlListItems strips a trailing '# ...' comment through Get-UncommentedValue. That strip is
  # load-bearing HERE in a way it is not for any other consumer: every other card rule counts entries or
  # pattern-matches them, while this one RESOLVES them and BLOCKS the card when resolution fails, so an
  # un-stripped entry turns a perfectly good card red. The variant re-reads the same list without the
  # strip, which is how the examples prove that rather than asserting it.
  $entries = if ($Variant -eq 'ignore-comment-strip') {
    $raw = @(); $inList = $false
    foreach ($ln in ($fm -split '\r?\n')) {
      if (-not $inList) { if ($ln -match '^allow_paths\s*:') { $inList = $true }; continue }
      if ($ln -match '^\S') { break }
      if ($ln -match '^\s*-\s+(.+)$') { $raw += $Matches[1].Trim().Trim('"').Trim("'") }
    }
    $raw
  } else {
    @(Get-YamlListItems $fm 'allow_paths')
  }

  # Index the tree once per card: exact membership, basename -> paths, directory -> basenames.
  $exact = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  $byBase = @{}
  $byDir = @{}
  foreach ($t in @($TreePaths)) {
    $n = Get-ScaffoldNormalizedPath ([string]$t)
    if (-not $n) { continue }
    [void]$exact.Add($n)
    $cut = $n.LastIndexOf('/')
    $base = if ($cut -ge 0) { $n.Substring($cut + 1) } else { $n }
    $dir = if ($cut -ge 0) { $n.Substring(0, $cut) } else { '' }
    if (-not $byBase.ContainsKey($base)) { $byBase[$base] = New-Object System.Collections.ArrayList }
    [void]$byBase[$base].Add($n)
    if (-not $byDir.ContainsKey($dir)) { $byDir[$dir] = New-Object System.Collections.ArrayList }
    [void]$byDir[$dir].Add($base)
  }

  $decisions = @()
  foreach ($item in @($entries)) {
    $declared = [string]$item
    $norm = Get-ScaffoldNormalizedPath $declared
    if (-not $norm) { continue }
    # A pattern is not a path: this card deliberately does not expand one (non_goals).
    $isGlob = $norm -match '[\*\?\[]'
    $resolved = $exact.Contains($norm)
    $neighbour = $null
    if (-not $isGlob -and -not $resolved) {
      $cut = $norm.LastIndexOf('/')
      $base = if ($cut -ge 0) { $norm.Substring($cut + 1) } else { $norm }
      $dir = if ($cut -ge 0) { $norm.Substring(0, $cut) } else { '' }
      # The basename must be UNIQUE in the tree to count as a neighbour. A name that occurs many times
      # carries no information about where the author meant: measured on this repo, README.md occurs 14
      # times and SKILL.md 17, while init-scaffold.ps1 - the T158 defect - occurs exactly once. Without
      # this condition a card legitimately declaring a root README.md it is about to CREATE gets flagged
      # against fourteen unrelated neighbours, which is what took selftest gate 15's e2e fixture red.
      if ($byBase.ContainsKey($base) -and $byBase[$base].Count -eq 1) {
        $neighbour = "$($byBase[$base][0]) (same file name, and that name is unique in the tree)"
      } elseif ($byDir.ContainsKey($dir)) {
        # Report the CLOSEST sibling, not the first one under the ceiling - the finding names what the
        # author most likely meant, and a nearer candidate is strictly better evidence.
        $best = $null; $bestD = [int]::MaxValue
        foreach ($sib in $byDir[$dir]) {
          $d = Get-ScaffoldPathEditDistance $base $sib
          if ($d -lt $bestD) { $bestD = $d; $best = $sib }
        }
        if ($null -ne $best -and $bestD -le $maxEdits) {
          $where = if ($dir) { "$dir/$best" } else { $best }
          $plural = if ($bestD -eq 1) { 'edit' } else { 'edits' }
          $neighbour = "$where (sibling in the same directory, $bestD character $plural away)"
        }
      }
    }
    $offending =
      if ($isGlob) { $false }
      elseif ($Variant -eq 'bare-existence') { -not $resolved }
      else { (-not $resolved) -and ($null -ne $neighbour) }
    $decisions += [pscustomobject]@{
      Declared = $declared; Entry = $norm; IsGlob = $isGlob
      Resolved = $resolved; Neighbour = $neighbour; Offending = $offending
    }
  }
  return $decisions
}

# The live decision. Takes card TEXT and the tree list; returns findings as strings and never throws.
# check-cards.ps1 prints them as ERRORS: an entry that names nothing, one edit from something real, is a
# defect in the card, and forbid #4 of T174 says the card is what gets fixed.
function Test-ScaffoldCardPathNearMiss {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Text,
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$TreePaths
  )
  $findings = @()
  foreach ($d in @(Get-ScaffoldCardPathNearMissVia $Text $TreePaths $null)) {
    if (-not $d.Offending) { continue }
    $findings += "[CARD-PATH-NEARMISS] allow_paths entry '$($d.Declared)' does not exist, and the tree holds $($d.Neighbour). An entry the card intends to CREATE is silent by design - what is reported here is a path that names nothing while something one small edit away does exist, which is the shape both recorded defects had (TD166). This rule cannot tell a typo from a deliberate near-name; it reports the neighbour it found and stops there. [FIX] correct the entry to the neighbour if that is what was meant, or rename the file you intend to create so it is not a near-miss of an existing one."
  }
  return $findings
}

# Fixture tree for the declared examples: files AND directories, mirroring the real corpus shapes the
# rule has to separate. Nothing is written or resolved - it is a list of strings.
function Get-ScaffoldCardPathNearMissTree {
  return @(
    'init-scaffold.ps1'
    'scripts', 'scripts/task.ps1', 'scripts/selftest.ps1', 'scripts/check-cards.ps1', 'scripts/_cards.ps1'
    'docs', 'docs/adr', 'docs/adr/README.md', 'docs/adr/0013-no-trajectory-replay-view.md'
    'specs', 'specs/README.md'
    'specs/mutations', 'specs/mutations/TZ-OTHER-CARD.psd1'
    'specs/tasks', 'specs/tasks/TZ-EXAMPLE.md'
    'specs/archive', 'specs/archive/tasks', 'specs/archive/tasks/TZ-ARCHIVED.md'
    # Three README.md and no root one, so the fixture carries a COMMON basename as well as unique ones.
    # This is the shape that took selftest gate 15's e2e fixture red: its card declares a root README.md
    # it is about to create, and without the uniqueness condition the rule flagged it against these.
    'docs/README.md', 'frontend', 'frontend/README.md'
  )
}

# Declared examples for the near-miss decision, one per case CLASS. Returns findings as strings and never
# throws (same contract as Test-ScaffoldCardMutationPathsExamples). Default = exercise the live predicate
# and expect the declared verdict; -Variant re-runs the SAME cases through a named rejected shape, which
# MUST produce at least one disagreement. Hermetic: every case is text against the fixture tree.
function Test-ScaffoldCardPathNearMissExamples {
  [CmdletBinding()]
  param([ValidateSet('bare-existence', 'ignore-comment-strip')][string]$Variant)
  $tree = Get-ScaffoldCardPathNearMissTree
  $mk = {
    param($entry)
    (@('---', 'id: TZ-EXAMPLE', 'status: todo', 'allow_paths:', "  - $entry", '---', 'body')) -join "`n"
  }
  $cases = @(
    # The two recorded defects. These are the whole reason the rule exists (TD166).
    @{ what = 'T158 defect - same basename lives at the repo root'; entry = 'scripts/init-scaffold.ps1'; expect = $true }
    @{ what = 'T153 defect - sibling one small edit away'; entry = 'docs/adr/0013-no-trajectory-or-replay-view.md'; expect = $true }
    # The arm that keeps the rule shippable: a card is written before the files it creates exist. Both
    # are silent while UNRESOLVED, which is the distinction the 'resolves' assertion pins.
    @{ what = 'self-created mutation registry - card creates it'; entry = 'specs/mutations/TZ-EXAMPLE.psd1'; expect = $false; resolves = $false }
    @{ what = 'self-created mutation results TSV - card creates it'; entry = 'specs/mutations/TZ-EXAMPLE-results.tsv'; expect = $false; resolves = $false }
    # Real corpus shapes that must stay silent BECAUSE THEY RESOLVE - a different reason, asserted apart.
    @{ what = 'entry carrying a trailing comment, resolving underneath'; entry = 'scripts/selftest.ps1   # R3 round-2 seeded-defect coverage'; expect = $false; resolves = $true }
    @{ what = 'directory form resolves - Test-Path semantics'; entry = 'specs/mutations/'; expect = $false; resolves = $true }
    @{ what = 'backslash spelling of a path that exists'; entry = 'scripts\selftest.ps1'; expect = $false; resolves = $true }
    @{ what = 'template sample placeholder - no near neighbour'; entry = 'path/to/file.ps1'; expect = $false; resolves = $false }
    @{ what = 'glob entry is never resolved'; entry = 'scripts/*.ps1'; expect = $false }
    # A card creating a root README.md while three others exist elsewhere. A basename that occurs many
    # times says nothing about where the author meant, so it is NOT a neighbour - measured, README.md
    # occurs 14 times in this repo and SKILL.md 17. Real incident: selftest gate 15's e2e fixture card.
    @{ what = 'common basename the card creates - many exist, none is a signal'; entry = 'README.md'; expect = $false; resolves = $false }
    # Offending BY DESIGN, and the reason check-cards never scans archived cards: once a card is
    # archived its own specs/tasks/<id>.md is dead, and the archived copy is a same-basename neighbour.
    @{ what = 'archived card own path - why the archive is never swept'; entry = 'specs/tasks/TZ-ARCHIVED.md'; expect = $true }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()

  # A -Variant run REPORTS the damage the rejected shape does, so it must return at least one finding.
  # Zero findings under a variant means the rejected design is indistinguishable from the live one, which
  # would mean the narrowing is unmeasured - the same contract every other declared-examples pair uses.
  if ($useVariant -and $v -eq 'bare-existence') {
    foreach ($c in $cases) {
      if ($c.expect) { continue }
      $d = @(Get-ScaffoldCardPathNearMissVia (& $mk $c.entry) $tree $v)
      if ($d.Count -ge 1 -and $d[0].Offending) {
        $findings += "[CARD-PATH-NEARMISS-EXAMPLE] dropping the near-neighbour condition rejects case '$($c.what)' (entry '$($c.entry)'), which the live rule deliberately leaves silent. That is why this rule is NOT a bare existence check: measured over the live corpus, the bare form rejects 12 of the last 14 archived cards at -Phase start."
      }
    }
    return $findings
  }

  if ($useVariant -and $v -eq 'ignore-comment-strip') {
    # Resolution, not the offending verdict, is the property at stake: an un-stripped entry stops
    # resolving, and this rule BLOCKS on that, so a perfectly good card would go red.
    $entry = 'scripts/selftest.ps1   # R3 round-2 seeded-defect coverage'
    $bad = @(Get-ScaffoldCardPathNearMissVia (& $mk $entry) $tree $v)
    if ($bad.Count -ge 1 -and -not $bad[0].Resolved) {
      $findings += "[CARD-PATH-NEARMISS-EXAMPLE] resolving the entry AS DECLARED stops resolving '$entry', so the comment strip Get-YamlListItems performs is load-bearing for THIS rule: without it a card carrying a perfectly good commented entry is blocked. Four archived cards carry that shape (T1-SHIP-GATES, T2-VERIFY-LINT, T3-SHIP-FAILFAST, T74-LESSONS-COLD-SPLIT)."
    }
    return $findings
  }

  foreach ($c in $cases) {
    $d = @(Get-ScaffoldCardPathNearMissVia (& $mk $c.entry) $tree $v)
    if ($d.Count -ne 1) {
      $findings += "[CARD-PATH-NEARMISS-EXAMPLE] case '$($c.what)' produced $($d.Count) decision(s), expected exactly 1 - the entry was not extracted from allow_paths at all. [FIX] fix the predicate, never the example."
      continue
    }
    if ($d[0].Offending -ne $c.expect) {
      $findings += "[CARD-PATH-NEARMISS-EXAMPLE] case '$($c.what)' judged offending=$($d[0].Offending), expected $($c.expect) (entry '$($c.entry)', resolved=$($d[0].Resolved), neighbour=$(if ($d[0].Neighbour) { $d[0].Neighbour } else { 'none' })). [FIX] fix the predicate, never the example."
    }
    # Resolution is asserted separately from the verdict: a case can be silent for the RIGHT reason
    # (it resolved) or the wrong one (it did not resolve and merely had no neighbour).
    if ($c.ContainsKey('resolves') -and $d[0].Resolved -ne $c.resolves) {
      $findings += "[CARD-PATH-NEARMISS-EXAMPLE] case '$($c.what)' resolved=$($d[0].Resolved), expected $($c.resolves) - the verdict may be right for the wrong reason. [FIX] fix the predicate, never the example."
    }
  }
  return $findings
}

# ── T242-CARD-REVIEW-GATE-RUNS (TD248): the ship R3 leg's run/skip decision, and its declared examples ──
# The decision read `$ScaffoldConfig.ReviewGate` and nothing else, so a card declaring `review_gate:`
# shipped with no review at all. The field was load-bearing for T161's `acceptance:` trigger at card
# VALIDATION time and inert for the gate it names. Measured twice on the same code (TD248): T238 and
# T239 each declared it, ship skipped it both times, and the review that eventually ran returned block
# with four findings each time. Run before the merge it produces a fix; run after, a follow-up card.
# ORDERING WAS NEVER THE DEFECT, and TD248's first wording was wrong about it: the leg already sits
# before the merge on both paths. The condition was wrong, not the position.
#
# WHAT A CARD'S DECLARATION BUYS, AND WHAT IT MUST NOT. It buys a RUN, never a veto. T68 ruled the merge
# bar is the deterministic gates and TD248 explicitly refuses to reopen that, so the card-driven state is
# advisory: report the verdict, record it, merge anyway. That is why Run and Blocking are two fields
# rather than one tri-state - the naive fix routes the card into the existing `required` path and thereby
# re-arms a merge gate the project decided against, and only a separate Blocking field makes that
# failure assertable rather than invisible.
#
# WHY A CORE RATHER THAN A SECOND INLINE CONDITION. Two call sites branch on this (remote ship and
# -Local ship). The two sibling gates that read a card field at ship time, scope and budget, both route
# through a shared decision core with declared examples precisely so the gate and its test cannot drift.
# This was the one card-driven ship decision still written as an inline string comparison with no core
# and no examples, which is how it stayed wrong silently across three cards.
#
# The DECISION is split out of the PARSE (the T102 shape): callers hand it two already-read strings, so
# the examples below can feed it states no real card produces, without writing a card file to disk.
#
# THE THREE REJECTED SHAPES are declared BY NAME so the table can prove each one is still caught,
# instead of a comment asking the next reader to remember them:
#   config-only - the pre-T242 behaviour. The card is not an input at all.
#   card-blocks - the naive fix. A card's declaration reaches the BLOCKING path, re-arming T68's gate.
#   run-always  - a fix with no negative control. Every card runs the reviewer, declared or not.
function Get-ScaffoldReviewRunDecision {
  [CmdletBinding()]
  param(
    [string]$ConfigReviewGate,
    [string]$CardReviewGate,
    [ValidateSet('live', 'config-only', 'card-blocks', 'run-always')][string]$Variant = 'live'
  )
  # Trimmed HERE rather than at each call site: two callers cannot both remember, and a card scalar read
  # off a CRLF checkout carries a trailing \r that would otherwise read as a declaration (L230's class).
  $cfg  = "$ConfigReviewGate".Trim()
  $card = "$CardReviewGate".Trim()
  $required = ($cfg -eq 'required')
  $declared = -not [string]::IsNullOrWhiteSpace($card)
  if ($Variant -eq 'config-only') { $declared = $false }
  if ($Variant -eq 'run-always')  { $declared = $true }
  if ($required) { return [pscustomobject]@{ Run = $true; Blocking = $true; Source = 'config-required' } }
  if ($declared) { return [pscustomobject]@{ Run = $true; Blocking = ($Variant -eq 'card-blocks'); Source = 'card' } }
  return [pscustomobject]@{ Run = $false; Blocking = $false; Source = 'none' }
}

function Test-ScaffoldReviewRunDecisionExamples {
  <#
  .SYNOPSIS  T242 declared examples for Get-ScaffoldReviewRunDecision. Returns findings; empty is green.
             Hermetic - literal strings only, so it reads no file and needs no repo.
  .DESCRIPTION
    The table carries BOTH directions. The case that a card WITHOUT the field still skips the reviewer is
    the half a naive fix breaks first, and it is what keeps this from becoming "always review", which the
    T68 default deliberately is not. Each -Variant runs the SAME examples through one rejected shape and
    MUST produce at least one finding; a variant that goes quiet means the case discriminating it is gone.
  #>
  [CmdletBinding()]
  param([ValidateSet('live', 'config-only', 'card-blocks', 'run-always')][string]$Variant = 'live')
  $findings = @()
  # 1. The state TD248 records: global gate EMPTY, card declares review_gate. Must RUN, ADVISORY.
  $d1 = Get-ScaffoldReviewRunDecision -ConfigReviewGate '' -CardReviewGate 'codex {verdict:pass}' -Variant $Variant
  if (-not $d1.Run) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a card declaring review_gate under an EMPTY ReviewGate did not reach Run - that is the TD248 defect itself, a card declaring a review and shipping without one. [FIX] fix the rule, never the example." }
  elseif ($d1.Blocking) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a card's own declaration reached the BLOCKING state. T68 ruled the merge bar is the deterministic gates and TD248 refuses to reopen it - a card buys the reviewer a read, never a veto. [FIX] fix the rule, never the example." }
  elseif ($d1.Source -ne 'card') { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] the run was attributed to '$($d1.Source)' rather than 'card'. The source is what lets the ship log and the effectiveness ledger say WHY the reviewer ran, which is the difference between an advisory record and an unexplained one." }
  # 2. THE NEGATIVE CONTROL, and the half a naive fix breaks: no declaration, empty config, no reviewer.
  #    Without this case "always run" passes case 1, and the T68 default silently becomes "review everything".
  $d2 = Get-ScaffoldReviewRunDecision -ConfigReviewGate '' -CardReviewGate '' -Variant $Variant
  if ($d2.Run) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a card with NO review_gate ran the reviewer under an empty ReviewGate. The T68 default is that ship does not invoke a reviewer no one asked for; this card's change is opt-in by declaration, not a new default. [FIX] fix the rule, never the example." }
  # 3. The untouched path: 'required' still runs AND still blocks.
  $d3 = Get-ScaffoldReviewRunDecision -ConfigReviewGate 'required' -CardReviewGate '' -Variant $Variant
  if (-not ($d3.Run -and $d3.Blocking)) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] ReviewGate='required' produced Run=$($d3.Run) Blocking=$($d3.Blocking), expected both true. That path is opt-in legacy behaviour this card must leave byte for byte." }
  # 4. 'required' WINS over a card declaration - a card may not downgrade a configured merge gate to advisory.
  $d4 = Get-ScaffoldReviewRunDecision -ConfigReviewGate 'required' -CardReviewGate 'codex {verdict:pass}' -Variant $Variant
  if (-not $d4.Blocking) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a card declaration downgraded ReviewGate='required' to advisory. The card can only ever ADD a run; the project's configured gate outranks it." }
  # 5. A whitespace-only value is not a declaration. Both call sites hand over a raw scalar, and on a CRLF
  #    checkout Get-Scalar's trailing class keeps the \r out - but nothing downstream may depend on that.
  $d5 = Get-ScaffoldReviewRunDecision -ConfigReviewGate '' -CardReviewGate "   `t " -Variant $Variant
  if ($d5.Run) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a whitespace-only review_gate value was read as a declaration, so a card carrying the key with no value would silently start invoking the reviewer." }
  # 6. The config value is trimmed IN the core, so neither call site has to remember to.
  $d6 = Get-ScaffoldReviewRunDecision -ConfigReviewGate ' required ' -CardReviewGate '' -Variant $Variant
  if (-not $d6.Blocking) { $findings += "[REVIEW-RUN-DECISION-EXAMPLE] a padded 'required' was not recognised, so the merge gate would silently fall to advisory on a config value with stray whitespace - fail-OPEN on the one state that is supposed to block." }
  return @($findings)
}

# -- T273-TIER-CORE (ADR 0016 item 1): a card's ACCEPTANCE TIER, computed from its allow_paths ----------
# Every card pays the same bar today whatever it touches: 15 card guards, the full 17-gate run over a frozen
# tree, and a second-model read. ADR 0016 measured that the single bar is not the strict part of the system
# but the undifferentiated one - 80% of the last 813 commits touch no code file at all - and made the bar a
# function of blast radius. This is that function, and nothing else: the tiers it returns are CONSUMED by
# later cards (the routed self-test run, the review axes, the template), never by this one.
#
# WHY COMPUTED AND NOT DECLARED. A tier an author picks is the tier an author under pressure lowers.
# `allow_paths` is already bound to the BASE card by the ship scope gate, so a tier derived from it can only
# change through an edit on the base branch that the history shows. Raising is allowed - a card that knows
# it is risky should be able to say so - and lowering is a block, from either side.
#
# THE MATCH IS THE SCOPE GATE'S. Get-ScaffoldOutOfScopePath (scripts/_scope.ps1) asks the same question in
# the opposite direction (which changed path is covered by an allow entry), and its three clauses are
# restated in the matcher below rather than called: _scope.ps1 dot-sources THIS file for the front-matter
# reader, so calling into it from here would invert the load order every consumer relies on
# (check-budget.ps1's header states that ordering explicitly), and the seeded card fixtures that copy
# check-cards.ps1 + _cards.ps1 alone would stop running. The restatement is not left to trust: selftest
# sub-gate 10t drives BOTH matchers over the same corpus and reds if they ever disagree, which is a stronger
# bond than a shared call - a call proves the same code ran, the equivalence arm proves the same ANSWER,
# and it is the answer that TD68's drift pairs got wrong.
#
# ONE-DIRECTIONAL, and it stays that way (T274). This primitive answers the SCOPE question - is this path
# inside that entry - and the scope question has exactly one direction, which is why 10t can bind it to
# Get-ScaffoldOutOfScopePath at all. T273 read the tier off it directly and inherited that direction, so a
# card declaring a whole directory that CONTAINS a Tier-S file (`scripts/`, `.claude/`) computed 1. That was
# the tier being wrong, not the matcher: the TIER question is "could this card reach a Tier-S file", which
# is containment in BOTH directions. So the primitive is left exactly as the equivalence arm found it and
# the second direction is added one level up, in Test-ScaffoldTierSEntryMatch. Widening it here instead
# would have made the two matchers answer different questions while still looking byte-identical - the
# shape TD68's drift pairs took.
function Test-ScaffoldTierPathMatch {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$Path,
    [AllowEmptyString()][AllowNull()][string]$Entry
  )
  if ([string]::IsNullOrWhiteSpace($Path) -or [string]::IsNullOrWhiteSpace($Entry)) { return $false }
  # Backslashes are folded on the PATH only, exactly as the scope gate folds them on the changed path and
  # not on the allow entry: the equivalence arm in 10t compares the two matchers literally, and a
  # convenience normalisation applied on one side only is precisely how two matchers start to disagree.
  $f = $Path -replace '\\', '/'
  $norm = $Entry.TrimEnd('/')
  # Escaped for the prefix arm so a literal path carrying a wildcard metacharacter (a '[' in a file name)
  # stays literal there, while the last arm keeps the RAW entry so a deliberate glob ('*.md') still globs.
  $escNorm = [System.Management.Automation.WildcardPattern]::Escape($norm)
  return [bool]($f -eq $norm -or $f -like "$escNorm/*" -or $f -like $Entry)
}

# -- T280-TIER-FAIL-CLOSED: can a folded FrozenPaths fragment be evaluated as a regex at all? --
# Asked in TWO places - the matcher, which fails closed on a fragment it cannot evaluate, and the decision,
# which says so in the reason it prints - so it is one function rather than two try/catch blocks that could
# come to disagree about what "invalid" means. Never throws: a tier decision that dies on a configuration
# value would take check-cards down with it, which is the one thing worse than reading the value wrong.
function Test-ScaffoldFrozenFragmentValid {
  [CmdletBinding()]
  param([AllowEmptyString()][AllowNull()][string]$Fragment)
  if ([string]::IsNullOrWhiteSpace($Fragment)) { return $false }
  try { [void][regex]::new($Fragment); return $true } catch { return $false }
}

# -- T274-TIER-ACCEPTANCE: the two TIER-SPECIFIC readings of an entry, each built ON the primitive above --
# ADR 0016 item 8. Both live here rather than inside Get-ScaffoldCardTier so the decision stays readable and
# each reading can be exercised on its own, and both take the decision's $Variant so a rejected shape can
# reach the place where the boundary actually lives.
#
# The Tier-S reading is CONTAINMENT IN BOTH DIRECTIONS: an allow_path is S when it lies under a Tier-S
# entry, and equally when a Tier-S entry lies under it. A card that may write anywhere in `scripts/` may
# write `scripts/_guard.ps1`, so a bare directory has to raise the tier - T273 read only the first
# direction and computed 1 for `scripts/` and `.claude/`, the two widest allow_paths in the corpus.
#
# A FrozenPaths entry arrives behind the 'frozen:' marker that Get-ScaffoldTierSPaths folds it in with, and
# T282-TIER-FROZEN-SIMPLE reduced its branch to TWO RULES THAT READ NO REGEX TEXT. Every earlier reading
# tried to answer "could a path under this allow_path match this fragment" from the fragment's SHAPE, and
# each repair left the next shape open: T274 read it as a glob, T280 cut a literal prefix at the first
# metacharacter, T276 widened that to the alternation and the escape, and T276's own review then measured
# four more shapes computing 1 with the frozen file reachable (an unanchored literal, an alternation with
# different prefixes, an optional group, a star). The class is decidable only by not deciding it:
#   * A DIRECTORY allow_path (trailing slash) is S whenever the folded frozen list holds anything at all.
#     A card that may write anywhere under a directory may write whatever is frozen there, and a repository
#     that declares frozen contracts plus a card claiming a whole directory is the strict case by
#     definition. Nothing about the fragment is read, so no fragment shape can be the one that gets missed.
#   * A FILE allow_path is S when the fragment MATCHES it - the well-defined direction, an unanchored,
#     case-insensitive regex over the forward-slashed path, exactly as .claude/hooks/guard-frozen.ps1
#     matches it. A fragment that is empty or will not COMPILE has an unknown answer, and unknown resolves
#     to S (T280): read as a non-match, one broken configuration value cheapens every card it should have
#     raised. Get-ScaffoldCardTier names the invalidity in its reason so the tier is not merely right but
#     explicable, and check-cards prints that reason for every card it validates.
function Test-ScaffoldTierSEntryMatch {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$Path,
    [AllowEmptyString()][AllowNull()][string]$Entry,
    [string]$Variant = 'live'
  )
  if ([string]::IsNullOrWhiteSpace($Path) -or [string]::IsNullOrWhiteSpace($Entry)) { return $false }
  if ($Entry.StartsWith('frozen:')) {
    $fragment = $Entry.Substring(7)
    $folded = ($Path -replace '\\', '/')
    # RULE 1 - THE DIRECTORY. This entry freezes something and the card may write anywhere under this
    # directory, so the card can reach it. The fragment is NOT READ, which is the whole subtraction. Three
    # rejected shapes sit on this rule: 'frozen-dir-not-s' deletes it outright, while 'frozen-alternation'
    # (ask reverse containment of the fragment as a path) and 'frozen-no-reverse' (fall through and match
    # the directory forward) are the two ways of putting the fragment's TEXT back in charge of a directory.
    if ($folded.EndsWith('/')) {
      if ($Variant -eq 'frozen-dir-not-s') { return $false }
      if ($Variant -eq 'frozen-alternation') { return [bool](Test-ScaffoldTierPathMatch -Path $fragment -Entry $folded) }
      if ($Variant -ne 'frozen-no-reverse') { return $true }
    }
    # RULE 2 - THE FILE, matched forward, which is the direction a regex fragment answers well. Empty and
    # uncompilable are the two unknowns, and both resolve to S rather than to the cheaper tier.
    if ([string]::IsNullOrWhiteSpace($fragment)) { return ($Variant -ne 'frozen-empty') }
    if ($Variant -eq 'frozen-as-glob') { return [bool](Test-ScaffoldTierPathMatch -Path $folded -Entry $fragment) }
    if (-not (Test-ScaffoldFrozenFragmentValid -Fragment $fragment)) { return ($Variant -ne 'frozen-invalid-open') }
    if ($Variant -eq 'frozen-file-unmatched') { return $false }
    return [bool]($folded.ToLowerInvariant() -match $fragment.ToLowerInvariant())
  }
  $forward = [bool](Test-ScaffoldTierPathMatch -Path $Path -Entry $Entry)
  if ($Variant -eq 'directory-contains') { return $forward }
  return [bool]($forward -or (Test-ScaffoldTierPathMatch -Path $Entry -Entry $Path))
}

# The Tier-0 reading is one-directional - a path is 0 only when it lies under a Tier-0 entry - plus ADR
# 0016's root anchor: `*.md` is "*.md at the repo root", so a wildcard entry naming no directory covers only
# a path naming no directory. T273 left the glob unanchored and read `scripts/README.md` as Tier 0, which is
# the CHEAPENING direction and therefore the one that must not be loose: a card whose only path is a
# markdown file inside a script directory carries no gate surface only if nothing under it does.
function Test-ScaffoldTier0EntryMatch {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$Path,
    [AllowEmptyString()][AllowNull()][string]$Entry,
    [string]$Variant = 'live'
  )
  if ([string]::IsNullOrWhiteSpace($Path) -or [string]::IsNullOrWhiteSpace($Entry)) { return $false }
  if (-not (Test-ScaffoldTierPathMatch -Path $Path -Entry $Entry)) { return $false }
  if ($Variant -eq 'md-anywhere') { return $true }
  if (($Entry -notmatch '/') -and ($Entry -match '[*?\[]') -and (($Path -replace '\\', '/') -match '/')) { return $false }
  return $true
}

# The decision. Pure: it reads no configuration and touches no disk, so its examples can feed it states no
# real card produces. The caller supplies both lists (check-cards from Get-ScaffoldTierSPaths /
# Get-ScaffoldTier0Paths, which is where FrozenPaths is folded in) and the DECLARED value read off the card.
#
# THE THREE REJECTED SHAPES are declared BY NAME so the table can prove each one is still caught, instead of
# a comment asking the next reader to remember them:
#   ignore-declared - the declaration is not read at all. A card that knows it is risky cannot say so, and
#                     the raise half of ADR 0016's rule silently does not exist.
#   allow-lower     - the declaration always wins. This is the shape the ADR rejected in those words: the
#                     tier an author under pressure lowers, arriving as a one-line edit inside the branch.
#   no-off-switch   - an empty TierSPaths stops meaning "every card is S". A downstream that has not filled
#                     the list in, or a project that empties it to switch tiering off, would silently drop
#                     every card to the cheapest bar instead of keeping today's.
# T274 (ADR 0016 item 8) adds the four boundaries T273's advisory review named, each as a rejected shape of
# its own so the table proves the correction rather than describing it:
#   directory-contains - containment runs one way only, so an allow_path naming a directory that CONTAINS a
#                     Tier-S file computes 1. `scripts/` and `.claude/` are the widest allow_paths in the
#                     corpus and both took the cheapest possible tier under it.
#   md-anywhere     - Tier0Paths' `*.md` matches markdown anywhere instead of at the repo root, so a card
#                     touching scripts/README.md computes 0 - a script directory read as a doc commit.
#   fold-when-off   - the frozen fold is counted toward the off switch, so a project that empties TierSPaths
#                     while holding frozen contracts stops being OFF and drops every card to 0 or 1 on the
#                     one configuration whose whole meaning is "keep today's bar".
#   frozen-as-glob  - a FrozenPaths entry is matched as a path glob rather than as the regex fragment its
#                     own contract declares it to be, so every fragment carrying a metacharacter silently
#                     stops raising the tier.
# T280 adds the two remaining frozen readings, both cheapening ones its own advisory review measured live:
#   frozen-invalid-open - a fragment that does not COMPILE is read as a non-match, so one malformed entry in
#                     FrozenPaths quietly lowers every card it should have raised. Unknown resolves to S.
#   frozen-no-reverse - the directory rule is skipped and a directory allow_path is matched FORWARD like a
#                     file, so the directory a frozen contract lives in computes 1 - the plain entries' own
#                     boundary, unfixed on the one list whose members cannot be written as paths.
#   frozen-empty    - an EMPTY frozen fragment is read as a non-match, so a configuration value with no
#                     content silently decides that no card can reach whatever it was meant to freeze.
# T282 subtracts the regex-text analysis those readings were built on and declares the two shapes that
# ARE the analysis, so the table proves the live rule takes neither:
#   frozen-alternation - the directory rule asks reverse containment OF THE FRAGMENT, read as a path. That
#                     is the shape every earlier repair took, one fragment class at a time; under it
#                     `^(contracts|schemas)/...` and `^foo\-bar/x` name no directory a card can be measured
#                     against, so the directory holding a frozen contract computes 1 again.
#   frozen-dir-not-s - the directory rule is gone outright, so no directory allow_path is ever raised by a
#                     frozen entry: the widest declaration buying the cheapest bar, which is the defect the
#                     whole frozen branch exists to refuse.
#   frozen-file-unmatched - a file the fragment DOES match is read as a non-match, which is the other half:
#                     the well-defined direction stops answering and a card touching the frozen file itself
#                     computes 1.
#   bad-declared    - a `tier:` value outside S|1|0 is DROPPED with a note in the reason instead of being
#                     refused. The card then says one thing, the run does another, and the only trace is a
#                     line nobody greps - the same state [CARD-BUDGET] refuses for a declared budget that
#                     cannot be a line count: governed to a human, absent to every gate.
function Get-ScaffoldCardTier {
  [CmdletBinding()]
  param(
    [string[]]$AllowPaths = @(),
    [string[]]$TierSPaths = @(),
    [string[]]$Tier0Paths = @(),
    [AllowEmptyString()][AllowNull()][string]$Declared = '',
    [ValidateSet('live', 'ignore-declared', 'allow-lower', 'no-off-switch', 'directory-contains', 'md-anywhere', 'fold-when-off', 'frozen-as-glob', 'frozen-invalid-open', 'frozen-no-reverse', 'frozen-alternation', 'frozen-empty', 'frozen-dir-not-s', 'frozen-file-unmatched', 'bad-declared')][string]$Variant = 'live'
  )
  $paths = @(@($AllowPaths) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { ([string]$_).Trim() })
  $sEntries = @(@($TierSPaths) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  $zEntries = @(@($Tier0Paths) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  # T274 (boundary c): the OFF SWITCH reads the CONFIGURED list, never the folded one. Get-ScaffoldTierSPaths
  # folds FrozenPaths in behind the 'frozen:' marker, so a project that empties TierSPaths while holding
  # frozen contracts would otherwise arrive here with a non-empty list and stop being off - every card
  # dropping to 0 or 1 on exactly the configuration that says "keep today's bar for everything".
  # Assigned in two statements, not as `$x = if (...) { @() } else { ... }`: an if-EXPRESSION collects its
  # branch's OUTPUT, and an empty array enumerates to nothing, so that shape hands back $null - invisible
  # here (where $null.Count is 0) and fatal under the StrictMode every entry script sets.
  $sPlain = @($sEntries | Where-Object { -not $_.StartsWith('frozen:') })
  $sFolded = $sEntries
  if ($sPlain.Count -eq 0 -and $Variant -ne 'fold-when-off') { $sFolded = @() }

  $computed = ''
  $reason = ''
  $matched = ''
  if ($paths.Count -eq 0) {
    # check-cards blocks a card carrying no block-list allow_paths of its own; this is the fail-closed
    # answer for every other caller, since a card declaring no surface has not bounded its blast radius.
    $computed = 'S'; $reason = 'no-allow-paths'
  }
  elseif ($sFolded.Count -eq 0 -and $Variant -ne 'no-off-switch') {
    # Empty means OFF, and off means today's bar - the strictest one - never the cheapest.
    $computed = 'S'; $reason = 'off'
  }
  else {
    foreach ($e in $sFolded) {
      foreach ($p in $paths) {
        if (Test-ScaffoldTierSEntryMatch -Path $p -Entry $e -Variant $Variant) { $computed = 'S'; $matched = $e; $reason = "TierSPaths:$e"; break }
      }
      if ($computed) { break }
    }
    # T280: an entry that raised the tier because its regex CANNOT be evaluated says so, in the one line a
    # reader ever sees. The match itself is fail-closed either way; without this the printed reason names a
    # frozen entry that looks like it matched the path, and a broken FrozenPaths value stays invisible while
    # silently raising every card in the repo - right answer, unfixable cause.
    if ($matched.StartsWith('frozen:') -and -not (Test-ScaffoldFrozenFragmentValid -Fragment $matched.Substring(7))) { $reason = "$reason+frozen-regex-invalid" }
    # T282 deleted the companion note for a fragment that COMPILES and still could not be reasoned about:
    # nothing reasons about a fragment's text any more, so there is no unreadability left to report and the
    # entry that raised a directory raised it for the one reason the branch now has.
    if (-not $computed) {
      $uncovered = @($paths | Where-Object { $p = $_; -not (@($zEntries | Where-Object { Test-ScaffoldTier0EntryMatch -Path $p -Entry $_ -Variant $Variant }).Count) })
      if ($uncovered.Count -eq 0) { $computed = '0'; $reason = 'all-under-Tier0Paths' }
      else { $computed = '1'; $reason = "no-TierS-match:$($uncovered[0])" }
    }
  }

  # The declaration. Its legal SHAPE is the CARD-TIER row of Get-ScaffoldCardRules, read here rather than
  # restated. The computed tier still stands whatever the declaration says - that is the conservative answer
  # and it does not change - and the reason line still records the drop. What T276 adds is the REFUSAL:
  # T273 dropped an illegal value with a printed note only, so a card declaring `tier: 2` reads as governed
  # to a human and as undeclared to every gate, which is the state [CARD-BUDGET] already refuses for a
  # budget that cannot be a line count. The finding is raised on the SHAPE, before the raise/lower
  # comparison, so a value that decides nothing can never also be reported as a lowering.
  $tier = $computed
  $finding = ''
  $decl = "$Declared".Trim()
  if ($decl) {
    $rule = @(Get-ScaffoldCardRules | Where-Object { $_.Id -eq 'CARD-TIER' })
    if ($rule.Count -ne 1) { throw "Get-ScaffoldCardTier: rule CARD-TIER is missing from Get-ScaffoldCardRules - the declared table is the only source of the legal tier values and there is no fallback copy." }
    if (-not ($decl -cmatch $rule[0].Pattern)) {
      $reason = "$reason+declared-illegal:$decl"
      if ($Variant -ne 'bad-declared') { $finding = "[CARD-TIER-BADVALUE] declares 'tier: $decl', which is not one of S, 1 or 0 (the CARD-TIER row of Get-ScaffoldCardRules is the only source of the legal values). The computed tier $computed stands, so nothing was decided by the typo - and that is exactly why it is refused rather than dropped: the card reads as governed to a human and as undeclared to every gate, the same in-between state a declared budget that cannot be a line count is refused for. [FIX] write 'tier: S', 'tier: 1' or 'tier: 0' - the value is case-sensitive - or delete the line to accept the computed tier. A declaration may only RAISE." }
      $decl = ''
    }
  }
  if ($decl -and $Variant -ne 'ignore-declared') {
    $rank = @{ 'S' = 2; '1' = 1; '0' = 0 }
    if ($rank[$decl] -gt $rank[$computed]) { $tier = $decl; $reason = "declared-raise:$decl-over-$computed" }
    elseif ($rank[$decl] -lt $rank[$computed]) {
      if ($Variant -eq 'allow-lower') { $tier = $decl; $reason = "declared-lower:$decl-over-$computed" }
      else {
        $finding = "[CARD-TIER-LOWER] declares 'tier: $decl' while its allow_paths compute tier $computed ($reason). A declaration may only RAISE: the computed tier is bound to allow_paths, which the ship scope gate reads from the BASE card, so a tier a card can talk itself down to is a tier that gets talked down. [FIX] delete the 'tier:' line to accept the computed tier, or raise it (S is the strictest); if the computed tier is wrong, the fix is the allow_paths, never the declaration."
      }
    }
  }
  return [pscustomobject]@{ Tier = $tier; Reason = $reason; Matched = $matched; Finding = $finding }
}

# Declared examples for the tier decision. Returns findings as strings and never throws; empty is green.
# Hermetic - literal lists only, so it reads no configuration and needs no repo. Default = the live
# decision, which must report nothing; each -Variant runs the SAME cases through one rejected shape and
# must produce at least one finding, so a table that stopped discriminating is visible instead of quietly
# green (the contract sub-gate 10t asserts both ways).
function Test-ScaffoldCardTierExamples {
  [CmdletBinding()]
  param([ValidateSet('ignore-declared', 'allow-lower', 'no-off-switch', 'directory-contains', 'md-anywhere', 'fold-when-off', 'frozen-as-glob', 'frozen-invalid-open', 'frozen-no-reverse', 'frozen-alternation', 'frozen-empty', 'frozen-dir-not-s', 'frozen-file-unmatched', 'bad-declared')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $s = @('scripts/_guard.ps1', '.github/workflows/', 'specs/verdict.schema.json')
  $z = @('docs/', 'specs/', '*.md')
  $findings = @()
  # 1. A Tier-S FILE entry, matched exactly. The narrowest case, and the one every other arm rests on.
  $c1 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1', 'docs/SELFTEST.md') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c1.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card touching scripts/_guard.ps1 computed tier '$($c1.Tier)', expected S - one Tier-S path is enough, whatever else the card declares. [FIX] fix the rule, never the example." }
  elseif ($c1.Matched -ne 'scripts/_guard.ps1') { $findings += "[CARD-TIER-EXAMPLE] the S verdict named matched entry '$($c1.Matched)' rather than the entry that caused it. The matched entry is what lets the printed reason say WHY the card is S, which is the difference between a verdict and an assertion." }
  # 2. A Tier-S DIRECTORY entry, matched as a segment prefix - the '.github/workflows/' shape.
  $c2 = Get-ScaffoldCardTier -AllowPaths @('.github/workflows/ci.yml') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c2.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card touching .github/workflows/ci.yml computed tier '$($c2.Tier)', expected S - a directory entry covers what is under it, the way the scope gate reads allow_paths. [FIX] fix the rule, never the example." }
  # 3. EVERY path under Tier0Paths - the doc/card/ledger commit that is 80% of this repo's history.
  $c3 = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md', 'specs/tech-debt-tracker.md') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c3.Tier -ne '0') { $findings += "[CARD-TIER-EXAMPLE] a card whose every path sits under Tier0Paths computed tier '$($c3.Tier)', expected 0. [FIX] fix the rule, never the example." }
  # 4. Neither: no Tier-S match and not all Tier-0. The default, and the tier most work lands in.
  $c4 = Get-ScaffoldCardTier -AllowPaths @('scripts/lessons.ps1', 'docs/LESSONS.md') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c4.Tier -ne '1') { $findings += "[CARD-TIER-EXAMPLE] a card with no Tier-S path and one path outside Tier0Paths computed tier '$($c4.Tier)', expected 1 - the rule is any-S, then all-0, then 1. [FIX] fix the rule, never the example." }
  # 5. A same-prefix path in a DIFFERENT segment must not match. Segment matching, not character prefixing:
  #    'scripts/_guard.ps1.bak' and 'docsite/x.md' are the two shapes TD60/TD-123 recorded, one per direction.
  $c5 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1.bak', 'docsite/x.md') -TierSPaths $s -Tier0Paths @('docs/') -Variant $v
  if ($c5.Tier -eq 'S') { $findings += "[CARD-TIER-EXAMPLE] 'scripts/_guard.ps1.bak' matched the Tier-S entry 'scripts/_guard.ps1' - that is character prefixing, not segment matching, and it raises cards that touch no guarded file. [FIX] fix the rule, never the example." }
  if ($c5.Tier -eq '0') { $findings += "[CARD-TIER-EXAMPLE] 'docsite/x.md' was read as covered by the Tier-0 entry 'docs/' - the same character-prefix defect in the CHEAPENING direction, which is the worse one. [FIX] fix the rule, never the example." }
  # 6. THE RAISE. A card whose paths compute 0 declares S and gets S: a card that knows it is risky says so.
  $c6 = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths $s -Tier0Paths $z -Declared 'S' -Variant $v
  if ($c6.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a computed-0 card declaring 'tier: S' came back '$($c6.Tier)' - the raise half of ADR 0016's rule is gone, so a card that knows its own risk cannot record it. [FIX] fix the rule, never the example." }
  elseif ($c6.Finding) { $findings += "[CARD-TIER-EXAMPLE] a legitimate RAISE was reported as a finding: $($c6.Finding)" }
  # 7. THE LOWER, and the load-bearing case: an S card declaring 1 stays S AND is reported.
  $c7 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1') -TierSPaths $s -Tier0Paths $z -Declared '1' -Variant $v
  if ($c7.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a Tier-S card declaring 'tier: 1' came back '$($c7.Tier)' - a card talked itself down, which is the one thing this decision exists to refuse. [FIX] fix the rule, never the example." }
  if (-not $c7.Finding) { $findings += "[CARD-TIER-EXAMPLE] a lowering declaration produced no finding, so check-cards has nothing to block on and the refusal is silent. [FIX] fix the rule, never the example." }
  elseif ($c7.Finding -notmatch 'CARD-TIER-LOWER') { $findings += "[CARD-TIER-EXAMPLE] the lowering finding carries no [CARD-TIER-LOWER] sentinel, so nothing downstream can grep for it. Got: $($c7.Finding)" }
  # 8. A declaration EQUAL to the computed tier is not a lowering - it is a card restating what it is.
  $c8 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1') -TierSPaths $s -Tier0Paths $z -Declared 'S' -Variant $v
  if ($c8.Finding) { $findings += "[CARD-TIER-EXAMPLE] a declaration EQUAL to the computed tier was reported as a lowering: $($c8.Finding)" }
  # 9. THE OFF SWITCH. Empty TierSPaths means tiering is off and every card is S - today's bar, unchanged.
  $c9 = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths @() -Tier0Paths $z -Variant $v
  if ($c9.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] with an EMPTY TierSPaths a card computed tier '$($c9.Tier)', expected S. Empty means off, and off means today's bar for everything - a freshly initialised downstream and a project that deliberately switches tiering off both walk this path. [FIX] fix the rule, never the example." }
  # 10. An empty Tier0Paths means NO card is 0 - the same empty read, in the other list's own direction.
  $c10 = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths $s -Tier0Paths @() -Variant $v
  if ($c10.Tier -eq '0') { $findings += "[CARD-TIER-EXAMPLE] with an EMPTY Tier0Paths a card still computed 0 - an empty list covers nothing, so this is 'every path matched nothing' being read as a pass. [FIX] fix the rule, never the example." }
  # 11. An illegal declared value decides nothing and does not throw; the computed tier stands, loudly.
  $c11 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1') -TierSPaths $s -Tier0Paths $z -Declared 's' -Variant $v
  if ($c11.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] an illegal declared value ('s', which the CARD-TIER row's own ShouldNot list names) changed the tier to '$($c11.Tier)' instead of being dropped in favour of the computed one." }
  elseif ($c11.Reason -notmatch 'declared-illegal') { $findings += "[CARD-TIER-EXAMPLE] an illegal declared value was dropped SILENTLY - the reason line must say so, or a typo'd declaration looks like it was honoured. Got reason: $($c11.Reason)" }
  # -- T274 (ADR 0016 item 8): the four boundaries T273's advisory review named, each asserted BOTH ways --
  # 12. CONTAINMENT THE OTHER WAY. A card that may write anywhere in scripts/ may write scripts/_guard.ps1,
  #     so the bare directory is S. `scripts/` and `.claude/` are the two widest allow_paths in the corpus
  #     and both computed 1 until this direction existed - the cheapest tier for the widest declaration.
  $c12 = Get-ScaffoldCardTier -AllowPaths @('scripts/') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c12.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'scripts/' computed tier '$($c12.Tier)', expected S - a Tier-S entry (scripts/_guard.ps1) lies UNDER that allow_path, so the card can reach it. Containment has to run both ways, or the widest declaration buys the cheapest bar. [FIX] fix the rule, never the example." }
  # 12b. And the direction must not become a wildcard: a sibling directory that contains no Tier-S entry is
  #      still not S, or "both ways" would just mean "any path with a shared prefix".
  $c12b = Get-ScaffoldCardTier -AllowPaths @('context/') -TierSPaths $s -Tier0Paths @('context/') -Variant $v
  if ($c12b.Tier -eq 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing 'context/', a directory no Tier-S entry lies under, was raised to S anyway ($($c12b.Reason)). The reverse direction covers containment, not resemblance. [FIX] fix the rule, never the example." }
  # 13. THE ROOT ANCHOR. ADR 0016's Tier-0 entry is '*.md at the repo root'; markdown nested under a script
  #     directory is not a doc commit, and this is the cheapening direction, so it is the one held tight.
  $c13 = Get-ScaffoldCardTier -AllowPaths @('scripts/README.md') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c13.Tier -ne '1') { $findings += "[CARD-TIER-EXAMPLE] a card whose only path is 'scripts/README.md' computed tier '$($c13.Tier)', expected 1. Tier0Paths' '*.md' is the ROOT markdown entry; unanchored it swallows every nested .md and hands the cheapest acceptance to a card sitting inside a script directory. [FIX] fix the rule, never the example." }
  # 13b. The root case itself still works, or the anchor would have closed the entry entirely.
  $c13b = Get-ScaffoldCardTier -AllowPaths @('README.md') -TierSPaths $s -Tier0Paths $z -Variant $v
  if ($c13b.Tier -ne '0') { $findings += "[CARD-TIER-EXAMPLE] a card whose only path is the ROOT 'README.md' computed tier '$($c13b.Tier)', expected 0 - anchoring the entry must narrow it to the repo root, not close it. [FIX] fix the rule, never the example." }
  # 14. THE OFF SWITCH SURVIVES THE FROZEN FOLD. Get-ScaffoldTierSPaths folds FrozenPaths in behind the
  #     'frozen:' marker, so a list holding ONLY folded entries is still an empty TierSPaths - off, and off
  #     means S. Reading the folded list as "configured" flips the one state whose meaning is today's bar.
  $c14 = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths @('frozen:contracts/') -Tier0Paths $z -Variant $v
  if ($c14.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] with TierSPaths EMPTY and only a folded FrozenPaths entry present, a card computed tier '$($c14.Tier)' ($($c14.Reason)), expected S. Empty TierSPaths means tiering is off whatever else is folded in, and off means today's bar for every card. [FIX] fix the rule, never the example." }
  # 15. A FROZEN ENTRY IS A REGEX FRAGMENT, matched the way guard-frozen matches it. Read as a glob, a
  #     fragment carrying metacharacters matches nothing and the frozen contract quietly stops being S.
  $c15 = Get-ScaffoldCardTier -AllowPaths @('contracts/v2/schema.json') -TierSPaths @('scripts/task.ps1', 'frozen:contracts/v[0-9]+/schema\.json') -Tier0Paths $z -Variant $v
  if ($c15.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card touching a FROZEN contract computed tier '$($c15.Tier)' ($($c15.Reason)), expected S. FrozenPaths entries are regex fragments by their own contract (.claude/hooks/guard-frozen.ps1 matches them with -match); read as path globs, every fragment written with a metacharacter stops raising the tier. [FIX] fix the rule, never the example." }
  elseif ($c15.Matched -notmatch 'frozen:') { $findings += "[CARD-TIER-EXAMPLE] the S verdict on a frozen contract named matched entry '$($c15.Matched)' rather than the folded frozen entry that caused it, so the printed reason cannot say the card is S because it touches a frozen contract." }
  # -- T280 (its own advisory review, re-measured on the merged tree): the two frozen readings left open --
  # 16. AN UNPARSEABLE FRAGMENT IS S, AND SAYS WHY. T274 caught the compile error and answered "no match",
  #     so a single malformed FrozenPaths entry lowered every card it should have raised: a configuration
  #     defect resolving to the cheaper tier, which is the direction this decision may never take.
  #     The fragment is an unclosed GROUP rather than an unclosed CLASS on purpose: '[' is invalid as a
  #     wildcard too, so the frozen-as-glob rejected shape would throw inside the shared primitive instead
  #     of reporting - a fixture defect wearing a rule defect's failure text. '(' is a legal glob and an
  #     illegal regex, which is exactly the one property this case is about.
  $c16 = Get-ScaffoldCardTier -AllowPaths @('contracts/v2/schema.json') -TierSPaths @('scripts/_guard.ps1', 'frozen:(') -Tier0Paths @() -Variant $v
  if ($c16.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card computed tier '$($c16.Tier)' ($($c16.Reason)) against a FrozenPaths entry whose regex does not compile, expected S. An entry that cannot be EVALUATED has an unknown answer, and unknown resolves to the strictest tier; read as a non-match, one broken configuration value cheapens every card silently. [FIX] fix the rule, never the example." }
  elseif ($c16.Reason -notmatch 'frozen-regex-invalid') { $findings += "[CARD-TIER-EXAMPLE] the S verdict on an unparseable frozen fragment carried reason '$($c16.Reason)', which does not name the invalidity - the tier is then right for a cause nobody can act on, and the broken entry stays invisible while raising every card in the repo. [FIX] fix the rule, never the example." }
  # 17. REVERSE CONTAINMENT FOR A FROZEN ENTRY - case 12's boundary, on the one list whose members cannot be
  #     written as paths. A card allowed to write anywhere in contracts/ can reach the frozen schema below it.
  $c17 = Get-ScaffoldCardTier -AllowPaths @('contracts/') -TierSPaths @('scripts/_guard.ps1', 'frozen:^contracts/v[0-9]+/schema\.json$') -Tier0Paths @() -Variant $v
  if ($c17.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'contracts/' computed tier '$($c17.Tier)' ($($c17.Reason)) while a FROZEN contract lies under it, expected S. A directory allow_path reaches whatever a frozen entry protects, and that is decided without reading the fragment - or the widest declaration buys the cheapest bar wherever the entry happens to be a regex fragment. [FIX] fix the rule, never the example." }
  # 17b. And it must not become resemblance: a card touching no directory the frozen entry names stays 0.
  $c17b = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths @('scripts/_guard.ps1', 'frozen:^contracts/v[0-9]+/schema\.json$') -Tier0Paths @('docs/') -Variant $v
  if ($c17b.Tier -eq 'S') { $findings += "[CARD-TIER-EXAMPLE] a doc-only card was raised to S by a frozen entry naming a directory it never touches ($($c17b.Reason)). The reverse direction covers containment, not the mere presence of a frozen entry in the configuration. [FIX] fix the rule, never the example." }
  # -- T276 (T280's own advisory review, re-measured on the merged tree): the fragment shapes the prefix
  #    scan could not reason about, kept as examples after T282 removed the reasoning --
  # 18. A FRAGMENT THAT OPENS ON A METACHARACTER. `^(contracts|schemas)/...` is the ordinary way to freeze
  #     two trees at once and computed 1 for a card allowed to write the whole of contracts/, first because
  #     the literal prefix was empty and then, after T276, because the next shape was uncovered instead.
  $c18 = Get-ScaffoldCardTier -AllowPaths @('contracts/') -TierSPaths @('scripts/_guard.ps1', 'frozen:^(contracts|schemas)/v[0-9]+/schema') -Tier0Paths @() -Variant $v
  if ($c18.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'contracts/' computed tier '$($c18.Tier)' ($($c18.Reason)) against a frozen fragment opening on an alternation, expected S. The directory rule reads no fragment text, so no fragment shape can be the one it fails on. [FIX] fix the rule, never the example." }
  # 18b. THE ESCAPE, the same hole reached from the other side: cutting a literal prefix AT the escape read
  #     `^foo\-bar/x` as `foo`, real and three segments short, so a card allowing `foo-bar/` was NOT raised
  #     while one allowing `foo/` was - wrong in both directions rather than merely conservative.
  $c18b = Get-ScaffoldCardTier -AllowPaths @('foo-bar/') -TierSPaths @('scripts/_guard.ps1', 'frozen:^foo\-bar/x') -Tier0Paths @() -Variant $v
  if ($c18b.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing 'foo-bar/' computed tier '$($c18b.Tier)' ($($c18b.Reason)) while the frozen fragment '^foo\-bar/x' freezes a file directly under it. [FIX] fix the rule, never the example." }
  # 18c. And a FILE nowhere near the fragment is still not S, which is what stops 18/18b from being
  #      satisfied by raising every card: the file rule matches forward and still answers NO.
  $c18c = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths @('scripts/_guard.ps1', 'frozen:^contracts/v[0-9]+/schema') -Tier0Paths @('docs/') -Variant $v
  if ($c18c.Tier -eq 'S') { $findings += "[CARD-TIER-EXAMPLE] a doc-only card was raised to S by a frozen fragment naming a directory it never touches ($($c18c.Reason)). A FILE allow_path is S only when the fragment matches it; only a DIRECTORY is raised by the entry's mere presence. [FIX] fix the rule, never the example." }
  # 19. AN EMPTY FRAGMENT. T280 read it as a non-match, so a configuration value with no content quietly
  #     decided that no card reaches whatever it was meant to freeze - the cheapening direction again, and
  #     the one shape where the entry says nothing at all about what it protects.
  $c19 = Get-ScaffoldCardTier -AllowPaths @('docs/x.md') -TierSPaths @('scripts/_guard.ps1', 'frozen:') -Tier0Paths @('docs/') -Variant $v
  if ($c19.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card computed tier '$($c19.Tier)' ($($c19.Reason)) against an EMPTY frozen fragment, expected S. An entry that names nothing has an unknown answer, and unknown resolves to the strictest tier; read as a non-match it is a blank configuration value deciding that every card is cheap. [FIX] fix the rule, never the example." }
  # 20. THE BAD DECLARED VALUE. T273 dropped it with a note in the reason and blocked nothing, so a card
  #     saying `tier: 2` reads as governed to a human and as undeclared to every gate. The computed tier is
  #     unchanged either way - the point is the refusal, not the tier.
  $c20 = Get-ScaffoldCardTier -AllowPaths @('scripts/_guard.ps1') -TierSPaths $s -Tier0Paths $z -Declared '2' -Variant $v
  if ($c20.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] an illegal declared value ('2') changed the tier to '$($c20.Tier)' instead of leaving the computed one standing. Refusing the value must not also decide with it. [FIX] fix the rule, never the example." }
  if (-not $c20.Finding) { $findings += '[CARD-TIER-EXAMPLE] a `tier:` value outside S, 1 and 0 produced no finding, so check-cards has nothing to block on and a card can declare a tier no gate will ever read - the in-between state a declared-but-unusable value always is. [FIX] fix the rule, never the example.' }
  elseif ($c20.Finding -notmatch 'CARD-TIER-BADVALUE') { $findings += "[CARD-TIER-EXAMPLE] the bad-value finding carries no [CARD-TIER-BADVALUE] sentinel, so nothing downstream can grep for it and it cannot be told apart from [CARD-TIER-LOWER]. Got: $($c20.Finding)" }
  # 20b. A LEGAL declaration is still not a finding - the boundary that keeps 20 from being satisfied by
  #      reporting every card that declares a tier at all.
  $c20b = Get-ScaffoldCardTier -AllowPaths @('docs/HANDOFF.md') -TierSPaths $s -Tier0Paths $z -Declared 'S' -Variant $v
  if ($c20b.Finding) { $findings += "[CARD-TIER-EXAMPLE] a LEGAL raise was reported as a bad value: $($c20b.Finding)" }
  # -- T282 (T276's own advisory review, re-measured on the merged tree): the four shapes still computing 1,
  #    and the two boundaries that keep the subtraction from becoming "every frozen entry raises everything" --
  # 21. THE FOUR SHAPES, each a DIRECTORY allow_path holding what the fragment freezes. Every one of them
  #     computed 1 while the prefix scan existed, and each was a shape the previous repair had not reached:
  #     an unanchored literal, an alternation whose branches share no prefix, an optional group, a star.
  #     Driven as a list because the point is that the LIST is open-ended - naming a fifth would repeat the
  #     mistake of naming the fourth, and the rule that survives it reads none of them.
  foreach ($f21 in @('contracts/secret', '^foo/x|^bar/x', '^contracts?/x', '^foo*/x')) {
    $c21 = Get-ScaffoldCardTier -AllowPaths @('bar/') -TierSPaths @('scripts/_guard.ps1', "frozen:$f21") -Tier0Paths @() -Variant $v
    if ($c21.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'bar/' computed tier '$($c21.Tier)' ($($c21.Reason)) against the frozen fragment '$f21', expected S. A directory allow_path is S whenever a frozen entry exists at all: the shape of the fragment decides nothing, which is the only way the shape after this one is covered too. [FIX] fix the rule, never the example." }
  }
  # 21b. AND IT REACHES A TIER-0 DIRECTORY. `docs/` is the cheapest entry in the default list, so this is
  #      the case where the two rules disagree most and the one a reader is most likely to think is wrong.
  $c21b = Get-ScaffoldCardTier -AllowPaths @('docs/') -TierSPaths @('scripts/_guard.ps1', 'frozen:^contracts/v[0-9]+/schema') -Tier0Paths @('docs/') -Variant $v
  if ($c21b.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'docs/' computed tier '$($c21b.Tier)' ($($c21b.Reason)) in a repository holding frozen contracts, expected S. A whole-directory claim is the strict case by definition wherever anything is frozen, and Tier0Paths does not out-rank the frozen list. [FIX] fix the rule, never the example." }
  # 21c. THE CONTROL, and the reason the rule keys on FROZEN ENTRIES rather than on Tier-S paths in general:
  #      with nothing frozen, the same whole-directory claim is still the cheapest tier its paths compute.
  $c21c = Get-ScaffoldCardTier -AllowPaths @('docs/') -TierSPaths @('scripts/_guard.ps1') -Tier0Paths @('docs/') -Variant $v
  if ($c21c.Tier -eq 'S') { $findings += "[CARD-TIER-EXAMPLE] a card allowing the whole of 'docs/' was raised to S ($($c21c.Reason)) in a configuration holding NO frozen entry at all. The directory rule keys on a frozen entry existing; keyed on Tier-S paths in general it would raise every directory card in the repo and the tier would stop separating anything. [FIX] fix the rule, never the example." }
  # 22. THE FILE the fragment MATCHES - the well-defined direction, and the half 'frozen-file-unmatched'
  #     drops: a card touching the frozen contract itself would compute 1 while the directory above it is S.
  $c22 = Get-ScaffoldCardTier -AllowPaths @('contracts/v2/schema.json') -TierSPaths @('scripts/_guard.ps1', 'frozen:^contracts/v[0-9]+/schema') -Tier0Paths @() -Variant $v
  if ($c22.Tier -ne 'S') { $findings += "[CARD-TIER-EXAMPLE] a card whose only path IS the frozen file computed tier '$($c22.Tier)' ($($c22.Reason)), expected S. Matching the fragment against a concrete path is the one direction a regex answers well, and it is the direction guard-frozen itself uses. [FIX] fix the rule, never the example." }
  return @($findings)
}

# -- T280-TIER-FAIL-CLOSED: WHICH COPY of a card the tier is computed from ------------------------------
# The ship scope gate reads allow_paths from the BASE commit (T241/TD247) for one reason: a branch may not
# widen its own authorisation by editing the card it is judged by. T274 handed the tier the same field and
# the opposite source - `selftest.ps1 -TaskId` joined specs/tasks/<id>.md against the WORKING TREE - so
# narrowing allow_paths inside a branch bought Tier 0 or 1. That is the cheapening bypass arriving on the
# mechanism whose whole purpose is to keep risky work strict, and it is closed the way TD247 closed it:
# the base copy is the standard, and a base ref with no such card is a REFUSAL rather than a fallback,
# because no card means no computed bar and there is nothing safe to default to.
#
# The DECISION is separated from the git read (ADR 0011) so it can be exercised over synthetic text: the
# wrapper below fetches both copies, this core says which one wins and what to report when neither does.
# The wrapper's own git read is exercised end to end by selftest sub-gate 14q, over a real clone.
# Rejected shapes, declared by name:
#   working-tree-wins     - the branch's copy is preferred while a base copy exists. This IS the bypass:
#                           one edit inside the branch and the acceptance bar follows it down.
#   missing-base-is-cheap - a card absent from the base ref falls back to the working tree instead of
#                           refusing, so a card that authorises nothing on the baseline still buys a tier.
function Get-ScaffoldTierCardTextVia {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][AllowNull()][string]$BaseText,
    [AllowEmptyString()][AllowNull()][string]$WorkingText,
    [string]$TaskId = '',
    [string]$BaseRef = '',
    [string]$Variant = 'live'
  )
  $hasBase = -not [string]::IsNullOrWhiteSpace($BaseText)
  if ($hasBase -and $Variant -eq 'working-tree-wins') { return [pscustomobject]@{ Text = "$WorkingText"; Source = 'working-tree'; Finding = '' } }
  if ($hasBase) { return [pscustomobject]@{ Text = "$BaseText"; Source = "base:$BaseRef"; Finding = '' } }
  if ($Variant -eq 'missing-base-is-cheap') { return [pscustomobject]@{ Text = "$WorkingText"; Source = 'working-tree'; Finding = '' } }
  return [pscustomobject]@{ Text = ''; Source = 'none'; Finding = "specs/tasks/$TaskId.md is not on the base ref '$BaseRef', so this run has no card to compute an acceptance tier from. The copy in your checkout is not it: allow_paths is read from the baseline, exactly as the ship scope gate reads it (TD247), or a branch could buy a cheaper bar by narrowing its own card. [FIX] commit specs/tasks/$TaskId.md to the default branch first, or run the suite with no -TaskId at all." }
}

# The live read. Never throws: a missing git, an unresolvable ref and an absent card all arrive as the same
# empty base text, which the core turns into the one refusal a caller has to handle.
function Get-ScaffoldTierCardText {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$TaskId,
    [Parameter(Mandatory)][AllowEmptyString()][string]$BaseRef,
    [string]$GitDir = '.',
    [string]$Variant = 'live'
  )
  $baseText = ''
  if ($BaseRef -and (Get-Command git -ErrorAction SilentlyContinue)) {
    $baseText = @(& git -C $GitDir show "${BaseRef}:specs/tasks/$TaskId.md" 2>$null) -join "`n"
  }
  $workText = ''
  $workPath = Join-Path $GitDir "specs/tasks/$TaskId.md"
  if (Test-Path -LiteralPath $workPath -PathType Leaf) { $workText = (Get-Content -LiteralPath $workPath -Raw) }
  return (Get-ScaffoldTierCardTextVia -BaseText $baseText -WorkingText $workText -TaskId $TaskId -BaseRef $BaseRef -Variant $Variant)
}

# Declared examples for the card-source decision. Returns findings as strings and never throws; empty is
# green. Hermetic - the two copies are literal text, so no repository is built and nothing is read from
# disk. Default = the live rule, which must report nothing; each -Variant runs the SAME cases through one
# rejected shape and must produce at least one finding.
function Test-ScaffoldTierCardTextExamples {
  [CmdletBinding()]
  param([ValidateSet('working-tree-wins', 'missing-base-is-cheap')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $findings = @()
  $baseCard = "---`nid: T9-CARD-SOURCE`nallow_paths:`n  - scripts/_guard.ps1`n---`n"
  $branchCard = "---`nid: T9-CARD-SOURCE`nallow_paths:`n  - docs/NOTE.md`n---`n"
  # 1. BOTH COPIES EXIST AND DISAGREE - the whole case, and the shape of the bypass. The base copy wins.
  $t1 = Get-ScaffoldTierCardTextVia -BaseText $baseCard -WorkingText $branchCard -TaskId 'T9-CARD-SOURCE' -BaseRef 'refs/remotes/origin/master' -Variant $v
  if ($t1.Text -ne $baseCard) { $findings += "[TIER-CARD-SOURCE-EXAMPLE] with a card present on BOTH the base ref and the working tree, the run read the working copy. A branch that narrows its own allow_paths would then compute the cheaper tier it just declared for itself - the exact widening the ship scope gate refuses (TD247). [FIX] fix the rule, never the example." }
  elseif ($t1.Finding) { $findings += "[TIER-CARD-SOURCE-EXAMPLE] reading the base copy, which is the healthy path, produced a finding: $($t1.Finding)" }
  # 2. ONLY the working tree has it: a refusal, and never the branch's copy. No card, no computed bar.
  $t2 = Get-ScaffoldTierCardTextVia -BaseText '' -WorkingText $branchCard -TaskId 'T9-CARD-SOURCE' -BaseRef 'refs/remotes/origin/master' -Variant $v
  if ($t2.Text) { $findings += "[TIER-CARD-SOURCE-EXAMPLE] a card that exists ONLY in the working tree was handed back as the tier's input. A card the baseline does not carry authorises nothing, so the answer is a refusal - falling back to the branch's copy is the same bypass through the other door. [FIX] fix the rule, never the example." }
  if (-not $t2.Finding) { $findings += '[TIER-CARD-SOURCE-EXAMPLE] a card missing from the base ref produced NO finding, so the caller has nothing to refuse on and the run would continue with no computed bar at all. [FIX] fix the rule, never the example.' }
  # 3. Base has it, working tree does not (a worktree mid-cleanup): still the base copy, still no finding.
  $t3 = Get-ScaffoldTierCardTextVia -BaseText $baseCard -WorkingText '' -TaskId 'T9-CARD-SOURCE' -BaseRef 'refs/remotes/origin/master' -Variant $v
  if ($t3.Text -ne $baseCard) { $findings += "[TIER-CARD-SOURCE-EXAMPLE] the base copy was not used when the working tree had no copy at all, so the tier depends on a file the decision does not read. [FIX] fix the rule, never the example." }
  # 4. Neither copy exists - the same refusal, reached without a working file to be tempted by.
  $t4 = Get-ScaffoldTierCardTextVia -BaseText '' -WorkingText '' -TaskId 'T9-CARD-SOURCE' -BaseRef 'refs/remotes/origin/master' -Variant $v
  if (-not $t4.Finding) { $findings += '[TIER-CARD-SOURCE-EXAMPLE] an id with no card anywhere produced no finding, so an unknown card reads as a card. [FIX] fix the rule, never the example.' }
  return @($findings)
}
