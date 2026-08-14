#requires -Version 7
# 共享：范围闸（allow_paths 越界拦截）的**判定核**——卡 allow_paths 取值 / 改动清单求值 / 段级匹配器。
#
# 为什么是共享件（TD93 item①）：判定逻辑此前只活在 task.ps1 的内联 ship 块里。但「任何已 push 状态的手工恢复」
# （docs/DEVOPS-WORKFLOW.md）是**绕过 ship 主路**的最后手段平面，而 **CI 没有范围闸**（TD89 的根因）——那条恢复
# 序列里的范围核对，遂是该平面上范围闸的唯一承载，且原本只是散文（人眼比对 `git diff --name-only` 输出，
# **没有退出码**，漏看一行不会有任何信号）。把核抽到本文件后，独立入口 scripts/check-scope.ps1 与 ship 打的是
# **同一枚核**：恢复序列因此有了可跑命令，也不必写「等价的第二实现」——后者会重演 TD68（review.ps1 修了、
# task.ps1 没修，被 R3 抓出）。同 _gitbase.ps1 头注所述之理，在范围闸面的同款应用。
#
# 调用方职责（本库只判定、不处置）：allow 列表为空 / diff 求值失败 / 基线不可解析 —— **调用方一律 fail-closed**
# （不确定 ≠ 放行）。输入有效后的**匹配方向**才宽松（前缀 / glob、正斜杠归一，宁放不误拦），绝不误拦合法改动。
. (Join-Path $PSScriptRoot '_cards.ps1')   # Get-FrontMatter / Get-YamlBlockListItems（卡 front-matter 解析的单一实现）

# 卡**原文**的 allow_paths（反斜杠归一为正斜杠）。取不到即返回空数组，由调用方 fail-closed。
# 收**文本**而非路径：判定标准应来自受信基线，调用方常用 `git show <baseRef>:specs/tasks/<id>.md` 取原文
# （见 check-scope.ps1 的 [SCOPE-NOCARD] 一段——读被审检出里的卡，会让分支自行扩 allow_paths 绕过本闸）。
function Get-ScaffoldCardAllowPathFromText {
  param([string]$CardText)
  # 先用 check-cards 同款正则取 front-matter（防行走漏进正文、把正文列表项当路径），再行走收集 allow_paths。
  # 块式**专用**取值器（非 Get-YamlListItems）：行内 flow 写法解析为 0 项 → 调用方空列表分支 fail-closed 拦停。
  # check-cards 已在上游拒绝行内写法（闸 10d），这里是它失效时的后置防线；两个取值器的差异是有意的，见 _cards.ps1 注释。
  $fm = Get-FrontMatter $CardText
  if (-not $fm) { return @() }
  return @(Get-YamlBlockListItems $fm 'allow_paths' | ForEach-Object { $_ -replace '\\', '/' })
}

# 同上，取磁盘上那份卡（ship 侧用：L86 已强制相位命令在主检出跑，故 task.ps1 手上的卡本就是基线检出那份）。
function Get-ScaffoldCardAllowPath {
  param([Parameter(Mandatory)][string]$CardPath)
  return @(Get-ScaffoldCardAllowPathFromText -CardText (Get-Content -Raw $CardPath))
}

# 相对基线引用的改动清单（`<BaseRef>...<TipRef>`）。git 非零退出即 throw——调用方据此 fail-closed。
# TipRef 缺省 'HEAD'（ship 侧语义：$Wt 的 HEAD 就是本卡分支尖端，行为与抽核前逐字一致）；
# 独立检查器显式传 refs/heads/<TaskId>，好让判定对象由**卡 id** 锚定、不随 -Path 指到哪个检出而漂移。
function Get-ScaffoldChangedPath {
  param(
    [Parameter(Mandatory)][string]$GitDir,    # 在哪个 git 目录/工作树里求 diff
    [Parameter(Mandatory)][string]$BaseRef,   # 已解析的基线**引用**（全限定，见 _gitbase.ps1）
    [string]$TipRef = 'HEAD'                  # 被判定的尖端引用
  )
  # -c core.quotepath=false（TD54/TD-117）：否则 git 把非 ASCII 路径 C-quote 成 "docs/\346..." → 与 allow_paths
  # 的原样 UTF-8 条目匹配落空 → 合法的 CJK-名文件被判假越界 BLOCK（fail-closed 但真误拦）。
  # -c diff.renames=false（TD-202）：git 默认开启改名探测，把「删 A + 增 B（高相似）」折叠成单条 rename 记录、
  # 只印目标 B——被删的卡外 origin A 从此清单消失，范围闸看不到它离场（把仓内任意文件 relocate 进卡内目标目录即
  # 绕过越界拦截）。禁用改名探测令 origin 与 destination 各自作为独立路径重现、各受既有 allow_paths 检查；
  # 亦覆盖相似度改名（移动+编辑）。绝不去解析 --name-status 的 R100 记录（多一条代码路径与失败面）。
  $changed = @(& git -C $GitDir -c core.quotepath=false -c diff.renames=false diff --name-only "$BaseRef...$TipRef" 2>$null | Where-Object { $_ })
  if ($LASTEXITCODE -ne 0) { throw "git diff --name-only $BaseRef...$TipRef 非零退出（基线 '$BaseRef' 或尖端 '$TipRef' 在该仓不可解析？）" }
  return $changed
}

# 纯匹配器：返回 $ChangedPath 中不被任何 $AllowPath 条目覆盖的路径。无 git、无 IO、可单测。
# 注意：$AllowPath 为空时**全部**改动都判越界——「空 allow 即 fail-closed」的语义由调用方在调用前显式判定并给
# 专门的修法文案（本函数不区分「卡没写 allow_paths」与「真的全部越界」）。
function Get-ScaffoldOutOfScopePath {
  param(
    [string[]]$ChangedPath = @(),
    [string[]]$AllowPath = @()
  )
  # TD60/TD-123：段级前缀匹配，非字符前缀——旧写法 `$f -like ($_.TrimEnd('/')+'*')` 是纯字符串前缀，
  # allow `docs/`（trim 后 "docs*"）会让 "docs2/oob.md" 误判在范围内（字符前缀假阳性放行，反不该放行却放）；
  # allow `README.md`（"README.md*"）同理误放行 "README.md.bak"。改为「整段相等」或「以 `路径/` 开头」
  # 两种判据之一，杜绝同前缀不同路径段的误配。另外 allow 条目本身若含 `-like` 通配符特殊字符（如 `[`），
  # 直接把它拼进 `-like` 模式会被当字符类而非字面量、令这条**合法**的字面路径匹配失败（反向：本该放行却拦）——
  # 用 WildcardPattern.Escape 转义后再接 "/*"，使其在前缀分支里保持字面语义；末尾仍保留 `$f -like $_`
  # （原始、未转义）分支，让 allow 条目里刻意写的 glob（如 `frontend/**`）继续按通配符生效（不收窄既有能力）。
  return @($ChangedPath | Where-Object {
      $f = $_ -replace '\\', '/'
      -not ($AllowPath | Where-Object {
          $norm = $_.TrimEnd('/')
          $escNorm = [System.Management.Automation.WildcardPattern]::Escape($norm)
          $f -eq $norm -or $f -like "$escNorm/*" -or $f -like $_
        })
    })
}
