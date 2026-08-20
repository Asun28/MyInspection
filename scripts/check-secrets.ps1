#requires -Version 7
<#
.SYNOPSIS
  变 public 前 / 提交前的**防泄露闸**：核验机密文件既被 .gitignore 排除、又**未被 git 追踪**。
  本脚本是「敏感文件模式集」的**单一真相源**，gh-bootstrap.ps1 复用它（不再各自硬编码扫描规则）。

.DESCRIPTION
  仓库变 PUBLIC 时最危险的不是「忘了写 .gitignore」，而是「文件已被 git 追踪」——
  对**已追踪**文件，.gitignore 完全无效（git 只对未追踪文件生效），它们会随仓库一起公开。
  补救只能 `git rm --cached <f>` 取消追踪后再提交。本闸把这层「gitignore 救不了已追踪文件」的坑机检掉。

  三类发现（按危险度）：
    1. 致命：**已被追踪的敏感文件**（git ls-files 命中模式集）。gitignore 救不了它们 —— 核心检查。
       每条给出补救：`git rm --cached <f>`（保留本地文件、仅取消追踪）后提交。
    2. 警告：**.gitignore 覆盖缺口** —— 核心模式（数据库/密钥/env）未在 .gitignore 出现，
       将来创建这类文件会被直接提交。
    3. 警告：**工作树里未被忽略的敏感文件** —— 磁盘上命中模式、却未被 gitignore 排除，
       下次 `git add` 就会纳入。

  退出码：有任何「致命」即 1，否则 0。`-Strict` 把所有「警告」升级为「致命」——**变 public 前**用它，须全绿。

  非 git 仓（如本元仓、或 _config GhAccount 留空尚未建仓）：打印「非 git 仓，跳过」并退出 0（优雅降级，
  selftest 在此状态下跑它）。本脚本不依赖 _config.ps1，默认配置下可干跑，无未捕获异常。

.PARAMETER Strict  把所有警告升级为致命（变 public 前用，须 exit 0）。
.PARAMETER AsLibrary
  库模式：只定义「内容密钥模式集 + Find-LineSecret」后立即 return——不执行任何扫描、不触达 git、不 exit，
  供外部脚本 dot-source 复用密钥判定（当前复用方：lessons.ps1 add 的入账过滤；单一真相源）。
  坑：本脚本 param 头 + 顶层主流程，**直接 dot-source 会执行整套扫描且其 exit 会杀掉调用方**——复用函数务必带 -AsLibrary。
  默认（不带该开关）CLI 行为零变化：ship / pre-push 钩子 / CI / gh-bootstrap 的调用与退出码均不受影响。
.EXAMPLE
  pwsh -File scripts\check-secrets.ps1
.EXAMPLE
  pwsh -File scripts\check-secrets.ps1 -Strict   # 变 public 前必须全绿
.EXAMPLE
  . (Join-Path $PSScriptRoot 'check-secrets.ps1') -AsLibrary   # 只取 Find-LineSecret，不跑扫描
#>
[CmdletBinding()]
param([switch]$Strict, [switch]$AsLibrary)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# CJK/非 ASCII 路径正确解码：git 以 UTF-8 输出路径字节，PowerShell 按 [Console]::OutputEncoding 解码原生命令输出；
# 旧版 Windows 控制台默认 OEM 代码页（ibm850/936…）会把 café.env / 中文名.env 解成乱码 → Test-Path 落空、
# 文件名与内容双漏，闸却仍 PASS（30-lens C40）。配合下方 git 调用的 -c core.quotepath=false（关 C-quote 转义）。
# OutputEncoding + 原生非零按码判现由共享前奏 _encoding.ps1 统一设——在 -AsLibrary 早返回后 dot-source（见下方），
# 免库模式（lessons/selftest dot-source 取函数）污染调用方作用域（TD54/TD-117）。

