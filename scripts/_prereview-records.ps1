#requires -Version 7.4
<#
.SYNOPSIS
  PR review v2 记录核心（RECORDS）：校验 worker 记录 · unit 归属 · C-<n> 铸造 · 精确重复合并 · fingerprint 提示 · missing 覆盖合成。
.DESCRIPTION
  两种模式（协议：docs/PREREVIEW-PROTOCOL.md 第 5 节；契约本体 specs/prereview-record.schema.json）：
    -AsLibrary   只定义函数后立即 return（check-secrets.ps1 的库模式形态）：不读夹具、不触达 git、不 exit。
    -SelfCheck   只读冻结 schema 与 scripts/fixtures/prereview/records/，末行 [PREREVIEW-RECORDS-SELFCHECK-PASS|FAIL]（exit 0|1）；先清掉
                 继承的 PRE_LIVE / PRE_LENS_ENDPOINT，不 spawn 进程，不 dot-source 别的脚本。
  输入 = 一个批次的记录列表，即 WORKERS A3 盖章后的 JSONL 行：内容字段 + 六个 provenance 键。本核心只用 worker_id（local_id 的命名空间 +
  provenance 列表），其余五个键原样进 provenance 条目；校验前把六个键与内容分开，内容对封闭的 $defs 子形状校验（id / verdict / missing 被拒）。
  规则（卡 T0-PREREVIEW-RECORDS A1–A5）：
    · 任何违规都返回 Ok=$false + Code=[PRE-BAD-RECORD] + Reasons[]，不抛错；批内任一违规即整批不铸 id、不出行（fail-closed）。
    · 精确重复键 = file|symbol|category|contract_ref|expected|actual（NFC + 去首尾空白 + 空白串折叠），不含行号与 anchor：同一符号上
      同一句无论行号都合并成一条；合并保留每个贡献者的 (worker_id, local_id)、unit_ids / evidence_refs / evidence_needed 的有序并集
      （位置不丢，TD176 的决定在 master 卡片 A4）；其余标量取首个贡献者（RUN 先传发现者），每个贡献者自己的 kind / severity_guess /
      anchor / line_start / line_end 记在它的 provenance 条目里。
    · fingerprint = file|category|symbol|contract_ref 只是分组提示：同 fingerprint 而键不同的记录是「近似重复」，各保留自己的
      id、共享 root_group（= 该 fingerprint 组里最小的 id）并互列 related_to。
    · C-<n> 在一个 state 生命期内单调铸造：调用方把上次返回的 NextId 喂给下一次调用，号码永不复用。candidates 结果带 StartId，
      coverage 从 StartId 重铸并要求整个结果对象逐字节相等；每个 worker 对每个 unit 只许一行 coverage；units 文档不许重复 unit_id。
    · missing 行只由本核心合成：每个 unit 若发现者（-DiscovererWorkerId）的 coverage 行 categories_checked 未同时含 C1、C2、C3
      即补一行 status=missing（只看 categories_checked，不看 status；透镜的行是附加的，不参与判定）。
  产出：candidate = id / fingerprint / root_group / related_to / 内容字段 / provenance[]；coverage = unit_id / worker_id / categories_checked /
  status / candidate_ids / missing_context。落盘归 STATE-1A。
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
$script:ResultFields = @('Ok', 'Code', 'Reasons', 'Candidates', 'NextId', 'LocalIdMap', 'StartId')   # candidates 结果的全部属性；coverage 绑定按此逐字节比
$script:RequiredCategories = @('C1', 'C2', 'C3')
$script:ExactKeyFields = @('file', 'symbol', 'category', 'contract_ref', 'expected', 'actual')
$script:FingerprintFields = @('file', 'category', 'symbol', 'contract_ref')
$script:UnionFields = @('unit_ids', 'evidence_refs', 'evidence_needed')
$script:ContributorFields = @('kind', 'severity_guess', 'anchor', 'line_start', 'line_end')   # 每个贡献者自己的定位与分级，记进其 provenance 条目
$script:SchemaDefs = $null

# ── 库导出区（-AsLibrary：不读文件、不 spawn、不 exit；StrictMode 与 ErrorActionPreference=Stop 同 check-secrets.ps1 留在调用方作用域）──
# 键与字典一律序数：unit_id 是路径、local_id 是字符串，'l1' 与 'L1' 是两个 id；身份比较用 [string]::Equals(…, Ordinal) 与序数 HashSet，
# 不用 -eq / -ceq / -contains / Select-Object -Unique（它们走 culture 比较：软连字符、零宽字符等可忽略码位与 NFC/NFD 变体会被判相等）。
function New-OrdinalMap { return [System.Collections.Hashtable]::new([StringComparer]::Ordinal) }
function Test-Ordinal([string]$A, [string]$B) { return [string]::Equals($A, $B, [StringComparison]::Ordinal) }

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
  param([AllowNull()][AllowEmptyString()][string]$Kind, [AllowNull()][AllowEmptyString()][string]$Json)
  if (@(@('candidate', 'coverage', 'facts', 'units') | Where-Object { Test-Ordinal $_ $Kind }).Count -ne 1) { return $false }
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
  param([AllowNull()][AllowEmptyString()][string]$Path)
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
  $provSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$script:ProvenanceKeys, [StringComparer]::Ordinal)
  foreach ($k in $Record.Keys) { if ($provSet.Contains([string]$k)) { $prov[$k] = $Record[$k] } else { $content[$k] = $Record[$k] } }
  foreach ($k in $script:ProvenanceKeys) { if (-not $prov.Contains($k)) { $prov[$k] = $null } }
  return @{ Content = $content; Provenance = $prov }
}

