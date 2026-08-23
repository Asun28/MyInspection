---
name: lessons
description: >-
  Use when hitting a recurring problem, an error that feels familiar, or before a tricky
  PowerShell / gh / git / worktree / GitHub-ruleset operation in this repo — to recall prior
  lessons instead of re-deriving them. Triggers on: "we hit this before", "recurring", "again",
  "经验", "复盘", "踩过的坑", "上次怎么解决的", "lesson", or after resolving any non-trivial error.
  Also the self-purification (自净化) entry point: capture a new lesson, promote/curate tiers.
  Do NOT use for normal feature coding (that's the task-loop skill).
---

# lessons — 自净化经验系统（三层 · 检索/捕获/晋升/提纯）

我让工作流**吸取经验、自我进步**：遇到同样问题先查经验自主解决，新坑解决后入账，定期提纯。
权威真相源是 `docs/lessons/LEDGER.md`；流程细节见 `docs/LESSONS.md`。一律用 `pwsh`（非 bash）。

## 三层（按加载方式）
- **必须层（Tier1）**：`CLAUDE.md` 的「经验铁律」小节，每轮自动在上下文，封顶 N 个**驻留经验 id**（见 `_config.ps1` `LessonsMustCap`，默认 10）。
  计量的是 id 不是条目：一条写着 `[L190][L193]` 的 bullet 算 2 个，把几条并进一条 bullet 不会让它变便宜。
- **按需层（Tier2）**：`docs/lessons/<topic>.md`（如 `powershell-and-gh.md`），相关时才读。
- **总账（Tier3）**：`docs/lessons/LEDGER.md`，全量、append-only、唯一真相源。

## 何时做什么
1. **RECALL（遇到问题先查）**：`pwsh -File scripts\lessons.ps1 search <关键词>`，命中就照 `rule` 做，别重导。
   涉及 PowerShell/gh 主题时连带读 `docs/lessons/powershell-and-gh.md`。
2. **CAPTURE（解决后入账，低摩擦）**：
   `pwsh -File scripts\lessons.ps1 add -Tags '...' -Severity blocking|major|minor -Symptom '...' -RootCause '...' -Rule '...' -Refs '...'`
   触发点：① 排除一个非平凡错误后；② `task.ps1 -Phase cleanup` 后复盘本卡踩的坑；③ 用户说「复盘/记下来」。
   也可把 `progress.md` 的「Errors Encountered」里**会复发**的条目提炼入账（会话级→精选层，单向）。
   **两类经验**（`-Kind`）：`pitfall`（默认，工具链/方法的坑→可升级机械守卫）vs `judgment`（方向/决策失手→喂
   `docs/HARNESS-REVIEW.md` 复审）。选错方向、本可有更优下一步时，用 `-Kind judgment` 记下来（见 `docs/LOOP-ENGINEERING.md`）。
3. **PROMOTE（晋升）**：同一条经验再次撞上时先 `pwsh -File scripts\lessons.ps1 bump <id>`（复发计数 +1，跨过 2 即提示晋升），再 `pwsh -File scripts\lessons.ps1 promote <id>`。客观门槛：`severity=blocking` 或 `recurrence≥2` → 够格进必须层；
   否则进按需层。晋升＝把一行结论写进 CLAUDE.md 铁律（或对应 `docs/lessons/<topic>.md`）并改该条 `tier`。
4. **PURIFY（提纯/封顶）**：`pwsh -File scripts\lessons.ps1 check`。校验 id 唯一、字段完整、`enforced_by` 形态可认
   （`TODO`/`N/A`/`待补` 这类占位符会被当场拒收——它们冒充了一次守卫声明），以及必须层**驻留经验 id 数**≤上限。
   超限时把**最不活跃**（recurrence 最低、最久未触发）的一条降回按需层，保持必须层精悍。

## 边界（不造第 4 个记忆库）
- `claude-mem`（若装）＝自动 episodic（原始、不入库）；`progress.md`/`task_plan.md`＝会话级 working memory（gitignored）；`findings.md`＝研究草稿。
- 本系统＝**精选、入库、跨任务**的工程结论。方向单向：会话级 ─捕获→ 总账 ─晋升→ 按需/必须。绝不回灌。
- 只记工程结论，**禁记** token/密钥/组织名/客户名/事故隐私（公开仓可见；`add` 会拒绝疑似凭据）。
