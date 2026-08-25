---
id: T0-RECONCILE-DESIGN-COMPONENTS
title: 补齐 Field Ledger 组件合同、对比度、动效与无障碍规则
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENTS
worktree: C:\wt\T0-RECONCILE-DESIGN-COMPONENTS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、组件 id 或已签认产品范围
  - 仅靠颜色表达状态、无限动画、阻断式离线提示或危险操作无确认
non_goals:
  - 页面覆盖索引与下游实现卡同步
  - 生成 Figma、位图或 Compose 代码
acceptance:
  - "A1 视觉可验证：light/dark 对比阈值表具名 4.50:1、7.00:1、3.00:1，CI contrast metadata、visual physics、typography/layout/elevation/shapes 不产生第二套 token"
  - "A2 组件闭包：九个 component family matrix 共恰好 81 个 backtick id rows，与 machine-readable registry 一一对应；每行有 anatomy、states、deterministic behaviour、semantics/focus、Compose base"
  - "A3 完整状态：导航/结构、证据/输入、反馈/决策、相机、列表/发现、表单/选择、进度/恢复、历史/媒体、备份/健康/合规九族均有 empty/loading/error/disabled/read-only/commit 等适用状态"
  - "A4 动效与触觉：100/150/180/200/250ms 与 reduced-motion 零位移一致；无限 pulse、颜色-only、焦点丢失和 layout-shift 动效被禁止"
  - "A5 无障碍与危险操作：48dp、200% 字号、TalkBack 顺序、焦点回退、状态文字+图标、预览影响/输入确认/执行中阻止重复和 Back 均为硬合同"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'context/DESIGN.md' -Pattern '^### Contrast threshold contract$') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '`4.50:1`') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '`7.00:1`') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '`3.00:1`') -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^### (Navigation and structure component matrix|Evidence and input component matrix|Feedback and decision component matrix|Camera component matrix|Structure, list, and discovery component matrix|Form and selection component matrix|State, progress, and recovery component matrix|History, evidence, and media component matrix|Backup, report, health, and compliance component matrix)$').Count -eq 9) -and ((Select-String -Path 'context/DESIGN.md' -Pattern '^\| `[a-z0-9-]+` \|').Count -eq 81) -and (Select-String -Path 'context/DESIGN.md' -Pattern '^### Element completeness gate$') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^## Motion and haptics$') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch 'reduced motion') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^## Accessibility contract$') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '`48dp`') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '`200%`') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch 'TalkBack'))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 全部通过：三档对比阈值、9 matrices、81 exact component rows、completeness/motion/accessibility headings 及 48dp/200%/TalkBack/reduced-motion 约束均可删除验证
review_gate: codex {verdict:pass}
hygiene: prose 引用机读 token/组件 id，避免第二套数值或命名真相源
doc_sync: 与前两张设计卡形成一个完整 DESIGN.md（R5）
---

# T0-RECONCILE-DESIGN-COMPONENTS

## 产出

为已登记的组件和页面旅程补齐视觉、交互、错误、空态、无障碍与动效合同，明确对比度和触控硬下限，并保持设计系统可在无网络环境中实现和验证。

## 验收

执行 front matter 的 `dod_command`，再运行 diff 预算与文档链接检查。
