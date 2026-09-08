---
id: T0-REVIEW-LOW-RISK
title: 横切 R3 低风险文档建议模式与本地可信评审入口
depends_on: []
status: in-progress
branch: T0-REVIEW-LOW-RISK
worktree: C:\wt\T0-REVIEW-LOW-RISK
allow_paths:
  - scripts/_review-policy.ps1
  - scripts/review.ps1
  - scripts/task.ps1
  - scripts/selftest.ps1
  - specs/verdict.schema.json
  - docs/QUALITY-RUBRIC.md
  - docs/DEVOPS-WORKFLOW.md
  - specs/README.md
  - specs/tasks/_TEMPLATE.md
  - specs/tasks/T0-REVIEW-LOW-RISK.md
  - .claude/skills/task-loop/SKILL.md
  - CLAUDE.md
forbid:
  - 修改冻结物、FrozenPaths 清单、运行时代码、安全/许可/CI 闸
  - 降低超时或轮次/体量预算、把真实 block 改写为 pass
  - 修改 scripts/_cards.ps1 或 scripts/_validation.ps1
non_goals:
  - 自动迁移已有任务卡或为源码改动开放 advisory
  - 新建评审或测试 runner 框架
  - 自动跳过第二模型评审
acceptance:
  - "A1 仅基线已提交卡的精确 review_gate: advisory 可选择建议模式；缺省、重复、未知或仅工作树自声明均 blocking"
  - "A2 以 pinned base...head 的真实路径决定适用范围；普通 docs/specs Markdown 与本卡仅 status 变化可建议，安全/流程/交付/关键合同/冻结/源码/未知混合路径及关键源路径 rename 继续 blocking"
  - "A3 有效 advisory block 保留真实 JSON verdict/reasons 和明确 findings，gate 返回零，不消费阻断 round"
  - "A4 超时、空输出、畸形 JSON/字段、非零 reviewer、stale SHA 与裁决落盘失败均不得被 advisory 放行"
  - "A5 旧卡语义保持 blocking，本地 ship 运行主检出 review.ps1，与远端来源一致"
  - "A6 真实 helper 的微型 SelfCheck 复用 git/reviewer 夹具模式覆盖正反例；关键守卫各有定点变异证据"
dod_command: pwsh -NoProfile -File scripts/_review-policy.ps1 -SelfCheck
dod_exit: 0
dod_assert: 微型 SelfCheck 调用真实生产策略，正反用例全通过并输出 REVIEW-POLICY-SELF-CHECK-PASS；SelfCheck 集成验证真实 review.ps1 的裁决、退出码、轮次与故障拒绝；既有 selftest 17aa R3 片段调用同一自检。
review_gate: codex {verdict:pass}
hygiene: 新增实际复用策略单元的微型自检（真实 git 与 stub reviewer），现有 R3 自测调用同一入口，不重复 runner；关键守卫逐条单点变异并核 SHA 还原。
doc_sync: 同步 rubric/workflow/card template/task-loop/CLAUDE 的建议模式边界与本地可信来源；保留真实评审意见。
---

# T0-REVIEW-LOW-RISK

用户已授权采纳有益的上游降摩擦与提速改进。本卡是受限策略接入，本身触及评审基础设施，故仍走 blocking R3。

已提交基线卡显式选择 advisory，且实际改动只能证明为普通 docs/specs Markdown 时，允许真实负面评审作为建议落盘。任何关键、冻结、安全、工作流、交付或未知路径均保持 blocking。超时与无效评审不构成建议意见。

实现先写真实策略 SelfCheck 并由主 Agent 固化 RED，再接生产分类与helper 内真实 reviewer fixture。本卡实施预算小于 900 changed lines，并满足 R3 1000 changed lines / 60000 chars 硬上限；预估 550–780 行。




## Remote delivery authorization and provenance

The user's 2026-09-08 instruction authorizes remote delivery through an independent worktree and PR. The remaining acceptance, forbid and non-goals stay in force. This todo registration does not represent local historical results as remote implementation or acceptance. Use task-loop with GPT-6 Astra, high effort and the configured independent GPT-5.6 Sol high R3. Establish current-source behavior evidence and preserve existing remote product changes.

Local candidate a9506225 repaired strict JSON and exact status-only findings after two blocked R3 rounds. The user authorized one counter reset on 2026-09-08, and it has already been consumed. Preserve the findings and current counter for remote delivery; a new worktree grants no additional reset.

## Original 0220 evidence (historical)

Origin 0220 R1/behavior RED: `.review/low-current-red.log` exit1; primary red recorded DoD1; `.review/low-red-test-only.ps1` preserves tested source. Both old BLOCK repairs remain; `.review/historical-provenance.md` distinguishes old receipts. Helper GREEN/source hashes: `.review/low-green-final.log`, `low-green-manifest.json`. Twenty isolated guard mutants: `.review/run-low-mutations.ps1`, `low-mutations.json`. No additional reset is authorized.

## Current base evidence

On ceb2685e, the preserved tests-only fixture against the real base reviewer again failed the named advisory behavior (session66995 exit1). Primary red session35031 exited0 with DoD exit1 and a fresh ceb receipt; the original0220 receipt remains historical. Four frozen production sources were restored exactly; final SelfCheck session55870 exited0. Raw logs, source hashes and the 20-record/44-log same-source historical R4 audit are bound by `.review/current-base-ceb-red/evidence.json`; no new 20-mutant run or reset is claimed.

## PR277 round1 repair

Head1677 R3 BLOCK exposed duplicate JSON keys. `.review/r3-duplicate-keys/evidence.json` binds four actual reviewer RED cases, final helper GREEN, seven current guard mutants and 19 unchanged historical target/fixture checks. Original round1 receipts are preserved; rounds=1. No reset or second ship is claimed here.
