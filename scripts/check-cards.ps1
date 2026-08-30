#requires -Version 7
<#
.SYNOPSIS
  任务卡静态校验：在动手前/CI 里机检 specs\tasks\*.md 的 front-matter 自洽，
  把「卡写错到 ship 才暴露」提前到 start/selftest/CI。

.DESCRIPTION
  卡片是「计划 → 可执行」的薄投影，task.ps1 / review.ps1 全程以**卡 id** 派生
  分支名 / worktree / 卡路径（specs\tasks\<id>.md），却**忽略**卡内 branch/worktree 字段——
  故 id 与文件名/branch 漂移会**静默**让 review.ps1 找错卡。本校验守住这层耦合。

  错误（exit 1）：
    - 无 front-matter（须 --- 包裹的 YAML 头）。
    - id 缺失 / 与文件名不一致 / 含非法字符（分支·worktree·文件名共用，禁空白与 \ / : ~ ^ ? * [ ]）。
    - id 不符规范命名 'T<阶段号>-<大写短横名>'（正则 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$；
      示例 T0-SCAFFOLD / T2-API / T3-REVIEW-GATE；反例 t1-foo（小写）/ T1_FOO（下划线）/ my-task（缺 T 阶段号）。
      此规范让 AI 编码 agent 能确定性地派生/自检卡名，而非靠看示例猜。下游若要换 ID 体系，改下方 $idPattern 即可）。
    - status 缺失或不属 todo|in-progress|in-review|merged。
    - branch 存在却 ≠ id；worktree 末段 ≠ id（漂移）。
    - dod_command 缺失/空（task.ps1 ship 的 DoD 闸门据此执行）。
    - dod_command 是 no-op（echo/true/exit 0/Write-Host…，且无 && / | 串接真命令）——DoD 须真跑验证，
      否则卡可「假绿」过 ship（修「TDD 未被 DoD 强制」）。
    - dod_command 嵌套 `pwsh … -Command "…$var…"` 且内含会被内插的 $ 变量——task.ps1 双层 `pwsh -Command` 执行下
      内层 $var 被中间 shell 内插成空串、孙 shell ParserError exit 1，`-Phase red` 误当合法 RED（vacuous RED；TD69/L95）。
    - allow_paths 缺失（review.ps1 据此判越界）。
    - acceptance 存在时非 >=3 条块式双引号字符串，或编号非严格 A1..An。
    - 卡文含模板占位符 token 字面量（双大括号包裹**大写蛇形**名，-cmatch 严格大写）——真 token 只应出现在模板产物；
      混进卡文，init 干跑冒烟会替换污染卡 / 留残留占位符触发失败，卡登记直推 master 即 CI 红（L61/TD111；selftest 闸10g 回归）。
    - 全卡模式：parallelizable_with（可选字段）声明并行的卡对，allow_paths 归一化前缀重叠——
      对称处理（单向声明即比对）；并行 worktree 互不重叠是并行前提（单卡 -TaskId 模式跳过跨卡检查）。
      重叠判定与列表解析带内建种子自检（同 selftest 闸 17 思路），逻辑退化即整体 FAIL。
  警告（仍 exit 0）：title 缺失；acceptance 缺失（可选作者声明）；字段疑似仍含占位符（path/to/… 或 <…>）；allow_paths > 5 条（右尺寸启发式：卡可能过大，宜拆）；
    动 frontend/ 的卡其 dod_command 未含前端测试闸（verify/vitest/playwright）。

  仅校验真实卡，跳过 _TEMPLATE.md（其 T?-EXAMPLE 占位故意违规）。无真实卡 => PASS（脚手架期）。
  本脚本不依赖 _config.ps1，默认配置下可干跑。

.PARAMETER TaskId  给定则只校验 specs\tasks\<TaskId>.md；否则校验全部真实卡。
.EXAMPLE
  pwsh -File scripts\check-cards.ps1
  pwsh -File scripts\check-cards.ps1 -TaskId T1-FOO
#>
[CmdletBinding()]
param([string]$TaskId)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$TasksDir = Join-Path $RepoRoot 'specs/tasks'
. (Join-Path $PSScriptRoot '_cards.ps1')

$validStatus = @('todo', 'in-progress', 'in-review', 'merged')
# 规范任务卡 ID：T<阶段号>-<大写短横名>（下游换 ID 体系改这一处即可）。
$idPattern = '^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$'
$cardErrors = @()
$cardWarns = @()
$cardMeta = @{}   # 卡名 → @{ allow=<allow_paths 项>; par=<parallelizable_with 项> }，供跨卡并行重叠检查（仅全卡模式）

