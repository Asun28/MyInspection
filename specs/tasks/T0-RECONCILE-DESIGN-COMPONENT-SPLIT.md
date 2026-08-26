---
id: T0-RECONCILE-DESIGN-COMPONENT-SPLIT
title: 将超预算设计组件卡拆为基础与组件两个完整评审单元
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENT-SPLIT
worktree: C:\\wt\\T0-RECONCILE-DESIGN-COMPONENT-SPLIT
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENT-SPLIT.md
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、设计内容或源夹具
  - 提高 R3 预算或删减设计以绕过闸门
non_goals:
  - 执行基础卡或组件卡
diagnosis: 组件卡的 432 个 changed lines 合格，但完整 unified diff 为 63429 字符，超过 R3 的 60000 字符硬上限；根因是颜色/排版/布局基础与 81 组件矩阵被塞进同一评审单元。
acceptance:
  - "A1 新基础卡只落 Colors 到 Components 前的精确源区域"
  - "A2 原组件卡改为依赖基础卡，最终设计内容不变"
  - "A3 两卡均保留完整自验证 DoD，未放宽预算"
  - "A4 现有组件分支必须合入 Foundations 后的最新主线并通过 SizeOnly"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$sha=[Security.Cryptography.SHA256]::Create();function ExactHash($p,$e){$s=(Get-Content $p -Raw).Replace([string][char]13,'').TrimEnd();$a=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($a-cne$e){throw ('exact card '+$p)}};ExactHash 'specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md' '0c67782d8aa27b96134fd0311b429e83a1fa3be024bb7c43eb9fdeee53b00c53';ExactHash 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' '1a8f6224e8de7147d87d3629e1dba9f293ae0355ba95baefec59dbd1abc20baa';$f=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md' -Raw;$c=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' -Raw;if(-not$f.Contains('$foundation=Region $src ')-or-not$f.Contains("'Colors' 'Components'")-or-not$f.Contains('$prefix+$foundation+$suffix')-or-not$c.Contains('&git merge-base --is-ancestor $remote HEAD')-or-not$c.Contains('review.ps1 -WorktreePath $PWD.Path -SizeOnly')){throw 'split sentinel'}
dod_exit: 0
dod_assert: A1–A4 两张卡全文 hash + region/equality/ancestry/SizeOnly sentinels；任一删除变异即 RED
review_gate: codex {verdict:pass}
hygiene: 预算拆分以语义边界为单位
doc_sync: 无
---

# T0-RECONCILE-DESIGN-COMPONENT-SPLIT

只登记预算拆卡，不改变产品设计。
