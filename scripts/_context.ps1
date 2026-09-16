#requires -Version 7
<#
  Bounded context injection (T87-CTX-BOUND).

  A fragment a hook injects into a session is unbounded unless something bounds it: its size is
  whatever the previous session happened to write into the file. On 2026-08-22 the SessionStart hook
  emitted 12KB into a fresh session; the harness spilled it to disk and replaced it with a preview
  that cut off mid-sentence. Limit-InjectedText is the one place that bounds it.

  The three steps happen in this order, and the order is what the card DoD asserts:
    1. Escape the closing-tag sequence BEFORE any cut. Doing it first matters twice: truncation must
       not be able to manufacture a closing sequence that was not in the input, and the payload must
       not be able to close the marker that frames it.
    2. Return the input unchanged when it is already under the cap - the common path is a no-op.
    3. Otherwise keep the head AND the tail and elide the middle, leaving a notice that says how many
       characters were dropped and where the full text lives. Head-only truncation is specifically
       wrong here: NEXT-ACTION, the single line a resuming session needs, sits at the END of the block.

  Deterministic by design: a hook runs before the session has a model in the loop, so this truncates -
  it never summarizes and never calls a model. No runtime dependency and no token counting; the cap is
  characters, which is the unit the harness that spilled the 12KB fragment actually measured.

  Callers: .claude/hooks/handoff-resume.ps1, .claude/hooks/handoff-reminder.ps1. Standard: docs/HANDOFF.md.
#>

function Limit-InjectedText {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
    # The one cap, in characters. Deliberately a default here rather than a per-hook field in
    # _config.ps1: one constant, one helper, until a second limit is actually needed.
    [int]$MaxChars = 4000,
    # Where a reader can find what was elided; named in the notice so a truncated block stays a
    # pointer to the full text instead of pretending to be it.
    [string]$Source = 'the source file'
  )
  $bounded = $Text
  $bounded = $bounded -replace '</', '<\/'
  if ($bounded.Length -le $MaxChars) { return $bounded }
  $keep = [int][math]::Min([math]::Floor($MaxChars / 2), [math]::Floor($bounded.Length / 2))
  $head = $bounded.Substring(0, $keep)
  $tail = $bounded.Substring($bounded.Length - $keep)
  $notice = ' ... [' + ($bounded.Length - (2 * $keep)) + ' characters elided from the middle; full text in ' + $Source + '] ... '
  return $head + $notice + $tail
}

# -- T112-CORE-SELFCHECK-SCOPE (TD140 / ADR 0011): the declared self-check for the injection budget --
# Limit-InjectedText is the one place that bounds what a hook injects into a session. Its three steps have
# an ORDER, and the order is the contract: escape the closing sequence BEFORE any cut (so truncation cannot
# manufacture one, and the payload cannot close the marker framing it), return short input untouched, and
# when cutting keep the HEAD AND THE TAIL - head-only truncation would drop NEXT-ACTION, the single line a
# resuming session needs, because it sits at the end of the block.
# The rejected shape:
#   no-truncation - escape but never cut, which is the unbounded behaviour T87 was filed against. The
#                   12KB fragment that started this went out under exactly that behaviour.
function Test-ScaffoldContextLimitVia($Text, $MaxChars, $Source, $Variant) {
  if ($Variant -eq 'no-truncation') { return ($Text -replace '</', '<\/') }
  return (Limit-InjectedText -Text $Text -MaxChars $MaxChars -Source $Source)
}

