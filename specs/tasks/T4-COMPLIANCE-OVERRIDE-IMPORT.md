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
  - "A1 [R1] 按 ADR-0008 验证真实签名包、改 payload/元数据并重算 hash、错误钥/epoch、坏签名、裸 JSON、截断/尾随/溢出、各限额等于与大于边界、有效签名的非法领域/未知 schema/work-check，均有具体拒绝原因。"
  - "A2 [R2] 源 URI 替换不改变已确认快照；staging 修改、active/策略变化、日期失效使确认失效；取消和旧确认重放无额外写入，并发提交串行化；损坏后的新预检/确认可修复同 release/同保存身份且不降低 H，成功状态与重读完整提交一致。"
  - "A3 [R3] 对 staging、关闭/同步、原子发布、回读/回执的每个中断点注入故障，只恢复完整合法旧/新提交；H 与 active 不分裂，无合格内置/当前版本或状态损坏时明确阻断，绝不无条件回内置。"
  - "A4 [R4] 设置、拒权/撤权、重启及真实生产调用者均使用唯一规则服务；无有效规则不能放行排期/通知和既有巡检合规步骤，但证据采集、只读历史报告和恢复入口可用；work-check 仍禁用。"
  - "A5 [R1] 覆盖低/同/高 release、同版本异内容、minimumRelease、APK 同代次升级与旧代次拒绝、旧钥撤销、新 epoch 恢复及旧钥伪造 Long.MAX_VALUE 后换钥；旧 digest-only 状态只按明确升级路径初始化。"
  - "A6 [R3] Auckland 当日/巡检日期的生效与独占过期边界、无期限、未来包、闰日/非法日期和时钟倒退均按 ADR 判定；数据恢复不覆盖本机信任状态，重装不宣称保留历史反回退保证。"
  - "A7 [R1] [R4] API 26 与目标真机通过独立生成的 JCA 签名向量、首个 release APK 可信安装、规则导入及换钥演练；证据绑定 APK 身份与公开钥指纹，不记录私钥或口令。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.compliance.update.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: ADR-0008 已批准且 A1–A7 真实签名/限额/日期/版本/中断恢复/生产调用者证据齐全；active 与 H 原子一致，无有效规则不放行且保留证据采集；API 26 与目标真机安装/换钥验证不由 JVM 绿代替，无阈值或关闭规则开关
review_gate: codex {verdict:pass}
---

# T4-COMPLIANCE-OVERRIDE-IMPORT

版本：V1；信任策略决策完成后开工。`ComplianceUpdate.inspect/activate` 见模块接口规格；选文件、预检、确认、失败恢复复用系统表面与基础状态组件，不复用 DOCX 的映射状态机。

信任与恢复矩阵唯一来源为 [ADR-0008](../../docs/adr/0008-compliance-update-trust.md)（2026-09-08 用户已批准技术策略、责任与首次信任安排）。本卡保持 todo，前置卡通过 R3 并合并后才解除本项依赖；以上验收是方案投影，非功能已交付。使用现有 loader，冻结 schema 不在本卡修改范围。入口与状态规划见 DESIGN 的版本可用性章节。

生产接线：在既有 Settings 注册 COMPLIANCE_RULES_SETTINGS 与 RULE_FILE_PICKER；只新增文件更新入口，不暴露规则阈值/关闭开关。

实现前须枚举全部生产 loader/合规调用者及备份路径，以 ADR 规定的唯一规则服务统一启动恢复、使用时校验和无有效规则的阻断；MainActivity owns 装配，私有状态复用现有存储、不新增 DB 表。若调用者或发布资产不在 allow_paths，先按调查结果修订卡范围，不以仅设置页验收代替 A4/A7。签名公钥、内置包、可信 release APK 与离线签名产物必须真实提供；签名与密钥操作服从用户批准的责任安排。
