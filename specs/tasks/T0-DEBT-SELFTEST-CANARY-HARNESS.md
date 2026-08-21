---
id: T0-DEBT-SELFTEST-CANARY-HARNESS
title: 让 post-merge selftest canary 离线确定且失败可定位
depends_on: [T0-DEBT-SELFTEST-SPLIT-PLAN]
status: todo
branch: T0-DEBT-SELFTEST-CANARY-HARNESS
worktree: C:\wt\T0-DEBT-SELFTEST-CANARY-HARNESS
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 为 scaffold-selftest 下载或预热 Android、Gradle、插件或依赖缓存
  - 关闭 post-merge canary、把失败改成无记录 PASS、或改动业务代码/schema
  - 与 PR #33 并行编辑 scripts/selftest.ps1
non_goals:
  - 重分 core/workflow/seeded 或运行 selftest all
  - 偿还 TD9 的 no-git routing、mutation budget、load stability
  - 改 task/review/verify/许可证/密钥闸的生产语义
diagnosis:
  root_cause: seeded 17a3 在通用 cold runner 中直接运行项目 Gradle migration 集成而未声明 wrapper/cache 前置；workflow 15b 又把真实 ship 的全部子输出重定向到 null，使环境缺失和编排缺陷都只剩顶层 gate 编号。
  same_class: 审计 selftest 中生产 workflow smoke 的静默重定向与 seeded 外部运行时调用；本卡只收口已在 run 32467267559/32475336719 双平台复现的 15b 与 17a3，不扩写其它分片。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture canary-harness
dod_exit: 0
dod_assert: workflow 15b 在双平台真实 ship 成功；受控 ship 失败输出稳定阶段/gate 与有界脱敏尾部；cold Gradle 前置缺失时 17a3 零启动 wrapper 并以稳定环境 skip 记录，前置就绪时仍执行真实 ADDED/REMOVED migration 负例；删除任一前置、诊断或真实迁移分支均被定向回归拒绝
review_gate: codex {verdict:pass}
hygiene: 用分片前退出的 canary-harness fixture 覆盖 15b/17a3；不新增整脚本 mutation 副本，不运行 all
doc_sync: 合并后把 TD158 置 paid，记录 PR/merge 与 post-merge workflow+seeded 双平台结果；TD9 保持 carded
---

# T0-DEBT-SELFTEST-CANARY-HARNESS

## 产出

修复 post-merge `scaffold-selftest` 的两个系统性 harness 缺陷：workflow 编排失败必须直接指出内层失败位置；seeded migration 负例不得把未配置 Android/Gradle 的 cold runner 误报为产品回归。

## 验收边界

- 15b 仍真跑 `task.ps1 start + ship -Local`，不降级成源码 grep；基线须通过，受控失败须保留可定位且有界的诊断。
- 17a3 只在已证明 wrapper distribution 可离线启动时进入真实 migration 负例；否则输出稳定、可汇总的环境 skip，且不得启动 wrapper/download。
- 有可用本地 Gradle 前置时，`.sq` 缺迁移仍精确命中 `ADDED`，错误 `1.sqm` 仍精确命中 `REMOVED`。

## 禁止

不通过给 canary 安装整套 Android/Gradle 来掩盖 harness 边界错误；不删除 migration 证明；不让 post-merge canary 回到静默失败。
