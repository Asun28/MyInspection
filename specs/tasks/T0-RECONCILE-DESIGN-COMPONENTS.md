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
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'context/DESIGN.md' -SimpleMatch '### Contrast threshold contract') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '### Component contract schema') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '### Element completeness gate') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '## Motion and haptics') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '## Accessibility contract'))) { exit 1 }"
dod_exit: 0
dod_assert: 颜色对比、尺寸、层级、所有组件族、状态/错误/空态、动效降级、触控与读屏顺序均有可验证合同，且总 patch 保持评审预算内
review_gate: codex {verdict:pass}
hygiene: prose 引用机读 token/组件 id，避免第二套数值或命名真相源
doc_sync: 与前两张设计卡形成一个完整 DESIGN.md（R5）
---

# T0-RECONCILE-DESIGN-COMPONENTS

## 产出

为已登记的组件和页面旅程补齐视觉、交互、错误、空态、无障碍与动效合同，明确对比度和触控硬下限，并保持设计系统可在无网络环境中实现和验证。

## 验收

执行 front matter 的 `dod_command`，再运行 diff 预算与文档链接检查。
