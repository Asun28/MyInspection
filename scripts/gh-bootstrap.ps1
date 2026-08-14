#requires -Version 7
<#
.SYNOPSIS
  一次性创建并加固 GitHub 私有仓库（安全工程基线）。幂等：可重复运行。

.DESCRIPTION
  实现「安全的 GitHub 项目」：
    1. 预检：gh 已登录（keyring）、无未追踪机密、工作树干净。
    2. 创建 *私有* 仓库并把 main 推上去（仓库设置后才加规则，避免首推被挡）。
    3. 仓库设置：仅 squash 合并、合并后删分支、开启 auto-merge。
    4. 分支规则集(ruleset)：main 必须走 PR + 必须通过状态检查 verify / R3 状态检查名（随 _config.ps1
       ReviewStatusContext 可换，默认 codex-review；第二模型评审，以「必需状态检查」形式代替人工审批
       → 见 docs\DEVOPS-WORKFLOW.md R3）。
       注：free+private 不支持服务端规则集（403），脚本会探测并优雅跳过（R3 改由客户端 review.ps1 强制）。

  账号守卫：本项目仅限 scripts\_config.ps1 配置的个人账号。
  注意会话进程里可能有一个 *无效* 的 GITHUB_TOKEN，会覆盖 keyring；本脚本在所有 gh 调用前清空它。

.PARAMETER RepoName  仓库名（默认取 _config.ps1 ProjectName，再回退仓库根目录名）。
.PARAMETER Private   默认 $true，创建私有仓库。
.EXAMPLE
  pwsh -File scripts\gh-bootstrap.ps1
