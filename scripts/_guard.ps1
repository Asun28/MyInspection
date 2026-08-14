#requires -Version 7
<#
.SYNOPSIS
  个人账号守卫：本项目的所有 GitHub 操作**仅限配置的个人账号**，禁止其它/组织账号。

.DESCRIPTION
  在任何 gh 写操作（建仓 / push / PR / 回贴状态）前调用 Assert-PersonalAccount。
  - 先清除会话里可能无效/串号的 GH_TOKEN/GITHUB_TOKEN（强制走 keyring）。
  - 校验 `gh api user` 的登录名 == scripts\_config.ps1 的 GhAccount；不符即抛错中止。
  - 可选校验 origin 远端 owner 也为该个人账号，防止误推到组织仓。
  期望账号来自 scripts\_config.ps1（单一配置点）；未配置即 fail-closed（见 Get-ScaffoldGhAccount）。
#>

. (Join-Path $PSScriptRoot '_config.ps1')

function Test-PushTargetOwner {
  <#
  .SYNOPSIS  校验 push/远端 URL 的 host 精确为 github.com 且首路径段(owner)==$Expected。
  .DESCRIPTION
    解析 URL 的 authority（而非子串匹配），杜绝把 github.com/<owner>/ 塞进攻击者 host 路径的伪装
    绕过账号守卫（TD38 / 评审 TD-101：evil.example/github.com/<Expected>/… 及 scp/ssh 内嵌形式）。
    两种合法形态：
      1. scheme://[user@]github.com[:port]/<owner>/…   （https / ssh / git；容许显式端口 :443/:22，C17）
      2. scp-like  [user@]github.com:<owner>/repo.git   （无 scheme）
    host 大小写不敏感（GitHub 恒小写）；两形态皆不匹配 => 拒（fail-closed）。
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Url,
    [Parameter(Mandatory)][string]$Expected
  )
  # 形态一：绝对 URI（有 scheme 且带 //authority → Host 非空）。
  #   opaque URI（如 scp 被 .NET 误当 scheme:path，Host 为空）不在此命中，落形态二。
  $u = $null
  if ([uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$u) -and $u.Host) {
    if ($u.Host -ine 'github.com') { return $false }
    $owner = ($u.AbsolutePath.Trim('/') -split '/', 2)[0]
    return [bool]($owner -and ($owner -ieq $Expected))
  }
  # 形态二：scp-like [user@]host:owner/repo.git
  if ($Url -match '^(?:[^@/]+@)?(?<host>[^:/]+):(?<owner>[^/]+)/') {
    return [bool](($Matches['host'] -ieq 'github.com') -and ($Matches['owner'] -ieq $Expected))
  }
  return $false
}

function Assert-PersonalAccount {
  [CmdletBinding()]
  param(
    [string]$Expected,                      # 不传则取 _config.ps1 的 GhAccount（fail-closed if 未配置）
    [string]$RepoRoot,                      # 给定则一并校验 origin 远端 owner
    [switch]$CheckRemote,
    [string]$RemoteUrl                      # 显式 push 目标 URL（pre-push 钩子传 git 的 $2）；给定则校验它而非仅 origin（C25）
  )
  if (-not $Expected) { $Expected = Get-ScaffoldGhAccount }
  # 函数域原生错误 pin（TD54/TD-117）：本函数按 $LASTEXITCODE 判 gh/git 非零（登录名 / 远端 owner），
  # 对「调用方 / 环境把 $PSNativeCommandUseErrorActionPreference 设 $true」健壮——否则 Stop 下首个预期非零调用抛、崩账号守卫。
  # 函数域（非顶层 dot-source）：_guard 被各脚本 dot-source、也被 selftest 直接取用，pin 就近本函数最稳（镜像 handoff.ps1）。
  $PSNativeCommandUseErrorActionPreference = $false
  Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

  $actual = (& gh api user -q .login 2>$null)
  if ($LASTEXITCODE -ne 0 -or -not $actual) {
    throw "无法确认 GitHub 账号（gh 未登录或凭据无效）。本项目仅限个人账号 '$Expected'，已中止。先跑：gh auth login"
  }
  $actual = "$actual".Trim()
  if ($actual -ne $Expected) {
    throw "GitHub 当前账号为 '$actual'，非个人账号 '$Expected'。本项目严禁用组织/其它账号操作，已中止。"
  }

  if ($CheckRemote -or $RemoteUrl) {
    # 优先校验**实际 push 目标**（钩子传入的 $2），无则回退 origin（C25：防推到非 origin 的组织远端绕过）。
    $url = if ($RemoteUrl) { $RemoteUrl } elseif ($RepoRoot) { (& git -C $RepoRoot remote get-url origin 2>$null) } else { $null }
    if ($url) {
      # 精确解析 authority（host + 首路径段 owner），非子串匹配——防 evil.example/github.com/<Expected>/
      # 这类路径内嵌 host 伪装绕过（TD38/评审 TD-101）；容许显式端口 :443/:22（C17）。见 Test-PushTargetOwner。
      if (-not (Test-PushTargetOwner -Url $url -Expected $Expected)) {
        throw "推送/远端目标 '$url' 不属于个人账号 '$Expected'（或 host 非 github.com）。本项目禁止组织/其它账号/伪装仓库，已中止。"
      }
    }
  }
  Write-Host "账号校验通过：个人账号 $actual ✓" -ForegroundColor DarkGreen
}
