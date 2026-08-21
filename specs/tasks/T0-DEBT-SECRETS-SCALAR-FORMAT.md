---
id: T0-DEBT-SECRETS-SCALAR-FORMAT
title: 让 tracked-sensitive allowlist 拒绝增补平面格式标量
depends_on: [T0-DEBT-UNICODE-SCALAR-TEXT]
parallelizable_with: [T0-DEBT-LICENSE-SCALAR-FORMAT]
status: todo
branch: T0-DEBT-SECRETS-SCALAR-FORMAT
worktree: C:\wt\T0-DEBT-SECRETS-SCALAR-FORMAT
allow_paths:
  - scripts/check-secrets.ps1
  - scripts/selftest.ps1
forbid:
  - 复制或重写 scripts/_unicode.ps1 的 scalar iteration
  - 放宽 tracked-sensitive allowlist 的 exact path、字段或 reparse 约束
  - 新增依赖、出站网络、登录态写入或自动发布
non_goals:
  - 修改 secret 内容模式、历史扫描或许可扫描器
  - Unicode 归一化或 bidi policy
  - 扩大 SQLDelight baseline allowlist 范围
diagnosis:
  root_cause: allowlist 的 path/purpose guard 使用 UTF-16 regex，增补平面 Cf 可作为不可见标量穿过字段校验。
  same_class: allowlist 的 path 与 purpose 两个字段同卡表驱动覆盖；license consumer 独立拆卡。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Only 17
dod_exit: 0
dod_assert: 真实 check-secrets fixture 对 path/purpose 中每个增补平面 Cf 与 malformed surrogate 均非零且点名 SECRET-ALLOWLIST scalar 失败；普通 emoji 不触发该守卫且后续 exact-path 语义保持；删除 path、purpose 任一 helper 接线的变异各自翻红
review_gate: codex {verdict:pass}
hygiene: 扩展既有 17a3 allowlist fixture，不建平行 harness；只复制一份 selftest script 的用户上限仅在诊断堵塞时启用且副本不提交
doc_sync: 三卡全 merged 后把 TD157 置 paid、L190 合并进 CLAUDE Unicode 铁律并归档三卡；TASK-BOARD 回填 PR/commit/R3 证据
---

# T0-DEBT-SECRETS-SCALAR-FORMAT

## 单一产出

tracked-sensitive allowlist 的 `path` 与 `purpose` 复用 scalar helper，拒绝不可见的增补平面 Cc/Cf 与 malformed UTF-16，同时保持既有 exact path 和 reparse fail-closed 语义。

## 验收

```powershell
pwsh -NoProfile -File scripts/selftest.ps1 -Only 17
```

- 期望退出码：0
- 断言：真实 check-secrets 入口执行 path/purpose hostile fixtures，并以字段级 mutation 证明两条接线均承重。
