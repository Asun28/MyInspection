#requires -Version 7
<#
  PreToolUse guard (matcher Bash|PowerShell; T0-LIVE-WORK-GUARD, L218): before a git command that discards or moves
  work runs in a worktree with uncommitted changes, ask the user, naming the worktree, its branch, its uncommitted
  count and its newest change time. It asks and never denies, so a session discarding its own work confirms once.
  Git runs only for a command that matches. Any other command, a clean worktree, unreadable input or any error prints
  nothing, and the hook always exits 0.
  The target worktree is the -C directory, else the directory of the last cd / Set-Location / Push-Location earlier in
  the same command, else the event's cwd; for worktree remove it is the path being removed. Best effort, like
  guard-frozen: a command built from a variable or an alias is not seen.
#>
try {
  try { [Console]::InputEncoding = [Text.Encoding]::UTF8 } catch {}
  try { [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false) } catch {}
  $evt = [Console]::In.ReadToEnd() | ConvertFrom-Json
  $cmd = [string]$evt.tool_input.command
  $dir = [string]$evt.cwd
  if (-not $cmd -or -not $dir) { exit 0 }
  . (Join-Path $PSScriptRoot '../../scripts/live-work.ps1') -AsLibrary
  # Per subcommand, the arguments that make it discard or move work.
  $arms = @{
    reset        = '(?:^|\s)--(?:hard|merge|keep)(?:\s|$)'
    checkout     = '(?:^|\s)(?:-f|--force|--|\.)(?:\s|$)'
    restore      = '^(?!.*(?:^|\s)(?:--staged|-S)(?:\s|$))|(?:^|\s)(?:--worktree|-W)(?:\s|$)'
    clean        = '(?:^|\s)(?:-[a-zA-Z]*f[a-zA-Z]*|--force)(?:\s|$)'
    stash        = '^(?!\s*(?:list|show)(?:\s|$))'
    switch       = '(?:^|\s)(?:-C|-f|--force|--discard-changes)(?:\s|$)'
    branch       = '(?:^|\s)(?:-[a-zA-Z]*[fDM][a-zA-Z]*|--force)(?:\s|$)'
    'update-ref' = '^'
    worktree     = '^\s*remove\b.*\s(?:-f|--force)(?:\s|$)'
  }
  $unquote = { param($p) $p.Trim().Trim('"', "'") }
  $asks = [System.Collections.Generic.List[string]]::new()
  foreach ($seg in ($cmd -split '&&|\|\||[;\r\n|]')) {
    $s = $seg.Trim()
    if ($s -match '^(?:cd|Set-Location|sl|pushd|Push-Location)\s+(?:-(?:Literal)?Path\s+)?(?<d>"[^"]+"|''[^'']+''|\S+)') {
      $d = & $unquote $Matches.d; if (-not [IO.Path]::IsPathRooted($d)) { $d = Join-Path $dir $d }; $dir = $d; continue
    }
    if ($s -notmatch '(?:^|[&\s])git(?:\.exe)?(?<opts>(?:\s+-C\s+(?:"[^"]+"|''[^'']+''|\S+)|\s+-c\s+\S+)*)\s+(?<sub>reset|checkout|restore|clean|stash|switch|branch|update-ref|worktree)(?<rest>(?:\s.*)?)$') { continue }
    $sub = $Matches.sub; $rest = $Matches.rest; $opts = $Matches.opts
    if ($rest -cnotmatch $arms[$sub]) { continue }
    $target = $dir
    foreach ($m in [regex]::Matches($opts, '-C\s+(?<d>"[^"]+"|''[^'']+''|\S+)')) { $c = & $unquote $m.Groups['d'].Value; $target = if ([IO.Path]::IsPathRooted($c)) { $c } else { Join-Path $target $c } }
    if ($sub -eq 'worktree' -and $rest -match 'remove\s+(?:(?:-f|--force)\s+)*(?<p>"[^"]+"|''[^'']+''|[^\s-]\S*)') { $p = & $unquote $Matches.p; $target = if ([IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $target $p } }
    if (-not (Test-Path -LiteralPath $target)) { continue }
    $top = @(& git -C $target rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $top.Count) { continue }
    $unc = @(Get-LiveWorkUncommitted $top[0])
    if (-not $unc.Count) { continue }
    $branch = "$(& git -C $top[0] branch --show-current 2>$null)".Trim(); if (-not $branch) { $branch = 'detached' }
    $asks.Add("git $sub would discard or move work in $($top[0]) (branch $branch): $($unc.Count) uncommitted path(s), newest change $(Format-LiveWorkTime (Get-LiveWorkNewest $top[0] $unc)).")
  }
  if ($asks.Count) {
    $reason = "[LIVE-WORK-ASK] $($asks -join ' ') Another session may hold this worktree (L218): run scripts/live-work.ps1 and confirm with the user before running this."
    @{ hookSpecificOutput = @{ hookEventName = 'PreToolUse'; permissionDecision = 'ask'; permissionDecisionReason = $reason } } | ConvertTo-Json -Depth 5 -Compress
  }
} catch { }
exit 0
