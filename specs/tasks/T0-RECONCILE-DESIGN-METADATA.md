---
id: T0-RECONCILE-DESIGN-METADATA
title: 建立 Field Ledger 可机读设计令牌与组件注册表
depends_on: []
parallelizable_with: [T0-RECONCILE-DATA-AUTHORITY, T0-RECONCILE-LESSONS]
status: todo
branch: T0-RECONCILE-DESIGN-METADATA
worktree: C:\wt\T0-RECONCILE-DESIGN-METADATA
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、生成资源或把 skeleton 当成生产视觉先例
  - 引入需要联网才能解析的字体、图标或设计依赖
non_goals:
  - 页面旅程、导航/恢复语义和完整性矩阵
  - 实现 Compose 组件
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'context/DESIGN.md' -Pattern '^version: beta$') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^dark-colors:$') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^  health-issue-row:$') -and (Select-String -Path 'context/DESIGN.md' -Pattern '^  report-action-sheet:$'))) { exit 1 }"
dod_exit: 0
dod_assert: DESIGN.md 具备可离线读取的浅色/深色令牌、排版/尺寸/动效/无障碍元数据与完整具名组件注册表，原有 prose 仍可读
review_gate: codex {verdict:pass}
hygiene: 元数据键稳定、无同义重复；视觉状态同时具备非颜色语义
doc_sync: 本卡只建立机读层，后续两卡补充行为合同（R5）
---

# T0-RECONCILE-DESIGN-METADATA

## 产出

在既有 `context/DESIGN.md` 前加入可机读的 Field Ledger 设计系统：颜色、排版、间距、形状、动效、触控/无障碍和具名组件状态。保持单文件真相源，不在本卡扩写页面旅程。

## 验收

执行 front matter 的 `dod_command`，并确认本卡净 diff 低于 R3 预算。
