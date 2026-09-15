#requires -Version 7.4
<#
.SYNOPSIS
  PR review v2 记录核心（RECORDS）：校验 worker 记录 · unit 归属 · C-<n> 铸造 · 精确重复合并 · fingerprint 提示 · missing 覆盖合成。
.DESCRIPTION
  两种模式（协议：docs/PREREVIEW-PROTOCOL.md 第 5 节；契约本体 specs/prereview-record.schema.json）：
    -AsLibrary   只定义函数后立即 return（check-secrets.ps1 的库模式形态）：不读夹具、不触达 git、不 exit。
    -SelfCheck   只读冻结 schema 与 scripts/fixtures/prereview/records/，末行 [PREREVIEW-RECORDS-SELFCHECK-PASS|FAIL]（exit 0|1）；
                 先清掉继承的 PRE_LIVE / PRE_LENS_ENDPOINT，不 spawn 任何进程，不 dot-source 别的脚本。
  输入 = 一个批次（一次 run 两个 worker）的记录列表，即 WORKERS A3 盖章后的 JSONL 行：内容字段 + 六个 provenance 键（snapshot_tree /
  worker_id / model_id / lens / schema_version / schema_revision）。本核心只用 worker_id（local_id 的命名空间 + provenance 列表），
  其余五个键原样带进 provenance 条目；校验前把六个键与内容分开，内容对封闭的 $defs 子形状校验，故 worker 写进记录的 id / verdict /
  missing 都作为未知字段或非法枚举被拒（worker 自写的 provenance 由 WORKERS A3 解包时拒绝；到本核心的六个键一律视为适配器盖章）。
  规则（卡 T0-PREREVIEW-RECORDS A1–A5）：
    · 任何违规都返回 Ok=$false + Code=[PRE-BAD-RECORD] + Reasons[]，不抛错；批内任一违规即整批不铸 id、不出行（fail-closed）。
    · 精确重复键 = file|symbol|category|contract_ref|expected|actual（NFC + 去首尾空白 + 空白串折叠为单个空格），不含行号与
      anchor：同一符号上同一句 expected/actual 无论行号都合并成一条，合并保留每个贡献者的 (worker_id, local_id) 与
      unit_ids / evidence_refs / evidence_needed 的有序并集，所以位置信息不丢（TD176 的局部性决定，写在 master 卡片 A4）；其余标量
      字段取批内首个贡献者（RUN 先传发现者），每个贡献者自己的 kind / severity_guess / anchor / line_start / line_end 记在它的
      provenance 条目里。
    · fingerprint = file|category|symbol|contract_ref 只是分组提示：同 fingerprint 而键不同的记录是「近似重复」，各保留自己的
      id、共享 root_group（= 该 fingerprint 组里最小的 id）并互列 related_to。
    · C-<n> 在一个 state 生命期内单调铸造：调用方把上次返回的 NextId 喂给下一次调用，号码永不复用。candidates 结果带 StartId，
      coverage 从 StartId 重铸并要求结果逐字节相等；每个 worker 对每个 unit 只许一行 coverage；units 文档不许重复 unit_id。
    · missing 行只由本核心合成：每个 unit 若发现者（-DiscovererWorkerId）的 coverage 行 categories_checked 未同时含 C1、C2、C3
      即补一行 status=missing（只看 categories_checked，不看 status；透镜的行是附加的，不参与判定）。
  产出：candidate = id / fingerprint / root_group / related_to / 内容字段（schema 顺序，local_id 进 provenance）/ provenance[]；
  coverage = unit_id / worker_id / categories_checked / status / candidate_ids / missing_context。落盘归 STATE-1A。
#>
# 7.4 下限：Test-Json 的 draft 2020-12 校验（JsonSchema.Net，7.4 起）；ConvertFrom-Json -AsHashtable 自 7.3 起才是保序、区分大小写的
# OrderedHashtable（candidate 字段顺序与 Expected/expected 并存都靠它）；-NoEnumerate。
[CmdletBinding()]
param([switch]$AsLibrary, [switch]$SelfCheck)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:RecordSchemaPath = Join-Path $script:RepoRoot 'specs/prereview-record.schema.json'
$script:BadRecordCode = '[PRE-BAD-RECORD]'
$script:ProvenanceKeys = @('snapshot_tree', 'worker_id', 'model_id', 'lens', 'schema_version', 'schema_revision')
$script:RequiredCategories = @('C1', 'C2', 'C3')
$script:ExactKeyFields = @('file', 'symbol', 'category', 'contract_ref', 'expected', 'actual')
$script:FingerprintFields = @('file', 'category', 'symbol', 'contract_ref')
$script:UnionFields = @('unit_ids', 'evidence_refs', 'evidence_needed')
$script:ContributorFields = @('kind', 'severity_guess', 'anchor', 'line_start', 'line_end')   # 每个贡献者自己的定位与分级，记进其 provenance 条目
$script:SchemaDefs = $null

# ── 库导出区（-AsLibrary 可安全取用：无副作用、不 exit、不 spawn）──
# 键与字典一律序数（区分大小写）：unit_id 是路径、local_id 是字符串，'l1' 与 'L1' 是两个 id（-eq / @{} 默认不敏感，故不用）。
function New-OrdinalMap { return [System.Collections.Hashtable]::new([StringComparer]::Ordinal) }

function Get-PrereviewSchemaDefs {
  # 冻结 schema 的 $defs，进程内只读一次；ConvertFrom-Json -AsHashtable 保留文档顺序，candidate 字段顺序由此得来。
  if ($null -eq $script:SchemaDefs) {
    $obj = [IO.File]::ReadAllText($script:RecordSchemaPath) | ConvertFrom-Json -AsHashtable -Depth 64
    $script:SchemaDefs = $obj['$defs']
  }
  return $script:SchemaDefs
}

