#requires -Version 7
<#
.SYNOPSIS
  依赖许可证守卫（商用）：扫描后端 PyPI、前端 npm 与 Gradle 已解析 classpath 的许可；禁列及 Gradle
  元数据/子进程/POM 失败均非零退出。规则见 docs\LICENSE-POLICY.md；模型权重/数据/字体/素材仍须人工登记。

.DESCRIPTION
  - 后端：优先 `uv run --with pip-licenses`（在项目环境内内省到项目实际安装依赖）；无 uv 时尝试 `pip-licenses`，再无则跳过并告警。
  - 前端：若有 frontend\package.json，用 `npx --yes license-checker --json`。
  - Gradle：离线解析 app debug/release runtime、core runtime/testRuntime 四张图，从缓存 POM 或受控精确豁免取许可。
  - 禁列（正则，大小写不敏感）：GPL / AGPL / SSPL / EUPL / EPL / CC-BY-NC / non-?commercial / research[- ]only。
  - LGPL/OpenRAIL/MPL 单独标黄（进程外 CLI / 文件级 copyleft 等隔离用法可接受；其它需人工确认）。
  - 退出码：发现禁列或 Gradle 未知/损坏元数据=1；仅黄=0（带告警）；干净=0。
.EXAMPLE
  pwsh -File scripts\check-licenses.ps1
