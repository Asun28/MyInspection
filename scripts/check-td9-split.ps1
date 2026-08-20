[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Read-RepoText([string]$RelativePath) {
  Get-Content -Raw -LiteralPath (Join-Path $repoRoot $RelativePath)
}

$boardRows = @(
  '| W0 | T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | 单分片与 all 汇总以稳定哨兵点名失败 shard/gate（TD9 1/5） | T0-DEBT-SELFTEST-CRITICAL-PATH |',
  '| W0 | T0-DEBT-SELFTEST-SKIP-VISIBILITY | 有意 skip 与前置失败裁剪进入确定性执行台账（TD9 2/5） | T0-DEBT-SELFTEST-CRITICAL-PATH + T0-LICENSE-SELFTEST-DRIFT |',
  '| W0 | T0-DEBT-SELFTEST-NOGIT-ROUTING | 有界 fixture mode 证明生产 seeded git-present/absent routing 与 outcome ledger（TD9 3/5） | T0-DEBT-SELFTEST-SKIP-VISIBILITY |',
  '| W0 | T0-DEBT-SELFTEST-MUTATION-BUDGET | parse-once 紧凑 identity inventory，消除数百份整脚本 mutation 副本（TD9 4/5） | T0-DEBT-SELFTEST-NOGIT-ROUTING |',
  '| W0 | T0-DEBT-SELFTEST-LOAD-STABILITY | 8.2e 用具名有界预算承受超过五秒的 runner 调度延迟（TD9 5/5） | T0-DEBT-SELFTEST-MUTATION-BUDGET |'
)

$requiredBySource = [ordered]@{
  Tracker = @(
    '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`',
    '全部 merged + post-merge core 重放后才可 paid'
  )
  Skip = @(
    '  - 在 core 内启动完整 seeded，或证明 seeded 的生产 no-git routing；该行为归 T0-DEBT-SELFTEST-NOGIT-ROUTING',
    '  - 建立全量 per-gate mutation 矩阵；紧凑身份清单与资源预算归 T0-DEBT-SELFTEST-MUTATION-BUDGET',
    'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-ledger',
    'bounded helper 控制组 count=0',
    '`-Fixture skip-ledger` 在进入任何 shard 前退出'
  )
  NoGit = @(
    'depends_on: [T0-DEBT-SELFTEST-SKIP-VISIBILITY]',
    '  - 从 core 启动完整 seeded 分片',
    '  - 以自由文本或部分 OK 文案推断 PASS/SKIP/FAIL',
    '  - mutation harness 的内存与 CPU 预算收敛',
    'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture seeded-nogit-routing',
    'git-present 控制组 skip count=0',
    '每个登记 gate 的 PASS/SKIP/FAIL 互斥',
    '`-Fixture seeded-nogit-routing` 在生产 routing 后、进入 seeded 套件前退出'
  )
  Mutation = @(
    'depends_on: [T0-DEBT-SELFTEST-NOGIT-ROUTING]',
    '  - 保留数百份完整 selftest 源码副本',
    '  - 为降低资源而删掉 reason、gate、batch truncation 或 FAIL/SKIP overlap 的变异证明',
    '  - 生产 no-git 路由行为',
    'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-mutation-budget',
    'parse-once 的紧凑身份清单加有界代表性变异证明',
    'core 不物化或重解析数百份 11k 行整脚本',
    '`-Fixture skip-mutation-budget` 在进入任何 shard 前退出'
  )
  Load = @(
    'depends_on: [T0-DEBT-SELFTEST-MUTATION-BUDGET]',
    'doc_sync: 五张 TD9 卡全部 merged 且 post-merge core 重放稳定后，才可把 TD9 置 paid'
  )
}

function Test-Td9SplitContract([hashtable]$Sources) {
  $cursor = -1
  foreach ($row in $boardRows) {
    $next = $Sources.Board.IndexOf($row, $cursor + 1, [System.StringComparison]::Ordinal)
    if ($next -lt 0) { return $false }
    $cursor = $next
  }

  foreach ($sourceName in $requiredBySource.Keys) {
    foreach ($needle in $requiredBySource[$sourceName]) {
      if (-not $Sources[$sourceName].Contains($needle, [System.StringComparison]::Ordinal)) {
        return $false
      }
    }
  }
  return $true
}

function Copy-Sources([hashtable]$Sources) {
  $copy = @{}
  foreach ($key in $Sources.Keys) { $copy[$key] = $Sources[$key] }
  return $copy
}

function Assert-MutantKilled([string]$Name, [hashtable]$Mutant) {
  if (Test-Td9SplitContract $Mutant) {
    throw "[TD9-SPLIT-MUTANT-SURVIVED] $Name"
  }
}

$sources = @{
  Board = Read-RepoText 'docs/TASK-BOARD.md'
  Tracker = Read-RepoText 'specs/tech-debt-tracker.md'
  Skip = Read-RepoText 'specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md'
  NoGit = Read-RepoText 'specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md'
  Mutation = Read-RepoText 'specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md'
  Load = Read-RepoText 'specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md'
}

if (-not (Test-Td9SplitContract $sources)) {
  throw '[TD9-SPLIT-CONTRACT] live documents do not match the canonical TD9 split'
}

$deletionCount = 0
foreach ($row in $boardRows) {
  $mutant = Copy-Sources $sources
  $mutant.Board = $mutant.Board.Replace($row, '')
  Assert-MutantKilled "delete-board-$deletionCount" $mutant
  $deletionCount++
}
foreach ($sourceName in $requiredBySource.Keys) {
  foreach ($needle in $requiredBySource[$sourceName]) {
    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($needle, '')
    Assert-MutantKilled "delete-$sourceName-$deletionCount" $mutant
    $deletionCount++
  }
}

$boardOrderMutant = Copy-Sources $sources
$boardOrderMutant.Board = $boardOrderMutant.Board.Replace($boardRows[1], '__TD9_BOARD_SWAP__').Replace($boardRows[2], $boardRows[1]).Replace('__TD9_BOARD_SWAP__', $boardRows[2])
Assert-MutantKilled 'reorder-board' $boardOrderMutant

$trackerOrderMutant = Copy-Sources $sources
$trackerOrderMutant.Tracker = $trackerOrderMutant.Tracker.Replace(
  $requiredBySource.Tracker[0],
  '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-LOAD-STABILITY`'
)
Assert-MutantKilled 'reorder-tracker' $trackerOrderMutant

$weakeningMutations = @(
  @{ Name = 'weaken-skip-command'; Source = 'Skip'; From = $requiredBySource.Skip[2]; To = "dod_command: pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-nogit-command'; Source = 'NoGit'; From = $requiredBySource.NoGit[4]; To = "dod_command: pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-mutation-command'; Source = 'Mutation'; From = $requiredBySource.Mutation[4]; To = "dod_command: pwsh -NoProfile -Command 'exit 0'" },
  @{ Name = 'weaken-paid-guard'; Source = 'Load'; From = $requiredBySource.Load[1]; To = 'doc_sync: TD9 paid' }
)
foreach ($case in $weakeningMutations) {
  $mutant = Copy-Sources $sources
  $mutant[$case.Source] = $mutant[$case.Source].Replace($case.From, $case.To)
  Assert-MutantKilled $case.Name $mutant
}

Write-Host "[TD9-SPLIT-CONTRACT] PASS deletion=$deletionCount reorder=2 weakening=$($weakeningMutations.Count)"
