#requires -Version 7
# 共享：把「基线分支**名**」解析成「实际用于 diff 的**引用**」。
#
# review.ps1（第二模型评审面）与 task.ps1（确定性范围闸）都要对照基线算 diff。两者的基线**名**探测
# 合理地不同（task 从主检出跑、优先当前分支；review 审 worktree、直接走 origin/HEAD），故名探测各留各处；
# 但「名 → 该拿哪个 ref 去 diff」这一步**完全相同、且是 TD68 的坑**：分开实现就会一处修、一处漏
# （TD68 正是 review.ps1 修了、task.ps1 没修被 R3 抓出）。故把这一步收敛到本函数，两处共用，防漂移。
#
# 正确的基线 = **本次 ship 的合并目标**，它随工作流不同：
#   · 远端 PR ship（缺省）：GitHub 把分支并入 **origin/<name>**，故须对照远端跟踪引用 origin/<name>。
#     TD68：直接用本地同名分支——本地落后远端 → 基线里的提交被当成本次改动；本地领先 → 反把本次改动隐藏。
#   · 本地 ship（task.ps1 -Local，见 line ~382 `git merge $TaskId` 并入**本地**当前分支）：合并目标是**本地** <name>。
#     此时 origin/<name> 可能不存在（T0 无远端）或**合法地落后**（前一次 -Local 合并已让本地领先）——
#     若仍强用 origin，会把前次本地合并的文件当成本任务的越界改动而误拦（R3 PR #102 三轮指出）。
# 故：-PreferLocal（-Local 工作流）优先本地、origin 兜底；缺省优先 origin、本地兜底。皆无返回 ''（调用方 fail-closed）。
# 已是 'origin/xxx' 形态的显式 BaseName 不受开关影响（只此一候选），故手动 -Base origin/master 恒定。
#
# F2（TD84，R3 PR#102 十轮 + 双审计）：候选一律用**全限定** ref（refs/remotes/origin/<name> / refs/heads/<name>）。
#   短名 `origin/<name>` 经 gitrevisions 优先级会被 `refs/heads/origin/<name>` **先**命中（worktree 共享 refs → 被审
#   分支一条 `git update-ref refs/heads/origin/master <恶意 sha>` 即可影子劫持基线，adversarial fail-open）；短名
#   `<name>` 亦可能歧义。全限定后 rev-parse 无歧义、不可被同名本地 ref 劫持。`^{commit}` 顺带确保解析到提交对象。
# 注：远端 ship「必须有远端 ref、不许静默回退本地陈旧 ref」（F5）由**调用方**（task.ps1 远端 ship 路径）在 scope 闸前
#   显式校验 refs/remotes/origin/<base> 存在——本解析器保留本地兜底，好让**无 origin 的本地 review**（T0 / 单文件夹具）仍可跑。
function Resolve-ScaffoldBaseRef {
  param(
    [Parameter(Mandatory)][string]$GitDir,     # 在哪个 git 目录/工作树里解析（review=worktree, task=worktree）
    [Parameter(Mandatory)][string]$BaseName,   # 基线分支名（如 'master'；已是 'origin/xxx' 形态则只解析远端跟踪引用）
    [switch]$PreferLocal                        # -Local 工作流：合并目标是本地 <name>，优先本地、origin 兜底
  )
  $candidates =
    if ($BaseName -match '^origin/') { @("refs/remotes/origin/$($BaseName -replace '^origin/', '')") }
    elseif ($PreferLocal) { @("refs/heads/$BaseName", "refs/remotes/origin/$BaseName") }
    else { @("refs/remotes/origin/$BaseName", "refs/heads/$BaseName") }
  foreach ($ref in $candidates) {
    & git -C $GitDir rev-parse --verify --quiet "$ref^{commit}" 1>$null 2>$null
    if ($LASTEXITCODE -eq 0) { return $ref }
  }
  return ''
}

