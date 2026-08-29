[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Read-RepoText([string]$RelativePath) {
  Get-Content -Raw -LiteralPath (Join-Path $repoRoot $RelativePath)
}

function Resolve-TaskCardPath {
  param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$TaskId
  )

  $candidates = @(
    "specs/tasks/$TaskId.md"
    "specs/archive/tasks/$TaskId.md"
  )
  $existing = @($candidates | Where-Object { Test-Path -LiteralPath (Join-Path $Root $_) -PathType Leaf })
  if ($existing.Count -ne 1) {
    throw "[TD9-SPLIT-CARD-RESOLUTION] expected one live or archived card for $TaskId, found $($existing.Count)"
  }
  return $existing[0]
}

function Read-TaskCardText {
  param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$TaskId
  )
  $relativePath = Resolve-TaskCardPath -Root $Root -TaskId $TaskId
  return Get-Content -Raw -LiteralPath (Join-Path $Root $relativePath)
}

function Read-ContractCardText {
  param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][hashtable]$Contract
  )
  if ($Contract.ContainsKey('TaskId')) {
    return Read-TaskCardText -Root $Root -TaskId $Contract.TaskId
  }
  if ($Contract.ContainsKey('Path')) {
    return Get-Content -Raw -LiteralPath (Join-Path $Root $Contract.Path)
  }
  throw '[TD9-SPLIT-CARD-CONTRACT] card source has neither TaskId nor Path'
}

function Get-FrontMatter([string]$Text) {
  $match = [regex]::Match($Text, '\A---\r?\n(?<body>.*?)\r?\n---(?:\r?\n|\z)', 'Singleline')
  if (-not $match.Success) { return $null }
  return $match.Groups['body'].Value
}

function Get-MarkdownBodyLines([string]$Text) {
  $match = [regex]::Match($Text, '\A---\r?\n.*?\r?\n---(?:\r?\n|\z)(?<body>.*)\z', 'Singleline')
  if (-not $match.Success) { return $null }
  return @($match.Groups['body'].Value -split '\r?\n')
}

function Get-ScalarField([string]$FrontMatter, [string]$Name) {
  $matches = @([regex]::Matches($FrontMatter, "(?m)^$([regex]::Escape($Name)):\s*(?<value>[^\r\n]*)\r?$"))
  if ($matches.Count -ne 1) { return $null }
  return $matches[0].Groups['value'].Value
}

function Get-ListField([string]$FrontMatter, [string]$Name) {
  $lines = @($FrontMatter -split '\r?\n')
  $starts = @($lines | ForEach-Object -Begin { $index = -1 } -Process { $index++; if ($_ -ceq "${Name}:") { $index } })
  if ($starts.Count -ne 1) { return $null }
  $start = $starts[0]
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

function Assert-TaskCardLifecycleFixture([hashtable]$Contract) {
  $fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "td9-card-$([guid]::NewGuid().ToString('N'))"
  $liveDir = Join-Path $fixtureRoot 'specs/tasks'
  $archiveDir = Join-Path $fixtureRoot 'specs/archive/tasks'
  $cardName = "$($Contract.TaskId).md"
  $liveCard = Join-Path $liveDir $cardName
  $archiveCard = Join-Path $archiveDir $cardName
  try {
    New-Item -ItemType Directory -Force -Path $liveDir, $archiveDir | Out-Null
    Set-Content -LiteralPath $archiveCard -Value 'archived' -Encoding utf8NoBOM
    try { $archiveText = Read-ContractCardText -Root $fixtureRoot -Contract $Contract } catch {
      throw "[TD9-SPLIT-ARCHIVE-FIXTURE] archive-only consumer failed: $($_.Exception.Message)"
    }
    if ($archiveText.Trim() -cne 'archived') {
      throw '[TD9-SPLIT-ARCHIVE-FIXTURE] archive-only card was not read through the consumer'
    }

    Remove-Item -LiteralPath $archiveCard -Force
    Set-Content -LiteralPath $liveCard -Value 'live' -Encoding utf8NoBOM
    try { $liveText = Read-ContractCardText -Root $fixtureRoot -Contract $Contract } catch {
      throw "[TD9-SPLIT-ARCHIVE-FIXTURE] live-only consumer failed: $($_.Exception.Message)"
    }
    if ($liveText.Trim() -cne 'live') {
      throw '[TD9-SPLIT-ARCHIVE-FIXTURE] live-only card was not read through the consumer'
    }

    Set-Content -LiteralPath $archiveCard -Value 'archived' -Encoding utf8NoBOM
    $duplicateRejected = $false
    try { [void](Read-ContractCardText -Root $fixtureRoot -Contract $Contract) } catch {
      if ($_.Exception.Message.Contains('[TD9-SPLIT-CARD-RESOLUTION]')) { $duplicateRejected = $true } else { throw }
    }
    if (-not $duplicateRejected) { throw '[TD9-SPLIT-ARCHIVE-FIXTURE] duplicate live/archive card was accepted' }

    Remove-Item -LiteralPath $liveCard, $archiveCard -Force
    $missingRejected = $false
    try { [void](Read-ContractCardText -Root $fixtureRoot -Contract $Contract) } catch {
      if ($_.Exception.Message.Contains('[TD9-SPLIT-CARD-RESOLUTION]')) { $missingRejected = $true } else { throw }
    }
    if (-not $missingRejected) { throw '[TD9-SPLIT-ARCHIVE-FIXTURE] missing card was accepted' }
  } finally {
    Remove-Item -LiteralPath $liveCard, $archiveCard -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $liveDir, $archiveDir -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'specs/archive'), (Join-Path $fixtureRoot 'specs'), $fixtureRoot -Force -ErrorAction SilentlyContinue
  }
}

