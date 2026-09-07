---
id: T4-COMPLIANCE-OVERRIDE-IMPORT
title: 手动规则文件预检、可信激活与恢复
status: todo
depends_on: [T4-COMPLIANCE-UPDATE-TRUST, T4-COMPLIANCE-ENGINE-R3-CLOSURE, T1-LOCAL-DATA-SECURITY, T2-CAPTURE-UI]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/compliance/update/
  - android/core/src/test/kotlin/nz/myinspection/core/compliance/update/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/settings/compliance/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/settings/compliance/
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
plan_ref: docs/TASK-BOARD.md#2026-09-06-审校补卡与版本计划
requirements:
  - "R1 当用户在设置中选取规则文件时，系统应先有界读取并执行已批准的来源验证和领域配置校验，再展示预检结果，不得先覆盖当前配置。"
  - "R2 只有当预检有效且用户确认的文件身份仍匹配时，系统才应原子激活该版本并显示真实版本、生效日期与来源。"
  - "R3 如果来源不可信、文件被替换、版本不支持、字段非法或激活中断，则系统应拒绝激活并保持已批准的回退策略，错误不得泄露原始路径或异常文本。"
  - "R4 在导入和启动恢复过程中，系统应保持合规阻断能力，且不应出现关闭或绕过规则的入口。"
acceptance:
  - "A1 [R1] 有效文件、同源伪造摘要、信任凭证负例、过大流和非法字段均按批准策略判定。"
  - "A2 [R2] 确认后替换文件、重复确认及失败写入不能激活另一份内容，成功状态与实际 loader 版本一致。"
  - "A3 [R3] 每个激活阶段中断后仍读到完整已批准配置；既有无效 override 回退内置行为只可按前置 ADR 显式改变。"
  - "A4 [R4] 设置页面、SAF 拒绝/撤权和重启均不能关闭引擎；work-check 仍遵守 ADR-0004。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.compliance.update.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 按已批准信任矩阵验证有效/伪造/过大/未知/替换文件；每个激活中断阶段保持完整有效配置与阻断能力；设置入口/拒权/撤权/恢复真机证据齐全，无阈值或关闭规则开关
review_gate: codex {verdict:pass}
---

# T4-COMPLIANCE-OVERRIDE-IMPORT

版本：V1；信任策略决策完成后开工。`ComplianceUpdate.inspect/activate` 见模块接口规格；选文件、预检、确认、失败恢复复用系统表面与基础状态组件，不复用 DOCX 的映射状态机。

待澄清由前置卡关闭：来源、公钥/摘要分发、日期/回退策略与输入限额。使用现有 loader，冻结 schema 不在本卡修改范围。入口与状态规划见 DESIGN 的版本可用性章节。

生产接线：在既有 Settings 注册 COMPLIANCE_RULES_SETTINGS 与 RULE_FILE_PICKER；只新增文件更新入口，不暴露规则阈值/关闭开关。
