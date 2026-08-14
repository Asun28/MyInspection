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
- **验收契约（RED 前对齐）** — 把 `dod_command` 复述成**可测形式**、与卡片对齐；**写不出 RED（标准不可测/模糊/范围错）就停，先回去修卡**（改 `dod_command`、必要时回流计划），别私自重解读。标准一旦冻结，后面**别为过闸悄悄放低**（vacuous pass，L19/L20/L47）。若本卡是把已有横切纪律行为化（跨文档/脚本的既有约定升级为强制检查），先 grep 全部权威面、一次性纳入 `allow_paths` + DoD（L97），别等 R3 逐轮外溢。
- **先测 · R2 RED** — 先写**失败**测试并跑 `dod_command` 确认非零退出，才写生产代码；`scripts\task.ps1 -TaskId <id> -Phase red` 把这一拍固化成证据 `.review/<id>.red`（`ship` 校验它 → RED-first 成可强制闸；非 TDD 卡 `-SkipRed` 显式跳过）。**测试落点镜像源码**：改既有代码先扩展该单元现有测试文件、别新建平行测试；新测试文件只给真正的新单元。
- **R2 GREEN** — 写最小实现到通过；**不改冻结物**（`_config.ps1` FrozenPaths，PreToolUse 钩子会拒），守 CLAUDE.md 关键不变量；**写第三方库调用前按其 pinned 版本核验 API**（Context7 取该版本文档／读 `docs/references/*-llms.txt`；R3 #15 会查。工具无关 L26，Context7 是当前默认）。
- **剪枝** — 设计层 `ponytail`（步骤 1.5 · 写测试前审**取向**：这卡/这层抽象/这依赖真要建吗、stdlib·平台原生能否覆盖）；代码层 `/simplify`（步骤 3.5 · GREEN 后清理本卡改动，只做质量清理不找 bug，改完必跑 `dod_command` 确认仍绿）；R4 按 `docs/DEVOPS-WORKFLOW.md §4` mutation-survivor 法删冗余测试、落实卡片 `hygiene`。脚手架本身是有意的重型 harness，别把刻意结构当 bloat 砍掉。
- **安全闸（步骤 4.5 · commit 前 · 建议）** — `/security-review-local` 审工作区未提交改动，末行 `pass` 才继续、`block` 修复后重跑（worktree 卡对**实际 diff** 施同一 rubric，见 L20）；硬编码密钥的**确定性**拦截由 `check-secrets` 提供、已接入 ship。流水线：安全(本地·建议) → commit → check-secrets(强制) → codex PR，正交不重复。
- **R3 前置自检（步骤 4.6 · 卡片首次 ship 前 · 建议层·非闸）** — 首次跑 `-Phase ship` 前，对 worktree 实际 diff 按 `docs/QUALITY-RUBRIC.md` 的评审维度做一次本地自检（工具无关 L26：如已装 `/code-review high` 之类的代码评审工具可直接用；无此类工具时改派一个 fresh-context 子代理对着同一份 rubric 逐条自查），本地吃掉 R3 会挑的问题——省一整轮 push+PR+codex 等待的往返（L97：R3 每轮只报当轮最刺眼一处，完备性问题会逐轮外溢而非一次点全；这一步把该检查挪到本地免费循环里）。**只对首轮建议**——卡已进入真实 R3 反馈循环（block 过一次）后，直接对着 R3 给的具体 reason 改，别在每次重 ship 前重复跑本地自检。
- **ship 前证据审计（步骤 4.7 · 建议层·非闸）** — 长自主/多卡运行按间隔派一个**独立全新上下文** verifier 子代理，只喂【卡片 + 实际 diff + DoD 输出】（不喂工作对话），核对每处「声称完成」是否有本会话工具证据——兜底事实标准见 `docs/HANDOFF.md`；绝不替代 DoD/verify/R3。
- **R3 ship** — `pwsh -NoProfile -File scripts\task.ps1 -TaskId <id> -Phase ship`（**RED 证据闸** → DoD 闸 → **verify 总闸** → 提交 → **范围闸**（allow_paths 越界拦截）→ 许可闸 → **check-secrets** → review.ps1 的 Codex 评审 → push → `gh pr create` → squash 合并）。Codex 裁决 `block` 修复后重 ship，**绝不绕过**；gh 未登录会停在闸门（提示 `gh auth login`）。**无远端/无 Codex 的本地 T0**：`-Phase ship -Local`（跑 DoD 后本地合并，不 push/PR/gh）。codex block 若命中的问题**是本卡这次 diff 本身引入/携带的真实缺陷**（即使维度与卡片原 `diagnosis` 不同），当场修好或把那段有缺陷的改动整段回退/剥离出本卡 diff——**不得留着已知缺陷合并**；只有问题确属既有系统、与本次 diff 无关（纯属评审顺带发现）才开一张新 `T<n>` 卡登记、本卡维持不动（L113——不同于 L101 讲的「首轮评审前须先有卡」，这里是评审**过程中**判断该修/该回退还是该拆）。
- **文档同步 · R5** — 合并后落实卡片 `doc_sync`（`status: -> merged`、CLAUDE.md「当前阶段」、面向用户则 README）。**技术债扫描（不阻塞）**：对照 CLAUDE.md 关键不变量 + `docs/QUALITY-RUBRIC.md §2` 扫本卡改动，「能跑但偏离既定模式/契约」即**追加一行**到 `specs/tech-debt-tracker.md`（status=`open`）——登记非当场修、修走新卡。最后 `pwsh -NoProfile -File scripts\task.ps1 -TaskId <id> -Phase cleanup`。
- **复盘 · R5.5（经验回流 · 闭环最后一拍）** — cleanup 后做一次极简复盘：本卡有没有踩到会复发的坑（工具链坑 / 判断坑 / 「下次该先知道」的东西）？有则 `pwsh -NoProfile -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'` 入账，`blocking` 的当场 `promote`；没有就**显式跳过、别硬凑**。这一拍与开场的「相关经验（Gotchas）检索」对称——**一头取、一头存**，闭合自净化经验回路。门槛/真相源见 `docs/LESSONS.md`「接入 task-loop」，别在此复述晋升规则（免双源漂移）。

## 并行窗口（多卡并行 · 适配全程 AI）
`plan-forge` 会标出 `parallel_window`（依赖已就绪、`allow_paths` 互不重叠的一批卡）。全程 AI 时**并行推进**——这正是「每卡一棵 worktree」的价值兑现处：
- **每卡一个 agent、各占一棵 worktree**：窗口内每张卡派一个子 agent（Agent 工具，必要时 `isolation: worktree`），各自跑完整 R1–R5。`task.ps1 -Phase start` 本就为每卡建独立 `<WorktreeRoot>\<id>`，天然隔离、互不撞文件（前提 `allow_paths` 不重叠，plan-forge 已校验）。
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
