#requires -Version 7
<#
.SYNOPSIS
  PR review v2 记录 schema 的机检：specs/prereview-record.schema.json（冻结契约）、其夹具、协议文档状态码表的锚定。
.DESCRIPTION
  三种模式，全按 ASCII 哨兵 + 退出码判（L165）：
    默认（无参）  在 scripts/fixtures/prereview/schema/{records,anchors,mini} 上自演练下面两种模式 + 记录 schema 的契约走查；
                  末行 [PREREVIEW-SCHEMA-OK]（exit 0）/ [PREREVIEW-SCHEMA-FAIL]（exit 1）。这是本卡的 DoD。
    -Schema f [-Samples d]  schema 卫生（可解析 / 每个 $ref 恰好是同文档 #/$defs/<name> 且目标在 / object 形状皆封闭，if/then/else 除外）
                  + 样本：d/valid/*.json 必须通过、d/reject/*.json 必须失败，唯一判据 = Test-Json -ErrorAction SilentlyContinue 的布尔值；
                  <def>.<name>.json 对 $defs/<def> 校验（内存里包一层 {$ref,$defs}），其余对整份 schema；*.schema.json 不算样本。
    -Anchors doc  标题「Status codes」下第一张表，每个数据行第 1 列的第一个 [PRE-…] token 是码，与 $defs/status_code 枚举双向核：
                  缺行 / 外码 / 重复行任一即 [PREREVIEW-SCHEMA-FAIL]，全合即 [PREREVIEW-ANCHORS-OK]。
  投影 worker-envelope.min.json 的四项检查归 WORKERS-CLAUDE；本脚本只把它当一份 schema 对 mini/ 跑样本。
#>
[CmdletBinding()]
param(
  [string]$Schema,
  [string]$Samples,
  [string]$Anchors
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RecordSchemaPath = Join-Path $RepoRoot 'specs/prereview-record.schema.json'
$FixtureRoot = Join-Path $RepoRoot 'scripts/fixtures/prereview/schema'
$OkSentinel = '[PREREVIEW-SCHEMA-OK]'
$FailSentinel = '[PREREVIEW-SCHEMA-FAIL]'
$AnchorsOkSentinel = '[PREREVIEW-ANCHORS-OK]'
$LocalRefPrefix = '#/$defs/'
$LocalRefPattern = '^#/\$defs/([^/#]+)$'   # 恰好同文档、直指一个 $defs 名；多一个 # 或 / 都不算
$StatusCodePattern = '\[PRE-[A-Z0-9-]+\]'
# 枚举值一律 lower_snake，例外只有两个标识符枚举：category 的 C 码、status_code 的 [PRE-…] 码。比较一律区分大小写（-c 系）。
$EnumValuePattern = '^([a-z][a-z0-9_]*|C[0-9]+|\[PRE-[A-Z0-9-]+\])$'
# records[] 里禁止的字段：适配器盖章的 provenance + RECORDS 铸造的核心字段 + 裁决词。
$RecordForbiddenFields = @('id', 'fingerprint', 'root_group', 'related_to', 'snapshot_tree', 'worker_id', 'model_id', 'lens', 'schema_version', 'schema_revision', 'verdict')
# 默认模式钉死的 reject 夹具清单（文件 → 违规类）：目录须与之恰好相等，少一类或多一个未登记文件都红。
$RejectClasses = [ordered]@{
  'candidate-verdict.json'               = 'field named verdict'
  'coverage-status-pass.json'            = 'status pass'
  'coverage-unknown-field.json'          = 'unknown field'
  'envelope-missing-schema-version.json' = 'schema_version absent'
  'envelope-wrong-revision.json'         = 'schema_revision not the pinned const'
  'coverage-id.json'                     = 'worker-emitted id'
  'coverage-status-missing.json'         = 'worker-emitted missing'
  'coverage-provenance.json'             = 'provenance inside records[]'
  'coverage-blocked-no-context.json'     = 'blocked without missing_context'
  'candidate-symbol-null-no-line.json'   = 'symbol null without line_start'
  'facts.bad-base-mode.json'             = 'facts base_mode outside the enum'
  'cross-file-ref.schema.json'           = 'schema: cross-file $ref'
  'hidden-ref.schema.json'               = 'schema: cross-file $ref under patternProperties'
  'malformed-ref.schema.json'            = 'schema: $ref not exactly #/$defs/<name>'
}
function Read-SchemaFile([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "schema file not found: $Path" }
  $text = [IO.File]::ReadAllText($Path)
  $obj = $text | ConvertFrom-Json -AsHashtable -Depth 64
  if (-not ($obj -is [System.Collections.IDictionary])) { throw "schema is not a JSON object: $Path" }
  return @{ Path = $Path; Text = $text; Obj = $obj }
}

# 扁平列出每个子 schema（路径, 节点）：只沿 draft 2020-12 的 schema 位置下钻，enum/required/description 的值不当 schema。
function Get-SchemaNodes($Node, [string]$Path = '#') {
  $acc = [System.Collections.Generic.List[object]]::new()
  $stack = [System.Collections.Generic.Stack[object]]::new()
  $stack.Push(@($Path, $Node))
  while ($stack.Count) {
    $pair = $stack.Pop(); $p = $pair[0]; $n = $pair[1]
    if (-not ($n -is [System.Collections.IDictionary])) { continue }
    $acc.Add([pscustomobject]@{ Path = $p; Node = $n })
    foreach ($k in @('properties', 'patternProperties', 'dependentSchemas', '$defs')) {
      if ($n.Contains($k) -and $n[$k] -is [System.Collections.IDictionary]) { foreach ($c in $n[$k].Keys) { $stack.Push(@("$p/$k/$c", $n[$k][$c])) } }
    }
    foreach ($k in @('items', 'additionalProperties', 'propertyNames', 'unevaluatedProperties', 'unevaluatedItems', 'contentSchema', 'if', 'then', 'else', 'not', 'contains')) { if ($n.Contains($k)) { $stack.Push(@("$p/$k", $n[$k])) } }
    foreach ($k in @('anyOf', 'oneOf', 'allOf', 'prefixItems')) {
      if ($n.Contains($k)) { $i = 0; foreach ($c in @($n[$k])) { $stack.Push(@("$p/$k/$i", $c)); $i++ } }
    }
  }
  return $acc
}

# 列出 JSON 里**每一个**对象节点（含数组里的），不问它是不是 schema 位置：$ref 扫描用它，藏在任何关键字下的 $ref 都逃不掉。
function Get-JsonNodes($Node, [string]$Path = '#') {
  $acc = [System.Collections.Generic.List[object]]::new()
  $stack = [System.Collections.Generic.Stack[object]]::new()
  $stack.Push(@($Path, $Node))
  while ($stack.Count) {
    $pair = $stack.Pop(); $p = $pair[0]; $n = $pair[1]
    if ($n -is [System.Collections.IDictionary]) {
      $acc.Add([pscustomobject]@{ Path = $p; Node = $n })
      foreach ($k in $n.Keys) { $stack.Push(@("$p/$k", $n[$k])) }
    } elseif ($n -is [System.Collections.IList] -and -not ($n -is [string])) {
      $i = 0; foreach ($c in $n) { $stack.Push(@("$p/$i", $c)); $i++ }
    }
  }
  return $acc
}

# 从一个起点出发、跟着（恰好同文档形态的）$ref 传递闭包可达的全部节点（records[] 可达形状的判定用）。
function Get-ReachableNodes($Root, $Start, [string]$StartPath) {
  $seen = [System.Collections.Generic.HashSet[string]]::new()
  $nodes = [System.Collections.Generic.List[object]]::new()
  $queue = [System.Collections.Generic.Queue[object]]::new()
  $queue.Enqueue(@($StartPath, $Start))
  while ($queue.Count) {
    $pair = $queue.Dequeue()
    foreach ($e in (Get-SchemaNodes $pair[1] $pair[0])) {
      $nodes.Add($e)
      if ($e.Node.Contains('$ref')) {
        $m = [regex]::Match([string]$e.Node['$ref'], $LocalRefPattern)
        if ($m.Success -and $seen.Add($m.Groups[1].Value) -and $Root['$defs'].Contains($m.Groups[1].Value)) { $queue.Enqueue(@("$LocalRefPrefix$($m.Groups[1].Value)", $Root['$defs'][$m.Groups[1].Value])) }
      }
    }
  }
  return $nodes
}

# schema 卫生（-Schema 模式对任何 prereview schema 都适用）：每个 $ref（无论藏在哪个关键字下）都恰好是 #/$defs/<name> 且目标在；
# 每个 object 形状封闭（if/then/else 片段除外）。
function Test-SchemaHygiene($Obj) {
  $reasons = @()
  $defs = if ($Obj.Contains('$defs') -and $Obj['$defs'] -is [System.Collections.IDictionary]) { $Obj['$defs'] } else { @{} }
  foreach ($e in (Get-JsonNodes $Obj)) {
    if (-not $e.Node.Contains('$ref')) { continue }
    $ref = [string]$e.Node['$ref']
    $m = [regex]::Match($ref, $LocalRefPattern)
    if (-not $m.Success) { $reasons += "$($e.Path): `$ref '$ref' is not exactly $LocalRefPrefix<name>"; continue }
    if (-not $defs.Contains($m.Groups[1].Value)) { $reasons += "$($e.Path): `$ref '$ref' has no target in `$defs" }
  }
  foreach ($e in (Get-SchemaNodes $Obj)) {
    $n = $e.Node
    # if/then/else 片段是叠加在同一对象上的约束，不能带 additionalProperties:false（会拒掉其它属性），故豁免。
    $isObject = ($n.Contains('type') -and $n['type'] -ceq 'object') -or ($n.Contains('properties') -and $e.Path -notmatch '/(if|then|else)$')
    if ($isObject -and -not ($n.Contains('additionalProperties') -and $n['additionalProperties'] -is [bool] -and -not $n['additionalProperties'])) {
      $reasons += "$($e.Path): object shape without additionalProperties:false"
    }
  }
  return $reasons
}

# 记录 schema 专属契约（默认模式）：A1/A2 的全称句按 schema 自身算出，不靠散文声称。
function Test-RecordContract($Obj) {
  $reasons = @()
  $req = @(if ($Obj.Contains('required')) { $Obj['required'] } else { @() })
  if (@(Compare-Object $req @('schema_version', 'schema_revision', 'records')).Count) { $reasons += "envelope required must be schema_version, schema_revision, records (got: $($req -join ', '))" }
  if (-not ($Obj.Contains('properties') -and $Obj['properties'].Contains('schema_version') -and $Obj['properties']['schema_version'].Contains('const') -and $Obj['properties']['schema_version']['const'] -eq 1)) { $reasons += 'envelope schema_version must be const 1' }
  $defs = if ($Obj.Contains('$defs')) { $Obj['$defs'] } else { @{} }
  foreach ($d in @('candidate', 'coverage', 'facts', 'units', 'status_code')) { if (-not $defs.Contains($d)) { $reasons += "`$defs/$d is missing" } }
  foreach ($e in (Get-SchemaNodes $Obj)) {
    $n = $e.Node
    if ($n.Contains('properties') -and $n['properties'].Contains('verdict')) { $reasons += "$($e.Path): a property named verdict" }
    if ($n.Contains('enum')) {
      foreach ($v in @($n['enum'])) {
        if ($null -eq $v -or -not ($v -is [string])) { continue }
        if ($v -ceq 'pass' -or $v -ceq 'block') { $reasons += "$($e.Path): enum value '$v' (verdict vocabulary)" }
        if ($v -cnotmatch $EnumValuePattern) { $reasons += "$($e.Path): enum value '$v' is not lower_snake / C-code / [PRE-…] code" }
      }
    }
  }
  if ($Obj.Contains('properties') -and $Obj['properties'].Contains('records') -and $Obj['properties']['records'].Contains('items')) {
    foreach ($e in (Get-ReachableNodes $Obj $Obj['properties']['records']['items'] '#/properties/records/items')) {
      $n = $e.Node
      if ($n.Contains('properties')) { foreach ($f in $RecordForbiddenFields) { if ($n['properties'].Contains($f)) { $reasons += "$($e.Path): records[] shape admits field '$f'" } } }
      if ($n.Contains('enum') -and @($n['enum']) -ccontains 'missing') { $reasons += "$($e.Path): records[] shape admits the state-only 'missing'" }
    }
  } else { $reasons += 'envelope has no records[] items shape' }
  if ($defs.Contains('status_code')) {
    $codes = @(if ($defs['status_code'].Contains('enum')) { $defs['status_code']['enum'] } else { @() })
    if ($codes.Count -eq 0) { $reasons += '$defs/status_code has no enum' }
    foreach ($c in $codes) { if (-not ($c -is [string]) -or $c -cnotmatch "^$StatusCodePattern`$") { $reasons += "`$defs/status_code: '$c' is not a [PRE-…] code" } }
    if (@($codes | Select-Object -Unique).Count -ne $codes.Count) { $reasons += '$defs/status_code enum has duplicates' }
  }
  return $reasons
}

# 样本：valid/ 必须通过、reject/ 必须失败；唯一判据 = Test-Json -ErrorAction SilentlyContinue 的布尔值。
function Test-Samples($SchemaFile, [string]$Dir) {
  $reasons = @(); $lines = @()
  foreach ($kind in @('valid', 'reject')) {
    $sub = Join-Path $Dir $kind
    $files = @(if (Test-Path -LiteralPath $sub -PathType Container) { Get-ChildItem -LiteralPath $sub -Filter *.json -File | Where-Object { $_.Name -notlike '*.schema.json' } | Sort-Object Name })
    if ($files.Count -eq 0) { $reasons += "no samples under $sub"; continue }
    foreach ($f in $files) {
      $schemaText = $SchemaFile.Text
      $target = 'schema'
      if ($f.Name -match '^(?<def>[a-z_]+)\.[^.]+\.json$' -and $SchemaFile.Obj.Contains('$defs') -and $SchemaFile.Obj['$defs'].Contains($Matches['def'])) {
        $target = "`$defs/$($Matches['def'])"
        $schemaText = @{ '$ref' = "#/`$defs/$($Matches['def'])"; '$defs' = $SchemaFile.Obj['$defs'] } | ConvertTo-Json -Depth 64 -Compress
      }
      $ok = Test-Json -Json ([IO.File]::ReadAllText($f.FullName)) -Schema $schemaText -ErrorAction SilentlyContinue
      $expect = ($kind -eq 'valid')
      $lines += "  $kind/$($f.Name) vs $target -> $(if ($ok) { 'valid' } else { 'rejected' })$(if ($ok -ne $expect) { ' (UNEXPECTED)' })"
      if ($ok -ne $expect) { $reasons += "$kind/$($f.Name): expected Test-Json $expect, got $ok" }
    }
  }
  return @{ Reasons = $reasons; Lines = $lines }
}

function Invoke-SchemaMode([string]$SchemaPath, [string]$SamplesDir) {
  $reasons = @(); $lines = @()
  try { $sf = Read-SchemaFile $SchemaPath } catch { return @{ Ok = $false; Reasons = @("$($_.Exception.Message)"); Lines = @() } }
  $reasons += Test-SchemaHygiene $sf.Obj
  $lines += "  schema ${SchemaPath}: $((Get-SchemaNodes $sf.Obj).Count) sub-schemas, hygiene $(if ($reasons.Count) { 'FAILED' } else { 'ok' })"
  if ($SamplesDir) { $r = Test-Samples $sf $SamplesDir; $reasons += $r.Reasons; $lines += $r.Lines }
  return @{ Ok = ($reasons.Count -eq 0); Reasons = $reasons; Lines = $lines }
}

function Get-StatusCodeEnum() {
  $sf = Read-SchemaFile $RecordSchemaPath
  $enum = @(if ($sf.Obj.Contains('$defs') -and $sf.Obj['$defs'].Contains('status_code') -and $sf.Obj['$defs']['status_code'].Contains('enum')) { $sf.Obj['$defs']['status_code']['enum'] })
  if ($enum.Count -eq 0) { throw '$defs/status_code enum is empty or missing' }
  return $enum
}

# 表锚定：见头注 -Anchors；缺行 / 外码 / 重复各自独立报。
function Invoke-AnchorsMode([string]$DocPath) {
  $reasons = @(); $lines = @()
  try { $enum = Get-StatusCodeEnum } catch { return @{ Ok = $false; Reasons = @("$($_.Exception.Message)"); Lines = @() } }
  if (-not (Test-Path -LiteralPath $DocPath -PathType Leaf)) { return @{ Ok = $false; Reasons = @("doc not found: $DocPath"); Lines = @() } }
  $doc = [IO.File]::ReadAllText($DocPath) -split '\r?\n'
  $h = -1; for ($i = 0; $i -lt $doc.Count; $i++) { if ($doc[$i] -cmatch '^#{1,6}\s+Status codes\b') { $h = $i; break } }
  if ($h -lt 0) { return @{ Ok = $false; Reasons = @("no heading 'Status codes' in $DocPath"); Lines = @() } }
  $t = $h + 1
  while ($t -lt $doc.Count -and $doc[$t] -notmatch '^\s*\|' -and $doc[$t] -notmatch '^#') { $t++ }
  $table = @(); while ($t -lt $doc.Count -and $doc[$t] -match '^\s*\|') { $table += $doc[$t]; $t++ }
  if ($table.Count -lt 3 -or $table[1] -notmatch '^\s*\|\s*:?-') { return @{ Ok = $false; Reasons = @('no table (header, separator, rows) under the Status codes heading'); Lines = @() } }
  $seen = [System.Collections.Generic.HashSet[string]]::new()
  foreach ($row in $table[2..($table.Count - 1)]) {
    $m = [regex]::Match(($row -split '\|')[1], $StatusCodePattern)
    if (-not $m.Success) { $reasons += "row without a [PRE-…] code in column 1: $row"; continue }
    $code = $m.Value
    if (-not $seen.Add($code)) { $reasons += "duplicate row for $code" }
    if ($enum -cnotcontains $code) { $reasons += "code outside the enum: $code" }
  }
  foreach ($c in $enum) { if (-not $seen.Contains($c)) { $reasons += "missing enum row: $c" } }
  $lines += "  anchors ${DocPath}: $($seen.Count) table codes vs $($enum.Count) enum codes"
  return @{ Ok = ($reasons.Count -eq 0); Reasons = $reasons; Lines = $lines }
}

function Write-ModeResult($Result, [string]$OkLine) {
  $Result.Lines | ForEach-Object { Write-Host $_ }
  if ($Result.Ok) { Write-Host $OkLine -ForegroundColor Green; exit 0 }
  $Result.Reasons | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  Write-Host $FailSentinel -ForegroundColor Red
  exit 1
}

if ($Anchors) {
  if ($Schema -or $Samples) { Write-Host "$FailSentinel -Anchors takes neither -Schema nor -Samples" -ForegroundColor Red; exit 2 }
  Write-ModeResult (Invoke-AnchorsMode $Anchors) $AnchorsOkSentinel
}
if ($Schema) { Write-ModeResult (Invoke-SchemaMode $Schema $Samples) $OkSentinel }
if ($Samples) { Write-Host "$FailSentinel -Samples requires -Schema" -ForegroundColor Red; exit 2 }

# ── 默认模式：自演练（DoD）──
$all = @()
function Expect([string]$Label, $Result, [bool]$ShouldPass) {
  $Result.Lines | ForEach-Object { Write-Host $_ }
  $good = ($Result.Ok -eq $ShouldPass)
  Write-Host "  $Label -> $(if ($Result.Ok) { 'pass' } else { 'fail' })$(if (-not $ShouldPass) { ' (expected)' })$(if (-not $good) { ' UNEXPECTED' })" -ForegroundColor $(if ($good) { 'DarkGray' } else { 'Red' })
  if ($good) { return }
  $script:all += if ($ShouldPass) { @($Result.Reasons | ForEach-Object { "${Label}: $_" }) } else { "${Label}: expected a failure, got pass" }
}
Write-Host '[1/5] record schema: hygiene + contract' -ForegroundColor Cyan
try {
  $rs = Read-SchemaFile $RecordSchemaPath
  $contract = @(Test-SchemaHygiene $rs.Obj) + @(Test-RecordContract $rs.Obj)
  Expect 'contract' @{ Ok = ($contract.Count -eq 0); Reasons = $contract; Lines = @("  $($rs.Obj['$defs'].Keys.Count) `$defs, $(@(Get-StatusCodeEnum).Count) status codes") } $true
} catch { $all += "record schema: $($_.Exception.Message)" }
Write-Host '[2/5] records/ samples against the record schema' -ForegroundColor Cyan
Expect 'records/' (Invoke-SchemaMode $RecordSchemaPath (Join-Path $FixtureRoot 'records')) $true
$rejectDir = Join-Path $FixtureRoot 'records/reject'
$present = @(if (Test-Path -LiteralPath $rejectDir) { Get-ChildItem -LiteralPath $rejectDir -Filter *.json -File | ForEach-Object Name })
$drift = @(Compare-Object $present @($RejectClasses.Keys) | ForEach-Object { "reject inventory drift: $($_.InputObject) $(if ($_.SideIndicator -eq '<=') { '(on disk, not declared)' } else { '(declared, not on disk)' })" })
$all += $drift
Write-Host "  reject inventory: $($present.Count) on disk vs $($RejectClasses.Count) declared classes$(if ($drift.Count) { ' (DRIFT)' })" -ForegroundColor $(if ($drift.Count) { 'Red' } else { 'DarkGray' })
Write-Host '[3/5] every reject/*.schema.json must fail -Schema' -ForegroundColor Cyan
foreach ($bad in @(Get-ChildItem -LiteralPath $rejectDir -Filter *.schema.json -File | Sort-Object Name)) { Expect "reject/$($bad.Name)" (Invoke-SchemaMode $bad.FullName '') $false }
Write-Host '[4/5] anchors: ok.md passes; missing-row / extra-code / duplicate-row fail' -ForegroundColor Cyan
Expect 'anchors/ok.md' (Invoke-AnchorsMode (Join-Path $FixtureRoot 'anchors/ok.md')) $true
foreach ($neg in @('missing-row', 'extra-code', 'duplicate-row')) { Expect "anchors/$neg.md" (Invoke-AnchorsMode (Join-Path $FixtureRoot "anchors/$neg.md")) $false }
Write-Host '[5/5] mini/ samples against the worker-envelope projection' -ForegroundColor Cyan
Expect 'mini/' (Invoke-SchemaMode (Join-Path $FixtureRoot 'worker-envelope.min.json') (Join-Path $FixtureRoot 'mini')) $true
if ($all.Count) { $all | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }; Write-Host $FailSentinel -ForegroundColor Red; exit 1 }
Write-Host $OkSentinel -ForegroundColor Green
exit 0
