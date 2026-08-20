[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Read-RepoText([string]$RelativePath) {
  Get-Content -Raw -LiteralPath (Join-Path $repoRoot $RelativePath)
}

function Get-FrontMatter([string]$Text) {
  $match = [regex]::Match($Text, '\A---\r?\n(?<body>.*?)\r?\n---(?:\r?\n|\z)', 'Singleline')
  if (-not $match.Success) { return $null }
  return $match.Groups['body'].Value
}

function Get-ScalarField([string]$FrontMatter, [string]$Name) {
  $matches = @([regex]::Matches($FrontMatter, "(?m)^$([regex]::Escape($Name)):\s*(?<value>[^\r\n]*)\r?$"))
  if ($matches.Count -ne 1) { return $null }
  return $matches[0].Groups['value'].Value
}

function Get-ListField([string]$FrontMatter, [string]$Name) {
  $lines = @($FrontMatter -split '\r?\n')
  $start = [Array]::IndexOf($lines, "${Name}:")
  if ($start -lt 0) { return @() }
  $items = [System.Collections.Generic.List[string]]::new()
  for ($index = $start + 1; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match '^  - (?<value>.*)$') {
      [void]$items.Add($Matches['value'])
      continue
    }
    break
  }
  return @($items)
}

$boardRows = @(
  '| W0 | T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | 单分片与 all 汇总以稳定哨兵点名失败 shard/gate（TD9 1/5） | T0-DEBT-SELFTEST-CRITICAL-PATH | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `b8dee45`，PR #31；稳定 ASCII gate、协议 fail-closed、hermetic/mutation 覆盖、core/verify/R3 绿；TD9 仍 carded） |',
  '| W0 | T0-DEBT-SELFTEST-SKIP-VISIBILITY | 有意 skip 与前置失败裁剪进入确定性执行台账（TD9 2/5） | T0-DEBT-SELFTEST-CRITICAL-PATH + T0-LICENSE-SELFTEST-DRIFT | M | GPT-5.6 Terra · high | Sonnet 5 max | PR #33 收回为 skip 协议 + bounded helper；生产 no-git routing 与 mutation 预算已拆卡 |',
  '| W0 | T0-DEBT-SELFTEST-NOGIT-ROUTING | 有界 fixture mode 证明生产 seeded git-present/absent routing 与 outcome ledger（TD9 3/5） | T0-DEBT-SELFTEST-SKIP-VISIBILITY | M | GPT-5.6 Terra · high | Sonnet 5 max | 与 mutation/load 卡共享 selftest，串行宽度 1 |',
  '| W0 | T0-DEBT-SELFTEST-MUTATION-BUDGET | parse-once 紧凑 identity inventory，消除数百份整脚本 mutation 副本（TD9 4/5） | T0-DEBT-SELFTEST-NOGIT-ROUTING | M | GPT-5.6 Terra · high | Sonnet 5 max | R3 实测旧形态约 1.6 GB / 500+ CPU 秒；须有机器预算上界 |',
  '| W0 | T0-DEBT-SELFTEST-LOAD-STABILITY | 8.2e 用具名有界预算承受超过五秒的 runner 调度延迟（TD9 5/5） | T0-DEBT-SELFTEST-MUTATION-BUDGET | M | GPT-5.6 Terra · high | Sonnet 5 max | 五卡全 merged + post-merge core 重放后才可 paid |'
)
$trackerChain = '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`'
$trackerGuard = '全部 merged + post-merge core 重放后才可 paid'
$planChain = '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`'
$planWidth = '四卡均修改 `scripts/selftest.ps1`，执行宽度固定为 1。'

