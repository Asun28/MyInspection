---
id: T0-LICENSE-CI-INTEGRATION
title: Gradle 许可扫描套件接线与 TD2 总验收（TD2 收口卡 5/5）
depends_on: [T0-LICENSE-GAV-BOUNDS]
status: todo
branch: T0-LICENSE-CI-INTEGRATION
worktree: C:\wt\T0-LICENSE-CI-INTEGRATION
allow_paths:
  - .github/workflows/ci.yml
  - scripts/selftest.ps1
  - scripts/license-scanner-check.ps1
  - docs/LICENSE-POLICY.md
  - docs/RELEASE-CHECKLIST.md
forbid:
  - 重写前四卡已通过 R3 的 scanner 核心
  - 让 verify 或 scanner 依赖出站网络
  - 在前四卡未 merged 或 TD 总验收未通过时把 TD2 标 paid
non_goals:
  - 新增生态扫描器、许可类别或 exception 格式
  - 修改 Android 产品代码或 Gradle 依赖
  - 发布应用、商店提交或外部交付
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite integration
dod_exit: 0
dod_assert: CI 在 JDK/Android/Gradle setup 与在线 cache warm-up 后运行 scanner，scanner 本身强制 offline；fresh-runner 顺序夹具可证不会因未预热必红。仓库真实扫描逐坐标输出至少含 org.testng:testng，禁列和未知夹具均非零；五个子套件与 selftest 接线全绿。政策/发布文档准确写明覆盖与剩余人工边界。只有本卡 merge 后执行 TD2 总验收并补齐五个 PR/commit 证据，随后才允许 paid/归档。
review_gate: codex {verdict:pass}
hygiene: integration 只证明接线、真实仓扫描和五个套件聚合；不复制 graph/policy/gav-bounds/diagnostics 的细粒度夹具
doc_sync: 本卡 merge 后把 TD2 置 paid，并记录 PR #20 基线及五张收口卡的 PR/commit；归档五张已 merged 卡；同步 LICENSE-POLICY 与 RELEASE-CHECKLIST
---

# T0-LICENSE-CI-INTEGRATION

## 目标

把前四张已独立通过 R3 的能力接进 CI 和权威文档，并执行 TD2 的总验收。本卡是 fan-in/closure，不拥有 scanner 核心。

PR #20 已在 master `b0a76d0` 合入 CI 顺序和 scanner 初版，但五个专用套件、selftest 聚合与权威文档事实同步尚未完成。本卡只验收新形成的串行合同，并如实把 PR #20 记为共同基线；不得倒填五个不存在的历史 PR。

## 单一产出

1. CI 顺序固定为 setup/cache warm-up → offline license scan。
2. `scripts/selftest.ps1` 只聚合调用五个专用 license scanner 套件，不再内嵌上千行 scanner fixture。
3. 政策和发布清单准确描述机检范围、fail-closed 语义与仍需人工证据的非分发图。
4. 用真实仓扫描和五个套件结果形成 TD2 总验收证据。

## 关闭条件

`T0-LICENSE-SCANNER`、`T0-LICENSE-POLICY`、`T0-LICENSE-DIAGNOSTICS`、`T0-LICENSE-GAV-BOUNDS`、本卡全部 merged；本卡 integration DoD、仓库 verify、R3 均通过；tracker 补齐 PR #20 基线与五张收口卡 PR/commit 后，TD2 才能从 `carded` 变 `paid`。

## 根因诊断

CI 和文档依赖稳定的 CLI/exit contract，却被旧卡与解析器、POM 和日志同时评审。把它放在末端可避免核心行为每改一次就重审 workflow 和政策措辞。
