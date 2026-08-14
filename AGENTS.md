# AGENTS.md

> **本文件是给「读 AGENTS.md 约定」的工具（Codex / OpenAI 系）的入口指针。**
> 本仓的**唯一权威 agent 指令**是 [`CLAUDE.md`](./CLAUDE.md)——架构、硬边界、关键不变量、经验铁律、工作准则全在那里。
> Codex 作为 R3 第二评审者运行时，请先读 `CLAUDE.md` 的「硬边界 / 关键不变量」与 `docs/QUALITY-RUBRIC.md`（评审 rubric）。

## 最小约定（详见 CLAUDE.md）
- 改动前读 `CLAUDE.md`「权威文档（按序读）」索引；新文档进 `docs/` 或 `specs/`，**不在仓库根新建文件**。
- 评审/验收的确定性闸门：`scripts/verify.ps1`（CI）、`scripts/review.ps1`（Codex R3，按 `docs/QUALITY-RUBRIC.md` 判）、`scripts/selftest.ps1`（元仓自检）。
- 依赖在上下文里的库文档放 `docs/references/`（`*-llms.txt`）；**不在上下文里的东西，对 agent 等于不存在**。