# ── 敏感文件模式集（单一真相源；gh-bootstrap.ps1 复用本脚本而非另写扫描）──
# 每条是「对仓库相对路径（正斜杠）做大小写不敏感正则匹配」的片段。分五类，便于文档对齐。
# 工具/扩展名仅作举例：按你项目实际增删（这是方法论 = 「机密既 ignore 又不 track」，非某具体清单）。
$SensitivePatterns = @(
  # env / 配置密钥
  '(^|/)\.env$', '\.env\.', '(^|/)[^/]*\.env$',
  # 核心数据库（最易被忽视：开发顺手 commit 了本地库就连数据一起公开）
  '\.db$', '\.sqlite$', '\.sqlite3$', '\.sqlite-journal$', '\.duckdb$', '\.mdb$',
  # 密钥 / 证书
  '\.key$', '\.pem$', '\.pfx$', '\.p12$', '\.keystore$', '(^|/)id_rsa',
  # 凭据文件
  'credentials.*\.json$', '(^|/)service-account[^/]*\.json$', '\.secret$', '(^|/)secret',
  # 登录态 / auth state
  'storage_state', '(^|/)auth/'
) -join '|'

# 例外：以下命中模式但属**合法可入库**（模板/示例/文档），不应误报。
# 收窄（TD62/TD-125）：不再 blanket 放行任意 `*.example`——旧 `\.example$` 令 `service-account.json.example` /
# `prod.pem.example` / `secret.example` 等「按模式敏感、仅加 .example 后缀」的文件名整个逃过**文件名闸**。
# 只显式豁免已知安全模板（`.env.example`、`data/README.md`）与动态清单文件**自身**。动态清单里的
# 数据库路径仍由下方严格解析后逐条加入，绝不在这个正则里写目录/扩展名级放行。注：内容闸(1b)对 `.example` 文件**始终扫描**
# （不查本白名单），故真含密钥的 `*.example` 仍会被内容扫描抓到；本收窄只补文件名闸这一层纵深。
$TrackedSensitiveAllowlistConfig = 'configs/secrets/tracked-sensitive-allowlist.json'
$Allowlist = '(^|/)\.env\.example$|(^|/)data/README\.md$'
$TrackedSensitiveAllowlist = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

function Test-Sensitive([string]$path) {
  if ($path -ceq $TrackedSensitiveAllowlistConfig) { return $false }
  if ($path -match $Allowlist) { return $false }
  if ($TrackedSensitiveAllowlist.Contains($path)) { return $false }
  return ($path -match "(?i)($SensitivePatterns)")
}

