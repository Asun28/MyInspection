---
id: T4-COMPLIANCE-UPDATE-TRUST
title: 规则更新的可信来源与版本决策
status: merged
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

版本：V1 的规则导入前置设计。2026-09-08 用户在明确责任问题后回复“好的”，批准本人最终批准规则、受控电脑分开保管规则与 APK 两类私钥、USB 首次可信安装/规则传递。此处仅记录下方问答直接支持的责任与渠道安排；单公钥协议及版本/日期/恢复矩阵由随后提交的 ADR 0008 提供完整证据，仍须原任务独立评审。真实公钥与制品安装证据由导入卡提供，责任安排批准不等于这些产物已经存在。

交付是按 ADR 目录顺序新建的决定记录及导入卡收口，不覆盖 accepted ADR-0004。静态 DoD 只检查卡结构/diff，不能自动证明用户选择；R3 必须核查三项验收的决策证据，缺失不得合并本卡。保留法律 work-check 待办，禁止借规则更新改变当前用途限制。

ADR 0008 是本计划预留；开工如已占用，先修订卡到下一空号，不能覆盖 accepted ADR。

决策证据（2026-09-08 会话）：向用户提供完整方案后，询问原话：“是否批准这套方案：由你最终批准规则，两类私钥分别保管在你控制的电脑，首个可信 APK 和规则文件通过该电脑经 USB 安装／传递？”用户答复原话：“好的”。用户后续请求 task-loop 合到 remote。本记录只同步批准事实；完整 ADR、恢复矩阵及验收投影须另经本卡正式远端 R3/CI 并合并，当前 todo 不解除任何依赖。

## 远端交付（2026-09-08）

PR #257 已 squash 合入 origin/master（4a1358e），正式远端 R3 第二轮 pass 于 2de8e6c6aca9b4107bb8721be6a2641a27154724。首轮关于批准上下文的发现已通过 ADR 中原始相邻消息、具体消息 ID 及批准层次说明修复；未把本地 R3 自动当成远端批准。DoD、verify（core 与 Golden Evidence JVM Core E2E）、范围、许可、防泄露、真实 diff 预算及同一 head 的 CI verify 全通过。签名制品、导入功能、API 26/真机验收仍由后续导入卡交付，work-check 未启用。

责任记录前置 PR #256 已合并；本设计前置完成，导入卡其他依赖不变。R5 同步状态、看板、CLAUDE 索引并由 archive.ps1 归档。本卡不新增工程经验条目：本轮取回会话证据与已有 L18/L101 的规划/评审边界相关，未改变脚手架规则。