$boardRows = @(
  '| W0 | T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | 单分片与 all 汇总以稳定哨兵点名失败 shard/gate（TD9 1/5） | T0-DEBT-SELFTEST-CRITICAL-PATH | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `b8dee45`，PR #31；稳定 ASCII gate、协议 fail-closed、hermetic/mutation 覆盖、core/verify/R3 绿；TD9 仍 carded） |',
  '| W0 | T0-DEBT-SELFTEST-SKIP-VISIBILITY | 有意 skip 与前置失败裁剪进入确定性执行台账（TD9 2/5） | T0-DEBT-SELFTEST-CRITICAL-PATH + T0-LICENSE-SELFTEST-DRIFT | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `c745015`，PR #33；机器 skip 台账、汇总与 bounded helper，core/verify/R3 PASS；生产 no-git routing 与 mutation 预算按卡拆分） |',
  '| W0 | T0-DEBT-SELFTEST-NOGIT-ROUTING | 有界 fixture mode 证明生产 seeded git-present/absent routing 与 outcome ledger（TD9 3/5） | T0-DEBT-SELFTEST-SKIP-VISIBILITY | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `02425dd`，PR #110；完整 130-gate 身份、nonce 子进程与 route inversion mutation；DoD/verify 绿，两轮 R3 finding 修复后人裁；TD9 仍 carded） |',
  '| W0 | T0-DEBT-SELFTEST-MUTATION-BUDGET | parse-once 紧凑 identity inventory，消除数百份整脚本 mutation 副本（TD9 4/5） | T0-DEBT-SELFTEST-NOGIT-ROUTING | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `86a895a9`，PR #187；91 个候选只执行 4 个代表性变异，峰值完整源码集合 2、终态 1；DoD/verify/R3 PASS；TD9 仍 carded） |',
  '| W0 | T0-DEBT-SELFTEST-LOAD-STABILITY | 8.2e 用具名有界预算承受超过五秒的 runner 调度延迟（TD9 5/5） | T0-DEBT-SELFTEST-MUTATION-BUDGET | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `95f74222`，PR #189；具名默认/注入预算、marker handshake、timeout/early-exit/cleanup mutations；DoD/focused gate8/verify/R3 PASS；TD9 仍 carded，等待 post-merge core 重放） |'
)
$trackerChain = '`T0-DEBT-SELFTEST-LOAD-STABILITY`'
$trackerGuard = '仅余一次 post-merge core 重放，稳定后才可把 TD9 置 paid'
$planChain = '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`'
$planWidth = '四卡均修改 `scripts/selftest.ps1`，执行宽度固定为 1。'

