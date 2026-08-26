---
id: T7-REMEDIATION
title: LLM remediation 建议：mock 优先 + 仅房东版 + 措辞边界 + 免责声明
depends_on: [T3-PDF-RENDERER, T1-SHARE-SCREEN-PRIVACY]
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
  - key 入库/入代码/系统备份，或 secret 明文进入 SharedPreferences/文件
  - cleartext HTTP；地址/姓名/联系方式/照片/音频/完整报告/备份内容进入 payload；网络失败阻断 finalize/PDF
non_goals:
  - 成本估算（需求 §9 明确不做）；多 provider 聚合 UI（provider 接口可换即可）
  - 无服务端校验的 Timestamp+Nonce 伪签名；额外 HTTPDNS/DoH resolver（新增网络信任边界须另立 ADR）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.remediation.*"
dod_exit: 0
dod_assert: mock provider 测试绿：飞行模式 seed 命中项直出并标 On-device；远程只由显式按钮触发，>1s 显示可取消阶段，离线/超时/非 2xx/超限/非 schema JSON 均保留本地建议且不阻断 finalize/PDF；只在连接/超时、429、502/503/504 上以 1/2/4 秒±20%抖动最多 3 次重试，4xx 鉴权/schema/安全拒绝不重试且取消终止后续尝试；响应经措辞门（禁处方式动词表——「更换/维修 X」拒，重写为「建议联系持牌 XX 评估」形态；NZS 4306 分级枚举强制）；payload 最小化测试只送不利发现项 stable_id/状态/必要备注，不送地址/姓名/联系方式/照片/音频/完整报告/备份内容；平台 TLS（支持时 TLS 1.3）且 cleartext/自定义 trust bypass 禁用，key/Authorization/log 脱敏；无自有服务端时不发送伪 nonce 签名、不新增 HTTPDNS 端点
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T7-REMEDIATION

## 产出
`core/remediation`（provider 接口 + mock + 种子表匹配 + 措辞门 + 分级）、`prompts/remediation/`（prompt 模板 + 「检查项→建议」种子对照表）、`app/remediation`（真 provider HTTP 薄壳 + key 设置 UI + 显式「生成建议」按钮）。

## 上下文包（执行模型必读）
- **定位（需求 §9 [定]）**：提示 + 分级（NZS 4306 思路：significant defect / maintenance item / monitor）+ 建议找谁（持牌电工/水管工/建筑检查员），**不是诊断 + 处方**。「建议联系持牌电工检查」安全；「更换这个开关」不安全——措辞门在 :core 用禁则表机检重写/拒绝。
- 流程：finalize 后房东版报告页先展示离线 seed-map 结果；用户再点 `Generate remote suggestions`（**唯一联网点**，可完全跳过）→ preview 明示将发送的最小字段 → payload = 不利发现项（stable_id/状态/经用户确认的必要备注/wear_or_damage）+ seed 先例，不带地址/联系人/照片/音频/完整报告 → HTTPS provider → 有界 schema JSON → 措辞门 + 分级校验 → 注入房东版 composer 建议插槽 → 重新出 PDF。建议内容**不进 data_hash**（ADR-0003 排除域）——生成前后哈希不变（测试断言）。
- 种子表（prompts/remediation/seed-map.json）：30–50 条「检查项类别→建议模板」双语对照（作者按调研报告的常见缺陷类别写）；种子命中可离线直出（provider 只处理未命中/复杂项）。
- provider 接口 `RemediationProvider`（suspend fun suggest(payload): Result）：mock 实现在 :core 测试；真实现 v1 接 Anthropic Messages API（provider 可换设计，key 由用户设置页填、Keystore 支持的本机加密存储）。仅 adapter 持有 HTTP client/`INTERNET`，Network Security Configuration 禁 cleartext；使用平台证书校验与平台可用的最高 TLS（支持双方具备时必须协商 TLS 1.3），禁自定义 trust-all/降级。设连接/读取/总时限、响应字节/条目上限，取消随页面生命周期传播。仅连接/超时、429、502/503/504 用同一 operation id 做 1/2/4 秒±20%抖动、最多 3 次；鉴权/其他 4xx、schema/措辞/安全拒绝不重试。无服务端验签能力时不伪造 Timestamp+Nonce；不为单一可选 API 引入额外 HTTPDNS/DoH 端点。**prompt 注入防御**：备注是不可信文本；系统侧只允许 JSON 建议数组，未知字段/自由文本/超限/错误 stable_id 全拒。日志不含 payload、response body、key、Authorization 或 provider 原始错误体。
- 离线 UX：seed 命中显示 `On-device suggestion`；远程按钮在无网络时仍可点击并就地说明 `Remote suggestions unavailable offline`，主恢复动作是 `Use on-device suggestions`，不弹全局离线 banner、不跳系统设置。网络中断保留已显示结果和用户的报告上下文。
- 免责声明（需求 §8 [定]）恒随建议节渲染。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5；**R3（Sol）按安全面重点评审**（payload 最小化/key 处理/措辞门）。难度 M。
