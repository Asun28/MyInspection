---
id: T7-REMEDIATION
title: LLM remediation 建议：mock 优先 + 仅房东版 + 措辞边界 + 免责声明
depends_on: [T3-PDF-RENDERER, T1-SHARE-SCREEN-PRIVACY, T1-LOCAL-DATA-SECURITY, T7-REMEDIATION-PROVIDER-DECISION]
status: todo
branch: T7-REMEDIATION
worktree: C:\wt\T7-REMEDIATION
plan_ref: context/DESIGN.md#offline-and-data-protection-experience
acceptance:
  - "A1 remediation is on-device first using the approved seed table and wording gate"
  - "A2 remote explicit generation is user-triggered, cancellable, and is the sole approved runtime network boundary"
  - "A3 offline fallback uses the local seed source, sends only a safe payload when remote is chosen, and displays source and disclaimer"
  - "A4 remediation failures never block inspection finalize or either report; the optional section is omitted cleanly"
  - "A5 [R1] 确认后切换 provider、模板/seed 版本或内容使确认失效；mock transport 记录的请求 body 与已确认 canonical JSON 字节一致。"
  - "A6 [R2] 地址/姓名/联系方式/备注/转写/路径/URI/媒体/报告/key/备份逐类负例验证最终 HTTP body 不含禁止值；Authorization 仅由 adapter 在已选受信端点附加，不写日志或确认 payload。"
  - "A7 [R3] 超时/取消/过大响应/结构及措辞拒绝不污染已有报告、租客输出或 data_hash。"
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/remediation/
  - android/core/src/test/kotlin/nz/myinspection/core/remediation/
  - android/app/src/main/kotlin/nz/myinspection/app/remediation/
  - prompts/remediation/
  - android/app/src/test/kotlin/nz/myinspection/app/remediation/
forbid:
  - 建议进房客版（composer 类型层已硬拦，本卡不得绕）
  - 未经显式用户动作的网络调用；测试/verify 走真网络（全 mock——verify 硬边界）
  - key 入证据库/代码/日志；本机凭据统一经 LocalSecretBox/Keystore，不另造持久化秘密方案
non_goals:
  - 成本估算（需求 §9 明确不做）；多 provider 聚合 UI（provider 接口可换即可）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.remediation.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: mock provider 测试绿：种子表命中项直出建议；LLM 响应经措辞门（禁处方式动词表——「更换/维修 X」拒，重写为「建议联系持牌 XX 评估」形态；NZS 4306 分级枚举强制）；网络失败/跳过 → 报告优雅无建议节（不报错不占位）；payload 最小化测试（只送版本号、锁定模板 stable id、合法状态与锁定 seed 建议码；各类自由文本/PII/路径/媒体的负例均不得进入最终请求字节）
requirements:
  - "R1 当用户选择远程建议时，系统应展示精确闭集 payload 与 provider 供确认；确认对象或最终请求字节改变时，应重新确认。"
  - "R2 当 adapter 发送请求时，系统应再次校验允许字段、模板成员与上限，任何自由文本、媒体、凭据字段或未知字段均应拒绝。"
  - "R3 如果 provider 失败、取消或返回非法结构/措辞，则系统应给出本地种子或无建议的可用结果，不阻断巡检与报告。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T7-REMEDIATION

## 产出
`core/remediation`（provider 接口 + mock + 种子表匹配 + 措辞门 + 分级）、`prompts/remediation/`（prompt 模板 + 「检查项→建议」种子对照表）、`app/remediation`（真 provider HTTP 薄壳 + key 设置 UI + 显式「生成建议」按钮）。

## 上下文包（执行模型必读）
- **定位（需求 §9 [定]）**：提示 + 分级（NZS 4306 思路：significant defect / maintenance item / monitor）+ 建议找谁（持牌电工/水管工/建筑检查员），**不是诊断 + 处方**。「建议联系持牌电工检查」安全；「更换这个开关」不安全——措辞门在 :core 用禁则表机检重写/拒绝。
- 流程：finalize 后房东报告页显式选择远程生成 → 从版本锁定模板/seed 投影闭集字段 → 展示精确 canonical JSON 与 provider 供确认 → HTTP adapter 重查字段全集、成员、长度和确认绑定 → 响应有界解析、结构/措辞/分级校验 → 注入仅房东可见建议节。建议不进 data_hash；失败、取消或跳过不阻断 finalize/PDF/HTML。
- 种子表（prompts/remediation/seed-map.json）：30–50 条「检查项类别→建议模板」双语对照（作者按调研报告的常见缺陷类别写）；种子命中可离线直出（用户显式选择远程时才请求 provider，未命中不自动联网）。
- provider 接口 `RemediationProvider` 接收 ConfirmedRemediationPayload；真实 provider 由前置决策卡选定，旧 Anthropic 示例不构成已选结果。key 经 LocalSecretBox 获取，不进入 payload/回执/日志。最终出站拒绝 notes、转写、双语自由文案、wear_or_damage、地址、路径/URI、媒体引用、报告及未知字段；prompt 指令不能代替字段白名单。
- 免责声明（需求 §8 [定]）恒随建议节渲染。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5；**R3（Sol）按安全面重点评审**（payload 最小化/key 处理/措辞门）。难度 M。
