# 自净化经验系统（Self-Improving Lessons）

> 目标：让工作流**吸取经验、自我进步**——遇到**同样的问题**时能自主解决，不再痛苦地重新推导。
> 本文件是流程说明；总经验真相源是热账本 `docs/lessons/LEDGER.md` 与冷库 `specs/archive/lessons-archive.md` 的并集；操作器是 `scripts/lessons.ps1`；触发器是 `lessons` skill。

## 三层经验（必须/按需/总）

| 层 | 名称 | 物理位置 | 加载方式 | 容量 |
|---|---|---|---|---|
| **Tier 1** | 必须加载的经验（铁律） | `CLAUDE.md` 「## 经验铁律」 | **每轮自动**入上下文 | **封顶 N 条**（见 _config.ps1 `LessonsMustCap`，默认 10） |
| **Tier 2** | 按需加载的经验（主题） | `docs/lessons/<topic>.md` | `lessons` skill 按上下文触发 | 不限 |
| **Tier 3** | 项目总经验（热账本 + 冷库） | `docs/lessons/LEDGER.md` + `specs/archive/lessons-archive.md` | `lessons.ps1 search` 统一检索 | append-only |

必须层为何封顶：`CLAUDE.md` 每轮全量进上下文，是稀缺预算；铁律只能放**会复发且会卡死**的极少数。超限即淘汰最不活跃项回按需层。

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
- `severity=blocking` **或** `recurrence≥2` → 够格进**必须层**（写一行进 CLAUDE.md 经验铁律 + 改 tier=must）。
- 否则进**按需层**（写入 `docs/lessons/<topic>.md` + 改 tier=ondemand）。
- 评估：`pwsh -File scripts\lessons.ps1 promote <id>`。

### 3. PURIFY（提纯，保持精悍）
- `pwsh -File scripts\lessons.ps1 check`：校验 id 唯一、字段完整、必须层 ≤上限 且与 CLAUDE.md 同步。
- 超限：把 recurrence 最低 / 最久未触发的铁律降回按需层。
- 定期：合并近义条目、淘汰已过时（如某限制随升级 Pro 消失）的经验。
- **归档（热 → 冷）的选择规则 —— 本节是这条规则的权威表述**（`scripts/lessons.ps1` 与 `specs/archive/README.md`
  只指向这里，不各自复述）：`pwsh -File scripts\lessons.ps1 archive -DryRun` 预览候选，只选 **`tier=ledger` 且
  `recurrence=1` 且非当前最大 ID 且未被 `CLAUDE.md`/`CLAUDE.template.md` 引用** 的条目；确认后去掉 `-DryRun`，
  仍由既有 `archive.ps1 -LessonIds` 搬入冷库——**没有第二套搬运器**。它是机械预筛，不替代人工复核、不定时运行。
  - **预览与实跑同口径**：`-DryRun` 透传给同一个搬运器，故「别名 id / 最高 ID / 未知 id / 两侧内容不一致」四类
    拒绝在预览里就会出现、退出码一致，不会「预览报绿、实跑搬到一半才失败」。
  - **元数据读不出来的条目一律留热**并报 `[LSN-META-INVALID]`，此时预览与实跑**都非零退出**（与 `check` 同口径）。
  - **「被引用」按裸写形态判**：`见 L26 之理` 与 `[L26]` 同样算引用（判定式与 `selftest.ps1` 闸 16 同源）。
  - **范围简写只保护两端点**：`L229–L232` 只保护 L229 与 L232，中间的 L230、L231 仍会进候选集——要保住某条
    经验就把 id 逐个写出来。

### 4. RECALL（检索，遇到问题先查）
- `pwsh -File scripts\lessons.ps1 search <关键词>` 查热账本 + 冷库 + 按需层；冷命中带 `[archived]`，命中即照 `rule` 做。
- 必须层每轮已在上下文，无需检索。
- `lessons` skill 在「复发/经验/复盘/踩过的坑」等语境自动触发。
- 冷库是只读历史面：对冷项执行 `bump/promote` 会 fail-closed，并提示先把完整条目移回热账本。

## 与既有记忆面的边界（不重复）
其余记忆面各管各的、不与本系统重叠：`claude-mem`（若装）走自动 episodic 观察 + `mem-search` 召回；
`progress.md`/`task_plan.md`/`findings.md` 是会话/任务级 working memory（hook 注入、gitignored，不入库）。
lessons 系统只装**精选、跨任务、入库的工程结论**（`docs/`，公开安全）。

## 接入 task-loop（每张卡的复盘点）
`task.ps1 -Phase cleanup` 的 R5 文档同步之后，增加一步「复盘」：本卡若踩过非平凡坑，`lessons.ps1 add` 入账；
blocking 的当场 `promote`。这样每跑完一张任务卡，经验系统就**自动长一点、提纯一点**。

> 模板自带一批**工具链通用**经验（PowerShell/gh/git/worktree/codex；见 LEDGER.md 与 powershell-and-gh.md）——
> 这些坑与项目领域无关，新项目直接受用。项目特定经验随开发逐步累积。
