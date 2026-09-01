# DevOps 工作流 · worktree + TDD + Codex-PR 闸门 + 测试卫生 + 文档同步

> EN: The authoritative operating manual for the R1–R5 single-card loop — per-card git worktree (R1), RED-first TDD (R2), second-model PR review (R3), test pruning (R4), doc sync (R5), then a closing lessons-capture retrospective (R5.5) — driven by `scripts/task.ps1` and the task-loop skill. Remote `ship` enforces RED evidence → DoD → verify → commit, refreshes the tracked base before the baseline-dependent scope/review gates, then runs scope `allow_paths` → license → secret-leak → real-diff budget → push/PR-base validation → deliberately non-deterministic codex R3 review → pre-merge base revalidation → squash merge.

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
| **R1 worktree** | 每张卡一个 `<WorktreeRoot>\<TaskId>` worktree + 同名分支；`task.ps1 -Phase start/cleanup` 管理（WorktreeRoot 见 scripts/_config.ps1） |
| **R2 TDD** | 红→绿→**重构（含 `/simplify` 质量清理，见 task-loop 步骤 3.5）**；契约/e2e 测试**先写**；CI `verify` + 卡片 `dod_command` 双重把关。**验收即开场契约**：`start` 时把验收标准复述对齐、以 RED-first 作「达成一致」签名；写不出 RED（不可测/模糊/错范围）即先修卡再开工，标准冻结后别为过闸放低（L47；vacuous-pass 见 L19/L20）。**RED 现为可强制检查点**：`task.ps1 -Phase red` 跑 DoD 断言**非零**并落 `.review/<id>.red` 证据；`ship` 缺该证据即拒（非 TDD 卡用 `-SkipRed` 显式跳过） |
| **R3 PR + Codex 评审代替人工** | `review.ps1` 跑 Codex 只读评审→`{verdict}`→回贴 commit status（名取自 `_config.ps1` `ReviewStatusContext`，默认 `codex-review`，换后端可改名）；有规则集则列为**必需检查**；free+private 无服务端规则集时由 review.ps1 退出码本地强制。远端 `ship` 先刷新并强制使用 `origin/<base>`（fetch 失败即 block），PR 建好后确认 `baseRefName`；R3 后、merge 前再确认一次，防并发 retarget。**fail-closed 新鲜度守卫**：评审者非零退出或裁决 sha≠HEAD 即 block，且每轮先清旧裁决文件（治评审者静默 no-op 读到上轮 pass 的 stale-verdict fail-open）。**阻断态可诊断（TD96）**：「评审者跑完了但读不出可用裁决」不再是一条兜底文案，而是四个各带 ASCII 状态码 + 专属恢复路由的态（rubric §5 有表），分类器拒答落 `[R3-NO-VERDICT-JSON]` 且原文另存 `.review/(分支名).raw.txt` 供你先读再判；裁决写不下来 `[R3-VERDICT-WRITE-FAILED]` 亦 block（无可复核记录不算放行）；裁决产物叶子、`.review` 或其任一祖先是符号链接/重解析点时打 `[R3-REVIEW-DIR-UNSAFE]` 停手（启动时与唤起评审者之前各判一次；检出后不再有任何建/删/写、评审者不被唤起）。**首轮提交前建议先跑本地自检**（task-loop 步骤 4.6）——R3 每轮只报当轮最刺眼一处，完备性问题不先自查会逐轮外溢（L97），拖长 round-trip |
| **R4 测试卫生** | 重构阶段用 **mutation-survivor 剪枝**（见 §4）；每卡 `hygiene` 字段（**建议性**——check-cards 不机检该字段，靠 task-loop 步骤 + R3 rubric §2 兜底） |
| **R5 文档同步** | 合并后立刻更新 CLAUDE.md/README/卡片 status；每卡 `doc_sync` 字段 + cleanup 阶段提醒（**建议性**——非机检字段，靠流程 + 评审兜底）。**效率约定（见 L123）**：R5 doc-sync（卡 status→merged、TD→paid、指针填 PR#）+ R5.5 lessons **默认写进 ship PR 本体**（一 PR / 一评审 / 一 CI，免每特性双 PR）；只有真·合并后事实（如 squash SHA）或并发撞号被迫拆分，才另开 follow-up PR |
| **R5.5 复盘（经验回流）** | **闭环最后一拍**：doc-sync/cleanup 之后做一次极简复盘——本卡若踩过会复发的非平凡坑（工具链/判断），`lessons.ps1 add` 入账、`blocking` 当场 `promote`，没有就显式跳过。与开场的经验检索（recall）对称，闭合自净化经验回路；门槛/时机的真相源见 `docs/LESSONS.md`「接入 task-loop」（**建议性**——非机检，靠流程 + `lessons` skill 触发兜底） |

