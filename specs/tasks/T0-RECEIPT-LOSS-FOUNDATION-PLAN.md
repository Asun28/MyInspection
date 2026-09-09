---
id: T0-RECEIPT-LOSS-FOUNDATION-PLAN
title: 登记真实恢复测试与 receipt-loss 运行时前置链
depends_on: [T0-RECEIPT-AUTHORIZATION-BIT]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: merged
branch: T0-RECEIPT-LOSS-FOUNDATION-PLAN
worktree: C:\wt\T0-RECEIPT-LOSS-FOUNDATION-PLAN
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-FOUNDATION-PLAN.md
  - specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md
  - specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md
  - specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md
  - docs/TASK-BOARD.md
acceptance:
  - "A1 本卡自己的五路径范围随 PR 发布，登记 H1 两路径、FOUNDATION 七路径及 AUTH→H1→FOUNDATION→B→SOURCE-CONTRACT→ASCII-SHIP 依赖链。"
  - "A2 B 保留原 id、A1–A7、完整 DoD/dod_assert、五路径、forbid、hygiene 与 doc_sync；相对 02c7 原卡仅替换 depends_on。"
  - "A3 DoD 在干净检出中独立复现，绑定 H1/FOUNDATION/B 的完整 LF 文本摘要、本卡五路径、六条 BOARD 行与完整 receipt 依赖图；外部临时文件不参与验收。"
  - "A4 H1/FOUNDATION 仍为 todo；本卡 merged 只投影本 PR 合并后的登记状态，不宣称运行时代码或测试已交付，不重绑任何历史 RED。"
forbid:
  - 修改运行时、selftest、操作文档、CLAUDE、LEDGER、评审计数或预算阈值
  - 放宽 B 原验收、用历史或预览结果冒充 FOUNDATION 验收
non_goals:
  - 实现 H1/FOUNDATION/B/C 或 ASCII 功能
  - 新增评审重置授权或更改既有评审闸门
diagnosis:
  root_cause: 原 B 的完整行为差异超过评审字符上限，单纯按源码与测试分开会留下不可验收的中间态。
  same_class: 同时检查登记卡自身范围、前置卡完整合同、B 保真及 BOARD 依赖链；不修改其他未完成卡。