function Test-PrereviewRecord {
  # A1：把 $defs 子形状包成 {$ref, $defs} 在内存里校验；唯一判据 = Test-Json 布尔值；坏 JSON 或读不到 schema 都只返回 $false。
  [OutputType([bool])]
  param([Parameter(Mandatory)][ValidateSet('candidate', 'coverage', 'facts', 'units')][string]$Kind, [AllowEmptyString()][string]$Json)
  try {
    $schema = @{ '$ref' = "#/`$defs/$Kind"; '$defs' = (Get-PrereviewSchemaDefs) } | ConvertTo-Json -Depth 64 -Compress
    return [bool](Test-Json -Json $Json -Schema $schema -ErrorAction SilentlyContinue)
  } catch { return $false }
}

function Get-PrereviewLocalKey([string]$WorkerId, [string]$LocalId) {
  # local_id 只在一个 worker 的信封内唯一，故批内身份 = (worker_id, local_id)；JSON 数组编码保证单射（分隔符出现在值里也不撞）。
  # coverage 的 (worker_id, unit_id) 唯一性用同一编码。
  return ConvertTo-Json -InputObject @($WorkerId, $LocalId) -Compress
}

function New-PrereviewResult([string[]]$Reasons, [System.Collections.IDictionary]$Extra) {
  $ok = (@($Reasons).Count -eq 0)
  $r = [ordered]@{ Ok = $ok; Code = $(if ($ok) { '' } else { $script:BadRecordCode }); Reasons = @($Reasons) }
  foreach ($k in $Extra.Keys) { $r[$k] = $Extra[$k] }
  return [pscustomobject]$r
}

function Read-PrereviewRecords {
  # 一行一条 JSON 对象（WORKERS 解包后的 JSONL）；空行跳过；任一行不是 JSON 对象即 [PRE-BAD-RECORD]（点名行号），不抛。
  param([Parameter(Mandatory)][string]$Path)
  $reasons = @(); $records = [System.Collections.Generic.List[object]]::new()
  try { $lines = [IO.File]::ReadAllLines($ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)) } catch { return New-PrereviewResult @("cannot read $Path") ([ordered]@{ Records = @() }) }
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ([string]::IsNullOrWhiteSpace($lines[$i])) { continue }
    $obj = $null
    try { $obj = $lines[$i] | ConvertFrom-Json -AsHashtable -Depth 32 -NoEnumerate -ErrorAction Stop } catch { $obj = $null }   # -NoEnumerate：[{...}] 是数组、不是对象
    if ($obj -is [System.Collections.IDictionary]) { $records.Add($obj) } else { $reasons += "line $($i + 1): not a JSON object" }
  }
  return New-PrereviewResult $reasons ([ordered]@{ Records = @($records) })
}

function ConvertTo-NormalisedText([object]$Value) {
  # 键归一：NFC + 去首尾空白 + 空白串折叠为单个空格；null（symbol 可为 null）折为空串。
  if ($null -eq $Value) { return '' }
  return [regex]::Replace(([string]$Value).Normalize([Text.NormalizationForm]::FormC).Trim(), '\s+', ' ')
}

function Get-OrdinalUnion([object[]]$Values) {
  # 去重 + 序数排序的字符串并集（确定性、区分大小写；不走 culture 比较）。
  $set = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach ($v in @($Values)) { if ($null -ne $v) { [void]$set.Add([string]$v) } }
  $arr = [string[]]::new($set.Count); $set.CopyTo($arr); [Array]::Sort($arr, [StringComparer]::Ordinal)
  return , $arr
}

function Split-PrereviewProvenance([System.Collections.IDictionary]$Record) {
  # 六个 provenance 键与内容分开：内容对封闭子形状校验，provenance 原样进合并后的条目（缺的键记 null）。
  # 序数字典：[ordered]@{} 不分大小写，会把 worker 写的 "Expected" 悄悄并进 expected（接受与否取决于键序）。
  $content = [System.Collections.Specialized.OrderedDictionary]::new([StringComparer]::Ordinal); $prov = [System.Collections.Specialized.OrderedDictionary]::new([StringComparer]::Ordinal)
  foreach ($k in $Record.Keys) { if ($script:ProvenanceKeys -ccontains $k) { $prov[$k] = $Record[$k] } else { $content[$k] = $Record[$k] } }
  foreach ($k in $script:ProvenanceKeys) { if (-not $prov.Contains($k)) { $prov[$k] = $null } }
  return @{ Content = $content; Provenance = $prov }
}