#>
[CmdletBinding(SupportsShouldProcess)]
param(
  [string]$RepoName,
  [bool]$Private = $true,
  [string[]]$RequiredChecks,   # 留空 => 下面按 _config 的 ReviewStatusContext 组装（@('verify', <R3 状态名>)）
  [switch]$AsLibrary           # 库模式：只定义 Install-PrePushHook 即返回——不触网络/不建仓/不 exit（供 selftest 17o 复用；镜像 check-secrets.ps1 -AsLibrary）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── pre-push 装钩（库导出区 · 置于建仓主流程之前，令 -AsLibrary 可安全取用；主流程与 selftest 17o 共用单一真相源）──
# 治本 C05：原实现无条件写 $RepoRoot/.git/hooks/pre-push，两处硬伤——
#   ① 忽略 core.hooksPath（Husky 等常设它）：git 根本不看 .git/hooks/pre-push，钩子静默失效却仍报「已装」= 安全控制假成功；
#   ② 静默覆盖既有 pre-push（无链式）：丢用户既有钩子。
# 本函数解析 git 实际钩子目录（honoring core.hooksPath）、不静默覆盖（备份 .local + 链式、保留 stdin/参数）、带标记支持幂等重跑。
function Install-PrePushHook {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$RepoRoot)

  # 原生命令非零不抛（git config 查缺失键返回 1、rev-parse 等按退出码判）——函数域局部置 $false，
  # 对「用户 profile / 环境把 $PSNativeCommandUseErrorActionPreference 设 $true」健壮（镜像 task.ps1:44 防御写法）。
  $PSNativeCommandUseErrorActionPreference = $false

  # 解析 git **实际读取**的钩子目录：直接用 git 自己的路径 API `rev-parse --git-path hooks`——它一并处理
  #   ① core.hooksPath（相对 / `~` 家目录展开 / 绝对；Husky 等常设它）
  #   ② 链接工作树（linked worktree）：返回**主仓公共** hooks 目录，而非 .git/worktrees/<name>/hooks（git 只在公共目录跑 pre-push）。
  # 写死 .git/hooks、或用 `config core.hooksPath` 手拼、或用 `--git-dir` 拼 hooks，都会在上述情形把钩子装到 git 根本不看的地方
  # = 安全控制假成功（C05）。`--git-path hooks` 返回相对路径时按仓库根解析（git 以运行目录为基，此处即 $RepoRoot）。
  $hp = (& git -C $RepoRoot rev-parse --git-path hooks 2>$null)
  if ($hp) { $hp = "$hp".Trim() }
  if (-not $hp) {
    Write-Warning '  未能解析 git 钩子目录（非标准 git 仓？），跳过 pre-push 钩子安装。'
    return
  }
  $hookDir = if ([System.IO.Path]::IsPathRooted($hp)) { $hp } else { Join-Path $RepoRoot $hp }
  if (-not (Test-Path $hookDir)) {
    try { New-Item -ItemType Directory -Force $hookDir -ErrorAction Stop | Out-Null }
    catch { Write-Warning "  无法创建钩子目录 '$hookDir'（$($_.Exception.Message)）——跳过 pre-push 安装。"; return }
  }
  $prePush = Join-Path $hookDir 'pre-push'

  # 单引号 here-string：$ROOT / $0 / $2 / $@ / $$ / $(...) 是 sh 语法，绝不能被 PowerShell 插值。
  # 首行含标记 scaffold-prepush-guard：幂等重跑据此识别本脚手架所装、避免把自己当作既有钩子备份。
  $hookBody = @'
#!/bin/sh
# scaffold-prepush-guard · 由 scripts/gh-bootstrap.ps1 安装：push 前 ① 账号守卫（仅配置的个人账号）② 防泄露闸（check-secrets，非 -Strict）。
# 安装前若已有 pre-push 钩子，其被保留为 <本文件>.local，并在两闸通过后链式调用（原样转交 git 传入的 stdin 与参数）。
# git 传 $1=远端名 $2=远端URL，并经 stdin 传 <local ref> <local sha> <remote ref> <remote sha>。临时绕过（不建议）：git push --no-verify
ROOT=$(git rev-parse --show-toplevel)
# 缓存 stdin：本闸不读 stdin，但链式既有钩子可能需要（如按推送内容判断）——缓存后原样转交。
STDIN_CACHE=$(mktemp 2>/dev/null || echo "${TMPDIR:-/tmp}/scaffold-prepush-$$")
cat > "$STDIN_CACHE"
# ① 账号守卫（不读 stdin）。$ROOT/$2 经**环境变量**（sh 前缀赋值）喂 pwsh，-Command 用 sh 单引号 + $env: 引用：env 值不被
#   pwsh 当代码解析，故 $2（git 远端 URL）里的单引号无法越界注入、撇号克隆路径 $ROOT 也不破坏 -Command（TD-204）。
SCAFFOLD_ROOT="$ROOT" SCAFFOLD_REMOTE="$2" pwsh -NoProfile -ExecutionPolicy Bypass -Command '. "$env:SCAFFOLD_ROOT/scripts/_guard.ps1"; Assert-PersonalAccount -RepoRoot "$env:SCAFFOLD_ROOT" -CheckRemote -RemoteUrl "$env:SCAFFOLD_REMOTE"' < /dev/null || { rm -f "$STDIN_CACHE"; exit 1; }
# ② 防泄露闸（不读 stdin）
pwsh -NoProfile -ExecutionPolicy Bypass -File "$ROOT/scripts/check-secrets.ps1" < /dev/null || { rm -f "$STDIN_CACHE"; exit 1; }
# ③ 链式调用安装前已存在的钩子（若有；按 .local, .local.1, .local.2 … 全部按序调用），原样转交参数与 stdin。
# 不以 [ -x ] 为跳过门槛（Windows 装钩不设可执行位）：Git-for-Windows MSYS 对含 shebang 的文件 [ -x ] 即真、直接 exec 遵其 shebang
# （任意解释器）；无 shebang 者 [ -x ] 假、经 sh 跑（与 Git-for-Windows 对无 shebang 钩子的处理一致）。两分支覆盖，既有钩子绝不被静默丢弃。
for CHAINED in "$0".local*; do
  [ -f "$CHAINED" ] || continue
  if [ -x "$CHAINED" ]; then
    "$CHAINED" "$@" < "$STDIN_CACHE" || { rm -f "$STDIN_CACHE"; exit 1; }
  else
    sh "$CHAINED" "$@" < "$STDIN_CACHE" || { rm -f "$STDIN_CACHE"; exit 1; }
  fi
done
rm -f "$STDIN_CACHE"
exit 0
'@

  # 不静默覆盖：若已存在 pre-push 且非本脚手架所装（无标记），备份为 pre-push.local 供上面 ③ 链式调用。
  if ((Test-Path $prePush) -and ((Get-Content $prePush -Raw -ErrorAction SilentlyContinue) -notmatch 'scaffold-prepush-guard')) {
    $backup = "$prePush.local"
    if (Test-Path $backup) {
      # .local 已被前次备份占用：给当前既有钩子另找不冲突的名（.local.1/.2/…），绝不覆盖前次备份——
      # 且**仍安装**脚手架守卫（关键不变量：绝不能因备份名冲突就 return 留仓库无 pre-push 守卫 = 账号守卫/防泄露闸静默失效）。
      # 装出的钩子按 .local, .local.1, … 顺序**全部**链式调用，故 $backup 也会被自动执行（不丢既有钩子、无需手动并入）。
      $n = 1; while (Test-Path "$prePush.local.$n") { $n++ }
      $backup = "$prePush.local.$n"
      Write-Warning "  $prePush.local 已存在（前次备份）——当前既有 pre-push 另备份为 $backup（不覆盖前次备份）；两者都会在两闸后按序链式调用。"
    }
    Move-Item $prePush $backup -Force
    if (-not $IsWindows) { & chmod +x $backup 2>$null }
    Write-Host "  检测到既有 pre-push 钩子——已备份为 $backup 并在本钩子两闸后链式调用（保留其 stdin/参数）。" -ForegroundColor DarkYellow
  }

  # LF 行尾、无 BOM（sh 钩子要求）。Set-Content -Encoding utf8 在 pwsh7 即「无 BOM UTF-8」，且在受限语言模式(CLM)下仍可用——
  # 比 [System.IO.File]::WriteAllText + New-Object UTF8Encoding 健壮（后者在 CLM 被禁会抛、连带中止整次建仓加固，C19）。
  try {
    ($hookBody -replace "`r`n", "`n") | Set-Content -Path $prePush -NoNewline -Encoding utf8
    if (-not $IsWindows) { & chmod +x $prePush 2>$null }   # *nix 需可执行位；Git-for-Windows 不看该位
    Write-Host "  已装 $prePush（账号守卫 + check-secrets 防泄露闸；路径经 git --git-path 解析，尊重 core.hooksPath/工作树）。绕过：git push --no-verify。" -ForegroundColor DarkGreen
  } catch {
    Write-Warning "  pre-push 钩子安装失败（$($_.Exception.Message)）——仓库其余加固已完成；可在受限策略外手动安装该钩子。"
  }
}

