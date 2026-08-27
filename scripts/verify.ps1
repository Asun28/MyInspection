#requires -Version 7
<#
.SYNOPSIS
  验收总闸门：确定性、可复现地跑通项目最小闭环。CI `verify` 必需检查同义。

.DESCRIPTION
  分级把关：闸门 1 的未引导技术面可优雅跳过；闸门 2 是已落地的交付必需闭环，缺失或失败必须红：
    闸门 1：ruff + pytest + 前端 check/test —— 有引导才收紧：pyproject.toml 在才跑 ruff/pytest
            （ruff 经 uv run --no-sync 离线跑；pytest exit 5 未收集到视为通过）；frontend/package.json 与
            node_modules 同在才跑前端检查（未 npm install 只警不挡；已引导而 npm 缺 → fail-closed 红）。
            未引导缺件一律优雅跳过，绝不误红。
    闸门 2：Golden Evidence JVM Core E2E —— 必须精确执行独立 :core:e2eTest，以 --offline --no-daemon
            运行；不启动 Android UI/权限/模拟器/真机。Gradle wrapper 缺失、命令未执行、任务错误或测试非零均
            fail-closed，绝不以 Gate 1 的结果或占位警告假绿。

.EXAMPLE
  pwsh -File scripts\verify.ps1
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）：verify 按退出码判 uv/pytest/npm 非零；缺失（hermetic 单文件测试）即 fail-open 退回原行为
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $RepoRoot

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
$failed = $false

# 归档卡索引是生成投影：PR verify 只读检查，漂移 fail-closed，绝不在验收时自动修工作树。
$archiveScript = Join-Path $RepoRoot 'scripts/archive.ps1'
if (Test-Path -LiteralPath $archiveScript -PathType Leaf) {
  & pwsh -NoProfile -File $archiveScript -RepoRoot $RepoRoot -CheckCardsIndex -Quiet
  if ($LASTEXITCODE -ne 0) {
    Write-Warning '归档卡索引只读检查失败（详见 archive.ps1 上方诊断）'
    $failed = $true
  }
}

# --- 闸门 1：静态检查 + 单元测试（ruff / pytest / 前端 check+test，有引导才收紧）---
Step '闸门 1/2：静态检查 + 契约/单元测试（ruff / pytest / 前端 check+test）'
$hasPyproject = Test-Path "$RepoRoot/pyproject.toml"
$hasUv = [bool](Get-Command uv -ErrorAction SilentlyContinue)

# ruff：pyproject.toml 在（Python 后端已引导）且 uv 可用才跑——「linter 兜底」常设化（命名/静态检查红即 fail）。
# --no-sync：不隐式创建/同步环境（verify 保持离线，闸内绝不联网装依赖）；ruff 由引导步 uv sync 的 dev 组供好，
# 环境缺料即非零计红（fail-closed），不静默下载。selftest 闸 15f(a) 常设断言本调用含 --no-sync。
if ($hasPyproject -and $hasUv) {
  & uv run --no-sync ruff check .
  if ($LASTEXITCODE -ne 0) { Write-Warning 'ruff lint 失败（命名/静态检查红）'; $failed = $true }
  else { Write-Host 'ruff lint 全绿。' }
} elseif ($hasPyproject) {
  # TD43：已引导（pyproject.toml 在）但 uv 缺席——镜像前端分支（:80-82）的 fail-closed 先例，
  # 绝不静默跳过成绿（否则零 lint 却报 PASS，违反本文件 line 17 的 fail-closed 自称）。
  Write-Warning 'pyproject.toml 在场但 uv 不在 PATH——fail-closed 计红（装 uv 后重跑；ruff 未跑）。'
  $failed = $true
} else {
  Write-Host '无 pyproject.toml，跳过 ruff（非 Python 项目正常）。'
}

# pytest：以 pyproject.toml（项目清单）存在为门，而非仅 uv 在 PATH——
# 否则「机器装了 uv 但项目未引导 Python」时 uv run 落到无 pytest 的裸解释器，误红 No module named pytest。
if (-not $hasPyproject) {
  Write-Host '无 pyproject.toml，跳过 pytest（Python 后端未引导 / 非 Python 项目正常；改成你的测试命令）。'
} elseif (-not $hasUv) {
  # TD43：同上——已引导但 uv 缺席须 fail-closed，不得静默跳过成绿（零测试却报 PASS）。
  Write-Warning 'pyproject.toml 在场但 uv 不在 PATH——fail-closed 计红（装 uv 后重跑；pytest 未跑）。'
  $failed = $true
} elseif ((Test-Path "$RepoRoot/backend/tests") -or (Test-Path "$RepoRoot/tests")) {
  $testDir = if (Test-Path "$RepoRoot/backend/tests") { 'backend/tests' } else { 'tests' }
  & uv run python -m pytest $testDir -q
  $code = $LASTEXITCODE
  if ($code -eq 5) { Write-Host '尚未收集到用例（脚手架期），视为通过。' }
  elseif ($code -ne 0) { Write-Warning "pytest 失败（退出码 $code）"; $failed = $true }
  else { Write-Host 'pytest 全绿。' }
} else { Write-Host '无测试目录，跳过（非 Python 项目请改成你的测试命令）。' }