function Test-PrereviewBatch([object[]]$Records, [object[]]$Units) {
  # 整批校验（两个 ConvertTo-* 都先过）：schema 可读 → units 文档 → 每条记录（provenance / 形状 / schema）→ 归属（A2）。
  # Reasons 的「record N」= 记录在传入批次里的 1 基序号；Read-PrereviewRecords 的「line N」才是文件行号。
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
    if (Test-Ordinal $kind 'candidate') {
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
  # 卡片 forbid：坏输入一律返回 [PRE-BAD-RECORD]、不抛——NextId 不是整数、< 1、或计数器用尽（NextId > [long]::MaxValue - 本批新号数）
  # 也走同一出口（NextId 原样返回、不铸）。计数器是 [long]：int 过 2147483647 会被提升成 double。
  param([AllowEmptyCollection()][object[]]$Records, [object[]]$Units, $NextId = 1)
  $fail = { param([string[]]$Why) New-PrereviewResult $Why ([ordered]@{ Candidates = @(); NextId = $NextId; LocalIdMap = (New-OrdinalMap); StartId = $NextId }) }
  if (@([byte], [sbyte], [int16], [uint16], [int32], [uint32], [int64] | Where-Object { $NextId -is $_ }).Count -ne 1 -or [long]$NextId -lt 1) { return & $fail @('NextId must be an integer type up to Int64 and >= 1') }
  [long]$NextId = $NextId
  $b = Test-PrereviewBatch $Records $Units
  if (-not $b.Ok) { return & $fail $b.Reasons }
  # 精确重复键 = 六个归一后字段的元组（JSON 数组编码：值里含 '|' 也不撞）；fingerprint 照卡片形态用 '|' 连接，只作分组提示。
  $keys = @(foreach ($c in $b.Candidates) { ConvertTo-Json -InputObject @($script:ExactKeyFields | ForEach-Object { ConvertTo-NormalisedText $c.Content[$_] }) -Compress })
  $fresh = [long][System.Collections.Generic.HashSet[string]]::new([string[]]$keys, [StringComparer]::Ordinal).Count   # 合并后真正要铸的号数（序数去重）
  if ($NextId -gt ([long]::MaxValue - $fresh)) { return & $fail @("C-n counter exhausted: NextId $NextId leaves no room for $fresh new ids") }
  $fieldOrder = @((Get-PrereviewSchemaDefs)['candidate']['properties'].Keys); $unionSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$script:UnionFields, [StringComparer]::Ordinal)
  $byKey = [System.Collections.Specialized.OrderedDictionary]::new([StringComparer]::Ordinal); $map = New-OrdinalMap; [long]$n = $NextId
  for ($i = 0; $i -lt $b.Candidates.Count; $i++) {
    $c = $b.Candidates[$i]; $key = $keys[$i]
    # 合并后的标量字段取批内首个贡献者（RUN 先传发现者）；每个贡献者自己的定位与分级记在它的 provenance 条目里，合并不丢信息。
    $prov = [ordered]@{ worker_id = $c.Worker; local_id = $c.Content['local_id'] }
    foreach ($k in $script:ProvenanceKeys) { if (-not (Test-Ordinal $k 'worker_id')) { $prov[$k] = $c.Provenance[$k] } }
    foreach ($k in $script:ContributorFields) { $prov[$k] = $c.Content[$k] }
    if ($byKey.Contains($key)) {
      $x = $byKey[$key]
      foreach ($f in $script:UnionFields) { $x[$f] = Get-OrdinalUnion (@($x[$f]) + @($c.Content[$f])) }
      $x['provenance'] = @($x['provenance']) + @($prov)
      $map[$c.Key] = $x['id']; continue
    }
    $x = [ordered]@{ id = "C-$n"; fingerprint = ($script:FingerprintFields | ForEach-Object { ConvertTo-NormalisedText $c.Content[$_] }) -join '|'; root_group = $null; related_to = @() }
    foreach ($f in $fieldOrder) { if (-not (Test-Ordinal $f 'local_id')) { $x[$f] = $(if ($unionSet.Contains($f)) { Get-OrdinalUnion @($c.Content[$f]) } else { $c.Content[$f] }) } }
    $x['provenance'] = @($prov)
    $byKey[$key] = $x; $map[$c.Key] = $x['id']; $n++
  }
  $roots = New-OrdinalMap; $groups = New-OrdinalMap
  foreach ($x in $byKey.Values) {
    if (-not $roots.ContainsKey($x['fingerprint'])) { $roots[$x['fingerprint']] = $x['id']; $groups[$x['id']] = [System.Collections.Generic.List[string]]::new() }
    $x['root_group'] = $roots[$x['fingerprint']]; $groups[$x['root_group']].Add($x['id'])
  }
  foreach ($x in $byKey.Values) { $x['related_to'] = @($groups[$x['root_group']] | Where-Object { -not (Test-Ordinal $_ $x['id']) }) }
  return New-PrereviewResult @() ([ordered]@{ Candidates = @($byKey.Values); NextId = $n; LocalIdMap = $map; StartId = $NextId })
}