# ── 库模式：Install-PrePushHook 已定义，就此返回——不触网络/不建仓/不 exit（供 selftest 17o dot-source 复用）──
if ($AsLibrary) { return }

try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # UTF-8 输出 + 原生非零按码判（TD54/TD-117）；缺失即 fail-open

# --- 仓库根（脚本位于 scripts\ 下）---
. (Join-Path $PSScriptRoot '_config.ps1')
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $RepoRoot
if (-not $RepoName) { $RepoName = Get-ScaffoldProjectName -RepoRoot $RepoRoot }
# R3 必需检查名从 _config 单一来源取（与 review.ps1 回贴的 context 一致；L26 工具无关，换后端只改 _config）。
if (-not $RequiredChecks) { $RequiredChecks = @('verify', (Get-ScaffoldReviewStatusContext)) }

# --- 关键：忽略会话里那个无效的 token，强制走 keyring（空串仍被 gh 视为“存在”，故用 Remove-Item）---
Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

# --- 个人账号守卫：本项目仅限配置的个人账号（禁组织）---
. (Join-Path $PSScriptRoot '_guard.ps1')
Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# --- 预检 1：gh 登录态 ---
Step '预检：gh 登录态（keyring）'
& gh auth status 2>&1 | Write-Host
if ($LASTEXITCODE -ne 0) {
  throw "gh 未登录。请先在干净终端运行：`n  `$env:GITHUB_TOKEN=''; `$env:GH_TOKEN=''; gh auth login --hostname github.com --git-protocol https --web`n然后重跑本脚本。"
}
# TD63 item9：预检①之后到此处之间鉴权可能失效（token 过期/网络抖动）——不判 $LASTEXITCODE 直接 .Trim()
# 空/null 输出会抛裸的「找不到方法」错误（仿 _guard.ps1:62-65 的守卫写法，给出可操作提示而非裸异常）。
$owner = (& gh api user -q .login 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $owner) {
  throw "无法确认 GitHub 账号（gh api user 失败或返回空——可能在预检①登录检查之后到此处之间鉴权失效）。请重新 gh auth login 后重跑本脚本。"
}
$owner = "$owner".Trim()
Write-Host "认证账号: $owner"

