<#
.SYNOPSIS
  共享编码 / 原生错误前奏（dependency-free prelude）。入口脚本 dot-source 它，单源化两条纪律
  ——TD54/TD-117 把散落各处的点修（C13/C15/C40/TD31/TD34）收敛为一处，消除不对称覆盖。

.DESCRIPTION
  用法：入口脚本顶部 `. (Join-Path $PSScriptRoot '_encoding.ps1')`。**库脚本**（check-secrets /
  check-licenses / gh-bootstrap 的 -AsLibrary）须放在 `-AsLibrary` 早返回**之后**再 dot-source，
  免污染 selftest 库 dot-source 的调用方作用域。dot-source 在**调用方作用域**生效，故：

    1. [Console]::OutputEncoding = UTF-8（无 BOM）——治非 UTF-8 主机上中文 Write-Host / 门诊断 /
       钩子注入被 OEM 代码页 mojibake，及原生命令（git 等）UTF-8 输出的错误解码（C15/C40/TD31/L66）。
       try/catch 兜底无 attached console 的 CI。

    2. $PSNativeCommandUseErrorActionPreference = $false——顶层原生命令（git diff --quiet /
       gh pr view / 非 git 目录里的 git rev-parse 等**正常返回非零**）按退出码判流程、不当终止错抛（C13）。
       dot-source 在调用方脚本作用域赋值，故对「用户 profile / 环境把它设 $true」健壮——缺此 pin 时
       `$ErrorActionPreference='Stop'` + 敌意 $true 会让第一个预期非零的原生调用崩掉优雅降级路径。
       块内需 $true 的（如 task.ps1 的 DoD 包装器）仍可局部覆盖。

  **刻意不碰 [Console]::InputEncoding**（L4 / L69）：全局设 InputEncoding 会破坏嵌套 / 重定向 stdin
  （codex exec 继承的管道、review.ps1 的子评审者）。读 stdin 的 InputEncoding pin 保持**就地、就读端**
  ——各在自己进程内设、只影响该读端（guard-frozen / route-new-work 钩子；review.ps1 注入子脚本首行）。

  纯 infra、**不依赖 _config.ps1**，故 check-secrets / verify / check-cards 这些刻意
  「不依赖 _config、默认可干跑」的脚本也能安全 dot-source 它（不引入项目配置耦合）。
#>

try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }
$PSNativeCommandUseErrorActionPreference = $false
