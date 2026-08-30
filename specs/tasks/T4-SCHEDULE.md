---
id: T4-SCHEDULE
title: 巡检排程：按类型节奏提醒 + 本地通知
depends_on: [T4-COMPLIANCE-ENGINE]
parallelizable_with: [T4-NOTICES, T5-RETENTION]
status: todo
branch: T4-SCHEDULE
worktree: C:\wt\T4-SCHEDULE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/schedule/
  - android/core/src/test/kotlin/nz/myinspection/core/schedule/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/
  - android/app/src/main/AndroidManifest.xml
forbid:
  - 把提醒节奏当法律限制展示（13 周是提醒节奏；法律上限在合规引擎——两层别混，需求 §3）
  - 在提醒计算/登记时伪造通知送达时刻来绕过或提前调用合规闸（提醒仅建议；真正建巡检/改期时才用真实输入过 ComplianceEngine）
non_goals:
  - 日历集成/外部日历写入（v1.1）；自定义节奏 UI；精确闹钟/开机 receiver
  - MainActivity/根导航/T2-CAPTURE-UI 接线；DB/schema/依赖变更；后台自动创建巡检
plan_ref: context/DESIGN.md#structure-list-and-discovery-component-matrix
acceptance:
  - "A1 due, empty, and type-filter views expose an explicit state badge; every property row emits the stable property-id + inspection-type route payload without owning the root navigator"
  - "A2 cadence is local-calendar based and type-specific: ROUTINE = last same-type finalized date +13 weeks, ANNUAL = +12 calendar months, INGOING/EXIT = no recurrence, and no same-type history = first-inspection empty state"
  - "A3 one unique persistent WorkManager reminder is derived per property/type/due occurrence with offline initial delay and stable route payload; repeated registration is idempotent and remains advisory rather than a compliance decision"
  - "A4 API 33+ notification permission is requested only from the user's reminder action; granted schedules/posts bilingual local notification, denied explains recovery and permits retry; pre-33 needs no runtime request"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.schedule.*" :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: core 测试钉住 ROUTINE +13 本地周、ANNUAL +12 本地月、INGOING/EXIT 无周期、同类型/物业隔离与月末/DST；app 测试钉住唯一 WorkSpec/初始延迟/稳定 route payload、重复登记幂等策略、API 33 权限三态与双语通知映射；WorkManager 重启后保留登记；assembleDebug 绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T4-SCHEDULE

## 产出
`core/schedule`（下次应巡计算，纯函数）+ WorkManager 提醒调度薄壳 + 独立 Schedule route 内容（物业列表的到期 badge/下次建议事实）。T2-CAPTURE-UI 后续把该 route 接入根导航。

## 上下文包（执行模型必读）
- 计算：每物业每类型独立（ROUTINE 13 本地周；ANNUAL 12 个本地民历月；INGOING/EXIT 不复发）；无同类型历史→「建议排首检」空态。建议值只是提醒——用户真正建巡检/改期时才以真实通知输入过合规引擎。
- 通知：仅增加 POST_NOTIFICATIONS；API 33+ 在用户点击提醒动作时请求，拒绝态给解释与重试，不在 app 启动时索权。双语通知文案复用 notice 的 key→copy 形态；pending-intent 载荷固定为 property id + inspection type，由后续根导航消费。
- 重启存活：选 WorkManager 唯一一次工作；持久登记与系统重启恢复由平台承担。使用 inexact initial delay，不引入精确闹钟、BOOT_COMPLETED receiver 或新依赖。
- UI：沿用 Field Ledger 的 Material 3 token/排版，Schedule 内容独立可预览/测试；本卡不改 MainActivity，不抢 T2-CAPTURE-UI 的 app shell 与 root route 所有权。

## 验收 / 执行建议
dod 见 front-matter。首选 GPT-5.6 Terra · medium；备选 DeepSeek V4 Pro。难度 S。