# Declared examples for the injection budget. Returns findings as strings and never throws. Fully hermetic:
# every input is a literal, nothing is read or written. Each case states what must hold about the RESULT
# rather than an exact string, because the notice text carries a computed count.
function Test-ScaffoldContextLimitExamples {
  [CmdletBinding()]
  param([ValidateSet('no-truncation')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $long = ('H' * 60) + ('m' * 400) + ('T' * 60)      # head / middle / tail, 520 chars
  $straddle = ('a' * 99) + '</b>' + ('c' * 99)        # the closing sequence sits near the midpoint
  $cases = @(
    @{ what = 'text under the cap is returned unchanged'; text = 'hello'; max = 100; check = { param($r) $r -eq 'hello' } }
    @{ what = 'a closing sequence is escaped even when nothing is cut'; text = 'a</b>c'; max = 100; check = { param($r) $r -eq 'a<\/b>c' } }
    @{ what = 'text over the cap comes back SHORTER than it went in'; text = $long; max = 200; check = { param($r) $r.Length -lt 520 } }
    @{ what = 'the TAIL survives the cut - NEXT-ACTION lives at the end of the block'; text = $long; max = 200; check = { param($r) $r.EndsWith('TTTTTTTTTT') } }
    @{ what = 'the HEAD survives the cut too'; text = $long; max = 200; check = { param($r) $r.StartsWith('HHHHHHHHHH') } }
    @{ what = 'the elision notice says how much went and where the full text is'; text = $long; max = 200; check = { param($r) ($r -match 'characters elided from the middle') -and ($r -match 'PROBE-SOURCE') } }
    @{ what = 'escaping happens BEFORE the cut, so no raw closing sequence can survive anywhere'; text = $straddle; max = 60; check = { param($r) $r -notmatch '</' } }
    @{ what = 'empty text stays empty'; text = ''; max = 100; check = { param($r) $r -eq '' } }
  )
  $findings = @()
  foreach ($c in $cases) {
    $r = Test-ScaffoldContextLimitVia $c.text $c.max 'PROBE-SOURCE' $v
    if (-not (& $c.check $r)) { $findings += "[CTX-LIMIT-EXAMPLE] case '$($c.what)' did not hold - an unbounded or wrongly-cut fragment is what the harness spills to disk and replaces with a preview that stops mid-sentence (T87). [FIX] fix Limit-InjectedText, never the example." }
  }
  return $findings
}

# -- T181-HANDOFF-ANCHOR-ONCE (TD176): what IS a HANDOFF field line? Decided here, once. --
# Until this card the anchor was written FOUR times: in handoff.ps1's Get-Fields, which is what `check`
# parses fields with, and three more times below in the judgements that must see exactly the fields the
# checker sees. They agreed only because the four literals happened to be identical - a coincidence any
# diff can end. Widen one (a digit in a field name, a leading bullet, a lowercase key) and the two halves
# silently disagree about what a field IS: a value the parser accepts becomes invisible to the guards, or a
# guard objects to a line the parser never accepted. Nothing goes red either way, which is why the gate
# that protects this (selftest 17w') is structural rather than behavioural - a VERBATIM copy behaves
# identically, so no fixture can see it.
#
# Returns $null for a line that is not a field line; otherwise the Key and the TRIMMED Value. The trim
# lives here for the same reason the anchor does: every consumer already applied it, so a trim in one place
# cannot drift either. It is also exactly the value a resuming session receives, which is what keeps the
# raw-TAB judgement below neither wider nor narrower than the thing it judges.
# Pure: no IO, no git, no _config.
function Get-ScaffoldHandoffFieldMatch {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Line)
  $mm = [regex]::Match($Line, '^\s*([A-Z][A-Z\-]+):\s*(.*)$')
  if (-not $mm.Success) { return $null }
  return [pscustomobject]@{ Key = $mm.Groups[1].Value; Value = $mm.Groups[2].Value.Trim() }
}

# Handoff retains the card validator's ordinary whole-value rule. The initializer also contains
# nested placeholder examples, where the inner angle token prevents the line rule from reaching its
# end anchor; they remain placeholders only when the entire value is framed by the outer pair.
function Test-ScaffoldHandoffValuePlaceholder {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Key,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Value
  )
  if (Test-ScaffoldFieldPlaceholder -Key $Key -Value $Value) { return $true }
  return $Value.StartsWith('<') -and $Value.EndsWith('>')
}

