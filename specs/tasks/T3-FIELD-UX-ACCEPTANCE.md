---
id: T3-FIELD-UX-ACCEPTANCE
title: Field Ledger 真机 UX 验收：日光、单手、无障碍与相机取证
depends_on: [T2-CAPTURE-UI, T3-HISTORY-COMPARE]
status: todo
branch: T3-FIELD-UX-ACCEPTANCE
worktree: C:\wt\T3-FIELD-UX-ACCEPTANCE
allow_paths:
  - docs/ux/
  - specs/tech-debt-tracker.md
forbid:
  - 在验收卡内顺手修改生产 UI；发现项登记成独立 TD/卡
  - 复制 Luosunce/material-design-data 的代码、图片或 CC BY-NC-SA 内容
  - 用模拟器截图代替日光、单手、TalkBack 与相机真机证据
non_goals:
  - 平板/横屏重设计（首版仍按单手竖屏）
  - 用户研究招募、遥测平台或远程分析服务
plan_ref: context/DESIGN.md#accessibility-contract
acceptance:
  - "A1 evidence records the device and build"
  - "A2 verify daylight and one-hand operation"
  - "A3 verify TalkBack, 200% text, and Reduce Motion"
  - "A4 verify process death, offline use, and provider recovery"
  - "A5 every P0/P1 finding has a closure reference"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; if (-not (Test-Path docs/ux/FIELD-UX-ACCEPTANCE.md)) { exit 1 }; if (Select-String -Path docs/ux/FIELD-UX-ACCEPTANCE.md -Pattern '⬜|PENDING|待验证') { exit 1 }
dod_exit: 0
dod_assert: 真机报告逐项含设备/构建、步骤、截图或录屏、结果与发现 TD：日光、单手拇指、48dp、TalkBack、200% 字号、保存/失败反馈、减少动态效果、相机/历史对位；所有 P0/P1 发现都有偿还指针
review_gate: codex {verdict:pass}
hygiene: 重复证据合并；每个发现只保留能证明风险的一组最小截图/录屏（R4）
doc_sync: context/DESIGN.md 只同步经证据确认的规则；TASK-BOARD 记录验收结论（R5）
---

# T3-FIELD-UX-ACCEPTANCE

## 产出
一份可审计的 Field Ledger 真机 UX 验收报告。它是发布前质量门，不是视觉意见清单。

## 固定矩阵
1. 户外/高亮屏下的主文案、状态与焦点可辨。
2. 单手完成一间房；主要动作位于可达区且点击区至少 48dp。
3. TalkBack 顺序、角色、状态与自定义 evidence rail 的合并语义准确。
4. 系统字号 200% 时不截断关键动作、缺失计数和相机控制。
5. 保存中、成功、离线和失败反馈不会丢失用户输入。
6. 减少动态效果开启时无依赖动画才能理解的状态。
7. 相机预览、拍摄与历史 overlay/并排降级符合 T1 spike 结论。
8. 隐私标记与错误/不利发现使用不同颜色和可读文本，不靠颜色单独传意。

## 发现处理
验收卡不修生产代码。每个 P0/P1 发现追加到技术债追踪器并开独立卡；报告记录指针后才可通过。
