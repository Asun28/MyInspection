#requires -Version 7
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('graph', 'policy', 'diagnostics', 'gav-bounds', 'integration', 'provision-handoff')]
  [string]$Suite,
  [string]$ScannerPath = (Join-Path $PSScriptRoot 'check-licenses.ps1'),
  [switch]$SkipMutations,
  [switch]$SkipRealScan
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_encoding.ps1') # Required UTF-8/native-error prelude; load failure must stop the suite.
# 本脚本不能靠 `. $ScannerPath -AsLibrary` 继承前奏：check-licenses.ps1 在 `-AsLibrary` 处提前 return，
# 前奏写在那两行**之后**（house gate 1g 对库脚本的既定形态）。本脚本的 stdout 现在含中文，父进程
# （selftest / task.ps1）在非 UTF-8 Windows 控制台上会按 OEM 解码，断言遂假红。故自行 dot-source，
# 并已登记进 selftest 闸 1g 的 $encScripts（该闸的计数从清单派生，无字面量要改）。

# -SkipRealScan 只对 integration 有意义：其余套件收下它却完全忽略，调用方以为省掉了真实扫描。
# 这道守卫排在 `. $ScannerPath -AsLibrary` **之前**——无效调用不该先把整个扫描器加载进来再被拒。
# 失败面是 ASCII 码而非散文（L165：机检读哨兵）；它由 integration 套件从子进程验证，并配一枚删除变异。
if ($SkipRealScan -and $Suite -ne 'integration') {
  Write-Error "[INTEGRATION-SKIPREALSCAN-SCOPE] -SkipRealScan is valid only for -Suite integration (got '$Suite')"
  exit 1
}

# This suite is intentionally the only network-enabled scanner proof. CI calls it from the online
# provisioning step; integration/selftest and every production scan remain offline and deterministic.
if ($Suite -eq 'provision-handoff') {
  $expectedUvVersion = '0.7.9'
  $handoffRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("license-uv-handoff-" + [guid]::NewGuid().ToString('N'))
  $handoffScripts = Join-Path $handoffRoot 'scripts'
  $handoffTrapBin = Join-Path $handoffRoot 'trap-bin'
  $handoffCache = Join-Path $handoffRoot 'uv-cache-5.5.5'
  $fallbackMarker = Join-Path $handoffRoot 'fallback-invoked.txt'
  $scannerRoot = Split-Path -Parent ([System.IO.Path]::GetFullPath($ScannerPath))
  $priorPath = $env:PATH
  $priorUvCache = [Environment]::GetEnvironmentVariable('UV_CACHE_DIR', 'Process')
  $priorUvOffline = [Environment]::GetEnvironmentVariable('UV_OFFLINE', 'Process')
  $priorFallbackMarker = [Environment]::GetEnvironmentVariable('UV_HANDOFF_FALLBACK_MARKER', 'Process')
  try {
    New-Item -ItemType Directory -Force -Path $handoffScripts, $handoffTrapBin, $handoffCache | Out-Null
    foreach ($sibling in @('check-licenses.ps1', '_config.ps1', '_unicode.ps1', '_encoding.ps1')) {
      Copy-Item -LiteralPath (Join-Path $scannerRoot $sibling) -Destination (Join-Path $handoffScripts $sibling)
    }
    Set-Content -LiteralPath (Join-Path $handoffRoot 'pyproject.toml') -Encoding utf8 -Value "[project]`nname = 'uv-handoff-probe'`nversion = '0.0.0'"
    Set-Content -LiteralPath (Join-Path $handoffTrapBin 'pip-licenses.ps1') -Encoding utf8 -Value @'
param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Remaining)
Set-Content -LiteralPath $env:UV_HANDOFF_FALLBACK_MARKER -Encoding utf8 -Value 'fallback'
exit 31
'@
    $env:PATH = $handoffTrapBin + [System.IO.Path]::PathSeparator + $priorPath
    $env:UV_CACHE_DIR = $handoffCache
    $env:UV_HANDOFF_FALLBACK_MARKER = $fallbackMarker
    Remove-Item Env:UV_OFFLINE -ErrorAction SilentlyContinue

    $uvVersionOutput = (& uv --version 2>&1 | Out-String).Trim()
    $uvVersionExit = $LASTEXITCODE
    if ($uvVersionExit -ne 0 -or $uvVersionOutput -notmatch ('^uv ' + [regex]::Escape($expectedUvVersion) + '(?:\s|$)')) {
      throw "[PROVISION-HANDOFF-UV-VERSION] expected uv $expectedUvVersion, got exit=$uvVersionExit output=[$uvVersionOutput]"
    }

    Push-Location $handoffRoot
    try {
      $warmOutput = (& uv run --with pip-licenses==5.5.5 pip-licenses --version 2>&1 | Out-String)
      $warmExit = $LASTEXITCODE
    } finally { Pop-Location }
    if ($warmExit -ne 0 -or $warmOutput -notmatch '(?m)^pip-licenses 5\.5\.5\s*$') {
      throw "[PROVISION-HANDOFF-WARM] exact pip-licenses warm-up failed (exit=$warmExit): $warmOutput"
    }

    $env:UV_OFFLINE = '1'
    $scanOutput = (& pwsh -NoProfile -File (Join-Path $handoffScripts 'check-licenses.ps1') -Strict 2>&1 | Out-String)
    $scanExit = $LASTEXITCODE
    if ($scanExit -ne 0 -or $scanOutput -notmatch '已扫描 PyPI 包' -or (Test-Path -LiteralPath $fallbackMarker)) {
      throw "[PROVISION-HANDOFF-OFFLINE] warmed cache did not feed the production scanner offline (exit=$scanExit fallback=$([bool](Test-Path -LiteralPath $fallbackMarker))): $scanOutput"
    }
    Write-Host 'license-scanner-check(provision-handoff): PASS [online-warm=executed] [offline-scan=executed]'
    exit 0
  } catch {
    Write-Error $_
    exit 1
  } finally {
    $env:PATH = $priorPath
    if ($null -eq $priorUvCache) { Remove-Item Env:UV_CACHE_DIR -ErrorAction SilentlyContinue } else { $env:UV_CACHE_DIR = $priorUvCache }
    if ($null -eq $priorUvOffline) { Remove-Item Env:UV_OFFLINE -ErrorAction SilentlyContinue } else { $env:UV_OFFLINE = $priorUvOffline }
    if ($null -eq $priorFallbackMarker) { Remove-Item Env:UV_HANDOFF_FALLBACK_MARKER -ErrorAction SilentlyContinue } else { $env:UV_HANDOFF_FALLBACK_MARKER = $priorFallbackMarker }
    if (Test-Path -LiteralPath $handoffRoot) { Remove-Item -LiteralPath $handoffRoot -Recurse -Force }
  }
}

$failures = [System.Collections.Generic.List[string]]::new()
function Assert-Graph {
  param(
    [Parameter(Mandatory)][bool]$Condition,
    [Parameter(Mandatory)][string]$Message
  )
  if (-not $Condition) { $failures.Add($Message) }
}

. $ScannerPath -AsLibrary