**真实 diff 预算是 push / 开 PR / R3 之前的硬闸**：`ship` 用 `review.ps1 -SizeOnly` 对 pinned `base...HEAD` 计算 `additions+deletions <= 1000` 且未截断 unified diff `<= 60000` 字符，任一超限即 `[R3-DIFF-TOO-LARGE]`，不 push、不开 PR、不消费 reviewer round。`allow_paths` 的条目数只在建卡期提示共享面与所有权——目录前缀可覆盖大量文件、单个脚本也能产生巨大 diff，故它**不是**规模证明。超限卡必须拆成有依赖的 1→N 卡，不能扩 `allow_paths` 或提高 CLI 参数绕过；`MaxChangedLines`/`MaxDiffChars` 只允许收紧。

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

# R2 RED 检查点（先写失败测试后跑）：断言 DoD 非零并落 .review\T0-SCAFFOLD.red 证据，ship 据此强制 RED-first
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase red

# 远端基线定向 fetch → R2 DoD绿 → verify 总闸 → 提交 → 范围闸(allow_paths) → 许可闸 → 防泄露闸(check-secrets) → 真实 diff 预算 → push → PR base确认 → R3 Codex pass → merge前再确认base → 合并
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship
#   无远端 / 无 Codex 的本地 T0：加 -Local（DoD + 可选评审后**本地**合并，不 push/PR/gh）
#   pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship -Local

# 合并后：R1 拆 worktree + R5 文档同步提醒
pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase cleanup