function Test-PrereviewBatch([object[]]$Records, [object[]]$Units) {
  # 整批校验（两个 ConvertTo-* 都先过这一步）：schema 可读 → units 文档 → 每条记录（provenance / 形状 / schema）→ 归属（A2）。
  # Reasons 里的「record N」= 记录在传入批次里的 1 基序号（JSONL 空行已跳过）；Read-PrereviewRecords 的「line N」才是文件行号。
  $reasons = @()
  try { [void](Get-PrereviewSchemaDefs) } catch { return @{ Ok = $false; Reasons = @("record schema unreadable: $($_.Exception.Message)"); Candidates = @(); Coverage = @() } }
  $unitIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  if (@($Units | Where-Object { -not ($_ -is [System.Collections.IDictionary]) }).Count) { $reasons += 'units document entries must be dictionaries (ConvertFrom-Json -AsHashtable)' }
  elseif (-not (Test-PrereviewRecord -Kind units -Json (ConvertTo-Json -InputObject @($Units) -Depth 8 -Compress))) { $reasons += 'units document does not validate as $defs/units' }
  else { foreach ($u in @($Units)) { if (-not $unitIds.Add([string]$u['unit_id'])) { $reasons += "units document repeats unit_id $($u['unit_id'])" } } }
  $cands = [System.Collections.Generic.List[object]]::new(); $covs = [System.Collections.Generic.List[object]]::new()
  $localKeys = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal); $coverageKeys = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  for ($i = 0; $i -lt @($Records).Count; $i++) {
    $rec = @($Records)[$i]; $no = $i + 1
    if (-not ($rec -is [System.Collections.IDictionary])) { $reasons += "record $no`: not a JSON object"; continue }
    $parts = Split-PrereviewProvenance $rec
    $worker = $parts.Provenance['worker_id']
    if (-not ($worker -is [string]) -or [string]::IsNullOrWhiteSpace($worker)) { $reasons += "record $no`: no worker_id provenance"; continue }
    $kind = if ($parts.Content.Contains('local_id')) { 'candidate' } elseif ($parts.Content.Contains('unit_id')) { 'coverage' } else { '' }
    if (-not $kind) { $reasons += "record $no`: neither candidate (local_id) nor coverage (unit_id)"; continue }
    if (-not (Test-PrereviewRecord -Kind $kind -Json ($parts.Content | ConvertTo-Json -Depth 32 -Compress))) { $reasons += "record $no`: $kind fails `$defs/$kind"; continue }
    $entry = [ordered]@{ Index = $no; Worker = $worker; Content = $parts.Content; Provenance = $parts.Provenance }
    if ($kind -eq 'candidate') {
      $entry['Key'] = Get-PrereviewLocalKey $worker $parts.Content['local_id']
      if (-not $localKeys.Add($entry['Key'])) { $reasons += "record $no`: worker $worker reuses local_id $($parts.Content['local_id'])" }
      $cands.Add($entry)
    } else {
      # 协议第 5 节：每个 worker 对每个 unit 恰好一行 coverage；同 (worker, unit) 第二行会给包读者两个互相矛盾的状态。
      if (-not $coverageKeys.Add((Get-PrereviewLocalKey $worker $parts.Content['unit_id']))) { $reasons += "record $no`: worker $worker has a second coverage row for unit $($parts.Content['unit_id'])" }
      $covs.Add($entry)
    }
  }
  foreach ($c in $cands) { foreach ($u in @($c.Content['unit_ids'])) { if (-not $unitIds.Contains([string]$u)) { $reasons += "record $($c.Index): unit_id $u is not in units.json" } } }
  foreach ($v in $covs) {
    if (-not $unitIds.Contains([string]$v.Content['unit_id'])) { $reasons += "record $($v.Index): unit_id $($v.Content['unit_id']) is not in units.json" }
    foreach ($lid in @($v.Content['candidate_local_ids'])) { if (-not $localKeys.Contains((Get-PrereviewLocalKey $v.Worker $lid))) { $reasons += "record $($v.Index): candidate_local_id $lid names no candidate of worker $($v.Worker) in this batch" } }
  }
  return @{ Ok = ($reasons.Count -eq 0); Reasons = $reasons; Candidates = $cands; Coverage = $covs }
}

function ConvertTo-PrereviewCandidates {
  # A3/A4：local_id → C-<n>（从 -NextId 起单调、返回下一个号）；精确重复合并；fingerprint / root_group / related_to。
  # [PRE-BAD-RECORD] 只描述 worker 记录；调用方参数错误（NextId < 1、计数器用尽：NextId > [long]::MaxValue - 本批候选数）按参数校验 /
  # ArgumentOutOfRangeException 抛出，那是编程错误或状态损坏、不是记录违规。计数器是 [long]：int 过 2147483647 会被提升成 double。
  param([AllowEmptyCollection()][object[]]$Records, [object[]]$Units, [ValidateRange(1, [long]::MaxValue)][long]$NextId = 1)
  $b = Test-PrereviewBatch $Records $Units
  if (-not $b.Ok) { return New-PrereviewResult $b.Reasons ([ordered]@{ Candidates = @(); NextId = $NextId; LocalIdMap = (New-OrdinalMap); StartId = $NextId }) }
  # 精确重复键 = 六个归一后字段的元组（JSON 数组编码：值里含 '|' 也不撞）；fingerprint 照卡片形态用 '|' 连接，只作分组提示。
  $keys = @(foreach ($c in $b.Candidates) { ConvertTo-Json -InputObject @($script:ExactKeyFields | ForEach-Object { ConvertTo-NormalisedText $c.Content[$_] }) -Compress })
  $fresh = [long]@($keys | Select-Object -Unique).Count   # 合并后真正要铸的号数：用尽判定按它算，重复多的批不会被冤枉
  if ($NextId -gt ([long]::MaxValue - $fresh)) { throw [ArgumentOutOfRangeException]::new('NextId', 'C-n counter exhausted for this batch') }
  $fieldOrder = @((Get-PrereviewSchemaDefs)['candidate']['properties'].Keys)
  $byKey = [System.Collections.Specialized.OrderedDictionary]::new([StringComparer]::Ordinal); $map = New-OrdinalMap; [long]$n = $NextId
  for ($i = 0; $i -lt $b.Candidates.Count; $i++) {
    $c = $b.Candidates[$i]; $key = $keys[$i]
    # 合并后的标量字段取批内首个贡献者（RUN 先传发现者）；每个贡献者自己的定位与分级记在它的 provenance 条目里，合并不丢信息。
    $prov = [ordered]@{ worker_id = $c.Worker; local_id = $c.Content['local_id'] }
    foreach ($k in $script:ProvenanceKeys) { if ($k -ne 'worker_id') { $prov[$k] = $c.Provenance[$k] } }
    foreach ($k in $script:ContributorFields) { $prov[$k] = $c.Content[$k] }
    if ($byKey.Contains($key)) {
      $x = $byKey[$key]
      foreach ($f in $script:UnionFields) { $x[$f] = Get-OrdinalUnion (@($x[$f]) + @($c.Content[$f])) }
      $x['provenance'] = @($x['provenance']) + @($prov)
      $map[$c.Key] = $x['id']; continue
    }
    $x = [ordered]@{ id = "C-$n"; fingerprint = ($script:FingerprintFields | ForEach-Object { ConvertTo-NormalisedText $c.Content[$_] }) -join '|'; root_group = $null; related_to = @() }
    foreach ($f in $fieldOrder) { if ($f -ne 'local_id') { $x[$f] = $(if ($script:UnionFields -ccontains $f) { Get-OrdinalUnion @($c.Content[$f]) } else { $c.Content[$f] }) } }
    $x['provenance'] = @($prov)
    $byKey[$key] = $x; $map[$c.Key] = $x['id']; $n++
  }
  $roots = New-OrdinalMap; $groups = New-OrdinalMap
  foreach ($x in $byKey.Values) {
    if (-not $roots.ContainsKey($x['fingerprint'])) { $roots[$x['fingerprint']] = $x['id']; $groups[$x['id']] = [System.Collections.Generic.List[string]]::new() }
    $x['root_group'] = $roots[$x['fingerprint']]; $groups[$x['root_group']].Add($x['id'])
  }
  foreach ($x in $byKey.Values) { $x['related_to'] = @($groups[$x['root_group']] | Where-Object { $_ -cne $x['id'] }) }
  return New-PrereviewResult @() ([ordered]@{ Candidates = @($byKey.Values); NextId = $n; LocalIdMap = $map; StartId = $NextId })
}

