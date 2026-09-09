---
id: T0-SHIP-SAGA-REPORTER-PLAN
title: 登记 T26 receipt-loss reporter 前置与 FOUNDATION 串链
depends_on: [T0-RECEIPT-NORMAL-SHIP-HARNESS]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: merged
branch: T0-SHIP-SAGA-REPORTER-PLAN
worktree: C:\wt\T0-SHIP-SAGA-REPORTER-PLAN
allow_paths:
  - specs/tasks/T0-SHIP-SAGA-REPORTER-PLAN.md
  - specs/tasks/T0-SHIP-SAGA-REPORTER.md
  - specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md
  - specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md
  - docs/TASK-BOARD.md
acceptance:
  - "A1 本卡只登记 reporter 前置；own、reporter、H1 todo-to-merged R5 projection、FOUNDATION dependency 投影与 BOARD 共五条路径，其他合同、文档、运行时、selftest 与评审证据不变。"
  - "A2 reporter 独立交付 post-watershed 未授权 fallback 的状态/诊断/bare-throw 边界；保留 H1 的 authorized/options/Local/early-RED 行为，不把全 catch 或全 stdout 改成单一旁路。"
  - "A3 FOUNDATION 除 depends_on 从 H1 改为 reporter 外逐字节保真，A1–A7、forbid、hygiene、doc_sync、原 DoD 与 B 合同均不被弱化。"
  - "A4 BOARD 既有 AUTH/H1/B/SOURCE/ASCII 行逐字保留，F 行只把依赖列改为 reporter；插入作者 GPT-6 Astra · high、review GPT-5.6 Sol · high 的 reporter 行，并把唯一 receipt graph 改为 H1→reporter→FOUNDATION→B。"
  - "A5 本登记是 metadata-only SkipRed 的 merged projection，不是 reporter、FOUNDATION 或 B 能力完成证据；此前 H1→F 七路径 63764 字符测量只作预算背景，不降低任何阈值。"
  - "A6 最终 DoD 必须从实际 checkout 计算 reporter/F/BOARD 完整 LF hash、自身五路径与身份字段，并对卡、行、顺序、graph 的真实文件变异 fail closed。"
forbid:
  - "把 ignored draft、未来 marker、历史日志或 metadata merge 投影冒充 reporter/FOUNDATION/B 的 RED/GREEN、verify、R3 或 ship。"
  - "修改 runtime、selftest、DEVOPS、DELIVERY、CLAUDE、LEDGER、F/B 其他字段、预算阈值或历史证据。"
non_goals:
  - "实现 reporter、FOUNDATION、B、SOURCE-CONTRACT 或 ASCII capability。"
  - "启动后继功能卡的 phase/full shard、正式 R3、push、merge 或 cleanup；本登记卡仍须完成自身正常交付流程。"
