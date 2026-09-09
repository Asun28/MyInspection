# DevOps 工作流 · worktree + TDD + Codex-PR 闸门 + 测试卫生 + 文档同步

> EN: The authoritative operating manual for the R1–R5 single-card loop — per-card git worktree (R1), RED-first TDD (R2), second-model PR review (R3, advisory by default since T68), test pruning (R4), doc sync (R5), then a closing lessons-capture retrospective (R5.5) — driven by `scripts/task.ps1` and the task-loop skill. Remote `ship` runs DoD → verify → commit, refreshes the tracked base before the baseline-dependent scope gate, then scope `allow_paths` → license → secret-leak → push/PR-base validation → (R3 - advisory, except a Tier-S spec block; blocking under `ReviewGate='required'` - T242/T277) → CI check gate → pre-merge base revalidation → squash merge. Every merge gate is deterministic and idempotent, so re-running the same `ship` command is always a safe resume.

> 本文件是工作流的唯一操作手册。它把 5 条要求（R1–R5）落到 Windows/PowerShell 原生、
> 零新增运行时依赖的闭环上。核心理念：**计划/任务卡 own 规划/冻结/验收，脚手架只补 git+TDD+评审接线**，
> 故用「已装原语的 DIY 接线」而非再叠一个规划框架。

## 0. 选型结论（一句话）

| 组件 | 裁决 | 用途 |
|---|---|---|
| **git worktree + Codex 评审 + task.ps1（本仓 scripts/）** | **采用** | R1+R2+R3+R4+R5 的权威接线，Windows 原生 |
| **codex CLI/插件** | **采用** | R3 权威评审者，产出 `{verdict:pass\|block}` |
| superpowers / mattpocock/skills | 可选（仅取技法文本） | 可挑 TDD / worktree 技法；其脚本体多为 bash，需 Git Bash |
| spec-kit / OpenSpec / GSD / ECC / BMAD / claude-flow / Taskmaster | **默认跳过**（但建新项目时可按需评估，见下） | 整框架照搬会与计划/任务卡重复、制造第二真相源；或在 PS7 上有已知问题 |

> **建新项目时的 referral**：`specs/README.md`「建新项目时」一节列了 spec-kit / OpenSpec 的**适用信号**与**引入边界**。
> 默认形态够用；只有当某新项目需求确实需要更重的独立 spec 纪律时，才**评估**这两套、取契合需求的**局部做法**作可选叠加层，
> 始终保持「计划=唯一真相源、卡/spec=薄投影」不变量。结论记一条 lesson。

安全/合规：以上全是**开发期工具**（MIT/Apache），永不链接进产品，也不放宽产品的运行时边界。

## 1. 五要求映射（R1–R5）

