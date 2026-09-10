---
name: task-loop
description: >-
  Use when implementing or shipping a task card from specs/tasks/ in this repo
  (any specs/tasks/<id>.md, e.g. T0-SCAFFOLD, T1-FOO). Triggers on
  "do/implement/ship <TaskId>", "start the next card", "work on <TaskId>".
  Drives the worktree -> TDD -> DoD -> Codex-review -> PR -> merge -> doc-sync
  loop by RUNNING scripts\task.ps1 and scripts\review.ps1.
  Do NOT use for ad-hoc edits outside a task card, or for editing the plan itself.
---

# task-loop — 单任务卡闭环驱动（R1–R5）

我是**驱动器**，不是真相源，也不重复实现脚本。权威在：项目计划/真相源、
`specs/tasks/<id>.md` 卡片字段、`docs/DEVOPS-WORKFLOW.md`。我只负责**按顺序触发**
并守住四件容易忘的纪律：**先测、剪枝、文档同步、复盘（经验回流）**。一律用 `pwsh`（非 bash）。

## 前置
- **MyInspection 项目配置**：`ReviewGate='required'`。下文的上游默认意见模式仅说明可配置行为；本项目任一 `block`、超时或不可用裁决都阻断合并，只有当前候选的 Codex `pass` 与 `required` CI 成功才可合并。评审次数遵循本地 `ReviewRoundCap` 与明确的用户授权。
- 若用户给的是 TaskId（如 `T1-FOO`），先 `Read specs/tasks/<id>.md`，再读它引用的计划章节。
- 遵守卡片 `allow_paths` / `forbid`；不发明字段。**所有编辑都在 `<WorktreeRoot>\<id>` 工作树内**，不动主检出。
  （WorktreeRoot 见 scripts/_config.ps1；留空则按 OS 自动取默认：Windows `<系统盘>\wt`（如 `C:\wt`）/ macOS·Linux `~/.wt`。）

## 相位与三纪律（退出码串联相位，非逐拍照抄）
`task.ps1` 的退出码串联相位、机检 RED 证据 / DoD / 范围 / 许可 / 防泄露——**脚本已强制的，我不复述**。我只守四件容易忘的纪律（**先测、剪枝、文档同步、复盘**）并按序触发脚本。

> **相位命令一律在主检出跑（L86）**：`start` / `red` / `ship` / `cleanup` 四个相位命令**必须**用**主检出**那份
> `scripts\task.ps1`。`cd` 进 worktree 只为**编辑文件**；在 worktree 里跑 `scripts\task.ps1`（相对路径会解析到 worktree
> 自带的那份）会被 fail-closed 守卫拒（哨兵 `L86-WT`）——因为 `$RepoRoot` 由脚本自身位置派生，届时 `-Local` 的合并
> 会把分支并进它自己、假报成功而 base 从未前进。**传 `-Base` 也救不了**。下面每条相位命令都从主检出根目录执行。

