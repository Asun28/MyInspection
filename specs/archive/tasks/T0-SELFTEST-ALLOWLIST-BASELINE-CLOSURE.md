---
id: T0-SELFTEST-ALLOWLIST-BASELINE-CLOSURE
title: 让动态 E2E 基线追踪完整敏感清单
depends_on: []
status: merged
branch: T0-SELFTEST-ALLOWLIST-BASELINE-CLOSURE
worktree: C:\wt\T0-SELFTEST-ALLOWLIST-BASELINE-CLOSURE
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 放宽 check-secrets 对 allowlist path 必须存在且被 git 精确追踪的 fail-closed 语义
  - 用 gitignore 负向通配符放开 schema 数据库目录，或把当前 1.db / 2.db 清单复制成另一份硬编码真相源
  - 删除 T0-SMOKE ship 动态 E2E、canary-harness 或既有单路径控制组
non_goals:
  - 修改 SQLDelight schema、迁移快照或 tracked-sensitive allowlist 内容
  - 修改 8.2e load-stability、skip oracle、CI matrix 或 workflow timeout
diagnosis:
  root_cause: scaffold-selftest run 33215637748 的动态 T0-SMOKE 临时仓先 git add -A，再只 force-add 1.db；PR #190 新增的已审 2.db 因 *.db gitignore 未进入临时基线，check-secrets 正确地以“allowlist path 未被 git 精确追踪”阻断 ship。
  same_class: 动态 E2E 从 tracked-sensitive-allowlist.json 解析全部 exact path 并逐项验证存在、force-add；canary-harness 用至少两条被忽略路径证明全量闭包，并以删除第二项处理的变异翻红。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture canary-harness
dod_exit: 0
dod_assert: canary-harness 证明两个 ignored sensitive baseline 均被精确追踪，生产 T0-SMOKE wiring 从权威 allowlist 传入完整 path 集合；缺少任一路径、只处理首项、删除清单解析或退回单路径硬编码均非零。
review_gate: codex {verdict:pass}
hygiene: 复用现有 canary-harness 与 Add-SelftestE2eBaseline，不新建平行测试文件；只增加多路径行为和最小 production wiring contract。
doc_sync: 合并后在 docs/TASK-BOARD.md 记录 PR、master commit、失败 run 与修复证据；本卡归档，并按 scaffold 元层缺陷规则决定是否上游 report。
---

# T0-SELFTEST-ALLOWLIST-BASELINE-CLOSURE

修复动态 Task Loop E2E 的 hermetic baseline 闭包。安全清单与 check-secrets 语义保持不变；临时仓必须像真实仓一样精确追踪每个已审敏感快照。

## 验收边界

- RED 必须在现有实现上证明第二条 ignored allowlist path 未被 helper 追踪。
- GREEN 只允许从权威 JSON 取 path 集合并由 baseline helper 逐项 fail-closed 处理。
- 真实 `-Shard workflow` 在合并前至少重放一次，证明 run 33215637748 的 gate 15 同类路径恢复。

## 资源冲突

本卡无业务依赖，但与 PR #189 `T0-DEBT-SELFTEST-LOAD-STABILITY` 共享 `scripts/selftest.ps1`。两卡不得并行编辑或基于不同 master 合并；仅在 #189 合并并同步最新 master 后 start。