# R5.5 复盘（闭环最后一拍）：本卡若踩过会复发的非平凡坑 → 入账；blocking 当场 promote；没有则跳过
#   （门槛/时机见 docs\LESSONS.md「接入 task-loop」；lessons skill 也会在「复盘/踩过的坑」语境自动触发）
pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'
```

> <!-- T36-DOCTRINE -->
> **ship 非原子 → 重跑同一条 `-Phase ship` 即 resume（T36-DOCTRINE）**：远端 `ship` 依次执行 RED 证据 → DoD → verify → **提交（watershed）** → 刷新远端基线 → 范围 → 许可 → 防泄露 → 真实 diff 预算 → push/PR → base 确认 → R3 → merge 前 base 复查 → 合并；任一闸失败即 `throw`，并先打印 saga 报告（`T26-SHIPSAGA`：已完成腿、失败点、待办腿及按状态生成的恢复命令）——先读它再动手。commit 前中断可直接重跑；commit 腿铸造 T35 watershed receipt 后，receipt 会让合法重跑通过 RED 新鲜度闸，因此 commit 后至 merge 前的**主恢复同样是：修复后重跑原封不动的同一条 `ship`，让全部闸重新通过（含修复提交），没有豁免。**
>
> 这条 doctrine 只由 saga catch 的 receipt-present 分支与本节承载。**旧的「不要重跑 ship，直接对已开 PR 重跑评审并合并」路径已反转**：它绕过范围（scope）闸，而 CI **没有范围闸**，这正是 TD89 的根因。不得把 CI 通过当作完整闸门凭据，也不得用直接 review/merge 替代正常的 ship resume。
> 该反转现由 `selftest.ps1` 闸 **15q** 的负断言机检锁死（TD93 item②）：`scripts/task.ps1` 内一旦复现旧路径的**三条既定措辞**（禁用词表见 15q）即 `FAIL`——旧教义最危险的载体正是印在 RED 证据闸抛错处的**运行期指引**注释，操作者撞闸时会照读照做。该锁是**字面量比对、非语义**：改写措辞可绕过，故它是最后一道栅栏、**不替代评审**。扫描面 = `scripts/task.ps1`（禁绝）+ `selftest.ps1` 自身（词表恰 1 次）；本文件**不在**扫描面内——本段引号内是「已反转」的历史描述，合法且必须保留。
>
> **watershed 后禁止改写历史（红线）**：从首个 baseline commit／receipt 铸造起，严禁 rebase、amend、filter 或其他 history rewrite——改写会令 receipt 不再自洽、破坏证据新鲜度、并使已 GREEN 的树无法重新合法执行 RED。远端出现 non-fast-forward 时只能 `fetch` 后 `merge`（merge 从不 rebase），绝不 rebase（这也是 `task.ps1` 范围闸/推送闸两处报错文案已去除 rebase 建议的原因）。
>
> **状态化恢复（S1–S9）**（机器可判特征 → 主路；真相源见 `specs` 计划 §5 状态表，本节为权威长文）：
>
> - **S1 committed-unpushed**：范围/许可/防泄露/真实 diff 预算腿失败且尚未 push——直接重跑同一条 ship，receipt 放行 RED、所有闸重判。receipt 丢失或不自洽时先 `git reset --soft <evidence.redSha>`（靶取 RED 证据 sha 原值，**非 `HEAD~1`**）后重跑；evidence sha 为占位符则无可 reset 之靶（该分支从未有 baseline commit），须人工处置。
> - **S2 pushed-no-PR**：push 后、PR 创建前失败——重跑同一条 ship，幂等 push 后续接 PR-create 腿。receipt 丢失时先手工重跑下述全部确定性闸；因本态**尚无 PR**，`-PostStatus`（需 PR 号）不可直用，须先 `gh pr view <TaskId>`（无则 `gh pr create --base <base> --head <TaskId>` 补建 PR）拿到 PR 号，再走 `-PostStatus` 最后手段。
> - **S3 push 被拒 / origin 分叉（非 fast-forward）**：`git fetch origin`，再在 worktree 内 `git merge origin/<TaskId>`（merge 从不 rebase，故无需 `--no-rebase` 标志；亦可 `git pull --no-rebase`），处理后重跑同一条 ship。
> - **S4 PR-open、R3 未过**：完成修复形成 fix commit，再重跑同一条 ship（全部闸重判，已开 PR 复用）。receipt 丢失按 S2。
> - **S5 R3-passed-unmerged**：merge 腿失败（含 merge 前 base 复查 throw）时重跑同一条 ship；head 或 base 有变即在管线内重新 R3。receipt 丢失按 S2。
> - **S6 `-Local` 合并冲突（磁盘存在 `MERGE_HEAD`）——主路径固定三步**：①在主检出执行 `git merge --abort`；②进入 worktree 执行 `git merge <base>`，解决冲突并提交 merge commit（**禁 rebase**；该 merge commit 被 receipt 的祖先语义接纳——T35 正例夹具已钉死）；③重跑同一条 ship，使消解树重新通过范围闸、R3 及其余闸，此时管线内合并必为 clean。`git merge --continue` 降为**最后手段**——它产生的树未过范围闸与 R3。
> - **S7 merged-unminted**：合并已成、T24 cleanup 凭据未铸——先人工确认合并事实，再经在线复验或显式 `-Force` 走既有 cleanup；无可靠确认则 fail-safe 保留分支。
> - **S8 history-rewritten post-watershed**：receipt 在场但已非当前 HEAD 的祖先，视为不自洽——未 push 按 S1 兜底；**已 push 时不得径直按 S2**：须先 `git fetch origin` 并以非-rebase 方式对齐本地与 `origin/<TaskId>`（`git merge origin/<TaskId>`，禁 rebase）后再 push，且合并前必须核验 PR head（`gh pr view <TaskId> --json headRefOid`）== 已过闸且已评审的本地 HEAD——否则手工闸审的是改写后的本地树、而 `gh pr merge` 合的却是另一远端 head。对齐并核验后方按 S2 兜底。历史改写红线本应阻止进入此态。
> - **S9 evidence lost**（`.red` 丢失／worktree 重建／误跑 `-Phase start`）：由「receipt 在场 ∧ evidence 缺失」的双 `Test-Path` 检测——未 push 走本节 reset 兜底（靶不可读则保留锚点人工处置）；已 push 走下述全部确定性闸手工重跑路径 + 最后手段。**失败后不要重新执行 `-Phase start`。**
>
> **任何已 push 状态的手工恢复都必须保持闸门保真（gate fidelity）**：合并前必须按序重跑 DoD、verify、范围、许可、防泄露、真实 diff 预算——CI 没有范围闸，**不能**以 CI 复跑替代下列路径：
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
> # 4. 商用许可闸
> pwsh -NoProfile -File scripts\check-licenses.ps1
> # 5. 防泄露闸
> pwsh -NoProfile -File scripts\check-secrets.ps1
> # 6. 真实 diff 预算（使用主检出检查器；超限或不可判即停止恢复）
> pwsh -NoProfile -File <主检出>\scripts\review.ps1 -WorktreePath <被审工作树> -Base <base> -SizeOnly
> if ($LASTEXITCODE -ne 0) { throw '真实 diff 预算 BLOCK：停止恢复，拆卡或修复 git 基线后重跑' }
> ```
>
> **receipt 丢失且已 push 时的最后手段（TD85-RESUME）**：只有上述全部确定性闸已手工通过后，才可直接 `review.ps1 -PostStatus` 并合并。以下命令本身**不会**重跑 DoD/verify/范围/许可/防泄露/真实 diff 预算，**不得单独使用**：
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
> gh pr merge <PR号> --squash --match-head-commit $head
> pwsh -File scripts\task.ps1 -TaskId <id> -Phase cleanup
> ```
> （恢复文案任何位置都**不以 `-SkipRed` 作恢复捷径**——`-SkipRed` 不经 RED 证据闸，且铸造条件明定其 ship 不铸 receipt。）
>
> **别用 `cleanup`「重来」**：它会拆 worktree、丢掉已实现改动，只在**已合并后**收尾。cleanup 删本地分支须 T24 凭据 / gh 在线复验（PR=MERGED 且 headRefOid==本地 tip）/ `-Force` 三信号之一；皆无或 tip 不匹配即 fail-safe 保留分支（机检 selftest 15p/15h4）。残留 merged worktree 由心跳 `worktree-orphan` 探针兜底发现。

两闸门分工：`verify` 是确定性 e2e 验收——**本地 ship 亦跑 verify（free+private 下本地即权威）**，CI 在 PR 上信息性复跑；`codex-review` 是不变量/边界定性评审。**两者皆绿方可合并。**

远端 ship 的 **candidate CI** 闸按「逐层身份绑定 → 决策前 exact-head/base 快照」收口，任一层不匹配即 fail-closed：① 本地 HEAD ≡ PR `headRefOid` ≡ run `head_sha`（R3 期间本地 HEAD 前移即 `[CI-GATE-LOCAL-HEAD-MOVED]`）；② run 的 `path`/`event`/`pull_requests[].number` 三处比较一律**大小写敏感**——PS 的 `-eq`/`-in`/`-contains` 与属性访问默认不敏感，`CI.yml@MASTER` 或只带 `Number` 的关联条目会被静默放行；③ 等待受既有的 wall-clock deadline（`SCAFFOLD_CI_TIMEOUT_SEC`，默认 1800s）约束，重试 sleep 只花剩余预算（把该预算扩到 git 腿、并做到进程树级清理，由承接卡 `T0-CI-DEADLINE-CONTAINMENT` 收口）；④ 决策前再取一次 exact-head/base 快照，base 前移 / retarget / head 前移 / 身份漂移均不合并。`-NoAutoMerge` **只**跳过合并腿，每层照跑照拦。机检：`T37-CIGATE/WORKFLOW-BINDING`（API 侧 job 集漂移那一层由承接卡 `T0-CI-JOBS-DRIFT` 补）。

纯文档 PR 仍产生同名 `verify` 状态，避免 required check 因 `pull_request.paths-ignore` 永久停在 Expected。只有非空改动全部位于 `docs/**`、`specs/**` 或为 Markdown 时才走轻量通道；该通道仍 fail-closed 运行卡片校验、归档索引投影与普通密钥扫描，跳过 Python/Java/Android/Gradle、许可和产品 E2E。源码、脚本、workflow、混合或分类失败一律完整 CI。默认分支纯文档 push 继续由既有 `paths-ignore` 跳过；含代码 push 与手动触发完整执行。

`scaffold-selftest` 不进 PR 必需检查；仅默认分支权威面 push 或手动触发。Windows/Ubuntu 各跑 core、workflow 与三个 seeded 子片（共 10 jobs）；三子片并集仍是完整闸 17，wall time 取最慢片。PR 仍由卡 DoD、verify、R3 守门。

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
| `scripts\_cards.ps1` | 卡片 front-matter 解析的单一真相源（`check-cards.ps1` / `task.ps1` / `archive.ps1` / `triage.ps1` 共用） |
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
| `.github\workflows\ci.yml` | CI `verify` 必需检查（R2） |
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