# -- T140-HANDOFF-FIELD-SURVIVAL: what does the RESUMING SESSION actually receive? --
# handoff.ps1 check validates the FILE; the next session receives the INJECTION. Those are two different
# strings the moment the block outgrows the cap, and the divergence is silent. Measured on the live
# progress.md: block 8721 chars -> injected 4076, and six of the twelve operative fields (BRANCH, WORKTREE,
# LAST-GREEN, NEXT-ACTION, VERIFY, DO-NOT) fell in the elided middle while check printed PASS. The head+tail
# elision above is CORRECT code - it keeps the tail precisely because NEXT-ACTION sits at the end - the
# block simply outgrew it. What was missing is anyone asking the question.
#
# Pure: no IO, no git. It judges the text it is handed by running the real Limit-InjectedText over it, so
# the cap and the elision are REUSED, never restated. -MaxChars is passed through only when a caller
# supplies one, which is what lets the examples drive a small cap without naming the constant here.
function Get-ScaffoldElidedHandoffField {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$BlockText,
    [Parameter(Mandatory)][string[]]$RequiredField,
    [int]$MaxChars
  )
  $limitArgs = @{ Text = $BlockText; Source = 'progress.md' }
  if ($PSBoundParameters.ContainsKey('MaxChars')) { $limitArgs['MaxChars'] = $MaxChars }
  $injected = Limit-InjectedText @limitArgs
  $lost = [System.Collections.Generic.List[string]]::new()
  foreach ($k in $RequiredField) {
    # Same grammar as Get-ScaffoldHandoffFieldMatch above, expressed per FIELD rather than per line: a field
    # counts as received only as a line-leading LABEL:. A label the elision cut in half therefore stops
    # counting, which is the whole point. Deliberately NOT folded into the shared matcher (TD176 excludes it
    # by name): this asks whether ONE NAMED field survived the cut, not whether a given line is a field.
    $anchor = '(?m)^\s*' + [regex]::Escape($k) + ':'
    if ($injected -notmatch $anchor) { $lost.Add($k) }
  }
  return @($lost)
}

# Companion judgement, same surface: Get-Fields assigns into an ordered hashtable, so a field written twice
# is LAST-WIN and check silently validates the last one. The live progress.md carried two CARD: lines and
# check was reading the stale one. Reported, never auto-resolved - which of the two is current is the
# author's call, not the parser's.
function Get-ScaffoldDuplicateHandoffKey {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$BlockText)
  $seen = [ordered]@{}
  foreach ($line in ($BlockText -split "`r?`n")) {
    $fm = Get-ScaffoldHandoffFieldMatch -Line $line
    if ($fm) {
      $k = $fm.Key
      if ($seen.Contains($k)) { $seen[$k] = [int]$seen[$k] + 1 } else { $seen[$k] = 1 }
    }
  }
  return @(@($seen.Keys) | Where-Object { [int]$seen[$_] -gt 1 })
}

# T179/TD170: third judgement on the same surface, and the only one that looks at CHARACTERS rather than
# semantics. Everything else here asks whether a value is present, filled, concrete and still live; none of
# it asks whether the value is made of characters an author could have typed. A raw TAB is not one - it is
# what an interpreter leaves behind after eating a backslash, so scripts\task.ps1 arrives as scripts + TAB +
# ask.ps1 (L17). The field still parses, so check printed PASS twice on this repo's own progress.md (T140,
# T173) while the resuming session received a NEXT-ACTION command that could not run.
# Naming and echoing only: which real path the author meant is not machine-decidable, so nothing is repaired.
function Get-ScaffoldTabbedHandoffField {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$BlockText)
  $found = [System.Collections.Generic.List[string]]::new()
  foreach ($line in ($BlockText -split "`r?`n")) {
    # The shared matcher, so this judges the same lines the checker parses and the same trimmed value a
    # resuming session actually receives - no wider (T181/TD176). A leading or trailing TAB is whitespace
    # the parser already drops, which is why the trim belongs to the grammar rather than to this rule.
    $fm = Get-ScaffoldHandoffFieldMatch -Line $line
    if (-not $fm) { continue }
    $val = $fm.Value
    if (-not $val.Contains("`t")) { continue }
    # Rendered, never echoed raw: a TAB is invisible in a terminal, which is precisely why the mangling
    # survived review both times it landed. A finding a reader cannot see is not a finding.
    $shown = $val -replace "`t", '[TAB]'
    $found.Add("$($fm.Key): $shown")
  }
  return @($found)
}

# T179/TD170: the negative control for the character comparison above. 'ignore-tab' keeps the parse and the
# rendering and drops ONLY the comparison, so every value reads as clean and the examples must flip. Without
# it the base arm's green would be equally well explained by the parse alone.
function Get-ScaffoldTabbedHandoffFieldVia($BlockText, $Variant) {
  if ($Variant -ne 'ignore-tab') { return @(Get-ScaffoldTabbedHandoffField -BlockText $BlockText) }
  foreach ($line in ($BlockText -split "`r?`n")) { $null = Get-ScaffoldHandoffFieldMatch -Line $line }
  return @()
}

