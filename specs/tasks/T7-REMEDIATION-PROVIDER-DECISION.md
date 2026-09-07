---
id: T7-REMEDIATION-PROVIDER-DECISION
title: 整改建议 provider 选择与出站合同决策
status: todo
depends_on: []
allow_paths:
  - docs/adr/0009-remediation-provider.md
  - specs/tasks/T7-REMEDIATION.md
  - docs/TASK-BOARD.md
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当选择远程整改建议服务时，规格应记录服务商、端点、API 版本、凭据获取方式及替换边界，不应把旧卡示例当作已选结果。"
  - "R2 在任何候选方案中，出站数据应保持 docs/SECURITY.md §2.1 的版本锁定字段白名单与精确请求预览，不应包含现场备注、转写、地址或其他自由文本。"
  - "R3 如果服务商、费用或数据处理条件尚未确认，则规格应保留待澄清并继续支持本地种子建议，不能以模糊 Result 接口掩盖失败和取消行为。"
acceptance:
  - "A1 [R1] 决策记录有官方接口依据、用户选择和可替换 adapter 范围，凭据不得写入文档。"
  - "A2 [R2] 示例 payload 仅用合成闭集值，确认对象与最终请求字节绑定；任何供应商默认 SDK 字段另行核验。"
  - "A3 [R3] 明确取消/超时/拒绝/限额/离线的结果及本地回退，选型影响落实到实现卡；未确认选项不得登记为 accepted。"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check
dod_exit: 0
dod_assert: 新增 ADR 0009 有官方依据、用户选择、provider/端点/API/key获取/费用与数据条件、取消失败矩阵及请求限额；禁止字段/无默认 Anthropic 边界落实实现卡，静态命令不代替真实用户选择
review_gate: codex {verdict:pass}
---

# T7-REMEDIATION-PROVIDER-DECISION

版本：V1 远程建议前置决策；本地种子与巡检不依赖网络。用户要求补充选型任务，没有选择 Anthropic 或授权采购。

静态 DoD 只验证文档/卡结构，R3 还必须检查真实决策依据和用户选择。实现前待澄清：provider/key、费用与数据处理条件、请求/响应字节上限和超时策略。不变边界：没有自有服务端，不引入账号、遥测或新的自动联网点。

ADR 0009 是本计划预留；开工如已占用先改卡。运行期 prompt/seed 变更归 T7-REMEDIATION，不属于本次选型决策。

还须确认所选 API 的消息封装与精确 JSON 预览/闭集字段合同如何对应：provider 固定协议字段或固定 prompt 不得夹带现场自由文本；可见预览、确认绑定与最终 body 的序列化关系必须可测试，不能为适配某家 API 静默扩大允许数据。