- **R1 start** — 在**主检出**跑 `pwsh -NoProfile -File scripts\task.ps1 -TaskId <id> -Phase start`（建 `<WorktreeRoot>\<id>`、引导 uv/.venv+npm、打印 TDD/DoD 提醒）。之后可 `cd` 进该 worktree **编辑文件**，但相位命令仍回主检出跑。
- **验收契约（RED 前对齐）** — 把 `dod_command` 复述成**可测形式**、与卡片对齐；**写不出 RED（标准不可测/模糊/范围错）就停，先回去修卡**（改 `dod_command`、必要时回流计划），别私自重解读。标准一旦冻结，后面**别为过闸悄悄放低**（vacuous pass，L19/L20/L47）。若本卡是把已有横切纪律行为化（跨文档/脚本的既有约定升级为强制检查），先 grep 全部权威面、一次性纳入 `allow_paths` + DoD（L97），别等 R3 逐轮外溢；**这一扫必须记进卡的 `sweep:` 字段**（跑的那条 grep + 它找到的教学面）——照本条办事的卡多半会超过 5 条 `allow_paths`，超过即 `check-cards` 强制该字段（哨兵 `[CARD-SWEEP]`，T94），不填则开卡当场被拒。
- **先测 · R2 RED** — 先写**失败**测试并跑 `dod_command` 确认非零退出，才写生产代码；`scripts\task.ps1 -TaskId <id> -Phase red` 固化这一拍（GREEN 下必抛、危险 dod 先被 check-cards 拦；T68 起为**可选辅助**——ship 不再校验 RED 证据，合并闸=确定性闸）。**测试落点镜像源码**：改既有代码先扩展该单元现有测试文件、别新建平行测试；新测试文件只给真正的新单元。
- **R2 GREEN** — 写最小实现到通过；**不改冻结物**（`_config.ps1` FrozenPaths，PreToolUse 钩子会拒），守 CLAUDE.md 关键不变量；**写第三方库调用前按其 pinned 版本核验 API**（Context7 取该版本文档／读 `docs/references/*-llms.txt`；R3 #15 会查。工具无关 L26，Context7 是当前默认）。
- **剪枝** — 设计层 `ponytail`（步骤 1.5 · 写测试前审**取向**：这卡/这层抽象/这依赖真要建吗、stdlib·平台原生能否覆盖）；代码层 `/simplify`（步骤 3.5 · GREEN 后清理本卡改动，只做质量清理不找 bug，改完必跑 `dod_command` 确认仍绿）；R4 按 `docs/DEVOPS-WORKFLOW.md §4` mutation-survivor 法删冗余测试、落实卡片 `hygiene`。脚手架本身是有意的重型 harness，别把刻意结构当 bloat 砍掉。
- **安全闸（步骤 4.5 · commit 前 · 建议）** — `/security-review-local` 审工作区未提交改动，末行 `pass` 才继续、`block` 修复后重跑（worktree 卡对**实际 diff** 施同一 rubric，见 L20）；硬编码密钥的**确定性**拦截由 `check-secrets` 提供、已接入 ship。流水线：安全(本地·建议) → commit → check-secrets(强制) → codex PR，正交不重复。
- **Pre-ship evidence audit (step 4.7 · advisory · not a gate)** — on long autonomous / multi-card runs, at intervals, dispatch one verifier subagent in a **fresh, isolated context**, fed only [the card + the actual diff + DoD output] and never the working conversation, to check that every "done" claim has tool evidence from this session. Fallback standard of truth: `docs/HANDOFF.md`. Never a substitute for DoD/verify/R3.
  **Why this stays while generic verification scaffolding goes** (Opus 5 removes its own need for "verify your work" / "use a subagent to double-check" instructions — see `docs/references/claude-opus-5-prompting-llms.txt`): that rule targets **same-context** self-checks, which the model now does natively and better. This is not one. The **isolation is the mechanism** — a verifier that never saw the working conversation cannot inherit the assumption that produced the defect. Measured returns in this repo: `archive.ps1` (3 real defects), `selftest.ps1` F2, `task.ps1` F1, T74's `$RepoRoot` clobber, TD97's relocation audit. **Do not generalize it into a same-context re-check, and do not run it per card on short work.**