$cardContract = [ordered]@{
  Plan = @{
    Path = 'specs/tasks/T0-DEBT-SELFTEST-SPLIT-PLAN.md'
    Scalars = [ordered]@{ status = 'todo' }
    Lists = [ordered]@{ allow_paths = @('scripts/check-td9-split.ps1') }
    Contains = [ordered]@{}
  }
  Skip = @{
    Path = 'specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md'
    Scalars = [ordered]@{
      status = 'todo'
      depends_on = '[T0-DEBT-SELFTEST-CRITICAL-PATH, T0-LICENSE-SELFTEST-DRIFT]'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-ledger'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      non_goals = @(
        '在 core 内启动完整 seeded，或证明 seeded 的生产 no-git routing；该行为归 T0-DEBT-SELFTEST-NOGIT-ROUTING',
        '建立全量 per-gate mutation 矩阵；紧凑身份清单与资源预算归 T0-DEBT-SELFTEST-MUTATION-BUDGET'
      )
    }
    Contains = [ordered]@{
      dod_assert = @('bounded helper 控制组 count=0')
      hygiene = @('`-Fixture skip-ledger` 在进入任何 shard 前退出')
    }
  }
  NoGit = @{
    Path = 'specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md'
    Scalars = [ordered]@{
      status = 'todo'
      depends_on = '[T0-DEBT-SELFTEST-SKIP-VISIBILITY]'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture seeded-nogit-routing'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @('从 core 启动完整 seeded 分片', '以自由文本或部分 OK 文案推断 PASS/SKIP/FAIL')
      non_goals = @('mutation harness 的内存与 CPU 预算收敛')
    }
    Contains = [ordered]@{
      dod_assert = @('git-present 控制组 skip count=0', '每个登记 gate 的 PASS/SKIP/FAIL 互斥')
      hygiene = @('`-Fixture seeded-nogit-routing` 在生产 routing 后、进入 seeded 套件前退出')
    }
  }
  Mutation = @{
    Path = 'specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md'
    Scalars = [ordered]@{
      status = 'todo'
      depends_on = '[T0-DEBT-SELFTEST-NOGIT-ROUTING]'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-mutation-budget'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @('保留数百份完整 selftest 源码副本', '为降低资源而删掉 reason、gate、batch truncation 或 FAIL/SKIP overlap 的变异证明')
      non_goals = @('生产 no-git 路由行为')
    }
    Contains = [ordered]@{
      dod_assert = @('parse-once 的紧凑身份清单加有界代表性变异证明', 'core 不物化或重解析数百份 11k 行整脚本')
      hygiene = @('`-Fixture skip-mutation-budget` 在进入任何 shard 前退出')
    }
  }
  Load = @{
    Path = 'specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md'
    Scalars = [ordered]@{
      status = 'todo'
      depends_on = '[T0-DEBT-SELFTEST-MUTATION-BUDGET]'
      doc_sync = '五张 TD9 卡全部 merged 且 post-merge core 重放稳定后，才可把 TD9 置 paid'
    }
    Lists = [ordered]@{ allow_paths = @('scripts/selftest.ps1') }
    Contains = [ordered]@{}
  }
}

function Test-Td9SplitContract([hashtable]$Sources) {
  $actualBoardRows = @($Sources.Board -split '\r?\n' | Where-Object { $_ -match '^\| W0 \| T0-DEBT-SELFTEST-(FAIL-DIAGNOSTICS|SKIP-VISIBILITY|NOGIT-ROUTING|MUTATION-BUDGET|LOAD-STABILITY) \|' })
  if ($actualBoardRows.Count -ne $boardRows.Count) { return $false }
  for ($index = 0; $index -lt $boardRows.Count; $index++) {
    if ($actualBoardRows[$index] -cne $boardRows[$index]) { return $false }
  }

  $trackerRows = @($Sources.Tracker -split '\r?\n' | Where-Object { $_ -match '^\| TD9 \|' })
  if ($trackerRows.Count -ne 1) { return $false }
  $trackerColumns = @($trackerRows[0] -split '\|' | ForEach-Object { $_.Trim() })
  if ($trackerColumns.Count -ne 9 -or $trackerColumns[1] -cne 'TD9' -or $trackerColumns[6] -cne 'carded' -or
      -not $trackerColumns[7].Contains($trackerChain, [System.StringComparison]::Ordinal) -or
      -not $trackerColumns[7].Contains($trackerGuard, [System.StringComparison]::Ordinal)) {
    return $false
  }

  foreach ($sourceName in $cardContract.Keys) {
    $frontMatter = Get-FrontMatter $Sources[$sourceName]
    if ($null -eq $frontMatter) { return $false }
    foreach ($fieldName in $cardContract[$sourceName].Scalars.Keys) {
      if ((Get-ScalarField $frontMatter $fieldName) -cne $cardContract[$sourceName].Scalars[$fieldName]) { return $false }
    }
    foreach ($fieldName in $cardContract[$sourceName].Lists.Keys) {
      $actualItems = @(Get-ListField $frontMatter $fieldName)
      foreach ($expectedItem in $cardContract[$sourceName].Lists[$fieldName]) {
        if ($expectedItem -cnotin $actualItems) { return $false }
      }
    }
    foreach ($fieldName in $cardContract[$sourceName].Contains.Keys) {
      $actualValue = Get-ScalarField $frontMatter $fieldName
      if ($null -eq $actualValue) { return $false }
      foreach ($expectedText in $cardContract[$sourceName].Contains[$fieldName]) {
        if (-not $actualValue.Contains($expectedText, [System.StringComparison]::Ordinal)) { return $false }
      }
    }
  }

  $planFrontMatter = Get-FrontMatter $Sources.Plan
  $planBody = $Sources.Plan.Substring($Sources.Plan.IndexOf("`n---", 4, [System.StringComparison]::Ordinal) + 4)
  if ($null -eq $planFrontMatter -or
      -not $planBody.Contains($planChain, [System.StringComparison]::Ordinal) -or
      -not $planBody.Contains($planWidth, [System.StringComparison]::Ordinal)) {
    return $false
  }
  return $true
}

