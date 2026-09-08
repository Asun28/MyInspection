---
id: T7-AUDIT-REMOTE-DOCS
title: 远端补交需求与页面版本合同
status: merged
depends_on: [T7-AUDIT-REMOTE-CARDS]
allow_paths:
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
  - docs/inspection-app-requirements.md
  - specs/tasks/T7-AUDIT-REMOTE-DOCS.md
requirements:
  - "R1 当补交本轮审校时，文档应保持用户的V1预设/键盘、V1.1批量、产品V2语音决定及未定项，不改变运行代码。"
acceptance:
  - "A1 [R1] 卡片结构与实际diff检查通过，无缺失或循环依赖；新功能卡保持todo，审校分批交付不宣称功能实现。"
  - "A2 [R1] 基于真实origin/master的完整PR差异经远端R3及CI通过，保留其他任务内容。"
  - "A3 [R1] 无运行源码或冻结文件变更；provider、信任根及未批准的格式细节继续标待澄清。"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: 卡片结构与diff通过；审校版本、引用及依赖复核；远端PR/R3/CI真实通过
review_gate: codex {verdict:pass}
non_goals:
  - 功能实现、冻结协议或脚手架改动、整体推送分叉的本地主分支
hygiene: 纯文档SkipRed；不新增镜像测试
doc_sync: 本交付卡随PR合入标merged；后继卡同步既有叙述，保留可追溯记录
---

用户2026-09-08指出旧闭环仅本地合并、未建PR。本卡从远端基线独立补交，旧本地R3不冒充远端结果。整个审校按新增卡/接口→既有卡修订→五文档同步分三张PR，阶段间以新版本决定为准，最终一致性由第三张验证。三阶段完成前不宣称整批远端完成。

前置 PR #240（b1b21441）、#247（212a0d05）已通过正式 R3/CI 并合并。本卡 status 随本 PR 成功合入生效；届时三批审校远端补交完成，11 张新功能卡仍待实施。
