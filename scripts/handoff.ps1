#requires -Version 7
<#
.SYNOPSIS
  会话交接闸（planning-with-files 三件套的「不能模糊交接」执法者）。
  把「下一 session 必须零歧义续上」从口头约定变成**机读校验**。

.DESCRIPTION
  交接介质是 cwd 的三件套（gitignored，hook 按 cwd 读取）：
    task_plan.md  —— 计划：目标 + 有序步骤(勾选状态) + 当前指针 + 约束/不变量
    findings.md   —— 沉淀：本任务期间学到的事实 / 决策+理由 / 死路（避免下个 session 重踩）
    progress.md   —— 时间线 + **末尾的 HANDOFF 块**（交接的唯一权威指针）
  权威标准见 docs/HANDOFF.md。本脚本只认 progress.md 末尾的 HANDOFF 块。

  动词：
    init   在 cwd 生成缺失的三件套（含 HANDOFF 模板；占位值故意非法 => 必须填好才过 check）。
    check  校验 HANDOFF 块：缺字段 / 空值 / 残留占位 / 枚举非法 / **行动字段含模糊措辞** 即非零退出。
    show   打印 HANDOFF 块（供新 session 续接；无则提示先 init）。

  退出码：check 通过=0 / 不通过=1；其它动词成功=0。无 _config 依赖，默认配置下可独立跑。

.PARAMETER Verb  init | check | show（默认 check）
.PARAMETER Path  progress.md 路径（默认 cwd 的 progress.md）
.EXAMPLE  pwsh -File scripts\handoff.ps1 check
.EXAMPLE  pwsh -File scripts\handoff.ps1 init
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('init', 'check', 'show')][string]$Verb = 'check',
  [string]$Path = 'progress.md'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open

# --- 交接契约（与 docs/HANDOFF.md 同源；改这里须同步那里）---
$Required   = @('STATUS', 'TASK', 'CARD', 'BRANCH', 'WORKTREE', 'LAST-GREEN', 'NEXT-ACTION', 'VERIFY', 'DO-NOT', 'OPEN-QUESTIONS', 'INVARIANTS', 'UPDATED')
$StatusEnum = @('in-progress', 'blocked', 'handoff-ready', 'done')
# 行动关键字段：必须具体、可执行；不许模糊、不许占位；STATUS≠done 时不许 'none'
$ActionFields = @('LAST-GREEN', 'NEXT-ACTION', 'VERIFY')
# 模糊措辞黑名单（命中行动字段即判模糊交接）
$Vague = @('tbd', '???', 'continue where', 'where i left off', 'should work', 'figure out',
  'somehow', 'somewhere', 'as needed', 'fix it later', 'do later', 'later.', '待定', '回头', '稍后', '大概', '差不多')

function Read-Block($file) {
  if (-not (Test-Path $file)) { return $null }
  $text = Get-Content $file -Raw
  # 只认**末尾**的 HANDOFF 块（契约见头注）：若 session 误 append 新块而非原地编辑旧块，
  # progress.md 里会出现多个 HANDOFF:START/END 对；[regex]::Matches 取全部匹配、采最后一个——
  # 防旧 [regex]::Match 懒惰首匹配把过期首块当权威指针（TD57/TD-120）。
  $ms = [regex]::Matches($text, '(?s)<!--\s*HANDOFF:START\s*-->(.*?)<!--\s*HANDOFF:END\s*-->')
  if ($ms.Count -gt 0) { return $ms[$ms.Count - 1].Groups[1].Value } else { return '' }
}

function Get-Fields($block) {
  $h = [ordered]@{}
  foreach ($line in ($block -split "`r?`n")) {
    $mm = [regex]::Match($line, '^\s*([A-Z][A-Z\-]+):\s*(.*)$')
    if ($mm.Success) {
      # TD63 item11：此前无条件剥离字段值内任何 ' #...' 后缀（当作行尾注释）——但字段值本就可能合法含
      # 空格+井号（如颜色码 " #FFFFFF"、shell 命令里的字面 # 文本），会被静默截断丢失半截内容。不再剥离，
      # 字段值原样保留（含用户自己写的任何 ' #' 文本）。
      $val = $mm.Groups[2].Value.Trim()
      $h[$mm.Groups[1].Value] = $val
    }
  }
  return $h
}