$cardContract = [ordered]@{
  Plan = @{
    TaskId = 'T0-DEBT-SELFTEST-SPLIT-PLAN'
    Scalars = [ordered]@{
      id = 'T0-DEBT-SELFTEST-SPLIT-PLAN'
      title = '将 TD9 skip 可见性余项拆成有界串行卡'
      depends_on = '[]'
      status = 'merged'
      branch = 'T0-DEBT-SELFTEST-SPLIT-PLAN'
      worktree = 'C:\wt\T0-DEBT-SELFTEST-SPLIT-PLAN'
      dod_command = 'pwsh -NoProfile -File scripts/check-td9-split.ps1'
      dod_exit = '0'
      dod_assert = '原 skip 卡明确收回到 bounded helper 协议；生产 no-git routing 与 mutation 资源预算各有独立任务卡；TD9、全部 live 卡与 TASK-BOARD 记录同一串行顺序。'
      review_gate = 'codex {verdict:pass}'
      hygiene = 'check-td9-split.ps1 解析 canonical board/tracker/card 语义，并在内存中逐项删除、换序与弱化；任一 mutant 存活即非零；两个实现卡共享 selftest 因而必须串行。'
      doc_sync = '本规划卡合并后标 merged；TD9 保持 carded，直到全部子卡与 post-merge core 重放完成。'
    }
    Lists = [ordered]@{
      allow_paths = @(
        'specs/tasks/T0-DEBT-SELFTEST-SPLIT-PLAN.md',
        'specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md',
        'specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md',
        'specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md',
        'specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md',
        'specs/tech-debt-tracker.md',
        'docs/TASK-BOARD.md',
        'scripts/check-td9-split.ps1'
      )
      forbid = @(
        '修改 scripts/selftest.ps1 或启动任何 selftest 分片',
        '把后续卡标成并行，或宣称 TD9 已偿还',
        '用缩窄卡片掩盖 PR #33 已引入且仍可达的缺陷'
      )
      non_goals = @('实现 no-git routing 夹具或 mutation 预算收敛', '合并或关闭 PR #33')
    }
    BodyClauses = @(
      'PR #33 的 R3 实测指出两类不同交付单元：生产 no-git 路由的行为证明，以及 mutation harness 的资源确定性。继续塞回原卡会同时扩大行为面与验证成本，因此先把原卡收回到 skip 协议本身，再串行偿还两项余债。',
      $planChain,
      $planWidth
    )
  }
  Skip = @{
    TaskId = 'T0-DEBT-SELFTEST-SKIP-VISIBILITY'
    Scalars = [ordered]@{
      id = 'T0-DEBT-SELFTEST-SKIP-VISIBILITY'
      title = '让 selftest 有意跳过与前置失败裁剪均可见'
      status = 'merged'
      depends_on = '[T0-DEBT-SELFTEST-CRITICAL-PATH, T0-LICENSE-SELFTEST-DRIFT]'
      branch = 'T0-DEBT-SELFTEST-SKIP-VISIBILITY'
      worktree = 'C:\wt\T0-DEBT-SELFTEST-SKIP-VISIBILITY'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-ledger'
      dod_exit = '0'
      dod_assert = '每个有意环境跳过或因已知前置失败而不执行的已登记检查输出 [SELFTEST-SKIP] gate={id} reason={stable code}；分片终态输出有序去重 [SELFTEST-SKIP-SUMMARY] 与准确 count；失败后的裁剪不可继续静默，也不得输出该检查 PASS；bounded helper 控制组 count=0。'
      review_gate = 'codex {verdict:pass}'
      hygiene = '`-Fixture skip-ledger` 在进入任何 shard 前退出；用 bounded helper 的环境缺失、前置失败、正常执行三组夹具证明 FAIL/SKIP/PASS 互斥；删除 skip 记录、reason code 或摘要计数任一层均翻红；完整 8.2e 只作附加证据'
      doc_sync = 'TD9 保持 carded；本卡只偿还 skip 可见性，不宣称 8.2e load-flake 已解决'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @(
        '把 skip 计作 PASS、把可选环境缺失升级为失败或把真实失败降级为 skip',
        '以通过日志缺行反推 skip 数量，或只保留自由文本跳过说明而无机器台账',
        '为追求全量执行而移除既有前置条件、隔离条件或 fail-safe 边界'
      )
      non_goals = @(
        '修改失败闸聚合协议或 8.2e rendezvous 时限',
        '重新编号 17 个顶层闸或重分 core/workflow/seeded',
        '改 CI/workflow/task/review 行为',
        '在 core 内启动完整 seeded，或证明 seeded 的生产 no-git routing；该行为归 T0-DEBT-SELFTEST-NOGIT-ROUTING',
        '建立全量 per-gate mutation 矩阵；紧凑身份清单与资源预算归 T0-DEBT-SELFTEST-MUTATION-BUDGET'
      )
    }
    BodyClauses = @(
      '失败 run 与同 SHA 通过 run 的日志差异显示大量后续检查无 PASS、FAIL 或 skip 终态。建立明确执行台账，区分“可选环境未满足”和“前置失败导致裁剪”，不再靠缺行猜测。',
      '- 只登记真实可独立判定的检查；成功文案被抑制不自动等于检查未执行。',
      '- reason 使用稳定 ASCII code，prose 可继续服务人工阅读但不参与机器判定。',
      '- 同一检查同一原因只登记一次；摘要顺序确定，重复运行结果稳定。',
      '- 已失败检查仍是 FAIL；被裁剪检查才是 SKIP，二者不得互相覆盖。',
      '本卡与 TD9 另外三张未合并余卡及 TD134 实现卡共享 `scripts/selftest.ps1`；后续三卡按显式语义依赖串行，本卡也必须在最新已合并基线上执行并重放验收。',
      'PR #33 的两轮 R3 证明，生产 no-git routing 行为与 mutation 资源预算不能继续塞进同一评审单元。本卡只保留 skip primitive、机器台账、摘要与 bounded helper 互斥证明；两项余债按后续卡串行偿还，不能以本卡合并宣称完成。'
    )
  }
  NoGit = @{
    TaskId = 'T0-DEBT-SELFTEST-NOGIT-ROUTING'
    Scalars = [ordered]@{
      id = 'T0-DEBT-SELFTEST-NOGIT-ROUTING'
      title = '用有界生产夹具证明 seeded no-git 路由'
      status = 'merged'
      depends_on = '[T0-DEBT-SELFTEST-SKIP-VISIBILITY]'
      branch = 'T0-DEBT-SELFTEST-NOGIT-ROUTING'
      worktree = 'C:\wt\T0-DEBT-SELFTEST-NOGIT-ROUTING'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture seeded-nogit-routing'
      dod_exit = '0'
      dod_assert = '专用有界 fixture mode 直接走生产 routing；git-present 控制组 skip count=0，git-absent 组输出完整机器记录与准确摘要；每个登记 gate 的 PASS/SKIP/FAIL 互斥，夹具在 routing 后立即退出且不进入完整 seeded 套件。'
      review_gate = 'codex {verdict:pass}'
      hygiene = '`-Fixture seeded-nogit-routing` 在生产 routing 后、进入 seeded 套件前退出；先以反转生产路由条件的单句变异证明旧接线可逃逸；断言机器 ledger，不枚举易漂移的人类 OK 文案；完整 8.2e 只作附加证据。'
      doc_sync = '合并后更新 TD9 指针；TD9 仍保持 carded，等待 mutation-budget、load-stability 与 post-merge core。'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @('从 core 启动完整 seeded 分片', '以自由文本或部分 OK 文案推断 PASS/SKIP/FAIL', '改变既有 gate 编号、分片归属或真实 git 检测语义')
      non_goals = @('mutation harness 的内存与 CPU 预算收敛', '8.2e rendezvous 负载稳定性')
    }
    BodyClauses = @(
      'helper 级环境缺失夹具只能证明 skip primitive，不能证明 seeded 的生产路由条件确实调用该 primitive；反转条件仍可能令现有静态 hash 与 helper 夹具全绿。',
      '夹具复用生产路由判定与机器 outcome ledger，但在该路由完成后立即终止。禁止为了“真实”而从 core 重跑完整 seeded。'
    )
  }
  Mutation = @{
    TaskId = 'T0-DEBT-SELFTEST-MUTATION-BUDGET'
    Scalars = [ordered]@{
      id = 'T0-DEBT-SELFTEST-MUTATION-BUDGET'
      title = '将 skip mutation 证明收敛到紧凑身份清单'
      status = 'merged'
      depends_on = '[T0-DEBT-SELFTEST-NOGIT-ROUTING]'
      branch = 'T0-DEBT-SELFTEST-MUTATION-BUDGET'
      worktree = 'C:\wt\T0-DEBT-SELFTEST-MUTATION-BUDGET'
      dod_command = 'pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-mutation-budget'
      dod_exit = '0'
      dod_assert = 'skip 接线由 parse-once 的紧凑身份清单加有界代表性变异证明；core 不物化或重解析数百份 11k 行整脚本；预算诊断输出稳定 ASCII 哨兵，删除任一必要变异仍翻红。'
      review_gate = 'codex {verdict:pass}'
      hygiene = '`-Fixture skip-mutation-budget` 在进入任何 shard 前退出；记录候选 mutation 数、实际执行数与峰值集合大小；用上界断言锁住回归，不以单机偶然耗时作为唯一判据；完整 8.2e 只作附加证据。'
      doc_sync = '合并后更新 TD9 指针；TD9 仍保持 carded，等待 load-stability 与 post-merge core。'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @('保留数百份完整 selftest 源码副本', '为降低资源而删掉 reason、gate、batch truncation 或 FAIL/SKIP overlap 的变异证明', '改变 skip 的运行时语义')
      non_goals = @('生产 no-git 路由行为', 'workflow/seeded/core 重分片')
    }
    BodyClauses = @(
      '当前 harness 为每个 reason/gate 变异物化一份完整 selftest 字符串，并对每份重跑 whole-AST 扫描。R3 实测约 1.6 GB 工作集与 500+ CPU 秒，确定性受 runner 资源影响。',
      '生产接线先 parse once 投影为紧凑 identity inventory；行为变异只保留能独立杀死 reason、gate、batch truncation 与 outcome overlap 的代表集合，并用机器预算哨兵锁定候选数和在存集合上界。'
    )
  }
  Load = @{
    TaskId = 'T0-DEBT-SELFTEST-LOAD-STABILITY'
    Scalars = [ordered]@{
      id = 'T0-DEBT-SELFTEST-LOAD-STABILITY'
      title = '消除 8.2e 高负载下固定五秒 rendezvous 假红'
      status = 'merged'
      depends_on = '[T0-DEBT-SELFTEST-MUTATION-BUDGET]'
      branch = 'T0-DEBT-SELFTEST-LOAD-STABILITY'
      worktree = 'C:\wt\T0-DEBT-SELFTEST-LOAD-STABILITY'
      dod_command = 'pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch ''[SELFTEST-8.2E-RENDEZVOUS]'') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch ''SCAFFOLD_SELFTEST_STUB_READY_TIMEOUT_SECONDS''))) { exit 1 }"'
      dod_exit = '0'
      dod_assert = '8.2e rendezvous 使用具名有界预算并输出 [SELFTEST-8.2E-RENDEZVOUS]；第二长分片延迟超过旧 5 秒仍通过并保留并发证明；注入短预算的真实 timeout 以专属诊断非零；删除等待上限、ready 条件或并发重叠断言均被变异击杀。'
      review_gate = 'codex {verdict:pass}'
      hygiene = 'hermetic 夹具提供 load-delay、bounded-timeout 与正常控制组；断言实际 elapsed/ready ticks 和退出语义，不以 Start-Sleep 后“没报错”作假证明'
      doc_sync = '五张 TD9 卡全部 merged 且 post-merge core 重放稳定后，才可把 TD9 置 paid'
    }
    Lists = [ordered]@{
      allow_paths = @('scripts/selftest.ps1')
      forbid = @('删除长分片并发、core 错峰、dirty overlay、StrictLint 三态或失败传播任一证明', '用无限等待、无上限重试或吞掉 timeout 让夹具假绿', '只把固定 5 秒换成另一个未验证的魔数')
      non_goals = @('改生产 all 分片调度、CI matrix/runner 或 post-merge 触发规则', '改失败/skip 观测协议', '优化完整 selftest 墙钟时间')
    }
    BodyClauses = @(
      'run `31941736470` 在同一 SHA 上前两次仅 Ubuntu core 的 8.2e 假红、第三次通过。当前 stub 的首个长分片只等另一个长分片五秒；runner 高负载下，后者尚未获调度就会退出 29。',
      '- timeout 必须有界、具名、可在 hermetic 测试中缩短；默认预算须覆盖实证的调度延迟。',
      '- load-delay 控制组必须超过旧五秒阈值，并证明两个长分片确实重叠而非串行放宽断言。',
      '- timeout 负例必须快速、专属地失败，防无限等待或静默跳过。',
      '- 8.2e 原有五类证明继续各自可证伪，不再用一个合取式告警掩盖具体失败面。',
      'mutation-budget 前置卡已合并；本卡必须从包含 PR #187（master `86a895a9`）的最新基线开工并重放验收。'
    )
  }
}