# T173/TD165: the negative control for the membership decision above. This predicate had a bare param()
# until this card, so its base arm's silence could not be told apart from a dead judgement - which is the
# same reason it sat outside the suite for so long (its own card's DoD was the only thing that ever called
# it, and a DoD is archived after merge). 'ignore-required-set' keeps the elision - the cut is not what is
# under test - and drops ONLY the comparison of the required set against the injected text, so every field
# reads as received. The examples must then flip; a control that cannot flip proves nothing about the base.
function Get-ScaffoldElidedHandoffFieldVia($BlockText, $RequiredField, $MaxChars, $Variant) {
  $callArgs = @{ BlockText = $BlockText; RequiredField = $RequiredField }
  if ($null -ne $MaxChars) { $callArgs['MaxChars'] = $MaxChars }
  if ($Variant -ne 'ignore-required-set') { return @(Get-ScaffoldElidedHandoffField @callArgs) }
  $limitArgs = @{ Text = $BlockText; Source = 'progress.md' }
  if ($null -ne $MaxChars) { $limitArgs['MaxChars'] = $MaxChars }
  $null = Limit-InjectedText @limitArgs
  return @()
}

function Test-ScaffoldHandoffFieldExamples {
  <#
  .SYNOPSIS  T140 arms 5/6: declared examples for the two handoff-survival judgements. Returns findings;
             an empty result is green. Hermetic - synthetic text only, no file and no git.
  .DESCRIPTION
    Both directions are carried. The negative direction (a short block where everything survives, a block
    with no repeats) is the half that keeps these checks from being a permanent red, and it is the half an
    inverted membership test breaks first.
    -Variant 'ignore-required-set' drops the membership comparison and must produce a finding (T173).
    -Variant 'ignore-tab' drops the raw-TAB comparison and must produce a finding (T179).
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-required-set', 'ignore-tab')][string]$Variant)
  $req = @('STATUS', 'TASK', 'NEXT-ACTION')
  $findings = @()

  # 1. Negative direction: a short block is returned untouched, so every field survives.
  $short = "STATUS: done`nTASK: a small thing`nNEXT-ACTION: run the thing"
  $got = @(Get-ScaffoldElidedHandoffFieldVia $short $req $null $Variant)
  if ($got.Count -ne 0) { $findings += "[HANDOFF-FIELD-EXAMPLE] a block under the cap lost fields ($($got -join ', ')) - nothing is elided below the cap, so silence is the only correct answer here. [FIX] fix the rule, never the example." }

  # 2. The live failure, in miniature: the middle is elided, so a middle field stops arriving while the
  #    first and last still do. This is exactly the 8721 -> 4076 state measured on progress.md.
  # TASK must sit in the MIDDLE to be elided: head+tail each keep MaxChars/2, so a label near the very
  # start survives in the head even when its value is cut. This predicate judges the LABEL - the same thing
  # Get-Fields parses - so the padding is what puts TASK out of both retained ends.
  $long = "STATUS: done`n" + ('x' * 100) + "`nTASK: the middle field`n" + ('y' * 100) + "`nNEXT-ACTION: run the thing"
  $got2 = @(Get-ScaffoldElidedHandoffFieldVia $long $req 120 $Variant)
  if ($got2 -notcontains 'TASK') { $findings += '[HANDOFF-FIELD-EXAMPLE] a field buried in the elided middle was reported as surviving - this is the defect the card exists for: check says PASS while the resuming session never sees the field.' }
  if ($got2 -contains 'STATUS' -or $got2 -contains 'NEXT-ACTION') { $findings += "[HANDOFF-FIELD-EXAMPLE] head+tail elision keeps BOTH ends, so a first field and a last field must both survive; got lost=($($got2 -join ', '))." }

  # 3. Duplicate keys, both directions.
  $dup = "STATUS: done`nCARD: old-one`nTASK: a thing`nCARD: current-one"
  $gotD = @(Get-ScaffoldDuplicateHandoffKey -BlockText $dup)
  if ($gotD -notcontains 'CARD') { $findings += '[HANDOFF-DUP-EXAMPLE] a key written twice went unreported - Get-Fields is last-win, so check would silently validate the stale value (measured live: two CARD: lines in progress.md).' }
  if (@(Get-ScaffoldDuplicateHandoffKey -BlockText $short).Count -ne 0) { $findings += '[HANDOFF-DUP-EXAMPLE] a block with no repeated key was reported as having one.' }

  # 4. The mangled-path direction (T179/TD170). The backtick-t below is not decoration: it produces exactly
  #    the byte an interpreter leaves where a backslash was, so this literal IS scripts\task.ps1 after the
  #    mangling that landed twice on the live file. The value parses, so every judgement above stays silent.
  $tabbed = "STATUS: in-progress`nTASK: a thing`nNEXT-ACTION: pwsh -File scripts`task.ps1"
  $gotT = @(Get-ScaffoldTabbedHandoffFieldVia $tabbed $Variant)
  if (($gotT -join ' ') -notmatch 'NEXT-ACTION') { $findings += '[HANDOFF-TAB-EXAMPLE] a raw TAB inside a field value went unreported - the field parses, so nothing else here objects, and check would hand the next session a command that cannot run (measured twice live: T140, T173).' }
  if (($gotT -join ' ') -notmatch '\[TAB\]') { $findings += '[HANDOFF-TAB-EXAMPLE] the finding did not render the TAB visibly - the character is invisible in a terminal, which is why this mangling survived review both times it landed.' }
  # 5. Clean direction. Deliberately NOT routed through the variant: the control must flip arm 4, not
  #    manufacture a finding here, and a legitimate value must stay writable in both runs.
  if (@(Get-ScaffoldTabbedHandoffField -BlockText $short).Count -ne 0) { $findings += '[HANDOFF-TAB-EXAMPLE] a block whose values carry no TAB was reported as carrying one.' }

  return $findings
}

