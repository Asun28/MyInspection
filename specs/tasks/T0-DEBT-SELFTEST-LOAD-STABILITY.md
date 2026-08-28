---
id: T0-DEBT-SELFTEST-LOAD-STABILITY
title: 消除 8.2e 高负载下固定五秒 rendezvous 假红
depends_on: [T0-DEBT-SELFTEST-MUTATION-BUDGET]
status: todo
branch: T0-DEBT-SELFTEST-LOAD-STABILITY
worktree: C:\wt\T0-DEBT-SELFTEST-LOAD-STABILITY
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 删除长分片并发、core 错峰、dirty overlay、StrictLint 三态或失败传播任一证明
  - 用无限等待、无上限重试或吞掉 timeout 让夹具假绿
  - 只把固定 5 秒换成另一个未验证的魔数
non_goals:
  - 改生产 all 分片调度、CI matrix/runner 或 post-merge 触发规则
  - 改失败/skip 观测协议
  - 优化完整 selftest 墙钟时间
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SELFTEST-8.2E-RENDEZVOUS]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'SCAFFOLD_SELFTEST_STUB_READY_TIMEOUT_SECONDS'))) { exit 1 }"
dod_exit: 0
dod_assert: 8.2e rendezvous 使用具名有界预算并输出 [SELFTEST-8.2E-RENDEZVOUS]；第二长分片延迟超过旧 5 秒仍通过并保留并发证明；注入短预算的真实 timeout 以专属诊断非零；删除等待上限、ready 条件或并发重叠断言均被变异击杀。
review_gate: codex {verdict:pass}
hygiene: hermetic 夹具提供 load-delay、bounded-timeout 与正常控制组；断言实际 elapsed/ready ticks 和退出语义，不以 Start-Sleep 后“没报错”作假证明
doc_sync: 五张 TD9 卡全部 merged 且 post-merge core 重放稳定后，才可把 TD9 置 paid
---

# T0-DEBT-SELFTEST-LOAD-STABILITY

## 目标与证据

run `31941736470` 在同一 SHA 上前两次仅 Ubuntu core 的 8.2e 假红、第三次通过。当前 stub 的首个长分片只等另一个长分片五秒；runner 高负载下，后者尚未获调度就会退出 29。

## 验收边界

- timeout 必须有界、具名、可在 hermetic 测试中缩短；默认预算须覆盖实证的调度延迟。
- load-delay 控制组必须超过旧五秒阈值，并证明两个长分片确实重叠而非串行放宽断言。
- timeout 负例必须快速、专属地失败，防无限等待或静默跳过。
- 8.2e 原有五类证明继续各自可证伪，不再用一个合取式告警掩盖具体失败面。

## 资源冲突

mutation-budget 前置卡已合并；本卡必须从包含 PR #187（master `86a895a9`）的最新基线开工并重放验收。
