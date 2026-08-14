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