| 要求 | 落地 |
|---|---|
| **R1 worktree** | 每张卡一个 `<WorktreeRoot>\<TaskId>` worktree + 同名分支；`task.ps1 -Phase start/cleanup` 管理（WorktreeRoot 见 scripts/_config.ps1）。**`-TaskId` 在参数绑定期即校验，判据取自 `_cards.ps1` 的 `card-id` 规则行（与 check-cards 同源、**大小写敏感**）**：`task.ps1` 与 `check-scope.ps1` 都不再各持一份正则副本，收窄规则行会在同一次改动里收窄这两个入口（T229/TD231；派生本身由 selftest 15v `[CARD-ID-DERIVED]` 机检）。四个相（start/red/ship/cleanup）一致，畸形/穿越 id 在拼进 worktree 与卡片路径**之前**即被拒（TD50）。注意这比旧行为**更严**：旧的 `ValidatePattern` 默认 `IgnoreCase`，`t1-foo`、`T0-scaffold` 这类 id 曾能通过入口、却会被 check-cards 拒——现在两处一致地拒。 |
| **R2 TDD** | 红→绿→**重构（含 `/simplify` 质量清理，见 task-loop 步骤 3.5）**；契约/e2e 测试**先写**；CI `verify` + 卡片 `dod_command` 双重把关。**验收即开场契约**：`start` 时把验收标准复述对齐、以 RED-first 作「达成一致」签名；写不出 RED（不可测/模糊/错范围）即先修卡再开工，标准冻结后别为过闸放低（L47；vacuous-pass 见 L19/L20）。**RED 为可选辅助检查点（T68：RED 证据闸已从 ship 拆除，合并闸=确定性闸）**：`task.ps1 -Phase red` 跑 DoD 断言**非零**（GREEN 下必抛、危险 dod 先被 check-cards 前置拦）；ship 不再校验 RED 证据（`-SkipRed` 保留为兼容 no-op） |
| **R3 第二模型评审（T68 起默认意见、非合并闸）** | **`ReviewGate` 留空（默认）= 意见模式**：ship **不因评审阻断**；跑不跑看**卡自己**——声明 `review_gate:` 即在合并前跑一遍（T242：只报告与入账、不回贴 status）；T301 起 `ReviewIntensityByTier` 再按卡算出的 tier 选档（只降不升）。要第二意见随时手动 `review.ps1`。**Two axes (T277)**: only a `spec` block on a **Tier-S** card stops a ship (`[R3-SPEC-BLOCK]`, pre-merge); `standards`, other tiers, and a verdict the base card arbitrates for the maker on that sha (`[R3-SPEC-ARBITRATED]`, T285) stay advisory. **The reviewer runs from the BASE commit (T288)**: both legs execute `scripts/review.ps1` as it exists on the sha the scope gate pinned (a temp copy, removed on every exit path) and print `[R3-REVIEWER-FROM-BASE] sha=<base>` — the rubric was already read from the baseline, and the script applying it now is too, so a card repairing `review.ps1` cannot review itself (`docs/HARNESS-REVIEW.md`). **R5**: a debt row only if `re-measured` on the merged tree as a code defect; card prose goes in that card's R5 commit, never a new card. **'required' = 旧强制闸，以下强制语义仅该态生效**。`review.ps1` 跑评审（workspace-write 沙箱、断网）→`{verdict}`→回贴 commit status（名取自 `ReviewStatusContext`）；有规则集则列为**必需检查**。远端 `ship` 强制用刷新后的 `origin/<base>`（fetch 失败即 block）；PR base 建好后与 merge 前各确认一次，防 retarget。**fail-closed 新鲜度守卫**：评审者非零退出或裁决 sha≠HEAD 即 block，每轮先清旧裁决（治 stale-verdict）。**阻断态可诊断（TD96）**：读不出可用裁决时分四态、各带 ASCII 状态码与恢复路由（表在 `rubric-detail.md`）；分类器拒答另存原文备读，裁决写不下来亦 block。**评审成本（T189/TD184）**：账本 block 报**质量轮**，`run_status`≠success 单列 attempts。 |
| **R4 测试卫生** | 重构阶段用 **mutation-survivor 剪枝**（见 §4）；每卡 `hygiene` 字段（**基本建议性**——内容靠 task-loop 步骤 + R3 rubric §2 兜底；check-cards 只机检**一条自洽性**：when `hygiene` promises a **mutation-evidence batch** (L165), `allow_paths` must cover BOTH files the batch writes — the registry `.psd1` AND its `-results.tsv`. The bare `specs/mutations/` directory covers both; a single `.psd1` does not, and that any-half form let T191 run its batch and then have the TSV refused at ship. 哨兵 `[CARD-HYGIENE-MUT]`，T110/TD145 + T200/TD194。模板那句 `mutation-survivor 剪枝` 不算承诺、不受此限） |
| **R5 文档同步** | 合并后立刻更新 CLAUDE.md/README/卡片 status；每卡 `doc_sync` 字段 + cleanup 阶段提醒（**建议性**——非机检字段，靠流程 + 评审兜底）。**冷存也归 R5**：卡一 merged 就该搬出热路径，故 cleanup 末尾跑 `archive.ps1 -Check`（只读：两张索引是否仍逐行等于生成器投影 + `[ARCHIVE-CHECK-PENDING]` 列出待搬项），有待搬项就跑 `archive.ps1` 清扫。`[ARCHIVE-HELD]` still holds a merged card whose worktree is on disk, so the sweep cannot get ahead of a full teardown. **What T293 fixed (issue #361) is the far side of that hold**: worktree gone, local branch still alive on its merge credential - the sweep moves the card, and cleanup used to refuse for want of it. It reads no card now, printing `[CLEANUP-CARD-COLD]`/`[CLEANUP-CARD-ABSENT]`.**同为建议性**（合并后才跑；合并闸恒为 ship 的确定性闸串，T68）。**效率约定（见 L123）**：R5 doc-sync（卡 status→merged、TD→paid、指针填 PR#）+ R5.5 lessons **默认写进 ship PR 本体**（一 PR / 一评审 / 一 CI，免每特性双 PR）；只有真·合并后事实（如 squash SHA）或并发撞号被迫拆分，才另开 follow-up PR |
| **R5.5 复盘（经验回流）** | **闭环最后一拍**：doc-sync/cleanup 之后做一次极简复盘——本卡若踩过会复发的非平凡坑（工具链/判断），`lessons.ps1 add` 入账、`blocking` 当场 `promote`，没有就显式跳过。与开场的经验检索（recall）对称，闭合自净化经验回路；门槛/时机的真相源见 `docs/LESSONS.md`「接入 task-loop」（**建议性**——非机检，靠流程 + `lessons` skill 触发兜底） |

## 2. 仓库托管：真实 GitHub PR

- 私有仓库；有 Pro 则 main 规则集要求 **PR + 必需检查 `verify`+`codex-review`**；仅 squash、合并后删分支。
- Codex 凭据**留在本地**，不进 CI；CI 只跑无网络的 `verify`。这是「Codex 代替人工」最安全的接法。
- free+private 不支持服务端规则集（403 Upgrade to Pro）→ R3 由客户端 `review.ps1` + task-loop skill 强制（verdict≠pass 即不合并）。
- 一次性建仓加固：`scripts\gh-bootstrap.ps1`（幂等，已探测 403 并优雅跳过）。
- **账号守卫**：所有 gh 写操作仅限 `scripts\_config.ps1` 配置的个人账号（`_guard.ps1` 前置校验）。

## 3. 单卡闭环命令

```powershell
# ⚠ L86：以下所有相位命令都从**主检出**根目录跑。cd 进 worktree 只为编辑文件——
#   在 worktree 里跑相对路径 scripts\task.ps1 会被 fail-closed 守卫拒（哨兵 L86-WT）：
#   其 $RepoRoot 派生成 worktree 自身，-Local 合并会把分支并进它自己、假报成功而 base 从未前进。传 -Base 也救不了。

# R1 + 引导隔离环境
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase start

#   在 <WorktreeRoot>\T0-SCAFFOLD 内**编辑文件**（由 Claude/人；相位命令仍回主检出跑）：
#   R2 绿： 实现到通过；不改冻结契约/manifest；
#          写第三方库调用前按 pinned 版本核验 API（Context7 MCP 取该版本文档 / 或 docs\references\*-llms.txt），别凭记忆写过时/错版本 API（R3 维度 #15 查；见 docs\references\README.md「动态 reference」）
#   R2 重构：/simplify 质量清理（只清理不找 bug；改后重跑 dod_command 确认仍绿）
#   R4   ： mutation-survivor 剪枝冗余测试

# R2 RED checkpoint (optional TDD aid since T68 — ship does not read it): asserts the DoD exits non-zero
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase red
# ONE REGIME, BOTH PHASES (T270/TD250 fix (b)): red and ship build what they run with the same call
# (`Get-ScaffoldDodPayload`), so a dod means one thing in both. Red ran it unwrapped until then, and three
# no-op shapes banked exit 0. Gate 10s measures it; why red never needed the raw form: specs/README.md.

# R2 DoD绿 → verify 总闸 → 提交 → 范围闸(allow_paths) → 预算闸(budget，T233) → 许可闸 → 防泄露闸(check-secrets) → push → PR base确认 → (R3：required 为强制闸；卡声明 review_gate: 则意见模式下也跑、不拦合并 T242) → CI 检查闸(分支 check-runs 全绿，T64；本地免跑全量 selftest 的验收面由 CI 分片并集承担) → merge前再确认base → 合并
#
# 预算闸（T233/TD235）与上一道范围闸成对：范围闸判**改哪里**，它判**改多少**（`budget:`）。两者都取 **BASE 卡**——
# 分支内抬高自己的上限无效。超出即 `[CARD-BUDGET-OVER]` 阻断，出路二选一：拆成后续卡，或把预算抬到 base 分支上、
# 作为独立提交、理由写进那个提交，再重跑同一次 ship。没声明 `budget:` 的卡照旧 ship（缺省即关）。字段语义见 `specs/README.md`。
# T241/TD247: both checked off ONE pinned base commit; a card not on base blocks `[SHIP-SCOPE-CARD-ABSENT]`.
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship
#   无远端 / 无 Codex 的本地 T0：加 -Local（DoD + 可选评审后**本地**合并，不 push/PR/gh）
#   pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship -Local

# 合并后：R1 拆 worktree + R5 文档同步提醒 + 两道只读自检（lessons check · archive -Check）
#   archive -Check 只读重投影两张冷存索引并报待搬项——**建议性、非闸门**（它跑在合并之后，合并闸
#   仍只有 ship 那串确定性闸，T68「越用越薄」）。有待搬项就顺手跑 pwsh -File scripts\archive.ps1
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase cleanup

# R5.5 复盘（闭环最后一拍）：本卡若踩过会复发的非平凡坑 → 入账；blocking 当场 promote；没有则跳过
#   （门槛/时机见 docs\LESSONS.md「接入 task-loop」；lessons skill 也会在「复盘/踩过的坑」语境自动触发）
pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'
```

> <!-- T36-DOCTRINE -->
> **ship 非原子 → 重跑同一条 `-Phase ship` 即 resume（T36-DOCTRINE）**：远端 `ship` 依次执行 DoD → verify → **提交** → 刷新远端基线 → 范围 → 预算 → 许可 → 防泄露 → push/PR → base 确认 →（R3：`required` 为强制闸；卡声明 `review_gate:` 则意见模式下也跑、不拦合并，T242）→ CI 检查闸（T64/T86：轮询 PR head 的 check-runs 分页取齐，期望检查 = `ci.yml` 自陈的单枚 fan-in context `required`——**契约、语义与沿革的真相源是 §3.0，此处不复述**；取不齐/任一红/超时皆 fail-closed。**元层 17 闸不在 PR 上跑**，验收面见 §3.2）→ merge 前 base 复查 → 合并；任一闸失败即 `throw`，并先打印 saga 报告（`T26-SHIPSAGA`; ASCII state-code lines: `[SAGA-DONE]` completed legs / `[SAGA-FAIL]` failure point / `[SAGA-TODO]` pending legs / `[SAGA-RESUME]` recovery command; the leg names are ASCII tokens, now printed live per leg by `[SHIP-TIME]` rather than copied here (T143 - a static second copy of $sagaLegs can only drift); the three -Local merge-leg failure states are `[SAGA-MERGE-TOKEN]`/`[SAGA-MERGE-CONFLICT]`/`[SAGA-MERGE-GUARDED]`; gate fail-closed throws carry `[SHIP-*]`/`[CI-GATE-*]` state codes, and machine checks anchor on the codes, never the prose — T73/TD120）。**每腿完成即打 `[SHIP-TIME] <腿名> <秒>s`**（T143），失败路径另打 `(incomplete leg)`，故死在中途的 ship 也说得出时间花在哪。**只打印、从不判定**：无任何 ship 决策读它，也不入效果账本（ADR 0003）。**只量 task.ps1 掌控的腿**——ship 前的强制本地全量验收（385.3s，操作者发起）与 agent 撰写时间（占单卡 50–80%）都不在内，当成卡的成本会差一个数量级。——先读它再动手。恢复无豁免：闸门确定性且幂等（T69），修复提交也走同一条 `ship`。
>
> **旧的「不要重跑 ship，直接对已开 PR 重跑评审并合并」路径已反转**：它绕过范围（scope）闸，而 CI **没有范围闸**，这正是 TD89 的根因。不得把 CI 通过当作完整闸门凭据，也不得用直接 review/merge 替代正常的 ship resume。
> (History: gate 15q's negative lexical lock on this reversal (TD93 item②) retired with the RED-evidence gate in the 0.30/0.31 subtractions. Guarded now by this text + gate 15r (saga report / recovery routing) + gate 15t (manual-recovery fixture).)
>
> **已 push 后禁止改写历史（红线）**：分支一经 push，严禁 rebase、amend、filter 或其他 history rewrite——已发布的提交被改写后，手工闸审的树与远端要合并的树可以是两棵（S8）。远端出现 non-fast-forward 时只能 `fetch` 后 `merge`（merge 从不 rebase），绝不 rebase（这也是 `task.ps1` 范围闸/推送闸两处报错文案已去除 rebase 建议的原因）。
>
> **Stateful recovery (S1–S8)** (machine-checkable feature → main route; post-T69 every state's main route is simply "fix, then rerun the same ship"):
>
> - **S1 committed-unpushed**: a scope/license/secrets leg failed before push — rerun the same ship; all gates re-judge. **Exception: if the scope leg names files you committed to the BASE locally, rerunning cannot help** — the gate judges `origin/<base>`, which lacks them. Fix: `git push origin <base>`, then rerun; ship names this state itself.
> - **S2 pushed-no-PR**: failed after push, before PR creation — rerun the same ship; idempotent push, then the PR-create leg. For the manual last-resort path note this state has **no PR yet**: get a PR number first (`gh pr view <TaskId>`, else `gh pr create --base <base> --head <TaskId> --title "<per §3.1>"`), then follow the last-resort block below.
> - **S3 push rejected / origin diverged (non fast-forward)**: `git fetch origin`, then inside the worktree `git merge origin/<TaskId>` (never rebase; `git pull --no-rebase` also works), resolve, rerun the same ship.
> - **S4 PR-open, review not passed** (`ReviewGate='required'`): land the fix commit, rerun the same ship (all gates re-judge; the open PR is reused).
> - **S5 approved-unmerged**: the merge leg failed (incl. the pre-merge base recheck throw) — rerun the same ship; if head or base moved, the pipeline re-reviews as needed.
> - **S6 `-Local` merge conflict (`MERGE_HEAD` on disk) — fixed three steps**: (1) `git merge --abort` in the main checkout; (2) inside the worktree `git merge <base>`, resolve, commit the merge commit (**never rebase**); (3) rerun the same ship so the resolved tree re-passes scope and every other gate — the in-pipeline merge is then clean. `git merge --continue` is the **last resort** (its tree passed no scope gate or review).
> - **S7 merged-unminted**: merge succeeded but the T24 cleanup token was not minted — confirm the merge fact, then cleanup via online re-verification or explicit `-Force`; without reliable confirmation the branch is kept fail-safe.
> - **S8 history-rewritten post-push**: **do not go straight to S2** — first `git fetch origin` and align with `origin/<TaskId>` non-rebase (`git merge origin/<TaskId>`), push, and before merging verify PR head (`gh pr view <TaskId> --json headRefOid`) == the locally gated-and-reviewed HEAD — otherwise the manually gated tree and the tree `gh pr merge` merges are two different trees. Then proceed per S2. The no-rewrite red line above exists to keep you out of this state.
>
> **任何已 push 状态的手工恢复都必须保持闸门保真（gate fidelity）**：合并前必须按序重跑 DoD、verify、范围、**预算**、许可、防泄露——CI 既没有范围闸也没有预算闸，**不能**以 CI 复跑替代下列路径：
> ```powershell
> # 1. 按任务卡 DoD 段逐字执行其中列出的原命令，并确认全部成功
> <任务卡 DoD 中的原命令>
> # 2. verify（确定性 e2e 验收）
> pwsh -NoProfile -File scripts\verify.ps1
> # 3. 范围闸（与 ship 同一判定核 scripts\_scope.ps1，非等价的第二实现）：越界 / 不可判（卡 allow_paths 取不到、
> #    基线引用不可解析、diff 求值失败）皆非零退出，等同范围闸 block、停止合并；越界时点名路径并给处置修法。
> #    与 ship 的**有意差异**：它不做定向 fetch（只读诊断口不动网络，ship 的 F5 则必须刷新且不许回退本地）——
> #    只打印所用基线 ref 与 sha，基线陈旧与否你自行 git fetch 后复跑；-Local 对照本地 <base>（同 ship -Local 的合并目标）。
> #    **-Base 要显式给**：在卡自己的 worktree 里跑时，缺省基线＝当前分支＝卡分支，会是空 diff、越界改动被空过——
> #    该情形已由自基线守卫 fail-closed 拒（哨兵 [SCOPE-SELFBASE]，同 task.ps1 的 L86-BASE 之理）。
> #    **先 fetch 再检、并把 PR head 钉进闸**：本脚本刻意离线（诊断口不动网络），故它看不出 origin/* 是否已陈旧——
> #    拿两个陈旧的跟踪引用照样能算出「无越界」。这一步的完整形态是三条：刷新两侧引用 → 取 PR head oid →
> #    以 -ExpectTip 让闸机检「判过的树 == 要合的树」；不符即非零退出（哨兵 [SCOPE-TIPMISMATCH]）。
> #    **跑主检出那份 checker、用 -Path 指被审树**：脚本按相对自身位置加载 _scope.ps1/_cards.ps1，从被审工作树里
> #    跑等于让被审分支自带的检查器判自己（把匹配器改成恒 PASS 即绕过本闸）——同 task.ps1 的 L86 之理。
> #    **fetch / gh 的退出码必须查**：PowerShell 在原生命令失败后会继续往下跑，于是 fetch 失败＝仍拿陈旧
> #    `origin/<base>` 判（连 allow_paths 都取自陈旧那份卡），gh 失败＝把空串喂给 -ExpectTip 把绑定静默关掉。
> #    **PR 的 baseRefName 也要核**：只钉 base 的 sha 不够——PR 若被 retarget 到别的基线分支，就会「按 A 判、
> #    往 B 合」；`--match-head-commit` 只绑 head，绑不到基线。故合并前须再核一次（下方最后手段块）。
> git fetch origin <base> <id>; if ($LASTEXITCODE -ne 0) { throw 'fetch 失败：拒绝在陈旧引用上判范围' }
> $prBase = gh pr view <PR号> --json baseRefName --jq .baseRefName
> if ($LASTEXITCODE -ne 0 -or $prBase -ne '<base>') { throw "PR 的基线是 '$prBase'、与本次判定的 <base> 不符：拒绝按 A 判往 B 合" }
> $head = gh pr view <PR号> --json headRefOid --jq .headRefOid
> if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw 'gh 未返回合法 head oid：拒绝在无绑定下判范围' }
> $baseOid = git -C <被审工作树> rev-parse refs/remotes/origin/<base>
> pwsh -NoProfile -File <主检出>\scripts\check-scope.ps1 -TaskId <id> -Base <base> -Path <被审工作树> -ExpectTip $head -ExpectBase $baseOid
> if ($LASTEXITCODE -ne 0) { throw '范围闸 BLOCK / 不可判：停止恢复，按其 [SCOPE-FIX] 行处置后重跑' }
> #    ↑ **这句不能省**：PowerShell 在原生命令非零后照样往下走，而下面那条说明用的 git 命令一执行就会把
> #    $LASTEXITCODE 覆盖掉，于是 BLOCK 会被后续 review/merge 步骤当成没发生。
> #    它在做什么（下面这条 git 命令仅为**说明判据**，故**整行注释掉、不可执行**——见其后三条绑定；真正该跑的是上面那条脚本）：
> #    core.quotepath=false 令 CJK 名字面重现、diff.renames=false 禁改名折叠（防「删卡外源 + 增卡内目标」被折叠成
> #    单条 rename、隐藏离场的越界源路径）；输出每一行都必须 ∈ 卡 allow_paths（整段相等或以 <allow>/ 开头），
> #    任一不在即等同范围闸 block、停止合并。
> #    **注意尖端写 <卡分支> 而非 HEAD**：脚本按卡 id 锚定尖端，手工敲成 HEAD 则「在主检出跑」会变成 master 比
> #    master、空 diff 假绿；脚本另有 HEAD 无从表达的三条绑定（远端/本地两侧对称取 ref、本地与远端分叉即 fail-closed、
> #    allow_paths 只认基线那份卡），故手敲 git 命令**不能**替代它。
> # git -c core.quotepath=false -c diff.renames=false diff --name-only origin/<base>...origin/<卡分支>
> # 3.5 预算闸（T233/TD235）：判「改多少」。跑**主检出**那份量表、用 -Path 指被审树（同上一步的 L86 之理），
> #     **-Base 显式给**（同上）。它与 ship 侧读同一枚核、同一份 BASE 卡，故分支内抬高预算在这里同样无效。
> #     退出码：0=under 或无预算；1=**trip 或 over**；2=量不出来。**只在 over 时停**——trip 是提示不是阻断，
> #     故必须读它打印的状态，不能只看退出码把 trip 当 block。
> pwsh -NoProfile -File <主检出>\scripts\check-budget.ps1 -TaskId <id> -Base <base> -Path <被审工作树>
> #     ↑ 打印 OVER 即停：拆成后续卡，或把 `budget:` 抬到 <base> 上（独立提交、理由写进它），再从头重跑本序列。
> # 4. 商用许可闸
> pwsh -NoProfile -File scripts\check-licenses.ps1
> # 5. 防泄露闸
> pwsh -NoProfile -File scripts\check-secrets.ps1
> ```
>
> **已 push 状态的手工最后手段（TD85-RESUME）**：只有上述全部确定性闸已手工通过后，才可直接 `review.ps1 -PostStatus` 并合并。以下命令本身**不会**重跑 DoD/verify/范围/预算/许可/防泄露，**不得单独使用**：
> ```powershell
> pwsh -NoProfile -File scripts\review.ps1 -WorktreePath <worktree> -Base <base> -PostStatus -PrNumber <PR号>
> # 合并的必须是上面**范围闸判过的那个** sha（$head 同上一步；--match-head-commit 令 head 变动即拒绝合并）——
> # 否则「检查过的树」与「被合并的树」可以是两棵。**基线也要在合并前再核一次**：head 没变但 PR 被 retarget
> # 到别的基线分支时，--match-head-commit 照样放行，结果是「按 A 判、往 B 合」。
> $prBase2 = gh pr view <PR号> --json baseRefName --jq .baseRefName
> if ($LASTEXITCODE -ne 0 -or $prBase2 -ne '<base>') { throw "合并前复核：PR 基线已变成 '$prBase2'，拒绝合并" }
> # 基线**名**没变还不够：同一条 base 分支若在判定之后前移，name 与 head 都仍合法，但合并落到的是**新基线**，
> # 而 allow_paths 取自基线那份卡——判定所依据的标准可能已经变了。故合并前必须复核基线 **OID** 未前移。
> git fetch origin <base>; if ($LASTEXITCODE -ne 0) { throw '合并前复核：fetch 失败，拒绝合并' }
> $baseOid2 = git -C <被审工作树> rev-parse refs/remotes/origin/<base>
> if ($baseOid2 -ne $baseOid) { throw "合并前复核：基线已前移（$baseOid -> $baseOid2），判定依据的 allow_paths 可能已变——回到第 1 步重跑全部确定性闸后再合" }
> # Pre-merge CI check (TD134): the CI acceptance surface must be green on the pinned $head before a
> # manual merge - the ship pipeline's CI check gate enforces this on both its paths (auto and
> # -NoAutoMerge), and this recipe must not be the one plane that skips it. T86: what has to be green is
> # ci.yml's ONE fan-in context, `required` (see "CI fan-in contract" below) - it is the job that fails
> # unless every other job succeeded, so a single literal SUCCESS on it is the whole acceptance surface.
> # Adding a job to ci.yml does NOT change this line; wiring the new job into `required`'s needs: does.
> # ADR 0007: scaffold-selftest no longer triggers on pull_request, so its 2 x 5 shards are NOT part of a
> # PR's check set - do not wait for them. NOTE: the meta-layer 17 gates are NOT proven by this check; on
> # a diff that touches scripts/hooks/workflows you must have a green local full selftest before merging
> # by hand.
> $ciOk = gh pr checks <PR号> --json name,state --jq '[.[] | select(.name == "required" and .state == "SUCCESS")] | length'
> if ($LASTEXITCODE -ne 0 -or [int]$ciOk -lt 1) { throw "pre-merge CI check: ci.yml's required fan-in context is not green on this head - fix/rerun CI first, refuse manual merge" }
> gh pr merge <PR号> --squash --match-head-commit $head
> pwsh -File scripts\task.ps1 -TaskId <id> -Phase cleanup
> ```
> （`-SkipRed` is a compat no-op since T68 — it changes nothing and is never part of any recovery recipe.）
>
> **别用 `cleanup`「重来」**：它会拆 worktree、丢掉已实现改动，只在**已合并后**收尾。cleanup 删本地分支须 T24 凭据 / gh 在线复验（PR=MERGED 且 headRefOid==本地 tip）/ `-Force` 三信号之一；皆无或 tip 不匹配即 fail-safe 保留分支（机检 selftest 15p/15h4）。残留 merged worktree 由心跳 `worktree-orphan` 探针兜底发现。

两闸门分工：`verify` 是确定性 e2e 验收——**本地 ship 亦跑 verify（free+private 下本地即权威）**，CI 在 PR 上信息性复跑；`codex-review` 是不变量/边界定性评审（T68 起默认意见、仅 `ReviewGate='required'` 时是合并闸）。**确定性闸全绿方可合并。**

### 3.0 CI fan-in contract (T86) — ci.yml states its own acceptance surface

`ci.yml` no longer leaves clients to reconstruct which checks must pass. It carries one **fan-in job named
`required`** at the bottom of the file: `if: ${{ always() }}`, `needs: [<every other job>]`, and one inline
pwsh step that reads `${{ toJSON(needs) }}` and exits non-zero unless every dependency's `result` is the
literal string `success`. **`skipped` and `cancelled` are failures** — a job that executed nothing proves
nothing, and for a context a ruleset requires only an explicit success is safe to accept.

`always()` is the load-bearing half. Without it GitHub **skips** the fan-in job as soon as a dependency
fails, and a skipped required check reports **success** to the ruleset: the merge sails through on a red
build. That trap is the whole reason this shape exists, which is also why the step has to re-derive the
verdict itself rather than trust its own having-run.

What follows from that:

- **The `needs:` list is the version-controlled required-check list.** A ruleset requires exactly one
  context, `required`. Adding a job to `ci.yml` and wiring it into `needs:` extends the merge bar with no
  client change at all. (Pointing the branch ruleset at `required` instead of `verify` is a repo-settings
  action the repo owner takes — it is deliberately not automated here.)
- **`task.ps1`'s `[CI-GATE]` waits on that one context** (present, completed, and exactly `success`) and
  asserts no other check is failing. It stays fail-closed: no `ci.yml` in the merge candidate tree
  (`[CI-GATE-WF-MISSING]`), or a `jobs:` block with no `required` job — deleted, renamed, or unparseable —
  (`[CI-GATE-JOBS-DRIFT]`) means the acceptance surface is unprovable, so no merge.
- **One judgement, two consumers.** `scripts\_ci.ps1`'s `Test-ScaffoldCiFanIn` decides both the ship gate's
  question and selftest **8.2g**'s, so the gate the merge trusts and the gate that guards the file cannot
  drift. 8.2g machine-checks that the `needs:` list names **every** job in the file — "added a job, forgot
  the list" goes red locally, before ship, rather than shipping a job that can never block a merge.
- **`verify`'s last step is a clean-worktree assertion** (`if: ${{ always() }}`): `git status --porcelain`
  non-empty fails the job even when every step above exited zero. That is what turns each "regenerate and
  commit the result" rule in this repo from a doc convention into a check, and it is the CI-side half of
  the projection discipline `archive.ps1 -Check` applies locally.

### 3.1 PR title convention (generated by ship, never hand-typed)

`ship` composes it as Conventional Commits + card id, derives `type`/`scope` from fields the card already
carries, and GitHub appends the real PR number on squash merge. **No gate checks the title** — it is
generated, so compliance follows from the construction. Full shape, the four-row derivation table and the
mandatory-space rule: **`docs/PR-CONVENTIONS.md`** (moved there by T197/TD156; this heading stays because
`CLAUDE.template.md`, `scripts/task.ps1` and the S2 note above all cite §3.1 by number).

### 3.2 Inner-loop acceptance（内环节奏 · T117）

- **Rounds**: run the GATE-MAP `-Only` row for what you touched (seconds); a scoped run is never acceptance.
- **Proof**: by card tier (ADR 0016, `[CARD-TIER]`). **S** = one full run green over the frozen tree — `-Parallel` first (385.3s, TD158), no-arg serial equally valid but slower — one successful proof per immutable candidate; a failed run or any post-launch edit voids it. **1** = `selftest.ps1 -TaskId <id>` (routed set + floor, `[SELFTEST-TIER-PASS]`; an unrouted path escalates to full). **0** = `ci.yml`.
- **Async**: run it in the background, keep working in another worktree; record `git rev-parse HEAD` + `git status --porcelain` at launch, else the PASS is not attributable.
- **Lane**: one -Parallel at a time machine-wide; heavy scoped runs (gate 15 / 17 segments) share it. Timings are solo-run.
- **Multi-session (2-3)**: one session per worktree (progress.md is per-cwd). Development parallel (fixtures per-PID/GUID; gate 15 owns a temp WorktreeRoot); finalization serial — refresh base, freeze, prove, ship; `-Local` ship never overlaps; no `gh auth` switch mid-run. ci.yml fan-in judges one PR head (per-ref), it does NOT serialize merges; racers are backstopped by the post-merge push matrix — its red = immediate fix.

## 4. R4：mutation-survivor 测试剪枝（让"删冗余测试"可机检，而非凭感觉）

对每个**候选冗余**测试，逐个验证：
1. 删除该测试；
2. 故意把它本应守护的生产代码改坏（注入 mutation）；
3. 跑全套：
   - 有**其它**测试失败 → 覆盖未丢 → **可安全删**；
   - 无任何失败 → 该测试是唯一守护者 → **还原**它；
4. 撤销 mutation。

**测试落点（与剪枝互补，防文件爆炸）**：mutation-survivor 删的是**冗余测试**；这里管的是**测试该放哪**。
改既有代码时**扩展该单元的现有测试文件**，不新建平行文件；测试布局**镜像源码**（一源↔一测试模块），
新测试文件只给真正的新单元。两者合起来：既不留冗余测试，也不让测试文件随每次改动无序膨胀。
（此为判断纪律，难纯机检——由 task-loop R2 RED 提醒 + QUALITY-RUBRIC §2 第 11 条让 Codex 评审兜底。）

## 5. Windows worktree 注意事项

- worktree 根用浅路径 `<WorktreeRoot>\<TaskId>`（留空配置 => 按 OS：Windows `<系统盘>\wt`（如 `C:\wt`，取自 `$env:SystemDrive`）/ macOS·Linux `~/.wt`），规避 MAX_PATH；Windows 建议 `git config --global core.longpaths true`。
- `.venv` / `node_modules` **每 worktree 独立**（gitignored），不共享。
- 拆除前先关掉占用该目录的服务/IDE/杀软句柄，再 `git worktree remove --force`（必要时 `-f` 两次）→ `git worktree prune`。**不要在资源管理器里直接删目录**。

## 6. 可选：安装 superpowers（仅取技法，参考用）

```text
# 在交互式 Claude Code 会话里：
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace
# 其 worktree/finish 脚本体是 bash，需 Git Bash 在 PATH。
```
权威闸门始终是 `verify.ps1` + `codex` + `task.ps1`；外部技法仅为可选参考。

### 6.1 ponytail（YAGNI 设计透镜，已就地 vendoring · 仅 skills）
`.claude\skills\ponytail{,-review}\` 是从 upstream（MIT，见 `ponytail\` 旁 `LICENSE`/`NOTICE.md`）就地
vendoring 的**设计层**极简透镜，**on-demand**（不装其常驻 Node 钩子，下游无新增运行时面）。`ponytail-review` 含
**diff / 全仓两模式**（diff 审当前改动 / 全仓审整库 over-engineering，按 biggest-cut-first 排序）。与代码层的 `/simplify`
**分两个高度，互补不重复**：

| 透镜 | 高度 | 问的问题 | 工作流位置 |
|---|---|---|---|
| **ponytail** | 设计/架构 | 要不要建？需要这层抽象/这个依赖吗？stdlib/原生能覆盖吗？ | task-loop **步骤 1.5**（写测试前） |
| **`/simplify`** | 代码机制 | 既然要建，码紧不紧、有没有复用/去重 | task-loop **步骤 3.5**（GREEN 后重构拍） |

**刻意不取**：常驻 Node 钩子（保持 on-demand）、`ponytail-debt`（与 `specs\tech-debt-tracker.md` 重复）、
非 Claude agent 的规则目录。**注意**：脚手架本身是有意的重型 harness——ponytail 审业务卡的取向，别拿它砍掉脚手架刻意的结构。

## 7. 交付层与文件清单

**四层架构（各司其职，不重叠）**——用「脚本substrate + 薄 skill 驱动 + 1 个护栏 hook」，
**不**做【R3 评审】subagent（会与 codex 重复）；长自主运行**按间隔**（每卡或每 N 步）派 fresh-context 证据审计子代理作为标准自校验法（独立全新上下文，优于自我批评；见 task-loop 步骤 4.7）——仍为**建议层、非闸**，确定性闸门始终是唯一的闸。

| 层 | 角色 | 文件 |
|---|---|---|
| **脚本（确定性 substrate）** | R1/R3 机制 + R2 的 DoD 执行；CI/人/Claude 同一闸门 | `scripts\*.ps1` + `.github\workflows\ci.yml` |
| **Skill（模型侧驱动器）** | 自动触发 + 记住 R2 先测/R4 剪枝/R5 文档同步；**包装脚本，不重实现** | `.claude\skills\task-loop\SKILL.md` |
| **Hook（确定性护栏）** | 拒绝编辑冻结物（契约/schema，见 _config.ps1 FrozenPaths） | `.claude\hooks\guard-frozen.ps1` + `.claude\settings.json`（PreToolUse） |
| **Codex（R3 评审者）** | 唯一评审者，`review.ps1` 调用 | codex CLI/插件 |

| 文件 | 作用 |
|---|---|
| `scripts\_config.ps1` | **唯一项目配置点**：账号 / 项目名 / 冻结路径 / Python 版本 / worktree 根 |
| `scripts\_gitbase.ps1` | 基线名→引用解析的单一真相源（`review.ps1` / `task.ps1` 共用） |
| `scripts\_ci.ps1` | CI fan-in 契约判定的单一真相源（`Test-ScaffoldCiFanIn`；ship 的 `[CI-GATE]` / selftest 闸 8.2g 共用，见 §3.0） |
| `scripts\_cards.ps1` | 卡片 front-matter 解析的单一真相源（`check-cards.ps1` / `task.ps1` / `archive.ps1` / `triage.ps1` 共用）；列表终止 = 任何非缩进行即止（TD112），check-cards 建卡期显式拒 front-matter 非缩进无冒号垃圾行（整行注释豁免，哨兵 `CARD-FM-GARBAGE`） |
| `scripts\gh-bootstrap.ps1` | 一次性建私有仓 + 加固 + main 规则集（R3 必需检查） |
| `scripts\task.ps1` | 单卡闭环编排（R1/R2/R3/R5） |
| `scripts\review.ps1` | Codex 评审 → `{verdict}` → 回贴 status/评论（R3） |
| `scripts\check-licenses.ps1` | 商用许可闸（PyPI/npm） |
| `scripts\lessons.ps1` | 自净化经验系统操作器（pitfall + judgment 两类，见 `docs\LOOP-ENGINEERING.md`） |
| `scripts\triage.ps1` | **心跳**：只读 cadence 扫描各子系统 → `_local\triage-inbox.md`（loop-engineering，见 `docs\LOOP-ENGINEERING.md`） |
| `scripts\verify.ps1` | 验收总闸门（确定性 e2e；项目特定闸门 2 需自填） |
| `.claude\skills\task-loop\SKILL.md` | 自动触发并驱动整条闭环（包装脚本） |
| `.claude\skills\triage\SKILL.md` | 心跳回路：scan → 分诊 → 喂既有交付链（只发现不行动） |
| `.claude\hooks\guard-frozen.ps1` + `.claude\settings.json` | PreToolUse 拒绝改冻结契约/schema |
| `.github\workflows\ci.yml` | CI 确定性闸（R2）：`verify` 干活 + `required` fan-in，规则集只需要求 `required`（见 §3.0） |
| `specs\verdict.schema.json` | 裁决机读契约 |
| `specs\tasks\*.md` | 计划任务章节的可执行投影 |
| `task_plan.md` / `findings.md` / `progress.md` | planning-with-files 交接三件套（gitignored） |

### 冻结护栏（guard-frozen hook）
PreToolUse 钩子在编辑落地**之前**拒绝改 `scripts\_config.ps1` 的 `FrozenPaths` 所列文件
（按仓库相对后缀匹配，故 worktree 内同样生效）。这是冻结不变量的确定性兜底，
与「codex 评审 + GitHub 规则集在合并时拦截」互补（更早、更省一次 PR 往返）。
**合法的版本升级**：临时在 `.claude\settings.json` 注释掉 `guard-frozen` 的 matcher，走版本评审后恢复。
（FrozenPaths 为空时本护栏不拦任何文件——项目还没冻结点时的默认。）

## 8. 双评审流水线（安全 → commit → Codex；正交不重复）

两个评审者**职责正交、不冲突**，串成一条流水线：

```
本地开发 → /security-review-local（侧重安全：注入/认证/加密/XSS/数据暴露）
          └─ 末行 SECURITY-REVIEW: pass ──▶ commit ──▶ Codex PR 评审（契约/边界/工程）──▶ 合并
             block → 修复后重跑，不 commit
```

- **第一道闸 · 安全（commit 前）**：斜杠命令 `/security-review-local`（审工作区未提交改动 `git diff HEAD`）。
  高置信、低误报、子任务并行 + 假阳性过滤；末行机读裁决 `SECURITY-REVIEW: pass|block`。
- **第二道闸 · Codex（PR 时）**：`scripts\review.ps1` 调用 codex（R3），审冻结契约/schema、关键不变量、商用边界。
- **为何不重复**：security-review 找**安全漏洞**，codex 找**工程/契约正确性**——两个不同维度。codex 仍是**唯一** R3 评审者。
- **PR 场景**另有官方原版 `/security-review`（对比 `origin/HEAD...`），需要远端；本地 commit 前用 `-local` 变体。
- 许可闸 `check-licenses.ps1` 已接入 `task.ps1 -Phase ship`（DoD 绿后、commit 前），命中 GPL/AGPL/非商用即 block。
- **防泄露闸 `check-secrets.ps1` 已接入 `ship`**（提交后、推送/合并前）：硬编码密钥（含 snake_case / 无引号赋值）或被追踪机密命中即 block——这是针对密钥的**确定性**拦截，跑工作树自带副本以扫到本卡刚提交的改动。
- **`/security-review-local` 是模型在环的【建议】层**（非确定性闸，`task.ps1` 不强制其裁决）：它覆盖注入/认证/越权等需语义判断的面，补充上面的确定性闸，但**不应被当作"机器强制的必过闸"**。涉敏感面时建议跑，密钥类硬拦交给 `check-secrets`。

来源：`anthropics/claude-code-security-review`（MIT）。仅取其 slash command（纯本地、无需 API key / Actions、Windows 原生）。
