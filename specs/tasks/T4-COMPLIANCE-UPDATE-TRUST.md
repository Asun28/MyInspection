---
id: T4-COMPLIANCE-UPDATE-TRUST
title: 规则更新的可信来源与版本决策
status: todo
depends_on: []
allow_paths:
  - docs/adr/0008-compliance-update-trust.md
  - specs/tasks/T4-COMPLIANCE-OVERRIDE-IMPORT.md
  - docs/TASK-BOARD.md
  - specs/android-module-boundaries.md
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当设计规则更新通道时，规格应明确发布者、可信凭证来源及篡改威胁；同源文件自带的 hash 不应被当作发布者身份。"
  - "R2 当确定信任策略时，规格应记录独立可信摘要与数字签名方案的证据和取舍，并明确凭证轮换、撤销及恢复行为。"
  - "R3 如果来源、生效日期、过期、回退或 schema 兼容策略尚未确认，则规格应标为待澄清，依赖实现卡不应宣称已可安全激活。"
acceptance:
  - "A1 [R1] ADR 说明真实分发渠道、信任根持有人/取得方式、验证边界和同源假 digest 负例。"
  - "A2 [R2] ADR 有用户确认的决策记录与完整轮换/撤销/恢复矩阵；将负例投影到导入卡。"
  - "A3 [R3] 所有会影响激活的待澄清项关闭；如修改冻结配置 schema，另列版本评审和旧配置兼容向量。"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check
dod_exit: 0
dod_assert: 新增 ADR 0008 记录真实发布渠道/信任根、用户决策、轮换/撤销/日期/恢复矩阵与负例；所有激活阻塞项关闭并同步导入卡；同源自带 hash 不作身份验证，静态命令不代替决策证据
review_gate: codex {verdict:pass}
---

# T4-COMPLIANCE-UPDATE-TRUST

版本：V1 的规则导入前置设计。用户认可增加可信来源防护，尚未选定发布者、公钥或协议。

交付是按 ADR 目录顺序新建的决定记录及导入卡收口，不覆盖 accepted ADR-0004。静态 DoD 只检查卡结构/diff，不能自动证明用户选择；R3 必须核查三项验收的决策证据，缺失不得合并本卡。保留法律 work-check 待办，禁止借规则更新改变当前用途限制。

ADR 0008 是本计划预留；开工如已占用，先修订卡到下一空号，不能覆盖 accepted ADR。