dod_command: $ErrorActionPreference='Stop'; pwsh -NoProfile -File scripts/check-cards.ps1; if($LASTEXITCODE -ne 0){exit 1}; . ./scripts/_cards.ps1; function Get-RegistrationFileHash([string]$Text){$s=$Text.Replace(([string][char]13+[string][char]10),[string][char]10).Replace([string][char]13,[string][char]10);[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(([Text.Encoding]::UTF8).GetBytes($s)))}; $hText=Get-Content -Raw 'specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md'; if((Get-RegistrationFileHash $hText) -cne 'F5E1BAC5FD912188E57FFCF87C8513431EFA5E52766F6DE9B0D8B8A6A920BF2A'){throw 'H1 full merged card changed'}; $fText=Get-Content -Raw 'specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md'; if((Get-RegistrationFileHash $fText) -cne '817EDC6DD789435460701CF2F23F51350663EEAC2D5AF55D0CBAF05B531D5DCA'){throw 'FOUNDATION projection changed'}; $rText=Get-Content -Raw 'specs/tasks/T0-SHIP-SAGA-REPORTER.md'; if((Get-RegistrationFileHash $rText) -cne '24646D94370E616F5DDFBB7B7DB969E77C7704B76831FBB6A77916E04D7F29B3'){throw 'Reporter card changed'}; $pm=Get-FrontMatter (Get-Content -Raw 'specs/tasks/T0-SHIP-SAGA-REPORTER-PLAN.md'); if((Get-Scalar $pm 'id') -cne 'T0-SHIP-SAGA-REPORTER-PLAN' -or (Get-Scalar $pm 'status') -cne 'merged' -or (Get-Scalar $pm 'branch') -cne 'T0-SHIP-SAGA-REPORTER-PLAN' -or (Get-Scalar $pm 'worktree') -cne 'C:\wt\T0-SHIP-SAGA-REPORTER-PLAN' -or ((Get-YamlListItems $pm 'depends_on') -join '|') -cne 'T0-RECEIPT-NORMAL-SHIP-HARNESS' -or ((Get-YamlListItems $pm 'allow_paths') -join '|') -cne 'specs/tasks/T0-SHIP-SAGA-REPORTER-PLAN.md|specs/tasks/T0-SHIP-SAGA-REPORTER.md|specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md|specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md|docs/TASK-BOARD.md'){throw 'Registration identity or scope changed'}; $lines=@(Get-Content 'docs/TASK-BOARD.md'); $expected=@('| W0 | T0-RECEIPT-AUTHORIZATION-BIT | 以本轮四谓词/铸据结果授权 catch resume | T0-RECEIPT-LOSS-SPLIT-PLAN | S | GPT-5.6 Sol · max | GPT-5.6 Luna · max | fresh RED-first；prototype 只读，不作实现历史 |','| W0 | T0-RECEIPT-NORMAL-SHIP-HARNESS | 用真实 normal ship 验证既有收据恢复边界 | T0-RECEIPT-AUTHORIZATION-BIT | S | GPT-6 Astra · high | GPT-5.6 Sol · high | 非 TDD 验证重构；替换测试自造配方，不宣称手工配方等价覆盖；生产与文档不变 |','| W0 | T0-SHIP-SAGA-REPORTER | T26 receipt-loss failure reporter / unauthorized fallback boundary | T0-RECEIPT-NORMAL-SHIP-HARNESS | S | GPT-6 Astra · high | GPT-5.6 Sol · high | post-watershed 未授权 fallback；独立 reporter 能力，不宣称 FOUNDATION/B fail-closed |','| W0 | T0-RECEIPT-LOSS-FOUNDATION | 收据失效单一路径基线与旧恢复旁路退役 | T0-SHIP-SAGA-REPORTER | M | GPT-6 Astra · high | GPT-5.6 Sol · high | 独立 RED/GREEN；复用真实 missing/valid 夹具；完整四态/reset-safe 留后继 B |','| W0 | T0-RECEIPT-LOSS-FAIL-CLOSED | 已发布 receipt 四类失效态单一路径 fail-closed（TD134 1c/6） | T0-RECEIPT-LOSS-FOUNDATION | M | GPT-5.6 Luna · max | GPT-5.6 Terra · max | 保留原卡全部运行时/T37/doc/reset-safe 责任；只把源码 mutation 后移 C |','| W0 | T0-RECEIPT-LOSS-SOURCE-CONTRACT | receipt-loss 源码合同、enum/discovery 与 mutation 防回归 | T0-RECEIPT-LOSS-FAIL-CLOSED | S | GPT-5.6 Sol · max | GPT-5.6 Terra · max | 只改 selftest + 自身卡；内部预算 300 行/35000 字符 |','| W0 | T0-ASCII-SHIP-CODES | ship saga/CI gate 的机器断言改锚 ASCII code（TD134 4/6） | T0-RECEIPT-LOSS-SOURCE-CONTRACT | M | GPT-5.6 Terra · high | DeepSeek V4 Pro | 只改观测面，不改控制流 |'); $last=-1; foreach($row in $expected){$id=$row.Split('|')[2].Trim();$hits=@($lines|Where-Object{$_ -ceq $row});if($hits.Count -ne 1){throw ('BOARD row changed: '+$id)};$pos=[Array]::IndexOf($lines,$row);if($pos -le $last){throw 'BOARD order changed'};$last=$pos}; $boardText=(Get-Content -Raw 'docs/TASK-BOARD.md').Replace(([string][char]13+[string][char]10),[string][char]10).Replace([string][char]13,[string][char]10); $fence=[string][char]96;$pattern='(?ms)^'+$fence+$fence+$fence+'mermaid'+[string][char]10+'.*?^'+$fence+$fence+$fence+'[ \t]*(?:'+[string][char]10+'|$)';$graphs=@([regex]::Matches($boardText,$pattern)|Where-Object{$_.Value.Contains('RA[T0-RECEIPT-AUTHORIZATION-BIT]')});if($graphs.Count -ne 1){throw 'Receipt graph missing or duplicated'};if((Get-RegistrationFileHash $graphs[0].Value) -cne '89E9370D3706996DF984F895412C80A0CDB80CCE80D48C9DB88853578D3726B3'){throw 'Receipt graph changed'};if((Get-RegistrationFileHash $boardText) -cne 'E81731B7F56FAC4F766CB3C62498D005FBB4EC2744512BED4619D22B61CACF82'){throw 'BOARD projection changed'};Write-Output '[SHIP-SAGA-REPORTER-REGISTRATION] metadata predicates PASS'
dod_exit: 0
dod_assert: "own id/status/branch/worktree/dependency/allow_paths、reporter/F 完整 LF hash、五条 allow_paths、六条既有+一条 reporter BOARD 行、唯一 receipt graph 与全文件 hash 均按实际输入匹配；任一删除/重复/重排/旁路变异必须非零。"
review_gate: codex {verdict:pass}
hygiene: "metadata-only SkipRed；使用实际 base commit、完整卡文本和 BOARD/graph 文件；保留原始输入、变异 raw 与退出码。不得依赖 ignored 路径或未来能力日志。"
doc_sync: "只登记 reporter 依赖边与 BOARD/graph；reporter 的运行时行为和 F/B 长期合同由各自卡交付。"
---

# T0-SHIP-SAGA-REPORTER-PLAN

本卡把 T26 reporter 作为 FOUNDATION 的串行前置。reporter 只收口 post-watershed 未授权 fallback 的失败报告边界；H1 的 authorized/options/Local/early-RED 行为、F 与 B 的既有合同保持独立。卡片 status=merged 是本 metadata-only 登记投影，不等于 reporter、FOUNDATION 或 B 已完成。

本卡 own scope 为五路径。旧 H1→F 七路径的 63764 字符实际测量解释了拆分背景，但不改变 card-inclusive 预算或任何完成标准。最终 renderer 必须把实际 reporter 内容、FOUNDATION dependency-only projection、BOARD 精确行/顺序/唯一 Mermaid graph 和完整 LF hash 固化到自包含 DoD；Sol 冻结 reporter marker 前不得运行本 renderer。