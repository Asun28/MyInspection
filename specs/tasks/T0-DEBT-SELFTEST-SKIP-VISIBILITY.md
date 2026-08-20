---
id: T0-DEBT-SELFTEST-SKIP-VISIBILITY
title: 让 selftest 有意跳过与前置失败裁剪均可见
depends_on: [T0-DEBT-SELFTEST-CRITICAL-PATH, T0-LICENSE-SELFTEST-DRIFT]
status: todo
branch: T0-DEBT-SELFTEST-SKIP-VISIBILITY
worktree: C:\wt\T0-DEBT-SELFTEST-SKIP-VISIBILITY
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 把 skip 计作 PASS、把可选环境缺失升级为失败或把真实失败降级为 skip
  - 以通过日志缺行反推 skip 数量，或只保留自由文本跳过说明而无机器台账
  - 为追求全量执行而移除既有前置条件、隔离条件或 fail-safe 边界
non_goals:
  - 修改失败闸聚合协议或 8.2e rendezvous 时限
  - 重新编号 17 个顶层闸或重分 core/workflow/seeded
  - 改 CI/workflow/task/review 行为
  - 在 core 内启动完整 seeded，或证明 seeded 的生产 no-git routing；该行为归 T0-DEBT-SELFTEST-NOGIT-ROUTING
  - 建立全量 per-gate mutation 矩阵；紧凑身份清单与资源预算归 T0-DEBT-SELFTEST-MUTATION-BUDGET
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SELFTEST-SKIP]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch '[SELFTEST-SKIP-SUMMARY]'))) { exit 1 }"
dod_exit: 0
dod_assert: 每个有意环境跳过或因已知前置失败而不执行的已登记检查输出 [SELFTEST-SKIP] gate={id} reason={stable code}；分片终态输出有序去重 [SELFTEST-SKIP-SUMMARY] 与准确 count；失败后的裁剪不可继续静默，也不得输出该检查 PASS；bounded helper 控制组 count=0。
review_gate: codex {verdict:pass}
hygiene: 先登记真正的执行单元与跳过原因，再用 bounded helper 的环境缺失、前置失败、正常执行三组夹具证明 FAIL/SKIP/PASS 互斥；删除 skip 记录、reason code 或摘要计数任一层均翻红；生产 routing 与资源预算分别由两张后续卡偿还
doc_sync: TD9 保持 carded；本卡只偿还 skip 可见性，不宣称 8.2e load-flake 已解决
---

# T0-DEBT-SELFTEST-SKIP-VISIBILITY

## 目标与证据

失败 run 与同 SHA 通过 run 的日志差异显示大量后续检查无 PASS、FAIL 或 skip 终态。建立明确执行台账，区分“可选环境未满足”和“前置失败导致裁剪”，不再靠缺行猜测。

## 验收边界

- 只登记真实可独立判定的检查；成功文案被抑制不自动等于检查未执行。
- reason 使用稳定 ASCII code，prose 可继续服务人工阅读但不参与机器判定。
- 同一检查同一原因只登记一次；摘要顺序确定，重复运行结果稳定。
- 已失败检查仍是 FAIL；被裁剪检查才是 SKIP，二者不得互相覆盖。

## 资源冲突

本卡与 TD9 另外三张未合并余卡及 TD134 实现卡共享 `scripts/selftest.ps1`；没有业务硬依赖，必须在最新已合并基线上串行执行并重放验收。

## R3 拆分仲裁

PR #33 的两轮 R3 证明，生产 no-git routing 行为与 mutation 资源预算不能继续塞进同一评审单元。本卡只保留 skip primitive、机器台账、摘要与 bounded helper 互斥证明；两项余债按后续卡串行偿还，不能以本卡合并宣称完成。
