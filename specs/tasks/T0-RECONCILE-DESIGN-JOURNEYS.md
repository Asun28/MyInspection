---
id: T0-RECONCILE-DESIGN-JOURNEYS
title: 补齐 Field Ledger 信息架构、导航、恢复与离线隐私旅程
depends_on: [T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEYS
worktree: C:\wt\T0-RECONCILE-DESIGN-JOURNEYS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码或机读组件 id
  - 把需要网络的 provider 行为伪装为核心离线流程
non_goals:
  - 逐组件视觉合同、motion token 或 Compose 实现
  - 新增 Dashboard、Reports 顶级页或导航抽屉
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'context/DESIGN.md' -SimpleMatch '## Information architecture') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '## Trigger-to-route mapping') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '## Navigation feedback and focus lifecycle') -and (Select-String -Path 'context/DESIGN.md' -SimpleMatch '### Offline and data-protection experience'))) { exit 1 }"
dod_exit: 0
dod_assert: 页面清单、三顶级导航、触发到路由、返回/焦点恢复、主巡检旅程及离线/备份/恢复/删除隐私旅程均有确定状态与失败出口
review_gate: codex {verdict:pass}
hygiene: 同一页面只有一个路由/返回合同；离线正常态不使用持续错误横幅
doc_sync: 与机读组件 id 和当前产品需求保持一致（R5）
---

# T0-RECONCILE-DESIGN-JOURNEYS

## 产出

把 Field Ledger 从视觉语言补全为可实现的应用体验合同：页面类型、导航栈、触发入口、焦点恢复、证据采集主流程，以及离线安全/备份/恢复/清除的用户旅程。

## 验收

执行 front matter 的 `dod_command`，并确认所有路线都有返回语义、所有失败状态都有恢复动作。