# 前端 check/test：package.json 与 node_modules 同时在（已引导）才跑并计红；
# package.json 在而 node_modules 缺 → 闸 2 风格警告、不挡（保 CI 绿；worktree ship 路径 task.ps1 start 已 npm install，自然收紧）；
# 两者皆无（如仅 *.example）→ 静默跳过。已引导而 npm 不在 PATH → fail-closed 计红（缺料即失败，绝不静默降级为跳过）。
$fePkg = Test-Path "$RepoRoot/frontend/package.json"
$feBootstrapped = $fePkg -and (Test-Path "$RepoRoot/frontend/node_modules")
if ($feBootstrapped -and (Get-Command npm -ErrorAction SilentlyContinue)) {
  Push-Location "$RepoRoot/frontend"
  try {
    & npm run check
    if ($LASTEXITCODE -ne 0) { Write-Warning '前端 npm run check 失败（lint/typecheck 红）'; $failed = $true }
    else { Write-Host '前端 npm run check 全绿。' }
    & npm run test
    if ($LASTEXITCODE -ne 0) { Write-Warning '前端 npm run test 失败'; $failed = $true }
    else { Write-Host '前端 npm run test 全绿。' }
  } finally { Pop-Location }
} elseif ($feBootstrapped) {
  Write-Warning '前端已引导（package.json + node_modules 在）但 npm 不在 PATH——fail-closed 计红（装 npm 后重跑）。'
  $failed = $true
} elseif ($fePkg) {
  Write-Warning '⚠️ 前端闸未引导，跳过——npm install 后 verify 会收紧。'
}

# Android/Gradle 闸（本项目主实现面）：本平台 Gradle wrapper 在（Gradle 工程已引导）才跑并计红；未引导 → 优雅跳过。
# 测试面 = :core 纯 JVM 单测/静检（确定性；--offline 保证闸内绝不联网拉依赖——依赖缓存由引导卡先在线 build 一次填充，
# 缓存缺料即非零计红，fail-closed，同上方 uv --no-sync 立场）。JDK 缺失时 gradlew 自身非零 → 同样计红。
# 不留守护进程的 no-daemon flag（T0-GATE-HARDENING item3，与 CLAUDE.md「命令」节口径一致）：残留 Gradle
# daemon 曾累计 800+ 秒 CPU，破坏 verify 的确定性/可复现——每次跑一个不留后台进程的一次性 JVM（flag 拼写见下方调用行）。
# Windows 的 .\gradlew.bat 显式相对路径（T0-GATE-HARDENING item5）：裸文件名依赖「当前目录参与 exe 搜索」，Claude Code 的
# shell 会话带进程级 NoDefaultCurrentDirectoryInExePath=1 时该行为被关闭，裸 'gradlew.bat' 会报
# "is not recognized"——显式路径免疫此环境差异；非 Windows 经 sh 执行仓内 ./gradlew，不依赖 cmd 或 executable bit。
$gwBat = Join-Path $RepoRoot 'android/gradlew.bat'
$gwSh = Join-Path $RepoRoot 'android/gradlew'
$gwPath = if ($IsWindows) { $gwBat } else { $gwSh }
if (Test-Path -LiteralPath $gwPath -PathType Leaf) {
  Push-Location (Join-Path $RepoRoot 'android')
  try {
    if ($IsWindows) {
      & cmd /c '.\gradlew.bat --offline --no-daemon -q :core:check'
    } else {
      & sh './gradlew' --offline --no-daemon -q :core:check
    }
    if ($LASTEXITCODE -ne 0) { Write-Warning "Android :core check 失败（退出码 $LASTEXITCODE；JDK 缺失/依赖缓存缺料/测试红均计红）"; $failed = $true }
    else { Write-Host 'Android :core check 全绿。' }
  } finally { Pop-Location }
} else {
  Write-Host "无本平台 Android Gradle wrapper（$gwPath），跳过 Android 闸（Gradle 工程未引导时正常；T0 引导卡落地后本闸自动收紧）。"
}

# --- 闸门 2：Golden Evidence JVM Core E2E 闭环 ---
Step '闸门 2/2：集成 / e2e 闭环（确定性 / 离线 / 可复现）'
# 纯 JVM，只跑独立 e2eTest source set；不启动 Android UI/权限/模拟器/真机。wrapper、任务或执行任一缺失都必须红。
$gate2Executed = $false
if (Test-Path -LiteralPath $gwPath -PathType Leaf) {
  # 清空原生命令退出码，避免调用被删/注释时沿用 Gate 1 的零退出码形成假执行证据。
  $global:LASTEXITCODE = $null
  if ($IsWindows) {
    & cmd /c 'android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest'
  } else {
    & sh './android/gradlew' -p android --offline --no-daemon -q :core:e2eTest
  }
  $gate2Exit = $LASTEXITCODE
  $gate2Executed = $null -ne $gate2Exit
  if ($gate2Executed -and $gate2Exit -ne 0) {
    Write-Warning "[GATE2-FAILED] Golden Evidence JVM Core E2E 失败（退出码 $gate2Exit）。"
    $failed = $true
  } elseif ($gate2Executed) {
    Write-Host 'Golden Evidence JVM Core E2E 全绿。'
  }
} else {
  Write-Warning "[GATE2-MISSING] 本平台 Gradle wrapper 缺失（$gwPath），Golden Evidence JVM Core E2E 未执行。"
  $failed = $true
}
if (-not $gate2Executed) {
  Write-Warning '[GATE2-NOT-RUN] Gate 2 命令未执行。'
  $failed = $true
}

Step '结论'
if ($failed) { Write-Host 'verify: FAIL' -ForegroundColor Red; exit 1 }
Write-Host 'verify: PASS（含 Golden Evidence JVM Core E2E 闭环）' -ForegroundColor Green
exit 0
