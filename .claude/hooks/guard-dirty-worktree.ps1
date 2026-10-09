#requires -Version 7
<#
  PreToolUse guard (matcher Bash|PowerShell; T0-DIRTY-WORKTREE-HOOK, L218): before one of the git commands in $arms
  below runs in a worktree with uncommitted changes, ask the user, naming the worktree, its branch, its uncommitted
  count and its newest change time. For clean -x or -X, ignored files count too (user ruling 2026-10-09: _local/,
  progress.md and .secrets/ are ignored and in no repository); an ignored directory counts once and is not dated. It
  asks and never denies, so a session discarding its own work confirms once.
  Git runs only for a command that matches, every git call passes --no-optional-locks, and core.fsmonitor is off for
  them, so a repository's fsmonitor program does not run (its clean filters still can, as in any git status).
  Any other command, a target with no uncommitted changes (for clean -x or -X, no ignored files either) or unreadable
  input prints nothing. A command the hook cannot check (a git error or a path it cannot resolve) is left unasked and
  the other commands in the same tool call are still checked; its git calls share one 10 s deadline, after which
  every later command is left unasked too. Asks already found are printed either way, and the hook always exits 0.
  A git command is read as its words with quotes removed: the subcommand is matched case-sensitively, bundled short
  options are split (-fq is -f -q), a flag of three or more characters that begins a long name in $arms counts as it
  (--har; also where git rejects it as ambiguous), the value of an option in $takes, spelled out, is neither a flag nor
  an operand (-b fix, -bfix, --source=x; any other option's value is an operand), words after -- are
  operands, and a command missing an operand git requires (branch -D, switch -f, restore without a path, checkout --
  without one) prints nothing. Shell escapes (\--hard, `--hard) are not undone, and a word after -- that starts
  with - is split like options.
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
  $script:LiveWorkGitConfig = @('core.fsmonitor=')   # empty is off on every git; -c outranks the repository's config

  # A path as the shell resolves it from $From: quotes stripped, /c/... read as C:/... on Windows, a relative path
  # joined to $From.
  function Resolve-DirtyTarget([string]$From, [string]$Path) {
    $p = $Path.Trim().Trim('"', "'")
    if ($IsWindows -and $p -match '^/([a-zA-Z])(/.*)?$') { $p = $Matches[1] + ':' + $(if ($Matches[2]) { $Matches[2] } else { '/' }) }
    if ([IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $From $p)
  }
  # Whether a flag is one of $Set; a flag of three or more characters may abbreviate a long name in $Set (--har).
  function Has([string[]]$Set) { foreach ($x in $Set) { foreach ($f in $flags) { if ($f -ceq $x -or ($f.Length -gt 2 -and $x.StartsWith($f, 'Ordinal'))) { return $true } } }; return $false }

  # Per subcommand, when its flags ($flags) and operands ($ops, $after of them after --) make it discard or move work.
  $arms = @{
    reset        = { Has '--hard', '--merge', '--keep' }
    checkout     = { (Has '-f', '--force', '--pathspec-from-file') -or $ops.Contains('.') -or $after }
    restore      = { ($ops.Count -or (Has '--pathspec-from-file')) -and (-not (Has '-S', '--staged') -or (Has '-W', '--worktree')) }
    clean        = { Has '-f', '--force' }
    stash        = { -not ($ops.Count - $after) -or $ops[0] -cnotin 'list', 'show' }
    switch       = { (Has '-C') -or @($flags -clike '--force-*').Count -or ($ops.Count -and (Has '-f', '--force', '--discard-changes')) }
    branch       = { $ops.Count -and (Has '-f', '-D', '-M', '--force') }
    'update-ref' = { $ops.Count -or (Has '--stdin') }
    worktree     = { $ops.Count -ge 2 -and $ops[0] -ceq 'remove' -and (Has '-f', '--force') }
  }
  # Per subcommand, the options taking a value whose value would otherwise change the answer (matched case-sensitively).
  $takes = @{ reset = '(?!)'; checkout = '^-[bB]$'; restore = '^-s$|^--(?:source|conflict)$'; clean = '^-e$'; stash = '^-m$|^--(?:message|pathspec-from-file)$'
    switch = '^-[cC]$'; branch = '(?!)'; 'update-ref' = '(?!)'; worktree = '(?!)' }
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
      } else {
        # The segment's words with quotes removed, then git's -C and -c options and its subcommand.
        $w = @([regex]::Matches(($s -replace '^&\s*'), '(?:"[^"]*"|''[^'']*''|[^\s"'']+)+') | ForEach-Object { $_.Value -replace '"([^"]*)"|''([^'']*)''', '$1$2' })
        $i = 1; $cs = @(); while ($i + 1 -lt $w.Count -and $w[$i] -cin '-C', '-c') { if ($w[$i] -ceq '-C') { $cs += $w[$i + 1] }; $i += 2 }
        $sub = if ($w.Count -gt $i -and $w[0] -match '^git(?:\.exe)?$' -and $w[$i] -cin $arms.Keys) { $w[$i] }
        if ($sub) {
          # Bundled short options split; one that takes a value keeps the rest of its word as that value.
          $t = @(foreach ($a in @($w | Select-Object -Skip ($i + 1))) {
              if ($a -cnotmatch '^-[^-]') { $a; continue }
              for ($k = 1; $k -lt $a.Length; $k++) { $o = "-$($a[$k])"; if ($k + 1 -lt $a.Length -and $o -cmatch $takes[$sub]) { "$o=$($a.Substring($k + 1))"; break }; $o } })
          # An option's value (after = or the next word) is dropped; one with no value is not counted.
          $flags = [System.Collections.Generic.List[string]]::new(); $ops = [System.Collections.Generic.List[string]]::new(); $dd = $false; $after = 0
          for ($k = 0; $k -lt $t.Count; $k++) {
            if ($dd -or $t[$k] -notmatch '^-.') { $ops.Add($t[$k]); $after += [int]$dd }
            elseif ($t[$k] -ceq '--') { $dd = $true }
            else { $o = ($t[$k] -split '=', 2)[0]; if ($o -cnotmatch $takes[$sub] -or $t[$k].Contains('=') -or (++$k) -lt $t.Count) { $flags.Add($o) } }
          }
          if (& $arms[$sub]) {
            $target = $dir
            foreach ($c in $cs) { $target = Resolve-DirtyTarget $target $c }
            if ($sub -ceq 'worktree') { $target = Resolve-DirtyTarget $target $ops[1] }
            $top = if (Test-Path -LiteralPath $target -PathType Container) { Invoke-LiveWorkGitRaw $target @('rev-parse', '--show-toplevel') }
            if ($top -and $top.Code -eq 0 -and $top.Lines.Count) {
              $wt = $top.Lines[0]
              $unc = @(Get-LiveWorkUncommitted $wt)
              $ign = @(if ($sub -ceq 'clean' -and (Has '-x', '-X')) { Invoke-LiveWorkGit $wt @('ls-files', '--others', '--ignored', '--exclude-standard', '--directory') | Where-Object { $_ } })
              if ($unc.Count -or $ign.Count) {
                $branch = @(Invoke-LiveWorkGit $wt @('branch', '--show-current')) -join ''; if (-not $branch) { $branch = 'detached' }
                $held = "$($unc.Count) uncommitted path(s)" + $(if ($ign.Count) { " and $($ign.Count) ignored path(s)" })
                # Get-LiveWorkNewest dates files only, so an ignored directory (listed as dir/) is not dated.
                $asks.Add("git $sub would discard or move work in $wt (branch $branch): $held, newest change $(Format-LiveWorkTime (Get-LiveWorkNewest $wt (@($unc) + @($ign)))).")
              }
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