# -- T230-HANDOFF-STATUS-SINGLE-SOURCE (TD230): which STATUS values exist, and which of them mean CLOSED? --
# Until this card there was no declaration - there were four literals that agreed by coincidence. handoff.ps1
# assigned the four values and validated STATUS against them; handoff.ps1 also wrote the same four into every
# generated progress.md as a guidance comment; and triage.ps1's handoff-open probe carried its own copy of the
# TERMINAL pair, twice, deciding for itself when a handoff was closed.
#
# The two halves fail differently, and that asymmetry is the whole reason this is one declaration rather than
# a convention. Rename or add a status and the VALIDATOR reds loudly, in the caller's face. The PROBE does
# not: it keeps testing membership of a set that no longer describes the enum, classifies an in-flight handoff
# as closed, and goes quiet. A probe that has gone quiet is indistinguishable from a probe with nothing to
# report, so the heartbeat looks healthy precisely while it is missing the thing it exists to catch.
#
# Terminal is a FLAG on the row, not a second list, so "is this status closed?" cannot be answered about a
# status that does not exist - the subset relation is structural rather than something a reviewer has to
# re-check. A downstream project that wants a fifth status edits these four rows and nothing else.
# Pure: no IO, no git, no _config. Standard: docs/HANDOFF.md.
function Get-ScaffoldHandoffStatusRules {
  @(
    @{ Name = 'in-progress';   Terminal = $false }
    @{ Name = 'blocked';       Terminal = $false }
    @{ Name = 'handoff-ready'; Terminal = $true }
    @{ Name = 'done';          Terminal = $true }
  )
}

# Every legal STATUS value, in declaration order - handoff.ps1 validates against exactly this.
function Get-ScaffoldHandoffStatusEnum {
  $rows = @(Get-ScaffoldHandoffStatusRules)
  if ($rows.Count -eq 0) { throw 'Get-ScaffoldHandoffStatusEnum: Get-ScaffoldHandoffStatusRules is empty - the declared table is the only source of the STATUS enum and there is deliberately no fallback copy.' }
  return @($rows | ForEach-Object { $_.Name })
}