function ConvertTo-PrereviewCoverage {
  # A2/A5：coverage 行 → state 形状（candidate_local_ids 经 LocalIdMap 换成 C-<n>）；发现者覆盖缺 C1/C2/C3 的 unit 合成 missing 行。
  param([AllowEmptyCollection()][object[]]$Records, [object[]]$Units, [Parameter(Mandatory)]$Candidates, [string]$DiscovererWorkerId = 'discoverer')
  $b = Test-PrereviewBatch $Records $Units
  if (-not $b.Ok) { return New-PrereviewResult $b.Reasons ([ordered]@{ Coverage = @() }) }
  # 绑定：-Candidates 必须恰好等于本核心对同一批记录、从其 StartId 重铸出的结果（Candidates / LocalIdMap 的规范 JSON 与 NextId 逐字节相等）。
  # 重铸而非核对：改 id、改 map、协同改写、补条目、缺字段、失败结果、别批结果，全部走同一个 [PRE-BAD-RECORD] 出口，不抛、不解引用。
  $bad = $true; $re = $null
  try {
    $p = @{}; foreach ($n in @('StartId', 'Candidates', 'LocalIdMap', 'NextId')) { $p[$n] = $Candidates.PSObject.Properties[$n].Value }
    $re = ConvertTo-PrereviewCandidates -Records $Records -Units $Units -NextId ([long]$p['StartId'])
    $canon = { param($v) ConvertTo-Json -InputObject @($v) -Depth 16 -Compress }
    # 键按序数排序（Sort-Object 走 culture、不分大小写：'l1' 与 'L1' 会并列，同一份 map 换个枚举顺序就序列化成两样）。
    $canonMap = { param($m) $d = New-OrdinalMap; foreach ($k in @($m.Keys)) { $d[[string]$k] = [string]$m[$k] }; $ks = [string[]]@($d.Keys); [Array]::Sort($ks, [StringComparer]::Ordinal); & $canon @($ks | ForEach-Object { @($_, $d[$_]) }) }
    $bad = -not ($re.Ok -and ((& $canon $re.Candidates) -ceq (& $canon $p['Candidates'])) -and ((& $canonMap $re.LocalIdMap) -ceq (& $canonMap $p['LocalIdMap'])) -and ([long]$p['NextId'] -eq $re.NextId))
  } catch { $bad = $true }
  if ($bad) { return New-PrereviewResult @('candidates result is not the one minted from this batch (failed, another batch, or altered)') ([ordered]@{ Coverage = @() }) }
  $map = $re.LocalIdMap
  $rows = [System.Collections.Generic.List[object]]::new(); $checked = New-OrdinalMap
  foreach ($v in $b.Coverage) {
    # 每个 (worker, lid) 已由 Test-PrereviewBatch 证明是本批的 candidate 键，且上面已证明 map 覆盖全部键：查表不会落空。
    $ids = @(foreach ($lid in @($v.Content['candidate_local_ids'])) { $map[(Get-PrereviewLocalKey $v.Worker $lid)] })
    $unit = [string]$v.Content['unit_id']
    $rows.Add([ordered]@{ unit_id = $unit; worker_id = $v.Worker; categories_checked = @($v.Content['categories_checked']); status = $v.Content['status']; candidate_ids = (Get-OrdinalUnion $ids); missing_context = @($v.Content['missing_context']) })
    if ($v.Worker -ceq $DiscovererWorkerId) {
      if (-not $checked.ContainsKey($unit)) { $checked[$unit] = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal) }
      foreach ($cat in @($v.Content['categories_checked'])) { [void]$checked[$unit].Add([string]$cat) }
    }
  }
  foreach ($u in @($Units)) {
    $unit = [string]$u['unit_id']
    $lacks = @($script:RequiredCategories | Where-Object { -not ($checked.ContainsKey($unit) -and $checked[$unit].Contains($_)) })
    if ($lacks.Count) { $rows.Add([ordered]@{ unit_id = $unit; worker_id = $null; categories_checked = @(); status = 'missing'; candidate_ids = @(); missing_context = @() }) }
  }
  return New-PrereviewResult @() ([ordered]@{ Coverage = @($rows) })
}

# ── 库模式：函数已定义，就此返回（不读夹具、不 exit）──
if ($AsLibrary) { return }
if (-not $SelfCheck) { Write-Host 'usage: _prereview-records.ps1 -AsLibrary | -SelfCheck' -ForegroundColor Yellow; exit 2 }
# A6：自检只读冻结 schema 与夹具，不 dot-source 任何别的脚本（输出全是 ASCII 哨兵与标签，不需要 _encoding.ps1）。

