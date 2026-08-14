#requires -Version 7
<#
  Stop hook：session 结束时提醒「离场前写好交接」——若 cwd 有 progress.md（说明在用三件套），
  就跑一次 handoff check 给出 PASS/FAIL 信号，并提示补全 HANDOFF 块。纯提示、exit 0、绝不阻断。
  无 progress.md（未启用规划/简单任务）则静默。标准见 docs/HANDOFF.md。

  TD61/L82：Stop 钩子的裸 stdout 不会送达模型（官方 hooks 文档：只有 UserPromptSubmit/UserPromptExpansion/
  SessionStart 的裸 stdout 会注入模型上下文，Stop 须走 JSON `hookSpecificOutput.additionalContext`），故本钩子
  把提醒文本累积后一次性包成该 JSON 形态输出，而非逐行裸打印。
#>
try {
  try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}
  # 与姊妹 Stop 钩子（lessons-reminder）一致节流：Stop 每轮触发，否则每轮都在
  # 嵌套 pwsh 里重跑 143 行的 handoff.ps1（banner blindness + 每轮开销）。Test-HookThrottle 自身 fail-open
  # （内部出错仍照常提醒）；_throttle.ps1 整体缺失/抛错则随下方外层 catch 静默——本钩子 best-effort、恒 exit 0。
  . (Join-Path $PSScriptRoot '_throttle.ps1')
  if (-not (Test-HookThrottle 'handoff' 30)) { exit 0 }
  $cwd = (Get-Location).Path
  if (Test-Path (Join-Path $cwd 'progress.md')) {
    $lines = @("`n[handoff] 提醒（非每次 Stop 都要照做）：真正离场前，确保 progress.md 末尾 HANDOFF 块已更新（12 字段全填、行动字段具体可执行）且 handoff.ps1 check 为 PASS：")
    $script = Join-Path $cwd 'scripts\handoff.ps1'
    if (Test-Path $script) {
      $lines += @(& pwsh -NoProfile -File $script check 2>&1 | ForEach-Object { "  $_" })
    }
    else {
      $lines += '  pwsh -NoProfile -File scripts\handoff.ps1 check   （不能模糊交接；标准见 docs/HANDOFF.md）'
    }
    $msg = ($lines -join "`n")
    $out = [ordered]@{ hookSpecificOutput = [ordered]@{ hookEventName = 'Stop'; additionalContext = $msg } }
    [Console]::Out.WriteLine(($out | ConvertTo-Json -Depth 5 -Compress))
  }
}
catch { }
exit 0