function Initialize-TrackedSensitiveAllowlist([string[]]$trackedPaths) {
  $configFile = Join-Path $RepoRoot $TrackedSensitiveAllowlistConfig
  if (-not (Test-Path -LiteralPath $configFile)) { return }

  $trackedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
  foreach ($trackedPath in $trackedPaths) { [void]$trackedSet.Add($trackedPath) }
  if (-not $trackedSet.Contains($TrackedSensitiveAllowlistConfig)) {
    throw "清单文件必须由 git 精确追踪：$TrackedSensitiveAllowlistConfig"
  }

  $configItem = Get-Item -LiteralPath $configFile -Force -ErrorAction Stop
  if ($configItem.PSIsContainer -or ($configItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
    throw "清单文件必须是非 reparse 的普通文件：$TrackedSensitiveAllowlistConfig"
  }

  $document = $null
  try {
    $document = [System.Text.Json.JsonDocument]::Parse((Get-Content -LiteralPath $configFile -Raw -ErrorAction Stop))
    if ($document.RootElement.ValueKind -ne [System.Text.Json.JsonValueKind]::Array) {
      throw '清单顶层必须是 JSON array。'
    }
    if ($document.RootElement.GetArrayLength() -eq 0) { throw '清单不得为空。' }

    $allowedFields = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    [void]$allowedFields.Add('path'); [void]$allowedFields.Add('purpose')
    $seenPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $repoPrefix = $RepoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $pathComparison = if ($IsWindows) { [System.StringComparison]::OrdinalIgnoreCase } else { [System.StringComparison]::Ordinal }

    $entryIndex = 0
    foreach ($entry in $document.RootElement.EnumerateArray()) {
      $entryIndex++
      if ($entry.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) { throw "条目 $entryIndex 必须是 JSON object。" }
      $fields = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
      $values = @{}
      foreach ($property in $entry.EnumerateObject()) {
        $name = $property.Name
        if (-not $fields.Add($name)) { throw "条目 $entryIndex 字段重复：$name" }
        if (-not $allowedFields.Contains($name)) { throw "条目 $entryIndex 含未知字段：$name" }
        if ($property.Value.ValueKind -ne [System.Text.Json.JsonValueKind]::String) { throw "条目 $entryIndex 字段 $name 必须是 string。" }
        $values[$name] = $property.Value.GetString()
      }
      if (-not $fields.Contains('path') -or -not $fields.Contains('purpose')) { throw "条目 $entryIndex 必须且只能含 path、purpose。" }

      $path = [string]$values.path
      $purpose = [string]$values.purpose
      if ([string]::IsNullOrWhiteSpace($path) -or $path -match '[\p{Cc}\p{Cf}]') { throw "条目 $entryIndex path 为空或含控制字符。" }
      if ([string]::IsNullOrWhiteSpace($purpose) -or $purpose -match '[\p{Cc}\p{Cf}]') { throw "条目 $entryIndex purpose 为空或含控制字符。" }
      if ($path.Contains('\') -or $path -match '[*?\[\]]' -or $path.StartsWith('/') -or $path -match '^[A-Za-z]:') {
        throw "条目 $entryIndex path 必须是无 glob 的正斜杠仓库相对精确路径：$path"
      }
      $segments = @($path -split '/')
      if ($segments.Count -lt 2 -or @($segments | Where-Object { $_ -in @('', '.', '..') }).Count -ne 0) {
        throw "条目 $entryIndex path 含空段、点段或越界段：$path"
      }
      if ($path -cnotmatch '^android/core/src/main/sqldelight/.+\.db$') {
        throw "条目 $entryIndex 只允许 SQLDelight schema baseline .db：$path"
      }
      if (-not $seenPaths.Add($path)) { throw "清单 path 重复：$path" }
      if (-not $trackedSet.Contains($path)) { throw "清单 path 不存在或未被 git 精确追踪：$path" }

      $fullPath = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot ($path -replace '/', [System.IO.Path]::DirectorySeparatorChar)))
      if (-not $fullPath.StartsWith($repoPrefix, $pathComparison)) { throw "清单 path 越出仓库：$path" }
      if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw "清单 path 不存在或不是普通文件：$path" }
      $item = Get-Item -LiteralPath $fullPath -Force -ErrorAction Stop
      if ($item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "清单 path 必须是非 reparse 的普通文件：$path"
      }
      [void]$TrackedSensitiveAllowlist.Add($path)
    }
  } finally {
    if ($null -ne $document) { $document.Dispose() }
  }
}

