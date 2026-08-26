---
id: T4-SCHEDULE
title: 巡检排程：13 周节奏提醒 + 本地通知
depends_on: [T4-COMPLIANCE-ENGINE]
parallelizable_with: [T4-NOTICES, T5-RETENTION]
status: todo
branch: T4-SCHEDULE
worktree: C:\wt\T4-SCHEDULE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/schedule/
  - android/core/src/test/kotlin/nz/myinspection/core/schedule/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/
forbid:
  - 把提醒节奏当法律限制展示（13 周是提醒节奏；法律上限在合规引擎——两层别混，需求 §3）
non_goals:
  - 日历集成/外部日历写入（v1.1）；自定义节奏 UI（默认 13 周，常量配置即可）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.schedule.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 下次应巡建议 = 上次同类型 finalize 后 +13 周（按物业/类型算，测试绿）；UI 全程标 `Suggested` 而非法定到期，空态/现在建议/即将建议分组与深链正确；建议日期/合规校验用 Pacific/Auckland，打扰策略用通知触发时的设备时区；通知权限只在开启提醒时请求；本地提醒在重启、时区、手工改时钟和 DST 后重算，同一 property+type+suggestedDate 只有一个有效提醒，设备当地 20:00–08:00 顺延且每个设备当地日最多 2 条；锁屏文案无地址/租客/照片；assembleDebug 绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T4-SCHEDULE

## 产出
`core/schedule`（下次应巡计算，纯函数）+ 提醒调度薄壳 + 物业列表页「距下次 Routine」徽标。

## Schedule experience contract

Schedule is a planning aid, not a compliance verdict. User-facing copy always says `Suggested` or `Plan`, never `Legally due`, `Overdue breach`, or an equivalent alarmist claim.

| Section/state | Card content | Action |
| --- | --- | --- |
| `SUGGESTED_NOW` | Property, inspection type, `Suggested now`, last finalized absolute date | Plan inspection |
| `UPCOMING` | Property, type, suggested absolute date, relative support text | View property |
| `NO_HISTORY` | Property, `No earlier {type} inspection` | Plan first inspection |
| `COMPLIANCE_CONFLICT` | Suggested rhythm plus exact legal/compliance correction returned by core | Choose valid date |

Cards sort Suggested now first, then upcoming date, then property address. The top-level badge counts only `SUGGESTED_NOW` properties and is announced as `3 inspections suggested`, never just `3`.

`Plan inspection` opens Setup with property/type prefilled and the suggested date proposed, not committed. Core validation runs before the date is accepted. Notification permission is requested only when the user enables a system reminder; denial leaves the Schedule screen and planning flow fully usable.

## 上下文包（执行模型必读）
- 计算：每物业每类型独立（ROUTINE 13 周默认；ANNUAL 12 个月）；无历史→「建议排首检」空态。建议值只是提醒——用户真正建巡检时合规引擎才是闸。
- 通知：POST_NOTIFICATIONS 运行时权限（API 33+）；提醒文案双语 key 复用 notice 文案表机制；点通知深链到该物业。
- 重启存活：BOOT_COMPLETED 重挂或用 WorkManager 周期扫描（实现者二选一，卡不锁实现，测试锚定「重启后提醒仍会来」的调度登记态）。
- 频控/安静时段：建议日期与法律规则始终按 `Pacific/Auckland` 计算；是否打扰按通知触发时的当前设备时区计算。每个 reminder 的稳定去重键是 `property + inspectionType + suggestedLocalDate`；单个本地用户每个设备当地日历日最多展示 2 条。目标时刻落在设备当地 20:00–08:00 时顺延至当地 08:00，再把对应巡检时刻交合规引擎复核；时区、DST、手工改时钟、重启和 app 升级都重算未送达项，不重复补发旧项。
- 本卡是 Android 本地通知，不做服务端 Push、营销触达、设备指纹或远程频控。权限拒绝、通知被系统禁用或深链目标失效均保留 Schedule 页面与建巡检能力。

## 验收 / 执行建议
dod 见 front-matter。首选 GPT-5.6 Terra · medium；备选 DeepSeek V4 Pro。难度 S。