#>
[CmdletBinding()]
param(
  [switch]$Strict,     # -Strict 时 LGPL/OpenRAIL 等黄牌也算失败
  [switch]$AsLibrary   # 库模式：只定义许可/Gradle 发现与扫描辅助函数即返回——不 Set-Location/不扫描/不触 git/不 exit（供 selftest 17p/17cc 复用；镜像 check-secrets.ps1 -AsLibrary）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_config.ps1')

# ── 许可分类（库导出区 · 置于 Set-Location/扫描之前，令 -AsLibrary 可安全取用；主流程与 selftest 17p 共用单一真相源）──
# 禁列：负向后顾 (?<!L)GPL 确保 LGPL 不被 GPL 命中；AGPL 显式列入禁列。
# 既匹配缩写（GPL/AGPL），也匹配**拼写全称**（Affero / General Public License）——治「分类器只给全称、
# 元数据无缩写 → GPL 依赖漏判」；(?<!Lesser )General Public License 让 LGPL 全称不误入禁列。
$forbidden = 'AGPL|(?<!L)GPL|SSPL|EUPL|EPL|Eclipse Public License|CC-?BY-?NC|non-?commercial|research[ -]?only|Affero|(?<!Lesser )General Public License'
$nonDowngradableForbidden = 'AGPL|Affero|SSPL|EUPL|EPL|Eclipse Public License|CC-?BY-?NC|non-?commercial|research[ -]?only'
$yellow    = 'LGPL|OpenRAIL|RAIL|MPL|Lesser General Public License'
# Gradle 的 POM 许可不是任意文本即可放行：只承认政策 §2 已明确允许的**完整名称**。每个模式均锚定
# 首尾，并先统一空白/大小写；不能让 `Mystery Apache License`、`Unknown MPL-like` 等带关键词的未知文本
# 借 substring 通过。PyPI/npm 继续沿用既有较宽的 Scan 行为；此收紧仅适用于 TD2 的 Gradle 元数据路径。
$gradlePermissiveLicensePatterns = @(
  '^(?:THE )?APACHE(?: SOFTWARE)?(?: LICENSE)?(?:,? VERSION)?[- ]?2(?:\.0)?(?:,? JANUARY 2004)?$',
  '^(?:THE )?MIT(?: LICENSE)?$',
  '^(?:THE |NEW )?BSD[- ]?(?:2|3)[- ]?CLAUSE(?: LICENSE)?$',
  '^ISC(?: LICENSE)?$',
  '^(?:THE )?UNLICENSE$',
  '^0BSD$',
  '^PYTHON[- ]?2\.0(?: LICENSE)?$'
)
$gradleYellowLicensePatterns = @(
  '^LGPL[- ]?(?:2(?:\.0|\.1)?|3(?:\.0)?)$',
  '^(?:GNU )?LESSER GENERAL PUBLIC LICENSE(?:,? VERSION)? ?(?:2(?:\.0|\.1)?|3(?:\.0)?)$',
  '^(?:CREATIVEML )?OPEN ?RAIL-M(?: LICENSE)?$',
  '^MOZILLA PUBLIC LICENSE(?:,? VERSION)? ?2\.0$',
  '^MPL[- ]?2\.0$'
)
$gradlePlainGplPatterns = @(
  '^GPL[- ]?(?:2(?:\.0)?|3(?:\.0)?)$',
  '^(?:GNU )?GENERAL PUBLIC LICENSE(?:,? VERSION)? ?(?:2(?:\.0)?|3(?:\.0)?)$'
)
# 纯 GPL（分发触发型 copyleft）：缩写严格排除 L(GPL)/A(GPL)；全称严格排除 Lesser/Affero 前缀。
# 仅用于 Distributes=$false 的降级判定——只降**分发触发**的纯 GPL，绝不碰 AGPL/Affero(网络)/其它触发点。
$gplPlain  = '(?<!L)(?<!A)GPL|(?<!Lesser )(?<!Affero )General Public License'
$Distributes = Get-ScaffoldDistributes   # 项目是否分发软件（GPL 触发点判定，⚖️ 非法律意见）
$bad = @(); $warn = @(); $coverageGap = @()

function Scan($name, $license) {
  if ([string]::IsNullOrWhiteSpace($license)) { $script:warn += "$name => 许可缺失/未知（-Strict 下视为不合规）"; return }
  # 禁列先于任何降级。唯一例外是不分发时的纯 GPL；AGPL/EPL/SSPL/EUPL/非商用等绝不因混入黄牌文本而绕过。
  if ($license -match $forbidden) {
    $mayDowngradePlainGpl = -not $script:Distributes -and $license -match $gplPlain -and $license -notmatch $nonDowngradableForbidden
    if (-not $mayDowngradePlainGpl) { $script:bad += "$name => $license"; return } # shared fatal-before-downgrade
  }
  if ($license -match $yellow) {
    $script:warn += "$name => $license（黄牌：需人工确认用途/链接方式）"; return
  }
  # C21：项目声明不分发（Distributes=$false）时，**纯 GPL**（分发触发型 copyleft）降为黄牌而非致命——不分发则 GPL 分发义务不触发。
  if (-not $script:Distributes -and $license -match $gplPlain) {
    $script:warn += "$name => $license（黄牌：纯 GPL 且本项目声明不分发[Distributes=`$false] → copyleft 分发触发点未命中；须人工确认确实不分发/不随产品交付二进制，AGPL/SaaS/网络提供除外。变 public 前用 -Strict 复核）"; return
  }
}

function Test-GradleNormalizedLicense([Parameter(Mandatory)][string]$NormalizedLicense, [Parameter(Mandatory)][string[]]$Patterns) {
  foreach ($pattern in $Patterns) {
    if ($NormalizedLicense -cmatch $pattern) { return $true }
  }
  return $false
}

function Get-GradleLicenseClassification([Parameter(Mandatory)][string]$License) {
  $normalized = [regex]::Replace($License.Trim().ToUpperInvariant(), '\s+', ' ')
  if (Test-GradleNormalizedLicense -NormalizedLicense $normalized -Patterns $gradlePlainGplPatterns) { return 'plain-gpl' }
  # 禁列刻意保持广匹配：不明文本只要出现 GPL/EPL/非商用等风险信号，就宁可拒绝而不降级。
  if ($License -match $forbidden) { return 'forbidden' }
  if (Test-GradleNormalizedLicense -NormalizedLicense $normalized -Patterns $gradleYellowLicensePatterns) { return 'yellow' }
  if (Test-GradleNormalizedLicense -NormalizedLicense $normalized -Patterns $gradlePermissiveLicensePatterns) { return 'permissive' }
  return 'unknown'
}

# ── Gradle 清单发现（库导出区，供 selftest dot-source 直测——修正 R3 finding：先前用
#   Get-ChildItem -Recurse 再 Where-Object 后置过滤，仍会**真的下钻进** .gradle/build/node_modules/.git
#   再丢弃结果，大型/不可读的被排除子树照样被遍历、可能产生假的枚举失败。改成手写栈式遍历，在**下钻前**
#   判断目录名是否在排除表——被排除目录从不会被 push 进栈，根本不会被枚举到）──
# -Enumerator 可注入（默认即真实 Get-ChildItem）：测试用它换成会对指定目录抛错的桩，
# 不必真的靠 Windows ACL 拒绝读权限去模拟「子树不可读」（省掉 icacls 的平台特定性与清理风险）。
# 名称级排除适用于任意深度的缓存/产物/机密目录；路径级排除只针对仓库 ignore 契约里的特定位置，避免把业务树中
# 同名目录一并跳过。三组规则共同覆盖根 .gitignore 与 android/.gitignore 的目录型条目。
#
# **大小写语义必须跟随文件系统**（T0-GATE-FIXFORWARD · T0-GATE-HARDENING 事后 R3 block ①）：PowerShell 的
# `-contains`/`-notcontains` 恒**不敏感**，而 `String.StartsWith(string)` 恒**敏感**——先前一行里两套语义并存。
# 在 Linux（敏感）上，被追踪的 `Build/`、`Data/` 会被当成 ignore 的 `build/`、`data` **静默剪掉**：而"没扫到"
# 会被下游当成"没有清单"，闸在看不见时反而变安静，正是本闸 fail-closed 立意要根除的形态。反向在 Windows
# （不敏感）上，`Android/` 前缀匹配不上 `android/`，该剪的 `.kotlin` 反而没剪。故下面所有路径/名称比较**只经
# 这三个比较器** Test-GradleNameEquals / Test-GradleNameInList / Test-GradlePathPrefix（前缀比较也在内——
# 裸 $path.StartsWith(prefix) 是恒敏感的，把它留在外面就等于把病灶留了一半），缺省语义统一由
# $gradlePathComparison 按 OS 决定。由 selftest 17cc(case) 的 OS 分支夹具 + 每个比较器各自的删除变异
# + 缺省语义替换变异钉住（任一被摘掉或缺省改成本平台的错误语义即必红）。
# **已知简化（刻意，非疏漏）**：按 OS 判定而非按卷探测。macOS 的 APFS/HFS+ 缺省不敏感但**可格式化为敏感**，
# Windows 亦可开启按目录的大小写敏感标志——这两种少数配置下本缺省会偏保守（同 Linux 的过剪风险）。
# 不做运行期卷探测：本仓 CI 只有 windows-latest + ubuntu-latest 两个矩阵点，探测要为每个 $Root 做一次
# 建文件/改大小写的 I/O，成本与复杂度都换不回等值的正确性。需要时调用方可显式传 -Comparison 覆盖缺省
# （三个比较器都收该参数，selftest 17cc(case) 的判据子进程正是这样对每个比较器两模式直测的）。
$gradlePathComparison = if ($IsWindows -or $IsMacOS) { [System.StringComparison]::OrdinalIgnoreCase } else { [System.StringComparison]::Ordinal }
function Test-GradleNameEquals {
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Left,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Right,
    [System.StringComparison]$Comparison = $gradlePathComparison
  )
  return [string]::Equals($Left, $Right, $Comparison)
}
function Test-GradleNameInList {
  param(
    [string[]]$List,
    [Parameter(Mandatory)][AllowEmptyString()][string]$Value,
    [System.StringComparison]$Comparison = $gradlePathComparison
  )
  foreach ($item in $List) { if (Test-GradleNameEquals -Left $item -Right $Value -Comparison $Comparison) { return $true } }
  return $false
}
# 前缀比较也必须走同一缺省语义：裸 $path.StartsWith('android/') 是**恒大小写敏感**的，与上面两个
# 比较器不同源，正是"一行之内两套语义"的病灶本身。收进具名函数后，五个调用点共用同一个 $gradlePathComparison。
function Test-GradlePathPrefix {
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$Path,
    [Parameter(Mandatory)][string]$Prefix,
    [System.StringComparison]$Comparison = $gradlePathComparison
  )
  return $Path.StartsWith($Prefix, $Comparison)
}
$gradleSkipDirs = @(
  '.gradle', 'build', 'node_modules', '.git',                                  # 既有：Gradle/前端产物缓存 + VCS 元数据
  '.venv', '__pycache__', '.pytest_cache', '.ruff_cache', '.mypy_cache',       # Python 工具链缓存
  '.review', '_local', 'runtime',                                              # 本仓运行时/评审/内部产物（CLAUDE.md 约定，均 gitignored）
  'auth', '.secrets',                                                          # 机密目录——不该下钻
  '.idea', '.vscode'                                                           # IDE 本地配置
)
$gradleSkipRelativePaths = @('data', 'frontend/dist')
$gradleAndroidSkipDirs = @('.kotlin', 'captures', '.cxx')
function Find-GradleManifests {
  param(
    [Parameter(Mandatory)][string]$Root,
    [string[]]$SkipDirs = $gradleSkipDirs,
    [string[]]$SkipRelativePaths = $gradleSkipRelativePaths,
    [string[]]$AndroidSkipDirs = $gradleAndroidSkipDirs,
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
        $relativePath = [System.IO.Path]::GetRelativePath($Root, $e.FullName).Replace('\', '/')
        # 四道剪枝守卫与文件收集**刻意各占一行**（而非合成一个长 -and 链 / elseif 条件）：每行都要能被 selftest
        # 的**单句删除变异**独立击杀（L165：每道守卫配一枚变异，它红了才算数）。合成一行时删任一子句都会连坐
        # 其余几道，变异只能证明"整行在"、证明不了"这一道在"。写法上也让"删掉某道守卫"必然表现为**命中集变化**
        # （而不是崩在未定义变量或语法上），故判据是行为差异、不是异常。
        # 守卫①用**替换**变异而非删除（删掉它 $skip 未定义 → 崩在 StrictMode，证明不了守卫语义）；②③④与
        # 收集行各配一枚**单句删除**变异。见 selftest 17cc(reparse-mut) 与 17cc(case-mut/*)。
        $skip = [bool]($e.Attributes -band [System.IO.FileAttributes]::ReparsePoint)   # 守卫①：目录联接/符号链接绝不下钻（否则可能扫出仓外，或经自引用联接死循环）
        if (-not $skip) { $skip = Test-GradleNameInList -List $SkipDirs -Value $e.Name }                     # 守卫②：名称级排除（任意深度的缓存/产物/机密目录）
        if (-not $skip) { $skip = Test-GradleNameInList -List $SkipRelativePaths -Value $relativePath }      # 守卫③：路径级排除（只针对 ignore 契约里的确切位置）
        if (-not $skip) { $skip = (Test-GradlePathPrefix -Path $relativePath -Prefix 'android/') -and (Test-GradleNameInList -List $AndroidSkipDirs -Value $e.Name) }   # 守卫④：android/ 子树内的本地产物目录
        if (-not $skip) { $stack.Push($e.FullName) }
      } else {
        if (Test-GradleNameInList -List $Names -Value $e.Name) { $found.Add($e.FullName) }   # 收集：清单文件名匹配（同样单占一行，供 17cc(case-mut/names) 删除变异）
      }
    }
  }
  return $found
}

