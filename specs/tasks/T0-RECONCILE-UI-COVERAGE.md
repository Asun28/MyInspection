---
id: T0-RECONCILE-UI-COVERAGE
title: 建立 UI/UX elements 页面与组件覆盖索引
depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-COVERAGE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE
allow_paths:
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 在任务卡复制 DESIGN.md 的完整组件规格
  - 修改产品代码、已归档任务卡或已合并主题/照片去重卡
non_goals:
  - 修改下游实现卡、实现 UI、生成截图或建立第二套设计 token
  - 改动数据库/安全权威文档
dod_command: pwsh -NoProfile -Command "if (-not ((Test-Path 'docs/UI-UX-ELEMENTS.md') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^> Normative source: `context/DESIGN.md`') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^\| T2-CAPTURE-UI \|') -and (Select-String -Path 'docs/UI-UX-ELEMENTS.md' -Pattern '^\| T5-BACKUP-IO \|'))) { exit 1 }"
dod_exit: 0
dod_assert: 每个生产页面、overlay、关键状态和设计组件都有唯一 owning card/验收指针；下游卡仅引用 canonical 章节和必要验收，不重复大段设计正文
review_gate: codex {verdict:pass}
hygiene: 组件索引是覆盖投影而非真相源；删除重复 prose 后仍可从卡定位到设计合同
doc_sync: DESIGN.md、UI-UX-ELEMENTS 与下游卡指针闭环（R5）
---

# T0-RECONCILE-UI-COVERAGE

## 产出

增加一份完整但非规范性的 UI/UX elements 覆盖索引，将页面、overlay、状态、组件 id 与 owning card 连成可检查的投影；设计细节仍只由 `context/DESIGN.md` 定义。

## 验收

执行 front matter 的 `dod_command`，并确认索引不复制 canonical token/行为正文。
