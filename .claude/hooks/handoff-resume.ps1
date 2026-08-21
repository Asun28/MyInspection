#requires -Version 7
<#
  SessionStart hook：新 session 起始时，若 cwd 有 progress.md 的 HANDOFF 块就打印它，
  让到岗的 session（人或 agent）**即见续接指针**，无需翻聊天记录——planning-with-files 的
  「/clear 后自动恢复」在本仓的落地。纯打印、exit 0、绝不阻断；无三件套则静默（仅一行提示）。
  标准见 docs/HANDOFF.md；校验/生成用 scripts\handoff.ps1。
#>
try {
  try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}
  $p = Join-Path (Get-Location).Path 'progress.md'
  if (Test-Path $p) {
    $text = Get-Content $p -Raw
    $m = [regex]::Match($text, '(?s)<!--\s*HANDOFF:START\s*-->(.*?)<!--\s*HANDOFF:END\s*-->')
    if ($m.Success -and $m.Groups[1].Value.Trim()) {
      [Console]::Out.WriteLine("`n[handoff] 上个 session 的交接指针（progress.md）——先跑 VERIFY 确认态；再重验旧动作，成立才执行 NEXT-ACTION：")
      [Console]::Out.WriteLine($m.Groups[1].Value.Trim())
      [Console]::Out.WriteLine('[handoff] [HANDOFF-REVALIDATE] 执行 NEXT-ACTION 前先确认旧动作仍成立；标准：docs/HANDOFF.md')
      [Console]::Out.WriteLine('[handoff] 校验：pwsh -NoProfile -File scripts\handoff.ps1 check ｜ 标准：docs/HANDOFF.md')
    }
    else {
      [Console]::Out.WriteLine('[handoff] 发现 progress.md 但无有效 HANDOFF 块。续接前请 `pwsh -File scripts\handoff.ps1 check`（见 docs/HANDOFF.md）。')
    }
  }
}
catch { }
exit 0
