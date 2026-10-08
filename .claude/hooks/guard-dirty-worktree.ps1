#requires -Version 7
<#
  PreToolUse guard (matcher Bash|PowerShell; T0-DIRTY-WORKTREE-HOOK, L218): before one of the git commands in $arms
  below runs in a worktree with uncommitted changes, ask the user, naming the worktree, its branch, its uncommitted
  count and its newest change time. For clean -x or -X, ignored files count too (user ruling 2026-10-09: _local/,
  progress.md and .secrets/ are ignored and in no repository); an ignored directory counts once and is not dated. It
  asks and never denies, so a session discarding its own work confirms once.
  Git runs only for a command that matches, and every git call passes --no-optional-locks.
  Any other command, a target with no uncommitted changes (for clean -x or -X, no ignored files either) or unreadable
  input prints nothing. A command the hook cannot check (a git error or a path it cannot resolve) is left unasked and
  the other commands in the same tool call are still checked; its git calls share one 10 s deadline, after which
  every later command is left unasked too. Asks already found are printed either way, and the hook always exits 0.
  The target worktree is the -C directory (a relative one joins the directory so far), else the directory of the last
  cd, Set-Location, Push-Location or pushd earlier in the same tool call (undone by Pop-Location or popd, and in Bash
  by the closing parenthesis of a ( ... ) subshell), else the event's cwd; for worktree remove it is the path being
  removed. Quoted text is masked before the command is split at ; | && || and newlines and before its parentheses are
  counted. On Windows a Git Bash path /c/... is read as C:/... . Best effort, like guard-frozen: a command built from
  a variable or an alias, git that does not start its segment (after env assignments, sudo, inside pwsh -Command "..."
  or $(...)) or is invoked by a path, git options other than -C and -c before the subcommand, and a directory change
  that does not start its segment (as in if (...) { Set-Location x } on one line) are not seen.
