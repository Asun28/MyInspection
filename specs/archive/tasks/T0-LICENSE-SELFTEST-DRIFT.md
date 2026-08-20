---
id: T0-LICENSE-SELFTEST-DRIFT
title: 恢复 Gradle diagnostics 的 selftest 回归覆盖并消除权威套件漂移
depends_on: [T0-LICENSE-GAV-BOUNDS]
status: merged
branch: T0-LICENSE-SELFTEST-DRIFT
worktree: C:\wt\T0-LICENSE-SELFTEST-DRIFT
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
  - scripts/selftest.ps1
forbid:
  - 删除、合并或弱化既有凭据脱敏、detail/configuration、safe-prefix 或 parse-redaction 覆盖来换取绿灯
  - 把真实 diagnostics 失败降级为 skip，或用环境切片绕过 17cc–17hh
  - 改变 Gradle 图范围、GAV/POM/许可分类、exception 语义或失败退出语义
non_goals:
  - 修改 selftest skip 台账、摘要协议、all-child 编排或 8.2e 负载预算
  - 修改 CI/workflow/task/review 行为
  - 新增许可策略、例外或运行时网络
diagnosis:
  root_cause: T0-LICENSE-DIAGNOSTICS 后续把脱敏收敛到统一 record boundary，但 selftest 仍定位旧的逐规则 marker；同时 bounded tail 改变了 detail/configuration/prefix 的可见位置，导致旧 mutation vacuous、旧断言与当前权威实现漂移。T0-DEBT-SELFTEST-SKIP-VISIBILITY 的无 git 全量 child 首次取消尾段切片后暴露了这组既存 seeded 红项。
  same_class: 扫全 17cc scanner 主断言与 mutation 表；保留 Authorization、赋值式/空格式 key、CLI/plain password/token、URI userinfo、safe-prefix、configuration/detail、parse-redaction 全部既有语义，每个迁移后的 mutation 都须非 vacuous。
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite diagnostics
dod_exit: 0
dod_assert: diagnostics 套件全绿且 mutation 数不减少；正常 seeded 的 17cc–17hh 全量尾段通过，旧覆盖无删除/合并/弱化；删除任一迁移后的 redaction/bound/category 守卫命中专属失败码。无 git 全量 child 由依赖本卡的 #33 在合入新 master 后证明。
review_gate: codex {verdict:pass}
hygiene: 以现有 diagnostics 权威套件为主，selftest 仅保留不重复且能杀死单句删除的集成回归；任何替换必须先证旧 mutation vacuous、再证新 mutation RED。
doc_sync: TD138 归档并记录 PR/commit；T0-DEBT-SELFTEST-SKIP-VISIBILITY 解除前置后继续，不宣称 TD9 已 paid
---

# T0-LICENSE-SELFTEST-DRIFT

## 单一产出

让 `scripts/check-licenses.ps1`、权威 `license-scanner-check` diagnostics 套件与 `selftest` 17cc scanner 回归重新一致：生产边界满足原覆盖，旧 marker 迁移为当前实现上的非 vacuous mutation，而不是删测试换绿。

## 验收边界

- 保留 baseline 的 CLI/plain password/token、Authorization、key=value、URI userinfo、configuration/detail、safe-prefix 与 parse-error 覆盖。
- 对 bounded record 的新语义，拆分夹具以分别证明“凭据 continuation 不泄漏”和“普通 benign detail 可保留”，不得用互相矛盾的单个输出记录降低任一断言。
- `license-scanner-check.ps1 -Suite diagnostics` 是权威行为套件；`selftest` 只做集成接线与同类 mutation 回归。
- 本卡重放正常 seeded 的 17cc–17hh；合并后由 #33 合入最新 master，再重放无 git seeded 与 core。本卡不实现 skip protocol。