# Gradle 发现结果构建（库导出区，供 selftest dot-source 直测枚举失败→发现错误的映射，不必真跑整份
# 脚本）：两个分支各自 try/catch、单独一行，便于单句删除变异独立覆盖；-Enumerator 透传给 Find-GradleManifests。
function Get-GradleDiscoveryResults {
  param(
    [Parameter(Mandatory)][string]$Root,
    [scriptblock]$Enumerator = { param($d) Get-ChildItem -LiteralPath $d -Force -ErrorAction Stop }
  )
  $hits = @(); $errs = @()
  # 分支①：libs.versions.toml（Gradle 版本目录，卡片点名的目标）——单独一行，便于单句删除变异独立覆盖本分支。
  try { $hits += @(Find-GradleManifests -Root $Root -Names @('libs.versions.toml') -Enumerator $Enumerator) } catch { $errs += "libs.versions.toml 递归枚举失败：$($_.Exception.Message)" }
  # 分支②：build.gradle / build.gradle.kts（传统构建脚本）——单独一行，便于单句删除变异独立覆盖本分支。
  try { $hits += @(Find-GradleManifests -Root $Root -Names @('build.gradle', 'build.gradle.kts') -Enumerator $Enumerator) } catch { $errs += "build.gradle{,.kts} 递归枚举失败：$($_.Exception.Message)" }
  $results = @()
  # fail-closed（T0-TOOLCHAIN finding #4）：枚举出错**不得**被吞掉——吞掉后「没扫到」会被当成「没有清单」。
  # 枚举错误显式返回，主流程把它转换成 [GRADLE-DISCOVERY] 阻断项，绝不静默降级为「未发现」。
  foreach ($e in $errs) { $results += "Gradle：$e ——枚举出错不等于没有清单，零覆盖≠合规（fail-closed，勿静默吞掉）。" }
  if ($hits.Count -gt 0) {
    # Find-GradleManifests 返回的是路径**字符串**（非 FileInfo），直接 .Substring，不取 .FullName。
    $names = @($hits | ForEach-Object { $_.Substring($Root.Length + 1) -replace '\\', '/' } | Sort-Object -Unique)
    # 清单命中只是启动真实 classpath 扫描器的发现回执，不是覆盖缺口。
    $results += "Gradle：检测到 $($names.Count) 个清单（$($names -join ', ')）。"
  }
  return $results
}

