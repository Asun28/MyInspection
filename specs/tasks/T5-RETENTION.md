---
id: T5-RETENTION
title: 租客数据保留期 + 一键清理（Privacy Act 2020）
depends_on: [T1-SCHEMA-CORE]
parallelizable_with: [T4-NOTICES, T4-SCHEDULE]
status: merged
branch: T5-RETENTION
worktree: C:\wt\T5-RETENTION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/retention/
  - android/core/src/test/kotlin/nz/myinspection/core/retention/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/settings/retention/
forbid:
  - 清理破坏 finalize 哈希可复验性（哈希域刻意不含租客联系方式——T1-CANON 契约；清理只动联系方式与照片保留策略允许的部分）
non_goals:
  - 自动定时清理（v1 手动一键，先给用户控制权）；照片匿名化处理（整删或保留，不做修图）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.retention.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 到期计算测试绿（租约结束 + 保留期（默认 12 个月，配置常量，待用户定数）→列出可清理 tenancy）；一键清理 = 联系方式字段置空 + 标记 purged_at，巡检/照片/报告证据默认保留（跨年证据立场）；清理后 verifyChain/data_hash 复验仍绿（测试断言）；设置页显示各 tenancy 保留状态
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5；保留期数值待用户定后回填配置）
---

# T5-RETENTION

## 产出
`core/retention`（到期计算+清理用例）+ 设置页保留区块。

## 上下文包（执行模型必读）
- 法据（需求 §11 [待] + 调研 synthesis NZ 节）：Privacy Principle 9 = not longer than necessary；**RTA s123A 反向要求**：巡检报告（reg 40 明确含照片/视频）租期中 + 结束后 **12 个月**内必须保留、MBIE 索取 10 个工作日内出示；实务建议 6 年（Limitation Act）。⇒ 12 个月是**保留的法定下限**而非清理期限——默认「联系方式清理期 = 租约结束后 12 个月」做成单点常量，**数值等用户定**（TASK-BOARD 待定清单 #3）；证据（照片/报告）保留立场与 s123A 完全对齐。
- 清理边界要跟用户确认过的立场一致：**联系方式**（tenancy.tenant_name/contact）= 清；**巡检证据**（照片/报告/哈希链）= 保（押金争议跨年证据是本 app 存在理由；哈希域不含联系方式正为此设计）。若用户日后要连照片清，是策略开关的扩展、不是本卡返工。
- 清理不可逆——确认对话框走「输入 tenancy 名确认」级别防误触。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · medium；备选 Luna Max。难度 S。