# The subset that means "this handoff is closed" - triage.ps1's handoff-open probe reads exactly this.
# Deriving it rather than listing it is the point: a fifth status is non-terminal until someone says
# otherwise on its own row, which is the safe default (reported as open, never silently swallowed).
function Get-ScaffoldHandoffTerminalStatus {
  $rows = @(Get-ScaffoldHandoffStatusRules | Where-Object { $_.Terminal })
  if ($rows.Count -eq 0) { throw 'Get-ScaffoldHandoffTerminalStatus: no row in Get-ScaffoldHandoffStatusRules is marked Terminal - with none, every handoff would read as open forever and the probe would be permanent noise.' }
  return @($rows | ForEach-Object { $_.Name })
}

# -- T230-HANDOFF-BLOCK-ONE-RULE / TD220: WHICH HANDOFF block is authoritative --
# One decision, one declaration. progress.md can legitimately carry more than one HANDOFF block: two
# sessions sharing one main checkout append rather than edit in place, the file is gitignored, and so
# there is no merge, no conflict marker and no history to object. The rule that resolves it - the LAST
# block wins - was discovered once (TD57/TD-120) and then written three times: handoff.ps1 Read-Block
# took the last, triage.ps1 re-implemented the same thing by hand, and the SessionStart injector kept
# the singular [regex]::Match it was born with and took the FIRST.
#
# The cost is not cosmetic and it is not hypothetical. Measured live on 2026-08-31 (L303): the injected
# recovered-state named one card's NEXT-ACTION while handoff.ps1 check printed PASS over a different
# block. A resuming session acts on unvalidated text and trusts a PASS earned by other text, and BOTH
# halves report success - there is no signal anywhere that they disagreed.
#
# This is the sibling of Get-ScaffoldHandoffFieldMatch above and follows its contract exactly: the
# pattern is declared here and nowhere else, every consumer calls it, and selftest's
# [HANDOFF-BLOCK-ONCE] holds both halves - structurally, by reading this anchor out of the live
# function via the AST, and behaviourally, by driving the real hook over a two-block fixture.
#
# The pattern itself, declared ONCE and nowhere else in this repo. It is split from the selection below
# so that the selection and its negative control can differ in exactly one respect - WHICH match is
# taken - rather than each carrying its own copy of the regex. A control that restated the pattern would
# be the second body this card exists to remove, and selftest [HANDOFF-BLOCK-ONCE] arm A counts literals,
# so it would also turn that arm red. Returns all matches in document order; empty when there are none.
function Get-ScaffoldHandoffBlockMatch {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  return [regex]::Matches($Text, '(?s)<!--\s*HANDOFF:START\s*-->(.*?)<!--\s*HANDOFF:END\s*-->')
}

# Returns the LAST block's body verbatim (untrimmed - callers differ on whether they want it trimmed,
# and trimming here would silently change what handoff.ps1's emptiness test sees). Returns '' when the
# text carries no block at all, so a caller can treat "no block" and "empty block" the same way it
# always has. Pure: no IO, no git, no _config.
function Get-ScaffoldHandoffBlock {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $ms = @(Get-ScaffoldHandoffBlockMatch -Text $Text)
  if ($ms.Count -eq 0) { return '' }
  return $ms[$ms.Count - 1].Groups[1].Value
}

# The negative control. 'first-match' reproduces the SessionStart injector as it stood before this card:
# the FIRST block rather than the last, which is what a singular [regex]::Match with a lazy group returns
# and is the shape TD220 names as the rejected one. It runs the SAME matcher as the base and differs in
# exactly one respect - the index it takes - so a change to the pattern moves both arms together and the
# control can only ever fail for the reason it names.
function Get-ScaffoldHandoffBlockVia($Text, $Variant) {
  if ($Variant -ne 'first-match') { return (Get-ScaffoldHandoffBlock -Text $Text) }
  $ms = @(Get-ScaffoldHandoffBlockMatch -Text $Text)
  if ($ms.Count -eq 0) { return '' }
  return $ms[0].Groups[1].Value
}