# ── Gradle 已解析依赖许可证扫描（TD2）────────────────────────────────────
# 不读 version catalog 推断依赖：只解析 Gradle `dependencies` 对实际 classpath 的输出。四张图覆盖交付
# Android app 的 debug/release runtime 与 core 的 runtime/testRuntime（后者包含 TestNG）。所有调用离线，
# 使“本机缓存里有什么”成为可复验输入，而不在扫描时联网改变解析结果。
$gradleLicenseConfigurations = @(
  [PSCustomObject]@{ Project = ':core'; Configuration = 'runtimeClasspath'; Label = ':core:runtimeClasspath' },
  [PSCustomObject]@{ Project = ':core'; Configuration = 'testRuntimeClasspath'; Label = ':core:testRuntimeClasspath' },
  [PSCustomObject]@{ Project = ':app'; Configuration = 'debugRuntimeClasspath'; Label = ':app:debugRuntimeClasspath' },
  [PSCustomObject]@{ Project = ':app'; Configuration = 'releaseRuntimeClasspath'; Label = ':app:releaseRuntimeClasspath' }
)

function Add-GradleNonCompliance {
  param([Parameter(Mandatory)][string]$Message)
  $script:bad += "[GRADLE] $Message"
}

function Get-GradleExceptionMap {
  param([Parameter(Mandatory)][string]$Path)

  $empty = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)
  if (-not (Test-Path -LiteralPath $Path)) {
    return [PSCustomObject]@{ Entries = $empty; Error = $null }
  }

  try {
    $raw = Get-Content -LiteralPath $Path -Raw -ErrorAction Stop
    if ($raw.TrimStart() -notmatch '^\[') { throw '顶层必须是 JSON 数组。' }
    $records = @($raw | ConvertFrom-Json -AsHashtable -Depth 16 -ErrorAction Stop)
    foreach ($record in $records) {
      if ($record -isnot [System.Collections.IDictionary]) { throw '数组项必须是对象。' }
      foreach ($field in $record.Keys) {
        if ($field -notin @('coordinate', 'declared_license', 'license', 'evidence_url', 'registered_by', 'registered_on')) {
          throw "记录含不支持字段 $field。"
        }
      }
      foreach ($field in @('coordinate', 'license', 'evidence_url', 'registered_by', 'registered_on')) {
        if (-not $record.ContainsKey($field) -or [string]::IsNullOrWhiteSpace([string]$record[$field])) {
          throw "记录缺少必填字段 $field。"
        }
      }
      $coordinate = [string]$record.coordinate
      if ($coordinate -notmatch '^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+~-]+$') {
        throw "坐标不是精确 GAV：$coordinate"
      }
      $evidenceUrl = [string]$record.evidence_url
      [uri]$uri = $null
      if (-not [uri]::TryCreate($evidenceUrl, [System.UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http', 'https')) {
        throw "evidence_url 必须是绝对 http(s) URL：$coordinate"
      }
      [datetime]$registeredOn = [datetime]::MinValue
      if (-not [datetime]::TryParseExact([string]$record.registered_on, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$registeredOn)) {
        throw "registered_on 必须是 yyyy-MM-dd：$coordinate"
      }
      $declaredLicense = $null
      if ($record.ContainsKey('declared_license')) {
        $declaredLicense = [string]$record.declared_license
        if ([string]::IsNullOrWhiteSpace($declaredLicense)) { throw "declared_license 不能为空：$coordinate" }
      }
      if (-not $empty.ContainsKey($coordinate)) {
        $empty.Add($coordinate, [PSCustomObject]@{
          Fallback = $null
          DeclaredLicenses = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)
        })
      }
      $entry = [PSCustomObject]@{
        License = [string]$record.license
        EvidenceUrl = $evidenceUrl
        RegisteredBy = [string]$record.registered_by
        RegisteredOn = [string]$record.registered_on
      }
      # 两种例外路径都只登记 §2 的精确宽松 canonical；例外绝不能把黄牌、禁列或未知许可降级放行。
      if ((Get-GradleLicenseClassification -License $entry.License) -ne 'permissive') {
        throw "例外记录的 license 必须是政策允许的完整宽松许可：$coordinate"
      }
      $bucket = $empty[$coordinate]
      if ($null -eq $declaredLicense) {
        if ($null -ne $bucket.Fallback) { throw "坐标重复、缺失元数据回退有歧义：$coordinate" }
        $bucket.Fallback = $entry
      } else {
        if ($bucket.DeclaredLicenses.ContainsKey($declaredLicense)) {
          throw "坐标 + declared_license 重复、映射有歧义：$coordinate / $declaredLicense"
        }
        $bucket.DeclaredLicenses.Add($declaredLicense, $entry)
      }
    }
    return [PSCustomObject]@{ Entries = $empty; Error = $null }
  } catch {
    return [PSCustomObject]@{ Entries = $empty; Error = "[GRADLE-OVERRIDE] $($_.Exception.Message)" }
  }
}

