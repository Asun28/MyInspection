---
name: triage
description: >-
  The scaffold's heartbeat loop. Use to run a cadence sweep for actionable work across the
  repo's subsystems instead of manually checking each one — then route findings into the
  existing delivery chains. Triggers on: "triage", "心跳", "扫一遍待办", "what needs doing",
  "what's pending", "分诊", "/loop triage", at the start of a work session, or after a
  long break. Drives RUNNING scripts\triage.ps1 (read-only discovery) and then acting via
  task-loop / lessons / tech-debt. Do NOT use for a single known task (that's task-loop) or
  for capturing a lesson (that's the lessons skill).
---

# triage — 脚手架的心跳（cadence 发现 → 分诊 → 既有交付链）

动机（addy osmani《Loop Engineering》组件①「the heartbeat」+ Anthropic《Recursive Self-Improvement》
「把 perspiration 自动化、人保留 direction-setting」，见 `docs/LOOP-ENGINEERING.md`）：脚手架其余部分都是
**按需**触发（`task start/ship`）；本 skill 补上**定期发现待办**的回路，让我（或下个 session）只需读收件箱、
决定**做哪件**，而不必手动巡检 lessons / tech-debt / cards / handoff。一律用 `pwsh`（非 bash）。

## 回路（DISCOVER → TRIAGE → ACT，单向喂既有链）
1. **DISCOVER（只读扫描，不行动）**：
   `pwsh -File scripts\triage.ps1 scan`
   纯文件解析、不打网络/不调 gh；产出 `_local/triage-inbox.md`（gitignored）。10 探针：
   `lessons-promote`（经验该晋升）· `tech-debt-open`（债该还）· `cards-active`（卡在飞）·
   `handoff-open`（交接未收口）· `lessons-cap`（必须层**驻留经验 id 数**达/超封顶，该做减法；标题找不到即 fail-closed 报）·
   `harness-refresh`（judgment 经验累积达门槛，该双向复审：删旧闸 + 搜更优工具/方法纳新）·
   `effectiveness`（效果账本：各 ship 闸真实拦截数——喂 HARNESS-REVIEW 做拦截计数减法）·
   `worktree-orphan`（卡已 merged 却没拆的残留 worktree——cleanup 漏跑/半合并遗留）·
   `lessons-demote`（必须层某条已被确定性守卫覆盖，该降层）·
   `delivery-blocked`（在飞卡坐在 R3 block 裁决上没人接——交付停摆，severity=blocking）。退出码恒 0（reporter，非闸门）。
2. **TRIAGE（读收件箱，定路由）**：按 severity 读，据项的性质分两路——心跳只发现，方向仍归人/agent 判断（RSI）。
   - **判断类/定方向**（`tech-debt-open` 转不转卡、`harness-refresh`/`lessons-cap` 的减法、`cards-active` 续不续）：一次挑**一件**、逐条由人/agent 确认，别无脑平推（会牺牲方向判断）。
   - **可逆机械项**（`worktree-orphan` 清残留及同类）：确认可逆后可并行执行。`lessons-promote` 候选不超过 5 条时逐 id 走 lessons 动作；超过 5 条时只建一项 HARNESS-REVIEW 批量复审，避免卡片风暴。
3. **ACT（喂既有交付链，别另起炉灶）**：
   - `lessons-promote` → 候选 `<=5` 时逐 id 用 `lessons` skill：`pwsh -File scripts\lessons.ps1 promote <id>`；候选 `>5` 时只开一个 `docs\HARNESS-REVIEW.md` 批量项。
   - `tech-debt-open`  → 转卡：写 `specs\tasks\<id>.md` 走 `task-loop` 偿还，或记 `docs\adr\` 接受；改 tracker status。
   - `cards-active`    → `task-loop` skill：续 `task.ps1 ship` 或补 `review.ps1`。
   - `handoff-open`    → `planning-with-files` skill：填 `progress.md` 末尾 HANDOFF 块 + `handoff.ps1 check`。
   - `lessons-cap`     → `docs\HARNESS-REVIEW.md` 仪式：淘汰最不活跃铁律回按需层。
   - `harness-refresh` → `docs\HARNESS-REVIEW.md` 仪式：逐条复审 judgment 经验（方向品味是否提升）+ 评估更优工具/方法替换（双向：删旧闸 + 纳新）。
   - `effectiveness`   → `docs\HARNESS-REVIEW.md` 仪式：据各 ship 闸拦截计数（0/低拦截）做减法决策。
   - `worktree-orphan` → `task-loop`：确认无未推改动后 `pwsh -File scripts\task.ps1 -TaskId <id> -Phase cleanup` 拆残留 worktree。
   - `lessons-demote`  → `docs\HARNESS-REVIEW.md` 仪式：确认守卫真覆盖后，从 CLAUDE.md 铁律小节摘掉该条 + LEDGER 改 `tier: ondemand`，再 `lessons.ps1 check`。
   - `delivery-blocked`→ `task-loop`：读该卡 `.review/*.json` 的 reasons，逐条修或拆卡后重 ship；理由若属既有系统而非本次 diff，另开卡（L113），别让 block 悬着。


## 节律（cadence）
- 手动：每个工作 session 开场跑一次 `triage.ps1 scan`，把收件箱当当天的 work-list。
- 半自动：Claude Code 里 `/loop` 可定时重跑本 skill（如每日一次）。**仅发现**会自动化；**定方向的 act 仍由人/agent 逐条确认**、可逆机械项可并行委派
  （见 `docs/LOOP-ENGINEERING.md` 的 comprehension-debt 警告：别盲信回路产出）。

## 边界（不造新东西）
- 心跳**不写**仓内被跟踪文件、**不做** git/gh 写操作——只读发现。所有改动走既有链的闸门（worktree/TDD/Codex/CI）。
- 收件箱是**运行时态**（`_local/`，每次 scan 覆盖），不是真相源；真相源仍是 LEDGER / tech-debt-tracker / specs/tasks。
- **判断/定方向类**一次挑一件做完再扫；**可逆机械项**可并行，但 `lessons-promote` 仍遵守 `<=5` 逐 id、`>5` 单个 HARNESS-REVIEW 批次的阈值。
