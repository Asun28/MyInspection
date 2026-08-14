# 0004 — 合规引擎：配置驱动 + entryPurpose 维度（行为不变，结构留门）

日期：2026-08-14 · 状态：accepted（法律细分部分 **proposed，待用户/持牌人士确认**）

## 背景
需求 §10 [定]：4 周 Routine 限额 / 48h–14d 通知窗 / 08:00–19:00（寄宿 18:00）为阻断闸、不可关闭、不进设置；规则做成可更新配置文件。Codex 评审引 RTA s48(2)(c)：「查验已约定/要求的维修工作」的进入在法上是另一种 entry purpose，经规定通知后可行——需求里「上次发现问题、两周后回去复检必须拦」的场景，若定性为 work-check 而非 inspection，法律结论可能不同。

## 决策
1. 规则引擎纯 :core 实现，规则内容从版本化 JSON 配置加载（`configs/compliance/`，schema 冻结于 T4 卡）：schema 版本、生效日、来源引用、时区（Pacific/Auckland，含 DST 边界测试）、per-`entryPurpose` 的通知窗/频率规则/时段、boarding house 变体。
2. **配置 schema 自始含 `entryPurpose` 维度**；本版只启用 `inspection` 用途，行为与需求写死的一致（含「双方同意也拦」）。将来法律确认 work-check 语义，只加配置条目不改代码。
3. 更新语义：APK 内置权威配置随版本发布；手动导入 override 须校验 schema 版本 + 签名式校验和，不做用户可编辑。

## 备选方案
规则硬编码（需求明拒）；本版即实现 work-check 放行（法律未经确认，拒——阻断从严是需求立场）。

## 后果
- 用户待办：向 Tenancy Services / 持牌人士确认 s48(2)(c) 复检语义后，决定是否加 work-check 配置条目。
- 引擎测试锚定来源链接的规则夹具（legislation.govt.nz / tenancy.govt.nz），法规变更 = 换配置 + 重跑夹具。