function Copy-Sources([hashtable]$Sources) {
  $copy = @{}
  foreach ($key in $Sources.Keys) { $copy[$key] = $Sources[$key] }
  return $copy
}

function Assert-MutantKilled([string]$Name, [hashtable]$Mutant) {
  if (Test-Td9SplitContract $Mutant) { throw "[TD9-SPLIT-MUTANT-SURVIVED] $Name" }
}

$sources = @{
  Board = Read-RepoText 'docs/TASK-BOARD.md'
  Tracker = Read-RepoText 'specs/tech-debt-tracker.md'
}
foreach ($sourceName in $cardContract.Keys) {
  $sources[$sourceName] = Read-RepoText $cardContract[$sourceName].Path
}
if (-not (Test-Td9SplitContract $sources)) { throw '[TD9-SPLIT-CONTRACT] live documents do not match the canonical TD9 split' }

$deletionCount = 0
foreach ($row in $boardRows) {
  $mutant = Copy-Sources $sources
  $mutant.Board = $mutant.Board.Replace($row, '')
  Assert-MutantKilled "delete-board-$deletionCount" $mutant
  $deletionCount++
}
foreach ($needle in @($trackerChain, $trackerGuard)) {
  $mutant = Copy-Sources $sources
  $mutant.Tracker = $mutant.Tracker.Replace($needle, '')
  Assert-MutantKilled "delete-tracker-$deletionCount" $mutant
  $deletionCount++
}
foreach ($needle in @($planChain, $planWidth)) {
  $mutant = Copy-Sources $sources
  $mutant.Plan = $mutant.Plan.Replace($needle, '')
  Assert-MutantKilled "delete-plan-$deletionCount" $mutant
  $deletionCount++
}
foreach ($sourceName in $cardContract.Keys) {
  $contract = $cardContract[$sourceName]
  foreach ($fieldName in $contract.Scalars.Keys) {
    $needle = "${fieldName}: $($contract.Scalars[$fieldName])"
    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($needle, '')
    Assert-MutantKilled "delete-$sourceName-$fieldName" $mutant
    $deletionCount++
  }
  foreach ($fieldName in $contract.Lists.Keys) {
    foreach ($item in $contract.Lists[$fieldName]) {
      $mutant = Copy-Sources $sources
      $mutant[$sourceName] = $mutant[$sourceName].Replace("  - $item", '')
      Assert-MutantKilled "delete-$sourceName-$fieldName-$deletionCount" $mutant
      $deletionCount++
    }
  }
  foreach ($fieldName in $contract.Contains.Keys) {
    foreach ($text in $contract.Contains[$fieldName]) {
      $mutant = Copy-Sources $sources
      $mutant[$sourceName] = $mutant[$sourceName].Replace($text, '')
      Assert-MutantKilled "delete-$sourceName-$fieldName-$deletionCount" $mutant
      $deletionCount++
    }
  }
}