# ── -SelfCheck（DoD）：只读夹具目录；清掉继承的 PRE_LIVE / PRE_LENS_ENDPOINT；全程不 spawn 进程 ──
$PassSentinel = '[PREREVIEW-RECORDS-SELFCHECK-PASS]'
$FailSentinel = '[PREREVIEW-RECORDS-SELFCHECK-FAIL]'
$FixtureRoot = Join-Path $script:RepoRoot 'scripts/fixtures/prereview/records'
foreach ($v in @('PRE_LIVE', 'PRE_LENS_ENDPOINT')) { Remove-Item -LiteralPath "Env:$v" -ErrorAction SilentlyContinue }
$fails = [System.Collections.Generic.List[string]]::new()
function Check([string]$Label, [bool]$Cond) {
  if ($Cond) { Write-Host "  ok   $Label" -ForegroundColor DarkGray; return }
  Write-Host "  FAIL $Label" -ForegroundColor Red; $script:fails.Add($Label)
}
function Same([object[]]$Actual, [string[]]$Expected) {   # 逐位、区分大小写的序列相等（L165：断言面 = 契约）
  $a = @($Actual); if ($a.Count -ne $Expected.Count) { return $false }
  for ($i = 0; $i -lt $a.Count; $i++) { if (-not ([string]$a[$i] -ceq $Expected[$i])) { return $false } }
  return $true
}
# reject 夹具清单钉死（文件 → 违规类）：目录须与之恰好相等；少一类或多一个未登记文件都红。
$RejectClasses = [ordered]@{
  'candidate-unknown-unit.jsonl'      = 'unit_ids entry not in units.json (A2)'
  'coverage-unknown-unit.jsonl'       = 'coverage unit_id not in units.json (A2)'
  'coverage-unknown-local-id.jsonl'   = 'candidate_local_ids names another worker local_id (A2)'
  'candidate-duplicate-local-id.jsonl' = 'one worker reuses a local_id (A2)'
  'coverage-worker-id.jsonl'          = 'worker-emitted id (A5)'
  'coverage-status-missing.jsonl'     = 'worker-emitted missing (A5)'
  'coverage-verdict.jsonl'            = 'field named verdict'
  'record-no-worker-id.jsonl'         = 'no worker_id provenance'
  'record-unknown-shape.jsonl'        = 'neither candidate nor coverage'
  'coverage-duplicate-unit.jsonl'     = 'two coverage rows for one (worker, unit)'
  'coverage-case-variant-key.jsonl'   = 'Status beside status (ordinal, closed)'
  'malformed.jsonl'                   = 'lines that are not JSON objects'
}
$U1 = 'scripts/a.ps1#0123456789ab'; $U2 = 'scripts/a.ps1#89abcdef0123'; $U3 = 'docs/x.md#file'; $U4 = 'docs/y.md#file'

Write-Host '[1/5] Test-PrereviewRecord: booleans, never a throw' -ForegroundColor Cyan
$unitsText = [IO.File]::ReadAllText((Join-Path $FixtureRoot 'units.json'))
$units = @($unitsText | ConvertFrom-Json -AsHashtable -Depth 8)
Check 'units.json validates as $defs/units' (Test-PrereviewRecord -Kind units -Json $unitsText)
Check 'facts.json validates as $defs/facts' (Test-PrereviewRecord -Kind facts -Json ([IO.File]::ReadAllText((Join-Path $FixtureRoot 'facts.json'))))
$batch = Read-PrereviewRecords -Path (Join-Path $FixtureRoot 'valid/batch.jsonl')
Check 'valid/batch.jsonl reads as 11 records' ($batch.Ok -and @($batch.Records).Count -eq 11)
$threw = $false; $r = $true
try { $r = Test-PrereviewRecord -Kind candidate -Json '{"local_id": "d1"' } catch { $threw = $true }
Check 'malformed JSON -> false without a throw' (-not $threw -and -not $r)
Check 'candidate with provenance attached -> false (closed shape)' (-not (Test-PrereviewRecord -Kind candidate -Json ($batch.Records[0] | ConvertTo-Json -Depth 8 -Compress)))
Check 'units document given as coverage -> false' (-not (Test-PrereviewRecord -Kind coverage -Json $unitsText))
$threw = $false; $cObj = $null
try { $cObj = ConvertTo-PrereviewCandidates -Records $batch.Records -Units @($unitsText | ConvertFrom-Json) -NextId 1 } catch { $threw = $true }
Check 'units given as PSCustomObject (plain ConvertFrom-Json) -> [PRE-BAD-RECORD], no throw' (-not $threw -and $cObj.Code -ceq $script:BadRecordCode)
$cDupUnit = ConvertTo-PrereviewCandidates -Records $batch.Records -Units @($units + @($units[3])) -NextId 1
Check 'units document repeating a unit_id -> [PRE-BAD-RECORD]' ($cDupUnit.Code -ceq $script:BadRecordCode -and @($cDupUnit.Reasons) -match 'repeats unit_id')