# --- 预检 2：禁止把机密推上去（防泄露闸；敏感模式集单一真相源 = check-secrets.ps1）---
# TD62/TD-125：建 **PUBLIC** 仓（-Private:$false）会暴露**整个提交历史**——强制 `-Strict` 全历史扫描（fail-closed），
# 补上「变 public 前的历史盲点从来没有任何自动闸把守」这个缺口。建私有仓走非 -Strict 快扫（历史仍藏在私有里）。
# 局限：本脚本只在**建仓**这一刻把关；日后手动 `gh repo edit --visibility public` / 网页翻转 private→public
#       check-secrets 无从拦截——那条路必须人工先跑 `-Strict` 并全绿（docs\SECURITY.md §1.1 变 public 清单）。
Step "预检：防泄露闸（check-secrets.ps1$(if (-not $Private) { ' -Strict 全历史' }))"
$secArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'check-secrets.ps1'))
if (-not $Private) { $secArgs += '-Strict' }
& pwsh @secArgs
if ($LASTEXITCODE -ne 0) {
  $histNote = if (-not $Private) { '（-Strict 全历史扫描：含历史文件名/新增行——git rm --cached 不删历史，须 git filter-repo/BFG 清历史 blob + 轮换密钥）' } else { '（补救：git rm --cached）' }
  throw "检测到疑似机密被 git 追踪/藏于历史，已中止建仓$histNote。见上 check-secrets 输出。"
}

# --- 预检 3：工作树干净 ---
Step '预检：工作树状态'
$dirty = & git status --porcelain
if ($dirty) { Write-Warning "工作树有未提交改动，建议先提交：`n$dirty" }

# --- 创建仓库 + 推送 ---
Step "创建 $(if($Private){'私有'}else{'公开'})仓库 $owner/$RepoName 并推送 main"
$exists = $false
& gh repo view "$owner/$RepoName" 2>$null 1>$null
if ($LASTEXITCODE -eq 0) { $exists = $true; Write-Host '仓库已存在，跳过创建。' }

if (-not $exists) {
  if ($PSCmdlet.ShouldProcess("$owner/$RepoName", '创建并推送')) {
    $vis = if ($Private) { '--private' } else { '--public' }
    & gh repo create "$RepoName" $vis --source . --remote origin --push --disable-wiki
    if ($LASTEXITCODE -ne 0) { throw 'gh repo create 失败' }
  }
} else {
  $hasOrigin = (& git remote) -contains 'origin'
  if (-not $hasOrigin) {
    & git remote add origin "https://github.com/$owner/$RepoName.git"
    # TD63 item10：跑完不查退出码——失败会静默续跑到下面的 git push（大概率因缺 origin 远端而费解地失败）。
    if ($LASTEXITCODE -ne 0) { Write-Warning "git remote add origin 失败（exit $LASTEXITCODE）——后续 push/PR 可能因缺 origin 远端而失败，请手动核查（如远端已存在但指向别处）。" }
  }
  & gh auth setup-git 2>$null
  # L38：绝不硬编码 main——解析仓库当前分支（默认分支因仓而异：master/main），失败再回退 main。
  # 镜像 task.ps1:45-49 的当前分支自动探测；并加 $LASTEXITCODE 守卫（pwsh 默认非 native-error → 失败会静默续跑，掩盖 'src refspec main does not match any'）。
  $cur = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
  if (-not $cur) { $cur = 'main' }
  & git push -u origin $cur
  if ($LASTEXITCODE -ne 0) { throw "git push -u origin $cur 失败（exit $LASTEXITCODE）：确认分支存在且有提交、远端可写。" }
}