function Get-GradleCachedPomInfo {
  param(
    [Parameter(Mandatory)][string]$Coordinate,
    [Parameter(Mandatory)][string]$GradleUserHome
  )

  $parts = $Coordinate.Split(':')
  if ($parts.Count -ne 3) {
    return [PSCustomObject]@{ State = 'Error'; Detail = '坐标不是 GAV。'; Licenses = @(); Paths = @() }
  }
  $group = $parts[0]; $artifact = $parts[1]; $version = $parts[2]
  $coordinateRoot = Join-Path $GradleUserHome "caches\modules-2\files-2.1\$group\$artifact\$version"
  if (-not (Test-Path -LiteralPath $coordinateRoot)) {
    return [PSCustomObject]@{ State = 'Missing'; Detail = '缓存中没有 POM。'; Licenses = @(); Paths = @() }
  }

  try {
    $pomPaths = @(
      Get-ChildItem -LiteralPath $coordinateRoot -Directory -ErrorAction Stop |
        ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter "$artifact-$version.pom" -File -ErrorAction Stop } |
        Sort-Object -Property FullName
    )
  } catch {
    return [PSCustomObject]@{ State = 'Error'; Detail = "读取缓存 POM 失败：$($_.Exception.Message)"; Licenses = @(); Paths = @() }
  }
  if ($pomPaths.Count -eq 0) {
    return [PSCustomObject]@{ State = 'Missing'; Detail = '缓存目录中没有 POM。'; Licenses = @(); Paths = @() }
  }

  $licenses = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
  $licenseSignatures = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
  $missingLicensePaths = [System.Collections.Generic.List[string]]::new()
  foreach ($pomPath in $pomPaths) {
    try {
      # POM 是缓存中的不可信输入：禁止 DTD/外部实体、显式断开 resolver，扫描器绝不因读取元数据而出网。
      $readerSettings = [System.Xml.XmlReaderSettings]::new()
      $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
      $readerSettings.XmlResolver = $null
      $readerSettings.MaxCharactersFromEntities = 0
      $reader = [System.Xml.XmlReader]::Create($pomPath.FullName, $readerSettings)
      try {
        $pom = [System.Xml.XmlDocument]::new()
        $pom.XmlResolver = $null
        $pom.Load($reader)
      } finally {
        if ($null -ne $reader) { $reader.Dispose() }
      }
      $project = $pom.DocumentElement
      if ($null -eq $project -or $project.LocalName -ne 'project') { throw '缺少 project 根元素。' }
      $groupNode = $project.SelectSingleNode('./*[local-name()="groupId"]')
      $parent = $project.SelectSingleNode('./*[local-name()="parent"]')
      $declaredGroup = if ($null -eq $groupNode) { '' } else { [string]$groupNode.InnerText }
      $parentGroupNode = if ($null -eq $parent) { $null } else { $parent.SelectSingleNode('./*[local-name()="groupId"]') }
      if ([string]::IsNullOrWhiteSpace($declaredGroup) -and $null -ne $parentGroupNode) { $declaredGroup = [string]$parentGroupNode.InnerText }
      $artifactNode = $project.SelectSingleNode('./*[local-name()="artifactId"]')
      $declaredArtifact = if ($null -eq $artifactNode) { '' } else { [string]$artifactNode.InnerText }
      $versionNode = $project.SelectSingleNode('./*[local-name()="version"]')
      $declaredVersion = if ($null -eq $versionNode) { '' } else { [string]$versionNode.InnerText }
      $parentVersionNode = if ($null -eq $parent) { $null } else { $parent.SelectSingleNode('./*[local-name()="version"]') }
      if ([string]::IsNullOrWhiteSpace($declaredVersion) -and $null -ne $parentVersionNode) { $declaredVersion = [string]$parentVersionNode.InnerText }
      if ([string]::IsNullOrWhiteSpace($declaredGroup) -or [string]::IsNullOrWhiteSpace($declaredArtifact) -or [string]::IsNullOrWhiteSpace($declaredVersion)) {
        throw 'POM 缺少可验证的 GAV。'
      }
      if ($declaredGroup -cne $group -or $declaredArtifact -cne $artifact -or $declaredVersion -cne $version) {
        throw "POM GAV 不匹配（声明 $declaredGroup`:$declaredArtifact`:$declaredVersion，期望 $Coordinate）。"
      }
      $licenseNodes = @($project.SelectNodes('./*[local-name()="licenses"]/*[local-name()="license"]'))
      $pomLicenses = @($licenseNodes | ForEach-Object {
        $nameNode = $_.SelectSingleNode('./*[local-name()="name"]')
        if ($null -ne $nameNode) { [string]$nameNode.InnerText }
      } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
      if ($pomLicenses.Count -eq 0) {
        $missingLicensePaths.Add($pomPath.FullName)
        continue
      }
      $normalizedLicenses = @($pomLicenses | ForEach-Object { $_.Trim() } | Sort-Object -Unique)
      [void]$licenseSignatures.Add(($normalizedLicenses -join "`u{001F}"))
      foreach ($license in $normalizedLicenses) { [void]$licenses.Add($license) }
    } catch {
      return [PSCustomObject]@{ State = 'Error'; Detail = "POM 解析/校验失败（$($pomPath.FullName)）：$($_.Exception.Message)"; Licenses = @(); Paths = @($pomPaths.FullName) }
    }
  }
  if ($missingLicensePaths.Count -gt 0) {
    if ($licenses.Count -gt 0) {
      return [PSCustomObject]@{ State = 'Error'; Detail = '同一 GAV 的缓存 POM 副本混有缺失与已声明许可证，不能安全回退豁免。'; Licenses = @(); Paths = @($pomPaths.FullName) }
    }
    return [PSCustomObject]@{ State = 'MissingLicense'; Detail = '所有缓存 POM 均未声明 license/name。'; Licenses = @(); Paths = @($pomPaths.FullName) }
  }
  if ($licenseSignatures.Count -ne 1) {
    return [PSCustomObject]@{ State = 'Error'; Detail = '同一 GAV 的缓存 POM 副本声明了冲突许可证，不能任选其一。'; Licenses = @(); Paths = @($pomPaths.FullName) }
  }
  return [PSCustomObject]@{ State = 'Valid'; Detail = $null; Licenses = @($licenses | Sort-Object); Paths = @($pomPaths.FullName) }
}

