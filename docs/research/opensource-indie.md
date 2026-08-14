# 开源与独立巡检 app 全景（Opus 5 深挖 · 2026-08-14）

**Headline: 不存在任何有维护、有采用的开源房产 condition-report app。** GitHub 最高 13 星且 2018 年死亡；F-Droid 零命中（ODK Collect 也已下架）。可用先例：OpenInspection（新、AGPL、极小）、Inspexly（Vue3+Laravel, MIT）、ODK/XLSForm 与 Epicollect5（表单引擎先例）、TurboTenant 免费 condition report（US 工作流最佳先例）、一排一次性买断的单人开发 app。

## A. 开源要点
- **OpenInspection**（github.com/InspectorHub/OpenInspection，AGPL-3.0+自托管豁免，Cloudflare 栈，PWA 非 local-first）。**数据模型精华**：inspection 上快照 `templateSnapshot`+`ratingSystemSnapshot`（多年后报告须可一致重渲）；答案 = 每巡检一个 JSON 文档；照片 = per-inspection 池 + item 引用 + 孤儿 GC 表；`report_pdfs` 带渲染输入的 SHA-256 `contentHash` 跳过重复渲染。评级层级 = 数据（2-10 级 {label, abbreviation, color, severity good/minor/marginal/significant, isDefect, pausesAdvance, hotkey}），非代码枚举。
- **Immoscan-v1**：Kotlin+Compose+Room+CameraX+听写+签名+PDF——与我们同栈，但 6 天死亡（24 commits）；佐证栈选型无阻碍、也无现成可抄。
- **microrealestate**（1.2k 星最流行开源房东软件）：**完全没有巡检功能**——域空白实锤。
- **F-Droid**：唯一近似 = Field Survey（GPL-3.0，YAML 模板+离线记录+照片，导出 XLSX 无 PDF）。
- **XLSForm/ODK 先例**：房间 = `begin_repeat` 重复组，非固定列。

## C. 独立/成熟 app 观察
- 独立档全部一次性买断（$19.99–29.99）+「无订阅」卖点+离线+双签+PDF；评级词表见 D。
- **myInspections（myRent NZ，10k+，4.6★）**：NZ 本土参照——离线、视频、e-sign、分享链接。
- homePad：**1000+ 预置描述短语** + 前次数据前滚；Chapps：仪表读数+钥匙交接模块。

## D. 相邻美国工具
- **TurboTenant**（免费，US 最佳工作流先例）：房间/设施全部 **Poor/Fair/Good**；**Poor/Fair 强制填备注**、提示拍照；收件人无需账号；e-sign 存档、退租复用。
- RentCheck：租客自助引导式走查，"unalterable and time-stamped record"。
- SafetyCulture→Mitti 免费档：5 个活动模板。Fulcrum：Repeatable Sections（Rooms 重复节示例）。

## 先例收敛的设计要点（对照本项目）
1. 模板+评级随巡检快照 → 我们：template_version 不可变 + content_hash，等价 ✓（卡内已锚定「被引用的版本行不可变」）。
2. 答案 JSON 文档 vs 规范化行 → 我们**保持规范化行**：逐项历史对齐（stable_id）是核心功能，SaaS 的 JSON 文档形态不适配 local-first 查询。
3. 评级 = 数据非枚举 → 我们已按模板类型配置 allowedStatuses ✓。
4. **低于 Good 强制备注（照片提示）**（TurboTenant 规则）→ [adopt·可否决] 并入采集核（短语库一点即满足，成本近零）。
5. 照片孤儿 GC → [adopt] 并入照片管线（去关联后孤儿文件清理）。
6. **房间 = 重复组/实例**（ODK/Fulcrum 收敛）→ **schema 修正**：inspection_item/photo 增加 room_instance 维度（多卧室同 stable_id 不冲突），冻结前修。
7. PDF 渲染输入 content-hash 缓存 → v1 不需要（单用户按需生成）；记为 v2 候选。
8. per-level hotkey/pausesAdvance「抬眼评级」→ 触摸大按钮已覆盖，不采。

## 完整原始报告
细节（含全部搜索日志与 URL）见会话产出；本文件为入卡用摘录。
