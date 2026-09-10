#requires -Version 7
<#
  Stop 钩子共用的**节流器**：Stop 事件**每轮**（每次助手回复结束）都触发，若每个提醒都每轮打印，
  会造成 banner blindness（提醒本身被脱敏忽略，恰是它想防的失败模式）。
  本助手让一个具名提醒在冷却期内最多打印一次（默认 30 分钟），把「每轮刷屏」降为「偶尔提示」。
  节流戳放 _local/.hook-stamps/（_local 已 gitignored）。节流失败一律退回「照常提醒」，绝不吞提醒。
#>
function Test-HookThrottle {
  param(
    [Parameter(Mandatory)][string]$Name,
    [int]$Minutes = 30,
    [string]$ContentKey
  )
  try {
    $root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
    $dir = Join-Path $root '_local/.hook-stamps'
    New-Item -ItemType Directory -Force $dir -ErrorAction SilentlyContinue | Out-Null
    $stamp = Join-Path $dir ($Name + '.txt')
    $keyed = $PSBoundParameters.ContainsKey('ContentKey')
    if (Test-Path $stamp) {
      $cool = ((Get-Date) - (Get-Item $stamp).LastWriteTime).TotalMinutes -lt $Minutes
      if ($cool) {
        # Periodic nudges retain their time-only rule. State reports may break
        # cooldown only when their caller explicitly supplies a changed key.
        if (-not $keyed) { return $false }
        $lines = @(Get-Content -LiteralPath $stamp -ErrorAction SilentlyContinue)
        $stored = if ($lines.Count -ge 2) { [string]$lines[1] } else { '' }
        if ($stored -ceq $ContentKey) { return $false }
      }
    }
    $payload = if ($keyed) { @((Get-Date).ToString('o'), $ContentKey) } else { @((Get-Date).ToString('o')) }
    Set-Content -LiteralPath $stamp -Value $payload -Encoding utf8 -ErrorAction SilentlyContinue
    return $true
  } catch { return $true }   # 节流自身出错 => 照常提醒（fail-open：宁可多提醒，不可静默吞掉）
}