function Get-GradleCoordinatesFromDependencyOutput {
  param([AllowEmptyCollection()][object[]]$Output)

  $coordinates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
  $errors = [System.Collections.Generic.List[string]]::new()
  foreach ($line in $Output) {
    # Gradle 树边只接收 Maven GAV；`project :core` 等仓内项目并非第三方坐标。
    # 其余任何无法辨认的边都是覆盖缺口，必须 fail-closed，不得被同图的正常 GAV 掩盖。
    $plain = [regex]::Replace([string]$line, "`e\[[0-?]*[ -/]*[@-~]", '')
    if ($plain -notmatch '^\s*(?:\|\s*)*(?:\+---|\\---)\s+(?<body>.+?)\s*$') { continue }
    $body = $Matches.body
    if ($body -match '^project\s+') { continue }
    if ($body -notmatch '^(?<group>[A-Za-z0-9_.-]+):(?<artifact>[A-Za-z0-9_.-]+)(?<tail>.*)$') {
      $errors.Add("无法判定 Gradle 外部依赖边：$body [GRADLE-PARSE]")
      continue
    }
    $module = "$($Matches.group):$($Matches.artifact)"
    $tail = $Matches.tail

    if ($body -match '(?:^|\s)(?:FAILED|\(n\))(?:\s|$)') {
      $errors.Add("$module => Gradle 报告为未解析边：$body [GRADLE-UNRESOLVED]")
      continue
    }

    $resolvedVersion = $null
    if ($tail -match '^(?::.*?)?\s+->\s+(?<resolved>[A-Za-z0-9_.+~-]+)(?:\s+\((?:c|\*)\))*\s*$') {
      $resolvedVersion = $Matches.resolved
    } elseif ($tail -match '^:(?<resolved>[A-Za-z0-9_.+~-]+)(?:\s+\((?:c|\*)\))*\s*$') {
      $resolvedVersion = $Matches.resolved
    } else {
      $errors.Add("$module => 无法判定 Gradle 外部依赖边：$body [GRADLE-PARSE]")
      continue
    }
    [void]$coordinates.Add("$($module):$resolvedVersion")
  }
  return [PSCustomObject]@{
    Coordinates = @($coordinates | Sort-Object)
    Errors = @($errors)
  }
}

function Add-GradleLicenseFinding {
  param(
    [Parameter(Mandatory)][string]$Coordinate,
    [Parameter(Mandatory)][string[]]$Licenses,
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string[]]$Configurations,
    [AllowNull()][object]$DeclaredLicenseMappings = $null
  )

  $licenseText = $Licenses -join '; '
  Write-Host "  - $Coordinate => $licenseText [$Source; configurations: $($Configurations -join ', ')]"
  foreach ($license in $Licenses) {
    $classification = Get-GradleLicenseClassification -License $license
    if ($classification -eq 'plain-gpl') {
      if (-not $script:Distributes) {
        $script:warn += "$Coordinate => $license（Gradle 黄牌：纯 GPL 且本项目声明不分发[Distributes=`$false]；变 public 前用 -Strict 复核）"
      } else {
        Add-GradleNonCompliance "$Coordinate => $license [GRADLE-FORBIDDEN]"
      }
      continue
    }
    if ($classification -eq 'forbidden') {
      Add-GradleNonCompliance "$Coordinate => $license [GRADLE-FORBIDDEN]" # direct forbidden classification
      continue
    }
    if ($classification -eq 'yellow') {
      $script:warn += "$Coordinate => $license（Gradle 黄牌：需人工确认用途/链接方式）"
      continue
    }
    if ($classification -eq 'unknown') {
      if ($null -ne $DeclaredLicenseMappings -and $DeclaredLicenseMappings.ContainsKey($license)) {
        $mapping = $DeclaredLicenseMappings[$license]
        # Get-GradleExceptionMap 已限定 canonical 只可为 permissive；这里保留纵深检查，避免未来调用点绕过解析器。
        if ((Get-GradleLicenseClassification -License $mapping.License) -ne 'permissive') {
          Add-GradleNonCompliance "$Coordinate => declared_license 映射的非允许 canonical '$($mapping.License)' [GRADLE-OVERRIDE]"
        } else {
          Write-Host "    exact declared-license mapping: '$license' => $($mapping.License) [override $($mapping.EvidenceUrl), $($mapping.RegisteredBy) $($mapping.RegisteredOn)]"
        }
      } else {
        Add-GradleNonCompliance "$Coordinate => 未被政策识别的许可 '$license' [GRADLE-UNKNOWN]"
      }
    }
  }
}

function Get-GradleWrapperPath {
  param(
    [Parameter(Mandatory)][string]$AndroidRoot,
    [bool]$UseWindows = $IsWindows
  )

  return (Join-Path $AndroidRoot $(if ($UseWindows) { 'gradlew.bat' } else { 'gradlew' }))
}

