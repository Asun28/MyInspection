#requires -Version 7
<#
  Stop hook：打印「经验捕获」提醒模板，把自净化闭环的 capture 步骤机械化（不再依赖模型记得）。
  纯提示、exit 0、绝不阻断。Stop 每轮触发，故经 _throttle 节流（默认每 30 分钟最多一次），防 banner blindness。
  详见 docs/LESSONS.md 与 .claude/skills/lessons/SKILL.md。

  TD61/L82：Stop 钩子的**裸 stdout 不会送达模型**（官方 hooks 文档只把 UserPromptSubmit/UserPromptExpansion/
  SessionStart 列为「stdout 即注入上下文」的例外，Stop 不在其列；Stop 须走 JSON `hookSpecificOutput.additionalContext`
  才能在「回合结束」时把提醒喂回模型、且对话继续）。故本钩子输出改为该 JSON 形态，而非裸文本。
#>
try {
  try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}
  . (Join-Path $PSScriptRoot '_throttle.ps1')
  if (-not (Test-HookThrottle 'lessons' 30)) { exit 0 }   # 冷却期内不重复刷屏
  $msg = @'

[lessons] 自净化复盘：本次会话若踩过非平凡坑/解决过反复问题，入账（同样问题不再重导）：
  pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'
  blocking 或复发≥2 → promote <id> 进必须层；完事 pwsh -File scripts\lessons.ps1 check
  已有相近条目就更新而非新增；发现错的条目删除。
'@
  $out = [ordered]@{ hookSpecificOutput = [ordered]@{ hookEventName = 'Stop'; additionalContext = $msg } }
  [Console]::Out.WriteLine(($out | ConvertTo-Json -Depth 5 -Compress))
}
catch { }
exit 0