- **内环节奏** — rounds = scoped `-Only`（GATE-MAP 行，永远只是诊断）; ship 的**验收按卡 tier**（ADR 0016；`check-cards` 每次都打印 `[CARD-TIER] id=<id> tier=<S|1|0>`，`-Phase start` 与 `-Phase ship` 都看得到）：**S** = one async full proof per frozen candidate（`selftest.ps1 -Parallel`，一如既往）; **1** = `pwsh -NoProfile -File scripts\selftest.ps1 -TaskId <id>`（按 GATE-MAP 路由 + 底座闸，结论 `[SELFTEST-TIER-PASS]`；有改动路径没被任何行覆盖就自动升级为全量）; **0**（纯文档/卡/账本）= `ci.yml`，`-TaskId` 建议不强制。multi-session lanes: see `docs/DEVOPS-WORKFLOW.md` §3.2 inner-loop acceptance.
- **ship（T68：合并闸=确定性闸）** — `pwsh -NoProfile -File scripts\task.ps1 -TaskId <id> -Phase ship`（DoD 闸 → **verify 总闸** → 提交 → **范围闸**（allow_paths 越界拦截）→ **预算闸**（T233/TD235：diff 超出 **base 卡**声明的 `budget:` 即 `[CARD-BUDGET-OVER]` 阻断——出路是拆卡，或把预算抬到 base 分支上、作为独立提交、理由写进那个提交；分支内抬高无效；没声明 `budget:` 的卡照旧通过）→ 许可闸 → **check-secrets** → push → `gh pr create` → CI 检查闸（TD134：auto 与 `-NoAutoMerge` **两条路径都过**——manual 模式仅在全部期望检查 success 后才宣告 PR ready，人工合并按 DEVOPS-WORKFLOW 最后手段块含 pre-merge CI check。期望检查 = `ci.yml` **自陈的单枚 fan-in context `required`**（T86，见 `docs/DEVOPS-WORKFLOW.md` §3.0；`ci.yml` 缺失或无 `required` job 即 fail-closed。ADR 0007：元层 scaffold-selftest 不在 PR 上跑，**故 ship 前本地全量 selftest 是强制的、CI 不替你跑**））→ squash 合并）。**R3 默认意见模式**（`ReviewGate` 留空）：ship **不因评审阻断**；但卡自己声明了 `review_gate:` 就在合并前跑一遍、只报告与入账（T242/TD248——本仓每张卡都声明它，故实际每次 ship 都会跑），没声明才跳过；要第二意见随时 `review.ps1`。**T301**：`ReviewIntensityByTier` 按卡**算出的** tier 选档（adversarial / advisory / skip，只降不升，哨兵 `[R3-INTENSITY]`）——tier-0 的 skip 写的是 `routed_skip` 裁决，不是「评审过了」。**One exception since T277 (ADR 0016 item 4)**: the verdict carries two axes, and on a **Tier-S** card an axis-`spec` block (the reviewer saying the diff does not do what the card closed) stops the ship with `[R3-SPEC-BLOCK]` before the merge — fix the diff, or fix the card on the base branch, then rerun the same ship. A `standards` finding never blocks at any tier, and neither does a reviewer timeout or an unreadable verdict: those carry no axes at all.`ReviewGate='required'` 恢复旧强制闸——该态下 Codex 裁决 `block` 修复后重 ship，**绝不绕过**；gh 未登录会停在闸门（提示 `gh auth login`）。**无远端的本地 T0**：`-Phase ship -Local`（DoD + 本仓 `ReviewGate='required'` 的 R3；缺少可用后端或评审未通过均 fail-closed，通过后本地合并，不 push/PR/gh）。评审意见/裁决命中的问题**是本卡这次 diff 本身引入/携带的真实缺陷**（即使维度与卡片原 `diagnosis` 不同），当场修好或把那段有缺陷的改动整段回退/剥离出本卡 diff——**不得留着已知缺陷合并**；只有问题确属既有系统、与本次 diff 无关（纯属评审顺带发现）才开一张新 `T<n>` 卡登记、本卡维持不动（L113——不同于 L101 讲的「首轮评审前须先有卡」，这里是评审**过程中**判断该修/该回退还是该拆）。
- **文档同步 · R5** — 合并后落实卡片 `doc_sync`（`status: -> merged`、CLAUDE.md「当前阶段」、面向用户则 README）。**技术债扫描（不阻塞）**：对照 CLAUDE.md 关键不变量 + `docs/QUALITY-RUBRIC.md §2` 扫本卡改动，「能跑但偏离既定模式/契约」即**追加一行**到 `specs/tech-debt-tracker.md`（status=`open`）——登记非当场修、修走新卡。**Where an advisory R3 finding goes (T277 / ADR 0016 item 4)**: a post-merge finding becomes a tech-debt row only once it is `re-measured` on the merged tree and still holds there as a code defect — one that no longer reproduces is closed in this card's R5 note, never carried as a row nobody can reproduce. A finding about **card prose** is fixed in that card's own R5 commit, in the same commit that flips its `status`, and never becomes a card. 最后 `pwsh -NoProfile -File scripts\task.ps1 -TaskId <id> -Phase cleanup`。
- **复盘 · R5.5（经验回流 · 闭环最后一拍）** — cleanup 后做一次极简复盘：本卡有没有踩到会复发的坑（工具链坑 / 判断坑 / 「下次该先知道」的东西）？有则 `pwsh -NoProfile -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'` 入账，`blocking` 的当场 `promote`；没有就**显式跳过、别硬凑**。这一拍与开场的「相关经验（Gotchas）检索」对称——**一头取、一头存**，闭合自净化经验回路。门槛/真相源见 `docs/LESSONS.md`「接入 task-loop」，别在此复述晋升规则（免双源漂移）。