function Get-GradleWrapperDistributionState {
  param(
    [Parameter(Mandatory)][string]$AndroidRoot,
    [Parameter(Mandatory)][string]$GradleUserHome
  )

  $propertiesPath = Join-Path $AndroidRoot 'gradle/wrapper/gradle-wrapper.properties'
  if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    return [PSCustomObject]@{ Ready = $false; Detail = "找不到 wrapper properties：$propertiesPath" }
  }
  $urlLines = @(Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match '^\s*distributionUrl=' })
  if ($urlLines.Count -ne 1) {
    return [PSCustomObject]@{ Ready = $false; Detail = "distributionUrl 必须恰好一条：$propertiesPath" }
  }
  $urlText = $urlLines[0].Substring($urlLines[0].IndexOf('=') + 1).Trim().Replace('\:', ':')
  [uri]$distributionUri = $null
  if (-not [uri]::TryCreate($urlText, [UriKind]::Absolute, [ref]$distributionUri)) {
    return [PSCustomObject]@{ Ready = $false; Detail = "distributionUrl 不是绝对 URL：$urlText" }
  }
  $zipName = [IO.Path]::GetFileName($distributionUri.AbsolutePath)
  if ($zipName -notmatch '^(?<distribution>gradle-(?<version>.+?)-(?:bin|all))\.zip$') {
    return [PSCustomObject]@{ Ready = $false; Detail = "无法从 distributionUrl 判定 Gradle 发行版：$urlText" }
  }
  $distributionName = $Matches.distribution
  $installName = "gradle-$($Matches.version)"

  $hashBytes = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($urlText))
  [Array]::Reverse($hashBytes)
  $hashNumber = [Numerics.BigInteger]::new(@($hashBytes + [byte]0))
  $alphabet = '0123456789abcdefghijklmnopqrstuvwxyz'
  $urlHash = ''
  while ($hashNumber -gt 0) {
    $urlHash = $alphabet[[int]($hashNumber % 36)] + $urlHash
    $hashNumber = [Numerics.BigInteger]::Divide($hashNumber, 36)
  }

  $distributionDir = Join-Path $GradleUserHome "wrapper/dists/$distributionName/$urlHash"
  $okPath = Join-Path $distributionDir "$zipName.ok"
  $binDir = Join-Path $distributionDir "$installName/bin"
  $ready = (Test-Path -LiteralPath $okPath -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $binDir 'gradle') -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $binDir 'gradle.bat') -PathType Leaf)
  return [PSCustomObject]@{
    Ready = $ready
    Detail = if ($ready) { $distributionDir } else { "wrapper distribution 未预置完成：$distributionDir" }
  }
}

function Get-GradleDiagnosticTail {
  param(
    [AllowEmptyCollection()][object[]]$Output,
    [ValidateRange(1, 100)][int]$MaxLines = 20,
    [ValidateRange(200, 10000)][int]$MaxChars = 2000
  )

  $sanitized = @($Output | ForEach-Object {
    $line = [regex]::Replace("$_", "`e\[[0-?]*[ -/]*[@-~]", '')
    $line = [regex]::Replace($line, '(?i)\bAuthorization\s*[:=]\s*(?:Bearer\s+)?\S+', 'Authorization: [REDACTED]')
    $line = [regex]::Replace($line, '(?i)\b(token|password|secret|api[-_]?key)\s*[:=]\s*\S+', '$1=[REDACTED]')
    $line = [regex]::Replace($line, '(?i)(https?://)[^/\s:@]+:[^@\s/]+@', '$1[REDACTED]@')
    if (-not [string]::IsNullOrWhiteSpace($line)) { $line.Trim() }
  } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  if ($sanitized.Count -eq 0) { return '<no output>' }

  $truncated = $sanitized.Count -gt $MaxLines
  $tail = (@($sanitized | Select-Object -Last $MaxLines) -join ' | ')
  $marker = '[TRUNCATED] '
  if ($tail.Length -gt $MaxChars) {
    $truncated = $true
    $tail = $tail.Substring($tail.Length - ($MaxChars - $marker.Length))
  }
  if ($truncated) { return "$marker$tail" }
  return $tail
}

function Invoke-GradleLicenseScan {
  param([Parameter(Mandatory)][string]$Root)

  $androidRoot = Join-Path $Root 'android'
  $wrapper = Get-GradleWrapperPath -AndroidRoot $androidRoot
  if (-not (Test-Path -LiteralPath $wrapper)) {
    Add-GradleNonCompliance "Gradle 清单存在但找不到 wrapper：$wrapper [GRADLE-SUBPROCESS]"
    return
  }

  $gradleUserHome = if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) { Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle' } else { $env:GRADLE_USER_HOME }
  $distribution = Get-GradleWrapperDistributionState -AndroidRoot $androidRoot -GradleUserHome $gradleUserHome
  if (-not $distribution.Ready) {
    Add-GradleNonCompliance "$($distribution.Detail)；为保持许可闸离线，先由 CI/setup 预置 pinned distribution，再重跑扫描 [GRADLE-WRAPPER-OFFLINE]"
    return
  }

  $exceptions = Get-GradleExceptionMap -Path (Join-Path $Root 'configs/licenses/gradle-exceptions.json')
  if ($null -ne $exceptions.Error) { Add-GradleNonCompliance $exceptions.Error }
  $coordinatesByConfiguration = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)
  foreach ($target in $gradleLicenseConfigurations) {
    try {
      if ($IsWindows) {
        $output = @(& $wrapper -p $androidRoot --offline --no-daemon "$($target.Project):dependencies" --configuration $target.Configuration 2>&1)
      } else {
        # android/gradlew is intentionally tracked mode 100644; use sh instead of mutating its mode in a license gate.
        $output = @(& sh $wrapper -p $androidRoot --offline --no-daemon "$($target.Project):dependencies" --configuration $target.Configuration 2>&1)
      }
      $gradleExit = $LASTEXITCODE
    } catch {
      Add-GradleNonCompliance "$($target.Label) => Gradle 子进程启动失败：$($_.Exception.Message) [GRADLE-SUBPROCESS]"
      continue
    }
    if ($gradleExit -ne 0) {
      $diagnosticTail = Get-GradleDiagnosticTail -Output $output
      Add-GradleNonCompliance "$($target.Label) => Gradle 子进程退出 $gradleExit；输出尾段=$diagnosticTail [GRADLE-SUBPROCESS]"
      continue
    }
    $parseResult = Get-GradleCoordinatesFromDependencyOutput -Output $output
    foreach ($parseError in $parseResult.Errors) {
      Add-GradleNonCompliance "$($target.Label) => $parseError"
    }
    $parsed = @($parseResult.Coordinates)
    if ($parsed.Count -eq 0 -and $parseResult.Errors.Count -eq 0) {
      Add-GradleNonCompliance "$($target.Label) => Gradle 输出没有可解析的已解析 GAV [GRADLE-PARSE]"
      continue
    }
    foreach ($coordinate in $parsed) {
      if (-not $coordinatesByConfiguration.ContainsKey($coordinate)) {
        $coordinatesByConfiguration.Add($coordinate, [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal))
      }
      [void]$coordinatesByConfiguration[$coordinate].Add($target.Label)
    }
  }

  if ($coordinatesByConfiguration.Count -eq 0) { return }
  Write-Host "  已从四张 Gradle 已解析图取得 $($coordinatesByConfiguration.Count) 个唯一 GAV（离线、去重、按坐标排序）："
  foreach ($coordinate in @($coordinatesByConfiguration.Keys | Sort-Object)) {
    $pom = Get-GradleCachedPomInfo -Coordinate $coordinate -GradleUserHome $gradleUserHome
    $configurations = @($coordinatesByConfiguration[$coordinate] | Sort-Object)
    $exceptionBucket = if ($exceptions.Entries.ContainsKey($coordinate)) { $exceptions.Entries[$coordinate] } else { $null }
    if ($pom.State -eq 'Error') {
      Add-GradleNonCompliance "$coordinate => $($pom.Detail) [GRADLE-POM]"
      continue
    }
    if ($pom.State -in @('Missing', 'MissingLicense')) {
      if ($null -ne $exceptionBucket -and $null -ne $exceptionBucket.Fallback) {
        $exception = $exceptionBucket.Fallback
        Add-GradleLicenseFinding -Coordinate $coordinate -Licenses @($exception.License) -Source "override $($exception.EvidenceUrl), $($exception.RegisteredBy) $($exception.RegisteredOn)" -Configurations $configurations
      } else {
        Add-GradleNonCompliance "$coordinate => 许可缺失/未知（$($pom.Detail)） [GRADLE-METADATA]"
      }
      continue
    }
    $declaredLicenseMappings = if ($null -ne $exceptionBucket) { $exceptionBucket.DeclaredLicenses } else { $null }
    Add-GradleLicenseFinding -Coordinate $coordinate -Licenses $pom.Licenses -Source 'cached POM' -Configurations $configurations -DeclaredLicenseMappings $declaredLicenseMappings
  }
}

