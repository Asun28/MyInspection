---
id: T2-ROOM-REPEATABLE
title: 房间 repeatable 契约与同窗口 schema 语义债收口（TD6/TD7/TD8）
depends_on: [T1-TEMPLATE-ENGINE, T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
status: todo
branch: T2-ROOM-REPEATABLE
worktree: C:\wt\T2-ROOM-REPEATABLE
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/template/
  - android/core/src/test/kotlin/nz/myinspection/core/template/
  - data/templates/README.md
forbid:
  - 未授权的运行期出站网络 / 写登录态 / 自动发布
  - 落 `.sqm` 却不同时更新受审 schema 快照，或绕过已恢复的 verifyMigrations / 防泄露闸
non_goals:
  - 采集期真正实例化 room_instance 的状态机（T2-CAPTURE-CORE 消费本卡产出）
  - 真实模板内容里逐房间标注 repeatable（T2-ROUTINE-CONTENT / T6-TEMPLATES-REST）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.template.*"
dod_exit: 0
dod_assert: 模板 JSON 的房间定义带 repeatable 标记，经加载→校验→**入库→读回**往返不丢失；每条 item.room 必须在房间定义里声明（未声明即拒，错误点名条目）；repeatable 房间与单例房间在读回结果里可区分；模板历史读回不得因 check_item_def 软删过滤而静默缺项；两处冻结 schema 哈希注释与权威实现一致
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；data/templates/README.md 的房间段；TD6/TD7/TD8 状态与 TASK-BOARD 备注（R5）
---

# T2-ROOM-REPEATABLE

## 产出
模板契约里的**房间定义**（房间键 + `repeatable` 标记）+ 对应 schema 迁移（新 `.sqm` + 版本评审）+ 加载/校验/入库/读回往返 + README 的房间段。

## 拆分依据（为什么不在 T1-TEMPLATE-ENGINE 里做）
T1-TEMPLATE-ENGINE 的 R3 评审两轮都点到这一项：第 1 轮该评审者自己判为 `[FOLLOW-UP]`（理由正是"可能需要改动本卡 allow_paths 之外、且已冻结的持久层 schema"），第 2 轮升级为 block。仲裁结论 = 维持第 1 轮的判断，独立成卡，理由三条：

1. **存不下**：`check_item_def` 只有 item 级的 `room` 列，没有房间定义表，也没有 repeatable 列。`android/core/src/main/sqldelight/` 已在 `fcdc88d` 起登记为 FrozenPaths，加列/加表必须走新 `.sqm` + 版本评审——这在 T1-TEMPLATE-ENGINE 的 `allow_paths` 之外。
2. **半落地更坏**：只往模板 JSON 加 `rooms[]` 而不持久化，会造出「入库时静默丢字段」的路径——正是 T1-SCHEMA-CORE 用 17 轮评审清掉的那一类缺陷，不该在一张**冻结点卡**里新造一个。
3. **迁移闸已就绪**：TD4 已由 PR #47 与后续清理 PR #93 偿还；逐路径 schema 快照豁免和 `verifyMigrations` 已恢复。本卡现在可以在版本评审后提交第一份 `.sqm`，但不得绕过这两道闸。

## 禁止
见 front-matter。特别地：动冻结的 `sqldelight/` 目录**必须**以「本卡 = 该版本评审」的形式显式声明，并通过 TD4 已恢复的 schema 快照与迁移校验闸。

## 两处冻结物都要过版本评审（开卡第一步）
本卡要改的两个面**都已冻结**（`scripts/_config.ps1` FrozenPaths，`guard-frozen` 钩子会当场拒绝就地编辑）：
- `android/core/src/main/sqldelight/`（T1-SCHEMA-CORE 起）——房间定义表/列走**新 `.sqm`**，同步更新受审快照并通过 `verifyMigrations`；
- `android/core/src/main/kotlin/nz/myinspection/core/template/Template.kt`（T1-TEMPLATE-ENGINE 起）——模板 JSON 形态即契约，加 `rooms[]` 段就是改它。

故本卡第一步是**版本评审本身**：把变更提案连同两处影响面报给用户，取得放行后再从 FrozenPaths 临时摘除对应条目、落改动、合并后重新登记。**不要绕过 `guard-frozen`**。

## 同一版本评审窗口并入的既有债

为避免对同一冻结目录反复开窗，本卡同时偿还三个已登记的小债，不另拆实现卡：

- TD6：把 `Supplement.sq` 的链哈希注释改为 `{created_at, text}` 快照域，与 `supplementChainHash` 一致；只改注释，不改哈希行为。
- TD7：模板历史读回使用不过滤 `check_item_def.deleted_at` 的专用查询，或在发现不完整定义时明确失败；不得静默少项，并以软删夹具验证。
- TD8：把 `TemplateVersion.sq` 的 `content_hash` 注释改为模板文件原始字节 SHA-256，与 `LoadedTemplate.parse` 一致；只改注释，不改存量值。

三项都服从本卡相同的 FrozenPaths 临时摘除、版本评审、合并后重新登记与 R5 证据要求。

## 非目标（本卡刻意不做的能力）
见 front-matter：不做采集期的实例化状态机，不写真实模板内容。

## 验收（DoD = 命令 + 退出码 + 断言）
```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.template.*"
```
- 期望退出码：0
- 断言：见 front-matter `dod_assert`。关键是**往返**——只加 JSON 字段而读回时丢掉，等于没做（见「拆分依据」第 2 条）。
