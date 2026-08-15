#requires -Version 7
<#
.SYNOPSIS
  依赖许可证守卫（商用）：扫描后端 PyPI 依赖与前端 npm 依赖的许可，命中禁列即非零退出。
  规则见 docs\LICENSE-POLICY.md。覆盖 PyPI/npm；模型权重/数据/字体/素材需人工按政策表登记。

.DESCRIPTION
  - 后端：优先 `uv run --with pip-licenses`（在项目环境内内省到项目实际安装依赖）；无 uv 时尝试 `pip-licenses`，再无则跳过并告警。
  - 前端：若有 frontend\package.json，用 `npx --yes license-checker --json`。
  - 禁列（正则，大小写不敏感）：GPL / AGPL / SSPL / EUPL / CC-BY-NC / non-?commercial / research[- ]only。
  - LGPL/OpenRAIL/MPL 单独标黄（进程外 CLI / 文件级 copyleft 等隔离用法可接受；其它需人工确认）。
  - 退出码：发现禁列=1；仅黄=0（带告警）；干净=0。
.EXAMPLE
  pwsh -File scripts\check-licenses.ps1
#>
[CmdletBinding()]
param(
  [switch]$Strict,     # -Strict 时 LGPL/OpenRAIL 等黄牌也算失败
  [switch]$AsLibrary   # 库模式：只定义正则/Scan/Distributes 即返回——不 Set-Location/不扫描/不触 git/不 exit（供 selftest 17p 复用；镜像 check-secrets.ps1 -AsLibrary）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_config.ps1')

# ── 许可分类（库导出区 · 置于 Set-Location/扫描之前，令 -AsLibrary 可安全取用；主流程与 selftest 17p 共用单一真相源）──
# 禁列：负向后顾 (?<!L)GPL 确保 LGPL 不被 GPL 命中；AGPL 显式列入禁列。
# 既匹配缩写（GPL/AGPL），也匹配**拼写全称**（Affero / General Public License）——治「分类器只给全称、
# 元数据无缩写 → GPL 依赖漏判」；(?<!Lesser )General Public License 让 LGPL 全称不误入禁列。
$forbidden = 'AGPL|(?<!L)GPL|SSPL|EUPL|CC-?BY-?NC|non-?commercial|research[ -]?only|Affero|(?<!Lesser )General Public License'
$yellow    = 'LGPL|OpenRAIL|RAIL|MPL|Lesser General Public License'
# 纯 GPL（分发触发型 copyleft）：缩写严格排除 L(GPL)/A(GPL)；全称严格排除 Lesser/Affero 前缀。
# 仅用于 Distributes=$false 的降级判定——只降**分发触发**的纯 GPL，绝不碰 AGPL/Affero(网络)/其它触发点。
$gplPlain  = '(?<!L)(?<!A)GPL|(?<!Lesser )(?<!Affero )General Public License'
$Distributes = Get-ScaffoldDistributes   # 项目是否分发软件（GPL 触发点判定，⚖️ 非法律意见）
$bad = @(); $warn = @(); $coverageGap = @()

function Scan($name, $license) {
  if ([string]::IsNullOrWhiteSpace($license)) { $script:warn += "$name => 许可缺失/未知（-Strict 下视为不合规）"; return }
  # 先判黄牌（LGPL/OpenRAIL/MPL 可接受路径），再判禁列，避免 LGPL 误入禁列
  if ($license -match $yellow -and $license -notmatch 'AGPL|Affero|(?<!L)GPL|SSPL|EUPL|non-?commercial') {
    $script:warn += "$name => $license（黄牌：需人工确认用途/链接方式）"; return
  }
  # C21：项目声明不分发（Distributes=$false）时，**纯 GPL**（分发触发型 copyleft）降为黄牌而非致命——不分发则 GPL 分发义务不触发。
  # 严格排除 AGPL/Affero(网络触发)、SSPL(SaaS 触发)、EUPL(分发+通信触发)、非商用/研究限(用途触发)：这些与分发无关、仍致命。
  if (-not $script:Distributes -and $license -match $gplPlain -and $license -notmatch 'AGPL|Affero|SSPL|EUPL|non-?commercial|research[ -]?only|CC-?BY-?NC') {
    $script:warn += "$name => $license（黄牌：纯 GPL 且本项目声明不分发[Distributes=`$false] → copyleft 分发触发点未命中；须人工确认确实不分发/不随产品交付二进制，AGPL/SaaS/网络提供除外。变 public 前用 -Strict 复核）"; return
  }
  if ($license -match $forbidden) { $script:bad += "$name => $license"; return }
}

# ── Gradle 清单发现（库导出区，供 selftest dot-source 直测——修正 R3 finding：先前用
#   Get-ChildItem -Recurse 再 Where-Object 后置过滤，仍会**真的下钻进** .gradle/build/node_modules/.git
#   再丢弃结果，大型/不可读的被排除子树照样被遍历、可能产生假的枚举失败。改成手写栈式遍历，在**下钻前**
#   判断目录名是否在排除表——被排除目录从不会被 push 进栈，根本不会被枚举到）──
# -Enumerator 可注入（默认即真实 Get-ChildItem）：测试用它换成会对指定目录抛错的桩，
# 不必真的靠 Windows ACL 拒绝读权限去模拟「子树不可读」（省掉 icacls 的平台特定性与清理风险）。
$gradleSkipDirs = @('.gradle', 'build', 'node_modules', '.git')
function Find-GradleManifests {
  param(
    [Parameter(Mandatory)][string]$Root,
    [string[]]$SkipDirs = $gradleSkipDirs,
    [Parameter(Mandatory)][string[]]$Names,
    [scriptblock]$Enumerator = { param($d) Get-ChildItem -LiteralPath $d -Force -ErrorAction Stop }
  )
  $found = [System.Collections.Generic.List[string]]::new()
  $stack = [System.Collections.Generic.Stack[string]]::new()
  $stack.Push($Root)
  while ($stack.Count -gt 0) {
    $dir = $stack.Pop()
    foreach ($e in (& $Enumerator $dir)) {
      if ($e.PSIsContainer) {
        # R3 round-2 dimension #10（确定性）：目录联接/符号链接（ReparsePoint）绝不下钻——
        # 否则可能扫出仓外（联接指向仓外目录），或经自引用联接死循环（不终止）。只跳过它本身，
        # 不影响它旁边正常子树的发现。
        $isReparse = [bool]($e.Attributes -band [System.IO.FileAttributes]::ReparsePoint)
        if ((-not $isReparse) -and ($SkipDirs -notcontains $e.Name)) { $stack.Push($e.FullName) }
      } elseif ($Names -contains $e.Name) {
        $found.Add($e.FullName)
      }
    }
  }
  return $found
}

# Gradle 覆盖缺口构建（库导出区，供 selftest dot-source 直测枚举失败→coverage gap 的映射，不必真跑整份
# 脚本）：两个分支各自 try/catch、单独一行，便于单句删除变异独立覆盖；-Enumerator 透传给 Find-GradleManifests。
function Get-GradleCoverageGaps {
  param(
    [Parameter(Mandatory)][string]$Root,
    [scriptblock]$Enumerator = { param($d) Get-ChildItem -LiteralPath $d -Force -ErrorAction Stop }
  )
  $hits = @(); $errs = @()
  # 分支①：libs.versions.toml（Gradle 版本目录，卡片点名的目标）——单独一行，便于单句删除变异独立覆盖本分支。
  try { $hits += @(Find-GradleManifests -Root $Root -Names @('libs.versions.toml') -Enumerator $Enumerator) } catch { $errs += "libs.versions.toml 递归枚举失败：$($_.Exception.Message)" }
  # 分支②：build.gradle / build.gradle.kts（传统构建脚本）——单独一行，便于单句删除变异独立覆盖本分支。
  try { $hits += @(Find-GradleManifests -Root $Root -Names @('build.gradle', 'build.gradle.kts') -Enumerator $Enumerator) } catch { $errs += "build.gradle{,.kts} 递归枚举失败：$($_.Exception.Message)" }
  $gaps = @()
  # fail-closed（T0-TOOLCHAIN finding #4）：枚举出错**不得**被吞掉——吞掉后「没扫到」会被当成「没有清单」，
  # -Strict 照样过，闸在看不见时反而变安静。枚举错误显式记一条 coverage gap，绝不静默降级为「未发现」。
  foreach ($e in $errs) { $gaps += "Gradle：$e ——枚举出错不等于没有清单，零覆盖≠合规（fail-closed，勿静默吞掉）。" }
  if ($hits.Count -gt 0) {
    # Find-GradleManifests 返回的是路径**字符串**（非 FileInfo），直接 .Substring，不取 .FullName。
    $names = @($hits | ForEach-Object { $_.Substring($Root.Length + 1) -replace '\\', '/' } | Sort-Object -Unique)
    $gaps += "Gradle：检测到 $($names.Count) 个清单（$($names -join ', ')）但本闸无对应许可扫描器——按 docs/LICENSE-POLICY.md §3.1/§3.2 人工核验该生态依赖（直接依赖已核验登记，约 220 个传递坐标未审计，见 TD2），或接入扫描器。"
  }
  return $gaps
}

# ── 库模式：正则/Scan/Distributes/Find-GradleManifests/Get-GradleCoverageGaps 已定义，就此返回——
#    不 Set-Location/不扫描/不触 git/不 exit（供 selftest 17p 与 Gradle 发现单测 dot-source 复用）──
if ($AsLibrary) { return }

try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $RepoRoot

Write-Host "=== 后端 PyPI 许可扫描 ===" -ForegroundColor Cyan
$pyJson = $null
if ((Test-Path (Join-Path $RepoRoot 'pyproject.toml')) -and (Get-Command uv -ErrorAction SilentlyContinue)) {
  # 必须扫**项目** venv 的依赖：uvx 是隔离环境，只会扫到 pip-licenses 自身依赖→漏扫项目。
  # 用 `uv run --with pip-licenses` 在项目环境内运行，pip-licenses 才能内省到项目实际安装的依赖。
  $pyJson = & uv run --with pip-licenses pip-licenses --format=json 2>$null
}
if (-not $pyJson -and (Get-Command pip-licenses -ErrorAction SilentlyContinue)) {
  $pyJson = & pip-licenses --format=json 2>$null
}
if ($pyJson) {
  try { (($pyJson | ConvertFrom-Json)) | ForEach-Object { Scan $_.Name $_.License } ; Write-Host "  已扫描 PyPI 包" }
  catch {
    Write-Warning "  pip-licenses 输出解析失败：$($_.Exception.Message)"
    $script:coverageGap += '后端：pip-licenses 输出解析失败——零覆盖不等于合规。'
  }
} elseif (Test-Path (Join-Path $RepoRoot 'pyproject.toml')) {
  # 有后端清单却扫不动 = **覆盖缺口**（治「零覆盖却 PASS」）：装 uv/pip-licenses 或建 venv 后重扫。
  $coverageGap += '后端：有 pyproject.toml 但未能扫描许可（缺 uv/pip-licenses 或 venv 未建）——零覆盖不等于合规。'
} else {
  Write-Warning "  跳过：无 pyproject.toml（无后端，脚手架期正常）。"
}

Write-Host "=== 前端 npm 许可扫描 ===" -ForegroundColor Cyan
$pkg = Join-Path $RepoRoot 'frontend/package.json'
if ((Test-Path $pkg) -and (Get-Command npx -ErrorAction SilentlyContinue)) {
  # TD-205：license-checker 默认从 process.cwd() 找 package.json（此处 cwd = $RepoRoot，见上方 Set-Location），恒扫仓根、从不进 frontend/。
  #   须显式 --start 指到前端目录，否则 frontend/ 的 GPL/AGPL 等违禁依赖漏判，而下面仍打印「已扫描 npm 包」——一个虚假的 commercial-safe 信号（fail-open）。
  $feDir = Join-Path $RepoRoot 'frontend'
  $njson = & npx --yes license-checker --start $feDir --json 2>$null
  if ($njson) {
    try {
      $obj = $njson | ConvertFrom-Json
      foreach ($p in $obj.PSObject.Properties) { Scan $p.Name $p.Value.licenses }
      Write-Host "  已扫描 npm 包"
    } catch {
      Write-Warning "  license-checker 解析失败：$($_.Exception.Message)"
      $script:coverageGap += '前端：license-checker 输出解析失败——零覆盖不等于合规。'
    }
  } else { $coverageGap += '前端：有 frontend/package.json 但 license-checker 无输出——零覆盖不等于合规。' }
} elseif (Test-Path $pkg) {
  $coverageGap += '前端：有 frontend/package.json 但 npx 不可用，未能扫描许可——零覆盖不等于合规。'
} else { Write-Host "  跳过：无 frontend/package.json（无前端）。" }

# === 未覆盖的依赖清单探针（C20：治「非 Python/前端项目零扫描却 PASS」的假绿）===
# 本闸只扫 PyPI(pyproject.toml) 与 npm(frontend/package.json)。若仓库存在**其它生态的依赖清单**而无对应扫描器，
# 零覆盖**不等于**合规——登记为覆盖缺口（正常运行告警、-Strict 失败），绝不让一个有依赖清单的项目无条件 PASS。
# 这是个**硬 ship 闸**：静默 PASS（零扫描）比没有闸更危险——它给了一个虚假的「commercial-safe」信号。
Write-Host "=== 其它生态依赖清单覆盖探针 ===" -ForegroundColor Cyan
$otherManifests = @(
  @{ file = 'go.mod';        eco = 'Go (go.mod)' }
  @{ file = 'Cargo.toml';    eco = 'Rust (Cargo.toml)' }
  @{ file = 'package.json';  eco = 'npm（仓库根 package.json；本闸只扫 frontend/package.json）' }
  @{ file = 'Gemfile';       eco = 'Ruby (Gemfile)' }
  @{ file = 'composer.json'; eco = 'PHP (composer.json)' }
  @{ file = 'pubspec.yaml';  eco = 'Dart/Flutter (pubspec.yaml)' }
  @{ file = 'pom.xml';       eco = 'Java/Maven (pom.xml)' }
)
$otherHits = @($otherManifests | Where-Object { Test-Path (Join-Path $RepoRoot $_.file) })
foreach ($m in $otherHits) {
  $coverageGap += "$($m.eco)：检测到依赖清单但本闸无对应许可扫描器——零覆盖≠合规（按 docs/LICENSE-POLICY.md 人工核验该生态依赖，或接入扫描器）。"
}
if (-not $otherHits) { Write-Host "  未发现其它生态依赖清单（Go/Rust/根 npm/Ruby/PHP/Dart/Maven）。" }

# === Gradle 清单递归发现（T0-GATE-HARDENING item1）===
# 只 glob 仓根 build.gradle{,.kts} 会漏掉嵌套子模块清单（T0-TOOLCHAIN 六轮评审 finding #2：卡片点名的
# android/gradle/libs.versions.toml 就是这样被漏掉的，「能报出来」只是因为当时恰好还有别的构建脚本存在）。
# 覆盖两个独立分支：① libs.versions.toml（Gradle 版本目录）② build.gradle / build.gradle.kts（传统构建脚本）。
# 排除 .gradle/、build/ 等缓存/产物目录，以及 node_modules/.git——Get-GradleCoverageGaps/Find-GradleManifests
# （库导出区）在**下钻前**剪枝，从不真的进入这些目录，且递归枚举出错 fail-closed 记一条 coverage gap（不静默吞掉）。
Write-Host "=== Gradle 清单递归发现探针 ===" -ForegroundColor Cyan
$gradleGapsBefore = $coverageGap.Count
$coverageGap += (Get-GradleCoverageGaps -Root $RepoRoot)
if ($coverageGap.Count -eq $gradleGapsBefore) { Write-Host "  未发现 Gradle 清单（含 libs.versions.toml / build.gradle{,.kts}）。" }

Write-Host ""
if ($coverageGap) { Write-Host "覆盖缺口（-Strict 下视为失败；零覆盖≠合规）：" -ForegroundColor Yellow; $coverageGap | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($warn) { Write-Host "黄牌（人工确认，见 docs/LICENSE-POLICY.md §2/§4）：" -ForegroundColor Yellow; $warn | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($bad)  { Write-Host "禁列命中（违反商用许可政策 §1）：" -ForegroundColor Red;  $bad  | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red } }

if ($bad.Count -gt 0)                       { Write-Host "`n结论：FAIL（发现禁用许可）" -ForegroundColor Red; exit 1 }
if ($Strict -and $warn.Count)               { Write-Host "`n结论：FAIL（-Strict：黄牌未清）" -ForegroundColor Red; exit 1 }
if ($Strict -and $coverageGap.Count)        { Write-Host "`n结论：FAIL（-Strict：有依赖清单却零覆盖，装好工具后重扫）" -ForegroundColor Red; exit 1 }
if ($coverageGap.Count) { Write-Host "`n结论：PASS（无禁用许可），但**有覆盖缺口**（见上）——变 public / 正式发布前请 -Strict 重跑。" -ForegroundColor Yellow; exit 0 }
Write-Host "`n结论：PASS（无禁用许可）。注意：模型权重/数据/素材需另行按政策表登记。" -ForegroundColor Green
exit 0
