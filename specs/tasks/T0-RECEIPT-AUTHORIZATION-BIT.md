---
id: T0-RECEIPT-AUTHORIZATION-BIT
title: 用本轮四谓词结果而非 catch 时文件存在性授权 receipt resume
depends_on: [T0-RECEIPT-LOSS-SPLIT-PLAN]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 fresh primary RED 证明 receipt 在 mint 后存在、在 catch 前已删除，旧代码只因重新探测文件而误走非 normal-ship 路由"
  - "A2 四谓词校验成功与本轮成功铸据各自置真同一授权位；catch 只消费该位，不再读取 receipt 文件"
  - "A3 删除 validation 赋值、删除 mint 赋值或恢复 catch presence probe 的任一变异都使具名测试失败"
  - "A4 p1/p2/p3/p4 各有独立反例：taskId 错、等式或 40hex 错、可解析非祖先 redSha、abbreviated 或 nonancestor commitSha"
  - "A5 workflow 终态 PASS，且 card-inclusive diff 低于 1000 行/60000 字符"
status: merged
branch: T0-RECEIPT-AUTHORIZATION-BIT
worktree: C:\wt\T0-RECEIPT-AUTHORIZATION-BIT
allow_paths:
  - specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md
  - scripts/task.ps1
  - scripts/selftest.ps1
forbid:
  - 在 saga catch 重新以 receipt 文件存在性授权 resume
  - 改动 receipt-loss 人工升级策略、远端 T37 恢复行为或两份运维文档
  - 使用 prototype commit 代替本卡 fresh RED-first 实现
non_goals:
  - missing/corrupt/inconsistent/store-unavailable 的最终人工升级合同
  - T37 pushed receipt-loss 行为与文档 source contract
diagnosis:
  root_cause: catch 在失败发生后重新探测可变 receipt 文件，而没有消费本轮已通过四谓词或已成功铸据的授权事实。
  same_class: 同时覆盖 validation 与 mint 两个授权来源、catch presence probe，以及 p1/p2/p3/p4 四个验证谓词。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard workflow
dod_exit: 0
dod_assert: 四谓词校验成功或本轮成功铸据各自设置唯一内存授权位；同一 ship 在后续范围闸删除刚铸 receipt 后，catch 仍只凭已建立的授权建议完整重跑 normal ship。删除任一授权赋值、恢复 catch 文件重探或放宽 p1/p2/p3/p4 时，具名行为或 mutation 必须失败。
review_gate: codex {verdict:pass}
hygiene: 在正式 TaskId 工作树重新执行 fresh RED；保留真实 receipt 删除前提、两处授权赋值删除变异，以及可解析非祖先、等值错配和 abbreviated OID 的独立证据。
doc_sync: 无；本卡只改变 ship 相内授权状态，运行时人工升级与文档由后继卡交付。
---

# T0-RECEIPT-AUTHORIZATION-BIT

## 轻量计划

1. 先在正式工作树复现 mint 后存在、catch 前删除 receipt 的真实行为 RED。
2. 增加单一内存授权位，由四谓词成功或本轮成功铸据置真，catch 只消费该位。
3. 用三类授权 mutation 与四个独立谓词反例证明测试可证伪。
4. 跑 workflow、范围、预算与 mandatory R3；不得复用 prototype 作为实现历史。

## 设计证据

探索性 prototype `4495fae8` 的投影为 57+/18-（75 changed lines）/ 17,120 normalized chars（未含卡），只证明该切分有充足预算；正式实现必须从 fresh RED 开始。

## 本轮证据

- 正式 RED：基线 `0f4e65474502d9c9ebda7f54b40843c9daae769a`，`-Phase red` 外层 exit 0、内部 DoD exit 1，唯一失败 `15r(e)B`；raw SHA256 `31D3357E9CB8BCDE27428DF1375192C1A45240CD6BACC427A8D2454265D6C5B5`，源文件 PRE=POST。
- 作者 GREEN：同一基线运行 workflow exit 0，`selftest(workflow): PASS`、`selftest: PASS`，仅预期 `15n/POST-INIT-NOT-APPLICABLE` skip；raw SHA256 `78D207D61AA303207FFEF357C288482E4EF8CFBC838CF31197B38536A9BA39F9`，task/selftest/card PRE=POST。
- 本卡 `status: merged` 是随实现 PR 交付的合并后状态投影，不预填未来 squash SHA；实际 ship、R3、CI、merge 与 cleanup 仍须由 Task Loop 终态证明。