& gh auth setup-git 2>$null

# --- 仓库合并策略加固 ---
Step '仓库设置：仅 squash、合并后删分支、开启 auto-merge'
if ($PSCmdlet.ShouldProcess("$owner/$RepoName", '加固合并策略')) {
  & gh repo edit "$owner/$RepoName" `
      --enable-auto-merge `
      --enable-squash-merge `
      --enable-merge-commit=false `
      --enable-rebase-merge=false `
      --delete-branch-on-merge 2>&1 | Write-Host
  # TD63 item10：跑完不查退出码——「合并策略加固静默失败」此前不可见；task.ps1:290-292（squash 合并后不加
  # --delete-branch-on-merge、依赖仓库设置的 delete_branch_on_merge 自动删远端分支）正依赖这条设置生效。
  if ($LASTEXITCODE -ne 0) { Write-Warning "gh repo edit 加固合并策略失败（exit $LASTEXITCODE）——squash-only/合并后删分支等设置可能未生效，请到仓库 Settings 手动核查。" }
}

# --- 分支规则集：main 必须 PR + 必须通过 verify / R3 状态检查（默认 codex-review，随 ReviewStatusContext 可换） ---
Step 'main 分支规则集：必须 PR + 必需状态检查'
$checks = @($RequiredChecks | ForEach-Object { @{ context = $_ } })
$ruleset = @{
  name        = 'protect-main'
  target      = 'branch'
  enforcement = 'active'
  conditions  = @{ ref_name = @{ include = @('~DEFAULT_BRANCH'); exclude = @() } }
  rules       = @(
    @{ type = 'deletion' },
    @{ type = 'non_fast_forward' },
    @{ type       = 'pull_request'
       parameters = @{
         required_approving_review_count   = 0   # Codex 以状态检查代替人工审批
         dismiss_stale_reviews_on_push     = $true
         require_code_owner_review         = $false
         require_last_push_approval        = $false
         required_review_thread_resolution = $false
       } },
    @{ type       = 'required_status_checks'
       parameters = @{
         strict_required_status_checks_policy = $true
         required_status_checks               = $checks
       } }
  )
} | ConvertTo-Json -Depth 12

# 经验 L3：free+private 不支持服务端规则集（GitHub 返回 403 "Upgrade to Pro or make public"）。
# 先探测可用性：列 rulesets 成功(exit 0)才尝试写；403/失败则干净跳过，R3 强制改由客户端 review.ps1 保证。
$rsList = & gh api "repos/$owner/$RepoName/rulesets" 2>$null
$rsOk = ($LASTEXITCODE -eq 0)
if (-not $rsOk) {
  Write-Warning '规则集不可用：免费账户的私有仓不支持服务端分支保护（需 GitHub Pro，或将仓库设为 public）。'
  Write-Host  '  → 已跳过 main 规则集。R3「Codex 代人工」由本地 review.ps1 + task-loop skill 强制（verdict≠pass 即不 push）。' -ForegroundColor DarkGray
} else {
  # 安全取已存在 protect-main 的 id（数值校验，避免把异形/错误对象当 id）
  $existingId = $null
  try {
    $hit = ($rsList | ConvertFrom-Json) | Where-Object { $_.PSObject.Properties.Name -contains 'name' -and $_.name -eq 'protect-main' } | Select-Object -First 1
    if ($hit) { $existingId = [string]$hit.id }
  } catch { $existingId = $null }
  $tmp = Join-Path $RepoRoot ".secrets/ruleset-body.json"
  New-Item -ItemType Directory -Force (Split-Path $tmp) | Out-Null
  $ruleset | Set-Content $tmp -Encoding utf8
  try {
    if ($PSCmdlet.ShouldProcess("$owner/$RepoName", '应用 main 规则集')) {
      $newId = $existingId
      $applyOk = $false
      if ($existingId -match '^\d+$') {
        & gh api --method PUT "repos/$owner/$RepoName/rulesets/$existingId" --input $tmp 1>$null
        if ($LASTEXITCODE -eq 0) { Write-Host "已更新规则集 id=$existingId"; $applyOk = $true } else { Write-Warning "规则集更新失败（exit $LASTEXITCODE）。" }
      } else {
        $createResp = & gh api --method POST "repos/$owner/$RepoName/rulesets" --input $tmp 2>$null
        if ($LASTEXITCODE -eq 0) {
          try { $newId = [string](($createResp | ConvertFrom-Json).id) } catch { $newId = $null }
          Write-Host "已创建规则集 protect-main（PR 必需 + $($RequiredChecks -join '/') 必过）"
          $applyOk = $true
        } else { Write-Warning "规则集创建失败（exit $LASTEXITCODE）。可在仓库 Settings→Rules 手动确认。" }
      }
      # TD64/TD-127 item7：CLI 退出码只证明请求被接受，不证明配置真正落地（GitHub API 偶发最终一致性延迟/
      # 静默截断）——读回校验 enforcement 与必需检查是否均已生效，不一致仅告警（不 throw：读回本身可能因权限/
      # 延迟失败，不应让整个 bootstrap 因此中断）。
      if ($applyOk -and $newId -match '^\d+$') {
        $verify = & gh api "repos/$owner/$RepoName/rulesets/$newId" 2>$null
        if ($LASTEXITCODE -eq 0) {
          try {
            $vObj = $verify | ConvertFrom-Json
            $landedChecks = @($vObj.rules | Where-Object { $_.type -eq 'required_status_checks' } | ForEach-Object { $_.parameters.required_status_checks.context })
            $missingChecks = @($RequiredChecks | Where-Object { $_ -notin $landedChecks })
            if ($vObj.enforcement -ne 'active') {
              Write-Warning "规则集读回校验：enforcement=$($vObj.enforcement)（预期 active）——请到仓库 Settings→Rules 手动确认。"
            } elseif ($missingChecks.Count -gt 0) {
              Write-Warning "规则集读回校验：必需检查未全部落地，缺失 $($missingChecks -join ', ')——请到仓库 Settings→Rules 手动确认。"
            } else {
              Write-Host "  读回校验 OK：enforcement=active，必需检查 $($RequiredChecks -join '/') 均已落地" -ForegroundColor Green
            }
          } catch { Write-Warning '规则集读回校验：响应解析失败，无法确认落地状态。' }
        } else {
          Write-Warning "规则集读回校验：GET 失败（exit $LASTEXITCODE），无法确认落地状态。"
        }
      }
    }
  } finally {
    Remove-Item $tmp -ErrorAction SilentlyContinue
  }
}

# --- 装 pre-push 钩子：把账号守卫 + 防泄露闸从「脚本里调一下」升级为「git 层强制」 ---
# 治本：Assert-PersonalAccount / check-secrets 原只在 gh-bootstrap/review/ship 里被调用，裸 `git push` 完全绕过。
# 装一个 pre-push 钩子，任何 push（含 agent 直接跑的）都先过账号守卫、再过 check-secrets（非 -Strict）。绕过需显式 --no-verify。
# 安装逻辑（honoring core.hooksPath + 不静默覆盖既有钩子的链式）见上方 Install-PrePushHook（库导出区，selftest 17o 同源测）。
Step 'pre-push 钩子：git 层强制账号守卫 + 防泄露闸（任何 push 都先过两闸；honoring core.hooksPath + 链式既有钩子）'
Install-PrePushHook -RepoRoot $RepoRoot

Step '完成'
Write-Host "仓库: https://github.com/$owner/$RepoName  (private=$Private)"
Write-Host "必需检查: $($RequiredChecks -join ', ')"
Write-Host '下一步：用 scripts\task.ps1 走 worktree+TDD+Codex 评审闭环（见 docs\DEVOPS-WORKFLOW.md）。'
