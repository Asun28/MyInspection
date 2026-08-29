# 自净化经验系统（Self-Improving Lessons）

> 目标：让工作流**吸取经验、自我进步**——遇到**同样的问题**时能自主解决，不再痛苦地重新推导。
> 本文件是流程说明；总经验真相源是热账本 `docs/lessons/LEDGER.md` 与冷库 `specs/archive/lessons-archive.md` 的并集；操作器是 `scripts/lessons.ps1`；触发器是 `lessons` skill。

## 三层经验（必须/按需/总）

| 层 | 名称 | 物理位置 | 加载方式 | 容量 |
|---|---|---|---|---|
| **Tier 1** | 必须加载的经验（铁律） | `CLAUDE.md` 「## 经验铁律」 | **每轮自动**入上下文 | **封顶 N 个驻留经验 id**（见 _config.ps1 `LessonsMustCap`，默认 10） |
| **Tier 2** | 按需加载的经验（主题） | `docs/lessons/<topic>.md` | `lessons` skill 按上下文触发 | 不限 |
| **Tier 3** | 项目总经验（热账本 + 冷库） | `docs/lessons/LEDGER.md` + `specs/archive/lessons-archive.md` | `lessons.ps1 search` 统一检索 | append-only |

必须层为何封顶：`CLAUDE.md` 每轮全量进上下文，是稀缺预算；铁律只能放**会复发且会卡死**的极少数。超限即淘汰最不活跃项回按需层。
**计量单位是驻留的经验 id、不是本节的条目数**：一条写着 `[L190][L193]` 的 Markdown bullet 包含 2 个驻留 id、占 2 个封顶单位，
封顶要管的正是后者——否则把几条并进一条 bullet 就能一边「合规」一边让每轮成本继续涨。判定核只有
`scripts/_lessons.ps1` 一处（`lessons.ps1 check` 与心跳探针 `lessons-cap` 共用）；小节标题找不到时两者一律
fail-closed 报错，不把「测不出」读成「未超」。

## 自净化闭环（capture → promote → purify → recall）

四步：**capture**（从会话级原始记录 progress.md「Errors」/ claude-mem / findings.md 精选入账 LEDGER.md Tier3）→
**promote**（达客观门槛晋升 Tier2/Tier1）→ **purify**（去重/合并/淘汰/封顶）→ **recall**（遇到问题先 search，命中即照 rule 做）。
**单向**：会话级 → 总账 → 按需/必须。绝不回灌（避免循环与第 4 个竞争记忆库）。

### 1. CAPTURE（捕获，低摩擦）
- 时机：排除一个非平凡错误后 / `task.ps1 -Phase cleanup` 复盘 / 用户说「记下来、复盘」。
- 命令：`pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..' -Refs '..' [-Cost '浪费40分钟']`
- `add` 自动取当日日期、分配新 `id`、拒绝疑似 token/密钥。
- `-Cost`（可选）：本坑的**犯错成本 / 浪费时间**（如 `'浪费40分钟'`/`'半天返工'`），写进 meta 行末尾，提 Gotcha 信噪比；缺省则不写、不影响任何校验（向后兼容）。`list`/`search` 有则显示。

### 2. PROMOTE（晋升，客观门槛）
- **复发一次就计数**：老坑又踩到时用 `pwsh -File scripts\lessons.ps1 bump <id>`，别新增一条近义经验。
  `recurrence` 是**仓库级元数据**，与任何卡片无关，故 `bump` 一律写**主检出**的账本（经
  normalized common-dir + `git worktree list --porcelain` 解析；submodule 再按 repository-local `core.worktree`
  相对 common-dir 取 checkout，并以 show-toplevel/common-dir 反向验同仓）——在卡片 worktree 或 submodule 里跑也一样，写不进卡片 diff，
  于是不会再被范围闸/R3 #7 当作夹带改动拦掉而丢失。解析得到主检出却没有账本时 fail-closed
  报 `[LSN-PLANE-UNRESOLVED]`，**不**回落到当前检出。`add` 不走这条平面（新经验随卡入库是有意的）。
- `severity=blocking` **或** `recurrence≥2` → 够格进**必须层**（写一行进 CLAUDE.md 经验铁律 + 改 tier=must）。
- 否则进**按需层**（写入 `docs/lessons/<topic>.md` + 改 tier=ondemand）。
- 评估：`pwsh -File scripts\lessons.ps1 promote <id>`。

### 3. PURIFY（提纯，保持精悍）
- `pwsh -File scripts\lessons.ps1 check`：校验 id 唯一、字段完整、`enforced_by` 形态可认（占位符如 `TODO`/`N/A` 一律拒收）、
  必须层**驻留经验 id 数** ≤上限 且与 CLAUDE.md 同步。