Write-Host '[2/5] reject classes -> [PRE-BAD-RECORD], no throw, nothing minted' -ForegroundColor Cyan
$rejectDir = Join-Path $FixtureRoot 'reject'
$present = @(Get-ChildItem -LiteralPath $rejectDir -Filter *.jsonl -File | ForEach-Object Name)
$drift = @(Compare-Object $present @($RejectClasses.Keys) | ForEach-Object { $_.InputObject })
Check "reject inventory: $($present.Count) on disk == $($RejectClasses.Count) declared" ($drift.Count -eq 0)
foreach ($name in $RejectClasses.Keys) {
  $path = Join-Path $rejectDir $name
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
  $threw = $false; $rd = $null; $c = $null; $v = $null
  try {
    $rd = Read-PrereviewRecords -Path $path
    if ($rd.Ok) {
      $c = ConvertTo-PrereviewCandidates -Records $rd.Records -Units $units -NextId 7
      $v = ConvertTo-PrereviewCoverage -Records $rd.Records -Units $units -Candidates $c
    }
  } catch { $threw = $true; Write-Host "    threw: $($_.Exception.Message)" -ForegroundColor Red }
  Check "$name ($($RejectClasses[$name])): no throw" (-not $threw)
  if ($threw) { continue }
  if ($rd.Ok) {
    Check "$name -> both normalisers return [PRE-BAD-RECORD] with reasons" ($c.Code -ceq $script:BadRecordCode -and $v.Code -ceq $script:BadRecordCode -and @($c.Reasons).Count -gt 0 -and @($v.Reasons).Count -gt 0)
    Check "$name -> NextId untouched, no candidate, no coverage row" ($c.NextId -eq 7 -and @($c.Candidates).Count -eq 0 -and @($v.Coverage).Count -eq 0)
  } else {
    Check "$name -> Read returns [PRE-BAD-RECORD] naming line 2 (unclosed) and line 3 (array)" ($rd.Code -ceq $script:BadRecordCode -and (Same $rd.Reasons @('line 2: not a JSON object', 'line 3: not a JSON object')) -and @($rd.Records).Count -eq 1)
  }
}

Write-Host '[3/5] minting, exact merge, near-duplicate groups' -ForegroundColor Cyan
$c1 = ConvertTo-PrereviewCandidates -Records $batch.Records -Units $units -NextId 1
Check 'valid batch normalises' ($c1.Ok -and $c1.Code -ceq '' -and @($c1.Reasons).Count -eq 0)
$ids = @($c1.Candidates | ForEach-Object { $_['id'] })
Check 'ids C-1..C-4 in input order, NextId 5' ((Same $ids @('C-1', 'C-2', 'C-3', 'C-4')) -and $c1.NextId -eq 5)
$k1 = @($c1.Candidates)[0]; $k2 = @($c1.Candidates)[1]; $k3 = @($c1.Candidates)[2]; $k4 = @($c1.Candidates)[3]
$pv = @($k1['provenance'])
Check 'C-1 = d1 + l1 (NFD, whitespace, other line): two provenance entries' ((Same @($pv | ForEach-Object { "$($_['worker_id'])|$($_['local_id'])" }) @('discoverer|d1', 'lens|l1')) -and (Same @($pv | ForEach-Object { $_['model_id'] }) @('claude-opus-5', 'deepseek-v4-flash')))
Check 'C-1 defect/high from d1; provenance keeps each contributor line/kind/severity (l1: 43, question, medium)' ($k1['kind'] -ceq 'defect' -and $k1['severity_guess'] -ceq 'high' -and $pv[0]['line_start'] -eq 41 -and $pv[1]['line_start'] -eq 43 -and $pv[1]['kind'] -ceq 'question' -and $pv[1]['severity_guess'] -ceq 'medium')
Check 'C-1 keeps the first contributor content (line 41, NFC text)' ($k1['line_start'] -eq 41 -and $k1['expected'] -ceq "every segment is checked (caf$([char]0xE9))")
Check 'C-1 unions: unit_ids dedup, evidence_refs sorted (41,43,40 in), evidence_needed disjoint sorted' ((Same $k1['unit_ids'] @($U1, $U2)) -and (Same $k1['evidence_refs'] @('scripts/a.ps1:40', 'scripts/a.ps1:41', 'scripts/a.ps1:43')) -and (Same $k1['evidence_needed'] @('docs/x.md', 'scripts/task.ps1 -Local branch')))
Check 'fingerprint = file|category|symbol|contract_ref' ($k1['fingerprint'] -ceq 'scripts/a.ps1|C2|Test-Foo|lesson:L228')
Check 'C-2 shares C-1 fingerprint yet keeps its own id (hint, not identity)' ($k2['fingerprint'] -ceq $k1['fingerprint'] -and $k2['id'] -ceq 'C-2')
Check 'near duplicates: root_group C-1 on both, related_to cross-listed' ($k1['root_group'] -ceq 'C-1' -and $k2['root_group'] -ceq 'C-1' -and (Same $k1['related_to'] @('C-2')) -and (Same $k2['related_to'] @('C-1')))
Check 'C-3 and C-4 are their own roots with empty related_to' ($k3['root_group'] -ceq 'C-3' -and $k4['root_group'] -ceq 'C-4' -and @($k3['related_to']).Count -eq 0 -and @($k4['related_to']).Count -eq 0)
Check 'C-3 keeps symbol null with line_start 3 (question on docs/x.md)' ($null -eq $k3['symbol'] -and $k3['line_start'] -eq 3 -and $k3['kind'] -ceq 'question')
$m = $c1.LocalIdMap
Check 'LocalIdMap: (lens,l1) C-1, (discoverer,d2) C-2, (lens,l2) C-4; (lens,L1) nobody (ordinal)' ($m[(Get-PrereviewLocalKey 'lens' 'l1')] -ceq 'C-1' -and $m[(Get-PrereviewLocalKey 'discoverer' 'd2')] -ceq 'C-2' -and $m[(Get-PrereviewLocalKey 'lens' 'l2')] -ceq 'C-4' -and -not $m.ContainsKey((Get-PrereviewLocalKey 'lens' 'L1')))
$c2 = ConvertTo-PrereviewCandidates -Records $batch.Records -Units $units -NextId $c1.NextId
$ids2 = @($c2.Candidates | ForEach-Object { $_['id'] })
Check 'second call continues at C-5..C-8, NextId 9, no number reused' ((Same $ids2 @('C-5', 'C-6', 'C-7', 'C-8')) -and $c2.NextId -eq 9 -and @($ids | Where-Object { $ids2 -ccontains $_ }).Count -eq 0)
$axis = @{ file = 'scripts/b.ps1'; symbol = 'Test-Bar'; category = 'C3'; contract_ref = 'lesson:L229'; expected = 'other expectation'; actual = 'other actual' }
$axisOk = $true
foreach ($f in $axis.Keys) {
  $q = [ordered]@{}; foreach ($k in $batch.Records[0].Keys) { $q[$k] = $batch.Records[0][$k] }; $q['local_id'] = 'q1'; $q[$f] = $axis[$f]
  $cq = ConvertTo-PrereviewCandidates -Records @($batch.Records[0], $q) -Units $units -NextId 1
  if (-not ($cq.Ok -and @($cq.Candidates).Count -eq 2)) { $axisOk = $false; Write-Host "    axis $f did not keep two candidates" -ForegroundColor Red }
}
Check 'one-axis near duplicates (each of the six key fields) never merge' $axisOk
$p1 = [ordered]@{}; $p2 = [ordered]@{}; foreach ($k in $batch.Records[0].Keys) { $p1[$k] = $batch.Records[0][$k]; $p2[$k] = $batch.Records[0][$k] }
$p1['expected'] = 'x|y'; $p1['actual'] = 'z'; $p2['local_id'] = 'd9'; $p2['expected'] = 'x'; $p2['actual'] = 'y|z'
$cPipe = ConvertTo-PrereviewCandidates -Records @($p1, $p2) -Units $units -NextId 1
Check 'tuple keys: (x|y,z) and (x,y|z) stay apart; local key likewise' ($cPipe.Ok -and @($cPipe.Candidates).Count -eq 2 -and (Get-PrereviewLocalKey 'a|b' 'c') -cne (Get-PrereviewLocalKey 'a' 'b|c'))
$threw = $false; $cBig = $null; $cBig2 = $null
try { $cBig = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId ([int]::MaxValue); $cBig2 = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId $cBig.NextId } catch { $threw = $true }
Check 'NextId past Int32.MaxValue: C-2147483647, then 2147483648 feeds the next call' (-not $threw -and @($cBig.Candidates)[0]['id'] -ceq 'C-2147483647' -and $cBig.NextId -eq 2147483648 -and @($cBig2.Candidates)[0]['id'] -ceq 'C-2147483648')
$exhausted = $false; try { [void](ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId ([long]::MaxValue)) } catch { $exhausted = ($_.Exception -is [ArgumentOutOfRangeException]) }
Check 'NextId at Int64.MaxValue with one candidate: ArgumentOutOfRangeException before minting (caller error, not a record)' $exhausted
$threw = $false; $dupTop = $null
try { $dupTop = ConvertTo-PrereviewCandidates -Records @($batch.Records[0], $batch.Records[6]) -Units $units -NextId ([long]::MaxValue - 1) } catch { $threw = $true }
Check 'two exact duplicates at Int64.MaxValue-1 need one id: minted, NextId = Int64.MaxValue (exhaustion counts unique keys)' (-not $threw -and $dupTop.Ok -and @($dupTop.Candidates).Count -eq 1 -and $dupTop.NextId -eq [long]::MaxValue)