$HandoffTemplate = @'
<!-- HANDOFF:START -->
<!-- STATUS 枚举: in-progress | blocked | handoff-ready | done -->
<!-- [HANDOFF-REVALIDATE] 执行 NEXT-ACTION 前先核它是否仍成立：STATUS 记的阻塞前提还在吗？该卡是否已被别的卡覆盖、已作废、或有更小的解法？ -->
STATUS: in-progress
TASK: <一句话：本 session 在做什么>
CARD: <specs/tasks/<id>.md 或 none>
BRANCH: <分支名 或 main>
WORKTREE: <worktree 绝对路径 或 (main checkout)>
LAST-GREEN: <最近已知良好态：<sha8> — 通过了什么，如 "dod_command exit 0" / "selftest PASS">
NEXT-ACTION: <下一步**可直接复制粘贴**的命令 或 具体首步>
VERIFY: <一条确认当前态的命令>
DO-NOT: <雷区/已试死路/冻结路径，无则 none>
OPEN-QUESTIONS: <需用户拍板的决策，无则 none>
INVARIANTS: <在场的冻结契约/关键不变量，无则 none>
UPDATED: <session 标记，如 卡号 + step n + 简短时刻>
<!-- HANDOFF:END -->
'@

switch ($Verb) {
  'init' {
    $made = @()
    $files = @{
      'task_plan.md' = "# 计划（task_plan）`n`n> 目标 + 有序步骤(勾选) + 当前指针 + 约束/不变量。真相源是项目计划/卡片，本文件是**当前 session 的工作视图**。`n`n## 目标`n<一句话>`n`n## 步骤`n- [ ] step 1 — <做什么> → 验证: <检查>`n- [ ] step 2`n`n## 约束 / 不变量（不可违反）`n- <如：冻结契约、离线/确定性、allow_paths>`n"
      'findings.md'  = "# 沉淀（findings）`n`n> 本任务期间学到的**事实 / 决策+理由 / 死路**。append-only 倾向：别删历史，改主意就新增一条标注 supersedes。`n`n## 事实`n- `n`n## 决策（含理由）`n- `n`n## 死路 / 别再试`n- `n"
      'progress.md'  = "# 进度（progress）`n`n> 时间线 + 末尾 HANDOFF 块。**HANDOFF 块是交接的唯一权威指针**（标准见 docs/HANDOFF.md）。`n`n## 时间线`n- <时刻> <发生了什么>`n`n$HandoffTemplate"
    }
    foreach ($name in $files.Keys) {
      if (-not (Test-Path $name)) { Set-Content -Path $name -Value $files[$name] -Encoding utf8; $made += $name }
    }
    if ($made.Count) { Write-Host "已生成：$($made -join ', ')。填好 progress.md 的 HANDOFF 块后跑 handoff check。" -ForegroundColor Green }
    else { Write-Host '三件套已存在，未覆盖。' -ForegroundColor Yellow }
    exit 0
  }

  'show' {
    $block = Read-Block $Path
    if ($null -eq $block) { Write-Warning "未找到 $Path —— 先 `pwsh -File scripts\handoff.ps1 init`。"; exit 1 }
    if (-not $block.Trim()) { Write-Warning "$Path 无 HANDOFF 块。"; exit 1 }
    Write-Host '<!-- HANDOFF:START -->' -ForegroundColor Cyan
    Write-Host $block.Trim()
    Write-Host '<!-- HANDOFF:END -->' -ForegroundColor Cyan
    exit 0
  }

  'check' {
    $block = Read-Block $Path
    if ($null -eq $block) { Write-Warning "未找到 $Path —— 交接无指针。先 `pwsh -File scripts\handoff.ps1 init` 并填 HANDOFF 块。"; exit 1 }
    if (-not $block.Trim()) { Write-Warning "$Path 缺 HANDOFF:START/END 块 —— 交接无权威指针（见 docs/HANDOFF.md）。"; exit 1 }

    $f = Get-Fields $block
    $errs = @()

    foreach ($k in $Required) {
      if (-not $f.Contains($k)) { $errs += "缺字段 $k"; continue }
      $v = $f[$k]
      if ([string]::IsNullOrWhiteSpace($v)) { $errs += "$k 为空" ; continue }
      # TD63 item11：此前逢 `<` 或 `>` 任一字符即拒（`[<>]`），连带禁掉裸 shell 重定向符（如 `*> out.log`）
      # 写进行动字段——但那不是占位符，只是恰好含 `>`。只识别**成对**占位符 `<...>`（真占位符形态）。
      if ($v -match '<[^>]*>') { $errs += "$k 残留占位（未填）：$v" ; continue }
    }
    if ($f.Contains('STATUS') -and $f['STATUS'] -and ($f['STATUS'] -notin $StatusEnum)) {
      $errs += "STATUS='$($f['STATUS'])' 非法（应 $($StatusEnum -join '|')）"
    }
    $isDone = $f.Contains('STATUS') -and $f['STATUS'] -eq 'done'
    foreach ($k in $ActionFields) {
      if (-not $f.Contains($k)) { continue }
      $v = $f[$k]; if ([string]::IsNullOrWhiteSpace($v)) { continue }
      $low = $v.ToLower()
      foreach ($t in $Vague) { if ($low.Contains($t)) { $errs += "$k 含模糊措辞「$t」：交接须具体可执行（$v）"; break } }
      if (-not $isDone -and $low -in @('none', '—', '-', 'n/a', 'na')) {
        $errs += "$k='$v'：STATUS≠done 时行动字段不能为空占位，须给出具体下一步/校验命令"
      }
    }

    # 存活性校验（C31）：字段合法≠续接环境仍在。STATUS 为**可续接态**（非 done）时，校验 WORKTREE 路径与 BRANCH 仍存在——
    # 防「新 session 照 HANDOFF 指针 cd 进已被 cleanup 拆除的 worktree / checkout 到已合并删除的分支」。
    # done 态跳过：ship 后 cleanup 拆 worktree、合并删分支属预期，非错。git 校验防御式（非 git 仓/git 不可用即跳过），保「无 _config 依赖、独立可跑」契约。
    if (-not $isDone) {
      # WORKTREE：非 (main checkout) 哨兵、非占位（占位已被上方字段校验拦）→ 须在磁盘上存在
      if ($f.Contains('WORKTREE')) {
        $wt = $f['WORKTREE']
        if ($wt -and $wt -ne '(main checkout)' -and $wt -notmatch '[<>]' -and -not (Test-Path -LiteralPath $wt)) {
          $errs += "WORKTREE 路径不存在：$wt —— 可能已被 cleanup 拆除；续接前须重建 worktree 或更新 HANDOFF 指针"
        }
      }
      # BRANCH：非 main/master、非占位 → 须在 git 中存在（无 git/非 git 仓则跳过，不误判）
      if ($f.Contains('BRANCH')) {
        $br = $f['BRANCH']
        if ($br -and $br -notin @('main', 'master') -and $br -notmatch '[<>]') {
          $PSNativeCommandUseErrorActionPreference = $false   # 原生命令非零按退出码判、不抛（对环境把它设 $true 亦健壮）
          $inRepo = $false
          try { $inRepo = ((& git rev-parse --is-inside-work-tree 2>$null) -eq 'true') -and ($LASTEXITCODE -eq 0) } catch { $inRepo = $false }
          if ($inRepo) {
            & git rev-parse --verify --quiet "refs/heads/$br" 1>$null 2>$null
            if ($LASTEXITCODE -ne 0) {
              $errs += "BRANCH 不存在于 git：$br —— 分支可能已合并删除；续接前须确认/重建分支或更新 HANDOFF 指针"
            }
          }
        }
      }
    }

    if ($errs.Count) {
      Write-Host "交接校验：FAIL（$Path）" -ForegroundColor Red
      $errs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
      Write-Host '  → 模糊交接被拒。补全后重跑（标准见 docs/HANDOFF.md）。' -ForegroundColor DarkGray
      exit 1
    }
    Write-Host "交接校验：PASS（$Path · STATUS=$($f['STATUS'])）" -ForegroundColor Green
    Write-Host "  下一步：$($f['NEXT-ACTION'])"
    Write-Host '  [HANDOFF-REVALIDATE] 执行它之前先核它是否仍成立：STATUS 记的阻塞前提还在吗？该卡是否已被别的卡覆盖、已作废、或有更小的解法？' -ForegroundColor DarkGray
    exit 0
  }
}