foreach ($lifecycleContract in @($cardContract.Plan, $cardContract.Skip, $cardContract.NoGit, $cardContract.Mutation, $cardContract.Load)) {
  Assert-TaskCardLifecycleFixture -Contract $lifecycleContract
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
      $expectedItems = @($cardContract[$sourceName].Lists[$fieldName])
      if ($actualItems.Count -ne $expectedItems.Count) { return $false }
      for ($index = 0; $index -lt $expectedItems.Count; $index++) {
        if ($actualItems[$index] -cne $expectedItems[$index]) { return $false }
      }
    }
    $bodyLines = @(Get-MarkdownBodyLines $Sources[$sourceName])
    if ($null -eq $bodyLines) { return $false }
    foreach ($clause in $cardContract[$sourceName].BodyClauses) {
      if ($clause -cnotin $bodyLines) { return $false }
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
  if (Test-Td9SplitContract $Mutant) { throw "[TD9-SPLIT-MUTANT-SURVIVED] $Name" }
}

$sources = @{
  Board = Read-RepoText 'docs/TASK-BOARD.md'
  Tracker = Read-RepoText 'specs/tech-debt-tracker.md'
}
foreach ($sourceName in $cardContract.Keys) {
  $contract = $cardContract[$sourceName]
  $sources[$sourceName] = Read-ContractCardText -Root $repoRoot -Contract $contract
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
$clauseWeakeningCount = 0
foreach ($sourceName in $cardContract.Keys) {
  $contract = $cardContract[$sourceName]
  foreach ($fieldName in $contract.Scalars.Keys) {
    $needle = "${fieldName}: $($contract.Scalars[$fieldName])"
    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($needle, '')
    Assert-MutantKilled "delete-$sourceName-$fieldName" $mutant
    $deletionCount++

    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($needle, "${fieldName}: [WEAKENED]")
    Assert-MutantKilled "weaken-$sourceName-$fieldName" $mutant
    $clauseWeakeningCount++
  }
  foreach ($fieldName in $contract.Lists.Keys) {
    foreach ($item in $contract.Lists[$fieldName]) {
      $mutant = Copy-Sources $sources
      $mutant[$sourceName] = $mutant[$sourceName].Replace("  - $item", '')
      Assert-MutantKilled "delete-$sourceName-$fieldName-$deletionCount" $mutant
      $deletionCount++

      $mutant = Copy-Sources $sources
      $mutant[$sourceName] = $mutant[$sourceName].Replace("  - $item", "  - [WEAKENED] $item")
      Assert-MutantKilled "weaken-$sourceName-$fieldName-$clauseWeakeningCount" $mutant
      $clauseWeakeningCount++
    }

    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace("${fieldName}:", "${fieldName}:`n  - [EXTRA-CLAUSE]")
    Assert-MutantKilled "expand-$sourceName-$fieldName" $mutant
    $clauseWeakeningCount++
  }
  foreach ($clause in $contract.BodyClauses) {
    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($clause, '')
    Assert-MutantKilled "delete-$sourceName-body-$deletionCount" $mutant
    $deletionCount++

    $mutant = Copy-Sources $sources
    $mutant[$sourceName] = $mutant[$sourceName].Replace($clause, "[WEAKENED] $clause")
    Assert-MutantKilled "weaken-$sourceName-body-$clauseWeakeningCount" $mutant
    $clauseWeakeningCount++
  }
}

$boardOrderMutant = Copy-Sources $sources
$boardOrderMutant.Board = $boardOrderMutant.Board.Replace($boardRows[1], '__TD9_BOARD_SWAP__').Replace($boardRows[2], $boardRows[1]).Replace('__TD9_BOARD_SWAP__', $boardRows[2])
Assert-MutantKilled 'reorder-board' $boardOrderMutant
$staleTrackerPointerMutant = Copy-Sources $sources
$staleTrackerPointerMutant.Tracker = $staleTrackerPointerMutant.Tracker.Replace($trackerChain, '`T0-DEBT-SELFTEST-MUTATION-BUDGET`')
Assert-MutantKilled 'stale-tracker-pointer' $staleTrackerPointerMutant

$structuralWeakening = @(
  @{ Name = 'weaken-tracker-status'; Source = 'Tracker'; From = '| major | carded |'; To = '| major | paid |' },
  @{ Name = 'weaken-board-status'; Source = 'Board'; From = 'TD9 仍 carded'; To = 'TD9 paid' }
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

Write-Host "[TD9-SPLIT-CONTRACT] PASS deletion=$deletionCount reorder=2 weakening=$($clauseWeakeningCount + $structuralWeakening.Count) decoy=$decoyCount"