$boardOrderMutant = Copy-Sources $sources
$boardOrderMutant.Board = $boardOrderMutant.Board.Replace($boardRows[1], '__TD9_BOARD_SWAP__').Replace($boardRows[2], $boardRows[1]).Replace('__TD9_BOARD_SWAP__', $boardRows[2])
Assert-MutantKilled 'reorder-board' $boardOrderMutant
$trackerOrderMutant = Copy-Sources $sources
$trackerOrderMutant.Tracker = $trackerOrderMutant.Tracker.Replace($trackerChain, '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-LOAD-STABILITY`')
Assert-MutantKilled 'reorder-tracker' $trackerOrderMutant

$weakeningMutations = @(
  @{ Name = 'weaken-skip-command'; Source = 'Skip'; Field = 'dod_command'; To = "pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-nogit-command'; Source = 'NoGit'; Field = 'dod_command'; To = "pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-mutation-command'; Source = 'Mutation'; Field = 'dod_command'; To = "pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-paid-guard'; Source = 'Load'; Field = 'doc_sync'; To = 'TD9 paid' }
)
foreach ($case in $weakeningMutations) {
  $mutant = Copy-Sources $sources
  $from = "$($case.Field): $($cardContract[$case.Source].Scalars[$case.Field])"
  $mutant[$case.Source] = $mutant[$case.Source].Replace($from, "$($case.Field): $($case.To)")
  Assert-MutantKilled $case.Name $mutant
}

$structuralWeakening = @(
  @{ Name = 'weaken-tracker-status'; Source = 'Tracker'; From = '| major | carded |'; To = '| major | paid |' },
  @{ Name = 'weaken-board-serial'; Source = 'Board'; From = '与 mutation/load 卡共享 selftest，串行宽度 1'; To = '与 mutation/load 卡共享 selftest，可并行' },
  @{ Name = 'weaken-plan-width'; Source = 'Plan'; From = $planWidth; To = '四卡均修改 `scripts/selftest.ps1`，可并行。' },
  @{ Name = 'weaken-skip-status'; Source = 'Skip'; From = 'status: todo'; To = 'status: merged' },
  @{ Name = 'weaken-nogit-status'; Source = 'NoGit'; From = 'status: todo'; To = 'status: merged' },
  @{ Name = 'weaken-mutation-status'; Source = 'Mutation'; From = 'status: todo'; To = 'status: merged' },
  @{ Name = 'weaken-load-status'; Source = 'Load'; From = 'status: todo'; To = 'status: merged' },
  @{ Name = 'weaken-skip-path'; Source = 'Skip'; From = '  - scripts/selftest.ps1'; To = '  - scripts/other.ps1' },
  @{ Name = 'weaken-nogit-path'; Source = 'NoGit'; From = '  - scripts/selftest.ps1'; To = '  - scripts/other.ps1' },
  @{ Name = 'weaken-mutation-path'; Source = 'Mutation'; From = '  - scripts/selftest.ps1'; To = '  - scripts/other.ps1' },
  @{ Name = 'weaken-load-path'; Source = 'Load'; From = '  - scripts/selftest.ps1'; To = '  - scripts/other.ps1' }
)
foreach ($case in $structuralWeakening) {
  $mutant = Copy-Sources $sources
  $mutant[$case.Source] = $mutant[$case.Source].Replace($case.From, $case.To)
  Assert-MutantKilled $case.Name $mutant
}

$decoyCount = 0
foreach ($sourceName in @('Skip', 'NoGit', 'Mutation', 'Load')) {
  foreach ($fieldName in @('depends_on', 'dod_command')) {
    if (-not $cardContract[$sourceName].Scalars.Contains($fieldName)) { continue }
    $expected = $cardContract[$sourceName].Scalars[$fieldName]
    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace("${fieldName}: $expected", "${fieldName}: INVALID") + "`n`n${fieldName}: $expected`n"
    Assert-MutantKilled "decoy-$sourceName-$fieldName" $mutant
    $decoyCount++
  }
}

Write-Host "[TD9-SPLIT-CONTRACT] PASS deletion=$deletionCount reorder=2 weakening=$($weakeningMutations.Count + $structuralWeakening.Count) decoy=$decoyCount"
