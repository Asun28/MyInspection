---
id: T0-R3-GATE-OUTCOME-WORDING
title: 区分 R3 reviewer verdict 与流水线闸门结果的通用叙述
depends_on: [T0-SHIP-SAGA-REPORTER]
status: merged
branch: T0-R3-GATE-OUTCOME-WORDING
worktree: C:\wt\T0-R3-GATE-OUTCOME-WORDING
allow_paths:
  - specs/tasks/T0-R3-GATE-OUTCOME-WORDING.md
  - scripts/task.ps1
  - docs/DEVOPS-WORKFLOW.md
  - specs/README.md
acceptance:
  - "A1 当前 blocking 实现下，review.ps1 的非零退出继续阻断；只有有效 pass 才满足该策略，本卡不授权 advisory"
  - "A2 task 的头注、Local 腿完成注释及 R3 Step 文案使用闸门结果；所有既有执行条件、调用参数与恢复逻辑原样保留"
  - "A3 DEVOPS 与 specs 生命周期按退出码描述 R3 闸门，并保留实际 PR、CI、base/head 验收顺序"
  - "A4 自包含 DoD 从指定基线重建三个文件的精确文本差异，钉住每个唯一替换和本卡完整契约；Reporter 已退役段不重复修改"
forbid:
  - 改动 reviewer verdict、退出码、策略、预算、轮次或任何闸门判断
  - 宣称 LOW-RISK advisory 或 Reporter 尚未实际发生的交付结果
  - 改写 Reporter 已负责的 fallback 或 selftest 恢复 anchor
non_goals:
  - advisory GitHub 发布夹具、LOW-RISK R3 修复与正式重试
  - 新建 runner、注册卡或 R5-of-R5
dod_command: $ErrorActionPreference='Stop';$base='5e5004e730e4a45c2ff20001773e3b27ec55741d';$changes='{"scripts/task.ps1":[["评审(必 pass)","评审闸门通过"],["pass 或显式跳过均算该腿完成","闸门通过或显式跳过均算该腿完成"],["block 即停、不合并","闸门失败即停、不合并"]],"docs/DEVOPS-WORKFLOW.md":[["verdict≠pass 即不合并","review.ps1 非零即不合并；blocking 策略须有效 pass"],["R3 Codex pass →","R3 Codex 闸门通过 →"]],"specs/README.md":[["R2 DoD绿 → 许可闸 → R3 Codex pass → PR → 合并","DoD/verify → 范围/许可/密钥/预算 → PR → R3 闸门通过 → CI/base/head → 合并"]]}'|ConvertFrom-Json -AsHashtable;foreach($path in $changes.Keys){$before=((& git show "${base}:$path"|Out-String)-replace "`r`n","`n").TrimStart([char]0xFEFF);if($LASTEXITCODE-ne 0){throw 'Baseline unreadable'};foreach($pair in $changes[$path]){if([regex]::Matches($before,[regex]::Escape($pair[0])).Count-ne 1){throw "Nonunique wording: $path"};$before=$before.Replace($pair[0],$pair[1])};$after=((Get-Content $path -Raw)-replace "`r`n","`n").TrimStart([char]0xFEFF);if($after-cne $before){throw "Unexpected text or execution change: $path"}};$card=((Get-Content 'specs/tasks/T0-R3-GATE-OUTCOME-WORDING.md' -Raw)-replace "`r`n","`n").TrimStart([char]0xFEFF);if([regex]::Matches($card,'(?m)^dod_command:').Count-ne 1){throw 'DoD count'};$card=$card-replace '(?m)^dod_command:.*\n','';if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($card)))-cne '223C153F77A70E4064F027FFD12907B062CF48E8DB41389FCEE7D6984D1D4677'){throw 'Card contract changed'};Write-Output '[R3-GATE-WORDING] exact wording projection PASS'
dod_exit: 0
dod_assert: 三文件恰为基线唯一替换后的文本、其它判定与调用字节不变，本卡除自引用 dod_command 外的完整 LF 内容匹配，并输出一次 [R3-GATE-WORDING] exact wording projection PASS。
review_gate: codex {verdict:pass}
hygiene: metadata-only SkipRed；逐个回退三个文件及修改卡字段的隔离反例必须失败。正式交付仍执行 verify、R3 和精确候选 CI。
doc_sync: 本卡内完成两份操作文档与 task 注释/label 同步；自身 merged 是合并后状态投影，实际成功后官方 cleanup，不再创建管理卡的管理卡。
---

# R3 闸门结果叙述

这是独立的 metadata-only 词义修正。当前 blocking 模式仍要求有效 pass；`review.ps1` 的退出码决定流水线是否继续，reviewer verdict 表达评审意见。未实现的 advisory 不因本卡获得授权。

依赖 Reporter 的实际交付：它已负责旧 fallback 配方和对应 selftest anchor；本卡仅修改剩余三个文件中的六处文字，不重新承接该范围。LOW-RISK 之后继承本卡，再完成自身发布验证和正式评审。

status=merged 仅为本 metadata PR 的合并后投影，不是提前声称 R3、CI 或 merge 已成功。DoD 基线为 Reporter 实际合并 5e5004e730e4a45c2ff20001773e3b27ec55741d；预览验证不能代替本卡正式交付。
