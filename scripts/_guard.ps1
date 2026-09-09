#requires -Version 7
<#
.SYNOPSIS
  个人账号守卫：本项目的所有 GitHub 操作**仅限配置的个人账号**，禁止其它/组织账号。

.DESCRIPTION
  在任何 gh 写操作（建仓 / push / PR / 回贴状态）前调用 Assert-PersonalAccount。
  - 先清除会话里可能无效/串号的 GH_TOKEN/GITHUB_TOKEN（强制走 keyring）。
  - 校验 `gh api user` 的登录名 == scripts\_config.ps1 的 GhAccount；不符即抛错中止。
  - 可选校验 origin 远端 owner 也为该个人账号，防止误推到组织仓。
  期望账号来自 scripts\_config.ps1（单一配置点）；未配置即 fail-closed（见 Get-ScaffoldGhAccount）。

  Also holds Test-ScaffoldDocBudget (T89-DOCBUDGETS): the standing docs' character ceilings are declared in
  scripts\_config.ps1 (DocBudgets) and judged here, so the manifest stays data and the judgement stays testable.

  Also holds Get-ScaffoldAdrFile / Test-ScaffoldAdrFormat (T90-ADR-FORMAT): the one checked header contract for
  architecture decision records. scripts\check-adr.ps1 is the thin CLI over them, the same split check-scope.ps1
  uses over _scope.ps1 - the judgement lives here once so the gate and the CLI cannot drift into two verdicts.

  Also holds Get-ScaffoldGateMapRow / Get-ScaffoldGateMapValidToken / Test-ScaffoldGateMap (T95-GATE-MAP): the
  reader and the checker for the change-surface to -Only token table published in scripts\selftest.ps1's header,
  enforced by sub-gate 14i. The table is a reading aid, never a router (ADR 0007), and it does not move the
  acceptance bar - the full unfiltered run stays the only pre-merge proof of the 17 gates.
#>

. (Join-Path $PSScriptRoot '_config.ps1')
# _lessons.ps1 is a LEAF core (it dot-sources nothing), so this cannot cycle. Sourced rather than probed:
# Get-ScaffoldLessonTierGuardError below judges enforced_by through [ENFORCED-BY-GRAMMAR], and a graceful
# "predicate missing, fall back to nonempty" would silently restore the exact bug the grammar removes.
. (Join-Path $PSScriptRoot '_lessons.ps1')

function Test-ScaffoldDocBudget {
  <#
  .SYNOPSIS  T89-DOCBUDGETS: judge standing docs against their declared character ceilings. Returns findings;
             an empty result is green.
  .DESCRIPTION
    No arguments => walks the declared manifest (Get-ScaffoldDocBudgets). An empty manifest yields no findings,
    which is how a freshly initialized downstream keeps the gate off.
    -Path with -MaxChars => judges that one pair, which is what makes the checker itself testable.
    A budgeted path that does not exist is a FINDING, not a skip: a doc renamed without updating the manifest
    is exactly the drift the manifest exists to catch.
    Size is a character count over the decoded UTF-8 text, never bytes: CJK codepoints are three bytes each,
    so a byte count would penalise the Chinese-heavy docs roughly threefold against the English ones.
    Each finding names its own fix (failure-message-is-the-fix): relocate or condense first, and raising a
    ceiling needs a stated reason.
  #>
  [CmdletBinding()]
  param(
    [string]$Path,
    [int]$MaxChars
  )
  $repoRoot = Split-Path $PSScriptRoot -Parent
  $budgets = if ($Path) { @{ $Path = $MaxChars } } else { Get-ScaffoldDocBudgets }

  $findings = [System.Collections.Generic.List[string]]::new()
  foreach ($rel in @($budgets.Keys)) {
    $max = [int]$budgets[$rel]
    $full = Join-Path $repoRoot $rel
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
      $findings.Add("$rel is budgeted but does not exist - it was renamed or deleted without updating DocBudgets in scripts/_config.ps1. Point the entry at the new path, or drop it if the doc is gone.")
      continue
    }
    try {
      $chars = [System.IO.File]::ReadAllText($full).Length
      if ($chars -gt $max) {
        $findings.Add("$rel is $chars characters, $($chars - $max) over its $max ceiling. First option: relocate the content into a docs/ file or condense it. Raising the ceiling in DocBudgets (scripts/_config.ps1) is allowed but requires a stated reason in the PR that raises it.")
      }
    }
    catch {
      $findings.Add("$rel is budgeted but could not be measured: $($_.Exception.Message)")
    }
  }
  return @($findings)
}

function Get-ScaffoldAdrFile {
  <#
  .SYNOPSIS  T90-ADR-FORMAT: enumerate the architecture decision records under docs/adr.
  .DESCRIPTION
    A record is a file whose name starts with the four-digit number docs/adr/README.md already mandates.
    That prefix is what separates records from the index README, rather than an exclusion list that would
    have to be extended every time a non-record file lands in the directory.
    -Path redirects the walk, which is what lets a fixture judge a temporary tree instead of the real one.
    A missing directory yields an empty result rather than throwing, so a downstream project that has not
    written its first ADR still runs the gate green.
  #>
  [CmdletBinding()]
  param([string]$Path)
  $dir = if ($Path) { $Path } else { Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/adr' }
  if (-not (Test-Path -LiteralPath $dir -PathType Container)) { return @() }
  return @(Get-ChildItem -LiteralPath $dir -Filter '*.md' -File |
    Where-Object { $_.Name -match '^\d{4}-' } |
    Sort-Object Name)
}

function Test-ScaffoldAdrFormat {
  <#
  .SYNOPSIS  T90-ADR-FORMAT: judge ADRs against the header contract. Returns findings; empty is green.
  .DESCRIPTION
    Two rules, both chosen because they pay for themselves and neither needs a lifecycle folder tree:

      1. A parseable status. New records use `- Status: <value>` in the header list.
         Before this gate the nine records carried four different syntaxes (a blockquote `> 状态：accepted`,
         `- 状态：已接受（Accepted）`, `- Status: accepted` and `- **Status**: accepted`), so nothing could
         read the status of a record without a human looking at it.
      2. A recorded alternative, because a decision recorded without what it beat invites re-litigation -
         which is the failure a decision record exists to prevent.

    The alternatives heading is accepted in BOTH the English and the Chinese form. That is not tidiness:
    four records use `## 备选方案` and the English-first rule explicitly forbids retroactively converting
    untouched lines, so the checker meets those records where they are. MyInspection's existing dated
    status headers and two alternatives headings are also recognized below. Those append-only decisions
    predate this template contract; importing it must not rewrite their approved status or invent rationale.

    The waiver exists because the alternative is worse. An agent told to satisfy this gate on a record whose
    rationale was never written down will otherwise invent plausible alternatives, and a fabricated rationale
    does more damage than an absent one. A record that never recorded its alternatives carries the marker
    instead, which is honest and greppable.

    No arguments => walks the real docs/adr tree. -Name with -Text judges one record in memory, which is what
    makes the checker testable without planting files. -Path redirects the walk for a fixture tree.
  #>
  [CmdletBinding()]
  param(
    [string]$Name,
    [string]$Text,
    [string]$Path
  )

  # (?m) with a trailing [ \t\r]* so a CRLF checkout does not leave a stray carriage return outside the
  # match - the defect class TD143/L231 records, where an over-wide \s* hid the problem until it was narrowed.
  $statusPattern = '(?m)^-[ \t]+Status:[ \t]*\S'
  $datedStatusPattern = '(?m)^(?:日期：|Date:)[ \t]*\d{4}-\d{2}-\d{2}[ \t]+·[ \t]+(?:状态：|Status:)[ \t]*(?:\*\*)?(?:accepted|proposed|superseded|rejected)\b'
  $altPatterns = @(
    '(?m)^##[ \t]+Alternatives considered[ \t\r]*$'
    '(?m)^##[ \t]+备选方案[ \t\r]*$'
    '(?m)^##[ \t]+Rejected alternatives[ \t\r]*$'
    '(?m)^##[ \t]+批准决定与备选[ \t\r]*$'
  )
  $waiverMarker = 'adr-format: alternatives-not-recorded'

  $records = if ($PSBoundParameters.ContainsKey('Text')) {
    @(@{ Name = $(if ($Name) { $Name } else { '(inline)' }); Text = $Text })
  }
  else {
    @(Get-ScaffoldAdrFile -Path $Path | ForEach-Object {
        @{ Name = $_.Name; Text = [System.IO.File]::ReadAllText($_.FullName) }
      })
  }

  $findings = [System.Collections.Generic.List[string]]::new()
  foreach ($rec in $records) {
    $recName = [string]$rec.Name
    $recText = [string]$rec.Text

    $recHeader = ($recText -split '(?m)^##[ \t]', 2)[0]
    if ($recText -notmatch $statusPattern -and $recHeader -notmatch $datedStatusPattern) {
      $findings.Add("$recName has no parseable status. New records use a '- Status: Accepted' line (or Proposed, Superseded, Rejected) directly under the title. Existing dated MyInspection headers are preserved; body prose or a blockquote does not establish header status.")
    }

    # Kept as three separate statements so each rule is its own single-line-deletion mutation target (L165):
    # drop the alias line and the four Chinese-headed records go red; drop the waiver line and the escape hatch
    # stops working; drop the Add below and the rule stops firing at all.
    $hasAlternatives = $false
    foreach ($p in $altPatterns) { if ($recText -match $p) { $hasAlternatives = $true; break } }
    if ($recText.Contains($waiverMarker)) { $hasAlternatives = $true }
    if (-not $hasAlternatives) {
      $findings.Add("$recName records a decision without what it beat. Add an '## Alternatives considered' section (or the Chinese '## 备选方案') saying what was rejected and why. If the alternatives were genuinely never written down, add the HTML-comment marker '$waiverMarker' instead - NEVER invent alternatives to satisfy this gate, because a fabricated rationale is worse than an absent one.")
    }
  }
  return @($findings)
}

function Get-ScaffoldGateMapRow {
  <#
  .SYNOPSIS  T95-GATE-MAP: read the change-surface to -Only token table published in scripts\selftest.ps1's
             header. Returns one object per row; an absent or unreadable table returns nothing.
  .DESCRIPTION
    This reader is the table's only consumer, and it consumes it to CHECK it, never to select gates for a
    diff - ADR 0007 records the owner's rejection of exactly that selector.
    The rows are delimited by the ASCII sentinels [GATE-MAP] and [GATE-MAP-END] so the parser can never
    wander into unrelated header prose, and the cell patterns exclude carriage return and newline
    explicitly instead of leaning on \s or a lazy dot: a per-line regex that lets a class cross the newline
    silently welds two rows into one, which is the defect class L230/TD143 records.
    A row whose token cell carries no digit is the markdown header or the separator line, not a row.
    -Path redirects the read, which is what lets a fixture judge a table it wrote instead of the real one.
  #>
  [CmdletBinding()]
  param([string]$Path)
  $file = if ($Path) { $Path } else { Join-Path $PSScriptRoot 'selftest.ps1' }
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return @() }
  $region = [regex]::Match([System.IO.File]::ReadAllText($file), '(?s)\[GATE-MAP\](?<body>.*?)\[GATE-MAP-END\]')
  if (-not $region.Success) { return @() }
  $rowMatches = [regex]::Matches($region.Groups['body'].Value, '(?m)^[ \t]*\|[ \t]*(?<surface>[^|\r\n]+?)[ \t]*\|[ \t]*(?<tokens>[^|\r\n]+?)[ \t]*\|')
  $rows = [System.Collections.Generic.List[psobject]]::new()
  foreach ($m in $rowMatches) {
    $cell = $m.Groups['tokens'].Value
    if ($cell -notmatch '\d') { continue }
    $rows.Add([pscustomobject]@{
        Surface = $m.Groups['surface'].Value
        Tokens  = @($cell -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
      })
  }
  return @($rows)
}

function Get-ScaffoldGateMapValidToken {
  <#
  .SYNOPSIS  T95-GATE-MAP: project the legal -Only tokens out of scripts\selftest.ps1's own id literal.
             Never re-declares that set.
  .DESCRIPTION
    Projecting rather than re-declaring is the whole point of the check that consumes this. A second copy of
    the id list would BE the drift the gate exists to prevent, so the only thing read here is the literal
    that selftest.ps1's own -Only parser validates against.
    The literal is read structurally - the numeric range it spans, plus the quoted ids beside it - rather
    than executed: Invoke-Expression against a file this same suite lints is a worse trade than a regex.
    The fallback is the numeric range alone, and it is deliberately incomplete. If the literal cannot be
    read, every non-numeric id goes invalid and the gate goes red, instead of quietly accepting anything.
    -Path redirects the read, matching Get-ScaffoldGateMapRow so a fixture judges one tree end to end.
  #>
  [CmdletBinding()]
  param([string]$Path)
  $numericOnly = @(1..17 | ForEach-Object { [string]$_ })
  $file = if ($Path) { $Path } else { Join-Path $PSScriptRoot 'selftest.ps1' }
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $numericOnly }
  $idsExpr = [regex]::Match([System.IO.File]::ReadAllText($file), '(?m)^[ \t]*\$onlyValidIds[ \t]*=[ \t]*(?<expr>[^\r\n]*)').Groups['expr'].Value
  if (-not $idsExpr) { return $numericOnly }
  $tokens = [System.Collections.Generic.List[string]]::new()
  foreach ($span in [regex]::Matches($idsExpr, '(?<lo>\d+)\.\.(?<hi>\d+)')) {
    foreach ($n in ([int]$span.Groups['lo'].Value)..([int]$span.Groups['hi'].Value)) { $tokens.Add([string]$n) }
  }
  foreach ($quoted in [regex]::Matches($idsExpr, "'(?<id>[^'\r\n]+)'")) { $tokens.Add($quoted.Groups['id'].Value) }
  if ($tokens.Count -eq 0) { return $numericOnly }
  return @($tokens | Sort-Object -Unique)
}

function Test-ScaffoldGateMap {
  <#
  .SYNOPSIS  T95-GATE-MAP: judge the published table's tokens against selftest.ps1's own -Only id literal.
             Returns findings; an empty result is green.
  .DESCRIPTION
    No arguments => every token of every published row is judged, which is the projection property: a gate
    renamed or retired without updating the table goes red rather than leaving the table quietly lying.
    -Token judges one token in isolation, which is what makes this checker testable without planting a
    table, and is how sub-gate 14i proves it can still fire before trusting its verdict on the real one.
    A finding names the offending token, the row that claimed it, and the ids that are legal RIGHT NOW,
    read live from the literal - the legal set is exactly the thing that moves, so it is never hardcoded
    into the failure text.
  #>
  [CmdletBinding()]
  param([string]$Token, [string]$Path)
  $valid = Get-ScaffoldGateMapValidToken -Path $Path
  $rows = if ($PSBoundParameters.ContainsKey('Token')) {
    @([pscustomobject]@{ Surface = 'the token handed to Test-ScaffoldGateMap'; Tokens = @($Token) })
  }
  else {
    @(Get-ScaffoldGateMapRow -Path $Path)
  }
  $findings = [System.Collections.Generic.List[string]]::new()
  foreach ($row in $rows) {
    foreach ($t in @($row.Tokens)) {
      if ($t -notin $valid) { $findings.Add("gate token '$t', claimed by the row [$($row.Surface)], is not a legal -Only id. Legal right now, read live from scripts/selftest.ps1: $($valid -join ', '). A gate was renamed or retired without updating the table in that file's header, or the row has a typo. Fix the row - never this checker, and never the id list.") }
    }
  }
  return @($findings)
}

# -- T274-TIER-ACCEPTANCE (ADR 0016 item 2): the [GATE-MAP] table's MACHINE half ------------------------
# T95 published the change-surface table as a reading aid and ADR 0007 recorded the owner's rejection of a
# router. ADR 0016 supersedes that rejected alternative - and only that one; keeping the meta suite off the
# PR critical path stands - because the measurement changed: 80% of the last 813 commits touch no code file
# at all, so the undifferentiated bar lands almost entirely on work carrying no blast radius.
#
# The table stays the thing a human reads. This list is the same rows expressed as DATA - a path pattern
# list per row plus that row's tokens - and sub-gate 14i holds the two representations EQUAL: same row
# count, same tokens per row, in order. Neither is derived from the other, on purpose. Deriving the routes
# by parsing the surface prose would make that prose a machine format and every clarifying edit a breaking
# change; deriving the prose from the routes would delete the sentence that says WHY those gates. So both
# are declared and the equality is CHECKED - the same trade Get-ScaffoldGateMapValidToken makes in the
# other direction by projecting the id list rather than restating it.
#
# Patterns are repo-relative, forward-slash, matched with -like against a changed path. A row may declare
# NO pattern: the 15fx row names a REGION inside scripts/selftest.ps1 rather than a surface of its own, so
# no changed path can route to it, and declaring an empty list is how that row keeps its place in the order
# 14i checks instead of vanishing from the data half.
function Get-ScaffoldGateRoutes {
  <#
  .SYNOPSIS  T274-TIER-ACCEPTANCE: the [GATE-MAP] rows as structured routes - one entry per published row,
             carrying that row's path patterns and its -Only tokens. Consumed by Get-ScaffoldTierGateSet.
  #>
  [CmdletBinding()]
  param()
  return @(
    [pscustomobject]@{ Surface = 'any scripts/*.ps1, any .claude/hooks/*.ps1'; Patterns = @('scripts/*.ps1', '.claude/hooks/*.ps1'); Tokens = @('1', '7') }
    [pscustomobject]@{ Surface = 'scripts/selftest.ps1 itself'; Patterns = @('scripts/selftest.ps1'); Tokens = @('14') }
    [pscustomobject]@{ Surface = 'scripts/task.ps1, verify.ps1, _scope.ps1, check-scope.ps1'; Patterns = @('scripts/task.ps1', 'scripts/verify.ps1', 'scripts/_scope.ps1', 'scripts/check-scope.ps1'); Tokens = @('15') }
    [pscustomobject]@{ Surface = 'the gate-15 fixture-contract region'; Patterns = @(); Tokens = @('15fx') }
    [pscustomobject]@{ Surface = 'scripts/check-cards.ps1, _cards.ps1, specs/tasks/*'; Patterns = @('scripts/check-cards.ps1', 'scripts/_cards.ps1', 'specs/tasks/*'); Tokens = @('10') }
    [pscustomobject]@{ Surface = 'scripts/lessons.ps1, docs/lessons/*, specs/archive/tasks/*, a lessons rule in CLAUDE.md'; Patterns = @('scripts/lessons.ps1', 'docs/lessons/*', 'specs/archive/tasks/*', 'CLAUDE.md'); Tokens = @('2', '16') }
    [pscustomobject]@{ Surface = 'scripts/review.ps1, check-secrets.ps1, check-licenses.ps1, triage.ps1'; Patterns = @('scripts/review.ps1', 'scripts/check-secrets.ps1', 'scripts/check-licenses.ps1', 'scripts/triage.ps1'); Tokens = @('6', '12', '13', '17') }
    [pscustomobject]@{ Surface = 'scripts/mutate.ps1, specs/mutations/*'; Patterns = @('scripts/mutate.ps1', 'specs/mutations/*'); Tokens = @('17ac', '14') }
    [pscustomobject]@{ Surface = '.claude/settings.json, .github/workflows/*.yml'; Patterns = @('.claude/settings.json', '.github/workflows/*.yml'); Tokens = @('8', '9', '14') }
    [pscustomobject]@{ Surface = '.claude/workflows/*.mjs'; Patterns = @('.claude/workflows/*.mjs'); Tokens = @('1', '8', '9', '14') }
    [pscustomobject]@{ Surface = 'CLAUDE.md, CLAUDE.template.md, TEMPLATE-README.md, docs/*.md, docs/adr/*, init-scaffold.ps1'; Patterns = @('CLAUDE.md', 'CLAUDE.template.md', 'TEMPLATE-README.md', 'docs/*.md', 'docs/adr/*', 'init-scaffold.ps1'); Tokens = @('3', '4', '5', '8', '11', '14', '16') }
    [pscustomobject]@{ Surface = '.claude/skills/* (a SKILL.md, a NOTICE.md, a whole skill directory)'; Patterns = @('.claude/skills/*'); Tokens = @('9', '14', '15', '16') }
    [pscustomobject]@{ Surface = 'specs/README.md, specs/mutations/README.md, specs/tech-debt-tracker.md, specs/archive/*.md'; Patterns = @('specs/README.md', 'specs/mutations/README.md', 'specs/tech-debt-tracker.md', 'specs/archive/*.md'); Tokens = @('8', '10', '12', '16') }
  )
}

function Test-ScaffoldGateRouteTable {
  <#
  .SYNOPSIS  T274-TIER-ACCEPTANCE: hold the published [GATE-MAP] table and Get-ScaffoldGateRoutes equal -
             same number of rows, same tokens per row, in order. Returns findings; an empty result is green.
  .DESCRIPTION
    Order matters and is checked rather than sorted away: the two halves are hand-kept, and a route list
    carrying the right tokens under the wrong surfaces routes real diffs to the wrong gates while every
    set-wise comparison stays quiet. -Path redirects the table read, so a fixture can judge a table it wrote.
  #>
  [CmdletBinding()]
  param([string]$Path)
  $rows = @(Get-ScaffoldGateMapRow -Path $Path)
  $routes = @(Get-ScaffoldGateRoutes)
  $findings = [System.Collections.Generic.List[string]]::new()
  if ($rows.Count -ne $routes.Count) {
    $findings.Add("the [GATE-MAP] table publishes $($rows.Count) row(s) while Get-ScaffoldGateRoutes declares $($routes.Count) route(s). Since ADR 0016 the table is BOTH the reading aid and the machine route, so a row with no route is a surface that silently escalates every diff to the full run, and a route with no row is a shrink nobody published. Add the missing half - the table lives in scripts/selftest.ps1's header, the routes in scripts/_guard.ps1.")
    return @($findings)
  }
  for ($i = 0; $i -lt $rows.Count; $i++) {
    $rowT = @($rows[$i].Tokens)
    $routeT = @($routes[$i].Tokens)
    if (($rowT -join ',') -ne ($routeT -join ',')) {
      $findings.Add("[GATE-MAP] row $($i + 1) [$($rows[$i].Surface.Trim())] publishes tokens '$($rowT -join ',')' while the route beside it [$($routes[$i].Surface)] declares '$($routeT -join ',')'. The published table is what a reader runs by hand and the route is what -TaskId runs for them; a difference means those two answers have quietly parted. Fix whichever half is wrong - they are declared separately so each can say what only it can say, never so they can disagree.")
    }
  }
  return @($findings)
}

# -- T274: tier + changed paths -> the gate set a card's acceptance owes --------------------------------
# ADR 0016 item 2. Three rules, written in the order they matter in:
#   S   => the FULL run, always. A card touching the merge path or an enforcer cannot buy a cheaper proof
#          by naming itself, so the tier carrying the exposure keeps today's bar byte for byte.
#   0   => the floor set and nothing else. ci.yml is the merge gate for a doc/card/ledger change; the floor
#          is the advised local ~20s, fixed rather than routed so it cannot shrink to nothing on a quiet diff.
#   1   => the floor PLUS every route a changed path matched, PLUS gate 7 for any .ps1 - every script lints,
#          and no row says that better than the file extension does.
# And the fail-safe that outranks all three for tier 1: a changed path matching NO route means the FULL run.
# The GATE-MAP header has said "an unlisted surface means run the full suite - never that no gate covers it"
# since T95; the router INHERITS that sentence as a rule instead of re-deciding it, so it can only ever
# shrink a run for paths a row names.
#
# Pure: lists in, verdict out, no repo and no configuration, so the declared examples can feed it states no
# real diff produces. Gates comes back sorted numerically with the lettered tokens after the numbers - one
# spelling per set, so a human comparing two sentinels by eye and a gate comparing them as strings agree.
function Get-ScaffoldTierGateSet {
  <#
  .SYNOPSIS  T274-TIER-ACCEPTANCE: the gate set a card owes, from its tier and its changed paths.
             Returns @{ Gates; Full; Unrouted }; Full means the whole suite, and then Gates is empty.
  .DESCRIPTION
    The rejected shapes are declared BY NAME so the examples can prove each one is still caught:
      ignore-unrouted - an unrouted changed path stops escalating, so a surface no row names runs a subset
                        and "unlisted" quietly comes to mean "uncovered" - the reading T95 refused.
      drop-floor      - the floor stops being added, so a tiny routed diff runs only its own row and the
                        always-on gates (lessons, cross-links, the secret scan, counts) run for nobody.
      subset-for-s    - Tier S takes the tier-1 path, which is exactly the cheaper proof a card would buy
                        by naming itself.
  #>
  [CmdletBinding()]
  param(
    [ValidateSet('S', '1', '0')][string]$Tier = '1',
    [string[]]$ChangedPaths = @(),
    [object[]]$Routes = @(),
    [ValidateSet('live', 'ignore-unrouted', 'drop-floor', 'subset-for-s')][string]$Variant = 'live'
  )
  # The always-on floor. Syntax, lessons, cross-links, the secret scan, counts and budgets, and the L-id
  # references: every one reads a surface no route can name, because its input is the whole tree rather
  # than the file that changed.
  $floor = @('1', '2', '11', '13', '14', '16')
  $paths = @(@($ChangedPaths) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { (([string]$_).Trim() -replace '\\', '/') })
  $routed = @()
  $unrouted = @()
  foreach ($p in $paths) {
    $hit = $false
    foreach ($r in @($Routes)) {
      foreach ($pat in @($r.Patterns)) {
        if ($p -like $pat) { $hit = $true; $routed += @($r.Tokens) }
      }
    }
    if (-not $hit) { $unrouted += $p }
  }
  if ($Tier -eq 'S' -and $Variant -ne 'subset-for-s') {
    return [pscustomobject]@{ Gates = @(); Full = $true; Unrouted = @($unrouted) }
  }
  $gates = @()
  if ($Variant -ne 'drop-floor') { $gates += $floor }
  if ($Tier -ne '0') {
    $gates += $routed
    if (@($paths | Where-Object { $_ -like '*.ps1' }).Count -gt 0) { $gates += '7' }
  }
  $ordered = @(@($gates | Where-Object { $_ -match '^\d+$' } | Select-Object -Unique | Sort-Object { [int]$_ })) + @(@($gates | Where-Object { $_ -notmatch '^\d+$' } | Select-Object -Unique | Sort-Object))
  # Tier 0 is the fixed floor whatever it touched, so an unrouted path does not escalate it: its merge bar
  # is ci.yml and this set is advice. Every other tier escalates, and that escalation is the fail-safe.
  $full = ($Tier -ne '0') -and ($unrouted.Count -gt 0) -and ($Variant -ne 'ignore-unrouted')
  if ($full) { return [pscustomobject]@{ Gates = @(); Full = $true; Unrouted = @($unrouted) } }
  return [pscustomobject]@{ Gates = @($ordered); Full = $false; Unrouted = @($unrouted) }
}

function Test-ScaffoldTierGateSetExamples {
  <#
  .SYNOPSIS  T274-TIER-ACCEPTANCE: declared examples for Get-ScaffoldTierGateSet. Returns findings; an
             empty result is green. Hermetic - a literal route list, so it reads no table and needs no repo.
  .DESCRIPTION
    Both directions, as 10t's table carries: the live rule must report nothing, and each -Variant must
    report at least one finding, so a table that stopped discriminating is visible instead of quietly green.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-unrouted', 'drop-floor', 'subset-for-s')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $routes = @(
    [pscustomobject]@{ Surface = 'scripts'; Patterns = @('scripts/*.ps1'); Tokens = @('1', '7') }
    [pscustomobject]@{ Surface = 'lessons'; Patterns = @('scripts/lessons.ps1', 'docs/lessons/*'); Tokens = @('2', '16') }
    [pscustomobject]@{ Surface = 'docs'; Patterns = @('docs/*.md'); Tokens = @('3', '11', '14') }
    [pscustomobject]@{ Surface = 'mutations'; Patterns = @('specs/mutations/*'); Tokens = @('17ac', '14') }
  )
  $findings = @()
  # 1. Tier S is the full run, and stays the full run on a diff every route covers - the cheap-looking
  #    change is exactly where a card would want the discount.
  $g1 = Get-ScaffoldTierGateSet -Tier 'S' -ChangedPaths @('docs/HANDOFF.md') -Routes $routes -Variant $v
  if (-not $g1.Full) { $findings += "[TIER-GATESET-EXAMPLE] a Tier-S card with a fully routed doc-only diff came back Full=$($g1.Full) with gates '$($g1.Gates -join ',')', expected the FULL run. Tier S is the tier that carries the blast radius; a card that can name itself into a subset has bought the cheaper proof ADR 0016 refuses it. [FIX] fix the rule, never the example." }
  # 2. Tier 0 is the floor, exactly and in order - never the floor plus whatever its paths routed to.
  $g2 = Get-ScaffoldTierGateSet -Tier '0' -ChangedPaths @('docs/HANDOFF.md', 'docs/lessons/x.md') -Routes $routes -Variant $v
  if ($g2.Full) { $findings += '[TIER-GATESET-EXAMPLE] a Tier-0 card came back Full, so the cheapest tier pays the most expensive proof and nobody will run it. [FIX] fix the rule, never the example.' }
  elseif (($g2.Gates -join ',') -ne '1,2,11,13,14,16') { $findings += "[TIER-GATESET-EXAMPLE] a Tier-0 card computed gates '$($g2.Gates -join ',')', expected exactly '1,2,11,13,14,16'. The floor is pinned as an ordered string on purpose: a gate cannot drop out of it unnoticed, and a routed gate cannot sneak into it either - Tier 0's merge bar is ci.yml and this set is the advised local one. [FIX] fix the rule, never the example." }
  # 3. Tier 1 on a routed script: floor + that row's tokens + gate 7, because every scripts/*.ps1 lints.
  $g3 = Get-ScaffoldTierGateSet -Tier '1' -ChangedPaths @('scripts/lessons.ps1') -Routes $routes -Variant $v
  if ($g3.Full) { $findings += '[TIER-GATESET-EXAMPLE] a Tier-1 card whose only changed path is routed by two rows came back Full, so routing bought nothing and every Tier-1 card pays the full run. [FIX] fix the rule, never the example.' }
  elseif (($g3.Gates -join ',') -ne '1,2,7,11,13,14,16') { $findings += "[TIER-GATESET-EXAMPLE] a Tier-1 diff touching scripts/lessons.ps1 computed '$($g3.Gates -join ',')', expected '1,2,7,11,13,14,16' - the floor, plus 1,7 and 2,16 from the two rows that name it, plus gate 7 for the .ps1, sorted numerically and deduplicated. [FIX] fix the rule, never the example." }
  # 4. THE FAIL-SAFE. A path no row names escalates to the full run: unlisted has never meant uncovered.
  $g4 = Get-ScaffoldTierGateSet -Tier '1' -ChangedPaths @('weird/thing.bin') -Routes $routes -Variant $v
  if (-not $g4.Full) { $findings += "[TIER-GATESET-EXAMPLE] a Tier-1 diff touching a path NO route names came back Full=$($g4.Full) with gates '$($g4.Gates -join ',')', expected the full run. The [GATE-MAP] header has said since T95 that an unlisted surface means run the whole suite, never that no gate covers it; a router that shrinks on an unknown path has inverted that sentence. [FIX] fix the rule, never the example." }
  elseif (@($g4.Unrouted) -notcontains 'weird/thing.bin') { $findings += "[TIER-GATESET-EXAMPLE] the escalating path was not named in Unrouted, so the run cannot say WHY it went full and the missing row cannot be added. Got: $(@($g4.Unrouted) -join ',')" }
  # 5. Mixed: one routed path and one unrouted one still escalates. The routed half is the trap - a rule
  #    that ORs the routes together instead of asking every path to be covered reads this as healthy.
  $g5 = Get-ScaffoldTierGateSet -Tier '1' -ChangedPaths @('scripts/lessons.ps1', 'weird/thing.bin') -Routes $routes -Variant $v
  if (-not $g5.Full) { $findings += "[TIER-GATESET-EXAMPLE] a Tier-1 diff mixing a routed path with an unrouted one came back Full=$($g5.Full): one covered path was allowed to speak for an uncovered one. [FIX] fix the rule, never the example." }
  # 6. A routed NON-script path adds no gate 7 - the lint gate rides the extension, not the row.
  $g6 = Get-ScaffoldTierGateSet -Tier '1' -ChangedPaths @('docs/HANDOFF.md') -Routes $routes -Variant $v
  if (-not $g6.Full -and (($g6.Gates -join ',') -match '(^|,)7(,|$)')) { $findings += "[TIER-GATESET-EXAMPLE] a doc-only Tier-1 diff pulled in gate 7 (PSScriptAnalyzer): got '$($g6.Gates -join ',')'. Nothing was linted, so a gate is being paid for by a file it cannot read. [FIX] fix the rule, never the example." }
  # 7. Ordering, on a set mixing numbers with a lettered token: numbers ascending, letters after them. A
  #    set a human compares by eye and a gate compares as a string has to have exactly one spelling.
  $g7 = Get-ScaffoldTierGateSet -Tier '1' -ChangedPaths @('specs/mutations/T1-X.psd1') -Routes $routes -Variant $v
  if (-not $g7.Full -and (($g7.Gates -join ',') -ne '1,2,11,13,14,16,17ac')) { $findings += "[TIER-GATESET-EXAMPLE] a Tier-1 diff routed to a lettered token computed '$($g7.Gates -join ',')', expected '1,2,11,13,14,16,17ac' - numbers ascending, lettered tokens after them, deduplicated (14 sits on the floor AND on that row). [FIX] fix the rule, never the example." }
  return @($findings)
}

# -- T280-TIER-FAIL-CLOSED: the repository's DEFAULT BRANCH, detected rather than written down ----------
# L38 is the lesson and its archive entry records the recurrence: a scaffold script that hardcodes 'main'
# cannot run in a master-default repo, and fixing one file left the same literal standing in the next.
# T274's -TaskId block wrote `merge-base origin/master HEAD` with a `master` fallback, so a downstream on
# `main` resolved no base at all - and because an empty changed set escalates, it failed in the EXPENSIVE
# direction, which is why no gate ever noticed. Since T280 an unresolvable base is a refusal instead, so
# the same misdetection is now loud.
#
# ONE probe, in the order the question is answerable: the remote's own HEAD states what the default branch
# IS, and the candidate list is only the fallback for a repository that has no origin/HEAD (a fresh `git
# init`, a detached fixture). '' when nothing resolves - the caller fail-closes on that rather than being
# handed a guess. This answers the NAME only; turning a name into the ref a diff is taken against stays
# Resolve-ScaffoldBaseRef's job (scripts/_gitbase.ps1, TD68/TD84) and is not restated here.
# task.ps1 and check-scope.ps1 keep their own probes deliberately: they ask for the CURRENT branch first,
# because a ship's merge target is the branch the main checkout is on - a different question with a
# different right answer, which is exactly why _gitbase.ps1 shares the name->ref step and not this one.
function Get-ScaffoldDefaultBranchName {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$GitDir)
  if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return '' }
  $originHead = "$(& git -C $GitDir symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>$null)".Trim()
  if ($originHead) { return ($originHead -replace '^origin/', '') }
  foreach ($cand in @('main', 'master')) {
    & git -C $GitDir rev-parse --verify --quiet "refs/heads/$cand^{commit}" 1>$null 2>$null
    if ($LASTEXITCODE -eq 0) { return $cand }
  }
  return ''
}

# -- T201-DOCGATE-READS-GUARD: the read-tag sites, judged the way 14i judges the [GATE-MAP] table --
# docs/SELFTEST.md teaches TWO grep-this-tag mechanisms in one sentence and only one of them ever had a
# reader. The gate-map half has had 14i since T95 (Test-ScaffoldGateMap above). The read-tag half arrived at
# T77 as failure-text prose - a convention rather than a data structure - so deleting the tag from a site
# reddened nothing, the doc kept telling readers to grep for a set that had silently shrunk, and the .md
# that gate reads then got edited under the pure-doc exemption with no gate run at all, which is the precise
# failure that exemption's own EXCEPT clause exists to prevent (TD193).
#
# This is a TRANSPOSITION of 14i, not a second design. The -Only id predicate below is
# Get-ScaffoldGateMapValidToken - 14i's own, CALLED. A second copy of the legal id set would BE the drift
# both gates exist to prevent, which is why the forbid list on T201 names it.
#
# What a site CLAIMS is what gets judged, and nothing more:
#   * the -Only token in its own re-check clause, which must be legal per selftest.ps1's own id literal
#   * every repo path it NAMES ahead of that clause, which must exist
# Naming no path is legal and deliberate. Five of the seven live sites name the file dynamically through
# $relPath, which is exactly what T77 built so that no file list is hardcoded into failure text (L97). The
# rule is "the claims a site makes must hold", never "every site must make a claim" - the second reading
# would undo T77 and put a filename back into every message.

function Get-ScaffoldDocReadTagSite {
  <#
  .SYNOPSIS  T201-DOCGATE-READS-GUARD: project the tagged doc-read sites out of scripts\selftest.ps1.
  .DESCRIPTION
    One object per tagged site: the sub-gate that owns it, the -Only token its re-check clause names, and
    every repo path its reads-clause names.
    The split point is the tag's OWN grammar - "this gate reads X; ... re-check with: ... -Only Y" - so the
    reads-clause is the text between the tag and the first semicolon. Splitting there is what keeps the two
    claims from contaminating each other: one live site ends "(or the faster scripts\check-adr.ps1)", which
    is a re-check hint, not a file that gate reads, and a scan of the whole line would judge it as one.
    -Text judges a string instead of a file, which is what lets the declared examples plant a bogus site
    without writing one into the real suite. -Path redirects the file read, matching the gate-map helpers.
  #>
  [CmdletBinding()]
  param([string]$Path, [string]$Text)
  $body = if ($PSBoundParameters.ContainsKey('Text')) { $Text }
  else {
    $file = if ($Path) { $Path } else { Join-Path $PSScriptRoot 'selftest.ps1' }
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return @() }
    [System.IO.File]::ReadAllText($file)
  }
  $sites = [System.Collections.Generic.List[psobject]]::new()
  $lines = $body -split "`r?`n"
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $tag = [regex]::Match($lines[$i], '\[DOC-GATE-READS[ \t]+(?<sub>[0-9][0-9a-z.]*)\]')
    if (-not $tag.Success) { continue }
    $rest = $lines[$i].Substring($tag.Index + $tag.Length)
    $readsClause = ($rest -split ';', 2)[0]
    $only = [regex]::Match($rest, '-Only[ \t]+(?<tok>[^\s''")]+)')
    $paths = @([regex]::Matches($readsClause, '(?<![\w.\\/-])(?:[A-Za-z0-9][\w.-]*(?:[\\/][\w.-]+)+|[A-Za-z0-9][\w.-]*\.(?:md|ps1|mjs|psd1|tsv|yml|yaml|json))') | ForEach-Object { $_.Value })
    $sites.Add([pscustomobject]@{
        Line      = $i + 1
        SubGate   = $tag.Groups['sub'].Value
        OnlyToken = $(if ($only.Success) { $only.Groups['tok'].Value } else { '' })
        Paths     = $paths
      })
  }
  return @($sites)
}

function Test-ScaffoldDocReadTag {
  <#
  .SYNOPSIS  T201-DOCGATE-READS-GUARD: judge the projected read-tag sites. Returns findings; empty is green.
  .DESCRIPTION
    Two independent claims per site, and only the claims actually made:
      * the -Only token must be legal per selftest.ps1's own id literal, judged by
        Get-ScaffoldGateMapValidToken - 14i's predicate, called rather than copied.
      * every repo path named must exist under the repo root.
    A site naming NO path is green with nothing to check: that is the deliberate dynamic form, not an
    omission. A site naming no -Only id IS a finding - naming one is half the convention docs/SELFTEST.md
    states, and a tag that re-checks with nothing tells a tripped agent to run nothing.
    The failure text names the legal set live rather than hardcoding it, for 14i's reason: the legal set is
    exactly the thing that moves.
    -RepoRoot redirects path resolution so the declared examples judge a tree they control.
  #>
  [CmdletBinding()]
  param([string]$Path, [string]$Text, [string]$RepoRoot)
  $root = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent $PSScriptRoot }
  $valid = Get-ScaffoldGateMapValidToken -Path $Path
  $sites = if ($PSBoundParameters.ContainsKey('Text')) { @(Get-ScaffoldDocReadTagSite -Text $Text) }
  else { @(Get-ScaffoldDocReadTagSite -Path $Path) }
  $findings = [System.Collections.Generic.List[string]]::new()
  foreach ($s in $sites) {
    if (-not $s.OnlyToken) {
      $findings.Add("the site at line $($s.Line), tagged for sub-gate '$($s.SubGate)', names no -Only id. The tag's contract is to name the file the gate read AND the id that re-checks it; without the id a tripped agent is told to run nothing. Add 're-check with: selftest.ps1 -Only <id>' to that message.")
    }
    elseif ($s.OnlyToken -notin $valid) {
      $findings.Add("the site at line $($s.Line), tagged for sub-gate '$($s.SubGate)', re-checks with -Only '$($s.OnlyToken)', which is not a legal -Only id. Legal right now, read live from this script's own id literal: $($valid -join ', '). A gate was renamed or retired without updating the message, or the id has a typo. Fix the message - never the id list.")
    }
    foreach ($p in @($s.Paths)) {
      if (-not (Test-Path -LiteralPath (Join-Path $root $p))) {
        $findings.Add("the site at line $($s.Line), tagged for sub-gate '$($s.SubGate)', says it reads '$p', and no such path exists. Either the file moved or was renamed and the message went stale, or the message names something that was never a path. Name the live path, or name the file dynamically the way the other sites do.")
      }
    }
  }
  return @($findings)
}

function Test-ScaffoldDocReadTagExamples {
  <#
  .SYNOPSIS  T201-DOCGATE-READS-GUARD: the declared examples. Returns the arms that did NOT hold; empty is green.
  .DESCRIPTION
    Sub-gate 14n runs this BEFORE trusting the verdict on the real file, for the reason 14i states about its
    own probe: a checker that always returned zero findings would sail through the real-file assertion, and
    would sail through it hardest in a file where there is nothing left to judge.
    Each arm names itself in its own failure string so a mutation batch can tell WHICH half it killed
    instead of reading one undifferentiated red (L265).
    The third arm is the one that matters most and is easiest to lose: the dynamic no-path form must PASS.
    Without it, a later tightening that demanded a literal filename at every site would still look green,
    and that tightening is precisely what T77 removed.
  #>
  [CmdletBinding()]
  param()
  $bad = [System.Collections.Generic.List[string]]::new()
  # The examples own their path population; initialized projects need neither a README nor a CHANGELOG.
  $root = Join-Path ([IO.Path]::GetTempPath()) ('scaffold-doc-read-ex-' + [guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $root | Out-Null
  [IO.File]::WriteAllText((Join-Path $root 'README.md'), '# fixture')
  try {
  $t = '[DOC-GATE-READS 9z]'

  $illegal = @(Test-ScaffoldDocReadTag -RepoRoot $root -Text "$t this gate reads README.md; re-check with: selftest.ps1 -Only no-such-gate")
  if ($illegal.Count -ne 1) { $bad.Add("illegal-only-id: expected 1 finding, got $($illegal.Count)") }

  $ghost = @(Test-ScaffoldDocReadTag -RepoRoot $root -Text "$t this gate reads docs/NO-SUCH-FILE.md; re-check with: selftest.ps1 -Only 14")
  if ($ghost.Count -ne 1) { $bad.Add("missing-path: expected 1 finding, got $($ghost.Count)") }

  $dynamic = @(Test-ScaffoldDocReadTag -RepoRoot $root -Text "$t this gate reads the .md named above; re-check with: selftest.ps1 -Only 14")
  if ($dynamic.Count -ne 0) { $bad.Add("dynamic-form-must-pass: expected 0 findings, got $($dynamic.Count) ($($dynamic -join ' ~ '))") }

  $noOnly = @(Test-ScaffoldDocReadTag -RepoRoot $root -Text "$t this gate reads README.md and nothing re-checks it")
  if ($noOnly.Count -ne 1) { $bad.Add("no-only-id: expected 1 finding, got $($noOnly.Count)") }

  $both = @(Test-ScaffoldDocReadTag -RepoRoot $root -Text "$t this gate reads docs/NO-SUCH-FILE.md; re-check with: selftest.ps1 -Only no-such-gate")
  if ($both.Count -ne 2) { $bad.Add("both-halves-independent: expected 2 findings, got $($both.Count)") }

  $seen = @(Get-ScaffoldDocReadTagSite -Text "$t this gate reads README.md; re-check with: selftest.ps1 -Only 14")
  if ($seen.Count -ne 1) { $bad.Add("projection-count: expected 1 site, got $($seen.Count)") }
  elseif ($seen[0].SubGate -ne '9z' -or $seen[0].OnlyToken -ne '14' -or @($seen[0].Paths).Count -ne 1 -or @($seen[0].Paths)[0] -ne 'README.md') {
    $bad.Add("projection-fields: expected sub-gate 9z, -Only 14, one path README.md; got sub-gate $($seen[0].SubGate), -Only $($seen[0].OnlyToken), path(s) $(@($seen[0].Paths) -join ',')")
  }

  $hint = @(Get-ScaffoldDocReadTagSite -Text "$t this gate reads the ADR named above under docs/adr; after editing it, re-check with: selftest.ps1 -Only 14 (or the faster scripts\check-adr.ps1)")
  if (@($hint).Count -ne 1 -or @($hint[0].Paths).Count -ne 1 -or @($hint[0].Paths)[0] -ne 'docs/adr') {
    $bad.Add("reads-clause-split: a path named only in the RE-CHECK hint must not be judged as something the gate reads; expected the single path docs/adr, got $(@($hint[0].Paths) -join ',')")
  }

  return @($bad)
  } finally {
    $fixtureFull = [IO.Path]::GetFullPath($root)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $fixtureFull.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $fixtureFull) -notlike 'scaffold-doc-read-ex-*') { throw 'Unsafe doc-read fixture cleanup path.' }
    Remove-Item -LiteralPath $fixtureFull -Recurse -Force
  }
}

# -- T141-SELFTEST-REGION-LITERAL: a gate-17 region literal that names no declared region --
# Gate 17's regions are chosen at the call site by a STRING LITERAL handed to Test-SeedRegionSelected. A
# literal matching no region arm answers to no -Only token, so it runs in NO shard under -Parallel while
# every token-level assertion in the suite still passes: those assertions check that the shard list mentions
# the token, never that a region answered to it. The result is a green run over an unexecuted region, and it
# is the reason the serial no-arg run has stayed the only acceptance face structurally immune to the typo.
#
# The declared set is PROJECTED, never restated, from TWO agreeing sources - both read out of the single
# text handed in, which is what keeps the judgement pure and hermetic:
#   * selftest.ps1's own $onlyValidIds literal, the same declaration -Only validates against. A gate-17
#     sub-token is '17' + region, so the vocabulary is the suffix of those tokens.
#   * the router's own `$Region -eq '<r>'` arms. This is what excludes the 17base UMBRELLA without naming
#     it: 17base selects two regions and has no arm of its own, so a call site reading 'base' answers to
#     nothing and is correctly reported.
# A region present in only ONE source is not declared: a token with no arm cannot route, and an arm with no
# token cannot be selected. Either way that call site is unreachable, which is the defect being caught.
# Call sites passing a VARIABLE are deliberately not matched - the routing-matrix self-check drives the
# router over every region by variable, and flagging it would be a false positive on correct code.
function Get-ScaffoldOrphanRegionLiteral {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
    [string[]]$DeclaredRegion
  )
  $declared = if ($PSBoundParameters.ContainsKey('DeclaredRegion')) { @($DeclaredRegion) }
  else {
    $idsExpr = [regex]::Match($Text, '(?m)^[ \t]*\$onlyValidIds[ \t]*=[ \t]*(?<expr>[^\r\n]*)').Groups['expr'].Value
    $suffix = foreach ($quoted in [regex]::Matches($idsExpr, "'(?<id>[^'\r\n]+)'")) {
      $sub = [regex]::Match($quoted.Groups['id'].Value, '^17(?<r>.+)$')
      if ($sub.Success) { $sub.Groups['r'].Value }
    }
    $routed = $suffix | Where-Object { $Text -match ('\$Region\s+-eq\s+''' + [regex]::Escape($_) + '''') }
    @($routed | Sort-Object -Unique)
  }
  $findings = [System.Collections.Generic.List[object]]::new()
  $regionLines = $Text -split '\r?\n'
  for ($i = 0; $i -lt $regionLines.Count; $i++) {
    foreach ($call in [regex]::Matches($regionLines[$i], 'Test-SeedRegionSelected\s+(?<q>[''"])(?<lit>[^''"]*)\k<q>')) {
      $lit = $call.Groups['lit'].Value
      if ($lit -notin $declared) {
        $findings.Add([pscustomobject]@{ Literal = $lit; Line = $i + 1; Declared = @($declared) })
      }
    }
  }
  return @($findings)
}

function Test-ScaffoldRegionLiteralExamples {
  <#
  .SYNOPSIS  T141 arms 4/5: declared examples for Get-ScaffoldOrphanRegionLiteral. Returns findings; an
             empty result is green. Hermetic - synthetic text only, so it reads no file and needs no repo.
  .DESCRIPTION
    The table carries BOTH directions. Silence on a clean text is the half that keeps the check from being a
    permanent red, and it is the half a membership test inverted by mutation would break first.
  #>
  [CmdletBinding()]
  param()
  $router = @'
$onlyValidIds = @(1..17 | ForEach-Object { [string]$_ }) + @('17t', '17ac', '17base', '17pre', '17post')
function Test-SeedRegionSelected([string]$Region, [string[]]$Tokens = $null) {
  return ((($Region -eq 't') -and ($Tokens -contains '17t')) -or (($Region -eq 'ac') -and ($Tokens -contains '17ac')) -or (($Region -eq 'pre') -and ($Tokens -contains '17pre')) -or (($Region -eq 'post') -and ($Tokens -contains '17post')))
}
'@
  $findings = @()
  # 1. Negative direction: every literal names a declared region, including a double-quoted one, and the
  #    routing-matrix call site that passes a VARIABLE. Silence is the required output.
  $clean = $router + @'

if (Test-SeedRegionSelected 'pre') { }
if (Test-SeedRegionSelected "post") { }
$got = Test-SeedRegionSelected $rg -Tokens $sfr.tok
'@
  $cleanGot = @(Get-ScaffoldOrphanRegionLiteral -Text $clean)
  if ($cleanGot.Count -ne 0) { $findings += "[REGION-LITERAL-EXAMPLE] a text whose every region literal is declared was reported: $(($cleanGot | ForEach-Object { $_.Literal }) -join ', '). Both quote styles are legal and a call site passing a variable is not a literal at all - reporting either makes the check unlandable. [FIX] fix the rule, never the example." }
  # 2. The typo this card exists for: a literal one character off runs in no shard and must be named.
  $typoGot = @(Get-ScaffoldOrphanRegionLiteral -Text ($router + "`nif (Test-SeedRegionSelected 'postt') { }"))
  if ($typoGot.Count -ne 1) { $findings += "[REGION-LITERAL-EXAMPLE] the orphaned literal 'postt' produced $($typoGot.Count) findings, expected exactly 1 - this is the green-over-an-unexecuted-region state the card exists to make red." }
  elseif ($typoGot[0].Literal -ne 'postt' -or $typoGot[0].Line -lt 1) { $findings += "[REGION-LITERAL-EXAMPLE] the finding must name the offending literal and its line so the fix is one jump away. Got literal '$($typoGot[0].Literal)' at line $($typoGot[0].Line)." }
  # 3. The umbrella: '17base' IS a legal -Only token but selects two regions and has no arm of its own, so
  #    a call site reading 'base' routes nowhere. Deriving from the token list ALONE would miss this.
  $umbrellaGot = @(Get-ScaffoldOrphanRegionLiteral -Text ($router + "`nif (Test-SeedRegionSelected 'base') { }"))
  if ($umbrellaGot.Count -ne 1) { $findings += "[REGION-LITERAL-EXAMPLE] the umbrella literal 'base' went unreported. 17base is a legal -Only token but routes no region of its own, so the declared set must be the token suffixes INTERSECTED with the router's own arms, not the suffixes alone." }
  # 4. An explicit declared set is what makes the membership test itself testable, independent of parsing.
  $explicitGot = @(Get-ScaffoldOrphanRegionLiteral -Text "if (Test-SeedRegionSelected 'zzz') { }" -DeclaredRegion @('pre', 'post'))
  if ($explicitGot.Count -ne 1) { $findings += "[REGION-LITERAL-EXAMPLE] with an explicitly declared set @('pre','post'), the literal 'zzz' must be reported - the membership test is the load-bearing line." }
  return $findings
}

# -- T194-REGION-EXECUTION-RECEIPT (upstream #272): did the SELECTED gate-17 regions actually RUN? --
# Every assertion around gate 17's region split observes the SELECTOR, never the execution. `-Only 17pre`
# derives its label and its closing [SELFTEST-ONLY-PASS] sentinel from the token that was REQUESTED, and
# Exit-Gate only counts failures around the parent gate - so a region whose guard never fired is
# indistinguishable from a region whose every check passed. Gate 14k narrows this and cannot close it: 14k
# rejects a literal naming NO routed region, while redirecting one guard to another VALID literal keeps every
# token-level assertion green over a body that never ran. That is the exact reproduction in issue #272, and
# 14k's own failure text already states the consequence it cannot detect.
# The evidence therefore has to come from INSIDE the guarded body - a receipt the body files about itself -
# and then be reconciled against the set the selector should have activated. Both directions are findings: a
# selected site that filed nothing did not run, and an unselected site that filed one ran outside its shard.
# Keyed per SITE, never per region: 'post' has TWO legitimate guarded bodies, so the duplicate-by-region rule
# issue #272 prescribes would red correct code (the card records that correction - L270: a prescribed fix is
# a hypothesis). Judgement lives here rather than in selftest.ps1 so it is testable on DATA, the same split
# 14g/14h/14i/14k already use.
function Get-ScaffoldRegionReceiptIssue {
  <#
  .SYNOPSIS  T194 arms 2/3/4: reconcile the receipts gate 17's region guards filed against the sites this
             run's selector should have activated. Returns findings; an empty result is green.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Expected,
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Observed
  )
  $findings = @()
  $rrExp = @($Expected | Sort-Object -Unique)
  $rrObs = @($Observed | Sort-Object -Unique)
  # Vacuity first, for the reason T191's timeout core states about its own empty projection: with nothing
  # expected there is nothing to reconcile, and silence would read as agreement.
  if ($rrExp.Count -eq 0) {
    $findings += "[SEED-REGION-EVIDENCE] no gate-17 guard site is registered for the regions this run selected, so the reconciliation would judge nothing and pass on an empty comparison. The site table was emptied, or its rows no longer name a region the router still routes."
    return $findings
  }
  foreach ($rrMiss in @($rrExp | Where-Object { $_ -notin $rrObs })) {
    $findings += "[SEED-REGION-EVIDENCE] site '$rrMiss' was selected but filed no execution evidence from inside its own body, so it never ran while every token-level assertion still passed - this shard is green over an unexecuted region. Either the guard was deleted, or it was redirected to a region this run did not select (issue #272; gate 14k cannot see the second case because the literal it redirects TO is legally routed)."
  }
  foreach ($rrExtra in @($rrObs | Where-Object { $_ -notin $rrExp })) {
    $findings += "[SEED-REGION-EVIDENCE] site '$rrExtra' filed execution evidence while NOT selected. That is drift in the other direction: a body running outside its own shard makes the union 8.2e proves structurally stop matching the work actually done, and charges its cost to whichever shard picked it up."
  }
  return $findings
}

function Test-ScaffoldRegionReceiptExamples {
  <#
  .SYNOPSIS  T194 arm 2's declared examples for Get-ScaffoldRegionReceiptIssue. Returns findings; an empty
             result is green. Hermetic - synthetic site ids only, so it reads no file and needs no repo.
  .DESCRIPTION
    Carries BOTH directions, because the live call can only ever feed this core a tree that is correct by
    construction: on a green run expected EQUALS observed, so the real face proves the core accepts agreement
    and can never prove it rejects anything (TD140 / ADR 0011, the same stance as 8.2g' and 8.2h').
    -Variant 'accept-any-run' is the find-nothing control the consumer re-runs the same cases through; it
    must disagree at least once or these cases assert nothing.
    Every finding here reports under [SEED-REGION-EVIDENCE-EXAMPLE], never under the tag the card's
    dod_command greps for. That is the load-bearing precaution, and it is STRUCTURAL: a DoD pattern of the
    form `TAG.*TAIL` matches any line carrying TAG whose prose happens to contain TAIL, and ordinary English
    supplies the tail for free - 'present' and 'interpreted' both contain 'pre'. Reserving the tag to the one
    success line holds no matter what prose a later editor writes; being careful with wording does not.
    Site ids here also avoid the substring 'pre' as a second line of defence, since this arm interpolates the
    core's own findings into its messages and would otherwise inherit whatever they quote (L283).
  #>
  [CmdletBinding()]
  param([ValidateSet('real', 'accept-any-run')][string]$Variant = 'real')
  $rrCases = @(
    @{ n = 'agreement';        exp = @('t@17t'); obs = @('t@17t'); want = @() }
    # Two sites of ONE region, filed in the other order. This is the shape issue #272's prescribed
    # duplicate-by-region rule would have rejected, and 'post' really has two bodies - so silence here is
    # what keeps the correction honest, and order-insensitivity is asserted rather than assumed.
    @{ n = 'two-sites-one-region'; exp = @('x@17v', 'y@17z'); obs = @('y@17z', 'x@17v'); want = @() }
    @{ n = 'missing';          exp = @('t@17t', 'ac@17ac'); obs = @('ac@17ac'); want = @('filed no execution evidence') }
    @{ n = 'unexpected';       exp = @('ac@17ac'); obs = @('ac@17ac', 't@17t'); want = @('while NOT selected') }
    # The redirect itself: the body ran, but under a region this run did not select. It must show up as BOTH
    # a site that owed evidence and never filed it AND a site that filed evidence it did not owe.
    @{ n = 'redirected';       exp = @('t@17t'); obs = @('ac@17ac'); want = @('filed no execution evidence', 'while NOT selected') }
    @{ n = 'nothing-selected'; exp = @(); obs = @('t@17t'); want = @('no gate-17 guard site is registered') }
  )
  $findings = @()
  foreach ($rrC in $rrCases) {
    $rrGot = @()
    if ($Variant -eq 'real') { $rrGot = @(Get-ScaffoldRegionReceiptIssue -Expected $rrC.exp -Observed $rrC.obs) }
    if (@($rrC.want).Count -eq 0) {
      if ($rrGot.Count -ne 0) { $findings += "[SEED-REGION-EVIDENCE-EXAMPLE] the agreeing shape '$($rrC.n)' was reported ($($rrGot -join ' | ')). A run whose filed set equals its selected set is the normal green state; a rule that reds it cannot be landed. [FIX] fix the rule, never the example." }
      continue
    }
    foreach ($rrWant in @($rrC.want)) {
      if (@($rrGot | Where-Object { $_ -like "*$rrWant*" }).Count -lt 1) {
        $findings += "[SEED-REGION-EVIDENCE-EXAMPLE] the shape '$($rrC.n)' was not reported through its own arm ('$rrWant'); got $(if ($rrGot.Count) { $rrGot -join ' | ' } else { 'nothing' }). [FIX] fix the rule, never the example."
      }
    }
  }
  return $findings
}

# -- T172-SUBGATE-LABEL-UNIQUE (TD163): does scripts/selftest.ps1 declare one sub-gate label twice? --
# Sub-gate letters inside a gate are hand-assigned and nothing read them back, so two different blocks could
# print under one label while the suite stayed green. Live at T141: a new region-literal check was written as
# 14j while 14j was already the T97 card-generator sweep check; it was caught by eye in a scoped -Only 14 run,
# not by any gate. The rule lives here rather than in selftest.ps1 so it is testable on TEXT, the same split
# 14g/14h/14i/14k use.
function Get-ScaffoldDuplicateSubGateLabel {
  <#
  .SYNOPSIS  T172 arms 1/2: project every sub-gate label scripts/selftest.ps1 declares and report a label
             declared twice inside one gate. Returns findings; an empty result is green.
  .DESCRIPTION
    Two declaration surfaces, because the file uses both and the recorded incident lives on the one the
    tracker row did NOT name:
      - a canonical comment header, `# <label>. ` at column 0 - 86 today, and for 83 of them it is the
        sub-gate's only declaration. 14j is one of those 83, which is where the T141 collision landed;
      - a `Step '<label>/...'` header - 9 lettered ones today (1a 1b 1h 14f 14g 14h 14i 14k 14m).
    Deliberately NOT declarations, all three measured on the live file:
      - the parenthetical variant `# <label>(...)` - 10d carries 10 of them and is ONE sub-gate implemented
        across several blocks;
      - printed success announcements - 17ac alone prints 33 label-bearing OK lines;
      - a comment header whose label is followed by anything other than `. ` - `# 8.2a' already pins ...` is a
        continuation line inside 13a'', and admitting that form would report 8.2a' as a false duplicate.
    A LABEL is the gate number, one or more letters, an OPTIONAL trailing digit and any number of primes -
    `14m`, `17b'`, `8.2f`, and since T286 the digit forms `17a2` and `8l2`. The digit belongs to the label and
    is never a separator: reading `17a` out of `17a2` would report a false duplicate against the real 17a,
    and rejecting the form outright is what kept four gate-17 labels and 8l2 outside this verdict until T286.
    Those exclusions are what make the projection one-to-one, and the price is real: a sub-gate that declares
    itself in neither canonical form is outside this verdict. Since T182 that price is no longer SILENT -
    Get-ScaffoldUnprojectedSubGateHeader below reads this projection back and names every header it cannot
    see, the dotted 8.0x/8.2x family having been normalised into the canonical form by that card.
    A `Step` declaration DIRECTLY under a canonical comment declaration of the same label (only comment or
    blank lines between) is one sub-gate introducing itself on both surfaces, not a collision, and collapses
    into it. 1a, 1b and 1h are live instances; without the collapse the live tree reports 3 false duplicates.
    The label carries its own gate number, so "inside one gate" needs no separate grouping and the same
    letter under different gates (14b and 15b, both live) stays legal by construction.
    -Variant 'step-headers-only' narrows the projection to the Step surface, which is what TD163 originally
    proposed. It exists so the widening has a machine-checked reason to stay: under it the examples must FAIL.
    -Declaration returns the projected declarations instead of the duplicates, which is how the consumer
    proves the scan is not vacuous before trusting its silence.
  #>
  [CmdletBinding()]
  param(
    [string]$Path,
    [AllowEmptyString()][string]$Text,
    [ValidateSet('both-surfaces', 'step-headers-only')][string]$Variant = 'both-surfaces',
    [switch]$Declaration
  )
  if ($PSBoundParameters.ContainsKey('Path')) { $Text = [System.IO.File]::ReadAllText((Convert-Path -LiteralPath $Path)) }
  $subGateLines = $Text -split '\r?\n'
  $declared = [System.Collections.Generic.List[object]]::new()
  for ($i = 0; $i -lt $subGateLines.Count; $i++) {
    if ($Variant -ne 'step-headers-only') {
      $commentDecl = [regex]::Match($subGateLines[$i], "^#[ \t]+(?<label>\d+(?:\.\d+)*[a-z]+\d?'*)\.[ \t]")
      if ($commentDecl.Success) {
        $declared.Add([pscustomobject]@{ Label = $commentDecl.Groups['label'].Value; Line = $i + 1; Surface = 'comment header' })
        continue
      }
    }
    $stepDecl = [regex]::Match($subGateLines[$i], "^Step[ \t]+'(?<label>\d+(?:\.\d+)*[a-z]+\d?'*)/")
    if ($stepDecl.Success) {
      $declared.Add([pscustomobject]@{ Label = $stepDecl.Groups['label'].Value; Line = $i + 1; Surface = 'Step header' })
    }
  }
  $kept = [System.Collections.Generic.List[object]]::new()
  foreach ($decl in $declared) {
    $prior = @($kept | Where-Object { $_.Label -eq $decl.Label }) | Select-Object -Last 1
    if ($decl.Surface -eq 'Step header' -and $prior -and $prior.Surface -eq 'comment header') {
      $introduces = $true
      for ($j = $prior.Line; $j -lt $decl.Line - 1; $j++) {
        if ($subGateLines[$j].Trim() -ne '' -and $subGateLines[$j] -notmatch '^#') { $introduces = $false; break }
      }
      if ($introduces) { continue }
    }
    $kept.Add($decl)
  }
  if ($Declaration) { return @($kept) }
  $findings = [System.Collections.Generic.List[object]]::new()
  foreach ($group in ($kept | Group-Object Label)) {
    if (@($group.Group).Count -lt 2) { continue }
    $findings.Add([pscustomobject]@{ Label = $group.Name; Declarations = @($group.Group) })
  }
  return @($findings)
}

function Test-ScaffoldSubGateLabelExamples {
  <#
  .SYNOPSIS  T172 arms 1/3/4/5/6: declared examples for Get-ScaffoldDuplicateSubGateLabel. Returns findings;
             an empty result is green. Hermetic - synthetic text only, so it reads no file and needs no repo.
  .DESCRIPTION
    The table carries BOTH directions, as 14k's examples do. Silence on a legal file is the half that keeps
    the rule landable, and it is the half a collapse or a comparison inverted by mutation breaks first.
    -Variant 'step-headers-only' runs the SAME examples through the narrowed projection TD163 proposed. It
    MUST produce findings: the recorded collision landed on a label with no Step header, so a narrow
    projection is measurably blind to the incident the rule exists for.
  #>
  [CmdletBinding()]
  param([ValidateSet('both-surfaces', 'step-headers-only')][string]$Variant = 'both-surfaces')
  $findings = @()
  # 1. The recorded T141 collision in its real shape: the incumbent declares itself with a comment header
  #    only, the newcomer with a Step header far below. Both declarations must be named, with their lines.
  $collisionText = @'
# 14j. T97-GENERATOR-SWEEP: the incumbent, declared by comment header only.
#   A continuation line belongs to that header and declares nothing of its own.
$incumbent = 1

Step '14/17 the gate itself'
$unrelated = 2

Step '14j/17 the newcomer that claimed a taken letter'
$newcomer = 3
'@
  $collisionGot = @(Get-ScaffoldDuplicateSubGateLabel -Text $collisionText -Variant $Variant)
  if ($collisionGot.Count -ne 1) { $findings += "[SUBGATE-LABEL-EXAMPLE] the T141 collision shape produced $($collisionGot.Count) findings, expected exactly 1. A label held by a comment-header sub-gate and claimed again by a Step header is the incident this rule exists for. [FIX] fix the rule, never the example." }
  elseif ($collisionGot[0].Label -ne '14j' -or @($collisionGot[0].Declarations).Count -ne 2 -or @($collisionGot[0].Declarations | Where-Object { $_.Line -lt 1 }).Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] the finding must name the label and BOTH declaration lines so the fix is one jump away. Got label '$($collisionGot[0].Label)' with $(@($collisionGot[0].Declarations).Count) declaration(s)." }
  # 2. Comment headers are projected on their own, independent of the Step surface. 47 of the 50 live
  #    comment-declared sub-gates have no Step header at all, so this is the load-bearing half.
  $commentOnlyGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 9c. the incumbent.
$a = 1

# 9c. a second block claiming the same letter.
$b = 2
'@)
  if ($commentOnlyGot.Count -ne 1) { $findings += "[SUBGATE-LABEL-EXAMPLE] two comment headers declaring 9c produced $($commentOnlyGot.Count) findings, expected exactly 1 - the comment surface must be projected without help from a Step header." }
  # 3. The parenthetical variant is NOT a declaration: 10d carries 10 of them live and is one sub-gate.
  $variantGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 10d. the sub-gate, declared once.
$a = 1

# 10d(inline flow): a further block of the SAME sub-gate.
$b = 2

# 10d(scope gate): and another.
$c = 3
'@)
  if ($variantGot.Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] the parenthetical form was read as a declaration and reported $($variantGot.Count) finding(s). 10d is implemented across several blocks and is one sub-gate - reporting it makes the rule unlandable." }
  # 4. Printed success announcements are NOT declarations: 17ac alone prints 33 label-bearing OK lines.
  $announceGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 17ac. the seeded-defect sub-gate, declared once.
Write-Host '  17ac(a) case OK' -ForegroundColor Green
Write-Host '  17ac(b) case OK' -ForegroundColor Green
Write-Host '  17ac(c) case OK' -ForegroundColor Green
'@)
  if ($announceGot.Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] printed OK lines were read as declarations and reported $($announceGot.Count) finding(s). Counting printed labels would flag nearly every gate." }
  # 5. The same letter under different gate numbers is legal and live today (14b and 15b coexist).
  $crossGateGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 14b. one gate's b.
$a = 1

# 15b. another gate's b.
$b = 2
'@)
  if ($crossGateGot.Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] the same letter under two different gate numbers was reported. 14b and 15b coexist on the live tree and a cross-gate verdict is an explicit non-goal of this rule." }
  # 6. A Step header directly under the canonical comment header of the SAME label is that one sub-gate
  #    introducing itself on both surfaces. 1a, 1b and 1h are live instances; without the collapse the live
  #    tree reports 3 false duplicates and the rule can never land.
  $introducesGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 1a. TD52: the prose header, which runs on for a few lines.
#     A continuation line, and then a blank one.

Step '1a/17 the same sub-gate announcing itself'
$a = 1
'@)
  if ($introducesGot.Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] a comment header followed by the Step header of the SAME sub-gate was reported as a collision. That is one sub-gate declared on both surfaces - 1a, 1b and 1h are live instances of it." }
  # 7. ShouldNot (T286): a trailing digit makes a DIFFERENT label, and it never collides with the label it
  #    extends nor with a primed one. 17a/17a2 and 17w'/17w2 are four live sub-gates; a grammar that left the
  #    digit outside the capture would read 17a out of 17a2 and report the pair as one label declared twice.
  $digitDistinctGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 17a. the sub-gate whose letter the digit form extends.
$a = 1

# 17a2. a DIFFERENT sub-gate, not a second declaration of 17a.
$b = 2

# 17w'. a primed label.
$c = 3

# 17w2. the digit form of the same letter, distinct from both 17w' and 17w.
$d = 4
'@)
  if ($digitDistinctGot.Count -ne 0) { $findings += "[SUBGATE-LABEL-EXAMPLE] the digit form collided with the label it extends or with a primed one: $($digitDistinctGot.Count) finding(s) over 17a, 17a2, 17w' and 17w2, which are four distinct live sub-gates. A digit read as a separator makes the pair one label and the rule unlandable." }
  # 8. Should (T286): the digit form is a declaration like any other, so two blocks claiming 17a2 are a
  #    duplicate. This is the arm that fails when the digit is dropped from the grammar again - example 7
  #    would stay silent, because a label the projection cannot see can never collide with anything.
  $digitDupGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 17a2. the incumbent, whose label carries a trailing digit.
$a = 1

# 17a2. a second block claiming the same digit-form label.
$b = 2
'@)
  if ($digitDupGot.Count -ne 1 -or $digitDupGot[0].Label -ne '17a2') { $findings += "[SUBGATE-LABEL-EXAMPLE] two comment headers declaring 17a2 produced $($digitDupGot.Count) finding(s), expected exactly 1 named 17a2. A label form the projection cannot see is exempt from the uniqueness verdict, which is where 17a2, 17p2, 17p3 and 17w2 sat until T286." }
  # 9. The digit is on BOTH declaration surfaces, so both are declared (T286, R3 round 1): every example
  #    above writes comment headers, which leaves the Step matcher's `\d?` free to be deleted with the
  #    examples and the live-tree census still green. First half asserts the CAPTURED label on the Step
  #    surface - 17a and 17a2 are two Step declarations, not one - and it is the half that reds when the
  #    digit leaves that matcher, because 17a2 then projects as nothing at all.
  $digitStepDecl = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Declaration -Text @'
Step '17a/17 the letter form'
$a = 1

Step '17a2/17 the digit form, a different sub-gate'
$b = 2
'@)
  if (@($digitStepDecl).Count -ne 2 -or @($digitStepDecl | Where-Object { $_.Label -ceq '17a2' -and $_.Surface -eq 'Step header' }).Count -ne 1) { $findings += "[SUBGATE-LABEL-EXAMPLE] the Step surface did not project 17a2 as its own label: got $(@($digitStepDecl).Count) declaration(s) [$((@($digitStepDecl) | ForEach-Object { $_.Label }) -join ', ')], expected 17a and 17a2. A digit the Step matcher cannot read makes that whole sub-gate invisible to the verdict." }
  # 9 (second half). The recorded T141 collision shape one label over: a comment-header incumbent and a
  #    Step-header newcomer claiming the same digit-form label are ONE label declared twice, and the finding
  #    must name it. Code between the two keeps the introduces-itself collapse out of the way.
  $digitStepDupGot = @(Get-ScaffoldDuplicateSubGateLabel -Variant $Variant -Text @'
# 17a2. the incumbent, declared by comment header only.
$incumbent = 1

Step '17a2/17 the newcomer claiming the same digit-form label'
$newcomer = 3
'@)
  if ($digitStepDupGot.Count -ne 1 -or $digitStepDupGot[0].Label -ne '17a2' -or @($digitStepDupGot[0].Declarations).Count -ne 2) { $findings += "[SUBGATE-LABEL-EXAMPLE] a digit-form label claimed on both surfaces produced $($digitStepDupGot.Count) finding(s), expected exactly 1 naming 17a2 with both declarations. This is the T141 shape written with the label form T286 admits." }
  return $findings
}

# -- T182-SUBGATE-FORM-RATCHET (TD168): does a sub-gate declare itself in a form 14m cannot project? --
# The projection above is one-to-one by design and its docstring states the price out loud, but nothing read
# that price back, so PARTIAL coverage looked exactly like a clean tree. Measured on the live file at T182:
# 37 of the 95 sub-gates declared themselves in a form the projector cannot see - three times what
# TD168 counted, because the row looked only at the supply-chain gate. T182 normalised the dotted family and
# put the measured remainder on a pending list; T185 drained that list from 25 labels to 4. T186 retires the
# list, because what was left is not a formatting backlog at all: each of the four is ONE sub-gate implemented
# across several blocks, and what they needed was a way to SAY so.
# The rule asks Get-ScaffoldDuplicateSubGateLabel WHICH LINES it projected instead of restating the canonical
# grammar (L271 - the second copy of a rule is where the drift starts), so narrowing the projector lights up
# every header it stopped seeing rather than quietly shrinking the verdict.
function Get-ScaffoldContinuedSubGateLabel {
  <#
  .SYNOPSIS  T186: the sub-gates implemented across several blocks, as label -> how many blocks CONTINUE the
             one canonical header. Read by Get-ScaffoldUnprojectedSubGateHeader below.
  .DESCRIPTION
    A continuation is not a declaration. 10d is ONE sub-gate whose implementation runs eleven blocks long, so
    normalising its eleven parenthetical headers into the canonical form would report eleven false duplicates
    rather than close a blind spot; 9g, 15f and 15r carry one continuation each. That shape - one canonical
    header plus N declared continuations - is what TD177 held open, and it replaces the pending list T182
    introduced and T185 drained. The list excused a header by NAME alone, which says nothing about WHY the
    header is legal and would tolerate the same label written in any invisible form at all.
    Three conditions make a continuation legal and the rule below checks all three: the label is on this map,
    the label IS canonically declared elsewhere in the same file, and the header is written in the
    continuation form `# <label>(`. The space form stays reported for every label, declared or not, so the
    original blind spot is still shut - which is why 9g's second header had to move rather than the rule.
    The COUNT is what makes this a ratchet in both directions: a block added under an already-continued label
    and a block deleted from one each turn gate 14 red until the number is restated, where a bare label list
    would notice neither. The cost is stated rather than hidden - restating the number is a declared act - and
    it can never introduce a NEW label, which is the thing the uniqueness verdict protects.
  #>
  return @{
    '9g'  = 1
    '10d' = 11
    '15f' = 1
    '15r' = 1
  }
}

function Get-ScaffoldUnprojectedSubGateHeader {
  <#
  .SYNOPSIS  T182/T186: report every sub-gate header scripts/selftest.ps1 declares that 14m's projection
             cannot see and that is not a declared continuation, plus every declared continuation count that
             no longer matches the file. An empty result is green.
  .DESCRIPTION
    A CANDIDATE is a column-0 comment whose first token is a sub-gate label followed by `.`, `(`, a full-width
    paren, or whitespace - every form the file actually uses to introduce a sub-gate. Canonical candidates are
    dropped because the PROJECTOR reported their line, never because this rule re-derives that they look
    canonical; that single line is the whole coupling, and deleting it reports all 92 canonical headers.
    The candidate grammar below deliberately KEEPS the pre-T286 label shape - no trailing digit - while the
    projector it reads gained one. Measured on the live file when T286 widened the projector: admitting the
    digit here adds exactly one new candidate, `# 15d2(` , a non-canonical header one gate over, and counting
    a non-canonical label outside gate 17 is that card's declared non-goal. Nothing regresses by holding it: a
    digit-form label written in an invisible form was outside this rule before T286 as well. Normalising 15d2
    is what lets the two grammars converge again.
    -ProseException carries the one declared NON-header: `# 8.2a' already pins the two SCANNER versions;` is
    prose continuing a block inside 13a'', not a block header of its own. TD168 records it as the reason a
    widened projection looked unlandable, and it is matched on its TEXT rather than its line number so edits
    above it are safe. It is deliberately separate from -Continued, which excuses real block HEADERS.
    Findings carry Kind = 'unprojected' (a header outside the verdict), 'orphan' (a continuation-form header
    whose label is declared continued but has NO canonical declaration to continue, so the declaration would
    be covering a sub-gate that was never declared at all) or 'count' (a declared continuation count that
    disagrees with the file, in either direction). The three need different fixes, and a gate message is only
    useful if it states the fix.
  #>
  [CmdletBinding()]
  param(
    [string]$Path,
    [AllowEmptyString()][string]$Text,
    $Continued = (Get-ScaffoldContinuedSubGateLabel),
    [string[]]$ProseException = @('already pins the two SCANNER versions')
  )
  if ($PSBoundParameters.ContainsKey('Path')) { $Text = [System.IO.File]::ReadAllText((Convert-Path -LiteralPath $Path)) }
  $projectedDecl = @(Get-ScaffoldDuplicateSubGateLabel -Text $Text -Declaration)
  $projectedLine = @($projectedDecl | ForEach-Object { $_.Line })
  $projectedLabel = @($projectedDecl | ForEach-Object { $_.Label })
  $headerLine = $Text -split '\r?\n'
  $findings = @()
  $continuationSeen = @{}
  for ($i = 0; $i -lt $headerLine.Count; $i++) {
    $candidate = [regex]::Match($headerLine[$i], "^#[ \t]+(?<label>\d+(?:\.\d+)*[a-z]+'*)(?=[.(\uFF08 \t])")
    if (-not $candidate.Success) { continue }
    if ($projectedLine -contains ($i + 1)) { continue }
    if (@($ProseException | Where-Object { $headerLine[$i].Contains($_) }).Count -gt 0) { continue }
    $label = $candidate.Groups['label'].Value
    # The separator the lookahead already matched, read back rather than re-derived: the continuation form is
    # the label followed IMMEDIATELY by an opening paren, half-width or full-width.
    $separator = $headerLine[$i].Substring($candidate.Length, 1)
    if (($separator -eq '(' -or $separator -eq [char]0xFF08) -and @($Continued.Keys) -ccontains $label) {
      if ($projectedLabel -cnotcontains $label) { $findings += [pscustomobject]@{ Kind = 'orphan'; Label = $label; Line = $i + 1; Declared = $Continued[$label]; Found = 0 }; continue }
      if (-not $continuationSeen.ContainsKey($label)) { $continuationSeen[$label] = 0 }
      $continuationSeen[$label]++
      continue
    }
    $findings += [pscustomobject]@{ Kind = 'unprojected'; Label = $label; Line = $i + 1; Declared = 0; Found = 0 }
  }
  foreach ($continuedLabel in @($Continued.Keys | Sort-Object)) {
    $seen = 0
    if ($continuationSeen.ContainsKey($continuedLabel)) { $seen = $continuationSeen[$continuedLabel] }
    if ($seen -eq $Continued[$continuedLabel]) { continue }
    # An orphan already names this label and states a different fix; reporting the count as well would send
    # the reader to restate a number when the missing thing is the canonical header.
    if (@($findings | Where-Object { $_.Kind -eq 'orphan' -and $_.Label -eq $continuedLabel }).Count -gt 0) { continue }
    $findings += [pscustomobject]@{ Kind = 'count'; Label = $continuedLabel; Line = 0; Declared = $Continued[$continuedLabel]; Found = $seen }
  }
  return $findings
}

# T182/T186 arms 2/2: the declared examples. Each one is a form the live file actually contains or a failure
# the rule must catch, so a rule that stops deciding fails here before its silence on the real file is ever
# trusted. Every finding NAMES its arm: the probe that runs them prints what it got back, and a mutation that
# breaks one arm's judgement has to be tellable from one that breaks another.
function Test-ScaffoldUnprojectedSubGateHeaderExamples {
  $findings = @()
  # 1. The space form is REPORTED. This is the blind spot TD168 registered - the label reads fine to a human
  #    and is exempt from the uniqueness verdict, so a collision on it ships green.
  $spaceGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{} -Text '# 9z a new sub-gate written the invisible way')
  if ($spaceGot.Count -ne 1 -or $spaceGot[0].Kind -ne 'unprojected' -or $spaceGot[0].Label -ne '9z') {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 1: a space-form sub-gate header went unreported. That form is invisible to 14m, which is the entire blind spot this rule exists to close."
  }
  # 2. The space form stays reported even for a label that IS declared continued. Only the continuation form
  #    is declared; excusing the space form here would reopen the blind spot for the labels carrying the most
  #    blocks, and it is the reason 9g's second header moved instead of the rule.
  $spaceContinuedGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{ '9z' = 1 } -Text "# 9z. canonical`n# 9z a continuation written in the space form")
  if (@($spaceContinuedGot | Where-Object { $_.Kind -eq 'unprojected' }).Count -ne 1) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 2: a SPACE-form header under a declared continued label was excused. The declaration covers the continuation form only, or the invisible form comes back through the labels that carry the most blocks."
  }
  # 3. The declared continuation form, under a declared label with a canonical header, is SILENT. Without it
  #    the rule cannot land on a file where a sub-gate is genuinely implemented across several blocks, and an
  #    unlandable rule is no rule.
  $legalGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{ '9z' = 1 } -Text "# 9z. canonical`n# 9z(the declared continuation)")
  if ($legalGot.Count -ne 0) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 3: a declared continuation under a canonically declared label was reported. A continuation is not a second declaration, and normalising it would manufacture a false duplicate."
  }
  # 4. A continuation-form header whose label is NOT declared continued is REPORTED, so the form is never a
  #    free pass a new sub-gate can take by writing itself parenthetically. The live file carried 22 of these
  #    at T182, and TD168 described the problem as "label then a space", which would have missed them all.
  $undeclaredGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{} -Text '# 15z(T1): a header in the parenthetical form')
  if ($undeclaredGot.Count -ne 1 -or $undeclaredGot[0].Kind -ne 'unprojected' -or $undeclaredGot[0].Label -ne '15z') {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 4: an UNDECLARED parenthetical header went unreported. The continuation form excuses a header only where the label is declared continued; otherwise it is as invisible to 14m as the space form."
  }
  # 5. A declared continuation with NO canonical header to continue is an ORPHAN. This is what stops the
  #    declaration from covering a sub-gate that was never canonically declared at all, which would turn the
  #    rule straight back into the exemption it replaced.
  $orphanGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{ '9z' = 1 } -Text '# 9z(continues nothing at all)')
  if ($orphanGot.Count -ne 1 -or $orphanGot[0].Kind -ne 'orphan' -or $orphanGot[0].Label -ne '9z') {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 5: a continuation whose label has no canonical declaration was not reported as an orphan. With no header to continue, the declaration excuses a sub-gate that is outside the uniqueness verdict entirely."
  }
  # 6. The count is read in BOTH directions. One block too few: a deleted block would shrink a sub-gate while
  #    the declaration still claimed it.
  $tooFewGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{ '9z' = 2 } -Text "# 9z. canonical`n# 9z(the only continuation)")
  if ($tooFewGot.Count -ne 1 -or $tooFewGot[0].Kind -ne 'count' -or $tooFewGot[0].Declared -ne 2 -or $tooFewGot[0].Found -ne 1) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 6: a declared count HIGHER than the file went unreported, so a deleted block would shrink a sub-gate while its declaration still claimed it."
  }
  # 7. One block too many: a new block could join an already-continued sub-gate without anyone declaring it,
  #    which is the only way the continuation form could be used to smuggle work past the verdict.
  $tooManyGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{ '9z' = 1 } -Text "# 9z. canonical`n# 9z(one)`n# 9z(two)")
  if ($tooManyGot.Count -ne 1 -or $tooManyGot[0].Kind -ne 'count' -or $tooManyGot[0].Found -ne 2) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 7: a declared count LOWER than the file went unreported, so a new block could join an already-continued sub-gate without anyone declaring it."
  }
  # 8. The one declared prose line is NOT a header. TD168 names this line as the reason widening the projector
  #    was unlandable; here the exclusion is explicit and machine-checked instead of implicit.
  $proseGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{} -Text "# 8.2a' already pins the two SCANNER versions; what was unpinned is the uv BINARY")
  if ($proseGot.Count -ne 0) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 8: the declared prose line inside 13a'' was reported as a header. It is prose continuing a block, not a sub-gate header, and it is excused by TEXT rather than by the continuation map."
  }
  # 9. A canonical header is silent, and it is the PROJECTOR that decides so - this rule never re-derives the
  #    canonical grammar, which is why the two cannot drift apart (L271).
  $canonicalGot = @(Get-ScaffoldUnprojectedSubGateHeader -Continued @{} -Text '# 14z. a canonical header')
  if ($canonicalGot.Count -ne 0) {
    $findings += "[SUBGATE-FORM-EXAMPLE] arm 9: a canonical header was reported as unprojected, so this rule is no longer reading Get-ScaffoldDuplicateSubGateLabel's own verdict."
  }
  return $findings
}

# -- T283-R3-VERDICT-EXTRACT: the reviewer's verdict object, found by PARSING and never by counting ------
# review.ps1 has always located the verdict with a brace matcher. T277 widened it from one level of nesting
# to a balanced-brace regex, and BOTH count braces wherever they appear - inside a JSON string included.
# T277's own ship (PR #364) measured what that costs: the reviewer emitted a well-formed verdict carrying
# eight findings, one reason contained a brace, the counter stopped inside that string, and the harness
# rejected the whole verdict as malformed. A malformed verdict carries no axes, so the Tier-S spec block
# that same card had just built could not fire on the one class of verdict it exists for - a reviewer that
# explains itself in prose containing a brace.
#
# The question this asks is "does this PARSE", never "do the braces balance". From each '{' in the text,
# walk forward with a quote/escape state machine, stop at the brace that closes that one, and hand the
# slice to ConvertFrom-Json; a slice that will not parse is not a verdict, so the scan resumes at the next
# '{'. The first candidate that parses wins, which keeps T277's answer to the nesting question - an `axes`
# object nested inside the verdict can never win over the verdict containing it - without its blindness.
#
# NOTHING comes back when no candidate parses: an empty pipeline, not a $null. review.ps1 fail-closes on
# that, and a returned $null would count as one element wherever a caller wraps the result in @(...).
#
# Pure: text in, object out. It reads no file, spawns nothing, and knows nothing about the verdict schema -
# what a parsed object must CARRY stays review.ps1's judgement (TD114), which is why the declared examples
# below can feed it shapes no reviewer would ever write.
function Get-ScaffoldVerdictJson {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][AllowNull()][string]$Text,
    [ValidateSet('live', 'brace-count', 'first-object-only', 'accept-truncated', 'axes-brace')][string]$Variant = 'live'
  )
  if ([string]::IsNullOrEmpty($Text)) { return }
  # The two rejected shapes that are BEHAVIOURS rather than inputs, each disabling one half of the scan:
  # 'brace-count' stops honouring quotes, which is the defect this function replaces; 'axes-brace' stops
  # honouring depth - the first '}' ends the object - which is the CLASS T277 replaced, and on a verdict
  # whose nested axes carries a brace in its reason it hands back the INNER object, carrying no axes at all.
  $stringAware = ($Variant -ne 'brace-count')
  $depthAware = ($Variant -ne 'axes-brace')
  $sawCandidate = $false
  for ($i = 0; $i -lt $Text.Length; $i++) {
    if ($Text[$i] -ne '{') { continue }
    $sawCandidate = $true
    $depth = 0
    $inString = $false
    $escape = $false
    $closed = $false
    for ($j = $i; $j -lt $Text.Length; $j++) {
      $c = $Text[$j]
      if ($inString) {
        # Order matters: a backslash consumed as an escape can itself be escaped, so the pending-escape
        # test runs before the backslash test. Without it \\" reads as an escaped quote and the scan
        # spends the rest of the text inside a string that already ended.
        if ($escape) { $escape = $false }
        elseif ($c -eq '\') { $escape = $true }
        elseif ($c -eq '"') { $inString = $false }
        continue
      }
      if ($stringAware -and $c -eq '"') { $inString = $true; continue }
      if ($c -eq '{') { $depth++; continue }
      if ($c -ne '}') { continue }
      $depth--
      if ($depthAware -and $depth -gt 0) { continue }
      $closed = $true
      # A candidate that CLOSED and does not parse is not a verdict: resume at the next '{' rather than
      # deciding the whole text is unusable, which is what lets prose containing a stray {block} precede
      # one. `break` leaves the inner loop only; $closed is what tells the outer loop it may resume.
      try { return ($Text.Substring($i, $j - $i + 1) | ConvertFrom-Json) } catch { break }
    }
    # A candidate that ran off the END without closing ends the whole scan, and this is the fail-closed
    # half of the rule. Everything after that point is INSIDE an object that never terminated, so the next
    # thing that parses is a FRAGMENT of a truncated document - and a reviewer killed mid-write leaves
    # exactly that shape: an outer verdict cut short around an `axes` object whose first axis did close.
    # Resuming would hand that axis back as the verdict, and an axis carries `verdict`, so a half-written
    # file would read as a clean decision. Measured on this function before the guard existed: a verdict
    # truncated inside its second axis returned {verdict=pass} with no axes at all, which review.ps1 reads
    # as an approval. It also bounds the scan - without it every unterminated brace rescans to end of text.
    if (-not $closed) { break }
    if ($Variant -eq 'first-object-only') { break }
  }
  # 'accept-truncated': the rejected shape that hands back an approving verdict for a body it never parsed.
  # A killed, rate-limited or half-flushed reviewer leaves exactly that - an object that opened and never
  # closed - and treating it as complete turns an outage into a pass. The live rule returns nothing, for
  # both truncated shapes: the one that carries no closed object at all, and the one whose nested axis did
  # close. The second is the dangerous one and it is why the `$closed` guard above exists.
  if ($sawCandidate -and $Variant -eq 'accept-truncated') { return ([pscustomobject]@{ verdict = 'pass'; reasons = @() }) }
}

function Test-ScaffoldVerdictJsonExamples {
  <#
  .SYNOPSIS  T283-R3-VERDICT-EXTRACT: declared examples for Get-ScaffoldVerdictJson. Returns findings; an
             empty result is green. Hermetic - literal text in, so it reads no file and needs no reviewer.
  .DESCRIPTION
    Both directions, as the sibling tables carry: the live rule must report nothing, and each -Variant must
    report at least one finding, so a rule that stopped discriminating is visible instead of quietly green.
    The axes arm is the one a Tier-S ship depends on and it lives here rather than in the card's DoD
    because that payload may carry no double quote at all (L95) and a nested JSON literal cannot be spelled
    without one.
  #>
  [CmdletBinding()]
  param([ValidateSet('brace-count', 'first-object-only', 'accept-truncated', 'axes-brace')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { 'live' }
  $findings = @()
  # Both readers go through a presence check rather than a dereference: "nothing came back" is the very
  # defect several arms below exist to catch, and under StrictMode dereferencing $null would kill the
  # caller instead of reporting a finding - red because the script died is red for the wrong reason (L167).
  $has = { param($o, $n) [bool]($o -and ($o.PSObject.Properties.Name -contains $n)) }
  $verdictOf = { param($o) if ($o -and ($o.PSObject.Properties.Name -contains 'verdict')) { [string]$o.verdict } else { '(nothing came back)' } }
  # 1. The shape T277's R3 measured failing: an UNMATCHED OPENING brace inside a reason string. A counter
  #    never closes the object; a parser never notices the brace at all.
  $a1 = Get-ScaffoldVerdictJson -Text '{"verdict":"pass","reasons":["an unmatched { inside a string"]}' -Variant $v
  if ((& $verdictOf $a1) -cne 'pass') { $findings += "[VERDICT-JSON-EXAMPLE] a verdict whose reason carries an unmatched opening brace did not come back as pass (got '$(& $verdictOf $a1)'). This is the exact shape measured on PR #364, where it landed as a malformed advisory and the Tier-S spec block never ran. [FIX] fix the rule, never the example." }
  # 2. The mirror shape - an unmatched CLOSING brace - with prose on both sides, which is what a reviewer
  #    that narrates before emitting its verdict actually writes.
  $a2 = Get-ScaffoldVerdictJson -Text 'prose before {"verdict":"block","reasons":["x } y"]} prose after' -Variant $v
  if ((& $verdictOf $a2) -cne 'block') { $findings += "[VERDICT-JSON-EXAMPLE] a verdict whose reason carries an unmatched closing brace, wrapped in prose, did not come back as block (got '$(& $verdictOf $a2)'). [FIX] fix the rule, never the example." }
  elseif (@($a2.reasons).Count -ne 1) { $findings += "[VERDICT-JSON-EXAMPLE] the reasons array did not survive extraction intact: expected 1 reason, got $(@($a2.reasons).Count). A verdict whose findings are truncated is a fix prompt with its content removed. [FIX] fix the rule, never the example." }
  # 3. NEGATIVE CONTROL: text with no object at all must produce nothing, so review.ps1 keeps its
  #    fail-closed no-verdict path instead of being handed something to approve.
  if (@(Get-ScaffoldVerdictJson -Text 'I am sorry, but I cannot help with that request.' -Variant $v).Count -ne 0) { $findings += '[VERDICT-JSON-EXAMPLE] a refusal written as prose produced a result. Nothing must come back, or a reviewer that never emitted a verdict acquires one. [FIX] fix the rule, never the example.' }
  # 4. NEGATIVE CONTROL: a truncated object is not a verdict. This is what a killed or rate-limited
  #    reviewer leaves behind, and accepting it would turn an outage into an approval.
  if (@(Get-ScaffoldVerdictJson -Text '{"verdict":"pass","reasons":[' -Variant $v).Count -ne 0) { $findings += '[VERDICT-JSON-EXAMPLE] a truncated object produced a result. An object that opened and never closed is a half-written file, not a decision. [FIX] fix the rule, never the example.' }
  # 4b. THE DANGEROUS TRUNCATION, and the one arm 4 cannot reach: the outer verdict is cut short but its
  #    first nested AXIS closed cleanly. An axis carries `verdict`, so a scan that resumes after an
  #    unterminated candidate hands that axis back and a killed reviewer reads as an approval. Measured
  #    before the guard: this returned {verdict=pass} with no axes at all.
  $a4b = Get-ScaffoldVerdictJson -Text '{"verdict":"block","reasons":["r"],"axes":{"spec":{"verdict":"pass","reasons":[]},"standards":{"verdict":"block","reasons":["cut off here' -Variant $v
  if (@($a4b).Count -ne 0) { $findings += "[VERDICT-JSON-EXAMPLE] a verdict truncated inside its second axis produced a result ('$(& $verdictOf $a4b)') - the scan resumed inside an object that never terminated and handed back a FRAGMENT. An axis carries a verdict of its own, so a reviewer killed mid-write reads as a clean decision and the ship merges. [FIX] fix the rule, never the example." }
  # 5. A candidate that fails to parse must not end the scan: the reviewer may narrate with braces before
  #    it emits the verdict, and stopping at the first candidate loses the real one.
  $a5 = Get-ScaffoldVerdictJson -Text 'notes {oops} then the verdict {"verdict":"pass","reasons":[]}' -Variant $v
  if ((& $verdictOf $a5) -cne 'pass') { $findings += "[VERDICT-JSON-EXAMPLE] a verdict preceded by an unparseable braced fragment was not found (got '$(& $verdictOf $a5)') - the scan stopped at the first candidate instead of continuing to the one that parses. [FIX] fix the rule, never the example." }
  # 6. THE ARM A TIER-S SHIP DEPENDS ON: a nested `axes` object whose spec reason carries a brace. The
  #    whole verdict must come back - not the inner axis object, which parses cleanly and carries no axes,
  #    so task.ps1 would read it as "no spec finding" and merge.
  $a6 = Get-ScaffoldVerdictJson -Text '{"verdict":"block","reasons":["[spec] #6 @ x.ps1 - the guard { never closes"],"axes":{"spec":{"verdict":"block","reasons":["#6 @ x.ps1 - the guard { never closes"]},"standards":{"verdict":"pass","reasons":[]}}}' -Variant $v
  if (-not (& $has $a6 'axes')) { $findings += '[VERDICT-JSON-EXAMPLE] a verdict carrying a nested axes object whose spec reason contains a brace came back without axes - the extraction handed over an INNER object instead of the verdict. task.ps1 reads a missing spec axis as no spec finding and merges, which is the Tier-S refusal disappearing silently. [FIX] fix the rule, never the example.' }
  elseif (-not (& $has $a6.axes 'spec') -or ((& $verdictOf $a6.axes.spec) -cne 'block')) { $findings += "[VERDICT-JSON-EXAMPLE] the spec axis did not survive extraction as a block (got '$(if (& $has $a6.axes 'spec') { & $verdictOf $a6.axes.spec } else { '(no spec axis)' })'). [FIX] fix the rule, never the example." }
  elseif (-not (& $has $a6.axes.spec 'reasons') -or (@($a6.axes.spec.reasons)[0] -notlike '*{*')) { $findings += "[VERDICT-JSON-EXAMPLE] the brace inside the spec axis reason was lost: got '$(if (& $has $a6.axes.spec 'reasons') { @($a6.axes.spec.reasons)[0] } else { '(no reasons)' })'. The reason is the fix prompt, and a sanitised one no longer says what the reviewer said. [FIX] fix the rule, never the example." }
  return @($findings)
}

# -- T160-REVIEW-ROUND-MEMORY (TD159): does the prior round's verdict belong in this round's prompt? --
# review.ps1 deletes .review/<branch>.json before invoking the reviewer, so round N knows nothing of round
# N-1. The rubric's section 0 clause tells the reviewer its reasons must be the COMPLETE finding list, and
# that a follow-up round may only judge whether prior findings are fixed and what is genuinely new - but
# with the prior verdict discarded that instruction has no data behind it, and the measured behaviour is
# findings arriving one or two per round across several rounds.
#
# This judges ONLY whether to carry. It never writes, never deletes, and does not touch the discard - the
# discard keeps its fail-open governance exactly as written; this merely READS before it runs.
# NO carry when there is no prior verdict, it will not parse, it holds no reasons, or its sha EQUALS the
# reviewed sha. That last arm is the load-bearing one: a verdict at the same commit is THIS round's own
# output, and handing a round its own findings back would manufacture agreement out of nothing.
function Get-ScaffoldReviewFollowupDecisionVia($PriorVerdictText, $ReviewedSha, $Variant) {
  $no = [pscustomobject]@{ Carry = $false; Reasons = @(); PriorSha = ''; Why = '' }
  if ([string]::IsNullOrWhiteSpace($PriorVerdictText)) { $no.Why = 'no prior verdict on disk'; return $no }
  $obj = $null
  try { $obj = $PriorVerdictText | ConvertFrom-Json -ErrorAction Stop } catch { $no.Why = 'prior verdict is not readable JSON'; return $no }
  if (-not $obj) { $no.Why = 'prior verdict parsed to nothing'; return $no }
  $priorSha = ''
  if ($obj.PSObject.Properties.Name -contains 'sha') { $priorSha = [string]$obj.sha }
  $priorReasons = @()
  if ($obj.PSObject.Properties.Name -contains 'reasons') {
    $priorReasons = @($obj.reasons | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | ForEach-Object { [string]$_ })
  }
  if ($priorReasons.Count -eq 0) { $no.Why = 'prior verdict carries no reasons'; $no.PriorSha = $priorSha; return $no }
  # The sha arm. 'ignore-sha' is the declared negative control and skips exactly this comparison, so the
  # example table can prove the same-commit suppression comes from HERE and not from some earlier branch.
  if ($Variant -ne 'ignore-sha' -and $priorSha -and $priorSha -eq $ReviewedSha) {
    $no.Why = 'prior verdict sits at the reviewed sha - it is this round own output'
    $no.PriorSha = $priorSha
    return $no
  }
  return [pscustomobject]@{ Carry = $true; Reasons = @($priorReasons); PriorSha = $priorSha; Why = 'prior verdict names a different sha' }
}

function Get-ScaffoldReviewFollowupDecision {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][string]$PriorVerdictText,
    [AllowEmptyString()][string]$ReviewedSha
  )
  return (Get-ScaffoldReviewFollowupDecisionVia $PriorVerdictText $ReviewedSha $null)
}

function Test-ScaffoldReviewFollowupExamples {
  <#
  .SYNOPSIS  T160 arms 5/6: declared examples for the carry-forward judgement. Returns findings; an empty
             result is green. Hermetic - synthetic JSON only, no .review directory and no git.
  .DESCRIPTION
    -Variant 'ignore-sha' feeds the decision the same input with the sha comparison skipped and must produce
    a finding, which is what proves the same-commit suppression comes from that comparison rather than from
    one of the earlier no-carry arms.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-sha')][string]$Variant)
  $shaNow = 'aaaa1111'
  $shaOld = 'bbbb2222'
  $sameShaVerdict = '{"verdict":"block","reasons":["r1"],"sha":"' + $shaNow + '","branch":"T1-X"}'
  $priorVerdict = '{"verdict":"block","reasons":["r1","r2"],"sha":"' + $shaOld + '","branch":"T1-X"}'

  if ($Variant -eq 'ignore-sha') {
    # The finding IS the evidence here, exactly as in Test-ScaffoldHarnessRatioExamples: with the sha
    # comparison skipped, a verdict sitting at the REVIEWED sha is carried back into its own review. Silence
    # in this variant would mean some earlier arm was doing the suppressing and the sha arm is dead weight.
    $ig = Get-ScaffoldReviewFollowupDecisionVia $sameShaVerdict $shaNow 'ignore-sha'
    if ($ig.Carry) {
      return @('[R3-FOLLOWUP-EXAMPLE] without the sha comparison, a verdict at the REVIEWED sha carries into its own round - a reviewer would be handed its own findings back and could only agree with itself. The sha arm is what prevents this.')
    }
    return @('[R3-FOLLOWUP-EXAMPLE] the ignore-sha control produced NO carry, so the same-commit case is being suppressed by an arm OTHER than the sha comparison - this control proves nothing about the arm it targets. [FIX] fix the rule or the control, never the expectation.')
  }

  $findings = @()
  if ((Get-ScaffoldReviewFollowupDecision -PriorVerdictText '' -ReviewedSha $shaNow).Carry) {
    $findings += '[R3-FOLLOWUP-EXAMPLE] an absent prior verdict produced a carry - the first round on any branch would be handed findings that do not exist.'
  }
  if ((Get-ScaffoldReviewFollowupDecision -PriorVerdictText 'not json at all' -ReviewedSha $shaNow).Carry) {
    $findings += '[R3-FOLLOWUP-EXAMPLE] an unparseable prior verdict produced a carry - unreadable must mean no data, never invented data.'
  }
  if ((Get-ScaffoldReviewFollowupDecision -PriorVerdictText ('{"verdict":"pass","reasons":[],"sha":"' + $shaOld + '"}') -ReviewedSha $shaNow).Carry) {
    $findings += '[R3-FOLLOWUP-EXAMPLE] a prior verdict with an empty reasons array produced a carry - there is nothing to carry.'
  }
  if ((Get-ScaffoldReviewFollowupDecision -PriorVerdictText $sameShaVerdict -ReviewedSha $shaNow).Carry) {
    $findings += '[R3-FOLLOWUP-EXAMPLE] a verdict at the REVIEWED sha was carried - the round would be fed its own findings back, manufacturing agreement out of nothing.'
  }
  $carried = Get-ScaffoldReviewFollowupDecision -PriorVerdictText $priorVerdict -ReviewedSha $shaNow
  if (-not $carried.Carry) {
    $findings += '[R3-FOLLOWUP-EXAMPLE] a readable prior verdict at a DIFFERENT sha did not carry - that is the whole mechanism, and without it every round re-reads the diff cold (TD159).'
  }
  elseif (@($carried.Reasons).Count -ne 2) {
    $findings += "[R3-FOLLOWUP-EXAMPLE] carried $(@($carried.Reasons).Count) reason(s) where 2 were reported - prior findings must arrive intact or the follow-up round judges a partial list."
  }
  return $findings
}

# -- T189-R3-QUALITY-ROUNDS (TD184): how many of the preserved rounds were QUALITY rounds? --
# R3 RECORDS how a run ended (`run_status`, T188/TD181); nothing acted on it. `Get-ReviewBlockDetail`
# counted every round-indexed sibling with a bare `.Count`, so a reviewer that timed out twice was
# indistinguishable IN THE COUNT from two genuine quality rounds - and that count is the input CLAUDE.md's
# "maker and checker stop after two rounds and queue a human" rule reads.
#
# TWO NUMBERS, BOTH HONEST, NEVER ONE. Quality is what the stop rule is about; Attempts is what the corpus
# preserved (TD161) and is what the infrastructure retries actually cost. Reporting only the filtered
# number would hide a reviewer that failed six times behind the word "round 1".
#
# UNKNOWN COUNTS AS QUALITY, and the direction is the whole point. A file carrying no `run_status` (every
# file written before T188), one that will not parse, or one carrying a class not named here is counted as
# a quality round. Miscounting UPWARD makes the stop rule queue a human EARLIER; miscounting downward would
# let a genuinely stuck maker/checker pair loop forever, which is the failure that stop rule exists to
# prevent. Fail-closed here means "count it".
#
# This function READS. It never deletes, renames or re-indexes a round file: `$roundIndex` in review.ps1 is
# the sibling's FILE NAME, and the preserved corpus must keep recording every attempt.
function Get-ScaffoldQualityRoundCountVia($RoundText, $Variant) {
  $texts = @(@($RoundText) | Where-Object { $null -ne $_ })
  $quality = 0
  $classes = [ordered]@{}
  foreach ($t in $texts) {
    $status = ''
    # THE ARM. 'ignore-run-status' is the declared negative control and skips exactly this read, so the
    # example table can prove the filtering comes from HERE rather than from the fallthrough below.
    if ($Variant -ne 'ignore-run-status') {
      try {
        $o = [string]$t | ConvertFrom-Json -ErrorAction Stop
        if ($o -and ($o.PSObject.Properties.Name -contains 'run_status')) { $status = [string]$o.run_status }
      } catch { $status = '' }   # unreadable means unknown, and unknown counts as quality (see header)
    }
    if ([string]::IsNullOrWhiteSpace($status) -or $status -eq 'success') { $quality++; continue }
    if ($classes.Contains($status)) { $classes[$status] = 1 + $classes[$status] } else { $classes[$status] = 1 }
  }
  $summary = "quality-round $quality of $($texts.Count) attempt(s)"
  if ($classes.Count -gt 0) {
    $spent = @($texts.Count - $quality)
    $named = @($classes.Keys | ForEach-Object { "$_ x$($classes[$_])" }) -join ', '
    $summary += " ($spent non-success: $named)"
  }
  return [pscustomobject]@{
    Quality    = $quality
    Attempts   = $texts.Count
    NonSuccess = $classes
    Summary    = $summary
  }
}

function Get-ScaffoldQualityRoundCount {
  [CmdletBinding()]
  param([AllowEmptyCollection()][string[]]$RoundText)
  return (Get-ScaffoldQualityRoundCountVia $RoundText $null)
}

function Test-ScaffoldQualityRoundExamples {
  <#
  .SYNOPSIS  T189 (TD184): declared examples for the quality-round count. Returns findings; empty is green.
             Hermetic - synthetic JSON strings only, no .review directory and no filesystem.
  .DESCRIPTION
    -Variant 'ignore-run-status' feeds the same input with the class read skipped and must produce a
    finding. That control is what proves the filtering comes from the run_status read rather than from some
    other arm - without it, a green table would be consistent with the read having been deleted.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-run-status')][string]$Variant)
  $timeout = '{"verdict":"block","reasons":["reviewer exceeded its wall-clock budget"],"sha":"aaaa1111","run_status":"timeout"}'
  $realBlock = '{"verdict":"block","reasons":["#6 missing test"],"sha":"cccc3333","run_status":"success"}'
  $legacy = '{"verdict":"block","reasons":["#6 missing test"],"sha":"dddd4444"}'
  $unreadable = 'not json at all'

  if ($Variant -eq 'ignore-run-status') {
    # The finding IS the evidence: with the class read skipped, two timeouts and one real block report
    # three quality rounds - exactly the conflation TD184 records. Silence here would mean the filtering
    # lives somewhere else and the run_status read is dead weight.
    $ig = Get-ScaffoldQualityRoundCountVia @($timeout, $timeout, $realBlock) 'ignore-run-status'
    if ($ig.Quality -ne 1) {
      return @('[R3-QUALITY-ROUND-EXAMPLE] without the run_status read, two timeouts plus one real block report ' + $ig.Quality + ' quality round(s) instead of 1 - an infrastructure failure spends a quality round and the two-round stop rule queues a human over a reviewer that never ran (TD184).')
    }
    return @('[R3-QUALITY-ROUND-EXAMPLE] the ignore-run-status control still reported 1 quality round, so the filtering is coming from an arm OTHER than the run_status read - this control proves nothing about the arm it targets. [FIX] fix the rule or the control, never the expectation.')
  }

  $findings = @()

  $twoTimeouts = Get-ScaffoldQualityRoundCount -RoundText @($timeout, $timeout, $realBlock)
  if ($twoTimeouts.Quality -ne 1) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] two injected timeouts followed by one genuine quality block reported $($twoTimeouts.Quality) quality round(s) where 1 is the contract - that count is what the two-round stop rule reads, so a reviewer that never ran would queue a human (TD184)."
  }
  if ($twoTimeouts.Attempts -ne 3) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] the same three rounds reported $($twoTimeouts.Attempts) attempt(s) where 3 were preserved - the attempt count is what the retries actually cost and must never be filtered away with the quality count."
  }
  if ((@($twoTimeouts.NonSuccess.Keys) -join ',') -ne 'timeout' -or $twoTimeouts.NonSuccess['timeout'] -ne 2) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] the non-success classes came back as '$(@($twoTimeouts.NonSuccess.Keys) -join ',')' rather than a single timeout counted twice - an operator reading only the gap cannot tell a timing-out reviewer from a missing CLI."
  }
  if ($twoTimeouts.Summary -notmatch '1 of 3' -or $twoTimeouts.Summary -notmatch 'timeout') {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] the summary '$($twoTimeouts.Summary)' does not name both numbers and the class behind the gap - a shortfall with no stated cause reads as a bug in the counter."
  }

  $legacyOnly = Get-ScaffoldQualityRoundCount -RoundText @($legacy, $legacy)
  if ($legacyOnly.Quality -ne 2) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] rounds carrying NO run_status reported $($legacyOnly.Quality) quality round(s) where 2 is the contract - every file written before T188 lacks the field, and an unknown class must count UPWARD or the stop rule silently stops stopping."
  }

  $broken = Get-ScaffoldQualityRoundCount -RoundText @($unreadable, $realBlock)
  if ($broken.Quality -ne 2) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] an unreadable round file reported $($broken.Quality) quality round(s) where 2 is the contract - unreadable means unknown, and unknown counts as a quality round rather than vanishing from the count."
  }

  $blockIsQuality = Get-ScaffoldQualityRoundCount -RoundText @($realBlock)
  if ($blockIsQuality.Quality -ne 1) {
    $findings += '[R3-QUALITY-ROUND-EXAMPLE] a verdict of block recording run_status success was not counted as a quality round - the class records how the run ENDED, never what it decided, and collapsing the two rebuilds exactly the conflation TD181 undid.'
  }
  if ($blockIsQuality.Summary -match 'non-success') {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] the summary '$($blockIsQuality.Summary)' names a non-success gap where there is none - a clean run must read cleanly."
  }

  $none = Get-ScaffoldQualityRoundCount -RoundText @()
  if ($none.Quality -ne 0 -or $none.Attempts -ne 0) {
    $findings += "[R3-QUALITY-ROUND-EXAMPLE] with no rounds preserved the count came back Quality=$($none.Quality) Attempts=$($none.Attempts) rather than 0/0 - the first review on any branch walks this path."
  }

  return $findings
}

# -- T146-RUBRIC-DIM-CONDITIONAL: spend reviewer attention only on dimensions this diff can trigger --
# The prompt carries all 17 dimensions every round regardless of content. Measured over the surviving
# verdict corpus (23 findings / 29 dimension labels), eight have never produced a finding and four of those
# cannot fire in this repo at all. They stay LIVE DOWNSTREAM, so nothing is deleted - four dimensions are
# made conditional, and only the four the rubric ALREADY marks "only when" in its own prose. Mechanising a
# condition the standard already states is not a new rule; inventing triggers the rubric never declared
# would be, so #8 and #16 (which say "applies to every code change") are untouched.
#
# FAIL-OPEN IS THE CONTRACT, and it runs in one direction only: a trigger that cannot be decided from the
# inputs INCLUDES the dimension. A wrongly suppressed dimension is a silent hole in the review; a wrongly
# included one costs only attention. Every branch below therefore defaults to include on empty/unreadable
# input, and the examples pin that direction per dimension.
function Get-ScaffoldRubricSectionVia($RubricText, $DiffText, $CardText, $Variant) {
  # Each entry: the dimension number, and a predicate that answers "can this diff/card trigger it?".
  # $null from a predicate means UNDECIDABLE, which the caller turns into include.
  $suppressed = [System.Collections.Generic.List[int]]::new()
  $included = [System.Collections.Generic.List[int]]::new()

  $haveDiff = -not [string]::IsNullOrWhiteSpace($DiffText)
  $haveCard = -not [string]::IsNullOrWhiteSpace($CardText)

  foreach ($dim in @(13, 14, 15, 17)) {
    $canFire = $null   # undecidable until proven otherwise
    switch ($dim) {
      13 {
        # "only when the change includes DB schema/migrations"
        if ($haveDiff) { $canFire = [bool]($DiffText -match '(?im)(migrations?[\\/]|\.sql\b|CREATE\s+TABLE|ALTER\s+TABLE|ADD\s+COLUMN|DROP\s+COLUMN)') }
      }
      14 {
        # "only when the card has non_goals" - and a card whose only non_goal is the literal none has none.
        if ($haveCard) {
          $ngBlock = [regex]::Match($CardText, '(?ms)^non_goals:\s*(.*?)(?=^\S|\z)').Groups[1].Value
          $ngItems = @([regex]::Matches($ngBlock, '(?m)^\s*-\s*(\S.*?)\s*$') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -and ($_ -notmatch '^(none|无|n/a)$') })
          $canFire = [bool]($ngItems.Count -gt 0)
        }
      }
      15 {
        # "only when the diff calls a third-party library API". Decidable only in the NEGATIVE: a diff that
        # adds no import-like line and touches no dependency manifest cannot be calling a new third-party
        # API. Anything else stays undecidable and is therefore included.
        if ($haveDiff) {
          $touchesDep = [bool]($DiffText -match '(?im)(pyproject\.toml|package(-lock)?\.json|requirements[^\s]*\.txt|go\.mod|Cargo\.toml)')
          $addsImport = [bool]($DiffText -match '(?im)^\+.*\b(import|require|using|from|Install-Module|Import-Module)\b')
          if (-not $touchesDep -and -not $addsImport) { $canFire = $false }
        }
      }
      17 {
        # "only when the card is a bugfix" - the card contract expresses that as a filled diagnosis block,
        # which is exactly what check-cards requires of a bugfix card. A commented-out template line is not one.
        if ($haveCard) { $canFire = [bool]($CardText -match '(?m)^diagnosis:') }
      }
    }
    # 'ignore-undecidable' is the declared negative control: it drops the fail-open default so the examples
    # can prove the include-on-undecidable direction comes from HERE and not from a predicate that happened
    # to return true anyway.
    if ($Variant -eq 'ignore-undecidable' -and $null -eq $canFire) { $canFire = $false }
    if ($null -eq $canFire -or $canFire) { $included.Add($dim) } else { $suppressed.Add($dim) }
  }

  # Assembly: drop only the numbered top-level bullet of a suppressed dimension. Nothing else is rewritten,
  # so a rubric this selector does not recognise passes through byte-identical.
  $kept = @()
  foreach ($line in ([string]$RubricText -split "`r?`n")) {
    $numMatch = [regex]::Match($line, '^(\d+)\.\s+\*\*')
    if ($numMatch.Success -and ([int]$numMatch.Groups[1].Value) -in $suppressed) { continue }
    $kept += $line
  }
  return [pscustomobject]@{
    Include    = @($included)
    Suppressed = @($suppressed)
    Text       = ($kept -join "`n")
  }
}

function Get-ScaffoldRubricSection {
  [CmdletBinding()]
  param(
    [AllowEmptyString()][string]$RubricText,
    [AllowEmptyString()][string]$DiffText,
    [AllowEmptyString()][string]$CardText
  )
  return (Get-ScaffoldRubricSectionVia $RubricText $DiffText $CardText $null)
}

function Test-ScaffoldRubricSectionExamples {
  <#
  .SYNOPSIS  T146 arms 5/6: declared examples for the conditional-dimension selector. Returns findings; an
             empty result is green. Hermetic - synthetic rubric/diff/card text only, no IO.
  .DESCRIPTION
    Both directions are carried per conditional dimension, plus the fail-open direction, which is the one
    that matters most: a wrongly suppressed dimension is a silent hole in the review.
    -Variant 'ignore-undecidable' drops the fail-open default and must produce a finding.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-undecidable')][string]$Variant)
  $rubric = @'
## 2. Should-flag dimensions
13. **Data/persistence design** (only when the change includes DB schema/migrations): x
14. **Scope fidelity** (only when the card has non_goals): x
15. **API-version correctness** (only when the diff calls a third-party library API): x
16. **De-AI-slop** (applies to every code change): x
17. **Bugfix root-cause** (only when the card is a bugfix): x
'@
  $bugfixCard = "id: T1-X`ndiagnosis:`n  root_cause: a real cause`nallow_paths:`n  - x"
  $plainCard = "id: T1-X`nnon_goals:`n  - none`nallow_paths:`n  - x"
  $ngCard = "id: T1-X`nnon_goals:`n  - an actual excluded capability`nallow_paths:`n  - x"
  $sqlDiff = "+++ b/migrations/001.sql`n+CREATE TABLE t (id int);"
  $plainDiff = "+++ b/scripts/foo.ps1`n+Write-Host 'hello'"
  $importDiff = "+++ b/app/x.py`n+import requests"

  if ($Variant -eq 'ignore-undecidable') {
    # The finding IS the evidence: with the fail-open default dropped, an EMPTY diff and card suppress
    # every conditional dimension - the silent-hole direction the card forbids.
    $ig = Get-ScaffoldRubricSectionVia $rubric '' '' 'ignore-undecidable'
    if (@($ig.Suppressed).Count -gt 0) {
      return @("[RUBRIC-SECTION-EXAMPLE] without the fail-open default, undecidable input suppressed dimension(s) $(@($ig.Suppressed) -join ', ') - a review would silently stop judging them. The undecidable-includes branch is what prevents this.")
    }
    return @('[RUBRIC-SECTION-EXAMPLE] the ignore-undecidable control suppressed nothing, so the include-on-undecidable direction is coming from somewhere other than the branch it targets. [FIX] fix the rule or the control, never the expectation.')
  }

  $findings = @()
  # Fail-open: nothing decidable => every conditional dimension survives.
  $blind = Get-ScaffoldRubricSection -RubricText $rubric -DiffText '' -CardText ''
  if (@($blind.Suppressed).Count -ne 0) {
    $findings += "[RUBRIC-SECTION-EXAMPLE] with no diff and no card, dimension(s) $(@($blind.Suppressed) -join ', ') were suppressed. Undecidable must INCLUDE: a wrongly suppressed dimension is a silent hole, a wrongly included one only costs attention."
  }
  # 13, both directions.
  if (13 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $sqlDiff -CardText $plainCard).Include)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a diff adding a migration with CREATE TABLE did not trigger #13 (data/persistence design).'
  }
  if (13 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $plainCard).Suppressed)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a diff touching no schema and no migration still carried #13 - the whole point is not to spend attention on a dimension this diff cannot trigger.'
  }
  # 14, both directions.
  if (14 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $ngCard).Include)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a card declaring a real non_goal did not trigger #14 (capability over-reach).'
  }
  if (14 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $plainCard).Suppressed)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a card whose only non_goal is the literal none still carried #14 - that card has no non-goals to over-reach.'
  }
  # 15, both directions.
  if (15 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $importDiff -CardText $plainCard).Include)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a diff adding an import line did not trigger #15 (API-version correctness).'
  }
  if (15 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $plainCard).Suppressed)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a diff adding no import and touching no dependency manifest still carried #15.'
  }
  # 17, both directions.
  if (17 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $bugfixCard).Include)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a card carrying a filled diagnosis block did not trigger #17 (bugfix root-cause).'
  }
  if (17 -notin @((Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $plainCard).Suppressed)) {
    $findings += '[RUBRIC-SECTION-EXAMPLE] a card with no diagnosis block still carried #17.'
  }
  # Assembly: a suppressed dimension leaves the text, an always-live one never does.
  $assembled = Get-ScaffoldRubricSection -RubricText $rubric -DiffText $plainDiff -CardText $plainCard
  if ($assembled.Text -match '(?m)^13\.') { $findings += '[RUBRIC-SECTION-EXAMPLE] #13 was reported suppressed but its bullet is still in the assembled rubric text.' }
  if ($assembled.Text -notmatch '(?m)^16\.') { $findings += '[RUBRIC-SECTION-EXAMPLE] #16 (applies to every code change) was dropped from the assembled text - only the four conditional dimensions may ever be removed.' }
  if ($assembled.Text -notmatch '## 2\. Should-flag dimensions') { $findings += '[RUBRIC-SECTION-EXAMPLE] a section heading was dropped - assembly removes numbered dimension bullets only.' }
  return $findings
}

# -- T149-SKILL-CONTRACT-GATE: can every skill actually LOAD, and is every skill actually INDEXED? --
# 17 skills carry ~9.5k characters of always-on description text and nothing asserted that any of them
# parses. A SKILL.md with broken frontmatter, no name or no description is a skill that silently never
# triggers while all 17 gates stay green - the same silent-no-op class as the entry hook T148 just closed.
# Two more holes ride along: nothing compared the frontmatter name to the directory name, and the
# dangling-link gate is ONE-DIRECTIONAL (a referenced artifact must exist, but a new skill directory that
# updates no index is green).
#
# Pure: no IO. The caller supplies the observations, which is what makes this testable with no skills on
# disk. ONE index is judged, not three: the skill set is named in several docs, and gating all of them
# would cement the double-source drift CLAUDE.md forbids - the authoritative row is gated, the rest stay
# pointers.
# NOTHING HERE JUDGES DESCRIPTION QUALITY. Under-triggering is the failure mode that matters most for a
# skill, but it is a judgement, and a lexical gate on wording leaks both ways exactly as TD120 records.
function Get-ScaffoldSkillContractIssue {
  [CmdletBinding()]
  param(
    [object[]]$Skill = @(),
    [string[]]$IndexName = @(),
    [hashtable]$Exemption = @{}
  )
  $issues = [System.Collections.Generic.List[string]]::new()
  $seenExempt = [System.Collections.Generic.List[string]]::new()

  foreach ($sk in @($Skill)) {
    $dir = [string]$sk.Name
    $fm = [string]$sk.FrontMatter
    if ([string]::IsNullOrWhiteSpace($fm)) {
      $issues.Add("[SKILL-CONTRACT] $dir has no parseable SKILL.md frontmatter. A skill that cannot load silently never triggers, and nothing else in the suite would notice. [FIX] give it a --- delimited frontmatter block with name and description.")
      continue
    }
    $nm = [regex]::Match($fm, '(?m)^name:\s*(\S.*?)\s*$').Groups[1].Value
    $desc = [regex]::Match($fm, '(?m)^description:\s*(\S.*?)\s*$').Groups[1].Value
    if (-not $nm) { $issues.Add("[SKILL-CONTRACT] $dir declares no non-empty frontmatter 'name'. [FIX] add one matching the directory name.") }
    if (-not $desc) { $issues.Add("[SKILL-CONTRACT] $dir declares no non-empty frontmatter 'description'. The description is the ONLY thing that decides whether a skill triggers, so an absent one is a skill that can never fire.") }
    if ($nm -and ($nm -cne $dir)) {
      if ($Exemption.ContainsKey($dir)) { $seenExempt.Add($dir) }
      else { $issues.Add("[SKILL-CONTRACT] $dir declares frontmatter name '$nm', which differs from its directory. [FIX] make them equal, or - for a VENDORED skill whose body must stay byte-identical to its upstream (gate 9c defends that) - add a declared exemption carrying the reason. Never edit a vendored body to satisfy this.") }
    }
  }
  # A stale exemption is itself a failure, so the list cannot rot into a permanent allowlist. Same ratchet
  # discipline the core-self-check gate applies to its own pending list.
  foreach ($ex in @($Exemption.Keys)) {
    if ($ex -cnotin $seenExempt) {
      $issues.Add("[SKILL-CONTRACT] the exemption for '$ex' is STALE - that directory no longer declares a mismatching name (or no longer exists), so the exemption is excusing nothing. [FIX] delete the entry; an exemption that outlives its cause is how an allowlist stops being a ratchet.")
    }
  }
  # Index completeness, both directions, reported BY NAME from live data (L97: never hardcode the cause).
  $dirNames = @(@($Skill) | ForEach-Object { [string]$_.Name })
  $missing = @($dirNames | Where-Object { $_ -cnotin @($IndexName) })
  $extra = @(@($IndexName) | Where-Object { $_ -and ($_ -cnotin $dirNames) })
  if ($missing.Count) { $issues.Add("[SKILL-CONTRACT] skill director(y/ies) present on disk but absent from the authoritative index row: $($missing -join ', '). The dangling-link gate only checks the other direction, so an unindexed skill is invisible to every reader. [FIX] add them to the trigger-layer row.") }
  if ($extra.Count) { $issues.Add("[SKILL-CONTRACT] the authoritative index row names skill(s) with no directory on disk: $($extra -join ', '). [FIX] remove them from the row, or restore the directory.") }
  return @($issues)
}

function Test-ScaffoldSkillContractExamples {
  <#
  .SYNOPSIS  T149 arm 5: declared examples for the skill-contract predicate. Returns findings; an empty
             result is green. Hermetic - synthetic descriptors only, no .claude/skills on disk.
  .DESCRIPTION
    Both directions per rule. The silence cases matter as much as the reports: a gate that fires on a
    healthy tree gets switched off, which is how the index rule became prose nobody executed.
  #>
  [CmdletBinding()]
  param()
  $ok = @{ Name = 'alpha'; FrontMatter = "name: alpha`ndescription: does a thing" }
  $findings = @()

  if (@(Get-ScaffoldSkillContractIssue -Skill @($ok) -IndexName @('alpha')).Count -ne 0) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a healthy skill that is indexed produced an issue - a gate that fires on a clean tree gets switched off. [FIX] fix the rule, never the example.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @(@{ Name = 'alpha'; FrontMatter = '' }) -IndexName @('alpha')).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] an unparseable SKILL.md went unreported - that is the silent no-op this card exists for.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @(@{ Name = 'alpha'; FrontMatter = 'name: alpha' }) -IndexName @('alpha')).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a skill with no description went unreported. The description is the only thing that decides triggering, so an absent one can never fire.'
  }
  $mismatch = @{ Name = 'taste'; FrontMatter = "name: other-name`ndescription: d" }
  if (@(Get-ScaffoldSkillContractIssue -Skill @($mismatch) -IndexName @('taste')).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a frontmatter name differing from its directory went unreported.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @($mismatch) -IndexName @('taste') -Exemption @{ 'taste' = 'vendored' }).Count -ne 0) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a DECLARED exemption did not suppress the mismatch - a vendored body must never be edited to satisfy this rule, so the exemption is the only legal repair.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @($ok) -IndexName @('alpha') -Exemption @{ 'alpha' = 'no longer mismatches' }).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a STALE exemption went unreported - an exemption that outlives its cause turns the list from a ratchet into a permanent allowlist.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @($ok) -IndexName @()).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] a skill on disk but absent from the index went unreported - the dangling-link gate only checks the other direction.'
  }
  if (@(Get-ScaffoldSkillContractIssue -Skill @($ok) -IndexName @('alpha', 'ghost')).Count -lt 1) {
    $findings += '[SKILL-CONTRACT-EXAMPLE] an index naming a skill with no directory went unreported.'
  }
  return $findings
}

# -- T150-RESIDENT-BUDGET: the always-on payload, measured and ratcheted --
# Measurement and judgement are separate on purpose: the measurement touches disk, the judgement does not,
# so the rule is testable from literals with no skills on disk at all.
function Get-ScaffoldSkillDescriptionSize {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$SkillRoot)
  $items = @()
  if (-not (Test-Path -LiteralPath $SkillRoot)) { return @{ Total = 0; PerSkill = @() } }
  foreach ($d in @(Get-ChildItem -LiteralPath $SkillRoot -Directory -ErrorAction SilentlyContinue)) {
    $f = Join-Path $d.FullName 'SKILL.md'
    $desc = ''
    if (Test-Path -LiteralPath $f) {
      $fm = [regex]::Match([System.IO.File]::ReadAllText($f), '(?s)^﻿?---\r?\n(.*?)\r?\n---').Groups[1].Value
      # The description runs to the next top-level key or the end of the block - it is routinely multi-line.
      $desc = [regex]::Match($fm, '(?ms)^description:\s*(.*?)(?=^\w+:|\z)').Groups[1].Value.Trim()
    }
    $items += [pscustomobject]@{ Name = $d.Name; Chars = $desc.Length }
  }
  return @{ Total = (@($items | Measure-Object Chars -Sum).Sum); PerSkill = @($items) }
}

# Pure judgement. Two ceilings are judged INDEPENDENTLY and are never summed or traded - buying core-doc
# headroom by shortening descriptions would silently degrade triggering, which is the failure mode that
# costs most for a skill, so the tension stays visible rather than being arbitrated by whoever edits last.
# An empty budget map means OFF, matching FrozenPaths / DocSyncMap / DocBudgets.
function Test-ScaffoldResidentBudget {
  [CmdletBinding()]
  param(
    [hashtable]$Budget = @{},
    [hashtable]$Measured = @{},
    [hashtable]$DocBudget = @{},
    [object[]]$PerItem = @()
  )
  $findings = [System.Collections.Generic.List[string]]::new()
  if (@($Budget.Keys).Count -eq 0) { return @($findings) }

  # Drift guard: the core-doc ceiling appears in BOTH tables because it answers two different questions
  # (standing-doc size, and resident payload). Two copies are tolerable only if a disagreement is reported.
  if ($Budget.ContainsKey('CLAUDE.md') -and $DocBudget.ContainsKey('CLAUDE.md') -and ([int]$Budget['CLAUDE.md'] -ne [int]$DocBudget['CLAUDE.md'])) {
    $findings.Add("[RESIDENT-BUDGET] ResidentBudgets['CLAUDE.md'] is $($Budget['CLAUDE.md']) but DocBudgets['CLAUDE.md'] is $($DocBudget['CLAUDE.md']). They describe the same file and must agree. [FIX] change both, or delete the resident copy and read the doc one.")
  }
  foreach ($k in @($Budget.Keys)) {
    if (-not $Measured.ContainsKey($k)) { continue }
    $max = [int]$Budget[$k]
    $now = [int]$Measured[$k]
    if ($now -le $max) { continue }
    $detail = ''
    if ($k -eq 'SkillDescriptions' -and @($PerItem).Count) {
      $top = @($PerItem | Sort-Object Chars -Descending | Select-Object -First 5 | ForEach-Object { "$($_.Name)=$($_.Chars)" })
      $detail = " Largest first: $($top -join ', ')."
    }
    $findings.Add("[RESIDENT-BUDGET] $k is $now characters, $($now - $max) over its $max ceiling. This payload rides in the system prompt on EVERY turn.$detail [FIX] cut the largest contributors, or raise the ceiling in scripts/_config.ps1 ResidentBudgets with a stated reason in the PR that raises it. Do NOT buy headroom here by squeezing the other ceiling - they are separate because they trade against each other.")
  }
  return @($findings)
}

function Test-ScaffoldResidentBudgetExamples {
  <#
  .SYNOPSIS  T150: declared examples for the resident-budget judgement. Returns findings; empty is green.
             Hermetic - literal budgets and measurements, no skills on disk.
  #>
  [CmdletBinding()]
  param()
  $findings = @()
  if (@(Test-ScaffoldResidentBudget -Budget @{} -Measured @{ 'SkillDescriptions' = 999999 }).Count -ne 0) {
    $findings += '[RESIDENT-BUDGET-EXAMPLE] an EMPTY budget map reported a finding - empty must mean OFF, which is how a freshly initialised downstream keeps this check quiet.'
  }
  if (@(Test-ScaffoldResidentBudget -Budget @{ 'SkillDescriptions' = 100 } -Measured @{ 'SkillDescriptions' = 100 }).Count -ne 0) {
    $findings += '[RESIDENT-BUDGET-EXAMPLE] a payload exactly AT its ceiling was reported - the ceiling is inclusive, same as DocBudgets.'
  }
  $over = @(Test-ScaffoldResidentBudget -Budget @{ 'SkillDescriptions' = 100 } -Measured @{ 'SkillDescriptions' = 101 } -PerItem @([pscustomobject]@{ Name = 'big'; Chars = 90 }, [pscustomobject]@{ Name = 'small'; Chars = 11 }))
  if ($over.Count -ne 1) { $findings += "[RESIDENT-BUDGET-EXAMPLE] one character over the ceiling produced $($over.Count) finding(s), expected 1." }
  elseif ($over[0] -notmatch 'big=90') { $findings += '[RESIDENT-BUDGET-EXAMPLE] the overrun finding carries no per-skill breakdown, so the reader needs a second command to act on it.' }
  $drift = @(Test-ScaffoldResidentBudget -Budget @{ 'CLAUDE.md' = 100 } -Measured @{ 'CLAUDE.md' = 1 } -DocBudget @{ 'CLAUDE.md' = 200 })
  if (@($drift | Where-Object { $_ -match 'must agree' }).Count -ne 1) {
    $findings += '[RESIDENT-BUDGET-EXAMPLE] the two CLAUDE.md ceilings disagreed and nothing reported it - two copies of one number are only tolerable while a disagreement is caught.'
  }
  return $findings
}

# -- T157-LICENSE-SCAN-SURFACE: what does the licence gate actually have to scan? --
# The gate reports zero interceptions all-time, and measuring WHY produced a worse answer than "no
# dependencies". Two separate facts: the manifest surface is genuinely empty (no pyproject.toml, no
# package.json anywhere), so the gate prints a green line for work it never did; and there IS third-party
# source here - three vendored skills each carrying a LICENSE and a NOTICE.md - which the gate could not
# see at all, because it references neither NOTICE nor vendor nor skills. Gate 9c checks those directories
# for PROVENANCE; nothing checked their licence COMPATIBILITY.
#
# Pure: no IO. The caller supplies what it found, which is what makes this testable with nothing on disk.
# THE SKIP IS DERIVED, NEVER REQUESTED. There is deliberately no -SkipLicenses switch anywhere: a
# caller-requestable skip is the silent-unlock class TD49 filed and gate 9e defends against. A skip here is
# taken only on POSITIVE EVIDENCE of emptiness, and it re-arms by itself the moment a manifest or a
# vendored directory appears - which is the whole point for a downstream that starts vendoring code.
# FAIL-CLOSED ON UNDECIDABLE: an unreadable manifest or an unrecognised vendored layout returns a surface,
# so the gate RUNS. Skipping on doubt would be the same false-green the empty state exists to abolish.
function Get-ScaffoldLicenseScanSurface {
  [CmdletBinding()]
  param(
    [string[]]$ManifestPath = @(),
    [string[]]$VendoredDir = @(),
    [switch]$Undecidable
  )
  $surfaces = [System.Collections.Generic.List[string]]::new()
  if ($Undecidable) { $surfaces.Add('undecidable') }
  if (@($ManifestPath | Where-Object { $_ }).Count -gt 0) { $surfaces.Add('manifest') }
  if (@($VendoredDir | Where-Object { $_ }).Count -gt 0) { $surfaces.Add('vendored') }
  return @($surfaces)
}

function Test-ScaffoldLicenseSurfaceExamples {
  <#
  .SYNOPSIS  T157 arm 6: declared examples for the scan-surface predicate. Returns findings; empty is green.
             Hermetic - literal lists only, nothing on disk.
  .DESCRIPTION
    All three directions plus the fail-closed one. The EMPTY case is the one that changes behaviour, so the
    other three exist to stop it widening: a skip taken on anything but positive emptiness would reintroduce
    exactly the green-line-for-no-work state this card exists to abolish.
  #>
  [CmdletBinding()]
  param()
  $findings = @()
  if (@(Get-ScaffoldLicenseScanSurface).Count -ne 0) {
    $findings += '[LICENSE-SURFACE-EXAMPLE] no manifest and no vendored directory still reported a surface - the empty state can then never be reached and the gate keeps printing a compliance line for work it never did.'
  }
  if (@(Get-ScaffoldLicenseScanSurface -ManifestPath @('pyproject.toml')) -notcontains 'manifest') {
    $findings += '[LICENSE-SURFACE-EXAMPLE] a present manifest was not reported as a surface - the gate would skip a project that has dependencies to scan.'
  }
  if (@(Get-ScaffoldLicenseScanSurface -VendoredDir @('.claude/skills/ponytail')) -notcontains 'vendored') {
    $findings += '[LICENSE-SURFACE-EXAMPLE] a vendored third-party directory was not reported as a surface - that is the only third-party code this repo actually has, and it was invisible to the gate before this card.'
  }
  if (@(Get-ScaffoldLicenseScanSurface -Undecidable).Count -eq 0) {
    $findings += '[LICENSE-SURFACE-EXAMPLE] an UNDECIDABLE surface reported nothing, so the gate would SKIP on doubt. Fail-closed is the contract: skip only on positive evidence of emptiness.'
  }
  return $findings
}

# -- T158-SELFTEST-SCAN-SKIP: which selftest SMOKE scans may a project declare redundant? --
# Gate 13 runs a full check-secrets scan over the meta repo as a SMOKE test - its own step text says so.
# What it does NOT do is prove the scanner works: that is gate 17a, which plants a hardcoded secret and
# requires check-secrets to catch it, and which this mechanism never touches. Meanwhile the real scan runs
# in four places that have nothing to do with selftest, and those are the whole safety argument.
#
# THE ENUM IS CLOSED IN CODE, NOT OPEN IN CONFIG. Config SELECTS from this set; it cannot extend it. An
# open skip list would let a project disarm an arbitrary gate from a data file, which is the silent-unlock
# class gate 9e exists to prevent. An id absent from this table is a HARD ERROR naming the id, never a
# silent no-op - a typo that quietly skips nothing is indistinguishable from one that quietly skips
# everything.
# EVERY MEMBER NAMES THE ARMS THAT STILL COVER IT. That naming IS the justification a reviewer checks; it
# is not the author's assurance. A scan with no covering arm does not belong here at any price.
function Get-ScaffoldSkippableSelftestScan {
  [CmdletBinding()]
  param()
  return @(
    [pscustomobject]@{
      Id  = 'secrets-smoke'
      What = "gate 13's full-repo check-secrets scan (the smoke assertion that this repo currently has no fatal hits)"
      CoveredBy = @(
        'task.ps1 ship: runs the worktree copy of check-secrets and throws on non-zero',
        'gh-bootstrap.ps1 pre-push hook: exits 1, bypass needs an explicit --no-verify',
        'ci.yml secret-leak step: fail-closed if the script is missing (selftest 8.2b)',
        'gh-bootstrap.ps1 repo creation: auto-upgrades to -Strict full history for a public repo',
        'selftest 17a: plants a secret and requires the scanner to catch it - NOT skippable, and it is what proves the scanner still works'
      )
    }
  )
}

function Get-ScaffoldSelftestSkipIssue {
  <#
  .SYNOPSIS  T158: judge a configured skip list against the closed enum. Returns findings; empty is green.
             Pure - the caller supplies the list, so an unknown id is caught with nothing on disk.
  #>
  [CmdletBinding()]
  param([string[]]$Requested = @())
  $known = @(Get-ScaffoldSkippableSelftestScan | ForEach-Object { $_.Id })
  $findings = @()
  foreach ($r in @($Requested | Where-Object { $_ })) {
    if ($r -cnotin $known) {
      $findings += "[SELFTEST-SCAN-SKIP] SelftestSkipScans names '$r', which is not a skippable scan. Legal ids right now, read live from the closed enum: $($known -join ', '). An unknown id is a HARD ERROR, never a silent no-op: a typo that quietly skips nothing reads exactly like one that quietly skips everything. [FIX] correct the id, or delete it - the set of what MAY be skipped is decided in scripts/_guard.ps1, deliberately not in config."
    }
  }
  return @($findings)
}

function Test-ScaffoldSelftestSkipExamples {
  <#
  .SYNOPSIS  T158 arm 8: declared examples for the skip judgement. Returns findings; empty is green.
             Hermetic - literal lists only, nothing read from disk or config.
  #>
  [CmdletBinding()]
  param()
  $findings = @()
  if (@(Get-ScaffoldSelftestSkipIssue -Requested @()).Count -ne 0) {
    $findings += '[SELFTEST-SKIP-EXAMPLE] an EMPTY skip list produced a finding - empty must mean skip nothing, which is the state every downstream arrives in.'
  }
  if (@(Get-ScaffoldSelftestSkipIssue -Requested @('secrets-smoke')).Count -ne 0) {
    $findings += '[SELFTEST-SKIP-EXAMPLE] a VALID enum id was rejected - the mechanism would be unusable and the only remaining way to skip would be editing selftest, which is the silent unlock this card exists to avoid.'
  }
  if (@(Get-ScaffoldSelftestSkipIssue -Requested @('no-such-scan')).Count -lt 1) {
    $findings += '[SELFTEST-SKIP-EXAMPLE] an UNKNOWN id was accepted silently. Config could then name anything and get no feedback, and a typo skipping nothing would be indistinguishable from one skipping everything.'
  }
  $enum = @(Get-ScaffoldSkippableSelftestScan)
  if ($enum.Count -lt 1) { $findings += '[SELFTEST-SKIP-EXAMPLE] the skippable enum is empty, so nothing can ever be skipped and the config field is dead.' }
  elseif (@($enum | Where-Object { @($_.CoveredBy).Count -lt 1 }).Count -ne 0) {
    $findings += '[SELFTEST-SKIP-EXAMPLE] an enum member names NO covering enforcement arms. That naming is the entire justification for allowing the skip - a scan with no covering arm must not be skippable at any price.'
  }
  return $findings
}

function Test-PushTargetOwner {
  <#
  .SYNOPSIS  校验 push/远端 URL 的 host 精确为 github.com 且首路径段(owner)==$Expected。
  .DESCRIPTION
    解析 URL 的 authority（而非子串匹配），杜绝把 github.com/<owner>/ 塞进攻击者 host 路径的伪装
    绕过账号守卫（TD38 / 评审 TD-101：evil.example/github.com/<Expected>/… 及 scp/ssh 内嵌形式）。
    两种合法形态：
      1. scheme://[user@]github.com[:port]/<owner>/…   （https / ssh / git；容许显式端口 :443/:22，C17）
      2. scp-like  [user@]github.com:<owner>/repo.git   （无 scheme）
    host 大小写不敏感（GitHub 恒小写）；两形态皆不匹配 => 拒（fail-closed）。
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Url,
    [Parameter(Mandatory)][string]$Expected
  )
  # 形态一：绝对 URI（有 scheme 且带 //authority → Host 非空）。
  #   opaque URI（如 scp 被 .NET 误当 scheme:path，Host 为空）不在此命中，落形态二。
  $u = $null
  if ([uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$u) -and $u.Host) {
    if ($u.Host -ine 'github.com') { return $false }
    $owner = ($u.AbsolutePath.Trim('/') -split '/', 2)[0]
    return [bool]($owner -and ($owner -ieq $Expected))
  }
  # 形态二：scp-like [user@]host:owner/repo.git
  if ($Url -match '^(?:[^@/]+@)?(?<host>[^:/]+):(?<owner>[^/]+)/') {
    return [bool](($Matches['host'] -ieq 'github.com') -and ($Matches['owner'] -ieq $Expected))
  }
  return $false
}

function Get-ScaffoldRemoteIdentity {
  <#
  .SYNOPSIS
    [REMOTE-IDENTITY] Reduce a GitHub remote URL - or a bare `owner/repo` - to one canonical identity.
  .DESCRIPTION
    Returns `owner/repo`, lowercased, or $null when the input is not a GitHub remote this repo can judge.
    $null is a REFUSAL, never a wildcard: every caller must treat it as "cannot confirm", because the two
    consumers use the answer to decide whether to fetch into a trusted namespace and whether to silence a
    staleness probe (upstream issue #260).

    Why a normalizer instead of a comparison: both call sites used to compare the wrong things. One checked
    that a remote NAME resolves, which says nothing about where it points. The other did
    `$originUrl -match [regex]::Escape($upstream)`, a SUBSTRING test, so
    `https://github.com/Asun28/claude-devops-scaffold-fork.git` contains `Asun28/claude-devops-scaffold`
    and a fork silenced its own staleness probe. A prefix spoof passes the same way. Substring is the wrong
    relation for identity; equality on a normalized form is the right one.

    Pure string work - no network, no git, no filesystem (the issue rules out an existence check).
  .EXAMPLE
    Get-ScaffoldRemoteIdentity 'git@github.com:Asun28/Claude-DevOps-Scaffold.git'   # asun28/claude-devops-scaffold
  #>
  [CmdletBinding()]
  param([Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Url)
  $u = ([string]$Url).Trim()
  if (-not $u) { return $null }
  # A bare owner/repo (how _config states UpstreamRepo). Anchored, so `a/b/c` is refused rather than trimmed.
  if ($u -match '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$') { return $u.ToLowerInvariant() }
  # HTTPS/HTTP (optionally with userinfo) and SSH, with or without a .git suffix or trailing slash.
  $m = [regex]::Match($u, '(?i)^(?:https?://(?:[^@/]+@)?github\.com/|(?:ssh://)?git@github\.com[:/])([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\.git)?/?$')
  if (-not $m.Success) { return $null }
  return ($m.Groups[1].Value + '/' + $m.Groups[2].Value).ToLowerInvariant()
}

function Test-ScaffoldRemoteIdentityMatch {
  <#
  .SYNOPSIS
    [REMOTE-IDENTITY] Do two remote specifications name the SAME repository? Fail closed on either side.
  .DESCRIPTION
    Both sides are normalized first, and an unnormalizable side makes the answer $false - never $true and
    never "probably". That is the whole point: the callers act on a true (fetch into the trusted namespace,
    suppress the stale probe), so an unknown must behave exactly like a mismatch.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Position = 0)][AllowNull()][AllowEmptyString()][string]$Left,
    [Parameter(Position = 1)][AllowNull()][AllowEmptyString()][string]$Right
  )
  $l = Get-ScaffoldRemoteIdentity $Left
  $r = Get-ScaffoldRemoteIdentity $Right
  if (-not $l -or -not $r) { return $false }
  return [string]::Equals($l, $r, [System.StringComparison]::Ordinal)
}

function Assert-PersonalAccount {
  [CmdletBinding()]
  param(
    [string]$Expected,                      # 不传则取 _config.ps1 的 GhAccount（fail-closed if 未配置）
    [string]$RepoRoot,                      # 给定则一并校验 origin 远端 owner
    [switch]$CheckRemote,
    [string]$RemoteUrl                      # 显式 push 目标 URL（pre-push 钩子传 git 的 $2）；给定则校验它而非仅 origin（C25）
  )
  if (-not $Expected) { $Expected = Get-ScaffoldGhAccount }
  # 函数域原生错误 pin（TD54/TD-117）：本函数按 $LASTEXITCODE 判 gh/git 非零（登录名 / 远端 owner），
  # 对「调用方 / 环境把 $PSNativeCommandUseErrorActionPreference 设 $true」健壮——否则 Stop 下首个预期非零调用抛、崩账号守卫。
  # 函数域（非顶层 dot-source）：_guard 被各脚本 dot-source、也被 selftest 直接取用，pin 就近本函数最稳（镜像 handoff.ps1）。
  $PSNativeCommandUseErrorActionPreference = $false
  Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

  $actual = (& gh api user -q .login 2>$null)
  if ($LASTEXITCODE -ne 0 -or -not $actual) {
    throw "无法确认 GitHub 账号（gh 未登录或凭据无效）。本项目仅限个人账号 '$Expected'，已中止。先跑：gh auth login"
  }
  $actual = "$actual".Trim()
  if ($actual -ne $Expected) {
    throw "GitHub 当前账号为 '$actual'，非个人账号 '$Expected'。本项目严禁用组织/其它账号操作，已中止。"
  }

  if ($CheckRemote -or $RemoteUrl) {
    # 优先校验**实际 push 目标**（钩子传入的 $2），无则回退 origin（C25：防推到非 origin 的组织远端绕过）。
    $url = if ($RemoteUrl) { $RemoteUrl } elseif ($RepoRoot) { (& git -C $RepoRoot remote get-url origin 2>$null) } else { $null }
    if ($url) {
      # 精确解析 authority（host + 首路径段 owner），非子串匹配——防 evil.example/github.com/<Expected>/
      # 这类路径内嵌 host 伪装绕过（TD38/评审 TD-101）；容许显式端口 :443/:22（C17）。见 Test-PushTargetOwner。
      if (-not (Test-PushTargetOwner -Url $url -Expected $Expected)) {
        throw "推送/远端目标 '$url' 不属于个人账号 '$Expected'（或 host 非 github.com）。本项目禁止组织/其它账号/伪装仓库，已中止。"
      }
    }
  }
  Write-Host "账号校验通过：个人账号 $actual ✓" -ForegroundColor DarkGreen
}

# ─────────────────────────────────────────────────────────────────────────────────────────────────
# T104-HARNESS-COST-VISIBLE: three places the harness spent without reporting what it spent.
# Each is a PURE decision plus a declared example set. The decisions take explicit arguments and read
# neither git nor config, so the entry scripts stay thin and the judgement stays testable off-disk -
# the same split check-scope.ps1 uses over _scope.ps1 and check-adr.ps1 uses over Test-ScaffoldAdrFormat.
# ─────────────────────────────────────────────────────────────────────────────────────────────────

function Get-ScaffoldReviewRouteDecision {
  <#
  .SYNOPSIS  T104: decide whether a diff has EARNED a review skip. Returns @{ Skip; Reason }.
  .DESCRIPTION
    CONTENT-DERIVED and deliberately unreachable from a caller flag. review.ps1 records why -SkipReview
    was moved from exit 0 to exit 1: "skip read as pass" bypassed the only review gate. That hole is
    caller-REQUESTED. This one cannot be requested - a reviewee only earns it by the diff itself being
    markdown-only and gate-free - which is why the exclusions below ARE the safety argument, and why each
    carries its own negative-control arm in Test-ScaffoldReviewRouteExamples.
    Scope of a skip: it skips the advisory second-model review ONLY. check-secrets, check-scope and every
    deterministic gate run regardless, so a gate-read markdown file is still gated by the gate that reads it.
    An empty $SkipWhen => never skip: the same graceful degradation FrozenPaths / DocSyncMap / DocBudgets
    give a freshly initialized downstream.
    An empty changed-path list => never skip: "nothing resolved" must never read as "reviewed".
  #>
  [CmdletBinding()]
  param(
    [string[]]$ChangedPaths = @(),
    [hashtable]$SkipWhen = @{}
  )
  $paths = @($ChangedPaths | Where-Object { $_ })
  if ($SkipWhen.Count -eq 0) {
    return [pscustomobject]@{ Skip = $false; Reason = 'ReviewSkipWhen is empty, so content routing is off and every diff draws a review.' }
  }
  if ($paths.Count -eq 0) {
    return [pscustomobject]@{ Skip = $false; Reason = 'no changed path was resolved, and an empty diff must never read as reviewed.' }
  }
  $allMatch = if ($SkipWhen.ContainsKey('AllPathsMatch')) { [string]$SkipWhen['AllPathsMatch'] } else { '' }
  if (-not $allMatch) {
    return [pscustomobject]@{ Skip = $false; Reason = 'ReviewSkipWhen declares no AllPathsMatch, so no diff is eligible.' }
  }
  $off = @($paths | Where-Object { $_ -notmatch $allMatch })
  if ($off.Count -gt 0) {
    return [pscustomobject]@{ Skip = $false; Reason = "$($off.Count) of $($paths.Count) changed paths do not match $allMatch (first: $($off[0]))." }
  }
  # Gate-bearing exclusion (arm-2 control). Note WHY this is not redundant with AllPathsMatch: a
  # scripts/*.ps1 path is already rejected above for not being markdown, so the case this exclusion
  # actually decides is MARKDOWN LIVING UNDER A GATE-BEARING DIRECTORY - a hook's or workflow's own
  # .md, where prose is part of how the gate behaves. Delete this block and that case flips to eligible.
  if ($SkipWhen.ContainsKey('NeverPrefixes')) {
    foreach ($prefix in @($SkipWhen['NeverPrefixes'])) {
      if (-not $prefix) { continue }
      $hit = @($paths | Where-Object { $_.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase) })
      if ($hit.Count -gt 0) {
        return [pscustomobject]@{ Skip = $false; Reason = "changed path '$($hit[0])' sits under the gate-bearing prefix '$prefix', where even prose can be part of how a gate behaves." }
      }
    }
  }
  # Rubric self-protection (arm-3 control). Must hold INDEPENDENTLY of the prefix list: review.ps1 already
  # reads the rubric from the BASELINE so a reviewee cannot weaken the standard it is judged by. Letting a
  # rubric edit route around the review entirely would reach that same end by a different road.
  if ($SkipWhen.ContainsKey('NeverPaths')) {
    foreach ($never in @($SkipWhen['NeverPaths'])) {
      if (-not $never) { continue }
      $hit = @($paths | Where-Object { $_.Equals($never, [System.StringComparison]::OrdinalIgnoreCase) })
      if ($hit.Count -gt 0) {
        return [pscustomobject]@{ Skip = $false; Reason = "changed path '$($hit[0])' is the review standard itself, which is never skippable at any composition." }
      }
    }
  }
  return [pscustomobject]@{ Skip = $true; Reason = "all $($paths.Count) changed paths match $allMatch and none is gate-bearing or the review standard itself." }
}

function Get-ScaffoldReviewRouteFixture {
  # The declared predicate these examples are judged against - the same shape _config.ps1 ships.
  $rule = @{
    AllPathsMatch = '\.md$'
    NeverPrefixes = @('scripts/', '.github/', '.claude/hooks/')
    NeverPaths    = @('docs/QUALITY-RUBRIC.md')
  }
  $cases = [ordered]@{
    'md-only'          = @('docs/HANDOFF.md', 'README.md')            # MAY skip - the only eligible shape
    'md-under-gate'    = @('docs/HANDOFF.md', '.github/RELEASING.md') # must NOT - decided by NeverPrefixes
    'md-plus-script'   = @('docs/HANDOFF.md', 'scripts/task.ps1')     # must NOT - decided by AllPathsMatch
    'rubric-only'      = @('docs/QUALITY-RUBRIC.md')                  # must NOT - the standard itself
    'rubric-mixed'     = @('README.md', 'docs/QUALITY-RUBRIC.md')     # must NOT - at any composition
    'code'             = @('scripts/review.ps1')                      # must NOT - one code file is enough
    'empty'            = @()                                          # must NOT - nothing resolved
  }
  return @{ Rule = $rule; Cases = $cases }
}

function Test-ScaffoldReviewRouteExamples {
  <#
  .SYNOPSIS  T104 arms 1-3. Default = the live decision on the declared examples, and must return nothing.
             Each -Variant DELETES one exclusion from the declared rule - the single-line deletion the
             card's hygiene field traces - and must then produce at least one finding. Those two variants
             are the only arms that can fail before this function exists, which is the property L239
             requires of a working RED: an upper-bound arm reads identically for "healthy" and "absent".
  #>
  [CmdletBinding()]
  param([ValidateSet('skip-gate-bearing', 'skip-rubric-itself')][string]$Variant)
  $fx = Get-ScaffoldReviewRouteFixture
  $rule = @{}
  foreach ($k in @($fx.Rule.Keys)) { $rule[$k] = $fx.Rule[$k] }
  if ($Variant -eq 'skip-gate-bearing') { $rule.Remove('NeverPrefixes') }
  if ($Variant -eq 'skip-rubric-itself') { $rule.Remove('NeverPaths') }

  $got = [ordered]@{}
  foreach ($name in @($fx.Cases.Keys)) {
    $got[$name] = Get-ScaffoldReviewRouteDecision -ChangedPaths @($fx.Cases[$name]) -SkipWhen $rule
  }

  if ($Variant) {
    $f = @()
    # The finding IS the evidence: deleting the exclusion flipped a case that must never flip, which is
    # what proves the exclusion - and not some other clause - is what decides it. Silence here would mean
    # the line is dead weight and arm 1 was passing for an unrelated reason.
    if ($Variant -eq 'skip-gate-bearing' -and $got['md-under-gate'].Skip) {
      $f += "[REVIEW-ROUTE-EXAMPLE] deleting NeverPrefixes made markdown under a gate-bearing directory eligible to skip: $($got['md-under-gate'].Reason)"
    }
    if ($Variant -eq 'skip-rubric-itself' -and $got['rubric-mixed'].Skip) {
      $f += "[REVIEW-ROUTE-EXAMPLE] deleting NeverPaths made a diff editing docs/QUALITY-RUBRIC.md eligible to skip: $($got['rubric-mixed'].Reason)"
    }
    return $f
  }

  $findings = @()
  if (-not $got['md-only'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] a 100% markdown, gate-free diff was NOT eligible to skip: $($got['md-only'].Reason) [FIX] fix the rule, never the example." }
  if ($got['md-under-gate'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] markdown under a gate-bearing directory was eligible to skip - prose there can be part of how a gate behaves. [FIX] fix the rule, never the example." }
  if ($got['md-plus-script'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] a diff carrying scripts/task.ps1 was eligible to skip - executable behaviour is exactly what a second reader is for." }
  if ($got['rubric-only'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] a diff touching docs/QUALITY-RUBRIC.md alone was eligible to skip - a reviewee must never route around the standard it is judged by." }
  if ($got['rubric-mixed'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] a markdown diff that also edits docs/QUALITY-RUBRIC.md was eligible to skip - the exclusion must hold at ANY composition, not just when the rubric is alone." }
  if ($got['code'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] a diff containing a code file was eligible to skip." }
  if ($got['empty'].Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] an empty changed-path list was eligible to skip - 'nothing resolved' must never read as 'reviewed'." }
  $offCfg = Get-ScaffoldReviewRouteDecision -ChangedPaths @('README.md') -SkipWhen @{}
  if ($offCfg.Skip) { $findings += "[REVIEW-ROUTE-EXAMPLE] an empty ReviewSkipWhen still skipped, breaking the empty-config-still-runs rule every other manifest in _config.ps1 obeys." }
  return $findings
}

function Resolve-ScaffoldReviewEffort {
  <#
  .SYNOPSIS  T104: resolve the R3 reasoning effort for a diff of this size. Returns '' for "backend default".
  .DESCRIPTION
    Deliberately NOT named Get-ScaffoldReviewEffort: that name is already the raw accessor in _config.ps1,
    which _guard.ps1 dot-sources, so reusing it would silently shadow the accessor for every consumer down
    the chain. This one RESOLVES, the accessor READS - the same split the rest of this file keeps.
    Accepts BOTH shapes, so an existing downstream keeps working untouched:
      a plain string  => that effort for every diff (the pre-T104 behaviour),
      a size-keyed map @{ small=…; medium=…; large=… } => a small diff draws a cheaper pass.
    An undeclared bucket falls back to the LARGEST declared effort, never to the backend default: a gap in
    the map must not quietly buy a cheaper review than the author declared.
  #>
  [CmdletBinding()]
  param(
    [int]$ChangedLines = 0,
    $Config,
    [int]$SmallMax = 100,
    [int]$MediumMax = 800
  )
  if ($null -eq $Config) { return '' }
  if ($Config -is [string]) { return [string]$Config }
  if ($Config -is [System.Collections.IDictionary]) {
    if ($Config.Count -eq 0) { return '' }
    $bucket = if ($ChangedLines -le $SmallMax) { 'small' } elseif ($ChangedLines -le $MediumMax) { 'medium' } else { 'large' }
    if ($Config.Contains($bucket)) { return [string]$Config[$bucket] }
    foreach ($b in @('large', 'medium', 'small')) { if ($Config.Contains($b)) { return [string]$Config[$b] } }
  }
  return ''
}

function Get-ScaffoldLessonTierGuardError {
  <#
  .SYNOPSIS  T104 (upstream issue #206): a lesson that costs context on every turn must name its guard.
  .DESCRIPTION
    Pre-T104 the predicate consulted severity ALONE, so a tier:must lesson could sit resident in every
    context forever with no mechanical guard while `lessons check` printed its green line over it. L97 was
    at recurrence 12 in exactly that state. Residency is the cost, so residency - not just severity - is
    what has to be paid for. 'none (reason)' stays a valid answer: an honest "no guard, here is why" is a
    declaration, and some rules genuinely cannot be machine-checked.
  #>
  [CmdletBinding()]
  param([string]$Id = 'L?', [string]$Tier, [string]$Severity, [string]$EnforcedBy)
  # [ENFORCED-BY-GRAMMAR] (upstream issue #244): the test was `if ($EnforcedBy)` - ANY nonempty text
  # satisfied it, so `TODO` bought a permanent seat in every context. A malformed declaration is treated
  # exactly like a missing one, because that is what it is.
  $shape = Get-ScaffoldEnforcedByShape $EnforcedBy
  if ($shape.WellFormed -and $shape.Kind -ne 'empty') { return '' }
  $why = @()
  if ($Severity -eq 'blocking') { $why += 'severity=blocking' }
  if ($Tier -eq 'must') { $why += 'tier=must (resident in every context)' }
  if ($why.Count -eq 0) { return '' }
  $malformed = if ($shape.Kind -eq 'malformed') { " The value it does carry is not a declaration: $($shape.Reason)." } else { '' }
  return "[LESSON-TIER-GUARD] $Id is $($why -join ' and ') but declares no enforced_by.$malformed Name the script or gate that makes the mistake unrepeatable, or record 'none (reason)' to state on the record that no machine check covers it."
}

function Get-ScaffoldLessonTierGuardFixture {
  return [ordered]@{
    # tier:must + major + no guard: the L97 shape. MUST be reported - this is the whole point of T104.
    'must-major-bare'  = @{ Id = 'L97';  Tier = 'must';   Severity = 'major';    EnforcedBy = '' }
    # tier:must + minor + no guard: the L17 shape. Severity is irrelevant once it is resident.
    'must-minor-bare'  = @{ Id = 'L17';  Tier = 'must';   Severity = 'minor';    EnforcedBy = '' }
    # tier:must with an explicit honest 'none (reason)': MUST pass.
    'must-none-reason' = @{ Id = 'L97';  Tier = 'must';   Severity = 'major';    EnforcedBy = 'none (an exhaustive teaching-face sweep is not machine-checkable)' }
    # blocking + no guard: the pre-T104 rule, which must keep firing unchanged.
    'blocking-bare'    = @{ Id = 'L2';   Tier = 'ledger'; Severity = 'blocking'; EnforcedBy = '' }
    # ledger tier + major + no guard: costs nothing per turn, so it MUST NOT be reported.
    'ledger-major'     = @{ Id = 'L50';  Tier = 'ledger'; Severity = 'major';    EnforcedBy = '' }
  }
}

function Test-ScaffoldLessonTierGuardExamples {
  <#
  .SYNOPSIS  T104 arms 4-5. Default must return nothing. -Variant 'severity-only' restores the pre-T104
             predicate (severity alone) and must then produce a finding - without that arm the change is
             indistinguishable from no change at all.
  #>
  [CmdletBinding()]
  param([ValidateSet('severity-only')][string]$Variant)
  $fx = Get-ScaffoldLessonTierGuardFixture
  $got = [ordered]@{}
  foreach ($name in @($fx.Keys)) {
    $c = $fx[$name]
    $tier = if ($Variant -eq 'severity-only') { 'ledger' } else { $c.Tier }   # the rejected design: tier never consulted
    $got[$name] = Get-ScaffoldLessonTierGuardError -Id $c.Id -Tier $tier -Severity $c.Severity -EnforcedBy $c.EnforcedBy
  }
  if ($Variant) {
    $f = @()
    if ($Variant -eq 'severity-only' -and -not $got['must-major-bare']) {
      $f += "[LESSON-TIER-EXAMPLE] with tier ignored, the L97 shape (tier:must, severity major, no enforced_by) went unreported - which is the upstream bug this card exists to close, and proves the tier clause is what decides it."
    }
    return $f
  }
  $findings = @()
  if (-not $got['must-major-bare']) { $findings += "[LESSON-TIER-EXAMPLE] a tier:must lesson with no enforced_by was not reported - residency is the cost, so residency must be paid for. [FIX] fix the rule, never the example." }
  if ($got['must-major-bare'] -and $got['must-major-bare'] -notmatch 'LESSON-TIER-GUARD') { $findings += "[LESSON-TIER-EXAMPLE] the finding carries no [LESSON-TIER-GUARD] sentinel, so nothing downstream can grep for it. Got: $($got['must-major-bare'])" }
  if (-not $got['must-minor-bare']) { $findings += "[LESSON-TIER-EXAMPLE] a tier:must lesson with severity=minor and no enforced_by was not reported - once a lesson is resident its severity no longer decides whether it needs a guard." }
  if ($got['must-none-reason']) { $findings += "[LESSON-TIER-EXAMPLE] an explicit 'none (reason)' was reported: $($got['must-none-reason']) - an honest declaration that nothing machine-checks the rule is a valid answer." }
  if (-not $got['blocking-bare']) { $findings += "[LESSON-TIER-EXAMPLE] a severity=blocking lesson with no enforced_by stopped being reported - the pre-T104 rule must survive this change unchanged." }
  if ($got['ledger-major']) { $findings += "[LESSON-TIER-EXAMPLE] a ledger-tier lesson was reported: $($got['ledger-major']) - a lesson that costs nothing per turn owes nothing." }
  return $findings
}

function Get-ScaffoldHarnessRatioFinding {
  <#
  .SYNOPSIS  T104 (upstream issue #204's unaddressed half): report harness volume against product volume.
  .DESCRIPTION
    Rubric #8 cannot catch harness accumulation - it scored 0 hits across 39 downstream verdicts because a
    reviewer sees ONE diff atomically and never the running total. This reports the aggregate instead.
    The zero-denominator branch is load-bearing, not defensive tidying: this template repo ships a skeleton
    with no product source files at all, so the honest output there is SILENCE. Without the branch the
    probe either divides by zero or reports an infinite ratio as a finding on every single heartbeat.
  #>
  [CmdletBinding()]
  param([int]$HarnessLines = 0, [int]$ProductLines = 0, [double]$WarnAtRatio = 1.0)
  if ($ProductLines -le 0) { return '' }
  $ratio = [math]::Round($HarnessLines / [double]$ProductLines, 2)
  if ($ratio -lt $WarnAtRatio) { return '' }
  return "[HARNESS-RATIO] harness is $HarnessLines lines against $ProductLines lines of product (${ratio}:1). Rubric #8 cannot see this: a reviewer judges one diff and never the running total. Cut a gate that has never fired before adding one - the effectiveness ledger (triage probe 8) shows which."
}

function Get-ScaffoldHarnessRatioFixture {
  return [ordered]@{
    'no-product'   = @{ Harness = 16169; Product = 0 }      # this template: MUST stay silent
    'heavy'        = @{ Harness = 17460; Product = 4992 }   # the measured downstream: MUST report
    'healthy'      = @{ Harness = 900;   Product = 12000 }  # product dominates: MUST stay silent
  }
}

function Test-ScaffoldHarnessRatioExamples {
  <#
  .SYNOPSIS  T104 arm 6. -Variant 'no-product-tree' feeds the decision the input a version WITHOUT the
             zero-denominator branch would produce (an absent product tree counted as one line) and must
             produce a finding - proving the template's silence comes from that branch and not merely
             from the ratio threshold.
  #>
  [CmdletBinding()]
  param([ValidateSet('no-product-tree')][string]$Variant)
  $fx = Get-ScaffoldHarnessRatioFixture
  if ($Variant -eq 'no-product-tree') {
    $got = Get-ScaffoldHarnessRatioFinding -HarnessLines $fx['no-product'].Harness -ProductLines 1
    $f = @()
    # As above, the finding IS the evidence: without the zero-denominator branch an absent product tree
    # alarms on every heartbeat. Silence here would mean the ratio threshold alone was keeping the
    # template quiet, and the branch arm 6 claims to protect would be dead weight.
    if ($got) { $f += "[HARNESS-RATIO-EXAMPLE] without the zero-denominator branch, an absent product tree reports on every heartbeat: $got" }
    return $f
  }
  $got = [ordered]@{}
  foreach ($name in @($fx.Keys)) {
    $got[$name] = Get-ScaffoldHarnessRatioFinding -HarnessLines $fx[$name].Harness -ProductLines $fx[$name].Product
  }
  $findings = @()
  if ($got['no-product']) { $findings += "[HARNESS-RATIO-EXAMPLE] a tree with no product denominator produced a finding: $($got['no-product']) - the template ships with zero product source files, so silence is the correct output there." }
  if (-not $got['heavy']) { $findings += "[HARNESS-RATIO-EXAMPLE] 17460 lines of harness against 4992 lines of product went unreported - that measured downstream ratio is the condition this probe exists for. [FIX] fix the rule, never the example." }
  if ($got['heavy'] -and $got['heavy'] -notmatch 'HARNESS-RATIO') { $findings += "[HARNESS-RATIO-EXAMPLE] the finding carries no [HARNESS-RATIO] sentinel, so nothing downstream can grep for it. Got: $($got['heavy'])" }
  if ($got['healthy']) { $findings += "[HARNESS-RATIO-EXAMPLE] a product-dominant tree was reported: $($got['healthy']) - the probe must speak only where the harness actually outweighs the product." }
  return $findings
}

# -- T178-WIRING-SELF-SAT (TD171): is a wiring row self-satisfying? --
# A wiring row says "file F must contain sentinel S", and the loop that judges it greps F for S. When F is
# the file the row itself is DECLARED in, the row's own declaration is what puts S into F: the answer is
# fixed before the grep runs, the row is true by construction, and no edit anywhere can turn it red. The
# loop cannot notice - it sees a hit and passes. That is the shape L265 names, and a single-line-deletion
# mutation batch is the only thing that detects it after the fact.
# Not hypothetical: T176 added three rows naming scripts/selftest.ps1 to selftest's own 17z table and the
# batch came back ok=0 survived=3, every probe at exit 0 (2026-08-26). The same literal measured both ways:
# grep hits 2 (the declaration plus the call site), AST hits 1 (the call).
# Paths are compared NORMALISED rather than exactly: a row is hand-written prose, so 'scripts/selftest.ps1',
# '.\scripts\selftest.ps1' and 'Scripts/SelfTest.ps1' all name the same file on the Windows checkout this
# repo is developed on, and an exact comparison would close the trap for the first spelling only.
# Takes the rows and the declaring path as DATA, so the rule is testable without reading scripts/ at all.
function ConvertTo-ScaffoldComparablePath {
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)
  $p = "$Path".Trim()
  $p = $p -replace '\\', '/'
  $p = $p -replace '^(?:\./)+', ''
  return $p.ToLowerInvariant()
}

function Get-ScaffoldSelfSatisfyingWiringRows {
  [CmdletBinding()]
  param(
    [AllowEmptyCollection()][object[]]$Rows = @(),
    [Parameter(Mandatory)][string]$DeclaringPath
  )
  $self = ConvertTo-ScaffoldComparablePath $DeclaringPath
  $findings = @()
  # T180/TD175: the offending row is named by its INDEX, not by echoing a second key. The second call site
  # (17n's $hookChecks) discriminates on 're' where $zWirings uses 's', and under Set-StrictMode -Version
  # Latest reading a key a row does not carry THROWS rather than yielding $null - so a hardcoded $row.s
  # would crash the gate at the exact moment it finally had a finding to report, arriving as an exception
  # attributed to no arm instead of as a finding naming the row. The index also does the echo's real job
  # better: three of 17n's five rows name the SAME file, so the file alone never said which row to remove.
  # The initialiser below is deliberately NOT a registered mutation target - deleting it leaves $index
  # undefined, and StrictMode then reds through an exception rather than through this rule's own arm, which
  # is wrong-arm evidence (L188). The increment IS registered: deleting it reports every row as #-1.
  $index = -1
  foreach ($row in $Rows) {
    $index++
    if (-not $row -or [string]::IsNullOrWhiteSpace([string]$row.f)) { continue }
    if ((ConvertTo-ScaffoldComparablePath ([string]$row.f)) -ne $self) { continue }
    $findings += "[WIRING-SELF-SAT] wiring row #$index { f = '$($row.f)' } names the file it is DECLARED in, so the grep reads back the row's own text and the assertion can never fail (L265/TD171/TD175). [FIX] prove this one through a channel that cannot read the row's own text - the AST call-count channel beside it, which counts CommandAst invocations of what must be called - and drop the row from the grep table."
  }
  return $findings
}

# -- T178: the ONE AST traversal answering "does this script actually CALL X?" --
# Extracted rather than written afresh: 17z(T176) already needed this answer, and a second traversal would
# be a second answer - a call shape only one of them recognised would be judged by only one of them, the
# same reason the TD69 payload scan keeps a single implementation. CommandAst counts real invocations only,
# so a name that merely appears as a string literal - which is exactly what a guard's own declaration looks
# like - does not count. That is the whole reason this exists instead of a grep.
function Get-ScaffoldScriptCallNames {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Path)
  $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
  if (-not $resolved) { return @() }
  $ast = [System.Management.Automation.Language.Parser]::ParseFile($resolved.Path, [ref]$null, [ref]$null)
  return @($ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.CommandAst] }, $true) |
      ForEach-Object { $_.GetCommandName() } | Where-Object { $_ })
}

function Test-ScaffoldWiringRowExamples {
  <#
  .SYNOPSIS  T178 arm. Base covers three directions: rows naming files OTHER than the declaring one stay
             silent, a row naming the declaring file is reported exactly once with its own file echoed, and
             the rule follows the DECLARING path it is given rather than a hardcoded selftest.ps1.
             -Variant 'raw-path-compare' feeds a self row spelled the way a Windows author would type it;
             the core folds separators and casing, so it must still be caught. Silence there means the
             folding is gone and only the one literal spelling stays guarded.
  #>
  [CmdletBinding()]
  param([ValidateSet('raw-path-compare')][string]$Variant)
  $self = 'scripts/selftest.ps1'
  $clean = @(
    @{ f = 'scripts/review.ps1'; s = 'REVIEW-ROUTE-SKIP' },
    @{ f = 'TEMPLATE-README.md'; s = 'ReviewSkipWhen' },
    @{ f = 'specs/verdict.schema.json'; s = 'routed_skip' }
  )
  if ($Variant -eq 'raw-path-compare') {
    $alt = @(Get-ScaffoldSelfSatisfyingWiringRows -Rows @(@{ f = '.\Scripts\SelfTest.ps1'; s = 'ANY' }) -DeclaringPath $self)
    $f = @()
    # The finding IS the evidence, same shape as the harness-ratio control above: an exact string comparison
    # misses this spelling entirely, so catching it is what proves the folding carries the rule.
    if ($alt.Count -ge 1) { $f += "[WIRING-SELF-SAT-EXAMPLE] alt-spelling-control: a self row spelled '.\Scripts\SelfTest.ps1' was caught against declaring path '$self', which is what proves the comparison is normalised rather than exact. If this control ever falls silent, the separator and casing folding is gone and only the literal spelling stays guarded." }
    return $f
  }
  $findings = @()
  $silent = @(Get-ScaffoldSelfSatisfyingWiringRows -Rows $clean -DeclaringPath $self)
  if ($silent.Count -ne 0) { $findings += "[WIRING-SELF-SAT-EXAMPLE] clean-rows-not-silent: $($silent.Count) row(s) naming files OTHER than the declaring one were reported: $($silent[0]) - those rows are the legitimate use of the grep channel and must pass. [FIX] fix the rule, never the example." }
  $selfRow = @(Get-ScaffoldSelfSatisfyingWiringRows -Rows ($clean + @{ f = 'scripts/selftest.ps1'; s = 'ANY' }) -DeclaringPath $self)
  if ($selfRow.Count -ne 1) { $findings += "[WIRING-SELF-SAT-EXAMPLE] self-row-unreported: a row naming the declaring file produced $($selfRow.Count) finding(s), expected exactly 1 - that row's sentinel is written into the declaring file BY the row, so the grep can never fail and the assertion is vacuous (L265/TD171)." }
  elseif ($selfRow[0] -notmatch 'WIRING-SELF-SAT') { $findings += "[WIRING-SELF-SAT-EXAMPLE] finding-missing-sentinel: the finding carries no [WIRING-SELF-SAT] sentinel, so nothing downstream can grep for it. Got: $($selfRow[0])" }
  elseif ($selfRow[0] -notmatch 'selftest\.ps1') { $findings += "[WIRING-SELF-SAT-EXAMPLE] finding-missing-row: the finding does not echo the offending row's own file, so the author cannot tell WHICH row to remove. Got: $($selfRow[0])" }
  $other = @(Get-ScaffoldSelfSatisfyingWiringRows -Rows @(@{ f = 'scripts/review.ps1'; s = 'ANY' }) -DeclaringPath 'scripts/review.ps1')
  if ($other.Count -ne 1) { $findings += "[WIRING-SELF-SAT-EXAMPLE] other-declarer-missed: a row naming scripts/review.ps1 judged against a DECLARING path of scripts/review.ps1 produced $($other.Count) finding(s), expected exactly 1 - the rule must follow the declaring path it is handed, not a hardcoded selftest.ps1." }
  # T180/TD175: the SECOND call site's table (17n's $hookChecks) keys its pattern as 're', not 's', and
  # repeats one file across several rows. Both facts are load-bearing here, so both get a case, shaped like
  # the real table rather than like a minimal fixture: reading an absent key throws under StrictMode, and a
  # finding that echoes only the file cannot say which of three rows on that file to remove.
  $reRows = @(
    @{ f = '.claude/hooks/handoff-reminder.ps1'; re = 'Test-HookThrottle'; what = 'throttle rhythm' },
    @{ f = 'scripts/selftest.ps1';               re = 'anything at all';   what = 'the self row' }
  )
  $reFound = @()
  try { $reFound = @(Get-ScaffoldSelfSatisfyingWiringRows -Rows $reRows -DeclaringPath $self) }
  catch {
    $findings += "[WIRING-SELF-SAT-EXAMPLE] re-keyed-self-row: the rule THREW on a table keyed 're' instead of 's' ($($_.Exception.Message)). 17n's rows carry no 's' key, and under Set-StrictMode -Version Latest reading an absent one throws, so the guard would crash at the exact moment it finally had a self row to report - an exception attributed to no arm, not a finding. [FIX] name the offending row by its index; never read a key only one call site's table carries."
    return $findings
  }
  if ($reFound.Count -ne 1) { $findings += "[WIRING-SELF-SAT-EXAMPLE] re-keyed-clean-silent: a two-row 're'-keyed table holding exactly ONE self row produced $($reFound.Count) finding(s), expected 1 - the row naming a hook file is the legitimate use of the grep channel and must stay silent while the self row is reported." }
  elseif ($reFound[0] -notmatch '#1\b') { $findings += "[WIRING-SELF-SAT-EXAMPLE] self-row-index: the finding does not name the offending row's index (#1 - it is the second of two). Three of 17n's five live rows name the SAME file, so without the index the message names a file the author must then search by hand. Got: $($reFound[0])" }
  return $findings
}

# -- T111-CORE-SELFCHECK-GATE (TD140 / ADR 0011): does this shared core declare a self-check anyone runs? --
# TD140 said not one script has a direct test. Measured, that was half true, and the half that was false is
# the useful half: three of the shared cores already declare `Test-Scaffold*Examples` functions that return
# findings and are called by check-cards.ps1 or selftest.ps1, while the rest declare none. The repo was
# half-tested by a convention nobody had written down. ADR 0011 chose that convention over Pester (a new
# runtime dependency) and over sibling `<name>.tests.ps1` files (which put the examples somewhere other than
# beside the code they describe - the exact drift T88 exists to prevent), and this is its gate.
#
# THREE judgements, and the order matters:
#   * a file declaring NO functions is exempt BY RULE, not by list. Environment-setup files have nothing to
#     self-check, and deriving the exemption from content means nobody maintains an allowlist for them.
#   * a core with a self-check that NOTHING CALLS is not covered. Presence is cheap; reachability is the
#     property that makes it run. This is the hole the gate mainly exists to close.
#   * a core on the PENDING list that has since gained a reached self-check is a STALE exemption and is
#     reported. That is what makes the list a ratchet: it cannot quietly outlive the work it was excusing.
# The gate measures presence and reachability, deliberately NOT depth - it does not even require the
# `-Variant` negative control most of the covered half has, because Test-ScaffoldCardRuleExamples takes an
# ad-hoc rule instead and rejecting a working self-check over its signature would be over-fitting. How
# thorough a self-check is remains each card's discipline, proven by single-line-deletion mutation (L165),
# the same stance [CARD-SWEEP] takes: it records THAT a sweep happened and claims nothing about depth.
# Takes TEXT for both the core and its consumers, so the rule is testable without reading scripts/ at all.
# Rejected shapes, declared by name rather than left to a comment:
#   name-only      - accept a self-check because the name exists, without checking anything calls it.
#   ignore-pending - never report a stale exemption, which turns the ratchet into a permanent allowlist.
function Test-ScaffoldCoreSelfCheckVia($CoreName, $CoreText, $ConsumerText, $Pending, $Variant) {
  $findings = @()
  $tok = $null; $err = $null
  $ast = [System.Management.Automation.Language.Parser]::ParseInput([string]$CoreText, [ref]$tok, [ref]$err)
  # A core that does not parse is gate 1's business, not this sub-gate's - reporting it twice would name two
  # causes for one defect.
  if ($err -and $err.Count -gt 0) { return $findings }
  $declared = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] }, $true) | ForEach-Object { $_.Name })
  if ($declared.Count -eq 0) { return $findings }        # exempt by rule: nothing to self-check
  $selfChecks = @($declared | Where-Object { $_ -like 'Test-Scaffold*Examples' })
  # $reached starts as every declared self-check and is then NARROWED to the ones a consumer really calls.
  # Written this way round on purpose: the narrowing is one deletable line whose removal is a behaviour
  # change the examples catch, rather than an assignment whose removal leaves $reached undefined and takes
  # the gate down with a StrictMode error - non-zero for the wrong reason, which is not evidence (L167).
  $reached = @($selfChecks)
  if ($Variant -ne 'name-only') { $reached = @($selfChecks | Where-Object { [string]$ConsumerText -match [regex]::Escape($_) }) }
  $isPending = (@($Pending) -contains $CoreName)
  if ($reached.Count -gt 0) {
    if ($isPending -and ($Variant -ne 'ignore-pending')) {
      $findings += "[CORE-SELFCHECK-STALE] $CoreName is still on the self-check pending list but now declares a self-check that is actually run ($($reached -join ', ')) - the list is a ratchet (ADR 0011) and an entry that outlives the work it excused turns the gate into a permanent allowlist. [FIX] remove '$CoreName' from the pending list."
    }
    return $findings
  }
  if ($isPending) { return $findings }                   # declared outstanding, and the ratchet is what forces it
  $why = if ($selfChecks.Count -gt 0) { "declares $($selfChecks -join ', ') but nothing calls it - a self-check nobody runs is not coverage, it is a function" } else { "declares $($declared.Count) function(s) and no Test-Scaffold*Examples at all" }
  $findings += "[CORE-NO-SELFCHECK] $CoreName $why. A shared decision core with no runnable self-check is proven only indirectly, so a regression in it surfaces as a confusing red in some unrelated gate instead of at the function that broke (TD140). [FIX] declare a Test-Scaffold*Examples function beside the code, returning findings as strings rather than throwing, and call it from selftest.ps1 or check-cards.ps1 - see ADR 0011 for why the examples live in the file rather than in a sibling."
  return $findings
}

# The live decision. One core per call, so a caller can report per file. Never throws.
function Test-ScaffoldCoreSelfCheck {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$CoreText,
    [Parameter(Mandatory)][string]$CoreName,
    [Parameter(Mandatory)][AllowEmptyString()][string]$ConsumerText,
    [string[]]$Pending = @()
  )
  return (Test-ScaffoldCoreSelfCheckVia $CoreName $CoreText $ConsumerText $Pending $null)
}

# Declared examples for the core self-check decision, one per case CLASS. Returns findings as strings and
# never throws (same contract as Test-ScaffoldCardSweepExamples). Default = exercise the live predicate and
# expect the stated finding count; -Variant re-runs the same cases through a named rejected shape, which
# MUST produce at least one disagreement. Hermetic: every case is text, nothing is read from scripts/.
# This function is itself the thing it checks for - _guard.ps1 satisfies its own gate.
function Test-ScaffoldCoreSelfCheckExamples {
  [CmdletBinding()]
  param([ValidateSet('name-only', 'ignore-pending')][string]$Variant)
  $covered = "function Get-Thing { 1 }`nfunction Test-ScaffoldThingExamples { @() }"
  $bare = "function Get-Thing { 1 }"
  $noFn = "# only comments and an assignment`n`$ScaffoldThing = 'x'"
  $callsIt = "the gate runs Test-ScaffoldThingExamples here"
  $callsNothing = "the gate runs something else entirely"
  $cases = @(
    @{ what = 'covered core - declares a self-check and it is called'; name = '_thing.ps1'; text = $covered; consumer = $callsIt; pending = @(); expect = 0; sentinel = $null }
    @{ what = 'uncovered core, not pending'; name = '_thing.ps1'; text = $bare; consumer = $callsIt; pending = @(); expect = 1; sentinel = 'CORE-NO-SELFCHECK' }
    @{ what = 'declared but unreached - nothing calls the self-check'; name = '_thing.ps1'; text = $covered; consumer = $callsNothing; pending = @(); expect = 1; sentinel = 'CORE-NO-SELFCHECK' }
    @{ what = 'no functions declared - exempt by rule, not by list'; name = '_thing.ps1'; text = $noFn; consumer = $callsNothing; pending = @(); expect = 0; sentinel = $null }
    @{ what = 'pending core, still uncovered - the ratchet is holding it'; name = '_thing.ps1'; text = $bare; consumer = $callsIt; pending = @('_thing.ps1'); expect = 0; sentinel = $null }
    @{ what = 'pending core now covered - a stale exemption'; name = '_thing.ps1'; text = $covered; consumer = $callsIt; pending = @('_thing.ps1'); expect = 1; sentinel = 'CORE-SELFCHECK-STALE' }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(Test-ScaffoldCoreSelfCheckVia $c.name $c.text $c.consumer $c.pending $v)
    if ($got.Count -ne $c.expect) { $findings += "[CORE-SELFCHECK-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) (TD140/ADR 0011). [FIX] fix the predicate, never the example." }
    elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) { $findings += "[CORE-SELFCHECK-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) sentinel - a finding that arrives from the wrong branch is not evidence the right one fired (L165). [FIX] fix the predicate, never the example." }
  }
  return $findings
}


# ── T176-GATE-RESULT-RECORD: the per-gate verdict record ────────────────────────────────────────────
# selftest reports ONE boolean per run. `Fail` raises a latch that nothing ever lowers, so the exit code
# names the shard and never the gate, and on CI's ten matrix jobs "which part is having the issue" costs
# opening logs one at a time. These pure functions make the verdict per GATE so a red run says which part.
# They live here rather than in selftest.ps1 for the reason every decision core does: the judgement has to
# be testable without running the thing it judges.
#
# THE COUNTER IS THE PRODUCT. A naive "is the latch set" check marks every gate after the first failure as
# FAIL, because the latch is global and monotonic. Comparing a per-gate snapshot of the failure COUNT is
# what makes attribution honest - which is why -Variant 'ignore-per-gate-counter' feeds the examples the
# latch instead: without the counter, the clean-gate-after-an-earlier-failure case reports FAIL and the
# whole record becomes a second, more expensive way to say what the exit code already said.
#
# Sentinels are pure ASCII (L165): [GATE-RESULT] {id} {PASS|FAIL} {sec}s and
# [GATE-RESULT-SUMMARY] failed={ids|none} ran={n} wall={sec}s recheck={cmd|n/a} (caveat).

function Get-ScaffoldGateResultLineVia {
  <#
  .SYNOPSIS  T176 arms 1/3/4. The per-gate verdict line. -Variant 'ignore-per-gate-counter' substitutes the
             REJECTED design (read the global latch) so the examples can prove the counter is what decides.
  .DESCRIPTION
    $Id is passed in, never derived here: selftest hands over the same live $script:CurGateId the timing
    line prints, so the two lines cannot name different gates and no second id list exists to drift.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Id,
    [int]$FailsBefore = 0,
    [int]$FailsAfter = 0,
    [bool]$LatchSet = $false,
    [double]$Seconds = 0,
    [string]$Variant
  )
  $failed = if ($Variant -eq 'ignore-per-gate-counter') { $LatchSet } else { $FailsAfter -gt $FailsBefore }
  $verdict = if ($failed) { 'FAIL' } else { 'PASS' }
  return ('[GATE-RESULT] {0} {1} {2}s' -f $Id, $verdict, $Seconds)
}

function Get-ScaffoldGateResultLine {
  <# .SYNOPSIS  The live per-gate verdict line (thin wrapper over the Via helper, no variant). #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Id,
    [int]$FailsBefore = 0,
    [int]$FailsAfter = 0,
    [double]$Seconds = 0
  )
  return (Get-ScaffoldGateResultLineVia -Id $Id -FailsBefore $FailsBefore -FailsAfter $FailsAfter -Seconds $Seconds)
}

function Get-ScaffoldGateResultSummaryVia {
  <#
  .SYNOPSIS  T176 arms 2/5. The one end-of-run line naming every failing gate. -Variant
             'ignore-diagnosis-caveat' drops the clause that keeps the named command from reading as a pass.
  .DESCRIPTION
    The line names a scoped re-check command AND says in the same breath that a scoped run is diagnosis,
    never acceptance (ADR 0007, T62). Naming the command without the caveat is exactly how a filtered run
    gets mistaken for a green acceptance run, so the caveat travels ON the line that names the command
    rather than in prose somewhere above it that a CI job summary would never carry.
  #>
  [CmdletBinding()]
  param(
    [string[]]$FailedIds = @(),
    [int]$Ran = 0,
    [double]$Wall = 0,
    [string]$Variant
  )
  $ids = @($FailedIds | Where-Object { $_ })
  $failedField = if ($ids.Count -gt 0) { $ids -join ',' } else { 'none' }
  $recheck = if ($ids.Count -gt 0) { "pwsh -File scripts/selftest.ps1 -Only '$($ids -join ',')'" } else { 'n/a' }
  $caveat = if ($Variant -eq 'ignore-diagnosis-caveat') { '' } else { ' (diagnosis only, never acceptance - the full unfiltered run stays the bar)' }
  return ('[GATE-RESULT-SUMMARY] failed={0} ran={1} wall={2}s recheck={3}{4}' -f $failedField, $Ran, $Wall, $recheck, $caveat)
}

function Get-ScaffoldGateResultSummary {
  <# .SYNOPSIS  The live end-of-run summary line (thin wrapper over the Via helper, no variant). #>
  [CmdletBinding()]
  param(
    [string[]]$FailedIds = @(),
    [int]$Ran = 0,
    [double]$Wall = 0
  )
  return (Get-ScaffoldGateResultSummaryVia -FailedIds $FailedIds -Ran $Ran -Wall $Wall)
}

function Test-ScaffoldGateResultExamples {
  <#
  .SYNOPSIS  Declared examples for the gate-result record, one per case CLASS. Default returns nothing;
             each -Variant re-runs the same cases through a named REJECTED shape and must disagree.
  .DESCRIPTION
    Hermetic: every case is literal data, nothing is read from scripts/ or from a live run. Run from
    sub-gate 17z's probe table and from this card's DoD.
  #>
  [CmdletBinding()]
  param([ValidateSet('ignore-per-gate-counter', 'ignore-diagnosis-caveat')][string]$Variant)
  $v = if ($PSBoundParameters.ContainsKey('Variant')) { $Variant } else { $null }

  # before/after = the failure COUNT snapshotted around one gate; latch = the global monotonic flag.
  $lineCases = @(
    @{ what = 'a clean gate, nothing failed yet';                     id = '3';     before = 0; after = 0; latch = $false; sec = 1.2; expect = '[GATE-RESULT] 3 PASS 1.2s' }
    @{ what = 'the gate that actually failed';                        id = '7';     before = 0; after = 1; latch = $true;  sec = 4.5; expect = '[GATE-RESULT] 7 FAIL 4.5s' }
    @{ what = 'a clean gate running AFTER an earlier failure';        id = '10';    before = 1; after = 1; latch = $true;  sec = 0.4; expect = '[GATE-RESULT] 10 PASS 0.4s' }
    @{ what = 'a sub-segment label passes through verbatim (T62 r4)'; id = '17pre'; before = 2; after = 2; latch = $true;  sec = 9;   expect = '[GATE-RESULT] 17pre PASS 9s' }
    @{ what = 'a gate that failed twice is still one FAIL';           id = '14';    before = 1; after = 3; latch = $true;  sec = 2;   expect = '[GATE-RESULT] 14 FAIL 2s' }
  )
  $sumCases = @(
    @{ what = 'failures named in run order'; ids = @('7', '17post'); ran = 19; wall = 385.3
       needs = @('failed=7,17post', 'ran=19', 'wall=385.3s', "-Only '7,17post'", 'diagnosis only, never acceptance') }
    @{ what = 'a green run says none';       ids = @();             ran = 19; wall = 12.5
       needs = @('failed=none', 'ran=19', 'recheck=n/a', 'diagnosis only, never acceptance') }
  )

  $findings = @()
  if ($v -eq 'ignore-per-gate-counter') {
    foreach ($c in $lineCases) {
      $got = Get-ScaffoldGateResultLineVia -Id $c.id -FailsBefore $c.before -FailsAfter $c.after -LatchSet $c.latch -Seconds $c.sec -Variant $v
      if ($got -ne $c.expect) { return @("[GATE-RESULT-EXAMPLE] with the per-gate counter removed, case '$($c.what)' produced '$got' instead of '$($c.expect)' - the latch decides, so attribution is per RUN and every gate after the first failure is mislabelled.") }
    }
    return @()
  }
  if ($v -eq 'ignore-diagnosis-caveat') {
    foreach ($c in $sumCases) {
      $got = Get-ScaffoldGateResultSummaryVia -FailedIds $c.ids -Ran $c.ran -Wall $c.wall -Variant $v
      foreach ($n in $c.needs) {
        if ($got -notmatch [regex]::Escape($n)) { return @("[GATE-RESULT-EXAMPLE] with the caveat removed, the summary for case '$($c.what)' lost '$n' - it names a scoped command with nothing on the line saying a scoped run is never acceptance (ADR 0007, T62).") }
      }
    }
    return @()
  }

  foreach ($c in $lineCases) {
    $got = Get-ScaffoldGateResultLineVia -Id $c.id -FailsBefore $c.before -FailsAfter $c.after -LatchSet $c.latch -Seconds $c.sec
    if ($got -ne $c.expect) { $findings += "[GATE-RESULT-EXAMPLE] case '$($c.what)' produced '$got', expected '$($c.expect)'. [FIX] fix the predicate, never the example." }
  }
  foreach ($c in $sumCases) {
    $got = Get-ScaffoldGateResultSummaryVia -FailedIds $c.ids -Ran $c.ran -Wall $c.wall
    foreach ($n in $c.needs) {
      if ($got -notmatch [regex]::Escape($n)) { $findings += "[GATE-RESULT-EXAMPLE] case '$($c.what)' produced a summary missing '$n'. Got: $got [FIX] fix the predicate, never the example." }
    }
  }
  return $findings
}

# -- T202-ENV-SKIP-EXERCISED (TD195): which gate bodies vanish when a TOOL is absent? --
# A gate wrapped in `if (-not (Get-Command <tool> -ErrorAction SilentlyContinue)) { announce } else { every
# assertion }` still reports PASS, because Exit-Gate reads the failure counter around the gate: zero executed
# assertions is indistinguishable from all of them passing. mutate.ps1 cannot reach that arm at all - it
# deletes lines in a tree that HAS the tool - so the degraded branch is structurally outside the evidence
# batch and LOOKS covered. The projection lives here rather than in selftest.ps1 so it is testable on TEXT,
# the same split 14g/14h/14i/14k/14m use.
function Get-ScaffoldEnvSkipGuardSite {
  <#
  .SYNOPSIS  T202 arm 5: every tool-availability guard in a selftest-shaped script, bound to the gate whose
             body it can silence. Returns sites (Line/Tool/Gate); Gate is '' for a guard outside any gate.
  .DESCRIPTION
    Attribution rule, stated because it is a heuristic and not a parse: the enclosing gate is the most recent
    `if (Enter-Gate '<id>')` above the guard. Guards above the FIRST such line get Gate '' - today that is
    the -Parallel launcher's own no-git fallback, which reports no gate verdict at all (it switches
    snapshotting from `git clone` to a file copy) and is therefore a different defect shape, registered as
    TD195 remainder rather than judged here.
  #>
  [CmdletBinding()]
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $sites = @()
  $gate = ''
  $lines = $Text -split "`r?`n"
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^\s*if \(Enter-Gate '([^']+)'\)") { $gate = $Matches[1] }
    foreach ($m in [regex]::Matches($lines[$i], "Get-Command\s+([A-Za-z][\w.-]*)\s+-(?:EA|ErrorAction)\s+SilentlyContinue")) {
      $sites += [pscustomobject]@{ Line = $i + 1; Tool = $m.Groups[1].Value; Gate = $gate }
    }
  }
  return $sites
}

function Get-ScaffoldEnvSkipIssue {
  <#
  .SYNOPSIS  T202 arms 5/6b: reconcile the projected guard sites against the declared registry. Returns
             findings; an empty result is green.
  .DESCRIPTION
    Two directions, and the second is the one that keeps this honest over time. An UNREGISTERED (tool, gate)
    pair is a body that can vanish with nothing exercising it - the TD195 defect itself, and the reason a
    hand-written registry is not enough: TD195's own row counted two instances where the tree has four, and a
    registry nothing checks back against the file inherits exactly that undercount (L282). A row that
    EXCLUDES part of its gate is legal - cost and known defects are real - but an exclusion with no stated
    reason is a silent cap, which reads from outside exactly like full coverage. Reasons are required, never
    validated for truthfulness: this can tell a stated act from a quiet one, nothing more.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Sites,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Registry
  )
  $findings = @()
  $inGate = @($Sites | Where-Object { $_.Gate -ne '' })
  # Vacuity first, the stance T191's timeout core and T194's receipt core both take: with nothing projected
  # there is nothing to reconcile, and silence would read as agreement.
  if ($inGate.Count -eq 0) {
    $findings += "[ENV-SKIP-DEGRADED] no tool-availability guard was projected out of the script at all, so the registry below is judged against an empty set and would pass however wrong it is. The guard form drifted, or the Enter-Gate attribution stopped matching - fix the projection in scripts/_guard.ps1, never this check."
    return $findings
  }
  if (@($Registry).Count -eq 0) {
    $findings += "[ENV-SKIP-DEGRADED] $($inGate.Count) tool-availability guard(s) were projected but the registry is empty, so no degraded branch is exercised by anything."
    return $findings
  }
  foreach ($pair in @($inGate | Group-Object { "$($_.Tool)@$($_.Gate)" })) {
    $row = @($Registry | Where-Object { "$($_.Tool)@$($_.Gate)" -eq $pair.Name })
    if ($row.Count -eq 0) {
      $findings += "[ENV-SKIP-DEGRADED] '$($pair.Name)' guards $($pair.Count) site(s) (line(s) $((@($pair.Group | ForEach-Object { $_.Line }) | Sort-Object) -join ', ')) and no registry row exercises it. When that tool is absent those bodies do not run, the gate still reports PASS, and no mutation can reach the branch because mutate.ps1 deletes lines in a tree that HAS the tool. [FIX] add a row to the registry in scripts/selftest.ps1 - either exercising the pair with an -Only token, or declaring the exclusion WITH a reason."
    }
  }
  foreach ($row in @($Registry)) {
    foreach ($ex in @($row.Exclude)) {
      if ([string]::IsNullOrWhiteSpace($ex)) {
        $findings += "[ENV-SKIP-DEGRADED] the registry row '$($row.Tool)@$($row.Gate)' carries an exclusion with no stated reason. An unexplained exclusion is a silent cap: from outside, a check green over a population it declined to look at is indistinguishable from one green over the whole population (L282). [FIX] state why - a tracked debt id, or a measured cost."
      }
    }
    if (-not $row.Token -and @($row.Exclude).Count -eq 0) {
      $findings += "[ENV-SKIP-DEGRADED] the registry row '$($row.Tool)@$($row.Gate)' runs no token and declares no exclusion, so it neither exercises its pair nor says why not - it only silences the unregistered-pair finding above. [FIX] give it a token, or state the exclusion."
    }
  }
  return $findings
}

function Get-ScaffoldEnvSkipRunIssue {
  <#
  .SYNOPSIS  T202 arms 2/3/4: judge ONE degraded child run - was the tool really hidden, did the child exit
             0, did it print every announcement the registry declared? Returns findings; empty is green.
  .DESCRIPTION
    This lives here, and not inline in the sub-gate, for a reason the first cut of this card got wrong and
    the mutation batch caught: written as `if (bad) { Fail ... }` around a live run, those branches NEVER
    EXECUTE on a healthy tree, so deleting any one of them changes nothing and the mutation SURVIVES. An
    assertion that cannot be killed is not evidence that it works (L165/L167). As a pure function over
    (hidden, exit, output) it is driven by declared examples that DO take every branch, so each arm is
    provably load-bearing.
    Order matters and is asserted: a tool that was not hidden is reported INSTEAD of judging the run, because
    a child that took the tool-present branch says nothing about the degraded one, and reporting a green over
    it would be the very shape this sub-gate exists to reject (L282 third shape).
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Tool,
    [Parameter(Mandatory)][string]$Token,
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Expect,
    [Parameter(Mandatory)][bool]$ToolHidden,
    [Parameter(Mandatory)][int]$ExitCode,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Output
  )
  $findings = @()
  if (-not $ToolHidden) {
    $findings += "[ENV-SKIP-DEGRADED] '$Tool' is still resolvable after every PATH entry carrying it was removed, so the '-Only $Token' child took the tool-PRESENT branch and any verdict below would be green about a degraded path never entered. This fails rather than skipping, because a fixture that skips under the exact condition it exists to test reproduces the defect inside its own guard. [FIX] the tool resolves PATH-independently (an alias, a shim, an absolute call); teach the strip, do not drop the row."
    return $findings
  }
  if ($ExitCode -ne 0) {
    $findings += "[ENV-SKIP-DEGRADED] with '$Tool' hidden, '-Only $Token' exited $ExitCode instead of 0. That gate announces graceful degradation when the tool is absent, and downstream projects are told they can run this suite offline, so a non-zero here is that promise being false."
    return $findings
  }
  foreach ($esW in @($Expect)) {
    if ($Output -notmatch [regex]::Escape($esW)) {
      $findings += "[ENV-SKIP-DEGRADED] with '$Tool' hidden, '-Only $Token' exited 0 but never printed the declared announcement '$esW'. Exit 0 alone cannot tell a gate that DEGRADED from one that VANISHED - both report PASS - so the announcement is the half that makes the skip observable. [FIX] restore the announcement, or update the registry row if the wording legitimately changed."
    }
  }
  return $findings
}

function Get-ScaffoldPathWithoutDirs {
  <#
  .SYNOPSIS  T205 arm 1: remove the named directories from a PATH string. Pure - takes the separator, so
             the POSIX shape is drivable from a Windows box and the Windows shape from a POSIX one.
  .DESCRIPTION
    8.2j hides a tool by dropping the PATH entries that carry it, and the strip that did that was inline,
    written in Windows shapes, and only ever EXECUTED on Windows - so neither of its two platform
    assumptions was ever observable as an assumption. It split PATH on a literal ';' and identified the
    tool by probing '<tool>.exe/.cmd/.bat'. On POSIX the split yields the whole PATH as a single element
    and no probe matches, so PATH came back unchanged, the tool still resolved, and every exercised row
    fail-closed on `is still resolvable` - ubuntu-latest/light red on every push from T202 to T205 (TD201).
    Taking the separator as a PARAMETER rather than reading [System.IO.Path]::PathSeparator here is the
    whole point: the live caller passes the framework's answer, and the examples pass the other platform's,
    so the branch the live face can never enter is still proven on every run.
    Directory identity is compared with trailing '/' and '\' trimmed, because a PATH entry and the parent
    reported by Split-Path routinely differ by exactly that.
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Path,
    [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Directories,
    [Parameter(Mandatory)][string]$Separator
  )
  $drop = @($Directories | Where-Object { $_ } | ForEach-Object { $_.TrimEnd('\', '/') })
  # -split takes a REGEX, and the Windows separator is not a metacharacter while a future one might be, so
  # the separator is escaped on the way in and used literally on the way out.
  return (($Path -split [regex]::Escape($Separator)) | Where-Object { $_ -and ($drop -notcontains $_.TrimEnd('\', '/')) }) -join $Separator
}

function Test-ScaffoldEnvSkipExamples {
  <#
  .SYNOPSIS  T202 arm 6's declared examples for the three functions above. Returns findings; empty is green.
             Hermetic - synthetic script text, synthetic rows and synthetic run results, so it reads no file
             and needs no repo.
  .DESCRIPTION
    The live call can only ever feed these cores a tree that is correct by construction: on a green run every
    projected pair has a row, so the real face proves they ACCEPT agreement and can never prove they reject
    anything (TD140 / ADR 0011, the stance 8.2g' / 8.2h' / 17ad' already take). -Variant 'find-nothing' is the
    control the consumer re-runs these same cases through; it must disagree at least once.
    Every finding reports under [ENV-SKIP-EXAMPLE], and nothing here may emit the string the card's
    dod_command greps for - that sentinel is RESERVED to the one success line in selftest.ps1 (L283).
  #>
  [CmdletBinding()]
  param([ValidateSet('real', 'find-nothing')][string]$Variant = 'real')
  $script1 = @"
if (Enter-Gate '1') {
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Host 'skip' }
}
if (Enter-Gate '17') {
  if (-not (Get-Command git -EA SilentlyContinue)) { Write-Host 'skip' }
}
"@
  # A guard ABOVE the first Enter-Gate belongs to no gate: the -Parallel launcher's own fallback reports no
  # gate verdict, so it must not be demanded of the registry.
  $script2 = @"
`$plGit = [bool](Get-Command git -ErrorAction SilentlyContinue)
if (Enter-Gate '1') {
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Host 'skip' }
}
"@
  $rowNode = @{ Tool = 'node'; Gate = '1'; Token = '1'; Exclude = @() }
  $rowGit = @{ Tool = 'git'; Gate = '17'; Token = '17pre'; Exclude = @('17post: TD196') }
  $findings = @()

  $projCases = @(
    @{ n = 'two-gates'; text = $script1; wantPairs = @('node@1', 'git@17'); wantUngated = 0 }
    @{ n = 'guard-above-first-gate'; text = $script2; wantPairs = @('node@1'); wantUngated = 1 }
    @{ n = 'empty-text'; text = ''; wantPairs = @(); wantUngated = 0 }
  )
  foreach ($c in $projCases) {
    $got = @(Get-ScaffoldEnvSkipGuardSite -Text $c.text)
    $pairs = @($got | Where-Object { $_.Gate -ne '' } | ForEach-Object { "$($_.Tool)@$($_.Gate)" } | Sort-Object -Unique)
    $ungated = @($got | Where-Object { $_.Gate -eq '' }).Count
    foreach ($w in @($c.wantPairs)) {
      if ($w -notin $pairs) { $findings += "[ENV-SKIP-EXAMPLE] projection case '$($c.n)' lost the pair '$w'; got $(if ($pairs.Count) { $pairs -join ', ' } else { 'nothing' }). [FIX] fix the projection, never the example." }
    }
    if ($pairs.Count -ne @($c.wantPairs).Count) { $findings += "[ENV-SKIP-EXAMPLE] projection case '$($c.n)' returned $($pairs.Count) gated pair(s), expected $(@($c.wantPairs).Count). [FIX] fix the projection, never the example." }
    if ($ungated -ne $c.wantUngated) { $findings += "[ENV-SKIP-EXAMPLE] projection case '$($c.n)' attributed $ungated guard(s) to no gate, expected $($c.wantUngated) - a guard above the first Enter-Gate must NOT be demanded of the registry, and one below it must not be excused from it. [FIX] fix the projection, never the example." }
  }

  $issueCases = @(
    @{ n = 'all-registered'; text = $script1; reg = @($rowNode, $rowGit); want = @() }
    @{ n = 'unregistered-pair'; text = $script1; reg = @($rowNode); want = @('and no registry row exercises it') }
    @{ n = 'empty-registry'; text = $script1; reg = @(); want = @('the registry is empty') }
    @{ n = 'nothing-projected'; text = ''; reg = @($rowNode); want = @('no tool-availability guard was projected') }
    @{ n = 'exclusion-without-reason'; text = $script1; reg = @($rowNode, @{ Tool = 'git'; Gate = '17'; Token = '17pre'; Exclude = @('  ') }); want = @('an exclusion with no stated reason') }
    @{ n = 'row-that-does-nothing'; text = $script1; reg = @($rowNode, @{ Tool = 'git'; Gate = '17'; Token = ''; Exclude = @() }); want = @('neither exercises its pair nor says why not') }
  )
  foreach ($c in $issueCases) {
    $sites = @(Get-ScaffoldEnvSkipGuardSite -Text $c.text)
    $got = @()
    if ($Variant -eq 'real') { $got = @(Get-ScaffoldEnvSkipIssue -Sites $sites -Registry @($c.reg)) }
    if (@($c.want).Count -eq 0) {
      if ($got.Count -ne 0) { $findings += "[ENV-SKIP-EXAMPLE] the agreeing shape '$($c.n)' was reported ($($got -join ' | ')). A registry covering every projected pair is the normal green state; a rule that reds it cannot be landed. [FIX] fix the rule, never the example." }
      continue
    }
    foreach ($w in @($c.want)) {
      if (@($got | Where-Object { $_ -like "*$w*" }).Count -lt 1) {
        $findings += "[ENV-SKIP-EXAMPLE] the shape '$($c.n)' was not reported through its own arm ('$w'); got $(if ($got.Count) { $got -join ' | ' } else { 'nothing' }). [FIX] fix the rule, never the example."
      }
    }
  }

  # The degraded-run arms. These exist because the live face can only ever feed that core a HEALTHY run - on
  # a green tree the tool hides, the child exits 0 and prints everything - so every rejecting branch is dead
  # code from the live side and a deletion there survives. Measured, not theorised: the first cut of this
  # card wrote those three checks inline and the mutation batch reported SURVIVED on all three.
  $goodOut = "  node skipped (not installed)`n  1b skipped`n[GATE-RESULT] 1 PASS"
  $runCases = @(
    @{ n = 'healthy-degraded-run'; hidden = $true; exit = 0; out = $goodOut; expect = @('node skipped (not installed)', '1b skipped'); want = @() }
    @{ n = 'no-announcements-declared'; hidden = $true; exit = 0; out = $goodOut; expect = @(); want = @() }
    @{ n = 'tool-not-hidden'; hidden = $false; exit = 0; out = $goodOut; expect = @('node skipped (not installed)'); want = @('is still resolvable after every PATH entry') }
    @{ n = 'child-exited-nonzero'; hidden = $true; exit = 1; out = 'The term git is not recognized'; expect = @(); want = @('exited 1 instead of 0') }
    @{ n = 'announcement-missing'; hidden = $true; exit = 0; out = "[GATE-RESULT] 1 PASS"; expect = @('node skipped (not installed)'); want = @('never printed the declared announcement') }
    # A gate that vanished entirely exits 0 AND prints a PASS line, so exit code alone cannot separate it
    # from an honest skip. This case is the one that proves the announcement arm carries its own weight.
    @{ n = 'vanished-but-exit-zero'; hidden = $true; exit = 0; out = "[GATE-RESULT] 1 PASS 0.1s"; expect = @('node skipped (not installed)', '1b skipped'); want = @('never printed the declared announcement') }
    # Precedence: an unhidden tool is reported INSTEAD of the run verdict. Without this the fixture could
    # report a missing announcement from a run that never entered the degraded branch at all - a true
    # finding about a meaningless observation.
    @{ n = 'not-hidden-outranks-run'; hidden = $false; exit = 1; out = ''; expect = @('never printed'); want = @('is still resolvable after every PATH entry'); reject = @('exited 1 instead of 0') }
  )
  foreach ($c in $runCases) {
    $got = @()
    if ($Variant -eq 'real') { $got = @(Get-ScaffoldEnvSkipRunIssue -Tool 'node' -Token '1' -Expect @($c.expect) -ToolHidden $c.hidden -ExitCode $c.exit -Output $c.out) }
    if (@($c.want).Count -eq 0) {
      if ($got.Count -ne 0) { $findings += "[ENV-SKIP-EXAMPLE] the agreeing run shape '$($c.n)' was reported ($($got -join ' | ')). A hidden tool, exit 0 and every declared announcement present is the normal green state; a rule that reds it cannot be landed. [FIX] fix the rule, never the example." }
      continue
    }
    foreach ($w in @($c.want)) {
      if (@($got | Where-Object { $_ -like "*$w*" }).Count -lt 1) {
        $findings += "[ENV-SKIP-EXAMPLE] the run shape '$($c.n)' was not reported through its own arm ('$w'); got $(if ($got.Count) { $got -join ' | ' } else { 'nothing' }). [FIX] fix the rule, never the example."
      }
    }
    # ContainsKey, not `$c.reject`: the consumer runs under Set-StrictMode -Version Latest, where reading a
    # key a hashtable does not have THROWS rather than returning $null - so the bare form passed standalone
    # and killed gate 8. Filtered as well, because a bare @($null) makes `-like "**"` match every finding.
    foreach ($r in @($(if ($c.ContainsKey('reject')) { $c.reject }) | Where-Object { $_ })) {
      if (@($got | Where-Object { $_ -like "*$r*" }).Count -gt 0) {
        $findings += "[ENV-SKIP-EXAMPLE] the run shape '$($c.n)' ALSO reported '$r', but an unhidden tool must be reported instead of any verdict on the run - the child never entered the degraded branch, so a finding about its output is a true statement about a meaningless observation. [FIX] fix the rule, never the example."
      }
    }
  }

  # The strip arms (T205/TD201). Everything above judges a run that ALREADY happened; these judge the step
  # that makes it happen at all. They exist because the live face runs on exactly one platform, so the other
  # platform's branch is unreachable from it - which is not a hypothetical: the inline strip these replace
  # split PATH on a literal ';' and probed '<tool>.exe/.cmd/.bat', both correct on Windows and both inert on
  # POSIX, and ubuntu-latest/light was red on every push from T202 to T205 with nine green legs beside it.
  # Passing the separator in is what makes both branches reachable from either host.
  $stripCases = @(
    @{ n = 'posix-strip'; path = '/usr/bin:/bin:/usr/local/bin'; dirs = @('/usr/bin'); sep = ':'; want = '/bin:/usr/local/bin' }
    @{ n = 'posix-trailing-slash'; path = '/usr/bin/:/bin'; dirs = @('/usr/bin'); sep = ':'; want = '/bin' }
    @{ n = 'windows-strip'; path = 'C:\a;C:\b'; dirs = @('C:\a'); sep = ';'; want = 'C:\b' }
    @{ n = 'strip-nothing'; path = '/usr/bin:/bin'; dirs = @(); sep = ':'; want = '/usr/bin:/bin' }
  )
  foreach ($sc in $stripCases) {
    # Not $null: under -Variant 'find-nothing' the core is not called at all, and a sentinel that can never
    # be a legal return value makes that variant DISAGREE on every case - which is what the consumer's
    # anti-vacuous probe re-runs these through.
    $sgot = '<core not run>'
    if ($Variant -eq 'real') { $sgot = Get-ScaffoldPathWithoutDirs -Path $sc.path -Directories @($sc.dirs) -Separator $sc.sep }
    if ($sgot -ne $sc.want) {
      $findings += "[ENV-SKIP-EXAMPLE] the strip shape '$($sc.n)' returned '$sgot', expected '$($sc.want)'. A strip that returns the PATH unchanged hides nothing, so the child takes the tool-PRESENT branch and the whole sub-gate can only report that the tool is still resolvable - never the degradation it exists to check. [FIX] fix the strip, never the example."
    }
  }
  return $findings
}

# -- T206-TRIAGE-CI-HEALTH (TD203): the pure half of heartbeat probe 16 --
# The heartbeat's admission rule (docs/LOOP-ENGINEERING.md) is not "does the signal originate outside" but
# "does THIS SCAN itself need network, state or credentials" - if it does, the probe cannot enter. CI health
# is a remote fact, so it enters the way `scaffold-stale` does: an explicit `triage.ps1 scan -Fetch` performs
# one read-only refresh into a local record, and the scan judges that record offline. This function IS that
# judgement - no I/O, no clock, no gh - which is also what makes every one of its arms drivable from a DoD.
function Get-ScaffoldCiHealthFinding {
  <#
  .SYNOPSIS  T206 (TD203): judge the default branch's CI health from an already-cached run record.
  .DESCRIPTION
    Returns nothing when the branch is healthy, or ONE finding object (severity / what / next) otherwise.
    Three verdicts, each measured against a failure this repo has actually taken:
      * no record -> minor. Silence would be the fail-quiet shape TD195 rejects; blocking would fire forever
                    on every box without gh, and a probe that always fires stops being read (the bar
                    selfcheck case 8b already applies to release-untagged).
      * failure   -> blocking. ADR 0007 keeps the acceptance matrix off the PR critical path and ship waits
                    only on the ci.yml fan-in context, so nothing else on the loop reads this result: it
                    outranks every self-maintenance finding or it is invisible all over again.
      * cancelled -> minor NON-VERDICT. CLAUDE.md [CI-RERUN-RACE]: a run cancelled by the concurrency group
                    is neither a pass nor a failure, and calling it red alarms on every superseded rerun.
    A run that has not completed is not judged at all - an acceptance run still in flight is not a red leg.
  #>
  [CmdletBinding()]
  param([hashtable]$Record)

  # Records arrive from ConvertFrom-Json -AsHashtable, so a key can be absent entirely; under StrictMode an
  # unguarded read of one throws, which for a reporter would turn a stale cache into a crash.
  function Get-CiField($h, $k) {
    if (($h -is [hashtable]) -and $h.ContainsKey($k) -and $null -ne $h[$k]) { return $h[$k] }
    return ''
  }

  if ((-not $Record) -or (-not $Record.ContainsKey('runs')) -or ($null -eq $Record['runs'])) {
    return [pscustomobject]@{
      severity = 'minor'
      what     = '[CI-HEALTH] CI health for the default branch is UNKNOWN - no run record is cached. The heartbeat judges CI offline from a record an explicit refresh writes, so until one is taken a red acceptance leg stays invisible here: that is how L260 recurrence 1 rode six merged cards and recurrence 2 four pushes, both found only when a human opened the Actions tab.'
      next     = 'pwsh -File scripts\triage.ps1 scan -Fetch   (one read-only run query; the scan itself stays offline, and with no gh it announces and skips)'
    }
  }

  $branch = [string](Get-CiField $Record 'branch')
  if (-not $branch) { $branch = 'the default branch' }
  $captured = [string](Get-CiField $Record 'capturedAt')
  if (-not $captured) { $captured = 'an unrecorded time' }

  $red = @()
  $unclear = @()
  foreach ($r in @($Record['runs'])) {
    if (-not ($r -is [hashtable])) { continue }
    $status = [string](Get-CiField $r 'status')
    $concl = [string](Get-CiField $r 'conclusion')
    if ($status -and ($status -ne 'completed')) { continue }   # still in flight: not a verdict
    if (-not $concl) { continue }                              # queued / no verdict yet
    if ($concl -eq 'cancelled') { $unclear += $r; continue }
    if (@('success', 'skipped', 'neutral') -contains $concl) { continue }
    $red += $r
  }

  if ($red.Count -gt 0) {
    $lines = foreach ($r in $red) {
      $jobs = @(@(Get-CiField $r 'failedJobs') | Where-Object { $_ })   # re-wrap: a pipeline that yields ONE item is a scalar, and .Count on it throws under StrictMode
      $jobTxt = if ($jobs.Count) { "jobs: $($jobs -join ', ')" } else { 'failing job not recorded' }
      "$(Get-CiField $r 'workflow') concluded $(Get-CiField $r 'conclusion') (run $(Get-CiField $r 'runId'), $jobTxt)"
    }
    return [pscustomobject]@{
      severity = 'blocking'
      what     = "[CI-HEALTH] the newest run of $($red.Count) workflow(s) on $branch did not pass - $($lines -join ' | '). Record captured $captured. ADR 0007 keeps the acceptance matrix off the PR critical path and ship waits only on the ci.yml fan-in context, so every PR keeps reporting green while this leg stays red."
      next     = "gh run view $(Get-CiField $red[0] 'runId') --log-failed   (then fix on a card; continue a red run with gh run rerun <id> --failed, never a fresh full run)"
    }
  }

  if ($unclear.Count -gt 0) {
    $ids = @(foreach ($r in $unclear) { "$(Get-CiField $r 'workflow') (run $(Get-CiField $r 'runId'))" })
    return [pscustomobject]@{
      severity = 'minor'
      what     = "[CI-HEALTH] the newest run of $($unclear.Count) workflow(s) on $branch was CANCELLED - $($ids -join ', '). Record captured $captured. [CI-RERUN-RACE]: a cancelled run is a non-verdict, not a pass, so this branch has no acceptance evidence at all - most often a rerun a newer push cancelled mid-flight."
      next     = "gh run rerun $(Get-CiField $unclear[0] 'runId') --failed   (and do not push to the default branch while it runs, or the concurrency group cancels it again)"
    }
  }

  return
}


# ── T211-MUT-ANCHOR-RATCHET (TD206): the mutation registry's binding to the code it names ────────────
# A registry entry names an EXACT source line. When that line is later reworded the entry stops resolving,
# and from then on it produces NO evidence - not a wrong verdict, which would argue with you, but silence,
# while keeping its seat in the batch and a stored TSV row that reads ANCHOR-MISS and therefore reads as
# handled. TD206 found one such entry eight days dead; the census that scoped this card found 16 across 9
# registries in a corpus 14 days old, so decay is a property of the corpus, not one stale line.
#
# The runner is NOT at fault and is not touched: mutate.ps1 already counts ANCHOR-MISS into its failure
# total. What was missing is anything that re-reads the corpus between the rare moments somebody happens to
# touch one registry, which is why the [GATE-MAP] row for specs/mutations/* pointed only at 17ac - a gate
# that tests the RUNNER's own seeded defects and never the corpus.
#
# TWO FUNCTIONS, and the split is the point. The decision core below is PURE: it takes entries already
# resolved to a boolean and never opens a file, so its examples are hermetic text (ADR 0011). The live
# wrapper does the I/O and resolves through the PRODUCTION matcher Get-MutatedVariant, reached via
# mutate.ps1 -AsLibrary. Re-deriving the matching rule here would let the gate and the runner disagree
# about what resolves, which is the exact failure class this gate exists to close.
#
# Ratchets in THREE directions, per ADR 0011, and the third was added by T235-MUT-ANCHOR-ORPHAN (TD209)
# after the first two had run for two days: an entry that decays without being listed FAILS; a listed entry
# that resolves again is reported STALE; and a listed KEY whose entry no longer exists at all is reported
# ORPHAN, so the list cannot outlive the work it excuses in either of the two ways it can.
# Rejected shapes, declared by name rather than left to a comment:
#   ignore-stale   - never report a stale exemption, which turns the ratchet into a permanent allowlist.
#   pending-blind  - ignore the pending list entirely, which leaves the gate permanently red and therefore
#                    permanently ignored; a gate nobody can make green is not a gate.
#   orphan-blind   - read the pending list only as a lookup and never as a SET to be validated, which is
#                    the shape both cores had before TD209 and which lets a retired entry's declaration sit
#                    forever, seen by nothing.
# Captured HERE, at script scope, and not read from $PSScriptRoot inside the function below. Measured while
# building this card: $PSScriptRoot is an automatic bound to the CURRENT script scope, so after a bare
# `. ./scripts/_guard.ps1` from a command line it is EMPTY inside a function, and `Split-Path -Parent ''`
# throws a binding error whose only visible effect is that the corpus is never found and the check returns
# zero findings. That is a fail-OPEN, and it is invisible: the sibling helpers at :52/:340/:377 read
# $PSScriptRoot the same way and get away with it only because their caller is selftest.ps1, which lives in
# this same directory. A card dod_command does not, and this gate is one.
$script:ScaffoldGuardDir = $PSScriptRoot

# ── T235-MUT-ANCHOR-ORPHAN (TD209): the direction of the ratchet that nothing watched ────────────────
# Both verdict cores in this file loop over ENTRIES and ask of each entry whether it is declared. That
# shape can only ever report facts about entries that EXIST. Neither asked the other question - of each
# declared KEY, does an entry still exist? - so a key naming an entry RETIRED from its registry keeps its
# seat forever and both gates stay green over it. That is the permanent-allowlist shape ADR 0011 exists to
# prevent, reached from the side neither STALE nor DECAY can see. Retiring is not hypothetical: TD207
# sanctions it in as many words and T216 and T219 have each done it, so the operation that mints an orphan
# is one this repo performs deliberately, in the same workflow whose gates could not see the result.
#
# The rule lives HERE once and both gates call it, so the two lists cannot drift about what an orphan is -
# two copies of one rule is the class T229 and T230 both shipped fixes for.
#
# CALLER CONTRACT, and it is load-bearing: $Entries must be the COMPLETE set for the keys in $Pending. A
# caller that could not enumerate some registry - an unparseable .psd1 - must name it in $Incomplete so its
# keys are withheld rather than called orphans. The [FIX] below says "delete the declaration", and a
# maintainer who follows that against a registry the gate merely failed to PARSE destroys a live debt row.
# A guard that can talk somebody into deleting evidence is worse than the silence it replaces.
function Get-ScaffoldMutationOrphanFindingsVia($Entries, $Pending, $Incomplete, $ListName, $Sentinel) {
  # The whole-corpus case of the same contract, and it is the one direction that is uniquely this rule's:
  # an EMPTY entry set with a NON-empty pending list. The loops above this rule iterate entries, so they
  # report nothing and cannot be wrong; this rule reads the list itself, so it would report EVERY key as an
  # orphan and tell a maintainer to delete every live declaration at once. Refuse to judge instead, and say
  # why. Neither real configuration reaches it - this repo has 74 registries, and a fresh downstream has an
  # EMPTY pending list, so the conjunction is false on both - which is exactly why it needs a declared
  # example rather than a comment.
  if (@($Entries).Count -eq 0 -and @($Pending).Count -gt 0) { return @("[MUT-ORPHAN-NO-CORPUS] $ListName declares $(@($Pending).Count) exemption(s) but the run found NO registry entries at all, so every one of them would read as an orphan at once. That is a corpus that failed to load, not a list that rotted, and the difference matters because this rule's [FIX] is to DELETE declarations. No key was judged. [FIX] check that specs/mutations/ is present and readable before trusting any orphan verdict.") }
  $live = @(@($Entries) | ForEach-Object { "$($_.Registry):$($_.Id)" })
  $unknown = @($Incomplete)
  $findings = @()
  foreach ($key in @($Pending)) {
    if ($live -contains $key) { continue }
    if ($unknown -contains ($key -split ':', 2)[0]) { continue }   # registry unreadable: withheld, not orphan
    $findings += "[$Sentinel] $key is declared on $ListName but no entry with that id exists under specs/mutations/ - a declaration whose entry was retired keeps its seat forever and no other arm of this ratchet can see it (STALE reads entries that resolve again, DECAY reads entries that stopped; neither reads a key with no entry), which is the permanent allowlist ADR 0011 exists to prevent. [FIX] remove '$key' from $ListName in scripts/_config.ps1; if the entry should still exist, restore it to its registry instead - never delete the declaration to reach green while the work it excuses is outstanding."
  }
  return $findings
}

function Get-ScaffoldMutationAnchorVerdictVia($Entries, $Pending, $Variant, $Incomplete) {
  $findings = @()
  $pend = @($Pending)
  foreach ($e in @($Entries)) {
    $key = "$($e.Registry):$($e.Id)"
    # pending-blind drops this one lookup, so the rejected shape is one deletable expression rather than a
    # whole branch - the examples below catch its removal in both directions.
    $isPending = if ($Variant -eq 'pending-blind') { $false } else { $pend -contains $key }
    if ($e.Resolves) {
      if ($isPending -and ($Variant -ne 'ignore-stale')) {
        $findings += "[MUT-ANCHOR-STALE] $key resolves again but is still on MutationAnchorPending - the list is a ratchet (ADR 0011) and an entry that outlives the work it excused turns the gate into a permanent allowlist. [FIX] remove '$key' from MutationAnchorPending in scripts/_config.ps1."
      }
      continue
    }
    if ($isPending) { continue }                        # declared outstanding, and the ratchet is what forces it
    $findings += "[MUT-ANCHOR-DECAY] $key no longer resolves in $($e.Target) ($($e.Why)) - the entry produces NO evidence, silently, and keeps its seat in the batch. [FIX] re-anchor it to the line that replaced the one it names, then re-run its batch; declare it in MutationAnchorPending only if the re-anchor is genuinely a separate piece of work."
  }
  # The third direction (TD209). Guarded against pending-blind too: a shape that ignores the pending list
  # entirely cannot report facts ABOUT that list either, so both rejected shapes drop this one line.
  if ($Variant -notin @('orphan-blind', 'pending-blind')) { $findings += @(Get-ScaffoldMutationOrphanFindingsVia $Entries $pend $Incomplete 'MutationAnchorPending' 'MUT-ANCHOR-ORPHAN') }
  return $findings
}

# The live decision. Reads every registry under $RegistryDir and resolves each entry's anchor through the
# production matcher. Never throws: an unreadable or unparseable registry is reported as a finding, because
# a registry the gate cannot read is a registry whose entries are not being checked either.
function Get-ScaffoldMutationAnchorIssue {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$RegistryDir,
    [string[]]$Pending = @(),
    [string]$RepoRoot = ''
  )
  # Dot-source FIRST, and never into a local named after one of its parameters. `. mutate.ps1 -AsLibrary`
  # binds that script's whole param() block into THIS scope - Registry, Only, Force, RunCommand, Results,
  # Root, AllowDirty, AsLibrary - and PowerShell variable names are case-insensitive, so its `[string]$Root
  # = ''` silently overwrites a local `$root`. Measured while building this card: the repo path went empty
  # mid-function, Join-Path threw a binding error on 'Path', and the function returned ZERO findings - a
  # fail-open that looked exactly like a clean corpus. Same class as the T74 $RepoRoot clobber. Hence the
  # deliberately non-colliding name below, and the ordering: nothing this function computes is allowed to
  # exist before the dot-source that could overwrite it.
  . (Join-Path $script:ScaffoldGuardDir 'mutate.ps1') -AsLibrary
  $repoBase = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent $script:ScaffoldGuardDir }
  $dir = if ([IO.Path]::IsPathRooted($RegistryDir)) { $RegistryDir } else { Join-Path $repoBase $RegistryDir }
  if (-not (Test-Path $dir)) { return @() }             # no corpus => nothing to bind; a fresh downstream
  $entries = @()
  $findings = @()
  $incomplete = @()                                    # registries this run could not enumerate (TD209)
  foreach ($reg in @(Get-ChildItem -Path $dir -Filter '*.psd1' -File | Sort-Object Name)) {
    $data = $null
    try { $data = Import-PowerShellDataFile -Path $reg.FullName } catch { $data = $null }
    if ($null -eq $data -or -not $data.ContainsKey('mutations')) {
      $findings += "[MUT-ANCHOR-UNREADABLE] $($reg.BaseName) could not be parsed as a mutation registry, so none of its entries were checked - an unchecked registry is indistinguishable from a clean one. [FIX] fix the .psd1, or delete it if it is no longer a registry."
      $incomplete += $reg.BaseName
      continue
    }
    foreach ($m in @($data.mutations)) {
      $h = @{}
      foreach ($k in $m.Keys) { $h[$k] = $m[$k] }
      $target = [string]$h.target
      $tp = Join-Path $repoBase $target
      if (-not (Test-Path $tp)) {
        $entries += [pscustomobject]@{ Registry = $reg.BaseName; Id = [string]$h.id; Target = $target; Resolves = $false; Why = 'target not found' }
        continue
      }
      $resolved = $null
      try { $resolved = Get-MutatedVariant ([IO.File]::ReadAllText($tp)) $h } catch { $resolved = $null }
      $entries += [pscustomobject]@{ Registry = $reg.BaseName; Id = [string]$h.id; Target = $target; Resolves = ($null -ne $resolved); Why = 'anchor not found exactly once' }
    }
  }
  return @($findings) + @(Get-ScaffoldMutationAnchorVerdictVia $entries $Pending $null $incomplete)
}

# Declared examples for the anchor-binding decision, one per case CLASS. Returns findings as strings and
# never throws. Default = exercise the live core and expect the stated finding count; -Variant re-runs the
# SAME cases through a named rejected shape, which MUST produce at least one disagreement. Hermetic: every
# case is an in-memory entry, nothing is read from specs/ or scripts/.
function Test-ScaffoldMutationAnchorExamples {
  [CmdletBinding()]
  param([ValidateSet('ignore-stale', 'pending-blind', 'orphan-blind')][string]$Variant)
  $ok = [pscustomobject]@{ Registry = 'TREG'; Id = 'M1'; Target = 'scripts/x.ps1'; Resolves = $true; Why = '' }
  $dead = [pscustomobject]@{ Registry = 'TREG'; Id = 'M2'; Target = 'scripts/x.ps1'; Resolves = $false; Why = 'anchor not found exactly once' }
  $gone = [pscustomobject]@{ Registry = 'TREG'; Id = 'M3'; Target = 'scripts/deleted.ps1'; Resolves = $false; Why = 'target not found' }
  $cases = @(
    @{ what = 'entry resolves and is not pending - the healthy majority'; entries = @($ok); pending = @(); expect = 0; sentinel = $null }
    @{ what = 'entry decayed and not pending - the silence TD206 registered'; entries = @($dead); pending = @(); expect = 1; sentinel = 'MUT-ANCHOR-DECAY' }
    @{ what = 'entry decayed and declared pending - the ratchet is holding it'; entries = @($dead); pending = @('TREG:M2'); expect = 0; sentinel = $null }
    @{ what = 'entry resolves but is still pending - a stale exemption'; entries = @($ok); pending = @('TREG:M1'); expect = 1; sentinel = 'MUT-ANCHOR-STALE' }
    @{ what = 'target file gone entirely - same class as a reworded line, no evidence either way'; entries = @($gone); pending = @(); expect = 1; sentinel = 'MUT-ANCHOR-DECAY' }
    @{ what = 'pending is matched per ENTRY, not per registry - a sibling entry stays reported'; entries = @($ok, $dead); pending = @('TREG:M1'); expect = 2; sentinel = $null }
    @{ what = 'a declared key whose entry was RETIRED from its registry - the seat nothing could see (TD209)'; entries = @($ok); pending = @('TREG:GONE'); expect = 1; sentinel = 'MUT-ANCHOR-ORPHAN' }
    @{ what = 'a declared key in a registry this run could NOT parse - withheld, because the fix here is to delete a declaration'; entries = @($ok); pending = @('BROKEN:M1'); incomplete = @('BROKEN'); expect = 0; sentinel = $null }
    # One case covers BOTH gates here, because the rule under it is shared: 14p routes through the same
    # function, so a second copy of this case in the evidence table would assert the same thing twice.
    @{ what = 'declarations but NO corpus at all - refuse to judge rather than call every declaration an orphan'; entries = @(); pending = @('TREG:M1', 'TREG:M2'); expect = 1; sentinel = 'MUT-ORPHAN-NO-CORPUS' }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $inc = if ($c.ContainsKey('incomplete')) { $c.incomplete } else { @() }
    $got = @(Get-ScaffoldMutationAnchorVerdictVia $c.entries $c.pending $v $inc)
    if ($got.Count -ne $c.expect) { $findings += "[MUT-ANCHOR-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) (TD206/ADR 0011). [FIX] fix the predicate, never the example." }
    elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) { $findings += "[MUT-ANCHOR-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) sentinel - a finding that arrives from the wrong branch is not evidence the right one fired (L165). [FIX] fix the predicate, never the example." }
  }
  return $findings
}

# ── T220-MUT-EVIDENCE-COVERAGE (TD212): the registry's binding to the evidence it already recorded ─────
# 14o above watches ONE of the two channels an entry can decay in. It resolves the ANCHOR through the
# production matcher, so a reworded target line reds and the ratchet forces the entry onto a declared list.
# The mustFind TEXT channel has no such watcher: mutate.ps1 compares each literal against the captured gate
# output and writes the verdict into a results TSV, but nothing reads those rows again, and for a merged
# card the batch is never re-run. So an entry can hold a perfectly good anchor, make a claim nothing has
# proven, and read as healthy to every gate in this repo. TD212 registered the class after T219 measured
# T110 M4 - anchor byte-identical to T200 M5's, therefore absent from the sixteen TD207 enumerated, and a
# pristine-tree batch nonetheless recorded BAD-EVIDENCE for it.
#
# WHAT THIS GATE DOES NOT DO, measured rather than assumed. Three cheaper designs were tried against ground
# truth (T110 M4's known-dead literal) and all three failed:
#   naive source scan   - 234 of 515 mustFind literals do not occur in any script source (45 percent),
#                         because gate output is composed at runtime. Unusable as a signal.
#   wildcarded literals - parse string literals, widen each interpolation and match: VACUOUS. It explains
#                         M4's dead literal through 24 junk patterns, one of them a bare parenthesised
#                         wildcard derived from an ordinary interpolated string.
#   cacheKey staleness  - exact, but scripts/selftest.ps1 enters every key, so 259 of 270 stored rows are
#                         already stale. Permanently red is the 'pending-blind' shape T211 rejected by name.
# What survives is the comparison below: an entry's CURRENT mustFind against the evidence its OWN stored row
# recorded. Exact, hermetic, instant. It catches the entry edited without a re-run and the verdict already
# recorded but never read; it does NOT catch source reworded since the last batch, which stays on TD212.
#
# THREE FUNCTIONS, same split as 14o and for the same reason (ADR 0011): the verdict core is PURE - it takes
# entries already reduced to booleans and never opens a file, so its examples are hermetic text - the
# COVERAGE core below it is pure for the same reason, and the live wrapper does the I/O. Both cores compare
# against the literal form the RUNNER writes ('found:' plus the mustFind text, joined with '; '), never a
# re-derived rule, so gate and runner cannot disagree about what counts as covered.
#
# The coverage core was SPLIT OUT by TD219, and its absence is the whole story of that defect. While the
# comparison sat inline in the I/O wrapper, the example table below could not reach it - the table feeds the
# verdict core entries whose Uncovered list is ALREADY COMPUTED - so a substring test that accepted any
# PREFIX of the recorded evidence passed 17 gates and a 10-of-10 post-merge matrix. An untestable expression
# is where a too-permissive predicate hides; moving it is the fix, tightening it in place would not have been.
#
# Ratchets in THREE directions, the third added by T235-MUT-ANCHOR-ORPHAN (TD209): an entry whose evidence
# stops covering its claim FAILS unless declared; a declared entry that becomes covered again is reported
# STALE; and a declared KEY whose entry no longer exists at all is reported ORPHAN through the shared rule
# 14o calls, so the list cannot outlive the work it excuses in either of the two ways it can. This core was
# written after TD209 was registered against 14o and inherited its loop shape, which is why that row names
# only the anchor half - one predicate now serves both, so the two lists cannot drift about what an orphan is.
# An entry already excused on MutationAnchorPending is SKIPPED here - an unresolved anchor produces no
# evidence at all, so 14o already owns that debt and reporting it twice would make two gates argue about one.
# Rejected shapes, declared by name rather than left to a comment:
#   row-blind      - ignore whether a stored row exists, so an entry that has NEVER run reads as proven.
# The missing-row arm is deliberately SCOPED to registries that have already produced evidence. A registry
# whose TSV holds no rows at all is not decayed, it is new, and reddening there would be unsatisfiable by
# construction (L297): mutate.ps1 refuses any batch whose gate is already red, so the gate would forbid the
# very run that would clear it. Measured on this card - the first batch of its own registry hit exactly that.
#   verdict-blind  - accept any recorded verdict, so a stored BAD-EVIDENCE row keeps its seat in silence.
#   ignore-stale   - never report a stale exemption, which turns the ratchet into a permanent allowlist.
#   orphan-blind   - read the pending list only as a lookup and never as a SET to be validated, so a key
#                    whose entry was retired sits there forever, seen by nothing (TD209).
function Get-ScaffoldMutationEvidenceVerdictVia($Entries, $Pending, $AnchorPending, $Variant, $Incomplete) {
  $findings = @()
  $pend = @($Pending)
  $anchorPend = @($AnchorPending)
  foreach ($e in @($Entries)) {
    $key = "$($e.Registry):$($e.Id)"
    if ($anchorPend -contains $key) { continue }
    $isPending = $pend -contains $key
    # Each rejected shape drops ONE expression, so its removal is a single-line deletion the examples catch.
    $hasRow    = if ($Variant -eq 'row-blind')     { $true } else { [bool]$e.HasRow }
    $verdictOk = if ($Variant -eq 'verdict-blind') { $true } else { [bool]$e.VerdictOk }
    $uncovered = @($e.Uncovered)
    if ($hasRow -and $verdictOk -and $uncovered.Count -eq 0) {
      if ($isPending -and ($Variant -ne 'ignore-stale')) {
        $findings += "[MUT-EVIDENCE-STALE] $key is covered by its stored evidence again but is still on MutationEvidencePending - the list is a ratchet (ADR 0011) and an entry that outlives the work it excused turns the gate into a permanent allowlist. [FIX] remove '$key' from MutationEvidencePending in scripts/_config.ps1."
      }
      continue
    }
    if ($isPending) { continue }                        # declared on the evidence list; the ratchet forces it
    # A registry whose results TSV holds NO rows at all has never run: nothing has DECAYED yet, and this is
    # the arm that must not fire there. mutate.ps1 refuses to run any batch whose gate is already red
    # ([MUT-PRISTINE-RED]), so reddening on a never-run registry would make its FIRST batch unrunnable and
    # brick every future card that adds one - MEASURED on this card's own registry, which is how the trap
    # was found. The valuable half is kept: an entry added to a registry that HAS run is still reported,
    # which is the T202 N4 shape this gate exists for.
    #
    # TD219 RE-EXAMINED THIS SKIP AND IT STAYS, with the verdict recorded here rather than left to a reader.
    # The objection is real: a registry added and never run is invisible to 14p FOREVER, not just while it
    # is new. But reddening cannot be the answer while mutate.ps1 fail-closes on an already-red probe gate -
    # the gate would forbid the very batch that clears it, and the brick lands on every future card that
    # adds a registry, not on the one entry that earned it. MEASURED at the time of that verdict: all 74
    # registries under specs/mutations/ have a results TSV and 273 valid rows between them, so the class has
    # zero live instances. Its one COMPOUND hazard - a TSV whose only row is malformed, silently collapsing
    # a run registry into a never-run one - is closed above by [MUT-EVIDENCE-BADROW] instead, which reports
    # rather than reddens-by-omission. What would make this arm worth revisiting: a real instance, i.e. a
    # registry that sat unrun across more than one card. An announce line that prints zero forever is not
    # the fix; this repo already treats a zero-catch surface as a cost (T221).
    if (-not $hasRow -and -not [bool]$e.RegistryRan) { continue }
    if (-not $hasRow) {
      $findings += "[MUT-EVIDENCE-UNRUN] $key has no row in its registry's results TSV, so the mustFind claim it makes has never been proven by anything - never-run and proven are different states and only one of them is evidence. [FIX] run its batch with scripts/mutate.ps1 -Registry, or declare '$key' on MutationEvidencePending if that run is genuinely separate work."
      continue
    }
    if (-not $verdictOk) {
      $findings += "[MUT-EVIDENCE-VERDICT] $key has a stored row whose verdict is '$($e.Verdict)', not OK - the runner already recorded that this entry produced no usable evidence, and nothing has read that row since. [FIX] re-anchor or retire the entry and re-run its batch, or declare '$key' on MutationEvidencePending."
      continue
    }
    $findings += "[MUT-EVIDENCE-UNPROVEN] $key claims mustFind text its own stored evidence does not cover ($($uncovered -join ' | ')) - the registry was edited after its last batch, so the claim it makes today has never been proven, while the anchor still resolves and 14o still reads it healthy. [FIX] re-run its batch so the recorded evidence matches the claim, or declare '$key' on MutationEvidencePending."
  }
  # The third direction (TD209), decided by the SAME rule 14o calls. Note it reads $Entries, not the loop
  # above: an entry excused on MutationAnchorPending is skipped there but it EXISTS, so it is not an orphan.
  if ($Variant -ne 'orphan-blind') { $findings += @(Get-ScaffoldMutationOrphanFindingsVia $Entries $pend $Incomplete 'MutationEvidencePending' 'MUT-EVIDENCE-ORPHAN') }
  return $findings
}

# The COVERAGE decision (TD219), pure so its declared examples execute the very expression that was wrong.
# Takes the evidence string exactly as the results row stores it plus an entry's mustFind list, and returns
# the literals that row does NOT prove. Opens nothing.
#
# By ELEMENT, not by substring. mutate.ps1 joins its evidence with '; ' and writes each positive hit as
# 'found:<literal>', so a claim is covered only when it stands as a WHOLE element - the wrap below puts a
# delimiter on both sides and tests for that, which is the boundary condition a bare Contains lacks. The
# pre-TD219 form accepted any prefix of the recorded text: recorded `found:gate 10l seeded defect
# (T110/TD145 + T200/TD194)` proved a claim truncated at `(T110/TD145`, which is the T110 M4 decay shape
# mirrored - 14p reading healthy over the exact class it was built to catch.
#
# TAB normalisation mirrors the runner rather than re-deriving it: mutate.ps1 replaces TABs with spaces
# before writing the row (the TSV has no other choice), so the pattern is normalised the same way here.
#
# RESIDUAL, decided rather than discovered. The runner's format is LOSSY for a literal that itself contains
# '; ': `found:alpha; beta` reads identically whether the recorded literal was `alpha; beta` or the two
# elements `alpha` and `beta`. Matching with delimiters resolves that ambiguity toward COVERED, so no entry
# can be pinned into a red that re-running its batch cannot clear - the unsatisfiable-gate trap L297
# measured. Splitting on '; ' instead would be strictly worse: same ambiguity, plus a permanent false red on
# the multi-element literal. Zero of the 524 mustFind literals in the current corpus contain '; ' or a TAB,
# so both branches buy nothing today and cost nothing; they are here because deciding them later, under a
# red, is how the unsatisfiable case gets discovered the expensive way.
# Rejected shape, declared by name rather than left to a comment:
#   substring-match - the pre-TD219 predicate, which calls any PREFIX of the recorded evidence covered.
function Get-ScaffoldMutationEvidenceCoverageVia($Evidence, $MustFind, $Variant) {
  $ev = [string]$Evidence
  $delimited = '; ' + $ev + '; '
  $uncovered = @()
  foreach ($p in @($MustFind)) {
    $want = 'found:' + ([string]$p).Replace("`t", ' ')
    # ONE expression separates the shapes, so the rejected one is a single-line deletion the examples catch.
    $hit = if ($Variant -eq 'substring-match') { $ev.Contains($want) } else { $delimited.Contains('; ' + $want + '; ') }
    if (-not $hit) { $uncovered += [string]$p }
  }
  return $uncovered
}

# The live decision. Reads every registry under $RegistryDir together with the results TSV the runner wrote
# beside it. Never throws: an unreadable registry is reported as a finding, because a registry the gate
# cannot read is a registry whose entries are not being checked either. An entry carrying no `mustFind` at
# all (regex-only evidence) is still judged on row presence and verdict - there is simply no literal to
# cover, and inventing one here would be re-deriving the runner's rule.
function Get-ScaffoldMutationEvidenceIssue {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$RegistryDir,
    [string[]]$Pending = @(),
    [string[]]$AnchorPending = @(),
    [string]$RepoRoot = ''
  )
  $repoBase = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent $script:ScaffoldGuardDir }
  $dir = if ([IO.Path]::IsPathRooted($RegistryDir)) { $RegistryDir } else { Join-Path $repoBase $RegistryDir }
  if (-not (Test-Path $dir)) { return @() }             # no corpus => nothing to bind; a fresh downstream
  $entries = @()
  $findings = @()
  $incomplete = @()                                    # registries this run could not enumerate (TD209)
  foreach ($reg in @(Get-ChildItem -Path $dir -Filter '*.psd1' -File | Sort-Object Name)) {
    $data = $null
    try { $data = Import-PowerShellDataFile -Path $reg.FullName } catch { $data = $null }
    if ($null -eq $data -or -not $data.ContainsKey('mutations')) {
      $findings += "[MUT-EVIDENCE-UNREADABLE] $($reg.BaseName) could not be parsed as a mutation registry, so none of its entries were checked - an unchecked registry is indistinguishable from a clean one. [FIX] fix the .psd1, or delete it if it is no longer a registry."
      $incomplete += $reg.BaseName
      continue
    }
    # The results TSV the runner writes beside the registry, same seven columns it emits:
    # id, verdict, exit, seconds, evidence, cacheKey, at. A row that is not one this runner writes is
    # REPORTED, never quietly skipped (TD219): a silent drop makes the entry look never-run, and if it is
    # the registry's ONLY row the whole registry looks never-run - which the arm below then excuses
    # forever. The rule is mutate.ps1's own (exactly seven columns, a verdict in its known set, a SHA256
    # cacheKey), read off the runner rather than re-derived, so gate and runner agree on what a row IS.
    $rows = @{}
    $tsv = Join-Path $reg.DirectoryName ($reg.BaseName + '-results.tsv')
    if (Test-Path $tsv) {
      foreach ($line in @(Get-Content -Path $tsv -ErrorAction SilentlyContinue | Select-Object -Skip 1)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $c = $line -split "`t"
        # The report quotes the RAW LINE, never a parsed cell, and that is load-bearing rather than stylistic.
        # Under Set-StrictMode -Version Latest - which selftest.ps1 sets and this file is dot-sourced into -
        # indexing past the end of an array THROWS. The condition below is safe because -or short-circuits on
        # the column count (measured, not assumed), but a failure MESSAGE interpolating $c[1] is evaluated
        # unconditionally, so a one-column line would have crashed a guard whose whole contract is that it
        # never throws - reporting a malformed row as a stack trace instead of a finding (L167). Quoting the
        # raw line also keeps the text honest when the row is malformed some other way (L97: never hardcode
        # one cause into failure text; report from live data).
        if (($c.Count -ne 7) -or (-not $c[0]) -or ($c[1] -notin @('OK', 'SURVIVED', 'BAD-EVIDENCE', 'ANCHOR-MISS', 'RESTORE-FAIL')) -or ($c[5] -notmatch '^[0-9A-Fa-f]{64}$')) {
          $findings += "[MUT-EVIDENCE-BADROW] $($reg.BaseName) holds a results row that is not a row mutate.ps1 writes - a row needs exactly seven tab-separated columns, a verdict in its known set and a SHA256 cacheKey; this one has $($c.Count) column(s): $($line.Substring(0, [Math]::Min(100, $line.Length))) - so whatever entry it belongs to is judged as if it had never run, and if it is the only row in this TSV the whole registry reads never-run and is excused forever. [FIX] re-run its batch with scripts/mutate.ps1 -Registry, which drops malformed rows itself, or delete the row."
          continue
        }
        $rows[$c[0]] = @{ verdict = $c[1]; evidence = $c[4] }
      }
    }
    foreach ($m in @($data.mutations)) {
      $h = @{}
      foreach ($k in $m.Keys) { $h[$k] = $m[$k] }
      $id = [string]$h.id
      $hasRow = $rows.ContainsKey($id)
      $verdict = if ($hasRow) { [string]$rows[$id].verdict } else { '' }
      $ev = if ($hasRow) { [string]$rows[$id].evidence } else { '' }
      $uncovered = @()
      # The comparison itself is the pure core above, not an expression inlined here - TD219 is what an
      # inlined one costs, and no declared example could reach it while it lived at this spot.
      if ($hasRow -and $h.ContainsKey('mustFind')) { $uncovered = @(Get-ScaffoldMutationEvidenceCoverageVia $ev $h.mustFind $null) }
      $entries += [pscustomobject]@{
        Registry = $reg.BaseName; Id = $id; HasRow = $hasRow; RegistryRan = ($rows.Count -gt 0)
        Verdict = $verdict; VerdictOk = ($verdict -eq 'OK'); Uncovered = $uncovered
      }
    }
  }
  return @($findings) + @(Get-ScaffoldMutationEvidenceVerdictVia $entries $Pending $AnchorPending $null $incomplete)
}

# Declared examples for the evidence-coverage decision, one per case CLASS. Returns findings as strings and
# never throws. Default = exercise the live core and expect the stated finding count; -Variant re-runs the
# SAME cases through a named rejected shape, which MUST produce at least one disagreement. Hermetic: every
# case is an in-memory entry, nothing is read from specs/ or scripts/.
function Test-ScaffoldMutationEvidenceExamples {
  [CmdletBinding()]
  param([ValidateSet('row-blind', 'verdict-blind', 'ignore-stale', 'orphan-blind')][string]$Variant)
  $mk = {
    param($EntryId, $Row, $Verdict, $Uncovered, $Ran = $true)
    [pscustomobject]@{ Registry = 'TREG'; Id = $EntryId; HasRow = $Row; Verdict = $Verdict
                       VerdictOk = ($Verdict -eq 'OK'); Uncovered = @($Uncovered); RegistryRan = $Ran }
  }
  $ok       = & $mk 'M1' $true  'OK'           @()
  $unproven = & $mk 'M2' $true  'OK'           @('below the floor of 20')
  $unrun    = & $mk 'M3' $false ''             @()
  $badverd  = & $mk 'M4' $true  'BAD-EVIDENCE' @()
  $fresh    = & $mk 'M5' $false ''             @() $false
  $cases = @(
    @{ what = 'stored evidence covers every mustFind - the healthy majority'; entries = @($ok); pending = @(); anchor = @(); expect = 0; sentinel = $null }
    @{ what = 'registry edited after its last batch, so a literal is uncovered - the T202 N4 shape TD212 measured'; entries = @($unproven); pending = @(); anchor = @(); expect = 1; sentinel = 'MUT-EVIDENCE-UNPROVEN' }
    @{ what = 'uncovered and declared pending - the ratchet is holding it'; entries = @($unproven); pending = @('TREG:M2'); anchor = @(); expect = 0; sentinel = $null }
    @{ what = 'covered again but still pending - a stale exemption'; entries = @($ok); pending = @('TREG:M1'); anchor = @(); expect = 1; sentinel = 'MUT-EVIDENCE-STALE' }
    @{ what = 'no stored row at all - never run is not the same state as proven'; entries = @($unrun); pending = @(); anchor = @(); expect = 1; sentinel = 'MUT-EVIDENCE-UNRUN' }
    @{ what = 'a stored row the runner already marked BAD-EVIDENCE, unread by any gate ever since'; entries = @($badverd); pending = @(); anchor = @(); expect = 1; sentinel = 'MUT-EVIDENCE-VERDICT' }
    @{ what = 'anchor already excused on MutationAnchorPending - 14o owns it, and two gates must not argue about one debt'; entries = @($unproven); pending = @(); anchor = @('TREG:M2'); expect = 0; sentinel = $null }
    @{ what = 'a brand-new registry whose first batch has not run yet - nothing has decayed, and reddening here would make that very batch unrunnable'; entries = @($fresh); pending = @(); anchor = @(); expect = 0; sentinel = $null }
    @{ what = 'pending is matched per ENTRY, not per registry - a sibling entry stays reported'; entries = @($ok, $unproven); pending = @('TREG:M1'); anchor = @(); expect = 2; sentinel = $null }
    @{ what = 'a declared key whose entry was RETIRED from its registry - the seat nothing could see (TD209)'; entries = @($ok); pending = @('TREG:GONE'); anchor = @(); expect = 1; sentinel = 'MUT-EVIDENCE-ORPHAN' }
    @{ what = 'a declared key in a registry this run could NOT parse - withheld, because the fix here is to delete a declaration'; entries = @($ok); pending = @('BROKEN:M1'); anchor = @(); incomplete = @('BROKEN'); expect = 0; sentinel = $null }
    @{ what = 'an orphan key whose entry is also anchor-excused is still an orphan - the anchor skip is about entries that EXIST'; entries = @($ok); pending = @('TREG:GONE'); anchor = @('TREG:GONE'); expect = 1; sentinel = 'MUT-EVIDENCE-ORPHAN' }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $inc = if ($c.ContainsKey('incomplete')) { $c.incomplete } else { @() }
    $got = @(Get-ScaffoldMutationEvidenceVerdictVia $c.entries $c.pending $c.anchor $v $inc)
    if ($got.Count -ne $c.expect) { $findings += "[MUT-EVIDENCE-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) (TD212/ADR 0011). [FIX] fix the predicate, never the example." }
    elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) { $findings += "[MUT-EVIDENCE-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) sentinel - a finding that arrives from the wrong branch is not evidence the right one fired (L165). [FIX] fix the predicate, never the example." }
  }
  return $findings
}

# Declared examples for the COVERAGE decision, one per case CLASS (TD219). Same contract as the verdict
# table above: findings as strings, never throws, hermetic - every case is a literal evidence string and a
# literal claim, nothing is read from specs/ or scripts/. Default = exercise the live core; -Variant re-runs
# the SAME cases through the rejected shape, which MUST disagree at least once, or the table has stopped
# discriminating a shape it must and the old predicate could be merged back in unnoticed.
#
# The first two rows are the DEMONSTRATED defect, kept verbatim from the corpus text that exposed it rather
# than reduced to `found:AB` vs `A` - a synthetic pair proves the same logic but loses the reason anyone
# should care, which is that this exact shape occurs in this exact repo.
function Test-ScaffoldMutationEvidenceCoverageExamples {
  [CmdletBinding()]
  param([ValidateSet('substring-match')][string]$Variant)
  $real = 'found:gate 10l seeded defect (T110/TD145 + T200/TD194)'
  $cases = @(
    @{ what = 'the claim IS the recorded literal - the healthy majority'; ev = $real; find = @('gate 10l seeded defect (T110/TD145 + T200/TD194)'); expect = @() }
    @{ what = 'the claim is a PREFIX of the recorded literal - the TD219 false positive, on the text that demonstrated it'; ev = $real; find = @('gate 10l seeded defect (T110/TD145'); expect = @('gate 10l seeded defect (T110/TD145') }
    @{ what = 'the claim is a SUFFIX of the recorded literal'; ev = 'found:[SELFTEST-ONLY-FAIL] gates=14'; find = @('gates=14'); expect = @('gates=14') }
    @{ what = 'a mid-field claim that is a prefix of a LATER element - the boundary a bare Contains cannot see'; ev = 'found:alpha; found:beta gamma'; find = @('beta'); expect = @('beta') }
    @{ what = 'every real element of a multi-element row is covered, first and last alike'; ev = 'found:alpha; found:beta gamma'; find = @('alpha', 'beta gamma'); expect = @() }
    @{ what = 'a MISS-find element is not coverage - the runner records a miss in the same field'; ev = 'MISS-find:alpha'; find = @('alpha'); expect = @('alpha') }
    @{ what = 'an absent:<literal> element is the mustNotFind channel and proves nothing about a mustFind'; ev = 'absent:alpha'; find = @('alpha'); expect = @('alpha') }
    @{ what = 'a TAB-bearing literal, recorded space-normalised because the runner replaces TABs before writing the row'; ev = "found:alpha beta"; find = @("alpha`tbeta"); expect = @() }
    @{ what = 'a literal containing the delimiter itself, recorded as ONE element - the lossy case resolved toward covered so no entry is pinned into an unclearable red (L297)'; ev = 'found:alpha; beta'; find = @('alpha; beta'); expect = @() }
    @{ what = 'empty evidence covers nothing'; ev = ''; find = @('alpha'); expect = @('alpha') }
    @{ what = 'an entry with no mustFind at all - regex-only evidence, nothing to cover'; ev = $real; find = @(); expect = @() }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(Get-ScaffoldMutationEvidenceCoverageVia $c.ev $c.find $v)
    $want = @($c.expect)
    if (($got -join '|') -ne ($want -join '|')) {
      $findings += "[MUT-EVIDENCE-COVER-EXAMPLE] case '$($c.what)' reported uncovered=[$($got -join ', ')], expected [$($want -join ', ')] (TD219). [FIX] fix the predicate, never the example."
    }
  }
  return $findings
}

# Declared examples for the parts of 14p that only exist on the LIVE path - the results-row parse, and the
# fact that the wrapper actually calls the coverage core. The two tables above are pure-input tables and
# cannot reach either, so this one drives the real Get-ScaffoldMutationEvidenceIssue against a throwaway
# registry built under the temp dir. Hermetic in the sense the other fixture-driven gates in this repo use:
# nothing under specs/ or scripts/ is read or written, the directory is unique per run and removed in a
# finally, and no network or git is involved. Returns findings as strings and never throws.
#
# TWO THINGS ARE PINNED HERE, and both were review findings against the first cut of this card rather than
# foresight - recorded that way because a comment claiming foresight would be a lie a later reader acts on:
#   * [MUT-EVIDENCE-BADROW] had NO behavioural test. The card asserted it by grepping its own source for the
#     sentinel, which proves the string exists and nothing else, and its branch is unkillable by any
#     single-line deletion on a corpus with zero malformed rows. Case 1 is also the exact shape that made an
#     earlier cut of that report CRASH: under StrictMode a one-column row has no $c[1] to interpolate.
#   * The WIRING mutation (registry N2, which deletes the wrapper's call to the coverage core) was killed
#     only because a specific entry happened to sit on MutationEvidencePending, so it read covered-again and
#     reported STALE. That kill is corpus state, not a property of the code: draining that pending entry -
#     which is planned work - would make N2 survive silently, and its cache key does not hash
#     scripts/_config.ps1, so the stale OK row would be REUSED and the corpus would keep saying "proven".
#     Case 5 replaces it with a kill that cannot decay: a claim that is a PREFIX of what its own row
#     recorded, judged through the live path, which is exactly the TD219 class end to end.
function Test-ScaffoldMutationEvidenceRowExamples {
  [CmdletBinding()]
  param()
  $sha = '0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF'
  $hdr = "id`tverdict`texit`tseconds`tevidence`tcacheKey`tat"
  $cases = @(
    @{ what  = 'a one-column row - not a row mutate.ps1 writes, and the shape that has no cell to quote back'
       claim = 'alpha'; rows = @('garbage-single-column'); expect = 1; sentinel = 'MUT-EVIDENCE-BADROW' }
    @{ what  = 'seven columns but a cacheKey that is not SHA256 - the runner would drop this row, so the gate must not trust it'
       claim = 'alpha'; rows = @("A1`tOK`t1`t0.1`tfound:alpha`tnot-a-sha`t2026-01-01"); expect = 1; sentinel = 'MUT-EVIDENCE-BADROW' }
    @{ what  = 'seven columns but a verdict outside the runner known set'
       claim = 'alpha'; rows = @("A1`tWEIRD`t1`t0.1`tfound:alpha`t$sha`t2026-01-01"); expect = 1; sentinel = 'MUT-EVIDENCE-BADROW' }
    @{ what  = 'a registry whose ONLY row is malformed reds rather than collapsing into a never-run registry excused forever - the compound hazard the never-run skip would otherwise hide'
       claim = 'alpha'; rows = @('garbage-single-column'); expect = 1; sentinel = 'MUT-EVIDENCE-BADROW' }
    @{ what  = 'a claim that is a PREFIX of what its own row recorded, judged through the LIVE path - the TD219 class end to end, and the arm that proves the wrapper calls the coverage core at all'
       claim = 'alpha beta'; rows = @("A1`tOK`t1`t0.1`tfound:alpha beta gamma`t$sha`t2026-01-01"); expect = 1; sentinel = 'MUT-EVIDENCE-UNPROVEN' }
    @{ what  = 'a well-formed row whose evidence covers the claim exactly - the control, without which every case above passes for a gate that simply always reports'
       claim = 'alpha'; rows = @("A1`tOK`t1`t0.1`tfound:alpha`t$sha`t2026-01-01"); expect = 0; sentinel = $null }
  )
  $findings = @()
  $fx = Join-Path ([System.IO.Path]::GetTempPath()) ('mutevrow-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
  try {
    New-Item -ItemType Directory -Force $fx | Out-Null
    foreach ($c in $cases) {
      Set-Content -Path (Join-Path $fx 'FXREG.psd1') -Encoding utf8 `
        -Value ("@{ mutations = @( @{ id = 'A1'; target = 'x'; anchor = 'y'; gate = '1'; mustFind = @('" + $c.claim + "') } ) }")
      Set-Content -Path (Join-Path $fx 'FXREG-results.tsv') -Encoding utf8 -Value (@($hdr) + @($c.rows) -join "`n")
      $got = @(Get-ScaffoldMutationEvidenceIssue -RegistryDir $fx -Pending @() -AnchorPending @())
      if ($got.Count -ne $c.expect) {
        $findings += "[MUT-EVIDENCE-ROW-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) (TD219). [FIX] fix the predicate, never the example."
      }
      elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) {
        $findings += "[MUT-EVIDENCE-ROW-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) sentinel - a finding that arrives from the wrong branch is not evidence the right one fired (L165). [FIX] fix the predicate, never the example."
      }
    }
  }
  catch {
    # The function under test promises never to throw; if anything here does, that IS the finding.
    $findings += "[MUT-EVIDENCE-ROW-EXAMPLE] the fixture itself threw, so nothing below it was judged: $($_.Exception.Message). [FIX] a guard that throws instead of reporting is the L167 failure this table exists to catch."
  }
  finally { if (Test-Path $fx) { Remove-Item $fx -Recurse -Force -ErrorAction SilentlyContinue } }
  return $findings
}

# -- T231-CARD-BUDGET-CORE (TD235): is this card's live diff inside the budget the card declared? --
#
# The problem this answers is a WINDOW, not a number. A card's size is estimated once, by a planner,
# before a line of code exists, and is then never measured again until the diff reaches review - so
# growth is invisible during exactly the stretch in which splitting is still cheap.
#
# The repayment is deliberately NOT a lower ceiling. A fixed ceiling of N makes N-1 a pass, and the work
# then optimises for fitting under the limit rather than for being reviewable; moving the ceiling moves
# the target without removing it. What is judged here is a card against ITS OWN declared budget, and the
# interesting event is the TRIP - crossing a fraction of that budget while splitting is still cheap -
# not the ceiling.
#
# Two properties are load-bearing and both live in this core rather than in a caller, because a caller
# that gets either wrong makes the whole mechanism decorative:
#
#   1. THE BUDGET IS READ FROM THE BASE CARD. If a card could raise its own budget inside the diff the
#      budget is judging, the budget judges nothing. allow_paths already solved this the same way - the
#      ship scope gate reads allow_paths from the base card, so widening it in-branch is inert by
#      design (_cards.ps1). Both texts are taken as parameters so the binding is a property of the
#      decision and not of whichever entry point happens to call it.
#   2. AN ABSENT budget NEVER BLOCKS. Missing or empty => state 'no-budget': report the raw count and
#      trip on nothing. This is the "empty means off, never broken" degradation FrozenPaths / DocSyncMap
#      / DocBudgets already give a freshly initialised downstream - and here it is also what keeps a card
#      that predates the field shippable, which matters because cards are in flight continuously.
#
# Raising a budget is ALLOWED and is meant to be visible, not forbidden. Because the base card is what is
# read, a raise has to land on the base branch as its own commit, with its reason in that commit - the
# rule Test-ScaffoldDocBudget already states for DocBudgets ("raising the ceiling is allowed but requires
# a stated reason in the PR that raises it"), reused rather than reinvented. There is deliberately no cap
# on what a budget may be set to: a cap is the fixed ceiling this whole mechanism exists to remove.
#
# Pure by construction: card TEXT in, a decision object out. No git, no IO, no config read - the two
# constants arrive as parameters so the examples table can drive boundaries the live config does not hold.

function Get-ScaffoldCardBudgetValue {
  <#
  .SYNOPSIS  Read the budget key out of a card's front matter. Returns $null when absent, unparseable, or
             commented out; otherwise the declared integer (which may be 0 or negative - judging the
             VALUE is a check-cards concern, T233, not this reader's).
  .DESCRIPTION
    Anchored to a line-start key so a mention inside sweep:/notes: prose is not read as the declaration.
    A leading '#' is a comment and reads as absent, which is what lets the template ship the key
    commented out without every card inheriting a budget it never chose.
  #>
  [CmdletBinding()]
  param([string]$CardText)
  if ([string]::IsNullOrWhiteSpace($CardText)) { return $null }
  foreach ($ln in ($CardText -split "`r?`n")) {
    if ($ln -match '^\s*#') { continue }
    if ($ln -match '^budget\s*:\s*([^#\r\n]*)') {
      $raw = $Matches[1].Trim().Trim('"').Trim("'")
      if ($raw -eq '') { return $null }
      $n = 0
      if ([int]::TryParse($raw, [ref]$n)) { return $n }
      return $null
    }
  }
  return $null
}

function Get-ScaffoldCardBudgetDecision {
  <#
  .SYNOPSIS  T231/TD235: judge a card's live diff against the budget its BASE card declares.
  .DESCRIPTION
    Returns one object, never throws, and never blocks on its own - the caller decides what a state means.

      State       meaning
      ----------  --------------------------------------------------------------------------------
      no-budget   neither the base card nor the default declares one. Report the count, trip nothing.
      under       inside the trip fraction. Nothing to say.
      trip        at or past the trip fraction and inside the budget. THIS is the interesting one:
                  splitting is still cheap here, which is the entire point of tripping early.
      over        past the declared budget.

    BranchCardText is accepted and deliberately IGNORED for the budget. It is a parameter so that the
    base-card binding is visible at the call site and testable in the examples table (see the
    'branch-side-budget' variant), rather than being an unstated convention a second entry point can
    quietly get wrong.
  #>
  [CmdletBinding()]
  param(
    [string]$BaseCardText,
    [string]$BranchCardText,
    [int]$ChangedLines = 0,
    [int]$DefaultBudget = 0,
    [double]$TripFraction = 0.6,
    [ValidateSet('branch-side-budget', 'no-trip', 'absent-blocks')][string]$Variant
  )

  # The one line the 'branch-side-budget' variant flips. Everything below is shared, so the variant
  # cannot accidentally exercise a different code path than the real decision does.
  $declared = if ($Variant -eq 'branch-side-budget') {
    Get-ScaffoldCardBudgetValue -CardText $BranchCardText
  }
  else {
    Get-ScaffoldCardBudgetValue -CardText $BaseCardText
  }

  $source = 'card'
  if ($null -eq $declared) {
    if ($DefaultBudget -gt 0) { $declared = $DefaultBudget; $source = 'default' }
    else { $source = 'none' }
  }

  if ($source -eq 'none') {
    # Absent budget AND no default => report only. The 'absent-blocks' variant is the rejected shape: it
    # exists so the examples table can PROVE this degradation rather than merely intend it.
    $noneState = if ($Variant -eq 'absent-blocks') { 'over' } else { 'no-budget' }
    return [pscustomobject]@{
      State    = $noneState
      Budget   = 0
      Used     = $ChangedLines
      Fraction = 0.0
      TripAt   = 0
      Source   = $source
      Message  = "no declared budget - $ChangedLines changed lines, reporting only"
    }
  }

  $tripAt = [int][Math]::Ceiling($declared * $TripFraction)
  $fraction = if ($declared -gt 0) { [Math]::Round($ChangedLines / [double]$declared, 3) } else { 0.0 }

  $state =
  if ($ChangedLines -gt $declared) { 'over' }
  elseif ($Variant -eq 'no-trip') { 'under' }        # rejected shape: folds the trip band into 'under'
  elseif ($ChangedLines -ge $tripAt) { 'trip' }
  else { 'under' }

  # WHOSE number is this (TD246)? A 'trip' or 'over' means two different things depending on where the
  # budget came from, and only one of them is a merge block. `task.ps1`'s ship gate passes DefaultBudget 0,
  # so a card declaring no key reaches 'no-budget' and ships without comment; the meter passes the
  # configured default so it can print a number for that same card at all. Both are right. Saying only
  # OVER collapses them and instructs the author to prevent a block that will not happen.
  # Written as an empty default plus an override rather than one conditional expression, so that deleting
  # the override degrades into the examples table's own finding - which says the note is missing - instead
  # of an unset-variable throw the table can only report as 'THREW' (L165: a guard is proved by the
  # assertion that names what broke).
  $srcNote = ''
  if ($source -eq 'default') { $srcNote = ' The number came from CardBudgetDefault, not from this card, and ship does NOT block a card that declares no budget - declare the key on the base card to make a number binding.' }

  $msg = switch ($state) {
    'over' { "$ChangedLines of $declared declared lines - OVER. Split the card, or raise the budget on the BASE card in its own commit with the reason in that commit. Raising it in-branch is inert: the base card is what is read.$srcNote" }
    'trip' { "$ChangedLines of $declared declared lines (trip at $tripAt) - record a decision NOW, while splitting is still cheap: split and name the cards, or raise the budget on the base card with a reason.$srcNote" }
    default { "$ChangedLines of $declared declared lines - under." }
  }

  return [pscustomobject]@{
    State    = $state
    Budget   = $declared
    Used     = $ChangedLines
    Fraction = $fraction
    TripAt   = $tripAt
    Source   = $source
    Message  = $msg
  }
}

function Test-ScaffoldCardBudgetExamples {
  <#
  .SYNOPSIS  Declared examples for the card-budget decision. Returns findings as strings; never throws.
  .DESCRIPTION
    Hermetic - every input is a literal, no git, no IO, no config. Three variants exist, and each one is
    a REJECTED SHAPE whose job is to make one load-bearing rule falsifiable:

      no-trip             folds the trip band into 'under'  => proves the trip band is exercised at all.
      absent-blocks       reports 'over' for a card with no budget => proves absence does not block.
      branch-side-budget  reads the budget from the BRANCH card => proves the base-card binding.

    Without them, the green table passes vacuously wherever the predicate is absent, which is the exact
    false-green this repo keeps paying for (L95, TD236). The caller asserts BOTH that this returns zero
    findings AND that each variant returns at least one.
  #>
  [CmdletBinding()]
  param([ValidateSet('branch-side-budget', 'no-trip', 'absent-blocks')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $findings = @()

  function Local-Card([string]$budgetLine) {
    $lines = @('---', 'id: TZ-EXAMPLE', 'status: todo')
    if ($budgetLine) { $lines += $budgetLine }
    $lines += @('allow_paths:', '  - scripts/x.ps1', '---', 'body mentioning budget: 9999 in prose')
    return ($lines -join "`n")
  }

  $c400 = Local-Card 'budget: 400'
  $cNone = Local-Card $null
  $c4000 = Local-Card 'budget: 4000'
  $cHash = Local-Card '# budget: 400'

  # State table. TripFraction 0.6 => a 400 budget trips at 240.
  $cases = @(
    @{ what = 'well under the trip fraction'; base = $c400; used = 100; def = 0; expect = 'under' }
    @{ what = 'one line below the trip point stays under'; base = $c400; used = 239; def = 0; expect = 'under' }
    @{ what = 'exactly on the trip point trips - the boundary is inclusive, so the meter speaks at the fraction rather than one line later'; base = $c400; used = 240; def = 0; expect = 'trip' }
    @{ what = 'inside the budget but past the trip point'; base = $c400; used = 399; def = 0; expect = 'trip' }
    @{ what = 'exactly on the budget is still not over - over means PAST it'; base = $c400; used = 400; def = 0; expect = 'trip' }
    @{ what = 'past the declared budget'; base = $c400; used = 401; def = 0; expect = 'over' }
    @{ what = 'no budget and no default reports only and never trips'; base = $cNone; used = 5000; def = 0; expect = 'no-budget' }
    @{ what = 'no budget but a configured default falls back to the default'; base = $cNone; used = 5000; def = 400; expect = 'over' }
    @{ what = 'a commented-out key reads as ABSENT, so the template can ship it commented'; base = $cHash; used = 5000; def = 0; expect = 'no-budget' }
    @{ what = 'a budget mentioned in BODY prose is not the declaration'; base = $cNone; used = 1; def = 0; expect = 'no-budget' }
  )
  foreach ($c in $cases) {
    try {
      $callArgs = @{ BaseCardText = $c.base; BranchCardText = $c.base; ChangedLines = $c.used; DefaultBudget = $c.def; TripFraction = 0.6 }
      if ($useVariant) { $callArgs['Variant'] = $Variant }
      $got = Get-ScaffoldCardBudgetDecision @callArgs
      if ($got.State -ne $c.expect) {
        $findings += "[CARD-BUDGET-EXAMPLE] case '$($c.what)' judged '$($got.State)', expected '$($c.expect)'. [FIX] fix the decision, never the example."
      }
    }
    catch {
      $findings += "[CARD-BUDGET-EXAMPLE] case '$($c.what)' THREW: $($_.Exception.Message). A decision core that throws instead of returning a state is the L167 failure this table exists to catch."
    }
  }

  # The base-card binding, stated as its own case because it is the property a second entry point is
  # most likely to get wrong: base says 400, branch says 4000, and 800 lines are spent. Reading the base
  # (correct) is 'over'; reading the branch is 'under', which is a card that raised its own ceiling
  # inside the diff being judged.
  try {
    $callArgs = @{ BaseCardText = $c400; BranchCardText = $c4000; ChangedLines = 800; DefaultBudget = 0; TripFraction = 0.6 }
    if ($useVariant) { $callArgs['Variant'] = $Variant }
    $bind = Get-ScaffoldCardBudgetDecision @callArgs
    if ($bind.State -ne 'over' -or $bind.Budget -ne 400) {
      $findings += "[CARD-BUDGET-EXAMPLE] the budget was taken from the BRANCH card (state '$($bind.State)', budget $($bind.Budget)) instead of the base card's 400. Widening in-branch must be inert or the budget judges nothing - this is the allow_paths binding, restated."
    }
  }
  catch {
    $findings += "[CARD-BUDGET-EXAMPLE] the base-card binding case THREW: $($_.Exception.Message)."
  }

  # WHOSE number (T240/TD246), asserted as a PAIR because one half alone passes on an unconditional
  # clause. The two decisions below reach the same State from different sources, so nothing in the state
  # table above can tell them apart - and only one of them is a merge block, since ship judges a card
  # with no budget at DefaultBudget 0 and never blocks it.
  try {
    $dfltArgs = @{ BaseCardText = $cNone; BranchCardText = $cNone; ChangedLines = 5000; DefaultBudget = 400; TripFraction = 0.6 }
    $cardArgs = @{ BaseCardText = $c400; BranchCardText = $c400; ChangedLines = 5000; DefaultBudget = 400; TripFraction = 0.6 }
    if ($useVariant) { $dfltArgs['Variant'] = $Variant; $cardArgs['Variant'] = $Variant }
    $dflt = Get-ScaffoldCardBudgetDecision @dfltArgs
    $card = Get-ScaffoldCardBudgetDecision @cardArgs
    if ($dflt.Source -ne 'default' -or $dflt.Message -notmatch 'CardBudgetDefault') {
      $findings += "[CARD-BUDGET-EXAMPLE] an overrun measured against the CONFIGURED DEFAULT (source '$($dflt.Source)') does not name CardBudgetDefault in its message, so the meter reads as a block the author must act on while ship will not block at all."
    }
    if ($card.Source -ne 'card' -or $card.Message -match 'CardBudgetDefault') {
      $findings += "[CARD-BUDGET-EXAMPLE] an overrun against a CARD-DECLARED budget (source '$($card.Source)') names CardBudgetDefault, so the note is unconditional and says nothing - the whole point is that it distinguishes the two sources."
    }
  }
  catch {
    $findings += "[CARD-BUDGET-EXAMPLE] the budget-source case THREW: $($_.Exception.Message)."
  }

  # The reader, judged separately from the state machine so a reader bug cannot hide behind a state bug.
  $readCases = @(
    @{ what = 'a declared integer is read'; text = $c400; expect = 400 }
    @{ what = 'an absent key reads as null'; text = $cNone; expect = $null }
    @{ what = 'a commented key reads as null'; text = $cHash; expect = $null }
    @{ what = 'a non-numeric value reads as null rather than throwing'; text = (Local-Card 'budget: soon'); expect = $null }
    @{ what = 'an empty value reads as null'; text = (Local-Card 'budget:'); expect = $null }
    @{ what = 'a trailing comment does not corrupt the number'; text = (Local-Card 'budget: 250   # this card own budget'); expect = 250 }
    @{ what = 'zero is READ as zero - rejecting it is check-cards work (T233), not the reader s'; text = (Local-Card 'budget: 0'); expect = 0 }
  )
  foreach ($c in $readCases) {
    try {
      $got = Get-ScaffoldCardBudgetValue -CardText $c.text
      if ($got -ne $c.expect) {
        $findings += "[CARD-BUDGET-EXAMPLE] reader case '$($c.what)' returned '$got', expected '$($c.expect)'. [FIX] fix the reader, never the example."
      }
    }
    catch {
      $findings += "[CARD-BUDGET-EXAMPLE] reader case '$($c.what)' THREW: $($_.Exception.Message)."
    }
  }

  return $findings
}

# -- T259-ERRTEXT-WRAP-FOLD (TD258): reading a phrase out of text a CHILD process threw --
# A gate that spawns `pwsh -File ...` and asserts a multi-word phrase in the output is reading a NATIVE
# process stderr. The child renders the ErrorRecord itself and wraps it at ITS OWN console width - 80 on
# the ubuntu runner, wider on windows - and each wrapped body line opens with the error-view continuation
# prefix (spaces, a pipe, a space). So the phrase a gate asks for can be split across a line break the
# gate never chose and cannot control, and the same assertion then passes on one OS and fails on the other.
# MEASURED, not reasoned: master was red on `ubuntu-latest, e2e` for three consecutive commits (b61d4a3,
# 7b612a8, 63115e8), every one at 15d4(c), every one green on windows for the same commit, with the break
# falling between `commit` and the seeded card's path. The message itself was correct throughout - only
# its physical layout differed - so the defect was in the reading, never in what task.ps1 prints.
# THE FOLD IS NARROW ON PURPOSE. It joins a line to the previous one ONLY where that line opens with the
# continuation prefix, which is exactly what reconstructs one logical message. Collapsing every newline
# would be shorter and would let a phrase be satisfied by two unrelated output lines - a false NEGATIVE in
# a gate whose whole job is strictness - and that is the `join-all` rejected shape below.
# THE INDENT IS PART OF THE PREFIX, NOT DECORATION (T261/TD260). The error view indents every continuation
# line before the pipe, so the prefix is <whitespace><pipe>, and the whitespace is REQUIRED. Accepting a
# bare pipe at column zero folds ordinary text that legitimately opens with one - a markdown table row, a
# `git log --graph` line - into its predecessor, which is the same false-negative direction as `join-all`,
# just reached through a narrower door. That is the `no-indent` rejected shape below; it was the LIVE
# regex until T261, and no case in the table reached it, which is why neither the examples nor the
# mutation batch could see it.
# SINGLE TOKENS NEED NONE OF THIS: a sentinel carrying no whitespace cannot be split by a wrap, which is
# why [SHIP-SCOPE-CARD-ABSENT] matched on the very run whose phrase arm failed. Only phrase assertions
# against THROWN text need folding; Write-Host output is not error-view formatted and is never wrapped,
# which is why the [SAGA-*] arms in the same gate passed on both runners throughout.
# Returns the text with continuation lines folded; ordinary lines and line structure are left alone.
# Never throws, and an empty or newline-free input is returned unchanged.
# Rejected shapes, declared by name rather than left to a comment:
#   join-all - collapse EVERY newline. Over-broad: welds unrelated output lines into one, so a phrase can
#              be satisfied by text that was never a single message.
#   no-fold  - return the input untouched. Under-broad: this is the live defect itself.
#   no-indent- accept a continuation pipe with ZERO leading whitespace. Over-broad in a narrower way than
#              `join-all`: it welds only lines that open with a pipe, which is enough to eat a table row.
function Get-ScaffoldUnwrappedErrorText {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
    [ValidateSet('join-all', 'no-fold', 'no-indent')][string]$Variant
  )
  # No empty-string guard: the param is [string] with AllowEmptyString, so $null arrives as '' and
  # -replace on '' already returns '' - a guard here would be a line no mutation could kill (R4).
  if ($Variant -eq 'no-fold') { return $Text }
  if ($Variant -eq 'join-all') { return ($Text -replace '\r?\n', ' ') }
  if ($Variant -eq 'no-indent') { return ($Text -replace '\r?\n[ \t]*\|[ \t]?', ' ') }
  return ($Text -replace '\r?\n[ \t]+\|[ \t]?', ' ')
}

# Declared examples for the fold, one per case CLASS. Returns findings as strings and never throws (same
# contract as Test-ScaffoldCoreSelfCheckExamples). Default = exercise the live predicate and expect the
# declared verdict; -Variant re-runs the SAME cases through a named rejected shape, which MUST produce at
# least one disagreement. Hermetic: every case is text built in-process, so a defect that only ubuntu CI
# can observe in the wild is reproducible on any machine - which is the whole point, because the local
# acceptance face is Windows-only (TD210) and nothing between authoring and post-merge CI could see it.
function Test-ScaffoldUnwrappedErrorTextExamples {
  [CmdletBinding()]
  param([ValidateSet('join-all', 'no-fold', 'no-indent')][string]$Variant)
  $lf = [string][char]10
  $crlf = [string][char]13 + [string][char]10
  $pfx = '     | '
  $cases = @(
    @{ what = 'the wrapped repair phrase (the shape measured on the ubuntu runner)'; text = 'exists only in a working tree authorises nothing. Fix: commit' + $lf + $pfx + 'the seeded card to master, then rerun the same ship.'; pattern = 'commit the seeded card'; want = $true }
    @{ what = 'the same message unwrapped is still matchable'; text = 'Fix: commit the seeded card to master, then rerun the same ship.'; pattern = 'commit the seeded card'; want = $true }
    @{ what = 'a CRLF wrap folds as well as an LF one'; text = 'Fix: commit' + $crlf + $pfx + 'the seeded card to master.'; pattern = 'commit the seeded card'; want = $true }
    @{ what = 'two ordinary output lines are NOT welded together'; text = 'alpha' + $lf + 'beta'; pattern = 'alpha beta'; want = $false }
    @{ what = 'a zero-indent pipe does NOT open a continuation line'; text = 'alpha' + $lf + '| beta'; pattern = 'alpha beta'; want = $false }
    @{ what = 'two consecutive markdown table rows are not welded into one row'; text = '| col | val |' + $lf + '| a | 1 |'; pattern = 'val \| a'; want = $false }
    @{ what = 'ordinary lines survive the fold rather than being emptied'; text = 'alpha' + $lf + 'beta'; pattern = 'alpha'; want = $true }
    @{ what = 'a whitespace-free sentinel is unaffected either way'; text = 'Exception:' + $lf + $pfx + '[SHIP-SCOPE-CARD-ABSENT] the card is not on the base ref'; pattern = '\[SHIP-SCOPE-CARD-ABSENT\]'; want = $true }
    @{ what = 'text with no line break at all is returned unchanged'; text = 'Fix: commit the seeded card to master.'; pattern = 'commit the seeded card'; want = $true }
    @{ what = 'empty text matches nothing and does not throw'; text = ''; pattern = 'commit the seeded card'; want = $false }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = if ($v) { Get-ScaffoldUnwrappedErrorText -Text $c.text -Variant $v } else { Get-ScaffoldUnwrappedErrorText -Text $c.text }
    $hit = [bool]([string]$got -match $c.pattern)
    if ($hit -ne $c.want) { $findings += "[ERRTEXT-WRAP-FOLD] case '$($c.what)' judged match=$hit, expected $($c.want) - the fold must reconstruct one logical message out of an error view's continuation lines and must join nothing else, so both directions are defects: too little and a phrase a child process wrapped at its own console width stops matching (TD258, ubuntu-latest red for three commits), too much and a phrase is satisfied by text that was never one message (TD260). [FIX] fix the fold, never the example." }
  }
  return $findings
}

# ── Source anchors: the three properties T261 asserted only by a count and by a grep (T264/TD263) ──────
# T261 closed three real defects in the gate-1 fold block and the 15r(e) region, and guarded each with an
# assertion that is blind along exactly the axis its acceptance item is about: a count of the CORE's own
# ValidValues for a claim about selftest.ps1's LOOP, a count of the lines holding the folded copy for a
# claim about WHICH line reads it, and a grep for a bare literal for a claim about WHERE that line sits.
# A count answers "how many" and a grep answers "is it present"; neither can answer "which one" or
# "where", so no amount of care in writing them would have closed the gap. The channel is the defect.
#
# The fix is the channel 17z already paid for, and the measurement that settles it is recorded in that
# gate's own header: T176 put three equivalent rows on the grep channel and the mutation batch came back
# ok=0 survived=3, because a row that spells its own sentinel out satisfies its own grep, while the AST
# channel sees only real call sites. This core writes no new channel; it puts three more judgements on
# that one.
#
# Takes TEXT rather than a path, so the examples below are hermetic and so no [System.IO.*] static ever
# receives a relative path (L313). Returns findings as strings and never throws. Source that does not
# parse returns NO finding rather than a bad one: that is gate 1's business, and reporting it here would
# name two causes for one defect (the same stance Test-ScaffoldCoreSelfCheckVia takes).
#
# Rejected shapes, declared by name rather than left to a comment. Each one is the assertion T261
# actually shipped, not a strawman - which is what makes the anti-vacuous controls an argument rather
# than a ritual:
#   count-only    - judge the folded copy by COUNTING the lines that hold it (T261 dod arm 11).
#                   Identity-blind: moving the read to one of the eleven Write-Host arms keeps it at 3.
#   literal-only  - judge the pre-initialisation by the presence of its literal (T261 dod arm 12).
#                   Position-blind: moving it inside the fixture branch keeps the literal present.
#   no-derivation - do not judge the shape list at all (T261 wrote no arm for it, and dod arm 9 read the
#                   core rather than selftest.ps1). Blind by absence: a hardcoded second copy of the same
#                   names passes every assertion that card shipped.
function Get-ScaffoldSelftestSourceAnchorFindings {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
    [ValidateSet('count-only', 'literal-only', 'no-derivation')][string]$Variant
  )
  $findings = @()
  $perr = $null
  $ast = [System.Management.Automation.Language.Parser]::ParseInput([string]$Text, [ref]$null, [ref]$perr)
  if ($perr -and $perr.Count -gt 0) { return $findings }
  $asgs = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] }, $true))
  $vars = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.VariableExpressionAst] }, $true))
  # The enclosing statement block of a node. A local scriptblock rather than a sibling function: a
  # Get-Scaffold* name is API surface, and the T244/TD250 rule then owes it an existence arm in every DoD
  # that transitively reaches it - a heavy price for a four-line walk used twice inside one function.
  $blockOf = {
    param($node)
    $q = $node
    while ($q -and -not ($q -is [System.Management.Automation.Language.StatementBlockAst] -or $q -is [System.Management.Automation.Language.ScriptBlockAst])) { $q = $q.Parent }
    return $q
  }

  # (1) DERIVATION. Every rejected-shape list this file drives a loop from must be DERIVED from the core's
  #     own [ValidateSet], never a second hand-maintained copy of the same names - which is how a newly
  #     added shape goes dead silently while every assertion stays green (T261 acceptance 3).
  #     THREE lists are named because there are three: $wrapShapes (the error-view fold), $anchorShapes
  #     (T264's own block, which would otherwise ship the very debt it repays) and $ciShardShapes (T265,
  #     the CI shard-death judge). Adding the third was the one-line edit this comment promised. A NAMED
  #     LIST rather
  #     than a name-suffix convention: binding the rule to a naming habit makes it depend on something
  #     nothing enforces, and adding a third list is a one-line edit here, made by whoever adds it, at the
  #     moment they add it. Absent lists are skipped rather than demanded, so a downstream that trimmed
  #     one is not held to it; the floor below is what stops "all absent" from passing vacuously.
  if ($Variant -ne 'no-derivation') {
    $seenLists = 0
    foreach ($shapeVar in @('$wrapShapes', '$anchorShapes', '$ciShardShapes')) {
      $shapeAsg = @($asgs | Where-Object { $_.Left.Extent.Text -eq $shapeVar })
      if ($shapeAsg.Count -eq 0) { continue }
      $seenLists++
      if ($shapeAsg.Count -ne 1) {
        $findings += "[SELFTEST-ANCHOR] derivation: $shapeVar is assigned $($shapeAsg.Count) times, so which assignment the loop consumes is not decidable from the source and the derivation cannot be judged. [FIX] assign the rejected-shape list once, from the core's own [ValidateSet]."
        continue
      }
      $shapeMembers = @($shapeAsg[0].Right.FindAll({ param($n) $n -is [System.Management.Automation.Language.MemberExpressionAst] }, $true) | ForEach-Object { $_.Member.Extent.Text })
      if ($shapeMembers -notcontains 'ValidValues') {
        $findings += "[SELFTEST-ANCHOR] derivation: the assignment to $shapeVar does not reach a ValidValues member access, so the rejected-shape list is a SECOND hand-maintained copy of names the core already declares. A fourth shape added to the [ValidateSet] would then be exercised by nothing, silently, while this gate and the card's own arms all stayed green - which is exactly what T261's acceptance 3 promised could not happen and asserted nowhere (TD263). [FIX] derive the list from (Get-Command <core>).Parameters['Variant'].Attributes' ValidateSet; do not re-list the names."
      }
    }
    if ($seenLists -eq 0) {
      $findings += "[SELFTEST-ANCHOR] derivation (anti-vacuous): none of the named rejected-shape lists is present, so this judgement ran over nothing and would pass on any file at all. [FIX] restore the derived list, or retire this judgement in the same diff that retires the last loop it guards."
    }
  }

  # (2) IDENTITY. Exactly ONE 15r(e) arm may read the folded copy, and it must be the arm greping the
  #     THROWN [SHIP-LOCAL-MERGE-FAIL] message - the only text in that region a child process renders at
  #     its own console width. The other eleven grep Write-Host output, which is not error-view formatted
  #     and has never wrapped on either runner; folding those was T259's misclassification and undoing it
  #     was T261's whole subject (T261 acceptance 4).
  if ($Variant -eq 'count-only') {
    $foldedLines = @(($Text -split '\r?\n') | Where-Object { $_.Contains('$cFlat') })
    if ($foldedLines.Count -ne 3) {
      $findings += "[SELFTEST-ANCHOR] identity: expected exactly 3 lines holding the folded copy, found $($foldedLines.Count)."
    }
  }
  else {
    $foldedReads = @($vars |
      Where-Object { $_.VariablePath.UserPath -eq 'cFlat' } |
      Where-Object { -not ($_.Parent -is [System.Management.Automation.Language.AssignmentStatementAst] -and $_.Parent.Left -eq $_) })
    if ($foldedReads.Count -ne 1) {
      $findings += "[SELFTEST-ANCHOR] identity: the folded copy is READ $($foldedReads.Count) time(s), expected exactly one. More than one means an arm greping Write-Host output was moved onto a folded copy it has no reason to read; none means the fold is dead and the thrown message is being matched raw, which is TD258 reopened. [FIX] leave exactly one reader, the [SHIP-LOCAL-MERGE-FAIL] arm."
    }
    else {
      $foldedStmt = $foldedReads[0]
      while ($foldedStmt -and -not ($foldedStmt -is [System.Management.Automation.Language.StatementAst])) { $foldedStmt = $foldedStmt.Parent }
      if (-not $foldedStmt -or -not $foldedStmt.Extent.Text.Contains('SHIP-LOCAL-MERGE-FAIL')) {
        $findings += "[SELFTEST-ANCHOR] identity: the single reader of the folded copy is not the arm that greps the thrown [SHIP-LOCAL-MERGE-FAIL] message - it reads a Write-Host phrase instead. The line COUNT is unchanged by that move, which is why T261's arms 10-11 cannot see it and why this is judged by identity (TD263). [FIX] point the fold at the thrown arm and give every Write-Host arm the raw capture back."
      }
    }
  }

  # (3) POSITION. The folded copy's empty-string pre-initialisation must DOMINATE its conditional
  #     assignment - stated as domination rather than as "beside its raw twin" because domination is the
  #     property that actually makes a skipped fixture branch safe, and it names no twin variable that a
  #     later edit could legitimately rename. Fail() sets a flag and returns rather than exiting, and
  #     selftest runs under Set-StrictMode -Version Latest, so a copy assigned only INSIDE the branch and
  #     read after it masks the fixture's own diagnostic with an undefined-variable exception attributed
  #     to no arm (T261 acceptance 5).
  if ($Variant -eq 'literal-only') {
    if (-not $Text.Contains('$cFlat = ' + "''")) {
      $findings += "[SELFTEST-ANCHOR] position: the folded copy's empty-string pre-initialisation is absent."
    }
  }
  else {
    $foldedAsg = @($asgs | Where-Object { $_.Left.Extent.Text -eq '$cFlat' })
    $foldedPre = @($foldedAsg | Where-Object { $_.Right.Extent.Text -eq "''" })
    $foldedReal = @($foldedAsg | Where-Object { $_.Right.Extent.Text -ne "''" })
    if ($foldedPre.Count -ne 1 -or $foldedReal.Count -ne 1) {
      $findings += "[SELFTEST-ANCHOR] position: expected exactly one empty-string pre-initialisation of the folded copy and exactly one real assignment, found $($foldedPre.Count) and $($foldedReal.Count). [FIX] pre-initialise the copy once, outside the fixture branch, and assign it once inside."
    }
    else {
      $preBlock = & $blockOf $foldedPre[0]
      $realBlock = & $blockOf $foldedReal[0]
      $ancestor = if ($realBlock) { $realBlock.Parent } else { $null }
      $dominates = $false
      while ($ancestor) {
        if ($ancestor -eq $preBlock) { $dominates = $true; break }
        $ancestor = $ancestor.Parent
      }
      if (-not $dominates) {
        $findings += "[SELFTEST-ANCHOR] position: the folded copy's pre-initialisation does not dominate its conditional assignment - it sits in the same block, so it runs only when that block runs. The LITERAL is unchanged by that move, which is why T261's arm 12 cannot see it and why this is judged by position (TD263). Under Set-StrictMode -Version Latest a fixture that failed to build then raises an undefined-variable exception instead of the Fail written to explain it. [FIX] pre-initialise the copy beside its raw twin, outside the branch that assigns it."
      }
    }
  }
  return $findings
}

# Declared examples for the source anchors, one per case CLASS. Returns findings as strings and never
# throws (same contract as Test-ScaffoldUnwrappedErrorTextExamples). Default = exercise the live judge and
# expect the declared verdict; -Variant re-runs the SAME cases through a named rejected shape, which MUST
# produce at least one disagreement. Hermetic: every case is source text built in-process, so the three
# defect shapes are reproducible without a repository and nothing here reads scripts/.
# The three defective snippets are DERIVED from the sound one by a single named substitution each, so a
# disagreement names the property rather than the snippet, and a substitution that silently stopped
# matching turns the table red on its own case rather than quietly re-testing the sound snippet.
function Test-ScaffoldSelftestSourceAnchorExamples {
  [CmdletBinding()]
  param([ValidateSet('count-only', 'literal-only', 'no-derivation')][string]$Variant)
  $sound = @'
$wrapSetAttr = @((Get-Command Get-ScaffoldUnwrappedErrorText).Parameters['Variant'].Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateSetAttribute] })
$wrapShapes = @(if ($wrapSetAttr.Count) { $wrapSetAttr[0].ValidValues } else { @() })
foreach ($wrapVariant in $wrapShapes) { Test-Thing -Variant $wrapVariant }
$cExit = -1; $cOut = ''; $cFlat = ''
if (-not (Test-Path $sgWtC)) { Fail 'fixture C absent' }
else {
  $cOut = (& pwsh -NoProfile -File $encWrapR 2>&1 | Out-String)
  $cFlat = Get-ScaffoldUnwrappedErrorText -Text $cOut
}
if ($cOut -notmatch 'merge --abort') { Fail 'C write-host arm' }
if ($cFlat -notmatch '\[SHIP-LOCAL-MERGE-FAIL\] local merge failed') { Fail 'C thrown arm' }
'@
  # .Replace, never -replace: a replacement TEMPLATE processes $ and \ escapes independently of how the
  # string was built, which is the L17 family of defect. String.Replace is literal on both sides.
  # Each substitution is written to leave the snippet PARSEABLE and to leave the two blind channels
  # satisfied - the line count stays at three and the pre-initialisation literal stays present - because
  # a snippet that stopped parsing, or that broke a second property, would prove the wrong thing.
  $hardcoded = $sound.Replace(
    '$wrapShapes = @(if ($wrapSetAttr.Count) { $wrapSetAttr[0].ValidValues } else { @() })',
    '$wrapShapes = @(''join-all'', ''no-fold'', ''no-indent'')')
  $movedRead = $sound.Replace('($cOut -notmatch ''merge --abort'')', '($cFlat -notmatch ''merge --abort'')')
  $movedRead = $movedRead.Replace('($cFlat -notmatch ''\[SHIP', '($cOut -notmatch ''\[SHIP')
  $movedPre = $sound.Replace('$cExit = -1; $cOut = ''''; $cFlat = ''''', '$cExit = -1; $cOut = ''''')
  $movedPre = $movedPre.Replace('  $cOut = (& pwsh', '  $cFlat = ''''; $cOut = (& pwsh')
  # The floor's own case. Without it the anti-vacuous arm is itself unfalsifiable: no snippet reaches
  # it, so deleting the arm would SURVIVE its mutation and the batch would record that as evidence.
  $noList = $sound.Replace('$wrapShapes = @(if ($wrapSetAttr.Count) { $wrapSetAttr[0].ValidValues } else { @() })', '$notAShapeList = @()')
  $cases = @(
    @{ what = 'the sound shape - list derived, one reader and it is the thrown arm, pre-initialisation outside the branch'; src = $sound; expect = 0 }
    @{ what = 'the derived shape list replaced by a hardcoded second copy of the same three names'; src = $hardcoded; expect = 1 }
    @{ what = 'the folded copy read by a Write-Host arm instead of the thrown one, line count unchanged at three'; src = $movedRead; expect = 1 }
    @{ what = 'the pre-initialisation moved inside the fixture branch, its literal unchanged'; src = $movedPre; expect = 1 }
    @{ what = 'no rejected-shape list present at all - the derivation floor, which is what stops that judgement passing on any file'; src = $noList; expect = 1 }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(if ($v) { Get-ScaffoldSelftestSourceAnchorFindings -Text $c.src -Variant $v } else { Get-ScaffoldSelftestSourceAnchorFindings -Text $c.src })
    if ($got.Count -ne $c.expect) { $findings += "[SELFTEST-ANCHOR-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) - each defective snippet differs from the sound one by ONE named substitution, so a disagreement here is the judge losing (or inventing) the property that substitution breaks, never the snippet being wrong. [FIX] fix the judge, never the example." }
  }
  return $findings
}

# -------------------------------------------------------------------------------------------------
# T265-CI-SHARD-EARLY-DEATH: the two guards that exist for a shard that dies EARLY, judged by the
# BEHAVIOUR each promises rather than by the presence of its ingredients.
#
# Both were measured failing at once, on scaffold-selftest run 33577341519 (sha ccd6bf5, job
# `selftest (ubuntu-latest, seed-post)`), while every gate in this repo stayed green:
#   (A) `Register-PSRepository -Default -ErrorAction SilentlyContinue` sat ABOVE the retry loop with
#       its error swallowed, so the loop body held only `Install-Module` and the three attempts could
#       do nothing but repeat one identical "Unable to find repository 'PSGallery'". Gate 8.2a'' asks
#       whether `Register-PSRepository -Default` and `foreach ($psaTry in 1..N)` both APPEAR in the
#       file; they did, in the order that makes the retry useless. Its check (1) even names the
#       outcome it failed to prevent.
#   (B) the `if: always()` summary step's fallback - the string written for exactly an early death -
#       never printed. When the shard dies before the selftest step runs, the teed log does not
#       exist, and `Select-String -Path <missing>` terminates the script under the
#       `$ErrorActionPreference = 'stop'` that GitHub's pwsh shell sets, the cmdlet's own
#       `-ErrorAction SilentlyContinue` notwithstanding. The step then reported its own failure,
#       adding a second red that misattributes the cause. No gate read that step at all.
#
# The subject is YAML; the judgement is not. A `run: |` body IS PowerShell, so the two named bodies
# are lifted out and parsed as such - the T264 channel (structure over source) applied to the one
# file where a presence check was measured passing over a live defect. Adding two more regexes beside
# the four that already missed it would have been the same mistake a third time.
#
# NO YAML parser is written and none is needed. A body the locator cannot find, or cannot parse, is
# REPORTED (`locate` / `parse`), never passed over: a judge that silently found nothing must not be
# able to masquerade as a judge that found nothing wrong.
# -------------------------------------------------------------------------------------------------
function Get-ScaffoldCiShardResilienceFindings {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
    [ValidateSet('presence-only', 'swallow-blind', 'summary-blind', 'always-blind', 'write-blind', 'denylist-ea', 'path-blind')][string]$Variant
  )
  $findings = @()

  # Lift one step's `run: |` body out of the workflow and hand back parseable PowerShell. Local
  # scriptblocks rather than sibling functions, for T264's reason: a Get-Scaffold* name is API surface,
  # and the T244/TD250 rule then owes it an existence arm in every DoD that transitively reaches it -
  # a heavy price for helpers used twice inside one function.
  $bodyOf = {
    param($all, $needle)
    $lines = @([string]$all -split '\r?\n')
    $at = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($lines[$i] -match '^\s*-\s+name:' -and $lines[$i].Contains($needle)) { $at = $i; break }
    }
    if ($at -lt 0) { return $null }
    $runAt = -1
    for ($i = $at + 1; $i -lt $lines.Count; $i++) {
      if ($lines[$i] -match '^\s*-\s+name:') { break }
      if ($lines[$i] -match '^\s*run:\s*\|') { $runAt = $i; break }
    }
    if ($runAt -lt 0) { return $null }
    $runInd = [regex]::Match($lines[$runAt], '^(\s*)').Groups[1].Value.Length
    $body = @()
    for ($i = $runAt + 1; $i -lt $lines.Count; $i++) {
      if ($lines[$i].Trim().Length -eq 0) { $body += ''; continue }
      if ([regex]::Match($lines[$i], '^(\s*)').Groups[1].Value.Length -le $runInd) { break }
      $body += $lines[$i]
    }
    $held = @($body | Where-Object { $_.Trim().Length })
    if ($held.Count -eq 0) { return $null }
    $pad = ($held | ForEach-Object { [regex]::Match($_, '^(\s*)').Groups[1].Value.Length } | Measure-Object -Minimum).Minimum
    $flat = (($body | ForEach-Object { if ($_.Length -ge $pad) { $_.Substring($pad) } else { $_ } }) -join "`n")
    # A GitHub expression never reaches the runner as PowerShell - it is substituted before the shell
    # sees it - so substituting a bare token here parses the body the runner actually executes.
    return [regex]::Replace($flat, '\$\{\{[^}]*\}\}', 'GHAEXPR')
  }

  # The raw lines of one step, `- name:` to the next `- name:`. The `if:` key lives HERE, not in the
  # run body, and a summary step that has lost `if: always()` does not run on a red shard at all -
  # which defeats the protected property more completely than any defect inside the body (T266).
  $stepOf = {
    param($all, $needle)
    $lines = @([string]$all -split '\r?\n')
    $at = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($lines[$i] -match '^\s*-\s+name:' -and $lines[$i].Contains($needle)) { $at = $i; break }
    }
    if ($at -lt 0) { return $null }
    $block = @($lines[$at])
    for ($i = $at + 1; $i -lt $lines.Count; $i++) {
      if ($lines[$i] -match '^\s*-\s+name:') { break }
      $block += $lines[$i]
    }
    return $block
  }

  $astOf = {
    param($src)
    $perr = $null
    $a = [System.Management.Automation.Language.Parser]::ParseInput([string]$src, [ref]$null, [ref]$perr)
    if ($perr -and $perr.Count -gt 0) { return $null }
    return $a
  }

  $isUnder = {
    param($node, $root)
    $q = $node
    while ($q) { if ([object]::ReferenceEquals($q, $root)) { return $true }; $q = $q.Parent }
    return $false
  }

  # (A) THE RETRY MUST RETRY THE OPERATION THAT FAILS, AND ITS FAILURE MUST BE VISIBLE.
  $provSrc = & $bodyOf $Text 'Provision PSScriptAnalyzer'
  if ($null -eq $provSrc) {
    $findings += "[CI-SHARD-DEATH] locate-provision: no ``run: |`` body was found for a step whose name holds 'Provision PSScriptAnalyzer', so the analyzer-provisioning judgement ran over nothing and would report clean on any text at all. [FIX] restore the step, or retire this judgement in the same diff that retires the step."
  }
  else {
    $provAst = & $astOf $provSrc
    if ($null -eq $provAst) {
      $findings += '[CI-SHARD-DEATH] parse-provision: the provisioning step body does not parse as PowerShell, so nothing about it can be judged - and the runner would fail on it too. [FIX] repair the body.'
    }
    else {
      $regs = @($provAst.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Register-PSRepository' }, $true))
      if ($regs.Count -eq 0) {
        $findings += '[CI-SHARD-DEATH] retry-absent: the provisioning step never calls Register-PSRepository, so a runner whose PSGallery source is absent - the measured failure of run 33577341519 - has no path to recovery at all. [FIX] register the default source inside the retry.'
      }
      else {
        $loops = @($provAst.FindAll({ param($n) $n -is [System.Management.Automation.Language.ForEachStatementAst] }, $true))
        # (1) SCOPE. The retry's body must contain the registration. A precondition established once,
        #     above the loop, means every attempt after the first repeats the same failure - which is
        #     not a retry, and is exactly what 3/3 identical errors looked like on the measured run.
        if ($Variant -ne 'presence-only') {
          foreach ($rc in $regs) {
            $inside = $false
            foreach ($lp in $loops) { if (& $isUnder $rc $lp.Body) { $inside = $true; break } }
            if (-not $inside) {
              $findings += "[CI-SHARD-DEATH] retry-scope: the Register-PSRepository call at line $($rc.Extent.StartLineNumber) of the provisioning body is NOT inside the retry loop, so the loop retries only the install and a failed registration is repeated into N identical 'Unable to find repository' errors. Measured that way on run 33577341519, where 3/3 attempts carried the same message while gate 8.2a'' - which asks only that both strings appear somewhere in the file - stayed green. [FIX] move the registration inside the loop body so the attempt that failed is the attempt that is retried."
              break
            }
          }
        }
        # (2) EVIDENCE. A swallowed registration error is why nobody could tell whether the failure was
        #     transient. -ErrorAction Stop hands it to the retry's own catch, which already logs it.
        if ($Variant -ne 'swallow-blind') {
          foreach ($rc in $regs) {
            $els = @($rc.CommandElements)
            $swallowed = ''
            $sawStop = $false
            for ($k = 0; $k -lt $els.Count; $k++) {
              if (-not ($els[$k] -is [System.Management.Automation.Language.CommandParameterAst])) { continue }
              $pn = [string]$els[$k].ParameterName
              if ($pn.Length -lt 2) { continue }
              if (-not (('ErrorAction' -like "$pn*") -or ($pn -eq 'EA'))) { continue }
              # BOTH forms, because they are one parameter: `-ErrorAction Stop` puts the value in the next
              # element, `-ErrorAction:Stop` attaches it to the parameter. Reading only the first would let
              # a swallow written the colon way through - a false negative in the unsafe direction, which
              # is the exact failure class this judge exists to end.
              $argText = if ($els[$k].Argument) { $els[$k].Argument.Extent.Text } elseif ($k + 1 -lt $els.Count) { $els[$k + 1].Extent.Text } else { '' }
              if ($argText -match '^\s*[''"]?(SilentlyContinue|Ignore|Continue)[''"]?\s*$') { $swallowed = $Matches[1] }
              if ($argText -match '^\s*[''"]?Stop[''"]?\s*$') { $sawStop = $true }
            }
            # Pre-initialised OUTSIDE the decision below, so that DELETING the decision leaves a defined
            # value and the example table reports a named disagreement instead of throwing under StrictMode.
            # A mutation that reds by crashing is [MUT-BAD-EVIDENCE], not evidence (L165/L167/L249) - the
            # T266 batch measured exactly that on its first run and this line is the repair.
            $evidenceBad = $false
            # POLARITY (T266). T265 refused the swallowing literals and accepted everything else, and the
            # advisory R3 defeated that in two moves at once: `-ErrorAction 0` (ActionPreference is a
            # NUMERIC enum whose 0 IS SilentlyContinue) and `-ErrorAction $policy` (a value no parser can
            # resolve). Both are the same swallow spelled a way nobody enumerated, and a third spelling
            # would always be available, because the broken set is OPEN. The sound set has exactly one
            # member, so the rule requires it and refuses the rest - including the unresolvable and the
            # absent (L317). 'denylist-ea' keeps T265's polarity so the difference stays measurable.
            $evidenceBad = if ($Variant -eq 'denylist-ea') { [bool]$swallowed } else { -not $sawStop }
            if ($evidenceBad) {
              $swallowed = if ($swallowed) { $swallowed } elseif ($sawStop) { 'Stop' } else { 'no resolvable -ErrorAction Stop' }
              $findings += "[CI-SHARD-DEATH] retry-evidence: the Register-PSRepository call at line $($rc.Extent.StartLineNumber) of the provisioning body does not carry a literal -ErrorAction Stop ($swallowed), so a failed registration is not guaranteed to reach the retry's catch and may write nothing anyone can read - the state run 33577341519 left behind, where whether the failure was transient is unknowable from the log. The rule REQUIRES the sound form rather than refusing the broken ones: -ErrorAction 0 is SilentlyContinue spelled numerically and -ErrorAction `$var cannot be resolved at all, so a denylist of spellings can always be extended by one more (L317). [FIX] write -ErrorAction Stop literally."
              break
            }
          }
        }
      }
    }
  }

  # (B) THE SUMMARY FALLBACK MUST SURVIVE A LOG THAT WAS NEVER WRITTEN - AND THE STEP HOLDING IT MUST
  #     STILL RUN, AND MUST ACTUALLY PUBLISH. T265 judged only the read inside the body, so deleting
  #     `if: always()` or replacing the write with `Out-Null` defeated the whole property in one edit
  #     and returned ZERO findings (both re-measured on merged 2736c87).
  $sumStep = & $stepOf $Text 'Publish gate results'
  if ($null -ne $sumStep -and $Variant -ne 'always-blind') {
    if (-not (@($sumStep | Where-Object { $_ -match '^\s*if:\s*always\(\)' }).Count)) {
      $findings += "[CI-SHARD-DEATH] summary-always: the summary step does not declare ``if: always()``, so on a red shard it does not run AT ALL and the fallback this judgement is about is unreachable - a stronger defeat of the property than any defect inside the body, and invisible to a judge that reads only the run block. [FIX] restore ``if: always()`` on the step."
    }
  }

  $sumSrc = & $bodyOf $Text 'Publish gate results'
  if ($null -eq $sumSrc) {
    $findings += "[CI-SHARD-DEATH] locate-summary: no ``run: |`` body was found for a step whose name holds 'Publish gate results', so the summary judgement ran over nothing and would report clean on any text at all. [FIX] restore the step, or retire this judgement in the same diff that retires the step."
  }
  else {
    $sumAst = & $astOf $sumSrc
    if ($null -eq $sumAst) {
      $findings += '[CI-SHARD-DEATH] parse-summary: the summary step body does not parse as PowerShell, so nothing about it can be judged - and the runner would fail on it too. [FIX] repair the body.'
    }
    else {
      if ($Variant -ne 'write-blind') {
        $writes = @($sumAst.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true) | Where-Object { $_.Extent.Text -match 'GITHUB_STEP_SUMMARY' })
        if ($writes.Count -eq 0) {
          $findings += "[CI-SHARD-DEATH] summary-write: the summary body never writes to ``$env:GITHUB_STEP_SUMMARY``, so whatever it computes is discarded and the step publishes nothing. Measured on merged 2736c87 - replacing the write with ``Out-Null`` left the body correct and the judgement silent. [FIX] restore the write to the step summary."
        }
      }
    }
    if ($null -ne $sumAst -and $Variant -ne 'summary-blind') {
      $reads = @($sumAst.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] -and @('Select-String', 'Get-Content') -contains $n.GetCommandName() }, $true))
      if ($reads.Count -eq 0) {
        $findings += '[CI-SHARD-DEATH] locate-read: the summary step reads no log at all, so the guard this judgement is about has no subject and the judgement would report clean whatever the step did. [FIX] restore the read, or retire this judgement in the same diff that retires it.'
      }
      else {
        # The read must be governed by an `if` whose CONDITION tests THE SAME PATH. Not "a Test-Path
        # appears in the step": a check that runs after the read, or in the other branch, prevents
        # nothing - and it is the ORDERING, not the ingredient, that this whole card is about. And not
        # "a Test-Path appears in the condition" either: T265 declined the same-path check as fragile
        # over-precision, and the advisory R3 then walked through it with `Test-Path -LiteralPath
        # 'unrelated.log'` guarding a read of `$log`. A guard on a path nobody reads prevents nothing,
        # so the path IS the check ('path-blind' keeps T265's weaker rule so the difference is measurable).
        $pathArgOf = {
          param($cmdAst)
          $es = @($cmdAst.CommandElements)
          for ($k = 0; $k -lt $es.Count; $k++) {
            if (-not ($es[$k] -is [System.Management.Automation.Language.CommandParameterAst])) { continue }
            $nm = [string]$es[$k].ParameterName
            if ($nm.Length -lt 2) { continue }
            if (-not (('Path' -like "$nm*") -or ('LiteralPath' -like "$nm*"))) { continue }
            $a = if ($es[$k].Argument) { $es[$k].Argument } elseif ($k + 1 -lt $es.Count) { $es[$k + 1] } else { $null }
            if ($a) { return $a.Extent.Text.Trim() }
          }
          return ''
        }
        foreach ($rd in $reads) {
          $guarded = $false
          $readPath = & $pathArgOf $rd
          $q = $rd
          while ($q -and -not $guarded) {
            $p = $q.Parent
            if ($p -is [System.Management.Automation.Language.IfStatementAst]) {
              foreach ($cl in $p.Clauses) {
                if (-not (& $isUnder $rd $cl.Item2)) { continue }
                foreach ($tp in @($cl.Item1.FindAll({ param($m) $m -is [System.Management.Automation.Language.CommandAst] -and $m.GetCommandName() -eq 'Test-Path' }, $true))) {
                  if ($Variant -eq 'path-blind') { $guarded = $true; break }
                  if ((& $pathArgOf $tp) -eq $readPath -and $readPath) { $guarded = $true; break }
                }
                if ($guarded) { break }
              }
            }
            $q = $p
          }
          if (-not $guarded) {
            $findings += "[CI-SHARD-DEATH] summary-guard: the log read at line $($rd.Extent.StartLineNumber) of the summary body is not governed by an if whose condition tests the path, so on the earliest shard deaths - the ones this step's fallback text was written for - the file does not exist, the read terminates the script under the stop preference GitHub's pwsh shell sets, and the 'unavailable' message is lost. The cmdlet's own -ErrorAction SilentlyContinue does NOT prevent this; measured on run 33577341519 and reproduced locally on pwsh 7. [FIX] wrap the read in an if (Test-Path -LiteralPath <log>) - naming THE SAME path the read uses - whose else branch yields nothing."
            break
          }
        }
      }
    }
  }

  return $findings
}

# The declared examples for the judge above. Verdicts are compared as CODE SETS, never as counts: the
# advisory R3 on T264 found that a count-only example loop cannot tell a case that lost its expected
# property from one that gained a different one, and this table is the first built the corrected way.
# Each defective snippet differs from the sound one by ONE named substitution, so a disagreement here
# is the judge losing (or inventing) the property that substitution breaks, never the snippet.
function Test-ScaffoldCiShardResilienceExamples {
  [CmdletBinding()]
  param([ValidateSet('presence-only', 'swallow-blind', 'summary-blind', 'always-blind', 'write-blind', 'denylist-ea', 'path-blind')][string]$Variant)
  $sound = @'
jobs:
  selftest:
    steps:
      - name: Provision PSScriptAnalyzer (lint gate must not skip-as-pass in CI)
        shell: pwsh
        run: |
          $psaOk = $false
          foreach ($psaTry in 1..3) {
            try {
              if (-not (Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue)) {
                Register-PSRepository -Default -ErrorAction Stop
              }
              Install-Module PSScriptAnalyzer -RequiredVersion 1.24.0 -Repository PSGallery -Scope CurrentUser -Force -ErrorAction Stop
              $psaOk = $true
              break
            } catch {
              if ($psaTry -lt 3) { Start-Sleep -Seconds (5 * $psaTry) }
            }
          }
          if (-not $psaOk) { Write-Error 'exhausted'; exit 1 }

      - name: Publish gate results to the job summary
        if: always()
        shell: pwsh
        run: |
          $log = Join-Path $env:RUNNER_TEMP 'selftest-shard.log'
          $hit = if (Test-Path -LiteralPath $log) { Select-String -Path $log -Pattern 'SUMMARY' | Select-Object -Last 1 } else { $null }
          $body = if ($hit) { $hit.Line.Trim() } else { 'unavailable' }
          $body | Out-File -FilePath $env:GITHUB_STEP_SUMMARY -Append
'@
  # .Replace, never -replace: a replacement TEMPLATE processes $ and \ escapes independently of how the
  # string was built (the L17 family). String.Replace is literal on both sides. Each substitution leaves
  # the snippet PARSEABLE and breaks exactly one property - a snippet that stopped parsing, or broke a
  # second property, would prove the wrong thing.
  $hoisted = @'
              if (-not (Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue)) {
                Register-PSRepository -Default -ErrorAction Stop
              }

'@
  $outside = $sound.Replace($hoisted, '')
  $outside = $outside.Replace('          $psaOk = $false', @'
          if (-not (Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue)) {
            Register-PSRepository -Default -ErrorAction Stop
          }
          $psaOk = $false
'@)
  $swallowed = $sound.Replace('Register-PSRepository -Default -ErrorAction Stop', 'Register-PSRepository -Default -ErrorAction SilentlyContinue')
  $swallowedcolon = $sound.Replace('Register-PSRepository -Default -ErrorAction Stop', 'Register-PSRepository -Default -ErrorAction:SilentlyContinue')
  $unguarded = $sound.Replace(
    '$hit = if (Test-Path -LiteralPath $log) { Select-String -Path $log -Pattern ''SUMMARY'' | Select-Object -Last 1 } else { $null }',
    '$hit = Select-String -Path $log -Pattern ''SUMMARY'' -ErrorAction SilentlyContinue | Select-Object -Last 1')
  # The floor's own cases. Without them the anti-vacuous arms are themselves unfalsifiable: no snippet
  # reaches them, so deleting one would SURVIVE its mutation and the batch would bank that as evidence.
  $noregister = $sound.Replace($hoisted, '')
  $noread = $sound.Replace(
    '$hit = if (Test-Path -LiteralPath $log) { Select-String -Path $log -Pattern ''SUMMARY'' | Select-Object -Last 1 } else { $null }',
    '$hit = $null')
  # T266 - the five shapes the advisory R3 on PR #349 constructed and defeated the judge with, each
  # re-measured against merged 2736c87 before being written down here. `numeric`, `dynamic` and `noea`
  # exist as three cases rather than one because they are three DIFFERENT ways to miss the sound form,
  # and together they are what makes the polarity change measurable: `denylist-ea` reproduces T265's
  # rule and is blind to all three at once.
  $numeric = $sound.Replace('Register-PSRepository -Default -ErrorAction Stop', 'Register-PSRepository -Default -ErrorAction 0')
  $dynamic = $sound.Replace('Register-PSRepository -Default -ErrorAction Stop', "`$policy = 'SilentlyContinue'`n                Register-PSRepository -Default -ErrorAction `$policy")
  $noea = $sound.Replace('Register-PSRepository -Default -ErrorAction Stop', 'Register-PSRepository -Default')
  $noalways = $sound.Replace("        if: always()`n", '')
  $nowrite = $sound.Replace('$body | Out-File -FilePath $env:GITHUB_STEP_SUMMARY -Append', '$body | Out-Null')
  $wrongpath = $sound.Replace('Test-Path -LiteralPath $log', "Test-Path -LiteralPath 'unrelated.log'")
  $nosteps = 'name: something that is not this workflow at all'
  $brokenprov = $sound.Replace('          $psaOk = $false', '          $psaOk = @(')
  $brokensum = $sound.Replace("          `$log = Join-Path `$env:RUNNER_TEMP 'selftest-shard.log'", '          $log = @(')
  # Every finding LINE the judge can emit has at least one case that fires it, which is what makes each
  # one independently detectable when that single line is deleted (L165). The rule is one case per LINE,
  # not per code: where two spellings of the same thing reach the SAME line - the two -ErrorAction forms
  # below - both are exercised, and deleting that line still breaks both cases. What must never happen is
  # a line no case reaches, because its deletion would then SURVIVE and the batch would bank the silence.
  $cases = @(
    @{ what = 'the sound shape - registration inside the retry with -ErrorAction Stop, and the summary read governed by Test-Path'; src = $sound; codes = @() }
    @{ what = 'the registration hoisted ABOVE the retry loop, unchanged in every other respect - the live shape on run 33577341519'; src = $outside; codes = @('retry-scope') }
    @{ what = 'the registration inside the loop but with its failure swallowed by -ErrorAction SilentlyContinue - the live shape on run 33577341519'; src = $swallowed; codes = @('retry-evidence') }
    @{ what = 'the same swallow written the colon way, -ErrorAction:SilentlyContinue, which puts the value on the parameter instead of the next element'; src = $swallowedcolon; codes = @('retry-evidence') }
    @{ what = 'no registration at all, so a runner without the source has no path to recovery'; src = $noregister; codes = @('retry-absent') }
    @{ what = 'the summary read stripped of its Test-Path guard and given -ErrorAction SilentlyContinue instead, which does NOT save it - the live shape on run 33577341519'; src = $unguarded; codes = @('summary-guard') }
    @{ what = 'a summary step that reads no log at all, so the guard being judged has no subject'; src = $noread; codes = @('locate-read') }
    @{ what = 'the swallow spelled numerically, -ErrorAction 0 - ActionPreference is an enum and 0 IS SilentlyContinue, so this is the same defect and T265 could not see it'; src = $numeric; codes = @('retry-evidence') }
    @{ what = 'the swallow arriving through a variable, -ErrorAction $policy - unresolvable by any parser, which is why the rule requires the sound form rather than refusing the broken ones'; src = $dynamic; codes = @('retry-evidence') }
    @{ what = 'no -ErrorAction at all on the registration, so reaching the catch depends on an ambient preference the step does not set itself'; src = $noea; codes = @('retry-evidence') }
    @{ what = 'the summary step stripped of if: always() - it then does not run on a red shard at all, defeating the property more completely than any defect inside its body'; src = $noalways; codes = @('summary-always') }
    @{ what = 'the summary body computing the fallback correctly and discarding it with Out-Null instead of writing to GITHUB_STEP_SUMMARY'; src = $nowrite; codes = @('summary-write') }
    @{ what = 'a Test-Path guarding a path nobody reads, which is the over-precision T265 declined and the advisory R3 then walked straight through'; src = $wrongpath; codes = @('summary-guard') }
    @{ what = 'a text holding neither step - the locate floor, which is what stops either judgement passing on any file at all'; src = $nosteps; codes = @('locate-provision', 'locate-summary') }
    @{ what = 'a provisioning body that does not parse as PowerShell - the parse floor on the provisioning half'; src = $brokenprov; codes = @('parse-provision') }
    @{ what = 'a summary body that does not parse as PowerShell - the parse floor on the summary half'; src = $brokensum; codes = @('parse-summary') }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(if ($v) { Get-ScaffoldCiShardResilienceFindings -Text $c.src -Variant $v } else { Get-ScaffoldCiShardResilienceFindings -Text $c.src })
    $gotCodes = @($got | ForEach-Object { if ($_ -match '^\[CI-SHARD-DEATH\]\s+([a-z-]+):') { $Matches[1] } } | Sort-Object -Unique)
    $want = @($c.codes | Sort-Object -Unique)
    if (($gotCodes -join ',') -ne ($want -join ',')) {
      $findings += "[CI-SHARD-EXAMPLE] case '$($c.what)' produced code set '$($gotCodes -join ', ')', expected '$($want -join ', ')'. Codes are compared as a SET rather than counted, so a case that swaps one property for another is visible instead of balancing out. [FIX] fix the judge, never the example."
    }
  }
  return $findings
}