# ── 库模式：正则/Scan/Distributes/Find-GradleManifests/Get-GradleDiscoveryResults 已定义，就此返回——
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

# === 其它未覆盖生态依赖清单探针（C20：治「已支持生态之外零扫描却 PASS」的假绿）===
# 本闸已扫描 PyPI、frontend npm 与 Gradle 已解析 classpath。若仓库存在其它生态的依赖清单而无对应扫描器，
# 零覆盖不等于合规：登记为覆盖缺口，普通模式告警，-Strict 失败。
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

# === Gradle 已解析依赖许可证扫描（TD2）===
# 发现仍复用既有的安全递归器（它会在下钻前跳过缓存/联接）；但“发现了 Gradle”不再登记 advisory
# coverage gap，而是立即扫描真实 classpath。发现错误同样是合规失败，不能让错误伪装成“没有清单”。
Write-Host "=== Gradle 已解析依赖许可证扫描 ===" -ForegroundColor Cyan
$gradleDiscovery = @(Get-GradleDiscoveryResults -Root $RepoRoot)
$gradleDiscoveryErrors = @($gradleDiscovery | Where-Object { $_ -match '递归枚举失败' })
$gradleDiscoveryHits = @($gradleDiscovery | Where-Object { $_ -match '检测到 .* 个清单' })
foreach ($discoveryError in $gradleDiscoveryErrors) {
  Add-GradleNonCompliance "$discoveryError [GRADLE-DISCOVERY]"
}
if ($gradleDiscoveryHits.Count -gt 0 -and $gradleDiscoveryErrors.Count -eq 0) {
  Invoke-GradleLicenseScan -Root $RepoRoot
} elseif ($gradleDiscoveryHits.Count -eq 0 -and $gradleDiscoveryErrors.Count -eq 0) {
  Write-Host "  未发现 Gradle 清单（含 libs.versions.toml / build.gradle{,.kts}）。"
}

Write-Host ""
if ($coverageGap) { Write-Host "覆盖缺口（-Strict 下视为失败；零覆盖≠合规）：" -ForegroundColor Yellow; $coverageGap | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($warn) { Write-Host "黄牌（人工确认，见 docs/LICENSE-POLICY.md §2/§4）：" -ForegroundColor Yellow; $warn | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow } }
if ($bad)  { Write-Host "阻断项（禁列许可 / 扫描或元数据不合规）：" -ForegroundColor Red;  $bad  | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red } }

if ($bad.Count -gt 0)                       { Write-Host "`n结论：FAIL（发现许可或依赖扫描不合规）" -ForegroundColor Red; exit 1 }
if ($Strict -and $warn.Count)               { Write-Host "`n结论：FAIL（-Strict：黄牌未清）" -ForegroundColor Red; exit 1 }
if ($Strict -and $coverageGap.Count)        { Write-Host "`n结论：FAIL（-Strict：有依赖清单却零覆盖，装好工具后重扫）" -ForegroundColor Red; exit 1 }
if ($coverageGap.Count) { Write-Host "`n结论：PASS（无禁用许可），但**有覆盖缺口**（见上）——变 public / 正式发布前请 -Strict 重跑。" -ForegroundColor Yellow; exit 0 }
Write-Host "`n结论：PASS（无禁用许可）。注意：模型权重/数据/素材需另行按政策表登记。" -ForegroundColor Green
exit 0