Write-Host '[4/5] coverage rows, binding, missing synthesis' -ForegroundColor Cyan
$v1 = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $c1
Check 'coverage normalises' ($v1.Ok -and $v1.Code -ceq '')
$rows = @($v1.Coverage)
function Row([string]$Unit, $Worker) { return , @($rows | Where-Object { $_['unit_id'] -ceq $Unit -and $_['worker_id'] -ceq $Worker }) }
Check '6 worker rows + 3 missing rows' ($rows.Count -eq 9)
$d1 = Row $U1 'discoverer'; $l1 = Row $U1 'lens'; $d3 = Row $U3 'discoverer'; $l3 = Row $U3 'lens'
Check 'U1/discoverer: finding, candidate_ids C-1,C-2' ($d1.Count -eq 1 -and $d1[0]['status'] -ceq 'finding' -and (Same $d1[0]['candidate_ids'] @('C-1', 'C-2')))
Check 'U1/lens: finding, candidate_ids C-1 (merged id)' ($l1.Count -eq 1 -and (Same $l1[0]['candidate_ids'] @('C-1')))
Check 'U3/discoverer: blocked with missing_context, candidate_ids C-3' ($d3.Count -eq 1 -and $d3[0]['status'] -ceq 'blocked' -and (Same $d3[0]['missing_context'] @('docs/y.md')) -and (Same $d3[0]['candidate_ids'] @('C-3')))
Check 'U3/lens: finding, candidate_ids C-4' ($l3.Count -eq 1 -and (Same $l3[0]['candidate_ids'] @('C-4')))
$missing = @($rows | Where-Object { $_['status'] -ceq 'missing' })
Check 'missing rows for exactly U2 (lacks C2), U3 (lacks C2, C3) and U4 (no row from anyone), in units order' ((Same @($missing | ForEach-Object { $_['unit_id'] }) @($U2, $U3, $U4)))
Check 'missing rows: worker_id null, empty categories / candidate_ids / missing_context' (@($missing | Where-Object { $null -eq $_['worker_id'] -and @($_['categories_checked']).Count -eq 0 -and @($_['candidate_ids']).Count -eq 0 -and @($_['missing_context']).Count -eq 0 }).Count -eq 3)
Check 'U4 (no row from any worker) gets exactly one missing row' (@($rows | Where-Object { $_['unit_id'] -ceq $U4 }).Count -eq 1)
Check 'no missing row for U1; the lens C2 row on U2 does not rescue it' (@($missing | Where-Object { $_['unit_id'] -ceq $U1 }).Count -eq 0 -and @(Row $U2 'lens').Count -eq 1)
$cDup = ConvertTo-PrereviewCandidates -Records @($batch.Records + @($batch.Records[0])) -Units $units -NextId 1
$threw = $false; $vBad = $null
try { $vBad = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $cDup } catch { $threw = $true }
Check 'failed candidates result (d1 twice: Ok false) refused, no throw' (-not $threw -and -not $cDup.Ok -and $null -ne $vBad -and $vBad.Code -ceq $script:BadRecordCode -and @($vBad.Coverage).Count -eq 0)
$alt = @($batch.Records | ForEach-Object { $_ }); $alt[1] = $p2; $alt[1]['local_id'] = 'd2'
$cAlt = ConvertTo-PrereviewCandidates -Records $alt -Units $units -NextId 1
Check 'alt batch (d2 reworded) mints on its own, map resolves d2' ($cAlt.Ok -and $cAlt.LocalIdMap.ContainsKey((Get-PrereviewLocalKey 'discoverer' 'd2')))
$vOther = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $cAlt
Check 'another batch result refused (recompute differs) though every local_id resolves' ($vOther.Code -ceq $script:BadRecordCode -and @($vOther.Coverage).Count -eq 0)
function Forge($Map, $Cands, $Next = $c1.NextId) { [pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $Cands; NextId = $Next; LocalIdMap = $Map; StartId = 1 } }
function MapWith([string]$Key, $Value, [string]$Drop = '') { $m = New-OrdinalMap; foreach ($k in $c1.LocalIdMap.Keys) { if ($k -cne $Drop) { $m[$k] = $c1.LocalIdMap[$k] } }; if ($Key) { $m[$Key] = $Value }; return $m }
function CopyCands { return @($c1.Candidates | ForEach-Object { $x = [ordered]@{}; foreach ($k in $_.Keys) { $x[$k] = $_[$k] }; $x }) }
$d1k = Get-PrereviewLocalKey 'discoverer' 'd1'; $d3k = Get-PrereviewLocalKey 'discoverer' 'd3'
$rewritten = CopyCands; $rewritten[0]['id'] = 'C-9'; $coMap = MapWith $d1k 'C-9'; $coMap[(Get-PrereviewLocalKey 'lens' 'l1')] = 'C-9'
$forged = @(
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $c1.Candidates; NextId = $c1.NextId; StartId = 1 }),   # no LocalIdMap
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap }),   # no StartId
  (Forge 'not a map' $c1.Candidates),                                                        # non-dictionary
  (Forge (MapWith $d1k 'C-999') $c1.Candidates),                                             # out-of-set id
  (Forge (MapWith '' $null (Get-PrereviewLocalKey 'lens' 'l2')) $c1.Candidates),            # short map
  (Forge (MapWith $d3k 'C-1') $c1.Candidates),                                               # in-set remap
  (Forge (MapWith $d3k 'C-777') @($c1.Candidates + @([ordered]@{ id = 'C-777' }))),          # padded with an id-only entry
  (Forge $c1.LocalIdMap @($c1.Candidates + @([ordered]@{ id = 'C-5'; provenance = @() }))),  # padded with a provenance-free entry
  (Forge $coMap $rewritten),                                                                 # coordinated id + map rewrite (C-1 -> C-9)
  (Forge $c1.LocalIdMap $c1.Candidates 6)                                                    # NextId altered
)
$gen = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal); foreach ($k in $c1.LocalIdMap.Keys) { $gen[$k] = $c1.LocalIdMap[$k] }
$threw = $false; $vF = @(); $vGen = $null
try { $vF = @(foreach ($f in $forged) { ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $f }); $vGen = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates (Forge $gen $c1.Candidates) } catch { $threw = $true; Write-Host "    threw: $($_.Exception.Message)" -ForegroundColor Red }
Check 'no map / no StartId / non-dictionary / out-of-set / short / in-set remap / id-only pad / provenance-free pad / coordinated rewrite / NextId altered: all 10 refused, no throw' (-not $threw -and @($vF | Where-Object { $_.Code -ceq $script:BadRecordCode -and @($_.Coverage).Count -eq 0 }).Count -eq 10)
Check 'a generic Dictionary[string,string] holding the true map is accepted (same 9 rows)' (-not $threw -and $vGen.Ok -and @($vGen.Coverage).Count -eq 9)
$qU = [ordered]@{}; foreach ($k in $batch.Records[0].Keys) { $qU[$k] = $batch.Records[0][$k] }; $qU['local_id'] = 'D1'; $qU['expected'] = 'upper twin'
$cCase = ConvertTo-PrereviewCandidates -Records @($batch.Records[0], $qU) -Units $units -NextId 1
$rev = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal); foreach ($k in @(@($cCase.LocalIdMap.Keys)[-1..0])) { $rev[$k] = $cCase.LocalIdMap[$k] }
$vCase = ConvertTo-PrereviewCoverage -Records @($batch.Records[0], $qU) -Units $units -Candidates ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $cCase.Candidates; NextId = $cCase.NextId; LocalIdMap = $rev; StartId = 1 })
Check 'case-variant local ids (d1, D1) in reverse insertion order still bind (ordinal key sort, not culture)' ($cCase.Ok -and @($cCase.Candidates).Count -eq 2 -and $vCase.Ok)
$vUnits = ConvertTo-PrereviewCoverage -Records $batch.Records -Units @() -Candidates $c1
Check 'coverage re-validates its batch: empty units, matching result -> [PRE-BAD-RECORD], no row' ($vUnits.Code -ceq $script:BadRecordCode -and @($vUnits.Coverage).Count -eq 0 -and @($vUnits.Reasons) -match 'not in units.json')

Write-Host '[5/5] environment: PRE_LIVE and PRE_LENS_ENDPOINT absent at the end' -ForegroundColor Cyan
Check 'PRE_LIVE / PRE_LENS_ENDPOINT are not set' (-not (Test-Path Env:PRE_LIVE) -and -not (Test-Path Env:PRE_LENS_ENDPOINT))

if ($fails.Count) { $fails | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }; Write-Host $FailSentinel -ForegroundColor Red; exit 1 }
Write-Host $PassSentinel -ForegroundColor Green
exit 0