if ($Suite -eq 'integration') {
  $integrationFailures = [System.Collections.Generic.List[string]]::new()
  function Assert-Integration {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $integrationFailures.Add($Message) }
  }

  $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
  $selftestPath = Join-Path $repoRoot 'scripts/selftest.ps1'
  $workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'
  $policyPath = Join-Path $repoRoot 'docs/LICENSE-POLICY.md'
  $releasePath = Join-Path $repoRoot 'docs/RELEASE-CHECKLIST.md'
  $selftestText = [System.IO.File]::ReadAllText($selftestPath)
  $workflowText = [System.IO.File]::ReadAllText($workflowPath)
  $policyText = [System.IO.File]::ReadAllText($policyPath)
  $releaseText = [System.IO.File]::ReadAllText($releasePath)

  # 这一段的**判据形态**是本卡两轮 R3 的病灶所在，结论写在最前面：
  #   ① PowerShell 侧判「调用」，不判「文本」。`CommandAst.Extent.Text` 覆盖它的全部实参（字符串
  #      字面量在内），于是 `Write-Host "run: …license-scanner-check.ps1 -Suite integration…"` 同时
  #      满足了 WIRING 与 COLD 两条断言（实测 scannerCommands=1 / integrationCalls=1 / coldCalls=1）
  #      ——上一版换成 AST 只去掉了「注释」这一种失效形态，没去掉「提到即算数」。现在要求：命令名
  #      是 pwsh、存在 `-File` 参数、且绑定到 `-File` 的**实参**指向本脚本。
  #   ② YAML 侧把 `run:` 块当 PowerShell 解析，解出**真正被执行的那个路径**。旧谓词
  #      `pwsh[^\r\n]*check-licenses\.ps1` 里的 `pwsh` 来自调用、`check-licenses.ps1` 来自
  #      `Write-Error` 的消息串，同行即绿：把 `$f` 换成 `scripts/check-cards.ps1`、或把整句调用
  #      注释到行尾，两种改法实测都保持绿（`^\s*#` 只排除整行注释）。
  #   ③ 「这一步是活的」写成**正面契约**：顶层键集恰好 name/if/shell/run，且 `if:` 逐字等于姊妹闸
  #      E2E verify gate 用的那条表达式。不写「禁止 continue-on-error / if: false」式黑名单——
  #      黑名单证明不了别名不存在（同型断言在 PR #124 两度被 block）。
  # Get-IntegrationWiringFailures 对输入纯函数，正是为了让下面的变异块喂进注释/删除/改写/移位变体，
  # 并要求它抬起**恰好**那一组失败码。

  # ci.yml 里 License gate 与其姊妹闸共用的运行条件（逐字）。两处都要等于它：只写「等于姊妹闸」会让
  # 「两处一起改成 ${{ false }}」保持绿，故把这条表达式本身钉成字面量。
  $expectedGateCondition = '${{ steps.docs_scope.outputs.docs_only != ''true'' }}'
  $expectedUvCache = '${{ runner.temp }}/license-scanner-uv-5.5.5'
  $expectedUvVersion = '0.7.9'
  # License gate 真正必须执行的脚本路径（ci.yml 的 run: 块里解析出来的字面量）。
  $expectedGateScript = 'scripts/check-licenses.ps1'

  function Get-BoundParameter {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.CommandAst]$Command,
      [Parameter(Mandatory)][string]$Name
    )
    # 返回 $null = 该参数不在这条命令上；否则返回 @{ Argument = <实参 Ast 或 $null（开关形态）> }。
    # `-File value` 的实参是下一个元素；`-File:value` 挂在参数节点自己身上。
    $elements = @($Command.CommandElements)
    for ($i = 0; $i -lt $elements.Count; $i++) {
      $element = $elements[$i]
      if ($element -isnot [System.Management.Automation.Language.CommandParameterAst]) { continue }
      if (-not [string]::Equals($element.ParameterName, $Name, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
      if ($null -ne $element.Argument) { return [PSCustomObject]@{ Argument = $element.Argument } }
      if ($i + 1 -lt $elements.Count -and $elements[$i + 1] -isnot [System.Management.Automation.Language.CommandParameterAst]) {
        return [PSCustomObject]@{ Argument = $elements[$i + 1] }
      }
      return [PSCustomObject]@{ Argument = $null }
    }
    return $null
  }

  function Get-CommandPathArguments {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.CommandAst]$Command,
      [Parameter(Mandatory)][string[]]$PathParameters
    )
    # 收齐这条命令上**所有**可能是写入目标的实参：① 每一个具名路径参数（全部，不是碰上第一个就 break——
    # `Copy-Item -LiteralPath $src -Destination …/gradlew.bat` 会因为「取到 LiteralPath 就停」而漏掉真正的
    # 目标）；② 第一个未被任何参数消费的位置实参（`Set-Content $p 'x'` 这种形态）。
    $targets = [System.Collections.Generic.List[System.Management.Automation.Language.Ast]]::new()
    foreach ($parameterName in $PathParameters) {
      $bound = Get-BoundParameter -Command $Command -Name $parameterName
      if ($null -ne $bound -and $null -ne $bound.Argument) { $targets.Add($bound.Argument) }
    }
    $elements = @($Command.CommandElements)
    $commandInfo = @(Get-Command $Command.GetCommandName() -ErrorAction SilentlyContinue | Select-Object -First 1)
    for ($i = 1; $i -lt $elements.Count; $i++) {
      $element = $elements[$i]
      if ($element -is [System.Management.Automation.Language.CommandParameterAst]) {
        $consumesNext = $true
        if ($commandInfo.Count -eq 1) {
          $parameterMatches = @($commandInfo[0].Parameters.Values | Where-Object {
            $_.Name.StartsWith($element.ParameterName, [System.StringComparison]::OrdinalIgnoreCase)
          })
          if ($parameterMatches.Count -eq 1 -and $parameterMatches[0].ParameterType -eq [switch]) {
            $consumesNext = $false
          }
        }
        if ($consumesNext -and $null -eq $element.Argument -and $i + 1 -lt $elements.Count -and
            $elements[$i + 1] -isnot [System.Management.Automation.Language.CommandParameterAst]) { $i++ }
        continue
      }
      $targets.Add($element)
      break
    }
    return $targets.ToArray()
  }

  function Get-AstLiteralValue {
    param([Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Node)
    # 裸词与带引号的字符串都归一成同一个值；其它形态退回原文，交给调用方按原文判。
    if ($Node -is [System.Management.Automation.Language.StringConstantExpressionAst]) { return $Node.Value }
    return $Node.Extent.Text
  }

  function Test-AstCommandStaticallyLive {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Command,
      [string[]]$AllowedIfConditions = @(),
      [string[]]$RequiredIfConditions = @()
    )
    # Liveness is a positive contract. Every enclosing branch must be one of the exact conditions the caller
    # expects; an expression that merely looks dynamic is not execution evidence (`1 -eq 2` was previously
    # accepted). Loops, else branches, definitions, and script-block expressions are never accepted evidence.
    $allowed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($condition in $AllowedIfConditions) { [void]$allowed.Add(($condition -replace '\s+', ' ').Trim()) }
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $ancestor = $Command.Parent
    while ($null -ne $ancestor) {
      if ($ancestor -is [System.Management.Automation.Language.FunctionDefinitionAst] -or
          $ancestor -is [System.Management.Automation.Language.ScriptBlockExpressionAst]) { return $false }
      if ($ancestor -is [System.Management.Automation.Language.IfStatementAst]) {
        $commandClause = -1
        for ($clauseIndex = 0; $clauseIndex -lt $ancestor.Clauses.Count; $clauseIndex++) {
          $clause = $ancestor.Clauses[$clauseIndex]
          $body = $clause.Item2
          if ($Command.Extent.StartOffset -ge $body.Extent.StartOffset -and
              $Command.Extent.EndOffset -le $body.Extent.EndOffset) { $commandClause = $clauseIndex; break }
        }
        if ($commandClause -lt 0) { return $false }
        $conditionText = ($ancestor.Clauses[$commandClause].Item1.Extent.Text -replace '\s+', ' ').Trim()
        if (-not $allowed.Contains($conditionText)) { return $false }
        [void]$seen.Add($conditionText)
      }
      if ($ancestor -is [System.Management.Automation.Language.LoopStatementAst]) { return $false }
      $ancestor = $ancestor.Parent
    }
    foreach ($requiredCondition in $RequiredIfConditions) {
      if (-not $seen.Contains(($requiredCondition -replace '\s+', ' ').Trim())) { return $false }
    }
    return $true
  }

  function Get-LiveLiteralAssignmentValueBefore {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Root,
      [Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Target,
      [Parameter(Mandatory)][string]$VariableName
    )
    # Resolve the value that is actually in force at Target: only unconditional, statically live assignments
    # before the target participate, and the last such assignment wins just as PowerShell execution does.
    # A dead/conditional assignment is not execution evidence; a final non-literal assignment fails closed.
    $assignments = @($Root.FindAll({
      param($node)
      $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
      $node.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
      $node.Left.VariablePath.UserPath -ceq $VariableName -and
      $node.Extent.StartOffset -lt $Target.Extent.StartOffset -and
      (Test-AstCommandStaticallyLive -Command $node)
    }, $true) | Sort-Object { $_.Extent.StartOffset })
    if ($assignments.Count -eq 0) { return $null }
    $last = $assignments[-1]
    if ($last.Right -isnot [System.Management.Automation.Language.CommandExpressionAst] -or
        $last.Right.Expression -isnot [System.Management.Automation.Language.StringConstantExpressionAst]) { return $null }
    return $last.Right.Expression.Value
  }

  function Test-WrapperPathExpression {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.Ast]$Node,
      [Parameter(Mandatory)][string]$Pattern,
      [Parameter(Mandatory)][System.Collections.Generic.HashSet[string]]$NamingVariables
    )
    # 该表达式是否**静态可见地**指向一个 Gradle wrapper 文件：自身文本命中，或它是裸变量而该变量在本文件
    # 里的任一赋值右值命中。大小写敏感（`-cmatch`）：`Get-GradleWrapperDistributionState` 不该被误判。
    if ($Node.Extent.Text -cmatch $Pattern) { return $true }
    if ($Node -is [System.Management.Automation.Language.VariableExpressionAst]) {
      return $NamingVariables.Contains($Node.VariablePath.UserPath)
    }
    return $false
  }

  function Test-ScannerCheckInvocation {
    param([Parameter(Mandatory)][System.Management.Automation.Language.CommandAst]$Command)
    # 「是不是真的在调本脚本」= 命令名是 pwsh（`& pwsh` 同解）+ 有 -File + 其实参提到本脚本文件名。
    if (-not [string]::Equals($Command.GetCommandName(), 'pwsh', [System.StringComparison]::OrdinalIgnoreCase)) { return $false }
    $file = Get-BoundParameter -Command $Command -Name 'File'
    if ($null -eq $file -or $null -eq $file.Argument) { return $false }
    return ($file.Argument.Extent.Text -match 'license-scanner-check\.ps1')
  }

  function Resolve-ExecutedFilePath {
    param(
      [Parameter(Mandatory)][System.Management.Automation.Language.CommandAst]$Command,
      [Parameter(Mandatory)][hashtable]$Literals
    )
    # 返回这条 `pwsh … -File <expr>` 真正会执行的路径字面量：直接量取其值，变量查同块内的字面量赋值；
    # 都解不出就返回 $null（= 无法证明它执行了目标脚本，按 fail-closed 处理）。
    if (-not [string]::Equals($Command.GetCommandName(), 'pwsh', [System.StringComparison]::OrdinalIgnoreCase)) { return $null }
    $file = Get-BoundParameter -Command $Command -Name 'File'
    if ($null -eq $file -or $null -eq $file.Argument) { return $null }
    $argument = $file.Argument
    if ($argument -is [System.Management.Automation.Language.StringConstantExpressionAst]) { return $argument.Value }
    if ($argument -is [System.Management.Automation.Language.VariableExpressionAst]) {
      $name = $argument.VariablePath.UserPath
      if ($Literals.ContainsKey($name)) { return [string]$Literals[$name] }
    }
    return $null
  }

  function Get-WorkflowStepLines {
    param(
      [Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Lines,
      [Parameter(Mandatory)][int]$Start
    )
    # 一个 step 从它的 `- name:` 行起，到下一个**同级或更浅**的序列项前一行止。
    $stepLines = [System.Collections.Generic.List[string]]::new()
    if ($Start -lt 0 -or $Start -ge $Lines.Count) { return $stepLines.ToArray() }
    $indent = if ($Lines[$Start] -match '^(?<indent>[ \t]*)-[ \t]') { $Matches.indent.Length } else { 0 }
    $stepLines.Add($Lines[$Start])
    for ($i = $Start + 1; $i -lt $Lines.Count; $i++) {
      if ($Lines[$i] -match '^(?<indent>[ \t]*)-[ \t]' -and $Matches.indent.Length -le $indent) { break }
      $stepLines.Add($Lines[$i])
    }
    return $stepLines.ToArray()
  }

  function Test-YamlQuotedScalarClosed {
    param([Parameter(Mandatory)][string]$Text, [Parameter(Mandatory)][char]$Quote, [int]$Start = 0)
    for ($i = $Start; $i -lt $Text.Length; $i++) {
      if ($Text[$i] -ne $Quote) { continue }
      if ($Quote -eq [char]39 -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -eq [char]39) { $i++; continue }
      if ($Quote -eq [char]34) {
        $slashes = 0
        for ($j = $i - 1; $j -ge 0 -and $Text[$j] -eq [char]92; $j--) { $slashes++ }
        if (($slashes % 2) -eq 1) { continue }
      }
      return $true
    }
    return $false
  }

  function Get-WorkflowStructuralLineMask {
    param([Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Lines)
    $mask = [bool[]]::new($Lines.Count)
    for ($i = 0; $i -lt $mask.Count; $i++) { $mask[$i] = $true }
    $blockScalarIndent = -1
    $quotedScalar = [char]0
    for ($i = 0; $i -lt $Lines.Count; $i++) {
      $current = $Lines[$i]
      if ($quotedScalar -ne [char]0) {
        $mask[$i] = $false
        if (Test-YamlQuotedScalarClosed -Text $current -Quote $quotedScalar) { $quotedScalar = [char]0 }
        continue
      }
      if ($blockScalarIndent -ge 0) {
        if ([string]::IsNullOrWhiteSpace($current)) { $mask[$i] = $false; continue }
        $currentIndent = ([regex]::Match($current, '^[ ]*')).Value.Length
        if ($currentIndent -gt $blockScalarIndent) { $mask[$i] = $false; continue }
        $blockScalarIndent = -1
      }
      if ($current -match '^(?<indent>[ ]*)(?:-[ ]+)?[A-Za-z][A-Za-z0-9_-]*:[ \t]*[|>](?:[1-9][+-]?|[+-][1-9]?)?[ \t]*(?:#.*)?$') {
        $blockScalarIndent = $Matches.indent.Length
        continue
      }
      $valueMatch = [regex]::Match($current, '^[ ]*(?:-[ ]+)?[A-Za-z][A-Za-z0-9_-]*:[ \t]*(?<value>.*)$')
      if (-not $valueMatch.Success) { $valueMatch = [regex]::Match($current, '^[ ]*-[ ]+(?<value>.*)$') }
      if (-not $valueMatch.Success) { continue }
      $value = $valueMatch.Groups['value'].Value.TrimStart()
      if ($value.Length -eq 0 -or ($value[0] -ne [char]34 -and $value[0] -ne [char]39)) { continue }
      if (-not (Test-YamlQuotedScalarClosed -Text $value -Quote $value[0] -Start 1)) { $quotedScalar = $value[0] }
    }
    return $mask
  }

  function Get-WorkflowActiveStepRecords {
    param([Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Lines)
    $records = [System.Collections.Generic.List[object]]::new()
    $structural = Get-WorkflowStructuralLineMask -Lines $Lines
    $jobsHeaders = @(0..($Lines.Count - 1) | Where-Object { $structural[$_] -and $Lines[$_] -match '^jobs:[ \t]*$' })
    foreach ($jobsHeader in $jobsHeaders) {
      $jobsIndent = ([regex]::Match($Lines[$jobsHeader], '^[ ]*')).Value.Length
      $jobIndent = $jobsIndent + 2
      $jobStarts = @()
      for ($i = $jobsHeader + 1; $i -lt $Lines.Count; $i++) {
        if (-not $structural[$i] -or [string]::IsNullOrWhiteSpace($Lines[$i]) -or $Lines[$i] -match '^[ \t]*#') { continue }
        $indent = ([regex]::Match($Lines[$i], '^[ ]*')).Value.Length
        if ($indent -le $jobsIndent) { break }
        if ($indent -eq $jobIndent -and $Lines[$i] -match '^[ ]*(?<job>[A-Za-z0-9_-]+):[ \t]*(?:#.*)?$') {
          $jobStarts += [pscustomobject]@{ Index = $i; Name = $Matches.job }
        }
      }
      foreach ($job in $jobStarts) {
        $jobEnd = $Lines.Count
        for ($i = $job.Index + 1; $i -lt $Lines.Count; $i++) {
          if (-not $structural[$i] -or [string]::IsNullOrWhiteSpace($Lines[$i]) -or $Lines[$i] -match '^[ \t]*#') { continue }
          $indent = ([regex]::Match($Lines[$i], '^[ ]*')).Value.Length
          if ($indent -le $jobIndent) { $jobEnd = $i; break }
        }
        $fieldIndent = $jobIndent + 2
        $jobLive = $true
        $stepsStarts = @()
        for ($i = $job.Index + 1; $i -lt $jobEnd; $i++) {
          if (-not $structural[$i]) { continue }
          if ($Lines[$i] -match ('^' + (' ' * $fieldIndent) + 'if:[ \t]*')) {
            # The ordered contract currently needs no job-level condition. Treat every such condition as
            # unproven rather than trying to recognize an open-ended set of false-looking expressions.
            $jobLive = $false
          }
          if ($Lines[$i] -match ('^' + (' ' * $fieldIndent) + 'steps:[ \t]*$')) { $stepsStarts += $i }
        }
        if (-not $jobLive) { continue }
        foreach ($stepsStart in $stepsStarts) {
          $itemIndent = $fieldIndent + 2
          for ($i = $stepsStart + 1; $i -lt $jobEnd; $i++) {
            if (-not $structural[$i] -or [string]::IsNullOrWhiteSpace($Lines[$i]) -or $Lines[$i] -match '^[ \t]*#') { continue }
            $indent = ([regex]::Match($Lines[$i], '^[ ]*')).Value.Length
            if ($indent -le $fieldIndent) { break }
            if ($indent -eq $itemIndent -and $Lines[$i] -match '^[ ]*-[ \t]+name:') {
              $records.Add([pscustomobject]@{ Index = $i; Job = $job.Name })
            }
          }
        }
      }
    }
    return $records.ToArray()
  }

  function Get-WorkflowActiveStepIndices {
    param([Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Lines)
    return @((Get-WorkflowActiveStepRecords -Lines $Lines) | ForEach-Object Index)
  }

  function Get-WorkflowStepKeys {
    param([Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$StepLines)
    # 只收该 step 的**顶层**映射键：`- name:` 行上的那个，以及与它同列（破折号后两列）的后续键。
    # 更深缩进的行属于块标量或子映射，不是这一步的键。
    $keys = [ordered]@{}
    if ($StepLines.Count -eq 0) { return $keys }
    if ($StepLines[0] -notmatch '^(?<indent>[ \t]*)-[ \t]+(?<key>[A-Za-z][A-Za-z0-9_-]*):(?<value>.*)$') { return $keys }
    $keyColumn = $Matches.indent.Length + 2
    $keys[$Matches.key] = $Matches.value.Trim()
    for ($i = 1; $i -lt $StepLines.Count; $i++) {
      if ($StepLines[$i] -notmatch '^(?<indent>[ \t]*)(?<key>[A-Za-z][A-Za-z0-9_-]*):(?<value>.*)$') { continue }
      if ($Matches.indent.Length -ne $keyColumn) { continue }
      $keys[$Matches.key] = $Matches.value.Trim()
    }
    return $keys
  }

  function Get-WorkflowStepMappingValues {
    param(
      [Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$StepLines,
      [Parameter(Mandatory)][string]$MappingName,
      [Parameter(Mandatory)][string]$Name
    )
    $values = [System.Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt $StepLines.Count; $i++) {
      if ($StepLines[$i] -notmatch ('^(?<indent>[ \t]*)' + [regex]::Escape($MappingName) + ':[ \t]*$')) { continue }
      $mappingIndent = $Matches.indent.Length
      for ($j = $i + 1; $j -lt $StepLines.Count; $j++) {
        $line = $StepLines[$j]
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^(?<indent>[ \t]*)(?<key>[A-Za-z_][A-Za-z0-9_]*):(?<value>.*)$') { break }
        if ($Matches.indent.Length -le $mappingIndent) { break }
        if ($Matches.indent.Length -eq $mappingIndent + 2 -and $Matches.key -ceq $Name) {
          $values.Add($Matches.value.Trim().Trim("'`""))
        }
      }
    }
    return $values.ToArray()
  }

  function Get-WorkflowRunScript {
    param([Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]]$StepLines)
    # `run: |` 之后、缩进比 `run:` 深的连续行即块标量；不做去缩进（PowerShell 解析器不在乎前导空白，
    # 而去缩进会破坏块内的 here-string）。
    $runIndex = -1
    $runIndent = 0
    for ($i = 0; $i -lt $StepLines.Count; $i++) {
      if ($StepLines[$i] -match '^(?<indent>[ \t]*)run:[ \t]*\|[ \t]*$') { $runIndex = $i; $runIndent = $Matches.indent.Length; break }
    }
    if ($runIndex -lt 0) { return $null }
    $body = [System.Collections.Generic.List[string]]::new()
    for ($i = $runIndex + 1; $i -lt $StepLines.Count; $i++) {
      $line = $StepLines[$i]
      if ([string]::IsNullOrWhiteSpace($line)) { $body.Add(''); continue }
      $indent = ($line -replace '^(?<indent>[ \t]*).*$', '${indent}').Length
      if ($indent -le $runIndent) { break }
      $body.Add($line)
    }
    return ($body -join "`n")
  }

  function Move-WorkflowStepBefore {
    param(
      [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
      [Parameter(Mandatory)][string]$Step,
      [Parameter(Mandatory)][string]$Before
    )
    # 变异用：把整个 step 块搬到另一个 step 之前（只搬 `- name:` 一行会连带砍掉它的 run: 块，
    # 那样一枚变异会同时抬起 SEQUENCE 与 CI-SCANNER，证不了排序这一条）。
    $lines = @($Text -split "`n")
    $stepPattern = '^[ \t]*-[ \t]+name:[ \t]+' + [regex]::Escape($Step) + '[ \t]*$'
    $beforePattern = '^[ \t]*-[ \t]+name:[ \t]+' + [regex]::Escape($Before) + '[ \t]*$'
    $stepIndex = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match $stepPattern })
    if ($stepIndex.Count -ne 1) { return $Text }
    $block = @(Get-WorkflowStepLines -Lines $lines -Start $stepIndex[0])
    $remaining = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ($i -lt $stepIndex[0] -or $i -ge ($stepIndex[0] + $block.Count)) { $lines[$i] } })
    $target = @(0..($remaining.Count - 1) | Where-Object { $remaining[$_] -match $beforePattern })
    if ($target.Count -ne 1) { return $Text }
    $moved = @(for ($i = 0; $i -lt $remaining.Count; $i++) {
      if ($i -eq $target[0]) { $block }
      $remaining[$i]
    })
    return ($moved -join "`n")
  }

  function Replace-LicenseStepWithInertScalarFixture {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $lines = @($Text -split "`n")
    $licenseIndices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match '^      - name: License gate[ \t]*$' })
    $warmupIndices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match '^      - name: Gradle online build' })
    if ($licenseIndices.Count -ne 1 -or $warmupIndices.Count -ne 1) { return $Text }
    $licenseBlock = @(Get-WorkflowStepLines -Lines $lines -Start $licenseIndices[0])
    $remaining = @(for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($i -lt $licenseIndices[0] -or $i -ge ($licenseIndices[0] + $licenseBlock.Count)) { $lines[$i] }
    })
    $warmupRun = @(($warmupIndices[0] + 1)..($remaining.Count - 1) | Where-Object { $remaining[$_] -match '^        run: \|[ \t]*$' } | Select-Object -First 1)
    if ($warmupRun.Count -ne 1) { return $Text }
    $fake = @(
      '          steps:',
      '            - name: License gate',
      "              if: `${{ steps.docs_scope.outputs.docs_only != 'true' }}",
      '              shell: pwsh',
      '              run: |',
      "                `$f = 'scripts/check-licenses.ps1'",
      '                pwsh -NoProfile -File $f'
    )
    return (@(for ($i = 0; $i -lt $remaining.Count; $i++) {
      $remaining[$i]
      if ($i -eq $warmupRun[0]) { $fake }
    }) -join "`n")
  }

  function Replace-LicenseStepWithQuotedScalarFixture {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $lines = @($Text -split "`n")
    $licenseIndices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match '^      - name: License gate[ \t]*$' })
    if ($licenseIndices.Count -ne 1) { return $Text }
    $licenseBlock = @(Get-WorkflowStepLines -Lines $lines -Start $licenseIndices[0])
    $remaining = @(for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($i -lt $licenseIndices[0] -or $i -ge ($licenseIndices[0] + $licenseBlock.Count)) { $lines[$i] }
    })
    $e2eIndices = @(0..($remaining.Count - 1) | Where-Object { $remaining[$_] -match '^      - name: E2E verify gate[ \t]*$' })
    if ($e2eIndices.Count -ne 1) { return $Text }
    $bait = @(
      '      - name: Quoted scalar bait',
      '        shell: pwsh',
      '        env:',
      '          BAIT: "prefix',
      '            steps:',
      '              - name: License gate',
      "                if: `${{ steps.docs_scope.outputs.docs_only != 'true' }}",
      '                shell: pwsh',
      '                run: |',
      "                  `$f = 'scripts/check-licenses.ps1'",
      '                  pwsh -NoProfile -File $f',
      '            suffix"',
      '        run: Write-Host bait'
    )
    return (@(for ($i = 0; $i -lt $remaining.Count; $i++) {
      if ($i -eq $e2eIndices[0]) { $bait }
      $remaining[$i]
    }) -join "`n")
  }

  function Replace-OrderedStepsWithDisabledJobFixture {
    param(
      [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
      [string]$Condition = '${{ false }}'
    )
    $ordered = @('Setup Java (Temurin 17)', 'Setup Android SDK', 'Setup Gradle (dependency cache across CI runs)', "Gradle online build (warms cache for verify.ps1's --offline gate)", 'Provision license scanners (online cache warm-up)', 'License gate', 'E2E verify gate')
    $lines = @($Text -split "`n")
    $remove = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($step in $ordered) {
      $pattern = '^      - name: ' + [regex]::Escape($step) + '[ \t]*$'
      $indices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match $pattern })
      if ($indices.Count -ne 1) { return $Text }
      foreach ($offset in 0..((@(Get-WorkflowStepLines -Lines $lines -Start $indices[0])).Count - 1)) { [void]$remove.Add($indices[0] + $offset) }
    }
    $remaining = @(for ($i = 0; $i -lt $lines.Count; $i++) { if (-not $remove.Contains($i)) { $lines[$i] } })
    $fakeJob = @(
      '  inert_license_evidence:',
      "    if: $Condition",
      '    runs-on: windows-latest',
      '    steps:',
      '      - name: Setup Java (Temurin 17)',
      '        run: Write-Host inert',
      '      - name: Setup Android SDK',
      '        run: Write-Host inert',
      '      - name: Setup Gradle (dependency cache across CI runs)',
      '        run: Write-Host inert',
      "      - name: Gradle online build (warms cache for verify.ps1's --offline gate)",
      '        run: Write-Host inert',
      '      - name: Provision license scanners (online cache warm-up)',
      "        if: `${{ steps.docs_scope.outputs.docs_only != 'true' }}",
      '        shell: pwsh',
      '        run: Write-Host inert',
      '      - name: License gate',
      "        if: `${{ steps.docs_scope.outputs.docs_only != 'true' }}",
      '        shell: pwsh',
      '        run: |',
      "          `$f = 'scripts/check-licenses.ps1'",
      '          pwsh -NoProfile -File $f',
      '      - name: E2E verify gate',
      "        if: `${{ steps.docs_scope.outputs.docs_only != 'true' }}",
      '        shell: pwsh',
      '        run: Write-Host inert'
    )
    return (($remaining + $fakeJob) -join "`n")
  }

  function Move-E2eStepToSeparateJobFixture {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $lines = @($Text -split "`n")
    $indices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match '^      - name: E2E verify gate[ \t]*$' })
    if ($indices.Count -ne 1) { return $Text }
    $block = @(Get-WorkflowStepLines -Lines $lines -Start $indices[0])
    $remaining = @(for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($i -lt $indices[0] -or $i -ge ($indices[0] + $block.Count)) { $lines[$i] }
    })
    $newJob = @('  split_e2e:', '    runs-on: windows-latest', '    steps:') + $block
    return (($remaining + $newJob) -join "`n")
  }

  function Get-IntegrationWiringFailures {
    param(
      [Parameter(Mandatory)][AllowEmptyString()][string]$SelftestText,
      [Parameter(Mandatory)][AllowEmptyString()][string]$WorkflowText,
      [Parameter(Mandatory)][AllowEmptyString()][string]$PolicyText,
      [Parameter(Mandatory)][AllowEmptyString()][string]$ReleaseText
    )
    $found = [System.Collections.Generic.List[string]]::new()

    # --- selftest.ps1：判的是被执行的命令，不是提到它的文本 ---
    $selftestAst = [System.Management.Automation.Language.Parser]::ParseInput($SelftestText, [ref]$null, [ref]$null)
    $selftestCommands = @($selftestAst.FindAll({
      param($node) $node -is [System.Management.Automation.Language.CommandAst]
    }, $true))
    $scannerCommands = @($selftestCommands | Where-Object { Test-ScannerCheckInvocation -Command $_ })
    $integrationCalls = @($scannerCommands | Where-Object {
      $suite = Get-BoundParameter -Command $_ -Name 'Suite'
      $null -ne $suite -and $null -ne $suite.Argument -and (Get-AstLiteralValue -Node $suite.Argument) -ceq 'integration'
    })
    if ($integrationCalls.Count -ne 1) { $found.Add("[INTEGRATION-SELFTEST-WIRING] expected exactly one executed integration-suite invocation in selftest.ps1, found $($integrationCalls.Count)") }
    $coldCalls = @($integrationCalls | Where-Object { $null -ne (Get-BoundParameter -Command $_ -Name 'SkipRealScan') }).Count
    if ($coldCalls -ne 1) { $found.Add("[INTEGRATION-SELFTEST-COLD] expected the seeded integration invocation to pass -SkipRealScan, found $coldCalls") }

    # 「调用存在」不等于「调用会执行」：把那一行包进 `if ($false) { … }`、或埋进一个没人调的函数，
    # 上面两条断言照样绿而闸 17cc 已被静默停用。故再判一条**正面存活契约**：调用的祖先链只允许
    # 当前真实分片门 `$Shard -eq 'seeded'`；其它 if/loop、else、函数/脚本块定义一概不能充当执行证据。
    # 边界照实说：这只证明它没有被**静态**停用；「seeded 分片确实走到了它」这条证据不在本套件里——
    # 那是 seeded 自己那行 `17cc(scanner-integration) … OK`，它只有真跑过才会打印。
    $deadCalls = @($integrationCalls | Where-Object { -not (Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @("`$Shard -eq 'seeded'")) })
    if ($deadCalls.Count -ne 0) { $found.Add("[INTEGRATION-SELFTEST-LIVENESS] the integration invocation in selftest.ps1 is not under the exact seeded-shard ancestor contract, so its presence proves nothing about execution ($($deadCalls.Count) such call(s))") }

    # A15 的正面契约：selftest.ps1 里**不存在写文件动作、其目标路径静态可见地指向 gradlew / gradlew.bat**。
    # 旧断言是变量名黑名单（「不得存在名为 scannerFixtureRoot 的变量」）——改个名字整棵 1400 行 fixture 就能
    # 原样回来。这一版判写动作本身，覆盖三种**写入原语**（原语不可改名，变量名可以）：
    #   ① 写文件 cmdlet（含内置别名）绑定的路径参数，以及第一个未被参数消费的位置实参；
    #   ② `[System.IO.File]` / `[System.IO.Directory]` / `[System.IO.Compression.ZipFile]` 上的写方法调用——
    #      它是 InvokeMemberExpressionAst，**根本不是 CommandAst**，`GetCommandName()` 永远看不到它，而本文件
    #      自己就用这个形态写夹具（Write-PolicyPom / process fixture）；
    #   ③ 文件重定向 `>` / `>>` 的目标。
    # 「静态可见地指向」= 该实参自身的文本大小写敏感地命中 `gradlew`/`gradlew.bat`（故 `Get-GradleWrapper…`、
    # `$gradleWrapperOfflineOutput` 这类标识符不误伤），**或**该实参是裸变量、而它在本文件内的任一赋值右值
    # 命中同一模式——后者堵掉「先把路径赋给变量再写」这条绕过。闸 17dd 的
    # `Set-Content -LiteralPath $vfScratch` 正是裸变量形态，但 `$vfScratch` 的赋值解出来是
    # `…GetTempPath() + "st17dd-verify-…ps1"`，不含 gradlew，故它**按内容**被排除，而不是按变量名被豁免。
    # 契约边界（不夸大）：这三条只覆盖**静态可见**的目标；把路径拼出来（`'gradle' + 'w.bat'`）或从外部数据
    # 读进来仍能规避——那是任何静态分析的共同下界，与「改个变量名即整份夹具复活」不是一个量级。
    $wrapperPathPattern = 'gradlew(\.bat)?(?![A-Za-z0-9_.-])'
    $wrapperNamingVariables = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($assignment in $selftestAst.FindAll({
      param($node) $node -is [System.Management.Automation.Language.AssignmentStatementAst]
    }, $true)) {
      if ($assignment.Left -isnot [System.Management.Automation.Language.VariableExpressionAst]) { continue }
      if ($assignment.Right.Extent.Text -cnotmatch $wrapperPathPattern) { continue }
      # 只认**构造路径**的赋值：字符串字面量、拼接、Join-Path。`$output = & cmd /c android\gradlew.bat …`
      # 那种「把命令输出接下来」的赋值不是路径——把它算进来会污染 `$output` 这个在 selftest 里到处都是的
      # 名字，之后任何 `Set-Content -Path $output` 都成假红。故：右值里除 Join-Path 外不得有任何命令调用。
      $pathBuildingCommands = @($assignment.Right.FindAll({
        param($node) $node -is [System.Management.Automation.Language.CommandAst]
      }, $true) | Where-Object { $_.GetCommandName() -ne 'Join-Path' })
      if ($pathBuildingCommands.Count -ne 0) { continue }
      [void]$wrapperNamingVariables.Add($assignment.Left.VariablePath.UserPath)
    }
    $fixtureWriteTargets = [System.Collections.Generic.List[string]]::new()
    $writerCommandNames = @(
      'Set-Content', 'sc', 'Add-Content', 'ac', 'New-Item', 'ni', 'Out-File',
      'Copy-Item', 'copy', 'cp', 'cpi', 'Move-Item', 'move', 'mv', 'mi',
      'Rename-Item', 'ren', 'rni', 'Tee-Object', 'tee'
    )
    foreach ($command in $selftestCommands) {
      $commandName = $command.GetCommandName()
      if ($null -eq $commandName -or $writerCommandNames -notcontains $commandName) { continue }
      foreach ($target in (Get-CommandPathArguments -Command $command -PathParameters @('LiteralPath', 'Path', 'Destination', 'FilePath', 'NewName'))) {
        if (Test-WrapperPathExpression -Node $target -Pattern $wrapperPathPattern -NamingVariables $wrapperNamingVariables) {
          $fixtureWriteTargets.Add("$commandName -> $($target.Extent.Text)")
        }
      }
    }
    foreach ($invocation in $selftestAst.FindAll({
      param($node) $node -is [System.Management.Automation.Language.InvokeMemberExpressionAst]
    }, $true)) {
      if ($null -eq $invocation.Arguments) { continue }
      if ($invocation.Expression.Extent.Text -notmatch 'IO\.(File|Directory|Compression\.ZipFile)\]$') { continue }
      if ($invocation.Member.Extent.Text -notmatch '^(WriteAll|AppendAll|Create|Copy|Move|Replace|Extract|Open)') { continue }
      foreach ($argument in @($invocation.Arguments)) {
        if (Test-WrapperPathExpression -Node $argument -Pattern $wrapperPathPattern -NamingVariables $wrapperNamingVariables) {
          $fixtureWriteTargets.Add("$($invocation.Expression.Extent.Text)::$($invocation.Member.Extent.Text) -> $($argument.Extent.Text)")
        }
      }
    }
    foreach ($redirection in $selftestAst.FindAll({
      param($node) $node -is [System.Management.Automation.Language.FileRedirectionAst]
    }, $true)) {
      if (Test-WrapperPathExpression -Node $redirection.Location -Pattern $wrapperPathPattern -NamingVariables $wrapperNamingVariables) {
        $fixtureWriteTargets.Add("redirection -> $($redirection.Location.Extent.Text)")
      }
    }
    if ($fixtureWriteTargets.Count -ne 0) { $found.Add("[INTEGRATION-SELFTEST-INLINE] selftest.ps1 still constructs a Gradle wrapper fixture in-line ($($fixtureWriteTargets.Count) write(s) whose target path names gradlew/gradlew.bat: $($fixtureWriteTargets -join '; '))") }

    # --- ci.yml：步骤只在它是一个活的序列项时才算数 ---
    $workflowLines = @($WorkflowText -split '\r?\n')
    $orderedSteps = @(
      'Setup Java (Temurin 17)',
      'Setup Android SDK',
      'Setup Gradle (dependency cache across CI runs)',
      "Gradle online build (warms cache for verify.ps1's --offline gate)",
      'Provision license scanners (online cache warm-up)',
      'License gate',
      'E2E verify gate'
    )
    $stepLine = @{}
    $stepJob = @{}
    $activeStepRecords = @(Get-WorkflowActiveStepRecords -Lines $workflowLines)
    $uvSetupHits = @($activeStepRecords | Where-Object { $workflowLines[$_.Index] -match '^[ ]*-[ \t]+name:[ \t]+Install uv[ \t]*$' })
    if ($uvSetupHits.Count -ne 1) {
      $found.Add("[INTEGRATION-CI-ACTIVE] expected exactly one active Install uv step, found $($uvSetupHits.Count)")
    } else {
      $uvSetupLines = @(Get-WorkflowStepLines -Lines $workflowLines -Start $uvSetupHits[0].Index)
      $uvSetupVersions = @(Get-WorkflowStepMappingValues -StepLines $uvSetupLines -MappingName 'with' -Name 'version')
      if ($uvSetupVersions.Count -ne 1 -or $uvSetupVersions[0] -cne $expectedUvVersion) {
        $found.Add("[INTEGRATION-CI-ACTIVE] Install uv must pin setup-uv to exact uv version '$expectedUvVersion'")
      }
    }
    foreach ($step in $orderedSteps) {
      $pattern = '^[ ]*-[ \t]+name:[ \t]+' + [regex]::Escape($step) + '[ \t]*$'
      $hits = @($activeStepRecords | Where-Object { $workflowLines[$_.Index] -match $pattern })
      if ($hits.Count -ne 1) { $found.Add("[INTEGRATION-CI-ORDER-COUNT] expected exactly one active '- name: $step' step in ci.yml, found $($hits.Count)"); continue }
      $stepLine[$step] = $hits[0].Index
      $stepJob[$step] = $hits[0].Job
    }
    # 计数与排序拆成两个码：两枚既有变异都只是删/注释掉 `- name: License gate`，两枚都落在计数这一支，
    # 于是卡片的头号 dod_assert（scanner 排在 JDK/Android/Gradle setup 与在线预热之后）此前一枚变异都没有。
    if ($stepLine.Count -eq $orderedSteps.Count) {
      $orderedJobs = @($orderedSteps | ForEach-Object { $stepJob[$_] } | Sort-Object -Unique)
      if ($orderedJobs.Count -ne 1) {
        $found.Add("[INTEGRATION-CI-ORDER-SEQUENCE] ordered setup/license/E2E steps span jobs {$($orderedJobs -join ',')}; all must be direct steps of one live job")
      } else {
        $previous = -1
        foreach ($step in $orderedSteps) {
          if ($stepLine[$step] -le $previous) { $found.Add("[INTEGRATION-CI-ORDER-SEQUENCE] step '$step' is out of order in ci.yml") }
          $previous = $stepLine[$step]
        }
      }
    }

    if ($stepLine.ContainsKey('License gate')) {
      $gateLines = @(Get-WorkflowStepLines -Lines $workflowLines -Start $stepLine['License gate'])
      $gateKeys = Get-WorkflowStepKeys -StepLines $gateLines
      $gateUvCaches = @(Get-WorkflowStepMappingValues -StepLines $gateLines -MappingName 'env' -Name 'UV_CACHE_DIR')
      # 键集**不**逐字逐序钉死：那样一个无害的 `env:` / `working-directory:`、甚至把 `shell:` 写到 `if:`
      # 上面（YAML 语义完全相同），都会让 seeded 以一条读起来像「检测到破坏」的消息变红。这里改成
      # 「必需键齐 + 多出来的键只能来自良性白名单」，并把这一段真正依赖的东西钉住：
      #   · `shell` 的**值**必须是 pwsh——下面整段 `run:` 块分析都按 PowerShell 解析它，此前只检查了键
      #     存在、没检查值，`shell: bash` 能一路绿到底；
      #   · `if` 仍逐字钉死（见上方 $expectedGateCondition 的理由）。
      # `continue-on-error` 无论取什么值都不在良性集里：写成 `false` 只是默认值的复述，却给「下一次悄悄
      # 改成 true」提供了一个不显眼的落脚点。
      $requiredGateKeys = @('name', 'if', 'shell', 'run')
      $benignGateKeys = @('env', 'working-directory', 'timeout-minutes')
      $actualGateKeys = @($gateKeys.Keys)
      $missingGateKeys = @($requiredGateKeys | Where-Object { $actualGateKeys -cnotcontains $_ })
      $unexpectedGateKeys = @($actualGateKeys | Where-Object { $requiredGateKeys -cnotcontains $_ -and $benignGateKeys -cnotcontains $_ })
      if ($missingGateKeys.Count -ne 0) {
        $found.Add("[INTEGRATION-CI-ACTIVE] License gate is missing required top-level key(s) {$($missingGateKeys -join ',')}; keys are {$($actualGateKeys -join ',')}")
      } elseif ($unexpectedGateKeys.Count -ne 0) {
        $found.Add("[INTEGRATION-CI-ACTIVE] License gate carries top-level key(s) {$($unexpectedGateKeys -join ',')} that are neither required {$($requiredGateKeys -join ',')} nor benign {$($benignGateKeys -join ',')}")
      } elseif ($gateKeys['shell'] -cne 'pwsh') {
        $found.Add("[INTEGRATION-CI-ACTIVE] License gate declares shell '$($gateKeys['shell'])', but the run-block analysis below parses it as PowerShell, so it must be exactly 'pwsh'")
      } elseif ($gateKeys['if'] -cne $expectedGateCondition) {
        $found.Add("[INTEGRATION-CI-ACTIVE] License gate runs under condition '$($gateKeys['if'])', expected exactly '$expectedGateCondition'")
      }

      # 必须**执行**扫描器，而且执行的就是那个路径。旧谓词只是「同一行里既有 pwsh 又有
      # check-licenses.ps1」，`pwsh` 来自调用、`check-licenses.ps1` 来自 Write-Error 的消息串。
      $runScript = Get-WorkflowRunScript -StepLines $gateLines
      if ([string]::IsNullOrWhiteSpace($runScript)) {
        $found.Add('[INTEGRATION-CI-SCANNER] License gate has no run: block to execute the scanner from')
      } elseif ($gateUvCaches.Count -ne 1 -or $gateUvCaches[0] -cne $expectedUvCache) {
        $found.Add("[INTEGRATION-CI-SCANNER] License gate must reuse the isolated exact-version uv cache '$expectedUvCache' warmed by the provisioning step")
      } else {
        $runAst = [System.Management.Automation.Language.Parser]::ParseInput($runScript, [ref]$null, [ref]$null)
        # Bind every prerequisite to the live scanner command. Whole-block presence is insufficient: a correct
        # assignment after the command, or inside `if ($false)`, never affects that invocation.
        $liveCommands = @($runAst.FindAll({
          param($node) $node -is [System.Management.Automation.Language.CommandAst]
        }, $true) | Where-Object { Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @('Test-Path $f') })
        $executedPaths = [System.Collections.Generic.List[string]]::new()
        $scannerRuns = [System.Collections.Generic.List[object]]::new()
        foreach ($command in $liveCommands) {
          $fValue = Get-LiveLiteralAssignmentValueBefore -Root $runAst -Target $command -VariableName 'f'
          $literals = @{}
          if ($null -ne $fValue) { $literals['f'] = $fValue }
          $resolvedPath = Resolve-ExecutedFilePath -Command $command -Literals $literals
          if ($null -ne $resolvedPath) { $executedPaths.Add($resolvedPath) }
          if ($resolvedPath -ceq $expectedGateScript) { $scannerRuns.Add($command) }
        }
        if ($scannerRuns.Count -lt 1) {
          $found.Add("[INTEGRATION-CI-SCANNER] License gate never executes $expectedGateScript (resolved pwsh -File targets: $(if ($executedPaths.Count -eq 0) { '<none>' } else { $executedPaths -join ', ' }))")
        } elseif (@($scannerRuns | Where-Object {
          (Get-LiveLiteralAssignmentValueBefore -Root $runAst -Target $_ -VariableName 'env:UV_OFFLINE') -ceq '1' -and
          (Get-LiveLiteralAssignmentValueBefore -Root $runAst -Target $_ -VariableName 'env:npm_config_offline') -ceq 'true'
        }).Count -ne $scannerRuns.Count) {
          $found.Add('[INTEGRATION-CI-SCANNER] License gate must set UV_OFFLINE=1 and npm_config_offline=true before the scanner so uv/npx cannot make network requests')
        } elseif (@($scannerRuns | Where-Object {
          $strict = Get-BoundParameter -Command $_ -Name 'Strict'
          $null -eq $strict -or ($null -ne $strict.Argument -and $strict.Argument.Extent.Text -cne '$true')
        }).Count -ne 0) {
          $found.Add('[INTEGRATION-CI-SCANNER] License gate must execute check-licenses.ps1 with the enabled -Strict switch so every coverage gap fails closed')
        }
      }
    }
    $provisionStep = 'Provision license scanners (online cache warm-up)'
    if ($stepLine.ContainsKey($provisionStep)) {
      $provisionLines = @(Get-WorkflowStepLines -Lines $workflowLines -Start $stepLine[$provisionStep])
      $provisionKeys = Get-WorkflowStepKeys -StepLines $provisionLines
      $provisionUvCaches = @(Get-WorkflowStepMappingValues -StepLines $provisionLines -MappingName 'env' -Name 'UV_CACHE_DIR')
      $provisionRun = Get-WorkflowRunScript -StepLines $provisionLines
      $provisionTokens = $null
      $provisionErrors = $null
      $provisionAst = [System.Management.Automation.Language.Parser]::ParseInput($provisionRun, [ref]$provisionTokens, [ref]$provisionErrors)
      $provisionCommands = @($provisionAst.FindAll({
        param($node) $node -is [System.Management.Automation.Language.CommandAst]
      }, $true))
      $uvProvisionRuns = @($provisionCommands | Where-Object {
        ($_.Extent.Text -replace '\s+', ' ').Trim() -ceq 'uv run --with pip-licenses==5.5.5 pip-licenses --version' -and
        (Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @('Test-Path pyproject.toml') -RequiredIfConditions @('Test-Path pyproject.toml'))
      }).Count
      $npmDependencyRuns = @($provisionCommands | Where-Object {
        ($_.Extent.Text -replace '\s+', ' ').Trim() -ceq 'npm ci --prefix frontend --ignore-scripts --no-audit --no-fund' -and
        (Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @('Test-Path frontend/package.json', 'Test-Path frontend/package-lock.json') -RequiredIfConditions @('Test-Path frontend/package.json', 'Test-Path frontend/package-lock.json'))
      })
      $npmProvisionRuns = @($provisionCommands | Where-Object {
        ($_.Extent.Text -replace '\s+', ' ').Trim() -ceq 'npm install --no-save --package-lock=false --ignore-scripts license-checker@25.0.1' -and
        (Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @('Test-Path frontend/package.json', 'Test-Path frontend/package-lock.json') -RequiredIfConditions @('Test-Path frontend/package.json', 'Test-Path frontend/package-lock.json'))
      })
      $handoffProvisionRuns = @($provisionCommands | Where-Object {
        ($_.Extent.Text -replace '\s+', ' ').Trim() -ceq 'pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite provision-handoff' -and
        (Test-AstCommandStaticallyLive -Command $_ -AllowedIfConditions @('Test-Path pyproject.toml') -RequiredIfConditions @('Test-Path pyproject.toml'))
      }).Count
      if (-not $provisionKeys.Contains('if') -or $provisionKeys['if'] -cne $expectedGateCondition -or
          -not $provisionKeys.Contains('shell') -or $provisionKeys['shell'] -cne 'pwsh' -or
          $provisionUvCaches.Count -ne 1 -or $provisionUvCaches[0] -cne $expectedUvCache -or
          $provisionErrors.Count -ne 0 -or $uvProvisionRuns -ne 1 -or $npmDependencyRuns.Count -ne 1 -or
          $npmProvisionRuns.Count -ne 1 -or $npmDependencyRuns[0].Extent.EndOffset -ge $npmProvisionRuns[0].Extent.StartOffset -or
          $handoffProvisionRuns -ne 1) {
        $found.Add('[INTEGRATION-CI-ACTIVE] online provisioning must install the locked frontend dependency tree before the exact scanner pin, execute the real uv handoff suite under manifest guards, warm the isolated versioned uv cache, and share the docs-only condition')
      }
    }
    if ($stepLine.ContainsKey('E2E verify gate')) {
      $siblingKeys = Get-WorkflowStepKeys -StepLines @(Get-WorkflowStepLines -Lines $workflowLines -Start $stepLine['E2E verify gate'])
      if (-not $siblingKeys.Contains('if') -or $siblingKeys['if'] -cne $expectedGateCondition) {
        $found.Add("[INTEGRATION-CI-ACTIVE] sibling gate 'E2E verify gate' no longer carries the pinned condition '$expectedGateCondition' — the pin above is no longer the sibling's expression")
      }
    }

    # --- docs：命令必须落在**具体那一条**上，不是文件里随便哪一行 ---
    $integrationCommand = 'license-scanner-check.ps1 -Suite integration'
    # RELEASE-CHECKLIST：锚到闸 17ee 逐字节钉死的那条哨兵项本身（17ee 管「单一解锁路径」的措辞，
    # 这里管「审计命令写在同一条上」；不重复 17ee 的哈希，只要求二者指的是同一行）。
    $releaseVisible = @([regex]::Replace($ReleaseText, '(?s)<!--.*?-->', '') -split '\r?\n')
    $releaseSentinelLines = @($releaseVisible | Where-Object { $_.Contains('[GRADLE-LIC-SCANNER-ONLY]') })
    if ($releaseSentinelLines.Count -ne 1) {
      $found.Add("[INTEGRATION-RELEASE-DOC] docs/RELEASE-CHECKLIST.md 的可见 [GRADLE-LIC-SCANNER-ONLY] 项应恰好 1 条，实得 $($releaseSentinelLines.Count)")
    } elseif (-not $releaseSentinelLines[0].Contains($integrationCommand)) {
      $found.Add("[INTEGRATION-RELEASE-DOC] docs/RELEASE-CHECKLIST.md 的 [GRADLE-LIC-SCANNER-ONLY] 项没有写出 $integrationCommand")
    }
    # LICENSE-POLICY：锚到 §5「核验流程」这一节内（`^##\s` 不会匹配 `### 5.1`，故 5.1 仍属本节）。
    $policyVisible = @([regex]::Replace($PolicyText, '(?s)<!--.*?-->', '') -split '\r?\n')
    $policySectionStarts = @(0..($policyVisible.Count - 1) | Where-Object { $policyVisible[$_] -match '^##\s+5\.\s' })
    if ($policySectionStarts.Count -ne 1) {
      $found.Add("[INTEGRATION-POLICY-DOC] docs/LICENSE-POLICY.md 没有唯一的「## 5.」核验流程小节（实得 $($policySectionStarts.Count) 处）")
    } else {
      $sectionEnd = $policyVisible.Count
      for ($i = $policySectionStarts[0] + 1; $i -lt $policyVisible.Count; $i++) {
        if ($policyVisible[$i] -match '^##\s') { $sectionEnd = $i; break }
      }
      $sectionLines = @($policyVisible[$policySectionStarts[0]..($sectionEnd - 1)])
      if (@($sectionLines | Where-Object { $_.Contains($integrationCommand) }).Count -lt 1) {
        $found.Add("[INTEGRATION-POLICY-DOC] docs/LICENSE-POLICY.md 的「## 5. 核验流程」小节没有写出 $integrationCommand")
      }
    }

    return $found.ToArray()
  }

  foreach ($wiringFailure in (Get-IntegrationWiringFailures -SelftestText $selftestText -WorkflowText $workflowText -PolicyText $policyText -ReleaseText $releaseText)) {
    Assert-Integration $false $wiringFailure
  }

  # 上面 Get-IntegrationWiringFailures 会抬起的**每一个**失败码，都必须出现在下面某一枚变异**声明的期望
  # 集合**里；分类器要求实得集合与声明集合**逐字相等**。
  # 此前这句话是注释里的一个**数字**（「九个码」）——随手加一个抬升点它就悄悄变假，而 R3 只读 diff，
  # 看不出注释与代码已经对不上（A16 说的正是这类承重陈述）。故改成机检（见下方
  # [INTEGRATION-WIRING-CODE-COVERAGE]）：码集从 Get-IntegrationWiringFailures 的**源码本身**抽出来，
  # 与变异声明集合求**相等**而非包含——新增抬升点却没配变异会红；变异声明了一个没有任何抬升点能产生的码
  # （打错字、被重构掉）也会红；抽取本身失效（正则漂了 ⇒ 左边为空）同样红，不会静默变成真空绿。
  # 上一版的分类器只要求「期望码出现 ≥1 次」，于是 -SELFTEST-COLD 从来不是任何一枚变异的被测对象
  # （两枚 selftest 变异顺带把它抬起来而已）——删掉那两行断言后，八枚变异照样全绿（实测）。
  # 注意 WIRING 与 COLD 天然成对：调用整条没了，`-SkipRealScan` 自然也没了，所以那三枚变异声明的是
  # 两码的集合；COLD 另有一枚**只摘 -SkipRealScan 这一个 token** 的变异，其声明集合恰好只含 COLD。
  $wiringMutations = @(
    @{ Id = 'selftest-call-commented'; Codes = @('INTEGRATION-SELFTEST-WIRING', 'INTEGRATION-SELFTEST-COLD'); Target = 'Selftest'; Kind = 'comment'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    @{ Id = 'selftest-call-deleted'; Codes = @('INTEGRATION-SELFTEST-WIRING', 'INTEGRATION-SELFTEST-COLD'); Target = 'Selftest'; Kind = 'delete'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    # R3 第 2 轮的那条 finding 的直接回归：把调用换成一句**提到**它的 Write-Host。AST 版断言在这枚
    # 变异下曾照样绿（Extent.Text 含字符串字面量），本枚就是它的红证据。
    @{ Id = 'selftest-call-quoted-mention'; Codes = @('INTEGRATION-SELFTEST-WIRING', 'INTEGRATION-SELFTEST-COLD'); Target = 'Selftest'; Kind = 'replace-line'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$'; Text = 'Write-Host "run: scripts/license-scanner-check.ps1 -Suite integration -SkipRealScan (disabled for now)"' },
    # -SELFTEST-COLD 自己的变异：只摘掉 -SkipRealScan 这一个 token，调用其余部分原样保留。
    @{ Id = 'selftest-skiprealscan-removed'; Codes = @('INTEGRATION-SELFTEST-COLD'); Target = 'Selftest'; Kind = 'strip-token'; Pattern = '(?m)^(?<head>.*license-scanner-check\.ps1.*-Suite[ \t]+integration)[ \t]+-SkipRealScan(?<tail>.*)$' },
    # -SELFTEST-LIVENESS 自己的变异：调用整条原样保留（WIRING/COLD 仍绿），只把它包进恒假的 if——
    # 这正是「闸还在、但已经不会执行」的形态。
    @{ Id = 'selftest-call-dead-guard'; Codes = @('INTEGRATION-SELFTEST-LIVENESS'); Target = 'Selftest'; Kind = 'wrap-false'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    @{ Id = 'selftest-call-constant-false'; Codes = @('INTEGRATION-SELFTEST-LIVENESS'); Target = 'Selftest'; Kind = 'wrap-constant-false'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    @{ Id = 'selftest-call-uncalled-function'; Codes = @('INTEGRATION-SELFTEST-LIVENESS'); Target = 'Selftest'; Kind = 'wrap-function'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    @{ Id = 'selftest-call-dead-else'; Codes = @('INTEGRATION-SELFTEST-LIVENESS'); Target = 'Selftest'; Kind = 'wrap-else'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    @{ Id = 'selftest-call-dead-loop'; Codes = @('INTEGRATION-SELFTEST-LIVENESS'); Target = 'Selftest'; Kind = 'wrap-while-false'; Pattern = '(?m)^.*license-scanner-check\.ps1.*-Suite[ \t]+integration.*$' },
    # INLINE 覆盖的每条路径各一枚变异：具名路径参数（下一枚）· 位置实参（不写 -LiteralPath/-Path）·
    # .NET 静态写方法（根本不是 CommandAst）· 把路径先赋给变量再写（extent 只剩变量名）· 文件重定向。
    # 五枚共同证明这条契约覆盖的是**写入原语**，而不是某个变量名。
    @{ Id = 'selftest-inline-fixture-restored'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`nSet-Content -LiteralPath (Join-Path `$restoredFixtureRoot 'android/gradlew.bat') -Encoding utf8 -Value 'fixture'`n" },
    @{ Id = 'selftest-inline-fixture-positional-path'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`nSet-Content (Join-Path `$restoredFixtureRoot 'android/gradlew.bat') -Encoding utf8 -Value 'fixture'`n" },
    @{ Id = 'selftest-inline-fixture-dotnet-write'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`n[System.IO.File]::WriteAllText((Join-Path `$restoredFixtureRoot 'android/gradlew.bat'), 'fixture', [System.Text.UTF8Encoding]::new(`$false))`n" },
    @{ Id = 'selftest-inline-fixture-hoisted-path'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`n`$restoredWrapperPath = Join-Path `$restoredFixtureRoot 'android/gradlew.bat'`nSet-Content -LiteralPath `$restoredWrapperPath -Encoding utf8 -Value 'fixture'`n" },
    @{ Id = 'selftest-inline-fixture-redirection'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`n'fixture' > 'android/gradlew.bat'`n" },
    @{ Id = 'selftest-inline-switch-before-positional'; Codes = @('INTEGRATION-SELFTEST-INLINE'); Target = 'Selftest'; Kind = 'append'; Text = "`nSet-Content -NoNewline (Join-Path `$RepoRoot 'android/gradlew.bat') -Value 'fixture'`n" },
    @{ Id = 'ci-step-commented'; Codes = @('INTEGRATION-CI-ACTIVE', 'INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'comment'; Pattern = '(?m)^[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*$' },
    @{ Id = 'ci-step-deleted'; Codes = @('INTEGRATION-CI-ACTIVE', 'INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'delete'; Pattern = '(?m)^[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*$' },
    @{ Id = 'ci-provision-step-deleted'; Codes = @('INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'delete'; Pattern = '(?m)^[ \t]*-[ \t]+name:[ \t]+Provision license scanners \(online cache warm-up\)[ \t]*$' },
    # The same text nested under the preceding run scalar is inert YAML. Whole-file regex counting used to accept
    # this after the real direct-child name was indented away.
    @{ Id = 'ci-step-name-inert-block-scalar'; Codes = @('INTEGRATION-CI-ACTIVE', 'INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*$'; Text = '          - name: License gate' },
    # 删除真实整步，并把完整可执行形状伪装进另一步的 `run: |` 标量；只忽略单独的 `- name:` 诱饵不够。
    @{ Id = 'ci-full-step-inert-block-scalar'; Codes = @('INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'replace-license-with-inert-scalar' },
    @{ Id = 'ci-full-step-inert-quoted-scalar'; Codes = @('INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'replace-license-with-quoted-scalar' },
    @{ Id = 'ci-ordered-steps-disabled-job'; Codes = @('INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'replace-ordered-with-disabled-job' },
    @{ Id = 'ci-ordered-steps-expression-disabled-job'; Codes = @('INTEGRATION-CI-ORDER-COUNT'); Target = 'Workflow'; Kind = 'replace-ordered-with-expression-disabled-job' },
    @{ Id = 'ci-e2e-split-live-job'; Codes = @('INTEGRATION-CI-ORDER-SEQUENCE'); Target = 'Workflow'; Kind = 'move-e2e-to-separate-job' },
    # 卡片头号 dod_assert 的专属变异：整块 License gate 搬到 JDK setup 之前。真实回归形态——
    # 新跑者上缓存是冷的，GRADLE-CACHE-OFFLINE 触发，每个 PR 都红而没有任何东西指向病因。
    @{ Id = 'ci-step-reordered'; Codes = @('INTEGRATION-CI-ORDER-SEQUENCE'); Target = 'Workflow'; Kind = 'reorder'; Step = 'License gate'; Before = 'Setup Java (Temurin 17)' },
    @{ Id = 'ci-scanner-exec-deleted'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'delete'; Pattern = '(?m)^.*pwsh.*check-licenses\.ps1.*$' },
    # 把闸换去跑别的脚本：`pwsh` 与 `check-licenses.ps1` 仍同行（后者来自 Write-Error 的消息串），
    # 旧谓词实测保持绿。
    @{ Id = 'ci-scanner-path-swapped'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*)\$f = ''scripts/check-licenses\.ps1''[ \t]*$'; Text = "`$f = 'scripts/check-cards.ps1'" },
    # 把整句调用注释到行尾：`^\s*#` 只排除整行注释，旧谓词实测保持绿。
    @{ Id = 'ci-scanner-exec-commented-out'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*)if \(Test-Path \$f\).*check-licenses\.ps1.*$'; Text = "Write-Host 'gate disabled'  # pwsh -NoProfile -File scripts/check-licenses.ps1" },
    @{ Id = 'ci-scanner-exec-dead-guard'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>if \(Test-Path \$f\).*check-licenses\.ps1.*)$' },
    @{ Id = 'ci-scanner-exec-constant-false'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-constant-false-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>if \(Test-Path \$f\).*check-licenses\.ps1.*)$' },
    @{ Id = 'ci-scanner-exec-uncalled-function'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-function-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>if \(Test-Path \$f\).*check-licenses\.ps1.*)$' },
    @{ Id = 'ci-scanner-exec-dead-else'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-else-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>if \(Test-Path \$f\).*check-licenses\.ps1.*)$' },
    @{ Id = 'ci-scanner-exec-dead-loop'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-while-false-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>if \(Test-Path \$f\).*check-licenses\.ps1.*)$' },
    @{ Id = 'ci-scanner-path-dead-guard'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = "(?m)^(?<keep>[ \t]*)(?<body>\`$f = 'scripts/check-licenses\.ps1'[ \t]*)$" },
    @{ Id = 'ci-scanner-path-moved-after'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'move-after-line'; Pattern = "(?m)^[ \t]*\`$f = 'scripts/check-licenses\.ps1'[ \t]*\r?$"; AnchorPattern = '(?m)^[ \t]*if \(Test-Path \$f\).*check-licenses\.ps1.*\r?$' },
    @{ Id = 'ci-scanner-uv-offline-removed'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'delete'; Pattern = "(?m)^[ \t]*\`$env:UV_OFFLINE[ \t]*=[ \t]*'1'[ \t]*\r?`n" },
    @{ Id = 'ci-scanner-npm-offline-removed'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'delete'; Pattern = "(?m)^[ \t]*\`$env:npm_config_offline[ \t]*=[ \t]*'true'[ \t]*\r?`n" },
    @{ Id = 'ci-scanner-uv-offline-dead-guard'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = "(?m)^(?<keep>[ \t]*)(?<body>\`$env:UV_OFFLINE[ \t]*=[ \t]*'1'[ \t]*)$" },
    @{ Id = 'ci-scanner-uv-offline-moved-after'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'move-after-line'; Pattern = "(?m)^[ \t]*\`$env:UV_OFFLINE[ \t]*=[ \t]*'1'[ \t]*\r?$"; AnchorPattern = '(?m)^[ \t]*if \(Test-Path \$f\).*check-licenses\.ps1.*\r?$' },
    @{ Id = 'ci-scanner-npm-offline-dead-guard'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = "(?m)^(?<keep>[ \t]*)(?<body>\`$env:npm_config_offline[ \t]*=[ \t]*'true'[ \t]*)$" },
    @{ Id = 'ci-scanner-npm-offline-moved-after'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'move-after-line'; Pattern = "(?m)^[ \t]*\`$env:npm_config_offline[ \t]*=[ \t]*'true'[ \t]*\r?$"; AnchorPattern = '(?m)^[ \t]*if \(Test-Path \$f\).*check-licenses\.ps1.*\r?$' },
    # Break caught: offline mode turns a cold/missing PyPI or npm cache into a coverage gap; without -Strict the
    # scanner reports that gap but exits zero, so CI can claim a successful license audit after scanning nothing.
    @{ Id = 'ci-scanner-strict-removed'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'strip-token'; Pattern = '(?m)^(?<head>[ \t]*if \(Test-Path \$f\) \{ pwsh -NoProfile -File \$f)[ \t]+-Strict(?<tail>[ \t]*\} else \{.*)$' },
    @{ Id = 'ci-provision-pip-pin-drift'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*uv run --with pip-licenses==)5\.5\.5(?: pip-licenses --version[ \t]*)$'; Text = '6.0.0a1 pip-licenses --version' },
    @{ Id = 'ci-provision-npm-pin-drift'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*npm install --no-save --package-lock=false --ignore-scripts license-checker@)25\.0\.1[ \t]*$'; Text = '24.0.0' },
    @{ Id = 'ci-provision-app-deps-deleted'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'delete'; Pattern = '(?m)^[ \t]*npm ci --prefix frontend --ignore-scripts --no-audit --no-fund[ \t]*\r?\n' },
    @{ Id = 'ci-provision-app-deps-moved-after-scanner'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'move-after-line'; Pattern = '(?m)^[ \t]*npm ci --prefix frontend --ignore-scripts --no-audit --no-fund[ \t]*\r?$'; AnchorPattern = '(?m)^[ \t]*npm install --no-save --package-lock=false --ignore-scripts license-checker@25\.0\.1[ \t]*\r?$' },
    @{ Id = 'ci-provision-pip-dead-guard'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>uv run --with pip-licenses==5\.5\.5 pip-licenses --version[ \t]*)$' },
    @{ Id = 'ci-provision-npm-dead-guard'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'wrap-false-indented'; Pattern = '(?m)^(?<keep>[ \t]*)(?<body>npm install --no-save --package-lock=false --ignore-scripts license-checker@25\.0\.1[ \t]*)$' },
    @{ Id = 'ci-provision-handoff-deleted'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'delete'; Pattern = '(?m)^[ \t]*pwsh -NoProfile -File scripts/license-scanner-check\.ps1 -Suite provision-handoff[ \t]*\r?\n' },
    @{ Id = 'ci-setup-uv-version-drift'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'strip-token'; Pattern = '(?ms)^(?<head>[ \t]*-[ \t]+name:[ \t]+Install uv[ \t]*\r?\n.*?^[ \t]*version:[ \t]*["'']?)0\.7\.9(?<tail>["'']?[ \t]*\r?$)' },
    @{ Id = 'ci-provision-uv-cache-drift'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'strip-token'; Pattern = '(?ms)^(?<head>[ \t]*-[ \t]+name:[ \t]+Provision license scanners \(online cache warm-up\)[ \t]*\r?\n.*?^[ \t]*UV_CACHE_DIR:[ \t]*\$\{\{ runner\.temp \}\}/license-scanner-uv-)5\.5\.5(?<tail>[ \t]*\r?$)' },
    @{ Id = 'ci-gate-uv-cache-drift'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'strip-token'; Pattern = '(?ms)^(?<head>[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*\r?\n.*?^[ \t]*UV_CACHE_DIR:[ \t]*\$\{\{ runner\.temp \}\}/license-scanner-uv-)5\.5\.5(?<tail>[ \t]*\r?$)' },
    @{ Id = 'ci-gate-condition-falsified'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*\r?\n[ \t]*)if:.*$'; Text = 'if: ${{ false }}' },
    @{ Id = 'ci-gate-continue-on-error'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'insert-after'; Pattern = '(?m)^[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*\r?\n[ \t]*if:.*$'; Text = '        continue-on-error: true' },
    # `shell:` 的**值**：下面整段 run: 块分析都按 PowerShell 解析它，改成 bash 后此前一路绿到底。
    @{ Id = 'ci-gate-shell-swapped'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*-[ \t]+name:[ \t]+License gate[ \t]*\r?\n[ \t]*if:.*\r?\n[ \t]*)shell:.*$'; Text = 'shell: bash' },
    # 「必需键缺失」与「没有 run: 块可执行」两处抬升点：删掉 License gate 的 `run: |` 那一行（用后瞻锚到
    # 它下面两行内的唯一那句 `$f = 'scripts/check-licenses.ps1'`，故单点唯一）。两个码同时抬起是正确结果。
    @{ Id = 'ci-gate-run-block-removed'; Codes = @('INTEGRATION-CI-ACTIVE', 'INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'delete'; Pattern = "(?m)^[ \t]*run:[ \t]*\|[ \t]*\r?\n(?=(?:[^\r\n]*\r?\n){0,4}[ \t]*\`$f = 'scripts/check-licenses\.ps1'[ \t]*\r?$)" },
    # 命令前最后一次存活赋值决定实参；这枚变异证明检查器与 PowerShell 的顺序语义一致。
    @{ Id = 'ci-scanner-path-ambiguous'; Codes = @('INTEGRATION-CI-SCANNER'); Target = 'Workflow'; Kind = 'insert-after'; Pattern = "(?m)^[ \t]*\`$f = 'scripts/check-licenses\.ps1'[ \t]*$"; Text = "          `$f = 'scripts/check-cards.ps1'" },
    # 姊妹闸 E2E verify gate 的条件被改掉：上面把 License gate 的 `if:` 钉成字面量，理由是「它等于姊妹闸
    # 用的那条」；姊妹闸一变，那个理由就不再成立，必须有人报出来。
    @{ Id = 'ci-sibling-gate-falsified'; Codes = @('INTEGRATION-CI-ACTIVE'); Target = 'Workflow'; Kind = 'replace-line'; Pattern = '(?m)^(?<keep>[ \t]*-[ \t]+name:[ \t]+E2E verify gate[ \t]*\r?\n[ \t]*)if:.*$'; Text = 'if: ${{ false }}' },
    @{ Id = 'policy-doc-hidden'; Codes = @('INTEGRATION-POLICY-DOC'); Target = 'Policy'; Kind = 'hide'; Pattern = '(?m)^.*license-scanner-check\.ps1 -Suite integration.*$' },
    # POLICY-DOC 的另一半（`## 5.` 小节不唯一）：`policy-doc-hidden` 落在「小节里没写出命令」那一支，
    # 这一枚落在「小节数 ≠ 1」那一支。
    @{ Id = 'policy-doc-section-duplicated'; Codes = @('INTEGRATION-POLICY-DOC'); Target = 'Policy'; Kind = 'insert-after'; Pattern = '(?m)^##\s+5\.\s.*$'; Text = '## 5. 核验流程（重复小节）' },
    @{ Id = 'release-doc-hidden'; Codes = @('INTEGRATION-RELEASE-DOC'); Target = 'Release'; Kind = 'hide'; Pattern = '(?m)^.*license-scanner-check\.ps1 -Suite integration.*$' },
    # RELEASE-DOC 的另一半（哨兵项还在、但那条命令被从项里删掉）：`release-doc-hidden` 落在「哨兵项数
    # ≠ 1」那一支，这一枚落在「哨兵项里没写出命令」那一支。
    @{ Id = 'release-doc-command-stripped'; Codes = @('INTEGRATION-RELEASE-DOC'); Target = 'Release'; Kind = 'strip-token'; Pattern = '(?m)^(?<head>.*\[GRADLE-LIC-SCANNER-ONLY\].*)license-scanner-check\.ps1 -Suite integration(?<tail>.*)$' }
  )
  # 「每个抬升点都有变异盯着」的机检本体（替掉原来那句注释里的数字，见上）。码集从
  # Get-IntegrationWiringFailures 的源码里抽 `$found.Add("[CODE] …")`，与变异声明集合求**相等**。
  # 本断言刻意放在函数体**之外**：它自己的失败码不会被上面的抽取正则收进去，也不进变异分类器的实得集合。
  $suiteSourceText = [System.IO.File]::ReadAllText($PSCommandPath)
  $suiteSourceAst = [System.Management.Automation.Language.Parser]::ParseInput($suiteSourceText, [ref]$null, [ref]$null)
  $wiringFunctionAst = @($suiteSourceAst.FindAll({
    param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-IntegrationWiringFailures'
  }, $true))
  if ($wiringFunctionAst.Count -ne 1) {
    Assert-Integration $false "[INTEGRATION-WIRING-CODE-COVERAGE] expected exactly one Get-IntegrationWiringFailures definition to derive the raised-code set from, found $($wiringFunctionAst.Count)"
  } else {
    $wiringRaisedCodes = @([regex]::Matches($wiringFunctionAst[0].Extent.Text, '\$found\.Add\((?:["''])\[(?<code>[A-Z0-9-]+)\]') | ForEach-Object { $_.Groups['code'].Value } | Sort-Object -Unique)
    $wiringDeclaredCodes = @($wiringMutations | ForEach-Object { $_.Codes } | Sort-Object -Unique)
    Assert-Integration (
      ($wiringRaisedCodes -join ',') -ceq ($wiringDeclaredCodes -join ',')
    ) "[INTEGRATION-WIRING-CODE-COVERAGE] the codes Get-IntegrationWiringFailures can raise {$($wiringRaisedCodes -join ',')} are not exactly the codes the mutations declare {$($wiringDeclaredCodes -join ',')} - either a raise site has no mutation watching it, or a mutation names a code nothing can produce"
  }

  if (-not $SkipMutations) {
    foreach ($wm in $wiringMutations) {
      $texts = @{ Selftest = $selftestText; Workflow = $workflowText; Policy = $policyText; Release = $releaseText }
      $original = $texts[$wm.Target]
      # 单点变异必须只动一处。`[regex]::Replace(input, pattern, evaluator, 1)` 没有 count 静态重载——
      # 那个 1 会被隐式当成 RegexOptions.IgnoreCase，于是变成全量替换（本仓 TD51 踩过同一个坑）。
      # 实例方法 Regex.Replace(input, evaluator, count) 才真的有 count。
      # append/reorder 类没有 Pattern 键；StrictMode 下先取再判会直接抛。
      $wmRegex = if (@('append', 'reorder', 'replace-license-with-inert-scalar', 'replace-license-with-quoted-scalar', 'replace-ordered-with-disabled-job', 'replace-ordered-with-expression-disabled-job', 'move-e2e-to-separate-job') -contains $wm.Kind) { $null } else { [regex]::new($wm.Pattern) }
      $mutated = switch ($wm.Kind) {
        'comment' { $wmRegex.Replace($original, { param($m) '#' + $m.Value }, 1) }
        'delete' { $wmRegex.Replace($original, '', 1) }
        'hide' { $wmRegex.Replace($original, { param($m) '<!-- ' + $m.Value + ' -->' }, 1) }
        'append' { $original + $wm.Text }
        # `keep` 是可选组：不存在时 .Value 是空串，于是 replace-line 既能整行替换、也能保留前缀。
        'replace-line' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + $wm.Text }, 1) }
        'strip-token' { $wmRegex.Replace($original, { param($m) $m.Groups['head'].Value + $m.Groups['tail'].Value }, 1) }
        # 语句原样保留，只把它包进恒假的 if：AST 上那条命令仍在，「存在」类断言全绿，唯有存活性会红。
        'wrap-false' { $wmRegex.Replace($original, { param($m) 'if ($false) { ' + $m.Value.Trim() + ' }' }, 1) }
        'wrap-constant-false' { $wmRegex.Replace($original, { param($m) 'if (1 -eq 2) { ' + $m.Value.Trim() + ' }' }, 1) }
        'wrap-function' { $wmRegex.Replace($original, { param($m) 'function Invoke-InertIntegrationGate { ' + $m.Value.Trim() + ' }' }, 1) }
        'wrap-else' { $wmRegex.Replace($original, { param($m) 'if ($true) { } else { ' + $m.Value.Trim() + ' }' }, 1) }
        'wrap-while-false' { $wmRegex.Replace($original, { param($m) 'while ($false) { ' + $m.Value.Trim() + ' }' }, 1) }
        'wrap-false-indented' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + 'if ($false) { ' + $m.Groups['body'].Value + ' }' }, 1) }
        'wrap-constant-false-indented' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + 'if (1 -eq 2) { ' + $m.Groups['body'].Value + ' }' }, 1) }
        'wrap-function-indented' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + 'function Invoke-InertLicenseGate { ' + $m.Groups['body'].Value + ' }' }, 1) }
        'wrap-else-indented' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + 'if ($true) { } else { ' + $m.Groups['body'].Value + ' }' }, 1) }
        'wrap-while-false-indented' { $wmRegex.Replace($original, { param($m) $m.Groups['keep'].Value + 'while ($false) { ' + $m.Groups['body'].Value + ' }' }, 1) }
        'insert-after' { $wmRegex.Replace($original, { param($m) $m.Value + "`n" + $wm.Text }, 1) }
        'reorder' { Move-WorkflowStepBefore -Text $original -Step $wm.Step -Before $wm.Before }
        'replace-license-with-inert-scalar' { Replace-LicenseStepWithInertScalarFixture -Text $original }
        'replace-license-with-quoted-scalar' { Replace-LicenseStepWithQuotedScalarFixture -Text $original }
        'replace-ordered-with-disabled-job' { Replace-OrderedStepsWithDisabledJobFixture -Text $original }
        'replace-ordered-with-expression-disabled-job' { Replace-OrderedStepsWithDisabledJobFixture -Text $original -Condition '${{ 1 == 0 }}' }
        'move-e2e-to-separate-job' { Move-E2eStepToSeparateJobFixture -Text $original }
        'move-after-line' {
          $sourceMatch = $wmRegex.Match($original)
          if (-not $sourceMatch.Success) { $original } else {
            $withoutSource = $original.Remove($sourceMatch.Index, $sourceMatch.Length)
            $anchorMatch = [regex]::Match($withoutSource, $wm.AnchorPattern)
            if (-not $anchorMatch.Success) { $original } else {
              $withoutSource.Insert($anchorMatch.Index + $anchorMatch.Length, "`n" + $sourceMatch.Value)
            }
          }
        }
        default { throw "unknown wiring mutation kind '$($wm.Kind)'" }
      }
      if ($mutated -ceq $original) {
        Assert-Integration $false "[INTEGRATION-WIRING-MUTATION] mutation '$($wm.Id)' changed nothing - its pattern no longer matches, so this assertion class is unproven"
        continue
      }
      $texts[$wm.Target] = $mutated
      $mutantFailures = @(Get-IntegrationWiringFailures -SelftestText $texts.Selftest -WorkflowText $texts.Workflow -PolicyText $texts.Policy -ReleaseText $texts.Release)
      $raisedCodes = @($mutantFailures | ForEach-Object {
        if ($_ -match '^\[(?<code>[A-Z0-9-]+)\]') { $Matches.code } else { 'UNCLASSIFIED' }
      } | Sort-Object -Unique)
      $expectedCodes = @($wm.Codes | Sort-Object -Unique)
      Assert-Integration (
        ($raisedCodes -join ',') -ceq ($expectedCodes -join ',')
      ) "[INTEGRATION-WIRING-MUTATION] mutation '$($wm.Id)' raised {$($raisedCodes -join ',')} but must raise exactly {$($expectedCodes -join ',')} - a code that only ever co-fires with another mutation's target is not proven to be under test. Raised: $($mutantFailures -join ' | ')"
    }
  }

  if ($integrationFailures.Count -gt 0) {
    Write-Error "[INTEGRATION-CONTRACT] $($integrationFailures -join "`n[INTEGRATION-CONTRACT] ")"
    exit 1
  }
  # 与四个子套件同一形态的 mutation PASS 行：它的**存在与否**就是「本套件自己的接线变异这一趟到底跑没跑」
  # 的可读证据。A7 要求 -SkipMutations 的语义机检可判，而「跑了但不打印」与「没跑」在 stdout 上此前无从分辨。
  if (-not $SkipMutations) { Write-Host "license-scanner-check(integration wiring mutations): PASS ($($wiringMutations.Count))" }

  # -SkipRealScan 作用域守卫的红证据：本进程早已跑过那一行，只能从子进程验。配一枚删除变异，
  # 证明这条红确实来自那道守卫、而不是别处（副本删掉守卫后同一条命令必须变绿）。
  $scopeGuardOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite gav-bounds -SkipRealScan -SkipMutations 2>&1 | Out-String)
  $scopeGuardExit = $LASTEXITCODE
  Assert-Integration (
    $scopeGuardExit -ne 0 -and $scopeGuardOutput -match '\[INTEGRATION-SKIPREALSCAN-SCOPE\]'
  ) "[INTEGRATION-SKIPREALSCAN-SCOPE] -SkipRealScan on a non-integration suite was accepted (exit=$scopeGuardExit): $scopeGuardOutput"
  function Invoke-ScopeGuardDeletionMutation {
    # PASS marker lives inside the operation, so a skipped run cannot execute the mutation silently: the parent
    # `-SkipMutations` probe rejects every `mutations): PASS` line in its child output.
    $scopeGuardSource = [System.IO.File]::ReadAllText($PSCommandPath)
    $scopeGuardBlock = @(
      "if (`$SkipRealScan -and `$Suite -ne 'integration') {",
      "  Write-Error `"[INTEGRATION-SKIPREALSCAN-SCOPE] -SkipRealScan is valid only for -Suite integration (got '`$Suite')`"",
      '  exit 1',
      '}'
    ) -join "`n"
    if (([regex]::Matches($scopeGuardSource, [regex]::Escape($scopeGuardBlock))).Count -ne 1) {
      Assert-Integration $false '[INTEGRATION-SKIPREALSCAN-SCOPE] the guard block anchor is no longer unique in this script, so its delete-mutation cannot be built'
      return
    }
    $scopeMutantPath = Join-Path $PSScriptRoot ".license-scanner-$PID-skiprealscan-scope.ps1"
    try {
      [System.IO.File]::WriteAllText($scopeMutantPath, $scopeGuardSource.Replace($scopeGuardBlock, ''), [System.Text.UTF8Encoding]::new($false))
      $scopeMutantOutput = (& pwsh -NoProfile -File $scopeMutantPath -Suite gav-bounds -SkipRealScan -SkipMutations 2>&1 | Out-String)
      $scopeMutantExit = $LASTEXITCODE
      Assert-Integration (
        $scopeMutantExit -eq 0
      ) "[INTEGRATION-SKIPREALSCAN-SCOPE] deleting the guard did not turn the invalid invocation green (exit=$scopeMutantExit) - the rejection observed above came from somewhere else: $scopeMutantOutput"
      Write-Host 'license-scanner-check(integration scope mutations): PASS (1)'
    } finally {
      if (Test-Path -LiteralPath $scopeMutantPath) { Remove-Item -LiteralPath $scopeMutantPath -Force }
    }
  }
  if (-not $SkipMutations) { Invoke-ScopeGuardDeletionMutation }

  # -SkipMutations 必须真的传给四个子套件。此前它被接受却从不转发：调用方以为省掉了 mutation 成本，
  # 实际全跑，于是「开关有效」和「开关被忽略」在输出上无从分辨——沉默地不做事比报错更难发现。
  # 转发线被抽成纯函数，两个分支的参数表各自被逐字钉住：删掉那一行，$true 那条断言立刻红。
  function Get-ChildSuiteArguments {
    param(
      [Parameter(Mandatory)][string]$ChildSuite,
      [Parameter(Mandatory)][string]$ChildScannerPath,
      [Parameter(Mandatory)][bool]$ChildSkipMutations
    )
    $arguments = @('-Suite', $ChildSuite, '-ScannerPath', $ChildScannerPath)
    if ($ChildSkipMutations) { $arguments += '-SkipMutations' } # 转发线
    return $arguments
  }
  Assert-Integration (
    (Get-ChildSuiteArguments -ChildSuite 'graph' -ChildScannerPath $ScannerPath -ChildSkipMutations $true) -contains '-SkipMutations'
  ) '[INTEGRATION-SKIPMUTATIONS-FORWARD] -SkipMutations was not forwarded into the child suite argument list'
  Assert-Integration (
    -not ((Get-ChildSuiteArguments -ChildSuite 'graph' -ChildScannerPath $ScannerPath -ChildSkipMutations $false) -contains '-SkipMutations')
  ) '[INTEGRATION-SKIPMUTATIONS-FORWARD] the child suite argument list carried -SkipMutations although the caller never asked for it'
  # 端到端：用**同一段**构造逻辑真跑一次最便宜的子套件，要求它没有打印 mutation PASS 行。
  $forwardProbeArguments = Get-ChildSuiteArguments -ChildSuite 'gav-bounds' -ChildScannerPath $ScannerPath -ChildSkipMutations $true
  $forwardProbeOutput = (& pwsh -NoProfile -File $PSCommandPath @forwardProbeArguments 2>&1 | Out-String)
  $forwardProbeExit = $LASTEXITCODE
  Assert-Integration (
    $forwardProbeExit -eq 0 -and $forwardProbeOutput -notmatch 'mutations\): PASS'
  ) "[INTEGRATION-SKIPMUTATIONS-FORWARD] the child suite still ran its mutations under the forwarded -SkipMutations (exit=$forwardProbeExit): $forwardProbeOutput"

  foreach ($childSuite in @('graph', 'policy', 'diagnostics', 'gav-bounds')) {
    $childArguments = Get-ChildSuiteArguments -ChildSuite $childSuite -ChildScannerPath $ScannerPath -ChildSkipMutations ([bool]$SkipMutations)
    $childOutput = (& pwsh -NoProfile -File $PSCommandPath @childArguments 2>&1 | Out-String)
    $childExit = $LASTEXITCODE
    Assert-Integration (
      $childExit -eq 0 -and $childOutput -match "license-scanner-check\($([regex]::Escape($childSuite))\): PASS"
    ) "[INTEGRATION-CHILD] $childSuite failed or omitted its PASS marker (exit=$childExit): $childOutput"
    # 这里是**唯一**一处把本脚本自己的 -SkipMutations 转发下去的调用（上面那三条断言喂的是字面量
    # $true/$false，观察不到这一行）。两个方向都要断言：只断言「跳过时不该有 mutation 行」的话，把这一行
    # 改成恒 $false 在默认运行下毫无影响、全绿，而 PASS 文案仍替它宣称「各子套件 mutation 均已按
    # -SkipMutations 跳过」——A16 那一类假陈述。
    $childRanMutations = $childOutput -match 'mutations\): PASS'
    Assert-Integration (
      $childRanMutations -ne [bool]$SkipMutations
    ) "[INTEGRATION-SKIPMUTATIONS-FORWARD] $childSuite did not follow this run's -SkipMutations (-SkipMutations=$([bool]$SkipMutations), child printed a mutations PASS line=$childRanMutations): $childOutput"
  }

  # A6/A7 的机检那一半：`-Suite integration -SkipMutations` 这条路径此前从未被任何测试执行过（三处
  # Get-ChildSuiteArguments 断言用字面量、转发那行无人观察、子套件循环也不检查 mutation 行的**缺席**）。
  # 子进程真跑一次，要求：① 退出 0；② PASS 行的 ASCII 哨兵是 [mutations=skipped]；③ 整份 stdout 里
  # **一条** `mutations): PASS` 都没有——四个子套件的、以及本套件自己那条 wiring 变异 PASS 行，都必须真的没跑。
  # 只在**非** -SkipMutations 的运行里起这个探针：既杜绝无限递归（子进程带 -SkipMutations，不会再起一层），
  # 也让 -SkipMutations 保持它「省成本」的本意。
  if (-not $SkipMutations) {
    $skipForwardArguments = @('-Suite', 'integration', '-ScannerPath', $ScannerPath, '-SkipRealScan', '-SkipMutations')
    $skipForwardOutput = (& pwsh -NoProfile -File $PSCommandPath @skipForwardArguments 2>&1 | Out-String)
    $skipForwardExit = $LASTEXITCODE
    Assert-Integration (
      $skipForwardExit -eq 0 -and
      $skipForwardOutput -match 'license-scanner-check\(integration\): PASS \[real-scan=skipped\] \[mutations=skipped\]' -and
      $skipForwardOutput -notmatch 'mutations\): PASS'
    ) "[INTEGRATION-SKIPMUTATIONS-FORWARD] a nested '-Suite integration -SkipRealScan -SkipMutations' run did not report both skips, or still ran mutations (exit=$skipForwardExit): $skipForwardOutput"
  }

  function Invoke-StrictOfflineLicenseScan {
    param([Parameter(Mandatory)][string]$Path)
    $priorUvOffline = [Environment]::GetEnvironmentVariable('UV_OFFLINE', 'Process')
    $priorNpmOffline = [Environment]::GetEnvironmentVariable('npm_config_offline', 'Process')
    try {
      $env:UV_OFFLINE = '1'
      $env:npm_config_offline = 'true'
      $output = (& pwsh -NoProfile -File $Path -Strict 2>&1 | Out-String)
      return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
    } finally {
      if ($null -eq $priorUvOffline) { Remove-Item Env:UV_OFFLINE -ErrorAction SilentlyContinue } else { $env:UV_OFFLINE = $priorUvOffline }
      if ($null -eq $priorNpmOffline) { Remove-Item Env:npm_config_offline -ErrorAction SilentlyContinue } else { $env:npm_config_offline = $priorNpmOffline }
    }
  }

  # Hermetic warm-up → offline handoff proof for npm. Install a local fixture package into the exact
  # repository-root `node_modules` scope used by CI, then run the real production scanner with an empty cache and npm
  # forced offline. The fixture bin marker proves npx resolved the warmed local tool instead of attempting fetch.
  if (-not $SkipMutations) {
    $handoffRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("license-npm-handoff-" + [guid]::NewGuid().ToString('N'))
    $handoffScripts = Join-Path $handoffRoot 'scripts'
    $handoffFrontend = Join-Path $handoffRoot 'frontend'
    $handoffPackage = Join-Path $handoffRoot 'fixture-license-checker'
    $handoffAppPackage = Join-Path $handoffRoot 'fixture-app-dep'
    $handoffCache = Join-Path $handoffRoot 'npm-cache'
    $handoffMarker = Join-Path $handoffRoot 'license-checker-invoked.txt'
    $priorNpmCacheHandoff = [Environment]::GetEnvironmentVariable('npm_config_cache', 'Process')
    $priorNpmOfflineHandoff = [Environment]::GetEnvironmentVariable('npm_config_offline', 'Process')
    $priorHandoffMarker = [Environment]::GetEnvironmentVariable('LICENSE_CHECKER_HANDOFF_MARKER', 'Process')
    try {
      New-Item -ItemType Directory -Force -Path $handoffScripts, $handoffFrontend, $handoffPackage, $handoffAppPackage, $handoffCache | Out-Null
      foreach ($sibling in @('check-licenses.ps1', '_config.ps1', '_unicode.ps1', '_encoding.ps1')) {
        Copy-Item -LiteralPath (Join-Path $repoRoot "scripts/$sibling") -Destination (Join-Path $handoffScripts $sibling)
      }
      Set-Content -LiteralPath (Join-Path $handoffFrontend 'package.json') -Encoding utf8 -Value '{"name":"handoff-app","private":true,"dependencies":{"fixture-app-dep":"file:../fixture-app-dep"}}'
      Set-Content -LiteralPath (Join-Path $handoffAppPackage 'package.json') -Encoding utf8 -Value '{"name":"fixture-app-dep","version":"1.0.0","license":"MIT"}'
      Set-Content -LiteralPath (Join-Path $handoffPackage 'package.json') -Encoding utf8 -Value '{"name":"license-checker","version":"25.0.1","bin":{"license-checker":"cli.js"}}'
      Set-Content -LiteralPath (Join-Path $handoffPackage 'cli.js') -Encoding utf8 -Value @'
#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const startIndex = process.argv.indexOf('--start');
const start = startIndex >= 0 ? process.argv[startIndex + 1] : '';
const dep = path.join(start, 'node_modules', 'fixture-app-dep', 'package.json');
if (!start || !fs.existsSync(dep)) {
  process.stderr.write('fixture app dependency missing from --start tree');
  process.exit(31);
}
fs.writeFileSync(process.env.LICENSE_CHECKER_HANDOFF_MARKER, 'fixture-app-dep@1.0.0');
process.stdout.write(JSON.stringify({'fixture-app-dep@1.0.0': {licenses: 'MIT'}}));
'@
      $env:npm_config_cache = $handoffCache
      $env:npm_config_offline = 'true'
      $env:LICENSE_CHECKER_HANDOFF_MARKER = $handoffMarker
      $handoffLockOutput = (& npm install --prefix $handoffFrontend --package-lock-only --ignore-scripts --no-audit --no-fund 2>&1 | Out-String)
      $handoffLockExit = $LASTEXITCODE
      $handoffCiOutput = if ($handoffLockExit -eq 0) { (& npm ci --prefix $handoffFrontend --ignore-scripts --no-audit --no-fund 2>&1 | Out-String) } else { '' }
      $handoffCiExit = if ($handoffLockExit -eq 0) { $LASTEXITCODE } else { $handoffLockExit }
      $handoffInstallOutput = if ($handoffCiExit -eq 0) { (& npm install --prefix $handoffRoot --no-save --package-lock=false --ignore-scripts $handoffPackage 2>&1 | Out-String) } else { '' }
      $handoffInstallExit = if ($handoffCiExit -eq 0) { $LASTEXITCODE } else { $handoffCiExit }
      $handoffResult = if ($handoffLockExit -eq 0 -and $handoffCiExit -eq 0 -and $handoffInstallExit -eq 0) {
        Invoke-StrictOfflineLicenseScan -Path (Join-Path $handoffScripts 'check-licenses.ps1')
      } else { [pscustomobject]@{ ExitCode = $handoffInstallExit; Output = $handoffInstallOutput } }
      Assert-Integration (
        $handoffLockExit -eq 0 -and $handoffCiExit -eq 0 -and $handoffInstallExit -eq 0 -and
        $handoffResult.ExitCode -eq 0 -and (Test-Path -LiteralPath $handoffMarker) -and
        (Get-Content -LiteralPath $handoffMarker -Raw) -ceq 'fixture-app-dep@1.0.0'
      ) "[INTEGRATION-NPM-HANDOFF] locked app dependency tree + repository-root scanner warm-up did not feed the real offline scan (lock=$handoffLockExit ci=$handoffCiExit install=$handoffInstallExit scan=$($handoffResult.ExitCode)): lock=[$handoffLockOutput] ci=[$handoffCiOutput] install=[$handoffInstallOutput] scan=[$($handoffResult.Output)]"
      Remove-Item -LiteralPath (Join-Path $handoffFrontend 'node_modules') -Recurse -Force
      Remove-Item -LiteralPath $handoffMarker -Force
      $missingModulesResult = Invoke-StrictOfflineLicenseScan -Path (Join-Path $handoffScripts 'check-licenses.ps1')
      Assert-Integration (
        $missingModulesResult.ExitCode -ne 0 -and -not (Test-Path -LiteralPath $handoffMarker)
      ) "[INTEGRATION-NPM-NODE-MODULES] missing frontend/node_modules did not fail the real strict offline scan (exit=$($missingModulesResult.ExitCode)): $($missingModulesResult.Output)"
    } finally {
      if ($null -eq $priorNpmCacheHandoff) { Remove-Item Env:npm_config_cache -ErrorAction SilentlyContinue } else { $env:npm_config_cache = $priorNpmCacheHandoff }
      if ($null -eq $priorNpmOfflineHandoff) { Remove-Item Env:npm_config_offline -ErrorAction SilentlyContinue } else { $env:npm_config_offline = $priorNpmOfflineHandoff }
      if ($null -eq $priorHandoffMarker) { Remove-Item Env:LICENSE_CHECKER_HANDOFF_MARKER -ErrorAction SilentlyContinue } else { $env:LICENSE_CHECKER_HANDOFF_MARKER = $priorHandoffMarker }
      if (Test-Path -LiteralPath $handoffRoot) { Remove-Item -LiteralPath $handoffRoot -Recurse -Force }
    }
  }

  # Hermetic cold-cache proof: the real production scanner sees both supported manifests and resolves uv,
  # pip-licenses, and npx to probes that record invocation. A probe records an outbound attempt if its ecosystem's
  # official offline environment flag is absent. Empty cache roots plus nonzero probes must make -Strict fail closed,
  # while the outbound marker stays absent.
  if (-not $SkipMutations) {
    $coldRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("license-offline-" + [guid]::NewGuid().ToString('N'))
    $coldScripts = Join-Path $coldRoot 'scripts'
    $coldBin = Join-Path $coldRoot 'bin'
    $coldFrontend = Join-Path $coldRoot 'frontend'
    $outboundMarker = Join-Path $coldRoot 'outbound-attempted.txt'
    $invokedMarker = Join-Path $coldRoot 'invoked.txt'
    $priorPath = $env:PATH
    $priorUvCache = [Environment]::GetEnvironmentVariable('UV_CACHE_DIR', 'Process')
    $priorNpmCache = [Environment]::GetEnvironmentVariable('npm_config_cache', 'Process')
    try {
      New-Item -ItemType Directory -Force -Path $coldScripts, $coldBin, $coldFrontend, (Join-Path $coldRoot 'uv-cache'), (Join-Path $coldRoot 'npm-cache') | Out-Null
      foreach ($sibling in @('check-licenses.ps1', '_config.ps1', '_unicode.ps1', '_encoding.ps1')) {
        Copy-Item -LiteralPath (Join-Path $repoRoot "scripts/$sibling") -Destination (Join-Path $coldScripts $sibling)
      }
      Set-Content -LiteralPath (Join-Path $coldRoot 'pyproject.toml') -Encoding utf8 -Value "[project]`nname = 'cold-license-probe'`nversion = '0.0.0'"
      Set-Content -LiteralPath (Join-Path $coldFrontend 'package.json') -Encoding utf8 -Value '{"name":"cold-license-probe","private":true}'
      $probeTemplate = @'
param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Remaining)
Add-Content -LiteralPath '__INVOKED__' -Encoding utf8 -Value '__TOOL__'
if (__OFFLINE_TEST__) { exit 23 }
Set-Content -LiteralPath '__OUTBOUND__' -Encoding utf8 -Value '__TOOL__'
exit 24
'@
      foreach ($probe in @(
        @{ Name = 'uv'; Offline = "`$env:UV_OFFLINE -ceq '1'" },
        @{ Name = 'pip-licenses'; Offline = "`$env:UV_OFFLINE -ceq '1'" },
        @{ Name = 'npx'; Offline = "`$env:npm_config_offline -ceq 'true'" }
      )) {
        $probeText = $probeTemplate.Replace('__INVOKED__', $invokedMarker.Replace("'", "''")).Replace('__OUTBOUND__', $outboundMarker.Replace("'", "''")).Replace('__TOOL__', $probe.Name).Replace('__OFFLINE_TEST__', $probe.Offline)
        Set-Content -LiteralPath (Join-Path $coldBin ($probe.Name + '.ps1')) -Encoding utf8 -Value $probeText
      }
      $env:PATH = $coldBin + [System.IO.Path]::PathSeparator + $priorPath
      $env:UV_CACHE_DIR = Join-Path $coldRoot 'uv-cache'
      $env:npm_config_cache = Join-Path $coldRoot 'npm-cache'
      $coldResult = Invoke-StrictOfflineLicenseScan -Path (Join-Path $coldScripts 'check-licenses.ps1')
      $invokedTools = if (Test-Path $invokedMarker) { @(Get-Content -LiteralPath $invokedMarker) } else { @() }
      Assert-Integration (
        $coldResult.ExitCode -ne 0 -and
        -not (Test-Path $outboundMarker) -and
        $invokedTools -contains 'uv' -and
        $invokedTools -contains 'pip-licenses' -and
        $invokedTools -contains 'npx'
      ) "[INTEGRATION-OFFLINE-COLD] cold-cache strict scan did not fail closed without an outbound attempt (exit=$($coldResult.ExitCode), invoked={$($invokedTools -join ',')}, outbound=$([bool](Test-Path $outboundMarker))): $($coldResult.Output)"
    } finally {
      $env:PATH = $priorPath
      if ($null -eq $priorUvCache) { Remove-Item Env:UV_CACHE_DIR -ErrorAction SilentlyContinue } else { $env:UV_CACHE_DIR = $priorUvCache }
      if ($null -eq $priorNpmCache) { Remove-Item Env:npm_config_cache -ErrorAction SilentlyContinue } else { $env:npm_config_cache = $priorNpmCache }
      if (Test-Path $coldRoot) { Remove-Item -LiteralPath $coldRoot -Recurse -Force }
    }
  }

  if (-not $SkipRealScan) {
    $scannerResult = Invoke-StrictOfflineLicenseScan -Path $ScannerPath
    $scannerOutput = $scannerResult.Output
    $scannerExit = $scannerResult.ExitCode
    Assert-Integration ($scannerExit -eq 0) "[INTEGRATION-REAL-SCAN] strict repository scan failed (exit=$scannerExit): $scannerOutput"
    # 版本不写死：`org.testng:testng` 并不在 libs.versions.toml 里被 pin（它是 kotlin-test-testng 的
    # 传递依赖），卡片要求的也只是这个 module 被逐坐标报告。钉 7.0.0 会让一次例行升级把套件红在
    # 「real scan omitted the core TestNG coordinate」上——一句关于病因的假陈述。故：需求锚到清单里
    # 真正声明的那一行，报告侧只要求 module + 一个具体版本号。
    # 清单只在真扫这一支才读：-SkipRealScan（seeded 走的那条）不该因 android/ 缺文件而炸。
    $versionsText = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'android/gradle/libs.versions.toml'))
    $testNgManifestPattern = '(?m)^[ \t]*kotlin-test-testng[ \t]*=[ \t]*\{[^\r\n]*module[ \t]*=[ \t]*"org\.jetbrains\.kotlin:kotlin-test-testng"[^\r\n]*\}[ \t]*\r?$'
    $testNgOutputPattern = '(?m)^[ \t]*-[ \t]+org\.testng:testng:[0-9][^\r\n]*\r?$'
    # Nested pwsh may preserve host colour SGR sequences in captured text; strip only those presentation bytes
    # before applying the anchored semantic line predicate. Diagnostics retain the raw scanner output.
    $scannerMatchText = $scannerOutput -replace '\e\[[0-9;]*m', ''
    Assert-Integration (
      ([regex]::Matches($versionsText, $testNgManifestPattern)).Count -eq 1
    ) "[INTEGRATION-TESTNG-MANIFEST] android/gradle/libs.versions.toml no longer has exactly one anchored kotlin-test-testng module declaration, so org.testng:testng is no longer the runner this assertion is about. Strict scanner output: $scannerOutput"
    Assert-Integration (
      $scannerMatchText -match $testNgOutputPattern
    ) "[INTEGRATION-TESTNG] real scan omitted a concrete org.testng:testng coordinate: $scannerOutput"
    if (-not $SkipMutations) {
      $manifestMutant = ([regex]::new($testNgManifestPattern)).Replace($versionsText, '', 1)
      Assert-Integration (
        $manifestMutant -cne $versionsText -and $manifestMutant -notmatch $testNgManifestPattern
      ) "[INTEGRATION-TESTNG-MANIFEST-MUTATION] deleting the anchored kotlin-test-testng declaration did not kill its predicate. Strict scanner output: $scannerOutput"
      $outputMutant = ([regex]::new($testNgOutputPattern)).Replace($scannerMatchText, '', 1)
      Assert-Integration (
        $outputMutant -cne $scannerMatchText -and $outputMutant -notmatch $testNgOutputPattern
      ) "[INTEGRATION-TESTNG-MUTATION] deleting the anchored org.testng:testng report line did not kill its predicate. Strict scanner output: $scannerOutput"
    }
  }

  if ($integrationFailures.Count -gt 0) {
    Write-Error "[INTEGRATION-CONTRACT] $($integrationFailures -join "`n[INTEGRATION-CONTRACT] ")"
    exit 1
  }
  # PASS 文案只许陈述**本次真的跑过的证据**。此前 seeded 用 -SkipRealScan 跑，输出却照样宣称
  # 「真实仓 Strict 扫描通过」——一句没有对应执行的成功陈述，比没有输出更糟：它让读者停止追问。
  # 但中文散文对机器不可读：两种模式的 ASCII 前缀完全相同，seeded 的断言分辨不出它跑的是哪一支。
  # 故模式本身进 ASCII 哨兵（L165），并把哨兵的生成抽成纯函数、四种组合逐字钉住——把任一个三元
  # 折叠成单支，对应那条断言立刻红。
  function Get-IntegrationEvidenceSentinel {
    param(
      [Parameter(Mandatory)][bool]$RealScanSkipped,
      [Parameter(Mandatory)][bool]$MutationsSkipped
    )
    $realScan = if ($RealScanSkipped) { 'skipped' } else { 'executed' }
    $mutations = if ($MutationsSkipped) { 'skipped' } else { 'executed' }
    return "[real-scan=$realScan] [mutations=$mutations]"
  }
  $sentinelTruthTable = @(
    @{ RealScanSkipped = $true;  MutationsSkipped = $true;  Expected = '[real-scan=skipped] [mutations=skipped]' },
    @{ RealScanSkipped = $true;  MutationsSkipped = $false; Expected = '[real-scan=skipped] [mutations=executed]' },
    @{ RealScanSkipped = $false; MutationsSkipped = $true;  Expected = '[real-scan=executed] [mutations=skipped]' },
    @{ RealScanSkipped = $false; MutationsSkipped = $false; Expected = '[real-scan=executed] [mutations=executed]' }
  )
  foreach ($row in $sentinelTruthTable) {
    $actualSentinel = Get-IntegrationEvidenceSentinel -RealScanSkipped $row.RealScanSkipped -MutationsSkipped $row.MutationsSkipped
    Assert-Integration (
      $actualSentinel -ceq $row.Expected
    ) "[INTEGRATION-EVIDENCE-SENTINEL] sentinel for (real-scan skipped=$($row.RealScanSkipped), mutations skipped=$($row.MutationsSkipped)) is '$actualSentinel', expected '$($row.Expected)'"
  }
  if ($integrationFailures.Count -gt 0) {
    Write-Error "[INTEGRATION-CONTRACT] $($integrationFailures -join "`n[INTEGRATION-CONTRACT] ")"
    exit 1
  }
  $integrationEvidence = @('graph/policy/diagnostics/gav-bounds 子套件')
  $integrationEvidence += if ($SkipMutations) { '本套件 wiring 变异与各子套件 mutation 均已按 -SkipMutations 跳过' } else { '本套件 wiring 变异 + 各子套件 mutation' }
  $integrationEvidence += if ($SkipRealScan) { '真实仓 Strict 扫描已按 -SkipRealScan 跳过（未执行）' } else { '真实仓 Strict 扫描 + TestNG 坐标' }
  Write-Host "license-scanner-check(integration): PASS $(Get-IntegrationEvidenceSentinel -RealScanSkipped ([bool]$SkipRealScan) -MutationsSkipped ([bool]$SkipMutations))（$($integrationEvidence -join '；')）"
  exit 0
}

if ($Suite -eq 'gav-bounds') {
  function Assert-GavBounds {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  $fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-gav-bounds-$PID-$([guid]::NewGuid().ToString('N'))"
  try {
    $group255 = (('Gg' * 127) + 'G')
    $artifact255 = (('Aa' * 127) + 'A')
    $version255 = (('Vv' * 127) + 'V')
    $acceptedCoordinate = "$group255`:$artifact255`:$version255"
    $acceptedParts = Get-GradleGavParts -Coordinate $acceptedCoordinate
    Assert-GavBounds (
      $null -ne $acceptedParts -and
      [string]::Equals([string]$acceptedParts.Group, $group255, [System.StringComparison]::Ordinal) -and
      [string]::Equals([string]$acceptedParts.Artifact, $artifact255, [System.StringComparison]::Ordinal) -and
      [string]::Equals([string]$acceptedParts.Version, $version255, [System.StringComparison]::Ordinal)
    ) '[GAV-BOUND-255] 255-character GAV segments did not preserve exact ordinal identity'

    $auditText = Get-GradleAuditText -Value "$acceptedCoordinate => prefix-$('x' * 600)-tail"
    Assert-GavBounds (
      $auditText.Length -eq 1000 -and
      $auditText.StartsWith("$acceptedCoordinate => [TRUNCATED] ", [System.StringComparison]::Ordinal) -and
      $auditText.EndsWith('-tail', [System.StringComparison]::Ordinal)
    ) '[GAV-AUDIT-ENVELOPE] accepted maximum GAV did not preserve the coordinate inside the 1000-character audit envelope'

    $script:bad = @()
    Add-GradleNonCompliance "$acceptedCoordinate => prefix-$('x' * 600)-tail [GRADLE-PARSE]"
    $auditEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
    Assert-GavBounds (
      $auditEntry.Length -eq 1024 -and
      $auditEntry.StartsWith("[GRADLE] $acceptedCoordinate => [TRUNCATED] ", [System.StringComparison]::Ordinal) -and
      $auditEntry.EndsWith('-tail [GRADLE-PARSE]', [System.StringComparison]::Ordinal)
    ) '[GAV-AUDIT-CATEGORY] accepted maximum GAV lost its exact coordinate or caller-owned category'

    $overlongCases = @(
      @{ Name = 'group'; Coordinate = "$(('g' * 256)):artifact:1.0" },
      @{ Name = 'artifact'; Coordinate = "group:$(('a' * 256)):1.0" },
      @{ Name = 'version'; Coordinate = "group:artifact:$(('v' * 256))" }
    )
    foreach ($overlongCase in $overlongCases) {
      Assert-GavBounds (
        $null -eq (Get-GradleGavParts -Coordinate $overlongCase.Coordinate)
      ) "[GAV-BOUND-$($overlongCase.Name.ToUpperInvariant())] 256-character $($overlongCase.Name) segment was accepted by the shared GAV boundary"
    }

    $cacheRejected = $false
    try {
      [void](Get-GradleCacheCoordinateRoot -GradleUserHome $fixtureRoot -Coordinate $overlongCases[0].Coordinate)
    } catch {
      $cacheRejected = $true
    }
    Assert-GavBounds $cacheRejected '[GAV-CACHE-BOUND] overlong GAV became a cache-coordinate path'

    $cachedPomResult = Get-GradleCachedPomInfo -Coordinate $overlongCases[1].Coordinate -GradleUserHome $fixtureRoot
    Assert-GavBounds (
      $cachedPomResult.State -ceq 'Error' -and
      $cachedPomResult.Detail -ceq '坐标不是具体且安全的 GAV。' -and
      $cachedPomResult.Paths.Count -eq 0
    ) '[GAV-POM-BOUND] overlong GAV reached cache lookup instead of failing at the cached-POM identity boundary'

    $exceptionPath = Join-Path $fixtureRoot 'exceptions.json'
    $exceptionRecord = @{
      coordinate = $overlongCases[2].Coordinate
      license = 'Apache-2.0'
      evidence_url = 'https://example.invalid/gav-bound'
      registered_by = 'gav-bound-test'
      registered_on = '2026-08-20'
    }
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    [System.IO.File]::WriteAllText($exceptionPath, (ConvertTo-Json -InputObject @($exceptionRecord) -Compress), [System.Text.UTF8Encoding]::new($false))
    $exceptionResult = Get-GradleExceptionMap -Path $exceptionPath
    Assert-GavBounds (
      $exceptionResult.Entries.Count -eq 0 -and
      $exceptionResult.Error.StartsWith('[GRADLE-OVERRIDE] 坐标不是具体且安全的 GAV：', [System.StringComparison]::Ordinal)
    ) '[GAV-EXCEPTION-BOUND] overlong GAV became an exception identity'

    $policyExceptionPath = Join-Path $fixtureRoot 'policy-exceptions.json'
    $policyExceptionRecord = @{
      coordinate = $overlongCases[1].Coordinate
      license = 'Apache-2.0'
      evidence_url = 'https://example.invalid/gav-policy-bound'
      registered_by = 'gav-bound-test'
      registered_on = '2026-08-20'
    }
    [System.IO.File]::WriteAllText($policyExceptionPath, (ConvertTo-Json -InputObject @($policyExceptionRecord) -Compress), [System.Text.UTF8Encoding]::new($false))
    $policyResult = Get-GradleLicensePolicyResult -Resolved @([PSCustomObject]@{
      Coordinate = $overlongCases[1].Coordinate
      Configurations = @(':core:testRuntimeClasspath')
    }) -GradleUserHome $fixtureRoot -ExceptionPath $policyExceptionPath
    Assert-GavBounds (
      $policyResult.Findings.Count -eq 0 -and
      @($policyResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1 -and
      @($policyResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE').Count -eq 1
    ) '[GAV-POLICY-BOUND] overlong GAV became a cached-POM or exception-backed finding'

    $graphResult = Get-GradleCoordinatesFromDependencyOutput -Output @("+--- $($overlongCases[0].Coordinate)")
    Assert-GavBounds (
      $graphResult.Coordinates.Count -eq 0 -and
      $graphResult.Errors.Count -eq 1 -and
      $graphResult.Errors[0] -match '\[GRADLE-PARSE\]$'
    ) '[GAV-GRAPH-BOUND] overlong GAV entered the parsed dependency finding set'
  } catch {
    Assert-GavBounds $false "[GAV-BOUND-SETUP] gav-bounds fixture failed: $($_.Exception.Message)"
  } finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
  }

  if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Error $failure -ErrorAction Continue }
    exit 1
  }

  if (-not $SkipMutations) {
    $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
    $gavBoundMutationCases = @(
      @{
        Name = 'segment-length'
        From = '    if ($segment.Length -gt 255) { return $null } # GAV segment length guard'
        To = '    if ($false) { return $null } # GAV segment length guard'
        Expected = '[GAV-BOUND-GROUP]'
      },
      @{
        Name = 'cache-shared-guard'
        From = '  $gav = Get-GradleGavParts -Coordinate $Coordinate # cache coordinate shared GAV guard'
        To = "  `$gav = [PSCustomObject]@{ Group = 'accepted'; Artifact = 'accepted'; Version = 'accepted' } # cache coordinate shared GAV guard"
        Expected = '[GAV-CACHE-BOUND]'
      },
      @{
        Name = 'pom-shared-guard'
        From = '  $gav = Get-GradleGavParts -Coordinate $Coordinate # cached POM shared GAV guard'
        To = "  `$gav = [PSCustomObject]@{ Group = 'accepted'; Artifact = 'accepted'; Version = 'accepted' } # cached POM shared GAV guard"
        Expected = '[GAV-POM-BOUND]'
      },
      @{
        Name = 'exception-shared-guard'
        From = '      if ($null -eq (Get-GradleGavParts -Coordinate $coordinate)) {'
        To = '      if ($false) {'
        Expected = '[GAV-EXCEPTION-BOUND]'
      },
      @{
        Name = 'graph-shared-guard'
        From = '    if ($null -eq (Get-GradleGavParts -Coordinate $resolvedCoordinate)) {'
        To = '    if ($false) {'
        Expected = '[GAV-GRAPH-BOUND]'
      }
    )

    foreach ($mutationCase in $gavBoundMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[GAV-BOUND-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-gav-bounds-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite gav-bounds -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[GAV-BOUND-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(gav-bounds mutations): PASS ($($gavBoundMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(gav-bounds): PASS'
  exit 0
}

if ($Suite -eq 'diagnostics') {
  function Assert-Diagnostics {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  # Each case names the production guard whose deletion must expose the hostile payload.
  $uriCanary = 'DIAG_URI_CANARY'
  $uriText = Get-GradleDiagnosticTail -Output @("ssh://credential-user:$uriCanary@example.invalid/repository") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $uriText -ceq 'ssh://[REDACTED]@example.invalid/repository' -and $uriText -notmatch [regex]::Escape($uriCanary)
  ) '[DIAG-URI] URI userinfo was not redacted'

  $authorizationCanary = 'DIAG_AUTH_CANARY'
  $authorizationText = Get-GradleDiagnosticTail -Output @("X-Authorization: Bearer $authorizationCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $authorizationText -ceq 'X-Authorization: [REDACTED]' -and $authorizationText -notmatch [regex]::Escape($authorizationCanary)
  ) '[DIAG-AUTH] Authorization value was not redacted'

  $credentialCanary = 'DIAG_CREDENTIAL_CANARY'
  $credentialName = '--pass' + 'word'
  $keyText = Get-GradleDiagnosticTail -Output @("$credentialName=$credentialCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $keyText -ceq "$credentialName=[REDACTED]" -and $keyText -notmatch [regex]::Escape($credentialCanary)
  ) '[DIAG-KEY] secret-like key value was not redacted'

  $credentialKeyName = 'credential' + 's'
  $credentialKeyText = Get-GradleDiagnosticTail -Output @("$credentialKeyName=$credentialCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $credentialKeyText -ceq "$credentialKeyName=[REDACTED]" -and $credentialKeyText -notmatch [regex]::Escape($credentialCanary)
  ) '[DIAG-CREDENTIAL-KEY] credential-like key value was not redacted'

  $spaceCredentialCases = @(
    @{ Input = "--password $credentialCanary"; Expected = '--password=[REDACTED]' },
    @{ Input = "password $credentialCanary"; Expected = 'password=[REDACTED]' },
    @{ Input = "token $credentialCanary"; Expected = 'token=[REDACTED]' }
  )
  foreach ($spaceCredentialCase in $spaceCredentialCases) {
    $spaceCredentialText = Get-GradleDiagnosticTail -Output @($spaceCredentialCase.Input) -MaxLines 2 -MaxChars 400
    Assert-Diagnostics (
      $spaceCredentialText -ceq $spaceCredentialCase.Expected -and $spaceCredentialText -notmatch [regex]::Escape($credentialCanary)
    ) "[DIAG-KEY-SPACE] whitespace-delimited secret-like key value was not redacted: $spaceCredentialText"
  }

  $recordCanary = 'DIAG_RECORD_CANARY'
  $recordKeyName = 'pass' + 'word'
  $recordCredentialCases = @(
    @{ Input = "Authorization:`nBearer $recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer prefix`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer prefix`nmiddle`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "$recordKeyName=`r`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "$recordKeyName=prefix`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "$recordKeyName=prefix`nmiddle`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "ssh://user:`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://user:prefix`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://user:prefix`nmiddle`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://first@second:$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' }
  )
  foreach ($recordCase in $recordCredentialCases) {
    $recordText = Get-GradleDiagnosticTail -Output @($recordCase.Input) -MaxLines 3 -MaxChars 400
    Assert-Diagnostics (
      $recordText -ceq $recordCase.Expected -and $recordText -notmatch [regex]::Escape($recordCanary)
    ) "[DIAG-RECORD-CREDENTIAL] composed credential record leaked or split before redaction: $recordText"
  }
  $ordinaryRecordText = Get-GradleDiagnosticTail -Output @("https://user`nordinary@example.invalid/repository") -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $ordinaryRecordText -ceq 'https://user ordinary@example.invalid/repository'
  ) "[DIAG-RECORD-BOUNDARY] ordinary next line was consumed as URI userinfo: $ordinaryRecordText"

  $windowsPathCases = @(
    @{ Input = 'failed at C:\Users\alice\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ Input = 'failed at C:\Users\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ Input = 'failed at file:///C:/Users/alice/private/pom.xml'; Expected = 'failed at file:///[USER_HOME]/private/pom.xml' },
    @{ Input = 'failed at C:\Users\Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at C:\Users\Alice Smith: denied'; Expected = 'failed at [USER_HOME]: denied' },
    @{ Input = 'failed at C:\Users\Alice Smith|denied'; Expected = 'failed at [USER_HOME]|denied' },
    @{ Input = 'failed at file:///C:/Users/Alice Smith'; Expected = 'failed at file:///[USER_HOME]' }
  )
  foreach ($pathCase in $windowsPathCases) {
    $windowsPathText = Get-GradleDiagnosticTail -Output @($pathCase.Input) -MaxLines 2 -MaxChars 400
    Assert-Diagnostics (
      $windowsPathText -ceq $pathCase.Expected -and $windowsPathText -notmatch '(?i)C:[\\/]Users[\\/]alice'
    ) "[DIAG-WINDOWS-HOME] Windows user directory was not redacted: $windowsPathText"
  }

  $unixPathCases = @(
    @{ Input = 'failed at /home/alice/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/Alice Smith/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/alice: permission denied'; Expected = 'failed at [USER_HOME]: permission denied' },
    @{ Input = 'failed at /Users/alice/Library/cache/pom.xml'; Expected = 'failed at [USER_HOME]/Library/cache/pom.xml' },
    @{ Input = 'failed at /root/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at file:///home/alice/.gradle/caches/pom.xml'; Expected = 'failed at file://[USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at /Users/Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at /Users/Alice Smith|denied'; Expected = 'failed at [USER_HOME]|denied' },
    @{ Input = 'failed at file:///home/Alice Smith'; Expected = 'failed at file://[USER_HOME]' },
    @{ Input = 'failed at file:///Users/Alice Smith'; Expected = 'failed at file://[USER_HOME]' }
  )
  foreach ($pathCase in $unixPathCases) {
    $pathText = Get-GradleDiagnosticTail -Output @($pathCase.Input) -MaxLines 2 -MaxChars 400
    Assert-Diagnostics ($pathText -ceq $pathCase.Expected) "[DIAG-UNIX-HOME] Unix user directory was not redacted: $pathText"
  }

  $configuredHomeCases = @(
    @{ EnvName = 'USERPROFILE'; Home = 'D:\Profiles\Alice Smith'; Input = 'failed at D:\Profiles\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ EnvName = 'USERPROFILE'; Home = '\\server\profiles\Alice Smith'; Input = 'failed at \\server\profiles\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ EnvName = 'HOME'; Home = '/var/home/Alice Smith'; Input = 'failed at /var/home/Alice Smith/private/pom.xml'; Expected = 'failed at [USER_HOME]/private/pom.xml' },
    @{ EnvName = 'HOME'; Home = '/srv/users/Alice Smith'; Input = 'failed at file:///srv/users/Alice Smith/private/pom.xml'; Expected = 'failed at file://[USER_HOME]/private/pom.xml' }
  )
  foreach ($configuredHomeCase in $configuredHomeCases) {
    $oldConfiguredHome = [Environment]::GetEnvironmentVariable($configuredHomeCase.EnvName)
    try {
      [Environment]::SetEnvironmentVariable($configuredHomeCase.EnvName, $configuredHomeCase.Home)
      $configuredHomeText = Get-GradleDiagnosticTail -Output @($configuredHomeCase.Input) -MaxLines 2 -MaxChars 400
      Assert-Diagnostics (
        $configuredHomeText -ceq $configuredHomeCase.Expected
      ) "[DIAG-CONFIGURED-HOME] configured user directory was not redacted: $configuredHomeText"
    } finally {
      [Environment]::SetEnvironmentVariable($configuredHomeCase.EnvName, $oldConfiguredHome)
    }
  }

  $formatControl = [char]0x202E
  $controlText = Get-GradleDiagnosticTail -Output @("prefix-$formatControl-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $controlText -ceq 'prefix- -suffix' -and -not [regex]::IsMatch($controlText, '[\p{Cc}\p{Cf}]')
  ) '[DIAG-CONTROL] control/format character survived diagnostics'

  $supplementaryFormat = [System.Text.Rune]::new(0x1BCA0).ToString()
  $supplementaryFormatText = Get-GradleDiagnosticTail -Output @("prefix-$supplementaryFormat-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $supplementaryFormatText -ceq 'prefix- -suffix'
  ) '[DIAG-SCALAR-SUPPLEMENTARY] supplementary format scalar survived diagnostics'

  $ordinaryEmoji = [System.Text.Rune]::new(0x1F600).ToString()
  $ordinaryEmojiText = Get-GradleDiagnosticTail -Output @("prefix-$ordinaryEmoji-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $ordinaryEmojiText -ceq "prefix-$ordinaryEmoji-suffix"
  ) '[DIAG-SCALAR-PRESERVE] ordinary supplementary scalar changed in diagnostics'

  foreach ($malformedCase in @(
    @{ Id = 'high'; Value = "prefix-$([char]0xD800)-suffix" },
    @{ Id = 'low'; Value = "prefix-$([char]0xDC00)-suffix" }
  )) {
    $malformedRejected = $false
    try {
      $null = Get-GradleDiagnosticTail -Output @($malformedCase.Value) -MaxLines 2 -MaxChars 400
    } catch {
      $malformedRejected = $_.Exception.Message -match '\[UNICODE-SCALAR-MALFORMED\]'
    }
    Assert-Diagnostics $malformedRejected "[DIAG-SCALAR-MALFORMED-$($malformedCase.Id.ToUpperInvariant())] malformed UTF-16 was accepted by diagnostics"
  }

  if (-not $SkipMutations) {
    $scalarTargetCount = 0
    $supplementaryTargetCount = 0
    $scalarTargetFailure = $null
    for ($codePoint = 0; $codePoint -le 0x10FFFF; $codePoint++) {
      if ($codePoint -ge 0xD800 -and $codePoint -le 0xDFFF) { continue }
      $rune = [System.Text.Rune]::new($codePoint)
      $category = [System.Text.Rune]::GetUnicodeCategory($rune)
      if ($category -ne [System.Globalization.UnicodeCategory]::Control -and
          $category -ne [System.Globalization.UnicodeCategory]::Format) { continue }
      $scalarTargetCount++
      if ($codePoint -gt 0xFFFF) { $supplementaryTargetCount++ }
      $actual = Get-GradleDiagnosticTail -Output @("L$($rune.ToString())R") -MaxLines 2 -MaxChars 400
      if ($actual -cne 'L R') {
        $scalarTargetFailure = ('U+{0:X}' -f $codePoint)
        break
      }
    }
    Assert-Diagnostics (
      $scalarTargetCount -gt 0 -and $supplementaryTargetCount -gt 0 -and $null -eq $scalarTargetFailure
    ) "[DIAG-SCALAR-EXHAUSTIVE] Cc/Cf scalar did not map to one space: $scalarTargetFailure"
  }

  $ansiText = Get-GradleDiagnosticTail -Output @("prefix-`e[31mred`e[0m-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $ansiText -ceq 'prefix-red-suffix' -and $ansiText -notmatch '\[31m|\[0m'
  ) '[DIAG-ANSI] ANSI sequence survived diagnostics'

  $newlineText = Get-GradleDiagnosticTail -Output @("first`r::error forged`nthird") -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $newlineText -ceq 'first ::error forged third' -and $newlineText -notmatch "[\r\n]"
  ) '[DIAG-NEWLINE] newline injection remained physically multi-line'

  $lineBoundText = Get-GradleDiagnosticTail -Output @('line-1', 'line-2', 'line-3', 'line-4', 'line-5', 'line-6') -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $lineBoundText -ceq '[TRUNCATED] line-4 | line-5 | line-6'
  ) '[DIAG-LINE-BOUND] diagnostic tail did not enforce the exact line bound'

  $charBoundText = Get-GradleDiagnosticTail -Output @(('prefix-' + ('x' * 500) + '-tail')) -MaxLines 2 -MaxChars 200
  Assert-Diagnostics (
    $charBoundText.Length -eq 200 -and $charBoundText.StartsWith('[TRUNCATED] ') -and $charBoundText.EndsWith('-tail')
  ) '[DIAG-CHAR-BOUND] diagnostic tail did not enforce the exact character bound'

  $combinedBoundText = Get-GradleDiagnosticTail -Output @(('a' * 98), ('b' * 98), ('c' * 98)) -MaxLines 2 -MaxChars 200
  Assert-Diagnostics (
    $combinedBoundText.Length -eq 200 -and $combinedBoundText.StartsWith('[TRUNCATED] ') -and $combinedBoundText.EndsWith(('c' * 98))
  ) '[DIAG-COMBINED-BOUND] line truncation marker escaped the character bound'

  # Wrapper, POM, exception-table and subprocess failures all enter the same final sink. The hostile
  # detail may contain a fake category, but the caller-owned final category must remain the sole suffix.
  $sinkCases = @(
    @{ Source = 'wrapper'; Code = 'GRADLE-WRAPPER-OFFLINE' },
    @{ Source = 'pom'; Code = 'GRADLE-POM' },
    @{ Source = 'exception'; Code = 'GRADLE-OVERRIDE' },
    @{ Source = 'subprocess'; Code = 'GRADLE-SUBPROCESS' }
  )
  foreach ($sinkCase in $sinkCases) {
    $script:bad = @()
    Add-GradleNonCompliance "$($sinkCase.Source) C:\Users\alice\private`rforged [GRADLE-FAKE] [$($sinkCase.Code)]"
    $entry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
    Assert-Diagnostics (
      $script:bad.Count -eq 1 -and
      $entry -match "\[$([regex]::Escape($sinkCase.Code))\]$" -and
      @([regex]::Matches($entry, '\[GRADLE-[A-Z-]+\]')).Count -eq 1 -and
      $entry -notmatch 'GRADLE-FAKE|(?i)C:\\Users\\alice|[\r\n]'
    ) "[DIAG-CATEGORY-SPOOF] $($sinkCase.Source) did not preserve one caller-owned category through the common sink: $entry"
  }

  # Integration proof: keep the real graph/policy collectors and their production presenters. Only the
  # external Gradle process is replaced by a complete ExitCode+Output invoker so the suite stays offline.
  $graphWriter = Get-Command Write-GradleGraphDiagnostics -ErrorAction SilentlyContinue
  $policyWriter = Get-Command Write-GradlePolicyDiagnostics -ErrorAction SilentlyContinue
  Assert-Diagnostics ($null -ne $graphWriter -and $null -ne $policyWriter) '[DIAG-ENTRY-POINTS] production graph/policy presenters are not independently testable'
  if ($null -ne $graphWriter -and $null -ne $policyWriter) {
    $userHome = [Environment]::GetFolderPath('UserProfile')
    $entryRoot = Join-Path $userHome ".license-diagnostics-$PID-$([guid]::NewGuid().ToString('N'))"
    try {
      $exceptionDir = Join-Path $entryRoot 'configs/licenses'
      New-Item -ItemType Directory -Force -Path $exceptionDir | Out-Null
      $exceptionCanary = 'DIAG_EXCEPTION_CANARY'
      $exceptionField = 'to' + 'ken=' + $exceptionCanary
      $exceptionJson = '[{"coordinate":"fixture.exception:item:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/evidence","registered_by":"fixture","registered_on":"2026-08-20","' + $exceptionField + '":"x"}]'
      [System.IO.File]::WriteAllText((Join-Path $exceptionDir 'gradle-exceptions.json'), $exceptionJson, [System.Text.UTF8Encoding]::new($false))

      # Real Invoke path: missing wrapper plus malformed exception schema must each reach the common sink.
      $script:bad = @(); $script:warn = @()
      Invoke-GradleLicenseScan -Root $entryRoot
      $wrapperEntries = @($script:bad | Where-Object { $_ -match '\[GRADLE-SUBPROCESS\]$' })
      $exceptionEntries = @($script:bad | Where-Object { $_ -match '\[GRADLE-OVERRIDE\]$' })
      $entryText = $script:bad -join "`n"
      Assert-Diagnostics (
        $wrapperEntries.Count -eq 1 -and $exceptionEntries.Count -eq 1 -and
        $entryText -notmatch [regex]::Escape($userHome) -and
        $entryText -notmatch [regex]::Escape($exceptionCanary)
      ) "[DIAG-ENTRY-WRAPPER-EXCEPTION] real Invoke path bypassed redaction/category preservation: $entryText"

      # Real POM policy path: an external license name containing a secret-like value remains unknown,
      # but its coordinate/code survive while the value is redacted.
      $pomCoordinate = 'fixture.diagnostics:pom-entry:1.0'
      $gradleHome = Join-Path $entryRoot 'gradle-home'
      $pomDir = Join-Path (Get-GradleCacheCoordinateRoot -GradleUserHome $gradleHome -Coordinate $pomCoordinate) 'fixture-hash'
      New-Item -ItemType Directory -Force -Path $pomDir | Out-Null
      $pomCanary = 'DIAG_POM_CANARY'
      $pomLicense = ('pass' + 'word=' + $pomCanary)
      $pomXml = '<project><modelVersion>4.0.0</modelVersion><groupId>fixture.diagnostics</groupId><artifactId>pom-entry</artifactId><version>1.0</version><licenses><license><name>' + $pomLicense + '</name></license></licenses></project>'
      [System.IO.File]::WriteAllText((Join-Path $pomDir 'pom-entry-1.0.pom'), $pomXml, [System.Text.UTF8Encoding]::new($false))
      $pomResolved = @([PSCustomObject]@{ Coordinate = $pomCoordinate; Configurations = @(':core:testRuntimeClasspath') })
      $pomPolicy = Get-GradleLicensePolicyResult -Resolved $pomResolved -GradleUserHome $gradleHome -ExceptionPath (Join-Path $entryRoot 'missing-exceptions.json')
      $script:bad = @(); $script:warn = @()
      Write-GradlePolicyDiagnostics -Policy $pomPolicy -Resolved $pomResolved
      $pomEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
      Assert-Diagnostics (
        $script:bad.Count -eq 1 -and $pomEntry.StartsWith("[GRADLE] $pomCoordinate => ") -and
        $pomEntry -match '\[GRADLE-UNKNOWN\]$' -and $pomEntry -notmatch [regex]::Escape($pomCanary)
      ) "[DIAG-ENTRY-POM] real POM policy path bypassed the common sink or changed category: $pomEntry"

      # Real graph collector path with only the slow child process replaced.
      $androidRoot = Join-Path $entryRoot 'android'
      $wrapperDir = Join-Path $androidRoot 'gradle/wrapper'
      $distributionDir = Join-Path $gradleHome 'wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6'
      $distributionRoot = Join-Path $distributionDir 'gradle-9.7.0'
      $nativeArtifactRoot = Join-Path $gradleHome 'caches/modules-2/files-2.1/fixture.group/fixture-artifact/1.0/fixture-hash'
      $nativeMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.107'
      foreach ($directory in @($wrapperDir, (Join-Path $distributionRoot 'lib'), (Join-Path $distributionRoot 'bin'), $nativeArtifactRoot, $nativeMetadataRoot)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
      }
      Set-Content -LiteralPath (Join-Path $wrapperDir 'gradle-wrapper.properties') -Encoding utf8 -Value 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip'
      foreach ($file in @(
        (Join-Path $androidRoot 'gradlew.bat'),
        (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok'),
        (Join-Path $distributionRoot 'lib/gradle-launcher-9.7.0.jar'),
        (Join-Path $distributionRoot 'bin/gradle'),
        (Join-Path $distributionRoot 'bin/gradle.bat'),
        (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom'),
        (Join-Path $nativeMetadataRoot 'module-metadata.bin')
      )) { Set-Content -LiteralPath $file -Encoding utf8 -Value 'fixture' }
      $subprocessCanary = 'DIAG_SUBPROCESS_CANARY'
      $failureInvoker = {
        param([string]$Command, [string[]]$Arguments)
        [PSCustomObject]@{
          ExitCode = 42
          Output = @("ssh://user:$subprocessCanary@example.invalid/repo", "C:\Users\alice\private`r::error forged")
        }
      }.GetNewClosure()
      $graphResult = Get-GradleResolvedGraphs -Root $entryRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $failureInvoker
      $script:bad = @(); $script:warn = @()
      Write-GradleGraphDiagnostics -Errors $graphResult.Errors -DecodeEscapedNewlines $false
      $subprocessText = $script:bad -join "`n"
      Assert-Diagnostics (
        $graphResult.Errors.Count -eq 4 -and $script:bad.Count -eq 4 -and
        @($script:bad | Where-Object { $_ -match '退出 42；' -and $_ -match '\[GRADLE-SUBPROCESS\]$' }).Count -eq 4 -and
        $subprocessText -notmatch [regex]::Escape($subprocessCanary) -and
        $subprocessText -notmatch '(?i)C:\\Users\\alice|[\r]'
      ) "[DIAG-ENTRY-SUBPROCESS] real graph subprocess path bypassed redaction or changed error semantics: $subprocessText"
    } finally {
      if (Test-Path -LiteralPath $entryRoot) { Remove-Item -LiteralPath $entryRoot -Recurse -Force }
    }
  }

  $script:bad = @()
  $missingCategoryRejected = $false
  try { Add-GradleNonCompliance 'detail without a caller-owned category' } catch { $missingCategoryRejected = $true }
  Assert-Diagnostics ($missingCategoryRejected -and $script:bad.Count -eq 0) '[DIAG-CATEGORY-REQUIRED] uncategorized diagnostic entered the final sink'

  $script:bad = @()
  $longCoordinate = 'fixture.group:fixture-artifact:1.2.3'
  Add-GradleNonCompliance "$longCoordinate => prefix-$('x' * 1400)-tail [GRADLE-POM]"
  $longEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $script:bad.Count -eq 1 -and
    $longEntry.StartsWith("[GRADLE] $longCoordinate => [TRUNCATED] ") -and
    $longEntry.EndsWith('-tail [GRADLE-POM]') -and
    $longEntry.Length -le 1070
  ) "[DIAG-GAV-PRESERVATION] bounded diagnostic lost exact GAV/category or exceeded the sink bound: $longEntry"

  $script:bad = @()
  $boundedCoordinate = "$(('g' * 500)):artifact:1.0"
  Add-GradleNonCompliance "$boundedCoordinate => prefix-$('x' * 1400)-tail [GRADLE-POM]"
  $boundedCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $boundedCoordinateEntry.StartsWith("[GRADLE] $boundedCoordinate => [TRUNCATED] ") -and
    $boundedCoordinateEntry.EndsWith('-tail [GRADLE-POM]') -and
    $boundedCoordinateEntry.Length -le 1040
  ) "[DIAG-GAV-TOTAL-BOUND] coordinate escaped the diagnostic character budget: $($boundedCoordinateEntry.Length)"

  $script:bad = @()
  $oversizedCoordinate = "$(('g' * 3000)):artifact:1.0"
  Add-GradleNonCompliance "$oversizedCoordinate => tail [GRADLE-PARSE]"
  $oversizedCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $oversizedCoordinateEntry.Length -le 1040 -and $oversizedCoordinateEntry.EndsWith('tail [GRADLE-PARSE]')
  ) "[DIAG-GAV-TOTAL-BOUND] oversized coordinate produced an unbounded diagnostic: $($oversizedCoordinateEntry.Length)"

  $script:bad = @()
  $multilineCoordinate = 'fixture.multiline:artifact:1.0'
  Add-GradleNonCompliance "$multilineCoordinate => first`n$('x' * 1200) tail [GRADLE-FAKE] [GRADLE-POM]"
  $multilineCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $multilineCoordinateEntry.StartsWith("[GRADLE] $multilineCoordinate => [TRUNCATED] ", [System.StringComparison]::Ordinal) -and
    $multilineCoordinateEntry.EndsWith(' tail [REDACTED-CATEGORY] [GRADLE-POM]', [System.StringComparison]::Ordinal) -and
    $multilineCoordinateEntry -notmatch '[\r\n]'
  ) "[DIAG-GAV-NEWLINE] multiline detail lost exact GAV/category preservation: $multilineCoordinateEntry"

  if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Error $failure -ErrorAction Continue }
    exit 1
  }

  if (-not $SkipMutations) {
    $source = Get-Content -LiteralPath $ScannerPath -Raw
    $diagnosticMutationCases = @(
      @{
        Name = 'record-credential-redaction'
        From = '    $raw = Protect-GradleDiagnosticRecord -Value $raw # diagnostic record credential boundary'
        To = '    $raw = $raw # diagnostic record credential boundary'
        Expected = '[DIAG-RECORD-CREDENTIAL]'
      },
      @{
        Name = 'whitespace-credential-redaction'
        From = '    $Value = [regex]::Replace($Value, ''(?is)(?<lead>^|[^A-Za-z0-9_.-])["'''']?(?<key>(?:(?:--?|/|-P))?[A-Za-z0-9_.-]*(?:token|password|passwd|secret|credential(?:s)?|api[-_]?key|access[-_]?key)[A-Za-z0-9_.-]*)["'''']?[ \t]+.*'', ''${lead}${key}=[REDACTED]'') # diagnostic whitespace credential redaction'
        To = '    $Value = $Value # diagnostic whitespace credential redaction'
        Expected = '[DIAG-KEY-SPACE]'
      },
      @{
        Name = 'record-uri-boundary'
        From = '    $Value = [regex]::Replace($Value, ''(?is)(?<scheme>[A-Za-z][A-Za-z0-9+.-]*://)[^/\r\n]*:[^/]*@'', ''${scheme}[REDACTED]@'') # diagnostic multiline URI boundary'
        To = '    $Value = [regex]::Replace($Value, ''(?is)(?<scheme>[A-Za-z][A-Za-z0-9+.-]*://)[^/]*@'', ''${scheme}[REDACTED]@'') # diagnostic multiline URI boundary'
        Expected = '[DIAG-RECORD-BOUNDARY]'
      },
      @{
        Name = 'windows-user-home'
        From = '    $line = [regex]::Replace($line, ''(?i)(?<![A-Za-z0-9])(?:[A-Za-z]:)[\\/]+Users[\\/]+(?:[^\\/|]+(?=[\\/])|[^\\/|:;,)\]\r\n]+)(?=[\\/]|[|:;,)\]]|$)'', ''[USER_HOME]'') # diagnostic Windows user-home redaction'
        To = '    $line = $line # diagnostic Windows user-home redaction'
        Expected = '[DIAG-WINDOWS-HOME]'
      },
      @{
        Name = 'unix-user-home'
        From = '    $line = [regex]::Replace($line, ''(?i)(?<![A-Za-z0-9:])/(?:home/(?:[^/|]+(?=/)|[^/|:;,)\]\r\n]+)|Users/(?:[^/|]+(?=/)|[^/|:;,)\]\r\n]+)|root)(?=/|[|:;,)\]]|$)'', ''[USER_HOME]'') # diagnostic Unix user-home redaction'
        To = '    $line = $line # diagnostic Unix user-home redaction'
        Expected = '[DIAG-UNIX-HOME]'
      },
      @{
        Name = 'configured-user-home'
        From = '      $line = [regex]::Replace($line, [regex]::Escape($homeVariant) + ''(?=[\\/]|[|:;,)\]\s]|$)'', ''[USER_HOME]'', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase) # diagnostic configured user-home redaction'
        To = '      $line = $line # diagnostic configured user-home redaction'
        Expected = '[DIAG-CONFIGURED-HOME]'
      },
      @{
        Name = 'ansi-redaction'
        From = '    $line = [regex]::Replace($raw, "`e\[[0-?]*[ -/]*[@-~]", '''') # diagnostic ANSI redaction'
        To = '    $line = $raw # diagnostic ANSI redaction'
        Expected = '[DIAG-ANSI]'
      },
      @{
        Name = 'scalar-control-format-normalization'
        From = '    $line = ConvertTo-ScaffoldControlFormatSpaces $line # diagnostic scalar control/format normalization'
        To = '    $line = $line # diagnostic scalar control/format normalization'
        Expected = '[DIAG-SCALAR-SUPPLEMENTARY]'
      },
      @{
        Name = 'line-bound'
        From = '  $tail = (@($sanitized | Select-Object -Last $MaxLines) -join '' | '') # diagnostic line bound'
        To = '  $tail = (@($sanitized) -join '' | '') # diagnostic line bound'
        Expected = '[DIAG-LINE-BOUND]'
      },
      @{
        Name = 'character-bound'
        From = '  if ($tail.Length -gt $payloadMax) { # diagnostic character bound'
        To = '  if ($false) { # diagnostic character bound'
        Expected = '[DIAG-CHAR-BOUND]'
      },
      @{
        Name = 'marker-inclusive-bound'
        From = '  $payloadMax = if ($truncated) { $MaxChars - $marker.Length } else { $MaxChars } # diagnostic marker-inclusive bound'
        To = '  $payloadMax = $MaxChars # diagnostic marker-inclusive bound'
        Expected = '[DIAG-COMBINED-BOUND]'
      },
      @{
        Name = 'category-spoof'
        From = '  $safeDetail = [regex]::Replace($safeDetail, ''(?i)\[GRADLE-[A-Z-]+\]'', ''[REDACTED-CATEGORY]'') # diagnostic category spoof guard'
        To = '  $safeDetail = $safeDetail # diagnostic category spoof guard'
        Expected = '[DIAG-CATEGORY-SPOOF]'
      },
      @{
        Name = 'category-required'
        From = '  if (-not $categoryMatch.Success) { throw ''Gradle diagnostic missing caller-owned [GRADLE-*] category.'' } # diagnostic category required guard'
        To = '  if ($false) { throw ''Gradle diagnostic missing caller-owned [GRADLE-*] category.'' } # diagnostic category required guard'
        Expected = '[DIAG-CATEGORY-REQUIRED]'
      },
      @{
        Name = 'exact-gav-preservation'
        From = '    $coordinatePrefix = "$($coordinateMatch.Groups[''coordinate''].Value) => " # diagnostic exact-GAV preservation'
        To = '    $coordinatePrefix = '''' # diagnostic exact-GAV preservation'
        Expected = '[DIAG-GAV-PRESERVATION]'
      },
      @{
        Name = 'gav-total-bound'
        From = '    $detailBudget = [Math]::Max($minimumDiagnosticChars, $auditMaxChars - $coordinatePrefix.Length) # diagnostic coordinate-inclusive bound'
        To = '    $detailBudget = $auditMaxChars # diagnostic coordinate-inclusive bound'
        Expected = '[DIAG-GAV-TOTAL-BOUND]'
      },
      @{
        Name = 'gav-oversized-coordinate-bound'
        From = '    if ($coordinatePrefix.Length -le ($auditMaxChars - $minimumDiagnosticChars)) { # diagnostic oversized-coordinate bound'
        To = '    if ($true) { # diagnostic oversized-coordinate bound'
        Expected = '[DIAG-GAV-TOTAL-BOUND]'
      },
      @{
        Name = 'gav-multiline-preservation'
        From = '  $coordinateMatch = [regex]::Match($Value, ''^(?<coordinate>[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)\s*=>\s*(?<rest>.*)$'', [System.Text.RegularExpressions.RegexOptions]::Singleline) # diagnostic multiline-GAV preservation'
        To = '  $coordinateMatch = [regex]::Match($Value, ''^(?<coordinate>[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)\s*=>\s*(?<rest>.*)$'') # diagnostic multiline-GAV preservation'
        Expected = '[DIAG-GAV-NEWLINE]'
      },
      @{
        Name = 'graph-presenter-route'
        From = '  Write-GradleGraphDiagnostics -Errors $graph.Errors -DecodeEscapedNewlines:(-not $IsWindows) # unified graph diagnostic route'
        To = '  $null = $graph.Errors # unified graph diagnostic route'
        Expected = '[DIAG-ENTRY-WRAPPER-EXCEPTION]'
      },
      @{
        Name = 'policy-presenter-route'
        From = '  Write-GradlePolicyDiagnostics -Policy $policy -Resolved $graph.Resolved # unified policy diagnostic route'
        To = '  $null = $policy # unified policy diagnostic route'
        Expected = '[DIAG-ENTRY-WRAPPER-EXCEPTION]'
      }
    )

    foreach ($mutationCase in $diagnosticMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[DIAGNOSTICS-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-diagnostics-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite diagnostics -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[DIAGNOSTICS-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(diagnostics mutations): PASS ($($diagnosticMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(diagnostics): PASS'
  exit 0
}

if ($Suite -eq 'policy') {
  function Assert-Policy {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  $policyRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-policy-$PID-$([guid]::NewGuid().ToString('N'))"
  $policyGradleHome = Join-Path $policyRoot 'gradle-home'
  $policyExceptionPath = Join-Path $policyRoot 'exceptions.json'

  function Write-PolicyPom {
    param(
      [Parameter(Mandatory)][string]$Coordinate,
      [Parameter(Mandatory)][string]$Xml,
      [string]$Hash = 'fixture-hash'
    )
    $gav = Get-GradleGavParts -Coordinate $Coordinate
    $coordinateRoot = Get-GradleCacheCoordinateRoot -GradleUserHome $policyGradleHome -Coordinate $Coordinate
    $hashRoot = Join-Path $coordinateRoot $Hash
    New-Item -ItemType Directory -Force -Path $hashRoot | Out-Null
    $pomPath = Join-Path $hashRoot "$($gav.Artifact)-$($gav.Version).pom"
    [System.IO.File]::WriteAllText($pomPath, $Xml, [System.Text.UTF8Encoding]::new($false))
    return $pomPath
  }

  function Set-PolicyExceptions([Parameter(Mandatory)][string]$Json) {
    [System.IO.File]::WriteAllText($policyExceptionPath, $Json, [System.Text.UTF8Encoding]::new($false))
  }

  function New-PolicyResolved([Parameter(Mandatory)][string]$Coordinate) {
    return [PSCustomObject]@{ Coordinate = $Coordinate; Configurations = @(':core:testRuntimeClasspath') }
  }

  function Invoke-PolicyFixture([Parameter(Mandatory)][string[]]$Coordinates) {
    $resolved = @($Coordinates | ForEach-Object { New-PolicyResolved -Coordinate $_ })
    return Get-GradleLicensePolicyResult -Resolved $resolved -GradleUserHome $policyGradleHome -ExceptionPath $policyExceptionPath
  }

  try {
    New-Item -ItemType Directory -Force -Path $policyRoot | Out-Null
    Set-PolicyExceptions -Json '[]'

    $multiCoordinate = 'fixture.policy:multi:1.0'
    [void](Write-PolicyPom -Coordinate $multiCoordinate -Xml @'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>fixture.policy</groupId><artifactId>multi</artifactId><version>1.0</version>
  <licenses>
    <license><name>Apache-2.0</name></license>
    <license><name>LGPL-2.1</name></license>
  </licenses>
</project>
'@)
    $multiResult = Invoke-PolicyFixture -Coordinates @($multiCoordinate)
    $multiLicenses = @($multiResult.Findings | ForEach-Object DeclaredLicense)
    Assert-Policy (
      $multiResult.Violations.Count -eq 0 -and
      ($multiLicenses -join ',') -ceq 'Apache-2.0,LGPL-2.1' -and
      $multiResult.Warnings.Count -eq 1 -and
      $multiResult.Warnings[0].DeclaredLicense -ceq 'LGPL-2.1' -and
      ($multiResult.Findings[0].Configurations -join ',') -ceq ':core:testRuntimeClasspath'
    ) '[POLICY-POM-MULTI] valid multi-license POM did not preserve both decisions/configuration'

    $classificationCases = @(
      @{ License = 'Apache-2.0'; Expected = 'permissive' },
      @{ License = 'LGPL-2.1'; Expected = 'yellow' },
      @{ License = 'EPL-1.0'; Expected = 'forbidden' },
      @{ License = 'GPL-3.0'; Expected = 'plain-gpl' },
      @{ License = 'Mystery Apache License'; Expected = 'unknown' }
    )
    foreach ($case in $classificationCases) {
      Assert-Policy (
        (Get-GradleLicenseClassification -License $case.License) -ceq $case.Expected
      ) "[POLICY-CLASSIFICATION] $($case.License) was not $($case.Expected)"
    }

    $dtdCoordinate = 'fixture.policy:dtd:1.0'
    [void](Write-PolicyPom -Coordinate $dtdCoordinate -Xml @'
<!DOCTYPE project [<!ENTITY policyLicense "Apache-2.0">]>
<project><groupId>fixture.policy</groupId><artifactId>dtd</artifactId><version>1.0</version><licenses><license><name>&policyLicense;</name></license></licenses></project>
'@)
    $dtdResult = Invoke-PolicyFixture -Coordinates @($dtdCoordinate)
    Assert-Policy (
      $dtdResult.Findings.Count -eq 0 -and @($dtdResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-DTD] DTD-bearing POM was not rejected as GRADLE-POM'

    $mismatchCoordinate = 'fixture.policy:mismatch:1.0'
    [void](Write-PolicyPom -Coordinate $mismatchCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>mismatch</artifactId><version>2.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>')
    $mismatchResult = Invoke-PolicyFixture -Coordinates @($mismatchCoordinate)
    Assert-Policy (
      @($mismatchResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-GAV] POM self-declared GAV mismatch was not rejected'

    $blankNameCoordinate = 'fixture.policy:blank-name:1.0'
    [void](Write-PolicyPom -Coordinate $blankNameCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>blank-name</artifactId><version>1.0</version><licenses><license><name> </name></license></licenses></project>')
    $blankNameResult = Invoke-PolicyFixture -Coordinates @($blankNameCoordinate)
    Assert-Policy (
      @($blankNameResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-LICENSE-NAME] blank declared license name was not rejected'

    $duplicatePomCases = @(
      @{ Id = 'group'; Coordinate = 'fixture.policy:duplicate-group:1.0'; Xml = '<project><groupId>fixture.policy</groupId><groupId>other</groupId><artifactId>duplicate-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'artifact'; Coordinate = 'fixture.policy:duplicate-artifact:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-artifact</artifactId><artifactId>other</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'version'; Coordinate = 'fixture.policy:duplicate-version:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-version</artifactId><version>1.0</version><version>2.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent'; Coordinate = 'fixture.policy:duplicate-parent:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent-a</artifactId><version>1.0</version></parent><parent><groupId>fixture.policy</groupId><artifactId>parent-b</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-group'; Coordinate = 'fixture.policy:duplicate-parent-group:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><groupId>other</groupId><artifactId>parent</artifactId><version>1.0</version></parent><artifactId>duplicate-parent-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-artifact'; Coordinate = 'fixture.policy:duplicate-parent-artifact:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId><artifactId>other</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-version'; Coordinate = 'fixture.policy:duplicate-parent-version:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId><version>1.0</version><version>2.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent-version</artifactId><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'licenses-container'; Coordinate = 'fixture.policy:duplicate-licenses-container:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-licenses-container</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses><licenses><license><name>MIT</name></license></licenses></project>' },
      @{ Id = 'license-name'; Coordinate = 'fixture.policy:duplicate-license-name:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-license-name</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name><name>EPL-1.0</name></license></licenses></project>' }
    )
    foreach ($duplicatePom in $duplicatePomCases) {
      [void](Write-PolicyPom -Coordinate $duplicatePom.Coordinate -Xml $duplicatePom.Xml)
      $duplicatePomResult = Invoke-PolicyFixture -Coordinates @($duplicatePom.Coordinate)
      Assert-Policy (
        @($duplicatePomResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
      ) "[POLICY-POM-SINGLETON-$($duplicatePom.Id.ToUpperInvariant())] repeated singleton element was accepted"
    }

    # 同一 GAV 在 Gradle 缓存里存在**两份** POM 副本（两个仓库、或 POM 被重新发布）是正常状态，而两条
    # 守卫都落在这里：① 副本里混有「未声明许可」与「已声明许可」两种，② 副本之间声明了互相冲突的许可证。
    # 两者都必须是 Error/GRADLE-POM——尤其 ① 不能回落到 Missing/MissingLicense 那条**能被豁免表回退记录
    # 洗白**的分支：一份 licence-less 副本 + 一份声明了 copyleft 的副本，会让人工回退把该 copyleft 坐标
    # 静默放行。docs/LICENSE-POLICY.md §3.2 明写这两种情形「一律不可由任何例外覆盖」。
    # 这两条守卫此前的唯一夹具（17cc(scanner/pom-mixed) / (scanner/pom-conflict)）随 selftest 的 -1434 行
    # 一起被删（实测：各改成 `if ($false)` 后 policy 套件仍全绿），故在此按副本 hash 目录重建。
    $multiCopyCases = @(
      @{
        Id = 'mixed'
        Coordinate = 'fixture.policy:multicopy-mixed:1.0'
        Copies = @(
          @{ Hash = 'no-license-copy'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>multicopy-mixed</artifactId><version>1.0</version></project>' },
          @{ Hash = 'licensed-copy'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>multicopy-mixed</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' }
        )
      },
      @{
        Id = 'conflict'
        Coordinate = 'fixture.policy:multicopy-conflict:1.0'
        Copies = @(
          @{ Hash = 'apache-copy'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>multicopy-conflict</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
          @{ Hash = 'mit-copy'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>multicopy-conflict</artifactId><version>1.0</version><licenses><license><name>MIT</name></license></licenses></project>' }
        )
      }
    )
    foreach ($multiCopy in $multiCopyCases) {
      foreach ($multiCopyPom in $multiCopy.Copies) {
        [void](Write-PolicyPom -Coordinate $multiCopy.Coordinate -Hash $multiCopyPom.Hash -Xml $multiCopyPom.Xml)
      }
      $multiCopyResult = Invoke-PolicyFixture -Coordinates @($multiCopy.Coordinate)
      Assert-Policy (
        $multiCopyResult.Findings.Count -eq 0 -and
        @($multiCopyResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
      ) "[POLICY-POM-MULTICOPY-$($multiCopy.Id.ToUpperInvariant())] two cached POM copies of one GAV were not rejected as GRADLE-POM (findings=$($multiCopyResult.Findings.Count); violations=$(@($multiCopyResult.Violations | ForEach-Object { $_.Code }) -join ', '))"
    }

    $supplementaryFormat = [System.Text.Rune]::new(0x1BCA0).ToString()
    $xmlEntityPrefix = ([string][char]38) + '#x'
    $malformedHighEntity = $xmlEntityPrefix + 'D800;'
    $malformedLowEntity = $xmlEntityPrefix + 'DC00;'
    $parentMetadataCases = @(
      @{ Id = 'missing-group'; Error = $null; Coordinate = 'fixture.policy:parent-missing-group:1.0'; Xml = '<project><parent><artifactId>parent</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-missing-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'missing-version'; Error = $null; Coordinate = 'fixture.policy:parent-missing-version:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId></parent><groupId>fixture.policy</groupId><artifactId>parent-missing-version</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'control-artifact'; Error = $null; Coordinate = 'fixture.policy:parent-control-artifact:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent&#x202E;</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-control-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'supplementary-format-artifact'; Error = '[LICENSE-METADATA-SCALAR]'; Coordinate = 'fixture.policy:parent-supplementary-format-artifact:1.0'; Xml = "<project><parent><groupId>fixture.policy</groupId><artifactId>parent${supplementaryFormat}</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-supplementary-format-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>" },
      @{ Id = 'malformed-high-artifact'; Error = $null; EntityHex = '26-23-78-44-38-30-30-3B'; Coordinate = 'fixture.policy:parent-malformed-high-artifact:1.0'; Xml = "<project><parent><groupId>fixture.policy</groupId><artifactId>parent${malformedHighEntity}</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-malformed-high-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>" },
      @{ Id = 'malformed-low-artifact'; Error = $null; EntityHex = '26-23-78-44-43-30-30-3B'; Coordinate = 'fixture.policy:parent-malformed-low-artifact:1.0'; Xml = "<project><parent><groupId>fixture.policy</groupId><artifactId>parent${malformedLowEntity}</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-malformed-low-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>" }
    )
    foreach ($parentMetadata in $parentMetadataCases) {
      if ($parentMetadata.ContainsKey('EntityHex')) {
        $pomFixtureHex = [System.BitConverter]::ToString([System.Text.Encoding]::UTF8.GetBytes($parentMetadata.Xml))
        Assert-Policy (
          [regex]::Matches($pomFixtureHex, [regex]::Escape($parentMetadata.EntityHex)).Count -eq 1
        ) "[POLICY-POM-MALFORMED-FIXTURE-BYTES-$($parentMetadata.Id.ToUpperInvariant())] generated XML entity bytes drifted"
      }
      [void](Write-PolicyPom -Coordinate $parentMetadata.Coordinate -Xml $parentMetadata.Xml)
      $parentMetadataResult = Invoke-PolicyFixture -Coordinates @($parentMetadata.Coordinate)
      $parentPomErrors = @($parentMetadataResult.Violations | Where-Object Code -CEQ 'GRADLE-POM')
      Assert-Policy (
        $parentPomErrors.Count -eq 1 -and
        ([string]::IsNullOrEmpty([string]$parentMetadata.Error) -or $parentPomErrors[0].Detail -match [regex]::Escape($parentMetadata.Error))
      ) "[POLICY-POM-PARENT-$($parentMetadata.Id.ToUpperInvariant())] malformed parent GAV was accepted"
    }

    $baselineExceptions = @'
[
  {"coordinate":"fixture.policy:fallback:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:fallback-no-license:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback-no-license","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:valid-unknown:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/valid-unknown","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:declared:1.0","declared_license":"BSD License","license":"BSD-3-Clause","evidence_url":"https://example.invalid/declared","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:risk:1.0","declared_license":"EPL-1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/risk","registered_by":"policy-test","registered_on":"2026-08-19"}
]
'@
    Set-PolicyExceptions -Json $baselineExceptions
    $fallbackResult = Invoke-PolicyFixture -Coordinates @('fixture.policy:fallback:1.0')
    Assert-Policy (
      $fallbackResult.Violations.Count -eq 0 -and
      $fallbackResult.Findings.Count -eq 1 -and
      $fallbackResult.Findings[0].Source -ceq 'fallback-override' -and
      $fallbackResult.Findings[0].EffectiveLicense -ceq 'Apache-2.0'
    ) '[POLICY-OVERRIDE-FALLBACK] exact missing-POM fallback was not accepted'

    $missingLicenseCoordinate = 'fixture.policy:fallback-no-license:1.0'
    [void](Write-PolicyPom -Coordinate $missingLicenseCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>fallback-no-license</artifactId><version>1.0</version></project>')
    $missingLicenseResult = Invoke-PolicyFixture -Coordinates @($missingLicenseCoordinate)
    Assert-Policy (
      $missingLicenseResult.Violations.Count -eq 0 -and
      $missingLicenseResult.Findings.Count -eq 1 -and
      $missingLicenseResult.Findings[0].Source -ceq 'fallback-override'
    ) '[POLICY-OVERRIDE-MISSING-LICENSE] exact fallback did not cover a valid POM with no license/name'

    Set-PolicyExceptions -Json '[]'
    $missingWithoutFallback = Invoke-PolicyFixture -Coordinates @('fixture.policy:missing-without-fallback:1.0')
    $missingLicenseWithoutFallback = Invoke-PolicyFixture -Coordinates @($missingLicenseCoordinate)
    Assert-Policy (
      @($missingWithoutFallback.Violations | Where-Object Code -CEQ 'GRADLE-METADATA').Count -eq 1 -and
      @($missingLicenseWithoutFallback.Violations | Where-Object Code -CEQ 'GRADLE-METADATA').Count -eq 1
    ) '[POLICY-METADATA-NO-FALLBACK] missing POM or missing license/name passed without an exact fallback'
    Set-PolicyExceptions -Json $baselineExceptions

    $validUnknownCoordinate = 'fixture.policy:valid-unknown:1.0'
    [void](Write-PolicyPom -Coordinate $validUnknownCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>valid-unknown</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    $validUnknownResult = Invoke-PolicyFixture -Coordinates @($validUnknownCoordinate)
    Assert-Policy (
      @($validUnknownResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($validUnknownResult.Findings | Where-Object Source -CEQ 'fallback-override').Count -eq 0
    ) '[POLICY-FALLBACK-STATE] valid unknown POM was incorrectly replaced by a missing-metadata fallback'

    $declaredCoordinate = 'fixture.policy:declared:1.0'
    [void](Write-PolicyPom -Coordinate $declaredCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>declared</artifactId><version>1.0</version><licenses><license><name>BSD License</name></license></licenses></project>')
    $declaredResult = Invoke-PolicyFixture -Coordinates @($declaredCoordinate)
    Assert-Policy (
      $declaredResult.Violations.Count -eq 0 -and
      $declaredResult.Findings.Count -eq 1 -and
      $declaredResult.Findings[0].Source -ceq 'declared-override' -and
      $declaredResult.Findings[0].EffectiveLicense -ceq 'BSD-3-Clause'
    ) '[POLICY-DECLARED-EXACT] exact declared_license mapping was not applied'

    $nearCoordinate = 'fixture.policy:declared-near:1.0'
    [void](Write-PolicyPom -Coordinate $nearCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>declared-near</artifactId><version>1.0</version><licenses><license><name>bsd license</name></license></licenses></project>')
    $nearExceptions = @'
[{"coordinate":"fixture.policy:declared-near:1.0","declared_license":"BSD License","license":"BSD-3-Clause","evidence_url":"https://example.invalid/near","registered_by":"policy-test","registered_on":"2026-08-19"}]
'@
    Set-PolicyExceptions -Json $nearExceptions
    $nearResult = Invoke-PolicyFixture -Coordinates @($nearCoordinate)
    Assert-Policy (
      @($nearResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1
    ) '[POLICY-DECLARED-ORDINAL] case-near declared_license mapping was accepted'

    Set-PolicyExceptions -Json @'
[{"coordinate":"fixture.policy:risk:1.0","declared_license":"EPL-1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/risk","registered_by":"policy-test","registered_on":"2026-08-19"}]
'@
    $riskCoordinate = 'fixture.policy:risk:1.0'
    [void](Write-PolicyPom -Coordinate $riskCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>risk</artifactId><version>1.0</version><licenses><license><name>EPL-1.0</name></license></licenses></project>')
    $riskResult = Invoke-PolicyFixture -Coordinates @($riskCoordinate)
    Assert-Policy (
      @($riskResult.Violations | Where-Object Code -CEQ 'GRADLE-FORBIDDEN').Count -eq 1
    ) '[POLICY-FORBIDDEN-FIRST] declared mapping overrode a forbidden POM license'

    $invalidExceptions = @(
      @{ Id = 'top-level'; Error = $null; Json = '{}' },
      @{ Id = 'non-object'; Error = $null; Json = '[7]' },
      @{ Id = 'duplicate-field'; Error = '字段重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","license":"BSD-3-Clause","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'unsupported-field'; Error = '不支持字段'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19","note":"no"}]' },
      @{ Id = 'control'; Error = '控制/格式'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy\u202etest","registered_on":"2026-08-19"}]' },
      @{ Id = 'supplementary-format'; Error = '[LICENSE-METADATA-SCALAR]'; Json = "[{`"coordinate`":`"fixture.policy:a:1.0`",`"license`":`"Apache-2.0`",`"evidence_url`":`"https://example.invalid/a`",`"registered_by`":`"policy${supplementaryFormat}test`",`"registered_on`":`"2026-08-19`"}]" },
      @{ Id = 'wildcard'; Error = '具体且安全'; Json = '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-coordinate'; Error = '缺少必填字段 coordinate'; Json = '[{"license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-license'; Error = '缺少必填字段 license'; Json = '[{"coordinate":"fixture.policy:a:1.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-evidence'; Error = '缺少必填字段 evidence_url'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-registrant'; Error = '缺少必填字段 registered_by'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-date'; Error = '缺少必填字段 registered_on'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test"}]' },
      @{ Id = 'empty-registrant'; Error = '缺少必填字段'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"","registered_on":"2026-08-19"}]' },
      @{ Id = 'blank-declared'; Error = 'declared_license 不能为空'; Json = '[{"coordinate":"fixture.policy:a:1.0","declared_license":" ","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'canonical-alias'; Error = '精确 canonical'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"apache 2","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-url'; Error = '绝对 http'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"file:///tmp/evidence","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-date'; Error = 'yyyy-MM-dd'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"19-08-2026"}]' },
      @{ Id = 'non-string'; Error = 'JSON 字符串'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":7,"registered_on":"2026-08-19"}]' },
      @{ Id = 'duplicate-fallback'; Error = '坐标重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:a:1.0","license":"BSD-3-Clause","evidence_url":"https://example.invalid/b","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'duplicate-declared'; Error = 'declared_license 重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","declared_license":"Mystery","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:a:1.0","declared_license":"Mystery","license":"BSD-3-Clause","evidence_url":"https://example.invalid/b","registered_by":"policy-test","registered_on":"2026-08-19"}]' }
    )
    $jsonEscapePrefix = ([string][char]92) + 'u'
    foreach ($surrogateCase in @(
      @{ Id = 'high'; Escape = $jsonEscapePrefix + 'D800'; EscapeHex = '5C-75-44-38-30-30' },
      @{ Id = 'low'; Escape = $jsonEscapePrefix + 'DC00'; EscapeHex = '5C-75-44-43-30-30' }
    )) {
      foreach ($field in @('coordinate', 'declared_license', 'license', 'evidence_url', 'registered_by', 'registered_on')) {
        $values = @{
          coordinate = 'fixture.policy:a:1.0'
          declared_license = 'Mystery'
          license = 'Apache-2.0'
          evidence_url = 'https://example.invalid/a'
          registered_by = 'policy-test'
          registered_on = '2026-08-19'
        }
        $values[$field] = "$($values[$field])$($surrogateCase.Escape)"
        $malformedJson = '[{"coordinate":"' + $values.coordinate + '","declared_license":"' + $values.declared_license + '","license":"' + $values.license + '","evidence_url":"' + $values.evidence_url + '","registered_by":"' + $values.registered_by + '","registered_on":"' + $values.registered_on + '"}]'
        $malformedJsonHex = [System.BitConverter]::ToString([System.Text.Encoding]::UTF8.GetBytes($malformedJson))
        Assert-Policy (
          [regex]::Matches($malformedJsonHex, [regex]::Escape($surrogateCase.EscapeHex)).Count -eq 1
        ) "[POLICY-OVERRIDE-MALFORMED-FIXTURE-BYTES-$($surrogateCase.Id.ToUpperInvariant())-$($field.Replace('_', '-').ToUpperInvariant())] generated JSON escape bytes drifted"
        $invalidExceptions += @{
          Id = "malformed-$($surrogateCase.Id)-$($field.Replace('_', '-'))"
          Error = '[LICENSE-METADATA-SCALAR]'
          Json = $malformedJson
        }
      }
      $invalidExceptions += @{
        Id = "malformed-$($surrogateCase.Id)-property-name"
        Error = '[LICENSE-METADATA-SCALAR]'
        Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by' + $surrogateCase.Escape + '":"policy-test","registered_on":"2026-08-19"}]'
      }
      $propertyFixtureHex = [System.BitConverter]::ToString([System.Text.Encoding]::UTF8.GetBytes($invalidExceptions[-1].Json))
      Assert-Policy (
        [regex]::Matches($propertyFixtureHex, [regex]::Escape($surrogateCase.EscapeHex)).Count -eq 1
      ) "[POLICY-OVERRIDE-MALFORMED-FIXTURE-BYTES-$($surrogateCase.Id.ToUpperInvariant())-PROPERTY-NAME] generated JSON property escape bytes drifted"
    }
    foreach ($invalid in $invalidExceptions) {
      Set-PolicyExceptions -Json $invalid.Json
      $invalidResult = Invoke-PolicyFixture -Coordinates @('fixture.policy:a:1.0')
      $overrideErrors = @($invalidResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE')
      Assert-Policy (
        $invalidResult.Findings.Count -eq 0 -and
        $overrideErrors.Count -eq 1 -and
        ([string]::IsNullOrEmpty([string]$invalid.Error) -or $overrideErrors[0].Detail -match [regex]::Escape($invalid.Error))
      ) "[POLICY-OVERRIDE-$($invalid.Id.ToUpperInvariant())] malformed exception did not fail closed"
    }

    $invalidContinueCoordinate = 'fixture.policy:invalid-continue:1.0'
    [void](Write-PolicyPom -Coordinate $invalidContinueCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>invalid-continue</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    Set-PolicyExceptions -Json '[{"coordinate":"fixture.policy:invalid-continue:1.0","declared_license":"Mystery License","license":"Apache-2.0","evidence_url":"https://example.invalid/partial","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]'
    $invalidContinueResult = Invoke-PolicyFixture -Coordinates @($invalidContinueCoordinate)
    Assert-Policy (
      @($invalidContinueResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE').Count -eq 1 -and
      @($invalidContinueResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($invalidContinueResult.Findings | Where-Object Source -CEQ 'declared-override').Count -eq 0
    ) '[POLICY-OVERRIDE-CONTINUE] invalid exception table retained a partial override or suppressed concrete-GAV evaluation'

    $caseGavCoordinate = 'fixture.policy:case-gav:1.0'
    [void](Write-PolicyPom -Coordinate $caseGavCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>case-gav</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    Set-PolicyExceptions -Json '[{"coordinate":"Fixture.Policy:case-gav:1.0","declared_license":"Mystery License","license":"Apache-2.0","evidence_url":"https://example.invalid/case-gav","registered_by":"policy-test","registered_on":"2026-08-19"}]'
    $caseGavResult = Invoke-PolicyFixture -Coordinates @($caseGavCoordinate)
    Assert-Policy (
      @($caseGavResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($caseGavResult.Findings | Where-Object Source -CEQ 'declared-override').Count -eq 0
    ) '[POLICY-OVERRIDE-GAV-ORDINAL] case-near GAV matched an exception record'

    $emptyGraphRoot = Join-Path $policyRoot 'empty-graph-root'
    New-Item -ItemType Directory -Force -Path (Join-Path $emptyGraphRoot 'configs/licenses') | Out-Null
    [System.IO.File]::WriteAllText(
      (Join-Path $emptyGraphRoot 'configs/licenses/gradle-exceptions.json'),
      '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]',
      [System.Text.UTF8Encoding]::new($false)
    )
    $script:policyMainResolved = @()
    function Get-GradleResolvedGraphs {
      return [PSCustomObject]@{ Resolved = @($script:policyMainResolved); Errors = @() }
    }
    $script:bad = @(); $script:warn = @()
    Invoke-GradleLicenseScan -Root $emptyGraphRoot
    Assert-Policy (
      @($script:bad | Where-Object { $_ -match '\[GRADLE-OVERRIDE\]' }).Count -eq 1
    ) '[POLICY-EMPTY-GRAPH-OVERRIDE] empty resolved graph skipped exception-table validation'

    $policyMainRoot = Join-Path $policyRoot 'main-path-root'
    New-Item -ItemType Directory -Force -Path (Join-Path $policyMainRoot 'configs/licenses') | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $policyMainRoot 'configs/licenses/gradle-exceptions.json'), '[]', [System.Text.UTF8Encoding]::new($false))
    $savedPolicyGradleHome = $env:GRADLE_USER_HOME
    try {
      $env:GRADLE_USER_HOME = $policyGradleHome
      $mainCases = @(
        @{ Id = 'permissive'; License = 'Apache-2.0'; Distributes = $false; Bad = 0; Warn = 0 },
        @{ Id = 'yellow'; License = 'LGPL-2.1'; Distributes = $false; Bad = 0; Warn = 1 },
        @{ Id = 'gpl-private'; License = 'GPL-3.0'; Distributes = $false; Bad = 0; Warn = 1 },
        @{ Id = 'gpl-distributed'; License = 'GPL-3.0'; Distributes = $true; Bad = 1; Warn = 0 },
        @{ Id = 'forbidden'; License = 'EPL-1.0'; Distributes = $false; Bad = 1; Warn = 0 },
        @{ Id = 'unknown'; License = 'Mystery License'; Distributes = $false; Bad = 1; Warn = 0 }
      )
      foreach ($mainCase in $mainCases) {
        $coordinate = "fixture.policy:main-$($mainCase.Id):1.0"
        [void](Write-PolicyPom -Coordinate $coordinate -Xml "<project><groupId>fixture.policy</groupId><artifactId>main-$($mainCase.Id)</artifactId><version>1.0</version><licenses><license><name>$($mainCase.License)</name></license></licenses></project>")
        $script:policyMainResolved = @([PSCustomObject]@{ Coordinate = $coordinate; Configurations = @(':core:testRuntimeClasspath') })
        $script:bad = @(); $script:warn = @(); $script:Distributes = [bool]$mainCase.Distributes
        Invoke-GradleLicenseScan -Root $policyMainRoot
        Assert-Policy (
          $script:bad.Count -eq $mainCase.Bad -and $script:warn.Count -eq $mainCase.Warn
        ) "[POLICY-MAIN-$($mainCase.Id.ToUpperInvariant())] production caller outcome was bad=$($script:bad.Count), warn=$($script:warn.Count)"
      }

      # The library checks above prove classification. These two child processes additionally pin the
      # public CLI boundary: a populated bad bucket must name the coordinate and return nonzero.
      $processRoot = Join-Path $policyRoot 'process-root'
      $processScripts = Join-Path $processRoot 'scripts'
      $processConfig = Join-Path $processRoot 'configs/licenses'
      New-Item -ItemType Directory -Force -Path $processScripts, $processConfig | Out-Null
      [System.IO.File]::WriteAllText((Join-Path $processConfig 'gradle-exceptions.json'), '[]', [System.Text.UTF8Encoding]::new($false))

      $processEplCoordinate = 'fixture.policy:process-epl:1.0'
      [void](Write-PolicyPom -Coordinate $processEplCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>process-epl</artifactId><version>1.0</version><licenses><license><name>EPL-1.0</name></license></licenses></project>')
      $processScannerPath = Join-Path $processScripts 'check-licenses.ps1'
      $processSource = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
      # 这个夹具把 check-licenses.ps1 复制到临时 scripts 目录再以子进程跑，于是它 dot-source 的每个兄弟脚本
      # 都必须一起搬过去——`$PSScriptRoot` 指向的是临时目录，不是 scripts/。此处**从被测脚本源码里推导**依赖清单，
      # 而不是写死一份：写死的那份在 2026-08-22 已经真的漏过一次（check-licenses.ps1 新增 `. _unicode.ps1` 后
      # 夹具仍只拷 _config.ps1，CI 上以缺文件炸掉）。推导 + 缺失即 throw，让「加了依赖忘了改夹具」不可表达。
      $processDeps = @([regex]::Matches($processSource, "(?m)^\.\s+\(Join-Path\s+\`$PSScriptRoot\s+'(?<dep>_[A-Za-z0-9_-]+\.ps1)'\)") | ForEach-Object { $_.Groups['dep'].Value } | Select-Object -Unique)
      if ($processDeps.Count -lt 1) { throw 'process fixture could not derive any dot-sourced dependency from the scanner source' }
      foreach ($processDep in $processDeps) {
        $processDepSource = Join-Path $PSScriptRoot $processDep
        if (-not (Test-Path -LiteralPath $processDepSource -PathType Leaf)) { throw "process fixture dependency '$processDep' is dot-sourced by the scanner but missing from scripts/" }
        Copy-Item -LiteralPath $processDepSource -Destination (Join-Path $processScripts $processDep) -Force
      }
      $processAnchor = 'Write-Host ""'
      if (([regex]::Matches($processSource, [regex]::Escape($processAnchor))).Count -ne 1) {
        throw 'process fixture could not locate the unique final CLI boundary'
      }
      $processHook = @'
if ($env:LICENSE_POLICY_PROCESS_CASE) {
  $probeResolved = @([PSCustomObject]@{ Coordinate = $env:LICENSE_POLICY_PROCESS_CASE; Configurations = @(':core:testRuntimeClasspath') })
  $probePolicy = Get-GradleLicensePolicyResult -Resolved $probeResolved -GradleUserHome $env:GRADLE_USER_HOME -ExceptionPath (Join-Path $RepoRoot 'configs/licenses/gradle-exceptions.json')
  Write-GradlePolicyDiagnostics -Policy $probePolicy -Resolved $probeResolved
}
'@
      [System.IO.File]::WriteAllText(
        $processScannerPath,
        $processSource.Replace($processAnchor, "$processHook`n$processAnchor"),
        [System.Text.UTF8Encoding]::new($false)
      )

      $hadProcessCase = Test-Path Env:LICENSE_POLICY_PROCESS_CASE
      $savedProcessCase = $env:LICENSE_POLICY_PROCESS_CASE
      try {
        $processCases = @(
          @{ Id = 'forbidden'; Coordinate = $processEplCoordinate; Category = '[GRADLE-FORBIDDEN]'; Strict = $true },
          @{ Id = 'unknown-metadata'; Coordinate = 'fixture.policy:process-missing:1.0'; Category = '[GRADLE-METADATA]'; Strict = $false }
        )
        foreach ($processCase in $processCases) {
          $env:LICENSE_POLICY_PROCESS_CASE = $processCase.Coordinate
          $processArgs = @('-NoProfile', '-File', $processScannerPath)
          if ($processCase.Strict) { $processArgs += '-Strict' }
          $processOutput = (& pwsh @processArgs 2>&1 | Out-String)
          $processExit = $LASTEXITCODE
          Assert-Policy (
            $processExit -ne 0 -and
            $processOutput.Contains($processCase.Coordinate, [System.StringComparison]::Ordinal) -and
            $processOutput.Contains($processCase.Category, [System.StringComparison]::Ordinal)
          ) "[POLICY-PROCESS-$($processCase.Id.ToUpperInvariant())] CLI did not fail nonzero with the exact coordinate/category (exit=$processExit; output=$processOutput)"
        }
      } finally {
        if ($hadProcessCase) { $env:LICENSE_POLICY_PROCESS_CASE = $savedProcessCase } else { Remove-Item Env:LICENSE_POLICY_PROCESS_CASE -ErrorAction SilentlyContinue }
      }
    } finally {
      $env:GRADLE_USER_HOME = $savedPolicyGradleHome
      $script:Distributes = $false
    }
  } catch {
    Assert-Policy $false "[POLICY-SETUP] policy suite failed: $($_.Exception.Message)"
  } finally {
    if (Test-Path -LiteralPath $policyRoot) { Remove-Item -LiteralPath $policyRoot -Recurse -Force }
  }

  if ($failures.Count -gt 0) {
    Write-Error "[POLICY-CONTRACT] $($failures -join "`n[POLICY-CONTRACT] ")"
    exit 1
  }

  if (-not $SkipMutations) {
    $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
    $policyMutationCases = @(
      @{
        Name = 'pom-dtd'
        From = '      $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit'
        To = '      $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Parse'
        Expected = '[POLICY-POM-DTD]'
      },
      @{
        Name = 'pom-gav'
        From = '      if ($declaredGroup -cne $group -or $declaredArtifact -cne $artifact -or $declaredVersion -cne $version) {'
        To = '      if ($false) {'
        Expected = '[POLICY-POM-GAV]'
      },
      @{
        Name = 'pom-license-name'
        From = '        if ([string]::IsNullOrWhiteSpace($licenseName)) { throw ''POM 中每个已声明 license 都必须有非空 name。'' } # require every declared license name'
        To = '        if ([string]::IsNullOrWhiteSpace($licenseName)) { continue } # require every declared license name'
        Expected = '[POLICY-POM-LICENSE-NAME]'
      },
      @{
        Name = 'pom-singleton'
        From = '  if ($nodes.Count -gt 1) { throw "POM 元素 $LocalName 必须至多出现一次。" } # POM singleton ambiguity guard'
        To = '  if ($false) { throw "POM 元素 $LocalName 必须至多出现一次。" } # POM singleton ambiguity guard'
        Expected = '[POLICY-POM-SINGLETON-PARENT]'
      },
      @{
        Name = 'pom-parent-required'
        From = '  if ($null -eq $Node -or [string]::IsNullOrWhiteSpace([string]$Node.InnerText)) { throw "POM $Field 缺失或为空。" } # POM required scalar guard'
        To = '  if ($null -eq $Node -or [string]::IsNullOrWhiteSpace([string]$Node.InnerText)) { return '''' } # POM required scalar guard'
        Expected = '[POLICY-POM-PARENT-MISSING-GROUP]'
      },
      # 同 GAV 多副本的两条守卫（此前零覆盖）。① 混合副本若不 fail-closed，会落进 MissingLicense →
      # 豁免表回退分支，一个**声明了**许可证的坐标就被人工回退洗白；② 冲突副本若不 fail-closed，
      # 扫描器会把两份声明并成一个 Valid 结果、任选其一放行。
      @{
        Name = 'pom-multicopy-mixed'
        From = '    if ($licenses.Count -gt 0) {'
        To = '    if ($false) {'
        Expected = '[POLICY-POM-MULTICOPY-MIXED]'
      },
      @{
        Name = 'pom-multicopy-conflict'
        From = '  if ($licenseSignatures.Count -ne 1) {'
        To = '  if ($false) {'
        Expected = '[POLICY-POM-MULTICOPY-CONFLICT]'
      },
      @{
        Name = 'pom-parent-scalar'
        From = '  Assert-GradleMetadataScalar -Field "POM $Field" -Value $value # POM required scalar safety guard'
        To = '  $null = $value # POM required scalar safety guard'
        Expected = '[POLICY-POM-PARENT-SUPPLEMENTARY-FORMAT-ARTIFACT]'
      },
      @{
        Name = 'classification-unknown'
        From = "  return 'unknown'"
        To = "  return 'permissive'"
        Expected = '[POLICY-CLASSIFICATION]'
      },
      @{
        Name = 'override-exact-gav'
        From = '      if ($null -eq (Get-GradleGavParts -Coordinate $coordinate)) {'
        To = '      if ($false) {'
        Expected = '[POLICY-OVERRIDE-WILDCARD]'
      },
      @{
        Name = 'override-gav-ordinal'
        From = '  $empty = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal) # exception coordinate ordinal map'
        To = '  $empty = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::OrdinalIgnoreCase) # exception coordinate ordinal map'
        Expected = '[POLICY-OVERRIDE-GAV-ORDINAL]'
      },
      @{
        Name = 'override-required-field'
        From = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_by', 'registered_on')) {"
        To = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_on')) {"
        Expected = '[POLICY-OVERRIDE-EMPTY-REGISTRANT]'
      },
      @{
        Name = 'override-duplicate-field'
        From = '          if (-not $seenFields.Add($field)) { throw "记录字段重复（大小写完全相同）：$field。" } # exception duplicate property guard'
        To = '          [void]$seenFields.Add($field) # exception duplicate property guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-FIELD]'
      },
      @{
        Name = 'override-supported-field'
        From = "      ForEach-Object { [void]`$allowedFields.Add(`$_) }"
        To = "      ForEach-Object { [void]`$allowedFields.Add(`$_) }; [void]`$allowedFields.Add('note')"
        Expected = '[POLICY-OVERRIDE-UNSUPPORTED-FIELD]'
      },
      @{
        Name = 'override-json-string'
        From = '          if ($property.Value.ValueKind -ne [System.Text.Json.JsonValueKind]::String) {'
        To = '          if ($false) {'
        Expected = '[POLICY-OVERRIDE-NON-STRING]'
      },
      @{
        Name = 'override-metadata-control'
        From = '          Assert-GradleMetadataScalar -Field $field -Value $jsonScalar # exception raw JSON scalar safety guard'
        To = '          $null = $jsonScalar # exception raw JSON scalar safety guard'
        Expected = '[POLICY-OVERRIDE-SUPPLEMENTARY-FORMAT]'
      },
      @{
        Name = 'override-declared-nonblank'
        From = '        if ([string]::IsNullOrWhiteSpace($declaredLicense)) { throw "declared_license 不能为空：$coordinate" } # exception declared license nonblank guard'
        To = '        if ($false) { throw "declared_license 不能为空：$coordinate" } # exception declared license nonblank guard'
        Expected = '[POLICY-OVERRIDE-BLANK-DECLARED]'
      },
      @{
        Name = 'override-url'
        From = "      if (-not [uri]::TryCreate(`$evidenceUrl, [System.UriKind]::Absolute, [ref]`$uri) -or `$uri.Scheme -notin @('http', 'https')) { # exception evidence URL guard"
        To = '      if ($false) { # exception evidence URL guard'
        Expected = '[POLICY-OVERRIDE-BAD-URL]'
      },
      @{
        Name = 'override-date'
        From = "      if (-not [datetime]::TryParseExact([string]`$record.registered_on, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]`$registeredOn)) { # exception registration date guard"
        To = '      if ($false) { # exception registration date guard'
        Expected = '[POLICY-OVERRIDE-BAD-DATE]'
      },
      @{
        Name = 'override-duplicate-fallback'
        From = '        if ($null -ne $bucket.Fallback) { throw "坐标重复、缺失元数据回退有歧义：$coordinate" } # exception duplicate fallback guard'
        To = '        if ($false) { throw "坐标重复、缺失元数据回退有歧义：$coordinate" } # exception duplicate fallback guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-FALLBACK]'
      },
      @{
        Name = 'override-duplicate-declared'
        From = '        if ($bucket.DeclaredLicenses.ContainsKey($declaredLicense)) { # exception duplicate declared mapping guard'
        To = '        if ($false) { # exception duplicate declared mapping guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-DECLARED]'
      },
      @{
        Name = 'override-canonical'
        From = '      if (-not (Test-GradleExceptionCanonicalLicense -License $entry.License)) {'
        To = '      if ($false) {'
        Expected = '[POLICY-OVERRIDE-CANONICAL-ALIAS]'
      },
      @{
        Name = 'override-partial-discard'
        From = '    $failedEntries = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal) # discard partial exception records'
        To = '    $failedEntries = $empty # discard partial exception records'
        Expected = '[POLICY-OVERRIDE-CONTINUE]'
      },
      @{
        Name = 'declared-ordinal'
        From = '          DeclaredLicenses = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)'
        To = '          DeclaredLicenses = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::OrdinalIgnoreCase)'
        Expected = '[POLICY-DECLARED-ORDINAL]'
      },
      @{
        Name = 'fallback-state'
        From = '    if ($pom.State -in @(''Missing'', ''MissingLicense'')) { # policy fallback state gate'
        To = '    if ($pom.State -in @(''Missing'', ''MissingLicense'', ''Valid'')) { # policy fallback state gate'
        Expected = '[POLICY-FALLBACK-STATE]'
      },
      @{
        Name = 'fallback-missing-license'
        From = '    if ($pom.State -in @(''Missing'', ''MissingLicense'')) { # policy fallback state gate'
        To = '    if ($pom.State -eq ''Missing'') { # policy fallback state gate'
        Expected = '[POLICY-OVERRIDE-MISSING-LICENSE]'
      },
      @{
        Name = 'metadata-no-fallback'
        From = '        $violations.Add($missingMetadataViolation) # policy missing metadata fail-closed record'
        To = '        $null = $missingMetadataViolation # policy missing metadata fail-closed record'
        Expected = '[POLICY-METADATA-NO-FALLBACK]'
      },
      @{
        Name = 'forbidden-precedence'
        From = '      } elseif ($classification -eq ''forbidden'') { # policy forbidden precedence'
        To = '      } elseif ($false) { # policy forbidden precedence'
        Expected = '[POLICY-FORBIDDEN-FIRST]'
      },
      @{
        Name = 'main-yellow'
        From = '        $script:warn += Get-GradleAuditText -Value "$($finding.Coordinate) => $($finding.DeclaredLicense)（Gradle 黄牌：需人工确认用途/链接方式）" # structured yellow warning'
        To = '        $null = $finding # structured yellow warning'
        Expected = '[POLICY-MAIN-YELLOW]'
      },
      @{
        Name = 'main-gpl-private'
        From = '        $script:warn += Get-GradleAuditText -Value "$($finding.Coordinate) => $($finding.DeclaredLicense)（Gradle 黄牌：纯 GPL 且本项目声明不分发[Distributes=`$false]；变 public 前用 -Strict 复核）" # structured plain-GPL warning'
        To = '        $null = $finding # structured plain-GPL warning'
        Expected = '[POLICY-MAIN-GPL-PRIVATE]'
      },
      @{
        Name = 'main-forbidden'
        From = '      Add-GradleMetadataNonCompliance "$Coordinate => $license [GRADLE-FORBIDDEN]" # direct forbidden classification'
        To = '      $null = $finding # direct forbidden classification'
        Expected = '[POLICY-MAIN-GPL-DISTRIBUTED]'
      },
      @{
        Name = 'main-unknown'
        From = '      Add-GradleMetadataNonCompliance "$($finding.Coordinate) => $($finding.Detail) [GRADLE-UNKNOWN]" # structured unknown classification'
        To = '      $null = $finding # structured unknown classification'
        Expected = '[POLICY-MAIN-UNKNOWN]'
      },
      @{
        Name = 'main-exit'
        From = 'if ($bad.Count -gt 0)                       { Write-Host "`n结论：FAIL（发现许可或依赖扫描不合规）" -ForegroundColor Red; exit 1 }'
        To = 'if ($bad.Count -gt 0)                       { Write-Host "`n结论：FAIL（发现许可或依赖扫描不合规）" -ForegroundColor Red; exit 0 }'
        Expected = '[POLICY-PROCESS-FORBIDDEN]'
      }
    )

    foreach ($mutationCase in $policyMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[POLICY-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-policy-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite policy -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[POLICY-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(policy mutations): PASS ($($policyMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(policy): PASS'
  exit 0
}

# Break caught: accepting Gradle constraint-only `(c)` rows would scan a declaration that is not a
# resolved runtime component. A repeated resolved component `(*)` remains a real graph member.
$constraintResult = Get-GradleCoordinatesFromDependencyOutput -Output @(
  '+--- fixture.constraint:only:1.0 (c)',
  '+--- fixture.actual:node:2.0',
  '\--- fixture.actual:node:2.0 (*)'
)
$constraintCoordinates = @($constraintResult.Coordinates)
Assert-Graph ($constraintResult.Errors.Count -eq 0) "constraint fixture produced parser errors: $($constraintResult.Errors -join ' | ')"
Assert-Graph (
  @($constraintCoordinates | Where-Object { $_ -ceq 'fixture.constraint:only:1.0' }).Count -eq 0
) "constraint-only row entered resolved GAV set: $($constraintCoordinates -join ', ')"
Assert-Graph (
  $constraintCoordinates.Count -eq 1 -and
  $constraintCoordinates[0] -ceq 'fixture.actual:node:2.0'
) "resolved duplicate GAV was not deduplicated: $($constraintCoordinates -join ', ')"

$parserCases = @(
  @{
    Name = 'resolved-version'
    Lines = @('+--- fixture.redirect:artifact:0.9 -> 1.0', '\--- fixture.redirect:artifact:1.0 (*)')
    Coordinates = @('fixture.redirect:artifact:1.0')
    ErrorCodes = @()
  },
  @{
    Name = 'project-boundary'
    Lines = @('+--- project :core', '+--- project :source -> project :core', '\--- project :source -> fixture.external:artifact:1.2.3')
    Coordinates = @('fixture.external:artifact:1.2.3')
    ErrorCodes = @()
  },
  @{
    Name = 'selected-targets'
    Lines = @('+--- old.group:old-artifact:1.0 -> project :core', '\--- old.group:old-artifact:1.0 -> new.group:new-artifact:2.0')
    Coordinates = @('new.group:new-artifact:2.0')
    ErrorCodes = @()
  },
  @{
    Name = 'unresolved-and-malformed'
    Lines = @('+--- fixture.unresolved:artifact:1.0 (n)', '+--- project :source ->', '\--- malformed external edge')
    Coordinates = @()
    ErrorCodes = @('GRADLE-UNRESOLVED', 'GRADLE-PARSE', 'GRADLE-PARSE')
  },
  @{
    Name = 'non-concrete'
    Lines = @('+--- fixture.invalid:artifact:..', '\--- fixture.invalid:artifact:latest.release')
    Coordinates = @()
    ErrorCodes = @('GRADLE-PARSE', 'GRADLE-PARSE')
  },
  # 未解析边守卫的 FAILED 那一半此前无人测：存活的 fixture 只含 `(n)`，把生产正则里的 `FAILED|`
  # 删掉，四个套件仍全绿——而 `+--- group:artifact:1.0 FAILED` 会就此被当成可解析坐标。
  @{
    Name = 'unresolved-failed'
    Lines = @('+--- fixture.failed:artifact:1.0 FAILED', '\--- fixture.failed:other:2.0 -> 2.1 FAILED')
    Coordinates = @()
    ErrorCodes = @('GRADLE-UNRESOLVED', 'GRADLE-UNRESOLVED')
  },
  # 外部依赖边的尾部判定 else 分支（「无法判定 Gradle 外部依赖边」那一处）此前零覆盖：
  # non-concrete 走的是其后的具体版本闸，malformed external edge 走的是更早的 group:artifact 不匹配。
  @{
    Name = 'empty-requested-selector'
    Lines = @('+--- fixture.empty:selector: -> 1.0')
    Coordinates = @()
    ErrorCodes = @('GRADLE-PARSE')
    DiagnosticEdges = @('fixture.empty:selector: -> 1.0')
  },
  @{
    Name = 'empty-redirect-tail'
    Lines = @('+--- fixture.tail:empty:1.0 ->')
    Coordinates = @()
    ErrorCodes = @('GRADLE-PARSE')
    DiagnosticEdges = @('fixture.tail:empty:1.0 ->')
  },
  # 「选中目标是个畸形 project 边」那一处 fail-closed 此前零覆盖：selected-targets 只走了
  # 「选中内部 project」与「选中外部 module」两条正常分支。
  @{
    Name = 'malformed-selected-project'
    Lines = @('+--- old.group:old-artifact:1.0 -> project :core extra')
    Coordinates = @()
    ErrorCodes = @('GRADLE-PARSE')
  }
)
foreach ($case in $parserCases) {
  $result = Get-GradleCoordinatesFromDependencyOutput -Output $case.Lines
  $actualCoordinates = @($result.Coordinates)
  $actualCodes = @($result.Errors | ForEach-Object {
    if ($_ -match '\[(GRADLE-[A-Z-]+)\]\s*$') { $Matches[1] } else { 'UNCLASSIFIED' }
  })
  Assert-Graph (($actualCoordinates -join ',') -ceq ($case.Coordinates -join ',')) "parser/$($case.Name) returned wrong GAVs: $($actualCoordinates -join ', ')"
  Assert-Graph (($actualCodes -join ',') -ceq ($case.ErrorCodes -join ',')) "parser/$($case.Name) returned wrong error codes: $($actualCodes -join ', ')"
  $diagnosticEdges = if ($case.ContainsKey('DiagnosticEdges')) { @($case.DiagnosticEdges) } else { @() }
  foreach ($diagnosticEdge in $diagnosticEdges) {
    Assert-Graph (($result.Errors -join "`n").Contains($diagnosticEdge)) "parser/$($case.Name) omitted original edge text: $diagnosticEdge"
  }
}

# Break caught: graph collection must be independently consumable by policy code. It must execute
# exactly the four approved configurations offline and return resolved GAVs with their provenance.
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-graph-$PID-$([guid]::NewGuid().ToString('N'))"
$hadGradleUserHome = Test-Path Env:GRADLE_USER_HOME
$savedGradleUserHome = $env:GRADLE_USER_HOME
try {
  $androidRoot = Join-Path $fixtureRoot 'android'
  $gradleHome = Join-Path $fixtureRoot 'gradle-home'
  $wrapperDir = Join-Path $androidRoot 'gradle/wrapper'
  $distributionDir = Join-Path $gradleHome 'wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6'
  $distributionRoot = Join-Path $distributionDir 'gradle-9.7.0'
  $nativeCacheRoot = Join-Path $gradleHome 'caches/modules-2/files-2.1'
  $nativeArtifactRoot = Join-Path $nativeCacheRoot 'fixture.group/fixture-artifact/1.0/fixture-hash'
  $nativeMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.107'
  foreach ($directory in @(
    $wrapperDir,
    (Join-Path $distributionRoot 'lib'),
    (Join-Path $distributionRoot 'bin'),
    $nativeArtifactRoot,
    $nativeMetadataRoot
  )) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
  Set-Content -LiteralPath (Join-Path $wrapperDir 'gradle-wrapper.properties') -Encoding utf8 -Value 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip'
  foreach ($file in @(
    (Join-Path $androidRoot 'gradlew'),
    (Join-Path $androidRoot 'gradlew.bat'),
    (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok'),
    (Join-Path $distributionRoot 'lib/gradle-launcher-9.7.0.jar'),
    (Join-Path $distributionRoot 'bin/gradle'),
    (Join-Path $distributionRoot 'bin/gradle.bat'),
    (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom'),
    (Join-Path $nativeMetadataRoot 'module-metadata.bin')
  )) { Set-Content -LiteralPath $file -Encoding utf8 -Value 'fixture' }

  $invocations = [System.Collections.Generic.List[object]]::new()
  $reports = @{
    runtimeClasspath = @('+--- fixture.core:runtime:1.0')
    testRuntimeClasspath = @('+--- org.testng:testng:7.0.0', '\--- fixture.constraint:only:1.0 (c)')
    debugRuntimeClasspath = @('+--- fixture.app:debug:1.0')
    releaseRuntimeClasspath = @('+--- fixture.app:release:1.0')
  }
  $invoker = {
    param([string]$Command, [string[]]$Arguments)
    $invocations.Add([PSCustomObject]@{ Command = $Command; Arguments = @($Arguments); GradleUserHome = $env:GRADLE_USER_HOME })
    $configurationIndex = [Array]::IndexOf($Arguments, '--configuration')
    $configuration = if ($configurationIndex -ge 0) { $Arguments[$configurationIndex + 1] } else { '' }
    [PSCustomObject]@{ ExitCode = 0; Output = @($reports[$configuration]) }
  }.GetNewClosure()

  try {
    $ambientColdHome = Join-Path $fixtureRoot 'ambient-cold-gradle-home'
    $env:GRADLE_USER_HOME = $ambientColdHome
    $graphResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    $resolvedCoordinates = @($graphResult.Resolved | ForEach-Object Coordinate)
    Assert-Graph ($graphResult.Errors.Count -eq 0) "graph collector returned errors: $($graphResult.Errors | ConvertTo-Json -Compress)"
    Assert-Graph ($invocations.Count -eq 4) "graph collector invoked $($invocations.Count) configurations instead of 4"
    Assert-Graph (
      ($resolvedCoordinates -join ',') -ceq 'fixture.app:debug:1.0,fixture.app:release:1.0,fixture.core:runtime:1.0,org.testng:testng:7.0.0'
    ) "graph collector returned wrong concrete GAV set: $($resolvedCoordinates -join ', ')"
    $testNg = @($graphResult.Resolved | Where-Object Coordinate -CEQ 'org.testng:testng:7.0.0')
    Assert-Graph (
      $testNg.Count -eq 1 -and ($testNg[0].Configurations -join ',') -ceq ':core:testRuntimeClasspath'
    ) "graph collector lost configuration provenance for TestNG"
    $expectedWindowsWrapper = Join-Path $androidRoot 'gradlew.bat'
    $expectedConfigurations = [ordered]@{
      runtimeClasspath = ':core:dependencies'
      testRuntimeClasspath = ':core:dependencies'
      debugRuntimeClasspath = ':app:dependencies'
      releaseRuntimeClasspath = ':app:dependencies'
    }
    foreach ($configuration in $expectedConfigurations.Keys) {
      $matchingCall = @($invocations | Where-Object {
        $_.Command -ceq $expectedWindowsWrapper -and
        ($_.Arguments -join "`u{001F}") -match "(?:^|`u{001F})--configuration`u{001F}$([regex]::Escape($configuration))(?:$|`u{001F})"
      })
      Assert-Graph ($matchingCall.Count -eq 1) "Windows graph call for $configuration was not exact"
      if ($matchingCall.Count -eq 1) {
        Assert-Graph ($matchingCall[0].GradleUserHome -ceq $gradleHome) "Windows $configuration did not bind preflighted GradleUserHome"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq '--offline' }).Count -eq 1) "Windows $configuration call omitted --offline"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq '--no-daemon' }).Count -eq 1) "Windows $configuration call omitted --no-daemon"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq $expectedConfigurations[$configuration] }).Count -eq 1) "Windows $configuration used wrong Gradle project task"
      }
    }
    Assert-Graph ($env:GRADLE_USER_HOME -ceq $ambientColdHome) "graph collector did not restore ambient GradleUserHome"

    # Break caught: the repository's POSIX wrapper is mode 100644, so Unix must invoke it through sh.
    $invocations.Clear()
    $unixResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $false -Invoker $invoker
    $expectedUnixWrapper = Join-Path $androidRoot 'gradlew'
    Assert-Graph ($unixResult.Errors.Count -eq 0) "Unix graph collector returned errors"
    Assert-Graph ($invocations.Count -eq 4) "Unix graph collector invoked $($invocations.Count) configurations instead of 4"
    foreach ($call in $invocations) {
      Assert-Graph ($call.Command -ceq 'sh') "Unix graph collector did not invoke sh: $($call.Command)"
      Assert-Graph ($call.Arguments.Count -gt 0 -and $call.Arguments[0] -ceq $expectedUnixWrapper) "Unix graph collector did not pass gradlew as sh argv[0]"
    }

    # Break caught: each nonzero Gradle subprocess must remain a graph error with target and exit code.
    $failureInvoker = {
      param([string]$Command, [string[]]$Arguments)
      [PSCustomObject]@{
        ExitCode = 42
        Output = @('simulated Gradle failure detail')
      }
    }
    $failureResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $failureInvoker
    Assert-Graph ($failureResult.Errors.Count -eq 4) "nonzero Gradle fixture did not return one error per approved graph"
    $failureLabels = @($failureResult.Errors.Configuration | Sort-Object)
    Assert-Graph (
      ($failureLabels -join ',') -ceq ':app:debugRuntimeClasspath,:app:releaseRuntimeClasspath,:core:runtimeClasspath,:core:testRuntimeClasspath'
    ) "nonzero Gradle error lost target provenance"
    foreach ($errorRecord in $failureResult.Errors) {
      Assert-Graph ($errorRecord.Code -ceq 'GRADLE-SUBPROCESS' -and $errorRecord.ExitCode -eq 42) "nonzero Gradle error lost code/exit"
    }

    # Break caught: a zero-exit Gradle report that yields no parseable GAV **and** no parser error must
    # still fail closed, per configuration. 这是本卡整条链路上最坏的失效形态：Gradle 换掉树形字符 / 某个
    # configuration 一行都不打 / `--console` 形态变化 ⇒ 解析器既不吐坐标也不吐错误，许可闸于是**绿着**
    # 报告「零个 Gradle 坐标」——扫了个空却宣布通过。把它变成 GRADLE-PARSE 的只有 check-licenses.ps1 的
    # `$parsed.Count -eq 0 -and $parseResult.Errors.Count -eq 0` 那一条守卫；它此前的唯一夹具随 selftest
    # 的 -1434 行一起被删除（实测：把该守卫改成 `if ($false)` 后 graph 与 policy 两个套件仍全绿）。
    $emptyInvoker = {
      param([string]$Command, [string[]]$Arguments)
      [PSCustomObject]@{ ExitCode = 0; Output = @('no resolved Gradle coordinate here') }
    }
    $emptyReportResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $emptyInvoker
    $emptyReportLabels = @($emptyReportResult.Errors | Where-Object { $_.Code -ceq 'GRADLE-PARSE' } | ForEach-Object { $_.Configuration } | Sort-Object)
    Assert-Graph (
      @($emptyReportResult.Resolved).Count -eq 0 -and
      $emptyReportResult.Errors.Count -eq 4 -and
      ($emptyReportLabels -join ',') -ceq ':app:debugRuntimeClasspath,:app:releaseRuntimeClasspath,:core:runtimeClasspath,:core:testRuntimeClasspath'
    ) "[GRAPH-EMPTY-REPORT] a zero-exit Gradle report with no parseable GAV did not fail closed once per configuration (resolved=$(@($emptyReportResult.Resolved).Count), errors=$($emptyReportResult.Errors.Count), GRADLE-PARSE targets=$($emptyReportLabels -join ', '))"

    # A warm ambient cache must not authorize a different, cold caller-supplied cache.
    $coldGradleHome = Join-Path $fixtureRoot 'caller-cold-gradle-home'
    New-Item -ItemType Directory -Force -Path $coldGradleHome | Out-Null
    Copy-Item -LiteralPath (Join-Path $gradleHome 'wrapper') -Destination (Join-Path $coldGradleHome 'wrapper') -Recurse
    $env:GRADLE_USER_HOME = $gradleHome
    $invocations.Clear()
    $mismatchedCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $coldGradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $mismatchedCacheResult.Errors.Count -eq 1 -and $mismatchedCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "cold caller cache with warm ambient cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "cold caller cache with warm ambient cache still started $($invocations.Count) wrapper calls"
    Assert-Graph ($env:GRADLE_USER_HOME -ceq $gradleHome) "cold-cache preflight changed ambient GradleUserHome"

    # Break caught: an absent native dependency cache must fail before any wrapper process starts.
    Remove-Item -LiteralPath $nativeCacheRoot -Recurse -Force
    $invocations.Clear()
    $missingCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingCacheResult.Errors.Count -eq 1 -and $missingCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "missing native cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "missing native cache still started $($invocations.Count) wrapper calls"

    # Break caught: an empty files-2.1 directory is not a warmed native dependency cache.
    New-Item -ItemType Directory -Force -Path $nativeCacheRoot | Out-Null
    $invocations.Clear()
    $emptyCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyCacheResult.Errors.Count -eq 1 -and $emptyCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty native cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty native cache still started $($invocations.Count) wrapper calls"

    # A directory-only Maven cache shape is still cold: no resolved artifact/POM was cached.
    New-Item -ItemType Directory -Force -Path $nativeArtifactRoot | Out-Null
    $invocations.Clear()
    $emptyArtifactResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyArtifactResult.Errors.Count -eq 1 -and $emptyArtifactResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty artifact subtree did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty artifact subtree still started $($invocations.Count) wrapper calls"

    # Cached files without Gradle's module-resolution metadata cannot prove an offline graph is ready.
    Set-Content -LiteralPath (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom') -Encoding utf8 -Value 'fixture'
    Remove-Item -LiteralPath $nativeMetadataRoot -Recurse -Force
    $invocations.Clear()
    $missingMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingMetadataResult.Errors.Count -eq 1 -and $missingMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "missing native metadata did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "missing native metadata still started $($invocations.Count) wrapper calls"

    New-Item -ItemType Directory -Force -Path $nativeMetadataRoot | Out-Null
    $invocations.Clear()
    $emptyMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyMetadataResult.Errors.Count -eq 1 -and $emptyMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty native metadata did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty native metadata still started $($invocations.Count) wrapper calls"

    # Gradle 9.7 consumes metadata-2.107; a populated older format is not an offline-ready native cache.
    Remove-Item -LiteralPath $nativeMetadataRoot -Recurse -Force
    $staleMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.106'
    New-Item -ItemType Directory -Force -Path $staleMetadataRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $staleMetadataRoot 'module-metadata.bin') -Encoding utf8 -Value 'stale fixture'
    $invocations.Clear()
    $staleMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $staleMetadataResult.Errors.Count -eq 1 -and $staleMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "stale native metadata format did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "[GRAPH-STALE-METADATA-STARTED] stale native metadata format still started $($invocations.Count) wrapper calls"
    Remove-Item -LiteralPath $staleMetadataRoot -Recurse -Force
    New-Item -ItemType Directory -Force -Path $nativeMetadataRoot | Out-Null

    # Existing wrapper readiness remains a graph boundary: missing completion marker is zero-start.
    Set-Content -LiteralPath (Join-Path $nativeMetadataRoot 'module-metadata.bin') -Encoding utf8 -Value 'fixture'
    Remove-Item -LiteralPath (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok') -Force
    $invocations.Clear()
    $missingDistributionResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingDistributionResult.Errors.Count -eq 1 -and $missingDistributionResult.Errors[0].Code -ceq 'GRADLE-WRAPPER-OFFLINE'
    ) "[GRAPH-WRAPPER-OK-MARKER] missing wrapper completion marker did not return GRADLE-WRAPPER-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "[GRAPH-WRAPPER-OK-MARKER] incomplete wrapper distribution still started $($invocations.Count) wrapper calls"

    # wrapper 就绪是**七个**合取，此前只有完成标记那一个被测：图 fixture 造出的发行树太完美，删掉
    # 其余任何一个合取，四个套件仍全绿——半解压 / 版本对不上的发行树会被判为就绪，扫描器照样对它启动
    # wrapper，「冷跑者上离线零启动」这条整个故事赖以成立的保证就此静默失效。每个合取一个专属码，
    # 各配一枚把该合取改成恒真的变异（见 $mutationCases 的 wrapper-* 六枚）。
    Set-Content -LiteralPath (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok') -Encoding utf8 -Value 'fixture'
    $wrapperLauncherPath = Join-Path $distributionRoot 'lib/gradle-launcher-9.7.0.jar'
    $wrapperAltLauncherPath = Join-Path $distributionRoot 'lib/gradle-launcher-9.6.0.jar'
    $wrapperAltRootPath = Join-Path $distributionDir 'gradle-9.6.0'
    $wrapperUnixBinPath = Join-Path $distributionRoot 'bin/gradle'
    $wrapperWindowsBinPath = Join-Path $distributionRoot 'bin/gradle.bat'
    $wrapperReadinessCases = @(
      @{ Code = 'GRAPH-WRAPPER-ROOT-CARDINALITY'; Label = 'a second distribution root beside gradle-9.7.0'
         Break = { New-Item -ItemType Directory -Force -Path $wrapperAltRootPath | Out-Null }
         Restore = { Remove-Item -LiteralPath $wrapperAltRootPath -Recurse -Force } }
      @{ Code = 'GRAPH-WRAPPER-ROOT-NAME'; Label = 'the single distribution root carrying the wrong version name'
         Break = { Rename-Item -LiteralPath $distributionRoot -NewName 'gradle-9.6.0' }
         Restore = { Rename-Item -LiteralPath $wrapperAltRootPath -NewName 'gradle-9.7.0' } }
      @{ Code = 'GRAPH-WRAPPER-LAUNCHER-CARDINALITY'; Label = 'a second gradle-launcher jar in lib/'
         Break = { Set-Content -LiteralPath $wrapperAltLauncherPath -Encoding utf8 -Value 'fixture' }
         Restore = { Remove-Item -LiteralPath $wrapperAltLauncherPath -Force } }
      @{ Code = 'GRAPH-WRAPPER-LAUNCHER-NAME'; Label = 'the single launcher jar carrying the wrong version name'
         Break = { Rename-Item -LiteralPath $wrapperLauncherPath -NewName 'gradle-launcher-9.6.0.jar' }
         Restore = { Rename-Item -LiteralPath $wrapperAltLauncherPath -NewName 'gradle-launcher-9.7.0.jar' } }
      @{ Code = 'GRAPH-WRAPPER-UNIX-BIN'; Label = 'a missing bin/gradle'
         Break = { Remove-Item -LiteralPath $wrapperUnixBinPath -Force }
         Restore = { Set-Content -LiteralPath $wrapperUnixBinPath -Encoding utf8 -Value 'fixture' } }
      @{ Code = 'GRAPH-WRAPPER-WINDOWS-BIN'; Label = 'a missing bin/gradle.bat'
         Break = { Remove-Item -LiteralPath $wrapperWindowsBinPath -Force }
         Restore = { Set-Content -LiteralPath $wrapperWindowsBinPath -Encoding utf8 -Value 'fixture' } }
    )
    foreach ($wrapperCase in $wrapperReadinessCases) {
      & $wrapperCase.Break
      try {
        $invocations.Clear()
        $wrapperCaseResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
        Assert-Graph (
          $wrapperCaseResult.Errors.Count -eq 1 -and $wrapperCaseResult.Errors[0].Code -ceq 'GRADLE-WRAPPER-OFFLINE'
        ) "[$($wrapperCase.Code)] $($wrapperCase.Label) did not return GRADLE-WRAPPER-OFFLINE: $($wrapperCaseResult.Errors | ConvertTo-Json -Compress)"
        Assert-Graph (
          $invocations.Count -eq 0
        ) "[$($wrapperCase.Code)] $($wrapperCase.Label) still started $($invocations.Count) wrapper calls"
      } finally { & $wrapperCase.Restore }
    }
  } catch {
    Assert-Graph $false "resolved graph API failed: $($_.Exception.Message)"
  }
} finally {
  if ($hadGradleUserHome) { $env:GRADLE_USER_HOME = $savedGradleUserHome }
  else { Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue }
  if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}

if ($failures.Count -gt 0) {
  Write-Error "[GRAPH-CONTRACT] $($failures -join "`n[GRAPH-CONTRACT] ")"
  exit 1
}

if (-not $SkipMutations) {
  $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
  $mutationCases = @(
    @{
      Name = 'constraint'
      From = '    if ($body -match ''\s+\(c\)\s*$'') { continue } # exclude Gradle constraint-only edge'
      To = '    if ($false) { continue } # exclude Gradle constraint-only edge'
      Expected = 'constraint-only row entered resolved GAV set'
    },
    @{
      Name = 'concrete-gav'
      From = '    if ($null -eq (Get-GradleGavParts -Coordinate $resolvedCoordinate)) {'
      To = '    if ($false) {'
      Expected = 'parser/non-concrete returned wrong GAVs'
    },
    @{
      Name = 'direct-project'
      From = '        continue # direct internal Gradle project edge'
      To = '        $body = $body # direct internal Gradle project edge'
      Expected = 'parser/project-boundary returned wrong error codes'
    },
    @{
      Name = 'redirected-internal-project'
      From = '        if ($body -match $internalProjectPattern) { continue }'
      To = '        if ($false) { continue }'
      Expected = 'parser/project-boundary returned wrong error codes'
    },
    @{
      Name = 'selected-project-target'
      From = '      if ($selectedTarget -match $internalProjectPattern) { continue } # selected internal project target'
      To = '      if ($false) { continue } # selected internal project target'
      Expected = 'parser/selected-targets returned wrong error codes'
    },
    @{
      Name = 'selected-module-target'
      From = '        $body = $selectedTarget # selected external module target'
      To = '        continue # selected external module target'
      Expected = 'parser/selected-targets returned wrong GAVs'
    },
    @{
      Name = 'deduplication'
      From = '  $coordinates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)'
      To = '  $coordinates = [System.Collections.Generic.List[string]]::new()'
      Expected = 'resolved duplicate GAV was not deduplicated'
    },
    @{
      Name = 'selected-version'
      From = '      $resolvedVersion = $Matches.resolved # selected version after replacement'
      To = '      $resolvedVersion = ($tail -replace ''^:'', '''' -replace ''\s+->.*$'', '''') # selected version after replacement'
      Expected = 'parser/resolved-version returned wrong GAVs'
    },
    @{
      Name = 'project-external'
      From = '        $body = $Matches.resolved.Trim() # project external substitution target'
      To = '        continue # project external substitution target'
      Expected = 'parser/project-boundary returned wrong GAVs'
    },
    @{
      Name = 'unresolved'
      From = '    if ($body -match ''(?:^|\s)(?:FAILED|\(n\))(?:\s|$)'') { # graph unresolved edge guard'
      To = '    if ($false) { # graph unresolved edge guard'
      Expected = 'parser/unresolved-and-malformed returned wrong error codes'
    },
    @{
      Name = 'configuration-set'
      From = '  [PSCustomObject]@{ Project = '':core''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      To = ''
      Expected = 'invoked 3 configurations instead of 4'
    },
    @{
      Name = 'project-configuration-pair'
      From = '  [PSCustomObject]@{ Project = '':core''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      To = '  [PSCustomObject]@{ Project = '':app''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      Expected = 'runtimeClasspath used wrong Gradle project task'
    },
    @{
      Name = 'offline'
      From = '    $gradleArguments = @(''-p'', $androidRoot, ''--offline'', ''--no-daemon'', "$($target.Project):dependencies", ''--configuration'', $target.Configuration) # graph offline invocation'
      To = '    $gradleArguments = @(''-p'', $androidRoot, ''--online'', ''--no-daemon'', "$($target.Project):dependencies", ''--configuration'', $target.Configuration) # graph offline invocation'
      Expected = 'omitted --offline'
    },
    @{
      Name = 'posix-sh'
      From = '    $commandArguments = if ($UseWindows) { $gradleArguments } else { @($wrapper) + $gradleArguments } # POSIX wrapper via sh'
      To = '    $commandArguments = $gradleArguments # POSIX wrapper via sh'
      Expected = 'did not pass gradlew as sh argv[0]'
    },
    @{
      Name = 'windows-wrapper'
      From = '  return (Join-Path $AndroidRoot $(if ($UseWindows) { ''gradlew.bat'' } else { ''gradlew'' }))'
      To = '  return (Join-Path $AndroidRoot ''gradlew'')'
      Expected = 'Windows graph call for runtimeClasspath was not exact'
    },
    @{
      Name = 'wrapper-preflight'
      From = '  if (-not $distribution.Ready) { # graph wrapper zero-start guard'
      To = '  if ($false) { # graph wrapper zero-start guard'
      Expected = 'missing wrapper completion marker'
    },
    @{
      Name = 'cache-preflight'
      From = '  if (-not $nativeCacheReady) { # graph native cache zero-start guard'
      To = '  if ($false) { # graph native cache zero-start guard'
      Expected = 'missing native cache'
    },
    @{
      Name = 'cache-artifact-readiness'
      From = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 -and $metadataReady # native cache readiness'
      To = '      $nativeCacheReady = $metadataReady # native cache readiness'
      Expected = 'empty artifact subtree did not return GRADLE-CACHE-OFFLINE'
    },
    @{
      Name = 'cache-metadata-readiness'
      From = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 -and $metadataReady # native cache readiness'
      To = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 # native cache readiness'
      Expected = 'missing native metadata did not return GRADLE-CACHE-OFFLINE'
    },
    @{
      Name = 'cache-metadata-version'
      From = "      `$metadataRoot = Join-Path `$modulesCacheRoot 'metadata-2.107' # Gradle 9.7 native metadata format"
      To = "      `$metadataRoot = @(Get-ChildItem -LiteralPath `$modulesCacheRoot -Directory -Force -ErrorAction Stop | Where-Object { `$_.Name -match '^metadata-2\.`\d+`$' } | Select-Object -First 1).FullName # Gradle 9.7 native metadata format"
      Expected = '[GRAPH-STALE-METADATA-STARTED]'
    },
    @{
      Name = 'gradle-user-home-binding'
      From = '    $env:GRADLE_USER_HOME = $GradleUserHome # bind preflighted cache to child'
      To = '    $env:GRADLE_USER_HOME = $savedGradleUserHome # bind preflighted cache to child'
      Expected = 'did not bind preflighted GradleUserHome'
    },
    @{
      Name = 'subprocess-exit'
      From = '    if ($gradleExit -ne 0) { # graph nonzero subprocess guard'
      To = '    if ($false) { # graph nonzero subprocess guard'
      Expected = 'nonzero Gradle error lost code/exit'
    },
    @{
      Name = 'subprocess-target'
      From = '      $errors.Add([PSCustomObject]@{ Code = ''GRADLE-SUBPROCESS''; Configuration = $target.Label; ExitCode = $gradleExit; Detail = $null; Output = @($output) }) # graph subprocess target provenance'
      To = '      $errors.Add([PSCustomObject]@{ Code = ''GRADLE-SUBPROCESS''; Configuration = $null; ExitCode = $gradleExit; Detail = $null; Output = @($output) }) # graph subprocess target provenance'
      Expected = 'nonzero Gradle error lost target provenance'
    },
    # wrapper 就绪的七个合取各一枚：把该合取改成恒真，只有它对应的用例会红。此前只有 `.ok` 完成标记
    # 那一条有用例，其余六条删掉后四个套件全绿（实测：六枚变异全部存活）。
    @{
      Name = 'wrapper-ok-marker'
      From = '    (Test-Path -LiteralPath $okPath -PathType Leaf) # wrapper completion marker'
      To = '    ($true) # wrapper completion marker'
      Expected = '[GRAPH-WRAPPER-OK-MARKER]'
    },
    @{
      Name = 'wrapper-root-cardinality'
      From = '    ($distributionRoots.Count -eq 1) # wrapper root cardinality'
      To = '    ($true) # wrapper root cardinality'
      Expected = '[GRAPH-WRAPPER-ROOT-CARDINALITY]'
    },
    @{
      Name = 'wrapper-root-name'
      From = '    ($expectedDistributionRoots.Count -eq 1) # wrapper root exact name'
      To = '    ($true) # wrapper root exact name'
      Expected = '[GRAPH-WRAPPER-ROOT-NAME]'
    },
    @{
      Name = 'wrapper-launcher-cardinality'
      From = '    ($launcherJars.Count -eq 1) # wrapper launcher cardinality'
      To = '    ($true) # wrapper launcher cardinality'
      Expected = '[GRAPH-WRAPPER-LAUNCHER-CARDINALITY]'
    },
    @{
      Name = 'wrapper-launcher-name'
      From = '    ($expectedLauncherJars.Count -eq 1) # wrapper launcher exact name'
      To = '    ($true) # wrapper launcher exact name'
      Expected = '[GRAPH-WRAPPER-LAUNCHER-NAME]'
    },
    @{
      Name = 'wrapper-unix-bin'
      From = '    ($null -ne $binDir -and (Test-Path -LiteralPath (Join-Path $binDir ''gradle'') -PathType Leaf)) # wrapper unix bin'
      To = '    ($true) # wrapper unix bin'
      Expected = '[GRAPH-WRAPPER-UNIX-BIN]'
    },
    @{
      Name = 'wrapper-windows-bin'
      From = '    ($null -ne $binDir -and (Test-Path -LiteralPath (Join-Path $binDir ''gradle.bat'') -PathType Leaf)) # wrapper windows bin'
      To = '    ($true) # wrapper windows bin'
      Expected = '[GRAPH-WRAPPER-WINDOWS-BIN]'
    },
    # 未解析边守卫的 FAILED 那一半（存活 fixture 只含 `(n)`，删掉 `FAILED|` 实测四套件全绿）。
    @{
      Name = 'unresolved-failed-half'
      From = '    if ($body -match ''(?:^|\s)(?:FAILED|\(n\))(?:\s|$)'') { # graph unresolved edge guard'
      To = '    if ($body -match ''(?:^|\s)(?:\(n\))(?:\s|$)'') { # graph unresolved edge guard'
      Expected = 'parser/unresolved-failed returned wrong error codes'
    },
    # 外部依赖边尾部判定的 fail-closed（此前零覆盖）：改成静默 continue 即漏计一条覆盖缺口。
    @{
      Name = 'external-tail-failclosed'
      From = '      $errors.Add("$module => 无法判定 Gradle 外部依赖边：$displayBody [GRADLE-PARSE]") # malformed external edge'
      To = '      $null = $displayBody # malformed external edge'
      Expected = 'parser/empty-redirect-tail returned wrong error codes'
    },
    @{
      Name = 'external-tail-diagnostic-edge'
      From = '      $errors.Add("$module => 无法判定 Gradle 外部依赖边：$displayBody [GRADLE-PARSE]") # malformed external edge'
      To = '      $errors.Add("$module => 无法判定 Gradle 外部依赖边 [GRADLE-PARSE]") # malformed external edge'
      Expected = 'omitted original edge text'
    },
    # 「选中目标是畸形 project 边」的 fail-closed（此前零覆盖）。
    @{
      Name = 'selected-project-failclosed'
      From = '        $errors.Add("无法判定 Gradle 选中 project 目标：$displayBody [GRADLE-PARSE]")'
      To = '        $null = $displayBody'
      Expected = 'parser/malformed-selected-project returned wrong error codes'
    },
    # 「Gradle 退出 0、却零个可解析坐标且零个解析错误」的 fail-closed（此前零覆盖：唯一夹具随 selftest
    # -1434 行删除）。关掉它 = 许可闸绿着报告「什么都没扫到」——本卡守的那条硬边界正是它。
    @{
      Name = 'empty-resolved-report'
      From = '      if ($parsed.Count -eq 0 -and $parseResult.Errors.Count -eq 0) {'
      To = '      if ($false) {'
      Expected = '[GRAPH-EMPTY-REPORT]'
    }
  )

  foreach ($mutationCase in $mutationCases) {
    $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
    if ($matches -ne 1) {
      Write-Error "[GRAPH-MUTATION] $($mutationCase.Name) target count=$matches"
      exit 1
    }
    $mutantPath = Join-Path $PSScriptRoot ".license-scanner-$PID-$($mutationCase.Name).ps1"
    try {
      [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
      $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite graph -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
      $mutationExit = $LASTEXITCODE
      if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
        Write-Error "[GRAPH-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
        exit 1
      }
    } finally {
      if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
    }
  }
  Write-Host "license-scanner-check(graph mutations): PASS ($($mutationCases.Count))"
}

Write-Host 'license-scanner-check(graph): PASS'
exit 0
