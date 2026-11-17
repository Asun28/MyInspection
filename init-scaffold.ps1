#requires -Version 7
<#
.SYNOPSIS
  脚手架一键初始化：把本模板就地变成你新项目的可用骨架。
  在**新项目目录**（已把模板内容复制进去）里运行：填 scripts\_config.ps1 + 替换 {{TOKENS}} + 重命名模板文件。

.DESCRIPTION
  幂等性有限：设计为**跑一次**。会就地修改文件——建议在干净的新仓库（git init 后、首次提交前）跑。
  做的事：
    1. 填 scripts\_config.ps1：GhAccount / ProjectName / PythonVersion / FrozenPaths；
     并**清空** ReviewModel / ReviewEffort（元仓的实测组合不随模板下发；下游自测后再回填）。
    2. 全仓替换 {{TOKENS}}：PROJECT_NAME / PROJECT_SLUG / PROJECT_TAGLINE / PYTHON_VERSION / LESSONS_MUST_CAP / GH_ACCOUNT / SCAFFOLD_VERSION（后者源自 _config，非 CLI）。
    3. 重命名 CLAUDE.template.md -> CLAUDE.md；可选 pyproject.toml.example -> pyproject.toml（-WithPython）。
    4. 可选清理元仓专属物（-Cleanup）：删模板自述 + 元仓 CHANGELOG；并修剪元仓自身的工作态（TD129）——归档树 specs/archive、
       specs/tasks 只留 _TEMPLATE.md、specs/mutations 与 docs/adr 各只留 README.md、技术债追踪器只留登记层契约文本（删债项行）；
       docs/lessons/LEDGER.md 刻意保留。init-scaffold.ps1 本身**保留**、待你确认后手动删。
       scripts\selftest.ps1 + 其 CI（scaffold-selftest.yml）**不删**（TD15）：约 12/17 闸测的是随下游保留的
       生产脚本（task.ps1/review.ps1/check-cards.ps1/...），继续当你项目自己的工作流自检。

  改造既有仓（-Retrofit）：把脚手架**加**到已有代码库上，而非建新仓。关键区别——
    token 替换**只扫脚手架自有路径**（scripts/ .claude/ docs/ specs/ .github/ + 指定根模板文件），
    **绝不递归扫用户既有源码**，避免 clobber 其中合法的 {{...}}（Vue/Handlebars/Helm/CI 变量）。
    前提：只把脚手架基建（上述路径 + CLAUDE.template.md 等）拷进既有仓，**别拷骨架空目录**
    （backend/ frontend/ tests/… 你已有自己的）；若已有 CLAUDE.md，本脚本跳过重命名、由你手动合并。

.PARAMETER ProjectName  显示名，如 "My Project"。
.PARAMETER GhAccount    GitHub 个人账号（账号守卫用；不填则留空，首次 gh 写操作会提示先配置）。
.PARAMETER ProjectSlug  仓库/包 slug；默认由 ProjectName 小写化、空格转连字符。
.PARAMETER ProjectTagline 一句话项目描述；默认取 ProjectName。
.PARAMETER PythonVersion 后端 Python 版本（默认 3.13）。
.PARAMETER FrozenPaths  冻结物正则片段（仓库相对、正斜杠），**逗号或分号分隔的单个字符串**
                        （`pwsh -File` 不解析数组语法，故用单串内部切分），如
                        'backend/app/providers/contract\.py,backend/app/schemas/manifest'。默认空（项目还没冻结点）。
.PARAMETER WithPython   重命名 pyproject.toml.example -> pyproject.toml。
.PARAMETER Cleanup      删元仓专属物（初始化后）：TEMPLATE-README.md、CHANGELOG.md，并修剪元仓工作态（TD129：
                        specs/archive 整树 · specs/tasks 只留 _TEMPLATE.md · specs/mutations 与 docs/adr 各只留 README.md ·
                        技术债追踪器只留契约文本；docs/lessons/LEDGER.md 保留）。scripts\selftest.ps1 与
                        .github\workflows\scaffold-selftest.yml **保留**（TD15，随下游继续当工作流自检）；
                        init-scaffold.ps1 本身也不自动删，保留待你确认后手动删。
.PARAMETER Retrofit     改造既有仓模式：token 替换只扫脚手架自有路径，不递归扫用户既有源码（非破坏式）。
.PARAMETER Force        跳过「已初始化」再跑守卫（默认检测到已初始化即拒，防二次跑只套半截）。
.PARAMETER DryRun       预览：跑完全部只读校验（含再跑守卫），但不真正写盘/改名/删除；结束时打印本次会碰哪些文件的改动清单。
.EXAMPLE
  pwsh -File init-scaffold.ps1 -ProjectName "Acme Studio" -GhAccount myhandle -WithPython
.EXAMPLE
  pwsh -File init-scaffold.ps1 -ProjectName "Acme" -GhAccount myhandle -FrozenPaths 'backend/app/providers/contract\.py,backend/app/schemas/manifest' -Cleanup