function Test-ScaffoldHandoffBlockExamples {
  <#
  .SYNOPSIS  T230 arm: declared examples for the block-selection decision. Returns findings; an empty
             result is green. Hermetic - synthetic text only, no file and no git.
  .DESCRIPTION
    Both directions are carried. The two-block example is the live defect in miniature and is the only
    one the variant can flip; the zero-, one- and whitespace-block examples are what keep the rule from
    being satisfied by a function that simply always returns the last thing it can find, or nothing.
    -Variant 'first-match' selects the FIRST block instead of the last and must produce a finding.
    Every value read out of the core goes through "$( ... )" before it is examined, which always
    yields a string. That is not defensive noise, and the exact form matters: a BROKEN core is the
    state this table exists to report on, and a broken core RETURNS NOTHING. [string](f) does NOT
    save you there - casting an EMPTY result gives $null, not '' (only [string]$null gives ''), so
    the cast reads as safe, prints as empty, and still throws on the next method call. Measured
    while fixing this very table. A predicate that throws instead of
    returning a finding takes the whole gate run down with it - 17z aborts and every verdict after it
    in the region is lost, including the ones that had already caught the defect. Measured (T230): the
    mutation batch deleted the last-index read, this table threw, selftest never reached its own
    summary line, and the entry was recorded BAD-EVIDENCE - red for the wrong reason (L167) over a
    mutation [HANDOFF-BLOCK-ONCE] had in fact already caught. The contract is findings, never
    exceptions.
  #>
  [CmdletBinding()]
  param([ValidateSet('first-match')][string]$Variant)
  $findings = @()

  # 1. The live failure (L303/TD220) in miniature: two blocks whose bodies differ. The last one wins,
  #    because it is the one handoff.ps1 check validated. This is the arm the variant flips.
  $two = "# progress`n`n<!-- HANDOFF:START -->`nSTATUS: done`nTASK: stale first block`n<!-- HANDOFF:END -->`n`n<!-- HANDOFF:START -->`nSTATUS: in-progress`nTASK: fresh last block`n<!-- HANDOFF:END -->`n"
  $gotTwo = "$(Get-ScaffoldHandoffBlockVia $two $Variant)"
  if ($gotTwo -notmatch 'fresh last block') { $findings += '[HANDOFF-BLOCK-EXAMPLE] with two blocks present the LAST one was not returned - this is TD220 exactly: the resuming session is handed text that handoff.ps1 check never validated, and both halves still report success.' }
  if ($gotTwo -match 'stale first block') { $findings += '[HANDOFF-BLOCK-EXAMPLE] the FIRST (stale) block was returned. A singular [regex]::Match with a lazy group does this, which is what the SessionStart injector did until T230.' }

  # 2. Exactly one block: the ordinary case. Last-of-one is that one, and a rule that only ever looked
  #    at the tail of the file would still pass here - which is why example 1 exists beside it.
  $one = "<!-- HANDOFF:START -->`nSTATUS: in-progress`nTASK: only block`n<!-- HANDOFF:END -->"
  if ("$(Get-ScaffoldHandoffBlock -Text $one)" -notmatch 'only block') { $findings += '[HANDOFF-BLOCK-EXAMPLE] a file carrying exactly one block did not return it - the common path is broken, which no multi-block example would have caught.' }

  # 3. No block at all. Must be empty rather than $null: every caller tests emptiness, and a $null here
  #    would throw under Set-StrictMode in handoff.ps1 before the caller's own message could be printed.
  $none = "# progress`n`nnothing here yet`n"
  if ("$(Get-ScaffoldHandoffBlock -Text $none)" -ne '') { $findings += '[HANDOFF-BLOCK-EXAMPLE] text carrying no HANDOFF block returned something other than the empty string - callers distinguish "no block" from "a block with nothing in it" by their own emptiness test, and this must not decide it for them.' }

  # 4. A block whose body is whitespace only. It IS the last block and must be returned as such - the
  #    judgement "this handoff is empty" belongs to handoff.ps1's own check, not to the selection.
  #    Deliberately NOT routed through the variant: with a single block the two paths agree, so a
  #    finding here would come from the selection being broken outright rather than from the control.
  $blank = "<!-- HANDOFF:START -->`n   `n<!-- HANDOFF:END -->"
  $gotBlank = "$(Get-ScaffoldHandoffBlock -Text $blank)"
  if ($gotBlank -eq '') { $findings += '[HANDOFF-BLOCK-EXAMPLE] a block whose body is whitespace was reported as no block at all - selection must not silently skip it, or a session that wrote an empty handoff would be handed the previous, stale one instead of being told its handoff is empty.' }
  if ($gotBlank.Trim() -ne '') { $findings += '[HANDOFF-BLOCK-EXAMPLE] the body of a whitespace-only block came back with content in it - the group is being captured wider than the block.' }

  return $findings
}