# -- T112-CORE-SELFCHECK-SCOPE (TD140 / ADR 0011): the declared self-check for base-ref resolution --
# Resolve-ScaffoldBaseRef decides which ref the diff is taken against, which decides what the scope gate
# and the second-model review even SEE. TD68 is what happens when it is wrong in the quiet direction: a
# local branch behind the remote makes base commits look like this card's changes; ahead, and this card's
# changes are hidden. F2/TD84 is what happens in the adversarial direction: a short `origin/<name>` is
# resolved by gitrevisions through `refs/heads/origin/<name>` first, so one `git update-ref` inside a
# reviewed branch shadow-hijacks the baseline. Both are why the candidates are fully qualified and why
# each is VERIFIED before it is returned.
# The rejected shape:
#   first-candidate-wins - return the first candidate without asking git whether it resolves. It looks
#                          right on a repo that has every ref and is wrong on every repo that does not:
#                          a T0 project with no remote gets handed refs/remotes/origin/master, and a
#                          misspelled base silently resolves instead of returning '' for the caller to
#                          fail closed on.
# NOT hermetic, and deliberately not pretending to be: the function asks git, so the examples build a
# throwaway repo and create refs in it - the same thing selftest 14f already does for its own fixtures.
function Test-ScaffoldBaseRefVia($GitDir, $BaseName, $PreferLocal, $Variant) {
  if ($Variant -eq 'first-candidate-wins') {
    $cands =
      if ($BaseName -match '^origin/') { @("refs/remotes/origin/$($BaseName -replace '^origin/', '')") }
      elseif ($PreferLocal) { @("refs/heads/$BaseName", "refs/remotes/origin/$BaseName") }
      else { @("refs/remotes/origin/$BaseName", "refs/heads/$BaseName") }
    return $cands[0]
  }
  if ($PreferLocal) { return (Resolve-ScaffoldBaseRef -GitDir $GitDir -BaseName $BaseName -PreferLocal) }
  return (Resolve-ScaffoldBaseRef -GitDir $GitDir -BaseName $BaseName)
}

# Declared examples for base-ref resolution. Returns findings as strings and never throws; the temp repo is
# removed in a finally, and a git that cannot init leaves a finding rather than an exception.
function Test-ScaffoldBaseRefExamples {
  [CmdletBinding()]
  param([ValidateSet('first-candidate-wins')][string]$Variant)
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  $repo = Join-Path ([System.IO.Path]::GetTempPath()) ("scaffold-baseref-" + [System.Diagnostics.Process]::GetCurrentProcess().Id + "-" + $PSCmdlet.MyInvocation.PipelineLength)
  if (Test-Path $repo) { Remove-Item -Recurse -Force $repo -ErrorAction SilentlyContinue }
  New-Item -ItemType Directory -Force $repo | Out-Null
  try {
    & git -C $repo init -q --initial-branch=master 1>$null 2>$null
    & git -C $repo -c user.email='t@t.t' -c user.name='t' commit -q --allow-empty -m base 1>$null 2>$null
    & git -C $repo rev-parse --verify --quiet 'refs/heads/master^{commit}' 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
      return @('[BASEREF-EXAMPLE] the throwaway repo could not be built (git init/commit failed), so nothing was measured - a fixture defect wearing a probe defect''s failure text. [FIX] check that git is on PATH.')
    }
    # Phase 1: LOCAL ONLY. No remote-tracking ref exists yet.
    $localOnly = @(
      @{ what = 'local only, default preference - falls back to the local ref rather than returning nothing'; base = 'master'; local = $false; expect = 'refs/heads/master' }
      @{ what = 'local only, -PreferLocal - takes the local ref'; base = 'master'; local = $true; expect = 'refs/heads/master' }
      @{ what = 'local only, an explicit origin/ base has ONE candidate and it does not exist - returns empty so the caller fail-closes'; base = 'origin/master'; local = $false; expect = '' }
      @{ what = 'a base name that exists nowhere returns empty'; base = 'no-such-base'; local = $false; expect = '' }
    )
    foreach ($c in $localOnly) {
      $got = Test-ScaffoldBaseRefVia $repo $c.base $c.local $v
      if ($got -ne $c.expect) { $findings += "[BASEREF-EXAMPLE] case '$($c.what)' resolved to '$got', expected '$($c.expect)'. The base ref decides what the scope gate and the review even see (TD68/TD84). [FIX] fix Resolve-ScaffoldBaseRef, never the example." }
    }
    # Phase 2: add the remote-tracking ref, so both candidates exist and PREFERENCE is what decides.
    & git -C $repo update-ref refs/remotes/origin/master HEAD 1>$null 2>$null
    $bothRefs = @(
      @{ what = 'both refs exist, default preference - the remote-tracking ref wins, because a remote ship merges into origin/<name> (TD68)'; base = 'master'; local = $false; expect = 'refs/remotes/origin/master' }
      @{ what = 'both refs exist, -PreferLocal - the local ref wins, because a -Local ship merges into the local branch'; base = 'master'; local = $true; expect = 'refs/heads/master' }
      @{ what = 'an explicit origin/ base resolves to the fully qualified remote ref, never a same-named local head (F2/TD84)'; base = 'origin/master'; local = $false; expect = 'refs/remotes/origin/master' }
    )
    foreach ($c in $bothRefs) {
      $got = Test-ScaffoldBaseRefVia $repo $c.base $c.local $v
      if ($got -ne $c.expect) { $findings += "[BASEREF-EXAMPLE] case '$($c.what)' resolved to '$got', expected '$($c.expect)'. [FIX] fix Resolve-ScaffoldBaseRef, never the example." }
    }
  }
  finally { Remove-Item -Recurse -Force $repo -ErrorAction SilentlyContinue }
  return $findings
}