.EXAMPLE
  # 既有代码库上加脚手架（先只拷 scripts/ .claude/ docs/ specs/ .github/ + CLAUDE.template.md 等进来）：
  pwsh -File init-scaffold.ps1 -ProjectName "Acme" -GhAccount myhandle -Retrofit
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$ProjectName,   # 自由文本（可含空格/撇号，如 "O'Brien Studio"）→ 写 _config 时转义，不做字符校验
  [ValidatePattern('^[A-Za-z0-9-]*$')][string]$GhAccount = '',          # GitHub 账号字符集（空或字母数字连字符）；bind-time 拒非法输入
  [string]$ProjectSlug,
  [string]$ProjectTagline,
  [ValidatePattern('^\d+\.\d+(\.\d+)?$')][string]$PythonVersion = '3.13',  # x.y 或 x.y.z
  [string]$FrozenPaths = '',          # 逗号/分号分隔（pwsh -File 不解析数组语法，故用单串内部切分）
  [ValidateRange(1, [int]::MaxValue)][int]$LessonsMustCap = 10,   # TD64/TD-127 item8：0/负数会静默写进下游 _config.ps1，令必须层封顶逻辑用荒谬上限运行
  [switch]$WithPython,
  [switch]$Cleanup,
  [switch]$Retrofit,
  [switch]$Force,         # 跳过「已初始化」再跑守卫（明知故犯时用）
  [switch]$DryRun         # 预览：不写盘，只打印改动清单（TD76-DRYRUN）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot 'scripts/_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open；本文件位于仓根,故路径含 scripts/ 前缀
$Root = $PSScriptRoot
# TD76-DRYRUN：预览模式的改动清单——DryRun 下每个写点只 Add 一条描述、不真正落盘。
$plannedChanges = [System.Collections.Generic.List[string]]::new()