#>
try {
  try { [Console]::InputEncoding = [Text.Encoding]::UTF8 } catch {}
  try { [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false) } catch {}
  $evt = [Console]::In.ReadToEnd() | ConvertFrom-Json
  $cmd = [string]$evt.tool_input.command
  $dir = [string]$evt.cwd
  if (-not $cmd -or -not $dir -or $cmd -notmatch '\bgit\b') { exit 0 }
  $bash = [string]$evt.tool_name -ceq 'Bash'
  . (Join-Path $PSScriptRoot '../../scripts/live-work.ps1') -AsLibrary
  $script:LiveWorkDeadline = [DateTime]::UtcNow.AddSeconds(10)

  # A path as the shell resolves it from $From: quotes stripped, /c/... read as C:/... on Windows, a relative path
  # joined to $From.
  function Resolve-DirtyTarget([string]$From, [string]$Path) {
    $p = $Path.Trim().Trim('"', "'")
    if ($IsWindows -and $p -match '^/([a-zA-Z])(/.*)?$') { $p = $Matches[1] + ':' + $(if ($Matches[2]) { $Matches[2] } else { '/' }) }
    if ([IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $From $p)
  }

  # Per subcommand, the arguments that make it discard or move work (matched case-sensitively).
  $arms = @{
    reset        = '(?:^|\s)--(?:hard|merge|keep)(?:\s|$)'
    checkout     = '(?:^|\s)(?:-f|--force|--|\.)(?:\s|$)'
    restore      = '^(?!.*(?:^|\s)(?:--staged|-S)(?:\s|$))|(?:^|\s)(?:--worktree|-W)(?:\s|$)'
    clean        = '(?:^|\s)(?:-[a-zA-Z]*f[a-zA-Z]*|--force)(?:\s|$)'
    stash        = '^(?!\s*(?:list|show)(?:\s|$))'
    switch       = '(?:^|\s)(?:-C|-f|--force|--force-create|--discard-changes)(?:\s|$)'
    branch       = '(?:^|\s)(?:-[a-zA-Z]*[fDM][a-zA-Z]*|--force)(?:\s|$)'
    'update-ref' = '^'
    worktree     = '^\s*remove\b.*\s(?:-f|--force)(?:\s|$)'
  }
  $arg = '"[^"]+"|''[^'']+''|\S+'
  # $masked is $cmd with the inside of every quoted string replaced by x, so both have the same length and the
  # separators and parentheses found in $masked are shell syntax.
  $masked = [regex]::Replace($cmd, '"[^"]*"|''[^'']*''', { param($m) $m.Value[0] + ('x' * ($m.Length - 2)) + $m.Value[-1] })
  $cuts = @([regex]::Matches($masked, '&&|\|\||[;\r\n|]') | ForEach-Object { $_ }) + @([pscustomobject]@{ Index = $cmd.Length; Length = 0 })
  $asks = [System.Collections.Generic.List[string]]::new()
  $pushed = [System.Collections.Generic.Stack[string]]::new(); $subshells = [System.Collections.Generic.Stack[string]]::new()
  $start = 0
  foreach ($cut in $cuts) {
    $s = $cmd.Substring($start, $cut.Index - $start); $q = $masked.Substring($start, $cut.Index - $start); $start = $cut.Index + $cut.Length
    $n = $q.Length - $q.TrimStart().Length; $s = $s.Substring($n).TrimEnd(); $q = $q.Substring($n, $s.Length)
    while ($q.StartsWith('(')) {
      if ($bash) { $subshells.Push($dir) }
      $n = $q.Length - $q.Substring(1).TrimStart().Length; $s = $s.Substring($n); $q = $q.Substring($n)
    }
    $close = [Math]::Max(0, $q.Split(')').Count - $q.Split('(').Count)
    for ($i = 0; $i -lt $close -and $q.EndsWith(')'); $i++) { $s = $s.Substring(0, $s.Length - 1).TrimEnd(); $q = $q.Substring(0, $s.Length) }
    try {
      if ($s -match "^(?<v>cd|Set-Location|Push-Location|pushd)\s+(?:-(?:Literal)?Path\s+)?(?<d>$arg)") {
        if ($Matches.v -in 'Push-Location', 'pushd') { $pushed.Push($dir) }
        $dir = Resolve-DirtyTarget $dir $Matches.d
      } elseif ($s -match '^(?:Pop-Location|popd)\b') {
        if ($pushed.Count) { $dir = $pushed.Pop() }
      } elseif ($s -match "^(?:&\s*)?git(?:\.exe)?(?<opts>(?:\s+-[Cc]\s+(?:$arg))*)\s+(?<sub>reset|checkout|restore|clean|stash|switch|branch|update-ref|worktree)(?<rest>(?:\s.*)?)$") {
        $sub = $Matches.sub.ToLowerInvariant(); $rest = $Matches.rest; $opts = $Matches.opts
        if ($rest -cmatch $arms[$sub]) {
          $target = $dir
          foreach ($m in [regex]::Matches($opts, "-C\s+(?<d>$arg)")) { $target = Resolve-DirtyTarget $target $m.Groups['d'].Value }
          if ($sub -eq 'worktree' -and $rest -match "remove\s+(?:(?:-f|--force)\s+)*(?<p>""[^""]+""|'[^']+'|[^\s-]\S*)") { $target = Resolve-DirtyTarget $target $Matches.p }
          $top = if (Test-Path -LiteralPath $target -PathType Container) { Invoke-LiveWorkGitRaw $target @('rev-parse', '--show-toplevel') }
          if ($top -and $top.Code -eq 0 -and $top.Lines.Count) {
            $wt = $top.Lines[0]
            $unc = @(Get-LiveWorkUncommitted $wt)
            $ign = @(if ($sub -eq 'clean' -and $rest -cmatch '(?:^|\s)-[a-zA-Z]*[xX][a-zA-Z]*(?:\s|$)') { Invoke-LiveWorkGit $wt @('ls-files', '--others', '--ignored', '--exclude-standard', '--directory') | Where-Object { $_ } })
            if ($unc.Count -or $ign.Count) {
              $branch = @(Invoke-LiveWorkGit $wt @('branch', '--show-current')) -join ''; if (-not $branch) { $branch = 'detached' }
              $held = "$($unc.Count) uncommitted path(s)" + $(if ($ign.Count) { " and $($ign.Count) ignored path(s)" })
              # Get-LiveWorkNewest dates files only, so an ignored directory (listed as dir/) is not dated.
              $asks.Add("git $sub would discard or move work in $wt (branch $branch): $held, newest change $(Format-LiveWorkTime (Get-LiveWorkNewest $wt (@($unc) + @($ign)))).")
            }
          }
        }
      }
    } catch { }
    for ($i = 0; $i -lt $close -and $subshells.Count; $i++) { $dir = $subshells.Pop() }
  }
  if ($asks.Count) {
    $reason = "[LIVE-WORK-ASK] $($asks -join ' ') Another session may hold this worktree (L218): run scripts/live-work.ps1 and confirm with the user before running this."
    @{ hookSpecificOutput = @{ hookEventName = 'PreToolUse'; permissionDecision = 'ask'; permissionDecisionReason = $reason } } | ConvertTo-Json -Depth 5 -Compress
  }
} catch { }
exit 0