# ── 内容密钥模式集 + Find-LineSecret（库导出区 · 密钥判定单一真相源）──
# 置于主流程（git 访问 / 扫描 / exit）**之前**，令 -AsLibrary 可安全取用；主流程 1b/4b 与 lessons.ps1 add 共用。
# 修「check-secrets 只看文件名、不看内容 → app.py/config.yml 里硬编码的 AKIA/ghp_/sk-/PEM 全过闸」。
# 只扫**已追踪**文本文件（= 会随仓库一起公开的文件）；用高精度前缀/头部模式（极少误报）。
# 逃生舱：行内含「allowlist secret」标记则跳过该行（占位/文档示例用）。本扫描器自身排除（含模式字面量）。
# OpenAI/Anthropic 的 sk- 前缀须加负向后顾 (?<![A-Za-z0-9-])：否则 disk-/task-/risk-/desk- 等
# 常见 kebab 标识符里的 'sk-' 会误报、且本扫描跑在默认调用上→会中止下游 gh-bootstrap 推送（评审 R3 实测）。
# OpenAI 走纯 alnum 尾（sk-/sk-proj- + 20+ 无连字符 → 词里的 sk-kebab 早遇 '-' 不足 20 不中）；
# Anthropic 单列 sk-ant-（该前缀英文不出现，故尾部容连字符以匹配 sk-ant-api03-… 真键）。
$ContentSecretPatterns = [ordered]@{
  'AWS Access Key ID'      = 'AKIA[0-9A-Z]{16}'
  'GitHub Token'           = 'gh[pousr]_[A-Za-z0-9]{36,}'
  'GitHub Fine-Grained PAT'= 'github_pat_[A-Za-z0-9_]{36,}'
  'OpenAI Key'             = '(?<![A-Za-z0-9-])sk-(?:proj-)?[A-Za-z0-9]{20,}'
  'Anthropic Key'          = '(?<![A-Za-z0-9-])sk-ant-[A-Za-z0-9_-]{20,}'
  'Slack Token'            = 'xox[baprs]-[A-Za-z0-9-]{10,}'
  'Google API Key'         = 'AIza[0-9A-Za-z_\-]{35}'
  'Stripe Live Key'        = '(?:sk|rk)_live_[0-9A-Za-z]{20,}'
  'Private Key (PEM)'      = '-----BEGIN(?:[ A-Z]+)? PRIVATE KEY-----'
  # JWT（session/bearer token）：header.payload.signature，前两段皆 base64url(JSON) 恒以 eyJ 起头（{"…）。
  # 高精度形状（两段 eyJ… 各 10+ 且以 '.' 相隔），极少在非 JWT 文本出现；治「opaque bearer token 无厂商前缀→过闸」。
  'JSON Web Token (JWT)'   = 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.'
  # 通用密钥赋值（治「前缀白名单只抓已知厂商，硬编码 DB 口令/通用 token 全过闸」）。
  # 8+ 字符值，引号**可选**（治「强制引号 → 漏掉 .env / YAML / 连接串的裸值」）；
  # 关键词前用 (?<![A-Za-z]) 而非 \b（治「\b 漏掉 snake_case：db_password / my_secret，因 '_' 是词字符无边界」）；
  # 占位符（{{}} / ${} / <...> / example / changeme 等）由 $PlaceholderRe 排除，压低误报。
  'Generic Secret Assignment' = '(?i)(?<![A-Za-z])(?:password|passwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|client[_-]?secret|connection[_-]?string)\b\s*[:=]\s*["'']?[^\s"''#]{8,}["'']?'
}
# 占位符/示例值：通用密钥模式命中这些时不算泄露（避免对模板/文档/示例误报）。
$PlaceholderRe = '(?i)(\{\{|\$\{|<[^>]+>|x{3,}|your[-_ ]|example|changeme|placeholder|dummy|sample|todo|fixme|redacted|\*{3,}|\.\.\.)'

# 单行密钥判定（工作树扫描与历史扫描共用）：命中返回模式名，否则 $null。
function Find-LineSecret([string]$line) {
  if ($line -match '(?i)allowlist secret') { return $null }      # 逐行逃生舱
  foreach ($name in $ContentSecretPatterns.Keys) {
    if ($line -match $ContentSecretPatterns[$name]) {
      if ($name -eq 'Generic Secret Assignment' -and $line -match $PlaceholderRe) { continue }
      return $name
    }
  }
  return $null
}

# ── 库模式：函数/模式集已定义，就此返回——不执行扫描、不触达 git、不 exit（TD18）──
if ($AsLibrary) { return }
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出（git 路径解码）+ 原生非零按码判（非 git 优雅 exit 0）；库模式早返回后才 dot-source，缺失即 fail-open（TD54/TD-117）

# ── 非 git 仓 => 优雅跳过（元仓 / 尚未建仓）──
& git -C $RepoRoot rev-parse --is-inside-work-tree 2>$null 1>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host '非 git 仓，跳过防泄露闸（无 .git；元仓或尚未建仓时正常）。' -ForegroundColor DarkGray
  exit 0
}

$fatal = @()   # 致命发现
$warn  = @()   # 警告发现（-Strict 下升级为致命）