dod_command: $ErrorActionPreference='Stop'; pwsh -NoProfile -File scripts/check-cards.ps1; if($LASTEXITCODE -ne 0){exit 1}; . ./scripts/_cards.ps1; function Get-RegistrationFileHash([string]$p){$s=[IO.File]::ReadAllText((Join-Path (Get-Location).Path $p)).Replace("`r`n","`n").Replace("`r","`n");[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($s)))}; if((Get-RegistrationFileHash 'specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md') -cne 'FBEF657702ED9D9F535976C895298E88B9DDB02A7668D449F55659C076D15DA9' -or (Get-RegistrationFileHash 'specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md') -cne 'D62BED74726AACCF534C535E4AEC0E31A10992F74A508C8E355F90D3851AAD13' -or (Get-RegistrationFileHash 'specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md') -cne 'E342B16E6207FA583B7DCF4BAE5E086F64BCC88762EC04EC07A0FA5B0FFAA320'){throw 'Registered card content changed'}; $pm=Get-FrontMatter (Get-Content -Raw 'specs/tasks/T0-RECEIPT-LOSS-FOUNDATION-PLAN.md'); if(((Get-YamlListItems $pm 'allow_paths') -join '|') -cne 'specs/tasks/T0-RECEIPT-LOSS-FOUNDATION-PLAN.md|specs/tasks/T0-RECEIPT-NORMAL-SHIP-HARNESS.md|specs/tasks/T0-RECEIPT-LOSS-FOUNDATION.md|specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md|docs/TASK-BOARD.md'){throw 'Registration scope changed'}; if((Get-Scalar $pm 'id') -cne 'T0-RECEIPT-LOSS-FOUNDATION-PLAN' -or (Get-Scalar $pm 'status') -cne 'merged' -or ((Get-YamlListItems $pm 'depends_on') -join '|') -cne 'T0-RECEIPT-AUTHORIZATION-BIT'){throw 'Registration identity/status/dependency changed'}; $expected=@('| W0 | T0-RECEIPT-AUTHORIZATION-BIT | 以本轮四谓词/铸据结果授权 catch resume | T0-RECEIPT-LOSS-SPLIT-PLAN | S | GPT-5.6 Sol · max | GPT-5.6 Luna · max | fresh RED-first；prototype 只读，不作实现历史 |','| W0 | T0-RECEIPT-NORMAL-SHIP-HARNESS | 用真实 normal ship 验证既有收据恢复边界 | T0-RECEIPT-AUTHORIZATION-BIT | S | GPT-6 Astra · high | GPT-5.6 Sol · high | 非 TDD 验证重构；替换测试自造配方，不宣称手工配方等价覆盖；生产与文档不变 |','| W0 | T0-RECEIPT-LOSS-FOUNDATION | 收据失效单一路径基线与旧恢复旁路退役 | T0-RECEIPT-NORMAL-SHIP-HARNESS | M | GPT-6 Astra · high | GPT-5.6 Sol · high | 独立 RED/GREEN；复用真实 missing/valid 夹具；完整四态/reset-safe 留后继 B |','| W0 | T0-RECEIPT-LOSS-FAIL-CLOSED | 已发布 receipt 四类失效态单一路径 fail-closed（TD134 1c/6） | T0-RECEIPT-LOSS-FOUNDATION | M | GPT-5.6 Luna · max | GPT-5.6 Terra · max | 保留原卡全部运行时/T37/doc/reset-safe 责任；只把源码 mutation 后移 C |','| W0 | T0-RECEIPT-LOSS-SOURCE-CONTRACT | receipt-loss 源码合同、enum/discovery 与 mutation 防回归 | T0-RECEIPT-LOSS-FAIL-CLOSED | S | GPT-5.6 Sol · max | GPT-5.6 Terra · max | 只改 selftest + 自身卡；内部预算 300 行/35000 字符 |','| W0 | T0-ASCII-SHIP-CODES | ship saga/CI gate 的机器断言改锚 ASCII code（TD134 4/6） | T0-RECEIPT-LOSS-SOURCE-CONTRACT | M | GPT-5.6 Terra · high | DeepSeek V4 Pro | 只改观测面，不改控制流 |'); $lines=@(Get-Content 'docs/TASK-BOARD.md'); $last=-1; foreach($row in $expected){$id=$row.Split('|')[2].Trim(); $hits=@($lines | Where-Object {$_ -match ('^\|\s*[^|]+\|\s*'+[regex]::Escape($id)+'\s*\|')}); if($hits.Count -ne 1 -or $hits[0] -cne $row){throw ('Board row changed: '+$id)}; $pos=[Array]::IndexOf($lines,$row); if($pos -le $last){throw 'Board chain order changed'}; $last=$pos}; $boardText=[IO.File]::ReadAllText('docs/TASK-BOARD.md').Replace("`r`n","`n").Replace("`r","`n"); $graphs=@([regex]::Matches($boardText,'(?ms)^```mermaid\n.*?^```[ \t]*(?:\n|$)') | Where-Object {$_.Value.Contains('RA[T0-RECEIPT-AUTHORIZATION-BIT]')}); if($graphs.Count -ne 1){throw 'Receipt dependency graph missing or duplicated'}; $graphHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($graphs[0].Value))); if($graphHash -cne 'FDEEC8B567906A7FB369F533FB7034855F27281893D150B8C49BEEE21A8051B3'){throw 'Receipt dependency graph changed'}; Write-Output '[RECEIPT-FOUNDATION-REGISTRATION] metadata predicates PASS'
dod_exit: 0
dod_assert: H1/FOUNDATION/B 的全部 LF 文本、own identity/status/dependency/allow_paths、六条 BOARD 行与完整 receipt 依赖图必须匹配核定登记结果；失配非零退出。
review_gate: codex {verdict:pass}
hygiene: 发布前在独立临时投影对实际文件做定点变更，确认同一验收谓词拒绝卡合同与依赖行失配；记录真实结果，不在每次 DoD 中改写工作树。
doc_sync: 本 PR 交付登记卡合并后状态投影和 BOARD 依赖边；实际 PR/CI/R3/T24/cleanup 证据在真实合并后记录，不另造递归 R5 卡。
---

# T0-RECEIPT-LOSS-FOUNDATION-PLAN

本卡只登记已拆明的实现边界。H1 先用真实 normal ship 验证既有 TD85 拒绝和有效 receipt 恢复，替换 selftest 自造的手工配方模型，不宣称两者整链覆盖等价；生产与操作文档不变，作为非 TDD 验证重构完整过闸。FOUNDATION 再以真实 RED/GREEN 引入 T35 失效停止、禁旧建议和权威合同同步；B 保留全部四态、发布标记、严格 reset-safe 与远端拓扑验收；C 保留后继源码合同。

验收摘要来自核定的完整卡片文本（统一 LF，保留其他所有字符），B 的原文基线固定为 `02c7b94e1dd0da56bb940b131dcec53761ff669b`。登记不等于这些功能已完成。
