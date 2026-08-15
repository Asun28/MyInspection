---
id: T7-REMEDIATION
title: LLM remediation 建议：mock 优先 + 仅房东版 + 措辞边界 + 免责声明
depends_on: [T3-PDF-RENDERER]
status: todo
branch: T7-REMEDIATION
worktree: C:\wt\T7-REMEDIATION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/remediation/
  - android/core/src/test/kotlin/nz/myinspection/core/remediation/
  - android/app/src/main/kotlin/nz/myinspection/app/remediation/
  - prompts/remediation/
forbid:
  - 建议进房客版（composer 类型层已硬拦，本卡不得绕）
  - 未经显式用户动作的网络调用；测试/verify 走真网络（全 mock——verify 硬边界）
  - key 入库/入代码（EncryptedSharedPreferences 或等价本机存储）
non_goals:
  - 成本估算（需求 §9 明确不做）；多 provider 聚合 UI（provider 接口可换即可）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.remediation.*"
dod_exit: 0
dod_assert: mock provider 测试绿：种子表命中项直出建议；LLM 响应经措辞门（禁处方式动词表——「更换/维修 X」拒，重写为「建议联系持牌 XX 评估」形态；NZS 4306 分级枚举强制）；网络失败/跳过 → 报告优雅无建议节（不报错不占位）；payload 最小化测试（只送不利发现项的 stable_id/状态/备注，不送租客 PII/照片）
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T7-REMEDIATION

## 产出
`core/remediation`（provider 接口 + mock + 种子表匹配 + 措辞门 + 分级）、`prompts/remediation/`（prompt 模板 + 「检查项→建议」种子对照表）、`app/remediation`（真 provider HTTP 薄壳 + key 设置 UI + 显式「生成建议」按钮）。

## 上下文包（执行模型必读）
- **定位（需求 §9 [定]）**：提示 + 分级（NZS 4306 思路：significant defect / maintenance item / monitor）+ 建议找谁（持牌电工/水管工/建筑检查员），**不是诊断 + 处方**。「建议联系持牌电工检查」安全；「更换这个开关」不安全——措辞门在 :core 用禁则表机检重写/拒绝。
- 流程：finalize 后房东版报告页出「生成整改建议」按钮（**唯一联网点**，可完全跳过）→ payload = 不利发现项（stable_id/双语文案/状态/备注/wear_or_damage）+ 种子表命中的先例对 → provider 调用（自带 key）→ 响应过措辞门 + 分级校验 → 注入房东版 composer 建议插槽 → 重新出 PDF。建议内容**不进 data_hash**（ADR-0003 排除域）——生成前后哈希不变（测试断言）。
- 种子表（prompts/remediation/seed-map.json）：30–50 条「检查项类别→建议模板」双语对照（作者按调研报告的常见缺陷类别写）；种子命中可离线直出（provider 只处理未命中/复杂项）。
- provider 接口 `RemediationProvider`（suspend fun suggest(payload): Result）：mock 实现在 :core 测试；真实现 v1 接 Anthropic Messages API（provider 可换设计，key 由用户设置页填、EncryptedSharedPreferences 存）。**prompt 注入防御**：备注是用户文本但同机同权，威胁模型低；仍加系统侧「只输出 JSON 建议数组」+ schema 校验拒自由文本。
- 免责声明（需求 §8 [定]）恒随建议节渲染。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5；**R3（Sol）按安全面重点评审**（payload 最小化/key 处理/措辞门）。难度 M。