# ── 1. 致命：已被 git 追踪的敏感文件（gitignore 救不了它们）──
# -c core.quotepath=false：否则 git 把非 ASCII 路径 C-quote 成 "\344\275..." → 文件名模式与 Test-Path 双失（C40）。
$tracked = @(& git -C $RepoRoot -c core.quotepath=false ls-files)
try {
  Initialize-TrackedSensitiveAllowlist $tracked
} catch {
  Write-Host "致命（[SECRET-ALLOWLIST] 精确敏感文件清单无效）：$($_.Exception.Message)" -ForegroundColor Red
  Write-Host "`ncheck-secrets: FAIL（[SECRET-ALLOWLIST]）" -ForegroundColor Red
  exit 1
}
$trackedHits = @($tracked | Where-Object { Test-Sensitive $_ })
foreach ($f in $trackedHits) {
  $fatal += "已被追踪的敏感文件：$f  → 补救：git rm --cached `"$f`" 然后提交（gitignore 对已追踪文件无效）"
}

# ── 1b. 致命：已被追踪文件的**内容**含疑似实时密钥（文件名扫不到的硬编码密钥；模式集/Find-LineSecret 见上「库导出区」）──
$selfRel = 'scripts/check-secrets.ps1'
foreach ($f in $tracked) {
  if ($f -eq $selfRel) { continue }
  $full = Join-Path $RepoRoot $f
  # -PathType Leaf：一处同时跳过「缺失条目」与「目录/gitlink 条目」。git ls-files 把**子模块**作为单条 gitlink 输出，
  # 其工作树路径是目录；旧 `Test-Path $full`（无 -PathType）对目录为真、放行后 :151 对 DirectoryInfo 求 .Length（StrictMode 终止错误）
  # / :152 ReadAllBytes 对目录抛 → 含子模块的任何下游仓上防泄露闸崩、不返回裁决（TD-201）。-LiteralPath 亦比裸 -Path 更正确。
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
  # -Force 必须：Linux 把点开头文件（.env.example / .gitignore / .gitattributes 等）视为隐藏，
  # Get-Item 无 -Force 会抛「Could not find item」→ check-secrets 在 Linux 崩（Windows 不视点文件为隐藏，故此前只在 Linux 暴露）。
  if ((Get-Item -LiteralPath $full -Force).Length -gt 5MB) { continue }   # 跳过超大文件（5MB；密钥几乎不在巨型文件里）
  $bytes = [System.IO.File]::ReadAllBytes($full)
  # 按 BOM 选编码：UTF-16 文本对 ASCII 字符含 NUL 字节，旧「含 NUL 即二进制跳过」会漏扫 UTF-16（治「UTF-16 被跳过」）。
  $text = $null
  if     ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) { $text = [System.Text.Encoding]::Unicode.GetString($bytes) }           # UTF-16 LE BOM
  elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) { $text = [System.Text.Encoding]::BigEndianUnicode.GetString($bytes) }  # UTF-16 BE BOM
  elseif ($bytes -contains 0) { continue }                    # 无 UTF-16 BOM 却含 NUL => 真二进制，跳过
  else   { $text = [System.Text.Encoding]::UTF8.GetString($bytes) }
  foreach ($line in ($text -split '\r?\n')) {
    $hit = Find-LineSecret $line
    if ($hit) {
      $fatal += "已追踪文件含疑似密钥（$hit）：$f  → 立即轮换该密钥，改用环境变量/密钥管理，并从历史移除（git filter-repo/BFG）"
      break                                                   # 每文件只报一次（去重噪音）
    }
  }
}

# ── 2. 警告：.gitignore 覆盖缺口（核心模式 db/key/env 是否在 .gitignore 出现）──
$giPath = Join-Path $RepoRoot '.gitignore'
$giRaw = if (Test-Path $giPath) { Get-Content -LiteralPath $giPath -Raw -Force } else { '' }   # -Force：.gitignore 在 Linux 是隐藏点文件，无 -Force 读不到
# 仅检最易遗漏的核心后缀；逐条看 .gitignore 文本里是否出现该 glob（粗匹配，发现缺口即提醒）。
$coreGlobs = @('*.db', '*.sqlite', '*.key', '*.pem', '*credentials*.json', '*.env', '.env')
$missingGlobs = @($coreGlobs | Where-Object { $giRaw -notmatch [regex]::Escape($_) })
if (-not (Test-Path $giPath)) {
  $warn += '仓库无 .gitignore —— 任何机密文件创建后都会被直接提交。'
} elseif ($missingGlobs.Count) {
  $warn += ".gitignore 未覆盖核心敏感模式：$($missingGlobs -join ', ') —— 将来创建这类文件会被提交。"
}

# ── 3. 警告：工作树里命中模式、却未被 gitignore 排除的文件（下次 git add 就会纳入）──
# git status --porcelain --ignored 标出 '!!' = 被忽略；列未追踪(??)且命中模式但未被忽略者。
$untrackedNotIgnored = @()
$porcelain = @(& git -C $RepoRoot -c core.quotepath=false status --porcelain --untracked-files=all)
foreach ($line in $porcelain) {
  if ($line -match '^\?\?\s+(.+)$') {
    $p = $Matches[1].Trim('"')
    if ((Test-Sensitive $p) -and ($p -notin $trackedHits)) { $untrackedNotIgnored += $p }
  }
}
foreach ($f in ($untrackedNotIgnored | Sort-Object -Unique)) {
  $warn += "工作树敏感文件未被忽略：$f  → 加进 .gitignore（否则下次 git add 会纳入）。"
}

# ── 4. （-Strict / 变 public 前）git 历史扫描 ──
# 治本最危险盲点：仓库变 public 暴露的是**整个提交历史**，不只当前快照。一个曾提交、后来
# `git rm --cached`/删除的 .env / .db / 硬编码密钥，当前快照扫不到，却能从历史 `git log -p` 恢复。
# 故 -Strict（变 public 前）须扫历史；命中即致命，且补救是 `git filter-repo`/BFG 清历史 + 轮换，
# **不是** `git rm --cached`（它只动当前索引、不删历史 blob）。大仓建议另用 gitleaks/trufflehog 做更全扫描。
if ($Strict) {
  # 4a. 历史文件名：所有曾被「新增」过的路径（含后来删除的），命中敏感模式即历史里有过该文件。
  $histAdded = @(& git -C $RepoRoot -c core.quotepath=false log --all --diff-filter=A --name-only --pretty=format: 2>$null) |
    Where-Object { $_ } | Sort-Object -Unique
  foreach ($p in ($histAdded | Where-Object { Test-Sensitive $_ })) {
    $fatal += "git 历史含敏感文件：$p  → git rm --cached 不删历史！须 git filter-repo / BFG 清历史 blob + 轮换其中密钥，再变 public。"
  }
  # 4b. 历史新增行内容：扫所有提交里的 +added 行（封顶 5MB patch），命中密钥模式即历史里曾提交过密钥。
  $histPatch = (& git -C $RepoRoot log --all -p --no-color 2>$null | Out-String)
  $histCap = 5MB
  if ($histPatch.Length -gt $histCap) {
    $histPatch = $histPatch.Substring(0, $histCap)
    # 截断是**覆盖限制**、不是泄露——故**不进 $warn**（否则 -Strict 把它升级为致命 → 历史较大的仓纯因截断而红，
    # 与「-Strict = 变 public 前一次性闸」定位矛盾）。仅作信息提示；完整历史扫描用 gitleaks/trufflehog。
    # 真在历史里发现的密钥仍走下面的 $fatal（不受影响）。
    Write-Host "  (信息) git 历史 > $([int]($histCap/1MB))MB，内容扫描仅覆盖前 $([int]($histCap/1MB))MB patch —— 完整历史扫描请用 gitleaks/trufflehog。" -ForegroundColor DarkYellow
  }
  $histSeen = @{}
  foreach ($line in ($histPatch -split '\r?\n')) {
    if ($line -notmatch '^\+') { continue }                   # 只看新增行
    $hit = Find-LineSecret $line.Substring(1)
    if ($hit -and -not $histSeen.ContainsKey($hit)) {
      $fatal += "git 历史含疑似密钥（$hit）：曾提交进历史 → 立即轮换该密钥 + git filter-repo/BFG 清历史，再变 public。"
      $histSeen[$hit] = $true
    }
  }
}

# ── 汇报 + 退出码 ──
if ($Strict -and $warn.Count) { $fatal += $warn; $warn = @() }

if ($warn.Count) {
  Write-Host '警告（-Strict 下会升级为致命）：' -ForegroundColor Yellow
  $warn | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
}
if ($fatal.Count) {
  Write-Host '致命（变 public / 提交前必须清零）：' -ForegroundColor Red
  $fatal | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  Write-Host "`ncheck-secrets: FAIL（$($fatal.Count) 项致命）" -ForegroundColor Red
  exit 1
}

if ($Strict) { Write-Host "check-secrets: PASS（-Strict 全绿：核验 $($tracked.Count) 个追踪文件 + 工作树 + git 历史；变 public 前的本闸已过。高保险可再跑 gitleaks/trufflehog）" -ForegroundColor Green }
else { Write-Host "check-secrets: PASS（无机密被追踪；核验 $($tracked.Count) 个追踪文件。变 public 前请再跑 -Strict）" -ForegroundColor Green }
exit 0
