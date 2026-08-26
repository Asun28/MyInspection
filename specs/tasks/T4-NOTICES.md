---
id: T4-NOTICES
title: 48h 通知：双语文本生成 + 一键复制 + 送达存档（全文快照/提前量/校验快照）
depends_on: [T4-COMPLIANCE-ENGINE]
parallelizable_with: [T4-SCHEDULE, T5-RETENTION]
status: todo
branch: T4-NOTICES
worktree: C:\wt\T4-NOTICES
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/notice/
  - android/core/src/test/kotlin/nz/myinspection/core/notice/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/notice/
forbid:
  - app 发送任何通知（短信/邮件皆禁——生成+复制，人工发送后回记；需求 §10 [定]）
non_goals:
  - 送达凭证附件管理（v1 只记录字段）；通知模板编辑 UI（永不做）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.notice.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 生成前先过合规引擎（Blocked 则拒并给 reason）；UI 明确区分 Generated/Copied/Recorded sent，复制绝不自动回记送达；生成产物=渲染后全文（含物业/时间/法定提前量声明，双语）；存档记录含全文快照/生成时间/预定巡检时间/送达方式枚举/送达时间/计算提前量小时/校验结果快照——**存快照非模板 id**；回记送达后提前量重算并锁定；测试覆盖「送达时刻使提前量掉出 48h–14d 窗」与生成后改期导致旧通知过期
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T4-NOTICES

## 产出
`core/notice`（文本渲染+存档模型）+ 通知页 UI（生成→预览→一键复制（ClipboardManager）→回记送达）。

## Notice handoff experience

Copying is not sending. The interface never checks a Sent box, creates a sent timestamp, or shows success language from a clipboard callback alone.

| State | Visible result | Primary action |
| --- | --- | --- |
| `NOT_GENERATED` | Scheduled visit facts and compliance result | Generate notice |
| `GENERATED` | Full bilingual preview and generation time | Copy notice |
| `COPIED_NOT_RECORDED` | `Copied — not recorded as sent` plus the scheduled visit | Record as sent |
| `SENT_RECORDED_VALID` | Method, absolute sent time, calculated lead time, `Notice recorded` | View saved notice |
| `SENT_RECORDED_INVALID` | Honest stored record plus violated lead-time rule and earliest safe correction | Change inspection time |
| `OUTDATED` | `Inspection time changed after this notice was generated` | Generate updated notice |

After Copy, a persistent handoff card remains on the Property hub and Notice page until the user records delivery or explicitly discards the generated draft. Returning from SMS/email does not assume success. `Record as sent` requires method and sent time; time defaults to now but remains editable before commit. The confirmation restates method, sent time, scheduled inspection time, and calculated lead hours.

Changing property address, recipient-facing tenancy details, inspection time, or notice-rule version after generation marks the generated notice Outdated. An already recorded historical notice remains immutable and visible; a new schedule requires a new notice instead of rewriting the old snapshot.

## 上下文包（执行模型必读）
- 文本形态：固定双语骨架（称谓/物业地址/巡检类型/预定日期时间/依据 RTA 的 48 小时通知声明/落款）+ 值插槽；渲染结果整段入 notice.full_text（**全文快照**，法据审计用——模板日后改了历史记录不变）。
- 流程：选巡检→引擎校验（Pass 才允许生成；Blocked 显示 reason 双语文案）→生成→复制→用户经短信/邮件自行发送→回 app 记录送达方式（SMS/EMAIL/LETTER 枚举）+送达时刻→lead_hours = (scheduled_at − sent_at) 真时长小时（Instant 差，跨 DST 语义同引擎）。
- 送达后若 lead 掉窗（如拖到只剩 40h 才发）：记录仍保存但界面红标+建议改期（校验快照记 fail 项）——存档诚实，不静默美化。
- 文案 key→双语文案表放 core/notice 资源（Luna Max 复核措辞）。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Terra；Luna Max 复核通知双语文案。难度 M。
