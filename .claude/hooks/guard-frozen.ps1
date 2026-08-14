#requires -Version 7
<#
  PreToolUse 守卫：拒绝对**冻结物**（契约 / schema 等一等资产）的写入。
  冻结物清单来自 scripts\_config.ps1 的 FrozenPaths（仓库相对、正斜杠、正则片段）。
  FrozenPaths 为空 => 本守卫不拦截任何文件（项目尚无冻结点时的默认）。

  覆盖两条路径（matcher: Edit|Write|MultiEdit|Bash|PowerShell）：
    A) 编辑类工具：取 tool_input.file_path 按**仓库相对后缀**匹配。
    B) 命令类工具（Bash/PowerShell）：取 tool_input.command，若其含写动作
       （Set-Content/Out-File/Add-Content/New-Item/Tee-Object/重定向 >|>>/cp/mv/删除 Remove-Item·rm·del·ri/
       sed -i/perl -i/awk -i/git apply·checkout·restore/patch）且目标含冻结路径 → deny。
       堵住「绕过 Edit 工具、直接命令写文件」的漏洞。非写动词但**引用**冻结路径（如 python -c open(w)）→
       发非阻断 defer 警告（additionalContext 提示核验、不误伤只读）。
  - 命中写动词→deny JSON；仅引用→defer 警告 JSON；未命中→不输出。**任何情况都 exit 0**（fail-open，避免误阻断/刷错）。
  - 尽力而为、非穷尽（面向善意模型；见 TD49/TD-112）：`cd <frozen-dir> && write <basename>` 会拆散完整冻结片段而漏过；
    heredoc 写经 `>`/tee 已被重定向动词覆盖。确定的对手无法靠单一命令守卫穷尽拦截——这是纵深防御一层，非唯一闸。
  合法的版本升级：临时在 .claude\settings.json 注释掉该 matcher，走版本评审后再恢复（见 docs\DEVOPS-WORKFLOW.md §7）。
#>
try {
  # stdin 解码先定为 UTF-8 再首次访问 [Console]::In：Claude 传入的事件 JSON 是 UTF-8，默认 Windows OEM 代码页会误码——
  # 冻结 pattern/路径里的非 ASCII 会解码走样 → 下面匹配落空、静默漏过（镜像 route-new-work.ps1:14；C15/TD49）。
  try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}
  try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}
  $raw = [Console]::In.ReadToEnd()
  if (-not $raw) { exit 0 }
  $evt = $raw | ConvertFrom-Json

  # 取冻结清单（dot-source 配置；失败/为空则直接放行）
  $frozenPatterns = @()
  try {
    . (Join-Path $PSScriptRoot '../../scripts/_config.ps1')
    $frozenPatterns = @($ScaffoldConfig.FrozenPaths)
  } catch { $frozenPatterns = @() }
  if ($frozenPatterns.Count -eq 0) { exit 0 }   # 未配置冻结点 → 不拦截

  $names = if ($evt.tool_input) { $evt.tool_input.PSObject.Properties.Name } else { @() }
  $frozen = $false

  # --- 路径 A：编辑类工具的 file_path ---
  if ($names -contains 'file_path') {
    $norm = ([string]$evt.tool_input.file_path -replace '\\', '/').ToLowerInvariant()
    foreach ($p in $frozenPatterns) {
      if ($norm -match $p.ToLowerInvariant()) { $frozen = $true; break }
    }
  }

  # --- 路径 B：命令类工具（Bash/PowerShell）的 command ---
  # 写动词白名单（扩至 perl -i / awk -i / git apply·checkout·restore / patch；heredoc 写经 `>`/tee 已覆盖）+ 冻结路径共现 → deny。
  # 非写动词但**引用**冻结路径（如 `python -c open(w)`）→ 非阻断 defer 警告（不 deny 免误伤只读、不 allow 免自动放行）。
  $warnFrozen = $false
  if (-not $frozen -and ($names -contains 'command')) {
    $cmd = ([string]$evt.tool_input.command -replace '\\', '/')
    $cmdLower = $cmd.ToLowerInvariant()
    $writeVerb = $cmdLower -match 'set-content|out-file|add-content|new-item|tee-object|\btee\b|\bcp\b|copy-item|\bmv\b|move-item|remove-item|\brm\b|\bdel\b|\bri\b|sed\s+-i|perl\s+-i|awk\s+-i|git\s+apply|git\s+checkout|git\s+restore|\bpatch\b|>>|>'
    $hitsFrozen = $false
    foreach ($p in $frozenPatterns) {
      if ($cmdLower -match $p.ToLowerInvariant()) { $hitsFrozen = $true; break }
    }
    if ($hitsFrozen) {
      if ($writeVerb) { $frozen = $true } else { $warnFrozen = $true }
    }
  }

  if ($frozen) {
    $reason = 'FROZEN — provider 契约 / schema 为冻结一等资产（见 scripts/_config.ps1 FrozenPaths），演进须走版本评审，禁止就地编辑。请停下并询问用户如何处理；冻结资产的任何变更一律走版本评审，不要绕过本守卫。'
    $out = @{
      hookSpecificOutput = @{
        hookEventName            = 'PreToolUse'
        permissionDecision       = 'deny'
        permissionDecisionReason = $reason
      }
    } | ConvertTo-Json -Depth 6 -Compress
    Write-Output $out
  }
  elseif ($warnFrozen) {
    # 非阻断警告：命令引用了冻结路径但无可识别写动词（如 python -c open(w)）。不裁决（defer=交常规许可流决定），
    # 只经 additionalContext 提示模型核验「这不是一次写入」——不 deny（免误伤 cat/Get-Content 只读）、不 allow（免绕过许可）。
    $warn = @{
      hookSpecificOutput = @{
        hookEventName      = 'PreToolUse'
        permissionDecision = 'defer'
        additionalContext  = '注意：本命令引用了冻结物路径（见 scripts/_config.ps1 FrozenPaths）。若为写入/就地改，请停下走版本评审、勿绕过冻结守卫；若确为只读可继续。'
      }
    } | ConvertTo-Json -Depth 6 -Compress
    Write-Output $warn
  }
  exit 0
}
catch {
  # 守卫自身异常时保持沉默并放行（不打断正常编辑流）。用 Write-Error（受限语言模式 CLM 下安全）写诊断——
  # 原 [Console]::Error.WriteLine 在 CLM 下不可用、会在 catch 内**再抛**，反而让钩子整体出错（30-lens C19）。
  try { Write-Error "guard-frozen hook error: $($_.Exception.Message)" } catch {}
  exit 0
}