function ConvertTo-PrereviewCoverage {
  # A2/A5：coverage 行 → state 形状（candidate_local_ids 经 LocalIdMap 换成 C-<n>）；发现者覆盖缺 C1/C2/C3 的 unit 合成 missing 行。
  param([AllowEmptyCollection()][object[]]$Records, [object[]]$Units, [AllowNull()]$Candidates, [string]$DiscovererWorkerId = 'discoverer')
  $b = Test-PrereviewBatch $Records $Units
  if (-not $b.Ok) { return New-PrereviewResult $b.Reasons ([ordered]@{ Coverage = @() }) }
  # 绑定：-Candidates 必须恰好等于本核心对同一批记录、从其 StartId 重铸出的整个结果（$script:ResultFields 七个属性的规范 JSON 逐字节相等）。
  # 重铸而非核对：改 id / map / 状态、协同改写、补条目、缺字段、失败或别批结果，全走同一个 [PRE-BAD-RECORD] 出口，不抛、不解引用。
  $bad = $true; $re = $null
  try {
    $re = ConvertTo-PrereviewCandidates -Records $Records -Units $Units -NextId ([long]$Candidates.PSObject.Properties['StartId'].Value)
    $canon = { param($v) ConvertTo-Json -InputObject @($v) -Depth 16 -Compress }
    # 键按序数排序（Sort-Object 走 culture、不分大小写：'l1' 与 'L1' 会并列，同一份 map 换个枚举顺序就序列化成两样）。
    $canonMap = { param($m) $d = New-OrdinalMap; foreach ($k in @($m.Keys)) { $d[[string]$k] = $m[$k] }; $ks = [string[]]@($m.Keys | ForEach-Object { [string]$_ }); [Array]::Sort($ks, [StringComparer]::Ordinal); & $canon @($ks | ForEach-Object { @($_, $d[$_]) }) }
    # 属性名集合须序数地恰好等于七个（PSObject.Properties 的索引器不分大小写、多余属性也不报）；赋值不经 $()，免得集合被展开。
    $canonResult = { param($r) $names = [System.Collections.Generic.HashSet[string]]::new([string[]]@($r.PSObject.Properties | ForEach-Object Name), [StringComparer]::Ordinal); if (-not $names.SetEquals([string[]]$script:ResultFields)) { throw 'not a candidates result' }; $o = [ordered]@{}; foreach ($n in $script:ResultFields) { $v = $r.PSObject.Properties[$n].Value; if (Test-Ordinal $n 'LocalIdMap') { $o[$n] = & $canonMap $v } else { $o[$n] = $v } }; & $canon $o }
    $bad = -not ($re.Ok -and (Test-Ordinal (& $canonResult $re) (& $canonResult $Candidates)))
  } catch { $bad = $true }
  if ($bad) { return New-PrereviewResult @('candidates result is not the one minted from this batch (not a result object, failed, another batch, or altered)') ([ordered]@{ Coverage = @() }) }
  $map = $re.LocalIdMap
  $rows = [System.Collections.Generic.List[object]]::new(); $checked = New-OrdinalMap
  foreach ($v in $b.Coverage) {
    # 每个 (worker, lid) 已由 Test-PrereviewBatch 证明是本批的 candidate 键，且上面已证明 map 覆盖全部键：查表不会落空。
    $ids = @(foreach ($lid in @($v.Content['candidate_local_ids'])) { $map[(Get-PrereviewLocalKey $v.Worker $lid)] })
    $unit = [string]$v.Content['unit_id']
    $rows.Add([ordered]@{ unit_id = $unit; worker_id = $v.Worker; categories_checked = @($v.Content['categories_checked']); status = $v.Content['status']; candidate_ids = (Get-OrdinalUnion $ids); missing_context = @($v.Content['missing_context']) })
    if (Test-Ordinal $v.Worker $DiscovererWorkerId) {
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
function Eq($A, $B) { return [string]::Equals([string]$A, [string]$B, [StringComparison]::Ordinal) }   # 断言一律序数相等（PowerShell 的比较运算符走 culture：软连字符 / 零宽字符 / NFD 会被判等）
function Same([object[]]$Actual, [string[]]$Expected) {   # 逐位序数序列相等（L165：断言面 = 契约）
  $a = @($Actual); if ($a.Count -ne $Expected.Count) { return $false }
  for ($i = 0; $i -lt $a.Count; $i++) { if (-not (Eq $a[$i] $Expected[$i])) { return $false } }
  return $true
}
function OrdinalSet([object[]]$Items) { return , [System.Collections.Generic.HashSet[string]]::new([string[]]@($Items | ForEach-Object { [string]$_ }), [StringComparer]::Ordinal) }   # 逗号：函数返回会把集合展开
# reject 夹具清单钉死（文件 → 违规类）：目录须与之恰好相等；少一类或多一个未登记文件都红。
$RejectClasses = [ordered]@{
  'coverage-worker-id.jsonl'          = 'worker-emitted id (A5)'
  'coverage-status-missing.jsonl'     = 'worker-emitted missing (A5)'
  'coverage-verdict.jsonl'            = 'field named verdict'
  'record-unknown-shape.jsonl'        = 'neither candidate nor coverage'
  'coverage-duplicate-unit.jsonl'     = 'two coverage rows for one (worker, unit)'
  'coverage-case-variant-key.jsonl'   = 'Status beside status (ordinal, closed)'
  'malformed.jsonl'                   = 'lines that are not JSON objects'
}
$U1 = 'scripts/a.ps1#0123456789ab'; $U2 = 'scripts/a.ps1#89abcdef0123'; $U3 = 'docs/x.md#file'; $U4 = 'docs/y.md#file'

Check 'harness: Eq / Same / OrdinalSet are ordinal (soft hyphen, ZWSP, case variants stay different)' (-not (Eq 'C-1' ('C-' + [char]0xAD + '1')) -and -not (Same @('a' + [char]0x200B) @('a')) -and -not (Eq 'd1' 'D1') -and -not (OrdinalSet @('l1')).Contains('L1'))
Write-Host '[1/5] Test-PrereviewRecord: booleans, never a throw' -ForegroundColor Cyan
$unitsText = [IO.File]::ReadAllText((Join-Path $FixtureRoot 'units.json'))
$units = @($unitsText | ConvertFrom-Json -AsHashtable -Depth 8)
Check 'units.json validates as $defs/units' (Test-PrereviewRecord -Kind units -Json $unitsText)
$h40 = '3a7f0c9d2b1e4f6a8c0d2e4f6a8b0c2d4e6f8a0b'; $h64 = $h40 + '5e884898da28047151d0e56f'
Check 'a facts document validates as $defs/facts' (Test-PrereviewRecord -Kind facts -Json ('{"snapshot_tree":"' + $h40 + '","head_sha":"' + $h40 + '","base_oid":"' + $h40 + '","base_mode":"local","merge_base":"' + $h40 + '","policy_hash":"' + $h64 + '","rubric_sha":"' + $h64 + '","models":{"discoverer":{"model":"m","effort":"e"},"lens":{"enabled":true,"model":"m","doc_model":"m","effort":"e"}},"risk_class":"risky","pack_layout_version":1}'))
$batch = Read-PrereviewRecords -Path (Join-Path $FixtureRoot 'valid/batch.jsonl')
Check 'valid/batch.jsonl reads as 11 records' ($batch.Ok -and @($batch.Records).Count -eq 11)
$threw = $false; $r = $true
try { $r = Test-PrereviewRecord -Kind candidate -Json '{"local_id": "d1"' } catch { $threw = $true }
Check 'malformed JSON -> false without a throw' (-not $threw -and -not $r)
$threw = $false; $rk = $true; $rp = $null
try { $rk = Test-PrereviewRecord -Kind 'bogus' -Json '{}'; $rp = Read-PrereviewRecords -Path '' } catch { $threw = $true }
Check 'unknown -Kind -> false; empty -Path -> [PRE-BAD-RECORD] cannot read; neither throws' (-not $threw -and -not $rk -and (Eq $rp.Code $script:BadRecordCode) -and @($rp.Records).Count -eq 0)
Check 'candidate with provenance attached -> false (closed shape)' (-not (Test-PrereviewRecord -Kind candidate -Json ($batch.Records[0] | ConvertTo-Json -Depth 8 -Compress)))
Check 'units document given as coverage -> false' (-not (Test-PrereviewRecord -Kind coverage -Json $unitsText))
$threw = $false; $cObj = $null
try { $cObj = ConvertTo-PrereviewCandidates -Records $batch.Records -Units @($unitsText | ConvertFrom-Json) -NextId 1 } catch { $threw = $true }
Check 'units given as PSCustomObject (plain ConvertFrom-Json) -> [PRE-BAD-RECORD], no throw' (-not $threw -and (Eq $cObj.Code $script:BadRecordCode))
$cDupUnit = ConvertTo-PrereviewCandidates -Records $batch.Records -Units @($units + @($units[3])) -NextId 1
Check 'units document repeating a unit_id -> [PRE-BAD-RECORD]' ((Eq $cDupUnit.Code $script:BadRecordCode) -and @($cDupUnit.Reasons | Where-Object { $_.Contains('repeats unit_id') }).Count -eq 1)

Write-Host '[2/5] reject classes -> [PRE-BAD-RECORD], no throw, nothing minted' -ForegroundColor Cyan
$rejectDir = Join-Path $FixtureRoot 'reject'
$present = @(Get-ChildItem -LiteralPath $rejectDir -File | ForEach-Object Name)
Check "reject inventory: $($present.Count) on disk == $($RejectClasses.Count) declared (ordinal names)" ((OrdinalSet $present).SetEquals((OrdinalSet @($RejectClasses.Keys))))
foreach ($name in $RejectClasses.Keys) {
  $path = Join-Path $rejectDir $name
  if (-not (@(Get-ChildItem -LiteralPath $rejectDir -File | Where-Object { Eq $_.Name $name }).Count -eq 1)) { Check "$name is present under exactly that name" $false; continue }
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
    Check "$name -> [PRE-BAD-RECORD] with reasons from both normalisers, NextId untouched, nothing minted" ((Eq $c.Code $script:BadRecordCode) -and (Eq $v.Code $script:BadRecordCode) -and @($c.Reasons).Count -gt 0 -and @($v.Reasons).Count -gt 0 -and $c.NextId -eq 7 -and @($c.Candidates).Count -eq 0 -and @($v.Coverage).Count -eq 0)
  } else {
    Check "$name -> Read returns [PRE-BAD-RECORD] naming line 2 (unclosed) and line 3 (array)" ((Eq $rd.Code $script:BadRecordCode) -and (Same $rd.Reasons @('line 2: not a JSON object', 'line 3: not a JSON object')) -and @($rd.Records).Count -eq 1)
  }
}
function Edit([int]$i, [hashtable]$Set, [string]$Drop = '') { $x = [ordered]@{}; foreach ($k in $batch.Records[$i].Keys) { if (-not (Eq $k $Drop)) { $x[$k] = $batch.Records[$i][$k] } }; foreach ($k in $Set.Keys) { $x[$k] = $Set[$k] }; return $x }
$mem = [ordered]@{
  'candidate unit_ids entry not in units.json'               = @((Edit 0 @{ unit_ids = @('scripts/zz.ps1#0123456789ab') }))
  'coverage candidate_local_ids names another worker local_id' = @($batch.Records[0], (Edit 8 @{ candidate_local_ids = @('d1') }))
  'one worker reuses a local_id'                              = @($batch.Records[0], (Edit 0 @{ expected = 'twice' }))
  'coverage unit_id not in units.json'                        = @((Edit 4 @{ unit_id = 'scripts/zz.ps1#file' }))
  'no worker_id provenance'                                   = @((Edit 4 @{} 'worker_id'))
}
foreach ($name in $mem.Keys) {
  $threw = $false; $c = $null; $v = $null
  try { $c = ConvertTo-PrereviewCandidates -Records $mem[$name] -Units $units -NextId 7; $v = ConvertTo-PrereviewCoverage -Records $mem[$name] -Units $units -Candidates $c } catch { $threw = $true }
  Check "in-memory reject: $name -> both normalisers [PRE-BAD-RECORD], nothing minted, no throw" (-not $threw -and (Eq $c.Code $script:BadRecordCode) -and (Eq $v.Code $script:BadRecordCode) -and $c.NextId -eq 7 -and @($c.Candidates).Count -eq 0 -and @($v.Coverage).Count -eq 0)
}

Write-Host '[3/5] minting, exact merge, near-duplicate groups' -ForegroundColor Cyan
$c1 = ConvertTo-PrereviewCandidates -Records $batch.Records -Units $units -NextId 1
Check 'valid batch normalises' ($c1.Ok -and (Eq $c1.Code '') -and @($c1.Reasons).Count -eq 0)
$ids = @($c1.Candidates | ForEach-Object { $_['id'] })
Check 'ids C-1..C-4 in input order, NextId 5' ((Same $ids @('C-1', 'C-2', 'C-3', 'C-4')) -and $c1.NextId -eq 5)
$k1 = @($c1.Candidates)[0]; $k2 = @($c1.Candidates)[1]; $k3 = @($c1.Candidates)[2]; $k4 = @($c1.Candidates)[3]
$pv = @($k1['provenance'])
Check 'C-1 = d1 + l1 (NFD, whitespace, other line): two provenance entries' ((Same @($pv | ForEach-Object { "$($_['worker_id'])|$($_['local_id'])" }) @('discoverer|d1', 'lens|l1')) -and (Same @($pv | ForEach-Object { $_['model_id'] }) @('claude-opus-5', 'deepseek-v4-flash')))
Check 'C-1 defect/high from d1; provenance keeps each contributor line/kind/severity (l1: 43, question, medium)' ((Eq $k1['kind'] 'defect') -and (Eq $k1['severity_guess'] 'high') -and $pv[0]['line_start'] -eq 41 -and $pv[1]['line_start'] -eq 43 -and (Eq $pv[1]['kind'] 'question') -and (Eq $pv[1]['severity_guess'] 'medium'))
Check 'state candidate keys: id, fingerprint, root_group, related_to, the 18 schema fields minus local_id, provenance' (Same @($k1.Keys) @('id', 'fingerprint', 'root_group', 'related_to', 'kind', 'severity_guess', 'category', 'anchor', 'file', 'symbol', 'line_start', 'line_end', 'unit_ids', 'trigger', 'expected', 'actual', 'impact', 'contract_ref', 'evidence_refs', 'evidence_needed', 'introduced_or_worsened', 'suggested_fix_direction', 'provenance'))
Check 'provenance entry keys: worker_id, local_id, the five stamped keys, kind, severity_guess, anchor, line_start, line_end' (Same @($pv[1].Keys) @('worker_id', 'local_id', 'snapshot_tree', 'model_id', 'lens', 'schema_version', 'schema_revision', 'kind', 'severity_guess', 'anchor', 'line_start', 'line_end'))
Check 'C-1 unions: unit_ids dedup, evidence_refs sorted (41,43,40 in), evidence_needed disjoint sorted' ((Same $k1['unit_ids'] @($U1, $U2)) -and (Same $k1['evidence_refs'] @('scripts/a.ps1:40', 'scripts/a.ps1:41', 'scripts/a.ps1:43')) -and (Same $k1['evidence_needed'] @('docs/x.md', 'scripts/task.ps1 -Local branch')))
Check 'fingerprint = file|category|symbol|contract_ref' ((Eq $k1['fingerprint'] 'scripts/a.ps1|C2|Test-Foo|lesson:L228'))
Check 'near duplicates: root_group C-1 on both, related_to cross-listed' ((Eq $k1['root_group'] 'C-1') -and (Eq $k2['root_group'] 'C-1') -and (Same $k1['related_to'] @('C-2')) -and (Same $k2['related_to'] @('C-1')))
$m = $c1.LocalIdMap
Check 'LocalIdMap: (lens,l1) C-1, (discoverer,d2) C-2, (lens,l2) C-4; (lens,L1) nobody (ordinal)' ((Eq $m[(Get-PrereviewLocalKey 'lens' 'l1')] 'C-1') -and (Eq $m[(Get-PrereviewLocalKey 'discoverer' 'd2')] 'C-2') -and (Eq $m[(Get-PrereviewLocalKey 'lens' 'l2')] 'C-4') -and -not $m.ContainsKey((Get-PrereviewLocalKey 'lens' 'L1')))
$c2 = ConvertTo-PrereviewCandidates -Records $batch.Records -Units $units -NextId $c1.NextId
$ids2 = @($c2.Candidates | ForEach-Object { $_['id'] })
Check 'second call continues at C-5..C-8, NextId 9, no number reused' ((Same $ids2 @('C-5', 'C-6', 'C-7', 'C-8')) -and $c2.NextId -eq 9 -and -not (OrdinalSet $ids).Overlaps((OrdinalSet $ids2)))
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
Check 'tuple keys: (x|y,z) and (x,y|z) stay apart; local key likewise' ($cPipe.Ok -and @($cPipe.Candidates).Count -eq 2 -and -not (Eq (Get-PrereviewLocalKey 'a|b' 'c') (Get-PrereviewLocalKey 'a' 'b|c')))
$threw = $false; $cBig = $null; $cBig2 = $null
try { $cBig = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId ([int]::MaxValue); $cBig2 = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId $cBig.NextId } catch { $threw = $true }
Check 'NextId past Int32.MaxValue: C-2147483647, then 2147483648 feeds the next call' (-not $threw -and (Eq @($cBig.Candidates)[0]['id'] 'C-2147483647') -and $cBig.NextId -eq 2147483648 -and (Eq @($cBig2.Candidates)[0]['id'] 'C-2147483648'))
$threw = $false; $cEx = $null; $cZero = $null; $cStr = $null
try { $cEx = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId ([long]::MaxValue); $cZero = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId 0; $cStr = ConvertTo-PrereviewCandidates -Records @($p1) -Units $units -NextId 'abc' } catch { $threw = $true }
Check 'NextId exhausted (Int64.MaxValue, one new id) / 0 / a string: failed result, NextId untouched, nothing minted, no throw' (-not $threw -and (Eq $cEx.Code $script:BadRecordCode) -and $cEx.NextId -eq [long]::MaxValue -and @($cEx.Candidates).Count -eq 0 -and (Eq $cZero.Code $script:BadRecordCode) -and $cZero.NextId -eq 0 -and (Eq $cStr.Code $script:BadRecordCode) -and (Eq $cStr.NextId 'abc'))
$threw = $false; $dupTop = $null
try { $dupTop = ConvertTo-PrereviewCandidates -Records @($batch.Records[0], $batch.Records[6]) -Units $units -NextId ([long]::MaxValue - 1) } catch { $threw = $true }
Check 'two exact duplicates at Int64.MaxValue-1 need one id: minted, NextId = Int64.MaxValue (exhaustion counts unique keys)' (-not $threw -and $dupTop.Ok -and @($dupTop.Candidates).Count -eq 1 -and $dupTop.NextId -eq [long]::MaxValue)

Write-Host '[4/5] coverage rows, binding, missing synthesis' -ForegroundColor Cyan
$v1 = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $c1
Check 'coverage normalises' ($v1.Ok -and (Eq $v1.Code ''))
$rows = @($v1.Coverage)
function Row([string]$Unit, $Worker) { return , @($rows | Where-Object { (Eq $_['unit_id'] $Unit) -and (Eq $_['worker_id'] $Worker) }) }
Check '6 worker rows + 3 missing rows' ($rows.Count -eq 9)
$d1 = Row $U1 'discoverer'; $l1 = Row $U1 'lens'; $d3 = Row $U3 'discoverer'
$u2d = Row $U2 'discoverer'
Check 'coverage row keys and values: U2/discoverer checked_no_finding with C1, C3, no candidate_ids' ($u2d.Count -eq 1 -and (Same @($u2d[0].Keys) @('unit_id', 'worker_id', 'categories_checked', 'status', 'candidate_ids', 'missing_context')) -and (Eq $u2d[0]['status'] 'checked_no_finding') -and (Same $u2d[0]['categories_checked'] @('C1', 'C3')) -and @($u2d[0]['candidate_ids']).Count -eq 0)
$vLens = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $c1 -DiscovererWorkerId 'lens'
Check '-DiscovererWorkerId lens: the lens rows drive synthesis, so U1..U4 are all missing (lens checks no C1/C3)' ($vLens.Ok -and (Same @($vLens.Coverage | Where-Object { Eq $_['status'] 'missing' } | ForEach-Object { $_['unit_id'] }) @($U1, $U2, $U3, $U4)))
Check 'U1/discoverer: finding, candidate_ids C-1,C-2' ($d1.Count -eq 1 -and (Eq $d1[0]['status'] 'finding') -and (Same $d1[0]['candidate_ids'] @('C-1', 'C-2')))
Check 'U1/lens: finding, candidate_ids C-1 (merged id)' ($l1.Count -eq 1 -and (Same $l1[0]['candidate_ids'] @('C-1')))
Check 'U3/discoverer: blocked with missing_context, candidate_ids C-3' ($d3.Count -eq 1 -and (Eq $d3[0]['status'] 'blocked') -and (Same $d3[0]['missing_context'] @('docs/y.md')) -and (Same $d3[0]['candidate_ids'] @('C-3')))
$missing = @($rows | Where-Object { (Eq $_['status'] 'missing') })
Check 'missing rows for exactly U2 (lacks C2), U3 (lacks C2, C3) and U4 (no row from anyone), in units order' ((Same @($missing | ForEach-Object { $_['unit_id'] }) @($U2, $U3, $U4)))
Check 'missing rows: worker_id null, empty categories / candidate_ids / missing_context' (@($missing | Where-Object { $null -eq $_['worker_id'] -and @($_['categories_checked']).Count -eq 0 -and @($_['candidate_ids']).Count -eq 0 -and @($_['missing_context']).Count -eq 0 }).Count -eq 3)
Check 'U4 (no row from any worker) gets exactly one missing row' (@($rows | Where-Object { (Eq $_['unit_id'] $U4) }).Count -eq 1)
Check 'no missing row for U1; the lens C2 row on U2 does not rescue it' (@($missing | Where-Object { (Eq $_['unit_id'] $U1) }).Count -eq 0 -and @(Row $U2 'lens').Count -eq 1)
$zw = [ordered]@{}; foreach ($k in $batch.Records[9].Keys) { $zw[$k] = $batch.Records[9][$k] }; $zw['worker_id'] = 'discoverer' + [char]0x200B
$cZw = ConvertTo-PrereviewCandidates -Records @($batch.Records + @($zw)) -Units $units -NextId 1
$vZw = ConvertTo-PrereviewCoverage -Records @($batch.Records + @($zw)) -Units $units -Candidates $cZw
Check 'a worker named discoverer+ZWSP is not the discoverer (ordinal): its C2 row on U2 leaves U2 missing' ($vZw.Ok -and @($vZw.Coverage | Where-Object { (Eq $_['unit_id'] $U2) -and (Eq $_['status'] 'missing') }).Count -eq 1)
$cDup = ConvertTo-PrereviewCandidates -Records @($batch.Records + @($batch.Records[0])) -Units $units -NextId 1
$threw = $false; $vBad = $null
try { $vBad = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $cDup } catch { $threw = $true }
Check 'failed candidates result (d1 twice: Ok false) refused, no throw' (-not $threw -and -not $cDup.Ok -and $null -ne $vBad -and (Eq $vBad.Code $script:BadRecordCode) -and @($vBad.Coverage).Count -eq 0)
$alt = @($batch.Records | ForEach-Object { $_ }); $alt[1] = $p2; $alt[1]['local_id'] = 'd2'
$cAlt = ConvertTo-PrereviewCandidates -Records $alt -Units $units -NextId 1
$vOther = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $cAlt
Check 'another batch result refused (recompute differs) though every local_id resolves' ((Eq $vOther.Code $script:BadRecordCode) -and @($vOther.Coverage).Count -eq 0)
function Forge($Map, $Cands, $Next = $c1.NextId) { [pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $Cands; NextId = $Next; LocalIdMap = $Map; StartId = 1 } }
function MapWith([string]$Key, $Value, [string]$Drop = '') { $m = New-OrdinalMap; foreach ($k in $c1.LocalIdMap.Keys) { if (-not (Eq $k $Drop)) { $m[$k] = $c1.LocalIdMap[$k] } }; if ($Key) { $m[$Key] = $Value }; return $m }
function CopyCands { return @($c1.Candidates | ForEach-Object { $x = [ordered]@{}; foreach ($k in $_.Keys) { $x[$k] = $_[$k] }; $x }) }
$d1k = Get-PrereviewLocalKey 'discoverer' 'd1'; $d3k = Get-PrereviewLocalKey 'discoverer' 'd3'
$rewritten = CopyCands; $rewritten[0]['id'] = 'C-9'; $coMap = MapWith $d1k 'C-9'; $coMap[(Get-PrereviewLocalKey 'lens' 'l1')] = 'C-9'
$forged = @(
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $c1.Candidates; NextId = $c1.NextId; StartId = 1 }),                          # no LocalIdMap
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap }),   # no StartId
  (Forge 'not a map' $c1.Candidates), (Forge (MapWith $d1k 'C-999') $c1.Candidates), (Forge (MapWith '' $null (Get-PrereviewLocalKey 'lens' 'l2')) $c1.Candidates),   # non-dictionary / out-of-set / short
  (Forge (MapWith $d3k 'C-1') $c1.Candidates), (Forge (MapWith $d3k 'C-777') @($c1.Candidates + @([ordered]@{ id = 'C-777' }))),   # in-set remap / id-only pad
  (Forge $c1.LocalIdMap @($c1.Candidates + @([ordered]@{ id = 'C-5'; provenance = @() }))), (Forge $coMap $rewritten), (Forge $c1.LocalIdMap $c1.Candidates 6),   # provenance-free pad / coordinated rewrite / NextId altered
  ([pscustomobject]@{ Ok = $false; Code = $script:BadRecordCode; Reasons = @('x'); Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap; StartId = 1 }),   # status-only alteration
  ([pscustomobject]@{ Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap; StartId = 1 }),   # Ok / Code / Reasons missing
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = $null; Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap; StartId = 1 }),   # Reasons null, not []
  ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $c1.Candidates; NextId = $c1.NextId; LocalIdMap = $c1.LocalIdMap; StartId = 1; Extra = 1 }),   # eighth property
  (Forge (MapWith $d1k @('C-1')) $c1.Candidates),   # map value wrapped in an array
  (Forge (MapWith $d1k ('C-' + [char]0xAD + '1')) (& { $z = CopyCands; $z[0]['id'] = 'C-' + [char]0xAD + '1'; $z }))   # soft hyphen inside an id: culture-equal, ordinal-different
)
$gen = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal); foreach ($k in $c1.LocalIdMap.Keys) { $gen[$k] = $c1.LocalIdMap[$k] }
$threw = $false; $vF = @(); $vGen = $null
try { $vF = @(foreach ($f in $forged) { ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $f }); $vGen = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates (Forge $gen $c1.Candidates) } catch { $threw = $true; Write-Host "    threw: $($_.Exception.Message)" -ForegroundColor Red }
Check 'the 16 forged results above are all refused, no throw' (-not $threw -and @($vF | Where-Object { (Eq $_.Code $script:BadRecordCode) -and @($_.Coverage).Count -eq 0 }).Count -eq 16)
Check 'a generic Dictionary[string,string] holding the true map is accepted (same 9 rows)' (-not $threw -and $vGen.Ok -and @($vGen.Coverage).Count -eq 9)
$qU = [ordered]@{}; foreach ($k in $batch.Records[0].Keys) { $qU[$k] = $batch.Records[0][$k] }; $qU['local_id'] = 'D1'; $qU['expected'] = 'upper twin'
$cCase = ConvertTo-PrereviewCandidates -Records @($batch.Records[0], $qU) -Units $units -NextId 1
$rev = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal); foreach ($k in @(@($cCase.LocalIdMap.Keys)[-1..0])) { $rev[$k] = $cCase.LocalIdMap[$k] }
$vCase = ConvertTo-PrereviewCoverage -Records @($batch.Records[0], $qU) -Units $units -Candidates ([pscustomobject]@{ Ok = $true; Code = ''; Reasons = @(); Candidates = $cCase.Candidates; NextId = $cCase.NextId; LocalIdMap = $rev; StartId = 1 })
Check 'case-variant local ids (d1, D1) in reverse insertion order still bind (ordinal key sort, not culture)' ($cCase.Ok -and @($cCase.Candidates).Count -eq 2 -and $vCase.Ok)
$threw = $false; $vNull = $null
try { $vNull = ConvertTo-PrereviewCoverage -Records $batch.Records -Units $units -Candidates $null } catch { $threw = $true }
Check '-Candidates null -> [PRE-BAD-RECORD], no throw' (-not $threw -and (Eq $vNull.Code $script:BadRecordCode))
$vUnits = ConvertTo-PrereviewCoverage -Records $batch.Records -Units @() -Candidates $c1
Check 'coverage re-validates its batch: empty units, matching result -> [PRE-BAD-RECORD], no row' ((Eq $vUnits.Code $script:BadRecordCode) -and @($vUnits.Coverage).Count -eq 0 -and @($vUnits.Reasons | Where-Object { $_.Contains('not in units.json') }).Count -gt 0)

Write-Host '[5/5] environment: PRE_LIVE and PRE_LENS_ENDPOINT absent at the end' -ForegroundColor Cyan
Check 'PRE_LIVE / PRE_LENS_ENDPOINT are not set' (-not (Test-Path Env:PRE_LIVE) -and -not (Test-Path Env:PRE_LENS_ENDPOINT))

if ($fails.Count) { $fails | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }; Write-Host $FailSentinel -ForegroundColor Red; exit 1 }
Write-Host $PassSentinel -ForegroundColor Green
exit 0