- 超限：把 recurrence 最低 / 最久未触发的铁律降回按需层。
- 定期：合并近义条目、淘汰已过时（如某限制随升级 Pro 消失）的经验。
- **归档（热 → 冷）的选择规则 —— 本节是这条规则的权威表述**（`scripts/lessons.ps1` 与 `specs/archive/README.md`
  只指向这里，不各自复述）：`pwsh -File scripts\lessons.ps1 archive -DryRun` 预览候选，只选 **`tier=ledger` 且
  `recurrence=1` 且非当前最大 ID 且未被 `CLAUDE.md`/`CLAUDE.template.md` 引用** 的条目；确认后去掉 `-DryRun`，
  仍由既有 `archive.ps1 -LessonIds` 搬入冷库——**没有第二套搬运器**。它是机械预筛，不替代人工复核、不定时运行。
  - **预览与实跑同口径**：`-DryRun` 透传给同一个搬运器，故搬运器自己的拒绝在预览里就会出现、退出码一致，
    不会「预览报绿、实跑搬到一半才失败」。经**本入口**实际可达的拒绝只有两类：**非规范别名 id**（带前导零的
    写法；具体例子写在 `specs/archive/README.md`——本文件受闸 16 悬空引用检查，不能内联那种形态的 id）与
    **两侧并存但内容不一致**；另两类（拒最高 ID / 未知 id）在这里结构上不可达——最高 ID 已被选择器先行排除，
    候选又恒取自热账本、搬运器必查得到。
  - **元数据读不出来的条目一律留热**并报 `[LSN-META-INVALID]`，此时预览与实跑**都非零退出**（与 `check` 同口径）。
    但实跑的非零退出**不代表零搬运**：合法候选照样已经搬进冷库，退出码报告的是「账本里还有读不出的条目」，
    别把 exit 1 读成回滚；修好坏条目后重跑幂等。
  - `check` 的正文不变量也适用于归档：缺 `rule`，或 `severity=blocking` 却缺 `enforced_by` 的条目报
    `[LSN-ENTRY-INVALID]`、留热并令命令非零；元数据合法不等于整个条目有效。
  - 源 `docs/lessons/LEDGER.md` 缺席或不可读时报 `[LSN-LEDGER-SOURCE-MISSING]` 并零写入；错误路径不能被解释成空账本。
  - **定义「谁被引用」的常驻 `CLAUDE.md` 缺席时拒绝归档**：`-RepoRoot` 指向一棵有 LEDGER 却没有 `CLAUDE.md`
    的树时，`archive` 以 `[LSN-RESIDENT-SOURCE-MISSING]` 非零退出且零搬运——「读不到受保护集合」不等于「没有
    条目被引用」，后者会让整批 `tier=ledger` 条目静默搬冷。（`CLAUDE.template.md` 只在元仓存在，**单独**缺席
    属正常形态、不触发。）`check` 不受此限（只诊断、不移动数据）。
  - **「被引用」按裸写形态判**：`见 L26 之理` 与 `[L26]` 同样算引用（判定式与 `selftest.ps1` 闸 16 同源）。
  - **范围简写只保护两端点**：`L229–L232` 只保护 L229 与 L232，中间的 L230、L231 仍会进候选集——要保住某条
    经验就把 id 逐个写出来。

### 4. RECALL（检索，遇到问题先查）
- `pwsh -File scripts\lessons.ps1 search <关键词>` 查热账本 + 冷库 + 按需层；冷命中带 `[archived]`，命中即照 `rule` 做。
- 必须层每轮已在上下文，无需检索。
- `lessons` skill 在「复发/经验/复盘/踩过的坑」等语境自动触发。
- 冷库是只读历史面：对冷项执行 `bump/promote` 会 fail-closed，并给出
  `archive.ps1 -LessonsOnly -RestoreLessonIds <id>`。这是唯一受支持的反向例外：脚本先把完整块无损移回热账本、
  再从冷库移除；任一步失败都至少保留一份，冷热并存态可重跑自愈。不要手工复制或删除冷库正文。

## 与既有记忆面的边界（不重复）
其余记忆面各管各的、不与本系统重叠：`claude-mem`（若装）走自动 episodic 观察 + `mem-search` 召回；
`progress.md`/`task_plan.md`/`findings.md` 是会话/任务级 working memory（hook 注入、gitignored，不入库）。
lessons 系统只装**精选、跨任务、入库的工程结论**（`docs/`，公开安全）。

## 接入 task-loop（每张卡的复盘点）
`task.ps1 -Phase cleanup` 的 R5 文档同步之后，增加一步「复盘」：本卡若踩过非平凡坑，`lessons.ps1 add` 入账；
blocking 的当场 `promote`。这样每跑完一张任务卡，经验系统就**自动长一点、提纯一点**。

> 模板自带一批**工具链通用**经验（PowerShell/gh/git/worktree/codex；见 LEDGER.md 与 powershell-and-gh.md）——
> 这些坑与项目领域无关，新项目直接受用。项目特定经验随开发逐步累积。
