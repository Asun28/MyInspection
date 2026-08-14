---
name: planning-with-files
description: >-
  Manus 式文件化规划 + 零歧义会话交接。跨会话、交接、或和其他 session/agent
  接力的任务，**必须**用 cwd 的三件套 task_plan.md / findings.md / progress.md
  跟踪，并在离场前写好 progress.md 末尾的 HANDOFF 块；同一 session 内按判断用，
  预计 >5 步·>5 次工具调用只是常见「划算」信号（启发，非硬闸）。
  Triggers: "规划/计划/拆解这个", "组织一下这个多步任务", "交接", "handoff",
  "续上次/上个 session"；`/clear` 后恢复也走它。
  权威标准见 docs/HANDOFF.md；校验用 scripts\handoff.ps1。一律 pwsh。
---

# planning-with-files — 文件化规划 + 不能模糊交接

本项目和 AI coding agent **一起做决策、跨会话接力**。这套技能把「计划与交接」从聊天记录搬进**磁盘上的真相文件**，让任一 session 零歧义续上。**跨会话/交接/多 agent 接力时必用**（离场态由 `handoff.ps1` 机检）；纯同会话内按判断——帮上忙就用。

## 三件套（cwd，已 gitignored，hook 按 cwd 读取）
- `task_plan.md` —— 目标 + 有序步骤(勾选) + 当前指针 + 约束/不变量（当前 session 的工作视图）。
- `findings.md` —— 学到的事实 / 决策+理由 / 死路（avoid 下个 session 重踩；append-only 倾向）。
- `progress.md` —— 时间线 + **末尾 HANDOFF 块**（交接唯一权威指针，标准见 `docs/HANDOFF.md`）。

> 真相源仍是项目计划与 `specs/tasks/<id>.md`；三件套是**会话接力带**，不替代卡片/提交/lessons。

## 何时启用
**必用 · 跨会话/交接/多 agent 接力**（离场态由 `handoff.ps1` 机检）：用户说「交接/handoff/续上次/上个 session 到哪了」；`/clear` 后恢复（读 SessionStart 打印的 HANDOFF 块或 `handoff show` 即续）；一张卡施工**跨 session**（三件套跟踪进度，卡片/worktree 仍是权威）。
**按判断 · 同一 session 内**：帮上忙就用、不强制；任务多分支/易迷失/预计 >5 步·>5 次工具调用是常见「划算」信号（启发，非硬闸）。

## 进场（恢复）
1. 看 SessionStart 钩子打印的 HANDOFF 块；没有就 `pwsh -NoProfile -File scripts\handoff.ps1 show`。
2. 照 `NEXT-ACTION` 续；动手前先跑 `VERIFY` 确认当前态与 `LAST-GREEN` 一致。
3. 读 `findings.md` 的「死路」，别重踩。

## 干活（维护）
- 没有三件套就先 `pwsh -NoProfile -File scripts\handoff.ps1 init`（不覆盖已存在文件）。
- 每完成一步：勾掉 `task_plan.md`；新决策/死路写 `findings.md`；`progress.md` 追加时间线一行。
- **长自主 run 之内**的关键进展（里程碑/闸 PASS·FAIL/部分交付物/被用户才能答的问题阻塞）按
  `docs/HANDOFF.md`「长自主运行的异步进度上报」节上报——何时报/怎么报/报什么以该节为唯一权威。
- 非平凡、会复发的坑 → 同时 `pwsh -File scripts\lessons.ps1 add ...` 入账（别只留在 findings，随三件套丢失）。

## 离场（交接）—— 不能模糊
1. 重写 `progress.md` 末尾的 HANDOFF 块，**12 字段全填**（见 `docs/HANDOFF.md`）。
2. 行动字段（`LAST-GREEN` / `NEXT-ACTION` / `VERIFY`）必须**可复制粘贴/具体可执行**；
   `none` 仅用于 `DO-NOT`/`OPEN-QUESTIONS`/`INVARIANTS` 的「精确无」。
3. **`pwsh -NoProfile -File scripts\handoff.ps1 check` 必须 PASS** 才算交接完成——
   缺字段/空值/残留占位/枚举非法/行动字段含模糊措辞（TBD、回头、should work…）即被拒。
4. `LAST-GREEN` 用卡片 `dod_command` 退出码或 `selftest`/`verify` 结果背书，不写「我觉得好了」。

## 边界
- 我**不**是真相源：跨机器/跨人的持久态是卡片 + 提交 + lessons；三件套是本地接力带（gitignored）。
- 我**不**替代 task-loop：卡片施工仍走 `task.ps1`/`review.ps1` 闭环；本技能只保证**跨 session 不丢线、不模糊交接**。
- 我**不**改冻结物，也不放宽任何硬边界；HANDOFF 的 `INVARIANTS` 字段只是**提示在场的不变量**，不是改它们的许可。