## 并行窗口（多卡并行 · 适配全程 AI）
`decompose-cards` 会标出 `parallel_window`（依赖已就绪、`allow_paths` 互不重叠的一批卡）——它是投影任务卡的唯一所有者，`plan-forge` 只审计计划、停在裁决（TD180）。全程 AI 时**并行推进**——这正是「每卡一棵 worktree」的价值兑现处：
- **每卡一个 agent、各占一棵 worktree**：窗口内每张卡派一个子 agent（Agent 工具，必要时 `isolation: worktree`），各自跑完整 R1–R5。`task.ps1 -Phase start` 本就为每卡建独立 `<WorktreeRoot>\<id>`，天然隔离、互不撞文件（前提 `allow_paths` 不重叠，decompose-cards 的 4 角度卡审已校验）。
- **顺序铁律**：冻结点卡（契约/schema）必须**先单独跑完并合并**，其依赖卡才进并行窗口；`depends_on` 未满足的卡不入窗口。
- **合并不冲突**：各 worktree 独立 ship/合并；因 `allow_paths` 不重叠，合并面不撞。任一卡 Codex `block` 只挡它自己，不连累同窗其他卡。
- 并行只是把单卡闭环**复制 N 份**，**不降低任何一道闸**（TDD/ponytail/安全/Codex/CI 照跑）。

## 边界
- 我**不**当评审者：R3 的评审是 `review.ps1` 调用的 Codex（第二模型）。不另起 Claude 评审。
- 我**不**复制计划/卡片正文；只链接与触发。脚本的退出码才是硬闸门。
- worktree 卡跑安全闸别盲信 `/security-review-local` 的 verbatim 调用：其内嵌 `git diff HEAD` 在主检出求值，
  看不到 worktree 改动 → 改为对 worktree 实际 diff 施同一 rubric 再判（见 docs/lessons L20）。

## 相关经验（Gotchas · 真相源 = `docs/lessons/LEDGER.md`，此处只指针）
本闭环踩过的坑已沉淀为经验；动手前用 `pwsh -File scripts\lessons.ps1 search <关键词>` 调取，尤其这几条最常复发：
- **ship / 合并**：L13（worktree 内 `gh pr merge` **不加** `--delete-branch`）· L15（PR 开好后**单次** `review.ps1 -PostStatus`，别跑两遍 codex）· L21（codex 配额耗尽 ≠ 裁决 block，待重置后**重跑** ship，勿手动合并）· L23（评审分支名**避免斜杠**，用 `T-id` / 连字符）。
- **评审边界**：L18（`review.ps1` 卡感知 `allow_paths`；卡自身 meta 改动走 main、勿入功能分支 PR）· L20（worktree 安全闸的 `git diff` 在主检出求值 → 对 worktree 实际 diff 施 rubric）。
- **工具链**：L1（只读诊断与写操作**分批**）· L4（`codex exec` 前置 EOF stdin）。