# --- 选卡 ---
if ($TaskId) {
  $card = Join-Path $TasksDir "$TaskId.md"
  if (-not (Test-Path $card)) { throw "任务卡不存在: $card" }
  $cards = @($card)
} else {
  $cards = @(Get-ChildItem $TasksDir -Filter *.md -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -ne '_TEMPLATE.md' } | ForEach-Object FullName)
}
if (-not $cards -or $cards.Count -eq 0) {
  Write-Host '（specs\tasks\ 无真实任务卡，仅 _TEMPLATE.md）——跳过，视为通过。' -ForegroundColor DarkGray
  exit 0
}

foreach ($cf in $cards) {
  $name = [IO.Path]::GetFileNameWithoutExtension($cf)
  $raw = Get-Content $cf -Raw
  $fm = Get-FrontMatter $raw
  if (-not $fm) { $cardErrors += "[$name] front-matter 缺失（须以 --- 包裹的 YAML 头）"; continue }

  $id = Get-UncommentedValue (Get-Scalar $fm 'id')
  $title = Get-UncommentedValue (Get-Scalar $fm 'title')
  $status = Get-UncommentedValue (Get-Scalar $fm 'status')
  $branch = Get-UncommentedValue (Get-Scalar $fm 'branch')
  $wt = Get-UncommentedValue (Get-Scalar $fm 'worktree')
  $dod = Get-Scalar $fm 'dod_command'    # 不剥注释，仅判非空

  # id：必填 + 与文件名一致 + 字符合法
  if (-not $id) { $cardErrors += "[$name] id 缺失" }
  else {
    if ($id -ne $name) { $cardErrors += "[$name] id='$id' 与文件名不一致（应为 '$name'；task.ps1/review.ps1 以文件名=id 定位）" }
    if ($id -match '[\s\\/:~^?*\[\]]') {
      $cardErrors += "[$name] id='$id' 含非法字符（分支/worktree/文件名共用，禁空白与 \ / : ~ ^ ? * [ ]）"
    } elseif ($id -cnotmatch $idPattern) {   # -cnotmatch：大小写敏感，否则 [A-Z] 会放过小写
      $cardErrors += "[$name] id='$id' 不符规范命名 'T<阶段号>-<大写短横名>'（正则 $idPattern；示例 T0-SCAFFOLD / T2-API / T3-REVIEW-GATE；反例 t1-foo / T1_FOO / my-task）"
    }
  }
  if (-not $title) { $cardWarns += "[$name] title 缺失（一句话产出）" }

  # status 枚举
  if (-not $status) { $cardErrors += "[$name] status 缺失" }
  elseif ($status -notin $validStatus) { $cardErrors += "[$name] status='$status' 非法（应 ∈ $($validStatus -join ' | ')）" }

  # branch / worktree 一致性（二者会被 task.ps1 忽略 → 漂移即隐患）
  if ($branch -and $id -and ($branch -ne $id)) { $cardErrors += "[$name] branch='$branch' 与 id='$id' 不一致（task.ps1/review.ps1 以 id 为准，卡内 branch 会被忽略 → 漂移）" }
  if ($wt -and $id) { $leaf = Split-Path $wt -Leaf; if ($leaf -ne $id) { $cardErrors += "[$name] worktree 末段 '$leaf' 与 id='$id' 不一致" } }

  # dod_command：必填非空
  if ([string]::IsNullOrWhiteSpace($dod)) { $cardErrors += "[$name] dod_command 缺失/为空（task.ps1 ship 的 DoD 闸门据此执行）" }
  # TD63 item5：YAML block-scalar（`dod_command: |` / `>` 等）会被本文件的单行取值器（Get-Scalar，非多行 YAML
  # 解析）截断成裸的指示符字面量（如 `|`），既通过上面的非空校验，又通过下方的 no-op 判定（分段后两侧皆空、
  # $dodSegments.Count=0 使 no-op 分支不触发）——静默放行成一张「看似合法」的卡，ship 阶段真执行这个裸管道符
  # 会产生诡异解析错误（远且贵）。校验期直接拒绝，给出可操作错误信息。
  elseif ($dod.Trim() -match '^[|>][+\-]?[0-9]*\s*(#.*)?$') {
    $cardErrors += "[$name] dod_command='$($dod.Trim())' 是 YAML block-scalar 指示符——本校验器只支持单行标量，写成 dod_command: | 或 dod_command: > 这类多行 block-scalar 会被截断成裸的 '$($dod.Trim())' 字面量（而非其后续缩进内容），ship 执行时会因前导管道符产生诡异解析错误。请把 dod_command 改写为单行命令（多条命令用 ; 或 && 串接）。"
  }
  else {
    # 不得是 no-op：DoD 闸门须真跑验证；echo/true/exit 0/Write-Host 之类空命令会让卡「假绿」过 ship。
    # 串接守卫按「分段」判定（TD46）：把 dod_command 按 && / || / ; / | 切成段，逐段判断是否 no-op——
    # 只要「至少一段」不是 no-op（如 pytest/verify.ps1 等真命令）就判「真 DoD」放行；
    # 只有「每一段」都命中 no-op 模式（如 `echo a; echo b`、`echo x && echo done`）才拒绝。
    # 此前的实现只判「是否存在 && 或 ;」就整体豁免、从不看分隔符右侧内容——`echo a; echo b` 这类
    # 纯 no-op 链能骗过闸、假绿过 ship（TD46）。单命令（无分隔符）行为不变：整串即唯一一段。
    $dodCmd = (Get-UncommentedValue $dod)
    if ($dodCmd) { $dodCmd = $dodCmd.Trim().Trim('"').Trim("'").Trim() }
    $noopPattern = '(?i)^(echo|true|exit\s+0|rem|write-host)\b|^:(\s|$)'
    if ($dodCmd) {
      $dodSegments = @($dodCmd -split '&&|\|\||;|\|' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
      $hasRealSegment = @($dodSegments | Where-Object { $_ -notmatch $noopPattern }).Count -gt 0
      if ($dodSegments.Count -gt 0 -and -not $hasRealSegment) {
        $cardErrors += "[$name] dod_command='$dodCmd' 是 no-op——DoD 须真跑验证（pytest/vitest/playwright/verify 等），否则卡可假绿过 ship。"
      }
    }
    # TD33/L60：dod_command 内嵌重型套件（selftest/pytest/npm test）→ 评审沙箱（ConstrainedLanguage/只读）可能不可复跑，
    # 评审者无法复跑 DoD 即按「不确定即 block」连环挡（L60 症状）。建议性（不阻断，同 >5 allow_paths 形态）：
    # DoD 宜留沙箱可复跑的轻量静态断言（Select-String/Test-Path 类），重型套件的强制点放 CI 闸。
    # 命中「调用」重型套件（-File …selftest.ps1 / pytest / npm test），不误伤「引用其路径」的轻量断言
    # （如 `Select-String -Path scripts/selftest.ps1`——正是 L60 推荐的沙箱可复跑形态，不该 warn）。
    if ($dodCmd -and ($dodCmd -match '(?i)-File\s+\S*selftest\.ps1|\bpytest\b|\bnpm\s+(run\s+)?test\b')) {
      $cardWarns += "[$name] dod_command 内嵌重型套件（selftest/pytest/npm test）——评审沙箱可能不可复跑，宜留沙箱可复跑的轻量静态断言（Select-String/Test-Path），重型套件的强制点放 CI 闸（见 L60/L62）"
    }

    # TD69/L95：dod_command 嵌套 `pwsh … -Command "…"` 且**双引号载荷内**含会被内插的 `$` → 双层包裹下静默铸成
    # vacuous RED。task.ps1 以 `& pwsh -NoProfile -Command <dod 原文>` 执行本字段（red 相 / ship 相皆然）：中间
    # shell **解析并执行** <dod>，届时 `pwsh -Command "…$ok…"` 里的双引号载荷会被中间 shell 先行内插——`$ok` 变空
    # 串，孙 shell 只收到 `if (-not ) { … }` → ParserError → exit 1；`-Phase red` 只看退出码非零、遂把「命令根本
    # 没跑起来」当合法 RED 收下（vacuous RED，GREEN 永不可达）。校验期确定性拒绝、给可操作修法。
    #
    # 判定用 **PowerShell 自己的解析器**（= 中间 shell 的真语义；codex R3 九轮 + Fable 5 复审后从正则改为真解析——正则
    # 做不到反引号奇偶转义、多段字符串边界、括号/拼接包裹、脚本块边界、-Command 的多种拼写）：先看 ParseInput 有无语法
    # 错误（dod 本身解析不通 → 中间 shell 直接 ParserError → vacuous RED 的另一扇门，Fable R3），无错再逐调用分析：
    # 找每个嵌套 PowerShell 主机（`pwsh`/`powershell`，可含 .exe/路径）调用，**只判其 -Command 家族参数的载荷**（那才会
    # 成为孙 shell 的脚本；遇 **-File 家族**即停扫本调用——其后是脚本路径 + 有意求值的脚本实参、非 vacuous RED，防越权误拦
    # `pwsh -File $runner … -Command "…"`，Fable R3 D3）。-Command 消费其后**所有**元素作为「命令 + 参数」（未加引号多 token
    # 如 `-Command Write-Output $ok` 会解析成多个独立元素），逐个搜其子树里是否有会被中间 shell 内插、**且不在脚本块体内**
    # 的节点（变量 `$x` / 子表达式 `$(…)`）——有则判危险。**-Command 拼写全认**：单横线 -c/…/-command（参数名与 'command'
    # 互为前缀）、GNU 双横线 --c/…/--command、**引号包裹 "-Command"/'-c'**（后两者 AST 均解析成裸字符串常量，实测 pwsh argv
    # 不留引号、绑定等价，Fable R3 D1）；位置裸串经实测由 pwsh 当 -File、不在此列。**-Command 与 -CommandWithArgs 语义不同**：
    # -Command 消费其后所有元素（多 token 都查）；-CommandWithArgs（前缀 commandw… 或独立别名 **cwa**，Fable R3 D2）只 command
    # 串本身是命令、其后是**实参**（有意求值传 $args，不查）——故只查其 command 串。由此天然正确处理：单引号载荷（`$` 字面）、
    # 反引号转义与**偶数**反引号、括号/拼接包裹里的可展开后代（`("…$ok…")`）、**载荷之外**的 `$`（别的命令/语句，如
    # `…-Command "…"; Write-Host "$x"`）、-File 变量实参、以及 **scriptblock** `-Command { …$ok… }`（脚本块由孙 shell 求值）。
    # **已知静态限**（Fable R3，写进卡 non_goals）：位置脚本后的 -Command 会保守误拦（`pwsh a.ps1 -Command "$v"`，无真实卡
    # 这样写）；变量主机 `& $pw -Command "…"` 主机名静态不可解、不查（GetCommandName 返回 null）。
    $td69Tok = $null; $td69Err = $null
    $dodAst = [System.Management.Automation.Language.Parser]::ParseInput($dod, [ref]$td69Tok, [ref]$td69Err)
    if ($td69Err -and $td69Err.Count -gt 0) {
      # dod 本身语法就不通 → task.ps1 双层 pwsh 执行时中间 shell 直接 ParserError exit 1、被 -Phase red 误当合法 RED
      # （vacuous RED 的另一扇门，同 TD69/L95 一类；Fable R3）。dod 惯用形态 `pwsh -Command "…"` 是合法 PS 命令行、必解析通过。
      $cardErrors += "[$name] " + ('dod_command 本身无法被 PowerShell 解析（首个语法错误：' + $td69Err[0].Message + '）——task.ps1 以 `& pwsh -NoProfile -Command <dod>` 执行时中间 shell 会 ParserError exit 1、被 -Phase red 误当合法 RED（vacuous RED，同 TD69/L95 一类）。修正 dod_command 语法。')
    }
    $dodVarHazard = $false
    foreach ($pc in $dodAst.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true)) {
      $cn = $pc.GetCommandName()
      # 只看嵌套的 PowerShell 主机调用（pwsh / powershell，可含 .exe / 路径）——它们的 -Command 实参才会成为孙 shell 的脚本。
      if (-not $cn -or ($cn -notmatch '(?i)(^|[\\/])(pwsh|powershell)(\.exe)?$')) { continue }
      # 只判 **-Command 家族**参数的载荷（那才会成为孙 shell 的脚本）——**不扫** -File / 其它无关参数，否则会把
      # `pwsh -File $runner` 这类安全的 -File 变量实参误拦（越权，codex R3 Dimension #14）。-Command 的拼写有两类，
      # 都要认（codex R3）：① 单横线 -c/-co/…/-command 及 -CommandWithArgs——AST 识别为 CommandParameterAst，参数名与
      # 'command' **互为前缀**即算（前缀缩写 或 command… 扩展）；② GNU 双横线 --c/…/--command——AST 识别为 `--…` 裸
      # 字符串常量（pwsh 仍当 -Command）。位置裸串经实测由 pwsh 当 **-File**（非 -Command）、不在此列。
      $ce = $pc.CommandElements
      for ($ei = 1; ($ei -lt $ce.Count) -and (-not $dodVarHazard); $ei++) {   # 从 1 起：跳过命令名（元素 0）
        $el = $ce[$ei]
        # 取「参数名」$pn：CommandParameterAst 直接有 ParameterName（-Command…）；被引号包裹（"-Command"/'-c'）或 GNU
        # 双横线（--Command）的参数被 AST 解析成裸字符串常量，需从 .Value 剥前导横线（单/双横线皆可）得名——实测 pwsh
        # 对引号形态、单/双横线形态一视同仁地绑定（Fable R3：argv 不留引号，故 `pwsh "-Command" "…$ok…"` 与裸 -Command 等价）。
        $pn = $null; $attachedArg = $null
        if ($el -is [System.Management.Automation.Language.CommandParameterAst]) { $pn = $el.ParameterName; $attachedArg = $el.Argument }
        elseif ($el -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
          $sv = $el.Value
          if ($sv -and $sv.Length -gt 1 -and $sv[0] -eq '-') { $pn = $sv.TrimStart('-') }
        }
        if (-not $pn) { continue }   # 非参数标志（命令名 / 位置值 / -File 的路径值 …）——跳过
        # -File 家族（-f/…/-file，含引号形态）：pwsh 进 -File 模式，其后是脚本路径 + 脚本**实参**（其中出现的 -Command
        # 也只是脚本实参、由中间 shell 有意求值传给脚本，非 vacuous RED）——遇 -File 即停止扫描本 pwsh 调用（Fable R3 D3，
        # 防越权误拦 `pwsh -File $runner … -Command "…"`）。
        if ('file'.StartsWith($pn, [System.StringComparison]::OrdinalIgnoreCase)) { break }
        # -CommandWithArgs（前缀 commandw… 或独立别名 cwa，pwsh≥7.4）：只 command 串是命令、其后是有意求值的 $args 实参 →
        # 'first'；其余 -Command 家族（-c/…/-command，参数名与 'command' 互为前缀）：消费其后所有元素作为「命令」→ 'all'。
        # **先辨前者**（'command' 也是 'commandwithargs' 前缀，会误落 -Command）。
        $cmdKind = ''
        if ($pn.StartsWith('commandw', [System.StringComparison]::OrdinalIgnoreCase) -or ($pn -ieq 'cwa')) { $cmdKind = 'first' }
        elseif (('command'.StartsWith($pn, [System.StringComparison]::OrdinalIgnoreCase)) -or ($pn.StartsWith('command', [System.StringComparison]::OrdinalIgnoreCase))) { $cmdKind = 'all' }
        if (-not $cmdKind) { continue }   # 别的参数（-NoProfile/-ExecutionPolicy…）——跳过、继续找 -Command
        # 收集要查的载荷元素（都逐个搜其子树里会被中间 shell 内插、且不在 scriptblock 体内的节点）：
        #  - 'all'（-Command）：附着实参 + 其后**所有**元素——未加引号多 token（如 `-Command Write-Output $ok`）会被解析成
        #     多个独立命令元素、都成为「命令」由中间 shell 内插，故不能只看紧邻一个（codex R3）。
        #  - 'first'（-CommandWithArgs）：只 command 串本身（附着实参，或紧邻一个元素）；其后实参不查（有意求值传 $args）。
        $cmdArgs = @()
        if ($attachedArg) { $cmdArgs += $attachedArg }
        if ($cmdKind -eq 'all') { for ($pj = $ei + 1; $pj -lt $ce.Count; $pj++) { $cmdArgs += $ce[$pj] } }
        elseif (-not $attachedArg) { if ($ei + 1 -lt $ce.Count) { $cmdArgs += $ce[$ei + 1] } }   # 'first' 非附着：只取 command 串
        foreach ($arg in $cmdArgs) {
          if ($dodVarHazard) { break }
          foreach ($node in $arg.FindAll({ param($n) ($n -is [System.Management.Automation.Language.VariableExpressionAst]) -or ($n -is [System.Management.Automation.Language.SubExpressionAst]) }, $true)) {
            $inSb = $false; $anc = $node
            while ($anc) {
              if ($anc -is [System.Management.Automation.Language.ScriptBlockExpressionAst]) { $inSb = $true; break }
              if ([object]::ReferenceEquals($anc, $arg)) { break }
              $anc = $anc.Parent
            }
            if (-not $inSb) { $dodVarHazard = $true; break }
          }
        }
        break   # -Command 已收完其后所有元素为载荷，本 pwsh 调用无需再扫（避免把载荷元素误当另一个 -Command 标志）
      }
    }
    if ($dodVarHazard) {
      $cardErrors += "[$name] " + 'dod_command 嵌套 pwsh/powershell 调用，其 -Command 载荷（任意拼写 -c/-Command/--Command）含会被中间 shell 内插的 $ 变量（如 $ok/$_/$env:/$(…)）——task.ps1 以 `& pwsh -NoProfile -Command <dod>` 双层执行本字段，载荷里的 $ 会被中间 shell 内插成空串、孙 shell 收到坏语法 → ParserError exit 1，而 -Phase red 只看退出码非零会把它误当合法 RED（vacuous RED，GREEN 永不可达；TD69/L95）。改用无变量写法：把判断内联进 if，如 pwsh -NoProfile -Command "if (-not ((Select-String …) -and …)) { exit 1 }"（单引号载荷 / 反引号转义 / scriptblock / -File 变量实参 / 载荷外的 $ 均不受限）。'
    }
  }

  # acceptance：可选作者声明，只判规范形态，不判条目内容是否够精确。
  # 本仓无 YAML 解析依赖；故只登记块式双引号序列，允许其中的空行/注释，遇下一个顶层键即停。
  $acKeyLine = -1; $acKeyGlued = $false; $acLines = @($fm -split '\r?\n')
  for ($acI = 0; $acI -lt $acLines.Count; $acI++) {
    if ($acLines[$acI] -match '^acceptance\s*:(?<sep>[ \t]*)(?<inline>.*)$') {
      $acKeyLine = $acI; $acInline = $Matches['inline']
      $acKeyGlued = $Matches['sep'].Length -eq 0 -and $acInline.StartsWith('#')
      break
    }
  }
  if ($acKeyLine -lt 0) {
    $cardWarns += "[CARD-ACCEPTANCE-ADVISORY] [$name] acceptance missing (optional author declaration)" # CARD-ACCEPTANCE-ADVISORY-GUARD
  } else {
    $acEntry = 0; $acIndent = $null; $acShapeBad = [int]$acKeyGlued; $acNumberBad = 0; $acNumberActual = ''
    $acInlineValue = ($acInline -replace '^\s*#.*$', '' -replace '\s+#.*$', '').Trim()
    if ($acInlineValue) { $acShapeBad = 1 }
    for ($acI = $acKeyLine + 1; $acI -lt $acLines.Count; $acI++) {
      $acLine = $acLines[$acI]
      if ($acLine -match '^[^\s#].*?:') { break }
      if ([string]::IsNullOrWhiteSpace($acLine) -or $acLine -match '^\s*#') { continue }
      $acEntry++
      $acShape = if ($acLine -match '^(?<indent> +)- +(?<value>.+?)\s*$') {
        $acThisIndent = $Matches['indent']; $acValue = $Matches['value']
        if ($null -eq $acIndent) { $acIndent = $acThisIndent }
        if ($acThisIndent -cne $acIndent) { [regex]::Match('', '(?!)') }
        else { [regex]::Match($acValue, '^"(?<label>A[0-9]+)\s+(?:[^"\\]|\\(?:[ 0abtnvfre"/N_LP\\]|x[0-9A-Fa-f]{2}|u(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|U(?:0000(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|00(?:0[1-9A-Fa-f]|10)[0-9A-Fa-f]{4})))+"(?:[ \t]+#.*|[ \t]*)$') }
      } else { [regex]::Match('', '(?!)') }
      if (-not $acShape.Success -and $acShapeBad -eq 0) { $acShapeBad = $acEntry }
      if ($acShape.Success -and $acShape.Groups['label'].Value -cne "A$acEntry" -and $acNumberBad -eq 0) {
        $acNumberBad = $acEntry; $acNumberActual = $acShape.Groups['label'].Value
      }
    }
    if ($acEntry -lt 3 -and $acShapeBad -eq 0) { $acShapeBad = $acEntry + 1 }
    if ($acShapeBad -gt 0) { $cardErrors += "[CARD-ACCEPTANCE-INVALID] [$name] entry=$acShapeBad reason=shape expected=>=3-block-double-quoted-strings" } # CARD-ACCEPTANCE-SHAPE-GUARD
    if ($acNumberBad -gt 0) { $cardErrors += "[CARD-ACCEPTANCE-INVALID] [$name] entry=$acNumberBad reason=number expected=A$acNumberBad actual=$acNumberActual" } # CARD-ACCEPTANCE-NUMBER-GUARD
  }

  # allow_paths：评审越界判定所需
  if ($fm -notmatch '(?m)^allow_paths\s*:') { $cardErrors += "[$name] allow_paths 缺失（review.ps1 据此判越界）" }
  else {
    # TD60/TD-123：此前只判「键存在」，未判「有块式列表项」——`allow_paths:`（空）与行内 flow
    # `allow_paths: [a, b]` 都能让上面的键存在性检查通过，但 task.ps1 ship 阶段的范围闸提取器
    # （镜像本文件的块式行走）只认块式列表（每项一行 `  - path`）、不识别行内/空值——两者的落差
    # 此前要等 DoD/verify/commit 全部跑完、到 ship 范围闸才 fail-closed 抛（晚且贵）。
    # Get-YamlListCount 本就只数块式项（不识别行内 `[...]`），故直接拿它当「ship 能否解析」的权威判据：
    # 空值与行内 flow 两种写法在它眼里都是 0 项，一并在此拒绝。
    $apCount = Get-YamlListCount $fm 'allow_paths'
    if ($apCount -eq 0) {
      $cardErrors += "[$name] allow_paths 无块式列表项（为空，或用了 ship 提取器不识别的行内 flow 语法 '[a, b]'）——ship 范围闸只认块式列表，否则 fail-closed 拒绝合并。请改写为块式，至少一项：allow_paths:`n  - path1"
    } elseif ($apCount -gt 5) {
      # 右尺寸启发式（建议性，不阻断）：allow_paths 条目 > 5 → 卡「可能过大」，宜按右尺寸标准考虑拆分（见 PLAN-TEMPLATE / decompose-cards）。
      $cardWarns += "[$name] allow_paths 有 $apCount 条（>5）——卡可能过大，建议按右尺寸标准（一个可评审/可验证单元：单一产出/touched≈1-3；除非声明长自主档）考虑拆分"
    }
  }

  # 前端测试启发式（建议性，不阻断）：动 frontend/ 的卡，其 dod_command 宜含确定性前端测试闸
  # （npm run verify / vitest / playwright）。漏了 → 前端卡可能没真测试就过 DoD（见 frontend/README.md「前端测试」）。
  # TD63 item6：此前对**任意**键下提到 frontend/ 的列表项都命中（如 forbid/non_goals 里写「不动 frontend/」
  # 这类无关列表项也会误触发）——收窄到目标键 allow_paths（这才是「卡真的改动 frontend/」的权威来源）。
  $allowItemsFe = Get-YamlListItems $fm 'allow_paths'
  if (($allowItemsFe -match 'frontend/') -and $dod -and ($dod -notmatch '(?i)verify|vitest|playwright|test')) {
    $cardWarns += "[$name] 卡改动 frontend/ 但 dod_command 未含前端测试闸（npm run verify / vitest / playwright）——前端卡宜跑确定性测试再过 DoD（见 frontend/README.md「前端测试」）"
  }

  # 占位符残留（建议性）
  if ($fm -match 'path/to/|<[^>\r\n]+>') { $cardWarns += "[$name] 字段疑似仍含占位符（path/to/… 或 <…>），落卡前请替换为真实值" }

  # TD111/L61（拒绝式，exit 1）：卡文不得含双大括号大写蛇形 token 形态字面量（真 token 只应出现在模板产物）——
  # 混进卡文会被 init 干跑冒烟（selftest 闸 8）替换真 token 污染卡、或留非真 token 触发残留占位符失败。用 **-cmatch**
  # （大小写敏感）：-match 会把 [A-Z_] 当 [A-Za-z_]、误拒小写/混合合法形态。复发案例/背景见卡 T52。selftest 闸10g 回归。
  if ($raw -cmatch '\{\{[A-Z_]+\}\}') {
    $tok111 = $Matches[0]
    $cardErrors += "[$name] 卡文含模板占位符字面量『$tok111』（双大括号包裹大写蛇形名）——真 token 只应出现在模板产物；出现在卡文里，init 干跑冒烟（selftest 闸 8）会把真 token 替换污染卡、或留非真 token 触发『残留占位符』失败，卡登记直推 master 即 CI 红（L61/TD111）。修法：改成文字描述（如『双大括号+大写蛇形名』），勿在卡里写真字面量。"
  }

  # 收集跨卡并行重叠检查所需字段（parallelizable_with 为可选字段，缺失即空列表、不受影响）
  $cardMeta[$name] = @{ allow = @(Get-YamlListItems $fm 'allow_paths'); par = @(Get-YamlListItems $fm 'parallelizable_with') }
}