if (-not $ProjectSlug) { $ProjectSlug = ($ProjectName.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-') }
# 已知限制（TD64/TD-127 item8，非本次修复范围）：重音 Latin 字符（如 "Ölbaum"）经 ToLowerInvariant() 后
# 不落在 [a-z0-9] 内，会被上面的正则整段吃掉而非转写（"lbaum" 而非 "olbaum"）——产出非空但失真的 slug，
# 不触发下方的空值拒绝路径。不做 transliteration（同下方立场：宁可不完美也不猜错），仅记录已知行为。
# TD42/TD-105：派生（或用户显式传入）的 $ProjectSlug 必须校验，且必须在**任何文件写入前**校验——
# 全 CJK（或重音字符为主）的 -ProjectName 会被上面的替换整段吃掉、派生出空字符串；空/非法 slug 经
# {{PROJECT_SLUG}} 替换会产出 `name = "-backend"` 这类违反 PEP 503/508 的包名，-WithPython 下更会写进
# 真实 pyproject.toml。故障应尽早暴露（fail-fast + 明确指引），而非留到下游 `uv sync`/pip 解析时才炸。
# 同一条正则同时守派生值与用户显式传入值——不做 transliteration/猜测（宁可拒绝也不猜错，见任务卡）。
if ($ProjectSlug -notmatch '^[a-z0-9][a-z0-9-]*$') {
  throw "ProjectSlug 无效（当前值：'$ProjectSlug'）。ProjectName '$ProjectName' 无法 derive 出有效 slug，" +
        "或显式传入的 -ProjectSlug 含非法字符。请显式传 -ProjectSlug <ascii-name>" +
        "（仅小写字母/数字/连字符，且以字母或数字开头，如 -ProjectSlug 'pipixia-studio'）。"
}
if (-not $ProjectTagline) { $ProjectTagline = $ProjectName }

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# ── 再跑守卫（治本「init 设计为跑一次；二次跑因 .Replace 无目标而静默半套」）──
# 信号：①模板已重命名（CLAUDE.template.md 没了、CLAUDE.md 在）= 非 Retrofit 下的已初始化；
#       ②_config 的 ProjectName 已非空。命中任一即拒（除非 -Force），避免半套/错值静默残留。
if (-not $Force) {
  $reasons = @()
  $tpl = Join-Path $Root 'CLAUDE.template.md'
  if (-not $Retrofit -and -not (Test-Path $tpl) -and (Test-Path (Join-Path $Root 'CLAUDE.md'))) {
    $reasons += 'CLAUDE.template.md 已重命名为 CLAUDE.md（模板已就地化）'
  }
  $cfgProbe = Join-Path $Root 'scripts/_config.ps1'
  if ((Test-Path $cfgProbe) -and ((Get-Content $cfgProbe -Raw) -match "ProjectName\s*=\s*'[^']+'")) {
    $reasons += '_config.ps1 的 ProjectName 已填（疑似已初始化）'
  }
  if ($reasons.Count) {
    throw "检测到本仓疑似已初始化：$($reasons -join '；')。init 设计为跑一次，重跑会因 .Replace 无目标而只套半截（如 ProjectName/PythonVersion 不会被再替换）。" +
          " 确需重跑请加 -Force；通常应手动改 scripts/_config.ps1 而非重跑 init。"
  }
}

# ── 1. 填 scripts\_config.ps1 ─────────────────────────────────────────────
Step '填 scripts\_config.ps1'
$cfgPath = Join-Path $Root 'scripts/_config.ps1'
if (-not (Test-Path $cfgPath)) { throw "找不到 scripts\_config.ps1（确认在模板根目录跑本脚本）。" }
$cfg = Get-Content $cfgPath -Raw
# 取脚手架版本（溯源戳，源自 _config 的 ScaffoldVersion，非 CLI 参数）；缺失回退 unknown。
$scaffoldVersion = ([regex]::Match($cfg, "ScaffoldVersion\s*=\s*'([^']*)'")).Groups[1].Value
if (-not $scaffoldVersion) { $scaffoldVersion = 'unknown' }
# 写进 _config.ps1 的值是**单引号 PS 字面量**：值里的单引号必须翻倍转义，否则一个撇号（如 "O'Brien Studio"）
# 会写出不可解析的 _config.ps1，而 init 仍报成功 → 此后所有 dot-source _config 的脚本（task/review/guard/
# gh-bootstrap/check-licenses + guard-frozen 钩子）全崩。这是高危「撇号 brick」坑，故对所有写入值统一转义。
$pnEsc = $ProjectName -replace "'", "''"
$ghEsc = $GhAccount -replace "'", "''"
$pyEsc = $PythonVersion -replace "'", "''"
# GhAccount 用值无关替换（非 .Replace 空串锚定）：元仓常态是本地已填 GhAccount 以启用账号守卫，
# selftest 闸 8 从这样的工作树拷出冒烟时也须能重填为 'smoke'；参数留空则不动既有值（不拿空串抹掉已配账号）。
# T292: every field rewrite here is anchored to its whole assignment LINE, the shape T289 landed for PlanDir.
# Unanchored, `GhAccount\s*=\s*'[^']*'` also reaches a COMMENTED look-alike (`# GhAccount = '...'`, which this
# repo's own _config.ps1 carries in its prose) and the tail of a LONGER-NAMED one (`LegacyGhAccount = '...'`),
# so a downstream config that merely mentions a field came back rewritten. Only the quoted value is replaced,
# so indentation is untouched and a line already holding the target value comes out byte-identical.
if ($GhAccount) { $cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*GhAccount[ \t]*=[ \t]*)'[^']*'(?=[ \t\r]*$)", "'$ghEsc'") }
# T190（上游 issue #266）：新下游的「生成溯源」= 它此刻被生成自的这一版，故 origin 初始 == current。
# 二者从此各走各的：`ScaffoldVersion` 随回填前进，本字段永不动，fleet 账本的 fail-closed 下限绑在它上。
# 值无关替换（同上 GhAccount 的理由）：模板里出厂是空串，但元仓本地可能已填，锚定空串会静默 no-op。
# 注意 "ScaffoldVersion" 不是 "ScaffoldOriginVersion" 的子串（Scaffold-Origin-Version），故两条正则互不误伤。
$cfg = [regex]::Replace($cfg, "ScaffoldOriginVersion\s*=\s*'[^']*'", "ScaffoldOriginVersion = '$scaffoldVersion'")
$cfg = $cfg.Replace("ProjectName = ''", "ProjectName = '$pnEsc'")
# PythonVersion/LessonsMustCap 同 GhAccount 用值无关替换（TD41/TD-104）：字面 .Replace 锚定的是「当前默认值」
# 的精确文本，若维护者日后 bump 了 _config.ps1 里的默认值，锚点即与实际内容不再相等 → 静默 no-op、下游拿到
# 陈旧值而 init 仍报成功。正则只认字段名与形状（'...'/数字），值本身随便改都不受影响。
# T292: line-anchored as above. Value-agnostic and line-anchored are independent properties - the match still
# ignores the value, it just refuses to start anywhere but at the head of the assignment line.
$cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*PythonVersion[ \t]*=[ \t]*)'[^']*'(?=[ \t\r]*$)", "'$pyEsc'")
$cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*LessonsMustCap[ \t]*=[ \t]*)\d+(?=[ \t\r]*$)", "$LessonsMustCap")
# R3 评审模型/推理档位：元仓把它们钉成自己**实测过**的组合（免疫用户级 codex 配置漂移），但那是
# 「某个模型名 + 某个 codex CLI 版本」的一次性证据——**不该随模板下发**给下游（L26：别把易变当恒久；
# 模型会改名、档位支持随模型而异、CLI 版本各异）。故 init 一律清空：下游先跑通后端默认，
# 待自己实测出可用的 <模型, 档位> 组合再回填 _config，即获同样的漂移免疫。
# T292: line-anchored as above. This pair carried the defect in the live tree - `_config.ps1` explains the
# size-tier table with a prose `ReviewEffort = 'high'`, and the unanchored match blanked that sentence too.
$cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*ReviewModel[ \t]*=[ \t]*)'[^']*'(?=[ \t\r]*$)", "''")
$cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*ReviewEffort[ \t]*=[ \t]*)'[^']*'(?=[ \t\r]*$)", "''")
# T162：ReviewEffortBySize 同理清空下发——一组 <模型, 档位> 只验证于上游当时的 codex CLI 版本，
# 下游先跑通后端默认、自己实测出可用组合再回填，才获得同样的漂移免疫（与上面两键同一理由，闸 17z 同锁）。
$cfg = [regex]::Replace($cfg, "ReviewEffortBySize\s*=\s*@\{[^}]*\}", "ReviewEffortBySize = @{}")
# T158：SelftestSkipScans 同理**一律清空下发**——它声明的是「本项目认为哪条冒烟扫描冗余」，那是**上游这个仓**
# 的判断，不是下游的。一个**预先解除武装**到货的脚手架是最糟的默认值：下游必须先全部跑起来，确认自己也有
# 对应的执行臂覆盖之后，再自己回填。变异批专门盯这一行——它若存活，下游就会继承上游的解除武装态。
$cfg = [regex]::Replace($cfg, "SelftestSkipScans\s*=\s*@\([^)]*\)", "SelftestSkipScans = @()")
# T289: PlanDir is cleared for the same reason as the keys above - what the upstream pinned for itself must
# not arrive as a product's default, and a product plan may carry commercially sensitive text. Empty is not
# off-and-broken: it resolves to today's gitignored `_local` (Get-ScaffoldPlanDir). Anchored to the whole
# assignment LINE - the shape T292 then gave every key above - so it cannot reach a commented or longer-named
# look-alike; only the quoted value is replaced, so an already-empty line comes out byte-identical.
$cfg = [regex]::Replace($cfg, "(?m)(?<=^[ \t]*PlanDir[ \t]*=[ \t]*)'[^']*'(?=[ \t\r]*$)", "''")
# 逗号/分号分隔 → 列表（pwsh -File 把参数当字面字符串，故在脚本内切分）
$frozenList = @($FrozenPaths -split '[;,]' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($frozenList.Count -gt 0) {
  # 注意：PowerShell 的 @(...) **不允许尾随逗号** → 用 ",`n" 连接、各项不自带逗号。
  $items = ($frozenList | ForEach-Object { "    '" + ($_ -replace "'", "''") + "'" }) -join ",`n"
  $block = "FrozenPaths = @(`n$items`n  )"
  # 同上改值无关正则（空白容忍）；$block 来自任意用户路径，可能含 '$'，故用 MatchEvaluator 脚本块而非
  # 字符串替换参数——.NET 正则字符串替换会对 $1/$$/${name} 做特殊展开，MatchEvaluator 原样返回、无此风险。
  $cfg = [regex]::Replace($cfg, "FrozenPaths\s*=\s*@\(\)", { $block })
}
if ($DryRun) {
  $plannedChanges.Add("写入: $cfgPath（GhAccount/ProjectName/PythonVersion/LessonsMustCap/FrozenPaths）")
  Write-Host "  [DryRun] 将写入 $cfgPath | GhAccount=$(if($GhAccount){$GhAccount}else{'(留空，稍后配置)'}) | ProjectName=$ProjectName | Python=$PythonVersion | FrozenPaths=$($frozenList.Count) 条"
} else {
  Set-Content -Path $cfgPath -Value $cfg -Encoding utf8 -NoNewline
  Write-Host "  GhAccount=$(if($GhAccount){$GhAccount}else{'(留空，稍后配置)'}) | ProjectName=$ProjectName | Python=$PythonVersion | FrozenPaths=$($frozenList.Count) 条"
}

# ── 2. 全仓替换 {{TOKENS}} ────────────────────────────────────────────────
Step '替换 {{TOKENS}}（.md/.json/.yml/.yaml/.toml/.example）'
$tokens = @{
  '{{PROJECT_NAME}}'     = $ProjectName
  '{{PROJECT_SLUG}}'     = $ProjectSlug
  '{{PROJECT_TAGLINE}}'  = $ProjectTagline
  '{{PYTHON_VERSION}}'   = $PythonVersion
  '{{LESSONS_MUST_CAP}}' = "$LessonsMustCap"
  # TD64/TD-127 item8：近死 token——GhAccount 的实际配置走 :115 对 scripts/_config.ps1 的值无关正则替换，
  # 不经这张 token map；{{GH_ACCOUNT}} 目前只出现在文档正文（CLAUDE.md/TEMPLATE-README.md）里被提及自身，
  # 无 .example/.yml/.toml payload 真正消费它。保留（非删除）以防未来模板文件需要内嵌账号名。
  '{{GH_ACCOUNT}}'       = $(if ($GhAccount) { $GhAccount } else { '<your-github-account>' })
  '{{SCAFFOLD_VERSION}}' = $scaffoldVersion   # 溯源戳：源自 _config ScaffoldVersion（非 CLI 参数）
}
$exts = '*.md', '*.json', '*.yml', '*.yaml', '*.toml', '*.example'
# -Force：Linux 把 .github/.claude 等点目录标记 Hidden，Get-ChildItem -Recurse 默认跳过隐藏目录，
#   会导致 .github/workflows/ci.yml、.claude/** 下的 {{TOKEN}} 全部漏替换（TD40）；下方 .git 排除仍生效。
$targets = Get-ChildItem -Path $Root -Recurse -File -Force -Include $exts |
  Where-Object { $_.FullName -notmatch '[\\/](\.git|node_modules|\.venv)[\\/]' }   # [\\/]：跨 OS 排除（Windows \ 与 *nix /），治「反斜杠-only 在 Unix FullName 上漏排除 .git/node_modules/.venv」
if ($Retrofit) {
  # 改造模式：只替换脚手架自有路径下的 token，绝不递归扫用户既有源码（防 clobber 合法 {{...}}）。
  $ownedDirs = @('scripts', '.claude', 'docs', 'specs', '.github')
  $ownedRootFiles = @('CLAUDE.template.md', 'TEMPLATE-README.md', 'AGENTS.md', 'pyproject.toml.example', '.env.example')
  $targets = @($targets | Where-Object {
      $rel = $_.FullName.Substring($Root.Length).TrimStart('\', '/')
      $top = ($rel -split '[\\/]')[0]
      ($top -in $ownedDirs) -or ($rel -in $ownedRootFiles)
    })
  Write-Host "  [Retrofit] token 替换仅限脚手架自有路径（$($ownedDirs -join ' / ') + 指定根模板文件），不碰用户既有源码。" -ForegroundColor DarkGray
}
$changed = 0
foreach ($f in $targets) {
  $orig = Get-Content $f.FullName -Raw
  # TD59/TD-122：零字节文件的 Get-Content -Raw 返回 $null（非空串）——$null 没有 .Replace 方法，
  # 下面的替换循环会抛「无法对 null 值的表达式调用方法」；$ErrorActionPreference=Stop 下 init 会在
  # 步骤 1 已写完 scripts\_config.ps1 后中途崩溃，随后裸重跑被上方再跑守卫拒绝（ProjectName 已填），
  # 把下游卡死在半初始化状态——pre-first-commit（无 git 提交可回滚）。空文件本就无 token 可替换，
  # 原样跳过即可（同 gate ⑤ 的 token 覆盖扫描对空/不可读文件的 try/catch 容忍手法）。
  if (-not $orig) { continue }
  $new = $orig
  foreach ($k in $tokens.Keys) { $new = $new.Replace($k, $tokens[$k]) }
  if ($new -ne $orig) {
    if (-not $DryRun) { Set-Content -Path $f.FullName -Value $new -Encoding utf8 -NoNewline }
    $changed++
  }
}
if ($DryRun) {
  if ($changed -gt 0) { $plannedChanges.Add("写入(token 替换): $changed 个文件") }
  Write-Host "  [DryRun] 将替换 $changed 个文件中的 token（未写盘）"
} else {
  Write-Host "  替换了 $changed 个文件中的 token"
}

# ── 3. 重命名模板文件 ─────────────────────────────────────────────────────
Step '重命名模板文件'
$claudeTpl = Join-Path $Root 'CLAUDE.template.md'
$claudeMd = Join-Path $Root 'CLAUDE.md'
if (Test-Path $claudeTpl) {
  if (Test-Path $claudeMd) {
    # C30：此前只 Write-Warning 跳过，不产出任何辅助合并的素材（用户仍需自己剥离 TEMPLATE-NOTE 头注
    # 才能比对）。改为额外生成 CLAUDE.scaffold-merge.md（CLAUDE.template.md 内容、已剥离头注、token
    # 已替换），不覆盖/不删既有 CLAUDE.md 或 CLAUDE.template.md，留给用户手动比对合并。
    $mergeAid = Join-Path $Root 'CLAUDE.scaffold-merge.md'
    $tplForMerge = Get-Content $claudeTpl -Raw
    $tplForMerge = [regex]::Replace($tplForMerge, '(?s)<!-- TEMPLATE-NOTE:START.*?TEMPLATE-NOTE:END -->\r?\n?', '')
    if ($DryRun) {
      $plannedChanges.Add("写入: $mergeAid（CLAUDE.md 已存在，供手动合并的剥头注副本）")
      Write-Host '  [DryRun] CLAUDE.md 已存在，跳过覆盖。将生成 CLAUDE.scaffold-merge.md 供手动比对合并。'
    } else {
      Set-Content -Path $mergeAid -Value $tplForMerge -Encoding utf8 -NoNewline
      Write-Warning '已存在 CLAUDE.md，跳过覆盖。已生成 CLAUDE.scaffold-merge.md（脚手架索引/铁律，已剥离模板头注、token 已替换）供手动比对合并进 CLAUDE.md；合并完成后可删除 CLAUDE.template.md 与 CLAUDE.scaffold-merge.md。'
    }
  }
  else {
    if ($DryRun) {
      $plannedChanges.Add("改名: $claudeTpl -> $claudeMd（并剥离模板头注）")
      Write-Host '  [DryRun] 将把 CLAUDE.template.md 改名为 CLAUDE.md（并删模板头注）'
    } else {
      Move-Item $claudeTpl $claudeMd
      # 删除模板头注块（TEMPLATE-NOTE 哨兵之间，含哨兵），使最终 CLAUDE.md 干净。
      $cm = Get-Content $claudeMd -Raw
      $cm = [regex]::Replace($cm, '(?s)<!-- TEMPLATE-NOTE:START.*?TEMPLATE-NOTE:END -->\r?\n?', '')
      Set-Content -Path $claudeMd -Value $cm -Encoding utf8 -NoNewline
      Write-Host '  CLAUDE.template.md -> CLAUDE.md（已删模板头注）'
    }
  }
}
elseif ((Test-Path $claudeMd) -and ((Get-Content $claudeMd -Raw) -match '<!-- TEMPLATE-NOTE:START')) {
  # TD65/TD-122 #3：模板已不在（说明上一次跑已 Move-Item 过），但 CLAUDE.md 仍携带 TEMPLATE-NOTE 哨兵——
  # 说明上次跑在「改名」与「剥头注」之间崩溃/中断，留下一个此前**永久无法 re-run 修复**的残局（原逻辑的
  # 剥离分支只在 `Test-Path $claudeTpl` 为真时才会进入，模板一旦不在即被永久跳过）。此处就地补一次幂等
  # 剥离：不依赖模板是否还在，只要现存 CLAUDE.md 携带哨兵就修——配合 -Force 即可修复该残局。
  if ($DryRun) {
    $plannedChanges.Add("改写: $claudeMd（剥离残留模板头注）")
    Write-Host '  [DryRun] [INIT-STALE-HEADER] CLAUDE.md still carries the template header note - it will be stripped in place (TD65/TD-122 #3)'
  } else {
    $cm = Get-Content $claudeMd -Raw
    $cm = [regex]::Replace($cm, '(?s)<!-- TEMPLATE-NOTE:START.*?TEMPLATE-NOTE:END -->\r?\n?', '')
    Set-Content -Path $claudeMd -Value $cm -Encoding utf8 -NoNewline
    Write-Host '  [INIT-STALE-HEADER] CLAUDE.md carried the template header note (a previous run likely stopped between the rename and the strip) - stripped in place (TD65/TD-122 #3).'
  }
}
if ($WithPython) {
  $pyTpl = Join-Path $Root 'pyproject.toml.example'
  $pyToml = Join-Path $Root 'pyproject.toml'
  if (Test-Path $pyTpl) {
    if (Test-Path $pyToml) { Write-Warning '已存在 pyproject.toml，跳过。' }
    elseif ($DryRun) { $plannedChanges.Add("改名: $pyTpl -> $pyToml"); Write-Host '  [DryRun] 将把 pyproject.toml.example 改名为 pyproject.toml' }
    else { Move-Item $pyTpl $pyToml; Write-Host '  pyproject.toml.example -> pyproject.toml' }
  }
} else {
  Write-Host '  （未加 -WithPython：保留 pyproject.toml.example，非 Python 项目可删）'
}

# ── 4. 可选清理 ───────────────────────────────────────────────────────────
if ($Cleanup) {
  Step '清理元仓专属物（模板自述 + CHANGELOG；init-scaffold.ps1 保留待手动删）'
  # C09：TEMPLATE-README.md 是仓内唯一的英文文档；无条件删除会让下游只剩纯中文 CLAUDE.md
  # （TD21 的「> EN:」摘要只是四份治理文档各一行，不构成完整 on-ramp）。删除前先把其
  # 「TL;DR (English)」引用块提炼另存为 README.md（若尚无），留一个存活的英文入口。
  $tplReadme = Join-Path $Root 'TEMPLATE-README.md'
  $readmeOut = Join-Path $Root 'README.md'
  $readmeExtractedInDryRun = $false   # 只在 DryRun 分支内置真；非 DryRun 路径恒 $false、不改变其判定
  if ((Test-Path $tplReadme) -and -not (Test-Path $readmeOut)) {
    $trContent = Get-Content $tplReadme -Raw
    $tldrMatch = [regex]::Match($trContent, '(?ms)^> ## TL;DR \(English\).*?(?=\r?\n(?!>))')
    if ($tldrMatch.Success) {
      $tldrLines = $tldrMatch.Value -split '\r?\n' | ForEach-Object { $_ -replace '^>\s?', '' }
      if ($DryRun) {
        $plannedChanges.Add("写入: $readmeOut（从 TEMPLATE-README.md 提炼 TL;DR）")
        Write-Host '  [DryRun] 将从 TEMPLATE-README.md 提炼 TL;DR (English) 写入 README.md（删除前存活英文 on-ramp）'
        $readmeExtractedInDryRun = $true
      } else {
        Set-Content -Path $readmeOut -Value ("# $ProjectName`n`n" + ($tldrLines -join "`n") + "`n") -Encoding utf8 -NoNewline
        Write-Host '  已从 TEMPLATE-README.md 提炼 TL;DR (English) 存入 README.md（删除前存活英文 on-ramp）。'
      }
    }
  }
  # TD65/TD-122 #2：删除动作须门控于「已确认存在存活的英文 on-ramp」——上面的提炼只在 TL;DR 正则命中时
  # 才写 README.md；此前 Remove-Item 是无条件的，若正则未命中（标题被改、或 blockquote 未在空行处正常
  # 收尾），仓内唯一英文文档会被删且无替代品（pre-first-commit，无法 git 回滚）。只有 README.md 确实存在
  # （本次刚提炼成功，或此前已有）才删 TEMPLATE-README.md；否则保留原文件并告警，留给用户手动处理。
  # （$readmeExtractedInDryRun 只补 DryRun 下"README.md 尚未真的写盘但已规划提炼"这一分支——非 DryRun
  #   路径的判定仍是原始 `Test-Path $readmeOut` 本身，未改动。）
  $readmeDeleted = $false
  if (Test-Path $tplReadme) {
    if ((Test-Path $readmeOut) -or $readmeExtractedInDryRun) {
      if ($DryRun) { $plannedChanges.Add("删除: $tplReadme（英文 on-ramp 已存在/已提炼）"); Write-Host '  [DryRun] 将删除 TEMPLATE-README.md（README.md 已存在或已提炼）' }
      else { Remove-Item $tplReadme -ErrorAction SilentlyContinue }
      $readmeDeleted = $true
    }
    else {
      Write-Warning '未能从 TEMPLATE-README.md 提炼出 TL;DR (English)（正则未命中，标题或格式已变）——已保留 TEMPLATE-README.md 而非删除，请手动确认英文 on-ramp 后再清理。'
    }
  }
  # CHANGELOG.md 记的是**脚手架自身**的发布历史（元层），下游应另起自己产品的 CHANGELOG，故与 TEMPLATE-README.md 同属元仓专用、一并删除。
  # TD64/TD-127 item8：删除前门控于「确实是脚手架自己的 CHANGELOG」（镜像上方 TEMPLATE-README 的提炼成功
  # 门控）——只有含自证标记（本文件是**元仓专属**）才删，否则保留 + 告警，防止用户已把自己产品的 CHANGELOG.md
  # 放在同名路径时被无条件 Remove-Item 误删。
  $changelogOut = Join-Path $Root 'CHANGELOG.md'
  $changelogDeleted = $false
  if (Test-Path $changelogOut) {
    $clRaw = Get-Content $changelogOut -Raw
    if ($clRaw -and ($clRaw -match '本文件是\*\*元仓专属\*\*')) {
      if ($DryRun) { $plannedChanges.Add("删除: $changelogOut（含脚手架自证标记）"); Write-Host '  [DryRun] 将删除 CHANGELOG.md（含脚手架自证标记）' }
      else { Remove-Item $changelogOut -ErrorAction SilentlyContinue }
      $changelogDeleted = $true
    } else {
      Write-Warning 'CHANGELOG.md 未含脚手架自证标记（疑似已被替换为你自己产品的 CHANGELOG）——已保留，未删除。'
    }
  }
  # TD15：scripts/selftest.ps1 + .github/workflows/scaffold-selftest.yml 不再删除——二者约 12/17 闸测的是
  # 会下发的生产脚本（task.ps1/review.ps1/check-cards.ps1/check-secrets.ps1/lessons.ps1/...），下游可继续
  # 拿它当自己的工作流自检 CI；真正元仓专属的子检查（模板哨兵/占位符/token 覆盖/init 干跑）已在 selftest.ps1
  # 内自动检测「已初始化」并跳过，不会误判为失败。
  # TD64/TD-127 item8（R3 catch 两轮）：TEMPLATE-README.md 与 CHANGELOG.md 均可能因各自守卫被保留而非删除，
  # 汇总消息须分别如实反映两者的真实去留——不能无条件宣称「已删」（第一轮只补了 CHANGELOG 一侧，漏了
  # TEMPLATE-README 侧同款 bug：其提炼失败保留路径早在 TD65/8l 就存在，消息却始终写死「已删 TEMPLATE-README.md」）。
  $deletedParts = @()
  $retainedNames = @()
  if ($readmeDeleted) { $deletedParts += 'TEMPLATE-README.md' } else { $retainedNames += 'TEMPLATE-README.md' }
  if ($changelogDeleted) { $deletedParts += 'CHANGELOG.md' } else { $retainedNames += 'CHANGELOG.md' }
  # [INIT-CLEANUP] 是本消息里**唯一**的去留陈述（TD120/L165）：deleted= / kept= 是逗号连接的裸文件名、内部
  # 无空白，故闸门断言的是**列表成员**而非散文相邻性（旧锁不得不手工收窄正则来避开**正确**消息——它把两个
  # 文件名一删一留写在同一行——那种窄正则本身就是易碎面）。**刻意不再另附一句散文重述去留**：TD64/TD-127
  # item8 的缺陷本体正是「消息宣称已删某个其实被保留的文件」，同一事实说两遍就等于留一份不受闸门保护的
  # 副本，缺陷可以从散文那半原样复活而两道锁全绿。每个被保留文件的**原因**已由上方各自的 Write-Warning
  # 打印，此处不重复。mode= 区分预览与实施。
  $mode = if ($DryRun) { 'dryrun' } else { 'apply' }
  $deletedField = if ($deletedParts.Count -gt 0) { $deletedParts -join ',' } else { 'none' }
  $keptField = if ($retainedNames.Count -gt 0) { $retainedNames -join ',' } else { 'none' }
  Write-Host "  [INIT-CLEANUP] mode=$mode deleted=$deletedField kept=$keptField (deleted files are meta-repo only; each kept file's reason is in its own warning above). scripts\selftest.ps1 and its CI workflow are kept - go on using them as your own project's workflow self-test (TD15). This script is kept after the run - delete init-scaffold.ps1 by hand once you have confirmed the result."

  # ── TD129: prune the meta-repo's own working state ────────────────────────
  # Why here: -Cleanup was written to remove the two files that are obviously meta-repo-only (TEMPLATE-README.md,
  # CHANGELOG.md) and was never revisited when the scaffold grew stateful subsystems - an archive tree, a debt ledger,
  # an ADR series, a mutation-evidence store. Those are all working state of this scaffold's own development, but they
  # live in ordinary tracked paths, so every copy-and-init path carries them verbatim into the new project. Measured
  # downstream (MyInspection, 2026-08-14): 85 cold-stored cards, the debt and lesson archives, every in-flight debt row
  # and 5 meta-repo ADRs all landed there, leaving the user to identify and delete them by hand - risky in both
  # directions (delete too much and template payload goes with it; too little and the project carries permanent
  # confusion about which debts and decisions are its own).
  # docs/lessons/LEDGER.md is kept on purpose: toolchain pitfalls generalise, and the template CLAUDE.md cites its L-ids.
  # -Cleanup only. -Retrofit joins a repo that is already the user's, where same-named files are their own (L55).
  $prunedNames = @(); $prunedKept = @('docs/lessons/LEDGER.md')
  $archiveDir = Join-Path $Root 'specs/archive'
  if (Test-Path $archiveDir) {
    if ($DryRun) { $plannedChanges.Add("prune: $archiveDir (meta-repo cold store: closed cards + debt/lesson archives)") }
    else { Remove-Item -Recurse -Force $archiveDir -ErrorAction SilentlyContinue }
    $prunedNames += 'specs/archive'
  }
  # Directory-level "keep exactly one file": every other entry is this repo's in-flight or historical artifact.
  foreach ($keepPair in @(@('specs/tasks', '_TEMPLATE.md'), @('specs/mutations', 'README.md'), @('docs/adr', 'README.md'))) {
    $dirRel = $keepPair[0]; $keepName = $keepPair[1]
    $dirFull = Join-Path $Root $dirRel
    if (-not (Test-Path $dirFull)) { continue }
    $victims = @(Get-ChildItem $dirFull -Force | Where-Object { $_.Name -ne $keepName })
    if ($victims.Count -eq 0) { continue }
    foreach ($v in $victims) {
      if ($DryRun) { $plannedChanges.Add("prune: $($v.FullName) (meta-repo working state; $dirRel keeps only $keepName)") }
      else { Remove-Item -Recurse -Force $v.FullName -ErrorAction SilentlyContinue }
    }
    $prunedNames += ('{0}(-{1})' -f $dirRel, $victims.Count); $prunedKept += ('{0}/{1}' -f $dirRel, $keepName)
  }
  # Tech-debt tracker: drop the DEBT ROWS only, keep the registration-layer contract text. The header prose, the status
  # enum, the "how to write a row" section, the table header/separator and the _示例_ teaching rows are all template
  # payload - a downstream registers its own debt against them. Criterion = a line beginning `| TD<digits> |`, so the
  # teaching rows and every paragraph survive untouched.
  $trackerFull = Join-Path $Root 'specs/tech-debt-tracker.md'
  if (Test-Path $trackerFull) {
    $trackerLines = @(Get-Content $trackerFull)
    $trackerKeep = @($trackerLines | Where-Object { $_ -notmatch '^\|\s*TD\d+\s*\|' })
    $trackerDropped = $trackerLines.Count - $trackerKeep.Count
    if ($trackerDropped -gt 0) {
      if ($DryRun) { $plannedChanges.Add("rewrite: $trackerFull (drop $trackerDropped meta-repo debt rows, keep the contract text)") }
      else { Set-Content -Path $trackerFull -Value (($trackerKeep -join "`n") + "`n") -Encoding utf8 -NoNewline }
      $prunedNames += ('specs/tech-debt-tracker.md(-{0} rows)' -f $trackerDropped)
    }
    $prunedKept += 'specs/tech-debt-tracker.md(contract text)'
  }
  # [INIT-PRUNE] gets its own line: gate 8 pins [INIT-CLEANUP]'s field group by list membership, and crowding a second
  # statement onto that line is exactly the fragile prose-adjacency shape TD120 wave 2 removed.
  $pruneMode = if ($DryRun) { 'dryrun' } else { 'apply' }
  $prunedField = if ($prunedNames.Count -gt 0) { $prunedNames -join ',' } else { 'none' }
  Write-Host "  [INIT-PRUNE] mode=$pruneMode pruned=$prunedField kept=$($prunedKept -join ',') (meta-repo working state: this scaffold's own development history, meaningless in your project. LEDGER.md is kept on purpose - toolchain pitfalls generalise and the template CLAUDE.md cites its L-ids.)"

}

if ($DryRun) {
  Step '预览完成（DryRun，未写盘）'
  if ($plannedChanges.Count -eq 0) {
    Write-Host '  TD76-DRYRUN: 未探测到任何待改动的文件（可能已初始化，或触发条件均未命中）。' -ForegroundColor Yellow
  } else {
    Write-Host "TD76-DRYRUN 改动清单（共 $($plannedChanges.Count) 项，未写盘）：" -ForegroundColor Cyan
    foreach ($c in $plannedChanges) { Write-Host "  - $c" }
  }
  Write-Host '  以上为预览：加 -DryRun 不会写盘/改名/删除。去掉 -DryRun 重跑以真正初始化。' -ForegroundColor Yellow
  return
}
Step '完成'
if ($Retrofit) {
  # TD15：selftest.ps1 + scaffold-selftest.yml 不再随 -Retrofit 移除，理由同 -Cleanup（见上）——
  # 既有仓也能白得一份工作流自检 CI；下方提示告知用户可按需移除，避免默默新增一条 CI 出人意料。
  # CHANGELOG.md 故意**不**在 -Retrofit 删：既有仓极可能已有自己产品的 CHANGELOG.md，无条件 Remove-Item 会**误删用户数据**——留给用户手动处理（下方提示）。
  Write-Host @"
[Retrofit 提示]
  · token 只替换了脚手架自有路径，你的既有源码与其中的 {{...}} 未被触碰。
  · 保留了 scripts/selftest.ps1 + .github/workflows/scaffold-selftest.yml（TD15）：验证脚手架工作流脚本本身
    （task.ps1/review.ps1/check-cards.ps1/...）的自检 + 双 OS CI；不需要就删掉这两个文件。
  · CHANGELOG.md 本脚本**不自动删**（怕误删你既有的）：若你误把脚手架的 CHANGELOG.md 拷进来了（它记的是脚手架自身发布历史），手动删掉或换回你自己产品的 CHANGELOG。
  · 若你已有 CLAUDE.md：本脚本未覆盖它——把 CLAUDE.template.md 的索引/铁律手动并入你的 CLAUDE.md。
  · 把脚手架运行时条目并入你既有 .gitignore（缺则追加，防私有/运行时文件误入库——尤其 _local/ 里的模型系统提示）：
      _local/  .review/  .secrets/  runtime/  task_plan.md  findings.md  progress.md
  · 确认未把脚手架的骨架空目录（backend/ frontend/ tests/ …）覆盖到你已有的同名目录。
  · 你既有的 CI 若已有同名 job，注意 .github/workflows/ci.yml 的 verify job 命名是否冲突。
"@ -ForegroundColor Yellow
}
Write-Host @"
下一步：
  1. 校对 scripts\_config.ps1（尤其 GhAccount / FrozenPaths）。
  2. 补全 CLAUDE.md 的「当前阶段 / 架构大图 / 硬边界 / 关键不变量」四节占位。
  3. 照 docs\PLAN-TEMPLATE.md 手工扩写计划到 _local\PLAN.md。
  4. 建仓加固：pwsh -File scripts\gh-bootstrap.ps1
  5. 第一张卡：在 specs\tasks\ 按 _TEMPLATE.md 建卡 → scripts\task.ps1 -TaskId <ID> -Phase start
  自检：pwsh -File scripts\lessons.ps1 check   （初始化后应 PASS）

  T0 极简档（玩具 / 一次性 / 无远端）：可跳过上面第 3-4 步——首卡 = 写 specs\tasks\<ID>.md →
    task.ps1 -Phase start →（先写失败测试 → -Phase red → 实现到绿）→ task.ps1 -Phase ship -Local
    （-Local 本地合并，无需 GitHub / Codex）。规模档位见 docs\IDEA-TO-PLAN.md。
"@ -ForegroundColor Green
