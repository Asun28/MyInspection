#requires -Version 7
<#
  UserPromptSubmit hook：显式启动闸。
  当用户 prompt 含启动触发语「根据脚手架」(及近义:按照/按/依据脚手架;含英文 per/using/based on the scaffold)时，
  注入高优先级路由指令——**先与用户定规模档位(T0/T1/T2)，再按档位走对应深度**，而非默认一律上全链
  (治「小活被全套流程拖慢」，见 LEDGER L37/L46)；产品级新项目(T2)才在元仓内先 init 下游项目。
  治根因:漏斗入口此前只是 CLAUDE.md 软指针，模型可忽略(L 见 LEDGER)。本钩子即该经验的 enforced_by。
  纯注入(stdout)、exit 0、绝不阻断；无触发语则静默。权威:docs/IDEA-TO-PLAN.md。
#>
try {
  # 先把 stdin 解码定为 UTF-8 再首次访问 [Console]::In：Claude 经 stdin 传入的 prompt 是 UTF-8，
  # 默认 Windows OEM 代码页下 [Console]::In 按错误代码页解码 → 中文「根据脚手架」乱码 → 下面正则永不命中、
  # 入口闸**静默失效**（30-lens C15）。redirected/失败时 try 吞掉、退回原行为（无回归）。
  try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}
  try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}
  $raw = [Console]::In.ReadToEnd()
  if (-not $raw) { exit 0 }
  # 触发语：中文「根据/按照/按/依据 脚手架」+ 英文「per/using/follow(ing)/based on/according to the scaffold」。
  # -notmatch 默认大小写不敏感，覆盖英文 prompt（修「触发语 Chinese-only→英文 prompt 永不命中入口闸」）。
  if ($raw -notmatch '(根据|按照|按|依据)\s*脚手架|\b(?:per|follow(?:ing)?|based\s+on|according\s+to)\s+the\s+scaffold\b') { exit 0 }
  # 注入实际档位（TD19）：dot-source 仓内 _config（$PSScriptRoot 相对定位：钩子在 .claude/hooks/，仓根上两级）
  # 调 Get-ScaffoldProjectTier，把「默认档」从「让模型去查 _config」变「直接告知实际值」。
  # 任何异常（config 缺失/损坏）回退原文案——钩子契约不变：纯 stdout 注入、exit 0、绝不阻断。
  $tierLine = '默认档取 scripts/_config.ps1 的 ProjectTier（软提示）；用户说了算，问一句即可。'
  try {
    . (Join-Path $PSScriptRoot '../../scripts/_config.ps1')
    $tier = Get-ScaffoldProjectTier
    if ($tier) { $tierLine = "当前默认档 ProjectTier=$tier（软提示，取自 scripts/_config.ps1）；用户说了算，问一句即可。" }
  } catch {}
  # 注意：下方是**双引号** here-string（为插值 $tierLine）；新增文案若含 $ 或 ` 须转义。
  $msg = @"
[route-new-work] 命中启动触发语「根据脚手架」—— 这是脚手架的「新活」启动闸。**先和用户定规模档位（altitude），再按档位走对应深度**，不要默认一律上全链（治「小活被全套流程拖慢」，见 LEDGER L37/L46）：
  ⛳ 定档规则（消解首尾矛盾，只此一句）：档位含糊才问；能从上下文明确判断档位时，声明假定档位并直接按它继续，不必先停下等答案。
    · T0 极简（脚本/玩具/一次性/单文件改）→ **跳过漏斗**，直接写一张任务卡 → task-loop R1–R5。最轻。
    · T1 标准（多数功能性改动）→ 轻量：必要时 shape-idea 理清需求 → 直接填计划要点 → 投影卡 → task-loop。可跳 scout-options / grill-design。
    · T2 完整（新产品/大特性/团队/合规）→ 全链（下方阶段 A–D）。
  $tierLine
  —— T2 全链（仅 T2 / 用户要求时走全套）——
  阶段 A–D（立项→想法漏斗→开工→收口）全链演练与检查点边界，权威见
  docs/IDEA-TO-PLAN.md 按规模档位表 · docs/references/claude-fable-5-prompting-llms.txt 检查点条款。
"@
  [Console]::Out.WriteLine($msg)
}
catch { }
exit 0