# --- 跨卡检查（仅全卡模式）：声明并行的卡对 allow_paths 必须互不重叠 ---
# 对称处理：任一方在 parallelizable_with 声明另一方即比对（单向声明即生效——手写卡常见形态）。
# 比对规则：路径归一化（正斜杠、去尾斜杠）后做段级前缀重叠（a/b 与 a/b/c 重叠；a/b 与 a/bc 不重叠）。
# 重叠 = 并行前提被破坏（并行 worktree 合并会撞）。
function Get-ParallelOverlapErrors($meta) {
  $errs = @(); $seen = @{}
  foreach ($a in @($meta.Keys)) {
    foreach ($b in @($meta[$a].par)) {
      if ($b -eq $a -or -not $meta.ContainsKey($b)) { continue }   # 自引用/未收集的卡 id：跳过
      $pairKey = (@($a, $b) | Sort-Object) -join '|'
      if ($seen.ContainsKey($pairKey)) { continue }                # 互声明的卡对只比对/报告一次
      $seen[$pairKey] = $true
      $overlaps = @()
      foreach ($pa in @($meta[$a].allow)) {
        $na = ($pa -replace '\\', '/').TrimEnd('/')
        foreach ($pb in @($meta[$b].allow)) {
          $nb = ($pb -replace '\\', '/').TrimEnd('/')
          if ($na -eq $nb -or $na.StartsWith("$nb/") -or $nb.StartsWith("$na/")) { $overlaps += "$pa ↔ $pb" }
        }
      }
      if ($overlaps.Count -gt 0) {
        $errs += "[$a ∥ $b] 声明可并行（parallelizable_with）但 allow_paths 重叠：$($overlaps -join '；')——并行卡必须互不重叠（防并行 worktree 合并冲突）"
      }
    }
  }
  return $errs
}
if (-not $TaskId) {
  # 内建种子自检（同 selftest 闸 17 的种子缺陷思路）：列表解析 + 重叠判定先对已知输入自证，再校验真实卡——
  # 已知重叠（反斜杠+深一级+单向声明）必须恰报 1 条；同前缀字符串不同路径段不得误报；未知卡 id 须跳过。
  # 逻辑退化 → 立即 FAIL，防「闸静默失效却全绿」。每次全卡运行都跑（task start / selftest 闸 ⑩ / CI），确定性覆盖。
  $seedFm = "parallelizable_with: [T9-SEED-B]   # 行内列表`nallow_paths:`n  - scripts/foo/   # 块式列表+行内注释`n  - docs/x"
  $seedMeta = @{
    'T9-SEED-A' = @{ allow = @(Get-YamlListItems $seedFm 'allow_paths'); par = @(Get-YamlListItems $seedFm 'parallelizable_with') + 'T9-MISSING' }
    'T9-SEED-B' = @{ allow = @('scripts\foo\bar.ps1'); par = @() }       # B 未回声明 A → 验证单向声明即比对
    'T9-SEED-C' = @{ allow = @('scripts/foobar'); par = @('T9-SEED-A') } # scripts/foobar 与 scripts/foo 同前缀字符串但不同路径段 → 不得误报
  }
  $seedErrs = @(Get-ParallelOverlapErrors $seedMeta)
  if ($seedErrs.Count -ne 1 -or $seedErrs[0] -notmatch 'T9-SEED-A ∥ T9-SEED-B') {
    Write-Host "错误：`n  - 内建种子自检失败：并行重叠判定/列表解析逻辑异常（期望恰 1 条 [T9-SEED-A ∥ T9-SEED-B] 重叠错误，实得 $($seedErrs.Count) 条）" -ForegroundColor Red
    Write-Host "`ncheck-cards: FAIL" -ForegroundColor Red
    exit 1
  }
  $cardErrors += @(Get-ParallelOverlapErrors $cardMeta)
}

if ($cardWarns) { Write-Host '警告（建议处理）：' -ForegroundColor Yellow; $cardWarns | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($cardErrors) {
  Write-Host '错误：' -ForegroundColor Red
  $cardErrors | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  Write-Host "`ncheck-cards: FAIL" -ForegroundColor Red
  exit 1
}
Write-Host "check-cards: PASS（校验 $($cards.Count) 张卡）" -ForegroundColor Green
exit 0
