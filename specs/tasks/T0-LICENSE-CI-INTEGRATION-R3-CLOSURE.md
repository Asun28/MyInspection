---
id: T0-LICENSE-CI-INTEGRATION-R3-CLOSURE
title: License integration 活跃接线断言与 cold 聚合语义（PR #49 R3 收口）
depends_on: [T0-LICENSE-CI-INTEGRATION]
status: todo
branch: T0-LICENSE-CI-INTEGRATION-R3-CLOSURE
worktree: C:\wt\T0-LICENSE-CI-INTEGRATION-R3-CLOSURE
allow_paths:
  - scripts/license-scanner-check.ps1
  - scripts/selftest.ps1
forbid:
  - 在原 PR #49 上启动第 3 轮 R3
  - 把真实仓 Strict 扫描放回 fresh seeded canary
  - 重写 scanner graph/POM/policy/diagnostics 核心
non_goals:
  - 新增许可类别、依赖图或 exception 格式
  - 修改 CI 触发器、Android 产品代码或 Gradle 依赖
  - 重审 PR #49 已闭合的 CLI forbidden/unknown 退出码夹具
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite integration
dod_exit: 0
dod_assert: integration 对 selftest、workflow、scanner 调用及两份可见文档使用活跃且唯一的结构断言，注释掉或删除任一接线均按专属码失败；seeded cold 模式不跑真实仓扫描，SkipMutations 被明确传播或拒绝，PASS 文案逐字对应实际证据；默认 integration 仍运行四个完整子套件与真实 Strict 扫描。
review_gate: codex {verdict:pass}
hygiene: 只接住 PR #49 第 2 轮两类 finding；每类使用紧凑表驱动 comment/delete mutation，不恢复旧 1400 行内联 fixture
doc_sync: 原卡经人裁合并后再启动本卡；本卡 merge 后继续原卡 TD2 总验收与 R5，不单独把 TD2 标 paid
---

# T0-LICENSE-CI-INTEGRATION-R3-CLOSURE

## 起因

PR #49 在第 1 轮修复了 fresh seeded 对预热 Gradle cache 的依赖，并补回 EPL/缺失元数据的进程级非零退出证据；第 2 轮仍发现两类收口缺口。仓库 R3 上限为两轮，因此原 PR 停止追评并转人裁，本卡精确承接余项。

## 唯一范围

1. `integration` 只能把 selftest 的真实可执行调用、CI 的真实 YAML step/run、scanner 的真实命令行及文档的可见命令行当作证据；注释文本不得满足。四类断言各用最小 comment/delete mutation 证明。
2. `-SkipRealScan` 的成功信息必须明确说未执行真实仓扫描。`-SkipMutations` 必须传给四个 child suite，或在 integration 上 fail-closed 拒绝；不得静默忽略。
3. seeded 的输出只宣称 cold、fixture-backed 子套件通过；默认 DoD 才宣称真实 Strict 扫描与 TestNG 坐标通过。

## 已闭合事项

不恢复旧 scanner fixture，不重开 CI warm-up 顺序、真实 150 GAV 扫描、EPL/缺失元数据 CLI exit、政策文档内容或 scanner 核心行为。

## 人裁边界

本卡依赖原卡。PR #49 是否先合并由人裁决定；未合并前本卡只登记、不实施。禁止用本卡作为原 PR 第 3 轮 R3 的旁路。
